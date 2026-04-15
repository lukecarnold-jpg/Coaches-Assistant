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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlayerGameStat(
    val player: PlayerEntity,
    val points: Int,
    val fouls: Int,
    val onCourt: Boolean
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

            events.forEach { event ->
                when (EventType.fromRaw(event.type)) {
                    EventType.SCORE -> event.playerId?.let { playerId ->
                        pointsByPlayer[playerId] = (pointsByPlayer[playerId] ?: 0) + event.points
                    }

                    EventType.FOUL -> event.playerId?.let { playerId ->
                        foulsByPlayer[playerId] = (foulsByPlayer[playerId] ?: 0) + 1
                    }

                    else -> Unit
                }
            }

            playerList.map { player ->
                PlayerGameStat(
                    player = player,
                    points = pointsByPlayer[player.id] ?: 0,
                    fouls = foulsByPlayer[player.id] ?: 0,
                    onCourt = onCourt.contains(player.id)
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
                    currentSelection == null -> selectedGameId.value = gameList.first().id
                    gameList.none { it.id == currentSelection } -> {
                        selectedGameId.value = gameList.first().id
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

    fun addFoul(playerId: Long) {
        val gameId = selectedGame.value?.id
        if (gameId == null) {
            _messages.tryEmit("Create or select a game first.")
            return
        }

        viewModelScope.launch {
            repository.addFoul(gameId, playerId)
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