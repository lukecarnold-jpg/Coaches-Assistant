package com.lukearnold.coachesassistant.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.lukearnold.coachesassistant.MainActivity
import com.lukearnold.coachesassistant.R
import com.lukearnold.coachesassistant.data.AppDatabase
import com.lukearnold.coachesassistant.data.GameRepository
import com.lukearnold.coachesassistant.data.Sport
import com.lukearnold.coachesassistant.data.currentGameTimeMillis
import com.lukearnold.coachesassistant.data.displayLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A substitution suggestion the coach put off, until the game clock reaches [untilGameTimeMillis]. */
data class SubSnooze(val key: Pair<Long, Long>, val untilGameTimeMillis: Long) {
    fun hides(suggestion: SubSuggestion, gameTimeMillis: Long): Boolean =
        key == suggestion.key && gameTimeMillis < untilGameTimeMillis
}

/**
 * Substitution reminders: a suggestion (see subSuggestion) shown as a banner on the Game tab, and
 * optionally as a notification, so it reaches the coach with the phone locked. The notification
 * has a Sub now action that makes the swap without opening the app.
 */
object SubReminders {
    private const val PREFS_NAME = "display_prefs"
    private const val KEY_SUGGEST = "suggest_substitutions"
    private const val KEY_NOTIFY = "notify_substitutions"
    private const val CHANNEL_ID = "sub_reminders"
    private const val NOTIFICATION_ID = 900
    internal const val EXTRA_GAME_ID = "game_id"
    internal const val EXTRA_PLAYER_ON = "player_on"
    internal const val EXTRA_PLAYER_OFF = "player_off"

    /** Whether the Game tab suggests substitutions (on unless the coach turns it off). */
    fun suggestEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_SUGGEST, true)

    fun setSuggestEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putBoolean(KEY_SUGGEST, enabled) }
        if (!enabled) cancel(context)
    }

    /** Whether suggestions also come as a notification (off until turned on, with permission). */
    fun notifyEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_NOTIFY, false)

    fun setNotifyEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putBoolean(KEY_NOTIFY, enabled) }
        if (!enabled) cancel(context)
    }

    private val _snooze = MutableStateFlow<SubSnooze?>(null)

    /**
     * The suggestion "Later" put off, shared by the Game tab's banner and the notifications (see
     * SubReminderService) so both hide it. Kept for the life of the process.
     */
    val snooze: StateFlow<SubSnooze?> = _snooze.asStateFlow()

    /** "Later" on a suggestion: hides that pair for another [SUB_SUGGESTION_MARGIN_MILLIS] of play. */
    fun snooze(context: Context, key: Pair<Long, Long>, gameTimeMillis: Long) {
        _snooze.value = SubSnooze(key, gameTimeMillis + SUB_SUGGESTION_MARGIN_MILLIS)
        cancel(context)
    }

    /**
     * Checks whether it's time to suggest a substitution for [gameId], and notifies if so, once
     * per suggested pair: [lastNotified] is the pair last notified, and the result is the pair to
     * pass next time. Reads the database directly, so it works while the app isn't showing.
     */
    internal suspend fun check(context: Context, repository: GameRepository, gameId: Long, lastNotified: Pair<Long, Long>?): Pair<Long, Long>? {
        val game = repository.getGame(gameId)?.takeUnless { it.isFinished } ?: return null
        val sport = Sport.entries.firstOrNull { it.name == game.sport } ?: return null
        val events = repository.getEvents(gameId)
        val players = repository.getPlayers(sport)
        val stats = calculatePlayerGameStats(players, game, repository.getLineup(gameId), events)
        val now = game.currentGameTimeMillis()
        val share = fairShare(game, stats.size, elapsedGameMillis(events, now, game.periodDurationMillis))
        val excluded = subSuggestionExclusions(players, sport.config, events)
        val suggestion = subSuggestion(stats.filter { it.onCourt }, stats.filter { !it.onCourt }, share, now, excluded)
            ?.takeUnless { snooze.value?.hides(it, now) == true }
        return when {
            suggestion == null -> {
                if (lastNotified != null) cancel(context)
                null
            }
            suggestion.key != lastNotified -> {
                notify(context, gameId, suggestion)
                suggestion.key
            }
            else -> lastNotified
        }
    }

    /** Posts [suggestion] for [gameId], replacing any earlier one. */
    fun notify(context: Context, gameId: Long, suggestion: SubSuggestion) {
        ensureChannel(context)
        val resources = context.resources
        val text = uiText(
            R.string.sub_suggestion_line,
            suggestion.playerOff.player.displayLabel(),
            formatDuration(suggestion.offAheadMillis),
            suggestion.playerOn.player.displayLabel(),
            formatDuration(suggestion.onBehindMillis),
        ).resolve(resources)
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val subNow = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, SubNowReceiver::class.java)
                .putExtra(EXTRA_GAME_ID, gameId)
                .putExtra(EXTRA_PLAYER_ON, suggestion.playerOn.player.id)
                .putExtra(EXTRA_PLAYER_OFF, suggestion.playerOff.player.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_game_clock)
            .setContentTitle(context.getString(R.string.sub_suggestion_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .addAction(0, context.getString(R.string.sub_now), subNow)
            .build()
        @Suppress("MissingPermission") // the caller checked GameClockNotification.canPost()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.sub_reminders_channel),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.sub_reminders_channel_description)
            enableVibration(true)
            // Game Day mode's Do Not Disturb silences this too: only the user can let an app's
            // notifications through a mode (the setting's description says how).
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

/** The reminder's Sub now action: makes the suggested swap, even if the app isn't running. */
class SubNowReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val gameId = intent.getLongExtra(SubReminders.EXTRA_GAME_ID, -1L).takeIf { it >= 0 } ?: return
        val playerOn = intent.getLongExtra(SubReminders.EXTRA_PLAYER_ON, -1L).takeIf { it >= 0 } ?: return
        val playerOff = intent.getLongExtra(SubReminders.EXTRA_PLAYER_OFF, -1L).takeIf { it >= 0 } ?: return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                GameRepository(AppDatabase.getInstance(appContext)).substitute(gameId, playerOn, playerOff)
                SubReminders.cancel(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}
