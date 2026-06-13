package com.example.basketballrotation.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.basketballrotation.data.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun GameScreen(
    modifier: Modifier = Modifier,
    isExpanded: Boolean,
    isLandscape: Boolean,
    players: List<PlayerEntity>,
    games: List<GameEntity>,
    selectedGame: GameEntity?,
    stats: List<PlayerGameStat>,
    events: List<GameEventEntity>,
    onCreateGame: (String, String, Boolean, Int) -> Unit,
    onSelectGame: (Long) -> Unit,
    onSetLineup: (List<Long>) -> Unit,
    onSubstitute: (Long?, Long?) -> Unit,
    onAddScore: (Long?, Int) -> Unit,
    onAddFoul: (Long, Int) -> Unit,
    onAddMvpVote: (Long) -> Unit,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onSetGameTime: (Long) -> Unit,
    onDeleteGame: (Long) -> Unit,
    onExportGame: (Uri) -> Unit,
    onExportPlayers: (Uri) -> Unit,
    onAdvancePeriod: () -> Unit,
    onEndGame: () -> Unit,
    onUpdateAbsentPlayers: (List<Long>) -> Unit,
    onUpdateNotes: (String) -> Unit,
    onUpdateEvent: (GameEventEntity) -> Unit,
    onDeleteEvent: (GameEventEntity) -> Unit,
) {
    var gameTitle by rememberSaveable { mutableStateOf("") }
    var opponent by rememberSaveable { mutableStateOf("") }
    var useQuarters by rememberSaveable { mutableStateOf(value = false) }
    var periodMins by rememberSaveable { mutableStateOf("10") }
    var showAttendanceDialog by remember { mutableStateOf(false) }

    val exportGameLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        uri?.let(onExportGame)
    }

    val exportRosterLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        uri?.let(onExportPlayers)
    }

    val startingLineupSelection = remember(selectedGame?.id, selectedGame?.currentPeriod) { mutableStateListOf<Long>() }
    var playerOutId by remember(selectedGame?.id, selectedGame?.currentPeriod) { mutableStateOf<Long?>(null) }
    var playerInId by remember(selectedGame?.id, selectedGame?.currentPeriod) { mutableStateOf<Long?>(null) }

    var editingEvent by remember { mutableStateOf<GameEventEntity?>(null) }

    val onCourtStats = stats.filter { it.onCourt }
    val benchStats = stats.filter { !it.onCourt }
    val onCourtIds = selectedGame?.onCourtIds()?.toSet().orEmpty()
    val playerById = players.associateBy { it.id }

    if (isExpanded) {
        Row(
            modifier = modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (selectedGame != null) {
                    GameControlPanel(
                        selectedGame = selectedGame,
                        onToggleTimer = onToggleTimer,
                        onResetTimer = onResetTimer,
                        onSetGameTime = onSetGameTime,
                        onAddScore = onAddScore,
                        onCourtIds = onCourtIds,
                        exportGameLauncher = exportGameLauncher,
                        exportRosterLauncher = exportRosterLauncher,
                        players = players,
                        startingLineupSelection = startingLineupSelection,
                        onSetLineup = onSetLineup,
                        onCourtStats = onCourtStats,
                        onAddFoul = onAddFoul,
                        onAddMvpVote = onAddMvpVote,
                        onAdvancePeriod = onAdvancePeriod,
                        onEndGame = onEndGame,
                        onManageAttendance = { showAttendanceDialog = true },
                        playerOutId = playerOutId,
                        onPlayerOutClick = { playerOutId = it },
                        benchStats = benchStats,
                        playerInId = playerInId,
                        onPlayerInClick = { playerInId = it },
                        onSubstitute = {
                            onSubstitute(playerInId, playerOutId)
                            playerInId = null
                            playerOutId = null
                        }
                    )
                } else {
                    EmptyMessage("Select or create a game to start tracking.")
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (selectedGame != null && isLandscape) {
                    GameNotesPanel(
                        notes = selectedGame.notes,
                        onNotesChange = onUpdateNotes
                    )
                }

                GameStatsPanel(
                    stats = stats,
                    events = events,
                    playerById = playerById,
                    onEditEvent = { editingEvent = it },
                    onDeleteEvent = onDeleteEvent,
                    gameTitle = gameTitle,
                    onGameTitleChange = { gameTitle = it },
                    opponent = opponent,
                    onOpponentChange = { opponent = it },
                    useQuarters = useQuarters,
                    onUseQuartersChange = { useQuarters = it },
                    periodMins = periodMins,
                    onPeriodMinsChange = { periodMins = it },
                    onCreateGame = {
                        onCreateGame(gameTitle, opponent, useQuarters, periodMins.toIntOrNull() ?: 10)
                        gameTitle = ""
                        opponent = ""
                    },
                    games = games,
                    onSelectGame = onSelectGame,
                    onDeleteGame = onDeleteGame,
                    selectedGameId = selectedGame?.id
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Game tracker",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            GameStatsPanel(
                stats = emptyList(), // Hidden in this mode or moved
                events = emptyList(),
                playerById = emptyMap(),
                onEditEvent = {},
                onDeleteEvent = {},
                gameTitle = gameTitle,
                onGameTitleChange = { gameTitle = it },
                opponent = opponent,
                onOpponentChange = { opponent = it },
                useQuarters = useQuarters,
                onUseQuartersChange = { useQuarters = it },
                periodMins = periodMins,
                onPeriodMinsChange = { periodMins = it },
                onCreateGame = {
                    onCreateGame(gameTitle, opponent, useQuarters, periodMins.toIntOrNull() ?: 10)
                    gameTitle = ""
                    opponent = ""
                },
                games = games,
                onSelectGame = onSelectGame,
                onDeleteGame = onDeleteGame,
                selectedGameId = selectedGame?.id,
                onlyManagement = true
            )

            if (selectedGame != null) {
                if (isLandscape) {
                    GameNotesPanel(
                        notes = selectedGame.notes,
                        onNotesChange = onUpdateNotes
                    )
                }

                GameControlPanel(
                    selectedGame = selectedGame,
                    onToggleTimer = onToggleTimer,
                    onResetTimer = onResetTimer,
                    onSetGameTime = onSetGameTime,
                    onAddScore = onAddScore,
                    onCourtIds = onCourtIds,
                    exportGameLauncher = exportGameLauncher,
                    exportRosterLauncher = exportRosterLauncher,
                    players = players,
                    startingLineupSelection = startingLineupSelection,
                    onSetLineup = onSetLineup,
                    onCourtStats = onCourtStats,
                    onAddFoul = onAddFoul,
                    onAddMvpVote = onAddMvpVote,
                    onAdvancePeriod = onAdvancePeriod,
                    onEndGame = onEndGame,
                    onManageAttendance = { showAttendanceDialog = true },
                    playerOutId = playerOutId,
                    onPlayerOutClick = { playerOutId = it },
                    benchStats = benchStats,
                    playerInId = playerInId,
                    onPlayerInClick = { playerInId = it },
                ) {
                    onSubstitute(playerInId, playerOutId)
                    playerInId = null
                    playerOutId = null
                }

                GameStatsPanel(
                    stats = stats,
                    events = events,
                    playerById = playerById,
                    onEditEvent = { editingEvent = it },
                    onDeleteEvent = onDeleteEvent,
                    onlyStats = true
                )
            }
        }
    }

    editingEvent?.let { event ->
        EventEditDialog(
            event = event,
            onDismiss = { editingEvent = null },
        ) { updated ->
            onUpdateEvent(updated)
            editingEvent = null
        }
    }

    if (showAttendanceDialog && selectedGame != null) {
        AttendanceDialog(
            players = players,
            absentPlayerIds = selectedGame.absentPlayerIds(),
            onDismiss = { showAttendanceDialog = false },
            onConfirm = { ids ->
                onUpdateAbsentPlayers(ids)
                showAttendanceDialog = false
            }
        )
    }
}

@Composable
fun AttendanceDialog(
    players: List<PlayerEntity>,
    absentPlayerIds: List<Long>,
    onDismiss: () -> Unit,
    onConfirm: (List<Long>) -> Unit
) {
    val currentAbsent = remember { mutableStateListOf<Long>().apply { addAll(absentPlayerIds) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Attendance") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Select players who are ABSENT for this game.")
                players.forEach { player ->
                    val isAbsent = currentAbsent.contains(player.id)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isAbsent) currentAbsent.remove(player.id)
                                else currentAbsent.add(player.id)
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isAbsent,
                            onCheckedChange = {
                                if (it) currentAbsent.add(player.id)
                                else currentAbsent.remove(player.id)
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(player.displayLabel())
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(currentAbsent.toList()) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EventEditDialog(
    event: GameEventEntity,
    onDismiss: () -> Unit,
    onConfirm: (GameEventEntity) -> Unit,
) {
    var points by remember { mutableStateOf(event.points.toString()) }
    var note by remember { mutableStateOf(event.note) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Event") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Type: ${event.type}")
                if ((event.type == EventType.SCORE.name) || (event.type == EventType.FOUL.name)) {
                    OutlinedTextField(
                        value = points,
                        onValueChange = { points = it },
                        label = { Text(if (event.type == EventType.FOUL.name) "Foul count/adj" else "Points") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(event.copy(points = points.toIntOrNull() ?: event.points, note = note))
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun GameControlPanel(
    selectedGame: GameEntity,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onSetGameTime: (Long) -> Unit,
    onAddScore: (Long?, Int) -> Unit,
    onCourtIds: Set<Long>,
    exportGameLauncher: androidx.activity.result.ActivityResultLauncher<String>,
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
    playerOutId: Long?,
    onPlayerOutClick: (Long) -> Unit,
    benchStats: List<PlayerGameStat>,
    playerInId: Long?,
    onPlayerInClick: (Long) -> Unit,
    onSubstitute: () -> Unit,
) {
    SectionCard(title = "Game clock & score") {
        val periodName = if (selectedGame.useQuarters) "Q${selectedGame.currentPeriod}" else "H${selectedGame.currentPeriod}"
        Text(
            text = "Period: $periodName",
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val maxPeriods = if (selectedGame.useQuarters) 4 else 2
                if (selectedGame.currentPeriod < maxPeriods) {
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
                    modifier = Modifier.padding(8.dp).align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

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
                    IconButton(onClick = { onAddScore(null, 1) }) {
                        Text("+1", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { exportGameLauncher.launch("game_${selectedGame.id}.csv") },
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

    if (players.size < 5) {
        EmptyMessage("You need at least 5 players in the roster before you can set the lineup.")
    } else {
        if (onCourtIds.size < 5) {
            SectionCard(title = "Set starting lineup") {
                Text("Select exactly 5 players.")
                Spacer(Modifier.height(8.dp))

                players.forEach { player ->
                    val checked = startingLineupSelection.contains(player.id)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { newValue ->
                                if (newValue && !checked && (startingLineupSelection.size < 5)) {
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

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { onSetLineup(startingLineupSelection.toList()) },
                    enabled = startingLineupSelection.size == 5
                ) {
                    Text("Save starting lineup")
                }
            }
        } else {
            SectionCard(title = "Players on court") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    onCourtStats.forEach { stat ->
                        PlayerActionCard(
                            stat = stat,
                            onAddScore = onAddScore,
                            onAddFoul = onAddFoul,
                            onAddMvpVote = onAddMvpVote,
                            gameTimerLastStartedAt = selectedGame.timerLastStartedAt,
                            enabled = !selectedGame.isFinished
                        )
                    }
                }
            }

            if (!selectedGame.isFinished) {
                SectionCard(title = "Substitution") {
                    Text("Choose one player to come out and one player to come in.")
                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "Player out",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        onCourtStats.forEach { stat ->
                            SelectablePlayerRow(
                                text = stat.player.displayLabel(),
                                selected = playerOutId == stat.player.id
                            ) {
                                onPlayerOutClick(stat.player.id)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "Player in",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))

                    if (benchStats.isEmpty()) {
                        EmptyMessage("No bench players available.")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            benchStats.forEach { stat ->
                                SelectablePlayerRow(
                                    text = stat.player.displayLabel(),
                                    selected = playerInId == stat.player.id
                                ) {
                                    onPlayerInClick(stat.player.id)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = onSubstitute,
                        enabled = (playerInId != null) && (playerOutId != null)
                    ) {
                        Text("Record substitution")
                    }
                }
            }
        }
    }
}

@Composable
fun GameStatsPanel(
    stats: List<PlayerGameStat> = emptyList(),
    events: List<GameEventEntity> = emptyList(),
    playerById: Map<Long, PlayerEntity> = emptyMap(),
    onEditEvent: (GameEventEntity) -> Unit = {},
    onDeleteEvent: (GameEventEntity) -> Unit = {},
    gameTitle: String = "",
    onGameTitleChange: (String) -> Unit = {},
    opponent: String = "",
    onOpponentChange: (String) -> Unit = {},
    useQuarters: Boolean = false,
    onUseQuartersChange: (Boolean) -> Unit = {},
    periodMins: String = "10",
    onPeriodMinsChange: (String) -> Unit = {},
    onCreateGame: () -> Unit = {},
    games: List<GameEntity> = emptyList(),
    onSelectGame: (Long) -> Unit = {},
    onDeleteGame: (Long) -> Unit = {},
    selectedGameId: Long? = null,
    onlyManagement: Boolean = false,
    onlyStats: Boolean = false,
) {
    if (!onlyStats) {
        SectionCard(title = "Create a game") {
            OutlinedTextField(
                value = gameTitle,
                onValueChange = onGameTitleChange,
                label = { Text("Game title") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = opponent,
                onValueChange = onOpponentChange,
                label = { Text("Opponent") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = useQuarters, onCheckedChange = onUseQuartersChange)
                Text("Use Quarters (instead of Halves)")
            }

            OutlinedTextField(
                value = periodMins,
                onValueChange = { onPeriodMinsChange(it.filter { c -> c.isDigit() }) },
                label = { Text("Period duration (minutes)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Button(onClick = onCreateGame) {
                Text("Create game")
            }
        }

        SectionCard(title = "Games") {
            if (games.isEmpty()) {
                EmptyMessage("No games yet.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    games.forEach { game ->
                        val selected = selectedGameId == game.id

                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = game.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (game.opponent.isNotBlank()) {
                                        Text("Opponent: ${game.opponent}")
                                    }
                                    Text("Score: ${game.teamScore}")
                                }

                                Column {
                                    TextButton(onClick = { onSelectGame(game.id) }) {
                                        Text(if (selected) "Selected" else "Select")
                                    }
                                    TextButton(
                                        onClick = { onDeleteGame(game.id) },
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text("Delete")
                                    }
                                }
                            }
                        }
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    stats.forEach { stat ->
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = stat.player.displayLabel(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text("Points: ${stat.points} | Fouls: ${stat.fouls} | Votes: ${stat.mvpVotes}")
                                    Text(
                                        "Total Time: ${formatDuration(stat.totalTimeMillis)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }

                                if (stat.onCourt) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        StatusBadge("ON COURT")
                                        Text(
                                            text = formatDuration(stat.currentShiftTimeMillis),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
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
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    events.forEach { event ->
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = eventDescription(event, playerById),
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

@Composable
fun GameClock(
    game: GameEntity,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onSetGameTime: (Long) -> Unit,
) {
    val runningTimeMillis = game.runningTimeMillis
    val timerLastStartedAt = game.timerLastStartedAt
    val duration = game.periodDurationMillis

    var elapsedDisplay by remember(runningTimeMillis, timerLastStartedAt) {
        mutableLongStateOf(runningTimeMillis)
    }

    var isEditing by remember { mutableStateOf(value = false) }

    LaunchedEffect(timerLastStartedAt) {
        if (timerLastStartedAt != null) {
            while (true) {
                elapsedDisplay = runningTimeMillis + (System.currentTimeMillis() - timerLastStartedAt)
                delay(100)
            }
        } else {
            elapsedDisplay = runningTimeMillis
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
                        Spacer(Modifier.width(8.dp))
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
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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

@Composable
fun PlayerActionCard(
    stat: PlayerGameStat,
    onAddScore: (Long, Int) -> Unit,
    onAddFoul: (Long, Int) -> Unit,
    onAddMvpVote: (Long) -> Unit,
    gameTimerLastStartedAt: Long?,
    enabled: Boolean = true,
) {
    var shiftDisplayTime by remember(stat.currentShiftTimeMillis, gameTimerLastStartedAt) {
        mutableLongStateOf(stat.currentShiftTimeMillis)
    }

    LaunchedEffect(gameTimerLastStartedAt) {
        if (gameTimerLastStartedAt != null) {
            while (true) {
                // currentShiftTimeMillis was calculated as (game.runningTimeMillis - enteredAt)
                // We add the same "live" offset to the shift time
                shiftDisplayTime = stat.currentShiftTimeMillis + (System.currentTimeMillis() - gameTimerLastStartedAt)
                delay(100)
            }
        } else {
            shiftDisplayTime = stat.currentShiftTimeMillis
        }
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stat.player.displayLabel(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("Points: ${stat.points}   Fouls: ${stat.fouls}")
                    if (enabled) {
                        Row {
                            TextButton(
                                onClick = { onAddScore(stat.player.id, -1) },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text("-1 pt", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Row {
                        Text(
                            text = "Shift: ${formatDuration(shiftDisplayTime)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Total: ${formatDuration(stat.totalTimeMillis - stat.currentShiftTimeMillis + shiftDisplayTime)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    StatusBadge(if (stat.currentShiftTimeMillis > 0 || gameTimerLastStartedAt != null) "ON COURT" else "BENCH")
                    Spacer(Modifier.height(4.dp))
                    if (enabled) {
                        TextButton(
                            onClick = { onAddMvpVote(stat.player.id) },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.textButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Text("MVP Vote (${stat.mvpVotes})", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            if (enabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SmallActionButton(label = "+1", modifier = Modifier.weight(1f)) {
                        onAddScore(stat.player.id, 1)
                    }
                    SmallActionButton(label = "+2", modifier = Modifier.weight(1f)) {
                        onAddScore(stat.player.id, 2)
                    }
                    SmallActionButton(label = "+3", modifier = Modifier.weight(1f)) {
                        onAddScore(stat.player.id, 3)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Fouls",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedCard(
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onAddFoul(stat.player.id, -1) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Text("-", fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = stat.fouls.toString(),
                                modifier = Modifier.padding(horizontal = 8.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { onAddFoul(stat.player.id, 1) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add foul", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

fun eventDescription(
    event: GameEventEntity,
    playersById: Map<Long, PlayerEntity>,
): String {
    return when (EventType.fromRaw(event.type)) {
        EventType.START_LINEUP -> {
            val names = event.note.split(',')
                .asSequence()
                .mapNotNull { raw -> raw.trim().toLongOrNull() }
                .mapNotNull(playersById::get)
                .joinToString(" | ") { it.displayLabel() }

            "Starting lineup: $names"
        }

        EventType.SUBSTITUTION -> {
            val playerIn = event.playerId?.let(playersById::get)?.displayLabel().orEmpty()
            val playerOut = event.relatedPlayerId?.let(playersById::get)?.displayLabel().orEmpty()
            "$playerIn in for $playerOut"
        }

        EventType.SCORE -> {
            val playerName = event.playerId?.let(playersById::get)?.displayLabel().orEmpty()
            "$playerName +${event.points}"
        }

        EventType.FOUL -> {
            val playerName = event.playerId?.let(playersById::get)?.displayLabel().orEmpty()
            "$playerName foul"
        }

        EventType.MVP_VOTE -> {
            val playerName = event.playerId?.let(playersById::get)?.displayLabel().orEmpty()
            "$playerName MVP vote"
        }

        EventType.PERIOD_CHANGE -> event.note
        EventType.GAME_END -> "Game Ended"
    }
}

fun formatTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

@Composable
fun GameNotesPanel(
    notes: String,
    onNotesChange: (String) -> Unit
) {
    SectionCard(title = "Game Notes") {
        OutlinedTextField(
            value = notes,
            onValueChange = onNotesChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 150.dp),
            placeholder = { Text("Enter coaching notes here...") },
            label = { Text("Notes") }
        )
    }
}
