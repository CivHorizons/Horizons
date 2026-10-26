package net.civhorizons.horizons.groups

import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent
import com.hypixel.hytale.server.core.universe.datastore.DiskDataStore
import net.civhorizons.horizons.Horizons
import net.civhorizons.horizons.groups.command.GroupsCommand
import net.civhorizons.horizons.groups.resource.GroupComponent
import net.civhorizons.horizons.groups.event.GroupUsernameRefreshListener
import net.civhorizons.horizons.groups.event.PendingGroupInviteListener
import kotlin.io.path.createDirectories

class GroupsModule(val horizons: Horizons) {

    typealias GroupDataStore = DiskDataStore<GroupComponent>

    // Groups belong to the universe, not a world, so each one is its own file under universe/horizons/groups
    val groupStorage = GroupDataStore("horizons/groups", GroupComponent.CODEC)

    val api = GroupsAPI(groupStorage)

    fun setup() {
        LOGGER.atInfo().log("Groups module starting...")

        groupStorage.path.createDirectories()
        api.load()
        LOGGER.atInfo().log("${api.getGroups().size} groups loaded from disk")

        val usernameRefreshListener = GroupUsernameRefreshListener(api)
        horizons.eventRegistry.registerGlobal(PlayerReadyEvent::class.java) {
            usernameRefreshListener.onPlayerReady(it)
        }
        val pendingInviteListener = PendingGroupInviteListener(api)
        horizons.eventRegistry.registerGlobal(PlayerReadyEvent::class.java) {
            pendingInviteListener.onPlayerReady(it)
        }

        horizons.commandRegistry.registerCommand(GroupsCommand(api))
    }

    companion object {
        val LOGGER = Horizons.LOGGER.getSubLogger("GroupsModule")
    }
}
