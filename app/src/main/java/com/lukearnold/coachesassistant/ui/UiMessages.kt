package com.lukearnold.coachesassistant.ui

import com.lukearnold.coachesassistant.R
import com.lukearnold.coachesassistant.data.RosterImportResult

/** A message for the snackbar, optionally with an Undo action for the change it reports. */
data class UiMessage(val text: UiText, val undo: (() -> Unit)? = null)

/** The snackbar after a roster CSV import. */
fun rosterImportMessage(result: RosterImportResult): UiText = when {
    result.added == 0 && result.skipped == 0 -> uiText(R.string.import_none_found)
    result.added == 0 -> pluralText(R.plurals.import_all_already_on_roster, result.skipped)
    result.skipped == 0 -> pluralText(R.plurals.import_added, result.added)
    else -> pluralText(R.plurals.import_added_and_skipped, result.skipped, pluralText(R.plurals.player_count, result.added), result.skipped)
}

/**
 * Why the clock can't go back that far. The clock counts down, so times are shown as time
 * remaining, the same as the game clock.
 */
fun clockTooEarlyMessage(periodDurationMillis: Long, earliestElapsedMillis: Long): UiText =
    uiText(R.string.clock_too_early, formatDuration(maxOf(0L, periodDurationMillis - earliestElapsedMillis)))
