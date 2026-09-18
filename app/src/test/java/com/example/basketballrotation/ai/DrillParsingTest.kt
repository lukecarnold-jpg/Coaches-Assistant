package com.example.basketballrotation.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillParsingTest {

    @Test
    fun buildDrillPrompt_includesAgeGroupAndFormatInstructions() {
        val prompt = buildDrillPrompt("Under 10's")

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
}
