package com.lukearnold.coachesassistant.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ClockEditTest {

    @Test
    fun elapsedFromRemaining_convertsTheCountdownBackToElapsedTime() {
        assertEquals(180_000L, elapsedFromRemaining(420_000L, periodDurationMillis = 600_000L)) // 7:00 left = 3:00 in
        assertEquals(0L, elapsedFromRemaining(600_000L, periodDurationMillis = 600_000L))
        assertEquals(0L, elapsedFromRemaining(900_000L, periodDurationMillis = 600_000L)) // more than the period
        assertEquals(600_000L, elapsedFromRemaining(0L, periodDurationMillis = 600_000L))
        assertEquals(90_000L, elapsedFromRemaining(90_000L, periodDurationMillis = 0L)) // no countdown
    }

    @Test
    fun clockTooEarlyMessage_speaksInTimeRemaining() {
        assertEquals(
            "Can't set the clock back past 05:00: something was already recorded then this period, " +
                "and moving before it would lose playing time.",
            clockTooEarlyMessage(periodDurationMillis = 600_000L, earliestElapsedMillis = 300_000L),
        )
    }
}
