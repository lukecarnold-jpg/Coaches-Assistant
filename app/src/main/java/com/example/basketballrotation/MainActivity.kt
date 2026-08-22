package com.example.basketballrotation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.basketballrotation.ui.AppViewModel
import com.example.basketballrotation.ui.GameHistoryScreen
import com.example.basketballrotation.ui.GameScreen
import com.example.basketballrotation.ui.RosterScreen
import com.example.basketballrotation.ui.LocalResponsiveLayout
import com.example.basketballrotation.ui.SettingsScreen
import com.example.basketballrotation.ui.ResponsiveLayoutProvider
import com.example.basketballrotation.ui.TrainingScreen
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ResponsiveLayoutProvider {
                    BasketballApp()
                }
            }
        }
    }
}

enum class AppTab {
    ROSTER,
    GAME,
    TRAINING,
    HISTORY,
    SETTINGS
}

@Composable
private fun BasketballApp(
    appViewModel: AppViewModel = viewModel(),
) {
    val players by appViewModel.players.collectAsState()
    val games by appViewModel.games.collectAsState()
    val selectedGame by appViewModel.selectedGame.collectAsState()
    val events by appViewModel.selectedGameEvents.collectAsState()
    val stats by appViewModel.selectedGameStats.collectAsState()

    var currentTab by rememberSaveable { mutableStateOf(AppTab.ROSTER.name) }
    val snackbarHostState = remember { SnackbarHostState() }

    val responsiveLayout = LocalResponsiveLayout.current
    val isExpanded = responsiveLayout.widthDp >= 600

    LaunchedEffect(Unit) {
        appViewModel.messages.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        if (isExpanded) {
            NavigationRail {
                NavigationRailItem(
                    selected = (currentTab == AppTab.ROSTER.name),
                    onClick = { currentTab = AppTab.ROSTER.name },
                    icon = { Text("👥") },
                    label = { Text("Roster") },
                )
                NavigationRailItem(
                    selected = (currentTab == AppTab.GAME.name),
                    onClick = { currentTab = AppTab.GAME.name },
                    icon = { Text("🏀") },
                    label = { Text("Game") },
                )
                NavigationRailItem(
                    selected = (currentTab == AppTab.TRAINING.name),
                    onClick = { currentTab = AppTab.TRAINING.name },
                    icon = { Text("🏋️") },
                    label = { Text("Training") },
                )
                NavigationRailItem(
                    selected = (currentTab == AppTab.HISTORY.name),
                    onClick = { currentTab = AppTab.HISTORY.name },
                    icon = { Text("🕘") },
                    label = { Text("History") },
                )
                NavigationRailItem(
                    selected = (currentTab == AppTab.SETTINGS.name),
                    onClick = { currentTab = AppTab.SETTINGS.name },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                )
            }
        }

        Scaffold(
            modifier = Modifier.weight(1f),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!isExpanded) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = (currentTab == AppTab.ROSTER.name),
                            onClick = { currentTab = AppTab.ROSTER.name },
                            icon = { Text("👥") },
                            label = { Text("Roster") },
                        )
                        NavigationBarItem(
                            selected = (currentTab == AppTab.GAME.name),
                            onClick = { currentTab = AppTab.GAME.name },
                            icon = { Text("🏀") },
                            label = { Text("Game") },
                        )
                        NavigationBarItem(
                            selected = (currentTab == AppTab.TRAINING.name),
                            onClick = { currentTab = AppTab.TRAINING.name },
                            icon = { Text("🏋️") },
                            label = { Text("Training") },
                        )
                        NavigationBarItem(
                            selected = (currentTab == AppTab.HISTORY.name),
                            onClick = { currentTab = AppTab.HISTORY.name },
                            icon = { Text("🕘") },
                            label = { Text("History") },
                        )
                        NavigationBarItem(
                            selected = (currentTab == AppTab.SETTINGS.name),
                            onClick = { currentTab = AppTab.SETTINGS.name },
                            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            label = { Text("Settings") },
                        )
                    }
                }
            },
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                val tab = AppTab.entries.find { it.name == currentTab } ?: AppTab.ROSTER
                when (tab) {
                    AppTab.ROSTER -> RosterScreen(
                        modifier = Modifier.fillMaxSize(),
                        isExpanded = isExpanded,
                        players = players,
                        onAddPlayer = appViewModel::addPlayer,
                        onUpdatePlayer = appViewModel::updatePlayer,
                        onDeletePlayer = appViewModel::deletePlayer,
                        onExportPlayers = appViewModel::exportPlayersCsv,
                        onImportPlayers = appViewModel::importPlayersCsv,
                    )

                    AppTab.GAME -> GameScreen(
                        modifier = Modifier.fillMaxSize(),
                        isExpanded = isExpanded,
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
                        onAddMvpVote = appViewModel::addMvpVote,
                        onToggleTimer = appViewModel::toggleTimer,
                        onResetTimer = appViewModel::resetTimer,
                        onSetGameTime = appViewModel::setGameTime,
                        onDeleteGame = appViewModel::deleteGame,
                        onExportGame = appViewModel::exportGameCsv,
                        onExportPlayers = appViewModel::exportPlayersCsv,
                        onAdvancePeriod = appViewModel::advancePeriod,
                        onEndGame = appViewModel::endGame,
                        onUpdateAbsentPlayers = appViewModel::updateAbsentPlayers,
                        onUpdateNotes = appViewModel::updateGameNotes,
                        onExportNotes = appViewModel::exportGameNotesTxt,
                        onUpdateEvent = appViewModel::updateEvent,
                        onDeleteEvent = appViewModel::deleteEvent,
                    )

                    AppTab.TRAINING -> TrainingScreen(
                        modifier = Modifier.fillMaxSize(),
                        isExpanded = isExpanded,
                    )

                    AppTab.HISTORY -> GameHistoryScreen(
                        modifier = Modifier.fillMaxSize(),
                        games = games,
                        onViewGame = { gameId ->
                            appViewModel.selectGame(gameId)
                            currentTab = AppTab.GAME.name
                        },
                        onDeleteGame = appViewModel::deleteGame,
                        onExportGame = appViewModel::exportGameCsv,
                    )

                    AppTab.SETTINGS -> SettingsScreen(
                        modifier = Modifier.fillMaxSize(),
                        onClearAllData = appViewModel::clearAllData,
                    )
                }
            }
        }
    }
}
