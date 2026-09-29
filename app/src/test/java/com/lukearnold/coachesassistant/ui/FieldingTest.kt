package com.lukearnold.coachesassistant.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.lukearnold.coachesassistant.data.Sport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FieldingTest {

    @Test
    fun enoughForAFullSide_meansExactlyAFullSide() {
        val rule = lineupRule(available = 8, onCourtSize = 5, minimum = 4)
        assertEquals(LineupRule.Full(5), rule)
        assertTrue(rule.allows(5))
        assertFalse(rule.allows(4)) // players are here, so no playing short
    }

    @Test
    fun exactlyAFullSide_isAFullSideWithNoSubs() {
        assertEquals(LineupRule.Full(5), lineupRule(available = 5, onCourtSize = 5, minimum = 5))
    }

    @Test
    fun shortOfAFullSide_canPlayShortHandedDownToTheMinimum() {
        val rule = lineupRule(available = 9, onCourtSize = 11, minimum = 7)
        assertEquals(LineupRule.ShortHanded(available = 9, minimum = 7, fullSize = 11), rule)
        assertTrue(rule.allows(9))
        assertTrue(rule.allows(7))
        assertFalse(rule.allows(6))
        assertFalse(rule.allows(10)) // more than are here
    }

    @Test
    fun belowTheMinimum_thereIsNoTeamToField() {
        val rule = lineupRule(available = 6, onCourtSize = 11, minimum = 7)
        assertEquals(LineupRule.TooFew(available = 6, minimum = 7), rule)
        assertFalse(rule.allows(6))
    }

    @Test
    fun theMinimumIsKeptBetweenOneAndAFullSide() {
        assertEquals(LineupRule.TooFew(0, 1), lineupRule(available = 0, onCourtSize = 5, minimum = 0))
        assertEquals(LineupRule.TooFew(4, 5), lineupRule(available = 4, onCourtSize = 5, minimum = 9))
    }

    @Test
    fun everySportsMinimum_isAtMostAFullSide() {
        Sport.entries.forEach { sport ->
            val config = sport.config
            assertTrue("${sport.name}'s minimum", config.minimumOnCourt in 1..config.onCourtSize)
        }
        assertEquals(7, Sport.SOCCER.config.minimumOnCourt) // IFAB Law 3
        assertEquals(13, Sport.GAELIC_FOOTBALL.config.minimumOnCourt)
    }

    @Test
    fun preferences_defaultToTheRules_andOnlyStoreAChange() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertEquals(7, FieldingPreferences.minimumOnCourt(app, Sport.SOCCER))

        FieldingPreferences.setMinimumOnCourt(app, Sport.SOCCER, 8)
        assertEquals(8, FieldingPreferences.minimumOnCourt(app, Sport.SOCCER))
        assertEquals(5, FieldingPreferences.minimumOnCourt(app, Sport.BASKETBALL)) // per sport

        FieldingPreferences.setMinimumOnCourt(app, Sport.SOCCER, 7)
        assertTrue(app.getSharedPreferences("fielding_prefs", 0).all.isEmpty())
    }
}
