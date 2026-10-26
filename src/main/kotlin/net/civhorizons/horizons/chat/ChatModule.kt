package net.civhorizons.horizons.chat

import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent
import net.civhorizons.horizons.Horizons

class ChatModule(val horizons: Horizons) {

    fun setup() {
        LOGGER.atInfo().log("Chat module starting...")

        val localChatListener = LocalChatListener()
        horizons.eventRegistry.registerAsyncGlobal<String, PlayerChatEvent>(PlayerChatEvent::class.java) { future ->
            future.thenApply { it.also(localChatListener::onPlayerChat) }
        }
    }

    companion object {
        val LOGGER = Horizons.LOGGER.getSubLogger("ChatModule")
    }
}
