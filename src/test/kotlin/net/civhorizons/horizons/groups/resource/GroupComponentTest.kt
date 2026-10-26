package net.civhorizons.horizons.groups.resource

import net.civhorizons.horizons.groups.GroupActionResult
import net.civhorizons.horizons.groups.resource.GroupComponent
import net.civhorizons.horizons.groups.resource.GroupComponent.GroupRole
import net.civhorizons.horizons.groups.resource.GroupComponent.GroupRole.Kind
import net.civhorizons.horizons.groups.resource.GroupComponent.GroupRole.Permission
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupComponentTest {

    private val owner = UUID.randomUUID()
    private val admin = UUID.randomUUID()
    private val member = UUID.randomUUID()
    private val outsider = UUID.randomUUID()

    /**
     * Owner "Alice", Admin "Dana", Member "Bob".
     */
    private fun group(): GroupComponent {
        val group = GroupComponent.createDefault("Test", owner, "Alice")
        group.join(admin, "Dana")
        group.join(member, "Bob")
        group.setRole(owner, admin, group.role("Admin").uuid)
        return group
    }

    private fun GroupComponent.join(player: UUID, name: String) {
        invites[player] = GroupComponent.GroupInviteData(owner, name)
        acceptInvite(player, name)
    }

    private fun GroupComponent.role(name: String) = roles.first { it.name == name }

    /**
     * Insert a custom role just above Member without going through createRole.
     */
    private fun GroupComponent.addRole(name: String, vararg permissions: Permission): GroupRole {
        val role = GroupRole(name = name, permissions = permissions.associateWith { true })
        roles = roles.toMutableList().apply { add(lastIndex, role) }.toTypedArray()
        return role
    }

    // --- defaults ---

    @Test
    fun `createDefault makes Owner, Admin, Member with the creator as Owner`() {
        val group = GroupComponent.createDefault("Test", owner, "Alice")

        assertEquals(listOf("Owner", "Admin", "Member"), group.roles.map { it.name })
        assertEquals(listOf(Kind.OWNER, Kind.CUSTOM, Kind.MEMBER), group.roles.map { it.kind })
        assertEquals(
            setOf(Permission.INVITE_MEMBERS, Permission.REMOVE_MEMBERS, Permission.ASSIGN_ROLES),
            group.role("Admin").permissions.filterValues { it }.keys,
        )
        assertTrue(group.role("Member").permissions.none { it.value })
        assertEquals(setOf(group.role("Owner").uuid), group.membership.getValue(owner).roles)
        assertEquals("Alice", group.membership.getValue(owner).username)
    }

    // --- permissions ---

    @Test
    fun `owner holds every permission even with an empty map`() {
        val group = group()
        group.role("Owner").permissions = mapOf()

        Permission.entries.forEach { assertTrue(group.hasPermission(owner, it)) }
    }

    @Test
    fun `permission requires a true value`() {
        val group = group()
        group.role("Admin").permissions = mapOf(Permission.INVITE_MEMBERS to false)

        assertFalse(group.hasPermission(admin, Permission.INVITE_MEMBERS))
    }

    @Test
    fun `outsiders have no permissions`() {
        assertFalse(group().hasPermission(outsider, Permission.INVITE_MEMBERS))
    }

    @Test
    fun `members without permissions cannot manage`() {
        val group = group()

        assertEquals(GroupActionResult.Error.NO_PERMISSION, group.invite(member, outsider, "Carol"))
        assertEquals(GroupActionResult.Error.NO_PERMISSION, group.revokeInvite(member, outsider))
        assertEquals(GroupActionResult.Error.NO_PERMISSION, group.removeMember(member, owner))
        assertEquals(GroupActionResult.Error.NO_PERMISSION, group.setRole(member, admin, group.role("Member").uuid))
        assertEquals(GroupActionResult.Error.NO_PERMISSION, group.canDelete(member))
    }

    @Test
    fun `granting a permission to Member lets members use it`() {
        val group = group()
        group.role("Member").permissions = mapOf(Permission.INVITE_MEMBERS to true)

        assertEquals(GroupActionResult.Success, group.invite(member, outsider, "Carol"))
    }

    @Test
    fun `delete group requires DELETE_GROUP`() {
        val group = group()

        assertEquals(GroupActionResult.Error.NO_PERMISSION, group.canDelete(admin))
        assertEquals(GroupActionResult.Success, group.canDelete(owner))
    }

    // --- ranking ---

    @Test
    fun `owners outrank every role, others only roles below their own`() {
        val group = group()

        group.roles.forEach { assertTrue(group.outranksRole(owner, it.uuid)) }
        assertFalse(group.outranksRole(admin, group.role("Owner").uuid))
        assertFalse(group.outranksRole(admin, group.role("Admin").uuid))
        assertTrue(group.outranksRole(admin, group.role("Member").uuid))
        assertFalse(group.outranksRole(admin, UUID.randomUUID()))
        assertFalse(group.outranksRole(outsider, group.role("Member").uuid))
    }

    @Test
    fun `nobody outranks themselves as a member`() {
        val group = group()

        assertFalse(group.outranksMember(owner, owner))
        assertTrue(group.outranksMember(owner, admin))
        assertTrue(group.outranksMember(admin, member))
        assertFalse(group.outranksMember(admin, owner))
    }

    @Test
    fun `membersWithRole counts holders`() {
        val group = group()

        assertEquals(1, group.membersWithRole(group.role("Admin").uuid))
        assertEquals(0, group.membersWithRole(UUID.randomUUID()))
    }

    // --- invites ---

    @Test
    fun `admins can invite`() {
        val group = group()

        assertEquals(GroupActionResult.Success, group.invite(admin, outsider, "Carol"))
        assertEquals(GroupComponent.GroupInviteData(admin, "Carol"), group.invites[outsider])
        assertEquals(GroupActionResult.Success, group.revokeInvite(admin, outsider))
    }

    @Test
    fun `cannot invite a member or invite twice`() {
        val group = group()

        assertEquals(GroupActionResult.Error.ALREADY_MEMBER, group.invite(owner, member, "Bob"))
        group.invite(owner, outsider, "Carol")
        assertEquals(GroupActionResult.Error.ALREADY_INVITED, group.invite(owner, outsider, "Carol"))
    }

    @Test
    fun `accepting adds the member role and removes the invite`() {
        val group = group()
        group.invite(owner, outsider, "Carol")

        assertEquals(GroupActionResult.Success, group.acceptInvite(outsider, "Carol"))
        assertFalse(group.invites.containsKey(outsider))
        assertEquals(setOf(group.role("Member").uuid), group.membership.getValue(outsider).roles)
        assertEquals("Carol", group.membership.getValue(outsider).username)
    }

    @Test
    fun `accepting finds Member by kind, not name`() {
        val group = group()
        group.role("Member").name = "Citizen"
        group.invite(owner, outsider, "Carol")

        assertEquals(GroupActionResult.Success, group.acceptInvite(outsider, "Carol"))
        assertEquals(Kind.MEMBER, group.roleOf(outsider)?.kind)
    }

    @Test
    fun `accept and decline require an invite`() {
        val group = group()

        assertEquals(GroupActionResult.Error.NOT_INVITED, group.acceptInvite(outsider, "Carol"))
        assertEquals(GroupActionResult.Error.NOT_INVITED, group.declineInvite(outsider))
        assertFalse(group.isMember(outsider))
    }

    @Test
    fun `decline and revoke remove the invite`() {
        val group = group()
        group.invite(owner, outsider, "Carol")
        assertEquals(GroupActionResult.Success, group.declineInvite(outsider))
        assertFalse(group.invites.containsKey(outsider))

        group.invite(owner, outsider, "Carol")
        assertEquals(GroupActionResult.Success, group.revokeInvite(owner, outsider))
        assertFalse(group.invites.containsKey(outsider))
        assertEquals(GroupActionResult.Error.NOT_INVITED, group.revokeInvite(owner, outsider))
    }

    // --- removing members ---

    @Test
    fun `admins remove members below them`() {
        val group = group()

        assertEquals(GroupActionResult.Success, group.removeMember(admin, member))
        assertFalse(group.isMember(member))
        assertEquals(GroupActionResult.Error.NOT_MEMBER, group.removeMember(admin, member))
    }

    @Test
    fun `admins cannot remove owners or other admins`() {
        val group = group()
        group.join(outsider, "Carol")
        group.setRole(owner, outsider, group.role("Admin").uuid)

        assertEquals(GroupActionResult.Error.NO_PERMISSION, group.removeMember(admin, owner))
        assertEquals(GroupActionResult.Error.NO_PERMISSION, group.removeMember(admin, outsider))
    }

    @Test
    fun `nobody can remove themselves, so the last owner stays`() {
        val group = group()

        assertEquals(GroupActionResult.Error.NO_PERMISSION, group.removeMember(owner, owner))
        assertTrue(group.isMember(owner))
    }

    @Test
    fun `owners can remove other owners`() {
        val group = group()
        group.setRole(owner, admin, group.role("Owner").uuid)

        assertEquals(GroupActionResult.Success, group.removeMember(admin, owner))
    }

    // --- assigning roles ---

    @Test
    fun `admins assign only roles below their own`() {
        val group = group()
        val builder = group.addRole("Builder")

        assertEquals(GroupActionResult.Success, group.setRole(admin, member, builder.uuid))
        assertEquals(GroupActionResult.Error.NO_PERMISSION, group.setRole(admin, member, group.role("Admin").uuid))
        assertEquals(GroupActionResult.Error.NO_PERMISSION, group.setRole(admin, member, group.role("Owner").uuid))
    }

    @Test
    fun `admins cannot change the role of owners`() {
        val group = group()

        assertEquals(GroupActionResult.Error.NO_PERMISSION, group.setRole(admin, owner, group.role("Member").uuid))
    }

    @Test
    fun `nobody can change their own role, so the last owner stays`() {
        val group = group()

        assertEquals(GroupActionResult.Error.NO_PERMISSION, group.setRole(owner, owner, group.role("Member").uuid))
        assertEquals(Kind.OWNER, group.roleOf(owner)?.kind)
    }

    @Test
    fun `owners can promote to owner and demote other owners`() {
        val group = group()

        assertEquals(GroupActionResult.Success, group.setRole(owner, member, group.role("Owner").uuid))
        assertEquals(GroupActionResult.Success, group.setRole(member, owner, group.role("Member").uuid))
        assertEquals(Kind.MEMBER, group.roleOf(owner)?.kind)
        assertEquals(Kind.OWNER, group.roleOf(member)?.kind)
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

        assertEquals(GroupActionResult.Error.NOT_MEMBER, group.setRole(owner, outsider, group.role("Owner").uuid))
        assertEquals(GroupActionResult.Error.ROLE_NOT_FOUND, group.setRole(owner, member, UUID.randomUUID()))
    }

    // --- managing roles ---

    /**
     * group() plus MANAGE_ROLES for Admin.
     */
    private fun managedGroup(): GroupComponent {
        val group = group()
        group.role("Admin").permissions += Permission.MANAGE_ROLES to true
        return group
    }

    @Test
    fun `managing roles requires MANAGE_ROLES`() {
        val group = group()
        val builder = group.addRole("Builder")

        assertEquals(GroupActionResult.Error.NO_PERMISSION, group.createRole(admin, "Guard"))
        assertEquals(GroupActionResult.Error.NO_PERMISSION, group.renameRole(admin, builder.uuid, "Guard"))
        assertEquals(
            GroupActionResult.Error.NO_PERMISSION,
            group.setRolePermission(admin, builder.uuid, Permission.INVITE_MEMBERS, true),
        )
        assertEquals(GroupActionResult.Error.NO_PERMISSION, group.deleteRole(admin, builder.uuid))
        assertFalse(group.canManageRole(admin, builder.uuid))
    }

    @Test
    fun `createRole inserts just above Member`() {
        val group = managedGroup()

        assertEquals(GroupActionResult.Success, group.createRole(admin, "  Guard  "))
        assertEquals(listOf("Owner", "Admin", "Guard", "Member"), group.roles.map { it.name })
        assertEquals(Kind.CUSTOM, group.role("Guard").kind)
        assertTrue(group.role("Guard").permissions.none { it.value })
    }

    @Test
    fun `role names are validated and unique ignoring case`() {
        val group = managedGroup()

        assertEquals(GroupActionResult.Error.INVALID_ROLE_NAME, group.createRole(owner, "   "))
        assertEquals(GroupActionResult.Error.INVALID_ROLE_NAME, group.createRole(owner, "x".repeat(33)))
        assertEquals(GroupActionResult.Error.ROLE_NAME_TAKEN, group.createRole(owner, "admin"))
        assertEquals(
            GroupActionResult.Error.ROLE_NAME_TAKEN,
            group.renameRole(owner, group.role("Admin").uuid, "MEMBER"),
        )
    }

    @Test
    fun `renameRole changes the name, including protected roles`() {
        val group = managedGroup()
        val memberRole = group.role("Member").uuid

        assertEquals(GroupActionResult.Success, group.renameRole(owner, memberRole, "Citizen"))
        assertEquals("Citizen", group.findRole(memberRole)?.name)
        assertEquals(GroupActionResult.Success, group.renameRole(owner, memberRole, "CITIZEN"))
        assertEquals(GroupActionResult.Success, group.renameRole(owner, group.role("Owner").uuid, "Mayor"))
    }

    @Test
    fun `cannot manage roles at or above your own`() {
        val group = managedGroup()

        assertEquals(GroupActionResult.Error.NO_PERMISSION, group.renameRole(admin, group.role("Admin").uuid, "Boss"))
        assertEquals(GroupActionResult.Error.NO_PERMISSION, group.renameRole(admin, group.role("Owner").uuid, "Boss"))
        assertTrue(group.canManageRole(admin, group.role("Member").uuid))
        assertFalse(group.canManageRole(admin, group.role("Admin").uuid))
    }

    @Test
    fun `setRolePermission grants and revokes`() {
        val group = managedGroup()
        val memberRole = group.role("Member").uuid

        assertEquals(
            GroupActionResult.Success,
            group.setRolePermission(admin, memberRole, Permission.INVITE_MEMBERS, true),
        )
        assertTrue(group.hasPermission(member, Permission.INVITE_MEMBERS))
        assertEquals(
            GroupActionResult.Success,
            group.setRolePermission(admin, memberRole, Permission.INVITE_MEMBERS, false),
        )
        assertFalse(group.hasPermission(member, Permission.INVITE_MEMBERS))
    }

    @Test
    fun `cannot grant a permission you do not hold`() {
        val group = managedGroup()

        assertEquals(
            GroupActionResult.Error.NO_PERMISSION,
            group.setRolePermission(admin, group.role("Member").uuid, Permission.DELETE_GROUP, true),
        )
    }

    @Test
    fun `owner permissions cannot be edited`() {
        val group = managedGroup()

        assertEquals(
            GroupActionResult.Error.ROLE_PROTECTED,
            group.setRolePermission(owner, group.role("Owner").uuid, Permission.DELETE_GROUP, false),
        )
    }

    @Test
    fun `role operations report missing roles`() {
        val group = managedGroup()
        val missing = UUID.randomUUID()

        assertEquals(GroupActionResult.Error.ROLE_NOT_FOUND, group.renameRole(owner, missing, "Guard"))
        assertEquals(
            GroupActionResult.Error.ROLE_NOT_FOUND,
            group.setRolePermission(owner, missing, Permission.INVITE_MEMBERS, true),
        )
        assertEquals(GroupActionResult.Error.ROLE_NOT_FOUND, group.moveRole(owner, missing, up = true))
        assertEquals(GroupActionResult.Error.ROLE_NOT_FOUND, group.deleteRole(owner, missing))
    }

    @Test
    fun `moveRole swaps custom roles`() {
        val group = managedGroup()
        group.addRole("Builder")
        group.addRole("Guard")

        assertEquals(GroupActionResult.Success, group.moveRole(owner, group.role("Guard").uuid, up = true))
        assertEquals(listOf("Owner", "Admin", "Guard", "Builder", "Member"), group.roles.map { it.name })
        assertEquals(GroupActionResult.Success, group.moveRole(admin, group.role("Guard").uuid, up = false))
        assertEquals(listOf("Owner", "Admin", "Builder", "Guard", "Member"), group.roles.map { it.name })
    }

    @Test
    fun `moveRole keeps Owner first, Member last, and roles below the actor`() {
        val group = managedGroup()
        val builder = group.addRole("Builder")

        assertEquals(
            GroupActionResult.Error.ROLE_PROTECTED,
            group.moveRole(owner, group.role("Owner").uuid, up = false),
        )
        assertEquals(
            GroupActionResult.Error.ROLE_PROTECTED,
            group.moveRole(owner, group.role("Member").uuid, up = true),
        )
        assertEquals(GroupActionResult.Error.ROLE_PROTECTED, group.moveRole(owner, group.role("Admin").uuid, up = true))
        assertEquals(GroupActionResult.Error.ROLE_PROTECTED, group.moveRole(owner, builder.uuid, up = false))
        // Moving Builder up would swap it with Admin, lifting it to the actor's own rank
        assertEquals(GroupActionResult.Error.NO_PERMISSION, group.moveRole(admin, builder.uuid, up = true))
        assertFalse(group.canMoveRole(admin, builder.uuid, up = true))
        assertTrue(group.canMoveRole(owner, builder.uuid, up = true))
    }

    @Test
    fun `deleteRole moves its members to Member`() {
        val group = managedGroup()
        val builder = group.addRole("Builder")
        group.setRole(owner, member, builder.uuid)

        assertEquals(GroupActionResult.Success, group.deleteRole(admin, builder.uuid))
        assertNull(group.findRole(builder.uuid))
        assertEquals(Kind.MEMBER, group.roleOf(member)?.kind)
    }

    @Test
    fun `Owner and Member cannot be deleted`() {
        val group = managedGroup()

        assertEquals(GroupActionResult.Error.ROLE_PROTECTED, group.deleteRole(owner, group.role("Owner").uuid))
        assertEquals(GroupActionResult.Error.ROLE_PROTECTED, group.deleteRole(owner, group.role("Member").uuid))
        assertEquals(GroupActionResult.Error.NO_PERMISSION, group.deleteRole(admin, group.role("Admin").uuid))
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
