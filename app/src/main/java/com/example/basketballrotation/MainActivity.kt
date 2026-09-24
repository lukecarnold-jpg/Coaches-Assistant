package com.example.basketballrotation

import android.app.Application
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.basketballrotation.data.Sport
import com.example.basketballrotation.ui.AppViewModel
import com.example.basketballrotation.ui.AppTheme
import com.example.basketballrotation.ui.AppViewModelFactory
import com.example.basketballrotation.ui.GameHistoryScreen
import com.example.basketballrotation.ui.GameScreen
import com.example.basketballrotation.ui.RosterScreen
import com.example.basketballrotation.ui.LocalResponsiveLayout
import com.example.basketballrotation.ui.SettingsScreen
import com.example.basketballrotation.ui.ResponsiveLayoutProvider
import com.example.basketballrotation.ui.SportPreferences
import com.example.basketballrotation.ui.SportSelectionScreen
import com.example.basketballrotation.ui.TrainingScreen
import com.example.basketballrotation.ui.TrainingViewModel
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                ResponsiveLayoutProvider {
                    RotationApp()
                }
            }
        }
    }
}

@Composable
private fun RotationApp() {
    val context = LocalContext.current
    var selectedSport by rememberSaveable { mutableStateOf(SportPreferences.load(context)) }

    val sport = selectedSport
    if (sport == null) {
        SportSelectionScreen(
            modifier = Modifier.fillMaxSize(),
            onSelectSport = {
                SportPreferences.save(context, it)
                selectedSport = it
            },
        )
    } else {
        SportApp(
            sport = sport,
            onSwitchSport = {
                SportPreferences.save(context, null)
                selectedSport = null
            },
        )
    }
}

enum class AppTab {
    ROSTER,
    GAME,
    TRAINING,
    HISTORY,
    SETTINGS
}

private data class NavTabSpec(
    val tab: AppTab,
    val label: String,
    val icon: @Composable () -> Unit,
)

/** An emoji used as a tab icon; hidden from screen readers since the tab's label already names it. */
@Composable
private fun EmojiIcon(emoji: String) {
    Text(emoji, modifier = Modifier.clearAndSetSemantics {})
}

private fun navTabSpecsFor(sport: Sport): List<NavTabSpec> = buildList {
    add(NavTabSpec(AppTab.ROSTER, "Roster") { EmojiIcon("👥") })
    add(NavTabSpec(AppTab.GAME, "Game") { EmojiIcon(sport.emoji) })
    if (sport == Sport.BASKETBALL) {
        add(NavTabSpec(AppTab.TRAINING, "Training") { EmojiIcon("🏋️") })
    }
    add(NavTabSpec(AppTab.HISTORY, "History") { EmojiIcon("🕘") })
    add(NavTabSpec(AppTab.SETTINGS, "Settings") { Icon(Icons.Default.Settings, contentDescription = null) })
}

@Composable
private fun SportApp(
    sport: Sport,
    onSwitchSport: () -> Unit,
    trainingViewModel: TrainingViewModel = viewModel(),
) {
    val application = LocalContext.current.applicationContext as Application
    val appViewModel: AppViewModel = viewModel(
        key = "AppViewModel-${sport.name}",
        factory = remember(sport) { AppViewModelFactory(application, sport) },
    )
    val navTabSpecs = remember(sport) { navTabSpecsFor(sport) }

    val players by appViewModel.players.collectAsState()
    val games by appViewModel.games.collectAsState()
    val selectedGame by appViewModel.selectedGame.collectAsState()
    val events by appViewModel.selectedGameEvents.collectAsState()
    val stats by appViewModel.selectedGameStats.collectAsState()
    val apiKeys by trainingViewModel.apiKeys.collectAsState()
    val selectedAiProvider by trainingViewModel.selectedProvider.collectAsState()
    val customAiEndpoint by trainingViewModel.customEndpoint.collectAsState()

    var currentTab by rememberSaveable(sport) { mutableStateOf(AppTab.ROSTER.name) }
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
                navTabSpecs.forEach { spec ->
                    NavigationRailItem(
                        selected = (currentTab == spec.tab.name),
                        onClick = { currentTab = spec.tab.name },
                        icon = spec.icon,
                        label = { Text(spec.label) },
                    )
                }
            }
        }

        Scaffold(
            modifier = Modifier.weight(1f),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!isExpanded) {
                    NavigationBar {
                        navTabSpecs.forEach { spec ->
                            NavigationBarItem(
                                selected = (currentTab == spec.tab.name),
                                onClick = { currentTab = spec.tab.name },
                                icon = spec.icon,
                                label = { Text(spec.label) },
                            )
                        }
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
                        sport = sport,
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
                        sport = sport,
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
                        trainingViewModel = trainingViewModel,
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
                        onClearSelectedData = appViewModel::clearSelectedData,
                        apiKeys = apiKeys,
                        selectedProvider = selectedAiProvider,
                        hasBuiltInApiKey = trainingViewModel.hasBuiltInApiKey,
                        customEndpoint = customAiEndpoint,
                        onSelectProvider = trainingViewModel::selectProvider,
                        onSaveApiKey = trainingViewModel::updateApiKey,
                        onClearApiKey = trainingViewModel::clearApiKey,
                        onSaveCustomEndpoint = trainingViewModel::updateCustomEndpoint,
                        currentSport = sport,
                        onSwitchSport = onSwitchSport,
                    )
                }
            }
        }
    }
}
