package com.lukearnold.coachesassistant.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

/**
 * Cricket games' scorecards, innings totals, and field plans. A cricket game is otherwise an
 * ordinary game (see GameRepository): created, ended, and deleted the same way.
 */
class CricketRepository(private val database: AppDatabase) {
    private val cricketDao = database.cricketDao()
    private val gameDao = database.gameDao()

    fun observeScorecards(gameId: Long): Flow<List<CricketScorecardEntity>> = cricketDao.observeScorecards(gameId)

    /** Every scorecard from [sport]'s finished games, for season totals. */
    fun observeFinishedScorecards(sport: Sport): Flow<List<CricketScorecardEntity>> =
        cricketDao.observeFinishedScorecards(sport.name)

    fun observeField(gameId: Long): Flow<List<CricketFieldPositionEntity>> = cricketDao.observeField(gameId)

    /**
     * Saves [card] as its player's scorecard for its game, replacing any earlier one (an empty card
     * removes it), and updates the game's total.
     */
    suspend fun saveScorecard(card: CricketScorecardEntity) {
        val playerId = card.playerId ?: return
        database.withTransaction {
            val existing = cricketDao.getScorecard(card.gameId, playerId)
            when {
                card.isEmpty -> existing?.let { cricketDao.deleteScorecard(it.id) }
                else -> cricketDao.upsertScorecard(card.copy(id = existing?.id ?: 0))
            }
            updateTeamScore(card.gameId)
        }
    }

    /** Sets our innings' [extras] and the other team's [opponentRuns] and [opponentWickets]. */
    suspend fun updateInnings(gameId: Long, extras: Int, opponentRuns: Int, opponentWickets: Int) {
        database.withTransaction {
            val game = gameDao.getById(gameId) ?: return@withTransaction
            gameDao.update(
                game.copy(
                    extras = extras.coerceAtLeast(0),
                    opponentScore = opponentRuns.coerceAtLeast(0),
                    opponentWickets = opponentWickets.coerceIn(0, MAX_WICKETS),
                ),
            )
            updateTeamScore(gameId)
        }
    }

    /**
     * Puts [playerId] at [position] in [gameId]'s field plan, moving them from wherever they were
     * and moving out whoever was there; a null [playerId] just empties [position].
     */
    suspend fun setFieldPosition(gameId: Long, position: String, playerId: Long?) {
        database.withTransaction {
            if (playerId == null) {
                cricketDao.clearFieldPosition(gameId, position)
            } else {
                cricketDao.clearFieldSpot(gameId, playerId, position)
                cricketDao.insertFieldPositions(listOf(CricketFieldPositionEntity(gameId, playerId, position)))
            }
        }
    }

    suspend fun clearField(gameId: Long) {
        cricketDao.clearField(gameId)
    }

    /**
     * Copies the field plan from [sport]'s most recent other game that has one into [gameId],
     * replacing its plan, for players who are still on the roster. Returns whether there was one.
     */
    suspend fun copyLatestField(gameId: Long, sport: Sport): Boolean = database.withTransaction {
        val source = gameDao.getAllBySport(sport.name)
            .filter { it.id != gameId }
            .sortedByDescending { it.createdAt }
            .map { cricketDao.getField(it.id) }
            .firstOrNull { it.isNotEmpty() }
            ?: return@withTransaction false
        cricketDao.clearField(gameId)
        cricketDao.insertFieldPositions(source.map { it.copy(gameId = gameId) })
        true
    }

    /** Sets the game's teamScore and teamWickets from its scorecards and extras (see cricketTotals). */
    private suspend fun updateTeamScore(gameId: Long) {
        val game = gameDao.getById(gameId) ?: return
        val (runs, wickets) = cricketTotals(game, cricketDao.getScorecards(gameId))
        if (game.teamScore != runs || game.teamWickets != wickets) gameDao.update(game.copy(teamScore = runs, teamWickets = wickets))
    }

    companion object {
        const val MAX_WICKETS = 10
    }
}
