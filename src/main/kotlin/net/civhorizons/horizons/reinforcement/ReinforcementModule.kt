package net.civhorizons.horizons.reinforcement

import com.hypixel.hytale.component.ComponentType
import net.civhorizons.horizons.Horizons
import net.civhorizons.horizons.reinforcement.component.BlockReinforcementChunkComponent

class ReinforcementModule(val horizons: Horizons) {

    fun setup() {
        LOGGER.atInfo().log("Reinforcement module starting...")

        val blockReinforcementChunkComponent = horizons.chunkStoreRegistry.registerComponent(
            BlockReinforcementChunkComponent::class.java,
            "BlockReinforcementChunkComponent",
            BlockReinforcementChunkComponent.CODEC,
        )
    }

    companion object {
        val LOGGER = Horizons.LOGGER.getSubLogger("ReinforcementModule")
    }
}
