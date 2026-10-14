package com.lonrad.coachesassistant.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.lonrad.coachesassistant.R
import com.lonrad.coachesassistant.data.*

@Composable
fun GameScreen(
    modifier: Modifier = Modifier,
    isExpanded: Boolean,
    sport: Sport,
    players: List<PlayerEntity>,
    games: List<GameEntity>,
    selectedGame: GameEntity?,
    lineup: GameLineup,
    stats: List<PlayerGameStat>,
    events: List<GameEventEntity>,
    /** The fewest players the team can take the field with (see SportConfig.minimumOnCourt). */
    minimumOnCourt: Int,
    /** Whether to show substitution suggestions (see subSuggestion), and the one put off with "Later". */
    suggestSubs: Boolean,
    subSnooze: SubSnooze?,
    /** Whether Game Day mode is on for the selected game (see GameDayMode). */
    gameDayActive: Boolean,
    actions: GameActions,
) {
    val layout = LocalResponsiveLayout.current
    val sportConfig = sport.config

    var gameTitle by rememberSaveable { mutableStateOf("") }
    var opponent by rememberSaveable { mutableStateOf("") }
    var periodCount by rememberSaveable(sport) { mutableIntStateOf(sportConfig.defaultPeriodCount) }
    var periodMins by rememberSaveable(sport) { mutableStateOf(sportConfig.defaultPeriodMinutes.toString()) }
    var showAttendanceDialog by remember { mutableStateOf(false) }
    var showNotesDialog by rememberSaveable { mutableStateOf(false) }

    val exportGame = rememberIdFileExportLauncher(mimeType = "text/csv", onExport = actions::exportGame)
    val exportNotes = rememberIdFileExportLauncher(mimeType = "text/plain", onExport = actions::exportNotes)

    val exportRosterLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        uri?.let(actions::exportPlayers)
    }

    val startingLineupSelection = remember(selectedGame?.id, selectedGame?.currentPeriod) { mutableStateListOf<Long>() }
    val pendingOut = remember(selectedGame?.id, selectedGame?.currentPeriod) { mutableStateListOf<Long>() }
    val pendingIn = remember(selectedGame?.id, selectedGame?.currentPeriod) { mutableStateListOf<Long>() }

    var editingEvent by remember { mutableStateOf<GameEventEntity?>(null) }

    val onCourtStats = stats.filter { it.onCourt }
    val benchStats = stats.filter { !it.onCourt }
    val onCourtIds = lineup.onCourtIds
    val liveGameTime = rememberLiveGameTime(selectedGame)
    val gameTimeMillis = { liveGameTime.value }
    // Each available (non-absent) player's equal share of playing time, as of a given clock time.
    // `stats` already leaves out absent players, so its size is the number available.
    val fairShareAt: (Long) -> FairShare? = remember(selectedGame, stats.size, events) {
        { now -> selectedGame?.let { fairShare(it, stats.size, elapsedGameMillis(events, now, it.periodDurationMillis)) } }
    }
    val playerById = players.associateBy { it.id }
    val availablePlayers = players.filter { it.id !in lineup.absentIds }
    val activeGames = games.filter { !it.isFinished }

    if (isExpanded) {
        Row(
            modifier = modifier.padding(layout.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(layout.sectionGap),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(layout.sectionGap),
            ) {
                if (selectedGame != null) {
                    GameControlPanel(
                        selectedGame = selectedGame,
                        gameTimeMillis = gameTimeMillis,
                        fairShareAt = fairShareAt,
                        sportConfig = sportConfig,
                        onToggleTimer = actions::toggleTimer,
                        onResetTimer = actions::resetTimer,
                        onSetGameTime = actions::setGameTime,
                        onAddPlayerScore = actions::addPlayerScore,
                        onAdjustTeamScore = actions::adjustTeamScore,
                        onAddOpponentScore = actions::addOpponentScore,
                        onCourtIds = onCourtIds,
                        onExportGame = {
                            exportGame(selectedGame.id, "game_${selectedGame.id}.csv")
                        },
                        exportRosterLauncher = exportRosterLauncher,
                        availablePlayers = availablePlayers,
                        minimumOnCourt = minimumOnCourt,
                        startingLineupSelection = startingLineupSelection,
                        onSetLineup = actions::setLineup,
                        onCourtStats = onCourtStats,
                        onAddFoul = actions::addFoul,
                        onAddMvpVote = actions::addMvpVote,
                        onAdvancePeriod = actions::advancePeriod,
                        onEndGame = actions::endGame,
                        gameDayActive = gameDayActive,
                        onStartGameDay = actions::startGameDay,
                        onStopGameDay = actions::stopGameDay,
                        onManageAttendance = { showAttendanceDialog = true },
                        pendingOut = pendingOut,
                        onPlayerOutClick = { id ->
                            if (pendingOut.contains(id)) pendingOut.remove(id) else pendingOut.add(id)
                        },
                        benchStats = benchStats,
                        pendingIn = pendingIn,
                        onPlayerInClick = { id ->
                            if (pendingIn.contains(id)) pendingIn.remove(id) else pendingIn.add(id)
                        },
                        onSubstitute = {
                            actions.substitute(pendingIn.toList(), pendingOut.toList())
                            pendingIn.clear()
                            pendingOut.clear()
                        },
                        suggestSubs = suggestSubs,
                        events = events,
                        subSnooze = subSnooze,
                        onSubNow = actions::subNow,
                        onSubLater = actions::subLater,
                    )
                } else {
                    EmptyMessage(stringResource(R.string.select_or_create_a_game_to_start_tracking))
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(layout.sectionGap),
            ) {
                if (selectedGame != null) {
                    GameNotesPanel(
                        onClick = { showNotesDialog = true },
                    )
                }

                GameStatsPanel(
                    stats = stats,
                    gameTimeMillis = gameTimeMillis,
                    fairShareAt = fairShareAt,
                    events = events,
                    eventsGame = selectedGame,
                    playerById = playerById,
                    opponentName = selectedGame?.opponent.orEmpty(),
                    infraction = sportConfig.infraction,
                    onEditEvent = { editingEvent = it },
                    onDeleteEvent = actions::deleteEvent,
                    gameTitle = gameTitle,
                    onGameTitleChange = { gameTitle = it },
                    opponent = opponent,
                    onOpponentChange = { opponent = it },
                    periodCount = periodCount,
                    onPeriodCountChange = { periodCount = it },
                    periodMins = periodMins,
                    onPeriodMinsChange = { periodMins = it },
                    onCreateGame = {
                        actions.createGame(gameTitle, opponent, periodCount, periodMins.toIntOrNull() ?: 10)
                        gameTitle = ""
                        opponent = ""
                    },
                    games = activeGames,
                    onSelectGame = actions::selectGame,
                    onDeleteGame = actions::deleteGame,
                    onExportGame = { gId ->
                        exportGame(gId, "game_$gId.csv")
                    },
                    selectedGameId = selectedGame?.id,
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(layout.screenPadding),
            verticalArrangement = Arrangement.spacedBy(layout.sectionGap),
        ) {
            Text(
                text = stringResource(R.string.game_tracker),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            GameStatsPanel(
                stats = emptyList(), // Hidden in this mode or moved
                events = emptyList(),
                playerById = emptyMap(),
                opponentName = selectedGame?.opponent.orEmpty(),
                infraction = sportConfig.infraction,
                onEditEvent = {},
                onDeleteEvent = {},
                gameTitle = gameTitle,
                onGameTitleChange = { gameTitle = it },
                opponent = opponent,
                onOpponentChange = { opponent = it },
                periodCount = periodCount,
                onPeriodCountChange = { periodCount = it },
                periodMins = periodMins,
                onPeriodMinsChange = { periodMins = it },
                onCreateGame = {
                    actions.createGame(gameTitle, opponent, periodCount, periodMins.toIntOrNull() ?: 10)
                    gameTitle = ""
                    opponent = ""
                },
                games = activeGames,
                onSelectGame = actions::selectGame,
                onDeleteGame = actions::deleteGame,
                selectedGameId = selectedGame?.id,
                onlyManagement = true,
            )

            if (selectedGame != null) {
                GameNotesPanel(
                    onClick = { showNotesDialog = true },
                )

                GameControlPanel(
                    selectedGame = selectedGame,
                    gameTimeMillis = gameTimeMillis,
                    fairShareAt = fairShareAt,
                    sportConfig = sportConfig,
                    onToggleTimer = actions::toggleTimer,
                    onResetTimer = actions::resetTimer,
                    onSetGameTime = actions::setGameTime,
                    onAddPlayerScore = actions::addPlayerScore,
                    onAdjustTeamScore = actions::adjustTeamScore,
                    onAddOpponentScore = actions::addOpponentScore,
                    onCourtIds = onCourtIds,
                    onExportGame = {
                        exportGame(selectedGame.id, "game_${selectedGame.id}.csv")
                    },
                    exportRosterLauncher = exportRosterLauncher,
                    availablePlayers = availablePlayers,
                    minimumOnCourt = minimumOnCourt,
                    startingLineupSelection = startingLineupSelection,
                    onSetLineup = actions::setLineup,
                    onCourtStats = onCourtStats,
                    onAddFoul = actions::addFoul,
                    onAddMvpVote = actions::addMvpVote,
                    onAdvancePeriod = actions::advancePeriod,
                    onEndGame = actions::endGame,
                    gameDayActive = gameDayActive,
                    onStartGameDay = actions::startGameDay,
                    onStopGameDay = actions::stopGameDay,
                    onManageAttendance = { showAttendanceDialog = true },
                    pendingOut = pendingOut,
                    onPlayerOutClick = { id ->
                        if (pendingOut.contains(id)) pendingOut.remove(id) else pendingOut.add(id)
                    },
                    benchStats = benchStats,
                    pendingIn = pendingIn,
                    onPlayerInClick = { id ->
                        if (pendingIn.contains(id)) pendingIn.remove(id) else pendingIn.add(id)
                    },
                    onSubstitute = {
                        actions.substitute(pendingIn.toList(), pendingOut.toList())
                        pendingIn.clear()
                        pendingOut.clear()
                    },
                    suggestSubs = suggestSubs,
                    events = events,
                    subSnooze = subSnooze,
                    onSubNow = actions::subNow,
                    onSubLater = actions::subLater,
                )

                GameStatsPanel(
                    stats = stats,
                    gameTimeMillis = gameTimeMillis,
                    fairShareAt = fairShareAt,
                    events = events,
                    eventsGame = selectedGame,
                    playerById = playerById,
                    opponentName = selectedGame.opponent,
                    infraction = sportConfig.infraction,
                    onEditEvent = { editingEvent = it },
                    onDeleteEvent = actions::deleteEvent,
                    onlyStats = true,
                    onExportGame = { gId ->
                        exportGame(gId, "game_$gId.csv")
                    },
                )
            }
        }
    }

    editingEvent?.let { event ->
        EventEditDialog(
            event = event,
            description = eventDescription(event, playerById, selectedGame?.opponent.orEmpty(), sportConfig.infraction).asString(),
            onDismiss = { editingEvent = null },
            infraction = sportConfig.infraction,
        ) { updated ->
            actions.updateEvent(updated)
            editingEvent = null
        }
    }

    if (showAttendanceDialog && selectedGame != null) {
        AttendanceDialog(
            players = players,
            absentPlayerIds = lineup.absentIds.toList(),
            onDismiss = { showAttendanceDialog = false },
            onConfirm = { ids ->
                actions.updateAbsentPlayers(ids)
                showAttendanceDialog = false
            },
        )
    }

    if (showNotesDialog && selectedGame != null) {
        CoachingNotesDialog(
            notes = selectedGame.notes,
            onDismiss = { showNotesDialog = false },
            onSave = { newNotes ->
                if (newNotes != selectedGame.notes) actions.updateNotes(newNotes)
                showNotesDialog = false
            },
            onExport = { newNotes ->
                if (newNotes != selectedGame.notes) actions.updateNotes(newNotes)
                exportNotes(selectedGame.id, "notes_${selectedGame.id}.txt")
            },
        )
    }
}
