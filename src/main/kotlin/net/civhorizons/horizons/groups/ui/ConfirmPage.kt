package net.civhorizons.horizons.groups.ui

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.civhorizons.horizons.groups.groupsTranslation

typealias PageAction = (Ref<EntityStore>, Store<EntityStore>) -> Unit

/**
 * Asks the player to confirm a destructive action. The caller decides where each choice leads.
 */
class ConfirmPage(
    playerRef: PlayerRef,
    private val title: Message,
    private val message: Message,
    private val onConfirm: PageAction,
    private val onCancel: PageAction,
) : BaseGroupsPage(playerRef) {

    override fun buildPage(
        ref: Ref<EntityStore>,
        commands: UICommandBuilder,
        events: UIEventBuilder,
        store: Store<EntityStore>,
    ) {
        commands.append("Pages/Groups/ConfirmPage.ui")
        commands.set("#PageTitle.Text", title)
        commands.set("#Message.Text", message)
        commands.set("#ConfirmButton.Text", groupsTranslation("ui.confirm.confirm"))
        commands.set("#CancelButton.Text", groupsTranslation("ui.common.cancel"))

        bindAction(events, "#ConfirmButton", CONFIRM)
        bindAction(events, "#CancelButton", CANCEL)
    }

    override fun handleEvent(ref: Ref<EntityStore>, store: Store<EntityStore>, data: GroupsPageEvent) {
        when (data.action) {
            CONFIRM -> onConfirm(ref, store)
            CANCEL -> onCancel(ref, store)
        }
    }

    private companion object {
        const val CONFIRM = "Confirm"
        const val CANCEL = "Cancel"
    }
}
