package net.civhorizons.horizons.groups

import com.hypixel.hytale.server.core.Message

/**
 * A message from horizons-groups.lang. Pass the key without the file prefix.
 */
fun groupsTranslation(key: String): Message = Message.translation("horizons-groups.$key")

fun GroupActionResult.Error.toMessage(): Message = Message.translation(translationKey)

/**
 * The error's message, or [onSuccess] if the operation succeeded.
 */
inline fun GroupActionResult.toMessage(onSuccess: () -> Message): Message = when (this) {
    GroupActionResult.Success -> onSuccess()
    is GroupActionResult.Error -> toMessage()
}
