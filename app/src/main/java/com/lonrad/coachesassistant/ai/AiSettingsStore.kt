package com.lonrad.coachesassistant.ai

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts and decrypts small secrets (API keys) to and from a storable string. */
interface SecretCipher {
    fun encrypt(plaintext: String): String
    fun decrypt(encoded: String): String
}

/**
 * AES-256-GCM with a key that lives in the Android Keystore and never leaves it. Output is
 * Base64 of the 12-byte IV followed by the ciphertext and tag.
 *
 * With [unlockedDeviceOnly] the key can be used only while the phone is unlocked (Android 9+), so a
 * locked phone can't have its keys decrypted by something running in the background.
 */
class KeystoreSecretCipher(private val keyAlias: String, private val unlockedDeviceOnly: Boolean) : SecretCipher {
    override fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    override fun decrypt(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .apply {
                        if (unlockedDeviceOnly && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) setUnlockedDeviceRequired(true)
                    }
                    .build(),
            )
            generateKey()
        }
    }

    companion object {
        const val KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128

        /** Removes a Keystore key, e.g. the old one once its saved keys have moved to a new one. */
        fun deleteKey(keyAlias: String) {
            KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(keyAlias)
        }
    }
}

/**
 * The AI provider settings: which provider is selected, the "Other" provider's endpoint and model,
 * and each provider's API key. API keys are encrypted with [cipher]; the rest isn't secret and is
 * stored as-is. A key that can't be decrypted (e.g. its Keystore key is gone) reads as unset
 * rather than crashing, so the user can simply enter it again.
 */
class AiSettingsStore(
    private val prefs: SharedPreferences,
    private val cipher: SecretCipher,
) {
    fun apiKey(provider: AiProvider): String =
        prefs.getString(provider.storageKey, null)
            ?.let { runCatching { cipher.decrypt(it) }.getOrNull() }
            .orEmpty()

    fun setApiKey(provider: AiProvider, key: String) {
        if (key.isBlank()) {
            clearApiKey(provider)
        } else {
            prefs.edit { putString(provider.storageKey, cipher.encrypt(key)) }
        }
    }

    fun clearApiKey(provider: AiProvider) {
        prefs.edit { remove(provider.storageKey) }
    }

    /** The model the user chose for [provider] in place of its default, or "" for the default. */
    fun modelOverride(provider: AiProvider): String = prefs.getString(modelKey(provider), null).orEmpty()

    fun setModelOverride(provider: AiProvider, model: String) {
        prefs.edit { if (model.isBlank()) remove(modelKey(provider)) else putString(modelKey(provider), model) }
    }

    private fun modelKey(provider: AiProvider) = "model_${provider.name}"

    /**
     * Re-encrypts every saved API key from [from] to [to], for when the Keystore key changes. A key
     * that [from] can't read is dropped, so the user enters it again rather than it failing later.
     */
    fun rekeyApiKeys(from: SecretCipher, to: SecretCipher) {
        val rekeyed = AiProvider.entries.mapNotNull { provider ->
            val stored = prefs.getString(provider.storageKey, null) ?: return@mapNotNull null
            provider to runCatching { to.encrypt(from.decrypt(stored)) }.getOrNull()
        }
        prefs.edit {
            rekeyed.forEach { (provider, encrypted) ->
                if (encrypted == null) remove(provider.storageKey) else putString(provider.storageKey, encrypted)
            }
        }
    }

    var selectedProvider: AiProvider
        get() = AiProvider.entries.firstOrNull { it.name == prefs.getString(KEY_SELECTED_PROVIDER, null) }
            ?: AiProvider.GEMINI
        set(value) = prefs.edit { putString(KEY_SELECTED_PROVIDER, value.name) }

    var customEndpoint: CustomEndpointConfig
        get() = CustomEndpointConfig(
            endpointUrl = prefs.getString(KEY_CUSTOM_ENDPOINT_URL, null).orEmpty(),
            model = prefs.getString(KEY_CUSTOM_MODEL, null).orEmpty(),
        )
        set(value) = prefs.edit {
            putString(KEY_CUSTOM_ENDPOINT_URL, value.endpointUrl)
            putString(KEY_CUSTOM_MODEL, value.model)
        }

    /**
     * Copies settings saved by an older version of the app into this store, without overwriting
     * anything already here. [legacyApiKey] returns the old key for a provider, or null.
     */
    fun importLegacy(
        legacyApiKey: (AiProvider) -> String?,
        legacySelectedProvider: String?,
        legacyEndpoint: CustomEndpointConfig?,
    ) {
        AiProvider.entries.forEach { provider ->
            val key = legacyApiKey(provider)
            if (!key.isNullOrBlank() && !prefs.contains(provider.storageKey)) setApiKey(provider, key)
        }
        if (legacySelectedProvider != null && !prefs.contains(KEY_SELECTED_PROVIDER)) {
            prefs.edit { putString(KEY_SELECTED_PROVIDER, legacySelectedProvider) }
        }
        if (legacyEndpoint != null && !prefs.contains(KEY_CUSTOM_ENDPOINT_URL)) {
            customEndpoint = legacyEndpoint
        }
    }

    companion object {
        private const val PREFS_NAME = "ai_settings"

        // The key saved keys were first encrypted with, and the one they're encrypted with now.
        private const val OLD_KEYSTORE_ALIAS = "ai_api_keys"
        private const val KEYSTORE_ALIAS = "ai_api_keys_unlocked"
        private const val KEY_STORAGE_VERSION = "api_key_storage_version"
        private const val CURRENT_STORAGE_VERSION = 2
        internal const val KEY_SELECTED_PROVIDER = "selected_ai_provider"
        internal const val KEY_CUSTOM_ENDPOINT_URL = "custom_endpoint_url"
        internal const val KEY_CUSTOM_MODEL = "custom_model"

        // Earlier storage: a plaintext file holding only a Gemini key. (The EncryptedSharedPreferences
        // file that came after it is read by LegacyKeyImport, in the transition build only.)
        private const val PLAINTEXT_PREFS_NAME = "training_prefs"
        private const val PLAINTEXT_GEMINI_KEY = "gemini_api_key"

        fun create(context: Context): AiSettingsStore {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val store = AiSettingsStore(prefs, KeystoreSecretCipher(KEYSTORE_ALIAS, unlockedDeviceOnly = true))
            migrateKeystoreKey(prefs, store)
            // Keys saved by pre-4.2 builds in EncryptedSharedPreferences; only the transition build has an importer.
            LegacyKeyImport.all(context).forEach { it.importInto(context, store) }
            migratePlaintextPrefs(context, store)
            return store
        }

        /**
         * Moves the saved keys from the key they were first encrypted with to the unlocked-only key,
         * then deletes the old key. Runs once: the version marker records that it has happened.
         */
        private fun migrateKeystoreKey(prefs: SharedPreferences, store: AiSettingsStore) {
            if (prefs.getInt(KEY_STORAGE_VERSION, 1) >= CURRENT_STORAGE_VERSION) return

            store.rekeyApiKeys(
                from = KeystoreSecretCipher(OLD_KEYSTORE_ALIAS, unlockedDeviceOnly = false),
                to = KeystoreSecretCipher(KEYSTORE_ALIAS, unlockedDeviceOnly = true),
            )
            prefs.edit { putInt(KEY_STORAGE_VERSION, CURRENT_STORAGE_VERSION) }
            runCatching { KeystoreSecretCipher.deleteKey(OLD_KEYSTORE_ALIAS) }
        }

        private fun prefsFileExists(context: Context, name: String) =
            File(context.applicationInfo.dataDir, "shared_prefs/$name.xml").exists()

        private fun migratePlaintextPrefs(context: Context, store: AiSettingsStore) {
            if (!prefsFileExists(context, PLAINTEXT_PREFS_NAME)) return

            val old = context.getSharedPreferences(PLAINTEXT_PREFS_NAME, Context.MODE_PRIVATE)
            runCatching {
                store.importLegacy(
                    legacyApiKey = { if (it == AiProvider.GEMINI) old.getString(PLAINTEXT_GEMINI_KEY, null) else null },
                    legacySelectedProvider = null,
                    legacyEndpoint = null,
                )
            }
            context.deleteSharedPreferences(PLAINTEXT_PREFS_NAME)
        }
    }
}
