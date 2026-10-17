package com.lonrad.coachesassistant.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Asks for the phone's own unlock (PIN, pattern, password, or a fingerprint or face) before a
 * sensitive action: showing an API key, moving a backup in or out, or deleting data. [onConfirmed]
 * runs only after the unlock succeeds. With no screen lock set there's nothing to check against, so
 * the action goes ahead. If the lock can't be checked right now, the action is refused.
 */
fun Context.confirmDeviceUnlock(title: String, onConfirmed: () -> Unit) {
    val activity = findActivity() as? FragmentActivity ?: return
    val authenticators = BiometricManager.Authenticators.DEVICE_CREDENTIAL
    when (BiometricManager.from(activity).canAuthenticate(authenticators)) {
        BiometricManager.BIOMETRIC_SUCCESS -> {
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onConfirmed()
            }
            BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setAllowedAuthenticators(authenticators)
                    .build(),
            )
        }

        // Covers device credentials too: no PIN, pattern, or password is set.
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> onConfirmed()

        else -> Unit
    }
}

/** The activity this context belongs to, looking through any wrappers, or null. */
fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
