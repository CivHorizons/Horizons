package net.civhorizons.horizons.groups.command

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.ParseResult
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes
import com.hypixel.hytale.server.core.command.system.arguments.types.SingleArgumentType
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.civhorizons.horizons.GroupsAPI
import net.civhorizons.horizons.groups.GroupsModule
import net.civhorizons.horizons.groups.resource.GroupsResource
import net.civhorizons.horizons.groups.ui.GroupsPage
import java.util.concurrent.CompletableFuture

// Yes, these commands are all ugly and hard to use.
// The gui will be the primary way to interact with groups, but these are much faster to POC
class GroupsCommand(val groupsApi: GroupsAPI) : AbstractPlayerCommand("groups", "Interact with groups") {

    init {
        addSubCommand(GroupsListCommand(groupsApi))
        addSubCommand(GroupsInfoCommand(groupsApi))
        addSubCommand(GroupsCreateCommand(groupsApi))
        addSubCommand(GroupsDeleteCommand(groupsApi))

        addSubCommand(GroupsMembersCommand(groupsApi))

        addAliases("group")
    }

    override fun execute(context: CommandContext, store: Store<EntityStore>, ref: Ref<EntityStore>, playerRef: PlayerRef, world: World) {
        val player = store.getComponent(ref, Player.getComponentType()) ?: return

        // TODO: Pass to GUI
        val groups = groupsApi.getGroups(store, playerRef)

        CompletableFuture.runAsync {
            player.pageManager.openCustomPage(ref, store, GroupsPage(playerRef))
        }
    }

    class GroupsListCommand(val groupsApi: GroupsAPI) : AbstractPlayerCommand("list", "List groups") {
        override fun execute(context: CommandContext, store: Store<EntityStore>, ref: Ref<EntityStore>, playerRef: PlayerRef, world: World) {
            val groups = groupsApi.getGroups(store, playerRef)

            groups.forEach { (uuid, group) ->
                playerRef.sendMessage(Message.raw(
                    "${group.name}: $uuid"
                ))
            }
        }
    }

    class GroupsInfoCommand(val groupsApi: GroupsAPI) : AbstractPlayerCommand("info", "Get group information") {
        val nameArg = withRequiredArg("name", "Name of the Group", ArgTypes.STRING)

        override fun execute(context: CommandContext, store: Store<EntityStore>, ref: Ref<EntityStore>, playerRef: PlayerRef, world: World) {
            val name = nameArg.get(context)

            val groups = groupsApi.getGroups(store, playerRef).filter { it.value.name.startsWith(name, true) }

            if (groups.isEmpty()) {
                playerRef.sendMessage(Message.translation("horizons-groups.command.groups.info.errnotfound"))
            }

            groups.forEach { (uuid, group) ->
                playerRef.sendMessage(Message.raw(
                    "${group.name}\nUUID: $uuid\nMembers: ${group.membership.size}"
                ))
            }
        }
    }

    class GroupsCreateCommand(val groupsApi: GroupsAPI) : AbstractPlayerCommand("create", "Create group") {

        val nameArg = withRequiredArg("name", "Name of the Group", ArgTypes.STRING)

        override fun execute(context: CommandContext, store: Store<EntityStore>, ref: Ref<EntityStore>, playerRef: PlayerRef, world: World) {
            val name = nameArg.get(context)

            val group = groupsApi.createNewGroup(store, playerRef, name)

            playerRef.sendMessage(Message.translation("horizons-groups.command.groups.create.success").param("name", group.name))
        }
    }

    class GroupsDeleteCommand(val groupsApi: GroupsAPI) : AbstractPlayerCommand("delete", "Delete group") {
        val uuidArg = withRequiredArg("uuid", "UUID of the Group", ArgTypes.UUID)

        override fun execute(context: CommandContext, store: Store<EntityStore>, ref: Ref<EntityStore>, playerRef: PlayerRef, world: World) {
            val uuid = uuidArg.get(context)

            val group = groupsApi.getGroup(store, uuid)

            // If the group doesn't exist, or the player is not on it, send this message.
            // While it's hard to probe for groups via UUID, you may know the UUID of a group after you're removed from it.
            if (group == null || !group.isPlayerOnGroup(playerRef)) {
                playerRef.sendMessage(Message.translation("horizons-groups.command.groups.delete.errnotfound"))
                return
            }

            if (!group.playerHasPermission(playerRef, GroupsResource.GroupComponent.GroupRole.Permission.MANAGE_GROUP)) {
                playerRef.sendMessage(Message.translation("horizons-groups.command.groups.delete.errnoperms"))
                return
            }

            groupsApi.deleteGroup(store, group)
            playerRef.sendMessage(Message.translation("horizons-groups.command.groups.delete.success").param("name", group.name))
        }
    }

    // TODO: A custom Arg Type can be created for this
    class GroupsMembersCommand(val groupsApi: GroupsAPI) : AbstractPlayerCommand("members", "Manage Group Members") {

        init {
            addAliases("member")

            addSubCommand(GroupsMembersListCommand(groupsApi))
            addSubCommand(GroupsMembersAddCommand(groupsApi))
            addSubCommand(GroupsMembersRemoveCommand(groupsApi))
        }

        override fun execute(context: CommandContext, store: Store<EntityStore>, ref: Ref<EntityStore>, playerRef: PlayerRef, world: World) {
            // TODO: Show GUI
        }

        class GroupsMembersListCommand(val groupsApi: GroupsAPI): AbstractPlayerCommand("list", "List Members on a Group") {
            val uuidArg = withRequiredArg("uuid", "UUID of the Group", ArgTypes.UUID)

            override fun execute(context: CommandContext, store: Store<EntityStore>, ref: Ref<EntityStore>, playerRef: PlayerRef, world: World) {
                val uuid = uuidArg.get(context)

                val group = groupsApi.getGroup(store, uuid)

                if (group == null || !group.isPlayerOnGroup(playerRef)) {
                    playerRef.sendMessage(Message.translation("horizons-groups.command.groups.members.list.errnotfound"))
                    return
                }

                playerRef.sendMessage(Message.raw(group.membership.keys.joinToString(", ") { group.memberName(it) }))
            }
        }

        class GroupsMembersAddCommand(val groupsApi: GroupsAPI): AbstractPlayerCommand("add", "Add Member to a group") {
            val uuidArg = withRequiredArg("uuid", "UUID of the Group", ArgTypes.UUID)
            val player = withRequiredArg("player", "Player to Add", ArgTypes.PLAYER_REF)


            override fun execute(context: CommandContext, store: Store<EntityStore>, ref: Ref<EntityStore>, playerRef: PlayerRef, world: World) {
                val uuid = uuidArg.get(context)
                val targetPlayer = player.get(context)

                val group = groupsApi.getGroup(store, uuid)

                if (group == null || !group.isPlayerOnGroup(playerRef)) {
                    playerRef.sendMessage(Message.translation("horizons-groups.command.groups.members.add.errnotfound"))
                    return
                }

                if (!group.playerHasPermission(playerRef, GroupsResource.GroupComponent.GroupRole.Permission.MANAGE_GROUP)) {
                    playerRef.sendMessage(Message.translation("horizons-groups.command.groups.members.add.errnoperms"))
                    return
                }

                if (group.isPlayerOnGroup(targetPlayer)) {
                    playerRef.sendMessage(Message.translation("horizons-groups.command.groups.members.add.erralreadyon"))
                    return
                }

                group.membership[targetPlayer.uuid] = GroupsResource.GroupComponent.GroupMembershipData(
                    roles = setOf(group.roles.find { it.name == "Member" }!!.uuid) // TODO This is JANKY. Replace when we have custom roles
                )

                groupsApi.createOrUpdateGroup(store, group)
                playerRef.sendMessage(Message.translation("horizons-groups.command.groups.members.add.success").param("player", player.name).param("name", group.name))
            }
        }

        class GroupsMembersRemoveCommand(val groupsApi: GroupsAPI): AbstractPlayerCommand("remove", "Remove Member from a group") {
            val uuidArg = withRequiredArg("uuid", "UUID of the Group", ArgTypes.UUID)
            val player = withRequiredArg("player", "Player to Add", ArgTypes.PLAYER_REF)

            override fun execute(context: CommandContext, store: Store<EntityStore>, ref: Ref<EntityStore>, playerRef: PlayerRef, world: World) {
                val uuid = uuidArg.get(context)
                val targetPlayer = player.get(context)

                val group = groupsApi.getGroup(store, uuid)

                if (group == null || !group.isPlayerOnGroup(playerRef)) {
                    playerRef.sendMessage(Message.translation("horizons-groups.command.groups.members.remove.errnotfound"))
                    return
                }

                if (!group.playerHasPermission(playerRef, GroupsResource.GroupComponent.GroupRole.Permission.MANAGE_GROUP)) {
                    playerRef.sendMessage(Message.translation("horizons-groups.command.groups.members.remove.errnoperms"))
                    return
                }

                if (!group.isPlayerOnGroup(targetPlayer)) {
                    playerRef.sendMessage(Message.translation("horizons-groups.command.groups.members.remove.errnoton"))
                    return
                }

                group.membership.remove(targetPlayer.uuid)

                groupsApi.createOrUpdateGroup(store, group)
                playerRef.sendMessage(Message.translation("horizons-groups.command.groups.members.remove.success").param("player", player.name).param("name", group.name))

            }
        }
    }
}
