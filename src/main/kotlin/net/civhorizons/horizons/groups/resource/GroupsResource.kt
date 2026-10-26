package net.civhorizons.horizons.groups.resource

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.codec.codecs.array.ArrayCodec
import com.hypixel.hytale.codec.codecs.map.EnumMapCodec
import com.hypixel.hytale.codec.codecs.map.MapCodec
import com.hypixel.hytale.codec.codecs.set.SetCodec
import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.civhorizons.horizons.Horizons
import java.util.EnumMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GroupsResource(
    val groups: ConcurrentHashMap<UUID, GroupComponent> = ConcurrentHashMap<UUID, GroupComponent>()
) : Resource<EntityStore> {

    override fun clone(): Resource<EntityStore> {
        return GroupsResource(ConcurrentHashMap(groups))
    }

    class GroupComponent(
        var uuid: UUID = UUID.randomUUID(),
        var name: String = "",
        var roles: Array<GroupRole> = arrayOf(),
        var membership: MutableMap<UUID, GroupMembershipData> = mutableMapOf(),
        var invites: MutableMap<UUID, GroupInviteData> = mutableMapOf(),
    ) {

        /**
         * Get all members on the group
         */
        fun members(): List<PlayerRef> {
            return membership.keys.mapNotNull {
                Universe.get().getPlayer(it)
            }
        }

        /**
         * Return true if _any_ role grants the user a permission
         */
        fun playerHasPermission(player: PlayerRef, permission: GroupRole.Permission): Boolean {
            val activeRoleUUIDs = membership[player.uuid]?.roles ?: return false
            val activeRoles = roles.filter { activeRoleUUIDs.contains(it.uuid) }

            return activeRoles.any { it.permissions[permission] == true }
        }

        /**
         * Returns true if the player is any member of a group at all, even if they don't have a role assigned
         */
        fun isPlayerOnGroup(player: PlayerRef): Boolean {
            return membership[player.uuid] != null
        }

        companion object {
            const val DEFAULT_OWNER_ROLE = "Owner"
            const val DEFAULT_MEMBER_ROLE = "Member"

            fun getType() = Horizons.instance.groupsModule.groupsResourceType

            /**
             * Create a group with the default roles, Owner and Member, and the creator as Owner.
             */
            fun createDefault(name: String, creator: UUID, creatorName: String): GroupComponent {
                val ownerRole = GroupRole(
                    name = DEFAULT_OWNER_ROLE,
                    permissions = mapOf(GroupRole.Permission.MANAGE_GROUP to true)
                )
                val memberRole = GroupRole(name = DEFAULT_MEMBER_ROLE, permissions = mapOf())

                return GroupComponent(
                    name = name,
                    roles = arrayOf(ownerRole, memberRole),
                    membership = mutableMapOf(
                        creator to GroupMembershipData(roles = setOf(ownerRole.uuid), username = creatorName)
                    )
                )
            }

            val CODEC = BuilderCodec.builder(GroupComponent::class.java, ::GroupComponent)
                .append(KeyedCodec("UUID", Codec.UUID_BINARY),
                    { group, uuid -> group.uuid = uuid},
                    { it.uuid }
                ).add()
                .append(KeyedCodec("Name", Codec.STRING),
                    { group, name -> group.name = name },
                    { it.name },
                ).add()
                .append(KeyedCodec("Roles", GroupRole.CODEC_ARRAY),
                    { group, roles -> group.roles = roles },
                    { it.roles }
                ).add()
                .append(KeyedCodec("Membership", MapCodec(GroupMembershipData.CODEC, ::ConcurrentHashMap)),
                { group, membership -> group.membership = membership.map { Pair(UUID.fromString(it.key), it.value) }.toMap().toMutableMap() },
                    { it -> it.membership.map { Pair(it.key.toString(), it.value) }.toMap() }
                ).add()
                .append(KeyedCodec("Invites", MapCodec(GroupInviteData.CODEC, ::ConcurrentHashMap)),
                    { group, invites -> group.invites = invites.mapKeys { UUID.fromString(it.key) }.toMutableMap() },
                    { group -> group.invites.mapKeys { it.key.toString() } }
                ).add()
                .build()
        }

        data class GroupMembershipData(
            var roles: Set<UUID> = setOf(),
            var username: String = "",
        ) {
            companion object {
                val CODEC = BuilderCodec.builder(GroupMembershipData::class.java, ::GroupMembershipData)
                    .append(KeyedCodec("Roles", SetCodec(Codec.UUID_STRING, ::HashSet, false)),
                        { data, roles -> data.roles = roles},
                        { it.roles }
                    ).add()
                    .append(KeyedCodec("Username", Codec.STRING),
                        { data, username -> data.username = username },
                        { it.username }
                    ).add()
                .build()
            }
        }

        data class GroupInviteData(
            var inviter: UUID = UUID(0, 0),
            var username: String = "",
        ) {
            companion object {
                val CODEC = BuilderCodec.builder(GroupInviteData::class.java, ::GroupInviteData)
                    .append(KeyedCodec("Inviter", Codec.UUID_STRING),
                        { data, inviter -> data.inviter = inviter },
                        { it.inviter }
                    ).add()
                    .append(KeyedCodec("Username", Codec.STRING),
                        { data, username -> data.username = username },
                        { it.username }
                    ).add()
                    .build()
            }
        }

        data class GroupRole(
            var uuid: UUID = UUID.randomUUID(),
            var name: String = "",
            var permissions: Map<Permission, Boolean> = EnumMap(Permission::class.java),
        ) {
            companion object {

                val CODEC = BuilderCodec.builder(GroupRole::class.java, ::GroupRole)
                    .append(KeyedCodec("UUID", Codec.UUID_STRING),
                        { role, uuid -> role.uuid = uuid},
                        { it.uuid }
                    ).add()
                    .append(KeyedCodec("Name", Codec.STRING),
                        { role, name -> role.name = name },
                        { it.name },
                    ).add()
                    .append(KeyedCodec("Permissions", EnumMapCodec(Permission::class.java, Codec.BOOLEAN)),
                        { role, permissions -> role.permissions = permissions },
                        { it.permissions }
                    ).add()
                    .build()

                // ArrayCodec fills the array by index, so it must be allocated at the requested size
                val CODEC_ARRAY = ArrayCodec<GroupRole>(CODEC) { size -> Array(size) { GroupRole() } }
            }

            // TODO: Create some sort of permission registry
            enum class Permission {
                MANAGE_GROUP
            }
        }
    }
}
