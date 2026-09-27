package com.lukearnold.coachesassistant.ui

import com.lukearnold.coachesassistant.data.EventType
import com.lukearnold.coachesassistant.data.GameEntity
import com.lukearnold.coachesassistant.data.GameEventEntity
import com.lukearnold.coachesassistant.data.PlayerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayingTimeTest {

    // Basketball-style: 4 x 10-minute quarters, 5 on court.
    private val game = GameEntity(
        title = "Test",
        opponent = "Rivals",
        createdAt = 0L,
        periodCount = 4,
        periodDurationMillis = 600_000L,
        onCourtSize = 5,
    )

    @Test
    fun fairShare_splitsOnCourtTimeEvenlyAcrossAvailablePlayers() {
        // 40 minutes x 5 spots = 200 player-minutes over 8 players = 25 minutes each.
        val share = fairShare(game, availablePlayers = 8, elapsedGameMillis = 960_000L)!!

        assertEquals(1_500_000L, share.fullGameMillis)
        assertEquals(600_000L, share.soFarMillis) // 16:00 played -> 10:00 each so far
        assertEquals(120_000L, share.behindMillis(480_000L))
        assertEquals(-60_000L, share.behindMillis(660_000L))
    }

    @Test
    fun fairShare_isCappedAtTheTimeItself_whenFewerPlayersThanSpots() {
        val share = fairShare(game, availablePlayers = 4, elapsedGameMillis = 300_000L)!!

        assertEquals(2_400_000L, share.fullGameMillis) // the whole game, not 50 minutes
        assertEquals(300_000L, share.soFarMillis)
    }

    @Test
    fun fairShare_isNullWithNoPlayersOrNoPeriodLength() {
        assertNull(fairShare(game, availablePlayers = 0, elapsedGameMillis = 0L))
        assertNull(fairShare(game.copy(periodDurationMillis = 0L), availablePlayers = 8, elapsedGameMillis = 0L))
    }

    @Test
    fun elapsedGameMillis_countsCompletedPeriodsAsPlayedPlusTheCurrentClock() {
        fun periodEnd(atMillis: Long) =
            GameEventEntity(gameId = 1, timestamp = 0, type = EventType.PERIOD_CHANGE.name, gameTimeMillis = atMillis)

        val events = listOf(
            periodEnd(600_000L),
            periodEnd(420_000L), // second period ended early, at 7:00
            GameEventEntity(gameId = 1, timestamp = 0, type = EventType.SCORE.name, points = 2, gameTimeMillis = 100_000L),
        )

        assertEquals(1_110_000L, elapsedGameMillis(events, currentPeriodMillis = 90_000L))
    }

    @Test
    fun benchSubOrder_putsLeastTimePlayedFirst_thenLongestOnTheBench() {
        fun benched(id: Long, playedMillis: Long, benchedAt: Long) = PlayerGameStat(
            player = PlayerEntity(id = id, name = "P$id"),
            points = 0,
            fouls = 0,
            mvpVotes = 0,
            timesSubbedOff = 0,
            onCourt = false,
            currentShiftTimeMillis = 0L,
            currentBenchTimeMillis = 0L,
            shiftStartGameTimeMillis = null,
            totalTimeMillis = playedMillis,
            benchStartGameTimeMillis = benchedAt,
        )

        val order = benchSubOrder(
            listOf(
                benched(1, playedMillis = 300_000L, benchedAt = 0L),
                benched(2, playedMillis = 120_000L, benchedAt = 400_000L),
                benched(3, playedMillis = 120_000L, benchedAt = 200_000L),
            ),
        )

        assertEquals(listOf(3L, 2L, 1L), order.map { it.player.id })
    }

    @Test
    fun paceLabel_callsSmallDifferencesOnPace() {
        assertEquals("on pace", paceLabel(29_000L).english())
        assertEquals("on pace", paceLabel(-29_000L).english())
        assertEquals("02:15 behind", paceLabel(135_000L).english())
        assertEquals("00:40 ahead", paceLabel(-40_000L).english())
    }

    @Test
    fun elapsedGameMillis_capsEachPeriodAtItsLength() {
        val events = listOf(
            GameEventEntity(gameId = 1, timestamp = 0, type = EventType.PERIOD_CHANGE.name, gameTimeMillis = 78_414_000L),
        )

        // A legacy period "ended" ~22 hours in counts as its 10 minutes; the running clock is capped too.
        assertEquals(1_200_000L, elapsedGameMillis(events, currentPeriodMillis = 900_000L, periodDurationMillis = 600_000L))
    }
}
