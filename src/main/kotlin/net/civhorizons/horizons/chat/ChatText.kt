package net.civhorizons.horizons.chat

import com.hypixel.hytale.server.core.Message

/**
 * A message from horizons-chat.lang. Pass the key without the file prefix.
 */
fun chatTranslation(key: String): Message = Message.translation("horizons-chat.$key")
