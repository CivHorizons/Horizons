package net.civhorizons.horizons.groups.resource

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Resource
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GroupsResource(
    val groups: ConcurrentHashMap<UUID, GroupComponent> = ConcurrentHashMap<UUID, GroupComponent>()
) : Resource<EntityStore> {

    override fun clone(): Resource<EntityStore> {
        return GroupsResource(ConcurrentHashMap(groups))
    }

    class GroupComponent(
        var uuid: UUID = UUID.randomUUID(),
        var name: String = ""
    ) {

        companion object {
            val CODEC = BuilderCodec.builder(GroupComponent::class.java, ::GroupComponent)
                .append(KeyedCodec("UUID", Codec.UUID_BINARY),
                    { group, uuid -> group.uuid = uuid},
                    { it.uuid }
                ).add()
                .append(KeyedCodec("Name", Codec.STRING),
                    { group, name -> group.name = name },
                    { it.name },
                ).add()
                .build()
        }
    }
}
