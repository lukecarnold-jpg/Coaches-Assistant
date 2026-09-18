package com.example.basketballrotation.ui

import com.example.basketballrotation.data.EventType
import com.example.basketballrotation.data.GameEntity
import com.example.basketballrotation.data.GameEventEntity
import com.example.basketballrotation.data.MvpVotes
import com.example.basketballrotation.data.PlayerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerGameStatsTest {

    private val player1 = PlayerEntity(id = 1, name = "Alex")
    private val player2 = PlayerEntity(id = 2, name = "Sam")

    private fun game(
        onCourtCsv: String = "",
        absentPlayerIdsCsv: String = "",
        runningTimeMillis: Long = 0L,
        mvpVotesJson: String = "{}",
    ) = GameEntity(
        id = 1,
        title = "Test game",
        opponent = "Rivals",
        createdAt = 0L,
        onCourtCsv = onCourtCsv,
        absentPlayerIdsCsv = absentPlayerIdsCsv,
        runningTimeMillis = runningTimeMillis,
        mvpVotesJson = mvpVotesJson,
    )

    @Test
    fun playerOnCourtSinceLineup_accruesShiftAndTotalTime() {
        val stats = calculatePlayerGameStats(
            playerList = listOf(player1),
            game = game(onCourtCsv = "1", runningTimeMillis = 300_000L),
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
            game = game(onCourtCsv = "2", runningTimeMillis = 180_000L),
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
            game = game(absentPlayerIdsCsv = "2"),
            events = emptyList(),
        )

        assertEquals(listOf(player1.id), stats.map { it.player.id })
    }

    @Test
    fun periodChange_closesOpenShiftIntoTotalTime() {
        val stats = calculatePlayerGameStats(
            playerList = listOf(player1),
            // Period reset: court cleared and clock zeroed, as BasketballRepository.advancePeriod does.
            game = game(onCourtCsv = "", runningTimeMillis = 0L),
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
    fun mvpVotes_areReadFromGameJson() {
        val stats = calculatePlayerGameStats(
            playerList = listOf(player1, player2),
            game = game(mvpVotesJson = MvpVotes.serialize(mapOf(2L to 3))),
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
            events = emptyList(),
        )

        val stat = stats.single()
        assertFalse(stat.onCourt)
        assertEquals(0L, stat.currentShiftTimeMillis)
        assertEquals(0L, stat.totalTimeMillis)
    }
}
