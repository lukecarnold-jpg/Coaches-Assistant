package com.lukearnold.coachesassistant.ui

import com.lukearnold.coachesassistant.data.PlayerEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SubSuggestionTest {

    private fun stat(id: Long, totalMillis: Long, onCourt: Boolean, benchSince: Long? = null) = PlayerGameStat(
        player = PlayerEntity(id = id, name = "P$id"),
        points = 0,
        fouls = 0,
        mvpVotes = 0,
        timesSubbedOff = 0,
        onCourt = onCourt,
        currentShiftTimeMillis = 0L,
        currentBenchTimeMillis = 0L,
        shiftStartGameTimeMillis = null,
        totalTimeMillis = totalMillis,
        benchStartGameTimeMillis = benchSince,
    )

    // Everyone's fair share so far: 6:00.
    private val share = FairShare(fullGameMillis = 1_200_000L, soFarMillis = 360_000L)

    @Test
    fun suggestsTheMostAheadOffAndTheMostBehindOn() {
        val onCourt = listOf(stat(1, 480_000, true), stat(2, 540_000, true)) // 2:00 and 3:00 ahead
        val bench = listOf(stat(3, 200_000, false), stat(4, 120_000, false)) // 2:40 and 4:00 behind

        val suggestion = subSuggestion(onCourt, bench, share, gameTimeMillis = 0L)!!

        assertEquals(2L to 4L, suggestion.key)
        assertEquals(180_000L, suggestion.offAheadMillis)
        assertEquals(240_000L, suggestion.onBehindMillis)
    }

    @Test
    fun nothingWhenMinutesAreEvenEnough() {
        // 1:59 ahead isn't enough; nor is a bench player only 1:00 behind.
        assertEquals(null, subSuggestion(listOf(stat(1, 479_000, true)), listOf(stat(3, 0, false)), share, 0L))
        assertEquals(null, subSuggestion(listOf(stat(1, 600_000, true)), listOf(stat(3, 300_000, false)), share, 0L))
    }

    @Test
    fun nothingWithAnEmptyBench_orNoShare() {
        assertEquals(null, subSuggestion(listOf(stat(1, 600_000, true)), emptyList(), share, 0L))
        assertEquals(null, subSuggestion(listOf(stat(1, 600_000, true)), listOf(stat(3, 0, false)), null, 0L))
    }
}
