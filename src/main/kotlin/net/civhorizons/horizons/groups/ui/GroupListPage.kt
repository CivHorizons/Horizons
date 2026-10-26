package net.civhorizons.horizons.groups.ui

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.civhorizons.horizons.groups.groupsText
import net.civhorizons.horizons.groups.toMessage

/**
 * Entry point: the player's groups and their pending invites.
 */
class GroupListPage(playerRef: PlayerRef, status: Message? = null) : BaseGroupsPage(playerRef, status) {

    override fun buildPage(
        ref: Ref<EntityStore>,
        commands: UICommandBuilder,
        events: UIEventBuilder,
        store: Store<EntityStore>,
    ) {
        commands.append("Pages/Groups/GroupListPage.ui")
        commands.set("#PageTitle.Text", groupsText("ui.list.title"))
        commands.set("#InvitesHeader.Text", groupsText("ui.list.invites"))
        commands.set("#GroupsHeader.Text", groupsText("ui.list.groups"))
        commands.set("#EmptyLabel.Text", groupsText("ui.list.empty"))
        commands.set("#CreateButton.Text", groupsText("ui.list.create"))
        bindAction(events, "#CreateButton", CREATE)

        buildInvites(commands, events)
        buildGroups(commands, events)
    }

    private fun buildInvites(commands: UICommandBuilder, events: UIEventBuilder) {
        val invited = groupsApi.getInvitesFor(playerRef).values.sortedBy { it.name.lowercase() }
        commands.set("#InvitesSection.Visible", invited.isNotEmpty())

        invited.forEachIndexed { i, group ->
            val row = "#InviteList[$i]"
            val invite = group.invites.getValue(playerRef.uuid)
            commands.append("#InviteList", "Pages/Groups/IncomingInviteRow.ui")
            commands.set("$row #Name.Text", group.name)
            val inviter = group.memberName(invite.inviter)
            commands.set("$row #Detail.Text", groupsText("ui.list.inviterow").param("inviter", inviter))
            commands.set("$row #AcceptButton.Text", groupsText("ui.list.accept"))
            commands.set("$row #DeclineButton.Text", groupsText("ui.list.decline"))
            bindAction(events, "$row #AcceptButton", ACCEPT, group.uuid.toString())
            bindAction(events, "$row #DeclineButton", DECLINE, group.uuid.toString())
        }
    }

    private fun buildGroups(commands: UICommandBuilder, events: UIEventBuilder) {
        val groups = groupsApi.getGroups(playerRef).values.sortedBy { it.name.lowercase() }
        commands.set("#EmptyLabel.Visible", groups.isEmpty())

        groups.forEachIndexed { i, group ->
            val row = "#GroupList[$i]"
            commands.append("#GroupList", "Pages/Groups/EntryButton.ui")
            commands.set("$row #Name.Text", group.name)
            commands.set(
                "$row #Detail.Text",
                groupsText("ui.list.row")
                    .param("role", group.roleOf(playerRef.uuid)?.name.orEmpty())
                    .param("count", group.membership.size)
            )
            bindAction(events, row, OPEN, group.uuid.toString())
        }
    }

    override fun handleEvent(ref: Ref<EntityStore>, store: Store<EntityStore>, data: GroupsPageEvent) {
        when (data.action) {
            CREATE -> navigate(ref, store, GroupCreatePage(playerRef))
            OPEN -> data.targetUuid()?.let { navigate(ref, store, GroupDetailPage(playerRef, it)) }
            ACCEPT -> data.targetUuid()?.let { groupUuid ->
                val name = groupsApi.getGroup(groupUuid)?.name.orEmpty()
                val result = groupsApi.acceptInvite(playerRef, groupUuid)
                refresh(result.toMessage { groupsText("ui.list.accepted").param("name", name) })
            }
            DECLINE -> data.targetUuid()?.let { groupUuid ->
                val name = groupsApi.getGroup(groupUuid)?.name.orEmpty()
                val result = groupsApi.declineInvite(playerRef, groupUuid)
                refresh(result.toMessage { groupsText("ui.list.declined").param("name", name) })
            }
        }
    }

    private companion object {
        const val CREATE = "Create"
        const val OPEN = "Open"
        const val ACCEPT = "Accept"
        const val DECLINE = "Decline"
    }
}
