package com.example.basketballrotation.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises [BasketballRepository]'s transactional behavior against a real (in-memory) Room
 * database. Robolectric supplies a working SQLite implementation so this runs as a fast JVM unit
 * test rather than needing an emulator-backed androidTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BasketballRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: BasketballRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = BasketballRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun addPlayers(count: Int): List<Long> = runBlocking {
        (1..count).map { i -> repository.addPlayer(Sport.BASKETBALL, "Player $i", i) }
    }

    private suspend fun newGame(onCourtSize: Int = 5): GameEntity {
        val gameId = repository.createGame(
            sport = Sport.BASKETBALL,
            title = "Test Game",
            opponent = "Rivals",
            onCourtSize = onCourtSize,
        )
        return database.gameDao().getById(gameId)!!
    }

    @Test
    fun deletePlayer_whileOnCourt_freesTheirSlotInsteadOfLeavingAGhost() = runBlocking {
        val playerIds = addPlayers(5)
        val game = newGame()
        repository.selectStartingLineup(game.id, playerIds, maxSize = 5)

        repository.deletePlayer(playerIds[2])

        val updated = database.gameDao().getById(game.id)!!
        assertEquals(4, updated.onCourtIds().size)
        assertFalse(updated.onCourtIds().contains(playerIds[2]))
    }

    @Test
    fun deletePlayer_alsoRemovesFromAbsentList() = runBlocking {
        val playerIds = addPlayers(6)
        val game = newGame()
        repository.updateAbsentPlayers(game.id, listOf(playerIds[5]))

        repository.deletePlayer(playerIds[5])

        val updated = database.gameDao().getById(game.id)!!
        assertTrue(updated.absentPlayerIds().isEmpty())
    }

    @Test
    fun substitute_swapsPlayerInForPlayerOut() = runBlocking {
        val playerIds = addPlayers(6)
        val onCourt = playerIds.take(5)
        val bench = playerIds[5]
        val game = newGame()
        repository.selectStartingLineup(game.id, onCourt, maxSize = 5)

        repository.substitute(game.id, playerInId = bench, playerOutId = onCourt[0])

        val updated = database.gameDao().getById(game.id)!!
        val newOnCourt = updated.onCourtIds()
        assertEquals(5, newOnCourt.size)
        assertTrue(newOnCourt.contains(bench))
        assertFalse(newOnCourt.contains(onCourt[0]))
    }

    @Test
    fun substitute_isNoOpWhenPlayerOutIsNotOnCourt() = runBlocking {
        val playerIds = addPlayers(6)
        val onCourt = playerIds.take(5)
        val bench = playerIds[5]
        val game = newGame()
        repository.selectStartingLineup(game.id, onCourt, maxSize = 5)

        // bench player is not on court, so this swap request is invalid and should change nothing.
        repository.substitute(game.id, playerInId = onCourt[0], playerOutId = bench)

        val updated = database.gameDao().getById(game.id)!!
        assertEquals(onCourt.toSet(), updated.onCourtIds().toSet())
    }

    @Test
    fun addScore_forPlayer_incrementsTeamScoreOnly() = runBlocking {
        val playerIds = addPlayers(1)
        val game = newGame()

        repository.addScore(game.id, playerIds[0], 2)

        val updated = database.gameDao().getById(game.id)!!
        assertEquals(2, updated.teamScore)
        assertEquals(0, updated.opponentScore)
    }

    @Test
    fun addScore_withNullPlayer_incrementsOpponentScore() = runBlocking {
        val game = newGame()

        repository.addScore(game.id, playerId = null, points = 3)

        val updated = database.gameDao().getById(game.id)!!
        assertEquals(0, updated.teamScore)
        assertEquals(3, updated.opponentScore)
    }

    @Test
    fun addScore_neverDropsTeamScoreBelowZero() = runBlocking {
        val playerIds = addPlayers(1)
        val game = newGame()

        repository.addScore(game.id, playerIds[0], -5)

        val updated = database.gameDao().getById(game.id)!!
        assertEquals(0, updated.teamScore)
    }

    @Test
    fun updateEvent_adjustsTeamScoreByTheDifference() = runBlocking {
        val playerIds = addPlayers(1)
        val game = newGame()
        repository.addScore(game.id, playerIds[0], 2)
        val event = database.gameEventDao().getByGame(game.id).single { it.type == EventType.SCORE.name }

        repository.updateEvent(event.copy(points = 3))

        val updated = database.gameDao().getById(game.id)!!
        assertEquals(3, updated.teamScore)
    }

    @Test
    fun deleteEvent_reversesItsScoreContribution() = runBlocking {
        val playerIds = addPlayers(1)
        val game = newGame()
        repository.addScore(game.id, playerIds[0], 2)
        repository.addScore(game.id, playerIds[0], 3)
        val events = database.gameEventDao().getByGame(game.id).filter { it.type == EventType.SCORE.name }

        repository.deleteEvent(events.first())

        val updated = database.gameDao().getById(game.id)!!
        assertEquals(3, updated.teamScore)
    }

    @Test
    fun advancePeriod_incrementsPeriodAndResetsCourtAndClock() = runBlocking {
        val playerIds = addPlayers(5)
        val game = newGame()
        repository.selectStartingLineup(game.id, playerIds, maxSize = 5)
        repository.setGameTime(game.id, 60_000L)

        repository.advancePeriod(game.id)

        val updated = database.gameDao().getById(game.id)!!
        assertEquals(2, updated.currentPeriod)
        assertEquals(0L, updated.runningTimeMillis)
        assertTrue(updated.onCourtIds().isEmpty())
    }

    @Test
    fun advancePeriod_doesNothingOncePastTheSportsMaxPeriods() = runBlocking {
        val game = newGame()
        // Halves by default (useQuarters = false) -> max 2 periods.
        repository.advancePeriod(game.id)
        assertEquals(2, database.gameDao().getById(game.id)!!.currentPeriod)

        repository.advancePeriod(game.id)

        assertEquals(2, database.gameDao().getById(game.id)!!.currentPeriod)
    }

    @Test
    fun endGame_marksFinishedAndClearsTheCourt() = runBlocking {
        val playerIds = addPlayers(5)
        val game = newGame()
        repository.selectStartingLineup(game.id, playerIds, maxSize = 5)

        repository.endGame(game.id)

        val updated = database.gameDao().getById(game.id)!!
        assertTrue(updated.isFinished)
        assertTrue(updated.onCourtIds().isEmpty())
    }

    @Test
    fun deleteGame_alsoDeletesItsEvents() = runBlocking {
        val playerIds = addPlayers(1)
        val game = newGame()
        repository.addScore(game.id, playerIds[0], 2)

        repository.deleteGame(game.id)

        assertTrue(database.gameEventDao().getByGame(game.id).isEmpty())
    }

    @Test
    fun clearSelectedData_onlyClearsRequestedCategories() = runBlocking {
        addPlayers(3)
        newGame()

        repository.clearSelectedData(players = false, games = true, events = true)

        assertEquals(3, database.playerDao().getAll().size)
        assertTrue(database.gameDao().getAll().isEmpty())
    }
}
