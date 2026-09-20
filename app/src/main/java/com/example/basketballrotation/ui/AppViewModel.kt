package com.example.basketballrotation.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.basketballrotation.data.AppDatabase
import com.example.basketballrotation.data.BasketballRepository
import com.example.basketballrotation.data.EventType
import com.example.basketballrotation.data.GameEntity
import com.example.basketballrotation.data.GameEventEntity
import com.example.basketballrotation.data.MvpVotes
import com.example.basketballrotation.data.PlayerEntity
import com.example.basketballrotation.data.Sport
import com.example.basketballrotation.data.absentPlayerIds
import com.example.basketballrotation.data.onCourtIds
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
)

class AppViewModel(application: Application, private val sport: Sport) : AndroidViewModel(application) {
    private val repository = BasketballRepository(AppDatabase.getInstance(application))

    private val selectedGameId = MutableStateFlow<Long?>(null)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
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
            when {
                gameList.isEmpty() -> null
                chosenId == null -> gameList.first()
                else -> gameList.firstOrNull { it.id == chosenId } ?: gameList.first()
            }
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

    val selectedGameStats =
        combine(players, selectedGame, selectedGameEvents) { playerList, game, events ->
            calculatePlayerGameStats(playerList, game, events)
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
                    currentSelection == null -> selectedGameId.value = gameList.firstOrNull()?.id
                    gameList.none { it.id == currentSelection } -> {
                        selectedGameId.value = gameList.firstOrNull()?.id
                    }
                }
            }
        }
    }

    fun selectGame(gameId: Long) {
        selectedGameId.value = gameId
    }

    fun addPlayer(name: String, jerseyNumberText: String, position: String? = null) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            _messages.tryEmit("Enter a player name.")
            return
        }

        val jerseyNumber = jerseyNumberText.trim().toIntOrNull()
        // Jersey number is now optional, only error if they entered something that isn't a number
        if ((jerseyNumberText.trim().isNotEmpty()) && (jerseyNumber == null)) {
            _messages.tryEmit("Jersey number must be numeric.")
            return
        }

        viewModelScope.launch {
            repository.addPlayer(sport, trimmedName, jerseyNumber, position)
            _messages.emit("Player added.")
        }
    }

    fun updatePlayer(player: PlayerEntity, newName: String, newJerseyNumberText: String, newPosition: String? = player.position) {
        val trimmedName = newName.trim()
        if (trimmedName.isBlank()) {
            _messages.tryEmit("Name cannot be empty.")
            return
        }

        val jerseyNumber = newJerseyNumberText.trim().toIntOrNull()
        if ((newJerseyNumberText.trim().isNotEmpty()) && (jerseyNumber == null)) {
            _messages.tryEmit("Jersey number must be numeric.")
            return
        }

        viewModelScope.launch {
            repository.updatePlayer(player.copy(name = trimmedName, jerseyNumber = jerseyNumber, position = newPosition))
            _messages.emit("Player updated.")
        }
    }

    fun deletePlayer(playerId: Long) {
        viewModelScope.launch {
            repository.deletePlayer(playerId)
            _messages.emit("Player removed.")
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
            _messages.emit("All data cleared.")
        }
    }

    fun clearSelectedData(players: Boolean, games: Boolean, events: Boolean) {
        viewModelScope.launch {
            repository.clearSelectedData(players, games, events)
            _messages.emit("Selected data cleared.")
        }
    }

    fun createGame(title: String, opponent: String, useQuarters: Boolean, periodMins: Int) {
        val finalTitle = title.trim().ifBlank { "Game ${games.value.size + 1}" }
        val durationMillis = periodMins * 60 * 1000L

        viewModelScope.launch {
            val gameId = repository.createGame(
                sport = sport,
                title = finalTitle,
                opponent = opponent,
                useQuarters = useQuarters,
                periodDurationMillis = durationMillis,
                onCourtSize = sport.config.onCourtSize,
            )
            selectedGameId.value = gameId
            _messages.emit("Game created.")
        }
    }

    fun advancePeriod() {
        val gameId = selectedGame.value?.id ?: return
        viewModelScope.launch {
            repository.advancePeriod(gameId)
            _messages.emit("Advanced to next period.")
        }
    }

    fun endGame() {
        val gameId = selectedGame.value?.id ?: return
        viewModelScope.launch {
            repository.endGame(gameId)
            _messages.emit("Game finalized and court reset.")
        }
    }

    fun updateAbsentPlayers(playerIds: List<Long>) {
        val gameId = selectedGame.value?.id ?: return
        viewModelScope.launch {
            repository.updateAbsentPlayers(gameId, playerIds)
            _messages.emit("Attendance updated.")
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
            _messages.emit("Event updated.")
        }
    }

    fun deleteEvent(event: GameEventEntity) {
        viewModelScope.launch {
            repository.deleteEvent(event)
            _messages.emit("Event removed.")
        }
    }

    fun deleteGame(gameId: Long) {
        viewModelScope.launch {
            repository.deleteGame(gameId)
            _messages.emit("Game and related events deleted.")
        }
    }

    fun setStartingLineup(playerIds: List<Long>) {
        val gameId = selectedGame.value?.id
        if (gameId == null) {
            _messages.tryEmit("Create or select a game first.")
            return
        }
        val requiredSize = sport.config.onCourtSize
        if (playerIds.distinct().size != requiredSize) {
            _messages.tryEmit("Select exactly $requiredSize players for the starting lineup.")
            return
        }

        viewModelScope.launch {
            repository.selectStartingLineup(gameId, playerIds, requiredSize)
            _messages.emit("Starting lineup saved.")
        }
    }

    fun substitute(playerInId: Long?, playerOutId: Long?) {
        val gameId = selectedGame.value?.id
        if (gameId == null) {
            _messages.tryEmit("Create or select a game first.")
            return
        }
        if ((playerInId == null) || (playerOutId == null)) {
            _messages.tryEmit("Pick one player in and one player out.")
            return
        }
        if (playerInId == playerOutId) {
            _messages.tryEmit("Player in and player out must be different.")
            return
        }

        viewModelScope.launch {
            repository.substitute(gameId, playerInId, playerOutId)
            _messages.emit("Substitution recorded.")
        }
    }

    fun addScore(playerId: Long?, points: Int) {
        val gameId = selectedGame.value?.id
        if (gameId == null) {
            _messages.tryEmit("Create or select a game first.")
            return
        }

        viewModelScope.launch {
            repository.addScore(gameId, playerId, points)
        }
    }

    fun addFoul(playerId: Long, adjustment: Int = 1) {
        val gameId = selectedGame.value?.id
        if (gameId == null) {
            _messages.tryEmit("Create or select a game first.")
            return
        }

        viewModelScope.launch {
            repository.addFoul(gameId, playerId, adjustment)
        }
    }

    fun toggleTimer() {
        val gameId = selectedGame.value?.id ?: return
        viewModelScope.launch {
            repository.toggleTimer(gameId)
        }
    }

    fun resetTimer() {
        val gameId = selectedGame.value?.id ?: return
        viewModelScope.launch {
            repository.resetTimer(gameId)
            _messages.emit("Timer reset to 0:00.")
        }
    }

    fun addMvpVote(playerId: Long) {
        val gameId = selectedGame.value?.id ?: return
        viewModelScope.launch {
            repository.addMvpVote(gameId, playerId)
            _messages.emit("MVP vote recorded.")
        }
    }

    fun setGameTime(timeMillis: Long) {
        val gameId = selectedGame.value?.id ?: return
        viewModelScope.launch {
            repository.setGameTime(gameId, timeMillis)
        }
    }

    fun exportPlayersCsv(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                repository.exportPlayersCsv(getApplication<Application>().contentResolver, uri, sport)
            }.onSuccess {
                _messages.emit("Roster CSV exported.")
            }.onFailure {
                _messages.emit(it.message ?: "Export failed.")
            }
        }
    }

    fun importPlayersCsv(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                repository.importPlayersCsv(getApplication<Application>().contentResolver, uri, sport)
            }.onSuccess {
                _messages.emit("Roster CSV imported.")
            }.onFailure {
                _messages.emit(it.message ?: "Import failed.")
            }
        }
    }

    fun exportGameNotesTxt(gameId: Long, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                repository.exportGameNotesTxt(getApplication<Application>().contentResolver, gameId, uri)
            }.onSuccess {
                _messages.emit("Coaching notes exported.")
            }.onFailure {
                _messages.emit(it.message ?: "Export failed.")
            }
        }
    }

    fun exportGameCsv(gameId: Long, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                repository.exportGameCsv(getApplication<Application>().contentResolver, gameId, uri)
            }.onSuccess {
                _messages.emit("Game CSV exported.")
            }.onFailure {
                _messages.emit(it.message ?: "Export failed.")
            }
        }
    }
}

class AppViewModelFactory(
    private val application: Application,
    private val sport: Sport,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return AppViewModel(application, sport) as T
    }
}

/**
 * Replays [events] in chronological order to derive each roster player's current on-court/bench
 * status, shift and bench durations, and per-game totals as of [game]'s current running time.
 *
 * A pure function (no Android/coroutine dependencies) so it can be unit tested directly.
 */
fun calculatePlayerGameStats(
    playerList: List<PlayerEntity>,
    game: GameEntity?,
    events: List<GameEventEntity>,
): List<PlayerGameStat> {
    val onCourt = game?.onCourtIds()?.toSet().orEmpty()
    val absent = game?.absentPlayerIds()?.toSet().orEmpty()

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
                        lastEnteredAt[id] = event.gameTimeMillis
                        lastBenchedAt.remove(id)
                    }
            }

            EventType.SUBSTITUTION -> {
                event.playerId?.let { playerInId ->
                    lastEnteredAt[playerInId] = event.gameTimeMillis
                    lastBenchedAt.remove(playerInId)
                }
                event.relatedPlayerId?.let { playerOutId ->
                    timesSubbedOffByPlayer[playerOutId] = (timesSubbedOffByPlayer[playerOutId] ?: 0) + 1
                    val entered = lastEnteredAt.remove(playerOutId)
                    if (entered != null) {
                        val duration = maxOf(0L, event.gameTimeMillis - entered)
                        totalTimeByPlayer[playerOutId] = (totalTimeByPlayer[playerOutId] ?: 0L) + duration
                    }
                    lastBenchedAt[playerOutId] = event.gameTimeMillis
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
                    val duration = maxOf(0L, event.gameTimeMillis - entered)
                    totalTimeByPlayer[id] = (totalTimeByPlayer[id] ?: 0L) + duration
                }
                lastEnteredAt.clear()
            }

            EventType.GAME_END -> {
                lastEnteredAt.forEach { (id, entered) ->
                    val duration = maxOf(0L, event.gameTimeMillis - entered)
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
                ?: (game?.runningTimeMillis ?: 0L)

            val currentShift = if (isOnCourt) {
                maxOf(0L, (game?.runningTimeMillis ?: 0L) - enteredAt)
            } else {
                0L
            }

            val benchedAt = lastBenchedAt[player.id] ?: 0L
            val currentBench = if (isOnCourt) {
                0L
            } else {
                maxOf(0L, (game?.runningTimeMillis ?: 0L) - benchedAt)
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
            )
        }
        .toList()
}