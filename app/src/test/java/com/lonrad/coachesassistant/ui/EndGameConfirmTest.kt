package com.lonrad.coachesassistant.ui

import com.lonrad.coachesassistant.data.GameEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EndGameConfirmTest {

    private val game = GameEntity(
        title = "Final",
        opponent = "Rivals",
        createdAt = 0L,
        teamScore = 42,
        opponentScore = 38,
        periodCount = 4,
    )

    @Test
    fun inTheLastPeriod_showsTheScoreAndWhatHappensNext() {
        assertEquals(
            "Final score: Us 42 – 38 Rivals. The game will move to History and can't be resumed.",
            endGameConfirmMessage(game.copy(currentPeriod = 4)).english(),
        )
    }

    @Test
    fun withPeriodsLeft_warnsThatTheGameIsntOver() {
        assertEquals(
            "Final score: Us 42 – 38 Rivals. You're only in Q2 of 4. The game will move to History and can't be resumed.",
            endGameConfirmMessage(game.copy(currentPeriod = 2)).english(),
        )
    }

    @Test
    fun withNoOpponentName_fallsBackToThem() {
        assertEquals(
            "Final score: Us 42 – 38 Them. The game will move to History and can't be resumed.",
            endGameConfirmMessage(game.copy(opponent = "", currentPeriod = 4)).english(),
        )
    }
}
