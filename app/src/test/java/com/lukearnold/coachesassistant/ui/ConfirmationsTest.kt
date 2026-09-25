package com.lukearnold.coachesassistant.ui

import com.lukearnold.coachesassistant.data.GameEntity
import com.lukearnold.coachesassistant.data.PlayerEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfirmationsTest {

    private val game = GameEntity(
        title = "Rnd 3",
        opponent = "Rivals",
        createdAt = 0L,
        teamScore = 4,
        opponentScore = 2,
        periodCount = 4,
        currentPeriod = 1,
        periodDurationMillis = 600_000L,
    )

    @Test
    fun deleteGame_namesTheGameAndScore() {
        assertEquals(
            "Delete \"Rnd 3\" (Us 4 – 2 Rivals)? Its events and stats are removed and it no longer counts in " +
                "season totals. This can't be undone.",
            deleteGameConfirmMessage(game),
        )
    }

    @Test
    fun removePlayer_namesThePlayerAndWhatHappensToTheirHistory() {
        assertEquals(
            "Remove #2 Tommy from the roster? They're taken off any game lineups, and past games show their " +
                "scores as \"Removed player\". This can't be undone.",
            removePlayerConfirmMessage(PlayerEntity(id = 1, name = "Tommy", jerseyNumber = 2)),
        )
    }

    @Test
    fun nextPeriod_namesBothPeriodsAndTheFreshClock() {
        assertEquals(
            "Move on to Q2? Q1 ends at the current clock time, the clock resets to 10:00, and everyone comes off " +
                "court until you set the next lineup.",
            nextPeriodConfirmMessage(game),
        )
    }

    @Test
    fun resetClock_namesThePeriodAndFullTime() {
        assertEquals(
            "Reset the Q1 clock to 10:00? Use this to restart a period that hasn't really begun.",
            resetClockConfirmMessage(game),
        )
    }
}
