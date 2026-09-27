package com.lukearnold.coachesassistant.ui

import com.lukearnold.coachesassistant.R
import androidx.compose.ui.res.stringResource
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.lukearnold.coachesassistant.data.*

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
    onCreateGame: (title: String, opponent: String, periodCount: Int, periodMins: Int) -> Unit,
    onSelectGame: (Long) -> Unit,
    onSetLineup: (List<Long>) -> Unit,
    onSubstitute: (pairs: List<Pair<Long, Long>>) -> Unit,
    onAddPlayerScore: (playerId: Long, points: Int) -> Unit,
    onAdjustTeamScore: (points: Int) -> Unit,
    onAddOpponentScore: (points: Int) -> Unit,
    onAddFoul: (Long, Int) -> Unit,
    onAddMvpVote: (Long) -> Unit,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit,
    onSetGameTime: (Long) -> Unit,
    onDeleteGame: (Long) -> Unit,
    onExportGame: (Long, Uri) -> Unit,
    onExportPlayers: (Uri) -> Unit,
    onAdvancePeriod: () -> Unit,
    onEndGame: () -> Unit,
    onUpdateAbsentPlayers: (List<Long>) -> Unit,
    onUpdateNotes: (String) -> Unit,
    onExportNotes: (Long, Uri) -> Unit,
    onUpdateEvent: (GameEventEntity) -> Unit,
    onDeleteEvent: (GameEventEntity) -> Unit,
) {
    val layout = LocalResponsiveLayout.current
    val sportConfig = sport.config

    var gameTitle by rememberSaveable { mutableStateOf("") }
    var opponent by rememberSaveable { mutableStateOf("") }
    var periodCount by rememberSaveable(sport) { mutableIntStateOf(sportConfig.defaultPeriodCount) }
    var periodMins by rememberSaveable(sport) { mutableStateOf(sportConfig.defaultPeriodMinutes.toString()) }
    var showAttendanceDialog by remember { mutableStateOf(false) }
    var showNotesDialog by remember { mutableStateOf(false) }

    val exportGame = rememberIdFileExportLauncher(mimeType = "text/csv", onExport = onExportGame)
    val exportNotes = rememberIdFileExportLauncher(mimeType = "text/plain", onExport = onExportNotes)

    val exportRosterLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        uri?.let(onExportPlayers)
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
    val activeGames = games.filter { !it.isFinished }

    if (isExpanded) {
        Row(
            modifier = modifier.padding(layout.screenPadding),
            horizontalArrangement = Arrangement.spacedBy(layout.sectionGap)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(layout.sectionGap)
            ) {
                if (selectedGame != null) {
                    GameControlPanel(
                        selectedGame = selectedGame,
                        gameTimeMillis = gameTimeMillis,
                        fairShareAt = fairShareAt,
                        sportConfig = sportConfig,
                        onToggleTimer = onToggleTimer,
                        onResetTimer = onResetTimer,
                        onSetGameTime = onSetGameTime,
                        onAddPlayerScore = onAddPlayerScore,
                        onAdjustTeamScore = onAdjustTeamScore,
                        onAddOpponentScore = onAddOpponentScore,
                        onCourtIds = onCourtIds,
                        onExportGame = {
                            exportGame(selectedGame.id, "game_${selectedGame.id}.csv")
                        },
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
                            val inIds = pendingIn.toList()
                            val outIds = pendingOut.toList()
                            if (inIds.size == outIds.size && inIds.isNotEmpty()) {
                                onSubstitute(inIds.zip(outIds))
                                pendingIn.clear()
                                pendingOut.clear()
                            }
                        }
                    )
                } else {
                    EmptyMessage(stringResource(R.string.select_or_create_a_game_to_start_tracking))
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(layout.sectionGap)
            ) {
                if (selectedGame != null) {
                    GameNotesPanel(
                        onClick = { showNotesDialog = true }
                    )
                }

                GameStatsPanel(
                    stats = stats,
                    gameTimeMillis = gameTimeMillis,
                    fairShareAt = fairShareAt,
                    events = events,
                    playerById = playerById,
                    opponentName = selectedGame?.opponent.orEmpty(),
                    infraction = sportConfig.infraction,
                    onEditEvent = { editingEvent = it },
                    onDeleteEvent = onDeleteEvent,
                    gameTitle = gameTitle,
                    onGameTitleChange = { gameTitle = it },
                    opponent = opponent,
                    onOpponentChange = { opponent = it },
                    periodCount = periodCount,
                    onPeriodCountChange = { periodCount = it },
                    periodMins = periodMins,
                    onPeriodMinsChange = { periodMins = it },
                    onCreateGame = {
                        onCreateGame(gameTitle, opponent, periodCount, periodMins.toIntOrNull() ?: 10)
                        gameTitle = ""
                        opponent = ""
                    },
                    games = activeGames,
                    onSelectGame = onSelectGame,
                    onDeleteGame = onDeleteGame,
                    onExportGame = { gId ->
                        exportGame(gId, "game_$gId.csv")
                    },
                    selectedGameId = selectedGame?.id
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .verticalScroll(rememberScrollState())
                .padding(layout.screenPadding),
            verticalArrangement = Arrangement.spacedBy(layout.sectionGap)
        ) {
            Text(
                text = stringResource(R.string.game_tracker),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
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
                    onCreateGame(gameTitle, opponent, periodCount, periodMins.toIntOrNull() ?: 10)
                    gameTitle = ""
                    opponent = ""
                },
                games = activeGames,
                onSelectGame = onSelectGame,
                onDeleteGame = onDeleteGame,
                selectedGameId = selectedGame?.id,
                onlyManagement = true
            )

            if (selectedGame != null) {
                GameNotesPanel(
                    onClick = { showNotesDialog = true }
                )

                GameControlPanel(
                    selectedGame = selectedGame,
                    gameTimeMillis = gameTimeMillis,
                    fairShareAt = fairShareAt,
                    sportConfig = sportConfig,
                    onToggleTimer = onToggleTimer,
                    onResetTimer = onResetTimer,
                    onSetGameTime = onSetGameTime,
                    onAddPlayerScore = onAddPlayerScore,
                    onAdjustTeamScore = onAdjustTeamScore,
                    onAddOpponentScore = onAddOpponentScore,
                    onCourtIds = onCourtIds,
                    onExportGame = {
                        exportGame(selectedGame.id, "game_${selectedGame.id}.csv")
                    },
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
                    pendingOut = pendingOut,
                    onPlayerOutClick = { id ->
                        if (pendingOut.contains(id)) pendingOut.remove(id) else pendingOut.add(id)
                    },
                    benchStats = benchStats,
                    pendingIn = pendingIn,
                    onPlayerInClick = { id ->
                        if (pendingIn.contains(id)) pendingIn.remove(id) else pendingIn.add(id)
                    },
                ) {
                    val inIds = pendingIn.toList()
                    val outIds = pendingOut.toList()
                    if (inIds.size == outIds.size && inIds.isNotEmpty()) {
                        onSubstitute(inIds.zip(outIds))
                        pendingIn.clear()
                        pendingOut.clear()
                    }
                }

                GameStatsPanel(
                    stats = stats,
                    gameTimeMillis = gameTimeMillis,
                    fairShareAt = fairShareAt,
                    events = events,
                    playerById = playerById,
                    opponentName = selectedGame.opponent,
                    infraction = sportConfig.infraction,
                    onEditEvent = { editingEvent = it },
                    onDeleteEvent = onDeleteEvent,
                    onlyStats = true,
                    onExportGame = { gId ->
                        exportGame(gId, "game_$gId.csv")
                    }
                )
            }
        }
    }

    editingEvent?.let { event ->
        EventEditDialog(
            event = event,
            onDismiss = { editingEvent = null },
            infraction = sportConfig.infraction,
        ) { updated ->
            onUpdateEvent(updated)
            editingEvent = null
        }
    }

    if (showAttendanceDialog && selectedGame != null) {
        AttendanceDialog(
            players = players,
            absentPlayerIds = lineup.absentIds.toList(),
            onDismiss = { showAttendanceDialog = false },
            onConfirm = { ids ->
                onUpdateAbsentPlayers(ids)
                showAttendanceDialog = false
            }
        )
    }

    if (showNotesDialog && selectedGame != null) {
        CoachingNotesDialog(
            notes = selectedGame.notes,
            onDismiss = { showNotesDialog = false },
            onSave = { newNotes ->
                onUpdateNotes(newNotes)
                showNotesDialog = false
            },
            onExport = {
                exportNotes(selectedGame.id, "notes_${selectedGame.id}.txt")
            }
        )
    }
}
