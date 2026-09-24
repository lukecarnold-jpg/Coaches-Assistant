package com.example.basketballrotation.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GameEntityTest {

    private val baseGame = GameEntity(
        title = "Test Game",
        opponent = "Rivals",
        createdAt = 0L,
        onCourtCsv = "1,2,3,4,5",
        absentPlayerIdsCsv = "6,7",
    )

    @Test
    fun withoutPlayer_removesIdFromOnCourtList() {
        val cleaned = baseGame.withoutPlayer(3L)

        assertEquals(listOf(1L, 2L, 4L, 5L), cleaned.onCourtIds())
    }

    @Test
    fun withoutPlayer_removesIdFromAbsentList() {
        val cleaned = baseGame.withoutPlayer(6L)

        assertEquals(listOf(7L), cleaned.absentPlayerIds())
    }

    @Test
    fun withoutPlayer_leavesOtherGamesIntactWhenPlayerNotReferenced() {
        val cleaned = baseGame.withoutPlayer(999L)

        assertSame(baseGame, cleaned)
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
}
