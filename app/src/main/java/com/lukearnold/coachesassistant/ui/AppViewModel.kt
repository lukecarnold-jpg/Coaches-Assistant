package com.lukearnold.coachesassistant.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import com.lukearnold.coachesassistant.R
import com.lukearnold.coachesassistant.data.AppDatabase
import com.lukearnold.coachesassistant.data.BackupIo
import com.lukearnold.coachesassistant.data.GameRepository
import com.lukearnold.coachesassistant.data.EventType
import com.lukearnold.coachesassistant.data.GameCsvIo
import com.lukearnold.coachesassistant.data.GameEntity
import com.lukearnold.coachesassistant.data.GameEventEntity
import com.lukearnold.coachesassistant.data.PlayerEntity
import com.lukearnold.coachesassistant.data.displayLabel
import com.lukearnold.coachesassistant.data.Sport
import com.lukearnold.coachesassistant.data.GameLineup
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

class AppViewModel(application: Application, private val sport: Sport) : AndroidViewModel(application) {
    private val repository = GameRepository(AppDatabase.getInstance(application))
    private val csvIo = GameCsvIo(AppDatabase.getInstance(application))
    private val backupIo = BackupIo(AppDatabase.getInstance(application))
    private val gameDayMode = GameDayMode.get(application)

    /** The game Game Day mode is silencing notifications for (any sport's), or null if it's off. */
    val gameDayGameId: StateFlow<Long?> = gameDayMode.activeGameId

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
        // Game Day mode lasts until its game ends. End Game turns it off directly; this also covers
        // the game being deleted, cleared, or replaced by a restore, in any sport.
        viewModelScope.launch {
            repository.games.collect { allGames ->
                gameDayMode.stopUnlessPlaying(allGames.filter { !it.isFinished }.map { it.id })
            }
        }

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
                _messages.emit(UiMessage(uiText(R.string.msg_period_ended)))
            }
        }
    }

    fun selectGame(gameId: Long) {
        selectedGameId.value = gameId
    }

    fun addPlayer(name: String, jerseyNumberText: String, position: String? = null) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            _messages.tryEmit(UiMessage(uiText(R.string.msg_enter_player_name)))
            return
        }

        val jerseyNumber = jerseyNumberText.trim().toIntOrNull()
        // Jersey number is now optional, only error if they entered something that isn't a number
        if ((jerseyNumberText.trim().isNotEmpty()) && (jerseyNumber == null)) {
            _messages.tryEmit(UiMessage(uiText(R.string.msg_jersey_not_numeric)))
            return
        }

        val clash = rosterClash(players.value, trimmedName, jerseyNumber)
        if (clash is RosterClash.SamePlayer) {
            _messages.tryEmit(UiMessage(uiText(R.string.msg_player_already_on_roster, clash.player.displayLabel())))
            return
        }

        viewModelScope.launch {
            repository.addPlayer(sport, trimmedName, jerseyNumber, position)
            _messages.emit(UiMessage(savedPlayerMessage(R.string.msg_player_added, clash)))
        }
    }

    fun updatePlayer(player: PlayerEntity, newName: String, newJerseyNumberText: String, newPosition: String? = player.position) {
        val trimmedName = newName.trim()
        if (trimmedName.isBlank()) {
            _messages.tryEmit(UiMessage(uiText(R.string.msg_name_empty)))
            return
        }

        val jerseyNumber = newJerseyNumberText.trim().toIntOrNull()
        if ((newJerseyNumberText.trim().isNotEmpty()) && (jerseyNumber == null)) {
            _messages.tryEmit(UiMessage(uiText(R.string.msg_jersey_not_numeric)))
            return
        }

        val clash = rosterClash(players.value, trimmedName, jerseyNumber, exceptId = player.id)
        if (clash is RosterClash.SamePlayer) {
            _messages.tryEmit(UiMessage(uiText(R.string.msg_player_already_on_roster, clash.player.displayLabel())))
            return
        }

        viewModelScope.launch {
            repository.updatePlayer(player.copy(name = trimmedName, jerseyNumber = jerseyNumber, position = newPosition))
            _messages.emit(UiMessage(savedPlayerMessage(R.string.msg_player_updated, clash)))
        }
    }

    /** [saved] ("Player added."), plus who else wears the number if it's shared. */
    private fun savedPlayerMessage(@StringRes saved: Int, clash: RosterClash?): UiText =
        if (clash is RosterClash.SharedNumber) {
            uiText(R.string.msg_number_shared, uiText(saved), clash.player.name, clash.player.jerseyNumber ?: 0)
        } else {
            uiText(saved)
        }

    fun deletePlayer(playerId: Long) {
        viewModelScope.launch {
            repository.deletePlayer(playerId)
            _messages.emit(UiMessage(uiText(R.string.msg_player_removed)))
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAllData()
            _messages.emit(UiMessage(uiText(R.string.msg_all_data_cleared)))
        }
    }

    fun clearSelectedData(players: Boolean, games: Boolean, events: Boolean) {
        viewModelScope.launch {
            repository.clearSelectedData(sport, players, games, events)
            _messages.emit(UiMessage(uiText(R.string.msg_selected_data_cleared)))
        }
    }

    fun createGame(title: String, opponent: String, periodCount: Int, periodMins: Int) {
        // Stored with the game, so it's in whatever language the app was showing when it was created.
        val finalTitle = title.trim().ifBlank {
            getApplication<Application>().getString(R.string.default_game_title, games.value.size + 1)
        }
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
            _messages.emit(UiMessage(uiText(R.string.msg_game_created)))
        }
    }

    fun advancePeriod() {
        val gameId = selectedGame.value?.id ?: return
        viewModelScope.launch {
            repository.advancePeriod(gameId)
            _messages.emit(UiMessage(uiText(R.string.msg_advanced_period)))
        }
    }

    fun endGame() {
        val gameId = selectedGame.value?.id ?: return
        viewModelScope.launch {
            repository.endGame(gameId)
            val wasGameDay = gameDayMode.activeGameId.value == gameId
            if (wasGameDay) gameDayMode.stop()
            _messages.emit(UiMessage(uiText(if (wasGameDay) R.string.msg_game_finalized_game_day_off else R.string.msg_game_finalized)))
        }
    }

    /** Silences notifications until the selected game ends (see GameDayMode). */
    fun startGameDay() {
        val game = selectedGame.value ?: return
        if (game.isFinished) return
        val started = gameDayMode.start(game.id)
        _messages.tryEmit(UiMessage(uiText(if (started) R.string.msg_game_day_on else R.string.msg_game_day_no_access)))
    }

    fun stopGameDay() {
        gameDayMode.stop()
        _messages.tryEmit(UiMessage(uiText(R.string.msg_game_day_off)))
    }

    fun updateAbsentPlayers(playerIds: List<Long>) {
        val gameId = selectedGame.value?.id ?: return
        viewModelScope.launch {
            repository.updateAbsentPlayers(gameId, playerIds)
            _messages.emit(UiMessage(uiText(R.string.msg_attendance_updated)))
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
            _messages.emit(UiMessage(uiText(R.string.msg_event_updated)))
        }
    }

    /**
     * Removes [event] from the log the same way Undo would (see GameRepository.undoEvents): a
     * substitution swaps its players back, and it's refused if they've moved since.
     */
    fun deleteEvent(event: GameEventEntity) {
        viewModelScope.launch {
            val removed = repository.undoEvents(listOf(event.id))
            _messages.emit(UiMessage(uiText(if (removed) R.string.msg_event_removed else R.string.msg_event_cant_remove)))
        }
    }

    fun deleteGame(gameId: Long) {
        viewModelScope.launch {
            repository.deleteGame(gameId)
            _messages.emit(UiMessage(uiText(R.string.msg_game_deleted)))
        }
    }

    fun setStartingLineup(playerIds: List<Long>) {
        val gameId = selectedGame.value?.id
        if (gameId == null) {
            _messages.tryEmit(UiMessage(uiText(R.string.msg_select_game_first)))
            return
        }
        val requiredSize = sport.config.onCourtSize
        if (playerIds.distinct().size != requiredSize) {
            _messages.tryEmit(UiMessage(pluralText(R.plurals.msg_lineup_needs_players, requiredSize)))
            return
        }

        viewModelScope.launch {
            repository.selectStartingLineup(gameId, playerIds, requiredSize)
            _messages.emit(UiMessage(uiText(R.string.msg_lineup_saved)))
        }
    }

    /** Makes each (player in, player out) swap in [pairs], offering one Undo for the whole batch. */
    fun substitute(pairs: List<Pair<Long, Long>>) {
        val gameId = selectedGame.value?.id
        if (gameId == null) {
            _messages.tryEmit(UiMessage(uiText(R.string.msg_select_game_first)))
            return
        }
        if (pairs.isEmpty()) {
            _messages.tryEmit(UiMessage(uiText(R.string.msg_pick_in_and_out)))
            return
        }
        if (pairs.any { (playerIn, playerOut) -> playerIn == playerOut }) {
            _messages.tryEmit(UiMessage(uiText(R.string.msg_in_and_out_same)))
            return
        }

        viewModelScope.launch {
            val eventIds = pairs.mapNotNull { (playerIn, playerOut) -> repository.substitute(gameId, playerIn, playerOut) }
            when {
                eventIds.isEmpty() -> _messages.emit(UiMessage(uiText(R.string.msg_substitution_stale)))
                eventIds.size < pairs.size -> announceUndoable(
                    eventIds,
                    text = pluralText(R.plurals.msg_some_substitutions_recorded, pairs.size, eventIds.size, pairs.size),
                )
                eventIds.size == 1 -> announceUndoable(eventIds)
                else -> announceUndoable(eventIds, text = pluralText(R.plurals.msg_substitutions_recorded, eventIds.size))
            }
        }
    }

    /** Scores for a player on our team. */
    fun addPlayerScore(playerId: Long, points: Int) {
        val gameId = selectedGame.value?.id
        if (gameId == null) {
            _messages.tryEmit(UiMessage(uiText(R.string.msg_select_game_first)))
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
            _messages.tryEmit(UiMessage(uiText(R.string.msg_select_game_first)))
            return
        }

        viewModelScope.launch {
            repository.addTeamScore(gameId, playerId = null, points = points)?.let { announceUndoable(listOf(it)) }
        }
    }

    fun addOpponentScore(points: Int) {
        val gameId = selectedGame.value?.id
        if (gameId == null) {
            _messages.tryEmit(UiMessage(uiText(R.string.msg_select_game_first)))
            return
        }

        viewModelScope.launch {
            repository.addOpponentScore(gameId, points)?.let { announceUndoable(listOf(it)) }
        }
    }

    fun addFoul(playerId: Long, adjustment: Int = 1) {
        val gameId = selectedGame.value?.id
        if (gameId == null) {
            _messages.tryEmit(UiMessage(uiText(R.string.msg_select_game_first)))
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
    private suspend fun announceUndoable(eventIds: List<Long>, text: UiText? = null) {
        val message = text ?: repository.getEvent(eventIds.single())?.let { event ->
            val description = eventDescription(
                event,
                players.value.associateBy { it.id },
                opponentName = selectedGame.value?.opponent.orEmpty(),
                infraction = sport.config.infraction,
            )
            if (event.type == EventType.FOUL.name && event.points < 0) uiText(R.string.msg_event_removed_named, description) else description
        } ?: return
        _messages.emit(
            UiMessage(message) {
                viewModelScope.launch {
                    val undone = repository.undoEvents(eventIds)
                    _messages.emit(UiMessage(uiText(if (undone) R.string.msg_undone else R.string.msg_undo_stale)))
                }
            },
        )
    }

    fun toggleTimer() {
        val game = selectedGame.value ?: return
        val gameId = game.id
        if (game.timerLastStartedAt == null && game.remainingPeriodTimeMillis() == 0L) {
            _messages.tryEmit(UiMessage(uiText(R.string.msg_period_time_up)))
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
                        uiText(R.string.msg_clock_reset, formatDuration(game.periodDurationMillis))
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
                _messages.emit(UiMessage(uiText(R.string.msg_backup_saved, playerCount(counts.players), gameCount(counts.games))))
            }.onFailure {
                _messages.emit(UiMessage(it.toUiText(fallback = R.string.msg_backup_failed)))
            }
        }
    }

    /** Replaces all data, for every sport, with the backup at [uri]; nothing changes if it can't be read. */
    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                backupIo.restore(getApplication<Application>().contentResolver, uri)
            }.onSuccess { counts ->
                _messages.emit(UiMessage(uiText(R.string.msg_restored, playerCount(counts.players), gameCount(counts.games))))
            }.onFailure {
                _messages.emit(UiMessage(it.toUiText(fallback = R.string.msg_restore_failed)))
            }
        }
    }

    fun exportPlayersCsv(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                csvIo.exportPlayersCsv(getApplication<Application>().contentResolver, uri, sport)
            }.onSuccess {
                _messages.emit(UiMessage(uiText(R.string.msg_roster_exported)))
            }.onFailure {
                _messages.emit(UiMessage(it.toUiText(fallback = R.string.msg_export_failed)))
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
                _messages.emit(UiMessage(it.toUiText(fallback = R.string.msg_import_failed)))
            }
        }
    }

    fun exportGameNotesTxt(gameId: Long, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                csvIo.exportGameNotesTxt(getApplication<Application>().contentResolver, getApplication<Application>().resources, gameId, uri)
            }.onSuccess {
                _messages.emit(UiMessage(uiText(R.string.msg_notes_exported)))
            }.onFailure {
                _messages.emit(UiMessage(it.toUiText(fallback = R.string.msg_export_failed)))
            }
        }
    }

    fun exportGameCsv(gameId: Long, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                csvIo.exportGameCsv(getApplication<Application>().contentResolver, gameId, uri)
            }.onSuccess {
                _messages.emit(UiMessage(uiText(R.string.msg_game_csv_exported)))
            }.onFailure {
                _messages.emit(UiMessage(it.toUiText(fallback = R.string.msg_export_failed)))
            }
        }
    }
}

private fun playerCount(count: Int) = pluralText(R.plurals.player_count, count)

private fun gameCount(count: Int) = pluralText(R.plurals.game_count, count)

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
