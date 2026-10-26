package net.civhorizons.horizons.groups.event

import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent
import com.hypixel.hytale.server.core.universe.PlayerRef
import net.civhorizons.horizons.groups.GroupsAPI
import net.civhorizons.horizons.groups.groupsTranslation

/**
 * Tells a player about their pending group invites when they finish joining.
 */
class PendingGroupInviteListener(val groupsApi: GroupsAPI) {

    fun onPlayerReady(event: PlayerReadyEvent) {
        val ref = event.playerRef
        val store = ref.store

        // Store access must happen on the world thread
        store.externalData.world.execute {
            if (!ref.isValid) return@execute
            val playerRef = store.getComponent(ref, PlayerRef.getComponentType()) ?: return@execute

            val invites = groupsApi.getInvitesFor(playerRef)
            if (invites.isNotEmpty()) {
                playerRef.sendMessage(groupsTranslation("invite.pending").param("count", invites.size))
            }
        }
    }
}
