package net.civhorizons.horizons.chat

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.Universe
import net.civhorizons.horizons.groups.GroupsAPI
import net.civhorizons.horizons.groups.resource.GroupComponent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Sends chat to every online member of a group, and tracks which group each player's chat is focused on.
 */
class GroupChat(val groupsApi: GroupsAPI) {

    /**
     * The group each player's normal chat goes to, keyed by player UUID. Players without an entry use local chat.
     */
    private val focus = ConcurrentHashMap<UUID, UUID>()

    fun focus(player: PlayerRef, group: GroupComponent) {
        focus[player.uuid] = group.uuid
    }

    /**
     * Returns true if the player was focused on a group.
     */
    fun unfocus(player: PlayerRef): Boolean = focus.remove(player.uuid) != null

    /**
     * Send one message to the group, without changing the sender's focus.
     */
    fun send(sender: PlayerRef, group: GroupComponent, content: String) {
        val message = format(group, sender, content)
        recipients(sender, group).forEach { it.sendMessage(message) }
        ChatModule.LOGGER.atInfo().log("[%s] %s: %s", group.name, sender.username, content)
    }

    /**
     * Send the chat to the sender's focused group instead of local chat. Returns false if the sender
     * isn't focused on a group, so the chat should stay local.
     */
    fun onPlayerChat(event: PlayerChatEvent): Boolean {
        val sender = event.sender
        val groupUuid = focus[sender.uuid] ?: return false
        val group = groupsApi.getMemberGroup(sender, groupUuid)
        if (group == null) {
            // They left the group, or it was deleted, since focusing on it
            focus.remove(sender.uuid, groupUuid)
            sender.sendMessage(chatTranslation("group.lost"))
        } else {
            event.targets = recipients(sender, group)
            event.formatter = PlayerChatEvent.Formatter { player, content -> format(group, player, content) }
        }
        return group != null
    }

    private fun recipients(sender: PlayerRef, group: GroupComponent): List<PlayerRef> =
        group.membership.keys
            .mapNotNull { Universe.get().getPlayer(it) }
            .filter { !it.hiddenPlayersManager.isPlayerHidden(sender.uuid) }

    private fun format(group: GroupComponent, sender: PlayerRef, content: String): Message =
        chatTranslation("group.message")
            .param("group", group.name)
            .param("username", sender.username)
            .param("message", content)

    sealed interface Target {
        data class Found(val group: GroupComponent, val message: String) : Target
        data class NotFound(val name: String) : Target
        data class Ambiguous(val name: String) : Target
    }

    companion object {
        /**
         * Split command text into one of [groups] and the message after its name. Names can contain spaces,
         * so the longest name that the text starts with wins, as long as a space or the end of the text follows.
         */
        fun findTarget(text: String, groups: Collection<GroupComponent>): Target {
            val input = text.trim()
            val matches = groups.filter { input.startsWithName(it.name) }
            val longest = matches.maxOfOrNull { it.name.length }
            val best = matches.filter { it.name.length == longest }

            return when {
                best.isEmpty() -> Target.NotFound(input.substringBefore(' '))
                best.size > 1 -> Target.Ambiguous(best.first().name)
                else -> Target.Found(best.single(), input.substring(longest!!).trim())
            }
        }

        private fun String.startsWithName(name: String): Boolean =
            startsWith(name, ignoreCase = true) && (length == name.length || this[name.length] == ' ')
    }
}
