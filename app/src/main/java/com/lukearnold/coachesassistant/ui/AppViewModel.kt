package com.lukearnold.coachesassistant.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lukearnold.coachesassistant.data.AppDatabase
import com.lukearnold.coachesassistant.data.BackupIo
import com.lukearnold.coachesassistant.data.GameRepository
import com.lukearnold.coachesassistant.data.EventType
import com.lukearnold.coachesassistant.data.GameCsvIo
import com.lukearnold.coachesassistant.data.GameEntity
import com.lukearnold.coachesassistant.data.GameEventEntity
import com.lukearnold.coachesassistant.data.MvpVotes
import com.lukearnold.coachesassistant.data.PlayerEntity
import com.lukearnold.coachesassistant.data.RosterImportResult
import com.lukearnold.coachesassistant.data.Sport
import com.lukearnold.coachesassistant.data.GameLineup
import com.lukearnold.coachesassistant.data.capToPeriod
import com.lukearnold.coachesassistant.data.remainingPeriodTimeMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A message for the snackbar, optionally with an Undo action for the change it reports. */
data class UiMessage(val text: String, val undo: (() -> Unit)? = null)

data class PlayerGameStat(
    val player: PlayerEntity,
    val points: Int,
    val fouls: Int,
    val mvpVotes: Int,
    val timesSubbedOff: Int,
    val onCourt: Boolean,
    val currentShiftTimeMillis: Long,
    val currentBenchTimeMillis: Long,
    val shiftStartGameTimeMillis: Long?,
    val totalTimeMillis: Long,
    /** Game time the player last went to the bench this period (0 if never on court), or null on court. */
    val benchStartGameTimeMillis: Long? = null,
) {
    // The *TimeMillis fields above are as of the game's stored running time, which only advances
    // when the clock is stopped. These give the same figures as of [gameTimeMillis], the live
    // clock, so on-screen times keep moving while the clock runs.

    fun shiftTimeAt(gameTimeMillis: Long): Long =
        shiftStartGameTimeMillis?.let { maxOf(0L, gameTimeMillis - it) } ?: 0L

    fun benchTimeAt(gameTimeMillis: Long): Long =
        benchStartGameTimeMillis?.let { maxOf(0L, gameTimeMillis - it) } ?: 0L

    fun totalTimeAt(gameTimeMillis: Long): Long =
        totalTimeMillis - currentShiftTimeMillis + shiftTimeAt(gameTimeMillis)
}

class AppViewModel(application: Application, private val sport: Sport) : AndroidViewModel(application) {
    private val repository = GameRepository(AppDatabase.getInstance(application))
    private val csvIo = GameCsvIo(AppDatabase.getInstance(application))
    private val backupIo = BackupIo(AppDatabase.getInstance(application))

    private val selectedGameId = MutableStateFlow<Long?>(null)
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    val players = repository.playersForSport(sport).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val games = repository.gamesForSport(sport).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val selectedGame: StateFlow<GameEntity?> =
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        combine(games, selectedGameId) { gameList, chosenId ->
            gameList.firstOrNull { it.id == chosenId } ?: defaultGame(gameList)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            null,
        )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedGameEvents = selectedGame.flatMapLatest { game ->
        if (game == null) flowOf(emptyList()) else repository.observeEvents(game.id)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedGameLineup = selectedGame.flatMapLatest { game ->
        if (game == null) flowOf(GameLineup()) else repository.observeLineup(game.id)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        GameLineup(),
    )

    /** Season totals across this sport's finished games; only computed while something observes it. */
    val seasonStats: StateFlow<SeasonSummary> =
        combine(
            players,
            games,
            repository.observeFinishedGameEvents(sport),
            repository.observeFinishedGameLineups(sport),
        ) { playerList, gameList, events, lineups ->
            calculateSeasonStats(playerList, gameList, events, lineups)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            SeasonSummary(),
        )

    val selectedGameStats =
        combine(players, selectedGame, selectedGameLineup, selectedGameEvents) { playerList, game, lineup, events ->
            calculatePlayerGameStats(playerList, game, lineup, events)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList(),
        )

    init {
        viewModelScope.launch {
            games.collect { gameList ->
                val currentSelection = selectedGameId.value

                when {
                    gameList.isEmpty() -> selectedGameId.value = null
                    currentSelection == null -> selectedGameId.value = defaultGame(gameList)?.id
                    gameList.none { it.id == currentSelection } -> {
                        selectedGameId.value = defaultGame(gameList)?.id
                    }
                }
            }
        }

        // Stop the clock when the period runs out. collectLatest restarts the wait whenever the
        // game changes (stopped, edited, or a different game selected), so it only ever fires for
        // the clock that's actually running. If the app isn't around to do this, the time is still
        // capped at the period length wherever it's read (see GameEntity.currentGameTimeMillis).
        viewModelScope.launch {
            selectedGame.collectLatest { game ->
                if (game == null || game.isFinished || game.timerLastStartedAt == null) return@collectLatest
                val remaining = game.remainingPeriodTimeMillis() ?: return@collectLatest
                delay(remaining)
                repository.stopTimer(game.id)
                _messages.emit(UiMessage("End of period — clock stopped."))
            }
        }
    }

    fun selectGame(gameId: Long) {
        selectedGameId.value = gameId
    }

    fun addPlayer(name: String, jerseyNumberText: String, position: String? = null) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            _messages.tryEmit(UiMessage("Enter a player name."))
            return
        }

        val jerseyNumber = jerseyNumberText.trim().toIntOrNull()
        // Jersey number is now optional, only error if they entered something that isn't a number
        if ((jerseyNumberText.trim().isNotEmpty()) && (jerseyNumber == null)) {
            _messages.tryEmit(UiMessage("Jersey number must be numeric."))
            return
        }

        viewModelScope.launch {
            repository.addPlayer(sport, trimmedName, jerseyNumber, position)
            _messages.emit(UiMessage("Player added."))
        }
    }

    fun updatePlayer(player: PlayerEntity, newName: String, newJerseyNumberText: String, newPosition: String? = player.position) {
        val trimmedName = newName.trim()
        if (trimmedName.isBlank()) {
            _messages.tryEmit(UiMessage("Name cannot be empty."))
            return
        }

        val jerseyNumber = newJerseyNumberText.trim().toIntOrNull()
        if ((newJerseyNumberText.trim().isNotEmpty()) && (jerseyNumber == null)) {
            _messages.tryEmit(UiMessage("Jersey number must be numeric."))
            return
        }

        viewModelScope.launch {
            repository.updatePlayer(player.copy(name = trimmedName, jerseyNumber = jerseyNumber, position = newPosition))
            _messages.emit(UiMessage("Player updated."))
        }
    }

    fun deletePlayer(playerId: Long) {
        viewModelScope.launch {
            repository.deletePlayer(playerId)
            _messages.emit(UiMessage("Player removed."))
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
            _messages.emit(UiMessage("All data cleared."))
        }
    }

    fun clearSelectedData(players: Boolean, games: Boolean, events: Boolean) {
        viewModelScope.launch {
            repository.clearSelectedData(sport, players, games, events)
            _messages.emit(UiMessage("Selected data cleared."))
        }
    }

    fun createGame(title: String, opponent: String, periodCount: Int, periodMins: Int) {
        val finalTitle = title.trim().ifBlank { "Game ${games.value.size + 1}" }
        val durationMillis = periodMins * 60 * 1000L

        viewModelScope.launch {
            val gameId = repository.createGame(
                sport = sport,
                title = finalTitle,
                opponent = opponent,
                periodCount = periodCount,
                periodDurationMillis = durationMillis,
                onCourtSize = sport.config.onCourtSize,
            )
            selectedGameId.value = gameId
            _messages.emit(UiMessage("Game created."))
        }
    }

    fun advancePeriod() {
        val gameId = selectedGame.value?.id ?: return
        viewModelScope.launch {
            repository.advancePeriod(gameId)
            _messages.emit(UiMessage("Advanced to next period."))
        }
    }

    fun endGame() {
        val gameId = selectedGame.value?.id ?: return
        viewModelScope.launch {
            repository.endGame(gameId)
            _messages.emit(UiMessage("Game finalized and court reset."))
        }
    }

    fun updateAbsentPlayers(playerIds: List<Long>) {
        val gameId = selectedGame.value?.id ?: return
        viewModelScope.launch {
            repository.updateAbsentPlayers(gameId, playerIds)
            _messages.emit(UiMessage("Attendance updated."))
        }
    }

    fun updateGameNotes(notes: String) {
        val gameId = selectedGame.value?.id ?: return
        viewModelScope.launch {
            repository.updateGameNotes(gameId, notes)
        }
    }

    fun updateEvent(event: GameEventEntity) {
        viewModelScope.launch {
            repository.updateEvent(event)
            _messages.emit(UiMessage("Event updated."))
        }
    }

    fun deleteEvent(event: GameEventEntity) {
        viewModelScope.launch {
            repository.deleteEvent(event)
            _messages.emit(UiMessage("Event removed."))
        }
    }

    fun deleteGame(gameId: Long) {
        viewModelScope.launch {
            repository.deleteGame(gameId)
            _messages.emit(UiMessage("Game and related events deleted."))
        }
    }

    fun setStartingLineup(playerIds: List<Long>) {
        val gameId = selectedGame.value?.id
        if (gameId == null) {
            _messages.tryEmit(UiMessage("Create or select a game first."))
            return
        }
        val requiredSize = sport.config.onCourtSize
        if (playerIds.distinct().size != requiredSize) {
            _messages.tryEmit(UiMessage("Select exactly $requiredSize players for the starting lineup."))
            return
        }

        viewModelScope.launch {
            repository.selectStartingLineup(gameId, playerIds, requiredSize)
            _messages.emit(UiMessage("Starting lineup saved."))
        }
    }

    /** Makes each (player in, player out) swap in [pairs], offering one Undo for the whole batch. */
    fun substitute(pairs: List<Pair<Long, Long>>) {
        val gameId = selectedGame.value?.id
        if (gameId == null) {
            _messages.tryEmit(UiMessage("Create or select a game first."))
            return
        }
        if (pairs.isEmpty()) {
            _messages.tryEmit(UiMessage("Pick one player in and one player out."))
            return
        }
        if (pairs.any { (playerIn, playerOut) -> playerIn == playerOut }) {
            _messages.tryEmit(UiMessage("Player in and player out must be different."))
            return
        }

        viewModelScope.launch {
            val eventIds = pairs.mapNotNull { (playerIn, playerOut) -> repository.substitute(gameId, playerIn, playerOut) }
            when {
                eventIds.isEmpty() -> _messages.emit(UiMessage("Substitution not recorded: the lineup has changed."))
                eventIds.size < pairs.size -> announceUndoable(
                    eventIds,
                    text = "${eventIds.size} of ${pairs.size} substitutions recorded; the rest no longer matched the lineup.",
                )
                eventIds.size == 1 -> announceUndoable(eventIds)
                else -> announceUndoable(eventIds, text = "${eventIds.size} substitutions recorded.")
            }
        }
    }

    /** Scores for a player on our team. */
    fun addPlayerScore(playerId: Long, points: Int) {
        val gameId = selectedGame.value?.id
        if (gameId == null) {
            _messages.tryEmit(UiMessage("Create or select a game first."))
            return
        }

        viewModelScope.launch {
            repository.addTeamScore(gameId, playerId, points)?.let { announceUndoable(listOf(it)) }
        }
    }

    /** Corrects our team's score without crediting or debiting any player. */
    fun adjustTeamScore(points: Int) {
        val gameId = selectedGame.value?.id
        if (gameId == null) {
            _messages.tryEmit(UiMessage("Create or select a game first."))
            return
        }

        viewModelScope.launch {
            repository.addTeamScore(gameId, playerId = null, points = points)?.let { announceUndoable(listOf(it)) }
        }
    }

    fun addOpponentScore(points: Int) {
        val gameId = selectedGame.value?.id
        if (gameId == null) {
            _messages.tryEmit(UiMessage("Create or select a game first."))
            return
        }

        viewModelScope.launch {
            repository.addOpponentScore(gameId, points)?.let { announceUndoable(listOf(it)) }
        }
    }

    fun addFoul(playerId: Long, adjustment: Int = 1) {
        val gameId = selectedGame.value?.id
        if (gameId == null) {
            _messages.tryEmit(UiMessage("Create or select a game first."))
            return
        }

        viewModelScope.launch {
            repository.addFoul(gameId, playerId, adjustment)?.let { announceUndoable(listOf(it)) }
        }
    }

    /**
     * Reports the action that recorded [eventIds] with an Undo that reverses exactly those events
     * (see GameRepository.undoEvents). [text] defaults to the event log's description of the one event.
     */
    private suspend fun announceUndoable(eventIds: List<Long>, text: String? = null) {
        val message = text ?: repository.getEvent(eventIds.single())?.let { event ->
            val description = eventDescription(
                event,
                players.value.associateBy { it.id },
                opponentName = selectedGame.value?.opponent.orEmpty(),
                infractionLabel = sport.config.infractionLabel,
            )
            if (event.type == EventType.FOUL.name && event.points < 0) "$description removed" else description
        } ?: return
        _messages.emit(
            UiMessage(message) {
                viewModelScope.launch {
                    val undone = repository.undoEvents(eventIds)
                    _messages.emit(UiMessage(if (undone) "Undone." else "Couldn't undo: that's already been changed."))
                }
            },
        )
    }

    fun toggleTimer() {
        val game = selectedGame.value ?: return
        val gameId = game.id
        if (game.timerLastStartedAt == null && game.remainingPeriodTimeMillis() == 0L) {
            _messages.tryEmit(UiMessage("This period's time is up. Move to the next period or adjust the clock."))
            return
        }
        viewModelScope.launch {
            repository.toggleTimer(gameId)
        }
    }

    fun resetTimer() {
        val game = selectedGame.value ?: return
        viewModelScope.launch {
            val earliest = repository.resetTimer(game.id)
            _messages.emit(
                UiMessage(
                    if (earliest == null) {
                        "Clock reset to ${formatDuration(game.periodDurationMillis)}."
                    } else {
                        clockTooEarlyMessage(game.periodDurationMillis, earliest)
                    },
                ),
            )
        }
    }

    fun addMvpVote(playerId: Long) {
        val gameId = selectedGame.value?.id ?: return
        viewModelScope.launch {
            repository.addMvpVote(gameId, playerId)?.let { announceUndoable(listOf(it)) }
        }
    }

    fun setGameTime(timeMillis: Long) {
        val game = selectedGame.value ?: return
        viewModelScope.launch {
            repository.setGameTime(game.id, timeMillis)?.let { earliest ->
                _messages.emit(UiMessage(clockTooEarlyMessage(game.periodDurationMillis, earliest)))
            }
        }
    }

    /** Saves every sport's data (not just this one's) to [uri]; see BackupIo. */
    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                backupIo.export(getApplication<Application>().contentResolver, uri)
            }.onSuccess { counts ->
                _messages.emit(UiMessage("Backup saved: ${counts.players} players and ${counts.games} games across all sports."))
            }.onFailure {
                _messages.emit(UiMessage(it.message ?: "Backup failed."))
            }
        }
    }

    /** Replaces all data, for every sport, with the backup at [uri]; nothing changes if it can't be read. */
    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                backupIo.restore(getApplication<Application>().contentResolver, uri)
            }.onSuccess { counts ->
                _messages.emit(UiMessage("Restored ${counts.players} players and ${counts.games} games."))
            }.onFailure {
                _messages.emit(UiMessage(it.message ?: "Restore failed. Nothing was changed."))
            }
        }
    }

    fun exportPlayersCsv(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                csvIo.exportPlayersCsv(getApplication<Application>().contentResolver, uri, sport)
            }.onSuccess {
                _messages.emit(UiMessage("Roster CSV exported."))
            }.onFailure {
                _messages.emit(UiMessage(it.message ?: "Export failed."))
            }
        }
    }

    fun importPlayersCsv(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                csvIo.importPlayersCsv(getApplication<Application>().contentResolver, uri, sport, sport.config.squadPositions)
            }.onSuccess { result ->
                _messages.emit(UiMessage(rosterImportMessage(result)))
            }.onFailure {
                _messages.emit(UiMessage(it.message ?: "Import failed."))
            }
        }
    }

    fun exportGameNotesTxt(gameId: Long, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                csvIo.exportGameNotesTxt(getApplication<Application>().contentResolver, gameId, uri)
            }.onSuccess {
                _messages.emit(UiMessage("Coaching notes exported."))
            }.onFailure {
                _messages.emit(UiMessage(it.message ?: "Export failed."))
            }
        }
    }

    fun exportGameCsv(gameId: Long, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                csvIo.exportGameCsv(getApplication<Application>().contentResolver, gameId, uri)
            }.onSuccess {
                _messages.emit(UiMessage("Game CSV exported."))
            }.onFailure {
                _messages.emit(UiMessage(it.message ?: "Export failed."))
            }
        }
    }
}

/**
 * The game to show when none is explicitly selected: the newest one still in progress, since
 * finished games live on the History tab, or the newest game if every game is finished.
 */
private fun defaultGame(newestFirst: List<GameEntity>): GameEntity? =
    newestFirst.firstOrNull { !it.isFinished } ?: newestFirst.firstOrNull()

class AppViewModelFactory(
    private val application: Application,
    private val sport: Sport,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AppViewModel(application, sport) as T
    }
}

/** The snackbar after a roster CSV import. */
fun rosterImportMessage(result: RosterImportResult): String {
    fun players(n: Int) = if (n == 1) "1 player" else "$n players"
    return when {
        result.added == 0 && result.skipped == 0 -> "No players found in that file."
        result.added == 0 -> "No new players: ${if (result.skipped == 1) "they were" else "all ${result.skipped} were"} already on the roster."
        result.skipped == 0 -> "Imported ${players(result.added)}."
        else -> "Imported ${players(result.added)} (${result.skipped} already on the roster)."
    }
}

/**
 * Why the clock can't go back that far. The clock counts down, so times are shown as time
 * remaining, the same as the game clock.
 */
fun clockTooEarlyMessage(periodDurationMillis: Long, earliestElapsedMillis: Long): String {
    val latest = formatDuration(maxOf(0L, periodDurationMillis - earliestElapsedMillis))
    return "Can't set the clock back past $latest: something was already recorded then this period, " +
        "and moving before it would lose playing time."
}

/**
 * Replays [events] in chronological order to derive each roster player's current on-court/bench
 * status (from [lineup]), shift and bench durations, and per-game totals as of [game]'s current
 * running time.
 *
 * A pure function (no Android/coroutine dependencies) so it can be unit tested directly.
 */
fun calculatePlayerGameStats(
    playerList: List<PlayerEntity>,
    game: GameEntity?,
    lineup: GameLineup,
    events: List<GameEventEntity>,
): List<PlayerGameStat> {
    // Times are read capped at the period length: games recorded before the clock stopped itself
    // can have events stamped hours past the end of a period (see capToPeriod).
    val periodLength = game?.periodDurationMillis ?: 0L
    fun GameEventEntity.at(): Long = capToPeriod(gameTimeMillis, periodLength)
    val runningTime = capToPeriod(game?.runningTimeMillis ?: 0L, periodLength)

    val onCourt = lineup.onCourtIds
    val absent = lineup.absentIds

    val totalTimeByPlayer = mutableMapOf<Long, Long>()
    val lastEnteredAt = mutableMapOf<Long, Long>()
    val lastBenchedAt = mutableMapOf<Long, Long>()
    val pointsByPlayer = mutableMapOf<Long, Int>()
    val foulsByPlayer = mutableMapOf<Long, Int>()
    val timesSubbedOffByPlayer = mutableMapOf<Long, Int>()

    // Process events chronologically to calculate stats and times
    val sortedEvents = events.sortedWith(compareBy<GameEventEntity>({ it.timestamp }, { it.id }))

    sortedEvents.forEach { event ->
        when (EventType.fromRaw(event.type)) {
            EventType.START_LINEUP -> {
                // note contains the player IDs
                event.note.split(',')
                    .mapNotNull { it.trim().toLongOrNull() }
                    .forEach { id ->
                        lastEnteredAt[id] = event.at()
                        lastBenchedAt.remove(id)
                    }
            }

            EventType.SUBSTITUTION -> {
                event.playerId?.let { playerInId ->
                    lastEnteredAt[playerInId] = event.at()
                    lastBenchedAt.remove(playerInId)
                }
                event.relatedPlayerId?.let { playerOutId ->
                    timesSubbedOffByPlayer[playerOutId] = (timesSubbedOffByPlayer[playerOutId] ?: 0) + 1
                    val entered = lastEnteredAt.remove(playerOutId)
                    if (entered != null) {
                        val duration = maxOf(0L, event.at() - entered)
                        totalTimeByPlayer[playerOutId] = (totalTimeByPlayer[playerOutId] ?: 0L) + duration
                    }
                    lastBenchedAt[playerOutId] = event.at()
                }
            }

            EventType.SCORE -> event.playerId?.let { playerId ->
                pointsByPlayer[playerId] = (pointsByPlayer[playerId] ?: 0) + event.points
            }

            EventType.FOUL -> event.playerId?.let { playerId ->
                foulsByPlayer[playerId] = (foulsByPlayer[playerId] ?: 0) + event.points
            }

            EventType.PERIOD_CHANGE -> {
                // Close all active shifts using the event's gameTimeMillis (which is end of period)
                lastEnteredAt.forEach { (id, entered) ->
                    val duration = maxOf(0L, event.at() - entered)
                    totalTimeByPlayer[id] = (totalTimeByPlayer[id] ?: 0L) + duration
                }
                lastEnteredAt.clear()
                // The clock restarts at 0 each period, so a sub-off time from an earlier period
                // can't be compared against the new period's clock; bench time counts from the
                // start of the current period instead, just as shift time does.
                lastBenchedAt.clear()
            }

            EventType.GAME_END -> {
                lastEnteredAt.forEach { (id, entered) ->
                    val duration = maxOf(0L, event.at() - entered)
                    totalTimeByPlayer[id] = (totalTimeByPlayer[id] ?: 0L) + duration
                }
                lastEnteredAt.clear()
            }

            EventType.OPPONENT_SCORE,
            EventType.MVP_VOTE -> {}
        }
    }

    val mvpVotesMap = MvpVotes.parse(game?.mvpVotesJson.orEmpty())

    return playerList
        .asSequence()
        .filter { !absent.contains(it.id) }
        .map { player ->
            val isOnCourt = onCourt.contains(player.id)
            val enteredAt = lastEnteredAt[player.id]
                ?: runningTime

            val currentShift = if (isOnCourt) {
                maxOf(0L, runningTime - enteredAt)
            } else {
                0L
            }

            val benchedAt = lastBenchedAt[player.id] ?: 0L
            val currentBench = if (isOnCourt) {
                0L
            } else {
                maxOf(0L, runningTime - benchedAt)
            }

            val totalTime = (totalTimeByPlayer[player.id] ?: 0L) + currentShift

            PlayerGameStat(
                player = player,
                points = pointsByPlayer[player.id] ?: 0,
                fouls = foulsByPlayer[player.id] ?: 0,
                mvpVotes = mvpVotesMap[player.id] ?: 0,
                timesSubbedOff = timesSubbedOffByPlayer[player.id] ?: 0,
                onCourt = isOnCourt,
                currentShiftTimeMillis = currentShift,
                currentBenchTimeMillis = currentBench,
                shiftStartGameTimeMillis = if (isOnCourt) enteredAt else null,
                totalTimeMillis = totalTime,
                benchStartGameTimeMillis = if (isOnCourt) null else benchedAt,
            )
        }
        .toList()
}