package com.lukearnold.coachesassistant.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lukearnold.coachesassistant.R
import com.lukearnold.coachesassistant.data.GameEntity
import com.lukearnold.coachesassistant.data.PlayerEntity
import com.lukearnold.coachesassistant.data.displayLabel
import com.lukearnold.coachesassistant.data.usesScorecards

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

/** "Us 4 – 2 Rivals" (or "Them" with no opponent name). */
fun GameEntity.scoreLine(): UiText = when {
    // Cricket: runs and wickets, "Us 145/6 – 140/9 Rivals".
    usesScorecards && opponent.isBlank() ->
        uiText(R.string.cricket_score_line_no_opponent, teamScore, teamWickets, opponentScore, opponentWickets)

    usesScorecards -> uiText(R.string.cricket_score_line, teamScore, teamWickets, opponentScore, opponentWickets, opponent)

    else -> if (opponent.isBlank()) {
        uiText(R.string.score_line_no_opponent, teamScore, opponentScore)
    } else {
        uiText(R.string.score_line, teamScore, opponentScore, opponent)
    }
}

/** The End Game confirmation's message: the score, a warning if periods remain, and what happens next. */
fun endGameConfirmMessage(game: GameEntity): UiText =
    if (game.currentPeriod < game.periodCount) {
        uiText(R.string.end_game_confirm_early, game.scoreLine(), game.periodLabel(), game.periodCount)
    } else {
        uiText(R.string.end_game_confirm, game.scoreLine())
    }

fun deleteGameConfirmMessage(game: GameEntity): UiText =
    uiText(R.string.delete_game_confirm, game.title, game.scoreLine())

fun removePlayerConfirmMessage(player: PlayerEntity): UiText =
    uiText(R.string.remove_player_confirm, player.displayLabel(), uiText(R.string.removed_player))

fun nextPeriodConfirmMessage(game: GameEntity): UiText = uiText(
    R.string.next_period_confirm,
    game.periodLabel(game.currentPeriod + 1),
    game.periodLabel(),
    formatDuration(game.periodDurationMillis),
)

fun resetClockConfirmMessage(game: GameEntity): UiText =
    uiText(R.string.reset_clock_confirm, game.periodLabel(), formatDuration(game.periodDurationMillis))
