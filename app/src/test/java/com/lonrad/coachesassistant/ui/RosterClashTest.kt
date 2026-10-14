package com.lonrad.coachesassistant.ui

import com.lonrad.coachesassistant.data.PlayerEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class RosterClashTest {

    private val tommy = PlayerEntity(id = 1, name = "Tommy", jerseyNumber = 2)
    private val kit = PlayerEntity(id = 2, name = "Kit", jerseyNumber = null)
    private val roster = listOf(tommy, kit)

    @Test
    fun theSamePlayerAgain_isCaught_ignoringCaseAndSpaces() {
        assertEquals(RosterClash.SamePlayer(tommy), rosterClash(roster, " tommy ", 2))
        assertEquals(RosterClash.SamePlayer(kit), rosterClash(roster, "KIT", null))
    }

    @Test
    fun aTakenNumber_isReportedWithWhoWearsIt() {
        assertEquals(RosterClash.SharedNumber(tommy), rosterClash(roster, "Beau", 2))
    }

    @Test
    fun noClash_forANewNameAndNumber_orNoNumber() {
        assertEquals(null, rosterClash(roster, "Beau", 93))
        assertEquals(null, rosterClash(roster, "Beau", null))
        assertEquals(null, rosterClash(roster, "Tommy", 7)) // a second Tommy with a different number
    }

    @Test
    fun editingAPlayer_doesntClashWithThemselves() {
        assertEquals(null, rosterClash(roster, "Tommy", 2, exceptId = tommy.id))
        assertEquals(RosterClash.SharedNumber(tommy), rosterClash(roster, "Kit", 2, exceptId = kit.id))
    }
}
