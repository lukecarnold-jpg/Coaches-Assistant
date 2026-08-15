package com.example.basketballrotation.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.basketballrotation.data.GameEntity

@Composable
fun GameHistoryScreen(
    modifier: Modifier = Modifier,
    isExpanded: Boolean,
    games: List<GameEntity>,
    onViewGame: (Long) -> Unit,
    onDeleteGame: (Long) -> Unit,
    onExportGame: (Long, Uri) -> Unit,
) {
    val layout = LocalResponsiveLayout.current
    val finishedGames = games
        .filter { it.isFinished }
        .sortedByDescending { it.finishedAt ?: it.createdAt }

    var pendingExportGameId by remember { mutableStateOf<Long?>(null) }
    val exportGameLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val gId = pendingExportGameId
        if (uri != null && gId != null) {
            onExportGame(gId, uri)
        }
        pendingExportGameId = null
    }

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
                    HistoryGameRow(
                        game = game,
                        onView = { onViewGame(game.id) },
                        onExport = {
                            pendingExportGameId = game.id
                            exportGameLauncher.launch("game_${game.id}.csv")
                        },
                        onDelete = { onDeleteGame(game.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryGameRow(
    game: GameEntity,
    onView: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val layout = LocalResponsiveLayout.current
    val result = when {
        game.teamScore > game.opponentScore -> "WIN"
        game.teamScore < game.opponentScore -> "LOSS"
        else -> "TIE"
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(layout.cardPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(layout.tinyGap)) {
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
            }

            Column {
                TextButton(onClick = onView) {
                    Text("View")
                }
                TextButton(onClick = onExport) {
                    Text("Export")
                }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete")
                }
            }
        }
    }
}
