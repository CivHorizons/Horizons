package net.civhorizons.horizons

import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.civhorizons.horizons.groups.GroupsModule.GroupDataStore
import net.civhorizons.horizons.groups.resource.GroupsResource
import net.civhorizons.horizons.util.remove
import net.civhorizons.horizons.util.save
import java.util.UUID
import kotlin.collections.set

class GroupsAPI(val storage: GroupDataStore) {

    /**
     * Get a single group by UUID
     */
    fun getGroup(store: Store<EntityStore>, uuid: UUID): GroupsResource.GroupComponent? {
        val resource = store.getResource(GroupsResource.GroupComponent.getType())

        return resource.groups[uuid]
    }

    /**
     * Get all groups from the store.
     *
     * Groups are already loaded into the store when the module starts.
     */
    fun getGroups(store: Store<EntityStore>): Map<UUID, GroupsResource.GroupComponent> {
        val resource = store.getResource(GroupsResource.GroupComponent.getType())
        return resource.groups
    }

    /**
     * Only get Groups a player is on
     */
    fun getGroups(store: Store<EntityStore>, player: PlayerRef): Map<UUID, GroupsResource.GroupComponent> {
        return getGroups(store).filterValues { it.membership.containsKey(player.uuid) }
    }

    /**
     * Add a new group to the store and save it to disk, or update an existing one.
     * Matching is done via the group's UUID.
     *
     * ```kotlin
     * // Create a new group
     * groupsApi.createOrUpdateGroup(store, GroupsResource.GroupComponent(
     *  name = "New Group"
     * )
     *
     * // Update an existing group
     * group.name = "Updated Group Name"
     * groupsApi.createOrUpdateGroup(store, group)
     * ```
     */
    fun createOrUpdateGroup(store: Store<EntityStore>, group: GroupsResource.GroupComponent): GroupsResource.GroupComponent {
        val resource = store.getResource(GroupsResource.GroupComponent.getType())

        resource.groups[group.uuid] = group
        storage.save(group.uuid, group)

        return group
    }

    /**
     * Create a new group with a set of default options.
     *
     * Two roles, Owner and Member.
     */
    fun createNewGroup(store: Store<EntityStore>, playerRef: PlayerRef, name: String): GroupsResource.GroupComponent {
        val ownerRoleUUID = UUID.randomUUID()

        val group = GroupsResource.GroupComponent(
            name = name,
            // Create two basic roles, Owner and Member
            roles = arrayOf(GroupsResource.GroupComponent.GroupRole(
                uuid = ownerRoleUUID,
                name = "Owner",
                permissions = mapOf(GroupsResource.GroupComponent.GroupRole.Permission.MANAGE_GROUP to true)
            ), GroupsResource.GroupComponent.GroupRole(
                name = "Member",
                permissions = mapOf()
            )),
            // Assign the creator as the owner
            membership = mutableMapOf(
                playerRef.uuid to GroupsResource.GroupComponent.GroupMembershipData(
                    roles = setOf(ownerRoleUUID)
                )
            )
        )

        return createOrUpdateGroup(store, group)
    }

    /**
     * Remove a group from the store and from disk.
     *
     * TODO: Turn this into a soft delete. See https://github.com/CivHorizons/Horizons/issues/20
     */
    fun deleteGroup(store: Store<EntityStore>, group: GroupsResource.GroupComponent) {
        val resource = store.getResource(GroupsResource.GroupComponent.getType())

        resource.groups.remove(group.uuid)
        storage.remove(group.uuid)
    }
}