package com.lukearnold.coachesassistant.ui

import com.lukearnold.coachesassistant.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.lukearnold.coachesassistant.data.*

@Composable
fun GameStatsPanel(
    stats: List<PlayerGameStat> = emptyList(),
    /** The live game clock (see rememberLiveGameTime), for times that keep moving while it runs. */
    gameTimeMillis: () -> Long = { 0L },
    /** Each available player's equal share of playing time at a given clock time (see fairShare). */
    fairShareAt: (gameTimeMillis: Long) -> FairShare? = { null },
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
    var pendingDelete by remember { mutableStateOf<GameEntity?>(null) }

    pendingDelete?.let { game ->
        ConfirmDialog(
            title = stringResource(R.string.delete_this_game_question),
            message = deleteGameConfirmMessage(game),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                pendingDelete = null
                onDeleteGame(game.id)
            },
            onDismiss = { pendingDelete = null },
        )
    }

    if (!onlyStats) {
        SectionCard(title = stringResource(R.string.create_a_game)) {
            OutlinedTextField(
                value = gameTitle,
                onValueChange = onGameTitleChange,
                label = { Text(stringResource(R.string.game_title)) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(layout.smallGap))

            OutlinedTextField(
                value = opponent,
                onValueChange = onOpponentChange,
                label = { Text(stringResource(R.string.opponent)) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(layout.smallGap))

            Text(stringResource(R.string.periods), style = MaterialTheme.typography.labelMedium)
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
                label = { Text(stringResource(R.string.period_duration_minutes)) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(layout.smallGap))

            Button(onClick = onCreateGame) {
                Text(stringResource(R.string.create_game))
            }
        }

        SectionCard(title = stringResource(R.string.games)) {
            if (games.isEmpty()) {
                EmptyMessage(stringResource(R.string.no_games_yet))
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
                                    Text(if (selected) stringResource(R.string.game_selected) else stringResource(R.string.select))
                                }
                                TextButton(onClick = { onExportGame(game.id) }) {
                                    Text(stringResource(R.string.export))
                                }
                                TextButton(
                                    onClick = { pendingDelete = game },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
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

    if (!onlyManagement) {
        SectionCard(title = stringResource(R.string.game_stats)) {
            if (stats.isEmpty()) {
                EmptyMessage(stringResource(R.string.no_stats_yet))
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
                                    StatusBadge(if (stat.onCourt) stringResource(R.string.on_court) else stringResource(R.string.bench))
                                    val now = gameTimeMillis()
                                    Text(
                                        text = formatDuration(
                                            if (stat.onCourt) stat.shiftTimeAt(now) else stat.benchTimeAt(now)
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
                                val now = gameTimeMillis()
                                val played = stat.totalTimeAt(now)
                                val share = fairShareAt(now)
                                Text(
                                    if (share == null) {
                                        "Total Time: ${formatDuration(played)}"
                                    } else {
                                        "Total Time: ${formatDuration(played)} of ${formatDuration(share.fullGameMillis)} target · " +
                                            paceLabel(share.behindMillis(played))
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }

        SectionCard(title = stringResource(R.string.recent_events)) {
            if (events.isEmpty()) {
                EmptyMessage(stringResource(R.string.no_events_yet))
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
