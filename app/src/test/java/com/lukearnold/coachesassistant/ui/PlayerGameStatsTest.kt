package com.lukearnold.coachesassistant.ui

import com.lukearnold.coachesassistant.data.EventType
import com.lukearnold.coachesassistant.data.GameEntity
import com.lukearnold.coachesassistant.data.GameLineup
import com.lukearnold.coachesassistant.data.GameEventEntity
import com.lukearnold.coachesassistant.data.MvpVotes
import com.lukearnold.coachesassistant.data.PlayerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerGameStatsTest {

    private val player1 = PlayerEntity(id = 1, name = "Alex")
    private val player2 = PlayerEntity(id = 2, name = "Sam")

    private fun game(
        runningTimeMillis: Long = 0L,
        mvpVotesJson: String = "{}",
    ) = GameEntity(
        id = 1,
        title = "Test game",
        opponent = "Rivals",
        createdAt = 0L,
        runningTimeMillis = runningTimeMillis,
        mvpVotesJson = mvpVotesJson,
    )

    @Test
    fun playerOnCourtSinceLineup_accruesShiftAndTotalTime() {
        val stats = calculatePlayerGameStats(
            playerList = listOf(player1),
            game = game(runningTimeMillis = 300_000L),
            lineup = GameLineup(onCourtIds = setOf(1)),
            events = listOf(
                GameEventEntity(
                    gameId = 1,
                    timestamp = 0,
                    type = EventType.START_LINEUP.name,
                    note = "1",
                    gameTimeMillis = 0,
                ),
                GameEventEntity(
                    gameId = 1,
                    timestamp = 1,
                    type = EventType.SCORE.name,
                    playerId = 1,
                    points = 2,
                    gameTimeMillis = 60_000,
                ),
            ),
        )

        val stat = stats.single()
        assertTrue(stat.onCourt)
        assertEquals(2, stat.points)
        assertEquals(300_000L, stat.currentShiftTimeMillis)
        assertEquals(300_000L, stat.totalTimeMillis)
        assertEquals(0L, stat.currentBenchTimeMillis)
        assertEquals(0L, stat.shiftStartGameTimeMillis)
    }

    @Test
    fun substitution_splitsShiftAndBenchTimeBetweenPlayers() {
        val stats = calculatePlayerGameStats(
            playerList = listOf(player1, player2),
            // Player 2 is now on court after subbing in for player 1 at the 2:00 mark.
            game = game(runningTimeMillis = 180_000L),
            lineup = GameLineup(onCourtIds = setOf(2)),
            events = listOf(
                GameEventEntity(
                    gameId = 1,
                    timestamp = 0,
                    type = EventType.START_LINEUP.name,
                    note = "1",
                    gameTimeMillis = 0,
                ),
                GameEventEntity(
                    gameId = 1,
                    timestamp = 1,
                    type = EventType.SUBSTITUTION.name,
                    playerId = 2,
                    relatedPlayerId = 1,
                    gameTimeMillis = 120_000,
                ),
            ),
        )

        val statsByPlayerId = stats.associateBy { it.player.id }
        val benched = statsByPlayerId.getValue(1)
        val onCourt = statsByPlayerId.getValue(2)

        assertFalse(benched.onCourt)
        assertEquals(1, benched.timesSubbedOff)
        assertEquals(60_000L, benched.currentBenchTimeMillis)
        assertEquals(120_000L, benched.totalTimeMillis)
        assertNull(benched.shiftStartGameTimeMillis)

        assertTrue(onCourt.onCourt)
        assertEquals(0, onCourt.timesSubbedOff)
        assertEquals(60_000L, onCourt.currentShiftTimeMillis)
        assertEquals(60_000L, onCourt.totalTimeMillis)
        assertEquals(120_000L, onCourt.shiftStartGameTimeMillis)
    }

    @Test
    fun absentPlayer_isExcludedFromStats() {
        val stats = calculatePlayerGameStats(
            playerList = listOf(player1, player2),
            game = game(),
            lineup = GameLineup(absentIds = setOf(2)),
            events = emptyList(),
        )

        assertEquals(listOf(player1.id), stats.map { it.player.id })
    }

    @Test
    fun periodChange_closesOpenShiftIntoTotalTime() {
        val stats = calculatePlayerGameStats(
            playerList = listOf(player1),
            // Period reset: court cleared and clock zeroed, as GameRepository.advancePeriod does.
            game = game(runningTimeMillis = 0L),
            lineup = GameLineup(),
            events = listOf(
                GameEventEntity(
                    gameId = 1,
                    timestamp = 0,
                    type = EventType.START_LINEUP.name,
                    note = "1",
                    gameTimeMillis = 0,
                ),
                GameEventEntity(
                    gameId = 1,
                    timestamp = 1,
                    type = EventType.PERIOD_CHANGE.name,
                    note = "Finished Period 1",
                    gameTimeMillis = 600_000,
                ),
            ),
        )

        val stat = stats.single()
        assertFalse(stat.onCourt)
        assertEquals(600_000L, stat.totalTimeMillis)
    }

    @Test
    fun periodChange_restartsBenchTimeForPlayersBenchedInAnEarlierPeriod() {
        val stats = calculatePlayerGameStats(
            playerList = listOf(player1, player2),
            // Period 2, 3:00 in, with player 2 on court.
            game = game(runningTimeMillis = 180_000L),
            lineup = GameLineup(onCourtIds = setOf(2)),
            events = listOf(
                GameEventEntity(
                    gameId = 1,
                    timestamp = 0,
                    type = EventType.START_LINEUP.name,
                    note = "1",
                    gameTimeMillis = 0,
                ),
                // Player 1 subbed off at 8:00 of period 1...
                GameEventEntity(
                    gameId = 1,
                    timestamp = 1,
                    type = EventType.SUBSTITUTION.name,
                    playerId = 2,
                    relatedPlayerId = 1,
                    gameTimeMillis = 480_000,
                ),
                GameEventEntity(
                    gameId = 1,
                    timestamp = 2,
                    type = EventType.PERIOD_CHANGE.name,
                    note = "Finished Period 1",
                    gameTimeMillis = 600_000,
                ),
                GameEventEntity(
                    gameId = 1,
                    timestamp = 3,
                    type = EventType.START_LINEUP.name,
                    note = "2",
                    gameTimeMillis = 0,
                ),
            ),
        )

        // ...so 3:00 into period 2 they've been on the bench all period, not max(0, 3:00 - 8:00).
        val benched = stats.associateBy { it.player.id }.getValue(1)
        assertFalse(benched.onCourt)
        assertEquals(180_000L, benched.currentBenchTimeMillis)
        assertEquals(480_000L, benched.totalTimeMillis)
    }

    @Test
    fun mvpVotes_areReadFromGameJson() {
        val stats = calculatePlayerGameStats(
            playerList = listOf(player1, player2),
            game = game(mvpVotesJson = MvpVotes.serialize(mapOf(2L to 3))),
            lineup = GameLineup(),
            events = emptyList(),
        )

        val statsByPlayerId = stats.associateBy { it.player.id }
        assertEquals(0, statsByPlayerId.getValue(1).mvpVotes)
        assertEquals(3, statsByPlayerId.getValue(2).mvpVotes)
    }

    @Test
    fun nullGame_returnsZeroedStatsForRoster() {
        val stats = calculatePlayerGameStats(
            playerList = listOf(player1),
            game = null,
            lineup = GameLineup(),
            events = emptyList(),
        )

        val stat = stats.single()
        assertFalse(stat.onCourt)
        assertEquals(0L, stat.currentShiftTimeMillis)
        assertEquals(0L, stat.totalTimeMillis)
    }

    @Test
    fun liveTimes_advanceFromTheStoredClockToTheLiveOne() {
        // Stored clock at 3:00 (e.g. when it was last started); player 1 subbed off for player 2 at 2:00.
        val stats = calculatePlayerGameStats(
            playerList = listOf(player1, player2),
            game = game(runningTimeMillis = 180_000L),
            lineup = GameLineup(onCourtIds = setOf(2)),
            events = listOf(
                GameEventEntity(gameId = 1, timestamp = 0, type = EventType.START_LINEUP.name, note = "1", gameTimeMillis = 0),
                GameEventEntity(
                    gameId = 1,
                    timestamp = 1,
                    type = EventType.SUBSTITUTION.name,
                    playerId = 2,
                    relatedPlayerId = 1,
                    gameTimeMillis = 120_000,
                ),
            ),
        ).associateBy { it.player.id }
        val benched = stats.getValue(1)
        val onCourt = stats.getValue(2)
        val liveClock = 240_000L // the clock has since run on to 4:00

        assertEquals(120_000L, onCourt.shiftTimeAt(liveClock))
        assertEquals(120_000L, onCourt.totalTimeAt(liveClock))
        assertEquals(0L, onCourt.benchTimeAt(liveClock))

        assertEquals(120_000L, benched.benchTimeAt(liveClock))
        assertEquals(0L, benched.shiftTimeAt(liveClock))
        assertEquals(120_000L, benched.totalTimeAt(liveClock)) // unchanged while on the bench

        // At the stored clock itself, the live figures match the stored ones.
        assertEquals(onCourt.currentShiftTimeMillis, onCourt.shiftTimeAt(180_000L))
        assertEquals(benched.currentBenchTimeMillis, benched.benchTimeAt(180_000L))
    }

    @Test
    fun millisUntilNextSecond_landsOnTheNextWholeSecond() {
        assertEquals(1000L, millisUntilNextSecond(0L))
        assertEquals(1000L, millisUntilNextSecond(5_000L))
        assertEquals(1L, millisUntilNextSecond(5_999L))
        assertEquals(750L, millisUntilNextSecond(62_250L))
    }

    @Test
    fun legacyEventsStampedPastThePeriodEnd_countNoMoreThanThePeriod() {
        // A game from before the clock stopped itself: 20-minute period, the clock left running,
        // and the game ended 1306.9 minutes "in", closing everyone's shift at that time.
        val stats = calculatePlayerGameStats(
            playerList = listOf(player1, player2),
            game = game(runningTimeMillis = 0L).copy(periodDurationMillis = 1_200_000L, isFinished = true),
            lineup = GameLineup(),
            events = listOf(
                GameEventEntity(gameId = 1, timestamp = 0, type = EventType.START_LINEUP.name, note = "1", gameTimeMillis = 0),
                GameEventEntity(
                    gameId = 1,
                    timestamp = 1,
                    type = EventType.SUBSTITUTION.name,
                    playerId = 2,
                    relatedPlayerId = 1,
                    gameTimeMillis = 1_100_000,
                ),
                GameEventEntity(gameId = 1, timestamp = 2, type = EventType.GAME_END.name, gameTimeMillis = 78_414_000),
            ),
        ).associateBy { it.player.id }

        assertEquals(1_100_000L, stats.getValue(1).totalTimeMillis) // 0:00-18:20, unaffected
        assertEquals(100_000L, stats.getValue(2).totalTimeMillis) // 18:20 to the 20:00 cap, not ~22 hours
    }
}
