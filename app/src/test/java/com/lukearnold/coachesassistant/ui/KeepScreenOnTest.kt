package com.lukearnold.coachesassistant.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lukearnold.coachesassistant.data.GameEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeepScreenOnTest {

    private val stopped = GameEntity(title = "G", opponent = "Rivals", createdAt = 0L)
    private val running = stopped.copy(timerLastStartedAt = 1_000L)

    @Test
    fun screenStaysOn_onlyWhileTheClockRunsAndTheSettingIsOn() {
        assertTrue(shouldKeepScreenOn(running, preferenceEnabled = true))

        assertFalse(shouldKeepScreenOn(running, preferenceEnabled = false))
        assertFalse(shouldKeepScreenOn(stopped, preferenceEnabled = true))
        assertFalse(shouldKeepScreenOn(running.copy(isFinished = true), preferenceEnabled = true))
        assertFalse(shouldKeepScreenOn(null, preferenceEnabled = true))
    }

    @Test
    fun preference_defaultsOnAndPersists() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(DisplayPreferences.keepScreenOnWhileClockRuns(context))

        DisplayPreferences.setKeepScreenOnWhileClockRuns(context, false)

        assertFalse(DisplayPreferences.keepScreenOnWhileClockRuns(context))
    }
}
