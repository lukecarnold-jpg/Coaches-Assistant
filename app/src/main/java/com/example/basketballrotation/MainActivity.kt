package com.example.basketballrotation

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.basketballrotation.data.EventType
import com.example.basketballrotation.data.GameEntity
import com.example.basketballrotation.data.GameEventEntity
import com.example.basketballrotation.data.PlayerEntity
import com.example.basketballrotation.data.displayLabel
import com.example.basketballrotation.data.onCourtIds
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.vectorResource
import com.example.basketballrotation.ui.AppViewModel
import com.example.basketballrotation.ui.PlayerGameStat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                BasketballApp()
            }
        }
    }
}

enum class AppTab {
    ROSTER,
    GAME,
    SETTINGS
}

@Composable
private fun BasketballApp(appViewModel: AppViewModel = viewModel()) {
    val players by appViewModel.players.collectAsState()
    val games by appViewModel.games.collectAsState()
    val selectedGame by appViewModel.selectedGame.collectAsState()
    val events by appViewModel.selectedGameEvents.collectAsState()
    val stats by appViewModel.selectedGameStats.collectAsState()

    var currentTab by rememberSaveable { mutableStateOf(AppTab.ROSTER.name) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        appViewModel.messages.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == AppTab.ROSTER.name,
                    onClick = { currentTab = AppTab.ROSTER.name },
                    icon = { Text("👥") },
                    label = { Text("Roster") },
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.GAME.name,
                    onClick = { currentTab = AppTab.GAME.name },
                    icon = { Text("🏀") },
                    label = { Text("Game") },
                )
                NavigationBarItem(
                    selected = currentTab == AppTab.SETTINGS.name,
                    onClick = { currentTab = AppTab.SETTINGS.name },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") }
                )
            }
        }
    ) { paddingValues ->
        when (AppTab.valueOf(currentTab)) {
            AppTab.ROSTER -> RosterScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                players = players,
                onAddPlayer = appViewModel::addPlayer,
                onUpdatePlayer = appViewModel::updatePlayer,
                onDeletePlayer = appViewModel::deletePlayer,
                onExportPlayers = appViewModel::exportPlayersCsv
            )

            AppTab.GAME -> GameScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                players = players,
                games = games,
                selectedGame = selectedGame,
                stats = stats,
                events = events,
                onCreateGame = appViewModel::createGame,
                onSelectGame = appViewModel::selectGame,
                onSetLineup = appViewModel::setStartingLineup,
                onSubstitute = appViewModel::substitute,
                onAddScore = appViewModel::addScore,
                onAddFoul = appViewModel::addFoul,
                onToggleTimer = appViewModel::toggleTimer,
                onResetTimer = appViewModel::resetTimer,
                onAddMvpVote = appViewModel::addMvpVote,
                onSetGameTime = appViewModel::setGameTime,
                onDeleteGame = appViewModel::deleteGame,
                onExportGame = appViewModel::exportSelectedGameCsv,
                onExportPlayers = appViewModel::exportPlayersCsv,
                onAdvancePeriod = appViewModel::advancePeriod,
                onEndGame = appViewModel::endGame,
                onUpdateEvent = appViewModel::updateEvent,
                onDeleteEvent = appViewModel::deleteEvent
            )

            AppTab.SETTINGS -> SettingsScreen(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onClearAllData = appViewModel::clearAllData
            )
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier = Modifier,
    onClearAllData: () -> Unit
) {
    var showConfirm by remember { mutableStateOf(value = false) }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        SectionCard(title = "Data management") {
            Text("Clear all local data including players, games, and events. This action cannot be undone.")
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { showConfirm = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Clear all data")
            }
        }

        SectionCard(title = "Exports") {
            Text("To view exported CSV files, use your device's file manager (e.g., Files or Downloads app).")
        }

        if (showConfirm) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showConfirm = false },
                title = { Text("Confirm deletion") },
                text = { Text("Are you sure you want to delete all data? This will remove all players and games.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onClearAllData()
                            showConfirm = false
                        }
                    ) {
                        Text("Delete everything", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun RosterScreen(
    modifier: Modifier = Modifier,
    players: List<PlayerEntity>,
    onAddPlayer: (String, String) -> Unit,
    onUpdatePlayer: (PlayerEntity, String, String) -> Unit,
    onDeletePlayer: (Long) -> Unit,
    onExportPlayers: (Uri) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var jerseyNumber by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(value = false) }

    var editingPlayer by remember { mutableStateOf<PlayerEntity?>(null) }
    var editName by remember { mutableStateOf("") }
    var editJerseyNumber by remember { mutableStateOf("") }

    val exportRosterLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let(onExportPlayers)
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Team roster",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = name,
            onValueChange = { 
                name = it
                showError = false 
            },
            label = { Text("Player name") },
            modifier = Modifier.fillMaxWidth(),
            isError = showError && name.isBlank()
        )

        OutlinedTextField(
            value = jerseyNumber,
            onValueChange = { 
                jerseyNumber = it
                showError = false 
            },
            label = { Text("Jersey number (optional)") },
            modifier = Modifier.fillMaxWidth()
        )

        if (showError) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text(
                    text = "Player Name is required.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        showError = true
                    } else {
                        onAddPlayer(name, jerseyNumber)
                        name = ""
                        jerseyNumber = ""
                        showError = false
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Add player")
            }

            OutlinedButton(
                onClick = { exportRosterLauncher.launch("players.csv") },
                modifier = Modifier.weight(1f)
            ) {
                Text("Export roster CSV")
            }
        }

        if (players.isEmpty()) {
            EmptyMessage("No players yet. Add your roster first.")
        } else {
            players.forEach { player ->
                if (editingPlayer?.id == player.id) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = editName,
                                onValueChange = { editName = it },
                                label = { Text("Player name") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = editJerseyNumber,
                                onValueChange = { editJerseyNumber = it },
                                label = { Text("Jersey number (optional)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        onUpdatePlayer(player, editName, editJerseyNumber)
                                        editingPlayer = null
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Save")
                                }
                                TextButton(
                                    onClick = { editingPlayer = null },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                } else {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = player.displayLabel(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )

                            Row {
                                TextButton(
                                    onClick = { editingPlayer = player }
                                ) {
                                    Text("Edit")
                                }
                                TextButton(
                                    onClick = { onDeletePlayer(player.id) },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Remove")
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
private fun GameScreen(
    modifier: Modifier = Modifier,
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
    onUpdateEvent: (GameEventEntity) -> Unit,
    onDeleteEvent: (GameEventEntity) -> Unit
) {
    var gameTitle by rememberSaveable { mutableStateOf("") }
    var opponent by rememberSaveable { mutableStateOf("") }
    var useQuarters by rememberSaveable { mutableStateOf(false) }
    var periodMins by rememberSaveable { mutableStateOf("10") }

    val exportGameLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let(onExportGame)
    }

    val exportRosterLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
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

        SectionCard(title = "Create a game") {
            OutlinedTextField(
                value = gameTitle,
                onValueChange = { gameTitle = it },
                label = { Text("Game title") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = opponent,
                onValueChange = { opponent = it },
                label = { Text("Opponent") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = useQuarters, onCheckedChange = { useQuarters = it })
                Text("Use Quarters (instead of Halves)")
            }

            OutlinedTextField(
                value = periodMins,
                onValueChange = { periodMins = it.filter { c -> c.isDigit() } },
                label = { Text("Period duration (minutes)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    onCreateGame(gameTitle, opponent, useQuarters, periodMins.toIntOrNull() ?: 10)
                    gameTitle = ""
                    opponent = ""
                }
            ) {
                Text("Create game")
            }
        }

        SectionCard(title = "Games") {
            if (games.isEmpty()) {
                EmptyMessage("No games yet.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    games.forEach { game ->
                        val selected = selectedGame?.id == game.id

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

        if (selectedGame == null) {
            EmptyMessage("Select a game to track rotation, scoring, and fouls.")
        } else {
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
                                        playerOutId = stat.player.id
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
                                            playerInId = stat.player.id
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    onSubstitute(playerInId, playerOutId)
                                    playerInId = null
                                    playerOutId = null
                                },
                                enabled = (playerInId != null) && (playerOutId != null)
                            ) {
                                Text("Record substitution")
                            }
                        }
                    }
                }
            }

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
                                        Text("Points: ${stat.points}   Fouls: ${stat.fouls}   Votes: ${stat.mvpVotes}")
                                    }

                                    if (stat.onCourt) {
                                        Column(horizontalAlignment = Alignment.End) {
                                            StatusBadge()
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
                                        IconButton(onClick = { editingEvent = event }) {
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

    if (editingEvent != null) {
        EventEditDialog(
            event = editingEvent!!,
            onDismiss = { editingEvent = null }
        ) { updated ->
            onUpdateEvent(updated)
            editingEvent = null
        }
    }
}

@Composable
private fun EventEditDialog(
    event: GameEventEntity,
    onDismiss: () -> Unit,
    onConfirm: (GameEventEntity) -> Unit
) {
    var points by remember { mutableStateOf(event.points.toString()) }
    var note by remember { mutableStateOf(event.note) }
    
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Event") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Type: ${event.type}")
                if (event.type == EventType.SCORE.name || event.type == EventType.FOUL.name) {
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
            Button(onClick = {
                onConfirm(event.copy(points = points.toIntOrNull() ?: event.points, note = note))
            }) {
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
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            content()
        }
    }
}

@Composable
private fun GameClock(
    game: GameEntity,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onSetGameTime: (Long) -> Unit
) {
    val runningTimeMillis = game.runningTimeMillis
    val timerLastStartedAt = game.timerLastStartedAt
    val duration = game.periodDurationMillis

    var elapsedDisplay by remember(runningTimeMillis, timerLastStartedAt) {
        mutableLongStateOf(runningTimeMillis)
    }

    var isEditing by remember { mutableStateOf(false) }

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
                Button(onClick = {
                    val mins = minutesText.toLongOrNull() ?: 0L
                    val secs = secondsText.toLongOrNull() ?: 0L
                    onSetGameTime((mins * 60 + secs) * 1000)
                    isEditing = false
                }) {
                    Text("Set")
                }
            }
        }
    }
}

@Composable
private fun PlayerActionCard(
    stat: PlayerGameStat,
    onAddScore: (Long, Int) -> Unit,
    onAddFoul: (Long, Int) -> Unit,
    onAddMvpVote: (Long) -> Unit,
    gameTimerLastStartedAt: Long?,
    enabled: Boolean = true
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
                    Text(
                        text = "Shift: ${formatDuration(shiftDisplayTime)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
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

@Composable
private fun SmallActionButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Text(label)
    }
}

@Composable
private fun SelectablePlayerRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background =
        if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = background,
        tonalElevation = if (selected) 2.dp else 0.dp
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun StatusBadge(text: String = "On court") {
    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun eventDescription(
    event: GameEventEntity,
    playersById: Map<Long, PlayerEntity>
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

private fun formatTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}