package net.civhorizons.horizons.groups.resource

import net.civhorizons.horizons.groups.resource.GroupsResource.GroupComponent
import org.bson.BsonDocument
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupComponentCodecTest {

    private val owner = UUID.randomUUID()
    private val invitee = UUID.randomUUID()

    @Test
    fun `round trips invites and usernames`() {
        val group = GroupComponent.createDefault("Test", owner, "Alice")
        group.invites[invitee] = GroupComponent.GroupInviteData(inviter = owner, username = "Bob")

        val decoded = checkNotNull(GroupComponent.CODEC.decode(GroupComponent.CODEC.encode(group)))

        assertEquals("Test", decoded.name)
        assertEquals("Alice", decoded.membership.getValue(owner).username)
        assertEquals(GroupComponent.GroupInviteData(owner, "Bob"), decoded.invites.getValue(invitee))
    }

    @Test
    fun `loads legacy json without invites or usernames`() {
        // Shape of a group saved before invites existed (copied from run/universe/horizons/groups)
        val legacy = BsonDocument.parse(
            """
            {
              "UUID": { "${'$'}binary": "Jk0AmAqfR9qXh34oJpcf+w==", "${'$'}type": "04" },
              "Name": "test",
              "Roles": [
                { "UUID": "0882353a-0d4b-457e-bfe3-93a702d07c8a", "Name": "Owner", "Permissions": { "ManageGroup": true } },
                { "UUID": "b98b7ab8-a45e-4347-af7b-d0bba0f822ca", "Name": "Member", "Permissions": {} }
              ],
              "Membership": {
                "bb46b88f-099b-4b6d-aa4e-f1611195255c": { "Roles": ["0882353a-0d4b-457e-bfe3-93a702d07c8a"] }
              }
            }
            """.trimIndent()
        )

        val decoded = checkNotNull(GroupComponent.CODEC.decode(legacy))

        assertEquals("test", decoded.name)
        assertTrue(decoded.invites.isEmpty())
        val member = decoded.membership.getValue(UUID.fromString("bb46b88f-099b-4b6d-aa4e-f1611195255c"))
        assertEquals("", member.username)
    }

    @Test
    fun `createDefault makes creator the only owner`() {
        val group = GroupComponent.createDefault("Test", owner, "Alice")

        assertEquals(listOf("Owner", "Member"), group.roles.map { it.name })
        val ownerRole = group.roles.first { it.name == "Owner" }
        assertEquals(setOf(ownerRole.uuid), group.membership.getValue(owner).roles)
        assertEquals("Alice", group.membership.getValue(owner).username)
    }
}
