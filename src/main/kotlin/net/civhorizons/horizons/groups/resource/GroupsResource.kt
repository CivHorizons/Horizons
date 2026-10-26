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
import net.civhorizons.horizons.groups.GroupResult
import java.util.EnumMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GroupsResource(
    val groups: ConcurrentHashMap<UUID, GroupComponent> = ConcurrentHashMap<UUID, GroupComponent>()
) : Resource<EntityStore> {

    override fun clone(): Resource<EntityStore> {
        return GroupsResource(ConcurrentHashMap(groups))
    }

    // Guard clauses read as the rule list for each operation, and the group owns all of its rules.
    @Suppress("ReturnCount", "TooManyFunctions")
    class GroupComponent(
        var uuid: UUID = UUID.randomUUID(),
        var name: String = "",
        var roles: Array<GroupRole> = arrayOf(),
        var membership: MutableMap<UUID, GroupMembershipData> = mutableMapOf(),
        var invites: MutableMap<UUID, GroupInviteData> = mutableMapOf(),
    ) {

        /**
         * Return true if _any_ of the player's roles grants the permission
         */
        fun hasPermission(player: UUID, permission: GroupRole.Permission): Boolean {
            val activeRoleUUIDs = membership[player]?.roles ?: return false
            return roles.any { activeRoleUUIDs.contains(it.uuid) && it.permissions[permission] == true }
        }

        fun playerHasPermission(player: PlayerRef, permission: GroupRole.Permission): Boolean =
            hasPermission(player.uuid, permission)

        /**
         * Returns true if the player is any member of a group at all, even if they don't have a role assigned
         */
        fun isMember(player: UUID): Boolean = membership.containsKey(player)

        fun isPlayerOnGroup(player: PlayerRef): Boolean = isMember(player.uuid)

        /**
         * The member's role. Members hold exactly one role.
         */
        fun roleOf(member: UUID): GroupRole? {
            val roleUUIDs = membership[member]?.roles ?: return null
            return roles.firstOrNull { roleUUIDs.contains(it.uuid) }
        }

        /**
         * Display name for a member: stored username, then online username, then UUID.
         */
        fun memberName(member: UUID): String =
            membership[member]?.username?.takeIf { it.isNotEmpty() }
                ?: Universe.get().getPlayer(member)?.username
                ?: member.toString()

        fun invite(actor: UUID, target: UUID, username: String): GroupResult {
            if (!canManage(actor)) return GroupResult.Error.NO_PERMISSION
            if (isMember(target)) return GroupResult.Error.ALREADY_MEMBER
            if (invites.containsKey(target)) return GroupResult.Error.ALREADY_INVITED

            invites[target] = GroupInviteData(inviter = actor, username = username)
            return GroupResult.Success
        }

        fun acceptInvite(player: UUID, username: String): GroupResult {
            if (!invites.containsKey(player)) return GroupResult.Error.NOT_INVITED
            val memberRole = roles.find { it.name == DEFAULT_MEMBER_ROLE } ?: return GroupResult.Error.ROLE_NOT_FOUND

            invites.remove(player)
            membership[player] = GroupMembershipData(roles = setOf(memberRole.uuid), username = username)
            return GroupResult.Success
        }

        fun declineInvite(player: UUID): GroupResult =
            if (invites.remove(player) == null) GroupResult.Error.NOT_INVITED else GroupResult.Success

        fun revokeInvite(actor: UUID, target: UUID): GroupResult {
            if (!canManage(actor)) return GroupResult.Error.NO_PERMISSION
            return if (invites.remove(target) == null) GroupResult.Error.NOT_INVITED else GroupResult.Success
        }

        fun removeMember(actor: UUID, target: UUID): GroupResult {
            if (!canManage(actor)) return GroupResult.Error.NO_PERMISSION
            if (!isMember(target)) return GroupResult.Error.NOT_MEMBER
            if (isLastOwner(target)) return GroupResult.Error.LAST_OWNER

            membership.remove(target)
            return GroupResult.Success
        }

        fun setRole(actor: UUID, target: UUID, role: UUID): GroupResult {
            if (!canManage(actor)) return GroupResult.Error.NO_PERMISSION
            val current = membership[target] ?: return GroupResult.Error.NOT_MEMBER
            val newRole = roles.find { it.uuid == role } ?: return GroupResult.Error.ROLE_NOT_FOUND
            val keepsOwnership = newRole.permissions[GroupRole.Permission.MANAGE_GROUP] == true
            if (!keepsOwnership && isLastOwner(target)) return GroupResult.Error.LAST_OWNER

            membership[target] = current.copy(roles = setOf(newRole.uuid))
            return GroupResult.Success
        }

        fun canDelete(actor: UUID): GroupResult =
            if (canManage(actor)) GroupResult.Success else GroupResult.Error.NO_PERMISSION

        private fun canManage(player: UUID) = hasPermission(player, GroupRole.Permission.MANAGE_GROUP)

        private fun isLastOwner(player: UUID) =
            canManage(player) && membership.keys.count { canManage(it) } <= 1

        companion object {
            const val DEFAULT_OWNER_ROLE = "Owner"
            const val DEFAULT_MEMBER_ROLE = "Member"
            const val MAX_NAME_LENGTH = 32

            /**
             * Returns the trimmed name if it is a valid group name, or null.
             */
            fun validateName(raw: String): String? = raw.trim().takeIf { it.length in 1..MAX_NAME_LENGTH }

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
