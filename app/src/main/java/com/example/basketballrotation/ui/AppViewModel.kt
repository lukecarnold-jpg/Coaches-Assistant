package com.example.basketballrotation.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.basketballrotation.data.AppDatabase
import com.example.basketballrotation.data.BasketballRepository
import com.example.basketballrotation.data.EventType
import com.example.basketballrotation.data.GameEntity
import com.example.basketballrotation.data.PlayerEntity
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
    val onCourt: Boolean,
    val currentShiftTimeMillis: Long
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BasketballRepository(AppDatabase.getInstance(application))

    private val selectedGameId = MutableStateFlow<Long?>(null)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    val players = repository.players.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val games = repository.games.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
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
            null
        )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedGameEvents = selectedGame.flatMapLatest { game ->
        if (game == null) flowOf(emptyList()) else repository.observeEvents(game.id)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val selectedGameStats =
        combine(players, selectedGame, selectedGameEvents) { playerList, game, events ->
            val onCourt = game?.onCourtIds()?.toSet().orEmpty()
            val pointsByPlayer = mutableMapOf<Long, Int>()
            val foulsByPlayer = mutableMapOf<Long, Int>()
            val lastEnteredAt = mutableMapOf<Long, Long>()

            // Sort events by time to track state chronologically
            val sortedEvents = events.sortedBy { it.gameTimeMillis }

            sortedEvents.forEach { event ->
                when (EventType.fromRaw(event.type)) {
                    EventType.START_LINEUP -> {
                        // All starting players entered at the game time of the event (usually 0)
                        game?.onCourtIds()?.forEach { id ->
                            lastEnteredAt[id] = event.gameTimeMillis
                        }
                    }

                    EventType.SUBSTITUTION -> {
                        event.playerId?.let { playerInId ->
                            lastEnteredAt[playerInId] = event.gameTimeMillis
                        }
                        event.relatedPlayerId?.let { playerOutId ->
                            lastEnteredAt.remove(playerOutId)
                        }
                    }

                    EventType.SCORE -> event.playerId?.let { playerId ->
                        pointsByPlayer[playerId] = (pointsByPlayer[playerId] ?: 0) + event.points
                    }

                    EventType.FOUL -> event.playerId?.let { playerId ->
                        foulsByPlayer[playerId] = (foulsByPlayer[playerId] ?: 0) + event.points
                    }
                }
            }

            // Fallback for players currently on court if no event found (shouldn't happen with proper event logging)
            onCourt.forEach { id ->
                if (!lastEnteredAt.containsKey(id)) {
                    lastEnteredAt[id] = 0L
                }
            }

            playerList.map { player ->
                val isOnCourt = onCourt.contains(player.id)
                val enteredAt = lastEnteredAt[player.id] ?: 0L
                
                // We'll use game.runningTimeMillis as a baseline if timer is stopped, 
                // but the UI will need the "live" game time for the active ticker.
                // For now, let's store the raw 'enteredAt' or calculate duration based on recorded game time.
                val shiftTime = if (isOnCourt) {
                    (game?.runningTimeMillis ?: 0L) - enteredAt
                } else {
                    0L
                }

                PlayerGameStat(
                    player = player,
                    points = pointsByPlayer[player.id] ?: 0,
                    fouls = foulsByPlayer[player.id] ?: 0,
                    onCourt = isOnCourt,
                    currentShiftTimeMillis = maxOf(0L, shiftTime)
                )
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            emptyList()
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

    fun addPlayer(name: String, jerseyNumberText: String) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            _messages.tryEmit("Enter a player name.")
            return
        }

        val jerseyNumber = jerseyNumberText.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
        if (jerseyNumberText.isNotBlank() && jerseyNumber == null) {
            _messages.tryEmit("Jersey number must be numeric.")
            return
        }

        viewModelScope.launch {
            repository.addPlayer(trimmedName, jerseyNumber)
            _messages.emit("Player added.")
        }
    }

    fun updatePlayer(player: PlayerEntity, newName: String, newJerseyNumberText: String) {
        val trimmedName = newName.trim()
        if (trimmedName.isBlank()) {
            _messages.tryEmit("Name cannot be empty.")
            return
        }

        val jerseyNumber = newJerseyNumberText.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
        if (newJerseyNumberText.isNotBlank() && jerseyNumber == null) {
            _messages.tryEmit("Jersey number must be numeric.")
            return
        }

        viewModelScope.launch {
            repository.updatePlayer(player.copy(name = trimmedName, jerseyNumber = jerseyNumber))
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

    fun createGame(title: String, opponent: String) {
        val finalTitle = title.trim().ifBlank { "Game ${games.value.size + 1}" }

        viewModelScope.launch {
            val gameId = repository.createGame(finalTitle, opponent)
            selectedGameId.value = gameId
            _messages.emit("Game created.")
        }
    }

    fun setStartingLineup(playerIds: List<Long>) {
        val gameId = selectedGame.value?.id
        if (gameId == null) {
            _messages.tryEmit("Create or select a game first.")
            return
        }
        if (playerIds.distinct().size != 5) {
            _messages.tryEmit("Select exactly 5 players for the starting lineup.")
            return
        }

        viewModelScope.launch {
            repository.selectStartingLineup(gameId, playerIds)
            _messages.emit("Starting lineup saved.")
        }
    }

    fun substitute(playerInId: Long?, playerOutId: Long?) {
        val gameId = selectedGame.value?.id
        if (gameId == null) {
            _messages.tryEmit("Create or select a game first.")
            return
        }
        if (playerInId == null || playerOutId == null) {
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

    fun addScore(playerId: Long, points: Int) {
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

    fun setGameTime(timeMillis: Long) {
        val gameId = selectedGame.value?.id ?: return
        viewModelScope.launch {
            repository.setGameTime(gameId, timeMillis)
        }
    }

    fun exportPlayersCsv(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                repository.exportPlayersCsv(getApplication<Application>().contentResolver, uri)
            }.onSuccess {
                _messages.emit("Roster CSV exported.")
            }.onFailure {
                _messages.emit(it.message ?: "Export failed.")
            }
        }
    }

    fun exportSelectedGameCsv(uri: Uri) {
        val gameId = selectedGame.value?.id
        if (gameId == null) {
            _messages.tryEmit("Select a game first.")
            return
        }

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