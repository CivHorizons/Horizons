package net.civhorizons.horizons.groups

import com.hypixel.hytale.server.core.Message

/**
 * A message from horizons-groups.lang. Pass the key without the file prefix.
 */
fun groupsText(key: String): Message = Message.translation("horizons-groups.$key")

fun GroupResult.Error.toMessage(): Message = Message.translation(translationKey)

/**
 * The error's message, or [onSuccess] if the operation succeeded.
 */
inline fun GroupResult.toMessage(onSuccess: () -> Message): Message = when (this) {
    GroupResult.Success -> onSuccess()
    is GroupResult.Error -> toMessage()
}
