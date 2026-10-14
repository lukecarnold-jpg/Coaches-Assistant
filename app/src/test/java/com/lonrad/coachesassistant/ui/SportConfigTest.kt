package com.lonrad.coachesassistant.ui

import com.lonrad.coachesassistant.data.Sport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SportConfigTest {

    @Test
    fun everySport_hasAtLeastOneScoringOptionAndPositiveOnCourtSize() {
        // Scorecard sports (cricket) have no live scoring buttons.
        Sport.entries.filterNot { it.usesScorecards }.forEach { sport ->
            val config = sport.config
            assertTrue("${sport.name} needs a positive on-court size", config.onCourtSize > 0)
            assertTrue("${sport.name} needs at least one scoring option", config.scoringOptions.isNotEmpty())
            assertTrue(
                "${sport.name} scoring options must award positive points",
                config.scoringOptions.all { it.points > 0 },
            )
        }
    }

    @Test
    fun onCourtSizeMatchesExpectedTeamSizePerSport() {
        assertEquals(5, Sport.BASKETBALL.config.onCourtSize)
        assertEquals(7, Sport.NETBALL.config.onCourtSize)
        assertEquals(11, Sport.SOCCER.config.onCourtSize)
        assertEquals(18, Sport.AFL.config.onCourtSize)
        assertEquals(15, Sport.RUGBY_UNION.config.onCourtSize)
        assertEquals(13, Sport.RUGBY_LEAGUE.config.onCourtSize)
        assertEquals(5, Sport.FUTSAL.config.onCourtSize)
        assertEquals(11, Sport.FIELD_HOCKEY.config.onCourtSize)
        assertEquals(6, Sport.ICE_HOCKEY.config.onCourtSize)
        assertEquals(6, Sport.TOUCH_FOOTBALL.config.onCourtSize)
        assertEquals(7, Sport.WATER_POLO.config.onCourtSize)
        assertEquals(7, Sport.HANDBALL.config.onCourtSize)
        assertEquals(10, Sport.LACROSSE.config.onCourtSize)
        assertEquals(15, Sport.GAELIC_FOOTBALL.config.onCourtSize)
        assertEquals(5, Sport.FLAG_FOOTBALL.config.onCourtSize)
        assertEquals(7, Sport.ULTIMATE_FRISBEE.config.onCourtSize)
    }

    @Test
    fun aflScoringOptions_matchGoalAndBehindValues() {
        val options = Sport.AFL.config.scoringOptions
        assertEquals(6, options.first { it.label.english().contains("Goal") }.points)
        assertEquals(1, options.first { it.label.english().contains("Behind") }.points)
    }

    @Test
    fun infractions_haveTheirPluralsAndCounts() {
        assertEquals("Fouls", uiText(Sport.BASKETBALL.config.infraction.plural).english())
        assertEquals("Penalties", uiText(Sport.NETBALL.config.infraction.plural).english())
        assertEquals("Cards", uiText(Sport.SOCCER.config.infraction.plural).english())
        assertEquals("Exclusions", uiText(Sport.WATER_POLO.config.infraction.plural).english())
        assertEquals("1 penalty", pluralText(Infraction.PENALTY.count, 1).english())
        assertEquals("3 free kicks", pluralText(Infraction.FREE_KICK.count, 3).english())
    }

    @Test
    fun positionLabel_translatesKnownCodesAndShowsOthersAsStored() {
        assertEquals("Five-Eighth", positionLabel("Five-Eighth").english())
        assertEquals(UiText.Plain("Sweeper"), positionLabel("Sweeper"))
        Sport.entries.flatMap { it.config.squadPositions }.forEach { code ->
            assertEquals("$code shows as itself in English", code, positionLabel(code).english())
        }
    }

    @Test
    fun defaultPeriodCount_isHalvesThirdsOrQuarters_andIceHockeyHasThree() {
        // Scorecard sports (cricket) have no periods to set up.
        Sport.entries.filterNot { it.usesScorecards }.forEach { sport ->
            assertTrue(
                "${sport.name} needs a period count the game setup can offer",
                PERIOD_COUNT_OPTIONS.any { it.first == sport.config.defaultPeriodCount },
            )
        }
        assertEquals(3, Sport.ICE_HOCKEY.config.defaultPeriodCount)
        assertEquals(4, Sport.BASKETBALL.config.defaultPeriodCount)
        assertEquals(2, Sport.SOCCER.config.defaultPeriodCount)
    }

    @Test
    fun periodLabel_usesHalvesQuartersOrGenericPeriodPrefix() {
        val game = com.lonrad.coachesassistant.data.GameEntity(title = "", opponent = "", createdAt = 0L, currentPeriod = 2)

        assertEquals("H2", game.copy(periodCount = 2).periodLabel().english())
        assertEquals("P2", game.copy(periodCount = 3).periodLabel().english())
        assertEquals("Q2", game.copy(periodCount = 4).periodLabel().english())
    }
}
