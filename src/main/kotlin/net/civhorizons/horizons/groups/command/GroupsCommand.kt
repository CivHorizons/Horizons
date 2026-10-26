package net.civhorizons.horizons.groups.command

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.command.system.CommandContext
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.permissions.provider.HytalePermissionsProvider
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.civhorizons.horizons.groups.GroupsAPI
import net.civhorizons.horizons.groups.ui.GroupListPage

/**
 * Opens the groups GUI. Groups are managed entirely through the GUI, so there are no subcommands.
 */
class GroupsCommand(val groupsApi: GroupsAPI) : AbstractPlayerCommand("groups", "Interact with groups") {

    init {
        addAliases("group")
        // Commands default to an op-only permission node; let every player use this one
        setPermissionGroups(HytalePermissionsProvider.GROUP_ADVENTURER)
    }

    override fun execute(context: CommandContext, store: Store<EntityStore>, ref: Ref<EntityStore>, playerRef: PlayerRef, world: World) {
        val player = store.getComponent(ref, Player.getComponentType()) ?: return
        player.pageManager.openCustomPage(ref, store, GroupListPage(playerRef))
    }
}
