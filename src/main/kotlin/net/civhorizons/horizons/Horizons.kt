package net.civhorizons.horizons

import com.hypixel.hytale.logger.HytaleLogger
import com.hypixel.hytale.server.core.Constants
import com.hypixel.hytale.server.core.HytaleServer
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import net.civhorizons.horizons.chat.ChatModule
import net.civhorizons.horizons.groups.GroupsModule
import net.civhorizons.horizons.reinforcement.ReinforcementModule

class Horizons(init: JavaPluginInit) : JavaPlugin(init) {

    val groupsModule = GroupsModule(this)
    val reinforcementModule = ReinforcementModule(this)
    val chatModule = ChatModule(this)

    init {
        instance = this
    }

    override fun setup() {
        LOGGER.atInfo().log("Horizons is starting...")

        groupsModule.setup()
        reinforcementModule.setup()
        chatModule.setup()
    }

    companion object {
        val LOGGER = HytaleLogger.get("Horizons")

        lateinit var instance: Horizons
            private set
    }
}
