package net.civhorizons.horizons.groups

import com.hypixel.hytale.component.ResourceType
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.datastore.DiskDataStore
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.civhorizons.horizons.GroupsAPI
import net.civhorizons.horizons.Horizons
import net.civhorizons.horizons.groups.command.GroupsCommand
import net.civhorizons.horizons.groups.resource.GroupsResource
import net.civhorizons.horizons.groups.system.GroupPlayerReadyListener
import net.civhorizons.horizons.groups.system.GroupSavingSystems
import net.civhorizons.horizons.util.loadAllUUIDs
import net.civhorizons.horizons.util.remove
import net.civhorizons.horizons.util.save
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createDirectories

class GroupsModule(val horizons: Horizons) {

    typealias GroupDataStore = DiskDataStore<GroupsResource.GroupComponent>
    val groupStorage = GroupDataStore("horizons/groups", GroupsResource.GroupComponent.CODEC)

    lateinit var groupsResourceType: ResourceType<EntityStore, GroupsResource>

    val api = GroupsAPI(groupStorage)

    fun setup() {
        LOGGER.atInfo().log("Groups module starting...")

        groupStorage.path.createDirectories()
        groupsResourceType = horizons.entityStoreRegistry.registerResource(GroupsResource::class.java) {
            val groups = groupStorage.loadAllUUIDs()
            LOGGER.atInfo().log("${groups.size} groups loaded from disk")

            GroupsResource(ConcurrentHashMap(groups))
        }

        horizons.entityStoreRegistry.registerSystem(GroupSavingSystems.WorldRemovedSystem())

        val playerReadyListener = GroupPlayerReadyListener(api)
        horizons.eventRegistry.registerGlobal(PlayerReadyEvent::class.java) { playerReadyListener.onPlayerReady(it) }

        horizons.commandRegistry.registerCommand(GroupsCommand(api))
    }

    companion object {
        val LOGGER = Horizons.LOGGER.getSubLogger("GroupsModule")
    }
}
