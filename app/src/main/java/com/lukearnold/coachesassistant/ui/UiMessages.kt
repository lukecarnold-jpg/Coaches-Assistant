package com.lukearnold.coachesassistant.ui

import com.lukearnold.coachesassistant.data.RosterImportResult

/** A message for the snackbar, optionally with an Undo action for the change it reports. */
data class UiMessage(val text: String, val undo: (() -> Unit)? = null)

/** The snackbar after a roster CSV import. */
fun rosterImportMessage(result: RosterImportResult): String {
    fun players(n: Int) = if (n == 1) "1 player" else "$n players"
    return when {
        result.added == 0 && result.skipped == 0 -> "No players found in that file."
        result.added == 0 -> "No new players: ${if (result.skipped == 1) "they were" else "all ${result.skipped} were"} already on the roster."
        result.skipped == 0 -> "Imported ${players(result.added)}."
        else -> "Imported ${players(result.added)} (${result.skipped} already on the roster)."
    }
}

/**
 * Why the clock can't go back that far. The clock counts down, so times are shown as time
 * remaining, the same as the game clock.
 */
fun clockTooEarlyMessage(periodDurationMillis: Long, earliestElapsedMillis: Long): String {
    val latest = formatDuration(maxOf(0L, periodDurationMillis - earliestElapsedMillis))
    return "Can't set the clock back past $latest: something was already recorded then this period, " +
        "and moving before it would lose playing time."
}
