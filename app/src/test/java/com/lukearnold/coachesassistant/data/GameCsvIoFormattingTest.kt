package com.lukearnold.coachesassistant.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GameCsvIoFormattingTest {

    @Test
    fun escapeCsv_wrapsInQuotesAndDoublesEmbeddedQuotes() {
        assertEquals("\"Alex\"", escapeCsv("Alex"))
        assertEquals("\"Say \"\"hi\"\"\"", escapeCsv("Say \"hi\""))
    }

    @Test
    fun escapeCsv_prefixesValuesASpreadsheetWouldRunAsFormulas() {
        assertEquals("\"'=HYPERLINK(\"\"http://x\"\")\"", escapeCsv("=HYPERLINK(\"http://x\")"))
        assertEquals("\"'+1\"", escapeCsv("+1"))
        assertEquals("\"'- tired legs\"", escapeCsv("- tired legs"))
        assertEquals("\"'@SUM(A1)\"", escapeCsv("@SUM(A1)"))
        assertEquals("\"'\tx\"", escapeCsv("\tx"))
        // Only the first character matters.
        assertEquals("\"Alex = MVP\"", escapeCsv("Alex = MVP"))
        assertEquals("\"\"", escapeCsv(""))
    }

    @Test
    fun unescapeCsvFormula_undoesEscapeCsvAndLeavesOtherApostrophesAlone() {
        assertEquals("=1+1", unescapeCsvFormula("'=1+1"))
        assertEquals("-5", unescapeCsvFormula("'-5"))
        assertEquals("'Tis", unescapeCsvFormula("'Tis"))
        assertEquals("'", unescapeCsvFormula("'"))
        assertEquals("O'Brien", unescapeCsvFormula("O'Brien"))
    }

    @Test
    fun parseCsvLine_splitsOnUnquotedCommas() {
        assertEquals(listOf("1", "Alex", "23"), parseCsvLine("1,Alex,23"))
    }

    @Test
    fun parseCsvLine_treatsCommasInsideQuotesAsLiteralAndStripsTheWrappingQuotes() {
        assertEquals(listOf("1", "Smith, Alex", "23"), parseCsvLine("1,\"Smith, Alex\",23"))
    }

    @Test
    fun parseCsvLine_unescapesDoubledQuotesInsideAQuotedField() {
        assertEquals(listOf("1", "Say \"hi\"", "23"), parseCsvLine("1,\"Say \"\"hi\"\"\",23"))
    }

    @Test
    fun formatDuration_padsMinutesAndSeconds() {
        assertEquals("00:00", formatDuration(0L))
        assertEquals("01:05", formatDuration(65_000L))
        assertEquals("12:34", formatDuration((12 * 60 + 34) * 1000L))
    }

    @Test
    fun lineupNoteToNames_resolvesKnownIdsAndSkipsUnknownOnes() {
        val players = mapOf(
            1L to PlayerEntity(id = 1, name = "Alex", jerseyNumber = 7),
            2L to PlayerEntity(id = 2, name = "Sam", jerseyNumber = null),
        )

        // id 99 isn't in the roster map (e.g. a deleted player) and should be silently skipped.
        assertEquals("#7 Alex | Sam", lineupNoteToNames("1,2,99", players))
    }
}
