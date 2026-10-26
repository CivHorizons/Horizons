package net.civhorizons.horizons

import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.civhorizons.horizons.groups.GroupResult
import net.civhorizons.horizons.groups.GroupsModule.GroupDataStore
import net.civhorizons.horizons.groups.groupsText
import net.civhorizons.horizons.groups.resource.GroupsResource.GroupComponent
import net.civhorizons.horizons.util.remove
import net.civhorizons.horizons.util.save
import java.util.UUID
import kotlin.collections.set

// The single entry point other modules use for groups, so it intentionally carries every operation.
@Suppress("TooManyFunctions")
class GroupsAPI(val storage: GroupDataStore) {

    /**
     * Get a single group by UUID
     */
    fun getGroup(store: Store<EntityStore>, uuid: UUID): GroupComponent? {
        val resource = store.getResource(GroupComponent.getType())

        return resource.groups[uuid]
    }

    /**
     * Get a group only if the player is on it. Group-scoped actions treat
     * "not a member" the same as "doesn't exist" so UUIDs can't be probed.
     */
    fun getMemberGroup(store: Store<EntityStore>, player: PlayerRef, groupUuid: UUID): GroupComponent? =
        getGroup(store, groupUuid)?.takeIf { it.isMember(player.uuid) }

    /**
     * Get all groups from the store.
     *
     * Groups are already loaded into the store when the module starts.
     */
    fun getGroups(store: Store<EntityStore>): Map<UUID, GroupComponent> {
        val resource = store.getResource(GroupComponent.getType())
        return resource.groups
    }

    /**
     * Only get Groups a player is on
     */
    fun getGroups(store: Store<EntityStore>, player: PlayerRef): Map<UUID, GroupComponent> {
        return getGroups(store).filterValues { it.isMember(player.uuid) }
    }

    /**
     * Groups the player has a pending invite to
     */
    fun getInvitesFor(store: Store<EntityStore>, player: PlayerRef): Map<UUID, GroupComponent> {
        return getGroups(store).filterValues { it.invites.containsKey(player.uuid) }
    }

    /**
     * Add a new group to the store and save it to disk, or update an existing one.
     * Matching is done via the group's UUID.
     *
     * ```kotlin
     * // Update an existing group
     * group.name = "Updated Group Name"
     * groupsApi.createOrUpdateGroup(store, group)
     * ```
     */
    fun createOrUpdateGroup(store: Store<EntityStore>, group: GroupComponent): GroupComponent {
        val resource = store.getResource(GroupComponent.getType())

        resource.groups[group.uuid] = group
        storage.save(group.uuid, group)

        return group
    }

    /**
     * Create a new group with the default roles, Owner and Member, and the player as Owner.
     *
     * Returns null if the name is invalid. See [GroupComponent.validateName].
     */
    fun createNewGroup(store: Store<EntityStore>, playerRef: PlayerRef, name: String): GroupComponent? {
        val validName = GroupComponent.validateName(name) ?: return null
        return createOrUpdateGroup(store, GroupComponent.createDefault(validName, playerRef.uuid, playerRef.username))
    }

    fun invitePlayer(store: Store<EntityStore>, actor: PlayerRef, groupUuid: UUID, target: PlayerRef): GroupResult {
        val group = getMemberGroup(store, actor, groupUuid) ?: return GroupResult.Error.NOT_FOUND
        val result = saveIfSuccess(store, group, group.invite(actor.uuid, target.uuid, target.username))

        if (result == GroupResult.Success) {
            target.sendMessage(groupsText("invite.received").param("name", group.name))
        }
        return result
    }

    fun acceptInvite(store: Store<EntityStore>, player: PlayerRef, groupUuid: UUID): GroupResult {
        val group = getGroup(store, groupUuid) ?: return GroupResult.Error.NOT_INVITED
        return saveIfSuccess(store, group, group.acceptInvite(player.uuid, player.username))
    }

    fun declineInvite(store: Store<EntityStore>, player: PlayerRef, groupUuid: UUID): GroupResult {
        val group = getGroup(store, groupUuid) ?: return GroupResult.Error.NOT_INVITED
        return saveIfSuccess(store, group, group.declineInvite(player.uuid))
    }

    fun revokeInvite(store: Store<EntityStore>, actor: PlayerRef, groupUuid: UUID, target: UUID): GroupResult {
        val group = getMemberGroup(store, actor, groupUuid) ?: return GroupResult.Error.NOT_FOUND
        return saveIfSuccess(store, group, group.revokeInvite(actor.uuid, target))
    }

    fun removeMember(store: Store<EntityStore>, actor: PlayerRef, groupUuid: UUID, target: UUID): GroupResult {
        val group = getMemberGroup(store, actor, groupUuid) ?: return GroupResult.Error.NOT_FOUND
        return saveIfSuccess(store, group, group.removeMember(actor.uuid, target))
    }

    fun setMemberRole(
        store: Store<EntityStore>,
        actor: PlayerRef,
        groupUuid: UUID,
        target: UUID,
        role: UUID,
    ): GroupResult {
        val group = getMemberGroup(store, actor, groupUuid) ?: return GroupResult.Error.NOT_FOUND
        return saveIfSuccess(store, group, group.setRole(actor.uuid, target, role))
    }

    /**
     * Remove a group from the store and from disk.
     *
     * TODO: Turn this into a soft delete. See https://github.com/CivHorizons/Horizons/issues/20
     */
    fun deleteGroup(store: Store<EntityStore>, actor: PlayerRef, groupUuid: UUID): GroupResult {
        val group = getMemberGroup(store, actor, groupUuid) ?: return GroupResult.Error.NOT_FOUND
        val result = group.canDelete(actor.uuid)

        if (result == GroupResult.Success) {
            store.getResource(GroupComponent.getType()).groups.remove(group.uuid)
            storage.remove(group.uuid)
        }
        return result
    }

    /**
     * Store the player's current username on every group they belong to, so offline members display by name.
     */
    fun refreshUsername(store: Store<EntityStore>, player: PlayerRef) {
        getGroups(store, player).values.forEach { group ->
            val membership = group.membership.getValue(player.uuid)
            if (membership.username != player.username) {
                group.membership[player.uuid] = membership.copy(username = player.username)
                createOrUpdateGroup(store, group)
            }
        }
    }

    private fun saveIfSuccess(store: Store<EntityStore>, group: GroupComponent, result: GroupResult): GroupResult {
        if (result == GroupResult.Success) {
            createOrUpdateGroup(store, group)
        }
        return result
    }
}
