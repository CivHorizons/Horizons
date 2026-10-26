package net.civhorizons.horizons.groups

import com.hypixel.hytale.server.core.universe.PlayerRef
import net.civhorizons.horizons.groups.GroupsModule.GroupDataStore
import net.civhorizons.horizons.groups.resource.GroupComponent
import net.civhorizons.horizons.groups.resource.GroupComponent.GroupRole.Permission
import net.civhorizons.horizons.util.loadAllUUIDs
import net.civhorizons.horizons.util.remove
import net.civhorizons.horizons.util.save
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// The single entry point other modules use for groups, so it intentionally carries every operation.
@Suppress("TooManyFunctions")
class GroupsAPI(val storage: GroupDataStore) {

    /**
     * Every group in the universe, keyed by UUID. Each group is also saved to its own file in [storage].
     */
    private val groups = ConcurrentHashMap<UUID, GroupComponent>()

    /**
     * Load every group from disk. Called once while the module sets up.
     */
    fun load() {
        groups.putAll(storage.loadAllUUIDs())
    }

    /**
     * Get a single group by UUID
     */
    fun getGroup(uuid: UUID): GroupComponent? {
        return groups[uuid]
    }

    /**
     * Get a group only if the player is on it. Group-scoped actions treat
     * "not a member" the same as "doesn't exist" so UUIDs can't be probed.
     */
    fun getMemberGroup(player: PlayerRef, groupUuid: UUID): GroupComponent? =
        getGroup(groupUuid)?.takeIf { it.isMember(player.uuid) }

    /**
     * Get every group in the universe.
     *
     * Groups are already loaded when the module starts.
     */
    fun getGroups(): Map<UUID, GroupComponent> {
        return groups
    }

    /**
     * Only get Groups a player is on
     */
    fun getGroups(player: PlayerRef): Map<UUID, GroupComponent> {
        return getGroups().filterValues { it.isMember(player.uuid) }
    }

    /**
     * Groups the player has a pending invite to
     */
    fun getInvitesFor(player: PlayerRef): Map<UUID, GroupComponent> {
        return getGroups().filterValues { it.invites.containsKey(player.uuid) }
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

    fun invitePlayer(actor: PlayerRef, groupUuid: UUID, target: PlayerRef): GroupResult {
        val group = getMemberGroup(actor, groupUuid) ?: return GroupResult.Error.NOT_FOUND
        val result = mutate(group) { group.invite(actor.uuid, target.uuid, target.username) }

        if (result == GroupResult.Success) {
            target.sendMessage(groupsText("invite.received").param("name", group.name))
        }
        return result
    }

    fun acceptInvite(player: PlayerRef, groupUuid: UUID): GroupResult {
        val group = getGroup(groupUuid) ?: return GroupResult.Error.NOT_INVITED
        return mutate(group) { group.acceptInvite(player.uuid, player.username) }
    }

    fun declineInvite(player: PlayerRef, groupUuid: UUID): GroupResult {
        val group = getGroup(groupUuid) ?: return GroupResult.Error.NOT_INVITED
        return mutate(group) { group.declineInvite(player.uuid) }
    }

    fun revokeInvite(actor: PlayerRef, groupUuid: UUID, target: UUID): GroupResult {
        val group = getMemberGroup(actor, groupUuid) ?: return GroupResult.Error.NOT_FOUND
        return mutate(group) { group.revokeInvite(actor.uuid, target) }
    }

    fun removeMember(actor: PlayerRef, groupUuid: UUID, target: UUID): GroupResult {
        val group = getMemberGroup(actor, groupUuid) ?: return GroupResult.Error.NOT_FOUND
        return mutate(group) { group.removeMember(actor.uuid, target) }
    }

    fun setMemberRole(
                actor: PlayerRef,
        groupUuid: UUID,
        target: UUID,
        role: UUID,
    ): GroupResult {
        val group = getMemberGroup(actor, groupUuid) ?: return GroupResult.Error.NOT_FOUND
        return mutate(group) { group.setRole(actor.uuid, target, role) }
    }

    fun createRole(actor: PlayerRef, groupUuid: UUID, name: String): GroupResult {
        val group = getMemberGroup(actor, groupUuid) ?: return GroupResult.Error.NOT_FOUND
        return mutate(group) { group.createRole(actor.uuid, name) }
    }

    fun renameRole(
                actor: PlayerRef,
        groupUuid: UUID,
        role: UUID,
        name: String,
    ): GroupResult {
        val group = getMemberGroup(actor, groupUuid) ?: return GroupResult.Error.NOT_FOUND
        return mutate(group) { group.renameRole(actor.uuid, role, name) }
    }

    fun setRolePermission(
                actor: PlayerRef,
        groupUuid: UUID,
        role: UUID,
        permission: Permission,
        granted: Boolean,
    ): GroupResult {
        val group = getMemberGroup(actor, groupUuid) ?: return GroupResult.Error.NOT_FOUND
        return mutate(group) { group.setRolePermission(actor.uuid, role, permission, granted) }
    }

    fun moveRole(actor: PlayerRef, groupUuid: UUID, role: UUID, up: Boolean): GroupResult {
        val group = getMemberGroup(actor, groupUuid) ?: return GroupResult.Error.NOT_FOUND
        return mutate(group) { group.moveRole(actor.uuid, role, up) }
    }

    fun deleteRole(actor: PlayerRef, groupUuid: UUID, role: UUID): GroupResult {
        val group = getMemberGroup(actor, groupUuid) ?: return GroupResult.Error.NOT_FOUND
        return mutate(group) { group.deleteRole(actor.uuid, role) }
    }

    /**
     * Remove a group from memory and from disk.
     *
     * TODO: Turn this into a soft delete. See https://github.com/CivHorizons/Horizons/issues/20
     */
    fun deleteGroup(actor: PlayerRef, groupUuid: UUID): GroupResult {
        val group = getMemberGroup(actor, groupUuid) ?: return GroupResult.Error.NOT_FOUND
        return synchronized(group) {
            // Another world may have deleted it since it was looked up
            if (groups[group.uuid] !== group) {
                GroupResult.Error.NOT_FOUND
            } else {
                group.canDelete(actor.uuid).also {
                    if (it == GroupResult.Success) {
                        groups.remove(group.uuid)
                        storage.remove(group.uuid)
                    }
                }
            }
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
                    val membership = group.membership[player.uuid] ?: return@mutate GroupResult.Error.NOT_MEMBER
                    group.membership[player.uuid] = membership.copy(username = player.username)
                    GroupResult.Success
                }
            }
    }

    /**
     * Apply [change] to the group while holding its lock, then save it if the change succeeded. Another world
     * may have deleted the group since it was looked up, so a group that is no longer registered isn't saved.
     */
    private fun mutate(group: GroupComponent, change: () -> GroupResult): GroupResult = synchronized(group) {
        if (groups[group.uuid] !== group) return GroupResult.Error.NOT_FOUND

        change().also { if (it == GroupResult.Success) storage.save(group.uuid, group) }
    }
}
