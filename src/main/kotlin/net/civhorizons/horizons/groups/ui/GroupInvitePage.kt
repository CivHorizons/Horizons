package net.civhorizons.horizons.groups.ui

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.civhorizons.horizons.groups.GroupResult
import net.civhorizons.horizons.groups.groupsText
import net.civhorizons.horizons.groups.resource.GroupsResource.GroupComponent
import net.civhorizons.horizons.groups.toMessage
import java.util.UUID

/**
 * Pick an online player to invite. Lists players who are neither members nor already invited.
 */
class GroupInvitePage(
    playerRef: PlayerRef,
    val groupUuid: UUID,
    status: Message? = null,
) : BaseGroupsPage(playerRef, status) {

    private var search = ""

    override fun buildPage(
        ref: Ref<EntityStore>,
        commands: UICommandBuilder,
        events: UIEventBuilder,
        store: Store<EntityStore>,
    ) {
        commands.append("Pages/Groups/GroupInvitePage.ui")
        commands.set("#PageTitle.Text", groupsText("ui.invite.title"))
        commands.set("#SearchInput.PlaceholderText", groupsText("ui.invite.search"))
        commands.set("#SearchInput.Value", search)
        commands.set("#EmptyLabel.Text", groupsText("ui.invite.empty"))
        commands.set("#BackButton.Text", groupsText("ui.common.back"))
        bindAction(events, "#BackButton", BACK)
        bindValueChanged(events, "#SearchInput", SEARCH, capture = "#SearchInput.Value")

        val group = groupsApi.getMemberGroup(store, playerRef, groupUuid)
        if (group == null) {
            commands.set("#EmptyLabel.Visible", false)
            status = GroupResult.Error.NOT_FOUND.toMessage()
            return
        }
        buildPlayerList(group, commands, events)
    }

    private fun buildPlayerList(group: GroupComponent, commands: UICommandBuilder, events: UIEventBuilder) {
        val query = search.trim().lowercase()
        val candidates = Universe.get().players
            .filter { !group.isMember(it.uuid) && !group.invites.containsKey(it.uuid) }
            .filter { it.username.lowercase().contains(query) }
            .sortedBy { it.username.lowercase() }

        commands.clear("#PlayerList")
        commands.set("#EmptyLabel.Visible", candidates.isEmpty())
        candidates.forEachIndexed { i, player ->
            val row = "#PlayerList[$i]"
            commands.append("#PlayerList", "Pages/Groups/EntryButton.ui")
            commands.set("$row #Name.Text", player.username)
            commands.set("$row #Detail.Text", "")
            bindAction(events, row, INVITE, player.uuid.toString())
        }
    }

    override fun handleEvent(ref: Ref<EntityStore>, store: Store<EntityStore>, data: GroupsPageEvent) {
        val group = groupsApi.getMemberGroup(store, playerRef, groupUuid)
        if (group == null) {
            navigate(ref, store, GroupListPage(playerRef, GroupResult.Error.NOT_FOUND.toMessage()))
            return
        }

        when (data.action) {
            BACK -> navigate(ref, store, GroupDetailPage(playerRef, groupUuid))
            SEARCH -> {
                // Patch only the list so the search field keeps focus
                search = data.value.orEmpty()
                val commands = UICommandBuilder()
                val events = UIEventBuilder()
                buildPlayerList(group, commands, events)
                sendUpdate(commands, events, false)
            }
            INVITE -> data.targetUuid()?.let { invite(ref, store, it) }
        }
    }

    private fun invite(ref: Ref<EntityStore>, store: Store<EntityStore>, targetUuid: UUID) {
        val target = Universe.get().getPlayer(targetUuid)
        if (target == null) {
            refresh(GroupResult.Error.PLAYER_OFFLINE.toMessage())
            return
        }

        when (val result = groupsApi.invitePlayer(store, playerRef, groupUuid, target)) {
            GroupResult.Success -> {
                val sent = groupsText("ui.invite.sent").param("player", target.username)
                navigate(ref, store, GroupDetailPage(playerRef, groupUuid, sent))
            }
            is GroupResult.Error -> refresh(result.toMessage())
        }
    }

    private companion object {
        const val BACK = "Back"
        const val SEARCH = "Search"
        const val INVITE = "Invite"
    }
}
