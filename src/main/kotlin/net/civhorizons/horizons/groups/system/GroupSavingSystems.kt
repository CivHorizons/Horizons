package net.civhorizons.horizons.groups.system

import com.hypixel.hytale.component.Store
import com.hypixel.hytale.component.system.StoreSystem
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import net.civhorizons.horizons.Horizons
import net.civhorizons.horizons.groups.GroupsModule
import net.civhorizons.horizons.util.saveAll

class GroupSavingSystems {

    class WorldRemovedSystem : StoreSystem<EntityStore>() {
        override fun onSystemAddedToStore(store: Store<EntityStore>) {
            // NO-OP
        }

        override fun onSystemRemovedFromStore(store: Store<EntityStore>) {
            LOGGER.atInfo().log("Saving groups...")

            val groupsResource = store.getResource(Horizons.instance.groupsModule.groupsResourceType)
            Horizons.instance.groupsModule.groupStorage.saveAll(groupsResource.groups)
        }
    }

    companion object {
        val LOGGER = GroupsModule.LOGGER.getSubLogger("GroupSavingSystems")
    }
}
