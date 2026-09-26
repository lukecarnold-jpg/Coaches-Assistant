package com.lukearnold.coachesassistant.ui

import com.lukearnold.coachesassistant.data.RosterImportResult
import org.junit.Assert.assertEquals
import org.junit.Test

class RosterImportMessageTest {

    @Test
    fun describesWhatWasAddedAndSkipped() {
        assertEquals("Imported 1 player.", rosterImportMessage(RosterImportResult(added = 1, skipped = 0)))
        assertEquals("Imported 5 players (2 already on the roster).", rosterImportMessage(RosterImportResult(added = 5, skipped = 2)))
        assertEquals("No new players: all 4 were already on the roster.", rosterImportMessage(RosterImportResult(added = 0, skipped = 4)))
        assertEquals("No new players: they were already on the roster.", rosterImportMessage(RosterImportResult(added = 0, skipped = 1)))
        assertEquals("No players found in that file.", rosterImportMessage(RosterImportResult(added = 0, skipped = 0)))
    }
}
