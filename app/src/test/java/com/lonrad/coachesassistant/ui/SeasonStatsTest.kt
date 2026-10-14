package com.lonrad.coachesassistant.ui

import com.lonrad.coachesassistant.data.EventType
import com.lonrad.coachesassistant.data.GameEntity
import com.lonrad.coachesassistant.data.GameEventEntity
import com.lonrad.coachesassistant.data.GameLineup
import com.lonrad.coachesassistant.data.MvpVotes
import com.lonrad.coachesassistant.data.PlayerEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SeasonStatsTest {

    private val alex = PlayerEntity(id = 1, name = "Alex")
    private val sam = PlayerEntity(id = 2, name = "Sam")
    private val jo = PlayerEntity(id = 3, name = "Jo")

    private fun game(id: Long, us: Int, them: Int, finished: Boolean = true, mvp: Map<Long, Int> = emptyMap()) =
        GameEntity(
            id = id,
            title = "G$id",
            opponent = "Rivals",
            createdAt = 0L,
            teamScore = us,
            opponentScore = them,
            isFinished = finished,
            mvpVotesJson = MvpVotes.serialize(mvp),
        )

    private fun event(gameId: Long, type: EventType, at: Long, playerId: Long? = null, points: Int = 0, note: String = "") =
        GameEventEntity(gameId = gameId, timestamp = at, type = type.name, playerId = playerId, points = points, note = note, gameTimeMillis = at)

    @Test
    fun season_addsUpFinishedGamesOnly() {
        val games = listOf(
            game(1, us = 10, them = 6, mvp = mapOf(1L to 2)),
            game(2, us = 4, them = 8),
            game(3, us = 5, them = 5),
            game(4, us = 99, them = 0, finished = false), // in progress: not part of the season yet
        )
        val events = listOf(
            // Game 1: Alex plays 0:00-6:00 and scores 4; Sam comes on for him at 6:00 until the end at 10:00.
            event(1, EventType.START_LINEUP, 0, note = "1"),
            event(1, EventType.SCORE, 1, playerId = 1, points = 4),
            GameEventEntity(gameId = 1, timestamp = 360_000, type = EventType.SUBSTITUTION.name, playerId = 2, relatedPlayerId = 1, gameTimeMillis = 360_000),
            event(1, EventType.FOUL, 400_000, playerId = 2, points = 1),
            event(1, EventType.GAME_END, 600_000),
            // Game 2: Alex plays all 10:00 and scores 2; Sam is absent.
            event(2, EventType.START_LINEUP, 0, note = "1"),
            event(2, EventType.SCORE, 1, playerId = 1, points = 2),
            event(2, EventType.GAME_END, 600_000),
            // Game 3: nobody's lineup was ever set.
            event(3, EventType.GAME_END, 600_000),
            // Game 4 is unfinished, so its points don't count.
            event(4, EventType.START_LINEUP, 0, note = "2"),
            event(4, EventType.SCORE, 1, playerId = 2, points = 50),
        )
        val lineups = mapOf(2L to GameLineup(absentIds = setOf(2L)))

        val season = calculateSeasonStats(listOf(alex, sam, jo), games, events, lineups)

        assertEquals(3, season.games)
        assertEquals(1, season.wins)
        assertEquals(1, season.losses)
        assertEquals(1, season.ties)
        assertEquals(19, season.pointsFor)
        assertEquals(19, season.pointsAgainst)

        val byId = season.players.associateBy { it.player.id }
        assertEquals(listOf(1L, 2L, 3L), season.players.map { it.player.id }) // roster order, everyone listed

        with(byId.getValue(1)) {
            assertEquals(2, gamesPlayed)
            assertEquals(960_000L, totalTimeMillis) // 6:00 + 10:00
            assertEquals(480_000L, averageTimeMillis)
            assertEquals(6, points)
            assertEquals(2, mvpVotes)
        }
        with(byId.getValue(2)) {
            assertEquals(1, gamesPlayed)
            assertEquals(240_000L, totalTimeMillis)
            assertEquals(1, fouls)
            assertEquals(0, points) // game 4's 50 points don't count
        }
        with(byId.getValue(3)) {
            assertEquals(0, gamesPlayed)
            assertEquals(0L, averageTimeMillis)
        }
    }

    @Test
    fun season_withNoFinishedGames_isEmptyButStillListsTheRoster() {
        val season = calculateSeasonStats(listOf(alex), listOf(game(1, 3, 1, finished = false)), emptyList(), emptyMap())

        assertEquals(0, season.games)
        assertEquals(0, season.players.single().gamesPlayed)
    }

    @Test
    fun gamesPlayed_countsGamesOnCourtEvenWithoutTheClock() {
        // The clock never ran: Alex started, Sam came on for him, Jo sat the whole game.
        val events = listOf(
            GameEventEntity(gameId = 1, timestamp = 0, type = EventType.START_LINEUP.name, note = "1"),
            GameEventEntity(gameId = 1, timestamp = 1, type = EventType.SUBSTITUTION.name, playerId = 2, relatedPlayerId = 1),
            GameEventEntity(gameId = 1, timestamp = 2, type = EventType.GAME_END.name),
        )

        val season = calculateSeasonStats(listOf(alex, sam, jo), listOf(game(1, 3, 1)), events, emptyMap())

        assertEquals(listOf(1, 1, 0), season.players.map { it.gamesPlayed })
        assertEquals(listOf(0L, 0L, 0L), season.players.map { it.totalTimeMillis })
    }
}
