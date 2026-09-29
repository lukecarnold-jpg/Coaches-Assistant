package com.lukearnold.coachesassistant.ui

import com.lukearnold.coachesassistant.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lukearnold.coachesassistant.data.*

@Composable
fun GameControlPanel(
    selectedGame: GameEntity,
    gameTimeMillis: () -> Long,
    fairShareAt: (gameTimeMillis: Long) -> FairShare?,
    sportConfig: SportConfig,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onSetGameTime: (Long) -> Unit,
    onAddPlayerScore: (playerId: Long, points: Int) -> Unit,
    onAdjustTeamScore: (points: Int) -> Unit,
    onAddOpponentScore: (points: Int) -> Unit,
    onCourtIds: Set<Long>,
    onExportGame: () -> Unit,
    exportRosterLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    /** Players on the roster who aren't marked absent from this game. */
    availablePlayers: List<PlayerEntity>,
    /** The fewest players the team can take the field with (see SportConfig.minimumOnCourt). */
    minimumOnCourt: Int,
    startingLineupSelection: MutableList<Long>,
    onSetLineup: (List<Long>) -> Unit,
    onCourtStats: List<PlayerGameStat>,
    onAddFoul: (Long, Int) -> Unit,
    onAddMvpVote: (Long) -> Unit,
    onAdvancePeriod: () -> Unit,
    onEndGame: () -> Unit,
    gameDayActive: Boolean,
    onStartGameDay: () -> Unit,
    onStopGameDay: () -> Unit,
    onManageAttendance: () -> Unit,
    pendingOut: List<Long>,
    onPlayerOutClick: (Long) -> Unit,
    benchStats: List<PlayerGameStat>,
    pendingIn: List<Long>,
    onPlayerInClick: (Long) -> Unit,
    onSubstitute: () -> Unit,
) {
    val layout = LocalResponsiveLayout.current
    var showEndGameConfirm by remember(selectedGame.id) { mutableStateOf(false) }

    var showNextPeriodConfirm by remember(selectedGame.id) { mutableStateOf(false) }

    if (showEndGameConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.end_this_game_question),
            message = endGameConfirmMessage(selectedGame).asString(),
            confirmLabel = stringResource(R.string.end_game),
            dismissLabel = stringResource(R.string.keep_playing),
            onConfirm = {
                showEndGameConfirm = false
                onEndGame()
            },
            onDismiss = { showEndGameConfirm = false },
        )
    }
    if (showNextPeriodConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.next_period_question),
            message = nextPeriodConfirmMessage(selectedGame).asString(),
            confirmLabel = stringResource(R.string.next_period),
            onConfirm = {
                showNextPeriodConfirm = false
                onAdvancePeriod()
            },
            onDismiss = { showNextPeriodConfirm = false },
        )
    }

    SectionCard(title = stringResource(R.string.game_clock_score)) {
        Text(
            text = stringResource(R.string.period_value, selectedGame.periodLabel().asString()),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        GameClock(
            game = selectedGame,
            gameTimeMillis = gameTimeMillis,
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
                    OutlinedButton(onClick = { showNextPeriodConfirm = true }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.next_period_title))
                    }
                }
                Button(
                    onClick = { showEndGameConfirm = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.end_game_title))
                }
            }

            GameDayModeButton(active = gameDayActive, onStart = onStartGameDay, onStop = onStopGameDay)

            OutlinedButton(
                onClick = onManageAttendance,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.manage_attendance_flag_absent))
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    stringResource(R.string.game_finished),
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
                Text(stringResource(R.string.us), style = MaterialTheme.typography.labelMedium)
                Text(
                    text = selectedGame.teamScore.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    // A team-level correction: it isn't taken off any particular player (each
                    // player's card has its own -1 for that).
                    IconButton(onClick = { onAdjustTeamScore(-1) }) {
                        Text("-1", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = selectedGame.opponent.ifBlank { stringResource(R.string.them) },
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = selectedGame.opponentScore.toString(),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    IconButton(onClick = { onAddOpponentScore(-1) }) {
                        Text("-1", style = MaterialTheme.typography.bodySmall)
                    }
                    sportConfig.scoringOptions.forEach { option ->
                        IconButton(onClick = { onAddOpponentScore(option.points) }) {
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
                Text(stringResource(R.string.export_game_csv))
            }

            OutlinedButton(
                onClick = { exportRosterLauncher.launch("players.csv") },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.export_roster_csv))
            }
        }
    }

    if (onCourtIds.isEmpty()) {
        StartingLineupPicker(
            availablePlayers = availablePlayers,
            onCourtSize = sportConfig.onCourtSize,
            minimumOnCourt = minimumOnCourt,
            selection = startingLineupSelection,
            onSetLineup = onSetLineup,
        )
    } else {
        SectionCard(title = stringResource(R.string.players_on_court)) {
            Column(verticalArrangement = Arrangement.spacedBy(layout.smallGap)) {
                onCourtStats.forEach { stat ->
                    PlayerActionCard(
                        stat = stat,
                        sportConfig = sportConfig,
                        onAddScore = onAddPlayerScore,
                        onAddFoul = onAddFoul,
                        onAddMvpVote = onAddMvpVote,
                        gameTimeMillis = gameTimeMillis,
                        fairShareAt = fairShareAt,
                        enabled = !selectedGame.isFinished
                    )
                }
            }
        }

        if (!selectedGame.isFinished) {
            SectionCard(title = stringResource(R.string.substitution)) {
                Text(stringResource(R.string.select_players_to_come_out_and_players_to_come_i))
                // Unequal picks are allowed: extras join without a swap or go off without a
                // replacement, as long as the court stays between one player and a full side.
                val courtAfter = onCourtIds.size - pendingOut.size + pendingIn.size
                val fullSide = sportConfig.onCourtSize
                val rotationNote = when {
                    courtAfter > fullSide && onCourtIds.size >= fullSide -> stringResource(R.string.rotation_court_already_full)
                    courtAfter > fullSide ->
                        pluralStringResource(R.plurals.rotation_court_full, fullSide - onCourtIds.size, fullSide - onCourtIds.size)
                    courtAfter < 1 -> stringResource(R.string.rotation_court_empty)
                    pendingIn.size > pendingOut.size ->
                        pluralStringResource(R.plurals.rotation_joining, pendingIn.size - pendingOut.size, pendingIn.size - pendingOut.size)
                    pendingOut.size > pendingIn.size ->
                        pluralStringResource(R.plurals.rotation_leaving, pendingOut.size - pendingIn.size, pendingOut.size - pendingIn.size) +
                            if (courtAfter < minimumOnCourt) " " + stringResource(R.string.rotation_below_minimum, courtAfter, minimumOnCourt) else ""
                    else -> null
                }
                val rotationValid = (pendingIn.isNotEmpty() || pendingOut.isNotEmpty()) && courtAfter in 1..fullSide
                if (rotationNote != null) {
                    Text(
                        rotationNote,
                        color = if (rotationValid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Spacer(Modifier.height(layout.smallGap))

                Text(
                    text = stringResource(R.string.players_coming_out, pendingOut.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(layout.tinyGap))

                Column(verticalArrangement = Arrangement.spacedBy(layout.tinyGap + 2.dp)) {
                    onCourtStats.forEach { stat ->
                        val outIndex = pendingOut.indexOf(stat.player.id)
                        val now = gameTimeMillis()
                        SelectablePlayerRow(
                            text = stat.player.displayLabel(),
                            selected = outIndex >= 0,
                            badgeNumber = if (outIndex >= 0) outIndex + 1 else null,
                            trailingText = fairShareAt(now)?.let { paceLabel(it.behindMillis(stat.totalTimeAt(now))).asString() },
                        ) {
                            onPlayerOutClick(stat.player.id)
                        }
                    }
                }

                Spacer(Modifier.height(layout.sectionGap))

                Text(
                    text = stringResource(R.string.players_coming_in, pendingIn.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(layout.tinyGap))

                if (benchStats.isEmpty()) {
                    EmptyMessage(stringResource(R.string.no_bench_players_available))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(layout.tinyGap + 2.dp)) {
                        benchSubOrder(benchStats).forEach { stat ->
                            val inIndex = pendingIn.indexOf(stat.player.id)
                            val now = gameTimeMillis()
                            SelectablePlayerRow(
                                text = stat.player.displayLabel(),
                                selected = inIndex >= 0,
                                badgeNumber = if (inIndex >= 0) inIndex + 1 else null,
                                trailingText = fairShareAt(now)?.let { paceLabel(it.behindMillis(stat.totalTimeAt(now))).asString() },
                            ) {
                                onPlayerInClick(stat.player.id)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(layout.smallGap))

                Button(
                    onClick = onSubstitute,
                    enabled = rotationValid
                ) {
                    val changes = maxOf(pendingIn.size, pendingOut.size)
                    Text(pluralStringResource(R.plurals.confirm_rotation, changes, changes))
                }
            }
        }
    }

}

@Composable
fun GameClock(
    game: GameEntity,
    gameTimeMillis: () -> Long,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onSetGameTime: (Long) -> Unit,
) {
    val layout = LocalResponsiveLayout.current

    val timerLastStartedAt = game.timerLastStartedAt
    val elapsed = gameTimeMillis()
    val remainingTime = maxOf(0L, game.periodDurationMillis - elapsed)

    var isEditing by remember { mutableStateOf(value = false) }
    var showResetConfirm by remember(game.id) { mutableStateOf(false) }

    if (showResetConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.reset_the_clock_question),
            message = resetClockConfirmMessage(game).asString(),
            confirmLabel = stringResource(R.string.reset),
            onConfirm = {
                showResetConfirm = false
                onResetTimer()
            },
            onDismiss = { showResetConfirm = false },
        )
    }

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
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.edit_time))
                    }
                    val resetClockLabel = stringResource(R.string.reset_clock)
                    IconButton(
                        onClick = { showResetConfirm = true },
                        modifier = Modifier.semantics { contentDescription = resetClockLabel },
                    ) {
                        Text("🔄", style = MaterialTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics {})
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
                                Icons.Default.Pause
                            } else {
                                Icons.Default.PlayArrow
                            },
                            contentDescription = null
                        )
                        Spacer(Modifier.width(layout.smallGap))
                        Text(if (timerLastStartedAt != null) stringResource(R.string.stop) else stringResource(R.string.start))
                    }
                }
            }
        }

        if (isEditing) {
            // Edited as time remaining, matching the countdown shown above.
            var minutesText by remember { mutableStateOf((remainingTime / 60000).toString()) }
            var secondsText by remember { mutableStateOf(((remainingTime % 60000) / 1000).toString()) }

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
                    label = { Text(stringResource(R.string.min_left)) },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = secondsText,
                    onValueChange = { secondsText = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.sec_left)) },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val mins = minutesText.toLongOrNull() ?: 0L
                        val secs = secondsText.toLongOrNull() ?: 0L
                        onSetGameTime(elapsedFromRemaining(((mins * 60) + secs) * 1000, game.periodDurationMillis))
                        isEditing = false
                    }
                ) {
                    Text(stringResource(R.string.set))
                }
            }
        }
    }
}

/**
 * The elapsed period time for a clock showing [remainingMillis] left, since the clock counts down
 * but the game stores elapsed time. A period with no set length has no countdown, so the entry is
 * taken as elapsed time.
 */
fun elapsedFromRemaining(remainingMillis: Long, periodDurationMillis: Long): Long =
    if (periodDurationMillis > 0) (periodDurationMillis - remainingMillis).coerceIn(0L, periodDurationMillis)
    else maxOf(0L, remainingMillis)

/**
 * Picks the starting lineup from the players who are here ([availablePlayers]). With enough for
 * a full side it's exactly [onCourtSize]; with fewer it's short-handed, down to [minimumOnCourt]
 * (see lineupRule); below that there's no lineup to pick. When there's no choice to make, everyone
 * here starts ticked.
 */
@Composable
fun StartingLineupPicker(
    availablePlayers: List<PlayerEntity>,
    onCourtSize: Int,
    minimumOnCourt: Int,
    selection: MutableList<Long>,
    onSetLineup: (List<Long>) -> Unit,
) {
    val layout = LocalResponsiveLayout.current
    val rule = lineupRule(availablePlayers.size, onCourtSize, minimumOnCourt)
    val availableIds = availablePlayers.map { it.id }

    LaunchedEffect(selection, availableIds, rule) {
        val noChoice = rule !is LineupRule.TooFew && availableIds.size <= onCourtSize
        if (noChoice && selection.isEmpty()) selection.addAll(availableIds)
    }

    if (rule is LineupRule.TooFew) {
        EmptyMessage(pluralStringResource(R.plurals.lineup_too_few, rule.available, rule.available, rule.minimum))
        return
    }

    SectionCard(title = stringResource(R.string.set_starting_lineup)) {
        Text(
            when (rule) {
                is LineupRule.ShortHanded ->
                    pluralStringResource(R.plurals.lineup_short_handed, rule.available, rule.available, rule.fullSize, rule.minimum)
                else -> pluralStringResource(R.plurals.select_exactly_players, onCourtSize, onCourtSize)
            },
        )
        Spacer(Modifier.height(layout.smallGap))

        availablePlayers.forEach { player ->
            val checked = selection.contains(player.id)

            // The whole row is one toggle, so tapping the name works and screen readers
            // announce the player's name with the checked state.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = checked, role = Role.Checkbox) { newValue ->
                        if (newValue && selection.size < minOf(onCourtSize, availablePlayers.size)) {
                            selection.add(player.id)
                        }
                        if (!newValue) {
                            selection.remove(player.id)
                        }
                    }
                    .padding(vertical = layout.tinyGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = checked, onCheckedChange = null)
                Text(player.displayLabel())
            }
        }

        Spacer(Modifier.height(layout.smallGap))

        Button(
            onClick = { onSetLineup(selection.filter { it in availableIds }) },
            enabled = rule.allows(selection.count { it in availableIds }),
        ) {
            Text(stringResource(R.string.save_starting_lineup))
        }
    }
}
