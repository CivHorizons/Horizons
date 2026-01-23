package net.civhorizons.horizons.groups

import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.universe.datastore.DiskDataStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.civhorizons.horizons.Horizons
import net.civhorizons.horizons.groups.command.GroupsCommand
import net.civhorizons.horizons.groups.resource.GroupsResource
import net.civhorizons.horizons.groups.system.GroupSavingSystems
import net.civhorizons.horizons.util.loadAllUUIDs
import net.civhorizons.horizons.util.remove
import net.civhorizons.horizons.util.save
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GroupsModule(val horizons: Horizons) {

    typealias GroupDataStore = DiskDataStore<GroupsResource.GroupComponent>
    val groupStorage = GroupDataStore("horizons/groups", GroupsResource.GroupComponent.CODEC)

    lateinit var groupsResourceType: ResourceType<EntityStore, GroupsResource>

    val api = GroupsAPI(groupStorage)

    fun setup() {
        LOGGER.atInfo().log("Groups module starting...")

        groupsResourceType = horizons.entityStoreRegistry.registerResource(GroupsResource::class.java) {
            val groups = groupStorage.loadAllUUIDs()
            LOGGER.atInfo().log("${groups.size} groups loaded from disk")

            GroupsResource(ConcurrentHashMap(groups))
        }

        horizons.entityStoreRegistry.registerSystem(GroupSavingSystems.WorldRemovedSystem())

        horizons.commandRegistry.registerCommand(GroupsCommand(api))
    }

    companion object {
        val LOGGER = Horizons.LOGGER.getSubLogger("GroupsModule")
    }

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
}
