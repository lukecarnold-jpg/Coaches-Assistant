package com.lukearnold.coachesassistant.ui

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.lukearnold.coachesassistant.data.PlayerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubRemindersTest {

    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun stat(id: Long, name: String, number: Int, onCourt: Boolean) = PlayerGameStat(
        player = PlayerEntity(id = id, name = name, jerseyNumber = number),
        points = 0, fouls = 0, mvpVotes = 0, timesSubbedOff = 0, onCourt = onCourt,
        currentShiftTimeMillis = 0L, currentBenchTimeMillis = 0L, shiftStartGameTimeMillis = null, totalTimeMillis = 0L,
    )

    private val suggestion = SubSuggestion(
        playerOff = stat(4, "Sam", 4, onCourt = true),
        playerOn = stat(7, "Kit", 7, onCourt = false),
        offAheadMillis = 150_000L,
        onBehindMillis = 130_000L,
    )

    @Test
    fun banner_namesBothPlayers_andItsButtonsAct() {
        var subbed = 0
        var later = 0
        compose.setContent { SubSuggestionBanner(suggestion, onSubNow = { subbed++ }, onLater = { later++ }) }

        compose.onNodeWithText("#4 Sam off (02:30 ahead) · #7 Kit on (02:10 behind)").assertExists()
        compose.onNodeWithText("Sub now").performClick()
        compose.onNodeWithText("Later").performClick()
        assertEquals(1, subbed)
        assertEquals(1, later)
    }

    @Test
    fun notification_saysWhoToSwap_withASubNowAction() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        SubReminders.notify(app, gameId = 3, suggestion)

        val notification = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertEquals("Time to sub", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(
            "#4 Sam off (02:30 ahead) · #7 Kit on (02:10 behind)",
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertEquals("Sub now", notification.actions.single().title.toString())

        SubReminders.cancel(app)
        assertTrue(shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications.isEmpty())
    }
}
