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
import net.civhorizons.horizons.groups.toMessage

class GroupCreatePage(playerRef: PlayerRef, status: Message? = null) : BaseGroupsPage(playerRef, status) {

    override fun buildPage(
        ref: Ref<EntityStore>,
        commands: UICommandBuilder,
        events: UIEventBuilder,
        store: Store<EntityStore>,
    ) {
        commands.append("Pages/Groups/GroupCreatePage.ui")
        commands.set("#PageTitle.Text", groupsText("ui.create.title"))
        commands.set("#NameInput.PlaceholderText", groupsText("ui.create.name"))
        commands.set("#CreateButton.Text", groupsText("ui.create.submit"))
        commands.set("#CancelButton.Text", groupsText("ui.common.cancel"))

        bindAction(events, "#CreateButton", CREATE, capture = "#NameInput.Value")
        bindAction(events, "#CancelButton", CANCEL)
    }

    override fun handleEvent(ref: Ref<EntityStore>, store: Store<EntityStore>, data: GroupsPageEvent) {
        when (data.action) {
            CREATE -> {
                val group = groupsApi.createNewGroup(playerRef, data.value.orEmpty())
                if (group == null) {
                    showStatus(GroupResult.Error.INVALID_NAME.toMessage())
                    return
                }
                val created = groupsText("ui.create.success").param("name", group.name)
                navigate(ref, store, GroupDetailPage(playerRef, group.uuid, created))
            }
            CANCEL -> navigate(ref, store, GroupListPage(playerRef))
        }
    }

    private companion object {
        const val CREATE = "Create"
        const val CANCEL = "Cancel"
    }
}
