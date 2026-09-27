package com.lukearnold.coachesassistant.ui

import com.lukearnold.coachesassistant.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.lukearnold.coachesassistant.data.GameEntity
import com.lukearnold.coachesassistant.data.displayLabel
import java.util.Locale

@Composable
fun GameHistoryScreen(
    modifier: Modifier = Modifier,
    games: List<GameEntity>,
    season: SeasonSummary,
    infraction: Infraction,
    onViewGame: (Long) -> Unit,
    onDeleteGame: (Long) -> Unit,
    onExportGame: (Long, Uri) -> Unit,
) {
    val layout = LocalResponsiveLayout.current
    val finishedGames = games
        .filter { it.isFinished }
        .sortedByDescending { it.finishedAt ?: it.createdAt }

    val exportGame = rememberIdFileExportLauncher(mimeType = "text/csv", onExport = onExportGame)
    var pendingDelete by remember { mutableStateOf<GameEntity?>(null) }

    pendingDelete?.let { game ->
        ConfirmDialog(
            title = stringResource(R.string.delete_this_game_question),
            message = deleteGameConfirmMessage(game).asString(),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                pendingDelete = null
                onDeleteGame(game.id)
            },
            onDismiss = { pendingDelete = null },
        )
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(layout.screenPadding),
        verticalArrangement = Arrangement.spacedBy(layout.sectionGap)
    ) {
        Text(
            text = stringResource(R.string.game_history),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        if (finishedGames.isEmpty()) {
            EmptyMessage(stringResource(R.string.no_completed_games_yet_games_appear_here_once_yo))
        } else {
            SeasonSummaryCard(season = season, infraction = infraction)

            Column(verticalArrangement = Arrangement.spacedBy(layout.smallGap)) {
                finishedGames.forEach { game ->
                    val result = when {
                        game.teamScore > game.opponentScore -> stringResource(R.string.win)
                        game.teamScore < game.opponentScore -> stringResource(R.string.loss)
                        else -> stringResource(R.string.tie)
                    }

                    GameListCard(
                        info = {
                            Text(
                                text = game.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (game.opponent.isNotBlank()) {
                                Text(stringResource(R.string.opponent_value, game.opponent))
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(layout.tinyGap),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                StatusBadge(result)
                                Text(stringResource(R.string.final_score_value, game.teamScore, game.opponentScore))
                            }
                            Text(
                                text = formatTime(game.finishedAt ?: game.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        actions = {
                            TextButton(onClick = { onViewGame(game.id) }) {
                                Text(stringResource(R.string.view))
                            }
                            TextButton(onClick = { exportGame(game.id, "game_${game.id}.csv") }) {
                                Text(stringResource(R.string.export))
                            }
                            TextButton(
                                onClick = { pendingDelete = game },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) {
                                Text(stringResource(R.string.delete))
                            }
                        },
                    )
                }
            }
        }
    }
}

/** Team record and per-player totals across every finished game of the current sport. */
@Composable
private fun SeasonSummaryCard(season: SeasonSummary, infraction: Infraction) {
    val layout = LocalResponsiveLayout.current

    SectionCard(title = pluralStringResource(R.plurals.season_title, season.games, season.games)) {
        Text(
            text = stringResource(R.string.season_record, season.wins, season.losses, season.ties),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(
                R.string.season_points,
                season.pointsFor,
                season.pointsAgainst,
                perGame(season.pointsFor, season.games),
                perGame(season.pointsAgainst, season.games),
            ),
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = layout.smallGap))

        Column(verticalArrangement = Arrangement.spacedBy(layout.smallGap)) {
            season.players.forEach { stat ->
                Column {
                    Text(
                        text = stat.player.displayLabel(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(
                            R.string.season_player_line,
                            stat.gamesPlayed,
                            formatDuration(stat.totalTimeMillis),
                            formatDuration(stat.averageTimeMillis),
                            stat.points,
                            pluralStringResource(infraction.count, stat.fouls, stat.fouls),
                            stat.mvpVotes,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

/** [total] averaged over [games], to one decimal place. */
private fun perGame(total: Int, games: Int): String =
    if (games == 0) "0" else String.format(Locale.getDefault(), "%.1f", total.toDouble() / games)

