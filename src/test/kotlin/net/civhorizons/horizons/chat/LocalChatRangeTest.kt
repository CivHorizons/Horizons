package net.civhorizons.horizons.chat

import org.joml.Vector3d
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalChatRangeTest {

    private val world = UUID.randomUUID()
    private val otherWorld = UUID.randomUUID()
    private val origin = Vector3d(0.0, 64.0, 0.0)

    @Test
    fun `hears a player within range`() {
        assertTrue(LocalChatListener.canHear(world, origin, world, Vector3d(600.0, 64.0, 600.0)))
    }

    @Test
    fun `hears a player exactly at the range`() {
        val edge = Vector3d(LocalChatListener.RANGE, 64.0, 0.0)
        assertTrue(LocalChatListener.canHear(world, origin, world, edge))
    }

    @Test
    fun `does not hear a player beyond range`() {
        assertFalse(LocalChatListener.canHear(world, origin, world, Vector3d(800.0, 64.0, 800.0)))
    }

    @Test
    fun `does not hear a player in another world`() {
        assertFalse(LocalChatListener.canHear(world, origin, otherWorld, origin))
    }

    @Test
    fun `does not hear a player with no world`() {
        assertFalse(LocalChatListener.canHear(world, origin, null, origin))
    }
}
