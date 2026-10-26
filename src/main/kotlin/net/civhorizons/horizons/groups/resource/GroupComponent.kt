package net.civhorizons.horizons.groups.resource

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.codec.codecs.EnumCodec
import com.hypixel.hytale.codec.codecs.array.ArrayCodec
import com.hypixel.hytale.codec.codecs.map.EnumMapCodec
import com.hypixel.hytale.codec.codecs.map.MapCodec
import com.hypixel.hytale.codec.codecs.set.SetCodec
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.Universe
import net.civhorizons.horizons.groups.GroupResult
import java.util.EnumMap
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * A group, shared by every world in the universe. Worlds tick on their own threads, so GroupsAPI serializes
 * changes per group, and the maps are concurrent so pages can read while another world writes.
 */
// Guard clauses read as the rule list for each operation, and the group owns all of its rules.
@Suppress("ReturnCount", "TooManyFunctions")
class GroupComponent(
    var uuid: UUID = UUID.randomUUID(),
    var name: String = "",
    var roles: Array<GroupRole> = arrayOf(),
    var membership: MutableMap<UUID, GroupMembershipData> = ConcurrentHashMap(),
    var invites: MutableMap<UUID, GroupInviteData> = ConcurrentHashMap(),
) {

    /**
     * Return true if the player's role grants the permission. Owners hold every permission.
     */
    fun hasPermission(player: UUID, permission: GroupRole.Permission): Boolean {
        val role = roleOf(player) ?: return false
        return role.kind == GroupRole.Kind.OWNER || role.permissions[permission] == true
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

    fun findRole(role: UUID): GroupRole? = roles.find { it.uuid == role }

    fun roleOfKind(kind: GroupRole.Kind): GroupRole? = roles.find { it.kind == kind }

    /**
     * Position in the role order, 0 being the highest. -1 if the role doesn't exist.
     */
    fun rankOf(role: UUID): Int = roles.indexOfFirst { it.uuid == role }

    /**
     * True if [actor] may act on [role]: Owners outrank every role, everyone else only the roles below their own.
     */
    fun outranks(actor: UUID, role: UUID): Boolean {
        val actorRole = roleOf(actor) ?: return false
        if (actorRole.kind == GroupRole.Kind.OWNER) return true
        val target = rankOf(role)
        return target >= 0 && rankOf(actorRole.uuid) < target
    }

    /**
     * True if [actor] may act on [member]. Nobody acts on themselves, and since only an Owner outranks
     * an Owner, a group can never lose its last Owner.
     */
    fun outranksMember(actor: UUID, member: UUID): Boolean {
        if (actor == member) return false
        val role = roleOf(member) ?: return false
        return outranks(actor, role.uuid)
    }

    fun membersWithRole(role: UUID): Int = membership.values.count { it.roles.contains(role) }

    /**
     * Display name for a member: stored username, then online username, then UUID.
     */
    fun memberName(member: UUID): String =
        membership[member]?.username?.takeIf { it.isNotEmpty() }
            ?: Universe.get().getPlayer(member)?.username
            ?: member.toString()

    fun invite(actor: UUID, target: UUID, username: String): GroupResult {
        if (!hasPermission(actor, GroupRole.Permission.INVITE_MEMBERS)) return GroupResult.Error.NO_PERMISSION
        if (isMember(target)) return GroupResult.Error.ALREADY_MEMBER
        if (invites.containsKey(target)) return GroupResult.Error.ALREADY_INVITED

        invites[target] = GroupInviteData(inviter = actor, username = username)
        return GroupResult.Success
    }

    fun acceptInvite(player: UUID, username: String): GroupResult {
        if (!invites.containsKey(player)) return GroupResult.Error.NOT_INVITED
        val memberRole = roleOfKind(GroupRole.Kind.MEMBER) ?: return GroupResult.Error.ROLE_NOT_FOUND

        invites.remove(player)
        membership[player] = GroupMembershipData(roles = setOf(memberRole.uuid), username = username)
        return GroupResult.Success
    }

    fun declineInvite(player: UUID): GroupResult =
        if (invites.remove(player) == null) GroupResult.Error.NOT_INVITED else GroupResult.Success

    fun revokeInvite(actor: UUID, target: UUID): GroupResult {
        if (!hasPermission(actor, GroupRole.Permission.INVITE_MEMBERS)) return GroupResult.Error.NO_PERMISSION
        return if (invites.remove(target) == null) GroupResult.Error.NOT_INVITED else GroupResult.Success
    }

    fun removeMember(actor: UUID, target: UUID): GroupResult {
        if (!hasPermission(actor, GroupRole.Permission.REMOVE_MEMBERS)) return GroupResult.Error.NO_PERMISSION
        if (!isMember(target)) return GroupResult.Error.NOT_MEMBER
        if (!outranksMember(actor, target)) return GroupResult.Error.NO_PERMISSION

        membership.remove(target)
        return GroupResult.Success
    }

    fun setRole(actor: UUID, target: UUID, role: UUID): GroupResult {
        if (!hasPermission(actor, GroupRole.Permission.ASSIGN_ROLES)) return GroupResult.Error.NO_PERMISSION
        val current = membership[target] ?: return GroupResult.Error.NOT_MEMBER
        val newRole = findRole(role) ?: return GroupResult.Error.ROLE_NOT_FOUND
        // Both ends must be below the actor, so nobody can promote anyone to or above their own rank
        if (!outranksMember(actor, target) || !outranks(actor, newRole.uuid)) return GroupResult.Error.NO_PERMISSION

        membership[target] = current.copy(roles = setOf(newRole.uuid))
        return GroupResult.Success
    }

    fun canDelete(actor: UUID): GroupResult =
        if (hasPermission(actor, GroupRole.Permission.DELETE_GROUP)) GroupResult.Success
        else GroupResult.Error.NO_PERMISSION

    fun canManageRole(actor: UUID, role: UUID): Boolean =
        hasPermission(actor, GroupRole.Permission.MANAGE_ROLES) && outranks(actor, role)

    fun createRole(actor: UUID, name: String): GroupResult {
        if (!hasPermission(actor, GroupRole.Permission.MANAGE_ROLES)) return GroupResult.Error.NO_PERMISSION
        val validName = validateName(name) ?: return GroupResult.Error.INVALID_ROLE_NAME
        if (isRoleNameTaken(validName, except = null)) return GroupResult.Error.ROLE_NAME_TAKEN

        // New roles start at the lowest custom rank, just above Member
        roles = roles.toMutableList().apply { add(lastIndex, GroupRole(name = validName)) }.toTypedArray()
        return GroupResult.Success
    }

    fun renameRole(actor: UUID, role: UUID, name: String): GroupResult {
        val target = findRole(role) ?: return GroupResult.Error.ROLE_NOT_FOUND
        if (!canManageRole(actor, role)) return GroupResult.Error.NO_PERMISSION
        val validName = validateName(name) ?: return GroupResult.Error.INVALID_ROLE_NAME
        if (isRoleNameTaken(validName, except = role)) return GroupResult.Error.ROLE_NAME_TAKEN

        target.name = validName
        return GroupResult.Success
    }

    fun setRolePermission(
        actor: UUID,
        role: UUID,
        permission: GroupRole.Permission,
        granted: Boolean,
    ): GroupResult {
        val target = findRole(role) ?: return GroupResult.Error.ROLE_NOT_FOUND
        if (target.kind == GroupRole.Kind.OWNER) return GroupResult.Error.ROLE_PROTECTED
        if (!canManageRole(actor, role)) return GroupResult.Error.NO_PERMISSION
        // Nobody can hand out a permission they don't hold themselves
        if (!hasPermission(actor, permission)) return GroupResult.Error.NO_PERMISSION

        target.permissions = EnumMap<GroupRole.Permission, Boolean>(GroupRole.Permission::class.java).apply {
            putAll(target.permissions)
            put(permission, granted)
        }
        return GroupResult.Success
    }

    fun canMoveRole(actor: UUID, role: UUID, up: Boolean): Boolean =
        checkMove(actor, role, up) == GroupResult.Success

    /**
     * Swap a custom role with its neighbour above or below.
     */
    fun moveRole(actor: UUID, role: UUID, up: Boolean): GroupResult {
        val check = checkMove(actor, role, up)
        if (check != GroupResult.Success) return check

        val from = rankOf(role)
        val to = if (up) from - 1 else from + 1
        roles = roles.copyOf().also {
            it[from] = roles[to]
            it[to] = roles[from]
        }
        return GroupResult.Success
    }

    fun deleteRole(actor: UUID, role: UUID): GroupResult {
        val target = findRole(role) ?: return GroupResult.Error.ROLE_NOT_FOUND
        if (target.kind != GroupRole.Kind.CUSTOM) return GroupResult.Error.ROLE_PROTECTED
        if (!canManageRole(actor, role)) return GroupResult.Error.NO_PERMISSION
        val fallback = roleOfKind(GroupRole.Kind.MEMBER) ?: return GroupResult.Error.ROLE_NOT_FOUND

        membership.replaceAll { _, data ->
            if (data.roles.contains(role)) data.copy(roles = setOf(fallback.uuid)) else data
        }
        roles = roles.filter { it.uuid != role }.toTypedArray()
        return GroupResult.Success
    }

    private fun checkMove(actor: UUID, role: UUID, up: Boolean): GroupResult {
        val target = findRole(role) ?: return GroupResult.Error.ROLE_NOT_FOUND
        if (target.kind != GroupRole.Kind.CUSTOM) return GroupResult.Error.ROLE_PROTECTED
        if (!canManageRole(actor, role)) return GroupResult.Error.NO_PERMISSION

        // Custom roles always sit between Owner and Member, so the neighbour always exists
        val neighbour = roles[rankOf(role) + if (up) -1 else 1]
        if (neighbour.kind != GroupRole.Kind.CUSTOM) return GroupResult.Error.ROLE_PROTECTED
        // Swapping with a role the actor doesn't outrank would lift this role to the actor's own rank
        if (!outranks(actor, neighbour.uuid)) return GroupResult.Error.NO_PERMISSION
        return GroupResult.Success
    }

    private fun isRoleNameTaken(name: String, except: UUID?): Boolean =
        roles.any { it.uuid != except && it.name.equals(name, ignoreCase = true) }

    companion object {
        const val DEFAULT_OWNER_ROLE = "Owner"
        const val DEFAULT_ADMIN_ROLE = "Admin"
        const val DEFAULT_MEMBER_ROLE = "Member"
        const val MAX_NAME_LENGTH = 32

        /**
         * Returns the trimmed name if it is a valid group or role name, or null.
         */
        fun validateName(raw: String): String? = raw.trim().takeIf { it.length in 1..MAX_NAME_LENGTH }

        /**
         * Create a group with the default roles, Owner, Admin, and Member, and the creator as Owner.
         */
        fun createDefault(name: String, creator: UUID, creatorName: String): GroupComponent {
            val ownerRole = GroupRole(
                name = DEFAULT_OWNER_ROLE,
                permissions = GroupRole.Permission.entries.associateWith { true },
                kind = GroupRole.Kind.OWNER,
            )
            val adminRole = GroupRole(
                name = DEFAULT_ADMIN_ROLE,
                permissions = mapOf(
                    GroupRole.Permission.INVITE_MEMBERS to true,
                    GroupRole.Permission.REMOVE_MEMBERS to true,
                    GroupRole.Permission.ASSIGN_ROLES to true,
                ),
            )
            val memberRole = GroupRole(name = DEFAULT_MEMBER_ROLE, kind = GroupRole.Kind.MEMBER)

            return GroupComponent(
                name = name,
                roles = arrayOf(ownerRole, adminRole, memberRole),
                membership = ConcurrentHashMap(mapOf(
                    creator to GroupMembershipData(roles = setOf(ownerRole.uuid), username = creatorName)
                ))
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
                { group, membership ->
                    group.membership = ConcurrentHashMap(membership.mapKeys { UUID.fromString(it.key) })
                },
                { group -> group.membership.mapKeys { it.key.toString() } }
            ).add()
            .append(KeyedCodec("Invites", MapCodec(GroupInviteData.CODEC, ::ConcurrentHashMap)),
                { group, invites -> group.invites = ConcurrentHashMap(invites.mapKeys { UUID.fromString(it.key) }) },
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
        var kind: Kind = Kind.CUSTOM,
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
                .append(KeyedCodec("Kind", EnumCodec(Kind::class.java)),
                    { role, kind -> role.kind = kind },
                    { it.kind }
                ).add()
                .build()

            // ArrayCodec fills the array by index, so it must be allocated at the requested size
            val CODEC_ARRAY = ArrayCodec<GroupRole>(CODEC) { size -> Array(size) { GroupRole() } }
        }

        /**
         * Owner and Member are protected: they always exist, Owner first and Member last.
         */
        enum class Kind {
            OWNER,
            MEMBER,
            CUSTOM,
        }

        // TODO: Create some sort of permission registry
        enum class Permission {
            INVITE_MEMBERS,
            REMOVE_MEMBERS,
            ASSIGN_ROLES,
            MANAGE_ROLES,
            DELETE_GROUP,
        }
    }
}
