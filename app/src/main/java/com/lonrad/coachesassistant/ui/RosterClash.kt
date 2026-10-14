package com.lonrad.coachesassistant.ui

import com.lonrad.coachesassistant.data.PlayerEntity

/** How a player being added or edited clashes with someone already on the roster. */
sealed interface RosterClash {
    /** The same name (ignoring case) and number as [player]: the same person, already listed. */
    data class SamePlayer(val player: PlayerEntity) : RosterClash

    /** A different player, [player], already wears this number. */
    data class SharedNumber(val player: PlayerEntity) : RosterClash
}

/**
 * Whether a player called [name] with [jerseyNumber] would clash with anyone in [roster] other
 * than the player with id [exceptId] (the one being edited). Names match as the roster CSV import
 * matches them: trimmed and ignoring case.
 */
fun rosterClash(roster: List<PlayerEntity>, name: String, jerseyNumber: Int?, exceptId: Long? = null): RosterClash? {
    val others = roster.filter { it.id != exceptId }
    others.firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) && it.jerseyNumber == jerseyNumber }
        ?.let { return RosterClash.SamePlayer(it) }
    if (jerseyNumber == null) return null
    return others.firstOrNull { it.jerseyNumber == jerseyNumber }?.let { RosterClash.SharedNumber(it) }
}
