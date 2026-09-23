package com.example.basketballrotation.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GameCsvIoFormattingTest {

    @Test
    fun escapeCsv_wrapsInQuotesAndDoublesEmbeddedQuotes() {
        assertEquals("\"Alex\"", escapeCsv("Alex"))
        assertEquals("\"Say \"\"hi\"\"\"", escapeCsv("Say \"hi\""))
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
