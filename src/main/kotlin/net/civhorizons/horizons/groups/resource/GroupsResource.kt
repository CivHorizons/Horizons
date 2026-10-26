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
        var membership: MutableMap<UUID, GroupMembershipData> = mutableMapOf()
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

            return activeRoles.any { it.permissions.contains(permission) }
        }

        /**
         * Returns true if the player is any member of a group at all, even if they don't have a role assigned
         */
        fun isPlayerOnGroup(player: PlayerRef): Boolean {
            return membership[player.uuid] != null
        }

        companion object {
            fun getType() = Horizons.instance.groupsModule.groupsResourceType

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
                .build()
        }

        data class GroupMembershipData(
            var roles: Set<UUID> = setOf(),
        ) {
            companion object {
                val CODEC = BuilderCodec.builder(GroupMembershipData::class.java, ::GroupMembershipData)
                    .append(KeyedCodec("Roles", SetCodec(Codec.UUID_STRING, ::HashSet, false)),
                        { data, roles -> data.roles = roles},
                        { it.roles }
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
