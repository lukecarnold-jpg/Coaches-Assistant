package com.example.basketballrotation.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class GameRepository(
    private val database: AppDatabase,
) {
    private val playerDao = database.playerDao()
    private val gameDao = database.gameDao()
    private val gameEventDao = database.gameEventDao()

    val players: Flow<List<PlayerEntity>> = playerDao.observeAll()
    val games: Flow<List<GameEntity>> = gameDao.observeAll()

    fun playersForSport(sport: Sport): Flow<List<PlayerEntity>> = playerDao.observeAllBySport(sport.name)
    fun gamesForSport(sport: Sport): Flow<List<GameEntity>> = gameDao.observeAllBySport(sport.name)

    fun observeEvents(gameId: Long): Flow<List<GameEventEntity>> =
        gameEventDao.observeByGame(gameId)

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

    suspend fun deletePlayer(playerId: Long) {
        database.withTransaction {
            gameDao.getAll().forEach { game ->
                val cleaned = game.withoutPlayer(playerId)
                if (cleaned !== game) {
                    gameDao.update(cleaned)
                }
            }
            playerDao.deleteById(playerId)
        }
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
            val game = gameDao.getById(gameId) ?: return@withTransaction
            if (game.currentPeriod < game.periodCount) {
                // Record the end of the previous period
                gameEventDao.insert(
                    GameEventEntity(
                        gameId = gameId,
                        timestamp = System.currentTimeMillis(),
                        type = EventType.PERIOD_CHANGE.name,
                        note = "Finished Period ${game.currentPeriod}",
                        gameTimeMillis = calculateCurrentGameTime(game),
                    ),
                )

                gameDao.update(
                    game.copy(
                        currentPeriod = game.currentPeriod + 1,
                        runningTimeMillis = 0L,
                        timerLastStartedAt = null,
                        onCourtCsv = "", // Reset court to force new lineup
                    ),
                )
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
                    onCourtCsv = "", // Reset player on court status
                ),
            )
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
            val game = gameDao.getById(gameId) ?: return@withTransaction
            gameDao.update(game.copy(absentPlayerIdsCsv = idsToCsv(playerIds)))
        }
    }

    suspend fun updateGameNotes(gameId: Long, notes: String) {
        database.withTransaction {
            val game = gameDao.getById(gameId) ?: return@withTransaction
            gameDao.update(game.copy(notes = notes))
        }
    }

    suspend fun updateEvent(event: GameEventEntity) {
        database.withTransaction {
            gameEventDao.update(event)
            recalculateTotals(event.gameId)
        }
    }

    suspend fun deleteEvent(event: GameEventEntity) {
        database.withTransaction {
            gameEventDao.deleteById(event.id)
            recalculateTotals(event.gameId)
        }
    }

    // game_events.gameId is now ON DELETE CASCADE, so this no longer needs to delete a game's
    // events itself — the database does it as part of the same delete.
    suspend fun deleteGame(gameId: Long) {
        gameDao.deleteById(gameId)
    }

    suspend fun selectStartingLineup(gameId: Long, playerIds: List<Long>, maxSize: Int = 5) {
        database.withTransaction {
            val game = gameDao.getById(gameId) ?: return@withTransaction
            val normalizedIds = playerIds.asSequence().distinct().take(maxSize).toList()

            gameDao.update(game.copy(onCourtCsv = idsToCsv(normalizedIds)))
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

    suspend fun substitute(gameId: Long, playerInId: Long, playerOutId: Long) {
        database.withTransaction {
            val game = gameDao.getById(gameId) ?: return@withTransaction
            val onCourt = game.onCourtIds().toMutableList()

            if (!onCourt.contains(playerOutId) || onCourt.contains(playerInId)) {
                return@withTransaction
            }

            val updated = onCourt.map { currentId ->
                if (currentId == playerOutId) playerInId else currentId
            }

            gameDao.update(game.copy(onCourtCsv = idsToCsv(updated)))
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
    }

    suspend fun addScore(gameId: Long, playerId: Long?, points: Int) {
        database.withTransaction {
            val game = gameDao.getById(gameId) ?: return@withTransaction
            // A null player means the points are the opponent's.
            val currentScore = if (playerId == null) game.opponentScore else game.teamScore
            if (currentScore + points < 0) return@withTransaction

            gameEventDao.insert(
                GameEventEntity(
                    gameId = gameId,
                    timestamp = System.currentTimeMillis(),
                    type = if (playerId == null) EventType.OPPONENT_SCORE.name else EventType.SCORE.name,
                    playerId = playerId,
                    points = points,
                    gameTimeMillis = calculateCurrentGameTime(game),
                ),
            )
            recalculateTotals(gameId)
        }
    }

    suspend fun addFoul(gameId: Long, playerId: Long, adjustment: Int = 1) {
        database.withTransaction {
            val game = gameDao.getById(gameId) ?: return@withTransaction
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
    }

    suspend fun toggleTimer(gameId: Long) {
        database.withTransaction {
            val game = gameDao.getById(gameId) ?: return@withTransaction
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

    suspend fun setGameTime(gameId: Long, timeMillis: Long) {
        database.withTransaction {
            val game = gameDao.getById(gameId) ?: return@withTransaction
            val capped = if (game.periodDurationMillis > 0) {
                timeMillis.coerceIn(0L, game.periodDurationMillis)
            } else {
                maxOf(0L, timeMillis)
            }
            gameDao.update(game.copy(runningTimeMillis = capped, timerLastStartedAt = null))
        }
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
                if (!games) {
                    // Every player is about to disappear. Without this, every surviving game keeps
                    // referencing the now-deleted player ids in onCourtCsv/absentPlayerIdsCsv —
                    // the same ghost-slot problem deletePlayer() guards against, just for every
                    // game at once (games.onCourtCsv isn't a real foreign key, so it isn't covered
                    // by the player -> game_events SET_NULL constraint).
                    gameDao.getAllBySport(sport.name).forEach { game ->
                        if (game.onCourtCsv.isNotEmpty() || game.absentPlayerIdsCsv.isNotEmpty()) {
                            gameDao.update(game.copy(onCourtCsv = "", absentPlayerIdsCsv = ""))
                        }
                    }
                }
                playerDao.deleteAllBySport(sport.name)
            }
        }
    }

    suspend fun resetTimer(gameId: Long) {
        database.withTransaction {
            val game = gameDao.getById(gameId) ?: return@withTransaction
            gameDao.update(game.copy(runningTimeMillis = 0L, timerLastStartedAt = null))
        }
    }

    suspend fun addMvpVote(gameId: Long, playerId: Long) {
        database.withTransaction {
            val game = gameDao.getById(gameId) ?: return@withTransaction
            gameEventDao.insert(
                GameEventEntity(
                    gameId = gameId,
                    timestamp = System.currentTimeMillis(),
                    type = EventType.MVP_VOTE.name,
                    playerId = playerId,
                    gameTimeMillis = calculateCurrentGameTime(game),
                ),
            )
            recalculateTotals(gameId)
        }
    }

    /** Re-derives the game's stored score and MVP totals from its events; call inside a transaction. */
    private suspend fun recalculateTotals(gameId: Long) {
        val game = gameDao.getById(gameId) ?: return
        val updated = game.withTotalsFrom(gameEventDao.getByGame(gameId))
        if (updated != game) gameDao.update(updated)
    }

    private fun calculateCurrentGameTime(game: GameEntity): Long = game.currentGameTimeMillis()
}
