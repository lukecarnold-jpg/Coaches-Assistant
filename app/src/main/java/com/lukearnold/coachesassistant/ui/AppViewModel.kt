package com.lukearnold.coachesassistant.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.annotation.StringRes
import com.lukearnold.coachesassistant.R
import com.lukearnold.coachesassistant.data.AppDatabase
import com.lukearnold.coachesassistant.data.GameRepository
import com.lukearnold.coachesassistant.data.EventType
import com.lukearnold.coachesassistant.data.GameEntity
import com.lukearnold.coachesassistant.data.GameEventEntity
import com.lukearnold.coachesassistant.data.PlayerEntity
import com.lukearnold.coachesassistant.data.displayLabel
import com.lukearnold.coachesassistant.data.Sport
import com.lukearnold.coachesassistant.data.GameLineup
import com.lukearnold.coachesassistant.data.remainingPeriodTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The roster and live game for one [sport]. Backup, CSV files, and clearing data are in
 * DataViewModel; app-wide settings in SettingsViewModel.
 */
class AppViewModel(
    application: Application,
    private val sport: Sport,
    private val repository: GameRepository,
    private val gameDayMode: GameDayMode,
) : AndroidViewModel(application) {

    private val _minimumOnCourt = MutableStateFlow(FieldingPreferences.minimumOnCourt(application, sport))

    /** The fewest players this sport's team takes the field with (see SportConfig.minimumOnCourt). */
    val minimumOnCourt: StateFlow<Int> = _minimumOnCourt

    fun setMinimumOnCourt(minimum: Int) {
        val value = minimum.coerceIn(1, sport.config.onCourtSize)
        FieldingPreferences.setMinimumOnCourt(getApplication(), sport, value)
        _minimumOnCourt.value = value
    }

    /** The game Game Day mode is silencing notifications for (any sport's), or null if it's off. */
    val gameDayGameId: StateFlow<Long?> = gameDayMode.activeGameId

    private val selectedGameId = MutableStateFlow<Long?>(null)
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    // Null until the database first answers, so "not loaded yet" isn't mistaken for "none".
    private val loadedPlayers: StateFlow<List<PlayerEntity>?> =
        repository.playersForSport(sport).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val loadedGames: StateFlow<List<GameEntity>?> =
        repository.gamesForSport(sport).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val players: StateFlow<List<PlayerEntity>> = loadedPlayers.map { it.orEmpty() }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val games: StateFlow<List<GameEntity>> = loadedGames.map { it.orEmpty() }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    /**
     * Whether this sport's players and games have loaded, so screens can wait rather than briefly
     * showing "No players yet" (or "no games") while the database is still being read.
     */
    val isLoaded: StateFlow<Boolean> = combine(loadedPlayers, loadedGames) { players, games -> players != null && games != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

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

    /** The suggestion the coach put off with "Later" (see SubReminders.snooze). */
    val subSnooze: StateFlow<SubSnooze?> = SubReminders.snooze

    fun snoozeSubSuggestion(key: Pair<Long, Long>, gameTimeMillis: Long) {
        SubReminders.snooze(getApplication(), key, gameTimeMillis)
    }

    /** "Sub now" on a suggestion: the swap, with Undo like any substitution. */
    fun acceptSubSuggestion(playerOn: Long, playerOff: Long) {
        SubReminders.cancel(getApplication())
        substitute(listOf(playerOn), listOf(playerOff))
    }

    init {
        // Game Day mode lasts until its game ends. End Game turns it off directly; this also covers
        // the game being deleted, cleared, or replaced by a restore, in any sport.
        viewModelScope.launch {
            repository.games.collect { allGames ->
                gameDayMode.stopUnlessPlaying(allGames.filter { !it.isFinished }.map { it.id })
                // Every sport's running clocks, so the notification follows whichever is running.
                GameClockNotification.sync(getApplication(), allGames)
                // Substitution reminders, which keep running with the app in the background.
                SubReminderService.startIfNeeded(getApplication(), allGames)
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

    private fun launchSafely(block: suspend CoroutineScope.() -> Unit): Job = launchSafely(_messages, block = block)

    /** Whether this sport has a game that hasn't ended, read from the database (not the UI state). */
    suspend fun hasGameInProgress(): Boolean = repository.gamesForSport(sport).first().any { !it.isFinished }

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

        launchSafely {
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

        launchSafely {
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
        launchSafely {
            repository.deletePlayer(playerId)
            _messages.emit(UiMessage(uiText(R.string.msg_player_removed)))
        }
    }

    fun createGame(title: String, opponent: String, periodCount: Int, periodMins: Int) {
        // Stored with the game, so it's in whatever language the app was showing when it was created.
        val finalTitle = title.trim().ifBlank {
            getApplication<Application>().getString(R.string.default_game_title, games.value.size + 1)
        }
        val durationMillis = periodMins * 60 * 1000L

        launchSafely {
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
        launchSafely {
            repository.advancePeriod(gameId)
            _messages.emit(UiMessage(uiText(R.string.msg_advanced_period)))
        }
    }

    fun endGame() {
        val gameId = selectedGame.value?.id ?: return
        launchSafely {
            repository.endGame(gameId)
            val wasGameDay = gameDayMode.activeGameId.value == gameId
            val message = when {
                !wasGameDay -> R.string.msg_game_finalized
                gameDayMode.stop() -> R.string.msg_game_finalized_game_day_off
                else -> R.string.msg_game_day_stop_failed
            }
            _messages.emit(UiMessage(uiText(message)))
        }
    }

    /** Silences notifications until the selected game ends (see GameDayMode). */
    fun startGameDay() {
        val game = selectedGame.value ?: return
        if (game.isFinished) return
        val message = when (gameDayMode.start(game.id)) {
            GameDayMode.StartResult.STARTED -> R.string.msg_game_day_on
            GameDayMode.StartResult.NO_ACCESS -> R.string.msg_game_day_no_access
            GameDayMode.StartResult.FAILED -> R.string.msg_game_day_failed
        }
        _messages.tryEmit(UiMessage(uiText(message)))
    }

    fun stopGameDay() {
        val stopped = gameDayMode.stop()
        _messages.tryEmit(UiMessage(uiText(if (stopped) R.string.msg_game_day_off else R.string.msg_game_day_stop_failed)))
    }

    fun updateAbsentPlayers(playerIds: List<Long>) {
        val gameId = selectedGame.value?.id ?: return
        launchSafely {
            repository.updateAbsentPlayers(gameId, playerIds)
            _messages.emit(UiMessage(uiText(R.string.msg_attendance_updated)))
        }
    }

    fun updateGameNotes(notes: String) {
        val gameId = selectedGame.value?.id ?: return
        launchSafely {
            repository.updateGameNotes(gameId, notes)
        }
    }

    fun updateEvent(event: GameEventEntity) {
        launchSafely {
            repository.updateEvent(event)
            _messages.emit(UiMessage(uiText(R.string.msg_event_updated)))
        }
    }

    /**
     * Removes [event] from the log the same way Undo would (see GameRepository.undoEvents): a
     * substitution swaps its players back, and it's refused if they've moved since.
     */
    fun deleteEvent(event: GameEventEntity) {
        launchSafely {
            val removed = repository.undoEvents(listOf(event.id))
            _messages.emit(UiMessage(uiText(if (removed) R.string.msg_event_removed else R.string.msg_event_cant_remove)))
        }
    }

    fun deleteGame(gameId: Long) {
        launchSafely {
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
        val absent = selectedGameLineup.value.absentIds
        val available = players.value.count { it.id !in absent }
        val chosen = playerIds.distinct().filter { it !in absent }
        val rule = lineupRule(available, requiredSize, _minimumOnCourt.value)
        if (!rule.allows(chosen.size)) {
            val message = when (rule) {
                is LineupRule.Full -> pluralText(R.plurals.msg_lineup_needs_players, requiredSize)
                is LineupRule.ShortHanded -> pluralText(R.plurals.msg_lineup_short_handed_range, rule.available, rule.minimum, rule.available)
                is LineupRule.TooFew -> uiText(R.string.msg_lineup_too_few)
            }
            _messages.tryEmit(UiMessage(message))
            return
        }

        launchSafely {
            repository.selectStartingLineup(gameId, chosen, requiredSize)
            _messages.emit(UiMessage(uiText(R.string.msg_lineup_saved)))
        }
    }

    /**
     * Brings [playersIn] on and takes [playersOut] off, offering one Undo for the whole batch. They
     * pair up in order as swaps; any extra player coming on joins without a swap, and any extra
     * going off leaves without a replacement (see GameRepository.substitute).
     */
    fun substitute(playersIn: List<Long>, playersOut: List<Long>) {
        val gameId = selectedGame.value?.id
        if (gameId == null) {
            _messages.tryEmit(UiMessage(uiText(R.string.msg_select_game_first)))
            return
        }
        if (playersIn.isEmpty() && playersOut.isEmpty()) {
            _messages.tryEmit(UiMessage(uiText(R.string.msg_pick_in_and_out)))
            return
        }
        if (playersIn.any { it in playersOut }) {
            _messages.tryEmit(UiMessage(uiText(R.string.msg_in_and_out_same)))
            return
        }
        val swaps = playersIn.zip(playersOut).map { (playerIn, playerOut) -> playerIn to playerOut }
        // Swaps and departures first, so joins find room on the court.
        val changes: List<Pair<Long?, Long?>> = swaps +
            playersOut.drop(swaps.size).map { null to it } +
            playersIn.drop(swaps.size).map { it to null }

        launchSafely {
            val pairs = changes
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

        launchSafely {
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

        launchSafely {
            repository.addTeamScore(gameId, playerId = null, points = points)?.let { announceUndoable(listOf(it)) }
        }
    }

    fun addOpponentScore(points: Int) {
        val gameId = selectedGame.value?.id
        if (gameId == null) {
            _messages.tryEmit(UiMessage(uiText(R.string.msg_select_game_first)))
            return
        }

        launchSafely {
            repository.addOpponentScore(gameId, points)?.let { announceUndoable(listOf(it)) }
        }
    }

    fun addFoul(playerId: Long, adjustment: Int = 1) {
        val gameId = selectedGame.value?.id
        if (gameId == null) {
            _messages.tryEmit(UiMessage(uiText(R.string.msg_select_game_first)))
            return
        }

        launchSafely {
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
                launchSafely {
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
        launchSafely {
            repository.toggleTimer(gameId)
        }
    }

    fun resetTimer() {
        val game = selectedGame.value ?: return
        launchSafely {
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
        launchSafely {
            repository.addMvpVote(gameId, playerId)?.let { announceUndoable(listOf(it)) }
        }
    }

    fun setGameTime(timeMillis: Long) {
        val game = selectedGame.value ?: return
        launchSafely {
            repository.setGameTime(game.id, timeMillis)?.let { earliest ->
                _messages.emit(UiMessage(clockTooEarlyMessage(game.periodDurationMillis, earliest)))
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

/** Builds the [AppViewModel] for [sport] with the app's database. */
fun appViewModelFactory(sport: Sport): ViewModelProvider.Factory = viewModelFactory {
    initializer {
        val application = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
        AppViewModel(application, sport, GameRepository(AppDatabase.getInstance(application)), GameDayMode.get(application))
    }
}
