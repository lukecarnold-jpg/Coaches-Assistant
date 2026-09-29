package com.lukearnold.coachesassistant.ui

import com.lukearnold.coachesassistant.R
import com.lukearnold.coachesassistant.data.EventType
import com.lukearnold.coachesassistant.data.GameEventEntity
import com.lukearnold.coachesassistant.data.PlayerEntity
import com.lukearnold.coachesassistant.data.TEAM_ADJUSTMENT_NOTE
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameEventDescriptionTest {

    @Test
    fun opponentScoreDescription_includesSignedAdjustment() {
        val event = GameEventEntity(
            id = 1,
            gameId = 1,
            timestamp = 0,
            type = EventType.OPPONENT_SCORE.name,
            points = -1,
            gameTimeMillis = 0,
        )

        val text = eventDescription(event, emptyMap(), "Tigers").english()

        assertEquals("Tigers -1", text)
    }

    @Test
    fun scoreDescription_usesPlayerDisplayLabel() {
        val player = PlayerEntity(id = 7, name = "Alex", jerseyNumber = 11)
        val event = GameEventEntity(
            id = 2,
            gameId = 1,
            timestamp = 0,
            type = EventType.SCORE.name,
            playerId = 7,
            points = 3,
            gameTimeMillis = 0,
        )

        val text = eventDescription(event, mapOf(player.id to player)).english()

        assertEquals("#11 Alex +3", text)
    }

    @Test
    fun scoreDescription_showsNegativeAdjustmentsWithoutAPlusSign() {
        val player = PlayerEntity(id = 7, name = "Alex", jerseyNumber = 11)
        val event = GameEventEntity(gameId = 1, timestamp = 0, type = EventType.SCORE.name, playerId = 7, points = -1)

        assertEquals("#11 Alex -1", eventDescription(event, mapOf(player.id to player)).english())
    }

    @Test
    fun scoreDescription_withNoPlayer_distinguishesTeamAdjustmentsFromRemovedPlayers() {
        val adjustment = GameEventEntity(
            gameId = 1,
            timestamp = 0,
            type = EventType.SCORE.name,
            points = -1,
            note = TEAM_ADJUSTMENT_NOTE,
        )
        val removedPlayersScore = GameEventEntity(gameId = 1, timestamp = 0, type = EventType.SCORE.name, points = 2)

        assertEquals("Team adjustment -1", eventDescription(adjustment, emptyMap()).english())
        assertEquals("Removed player +2", eventDescription(removedPlayersScore, emptyMap()).english())
    }

    @Test
    fun periodChange_showsTheFinishedPeriodFromItsStoredNote() {
        val event = GameEventEntity(gameId = 1, timestamp = 0, type = EventType.PERIOD_CHANGE.name, note = "Finished Period 2")
        assertEquals(uiText(R.string.event_finished_period, 2), eventDescription(event, emptyMap()))
        assertEquals("Finished Period 2", eventDescription(event, emptyMap()).english())

        val unexpected = event.copy(note = "Half time")
        assertEquals("Half time", eventDescription(unexpected, emptyMap()).english())
    }

    @Test
    fun foul_usesTheSportsInfraction() {
        val player = PlayerEntity(id = 4, name = "Sam", jerseyNumber = 4)
        val event = GameEventEntity(gameId = 1, timestamp = 0, type = EventType.FOUL.name, playerId = 4, points = 1)

        assertEquals("#4 Sam free kick", eventDescription(event, mapOf(4L to player), infraction = Infraction.FREE_KICK).english())
    }

    @Test
    fun messagesCanContainOtherMessages() {
        val adjustment = GameEventEntity(gameId = 1, timestamp = 0, type = EventType.SCORE.name, points = -1, note = TEAM_ADJUSTMENT_NOTE)
        val removed = uiText(R.string.msg_event_removed_named, eventDescription(adjustment, emptyMap()))

        assertEquals("Team adjustment -1 removed", removed.english())
        assertEquals(
            "Backup saved: 1 player and 3 games across all sports.",
            uiText(R.string.msg_backup_saved, pluralText(R.plurals.player_count, 1), pluralText(R.plurals.game_count, 3)).english(),
        )
    }

    @Test
    fun substitutionWithOneSide_readsAsComingOnOrGoingOff() {
        val sam = PlayerEntity(id = 4, name = "Sam", jerseyNumber = 4)
        val join = GameEventEntity(gameId = 1, timestamp = 0, type = EventType.SUBSTITUTION.name, playerId = 4)
        val leave = GameEventEntity(gameId = 1, timestamp = 0, type = EventType.SUBSTITUTION.name, relatedPlayerId = 4)

        assertEquals("#4 Sam comes on", eventDescription(join, mapOf(4L to sam)).english())
        assertEquals("#4 Sam goes off", eventDescription(leave, mapOf(4L to sam)).english())
    }
}
