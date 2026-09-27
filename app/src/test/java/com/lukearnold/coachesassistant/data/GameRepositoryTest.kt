package com.lukearnold.coachesassistant.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
        repository.addTeamScore(game.id, playerIds[0], 2)
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
    fun addTeamScore_forPlayer_incrementsTeamScoreOnly() = runBlocking {
        val playerIds = addPlayers(1)
        val game = newGame()

        repository.addTeamScore(game.id, playerIds[0], 2)

        val updated = database.gameDao().getById(game.id)!!
        assertEquals(2, updated.teamScore)
        assertEquals(0, updated.opponentScore)
    }

    @Test
    fun addOpponentScore_incrementsOpponentScoreOnly() = runBlocking {
        val game = newGame()

        repository.addOpponentScore(game.id, points = 3)

        val updated = database.gameDao().getById(game.id)!!
        assertEquals(0, updated.teamScore)
        assertEquals(3, updated.opponentScore)
    }

    @Test
    fun addTeamScore_neverDropsTeamScoreBelowZero() = runBlocking {
        val playerIds = addPlayers(1)
        val game = newGame()

        repository.addTeamScore(game.id, playerIds[0], -5)

        val updated = database.gameDao().getById(game.id)!!
        assertEquals(0, updated.teamScore)
        // And no event is logged for it, so the event log still adds up to the score.
        assertTrue(database.gameEventDao().getByGame(game.id).isEmpty())
    }

    @Test
    fun updateEvent_adjustsTeamScoreByTheDifference() = runBlocking {
        val playerIds = addPlayers(1)
        val game = newGame()
        repository.addTeamScore(game.id, playerIds[0], 2)
        val event = database.gameEventDao().getByGame(game.id).single { it.type == EventType.SCORE.name }

        repository.updateEvent(event.copy(points = 3))

        val updated = database.gameDao().getById(game.id)!!
        assertEquals(3, updated.teamScore)
    }

    @Test
    fun deleteEvent_reversesItsScoreContribution() = runBlocking {
        val playerIds = addPlayers(1)
        val game = newGame()
        repository.addTeamScore(game.id, playerIds[0], 2)
        repository.addTeamScore(game.id, playerIds[0], 3)
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
        repository.addTeamScore(game.id, playerIds[0], 2)

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

        repository.addTeamScore(game.id, playerIds[0], 2)

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
        repository.addTeamScore(game.id, playerIds[0], 2)
        repository.addOpponentScore(game.id, points = 3)
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
        repository.addTeamScore(basketballGame, basketballPlayer, 2)
        repository.addTeamScore(netballGame, netballPlayer, 1)

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

    @Test
    fun addTeamScore_withNoPlayer_adjustsOnlyTheTeamTotal() = runBlocking {
        val playerIds = addPlayers(5)
        val game = newGame()
        repository.selectStartingLineup(game.id, playerIds, maxSize = 5)
        repository.addTeamScore(game.id, playerIds[0], 3)
        repository.addOpponentScore(game.id, 2)

        // The "Us" -1 button: a team-level correction, not a debit from whoever is first on court.
        repository.addTeamScore(game.id, playerId = null, points = -1)

        val updated = database.gameDao().getById(game.id)!!
        assertEquals(2, updated.teamScore)
        assertEquals(2, updated.opponentScore)
        val adjustment = database.gameEventDao().getByGame(game.id).last()
        assertEquals(EventType.SCORE.name, adjustment.type)
        assertEquals(null, adjustment.playerId)
        assertEquals(TEAM_ADJUSTMENT_NOTE, adjustment.note)
    }

    @Test
    fun addTeamScore_withNoPlayerAndAnEmptyCourt_neverTouchesTheOpponent() = runBlocking {
        val playerIds = addPlayers(1)
        val game = newGame()
        repository.addTeamScore(game.id, playerIds[0], 2)
        repository.addOpponentScore(game.id, 4)

        repository.addTeamScore(game.id, playerId = null, points = -1)

        val updated = database.gameDao().getById(game.id)!!
        assertEquals(1, updated.teamScore)
        assertEquals(4, updated.opponentScore)
    }

    @Test
    fun finishedGameQueries_onlyReturnTheSportsFinishedGames() = runBlocking {
        val hoops = repository.addPlayer(Sport.BASKETBALL, "Hoops", 1)
        val shooter = repository.addPlayer(Sport.NETBALL, "Shooter", 2)
        val finished = repository.createGame(Sport.BASKETBALL, "Done", "Rivals")
        val live = repository.createGame(Sport.BASKETBALL, "Live", "Rivals")
        val otherSport = repository.createGame(Sport.NETBALL, "Netball", "Rivals", onCourtSize = 7)
        listOf(finished to hoops, live to hoops, otherSport to shooter).forEach { (gameId, playerId) ->
            repository.addTeamScore(gameId, playerId, 2)
            repository.updateAbsentPlayers(gameId, listOf(playerId))
        }
        repository.endGame(finished)
        repository.endGame(otherSport)

        val events = repository.observeFinishedGameEvents(Sport.BASKETBALL).first()
        val lineups = repository.observeFinishedGameLineups(Sport.BASKETBALL).first()

        assertEquals(setOf(finished), events.map { it.gameId }.toSet())
        assertEquals(setOf(finished), lineups.keys)
        assertEquals(setOf(hoops), lineups.getValue(finished).absentIds)
    }

    @Test
    fun undoEvents_reversesAScoreAndRestoresTheTotal() = runBlocking {
        val playerIds = addPlayers(1)
        val game = newGame()
        repository.addTeamScore(game.id, playerIds[0], 2)
        val mistake = repository.addTeamScore(game.id, playerIds[0], 3)!!

        assertTrue(repository.undoEvents(listOf(mistake)))

        assertEquals(2, database.gameDao().getById(game.id)!!.teamScore)
        assertEquals(1, database.gameEventDao().getByGame(game.id).size)
    }

    @Test
    fun undoEvents_reversesAnMvpVote() = runBlocking {
        val playerIds = addPlayers(1)
        val game = newGame()
        val vote = repository.addMvpVote(game.id, playerIds[0])!!

        assertTrue(repository.undoEvents(listOf(vote)))

        assertTrue(MvpVotes.parse(database.gameDao().getById(game.id)!!.mvpVotesJson).isEmpty())
    }

    @Test
    fun undoEvents_swapsASubstitutionBack() = runBlocking {
        val playerIds = addPlayers(6)
        val game = newGame()
        repository.selectStartingLineup(game.id, playerIds.take(5), maxSize = 5)
        val sub = repository.substitute(game.id, playerInId = playerIds[5], playerOutId = playerIds[0])!!

        assertTrue(repository.undoEvents(listOf(sub)))

        assertEquals(playerIds.take(5).toSet(), repository.getLineup(game.id).onCourtIds)
        assertTrue(database.gameEventDao().getByGame(game.id).none { it.type == EventType.SUBSTITUTION.name })
    }

    @Test
    fun undoEvents_refusesASubstitutionWhenTheLineupHasMovedOn() = runBlocking {
        val playerIds = addPlayers(7)
        val game = newGame()
        repository.selectStartingLineup(game.id, playerIds.take(5), maxSize = 5)
        val sub = repository.substitute(game.id, playerInId = playerIds[5], playerOutId = playerIds[0])!!
        // The player who came on has since gone off again.
        repository.substitute(game.id, playerInId = playerIds[6], playerOutId = playerIds[5])
        val lineupBefore = repository.getLineup(game.id)

        assertFalse(repository.undoEvents(listOf(sub)))

        assertEquals(lineupBefore, repository.getLineup(game.id))
        assertEquals(3, database.gameEventDao().getByGame(game.id).size) // lineup + both subs kept
    }

    @Test
    fun undoEvents_isAllOrNothingForABatch() = runBlocking {
        val playerIds = addPlayers(8)
        val game = newGame()
        repository.selectStartingLineup(game.id, playerIds.take(5), maxSize = 5)
        val first = repository.substitute(game.id, playerInId = playerIds[5], playerOutId = playerIds[0])!!
        val second = repository.substitute(game.id, playerInId = playerIds[6], playerOutId = playerIds[1])!!
        repository.substitute(game.id, playerInId = playerIds[7], playerOutId = playerIds[6]) // second's player moves on
        val lineupBefore = repository.getLineup(game.id)

        assertFalse(repository.undoEvents(listOf(first, second)))

        // The first swap could have been undone on its own, but the batch was rolled back as a whole.
        assertEquals(lineupBefore, repository.getLineup(game.id))
        assertEquals(4, database.gameEventDao().getByGame(game.id).size)
    }

    @Test
    fun undoEvents_forAnEventThatNoLongerExists_returnsFalse() = runBlocking {
        val playerIds = addPlayers(1)
        val game = newGame()
        val foul = repository.addFoul(game.id, playerIds[0])!!
        repository.deleteEvent(database.gameEventDao().getById(foul)!!)

        assertFalse(repository.undoEvents(listOf(foul)))
    }

    @Test
    fun actionsThatRecordNothing_returnNoEventId() = runBlocking {
        val playerIds = addPlayers(6)
        val game = newGame()
        repository.selectStartingLineup(game.id, playerIds.take(5), maxSize = 5)

        assertEquals(null, repository.substitute(game.id, playerInId = playerIds[0], playerOutId = playerIds[5]))
        assertEquals(null, repository.addOpponentScore(game.id, -1)) // would go below zero
        assertEquals(null, repository.addFoul(game.id, playerIds[0], adjustment = -1)) // likewise
    }

    @Test
    fun players_areOrderedByNumber_withNumberlessPlayersLast() = runBlocking {
        repository.addPlayer(Sport.BASKETBALL, "zoe", null)
        repository.addPlayer(Sport.BASKETBALL, "Kit", 95)
        repository.addPlayer(Sport.BASKETBALL, "Amy", null)
        repository.addPlayer(Sport.BASKETBALL, "Tommy", 2)

        assertEquals(
            listOf("Tommy", "Kit", "Amy", "zoe"),
            repository.playersForSport(Sport.BASKETBALL).first().map { it.name },
        )
    }

    @Test
    fun addFoul_removesFoulsDownToZeroButNoFurther() = runBlocking {
        val playerIds = addPlayers(2)
        val game = newGame()
        repository.addFoul(game.id, playerIds[0])
        repository.addFoul(game.id, playerIds[1])

        assertNotNull(repository.addFoul(game.id, playerIds[0], adjustment = -1))
        assertEquals(null, repository.addFoul(game.id, playerIds[0], adjustment = -1))
        // The other player's foul doesn't count toward this one's.
        assertEquals(3, database.gameEventDao().getByGame(game.id).size)
    }

    @Test
    fun setGameTime_refusesToGoEarlierThanAnEventThisPeriod_butMovesForward() = runBlocking {
        val playerIds = addPlayers(6)
        val game = newGame() // 10-minute periods
        repository.selectStartingLineup(game.id, playerIds.take(5), maxSize = 5)
        repository.setGameTime(game.id, 300_000L)
        repository.substitute(game.id, playerInId = playerIds[5], playerOutId = playerIds[0]) // at 5:00

        assertEquals(300_000L, repository.setGameTime(game.id, 120_000L)) // refused: earliest allowed is 5:00
        assertEquals(300_000L, database.gameDao().getById(game.id)!!.runningTimeMillis) // clock untouched

        assertEquals(null, repository.setGameTime(game.id, 420_000L))
        assertEquals(420_000L, database.gameDao().getById(game.id)!!.runningTimeMillis)
    }

    @Test
    fun resetTimer_isAllowedWhileNothingButTheLineupHasBeenRecorded() = runBlocking {
        val playerIds = addPlayers(5)
        val game = newGame()
        repository.selectStartingLineup(game.id, playerIds, maxSize = 5) // recorded at 0:00
        repository.setGameTime(game.id, 90_000L)

        assertEquals(null, repository.resetTimer(game.id))
        assertEquals(0L, database.gameDao().getById(game.id)!!.runningTimeMillis)
    }

    @Test
    fun resetTimer_isRefusedOnceSomethingWasRecordedLaterInThePeriod() = runBlocking {
        val playerIds = addPlayers(5)
        val game = newGame()
        repository.selectStartingLineup(game.id, playerIds, maxSize = 5)
        repository.setGameTime(game.id, 90_000L)
        repository.addTeamScore(game.id, playerIds[0], 2) // at 1:30

        assertEquals(90_000L, repository.resetTimer(game.id))
        assertEquals(90_000L, database.gameDao().getById(game.id)!!.runningTimeMillis)
    }
}
