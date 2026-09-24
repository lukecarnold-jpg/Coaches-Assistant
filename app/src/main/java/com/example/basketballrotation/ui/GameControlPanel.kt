package com.example.basketballrotation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.basketballrotation.data.*
import kotlinx.coroutines.delay

@Composable
fun GameControlPanel(
    selectedGame: GameEntity,
    sportConfig: SportConfig,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onSetGameTime: (Long) -> Unit,
    onAddScore: (Long?, Int) -> Unit,
    onCourtIds: Set<Long>,
    onExportGame: () -> Unit,
    exportRosterLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    players: List<PlayerEntity>,
    startingLineupSelection: MutableList<Long>,
    onSetLineup: (List<Long>) -> Unit,
    onCourtStats: List<PlayerGameStat>,
    onAddFoul: (Long, Int) -> Unit,
    onAddMvpVote: (Long) -> Unit,
    onAdvancePeriod: () -> Unit,
    onEndGame: () -> Unit,
    onManageAttendance: () -> Unit,
    pendingOut: List<Long>,
    onPlayerOutClick: (Long) -> Unit,
    benchStats: List<PlayerGameStat>,
    pendingIn: List<Long>,
    onPlayerInClick: (Long) -> Unit,
    onSubstitute: () -> Unit,
) {
    val layout = LocalResponsiveLayout.current

    SectionCard(title = "Game clock & score") {
        Text(
            text = "Period: ${selectedGame.periodLabel()}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        GameClock(
            game = selectedGame,
            onToggleTimer = onToggleTimer,
            onResetTimer = onResetTimer,
            onSetGameTime = onSetGameTime
        )

        if (!selectedGame.isFinished) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(layout.smallGap)
            ) {
                if (selectedGame.currentPeriod < selectedGame.periodCount) {
                    OutlinedButton(onClick = onAdvancePeriod, modifier = Modifier.weight(1f)) {
                        Text("Next Period")
                    }
                }
                Button(
                    onClick = onEndGame,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("End Game")
                }
            }

            OutlinedButton(
                onClick = onManageAttendance,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Manage Attendance (Flag Absent)")
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    "GAME FINISHED",
                    modifier = Modifier.padding(layout.smallGap).align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = layout.sectionGap / 2))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Us", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = selectedGame.teamScore.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    IconButton(onClick = { onAddScore(onCourtIds.firstOrNull(), -1) }) {
                        Text("-1", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = selectedGame.opponent.ifBlank { "Them" },
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = selectedGame.opponentScore.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    IconButton(onClick = { onAddScore(null, -1) }) {
                        Text("-1", style = MaterialTheme.typography.bodySmall)
                    }
                    sportConfig.scoringOptions.forEach { option ->
                        IconButton(onClick = { onAddScore(null, option.points) }) {
                            Text("+${option.points}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(layout.smallGap))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(layout.smallGap)
        ) {
            OutlinedButton(
                onClick = onExportGame,
                modifier = Modifier.weight(1f)
            ) {
                Text("Export game CSV")
            }

            OutlinedButton(
                onClick = { exportRosterLauncher.launch("players.csv") },
                modifier = Modifier.weight(1f)
            ) {
                Text("Export roster CSV")
            }
        }
    }

    val requiredOnCourt = sportConfig.onCourtSize

    if (players.size < requiredOnCourt) {
        EmptyMessage("You need at least $requiredOnCourt players in the roster before you can set the lineup.")
    } else {
        if (onCourtIds.size < requiredOnCourt) {
            SectionCard(title = "Set starting lineup") {
                Text("Select exactly $requiredOnCourt players.")
                Spacer(Modifier.height(layout.smallGap))

                players.forEach { player ->
                    val checked = startingLineupSelection.contains(player.id)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = layout.tinyGap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { newValue ->
                                if (newValue && !checked && (startingLineupSelection.size < requiredOnCourt)) {
                                    startingLineupSelection.add(player.id)
                                }
                                if (!newValue && checked) {
                                    startingLineupSelection.remove(player.id)
                                }
                            }
                        )
                        Text(player.displayLabel())
                    }
                }

                Spacer(Modifier.height(layout.smallGap))

                Button(
                    onClick = { onSetLineup(startingLineupSelection.toList()) },
                    enabled = startingLineupSelection.size == requiredOnCourt
                ) {
                    Text("Save starting lineup")
                }
            }
        } else {
            SectionCard(title = "Players on court") {
                Column(verticalArrangement = Arrangement.spacedBy(layout.smallGap)) {
                    onCourtStats.forEach { stat ->
                        PlayerActionCard(
                            stat = stat,
                            sportConfig = sportConfig,
                            onAddScore = onAddScore,
                            onAddFoul = onAddFoul,
                            onAddMvpVote = onAddMvpVote,
                            game = selectedGame,
                            enabled = !selectedGame.isFinished
                        )
                    }
                }
            }

            if (!selectedGame.isFinished) {
                SectionCard(title = "Substitution") {
                    Text("Select players to come out and players to come in.")
                    if (pendingIn.size != pendingOut.size) {
                        Text(
                            "Selection mismatch: ${pendingOut.size} out, ${pendingIn.size} in",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Spacer(Modifier.height(layout.smallGap))

                    Text(
                        text = "Players coming out (${pendingOut.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(layout.tinyGap))

                    Column(verticalArrangement = Arrangement.spacedBy(layout.tinyGap + 2.dp)) {
                        onCourtStats.forEach { stat ->
                            val outIndex = pendingOut.indexOf(stat.player.id)
                            SelectablePlayerRow(
                                text = stat.player.displayLabel(),
                                selected = outIndex >= 0,
                                badgeNumber = if (outIndex >= 0) outIndex + 1 else null
                            ) {
                                onPlayerOutClick(stat.player.id)
                            }
                        }
                    }

                    Spacer(Modifier.height(layout.sectionGap))

                    Text(
                        text = "Players coming in (${pendingIn.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(layout.tinyGap))

                    if (benchStats.isEmpty()) {
                        EmptyMessage("No bench players available.")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(layout.tinyGap + 2.dp)) {
                            benchStats.forEach { stat ->
                                val inIndex = pendingIn.indexOf(stat.player.id)
                                SelectablePlayerRow(
                                    text = stat.player.displayLabel(),
                                    selected = inIndex >= 0,
                                    badgeNumber = if (inIndex >= 0) inIndex + 1 else null
                                ) {
                                    onPlayerInClick(stat.player.id)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(layout.smallGap))

                    Button(
                        onClick = onSubstitute,
                        enabled = (pendingIn.isNotEmpty()) && (pendingIn.size == pendingOut.size)
                    ) {
                        Text("Confirm Rotation (${pendingIn.size} Subs)")
                    }
                }
            }
        }
    }
}

@Composable
fun GameClock(
    game: GameEntity,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onSetGameTime: (Long) -> Unit,
) {
    val layout = LocalResponsiveLayout.current

    val runningTimeMillis = game.runningTimeMillis
    val timerLastStartedAt = game.timerLastStartedAt
    val duration = game.periodDurationMillis

    var elapsedDisplay by remember(runningTimeMillis, timerLastStartedAt) {
        mutableLongStateOf(game.currentGameTimeMillis())
    }

    var isEditing by remember { mutableStateOf(value = false) }

    LaunchedEffect(runningTimeMillis, timerLastStartedAt, duration) {
        if (timerLastStartedAt != null) {
            // Holds at the period length once reached; AppViewModel stops the clock itself.
            while (true) {
                elapsedDisplay = game.currentGameTimeMillis()
                delay(100)
            }
        } else {
            elapsedDisplay = game.currentGameTimeMillis()
        }
    }

    val remainingTime = maxOf(0L, duration - elapsedDisplay)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatDuration(remainingTime),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = if (remainingTime < 60000) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )

            if (!game.isFinished) {
                Row {
                    IconButton(onClick = { isEditing = !isEditing }) {
                        Icon(Icons.Default.Settings, contentDescription = "Edit time")
                    }
                    IconButton(onClick = onResetTimer) {
                        Text("🔄", style = MaterialTheme.typography.titleLarge)
                    }
                    Button(
                        onClick = onToggleTimer,
                        colors = if (timerLastStartedAt != null) {
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        } else {
                            ButtonDefaults.buttonColors()
                        }
                    ) {
                        Icon(
                            if (timerLastStartedAt != null) {
                                Icons.Default.Info
                            } else {
                                Icons.Default.PlayArrow
                            },
                            contentDescription = null
                        )
                        Spacer(Modifier.width(layout.smallGap))
                        Text(if (timerLastStartedAt != null) "Stop" else "Start")
                    }
                }
            }
        }

        if (isEditing) {
            var minutesText by remember { mutableStateOf((elapsedDisplay / 60000).toString()) }
            var secondsText by remember { mutableStateOf(((elapsedDisplay % 60000) / 1000).toString()) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = layout.smallGap),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(layout.smallGap)
            ) {
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { minutesText = it.filter { c -> c.isDigit() } },
                    label = { Text("Min") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = secondsText,
                    onValueChange = { secondsText = it.filter { c -> c.isDigit() } },
                    label = { Text("Sec") },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val mins = minutesText.toLongOrNull() ?: 0L
                        val secs = secondsText.toLongOrNull() ?: 0L
                        onSetGameTime(((mins * 60) + secs) * 1000)
                        isEditing = false
                    }
                ) {
                    Text("Set")
                }
            }
        }
    }
}
