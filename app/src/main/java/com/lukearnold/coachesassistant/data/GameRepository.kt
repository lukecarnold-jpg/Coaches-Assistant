package com.lukearnold.coachesassistant.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Live-game actions (period changes, lineups, substitutions, scores, infractions, and the clock)
 * do nothing to a finished game. Notes, attendance, MVP votes, and corrections through the event
 * log still apply after the game.
 */
class GameRepository(
    private val database: AppDatabase,
) {
    private val playerDao = database.playerDao()
    private val gameDao = database.gameDao()
    private val gameEventDao = database.gameEventDao()
    private val gamePlayerDao = database.gamePlayerDao()

    val players: Flow<List<PlayerEntity>> = playerDao.observeAll()
    val games: Flow<List<GameEntity>> = gameDao.observeAll()

    fun playersForSport(sport: Sport): Flow<List<PlayerEntity>> = playerDao.observeAllBySport(sport.name)
    fun gamesForSport(sport: Sport): Flow<List<GameEntity>> = gameDao.observeAllBySport(sport.name)

    fun observeEvents(gameId: Long): Flow<List<GameEventEntity>> =
        gameEventDao.observeByGame(gameId)

    fun observeLineup(gameId: Long): Flow<GameLineup> =
        gamePlayerDao.observeByGame(gameId).map(GameLineup::from)

    suspend fun getLineup(gameId: Long): GameLineup = GameLineup.from(gamePlayerDao.getByGame(gameId))

    /** Every event from [sport]'s finished games, for season totals. */
    fun observeFinishedGameEvents(sport: Sport): Flow<List<GameEventEntity>> =
        gameEventDao.observeForFinishedGames(sport.name)

    /** Each of [sport]'s finished games' lineup (for attendance), keyed by game id. */
    fun observeFinishedGameLineups(sport: Sport): Flow<Map<Long, GameLineup>> =
        gamePlayerDao.observeForFinishedGames(sport.name).map { rows ->
            rows.groupBy { it.gameId }.mapValues { (_, gameRows) -> GameLineup.from(gameRows) }
        }

    suspend fun addPlayer(sport: Sport, name: String, jerseyNumber: Int?, position: String? = null): Long {
        return playerDao.insert(
            PlayerEntity(
                name = name.trim(),
                jerseyNumber = jerseyNumber,
                sport = sport.name,
                position = position,
            ),
        )
    }

    suspend fun updatePlayer(player: PlayerEntity) {
        playerDao.update(player)
    }

    // game_players rows cascade-delete with the player, freeing any on-court slot they held, and
    // their past events keep the record with the player reference nulled (SET_NULL).
    suspend fun deletePlayer(playerId: Long) {
        playerDao.deleteById(playerId)
    }

    suspend fun createGame(
        sport: Sport,
        title: String,
        opponent: String,
        periodCount: Int = 2,
        periodDurationMillis: Long = 10 * 60 * 1000L,
        onCourtSize: Int = 5,
    ): Long {
        return gameDao.insert(
            GameEntity(
                title = title.trim(),
                opponent = opponent.trim(),
                createdAt = System.currentTimeMillis(),
                periodCount = periodCount,
                periodDurationMillis = periodDurationMillis,
                sport = sport.name,
                onCourtSize = onCourtSize,
            ),
        )
    }

    suspend fun advancePeriod(gameId: Long) {
        database.withTransaction {
            val game = gameDao.getById(gameId)?.takeUnless { it.isFinished } ?: return@withTransaction
            if (game.currentPeriod < game.periodCount) {
                // Record the end of the previous period
                gameEventDao.insert(
                    GameEventEntity(
                        gameId = gameId,
                        timestamp = System.currentTimeMillis(),
                        type = EventType.PERIOD_CHANGE.name,
                        // Stored in English; the event log shows it translated (see eventDescription).
                        note = "Finished Period ${game.currentPeriod}",
                        gameTimeMillis = calculateCurrentGameTime(game),
                    ),
                )

                gameDao.update(
                    game.copy(
                        currentPeriod = game.currentPeriod + 1,
                        runningTimeMillis = 0L,
                        timerLastStartedAt = null,
                    ),
                )
                clearCourt(gameId) // forces a new lineup for the next period
            }
        }
    }

    suspend fun endGame(gameId: Long) {
        database.withTransaction {
            val game = gameDao.getById(gameId) ?: return@withTransaction
            val endedAt = System.currentTimeMillis()
            gameDao.update(
                game.copy(
                    isFinished = true,
                    finishedAt = endedAt,
                    timerLastStartedAt = null,
                ),
            )
            clearCourt(gameId)
            gameEventDao.insert(
                GameEventEntity(
                    gameId = gameId,
                    timestamp = endedAt,
                    type = EventType.GAME_END.name,
                    note = "Game Ended",
                    gameTimeMillis = calculateCurrentGameTime(game),
                ),
            )
        }
    }

    suspend fun updateAbsentPlayers(gameId: Long, playerIds: List<Long>) {
        database.withTransaction {
            if (gameDao.getById(gameId) == null) return@withTransaction
            saveLineup(gameId, getLineup(gameId).copy(absentIds = playerIds.toSet()))
        }
    }

    suspend fun updateGameNotes(gameId: Long, notes: String) {
        database.withTransaction {
            val game = gameDao.getById(gameId) ?: return@withTransaction
            gameDao.update(game.copy(notes = notes))
        }
    }

    /**
     * Corrects an event's points and note from [event]. Only events with editable points (see
     * EventType.hasEditablePoints) change, and a team adjustment keeps its note, which is what
     * marks it as one; nothing else about the stored event changes.
     */
    suspend fun updateEvent(event: GameEventEntity) {
        database.withTransaction {
            val stored = gameEventDao.getById(event.id) ?: return@withTransaction
            if (!EventType.fromRaw(stored.type).hasEditablePoints) return@withTransaction
            val note = if (stored.note == TEAM_ADJUSTMENT_NOTE) stored.note else event.note
            gameEventDao.update(stored.copy(points = event.points, note = note))
            recalculateTotals(stored.gameId)
        }
    }

    // game_events.gameId is now ON DELETE CASCADE, so this no longer needs to delete a game's
    // events itself — the database does it as part of the same delete.
    suspend fun deleteGame(gameId: Long) {
        gameDao.deleteById(gameId)
    }

    suspend fun selectStartingLineup(gameId: Long, playerIds: List<Long>, maxSize: Int = 5) {
        database.withTransaction {
            val game = gameDao.getById(gameId)?.takeUnless { it.isFinished } ?: return@withTransaction
            val normalizedIds = playerIds.asSequence().distinct().take(maxSize).toList()

            saveLineup(gameId, getLineup(gameId).copy(onCourtIds = normalizedIds.toSet()))
            gameEventDao.insert(
                GameEventEntity(
                    gameId = gameId,
                    timestamp = System.currentTimeMillis(),
                    type = EventType.START_LINEUP.name,
                    note = idsToCsv(normalizedIds),
                    gameTimeMillis = calculateCurrentGameTime(game),
                ),
            )
        }
    }

    /**
     * Puts [playerInId] on and takes [playerOutId] off as one substitution. Either may be null: a
     * player joining without a swap (e.g. arriving late to a short-handed side), or going off
     * without a replacement (e.g. injured). Returns the event's id, or null if it isn't a valid
     * change now: the player coming on must be here and on the bench, the one going off must be on
     * court, and the court can't go over a full side.
     */
    suspend fun substitute(gameId: Long, playerInId: Long?, playerOutId: Long?): Long? =
        database.withTransaction {
            val game = gameDao.getById(gameId)?.takeUnless { it.isFinished } ?: return@withTransaction null
            val lineup = getLineup(gameId)

            if (playerInId == null && playerOutId == null) return@withTransaction null
            if (playerOutId != null && playerOutId !in lineup.onCourtIds) return@withTransaction null
            if (playerInId != null && (playerInId in lineup.onCourtIds || playerInId in lineup.absentIds)) {
                return@withTransaction null
            }
            val onCourt = lineup.onCourtIds - setOfNotNull(playerOutId) + setOfNotNull(playerInId)
            if (onCourt.size > game.onCourtSize) return@withTransaction null

            saveLineup(gameId, lineup.copy(onCourtIds = onCourt))
            gameEventDao.insert(
                GameEventEntity(
                    gameId = gameId,
                    timestamp = System.currentTimeMillis(),
                    type = EventType.SUBSTITUTION.name,
                    playerId = playerInId,
                    relatedPlayerId = playerOutId,
                    gameTimeMillis = calculateCurrentGameTime(game),
                ),
            )
        }

    /**
     * Adds [points] (negative to correct) to our team's score, credited to [playerId]. A null
     * player is a team-level adjustment that changes the team total without touching any
     * player's stats. Returns the event's id, or null if nothing was recorded.
     */
    suspend fun addTeamScore(gameId: Long, playerId: Long?, points: Int): Long? =
        insertScore(
            gameId = gameId,
            type = EventType.SCORE,
            playerId = playerId,
            points = points,
            note = if (playerId == null) TEAM_ADJUSTMENT_NOTE else "",
        )

    /** Adds [points] (negative to correct) to the opponent's score. Returns the event's id, or null. */
    suspend fun addOpponentScore(gameId: Long, points: Int): Long? =
        insertScore(gameId, EventType.OPPONENT_SCORE, playerId = null, points = points, note = "")

    private suspend fun insertScore(gameId: Long, type: EventType, playerId: Long?, points: Int, note: String): Long? =
        database.withTransaction {
            val game = gameDao.getById(gameId)?.takeUnless { it.isFinished } ?: return@withTransaction null
            val currentScore = if (type == EventType.OPPONENT_SCORE) game.opponentScore else game.teamScore
            if (currentScore + points < 0) return@withTransaction null

            val eventId = gameEventDao.insert(
                GameEventEntity(
                    gameId = gameId,
                    timestamp = System.currentTimeMillis(),
                    type = type.name,
                    playerId = playerId,
                    points = points,
                    note = note,
                    gameTimeMillis = calculateCurrentGameTime(game),
                ),
            )
            recalculateTotals(gameId)
            eventId
        }

    /**
     * Adds [adjustment] (negative to correct) to the player's infraction count. Returns the event's
     * id, or null if the game doesn't exist or the count would go below zero.
     */
    suspend fun addFoul(gameId: Long, playerId: Long, adjustment: Int = 1): Long? =
        database.withTransaction {
            val game = gameDao.getById(gameId)?.takeUnless { it.isFinished } ?: return@withTransaction null
            val fouls = gameEventDao.getByGame(gameId)
                .filter { it.type == EventType.FOUL.name && it.playerId == playerId }
                .sumOf { it.points }
            if (fouls + adjustment < 0) return@withTransaction null
            gameEventDao.insert(
                GameEventEntity(
                    gameId = gameId,
                    timestamp = System.currentTimeMillis(),
                    type = EventType.FOUL.name,
                    playerId = playerId,
                    points = adjustment,
                    gameTimeMillis = calculateCurrentGameTime(game),
                ),
            )
        }

    suspend fun toggleTimer(gameId: Long) {
        database.withTransaction {
            val game = gameDao.getById(gameId)?.takeUnless { it.isFinished } ?: return@withTransaction
            val now = System.currentTimeMillis()

            if (game.timerLastStartedAt == null) {
                // Start, unless the period's time has already run out
                if (game.remainingPeriodTimeMillis(now) == 0L) return@withTransaction
                gameDao.update(game.copy(timerLastStartedAt = now))
            } else {
                // Stop
                gameDao.update(
                    game.copy(
                        runningTimeMillis = game.currentGameTimeMillis(now),
                        timerLastStartedAt = null,
                    ),
                )
            }
        }
    }

    /** Stops the clock if it's running; unlike [toggleTimer], never starts it. */
    suspend fun stopTimer(gameId: Long) {
        database.withTransaction {
            val game = gameDao.getById(gameId) ?: return@withTransaction
            if (game.timerLastStartedAt == null) return@withTransaction
            gameDao.update(
                game.copy(
                    runningTimeMillis = game.currentGameTimeMillis(),
                    timerLastStartedAt = null,
                ),
            )
        }
    }

    /**
     * Sets (and stops) the clock at [timeMillis] into the period. Returns null if it was set, or,
     * if [timeMillis] is earlier than an event already recorded this period, leaves the clock alone
     * and returns that event's time: the earliest the clock can go (see latestEventTimeThisPeriod).
     */
    suspend fun setGameTime(gameId: Long, timeMillis: Long): Long? =
        database.withTransaction {
            val game = gameDao.getById(gameId)?.takeUnless { it.isFinished } ?: return@withTransaction null
            val target = capToPeriod(timeMillis, game.periodDurationMillis)
            val earliest = latestEventTimeThisPeriod(gameEventDao.getByGame(gameId), game.periodDurationMillis)
            if (target < earliest) return@withTransaction earliest
            gameDao.update(game.copy(runningTimeMillis = target, timerLastStartedAt = null))
            null
        }

    suspend fun clearAllData() {
        database.withTransaction {
            gameDao.deleteAll() // game_events.gameId is ON DELETE CASCADE
            playerDao.deleteAll()
        }
    }

    /** Deletes the chosen categories of [sport]'s data, leaving every other sport's alone. */
    suspend fun clearSelectedData(
        sport: Sport,
        players: Boolean,
        games: Boolean,
        events: Boolean,
    ) {
        database.withTransaction {
            if (games) {
                gameDao.deleteAllBySport(sport.name) // game_events.gameId is ON DELETE CASCADE
            } else if (events) {
                gameEventDao.deleteAllBySport(sport.name)
                gameDao.getAllBySport(sport.name).forEach { recalculateTotals(it.id) }
            }
            if (players) {
                playerDao.deleteAllBySport(sport.name) // game_players.playerId is ON DELETE CASCADE
            }
        }
    }

    /** Resets the clock to the start of the period; see [setGameTime] for when it refuses. */
    suspend fun resetTimer(gameId: Long): Long? = setGameTime(gameId, 0L)

    /** Returns the event's id, or null if the game doesn't exist. */
    suspend fun addMvpVote(gameId: Long, playerId: Long): Long? =
        database.withTransaction {
            val game = gameDao.getById(gameId) ?: return@withTransaction null
            val eventId = gameEventDao.insert(
                GameEventEntity(
                    gameId = gameId,
                    timestamp = System.currentTimeMillis(),
                    type = EventType.MVP_VOTE.name,
                    playerId = playerId,
                    gameTimeMillis = calculateCurrentGameTime(game),
                ),
            )
            recalculateTotals(gameId)
            eventId
        }

    suspend fun getEvent(eventId: Long): GameEventEntity? = gameEventDao.getById(eventId)

    /**
     * Reverses the actions that recorded [eventIds] (scores, infractions, MVP votes, and
     * substitutions), newest first, all or nothing. Each event is deleted, and a substitution also
     * swaps its players back, but only if they're still where it put them: if the lineup has moved
     * on since, nothing is undone and this returns false, as it does if an event no longer exists.
     */
    suspend fun undoEvents(eventIds: List<Long>): Boolean = try {
        database.withTransaction {
            eventIds.asReversed().forEach { eventId -> requireUndone(undoEvent(eventId)) }
            true
        }
    } catch (_: UndoNotPossible) {
        false
    }

    private class UndoNotPossible : Exception()

    private fun requireUndone(undone: Boolean) {
        // Throwing (rather than returning) rolls back the whole transaction.
        if (!undone) throw UndoNotPossible()
    }

    /** Undoes one event; call inside a transaction. */
    private suspend fun undoEvent(eventId: Long): Boolean {
        val event = gameEventDao.getById(eventId) ?: return false
        val type = EventType.fromRaw(event.type)
        if (!type.isRemovable) return false
        if (type == EventType.SUBSTITUTION) {
            // Either side may be missing: a join without a swap, going off without a replacement,
            // or a player since deleted from the roster.
            val playerIn = event.playerId
            val playerOut = event.relatedPlayerId
            if (playerIn == null && playerOut == null) return false
            val lineup = getLineup(event.gameId)
            if (playerIn != null && playerIn !in lineup.onCourtIds) return false
            if (playerOut != null && playerOut in lineup.onCourtIds) return false
            val onCourt = lineup.onCourtIds - setOfNotNull(playerIn) + setOfNotNull(playerOut)
            val game = gameDao.getById(event.gameId) ?: return false
            if (onCourt.size > game.onCourtSize) return false
            saveLineup(event.gameId, lineup.copy(onCourtIds = onCourt))
        }
        gameEventDao.deleteById(eventId)
        recalculateTotals(event.gameId)
        return true
    }

    /** Replaces the game's on-court/absent rows with [lineup]; call inside a transaction. */
    private suspend fun saveLineup(gameId: Long, lineup: GameLineup) {
        gamePlayerDao.deleteByGame(gameId)
        gamePlayerDao.insertAll(lineup.toEntities(gameId))
    }

    /** Takes everyone off the court, keeping attendance; call inside a transaction. */
    private suspend fun clearCourt(gameId: Long) {
        saveLineup(gameId, getLineup(gameId).copy(onCourtIds = emptySet()))
    }

    /** Re-derives the game's stored score and MVP totals from its events; call inside a transaction. */
    private suspend fun recalculateTotals(gameId: Long) {
        val game = gameDao.getById(gameId) ?: return
        val updated = game.withTotalsFrom(gameEventDao.getByGame(gameId))
        if (updated != game) gameDao.update(updated)
    }

    private fun calculateCurrentGameTime(game: GameEntity): Long = game.currentGameTimeMillis()
}
