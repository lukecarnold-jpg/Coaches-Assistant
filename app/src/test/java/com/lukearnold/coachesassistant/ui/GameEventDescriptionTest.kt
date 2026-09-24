package com.lukearnold.coachesassistant.ui

import com.lukearnold.coachesassistant.data.EventType
import com.lukearnold.coachesassistant.data.GameEventEntity
import com.lukearnold.coachesassistant.data.PlayerEntity
import com.lukearnold.coachesassistant.data.TEAM_ADJUSTMENT_NOTE
import org.junit.Assert.assertEquals
import org.junit.Test

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

        val text = eventDescription(event, emptyMap(), "Tigers")

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

        val text = eventDescription(event, mapOf(player.id to player))

        assertEquals("#11 Alex +3", text)
    }

    @Test
    fun scoreDescription_showsNegativeAdjustmentsWithoutAPlusSign() {
        val player = PlayerEntity(id = 7, name = "Alex", jerseyNumber = 11)
        val event = GameEventEntity(gameId = 1, timestamp = 0, type = EventType.SCORE.name, playerId = 7, points = -1)

        assertEquals("#11 Alex -1", eventDescription(event, mapOf(player.id to player)))
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

        assertEquals("Team adjustment -1", eventDescription(adjustment, emptyMap()))
        assertEquals("Removed player +2", eventDescription(removedPlayersScore, emptyMap()))
    }
}
