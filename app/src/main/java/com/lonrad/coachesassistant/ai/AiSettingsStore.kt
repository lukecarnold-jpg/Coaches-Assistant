package com.lonrad.coachesassistant.ai

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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
 */
class KeystoreSecretCipher(private val keyAlias: String) : SecretCipher {
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
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
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
        private const val KEYSTORE_ALIAS = "ai_api_keys"
        private const val KEY_SELECTED_PROVIDER = "selected_ai_provider"
        private const val KEY_CUSTOM_ENDPOINT_URL = "custom_endpoint_url"
        private const val KEY_CUSTOM_MODEL = "custom_model"

        // Earlier storage: a plaintext file holding only a Gemini key, then an
        // EncryptedSharedPreferences file (androidx.security-crypto, now deprecated) plus the Tink
        // keyset file that library keeps alongside it.
        private const val PLAINTEXT_PREFS_NAME = "training_prefs"
        private const val PLAINTEXT_GEMINI_KEY = "gemini_api_key"
        private const val ENCRYPTED_PREFS_NAME = "training_prefs_secure"
        private const val TINK_KEYSET_PREFS_NAME = "__androidx_security_crypto_encrypted_prefs__"

        fun create(context: Context): AiSettingsStore {
            val store = AiSettingsStore(
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                KeystoreSecretCipher(KEYSTORE_ALIAS),
            )
            migrateEncryptedPrefs(context, store)
            migratePlaintextPrefs(context, store)
            return store
        }

        private fun prefsFileExists(context: Context, name: String) =
            File(context.applicationInfo.dataDir, "shared_prefs/$name.xml").exists()

        /**
         * One-time move out of EncryptedSharedPreferences. If the old file can't be read (a
         * known failure mode of that library), its keys are lost and the user re-enters them;
         * either way the old files are deleted so this only ever runs once.
         */
        @Suppress("DEPRECATION")
        private fun migrateEncryptedPrefs(context: Context, store: AiSettingsStore) {
            if (!prefsFileExists(context, ENCRYPTED_PREFS_NAME)) return

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
                    legacySelectedProvider = old.getString(KEY_SELECTED_PROVIDER, null),
                    legacyEndpoint = old.getString(KEY_CUSTOM_ENDPOINT_URL, null)?.let { url ->
                        CustomEndpointConfig(url, old.getString(KEY_CUSTOM_MODEL, null).orEmpty())
                    },
                )
            }
            context.deleteSharedPreferences(ENCRYPTED_PREFS_NAME)
            context.deleteSharedPreferences(TINK_KEYSET_PREFS_NAME)
        }

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
