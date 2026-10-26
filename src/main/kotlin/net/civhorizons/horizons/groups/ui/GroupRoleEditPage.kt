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
import net.civhorizons.horizons.groups.resource.GroupComponent
import net.civhorizons.horizons.groups.resource.GroupComponent.GroupRole
import net.civhorizons.horizons.groups.resource.GroupComponent.GroupRole.Kind
import net.civhorizons.horizons.groups.resource.GroupComponent.GroupRole.Permission
import net.civhorizons.horizons.groups.toMessage
import java.util.UUID

/**
 * Rename one role and toggle its permissions. Owner permissions are shown but fixed.
 */
class GroupRoleEditPage(
    playerRef: PlayerRef,
    val groupUuid: UUID,
    val roleUuid: UUID,
    status: Message? = null,
) : BaseGroupsPage(playerRef, status) {

    override fun buildPage(
        ref: Ref<EntityStore>,
        commands: UICommandBuilder,
        events: UIEventBuilder,
        store: Store<EntityStore>,
    ) {
        commands.append("Pages/Groups/GroupRoleEditPage.ui")
        commands.set("#BackButton.Text", groupsText("ui.common.back"))
        bindAction(events, "#BackButton", BACK)

        val group = groupsApi.getMemberGroup(playerRef, groupUuid)
        val role = group?.findRole(roleUuid)
        if (group == null || role == null) {
            commands.set("#Body.Visible", false)
            val error = if (group == null) GroupResult.Error.NOT_FOUND else GroupResult.Error.ROLE_NOT_FOUND
            status = error.toMessage()
            return
        }

        val canEdit = group.canManageRole(playerRef.uuid, roleUuid)
        commands.set("#PageTitle.Text", role.name)
        commands.set("#PermissionsHeader.Text", groupsText("ui.roleedit.permissions"))
        commands.set("#OwnerNote.Text", groupsText("ui.roleedit.ownernote"))
        commands.set("#OwnerNote.Visible", role.kind == Kind.OWNER)

        commands.set("#RenameSection.Visible", canEdit)
        if (canEdit) {
            commands.set("#NameInput.Value", role.name)
            commands.set("#NameInput.PlaceholderText", groupsText("ui.roleedit.name"))
            commands.set("#RenameButton.Text", groupsText("ui.roleedit.rename"))
            bindAction(events, "#RenameButton", RENAME, capture = "#NameInput.Value")
        }

        buildPermissions(group, role, canEdit, commands, events)
    }

    private fun buildPermissions(
        group: GroupComponent,
        role: GroupRole,
        canEdit: Boolean,
        commands: UICommandBuilder,
        events: UIEventBuilder,
    ) {
        Permission.entries.forEachIndexed { i, permission ->
            val row = "#PermissionList[$i]"
            val key = permission.name.lowercase().replace("_", "")
            val granted = role.kind == Kind.OWNER || role.permissions[permission] == true
            // Nobody can hand out a permission they don't hold themselves
            val editable = canEdit && role.kind != Kind.OWNER && group.hasPermission(playerRef.uuid, permission)

            commands.append("#PermissionList", "Pages/Groups/PermissionRow.ui")
            commands.set("$row #Name.Text", groupsText("permission.$key.name"))
            commands.set("$row #Description.Text", groupsText("permission.$key.description"))
            commands.set("$row #CheckBox.Value", granted)
            commands.set("$row #CheckBox.Disabled", !editable)
            // Bound even when disabled: if the client toggles anyway, the API rejects it and the refresh snaps it back
            bindValueChanged(events, "$row #CheckBox", TOGGLE, permission.name)
        }
    }

    override fun handleEvent(ref: Ref<EntityStore>, store: Store<EntityStore>, data: GroupsPageEvent) {
        val group = groupsApi.getMemberGroup(playerRef, groupUuid)
        if (group == null) {
            navigate(ref, store, GroupListPage(playerRef, GroupResult.Error.NOT_FOUND.toMessage()))
            return
        }
        val role = group.findRole(roleUuid)
        if (role == null) {
            navigate(ref, store, GroupRolesPage(playerRef, groupUuid, GroupResult.Error.ROLE_NOT_FOUND.toMessage()))
            return
        }

        when (data.action) {
            BACK -> navigate(ref, store, GroupRolesPage(playerRef, groupUuid))
            RENAME -> rename(data.value.orEmpty())
            TOGGLE -> Permission.entries.find { it.name == data.target }?.let { toggle(role, it) }
        }
    }

    private fun rename(name: String) {
        val result = groupsApi.renameRole(playerRef, groupUuid, roleUuid, name)
        refresh(result.toMessage { groupsText("ui.roleedit.renamed").param("role", name.trim()) })
    }

    private fun toggle(role: GroupRole, permission: Permission) {
        // Flip the stored value rather than trusting the client's checkbox state
        val granted = role.permissions[permission] != true
        val result = groupsApi.setRolePermission(playerRef, groupUuid, roleUuid, permission, granted)
        // Refresh either way so a rejected toggle snaps the checkbox back
        refresh((result as? GroupResult.Error)?.toMessage())
    }

    private companion object {
        const val BACK = "Back"
        const val RENAME = "Rename"
        const val TOGGLE = "Toggle"
    }
}
