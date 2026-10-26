package net.civhorizons.horizons.chat

import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent
import org.joml.Vector3dc
import java.util.UUID

/**
 * Limits public chat to players in the sender's world and within [RANGE] blocks of them.
 */
class LocalChatListener {

    fun onPlayerChat(event: PlayerChatEvent) {
        val sender = event.sender
        val world = sender.worldUuid ?: return
        // Chat runs off the world thread, so positions may be a tick stale, which is fine for range checks
        val position = sender.transform.position

        event.targets = event.targets.filter {
            it.uuid == sender.uuid || canHear(world, position, it.worldUuid, it.transform.position)
        }
    }

    companion object {
        const val RANGE = 1000.0

        fun canHear(senderWorld: UUID, senderPosition: Vector3dc, targetWorld: UUID?, targetPosition: Vector3dc) =
            senderWorld == targetWorld && senderPosition.distanceSquared(targetPosition) <= RANGE * RANGE
    }
}
