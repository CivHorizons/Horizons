package net.civhorizons.horizons.groups.resource

import com.hypixel.hytale.codec.ExtraInfo
import net.civhorizons.horizons.groups.resource.GroupComponent
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroupComponentCodecTest {

    private val owner = UUID.randomUUID()
    private val invitee = UUID.randomUUID()
    private val extraInfo = ExtraInfo.THREAD_LOCAL.get()

    @Test
    fun `round trips invites and usernames`() {
        val group = GroupComponent.createDefault("Test", owner, "Alice")
        group.invites[invitee] = GroupComponent.GroupInviteData(inviter = owner, username = "Bob")

        val encoded = GroupComponent.CODEC.encode(group, extraInfo)
        val decoded = checkNotNull(GroupComponent.CODEC.decode(encoded, extraInfo))

        assertEquals("Test", decoded.name)
        assertEquals("Alice", decoded.membership.getValue(owner).username)
        assertEquals(GroupComponent.GroupInviteData(owner, "Bob"), decoded.invites.getValue(invitee))
    }

    @Test
    fun `round trips role kinds and permissions`() {
        val group = GroupComponent.createDefault("Test", owner, "Alice")

        val encoded = GroupComponent.CODEC.encode(group, extraInfo)
        val decoded = checkNotNull(GroupComponent.CODEC.decode(encoded, extraInfo))

        assertEquals(group.roles.map { it.kind }, decoded.roles.map { it.kind })
        assertEquals(group.roles.map { it.permissions }, decoded.roles.map { it.permissions })
    }

    @Test
    fun `round trips the deleted flag`() {
        val group = GroupComponent.createDefault("Test", owner, "Alice")
        group.deleted = true

        val encoded = GroupComponent.CODEC.encode(group, extraInfo)
        val decoded = checkNotNull(GroupComponent.CODEC.decode(encoded, extraInfo))

        assertTrue(decoded.deleted)
    }

    @Test
    fun `groups saved before soft delete load as not deleted`() {
        val group = GroupComponent.createDefault("Test", owner, "Alice")

        val encoded = GroupComponent.CODEC.encode(group, extraInfo).apply { remove("Deleted") }
        val decoded = checkNotNull(GroupComponent.CODEC.decode(encoded, extraInfo))

        assertFalse(decoded.deleted)
    }
}
