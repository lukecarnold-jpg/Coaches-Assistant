package com.lukearnold.coachesassistant.ui

import com.lukearnold.coachesassistant.R
import com.lukearnold.coachesassistant.data.RosterImportResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** A message for the snackbar, optionally with an Undo action for the change it reports. */
data class UiMessage(val text: UiText, val undo: (() -> Unit)? = null)

/**
 * A view model's snackbar messages, each shown once. They wait in a channel until the screen
 * collects them, so one sent while there's no collector (mid-rotation, or two in quick succession)
 * is shown rather than dropped, as it would be by a SharedFlow.
 */
class MessageQueue {
    private val channel = Channel<UiMessage>(Channel.BUFFERED)

    val messages: Flow<UiMessage> = channel.receiveAsFlow()

    suspend fun emit(message: UiMessage) = channel.send(message)

    fun tryEmit(message: UiMessage) {
        channel.trySend(message)
    }
}

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
