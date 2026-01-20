package net.civhorizons.horizons.groups.ui

import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.component.Store
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore

class GroupsPage(playerRef: PlayerRef) : InteractiveCustomUIPage<GroupsPage.GroupsEventData>(playerRef,
    CustomPageLifetime.CanDismiss, GroupsEventData.CODEC
) {
    override fun build(
        ref: Ref<EntityStore>,
        commands: UICommandBuilder,
        events: UIEventBuilder,
        store: Store<EntityStore>
    ) {
        commands.append("Pages/GroupsPage.ui")
        commands.set("#WelcomeText.Text", "Groups for " + playerRef.username);
    }

    class GroupsEventData {

        companion object {
            val CODEC = BuilderCodec.builder(GroupsEventData::class.java, ::GroupsEventData)
                .build()
        }
    }
}
