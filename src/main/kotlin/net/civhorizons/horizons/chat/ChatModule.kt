package net.civhorizons.horizons.chat

import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent
import net.civhorizons.horizons.Horizons
import net.civhorizons.horizons.chat.command.GroupChatCommand

class ChatModule(val horizons: Horizons) {

    fun setup() {
        LOGGER.atInfo().log("Chat module starting...")

        val groupChat = GroupChat(horizons.groupsModule.api)
        val localChatListener = LocalChatListener()
        horizons.eventRegistry.registerAsyncGlobal<String, PlayerChatEvent>(PlayerChatEvent::class.java) { future ->
            future.thenApply { event ->
                // Chat goes to the sender's focused group, or stays local
                if (!groupChat.onPlayerChat(event)) localChatListener.onPlayerChat(event)
                event
            }
        }
        horizons.eventRegistry.registerGlobal(PlayerDisconnectEvent::class.java) {
            groupChat.unfocus(it.playerRef)
        }

        horizons.commandRegistry.registerCommand(GroupChatCommand(groupChat))
    }

    companion object {
        val LOGGER = Horizons.LOGGER.getSubLogger("ChatModule")
    }
}
