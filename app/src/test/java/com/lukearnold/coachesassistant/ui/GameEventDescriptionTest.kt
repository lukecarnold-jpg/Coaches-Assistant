package com.lukearnold.coachesassistant.ui

import com.lukearnold.coachesassistant.data.EventType
import com.lukearnold.coachesassistant.data.GameEventEntity
import com.lukearnold.coachesassistant.data.PlayerEntity
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
}
