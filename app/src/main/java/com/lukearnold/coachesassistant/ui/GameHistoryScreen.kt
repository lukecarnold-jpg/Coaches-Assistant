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
    infractionLabelPlural: String,
    onViewGame: (Long) -> Unit,
    onDeleteGame: (Long) -> Unit,
    onExportGame: (Long, Uri) -> Unit,
) {
    val layout = LocalResponsiveLayout.current
    val finishedGames = games
        .filter { it.isFinished }
        .sortedByDescending { it.finishedAt ?: it.createdAt }

    val exportGame = rememberIdFileExportLauncher(mimeType = "text/csv", onExport = onExportGame)

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(layout.screenPadding),
        verticalArrangement = Arrangement.spacedBy(layout.sectionGap)
    ) {
        Text(
            text = "Game history",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        if (finishedGames.isEmpty()) {
            EmptyMessage("No completed games yet. Games appear here once you end them.")
        } else {
            SeasonSummaryCard(season = season, infractionLabelPlural = infractionLabelPlural)

            Column(verticalArrangement = Arrangement.spacedBy(layout.smallGap)) {
                finishedGames.forEach { game ->
                    val result = when {
                        game.teamScore > game.opponentScore -> "WIN"
                        game.teamScore < game.opponentScore -> "LOSS"
                        else -> "TIE"
                    }

                    GameListCard(
                        info = {
                            Text(
                                text = game.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (game.opponent.isNotBlank()) {
                                Text("Opponent: ${game.opponent}")
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(layout.tinyGap),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                StatusBadge(result)
                                Text("Final: ${game.teamScore} - ${game.opponentScore}")
                            }
                            Text(
                                text = formatTime(game.finishedAt ?: game.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                        actions = {
                            TextButton(onClick = { onViewGame(game.id) }) {
                                Text("View")
                            }
                            TextButton(onClick = { exportGame(game.id, "game_${game.id}.csv") }) {
                                Text("Export")
                            }
                            TextButton(
                                onClick = { onDeleteGame(game.id) },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) {
                                Text("Delete")
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
private fun SeasonSummaryCard(season: SeasonSummary, infractionLabelPlural: String) {
    val layout = LocalResponsiveLayout.current

    SectionCard(title = "Season (${season.games} ${if (season.games == 1) "game" else "games"})") {
        Text(
            text = "W ${season.wins} · L ${season.losses} · T ${season.ties}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Points: ${season.pointsFor} for, ${season.pointsAgainst} against " +
                "(avg ${perGame(season.pointsFor, season.games)}–${perGame(season.pointsAgainst, season.games)})",
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
                        text = "${stat.gamesPlayed} GP · ${formatDuration(stat.totalTimeMillis)} played " +
                            "(${formatDuration(stat.averageTimeMillis)} avg) · ${stat.points} pts · " +
                            "${stat.fouls} ${infractionLabelPlural.lowercase()} · ${stat.mvpVotes} MVP",
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

