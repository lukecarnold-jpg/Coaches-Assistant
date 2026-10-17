package com.lonrad.coachesassistant.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.lonrad.coachesassistant.MainActivity
import com.lonrad.coachesassistant.R
import com.lonrad.coachesassistant.data.AppDatabase
import com.lonrad.coachesassistant.data.GameEntity
import com.lonrad.coachesassistant.data.GameRepository
import com.lonrad.coachesassistant.data.currentGameTimeMillis
import com.lonrad.coachesassistant.data.remainingPeriodTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A silent, ongoing notification for each game whose clock is running, so the time can be seen
 * on the lock screen without opening the app. The system counts it down (a chronometer), so it
 * stays right while the app isn't running, and it's removed at the end of the period; it has a
 * Stop clock action. Opt-in (Settings → Display), and needs the notifications permission on
 * Android 13+.
 */
object GameClockNotification {
    internal const val CHANNEL_ID = "game_clock"
    private const val PREFS_NAME = "display_prefs"
    private const val KEY_ENABLED = "game_clock_notification"
    private const val KEY_POSTED = "game_clock_notification_games"
    internal const val EXTRA_GAME_ID = "game_id"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { putBoolean(KEY_ENABLED, enabled) }
    }

    /** Whether the app may post notifications (always before Android 13; a runtime permission since). */
    fun canPost(context: Context): Boolean =
        (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * Shows a notification for each of [games] (every sport's) whose clock is running, and
     * removes the ones for games whose clock has stopped, or all of them if the setting is off.
     */
    fun sync(context: Context, games: List<GameEntity>, now: Long = System.currentTimeMillis()) {
        val manager = NotificationManagerCompat.from(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val running = if (isEnabled(context) && canPost(context)) {
            games.filter { !it.isFinished && it.timerLastStartedAt != null }
        } else {
            emptyList()
        }
        val previouslyPosted = prefs.getStringSet(KEY_POSTED, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }
        (previouslyPosted - running.map { it.id }.toSet()).forEach { manager.cancel(notificationId(it)) }
        if (running.isNotEmpty()) ensureChannel(context)
        running.forEach { post(context, it, now) }
        prefs.edit { putStringSet(KEY_POSTED, running.map { it.id.toString() }.toSet()) }
    }

    /** Removes [gameId]'s notification, e.g. once its clock is stopped from the notification. */
    fun cancel(context: Context, gameId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(gameId))
    }

    private fun post(context: Context, game: GameEntity, now: Long) {
        val remaining = game.remainingPeriodTimeMillis(now)
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopClock = PendingIntent.getBroadcast(
            context,
            game.id.toInt(),
            Intent(context, StopGameClockReceiver::class.java).putExtra(EXTRA_GAME_ID, game.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val resources = context.resources

        // The clock itself, which both the full notification and the lock-screen version show.
        fun withClock(builder: NotificationCompat.Builder): NotificationCompat.Builder {
            if (remaining != null) {
                // Counts down to the end of the period, then goes away (the clock stops itself there).
                builder.setWhen(now + remaining).setChronometerCountDown(true)
                if (remaining > 0) builder.setTimeoutAfter(remaining)
            } else {
                // No set period length: count up from when the period's time started.
                builder.setWhen(now - game.currentGameTimeMillis(now))
            }
            return builder
                .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
                .setOngoing(true)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(true)
                .setUsesChronometer(true)
        }
        // On the lock screen only the clock shows, not the team, opponent, score, or period, which
        // can name the game and the players' school or club.
        val lockScreenVersion = withClock(
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_game_clock)
                .setContentTitle(context.getString(R.string.game_clock_notification_channel)),
        ).build()
        val builder = withClock(
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_game_clock)
                .setContentTitle(context.getString(R.string.game_clock_notification_title, game.title, game.periodLabel().resolve(resources)))
                .setContentText(game.scoreLine().resolve(resources))
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(lockScreenVersion)
                .setContentIntent(openApp)
                .addAction(0, context.getString(R.string.game_clock_notification_stop), stopClock),
        )
        @Suppress("MissingPermission") // canPost() was checked by sync()
        NotificationManagerCompat.from(context).notify(notificationId(game.id), builder.build())
    }

    internal fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.game_clock_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = context.getString(R.string.game_clock_notification_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notificationId(gameId: Long): Int = 1000 + gameId.toInt()
}

/** The notification's Stop clock action: stops that game's clock, even if the app isn't running. */
class StopGameClockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val gameId = intent.getLongExtra(GameClockNotification.EXTRA_GAME_ID, -1L).takeIf { it >= 0 } ?: return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                GameRepository(AppDatabase.getInstance(appContext)).stopTimer(gameId)
                GameClockNotification.cancel(appContext, gameId)
            } finally {
                pending.finish()
            }
        }
    }
}
