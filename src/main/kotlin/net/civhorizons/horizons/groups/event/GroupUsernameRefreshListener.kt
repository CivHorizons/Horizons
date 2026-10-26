package net.civhorizons.horizons.groups.event

import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent
import com.hypixel.hytale.server.core.universe.PlayerRef
import net.civhorizons.horizons.groups.GroupsAPI

/**
 * Keeps the username stored on a player's group memberships current when they finish joining.
 */
class GroupUsernameRefreshListener(val groupsApi: GroupsAPI) {

    fun onPlayerReady(event: PlayerReadyEvent) {
        val ref = event.playerRef
        val store = ref.store

        // Store access must happen on the world thread
        store.externalData.world.execute {
            if (!ref.isValid) return@execute
            val playerRef = store.getComponent(ref, PlayerRef.getComponentType()) ?: return@execute

            groupsApi.refreshUsername(playerRef)
        }
    }
}
