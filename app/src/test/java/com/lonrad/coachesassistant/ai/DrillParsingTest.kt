package com.lonrad.coachesassistant.ai

import com.lonrad.coachesassistant.data.Sport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillParsingTest {

    @Test
    fun buildDrillPrompt_includesAgeGroupAndFormatInstructions() {
        val prompt = buildDrillPrompt(Sport.BASKETBALL, "Under 10's")

        assertTrue(prompt.contains("Under 10's"))
        assertTrue(prompt.contains("TITLE:"))
        assertTrue(prompt.contains("DESCRIPTION:"))
        assertTrue(prompt.contains("TEACHING POINTS:"))
    }

    @Test
    fun parseDrills_parsesWellFormedResponse() {
        val text = """
            TITLE: Dribble Relay
            DESCRIPTION: Players dribble cones and back.
            TEACHING POINTS: Keep head up, use fingertips.

            TITLE: Passing Circle
            DESCRIPTION: Players pass around a circle.
            TEACHING POINTS: Chest pass with both hands.
        """.trimIndent()

        val drills = parseDrills(text)

        assertEquals(2, drills.size)
        assertEquals("Dribble Relay", drills[0].title)
        assertEquals("Players dribble cones and back.", drills[0].description)
        assertEquals("Keep head up, use fingertips.", drills[0].teachingPoints)
        assertEquals("Passing Circle", drills[1].title)
    }

    @Test
    fun parseDrills_isCaseInsensitiveToLabels() {
        val text = "title: Layup Lines\ndescription: Line drill.\nteaching points: Footwork."

        val drills = parseDrills(text)

        assertEquals(1, drills.size)
        assertEquals("Layup Lines", drills[0].title)
    }

    @Test
    fun parseDrills_capsAtThreeDrills() {
        val block = { n: Int -> "TITLE: Drill $n\nDESCRIPTION: Desc $n\nTEACHING POINTS: Point $n\n" }
        val text = (1..5).joinToString("\n") { block(it) }

        val drills = parseDrills(text)

        assertEquals(3, drills.size)
    }

    @Test
    fun parseDrills_ignoresBlocksMissingRequiredLabels() {
        val text = "TITLE: Incomplete Drill\nJust some notes with no description or teaching points."

        val drills = parseDrills(text)

        assertEquals(0, drills.size)
    }

    @Test
    fun parseDrills_blankInput_returnsEmptyList() {
        assertEquals(0, parseDrills("").size)
    }

    @Test
    fun buildDrillPrompt_namesTheSport_spellingOutAmbiguousOnes() {
        assertTrue(buildDrillPrompt(Sport.BASKETBALL, "Under 10's").contains("3 age-appropriate basketball drills"))
        assertTrue(buildDrillPrompt(Sport.NETBALL, "Under 12's").contains("3 age-appropriate netball drills"))
        assertTrue(buildDrillPrompt(Sport.AFL, "Under 12's").contains("Australian rules football (AFL) drills"))
        assertTrue(buildDrillPrompt(Sport.TOUCH_FOOTBALL, "Ages 5-6").contains("touch football (touch rugby) drills"))
    }

    @Test
    fun buildDrillPrompt_inEnglish_saysNothingAboutLanguage() {
        assertEquals(buildDrillPrompt(Sport.BASKETBALL, "Under 10's"), buildDrillPrompt(Sport.BASKETBALL, "Under 10's", "en"))
        assertEquals(buildDrillPrompt(Sport.BASKETBALL, "Under 10's"), buildDrillPrompt(Sport.BASKETBALL, "Under 10's", "en-AU"))
        assertFalse(buildDrillPrompt(Sport.BASKETBALL, "Under 10's").contains("Write the"))
    }

    @Test
    fun buildDrillPrompt_inAnotherLanguage_asksForItButKeepsTheLabelsEnglish() {
        val prompt = buildDrillPrompt(Sport.SOCCER, "Moins de 10 ans", "fr")

        assertTrue(prompt.contains("Write the titles, descriptions, and teaching points in French"))
        assertTrue(prompt.contains("keep the labels 'TITLE:', 'DESCRIPTION:', and 'TEACHING POINTS:' in English"))
        assertTrue(buildDrillPrompt(Sport.SOCCER, "Sub-10", "pt-BR").contains("in Portuguese"))
    }

    @Test
    fun parseDrills_readsDrillsWrittenInAnotherLanguage() {
        val text = """
            TITLE: Passes en triangle
            DESCRIPTION: Trois joueurs se passent le ballon.
            TEACHING POINTS: Regarder le partenaire avant de passer.
        """.trimIndent()

        assertEquals(listOf(Drill("Passes en triangle", "Trois joueurs se passent le ballon.", "Regarder le partenaire avant de passer.")), parseDrills(text))
    }
}
