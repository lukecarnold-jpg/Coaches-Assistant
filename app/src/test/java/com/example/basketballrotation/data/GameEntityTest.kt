package com.example.basketballrotation.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GameEntityTest {

    private val baseGame = GameEntity(
        title = "Test Game",
        opponent = "Rivals",
        createdAt = 0L,
    )

    @Test
    fun gameLineup_roundTripsThroughRows_withOneRowPerPlayer() {
        val lineup = GameLineup(onCourtIds = setOf(1L, 2L), absentIds = setOf(2L, 3L))

        val rows = lineup.toEntities(gameId = 9L)

        assertEquals(3, rows.size)
        assertEquals(GamePlayerEntity(9L, 2L, onCourt = true, absent = true), rows.single { it.playerId == 2L })
        assertEquals(lineup, GameLineup.from(rows))
    }

    @Test
    fun currentGameTimeMillis_addsTimeSinceTheClockStarted() {
        val running = baseGame.copy(runningTimeMillis = 60_000L, timerLastStartedAt = 1_000L)

        assertEquals(90_000L, running.currentGameTimeMillis(now = 31_000L))
    }

    @Test
    fun currentGameTimeMillis_isCappedAtThePeriodLength() {
        // 10-minute period, clock left running for 15 minutes.
        val running = baseGame.copy(periodDurationMillis = 600_000L, timerLastStartedAt = 0L)

        assertEquals(600_000L, running.currentGameTimeMillis(now = 900_000L))
        assertEquals(0L, running.remainingPeriodTimeMillis(now = 900_000L))
    }

    @Test
    fun currentGameTimeMillis_isUncappedWhenThePeriodHasNoLength() {
        val running = baseGame.copy(periodDurationMillis = 0L, timerLastStartedAt = 0L)

        assertEquals(900_000L, running.currentGameTimeMillis(now = 900_000L))
        assertEquals(null, running.remainingPeriodTimeMillis(now = 900_000L))
    }

    @Test
    fun withTotalsFrom_sumsScoresPerSideAndCountsMvpVotes() {
        fun event(type: EventType, points: Int = 0, playerId: Long? = null) =
            GameEventEntity(gameId = 0, timestamp = 0, type = type.name, playerId = playerId, points = points)

        val totals = baseGame.copy(teamScore = 99, opponentScore = 99, mvpVotesJson = "{}").withTotalsFrom(
            listOf(
                event(EventType.SCORE, points = 3, playerId = 1),
                event(EventType.SCORE, points = -1, playerId = 1),
                event(EventType.OPPONENT_SCORE, points = 2),
                event(EventType.FOUL, points = 1, playerId = 2),
                event(EventType.MVP_VOTE, playerId = 2),
                event(EventType.MVP_VOTE, playerId = 2),
                // A vote for a since-deleted player (playerId nulled out) no longer counts.
                event(EventType.MVP_VOTE, playerId = null),
            ),
        )

        assertEquals(2, totals.teamScore)
        assertEquals(2, totals.opponentScore)
        assertEquals(mapOf(2L to 2), MvpVotes.parse(totals.mvpVotesJson))
    }

    @Test
    fun withTotalsFrom_neverReportsANegativeScore() {
        val totals = baseGame.withTotalsFrom(
            listOf(GameEventEntity(gameId = 0, timestamp = 0, type = EventType.OPPONENT_SCORE.name, points = -4)),
        )

        assertEquals(0, totals.opponentScore)
    }
}
