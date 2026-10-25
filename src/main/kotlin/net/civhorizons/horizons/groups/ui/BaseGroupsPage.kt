package net.civhorizons.horizons.groups.ui

import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType
import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage
import com.hypixel.hytale.server.core.ui.builder.EventData
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.civhorizons.horizons.GroupsAPI
import net.civhorizons.horizons.Horizons
import net.civhorizons.horizons.groups.GroupsModule
import net.civhorizons.horizons.groups.groupsText

/**
 * Common behaviour for group pages: a #Status line, navigation, event binding, and error handling.
 *
 * Pages hold identifiers only and re-read groups from the API on every build and event,
 * so the server always validates against current data.
 */
abstract class BaseGroupsPage(
    playerRef: PlayerRef,
    protected var status: Message? = null,
) : InteractiveCustomUIPage<GroupsPageEvent>(playerRef, CustomPageLifetime.CanDismiss, GroupsPageEvent.CODEC) {

    protected val groupsApi: GroupsAPI get() = Horizons.instance.groupsModule.api

    final override fun build(
        ref: Ref<EntityStore>,
        commands: UICommandBuilder,
        events: UIEventBuilder,
        store: Store<EntityStore>,
    ) {
        buildPage(ref, commands, events, store)
        renderStatus(commands)
    }

    protected abstract fun buildPage(
        ref: Ref<EntityStore>,
        commands: UICommandBuilder,
        events: UIEventBuilder,
        store: Store<EntityStore>,
    )

    // A failed action must never break the page. Log it and tell the player.
    @Suppress("TooGenericExceptionCaught")
    final override fun handleDataEvent(ref: Ref<EntityStore>, store: Store<EntityStore>, data: GroupsPageEvent) {
        try {
            handleEvent(ref, store, data)
        } catch (e: Exception) {
            GroupsModule.LOGGER.atSevere().withCause(e)
                .log("Error handling %s action %s", javaClass.simpleName, data.action)
            showStatus(groupsText("error.unexpected"))
        }
    }

    protected abstract fun handleEvent(ref: Ref<EntityStore>, store: Store<EntityStore>, data: GroupsPageEvent)

    protected fun navigate(ref: Ref<EntityStore>, store: Store<EntityStore>, page: BaseGroupsPage) {
        store.getComponent(ref, Player.getComponentType())?.pageManager?.openCustomPage(ref, store, page)
    }

    /**
     * Rebuild the whole page from current data, optionally with a new status.
     */
    protected fun refresh(message: Message? = null) {
        status = message
        rebuild()
    }

    /**
     * Change only the status line.
     */
    protected fun showStatus(message: Message) {
        status = message
        val commands = UICommandBuilder()
        renderStatus(commands)
        sendUpdate(commands, false)
    }

    protected fun bindAction(
        events: UIEventBuilder,
        selector: String,
        action: String,
        target: String? = null,
        capture: String? = null,
    ) {
        val data = eventData(action, target, capture)
        events.addEventBinding(CustomUIEventBindingType.Activating, selector, data, false)
    }

    protected fun bindValueChanged(
        events: UIEventBuilder,
        selector: String,
        action: String,
        target: String? = null,
        capture: String? = null,
    ) {
        val data = eventData(action, target, capture)
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, selector, data, false)
    }

    private fun eventData(action: String, target: String?, capture: String?): EventData {
        val data = EventData.of(GroupsPageEvent.ACTION, action)
        if (target != null) data.append(GroupsPageEvent.TARGET, target)
        if (capture != null) data.append(GroupsPageEvent.VALUE, capture)
        return data
    }

    private fun renderStatus(commands: UICommandBuilder) {
        val message = status
        commands.set("#Status.Visible", message != null)
        if (message != null) {
            commands.set("#Status.Text", message)
        }
    }
}
