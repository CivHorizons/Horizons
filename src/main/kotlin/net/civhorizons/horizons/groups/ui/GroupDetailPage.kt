package net.civhorizons.horizons.groups.ui

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo
import com.hypixel.hytale.server.core.ui.LocalizableString
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.civhorizons.horizons.groups.GroupResult
import net.civhorizons.horizons.groups.groupsText
import net.civhorizons.horizons.groups.resource.GroupsResource.GroupComponent
import net.civhorizons.horizons.groups.resource.GroupsResource.GroupComponent.GroupRole.Permission
import net.civhorizons.horizons.groups.toMessage
import java.util.UUID

/**
 * One group: members and their roles, pending invites, and management actions.
 * Management controls are only shown to members with MANAGE_GROUP; the API re-checks on every action.
 */
class GroupDetailPage(
    playerRef: PlayerRef,
    val groupUuid: UUID,
    status: Message? = null,
) : BaseGroupsPage(playerRef, status) {

    override fun buildPage(
        ref: Ref<EntityStore>,
        commands: UICommandBuilder,
        events: UIEventBuilder,
        store: Store<EntityStore>,
    ) {
        commands.append("Pages/Groups/GroupDetailPage.ui")
        commands.set("#BackButton.Text", groupsText("ui.common.back"))
        bindAction(events, "#BackButton", BACK)

        val group = groupsApi.getMemberGroup(store, playerRef, groupUuid)
        if (group == null) {
            commands.set("#Body.Visible", false)
            commands.set("#ManageButtons.Visible", false)
            status = GroupResult.Error.NOT_FOUND.toMessage()
            return
        }

        val canManage = group.hasPermission(playerRef.uuid, Permission.MANAGE_GROUP)
        commands.set("#PageTitle.Text", group.name)
        commands.set("#MembersHeader.Text", groupsText("ui.detail.members"))
        commands.set("#InvitesHeader.Text", groupsText("ui.detail.invites"))

        buildMembers(group, canManage, commands, events)
        buildInvites(group, canManage, commands, events)

        commands.set("#ManageButtons.Visible", canManage)
        if (canManage) {
            commands.set("#InviteButton.Text", groupsText("ui.detail.invite"))
            commands.set("#DeleteButton.Text", groupsText("ui.detail.delete"))
            bindAction(events, "#InviteButton", INVITE)
            bindAction(events, "#DeleteButton", DELETE)
        }
    }

    private fun buildMembers(
        group: GroupComponent,
        canManage: Boolean,
        commands: UICommandBuilder,
        events: UIEventBuilder,
    ) {
        val roleEntries = group.roles.map {
            DropdownEntryInfo(LocalizableString.fromString(it.name), it.uuid.toString())
        }
        val members = group.membership.keys.sortedBy { group.memberName(it).lowercase() }

        members.forEachIndexed { i, member ->
            val row = "#MemberList[$i]"
            val role = group.roleOf(member)
            // Managers edit everyone but themselves, so they can't lock themselves out by accident
            val editable = canManage && member != playerRef.uuid

            commands.append("#MemberList", "Pages/Groups/MemberRow.ui")
            commands.set("$row #Name.Text", group.memberName(member))
            commands.set("$row #Offline.Text", groupsText("ui.common.offline"))
            commands.set("$row #Offline.Visible", Universe.get().getPlayer(member) == null)
            commands.set("$row #Role.Text", role?.name.orEmpty())
            commands.set("$row #Role.Visible", !editable)
            commands.set("$row #RoleDropdown.Visible", editable)
            commands.set("$row #RemoveButton.Visible", editable)

            if (editable) {
                commands.set("$row #RoleDropdown.Entries", roleEntries)
                role?.let { commands.set("$row #RoleDropdown.Value", it.uuid.toString()) }
                commands.set("$row #RemoveButton.Text", groupsText("ui.detail.remove"))
                bindValueChanged(events, "$row #RoleDropdown", SET_ROLE, member.toString(), "$row #RoleDropdown.Value")
                bindAction(events, "$row #RemoveButton", REMOVE, member.toString())
            }
        }
    }

    private fun buildInvites(
        group: GroupComponent,
        canManage: Boolean,
        commands: UICommandBuilder,
        events: UIEventBuilder,
    ) {
        val invites = group.invites.entries.sortedBy { it.value.username.lowercase() }
        commands.set("#InvitesSection.Visible", canManage && invites.isNotEmpty())
        if (!canManage) return

        invites.forEachIndexed { i, (invitee, invite) ->
            val row = "#InviteList[$i]"
            commands.append("#InviteList", "Pages/Groups/PendingInviteRow.ui")
            commands.set("$row #Name.Text", invite.username)
            commands.set("$row #RevokeButton.Text", groupsText("ui.detail.revoke"))
            bindAction(events, "$row #RevokeButton", REVOKE, invitee.toString())
        }
    }

    override fun handleEvent(ref: Ref<EntityStore>, store: Store<EntityStore>, data: GroupsPageEvent) {
        val group = groupsApi.getMemberGroup(store, playerRef, groupUuid)
        if (group == null) {
            navigate(ref, store, GroupListPage(playerRef, GroupResult.Error.NOT_FOUND.toMessage()))
            return
        }

        when (data.action) {
            BACK -> navigate(ref, store, GroupListPage(playerRef))
            INVITE -> navigate(ref, store, GroupInvitePage(playerRef, groupUuid))
            DELETE -> confirmDelete(ref, store, group)
            REMOVE -> data.targetUuid()?.let { confirmRemove(ref, store, group, it) }
            SET_ROLE -> data.targetUuid()?.let { setRole(store, group, it, data.value) }
            REVOKE -> data.targetUuid()?.let { revokeInvite(store, group, it) }
        }
    }

    private fun setRole(store: Store<EntityStore>, group: GroupComponent, target: UUID, roleValue: String?) {
        val roleUuid = roleValue?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (roleUuid == null) {
            refresh(GroupResult.Error.ROLE_NOT_FOUND.toMessage())
            return
        }

        val result = groupsApi.setMemberRole(store, playerRef, groupUuid, target, roleUuid)
        // Refresh either way so a rejected change snaps the dropdown back
        refresh(result.toMessage {
            groupsText("ui.detail.rolechanged")
                .param("player", group.memberName(target))
                .param("role", group.roleOf(target)?.name.orEmpty())
        })
    }

    private fun revokeInvite(store: Store<EntityStore>, group: GroupComponent, target: UUID) {
        val name = group.invites[target]?.username.orEmpty()
        val result = groupsApi.revokeInvite(store, playerRef, groupUuid, target)
        refresh(result.toMessage { groupsText("ui.detail.revoked").param("player", name) })
    }

    private fun confirmDelete(ref: Ref<EntityStore>, store: Store<EntityStore>, group: GroupComponent) {
        navigate(ref, store, ConfirmPage(
            playerRef,
            title = groupsText("ui.detail.deleteconfirm.title"),
            message = groupsText("ui.detail.deleteconfirm.message").param("name", group.name),
            onConfirm = { r, s ->
                when (val result = groupsApi.deleteGroup(s, playerRef, groupUuid)) {
                    GroupResult.Success -> {
                        val deleted = groupsText("ui.detail.deleted").param("name", group.name)
                        navigate(r, s, GroupListPage(playerRef, deleted))
                    }
                    is GroupResult.Error -> navigate(r, s, GroupDetailPage(playerRef, groupUuid, result.toMessage()))
                }
            },
            onCancel = { r, s -> navigate(r, s, GroupDetailPage(playerRef, groupUuid)) },
        ))
    }

    private fun confirmRemove(ref: Ref<EntityStore>, store: Store<EntityStore>, group: GroupComponent, target: UUID) {
        val name = group.memberName(target)
        navigate(ref, store, ConfirmPage(
            playerRef,
            title = groupsText("ui.detail.removeconfirm.title"),
            message = groupsText("ui.detail.removeconfirm.message").param("player", name).param("name", group.name),
            onConfirm = { r, s ->
                val result = groupsApi.removeMember(s, playerRef, groupUuid, target)
                val status = result.toMessage { groupsText("ui.detail.removed").param("player", name) }
                navigate(r, s, GroupDetailPage(playerRef, groupUuid, status))
            },
            onCancel = { r, s -> navigate(r, s, GroupDetailPage(playerRef, groupUuid)) },
        ))
    }

    private companion object {
        const val BACK = "Back"
        const val INVITE = "Invite"
        const val DELETE = "Delete"
        const val REMOVE = "Remove"
        const val SET_ROLE = "SetRole"
        const val REVOKE = "Revoke"
    }
}
