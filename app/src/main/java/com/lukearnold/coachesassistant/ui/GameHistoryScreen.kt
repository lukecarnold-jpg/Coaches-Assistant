package com.lukearnold.coachesassistant.ui

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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.lukearnold.coachesassistant.R
import com.lukearnold.coachesassistant.data.GameEntity
import com.lukearnold.coachesassistant.data.displayLabel
import com.lukearnold.coachesassistant.data.formatOvers
import com.lukearnold.coachesassistant.data.usesScorecards
import java.util.Locale

@Composable
fun GameHistoryScreen(
    modifier: Modifier = Modifier,
    games: List<GameEntity>,
    season: SeasonSummary,
    /** Cricket's season totals, shown instead of [season] for a scorecard sport. */
    cricketSeason: CricketSeasonSummary? = null,
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
        verticalArrangement = Arrangement.spacedBy(layout.sectionGap),
    ) {
        Text(
            text = stringResource(R.string.game_history),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        if (finishedGames.isEmpty()) {
            EmptyMessage(stringResource(R.string.no_completed_games_yet_games_appear_here_once_yo))
        } else {
            if (cricketSeason != null) {
                CricketSeasonCard(cricketSeason)
            } else {
                SeasonSummaryCard(season = season, infraction = infraction)
            }

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
                                Text(
                                    if (game.usesScorecards) {
                                        stringResource(R.string.cricket_final_score, game.teamScore, game.teamWickets, game.opponentScore, game.opponentWickets)
                                    } else {
                                        stringResource(R.string.final_score_value, game.teamScore, game.opponentScore)
                                    },
                                )
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

/** Cricket's season: the team's record, and each player's batting, bowling, and fielding. */
@Composable
private fun CricketSeasonCard(season: CricketSeasonSummary) {
    val layout = LocalResponsiveLayout.current

    SectionCard(title = pluralStringResource(R.plurals.season_title, season.games, season.games)) {
        Text(
            text = stringResource(R.string.season_record, season.wins, season.losses, season.ties),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
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
                    val lines = buildList {
                        if (stat.hasBatted) {
                            val highScore = if (stat.highScoreNotOut) {
                                stringResource(R.string.cricket_runs_not_out, stat.highScore)
                            } else {
                                stat.highScore.toString()
                            }
                            add(
                                stringResource(
                                    R.string.cricket_season_batting,
                                    stat.innings,
                                    stat.runs,
                                    highScore,
                                    oneDecimal(stat.battingAverage),
                                    oneDecimal(stat.strikeRate),
                                ),
                            )
                        }
                        if (stat.hasBowled) {
                            add(
                                stringResource(
                                    R.string.cricket_season_bowling,
                                    formatOvers(stat.ballsBowled),
                                    stat.wickets,
                                    stat.bestBowling?.let { (wickets, runs) -> "$wickets/$runs" } ?: "–",
                                    oneDecimal(stat.bowlingAverage),
                                    oneDecimal(stat.economy),
                                ),
                            )
                        }
                        if (stat.hasFielded) {
                            val fielding = listOfNotNull(
                                stat.catches.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.cricket_catch_count, it, it) },
                                stat.runOuts.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.cricket_run_out_count, it, it) },
                                stat.stumpings.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.cricket_stumping_count, it, it) },
                            )
                            add(stringResource(R.string.cricket_season_fielding, fielding.joinToString(" · ")))
                        }
                        if (isEmpty()) add(stringResource(R.string.cricket_yet_to_play))
                    }
                    lines.forEach { line -> Text(text = line, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

/** [value] to one decimal place, or a dash when there's nothing to show (e.g. no dismissals yet). */
private fun oneDecimal(value: Double?): String =
    value?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "–"

/** [total] averaged over [games], to one decimal place. */
private fun perGame(total: Int, games: Int): String =
    if (games == 0) "0" else String.format(Locale.getDefault(), "%.1f", total.toDouble() / games)
