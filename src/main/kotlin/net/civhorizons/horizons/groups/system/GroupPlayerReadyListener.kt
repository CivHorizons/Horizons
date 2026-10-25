package net.civhorizons.horizons.groups.system

import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent
import com.hypixel.hytale.server.core.universe.PlayerRef
import net.civhorizons.horizons.GroupsAPI
import net.civhorizons.horizons.groups.groupsText

/**
 * When a player finishes joining: keep their stored username current and tell them about pending invites.
 */
class GroupPlayerReadyListener(val groupsApi: GroupsAPI) {

    fun onPlayerReady(event: PlayerReadyEvent) {
        val ref = event.playerRef
        val store = ref.store

        // Store access must happen on the world thread
        store.externalData.world.execute {
            if (!ref.isValid) return@execute
            val playerRef = store.getComponent(ref, PlayerRef.getComponentType()) ?: return@execute

            groupsApi.refreshUsername(store, playerRef)

            val invites = groupsApi.getInvitesFor(store, playerRef)
            if (invites.isNotEmpty()) {
                playerRef.sendMessage(groupsText("invite.pending").param("count", invites.size))
            }
        }
    }
}
