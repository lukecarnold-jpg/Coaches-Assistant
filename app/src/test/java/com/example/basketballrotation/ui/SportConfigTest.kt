package com.example.basketballrotation.ui

import com.example.basketballrotation.data.Sport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SportConfigTest {

    @Test
    fun everySport_hasAtLeastOneScoringOptionAndPositiveOnCourtSize() {
        Sport.entries.forEach { sport ->
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
        assertEquals(6, options.first { it.label.contains("Goal") }.points)
        assertEquals(1, options.first { it.label.contains("Behind") }.points)
    }

    @Test
    fun infractionLabelPlural_pluralizesCorrectly() {
        assertEquals("Fouls", Sport.BASKETBALL.config.infractionLabelPlural)
        assertEquals("Penalties", Sport.NETBALL.config.infractionLabelPlural)
        assertEquals("Cards", Sport.SOCCER.config.infractionLabelPlural)
        assertEquals("Exclusions", Sport.WATER_POLO.config.infractionLabelPlural)
    }
}
