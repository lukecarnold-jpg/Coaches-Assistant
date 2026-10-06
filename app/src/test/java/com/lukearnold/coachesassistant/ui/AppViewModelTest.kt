package com.lukearnold.coachesassistant.ui

import android.app.Application
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lukearnold.coachesassistant.R
import com.lukearnold.coachesassistant.data.AppDatabase
import com.lukearnold.coachesassistant.data.GameRepository
import com.lukearnold.coachesassistant.data.Sport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [AppViewModel] against a real in-memory database: the checks it makes before changing a game,
 * and that a failed write is reported rather than crashing the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppViewModelTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var database: AppDatabase
    private lateinit var repository: GameRepository
    private lateinit var viewModel: AppViewModel
    private val observers = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val messages = mutableListOf<UiText>()
    private var playerIds = emptyList<Long>()
    private var gameId = 0L

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = GameRepository(database)
        runBlocking {
            playerIds = (1..7).map { repository.addPlayer(Sport.BASKETBALL, "Player $it", it) }
            gameId = repository.createGame(Sport.BASKETBALL, "Round 1", "Opponents")
        }
        viewModel = AppViewModel(application, Sport.BASKETBALL, repository, GameDayMode.get(application))
        // The screen's subscriptions, which keep the view model's state up to date.
        observers.launch { viewModel.messages.collect { messages += it.text } }
        observers.launch { viewModel.players.collect {} }
        observers.launch { viewModel.selectedGame.collect {} }
        observers.launch { viewModel.selectedGameLineup.collect {} }
        awaitUntil { viewModel.selectedGame.value?.id == gameId && viewModel.players.value.size == playerIds.size }
    }

    @After
    fun tearDown() {
        observers.cancel()
        database.close()
    }

    /** Runs the main thread until [condition] holds, giving Room's background queries time to answer. */
    private fun awaitUntil(condition: () -> Boolean) {
        repeat(200) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("Timed out waiting")
    }

    @Test
    fun startingLineup_ofTheWrongSize_isRefusedWithTheReason() {
        viewModel.setStartingLineup(playerIds.take(3))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(pluralText(R.plurals.msg_lineup_needs_players, 5)), messages)
        assertTrue(runBlocking { repository.getLineup(gameId) }.onCourtIds.isEmpty())
    }

    @Test
    fun startingLineup_ofTheRightSize_isSaved() {
        viewModel.setStartingLineup(playerIds.take(5))
        awaitUntil { messages.isNotEmpty() }

        assertEquals(listOf(uiText(R.string.msg_lineup_saved)), messages)
        assertEquals(playerIds.take(5).toSet(), runBlocking { repository.getLineup(gameId) }.onCourtIds.toSet())
    }

    @Test
    fun aWriteThatFails_isReportedInsteadOfCrashing() {
        // A foul for a player who doesn't exist breaks the events table's foreign key.
        viewModel.addFoul(playerId = 9_999)
        awaitUntil { messages.isNotEmpty() }

        assertEquals(listOf(uiText(R.string.msg_action_failed)), messages)
    }
}
