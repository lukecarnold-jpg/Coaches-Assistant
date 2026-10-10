package com.lukearnold.coachesassistant.ui

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.lukearnold.coachesassistant.data.AppDatabase
import com.lukearnold.coachesassistant.data.GameRepository
import com.lukearnold.coachesassistant.data.Sport
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** [GameSyncViewModel] following every sport's games, against a real in-memory database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameSyncViewModelTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var database: AppDatabase
    private lateinit var repository: GameRepository
    private lateinit var gameDay: GameDayMode

    @Before
    fun setUp() {
        shadowOf(application.getSystemService(NotificationManager::class.java)).setNotificationPolicyAccessGranted(true)
        database = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = GameRepository(database)
        val prefs = application.getSharedPreferences("game_sync_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        gameDay = GameDayMode(application, prefs)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun awaitUntil(condition: () -> Boolean) {
        repeat(200) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("Timed out waiting")
    }

    @Test
    fun deletingTheGameDayGame_turnsGameDayModeOff_inWhicheverSport() {
        val soccerGame = runBlocking { repository.createGame(Sport.SOCCER, "Round 1", "Rovers") }
        val basketballGame = runBlocking { repository.createGame(Sport.BASKETBALL, "Round 1", "Hawks") }
        gameDay.start(soccerGame)
        GameSyncViewModel(application, repository, gameDay)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(soccerGame, gameDay.activeGameId.value) // still being played

        runBlocking { repository.deleteGame(basketballGame) }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(soccerGame, gameDay.activeGameId.value) // another game going doesn't matter

        runBlocking { repository.deleteGame(soccerGame) }
        awaitUntil { gameDay.activeGameId.value == null }
    }
}
