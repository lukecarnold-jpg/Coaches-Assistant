package com.lukearnold.coachesassistant

import com.lukearnold.coachesassistant.ui.GameClockNotification
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.os.Build
import android.Manifest
import com.lukearnold.coachesassistant.R
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lukearnold.coachesassistant.data.Sport
import com.lukearnold.coachesassistant.ui.AppViewModel
import com.lukearnold.coachesassistant.ui.AppTheme
import com.lukearnold.coachesassistant.ui.AppViewModelFactory
import com.lukearnold.coachesassistant.ui.DisplayPreferences
import com.lukearnold.coachesassistant.ui.KeepScreenOn
import com.lukearnold.coachesassistant.ui.shouldKeepScreenOn
import com.lukearnold.coachesassistant.ui.GameHistoryScreen
import com.lukearnold.coachesassistant.ui.GameScreen
import com.lukearnold.coachesassistant.ui.RosterScreen
import com.lukearnold.coachesassistant.ui.LocalResponsiveLayout
import com.lukearnold.coachesassistant.ui.SettingsScreen
import com.lukearnold.coachesassistant.ui.ResponsiveLayoutProvider
import com.lukearnold.coachesassistant.ui.SportPreferences
import com.lukearnold.coachesassistant.ui.SportSelectionScreen
import com.lukearnold.coachesassistant.ui.TrainingScreen
import com.lukearnold.coachesassistant.ui.TrainingViewModel
import com.lukearnold.coachesassistant.ui.config
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Transparent system bars whose icons follow the light/dark theme; without this, Android
        // 15+'s enforced edge-to-edge left white status-bar icons on the light background.
        enableEdgeToEdge()
        setContent {
            AppTheme {
                // Gives every screen the theme's background and default text colour. Screens
                // inside a Scaffold already got this, but the sport picker doesn't have one, so
                // its plain text was black even in dark mode.
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ResponsiveLayoutProvider {
                        RotationApp()
                    }
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
    @StringRes val label: Int,
    val icon: @Composable () -> Unit,
)

/** An emoji used as a tab icon; hidden from screen readers since the tab's label already names it. */
@Composable
private fun EmojiIcon(emoji: String) {
    Text(emoji, modifier = Modifier.clearAndSetSemantics {})
}

private fun navTabSpecsFor(sport: Sport): List<NavTabSpec> = buildList {
    add(NavTabSpec(AppTab.ROSTER, R.string.roster) { EmojiIcon("👥") })
    add(NavTabSpec(AppTab.GAME, R.string.game) { EmojiIcon(sport.emoji) })
    add(NavTabSpec(AppTab.TRAINING, R.string.training) { EmojiIcon("🏋️") })
    add(NavTabSpec(AppTab.HISTORY, R.string.history) { EmojiIcon("🕘") })
    add(NavTabSpec(AppTab.SETTINGS, R.string.settings) { Icon(Icons.Default.Settings, contentDescription = null) })
}

@Composable
private fun SportApp(
    sport: Sport,
    onSwitchSport: () -> Unit,
) {
    val application = LocalContext.current.applicationContext as Application
    // One per sport, so one sport's generated drills don't show up on another's Training tab.
    val trainingViewModel: TrainingViewModel = viewModel(key = "TrainingViewModel-${sport.name}")
    val appViewModel: AppViewModel = viewModel(
        key = "AppViewModel-${sport.name}",
        factory = remember(sport) { AppViewModelFactory(application, sport) },
    )
    val navTabSpecs = remember(sport) { navTabSpecsFor(sport) }

    val players by appViewModel.players.collectAsState()
    val games by appViewModel.games.collectAsState()
    val selectedGame by appViewModel.selectedGame.collectAsState()
    val gameDayGameId by appViewModel.gameDayGameId.collectAsState()
    val minimumOnCourt by appViewModel.minimumOnCourt.collectAsState()
    val context = LocalContext.current
    var keepScreenOnWhileClockRuns by remember { mutableStateOf(DisplayPreferences.keepScreenOnWhileClockRuns(context)) }
    var clockNotification by remember {
        mutableStateOf(GameClockNotification.isEnabled(context) && GameClockNotification.canPost(context))
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        appViewModel.setClockNotificationEnabled(granted)
        clockNotification = granted
        if (!granted) appViewModel.reportNotificationsDenied()
    }
    KeepScreenOn(shouldKeepScreenOn(selectedGame, keepScreenOnWhileClockRuns))
    val lineup by appViewModel.selectedGameLineup.collectAsState()
    val events by appViewModel.selectedGameEvents.collectAsState()
    val stats by appViewModel.selectedGameStats.collectAsState()
    val apiKeys by trainingViewModel.apiKeys.collectAsState()
    val selectedAiProvider by trainingViewModel.selectedProvider.collectAsState()
    val customAiEndpoint by trainingViewModel.customEndpoint.collectAsState()

    var currentTab by rememberSaveable(sport) { mutableStateOf(AppTab.ROSTER.name) }
    // Open on the Game tab if a game is in progress (e.g. reopening the app mid-game), unless the
    // user has already picked a tab. Decided once per sport, and kept across rotation.
    var startTabDecided by rememberSaveable(sport) { mutableStateOf(false) }
    LaunchedEffect(appViewModel) {
        if (startTabDecided) return@LaunchedEffect
        val inProgress = appViewModel.hasGameInProgress()
        // Checked again: the user may have picked a tab while that was loading.
        if (inProgress && !startTabDecided) currentTab = AppTab.GAME.name
        startTabDecided = true
    }
    val snackbarHostState = remember { SnackbarHostState() }

    val responsiveLayout = LocalResponsiveLayout.current
    val isExpanded = responsiveLayout.widthDp >= 600

    val undoLabel = stringResource(R.string.undo)
    val resources = LocalResources.current
    LaunchedEffect(appViewModel) {
        // collectLatest: a new message replaces the one showing (and its Undo) straight away.
        appViewModel.messages.collectLatest { message ->
            val undo = message.undo
            val result = snackbarHostState.showSnackbar(
                message = message.text.resolve(resources),
                actionLabel = if (undo != null) undoLabel else null,
                duration = if (undo != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) undo?.invoke()
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        if (isExpanded) {
            NavigationRail {
                navTabSpecs.forEach { spec ->
                    NavigationRailItem(
                        selected = (currentTab == spec.tab.name),
                        onClick = {
                            currentTab = spec.tab.name
                            startTabDecided = true
                        },
                        icon = spec.icon,
                        label = { Text(stringResource(spec.label)) },
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
                                onClick = {
                                    currentTab = spec.tab.name
                                    startTabDecided = true
                                },
                                icon = spec.icon,
                                label = { Text(stringResource(spec.label)) },
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
                        lineup = lineup,
                        stats = stats,
                        events = events,
                        onCreateGame = appViewModel::createGame,
                        onSelectGame = appViewModel::selectGame,
                        onSetLineup = appViewModel::setStartingLineup,
                        minimumOnCourt = minimumOnCourt,
                        onSubstitute = appViewModel::substitute,
                        onAddPlayerScore = appViewModel::addPlayerScore,
                        onAdjustTeamScore = appViewModel::adjustTeamScore,
                        onAddOpponentScore = appViewModel::addOpponentScore,
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
                        gameDayActive = selectedGame?.id != null && selectedGame?.id == gameDayGameId,
                        onStartGameDay = appViewModel::startGameDay,
                        onStopGameDay = appViewModel::stopGameDay,
                        onUpdateAbsentPlayers = appViewModel::updateAbsentPlayers,
                        onUpdateNotes = appViewModel::updateGameNotes,
                        onExportNotes = appViewModel::exportGameNotesTxt,
                        onUpdateEvent = appViewModel::updateEvent,
                        onDeleteEvent = appViewModel::deleteEvent,
                    )

                    AppTab.TRAINING -> TrainingScreen(
                        modifier = Modifier.fillMaxSize(),
                        isExpanded = isExpanded,
                        sport = sport,
                        trainingViewModel = trainingViewModel,
                    )

                    AppTab.HISTORY -> {
                        // Collected here rather than with the other state above, so season totals
                        // are only computed while the History tab is showing.
                        val season by appViewModel.seasonStats.collectAsState()
                        GameHistoryScreen(
                            modifier = Modifier.fillMaxSize(),
                            games = games,
                            season = season,
                            infraction = sport.config.infraction,
                            onViewGame = { gameId ->
                                appViewModel.selectGame(gameId)
                                currentTab = AppTab.GAME.name
                            },
                            onDeleteGame = appViewModel::deleteGame,
                            onExportGame = appViewModel::exportGameCsv,
                        )
                    }

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
                        onBackup = appViewModel::exportBackup,
                        onRestore = appViewModel::restoreBackup,
                        minimumOnCourt = minimumOnCourt,
                        onMinimumOnCourtChange = appViewModel::setMinimumOnCourt,
                        clockNotification = clockNotification,
                        onClockNotificationChange = { enabled ->
                            if (enabled && !GameClockNotification.canPost(context) &&
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                            ) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else if (enabled && !GameClockNotification.canPost(context)) {
                                appViewModel.reportNotificationsDenied() // switched off in system settings
                            } else {
                                appViewModel.setClockNotificationEnabled(enabled)
                                clockNotification = enabled
                            }
                        },
                        keepScreenOnWhileClockRuns = keepScreenOnWhileClockRuns,
                        onKeepScreenOnWhileClockRunsChange = { enabled ->
                            DisplayPreferences.setKeepScreenOnWhileClockRuns(context, enabled)
                            keepScreenOnWhileClockRuns = enabled
                        },
                    )
                }
            }
        }
    }
}
