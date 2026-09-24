package com.example.basketballrotation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.basketballrotation.data.*

@Composable
fun GameStatsPanel(
    stats: List<PlayerGameStat> = emptyList(),
    events: List<GameEventEntity> = emptyList(),
    playerById: Map<Long, PlayerEntity> = emptyMap(),
    opponentName: String = "",
    infractionLabel: String = "Foul",
    infractionLabelPlural: String = "Fouls",
    onEditEvent: (GameEventEntity) -> Unit = {},
    onDeleteEvent: (GameEventEntity) -> Unit = {},
    gameTitle: String = "",
    onGameTitleChange: (String) -> Unit = {},
    opponent: String = "",
    onOpponentChange: (String) -> Unit = {},
    periodCount: Int = 2,
    onPeriodCountChange: (Int) -> Unit = {},
    periodMins: String = "10",
    onPeriodMinsChange: (String) -> Unit = {},
    onCreateGame: () -> Unit = {},
    games: List<GameEntity> = emptyList(),
    onSelectGame: (Long) -> Unit = {},
    onDeleteGame: (Long) -> Unit = {},
    onExportGame: (Long) -> Unit = {},
    selectedGameId: Long? = null,
    onlyManagement: Boolean = false,
    onlyStats: Boolean = false,
) {
    val layout = LocalResponsiveLayout.current

    if (!onlyStats) {
        SectionCard(title = "Create a game") {
            OutlinedTextField(
                value = gameTitle,
                onValueChange = onGameTitleChange,
                label = { Text("Game title") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(layout.smallGap))

            OutlinedTextField(
                value = opponent,
                onValueChange = onOpponentChange,
                label = { Text("Opponent") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(layout.smallGap))

            Text("Periods", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(layout.smallGap)) {
                PERIOD_COUNT_OPTIONS.forEach { (count, label) ->
                    FilterChip(
                        selected = periodCount == count,
                        onClick = { onPeriodCountChange(count) },
                        label = { Text(label) },
                    )
                }
            }

            Spacer(Modifier.height(layout.smallGap))

            OutlinedTextField(
                value = periodMins,
                onValueChange = { onPeriodMinsChange(it.filter { c -> c.isDigit() }) },
                label = { Text("Period duration (minutes)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(layout.smallGap))

            Button(onClick = onCreateGame) {
                Text("Create game")
            }
        }

        SectionCard(title = "Games") {
            if (games.isEmpty()) {
                EmptyMessage("No games yet.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(layout.smallGap)) {
                    games.forEach { game ->
                        val selected = selectedGameId == game.id

                        GameListCard(
                            highlighted = selected,
                            info = {
                                Text(
                                    text = game.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (game.opponent.isNotBlank()) {
                                    Text("Opponent: ${game.opponent}")
                                }
                                Text("Score: ${game.teamScore}")
                            },
                            actions = {
                                TextButton(onClick = { onSelectGame(game.id) }) {
                                    Text(if (selected) "Selected" else "Select")
                                }
                                TextButton(onClick = { onExportGame(game.id) }) {
                                    Text("Export")
                                }
                                TextButton(
                                    onClick = { onDeleteGame(game.id) },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
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

    if (!onlyManagement) {
        SectionCard(title = "Game stats") {
            if (stats.isEmpty()) {
                EmptyMessage("No stats yet.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(layout.smallGap)) {
                    stats.forEach { stat ->
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(layout.cardPadding),
                                verticalArrangement = Arrangement.spacedBy(layout.tinyGap)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    StatusBadge(if (stat.onCourt) "ON COURT" else "BENCH")
                                    Text(
                                        text = formatDuration(
                                            if (stat.onCourt) stat.currentShiftTimeMillis else stat.currentBenchTimeMillis
                                        ),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                Text(
                                    text = stat.player.displayLabel(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text("Points: ${stat.points} | $infractionLabelPlural: ${stat.fouls} | Votes: ${stat.mvpVotes} | Subbed off: ${stat.timesSubbedOff}")
                                Text(
                                    "Total Time: ${formatDuration(stat.totalTimeMillis)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }

        SectionCard(title = "Recent events") {
            if (events.isEmpty()) {
                EmptyMessage("No events yet.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(layout.smallGap)) {
                    events.forEach { event ->
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(layout.cardPadding),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(layout.tinyGap)
                                ) {
                                    Text(
                                        text = eventDescription(event, playerById, opponentName, infractionLabel),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${formatTime(event.timestamp)} | ${formatDuration(event.gameTimeMillis)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                Row {
                                    IconButton(onClick = { onEditEvent(event) }) {
                                        Text("✏️")
                                    }
                                    IconButton(onClick = { onDeleteEvent(event) }) {
                                        Text("🗑️")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
