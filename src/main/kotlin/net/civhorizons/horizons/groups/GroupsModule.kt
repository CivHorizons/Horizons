package net.civhorizons.horizons.groups

import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSavingSystems
import com.hypixel.hytale.server.core.universe.datastore.DiskDataStore
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.civhorizons.horizons.Horizons
import net.civhorizons.horizons.groups.command.GroupsCommand
import net.civhorizons.horizons.groups.resource.GroupsResource
import net.civhorizons.horizons.groups.system.GroupSavingSystems
import net.civhorizons.horizons.util.loadAllUUIDs
import java.util.concurrent.ConcurrentHashMap

class GroupsModule(val horizons: Horizons) {

    val groupStorage = DiskDataStore<GroupsResource.GroupComponent>("horizons/groups", GroupsResource.GroupComponent.CODEC)

    lateinit var groupsResourceType: ResourceType<EntityStore, GroupsResource>

    fun setup() {
        LOGGER.atInfo().log("Groups module starting...")

        groupsResourceType = horizons.entityStoreRegistry.registerResource(GroupsResource::class.java) {
            val groups = groupStorage.loadAllUUIDs()
            LOGGER.atInfo().log("${groups.size} groups loaded from disk")

            GroupsResource(ConcurrentHashMap(groups))
        }

        horizons.entityStoreRegistry.registerSystem(GroupSavingSystems.WorldRemovedSystem())

        horizons.commandRegistry.registerCommand(GroupsCommand())
    }

    companion object {
        val LOGGER = Horizons.LOGGER.getSubLogger("GroupsModule")
    }

}
