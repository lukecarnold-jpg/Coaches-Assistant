package com.example.basketballrotation.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.basketballrotation.data.GameEntity

@Composable
fun GameHistoryScreen(
    modifier: Modifier = Modifier,
    games: List<GameEntity>,
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
