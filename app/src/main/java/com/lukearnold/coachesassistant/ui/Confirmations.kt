package com.lukearnold.coachesassistant.ui

import com.lukearnold.coachesassistant.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.lukearnold.coachesassistant.data.GameEntity
import com.lukearnold.coachesassistant.data.PlayerEntity
import com.lukearnold.coachesassistant.data.displayLabel

/**
 * Asks before an action that can't be undone (ending a game, deleting a game or player, changing
 * period, resetting the clock). These all sit on single buttons, easy to hit by mistake mid-game.
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = stringResource(R.string.cancel),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel)
            }
        },
    )
}

private fun GameEntity.scoreLine(): String = "Us $teamScore – $opponentScore ${opponent.ifBlank { "Them" }}"

/** The End Game confirmation's message: the score, a warning if periods remain, and what happens next. */
fun endGameConfirmMessage(game: GameEntity): String = buildString {
    append("Final score: ${game.scoreLine()}.")
    if (game.currentPeriod < game.periodCount) {
        append(" You're only in ${game.periodLabel()} of ${game.periodCount}.")
    }
    append(" The game will move to History and can't be resumed.")
}

fun deleteGameConfirmMessage(game: GameEntity): String =
    "Delete \"${game.title}\" (${game.scoreLine()})? Its events and stats are removed and it no longer " +
        "counts in season totals. This can't be undone."

fun removePlayerConfirmMessage(player: PlayerEntity): String =
    "Remove ${player.displayLabel()} from the roster? They're taken off any game lineups, and past games " +
        "show their scores as \"Removed player\". This can't be undone."

fun nextPeriodConfirmMessage(game: GameEntity): String {
    val next = game.copy(currentPeriod = game.currentPeriod + 1).periodLabel()
    return "Move on to $next? ${game.periodLabel()} ends at the current clock time, the clock resets to " +
        "${formatDuration(game.periodDurationMillis)}, and everyone comes off court until you set the next lineup."
}

fun resetClockConfirmMessage(game: GameEntity): String =
    "Reset the ${game.periodLabel()} clock to ${formatDuration(game.periodDurationMillis)}? " +
        "Use this to restart a period that hasn't really begun."
