package com.lonrad.coachesassistant.ui

import com.lonrad.coachesassistant.data.EventType
import com.lonrad.coachesassistant.data.GameEventEntity
import com.lonrad.coachesassistant.data.PlayerEntity
import com.lonrad.coachesassistant.data.Sport
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

    @Test
    fun excludedPlayers_areSkippedOnBothSides() {
        val onCourt = listOf(stat(1, 600_000, true), stat(2, 500_000, true)) // 4:00 and 2:20 ahead
        val bench = listOf(stat(3, 0, false), stat(4, 100_000, false)) // 6:00 and 4:20 behind

        // Player 1 (say the keeper) and player 3 are left out: next-most-ahead off, next-most-behind on.
        assertEquals(2L to 4L, subSuggestion(onCourt, bench, share, 0L, excluded = setOf(1L, 3L))!!.key)
    }

    @Test
    fun keepersAreExcluded_onlyWhereTheyPlayThrough() {
        val keeper = PlayerEntity(id = 1, name = "Kim", position = "GK")
        val striker = PlayerEntity(id = 2, name = "Sam", position = "FWD")

        assertEquals(setOf(1L), subSuggestionExclusions(listOf(keeper, striker), Sport.SOCCER.config, emptyList()))
        assertEquals(setOf(5L), subSuggestionExclusions(listOf(PlayerEntity(id = 5, name = "Lee", position = "G")), Sport.ICE_HOCKEY.config, emptyList()))
        // Netball's GK is a defender who rotates like everyone else.
        assertEquals(emptySet<Long>(), subSuggestionExclusions(listOf(keeper), Sport.NETBALL.config, emptyList()))
    }

    @Test
    fun aPlayerTakenOffWithoutAReplacement_isLeftOutUntilTheyComeBack() {
        fun event(id: Long, type: EventType, playerId: Long? = null, related: Long? = null, note: String = "") =
            GameEventEntity(id = id, gameId = 1, timestamp = id, type = type.name, playerId = playerId, relatedPlayerId = related, note = note)
        val injured = listOf(
            event(1, EventType.START_LINEUP, note = "1,2"),
            event(2, EventType.SUBSTITUTION, related = 1), // 1 goes off injured
        )
        assertEquals(setOf(1L), offWithoutReplacement(injured))

        // Back on through a join, a swap, or the next period's lineup: included again.
        assertEquals(emptySet<Long>(), offWithoutReplacement(injured + event(3, EventType.SUBSTITUTION, playerId = 1)))
        assertEquals(emptySet<Long>(), offWithoutReplacement(injured + event(3, EventType.SUBSTITUTION, playerId = 1, related = 2)))
        assertEquals(emptySet<Long>(), offWithoutReplacement(injured + event(3, EventType.START_LINEUP, note = "1,2")))
        // An ordinary swap doesn't count.
        assertEquals(emptySet<Long>(), offWithoutReplacement(listOf(event(1, EventType.SUBSTITUTION, playerId = 3, related = 1))))
    }
}
