package net.civhorizons.horizons.groups.ui

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.civhorizons.horizons.groups.GroupResult
import net.civhorizons.horizons.groups.groupsText
import net.civhorizons.horizons.groups.resource.GroupsResource.GroupComponent
import net.civhorizons.horizons.groups.resource.GroupsResource.GroupComponent.GroupRole.Kind
import net.civhorizons.horizons.groups.resource.GroupsResource.GroupComponent.GroupRole.Permission
import net.civhorizons.horizons.groups.toMessage
import java.util.UUID

/**
 * A group's roles in rank order, highest first. Custom roles can be created, reordered, and deleted,
 * and any role below the viewer's own can be opened for editing.
 * Controls are only shown for roles below the viewer's own; the API re-checks on every action.
 */
class GroupRolesPage(
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
        commands.append("Pages/Groups/GroupRolesPage.ui")
        commands.set("#PageTitle.Text", groupsText("ui.roles.title"))
        commands.set("#BackButton.Text", groupsText("ui.common.back"))
        bindAction(events, "#BackButton", BACK)

        val group = groupsApi.getMemberGroup(store, playerRef, groupUuid)
        if (group == null) {
            commands.set("#RoleList.Visible", false)
            commands.set("#CreateSection.Visible", false)
            status = GroupResult.Error.NOT_FOUND.toMessage()
            return
        }

        val canManage = group.hasPermission(playerRef.uuid, Permission.MANAGE_ROLES)
        commands.set("#CreateSection.Visible", canManage)
        if (canManage) {
            commands.set("#NameInput.PlaceholderText", groupsText("ui.roles.name"))
            commands.set("#CreateButton.Text", groupsText("ui.roles.create"))
            bindAction(events, "#CreateButton", CREATE, capture = "#NameInput.Value")
        }

        buildRoles(group, commands, events)
    }

    private fun buildRoles(group: GroupComponent, commands: UICommandBuilder, events: UIEventBuilder) {
        val me = playerRef.uuid

        group.roles.forEachIndexed { i, role ->
            val row = "#RoleList[$i]"
            val id = role.uuid.toString()
            val editable = group.canManageRole(me, role.uuid)

            fun button(name: String, textKey: String, action: String, visible: Boolean) {
                commands.set("$row #$name.Visible", visible)
                if (!visible) return
                commands.set("$row #$name.Text", groupsText(textKey))
                bindAction(events, "$row #$name", action, id)
            }

            commands.append("#RoleList", "Pages/Groups/RoleRow.ui")
            commands.set("$row #Name.Text", role.name)
            val count = group.membersWithRole(role.uuid)
            commands.set("$row #Detail.Text", groupsText("ui.roles.members").param("count", count))
            button("UpButton", "ui.roles.up", MOVE_UP, group.canMoveRole(me, role.uuid, up = true))
            button("DownButton", "ui.roles.down", MOVE_DOWN, group.canMoveRole(me, role.uuid, up = false))
            button("EditButton", "ui.roles.edit", EDIT, editable)
            button("DeleteButton", "ui.roles.delete", DELETE, editable && role.kind == Kind.CUSTOM)
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
            CREATE -> createRole(store, data.value.orEmpty())
            MOVE_UP -> data.targetUuid()?.let { moveRole(store, it, up = true) }
            MOVE_DOWN -> data.targetUuid()?.let { moveRole(store, it, up = false) }
            EDIT -> data.targetUuid()?.let { navigate(ref, store, GroupRoleEditPage(playerRef, groupUuid, it)) }
            DELETE -> data.targetUuid()?.let { confirmDelete(ref, store, group, it) }
        }
    }

    private fun createRole(store: Store<EntityStore>, name: String) {
        val result = groupsApi.createRole(store, playerRef, groupUuid, name)
        refresh(result.toMessage { groupsText("ui.roles.created").param("role", name.trim()) })
    }

    private fun moveRole(store: Store<EntityStore>, role: UUID, up: Boolean) {
        val result = groupsApi.moveRole(store, playerRef, groupUuid, role, up)
        // The new order speaks for itself, so only errors get a status line
        refresh((result as? GroupResult.Error)?.toMessage())
    }

    private fun confirmDelete(ref: Ref<EntityStore>, store: Store<EntityStore>, group: GroupComponent, role: UUID) {
        val target = group.findRole(role)
        if (target == null) {
            refresh(GroupResult.Error.ROLE_NOT_FOUND.toMessage())
            return
        }

        val fallback = group.roleOfKind(Kind.MEMBER)?.name.orEmpty()
        navigate(ref, store, ConfirmPage(
            playerRef,
            title = groupsText("ui.roles.deleteconfirm.title"),
            message = groupsText("ui.roles.deleteconfirm.message")
                .param("role", target.name)
                .param("count", group.membersWithRole(role))
                .param("member", fallback),
            onConfirm = { r, s ->
                val result = groupsApi.deleteRole(s, playerRef, groupUuid, role)
                val status = result.toMessage { groupsText("ui.roles.deleted").param("role", target.name) }
                navigate(r, s, GroupRolesPage(playerRef, groupUuid, status))
            },
            onCancel = { r, s -> navigate(r, s, GroupRolesPage(playerRef, groupUuid)) },
        ))
    }

    private companion object {
        const val BACK = "Back"
        const val CREATE = "Create"
        const val MOVE_UP = "MoveUp"
        const val MOVE_DOWN = "MoveDown"
        const val EDIT = "Edit"
        const val DELETE = "Delete"
    }
}
