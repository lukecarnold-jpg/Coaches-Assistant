package com.lukearnold.coachesassistant.ui

import android.content.Context
import androidx.core.content.edit
import com.lukearnold.coachesassistant.data.Sport

/**
 * How many players a starting lineup can have, given how many are [available] (on the roster and
 * not marked absent), the sport's full [onCourtSize], and the [minimum] it can field a team with.
 */
sealed interface LineupRule {
    /** Enough players to fill the court: the lineup is exactly [size]. */
    data class Full(val size: Int) : LineupRule

    /** Fewer than a full side but at least [minimum]: play short-handed with [minimum]..[available]. */
    data class ShortHanded(val available: Int, val minimum: Int, val fullSize: Int) : LineupRule

    /** Fewer than [minimum] available: there aren't enough to field a team. */
    data class TooFew(val available: Int, val minimum: Int) : LineupRule

    /** Whether a lineup of [count] players is allowed. */
    fun allows(count: Int): Boolean = when (this) {
        is Full -> count == size
        is ShortHanded -> count in minimum..available
        is TooFew -> false
    }
}

fun lineupRule(available: Int, onCourtSize: Int, minimum: Int): LineupRule {
    val floor = minimum.coerceIn(1, onCourtSize)
    return when {
        available >= onCourtSize -> LineupRule.Full(onCourtSize)
        available >= floor -> LineupRule.ShortHanded(available, floor, onCourtSize)
        else -> LineupRule.TooFew(available, floor)
    }
}

/**
 * The minimum players each sport's coach fields a team with. It defaults to the sport's rules
 * (SportConfig.minimumOnCourt); a coach whose competition differs can change it in Settings, and
 * only a changed value is stored.
 */
object FieldingPreferences {
    private const val PREFS_NAME = "fielding_prefs"

    private fun key(sport: Sport) = "minimum_on_court_${sport.name}"

    fun minimumOnCourt(context: Context, sport: Sport): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(key(sport), sport.config.minimumOnCourt)
            .coerceIn(1, sport.config.onCourtSize)

    fun setMinimumOnCourt(context: Context, sport: Sport, minimum: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            if (minimum == sport.config.minimumOnCourt) remove(key(sport)) else putInt(key(sport), minimum)
        }
    }
}
