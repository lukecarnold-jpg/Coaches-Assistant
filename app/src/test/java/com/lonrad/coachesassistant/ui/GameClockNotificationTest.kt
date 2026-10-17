package com.lonrad.coachesassistant.ui

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.lonrad.coachesassistant.data.GameEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameClockNotificationTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val notificationManager = app.getSystemService(NotificationManager::class.java)
    private val now = 1_000_000_000L

    // Q2 of a 10:00-quarter game, 3:00 played, started 1:00 ago (so 4:00 played now, 6:00 left).
    private val running = GameEntity(
        id = 3, title = "Rnd 3", opponent = "Rivals", createdAt = 0L, teamScore = 4, opponentScore = 2,
        periodCount = 4, currentPeriod = 2, periodDurationMillis = 600_000L,
        runningTimeMillis = 180_000L, timerLastStartedAt = now - 60_000L,
    )

    @Before
    fun allowNotifications() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        GameClockNotification.setEnabled(app, true)
    }

    private fun shown(): List<Notification> = shadowOf(notificationManager).allNotifications

    @Test
    fun aRunningClock_showsACountdownToTheEndOfThePeriod() {
        GameClockNotification.sync(app, listOf(running), now)

        val notification = shown().single()
        assertEquals(now + 360_000L, notification.`when`)
        assertTrue(notification.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals("Rnd 3 · Q2", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Us 4 – 2 Rivals", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals("Stop clock", notification.actions.single().title.toString())
    }

    @Test
    fun onTheLockScreen_onlyTheClockShows() {
        GameClockNotification.sync(app, listOf(running), now)

        val notification = shown().single()
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        val lockScreen = notification.publicVersion!!
        assertEquals("Game clock", lockScreen.extras.getString(Notification.EXTRA_TITLE))
        assertNull(lockScreen.extras.getCharSequence(Notification.EXTRA_TEXT))
        assertEquals(now + 360_000L, lockScreen.`when`)
        assertTrue(lockScreen.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
    }

    @Test
    fun itGoesAwayWhenTheClockStops_orTheGameEnds() {
        GameClockNotification.sync(app, listOf(running), now)
        GameClockNotification.sync(app, listOf(running.copy(timerLastStartedAt = null)), now)
        assertTrue(shown().isEmpty())

        GameClockNotification.sync(app, listOf(running), now)
        GameClockNotification.sync(app, listOf(running.copy(isFinished = true)), now)
        assertTrue(shown().isEmpty())
    }

    @Test
    fun nothingIsShown_whenTheSettingIsOff() {
        GameClockNotification.setEnabled(app, false)
        GameClockNotification.sync(app, listOf(running), now)
        assertTrue(shown().isEmpty())
    }

    @Test
    fun turningTheSettingOff_removesWhatsShowing() {
        GameClockNotification.sync(app, listOf(running), now)
        GameClockNotification.setEnabled(app, false)
        GameClockNotification.sync(app, listOf(running), now)
        assertTrue(shown().isEmpty())
    }
}
