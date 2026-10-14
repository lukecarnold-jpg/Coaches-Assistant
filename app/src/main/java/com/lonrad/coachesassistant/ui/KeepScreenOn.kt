package com.lonrad.coachesassistant.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.content.edit
import com.lonrad.coachesassistant.data.GameEntity

private const val PREFS_NAME = "display_prefs"
private const val KEY_KEEP_SCREEN_ON = "keep_screen_on_while_clock_runs"

/** Whether to keep the screen awake while a game clock runs (on unless the user turns it off). */
object DisplayPreferences {
    fun keepScreenOnWhileClockRuns(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_KEEP_SCREEN_ON, true)

    fun setKeepScreenOnWhileClockRuns(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putBoolean(KEY_KEEP_SCREEN_ON, enabled) }
    }
}

/**
 * Whether the screen should stay awake: [preferenceEnabled] and [game]'s clock is running, so a
 * coach glancing at the clock or tapping a sub mid-game doesn't find the phone locked.
 */
fun shouldKeepScreenOn(game: GameEntity?, preferenceEnabled: Boolean): Boolean =
    preferenceEnabled && game != null && !game.isFinished && game.timerLastStartedAt != null

/**
 * Keeps the screen on while [enabled] and this composable is on screen. It goes back to the normal
 * timeout as soon as [enabled] turns false or the composable leaves (e.g. the user switches sport).
 * Uses View.keepScreenOn, so it needs no permission and only applies while the app is visible.
 */
@Composable
fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}
