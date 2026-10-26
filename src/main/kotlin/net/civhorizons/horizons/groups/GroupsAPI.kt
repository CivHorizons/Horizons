package net.civhorizons.horizons.groups

import com.hypixel.hytale.server.core.universe.PlayerRef
import net.civhorizons.horizons.groups.GroupsModule.GroupDataStore
import net.civhorizons.horizons.groups.resource.GroupComponent
import net.civhorizons.horizons.groups.resource.GroupComponent.GroupRole.Permission
import net.civhorizons.horizons.util.loadAllUUIDs
import net.civhorizons.horizons.util.save
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// The single entry point other modules use for groups, so it intentionally carries every operation.
@Suppress("TooManyFunctions")
class GroupsAPI(val storage: GroupDataStore) {

    /**
     * Every group in the universe, keyed by UUID, including deleted ones. Each group is also saved to its own
     * file in [storage].
     */
    private val groups = ConcurrentHashMap<UUID, GroupComponent>()

    /**
     * Load every group from disk. Called once while the module sets up.
     */
    fun load() {
        groups.putAll(storage.loadAllUUIDs())
    }

    /**
     * Get a single group by UUID, even if it is deleted. Use this to resolve what a group owns, such as
     * reinforcements, and check [GroupComponent.deleted] before letting anyone act through it.
     */
    fun getGroup(uuid: UUID): GroupComponent? {
        return groups[uuid]
    }

    /**
     * Get a group players can still interact with: one that isn't deleted.
     */
    fun getActiveGroup(uuid: UUID): GroupComponent? = getGroup(uuid)?.takeUnless { it.deleted }

    /**
     * Get an active group only if the player is on it. Group-scoped actions treat
     * "not a member" the same as "doesn't exist" so UUIDs can't be probed.
     */
    fun getMemberGroup(player: PlayerRef, groupUuid: UUID): GroupComponent? =
        getActiveGroup(groupUuid)?.takeIf { it.isMember(player.uuid) }

    /**
     * Get every group in the universe, including deleted ones.
     *
     * Groups are already loaded when the module starts.
     */
    fun getGroups(): Map<UUID, GroupComponent> {
        return groups
    }

    /**
     * Only get active Groups a player is on
     */
    fun getGroups(player: PlayerRef): Map<UUID, GroupComponent> {
        return getGroups().filterValues { !it.deleted && it.isMember(player.uuid) }
    }

    /**
     * Active groups the player has a pending invite to
     */
    fun getInvitesFor(player: PlayerRef): Map<UUID, GroupComponent> {
        return getGroups().filterValues { !it.deleted && it.invites.containsKey(player.uuid) }
    }

    /**
     * Add a new group and save it to disk, or update an existing one.
     * Matching is done via the group's UUID.
     *
     * ```kotlin
     * // Update an existing group
     * group.name = "Updated Group Name"
     * groupsApi.createOrUpdateGroup(group)
     * ```
     */
    fun createOrUpdateGroup(group: GroupComponent): GroupComponent {
        synchronized(group) {
            groups[group.uuid] = group
            storage.save(group.uuid, group)
        }
        return group
    }

    /**
     * Create a new group with the default roles, Owner and Member, and the player as Owner.
     *
     * Returns null if the name is invalid. See [GroupComponent.validateName].
     */
    fun createNewGroup(playerRef: PlayerRef, name: String): GroupComponent? {
        val validName = GroupComponent.validateName(name) ?: return null
        return createOrUpdateGroup(GroupComponent.createDefault(validName, playerRef.uuid, playerRef.username))
    }

    fun invitePlayer(actor: PlayerRef, groupUuid: UUID, target: PlayerRef): GroupActionResult {
        val group = getMemberGroup(actor, groupUuid) ?: return GroupActionResult.Error.NOT_FOUND
        val result = mutate(group) { group.invite(actor.uuid, target.uuid, target.username) }

        if (result == GroupActionResult.Success) {
            target.sendMessage(groupsTranslation("invite.received").param("name", group.name))
        }
        return result
    }

    fun acceptInvite(player: PlayerRef, groupUuid: UUID): GroupActionResult {
        val group = getActiveGroup(groupUuid) ?: return GroupActionResult.Error.NOT_INVITED
        return mutate(group) { group.acceptInvite(player.uuid, player.username) }
    }

    fun declineInvite(player: PlayerRef, groupUuid: UUID): GroupActionResult {
        val group = getActiveGroup(groupUuid) ?: return GroupActionResult.Error.NOT_INVITED
        return mutate(group) { group.declineInvite(player.uuid) }
    }

    fun revokeInvite(actor: PlayerRef, groupUuid: UUID, target: UUID): GroupActionResult {
        val group = getMemberGroup(actor, groupUuid) ?: return GroupActionResult.Error.NOT_FOUND
        return mutate(group) { group.revokeInvite(actor.uuid, target) }
    }

    fun removeMember(actor: PlayerRef, groupUuid: UUID, target: UUID): GroupActionResult {
        val group = getMemberGroup(actor, groupUuid) ?: return GroupActionResult.Error.NOT_FOUND
        return mutate(group) { group.removeMember(actor.uuid, target) }
    }

    fun setMemberRole(
                actor: PlayerRef,
        groupUuid: UUID,
        target: UUID,
        role: UUID,
    ): GroupActionResult {
        val group = getMemberGroup(actor, groupUuid) ?: return GroupActionResult.Error.NOT_FOUND
        return mutate(group) { group.setRole(actor.uuid, target, role) }
    }

    fun createRole(actor: PlayerRef, groupUuid: UUID, name: String): GroupActionResult {
        val group = getMemberGroup(actor, groupUuid) ?: return GroupActionResult.Error.NOT_FOUND
        return mutate(group) { group.createRole(actor.uuid, name) }
    }

    fun renameRole(
                actor: PlayerRef,
        groupUuid: UUID,
        role: UUID,
        name: String,
    ): GroupActionResult {
        val group = getMemberGroup(actor, groupUuid) ?: return GroupActionResult.Error.NOT_FOUND
        return mutate(group) { group.renameRole(actor.uuid, role, name) }
    }

    fun setRolePermission(
                actor: PlayerRef,
        groupUuid: UUID,
        role: UUID,
        permission: Permission,
        granted: Boolean,
    ): GroupActionResult {
        val group = getMemberGroup(actor, groupUuid) ?: return GroupActionResult.Error.NOT_FOUND
        return mutate(group) { group.setRolePermission(actor.uuid, role, permission, granted) }
    }

    fun moveRole(actor: PlayerRef, groupUuid: UUID, role: UUID, up: Boolean): GroupActionResult {
        val group = getMemberGroup(actor, groupUuid) ?: return GroupActionResult.Error.NOT_FOUND
        return mutate(group) { group.moveRole(actor.uuid, role, up) }
    }

    fun deleteRole(actor: PlayerRef, groupUuid: UUID, role: UUID): GroupActionResult {
        val group = getMemberGroup(actor, groupUuid) ?: return GroupActionResult.Error.NOT_FOUND
        return mutate(group) { group.deleteRole(actor.uuid, role) }
    }

    /**
     * Mark a group deleted. It stays loaded so whatever it owns keeps resolving, but nobody can interact with it.
     * See [GroupComponent.deleted].
     */
    fun deleteGroup(actor: PlayerRef, groupUuid: UUID): GroupActionResult {
        val group = getMemberGroup(actor, groupUuid) ?: return GroupActionResult.Error.NOT_FOUND
        return mutate(group) {
            group.canDelete(actor.uuid).also { if (it == GroupActionResult.Success) group.deleted = true }
        }
    }

    /**
     * Store the player's current username on every group they belong to, so offline members display by name.
     */
    fun refreshUsername(player: PlayerRef) {
        getGroups(player).values
            .filter { it.membership[player.uuid]?.username != player.username }
            .forEach { group ->
                mutate(group) {
                    val membership = group.membership[player.uuid] ?: return@mutate GroupActionResult.Error.NOT_MEMBER
                    group.membership[player.uuid] = membership.copy(username = player.username)
                    GroupActionResult.Success
                }
            }
    }

    /**
     * Apply [change] to the group while holding its lock, then save it if the change succeeded. Another world
     * may have deleted or replaced the group since it was looked up, so those groups aren't changed.
     */
    private fun mutate(
        group: GroupComponent,
        change: () -> GroupActionResult,
    ): GroupActionResult = synchronized(group) {
        if (group.deleted || groups[group.uuid] !== group) return GroupActionResult.Error.NOT_FOUND

        change().also { if (it == GroupActionResult.Success) storage.save(group.uuid, group) }
    }
}
