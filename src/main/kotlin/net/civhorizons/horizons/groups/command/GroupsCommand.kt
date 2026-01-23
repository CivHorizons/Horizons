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
import net.civhorizons.horizons.groups.GroupsModule
import net.civhorizons.horizons.groups.resource.GroupsResource
import net.civhorizons.horizons.groups.ui.GroupsPage
import java.util.concurrent.CompletableFuture

class GroupsCommand(val groupsApi: GroupsModule.GroupsAPI) : AbstractPlayerCommand("groups", "Interact with groups") {

    init {
        addSubCommand(GroupsListCommand(groupsApi))
        addSubCommand(GroupsInfoCommand(groupsApi))
        addSubCommand(GroupsCreateCommand(groupsApi))
        addSubCommand(GroupsDeleteCommand(groupsApi))

        addAliases("group")
    }

    override fun execute(context: CommandContext, store: Store<EntityStore>, ref: Ref<EntityStore>, playerRef: PlayerRef, world: World) {
        val player = context.senderAs(Player::class.java)

        // TODO: Pass to GUI
        val groups = groupsApi.getGroups(store)

        CompletableFuture.runAsync {
            player.pageManager.openCustomPage(ref, store, GroupsPage(playerRef))
        }
    }

    class GroupsListCommand(val groupsApi: GroupsModule.GroupsAPI) : AbstractPlayerCommand("list", "List groups") {
        override fun execute(context: CommandContext, store: Store<EntityStore>, ref: Ref<EntityStore>, playerRef: PlayerRef, world: World) {
            val player = context.senderAs(Player::class.java)

            val groups = groupsApi.getGroups(store)

            groups.forEach { (uuid, group) ->
                player.sendMessage(Message.raw(
                    "${group.name}: $uuid"
                ))
            }
        }
    }

    class GroupsInfoCommand(val groupsApi: GroupsModule.GroupsAPI) : AbstractPlayerCommand("info", "Get group information") {
        val nameArg = withRequiredArg("name", "Name of the Group", ArgTypes.STRING)

        override fun execute(context: CommandContext, store: Store<EntityStore>, ref: Ref<EntityStore>, playerRef: PlayerRef, world: World) {
            val name = nameArg.get(context)

            val groups = groupsApi.getGroups(store).filter { it.value.name.startsWith(name, true) }

            if (groups.isEmpty()) {
                playerRef.sendMessage(Message.translation("horizons-groups.command.groups.info.errnotfound"))
            }

            groups.forEach { (uuid, group) ->
                playerRef.sendMessage(Message.raw(
                    "${group.name}\nUUID: $uuid"
                ))
            }
        }
    }

    class GroupsCreateCommand(val groupsApi: GroupsModule.GroupsAPI) : AbstractPlayerCommand("create", "Create group") {

        val nameArg = withRequiredArg("name", "Name of the Group", ArgTypes.STRING)

        override fun execute(context: CommandContext, store: Store<EntityStore>, ref: Ref<EntityStore>, playerRef: PlayerRef, world: World) {
            val name = nameArg.get(context)

            val group = groupsApi.createOrUpdateGroup(store, GroupsResource.GroupComponent(
                name = name,
            ))

            playerRef.sendMessage(Message.translation("horizons-groups.command.groups.create.success").param("name", group.name))
        }
    }

    class GroupsDeleteCommand(val groupsApi: GroupsModule.GroupsAPI) : AbstractPlayerCommand("delete", "Delete group") {
        val uuidArg = withRequiredArg("uuid", "UUID of the Group", ArgTypes.UUID)

        override fun execute(context: CommandContext, store: Store<EntityStore>, ref: Ref<EntityStore>, playerRef: PlayerRef, world: World) {
            val uuid = uuidArg.get(context)

            val group = groupsApi.getGroup(store, uuid)

            if (group == null) {
                playerRef.sendMessage(Message.translation("horizons-groups.command.groups.delete.errnotfound"))
            } else {
                groupsApi.deleteGroup(store, group)
                playerRef.sendMessage(Message.translation("horizons-groups.command.groups.delete.success").param("name", group.name))
            }
        }
    }
}
