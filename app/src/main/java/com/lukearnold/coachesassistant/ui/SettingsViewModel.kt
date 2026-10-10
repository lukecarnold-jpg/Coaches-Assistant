package com.lukearnold.coachesassistant.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lukearnold.coachesassistant.R
import com.lukearnold.coachesassistant.data.AppDatabase
import com.lukearnold.coachesassistant.data.GameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * App-wide display and substitution settings (the same for every sport). Each is saved where the
 * rest of the app reads it (DisplayPreferences, GameClockNotification, SubReminders); this keeps
 * the Settings screen's switches in step and applies a change straight away. A notification
 * setting only reads as on while the app may post notifications.
 */
class SettingsViewModel(
    application: Application,
    private val repository: GameRepository,
) : AndroidViewModel(application) {
    private val _messages = MessageQueue()
    val messages = _messages.messages

    private val _keepScreenOnWhileClockRuns = MutableStateFlow(DisplayPreferences.keepScreenOnWhileClockRuns(application))
    val keepScreenOnWhileClockRuns: StateFlow<Boolean> = _keepScreenOnWhileClockRuns.asStateFlow()

    private val _clockNotification =
        MutableStateFlow(GameClockNotification.isEnabled(application) && GameClockNotification.canPost(application))
    val clockNotification: StateFlow<Boolean> = _clockNotification.asStateFlow()

    private val _suggestSubs = MutableStateFlow(SubReminders.suggestEnabled(application))
    val suggestSubs: StateFlow<Boolean> = _suggestSubs.asStateFlow()

    private val _notifySubs =
        MutableStateFlow(SubReminders.notifyEnabled(application) && GameClockNotification.canPost(application))
    val notifySubs: StateFlow<Boolean> = _notifySubs.asStateFlow()

    fun setKeepScreenOnWhileClockRuns(enabled: Boolean) {
        DisplayPreferences.setKeepScreenOnWhileClockRuns(getApplication(), enabled)
        _keepScreenOnWhileClockRuns.value = enabled
    }

    /** Turns the game clock notification on or off, updating what's shown now. */
    fun setClockNotification(enabled: Boolean) {
        GameClockNotification.setEnabled(getApplication(), enabled)
        _clockNotification.value = enabled
        viewModelScope.launch { GameClockNotification.sync(getApplication(), repository.games.first()) }
    }

    fun setSuggestSubs(enabled: Boolean) {
        SubReminders.setSuggestEnabled(getApplication(), enabled)
        _suggestSubs.value = enabled
        if (enabled) startSubRemindersIfNeeded()
    }

    fun setNotifySubs(enabled: Boolean) {
        SubReminders.setNotifyEnabled(getApplication(), enabled)
        _notifySubs.value = enabled
        if (enabled) startSubRemindersIfNeeded()
    }

    /** Explains that a notification setting can't go on, since notifications aren't allowed. */
    fun reportNotificationsDenied() {
        _messages.tryEmit(UiMessage(uiText(R.string.msg_notifications_denied)))
    }

    /** Sub reminders for a clock that's already running, once they're turned on mid-game. */
    private fun startSubRemindersIfNeeded() {
        viewModelScope.launch { SubReminderService.startIfNeeded(getApplication(), repository.games.first()) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                SettingsViewModel(application, GameRepository(AppDatabase.getInstance(application)))
            }
        }
    }
}
