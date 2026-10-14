package com.lonrad.coachesassistant.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [CricketRepository] against a real in-memory database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CricketRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var games: GameRepository
    private lateinit var cricket: CricketRepository
    private var sam = 0L
    private var kit = 0L
    private var gameId = 0L

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        games = GameRepository(database)
        cricket = CricketRepository(database)
        sam = games.addPlayer(Sport.CRICKET, "Sam", 4)
        kit = games.addPlayer(Sport.CRICKET, "Kit", 7)
        gameId = games.createGame(Sport.CRICKET, "Round 1", "Hawks", periodCount = 1, periodDurationMillis = 0L, onCourtSize = 11)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun game() = games.getGame(gameId)!!
    private suspend fun cards() = cricket.observeScorecards(gameId).first()
    private suspend fun field() = cricket.observeField(gameId).first().associate { it.playerId to it.position }

    @Test
    fun scorecards_setTheTeamsRunsAndWickets_includingExtras() = runBlocking {
        cricket.saveScorecard(CricketScorecardEntity(gameId = gameId, playerId = sam, dismissal = "CAUGHT", runs = 32))
        cricket.saveScorecard(CricketScorecardEntity(gameId = gameId, playerId = kit, dismissal = "NOT_OUT", runs = 10))
        cricket.updateInnings(gameId, extras = 8, opponentRuns = 45, opponentWickets = 7)

        val game = game()
        assertEquals(50, game.teamScore)
        assertEquals(1, game.teamWickets)
        assertEquals(45, game.opponentScore)
        assertEquals(7, game.opponentWickets)
    }

    @Test
    fun savingAgain_replacesThePlayersCard_andAnEmptyCardRemovesIt() = runBlocking {
        cricket.saveScorecard(CricketScorecardEntity(gameId = gameId, playerId = sam, dismissal = "BOWLED", runs = 5))
        cricket.saveScorecard(CricketScorecardEntity(gameId = gameId, playerId = sam, dismissal = "CAUGHT", runs = 12))
        assertEquals(listOf(12), cards().map { it.runs })
        assertEquals(12, game().teamScore)

        cricket.saveScorecard(CricketScorecardEntity(gameId = gameId, playerId = sam))
        assertTrue(cards().isEmpty())
        assertEquals(0, game().teamScore)
    }

    @Test
    fun placingAFielder_movesThemAndWhoeverWasThere() = runBlocking {
        cricket.setFieldPosition(gameId, "POINT", sam)
        cricket.setFieldPosition(gameId, "COVER", kit)
        // Kit to point: Sam is moved out of it, and Kit leaves cover.
        cricket.setFieldPosition(gameId, "POINT", kit)
        assertEquals(mapOf(kit to "POINT"), field())

        cricket.setFieldPosition(gameId, "POINT", null)
        assertTrue(field().isEmpty())
    }

    @Test
    fun copyLatestField_takesTheMostRecentOtherGamesPlan() = runBlocking {
        cricket.setFieldPosition(gameId, "FIRST_SLIP", sam)
        val next = games.createGame(Sport.CRICKET, "Round 2", "Owls", periodCount = 1, periodDurationMillis = 0L, onCourtSize = 11)

        assertTrue(cricket.copyLatestField(next, Sport.CRICKET))
        assertEquals(listOf(sam to "FIRST_SLIP"), cricket.observeField(next).first().map { it.playerId to it.position })

        val other = games.createGame(Sport.SOCCER, "Soccer", "", periodCount = 2, periodDurationMillis = 1L, onCourtSize = 11)
        assertFalse(cricket.copyLatestField(other, Sport.SOCCER))
    }

    @Test
    fun deletingAPlayer_keepsTheirRunsInTheTotal_butTakesThemOffTheField() = runBlocking {
        cricket.saveScorecard(CricketScorecardEntity(gameId = gameId, playerId = sam, dismissal = "CAUGHT", runs = 20))
        cricket.setFieldPosition(gameId, "GULLY", sam)

        games.deletePlayer(sam)

        assertNull(cards().single().playerId)
        assertEquals(20, game().teamScore)
        assertTrue(field().isEmpty())
    }

    @Test
    fun clearingEvents_forCricket_clearsScorecardsFieldAndScores() = runBlocking {
        cricket.saveScorecard(CricketScorecardEntity(gameId = gameId, playerId = sam, dismissal = "CAUGHT", runs = 20))
        cricket.updateInnings(gameId, extras = 3, opponentRuns = 30, opponentWickets = 4)
        cricket.setFieldPosition(gameId, "GULLY", sam)

        games.clearSelectedData(Sport.CRICKET, players = false, games = false, events = true)

        assertTrue(cards().isEmpty())
        assertTrue(field().isEmpty())
        val game = game()
        assertEquals(listOf(0, 0, 0, 0, 0), listOf(game.teamScore, game.teamWickets, game.extras, game.opponentScore, game.opponentWickets))
    }
}
