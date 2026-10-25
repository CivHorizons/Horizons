package net.civhorizons.horizons.groups.ui

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import java.util.UUID

/**
 * Event data shared by all group pages. Each page defines its own action names.
 *
 * - [action]: what was clicked
 * - [target]: the row it was clicked on (a group or player UUID)
 * - [value]: a captured UI value, such as a text field or dropdown
 */
class GroupsPageEvent {
    var action: String = ""
    var target: String? = null
    var value: String? = null

    fun targetUuid(): UUID? = target?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    companion object {
        const val ACTION = "Action"
        const val TARGET = "Target"
        const val VALUE = "@Value"

        val CODEC: BuilderCodec<GroupsPageEvent> = BuilderCodec.builder(GroupsPageEvent::class.java, ::GroupsPageEvent)
            .append(KeyedCodec(ACTION, Codec.STRING), { event, action -> event.action = action }, { it.action }).add()
            .append(KeyedCodec(TARGET, Codec.STRING), { event, target -> event.target = target }, { it.target }).add()
            .append(KeyedCodec(VALUE, Codec.STRING), { event, value -> event.value = value }, { it.value }).add()
            .build()
    }
}
