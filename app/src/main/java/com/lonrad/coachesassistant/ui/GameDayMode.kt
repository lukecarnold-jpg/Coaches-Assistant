package com.lonrad.coachesassistant.ui

import android.annotation.SuppressLint
import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.core.net.toUri
import com.lonrad.coachesassistant.MainActivity
import com.lonrad.coachesassistant.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Game Day mode: silences notifications for one game, from when the coach turns it on until that
 * game ends. It's a Do Not Disturb rule of the app's own ("Game day", listed with the phone's other
 * Do Not Disturb modes), so it doesn't change the user's own Do Not Disturb settings, and they can
 * adjust what it lets through there. It lets through calls from starred contacts, repeat callers,
 * and alarms. Android 10+ only (AutomaticZenRule with a ZenPolicy), and it needs the user to grant
 * Do Not Disturb access once.
 *
 * The rule stays on if the app is closed; [stopUnlessPlaying] turns it off once its game is no
 * longer in progress (ended, deleted, cleared, or replaced by a restore).
 */
class GameDayMode internal constructor(
    private val context: Context,
    private val prefs: SharedPreferences,
) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    private val _activeGameId = MutableStateFlow(prefs.getLong(KEY_GAME_ID, NO_GAME).takeIf { it != NO_GAME })

    /** The game Game Day mode is silencing notifications for, or null if it's off. */
    val activeGameId: StateFlow<Long?> = _activeGameId.asStateFlow()

    fun hasAccess(): Boolean = notificationManager.isNotificationPolicyAccessGranted

    /** How turning Game Day mode on went. */
    enum class StartResult { STARTED, NO_ACCESS, FAILED }

    /**
     * Turns Game Day mode on for [gameId]. Without Do Not Disturb access it does nothing; if the
     * system refuses (access revoked just then, or the phone's limit on app-made modes reached),
     * it reports that rather than crashing mid-game.
     */
    fun start(gameId: Long): StartResult {
        if (!isSupported || !hasAccess()) return StartResult.NO_ACCESS
        if (runCatching { setRuleActive(true) }.isFailure) return StartResult.FAILED
        prefs.edit { putLong(KEY_GAME_ID, gameId) }
        _activeGameId.value = gameId
        return StartResult.STARTED
    }

    /**
     * Turns Game Day mode off, whichever game it was for. Returns false if the system wouldn't
     * turn the mode off (it's forgotten here either way, so the user can turn off Do Not Disturb
     * themselves). Without Do Not Disturb access there's nothing to turn off: revoking access
     * removes the app's modes.
     */
    fun stop(): Boolean {
        if (_activeGameId.value == null) return true
        val stopped = if (isSupported && hasAccess()) runCatching { setRuleActive(false) }.isSuccess else true
        prefs.edit { remove(KEY_GAME_ID) }
        _activeGameId.value = null
        return stopped
    }

    /** Turns Game Day mode off if its game isn't among [gamesInProgress] (every sport's unfinished games). */
    fun stopUnlessPlaying(gamesInProgress: Collection<Long>) {
        val gameId = _activeGameId.value ?: return
        if (gameId !in gamesInProgress) stop()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun setRuleActive(active: Boolean) {
        val state = if (active) Condition.STATE_TRUE else Condition.STATE_FALSE
        notificationManager.setAutomaticZenRuleState(
            ruleId(),
            Condition(CONDITION_ID, context.getString(R.string.game_day_condition), state),
        )
    }

    /** The app's rule, added the first time it's needed (or again if the user deleted it). */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun ruleId(): String {
        prefs.getString(KEY_RULE_ID, null)
            ?.takeIf { notificationManager.getAutomaticZenRule(it) != null }
            ?.let { return it }
        return notificationManager.addAutomaticZenRule(newRule()).also { prefs.edit { putString(KEY_RULE_ID, it) } }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun newRule(): AutomaticZenRule {
        val name = context.getString(R.string.game_day_rule_name)
        val settingsActivity = ComponentName(context, MainActivity::class.java)
        val policy = ZenPolicy.Builder()
            .allowCalls(ZenPolicy.PEOPLE_TYPE_STARRED)
            .allowRepeatCallers(true)
            .allowMessages(ZenPolicy.PEOPLE_TYPE_NONE)
            .allowAlarms(true)
            .allowMedia(true)
            .allowReminders(false)
            .allowEvents(false)
            .allowSystem(false)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) allowConversations(ZenPolicy.CONVERSATION_SENDERS_NONE)
            }
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            AutomaticZenRule.Builder(name, CONDITION_ID)
                .setType(AutomaticZenRule.TYPE_OTHER)
                .setConfigurationActivity(settingsActivity)
                .setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                .setZenPolicy(policy)
                .setTriggerDescription(context.getString(R.string.game_day_trigger))
                .setManualInvocationAllowed(false)
                .build()
        } else {
            AutomaticZenRule(
                name,
                null,
                settingsActivity,
                CONDITION_ID,
                policy,
                NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                true,
            )
        }
    }

    companion object {
        /** Whether this Android version can run Game Day mode (10+). */
        val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        private const val PREFS_NAME = "game_day_prefs"
        private const val KEY_GAME_ID = "active_game_id"
        private const val KEY_RULE_ID = "zen_rule_id"
        private const val NO_GAME = -1L
        private val CONDITION_ID: Uri = "condition://com.lonrad.coachesassistant/game_day".toUri()

        // Holds only the application context, which lives as long as the app, so it can't leak.
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: GameDayMode? = null

        /** The one instance, so every screen and view model sees the same state. */
        fun get(context: Context): GameDayMode = instance ?: synchronized(this) {
            instance ?: GameDayMode(
                context.applicationContext,
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
            ).also { instance = it }
        }
    }
}

/**
 * The Game screen's Game Day button. Turning it on the first time explains why the app needs Do
 * Not Disturb access and opens the system screen to grant it, then starts once the user comes back
 * with access granted. Shows nothing on Android versions without Game Day mode.
 */
@Composable
fun GameDayModeButton(active: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    if (!GameDayMode.isSupported) return
    val context = LocalContext.current
    var explainingAccess by rememberSaveable { mutableStateOf(false) }
    val accessSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (GameDayMode.get(context).hasAccess()) onStart()
    }

    if (active) {
        OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.game_day_turn_off))
        }
        Text(stringResource(R.string.game_day_on_explanation), style = MaterialTheme.typography.bodySmall)
    } else {
        OutlinedButton(
            onClick = { if (GameDayMode.get(context).hasAccess()) onStart() else explainingAccess = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.game_day_turn_on))
        }
    }

    if (explainingAccess) {
        AlertDialog(
            onDismissRequest = { explainingAccess = false },
            title = { Text(stringResource(R.string.game_day_access_title)) },
            text = { Text(stringResource(R.string.game_day_access_explanation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        explainingAccess = false
                        accessSettings.launch(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    },
                ) { Text(stringResource(R.string.open_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { explainingAccess = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
