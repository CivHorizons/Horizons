package net.civhorizons.horizons.chat.command

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand
import com.hypixel.hytale.server.core.permissions.provider.HytalePermissionsProvider
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.civhorizons.horizons.chat.GroupChat
import net.civhorizons.horizons.chat.GroupChat.Target
import net.civhorizons.horizons.chat.chatTranslation

/**
 * `/gc <group> <message>` sends one message to a group, `/gc <group>` sends all chat to that group,
 * and `/gc` goes back to local chat.
 */
class GroupChatCommand(val groupChat: GroupChat) : AbstractPlayerCommand("groupchat", "Chat to one of your groups") {

    private val textArg = withRequiredArg("text", "The group, then an optional message", ArgTypes.GREEDY_STRING)

    init {
        addAliases("gc")
        // Commands default to an op-only permission node; let every player use this one
        setPermissionGroups(HytalePermissionsProvider.GROUP_ADVENTURER)
        addUsageVariant(LocalChatCommand(groupChat))
    }

    override fun execute(
        context: CommandContext,
        store: Store<EntityStore>,
        ref: Ref<EntityStore>,
        playerRef: PlayerRef,
        world: World,
    ) {
        val groups = groupChat.groupsApi.getGroups(playerRef).values
        when (val target = GroupChat.findTarget(textArg.get(context), groups)) {
            is Target.NotFound -> playerRef.sendMessage(chatTranslation("group.notfound").param("name", target.name))
            is Target.Ambiguous -> playerRef.sendMessage(chatTranslation("group.ambiguous").param("name", target.name))
            is Target.Found if target.message.isEmpty() -> {
                groupChat.focus(playerRef, target.group)
                playerRef.sendMessage(chatTranslation("group.focused").param("group", target.group.name))
            }
            is Target.Found -> groupChat.send(playerRef, target.group, target.message)
        }
    }

    /**
     * `/gc` with nothing after it
     */
    private class LocalChatCommand(val groupChat: GroupChat) : AbstractPlayerCommand("Return to local chat") {

        init {
            setPermissionGroups(HytalePermissionsProvider.GROUP_ADVENTURER)
        }

        override fun execute(
            context: CommandContext,
            store: Store<EntityStore>,
            ref: Ref<EntityStore>,
            playerRef: PlayerRef,
            world: World,
        ) {
            groupChat.unfocus(playerRef)
            playerRef.sendMessage(chatTranslation("group.unfocused"))
        }
    }
}
