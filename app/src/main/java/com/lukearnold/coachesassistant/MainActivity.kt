package com.lukearnold.coachesassistant

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lukearnold.coachesassistant.R
import com.lukearnold.coachesassistant.data.Sport
import com.lukearnold.coachesassistant.ui.AppTheme
import com.lukearnold.coachesassistant.ui.AppViewModel
import com.lukearnold.coachesassistant.ui.CricketGameScreen
import com.lukearnold.coachesassistant.ui.CricketViewModel
import com.lukearnold.coachesassistant.ui.DataViewModel
import com.lukearnold.coachesassistant.ui.GameClockNotification
import com.lukearnold.coachesassistant.ui.GameHistoryScreen
import com.lukearnold.coachesassistant.ui.GameScreen
import com.lukearnold.coachesassistant.ui.GameSyncViewModel
import com.lukearnold.coachesassistant.ui.KeepScreenOn
import com.lukearnold.coachesassistant.ui.LocalResponsiveLayout
import com.lukearnold.coachesassistant.ui.ResponsiveLayoutProvider
import com.lukearnold.coachesassistant.ui.RosterScreen
import com.lukearnold.coachesassistant.ui.SettingsScreen
import com.lukearnold.coachesassistant.ui.SettingsViewModel
import com.lukearnold.coachesassistant.ui.SportPreferences
import com.lukearnold.coachesassistant.ui.SportSelectionScreen
import com.lukearnold.coachesassistant.ui.TrainingScreen
import com.lukearnold.coachesassistant.ui.TrainingViewModel
import com.lukearnold.coachesassistant.ui.appViewModelFactory
import com.lukearnold.coachesassistant.ui.config
import com.lukearnold.coachesassistant.ui.cricketViewModelFactory
import com.lukearnold.coachesassistant.ui.dataViewModelFactory
import com.lukearnold.coachesassistant.ui.shouldKeepScreenOn
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.merge

// An AppCompatActivity so the language chosen in Settings applies on Android versions before 13.
class MainActivity : AppCompatActivity() {
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
    // Created here, above the sport picker, so there's one for the whole app however many sports
    // are visited.
    viewModel<GameSyncViewModel>(factory = GameSyncViewModel.Factory)
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
    SETTINGS,
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
    // One per sport, so one sport's generated drills don't show up on another's Training tab.
    val trainingViewModel: TrainingViewModel = viewModel(key = "TrainingViewModel-${sport.name}", factory = TrainingViewModel.Factory)
    val appViewModel: AppViewModel = viewModel(
        key = "AppViewModel-${sport.name}",
        factory = remember(sport) { appViewModelFactory(sport) },
    )
    val dataViewModel: DataViewModel = viewModel(
        key = "DataViewModel-${sport.name}",
        factory = remember(sport) { dataViewModelFactory(sport) },
    )
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    // Only cricket (a scorecard sport) has one.
    val cricketViewModel: CricketViewModel? = if (sport.usesScorecards) {
        viewModel(key = "CricketViewModel-${sport.name}", factory = remember(sport) { cricketViewModelFactory(sport) })
    } else {
        null
    }
    val navTabSpecs = remember(sport) { navTabSpecsFor(sport) }

    val players by appViewModel.players.collectAsStateWithLifecycle()
    val games by appViewModel.games.collectAsStateWithLifecycle()
    val selectedGame by appViewModel.selectedGame.collectAsStateWithLifecycle()
    val gameDayGameId by appViewModel.gameDayGameId.collectAsStateWithLifecycle()
    val minimumOnCourt by appViewModel.minimumOnCourt.collectAsStateWithLifecycle()
    val isLoaded by appViewModel.isLoaded.collectAsStateWithLifecycle()
    val keepScreenOnWhileClockRuns by settingsViewModel.keepScreenOnWhileClockRuns.collectAsStateWithLifecycle()
    val clockNotification by settingsViewModel.clockNotification.collectAsStateWithLifecycle()
    val suggestSubs by settingsViewModel.suggestSubs.collectAsStateWithLifecycle()
    val notifySubs by settingsViewModel.notifySubs.collectAsStateWithLifecycle()
    val onNotifySubsChange = rememberNotificationSetting(settingsViewModel::setNotifySubs, settingsViewModel::reportNotificationsDenied)
    val onClockNotificationChange =
        rememberNotificationSetting(settingsViewModel::setClockNotification, settingsViewModel::reportNotificationsDenied)
    val subSnooze by appViewModel.subSnooze.collectAsStateWithLifecycle()
    KeepScreenOn(shouldKeepScreenOn(selectedGame, keepScreenOnWhileClockRuns))
    val lineup by appViewModel.selectedGameLineup.collectAsStateWithLifecycle()
    val events by appViewModel.selectedGameEvents.collectAsStateWithLifecycle()
    val stats by appViewModel.selectedGameStats.collectAsStateWithLifecycle()
    val apiKeys by trainingViewModel.apiKeys.collectAsStateWithLifecycle()
    val selectedAiProvider by trainingViewModel.selectedProvider.collectAsStateWithLifecycle()
    val customAiEndpoint by trainingViewModel.customEndpoint.collectAsStateWithLifecycle()
    val aiModelOverrides by trainingViewModel.modelOverrides.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable(sport) { mutableStateOf(AppTab.ROSTER) }
    // Open on the Game tab if a game is in progress (e.g. reopening the app mid-game), unless the
    // user has already picked a tab. Decided once per sport, and kept across rotation.
    var startTabDecided by rememberSaveable(sport) { mutableStateOf(false) }
    LaunchedEffect(appViewModel) {
        if (startTabDecided) return@LaunchedEffect
        val inProgress = appViewModel.hasGameInProgress()
        // Checked again: the user may have picked a tab while that was loading.
        if (inProgress && !startTabDecided) currentTab = AppTab.GAME
        startTabDecided = true
    }
    // Back returns to the main tab (the Game tab during a game, otherwise the Roster) before it
    // leaves the app, so a stray press mid-game doesn't close it.
    val homeTab = if (selectedGame?.isFinished == false) AppTab.GAME else AppTab.ROSTER
    BackHandler(enabled = currentTab != homeTab) {
        currentTab = homeTab
        startTabDecided = true
    }
    val snackbarHostState = remember { SnackbarHostState() }

    val responsiveLayout = LocalResponsiveLayout.current
    val isExpanded = responsiveLayout.widthDp >= 600

    val undoLabel = stringResource(R.string.undo)
    val resources = LocalResources.current
    LaunchedEffect(appViewModel, dataViewModel, settingsViewModel, cricketViewModel) {
        // collectLatest: a new message replaces the one showing (and its Undo) straight away.
        merge(
            appViewModel.messages,
            dataViewModel.messages,
            settingsViewModel.messages,
            cricketViewModel?.messages ?: emptyFlow(),
        ).collectLatest { message ->
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
                        selected = (currentTab == spec.tab),
                        onClick = {
                            currentTab = spec.tab
                            startTabDecided = true
                        },
                        icon = spec.icon,
                        label = { NavLabel(stringResource(spec.label)) },
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
                                selected = (currentTab == spec.tab),
                                onClick = {
                                    currentTab = spec.tab
                                    startTabDecided = true
                                },
                                icon = spec.icon,
                                label = { NavLabel(stringResource(spec.label)) },
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
                val tab = currentTab
                // The roster, game, and history tabs wait for their data (usually a split second)
                // rather than flashing their "none yet" messages.
                val waitingForData = !isLoaded && tab in setOf(AppTab.ROSTER, AppTab.GAME, AppTab.HISTORY)
                if (!waitingForData) {
                    when (tab) {
                        AppTab.ROSTER -> RosterScreen(
                            modifier = Modifier.fillMaxSize(),
                            isExpanded = isExpanded,
                            sport = sport,
                            players = players,
                            onAddPlayer = appViewModel::addPlayer,
                            onUpdatePlayer = appViewModel::updatePlayer,
                            onDeletePlayer = appViewModel::deletePlayer,
                            onExportPlayers = dataViewModel::exportPlayersCsv,
                            onImportPlayers = dataViewModel::importPlayersCsv,
                        )

                        AppTab.GAME -> if (cricketViewModel != null) {
                            CricketGameScreen(
                                modifier = Modifier.fillMaxSize(),
                                isExpanded = isExpanded,
                                players = players,
                                games = games,
                                selectedGame = selectedGame,
                                lineup = lineup,
                                cricketViewModel = cricketViewModel,
                                // No periods or clock: one period of no set length.
                                onCreateGame = { title, opponent -> appViewModel.createGame(title, opponent, 1, 0) },
                                onSelectGame = appViewModel::selectGame,
                                onDeleteGame = appViewModel::deleteGame,
                                onExportGame = dataViewModel::exportGameCsv,
                                onEndGame = appViewModel::endGame,
                                onUpdateAbsentPlayers = appViewModel::updateAbsentPlayers,
                                onUpdateNotes = appViewModel::updateGameNotes,
                                onExportNotes = dataViewModel::exportGameNotesTxt,
                            )
                        } else {
                            GameScreen(
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
                                suggestSubs = suggestSubs,
                                subSnooze = subSnooze,
                                onSubNow = appViewModel::acceptSubSuggestion,
                                onSubLater = appViewModel::snoozeSubSuggestion,
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
                                onExportGame = dataViewModel::exportGameCsv,
                                onExportPlayers = dataViewModel::exportPlayersCsv,
                                onAdvancePeriod = appViewModel::advancePeriod,
                                onEndGame = appViewModel::endGame,
                                gameDayActive = selectedGame?.id != null && selectedGame?.id == gameDayGameId,
                                onStartGameDay = appViewModel::startGameDay,
                                onStopGameDay = appViewModel::stopGameDay,
                                onUpdateAbsentPlayers = appViewModel::updateAbsentPlayers,
                                onUpdateNotes = appViewModel::updateGameNotes,
                                onExportNotes = dataViewModel::exportGameNotesTxt,
                                onUpdateEvent = appViewModel::updateEvent,
                                onDeleteEvent = appViewModel::deleteEvent,
                            )
                        }

                        AppTab.TRAINING -> TrainingScreen(
                            modifier = Modifier.fillMaxSize(),
                            isExpanded = isExpanded,
                            sport = sport,
                            trainingViewModel = trainingViewModel,
                        )

                        AppTab.HISTORY -> {
                            // Collected here rather than with the other state above, so season totals
                            // are only computed while the History tab is showing.
                            val season by appViewModel.seasonStats.collectAsStateWithLifecycle()
                            val cricketSeason = cricketViewModel?.season?.collectAsStateWithLifecycle()?.value
                            GameHistoryScreen(
                                modifier = Modifier.fillMaxSize(),
                                games = games,
                                season = season,
                                cricketSeason = cricketSeason,
                                infraction = sport.config.infraction,
                                onViewGame = { gameId ->
                                    appViewModel.selectGame(gameId)
                                    currentTab = AppTab.GAME
                                },
                                onDeleteGame = appViewModel::deleteGame,
                                onExportGame = dataViewModel::exportGameCsv,
                            )
                        }

                        AppTab.SETTINGS -> SettingsScreen(
                            modifier = Modifier.fillMaxSize(),
                            onClearAllData = dataViewModel::clearAllData,
                            onClearSelectedData = dataViewModel::clearSelectedData,
                            apiKeys = apiKeys,
                            selectedProvider = selectedAiProvider,
                            hasBuiltInApiKey = trainingViewModel.hasBuiltInApiKey,
                            customEndpoint = customAiEndpoint,
                            onSelectProvider = trainingViewModel::selectProvider,
                            onSaveApiKey = trainingViewModel::updateApiKey,
                            onClearApiKey = trainingViewModel::clearApiKey,
                            onSaveCustomEndpoint = trainingViewModel::updateCustomEndpoint,
                            modelOverrides = aiModelOverrides,
                            onSaveModel = trainingViewModel::updateModel,
                            currentSport = sport,
                            onSwitchSport = onSwitchSport,
                            onBackup = dataViewModel::exportBackup,
                            onRestore = dataViewModel::restoreBackup,
                            // Cricket has no lineup to field short-handed.
                            minimumOnCourt = if (sport.usesScorecards) null else minimumOnCourt,
                            suggestSubs = suggestSubs,
                            onSuggestSubsChange = settingsViewModel::setSuggestSubs,
                            notifySubs = notifySubs,
                            onNotifySubsChange = onNotifySubsChange,
                            onMinimumOnCourtChange = appViewModel::setMinimumOnCourt,
                            clockNotification = clockNotification,
                            onClockNotificationChange = onClockNotificationChange,
                            keepScreenOnWhileClockRuns = keepScreenOnWhileClockRuns,
                            onKeepScreenOnWhileClockRunsChange = settingsViewModel::setKeepScreenOnWhileClockRuns,
                        )
                    }
                }
            }
        }
    }
}

/**
 * A navigation label on one line, shrinking to fit with large text sizes rather than breaking
 * mid-word ("Settin / gs") in the narrow space each tab has.
 */
@Composable
private fun NavLabel(text: String) {
    Text(
        text,
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = LocalTextStyle.current.fontSize),
    )
}

/**
 * The change handler for a setting that posts notifications: turning it on first asks for the
 * notifications permission if needed (Android 13+), and [onDenied] explains if it's refused or
 * notifications are switched off in system settings. [onSet] saves the result.
 */
@Composable
private fun rememberNotificationSetting(onSet: (Boolean) -> Unit, onDenied: () -> Unit): (Boolean) -> Unit {
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        onSet(granted)
        if (!granted) onDenied()
    }
    return { enabled ->
        when {
            !enabled || GameClockNotification.canPost(context) -> onSet(enabled)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> onDenied() // switched off in system settings
        }
    }
}
