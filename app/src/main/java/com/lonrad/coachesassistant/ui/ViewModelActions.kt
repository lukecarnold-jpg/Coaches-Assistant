package com.lonrad.coachesassistant.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lonrad.coachesassistant.BuildConfig
import com.lonrad.coachesassistant.R
import com.lonrad.coachesassistant.data.UserFacingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val TAG = "ViewModelActions"

/**
 * Launches a user action so that if it fails (e.g. a database error) the coach gets a message in
 * [messages] rather than the app crashing mid-game. A [UserFacingException] shows its own message;
 * anything else shows [failure].
 */
internal fun ViewModel.launchSafely(
    messages: MessageQueue,
    failure: Int = R.string.msg_action_failed,
    block: suspend CoroutineScope.() -> Unit,
): Job = viewModelScope.launch {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // A release build logs only the failure's type: its message and stack trace can carry the coach's data.
        if (BuildConfig.DEBUG) Log.e(TAG, "Action failed", e) else Log.e(TAG, "Action failed: ${e.javaClass.simpleName}")
        messages.emit(UiMessage(if (e is UserFacingException) e.toUiText(fallback = failure) else uiText(failure)))
    }
}
