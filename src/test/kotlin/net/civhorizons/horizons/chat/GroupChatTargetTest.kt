package net.civhorizons.horizons.chat

import net.civhorizons.horizons.chat.GroupChat.Target
import net.civhorizons.horizons.groups.resource.GroupComponent
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GroupChatTargetTest {

    private val owner = UUID.randomUUID()
    private val miners = group("Miners")
    private val minersGuild = group("Miners Guild")
    private val farmers = group("Farmers")
    private val groups = listOf(miners, minersGuild, farmers)

    private fun group(name: String) = GroupComponent.createDefault(name, owner, "Alice")

    @Test
    fun `finds a group with no message`() {
        assertEquals(Target.Found(farmers, ""), GroupChat.findTarget("farmers", groups))
    }

    @Test
    fun `splits the message from the group name`() {
        assertEquals(Target.Found(farmers, "hello there"), GroupChat.findTarget("Farmers hello there", groups))
    }

    @Test
    fun `prefers the longest matching name`() {
        assertEquals(Target.Found(minersGuild, "hi"), GroupChat.findTarget("miners guild hi", groups))
        assertEquals(Target.Found(miners, "go"), GroupChat.findTarget("Miners go", groups))
    }

    @Test
    fun `needs a word break after the name`() {
        assertIs<Target.NotFound>(GroupChat.findTarget("Farmersville hi", groups))
    }

    @Test
    fun `reports unknown groups by their first word`() {
        assertEquals(Target.NotFound("Fishers"), GroupChat.findTarget("Fishers hi", groups))
    }

    @Test
    fun `reports names shared by more than one group`() {
        val twin = group("Farmers")
        assertEquals(Target.Ambiguous("Farmers"), GroupChat.findTarget("Farmers hi", groups + twin))
    }
}
