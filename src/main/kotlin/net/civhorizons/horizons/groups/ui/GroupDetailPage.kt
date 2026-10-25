package net.civhorizons.horizons.groups.ui

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.universe.PlayerRef
import java.util.UUID

// Replaced in Task 6
class GroupDetailPage(playerRef: PlayerRef, val groupUuid: UUID, status: Message? = null) : GroupListPage(playerRef, status)
