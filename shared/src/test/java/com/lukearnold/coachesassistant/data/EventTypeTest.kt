package com.lukearnold.coachesassistant.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EventTypeTest {

    @Test
    fun fromRaw_parsesOpponentScore() {
        assertEquals(EventType.OPPONENT_SCORE, EventType.fromRaw("OPPONENT_SCORE"))
    }

    @Test
    fun fromRaw_fallsBackToScoreForUnknownValue() {
        assertEquals(EventType.SCORE, EventType.fromRaw("UNKNOWN_EVENT"))
    }
}
