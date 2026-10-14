package com.lonrad.coachesassistant.ui

import com.lonrad.coachesassistant.data.EventType
import com.lonrad.coachesassistant.data.GameEventEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class EventTimingTest {

    private fun event(id: Long, type: EventType, timestamp: Long = id) =
        GameEventEntity(id = id, gameId = 1, timestamp = timestamp, type = type.name)

    @Test
    fun eventPeriods_countsThePeriodsFinishedBeforeEachEvent() {
        val events = listOf(
            event(1, EventType.START_LINEUP),
            event(2, EventType.SCORE),
            event(3, EventType.PERIOD_CHANGE), // the end of period 1
            event(4, EventType.SCORE),
            event(5, EventType.PERIOD_CHANGE),
            event(6, EventType.GAME_END),
        )

        assertEquals(mapOf(1L to 1, 2L to 1, 3L to 1, 4L to 2, 5L to 2, 6L to 3), eventPeriods(events))
        // The event log's newest-first order doesn't matter.
        assertEquals(eventPeriods(events), eventPeriods(events.reversed()))
    }

    @Test
    fun clockReading_showsTimeLeftLikeTheGameClock() {
        assertEquals("07:00", clockReading(elapsedMillis = 180_000L, periodDurationMillis = 600_000L))
        assertEquals("10:00", clockReading(elapsedMillis = 0L, periodDurationMillis = 600_000L))
        // Events from before the clock stopped itself can be stamped past the period's end.
        assertEquals("00:00", clockReading(elapsedMillis = 5_000_000L, periodDurationMillis = 600_000L))
        // Without a set period length there's no countdown, so it's the time played.
        assertEquals("03:00", clockReading(elapsedMillis = 180_000L, periodDurationMillis = 0L))
    }
}
