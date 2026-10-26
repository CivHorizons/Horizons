package net.civhorizons.horizons.groups.resource

import net.civhorizons.horizons.groups.GroupResult
import net.civhorizons.horizons.groups.resource.GroupsResource.GroupComponent
import net.civhorizons.horizons.groups.resource.GroupsResource.GroupComponent.GroupRole.Permission
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupComponentTest {

    private val owner = UUID.randomUUID()
    private val member = UUID.randomUUID()
    private val outsider = UUID.randomUUID()

    private fun group(): GroupComponent {
        val group = GroupComponent.createDefault("Test", owner, "Alice")
        group.invites[member] = GroupComponent.GroupInviteData(owner, "Bob")
        group.acceptInvite(member, "Bob")
        return group
    }

    private fun GroupComponent.role(name: String) = roles.first { it.name == name }

    // --- permissions ---

    @Test
    fun `permission requires a true value`() {
        val group = group()
        group.roles = group.roles.map {
            if (it.name == "Owner") it.copy(permissions = mapOf(Permission.MANAGE_GROUP to false)) else it
        }.toTypedArray()

        assertFalse(group.hasPermission(owner, Permission.MANAGE_GROUP))
    }

    @Test
    fun `non managers cannot manage`() {
        val group = group()

        assertEquals(GroupResult.Error.NO_PERMISSION, group.invite(member, outsider, "Carol"))
        assertEquals(GroupResult.Error.NO_PERMISSION, group.revokeInvite(member, outsider))
        assertEquals(GroupResult.Error.NO_PERMISSION, group.removeMember(member, owner))
        assertEquals(GroupResult.Error.NO_PERMISSION, group.setRole(member, member, group.role("Owner").uuid))
        assertEquals(GroupResult.Error.NO_PERMISSION, group.canDelete(member))
        assertEquals(GroupResult.Success, group.canDelete(owner))
    }

    // --- invites ---

    @Test
    fun `invite records the invite`() {
        val group = group()

        assertEquals(GroupResult.Success, group.invite(owner, outsider, "Carol"))
        assertEquals(GroupComponent.GroupInviteData(owner, "Carol"), group.invites[outsider])
    }

    @Test
    fun `cannot invite a member or invite twice`() {
        val group = group()

        assertEquals(GroupResult.Error.ALREADY_MEMBER, group.invite(owner, member, "Bob"))
        group.invite(owner, outsider, "Carol")
        assertEquals(GroupResult.Error.ALREADY_INVITED, group.invite(owner, outsider, "Carol"))
    }

    @Test
    fun `accepting adds the member role and removes the invite`() {
        val group = group()
        group.invite(owner, outsider, "Carol")

        assertEquals(GroupResult.Success, group.acceptInvite(outsider, "Carol"))
        assertFalse(group.invites.containsKey(outsider))
        assertEquals(setOf(group.role("Member").uuid), group.membership.getValue(outsider).roles)
        assertEquals("Carol", group.membership.getValue(outsider).username)
    }

    @Test
    fun `accept and decline require an invite`() {
        val group = group()

        assertEquals(GroupResult.Error.NOT_INVITED, group.acceptInvite(outsider, "Carol"))
        assertEquals(GroupResult.Error.NOT_INVITED, group.declineInvite(outsider))
        assertFalse(group.isMember(outsider))
    }

    @Test
    fun `decline and revoke remove the invite`() {
        val group = group()
        group.invite(owner, outsider, "Carol")
        assertEquals(GroupResult.Success, group.declineInvite(outsider))
        assertFalse(group.invites.containsKey(outsider))

        group.invite(owner, outsider, "Carol")
        assertEquals(GroupResult.Success, group.revokeInvite(owner, outsider))
        assertFalse(group.invites.containsKey(outsider))
        assertEquals(GroupResult.Error.NOT_INVITED, group.revokeInvite(owner, outsider))
    }

    // --- members and roles ---

    @Test
    fun `remove member`() {
        val group = group()

        assertEquals(GroupResult.Success, group.removeMember(owner, member))
        assertFalse(group.isMember(member))
        assertEquals(GroupResult.Error.NOT_MEMBER, group.removeMember(owner, member))
    }

    @Test
    fun `cannot remove the last owner`() {
        val group = group()

        assertEquals(GroupResult.Error.LAST_OWNER, group.removeMember(owner, owner))
        assertTrue(group.isMember(owner))
    }

    @Test
    fun `cannot demote the last owner`() {
        val group = group()

        assertEquals(GroupResult.Error.LAST_OWNER, group.setRole(owner, owner, group.role("Member").uuid))
        assertTrue(group.hasPermission(owner, Permission.MANAGE_GROUP))
    }

    @Test
    fun `can demote an owner when another owner remains`() {
        val group = group()

        assertEquals(GroupResult.Success, group.setRole(owner, member, group.role("Owner").uuid))
        assertEquals(GroupResult.Success, group.setRole(owner, owner, group.role("Member").uuid))
        assertFalse(group.hasPermission(owner, Permission.MANAGE_GROUP))
        assertTrue(group.hasPermission(member, Permission.MANAGE_GROUP))
    }

    @Test
    fun `setRole leaves exactly one role`() {
        val group = group()

        group.setRole(owner, member, group.role("Owner").uuid)

        assertEquals(setOf(group.role("Owner").uuid), group.membership.getValue(member).roles)
        assertEquals("Owner", group.roleOf(member)?.name)
    }

    @Test
    fun `setRole validates target and role`() {
        val group = group()

        assertEquals(GroupResult.Error.NOT_MEMBER, group.setRole(owner, outsider, group.role("Owner").uuid))
        assertEquals(GroupResult.Error.ROLE_NOT_FOUND, group.setRole(owner, member, UUID.randomUUID()))
    }

    // --- names ---

    @Test
    fun `validateName trims and bounds length`() {
        val max = GroupComponent.MAX_NAME_LENGTH
        assertEquals("Town", GroupComponent.validateName("  Town  "))
        assertNull(GroupComponent.validateName("   "))
        assertNull(GroupComponent.validateName("x".repeat(max + 1)))
        assertEquals("x".repeat(max), GroupComponent.validateName("x".repeat(max)))
    }

    @Test
    fun `memberName prefers stored username`() {
        assertEquals("Bob", group().memberName(member))
    }
}
