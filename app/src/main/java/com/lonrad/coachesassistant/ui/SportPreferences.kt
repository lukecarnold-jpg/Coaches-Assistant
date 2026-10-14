package com.lonrad.coachesassistant.ui

import android.content.Context
import androidx.core.content.edit
import com.lonrad.coachesassistant.data.Sport

private const val PREFS_NAME = "sport_prefs"
private const val KEY_SELECTED_SPORT = "selected_sport"

/** Remembers which sport the user last picked on the landing screen, if any. */
object SportPreferences {
    fun load(context: Context): Sport? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED_SPORT, null)
        return Sport.entries.firstOrNull { it.name == raw }
    }

    fun save(context: Context, sport: Sport?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            if (sport == null) remove(KEY_SELECTED_SPORT) else putString(KEY_SELECTED_SPORT, sport.name)
        }
    }
}
