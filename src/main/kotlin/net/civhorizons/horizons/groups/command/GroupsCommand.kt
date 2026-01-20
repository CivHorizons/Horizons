package net.civhorizons.horizons.groups.command

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.civhorizons.horizons.Horizons
import net.civhorizons.horizons.groups.ui.GroupsPage
import java.util.concurrent.CompletableFuture

class GroupsCommand : AbstractPlayerCommand("groups", "Interact with groups") {

    init {
        addSubCommand(GroupsListCommand())
        addSubCommand(GroupsCreateCommand())
    }

    override fun execute(context: CommandContext, store: Store<EntityStore>, ref: Ref<EntityStore>, playerRef: PlayerRef, world: World) {
        val player = context.senderAs(Player::class.java)

        // TODO: Pass to GUI
        val groupsResource = store.getResource(Horizons.instance.groupsModule.groupsResourceType)

        CompletableFuture.runAsync {
            player.pageManager.openCustomPage(ref, store, GroupsPage(playerRef))
        }
    }

    class GroupsListCommand : AbstractPlayerCommand("list", "List groups") {
        override fun execute(context: CommandContext, store: Store<EntityStore>, ref: Ref<EntityStore>, playerRef: PlayerRef, world: World) {
            val player = context.senderAs(Player::class.java)

            val groupsResource = store.getResource(Horizons.instance.groupsModule.groupsResourceType)

            player.sendMessage(Message.raw(groupsResource.groups.values.joinToString(", ") { "${it.name} (OWNER)" }))
        }
    }

    class GroupsCreateCommand : AbstractPlayerCommand("create", "Create group") {

        val nameArg = withRequiredArg("name", "Name of the Group", ArgTypes.STRING)

        override fun execute(context: CommandContext, store: Store<EntityStore>, ref: Ref<EntityStore>, playerRef: PlayerRef, world: World) {
            val name = nameArg.get(context)
        }
    }
}
