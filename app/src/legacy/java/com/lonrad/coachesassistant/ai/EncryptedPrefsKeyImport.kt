package com.lonrad.coachesassistant.ai

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

/**
 * Moves API keys out of the EncryptedSharedPreferences file that pre-4.2 builds wrote
 * (androidx.security-crypto, now deprecated, and the Tink keyset file it keeps alongside). Compiled
 * into the transition build only; see [LegacyKeyImport].
 *
 * If the old file can't be read (a known failure mode of that library), its keys are lost and the
 * user re-enters them. Either way the old files are deleted, so this runs only once.
 */
class EncryptedPrefsKeyImport : LegacyKeyImport {
    @Suppress("DEPRECATION")
    override fun importInto(context: Context, store: AiSettingsStore) {
        if (!File(context.applicationInfo.dataDir, "shared_prefs/$ENCRYPTED_PREFS_NAME.xml").exists()) return

        runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val old = EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            store.importLegacy(
                legacyApiKey = { old.getString(it.storageKey, null) },
                legacySelectedProvider = old.getString(AiSettingsStore.KEY_SELECTED_PROVIDER, null),
                legacyEndpoint = old.getString(AiSettingsStore.KEY_CUSTOM_ENDPOINT_URL, null)?.let { url ->
                    CustomEndpointConfig(url, old.getString(AiSettingsStore.KEY_CUSTOM_MODEL, null).orEmpty())
                },
            )
        }
        context.deleteSharedPreferences(ENCRYPTED_PREFS_NAME)
        context.deleteSharedPreferences(TINK_KEYSET_PREFS_NAME)
    }

    private companion object {
        const val ENCRYPTED_PREFS_NAME = "training_prefs_secure"
        const val TINK_KEYSET_PREFS_NAME = "__androidx_security_crypto_encrypted_prefs__"
    }
}
