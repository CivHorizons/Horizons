package net.civhorizons.horizons.reinforcement.component

import com.hypixel.hytale.codec.Codec
import com.hypixel.hytale.codec.KeyedCodec
import com.hypixel.hytale.codec.builder.BuilderCodec
import com.hypixel.hytale.component.Component
import com.hypixel.hytale.protocol.Vector3i
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import kotlin.collections.forEach

class BlockReinforcementChunkComponent : Component<ChunkStore> {

    // TODO: Supply expected size and load factor, once we know how many reinforcements per chunk
    val blockReinforcementMap = Object2ObjectOpenHashMap<Vector3i, BlockReinforcement>()

    fun reinforceBlock(block: Vector3i): BlockReinforcement {
        this.blockReinforcementMap[block] = BlockReinforcement()
        return this.blockReinforcementMap[block]!!
    }

    fun serialize(): ByteArray {
        val buf = Unpooled.buffer()

        buf.writeByte(SERIALIZATION_VERSION)
        buf.writeInt(blockReinforcementMap.size)

        // TODO: Serialize

        return try {
            ByteBufUtil.getBytes(buf)
        } finally {
            buf.release()
        }
    }

    fun deserialize(data: ByteArray) {
        blockReinforcementMap.clear()
        val buf = Unpooled.wrappedBuffer(data)

        val version = buf.readByte()
        val size = buf.readInt()

        for (i in 0..<size) {
            // TODO: deserialize
        }
    }

    override fun clone() = BlockReinforcementChunkComponent().apply {
        blockReinforcementMap.entries.forEach { (k, v) -> this.blockReinforcementMap[k] = v.clone() }
    }

    companion object {
        const val SERIALIZATION_VERSION = 1
        val CODEC = BuilderCodec.builder(BlockReinforcementChunkComponent::class.java, ::BlockReinforcementChunkComponent)
            .append(
                KeyedCodec("Data", Codec.BYTE_ARRAY), // TODO: Why is this deprecated?
                { chunk: BlockReinforcementChunkComponent, data: ByteArray -> chunk.deserialize(data) },
                BlockReinforcementChunkComponent::serialize
            )
            .documentation("Reinforcement data for chunk")
            .add()
            .build()
    }

    class BlockReinforcement : Cloneable {
        public override fun clone(): BlockReinforcement = BlockReinforcement()
    }
}
