package com.lonrad.coachesassistant.ui

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameDayModeTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val notificationManager = app.getSystemService(NotificationManager::class.java)
    private val prefs = app.getSharedPreferences("game_day_test", Context.MODE_PRIVATE)

    private fun gameDay() = GameDayMode(app, prefs)

    @Before
    fun grantAccess() {
        shadowOf(notificationManager).setNotificationPolicyAccessGranted(true)
    }

    @Test
    fun withoutDoNotDisturbAccess_itDoesNothing() {
        shadowOf(notificationManager).setNotificationPolicyAccessGranted(false)
        val gameDay = gameDay()

        assertEquals(GameDayMode.StartResult.NO_ACCESS, gameDay.start(gameId = 5))
        assertEquals(null, gameDay.activeGameId.value)
        shadowOf(notificationManager).setNotificationPolicyAccessGranted(true) // to look at the rules
        assertTrue(notificationManager.automaticZenRules.isEmpty())
    }

    @Test
    fun start_addsTheAppsOwnPriorityOnlyRule_andRemembersTheGame() {
        assertEquals(GameDayMode.StartResult.STARTED, gameDay().start(gameId = 5))

        val rule = notificationManager.automaticZenRules.values.single()
        assertEquals("Game day", rule.name)
        assertEquals(NotificationManager.INTERRUPTION_FILTER_PRIORITY, rule.interruptionFilter)
        // A new instance (e.g. after the app restarts) still knows it's on, and for which game.
        assertEquals(5L, gameDay().activeGameId.value)
    }

    @Test
    fun startingAgain_reusesTheSameRule() {
        gameDay().start(gameId = 5)
        gameDay().stop()
        gameDay().start(gameId = 6)

        assertEquals(1, notificationManager.automaticZenRules.size)
    }

    @Test
    fun itStaysOnWhileItsGameIsInProgress_andStopsOnceItIsnt() {
        val gameDay = gameDay()
        gameDay.start(gameId = 5)

        gameDay.stopUnlessPlaying(listOf(4, 5))
        assertEquals(5L, gameDay.activeGameId.value)

        gameDay.stopUnlessPlaying(listOf(4)) // ended, deleted, or cleared
        assertEquals(null, gameDay.activeGameId.value)
        assertEquals(null, gameDay().activeGameId.value)
    }

    @Test
    fun accessRevokedWhileOn_stillTurnsOffCleanly() {
        val gameDay = gameDay()
        gameDay.start(gameId = 5)
        shadowOf(notificationManager).setNotificationPolicyAccessGranted(false) // the user revoked it

        assertTrue(gameDay.stop())
        assertEquals(null, gameDay.activeGameId.value)
    }
}
