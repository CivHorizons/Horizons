package net.civhorizons.horizons.groups

/**
 * Outcome of a group operation. Errors carry the translation key shown to the player.
 */
sealed interface GroupActionResult {
    data object Success : GroupActionResult

    enum class Error(val translationKey: String) : GroupActionResult {
        NOT_FOUND("horizons-groups.error.notfound"),
        NO_PERMISSION("horizons-groups.error.nopermission"),
        ALREADY_MEMBER("horizons-groups.error.alreadymember"),
        ALREADY_INVITED("horizons-groups.error.alreadyinvited"),
        NOT_INVITED("horizons-groups.error.notinvited"),
        NOT_MEMBER("horizons-groups.error.notmember"),
        ROLE_NOT_FOUND("horizons-groups.error.rolenotfound"),
        ROLE_PROTECTED("horizons-groups.error.roleprotected"),
        INVALID_ROLE_NAME("horizons-groups.error.invalidrolename"),
        ROLE_NAME_TAKEN("horizons-groups.error.rolenametaken"),
        INVALID_NAME("horizons-groups.error.invalidname"),
        PLAYER_OFFLINE("horizons-groups.error.playeroffline"),
    }
}
