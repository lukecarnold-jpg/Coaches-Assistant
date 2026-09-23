package com.example.basketballrotation.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GameEntityTest {

    private val baseGame = GameEntity(
        title = "Test Game",
        opponent = "Rivals",
        createdAt = 0L,
        onCourtCsv = "1,2,3,4,5",
        absentPlayerIdsCsv = "6,7",
    )

    @Test
    fun withoutPlayer_removesIdFromOnCourtList() {
        val cleaned = baseGame.withoutPlayer(3L)

        assertEquals(listOf(1L, 2L, 4L, 5L), cleaned.onCourtIds())
    }

    @Test
    fun withoutPlayer_removesIdFromAbsentList() {
        val cleaned = baseGame.withoutPlayer(6L)

        assertEquals(listOf(7L), cleaned.absentPlayerIds())
    }

    @Test
    fun withoutPlayer_leavesOtherGamesIntactWhenPlayerNotReferenced() {
        val cleaned = baseGame.withoutPlayer(999L)

        assertSame(baseGame, cleaned)
    }
}
