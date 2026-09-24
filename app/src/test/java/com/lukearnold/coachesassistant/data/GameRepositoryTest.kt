package com.lukearnold.coachesassistant.data

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
 * Exercises [GameRepository]'s transactional behavior against a real (in-memory) Room
 * database. Robolectric supplies a working SQLite implementation so this runs as a fast JVM unit
 * test rather than needing an emulator-backed androidTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: GameRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = GameRepository(database)
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

        assertEquals(4, repository.getLineup(game.id).onCourtIds.size)
        assertFalse(repository.getLineup(game.id).onCourtIds.contains(playerIds[2]))
    }

    @Test
    fun deletePlayer_nullsTheirIdOnPastEventsInsteadOfLeavingADanglingReference() = runBlocking {
        val playerIds = addPlayers(2)
        val game = newGame()
        repository.selectStartingLineup(game.id, playerIds, maxSize = 2)
        repository.addScore(game.id, playerIds[0], 2)
        repository.substitute(game.id, playerInId = playerIds[1], playerOutId = playerIds[0])
        val eventsBefore = database.gameEventDao().getByGame(game.id)
        // Sanity check: the events we're about to verify actually reference the deleted player.
        assertTrue(eventsBefore.any { it.playerId == playerIds[0] || it.relatedPlayerId == playerIds[0] })

        repository.deletePlayer(playerIds[0])

        val eventsAfter = database.gameEventDao().getByGame(game.id)
        assertTrue(eventsAfter.none { it.playerId == playerIds[0] || it.relatedPlayerId == playerIds[0] })
        // The events themselves survive as history; only the dangling reference is cleared.
        assertEquals(eventsBefore.size, eventsAfter.size)
    }

    @Test
    fun clearSelectedData_playersOnly_alsoFreesEveryGamesOnCourtAndAbsentSlots() = runBlocking {
        val playerIds = addPlayers(6)
        val game = newGame()
        repository.selectStartingLineup(game.id, playerIds.take(5), maxSize = 5)
        repository.updateAbsentPlayers(game.id, listOf(playerIds[5]))

        repository.clearSelectedData(Sport.BASKETBALL, players = true, games = false, events = false)

        assertTrue(repository.getLineup(game.id).onCourtIds.isEmpty())
        assertTrue(repository.getLineup(game.id).absentIds.isEmpty())
    }

    @Test
    fun deletePlayer_alsoRemovesFromAbsentList() = runBlocking {
        val playerIds = addPlayers(6)
        val game = newGame()
        repository.updateAbsentPlayers(game.id, listOf(playerIds[5]))

        repository.deletePlayer(playerIds[5])

        assertTrue(repository.getLineup(game.id).absentIds.isEmpty())
    }

    @Test
    fun substitute_swapsPlayerInForPlayerOut() = runBlocking {
        val playerIds = addPlayers(6)
        val onCourt = playerIds.take(5)
        val bench = playerIds[5]
        val game = newGame()
        repository.selectStartingLineup(game.id, onCourt, maxSize = 5)

        repository.substitute(game.id, playerInId = bench, playerOutId = onCourt[0])

        val newOnCourt = repository.getLineup(game.id).onCourtIds
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

        assertEquals(onCourt.toSet(), repository.getLineup(game.id).onCourtIds.toSet())
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
        // And no event is logged for it, so the event log still adds up to the score.
        assertTrue(database.gameEventDao().getByGame(game.id).isEmpty())
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
        assertTrue(repository.getLineup(game.id).onCourtIds.isEmpty())
    }

    @Test
    fun advancePeriod_doesNothingOncePastTheSportsMaxPeriods() = runBlocking {
        val game = newGame()
        // Halves by default (periodCount = 2).
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
        assertTrue(repository.getLineup(game.id).onCourtIds.isEmpty())
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

        repository.clearSelectedData(Sport.BASKETBALL, players = false, games = true, events = true)

        assertEquals(3, database.playerDao().getAll().size)
        assertTrue(database.gameDao().getAll().isEmpty())
    }

    /** Starts [game]'s clock [millisAgo] in the past, as if it had been running unattended. */
    private suspend fun startClockAgo(game: GameEntity, millisAgo: Long) {
        database.gameDao().update(game.copy(timerLastStartedAt = System.currentTimeMillis() - millisAgo))
    }

    @Test
    fun toggleTimer_stoppingAfterThePeriodEnds_capsTimeAtThePeriodLength() = runBlocking {
        val game = newGame() // 10-minute periods by default
        startClockAgo(game, millisAgo = 15 * 60_000L)

        repository.toggleTimer(game.id)

        val updated = database.gameDao().getById(game.id)!!
        assertEquals(null, updated.timerLastStartedAt)
        assertEquals(game.periodDurationMillis, updated.runningTimeMillis)
    }

    @Test
    fun toggleTimer_doesNotStartOnceThePeriodsTimeIsUp() = runBlocking {
        val game = newGame()
        repository.setGameTime(game.id, game.periodDurationMillis)

        repository.toggleTimer(game.id)

        assertEquals(null, database.gameDao().getById(game.id)!!.timerLastStartedAt)
    }

    @Test
    fun stopTimer_stopsARunningClockButNeverStartsOne() = runBlocking {
        val game = newGame()

        repository.stopTimer(game.id)
        assertEquals(null, database.gameDao().getById(game.id)!!.timerLastStartedAt)

        startClockAgo(game, millisAgo = 60_000L)
        repository.stopTimer(game.id)

        val updated = database.gameDao().getById(game.id)!!
        assertEquals(null, updated.timerLastStartedAt)
        assertTrue(updated.runningTimeMillis in 60_000L until game.periodDurationMillis)
    }

    @Test
    fun setGameTime_isClampedToThePeriodLength() = runBlocking {
        val game = newGame()

        repository.setGameTime(game.id, game.periodDurationMillis + 5 * 60_000L)

        assertEquals(game.periodDurationMillis, database.gameDao().getById(game.id)!!.runningTimeMillis)
    }

    @Test
    fun eventsLoggedAfterThePeriodEnds_areStampedAtThePeriodLength() = runBlocking {
        val playerIds = addPlayers(1)
        val game = newGame()
        startClockAgo(game, millisAgo = 15 * 60_000L)

        repository.addScore(game.id, playerIds[0], 2)

        val event = database.gameEventDao().getByGame(game.id).single()
        assertEquals(game.periodDurationMillis, event.gameTimeMillis)
    }

    @Test
    fun advancePeriod_allowsAThirdPeriodForThreePeriodGames() = runBlocking {
        val gameId = repository.createGame(Sport.ICE_HOCKEY, "Faceoff", "Rivals", periodCount = 3, onCourtSize = 6)

        repeat(3) { repository.advancePeriod(gameId) }

        assertEquals(3, database.gameDao().getById(gameId)!!.currentPeriod)
    }

    @Test
    fun deleteEvent_forAnMvpVote_removesItFromTheTally() = runBlocking {
        val playerIds = addPlayers(1)
        val game = newGame()
        repository.addMvpVote(game.id, playerIds[0])
        repository.addMvpVote(game.id, playerIds[0])
        assertEquals(mapOf(playerIds[0] to 2), MvpVotes.parse(database.gameDao().getById(game.id)!!.mvpVotesJson))

        repository.deleteEvent(database.gameEventDao().getByGame(game.id).first())

        assertEquals(mapOf(playerIds[0] to 1), MvpVotes.parse(database.gameDao().getById(game.id)!!.mvpVotesJson))
    }

    @Test
    fun clearSelectedData_eventsOnly_alsoResetsScoresAndMvpVotes() = runBlocking {
        val playerIds = addPlayers(1)
        val game = newGame()
        repository.addScore(game.id, playerIds[0], 2)
        repository.addScore(game.id, playerId = null, points = 3)
        repository.addMvpVote(game.id, playerIds[0])

        repository.clearSelectedData(Sport.BASKETBALL, players = false, games = false, events = true)

        val updated = database.gameDao().getById(game.id)!!
        assertEquals(0, updated.teamScore)
        assertEquals(0, updated.opponentScore)
        assertTrue(MvpVotes.parse(updated.mvpVotesJson).isEmpty())
    }

    @Test
    fun clearSelectedData_leavesOtherSportsDataAlone() = runBlocking {
        val basketballPlayer = repository.addPlayer(Sport.BASKETBALL, "Hoops", 1)
        val netballPlayer = repository.addPlayer(Sport.NETBALL, "Shooter", 2)
        val basketballGame = repository.createGame(Sport.BASKETBALL, "B", "Rivals")
        val netballGame = repository.createGame(Sport.NETBALL, "N", "Rivals", onCourtSize = 7)
        repository.addScore(basketballGame, basketballPlayer, 2)
        repository.addScore(netballGame, netballPlayer, 1)

        repository.clearSelectedData(Sport.BASKETBALL, players = true, games = false, events = true)

        assertEquals(listOf(netballPlayer), database.playerDao().getAll().map { it.id })
        assertTrue(database.gameEventDao().getByGame(basketballGame).isEmpty())
        assertEquals(1, database.gameEventDao().getByGame(netballGame).size)
        assertEquals(1, database.gameDao().getById(netballGame)!!.teamScore)

        repository.clearSelectedData(Sport.BASKETBALL, players = false, games = true, events = false)

        assertEquals(listOf(netballGame), database.gameDao().getAll().map { it.id })
    }

    @Test
    fun advancePeriod_clearsTheCourtButKeepsAttendance() = runBlocking {
        val playerIds = addPlayers(6)
        val game = newGame()
        repository.selectStartingLineup(game.id, playerIds.take(5), maxSize = 5)
        repository.updateAbsentPlayers(game.id, listOf(playerIds[5]))

        repository.advancePeriod(game.id)

        val lineup = repository.getLineup(game.id)
        assertTrue(lineup.onCourtIds.isEmpty())
        assertEquals(setOf(playerIds[5]), lineup.absentIds)
    }

    @Test
    fun updateAbsentPlayers_leavesTheCourtAlone() = runBlocking {
        val playerIds = addPlayers(6)
        val game = newGame()
        repository.selectStartingLineup(game.id, playerIds.take(5), maxSize = 5)

        repository.updateAbsentPlayers(game.id, listOf(playerIds[5]))
        repository.updateAbsentPlayers(game.id, emptyList())

        val lineup = repository.getLineup(game.id)
        assertEquals(playerIds.take(5).toSet(), lineup.onCourtIds)
        assertTrue(lineup.absentIds.isEmpty())
    }

    @Test
    fun deleteGame_alsoDeletesItsLineup() = runBlocking {
        val playerIds = addPlayers(5)
        val game = newGame()
        repository.selectStartingLineup(game.id, playerIds, maxSize = 5)

        repository.deleteGame(game.id)

        assertTrue(database.gamePlayerDao().getByGame(game.id).isEmpty())
    }
}
