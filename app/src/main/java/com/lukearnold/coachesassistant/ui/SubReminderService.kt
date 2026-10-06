package com.lukearnold.coachesassistant.ui

import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lukearnold.coachesassistant.MainActivity
import com.lukearnold.coachesassistant.R
import com.lukearnold.coachesassistant.data.AppDatabase
import com.lukearnold.coachesassistant.data.GameEntity
import com.lukearnold.coachesassistant.data.GameRepository
import com.lukearnold.coachesassistant.data.remainingPeriodTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps substitution reminders coming while a game clock runs, even once the app is in the
 * background and Android would otherwise reclaim it: every [CHECK_INTERVAL_MILLIS] it checks
 * whether to suggest a sub (see SubReminders.check). It runs only while some game's clock is
 * running and sub notifications are on, and stops itself as soon as either isn't true.
 *
 * A foreground service alone doesn't keep the CPU awake with the screen off, so it also holds a
 * partial wake lock, renewed with a short timeout each check, so it can never outlive the service.
 * When the period's time runs out it stops that game's clock, as the Game tab would.
 */
class SubReminderService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var repository: GameRepository
    private lateinit var wakeLock: PowerManager.WakeLock
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        repository = GameRepository(AppDatabase.getInstance(applicationContext))
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CoachesAssistant:SubReminders")
            .apply { setReferenceCounted(false) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android requires startForeground() promptly after every startForegroundService().
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            watchingNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        if (!started) {
            started = true
            running = true
            scope.launch { watchRunningClock() }
        }
        // If Android stops it, the next clock start or app launch starts it again.
        return START_NOT_STICKY
    }

    private suspend fun watchRunningClock() {
        var lastNotified: Pair<Long, Long>? = null
        repository.games
            .map { runningGame(it)?.id }
            .distinctUntilChanged()
            .collectLatest { gameId ->
                if (gameId == null || !shouldRun(this)) {
                    stopSelf()
                    return@collectLatest
                }
                while (shouldRun(this)) {
                    wakeLock.acquire(CHECK_INTERVAL_MILLIS * 2)
                    val game = repository.getGame(gameId)
                    if (game != null && game.remainingPeriodTimeMillis() == 0L) {
                        repository.stopTimer(gameId) // the games flow then moves on
                        return@collectLatest
                    }
                    lastNotified = SubReminders.check(this, repository, gameId, lastNotified)
                    delay(CHECK_INTERVAL_MILLIS)
                }
                stopSelf()
            }
    }

    private fun watchingNotification() = NotificationCompat.Builder(this, GameClockNotification.CHANNEL_ID)
        .also { GameClockNotification.ensureChannel(this) }
        .setSmallIcon(R.drawable.ic_stat_game_clock)
        .setContentTitle(getString(R.string.sub_reminder_service_title))
        .setContentText(getString(R.string.sub_reminder_service_text))
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setOngoing(true)
        .setSilent(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    override fun onDestroy() {
        running = false
        scope.cancel()
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 901
        private const val CHECK_INTERVAL_MILLIS = 15_000L

        @Volatile
        private var running = false

        /** The game (any sport's) whose clock is running with time left in the period, if any. */
        internal fun runningGame(games: List<GameEntity>): GameEntity? =
            games.firstOrNull { !it.isFinished && it.timerLastStartedAt != null }

        private fun shouldRun(context: Context): Boolean =
            SubReminders.suggestEnabled(context) && SubReminders.notifyEnabled(context) && GameClockNotification.canPost(context)

        /**
         * Starts the service if [games] has a running clock and sub notifications are on. Safe to
         * call on every change: it does nothing while the service is already running, and the
         * service stops itself when it's no longer needed.
         */
        fun startIfNeeded(context: Context, games: List<GameEntity>) {
            if (running || runningGame(games) == null || !shouldRun(context)) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, SubReminderService::class.java))
            } catch (e: IllegalStateException) {
                // Android 12+ doesn't let a backgrounded app start one; the clock was started from
                // the app, so this only happens for changes made with the app out of sight (e.g.
                // from a notification), and the next time the app is opened starts it.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || e !is ForegroundServiceStartNotAllowedException) throw e
            }
        }
    }
}
