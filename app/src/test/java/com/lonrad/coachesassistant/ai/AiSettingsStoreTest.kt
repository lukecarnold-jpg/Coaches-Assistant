package com.lonrad.coachesassistant.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric has no Android Keystore, so these use a stand-in cipher; KeystoreSecretCipher itself
 * is only exercised on a real device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AiSettingsStoreTest {

    private val prefs = ApplicationProvider.getApplicationContext<Context>()
        .getSharedPreferences("ai_settings_test", Context.MODE_PRIVATE)

    private val reversingCipher = object : SecretCipher {
        override fun encrypt(plaintext: String) = "enc:" + plaintext.reversed()
        override fun decrypt(encoded: String): String {
            require(encoded.startsWith("enc:")) { "not ours" }
            return encoded.removePrefix("enc:").reversed()
        }
    }

    private lateinit var store: AiSettingsStore

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
        store = AiSettingsStore(prefs, reversingCipher)
    }

    @Test
    fun apiKey_isStoredEncryptedAndReadBackDecrypted() {
        store.setApiKey(AiProvider.ANTHROPIC, "sk-secret")

        assertEquals("sk-secret", store.apiKey(AiProvider.ANTHROPIC))
        assertFalse(prefs.getString(AiProvider.ANTHROPIC.storageKey, null)!!.contains("sk-secret"))
        assertEquals("", store.apiKey(AiProvider.OPENAI))
    }

    @Test
    fun rekeyApiKeys_movesEverySavedKeyToTheNewCipher_andDropsOneItCantRead() {
        val newCipher = object : SecretCipher {
            override fun encrypt(plaintext: String) = "new:$plaintext"
            override fun decrypt(encoded: String): String {
                require(encoded.startsWith("new:")) { "not ours" }
                return encoded.removePrefix("new:")
            }
        }
        AiSettingsStore(prefs, reversingCipher).apply {
            setApiKey(AiProvider.ANTHROPIC, "sk-1")
            setApiKey(AiProvider.OPENAI, "sk-2")
        }
        prefs.edit().putString(AiProvider.OPENAI.storageKey, "unreadable").commit()

        val store = AiSettingsStore(prefs, newCipher)
        store.rekeyApiKeys(from = reversingCipher, to = newCipher)

        assertEquals("sk-1", store.apiKey(AiProvider.ANTHROPIC))
        assertEquals("new:sk-1", prefs.getString(AiProvider.ANTHROPIC.storageKey, null))
        assertNull(prefs.getString(AiProvider.OPENAI.storageKey, null))
    }

    @Test
    fun modelOverride_isPerProviderAndBlankMeansDefault() {
        assertEquals("", store.modelOverride(AiProvider.GEMINI))

        store.setModelOverride(AiProvider.GEMINI, "gemini-next")
        assertEquals("gemini-next", store.modelOverride(AiProvider.GEMINI))
        assertEquals("", store.modelOverride(AiProvider.ANTHROPIC))

        store.setModelOverride(AiProvider.GEMINI, " ")
        assertEquals("", store.modelOverride(AiProvider.GEMINI))
    }

    @Test
    fun apiKey_thatCantBeDecrypted_readsAsUnsetInsteadOfCrashing() {
        prefs.edit().putString(AiProvider.GEMINI.storageKey, "garbage").commit()

        assertEquals("", store.apiKey(AiProvider.GEMINI))
    }

    @Test
    fun setApiKey_withABlankKey_removesIt() {
        store.setApiKey(AiProvider.GEMINI, "abc")
        store.setApiKey(AiProvider.GEMINI, "  ")

        assertFalse(prefs.contains(AiProvider.GEMINI.storageKey))
    }

    @Test
    fun providerAndEndpoint_defaultThenPersist() {
        assertEquals(AiProvider.GEMINI, store.selectedProvider)
        assertEquals(CustomEndpointConfig(), store.customEndpoint)

        store.selectedProvider = AiProvider.OTHER
        store.customEndpoint = CustomEndpointConfig("https://example.test/v1/chat/completions", "llama")

        val reopened = AiSettingsStore(prefs, reversingCipher)
        assertEquals(AiProvider.OTHER, reopened.selectedProvider)
        assertEquals("llama", reopened.customEndpoint.model)
    }

    @Test
    fun importLegacy_copiesOldSettingsWithoutOverwritingNewOnes() {
        store.setApiKey(AiProvider.OPENAI, "new-openai")

        store.importLegacy(
            legacyApiKey = { mapOf(AiProvider.GEMINI to "old-gemini", AiProvider.OPENAI to "old-openai")[it] },
            legacySelectedProvider = AiProvider.ANTHROPIC.name,
            legacyEndpoint = CustomEndpointConfig("https://old.test", "old-model"),
        )

        assertEquals("old-gemini", store.apiKey(AiProvider.GEMINI))
        assertEquals("new-openai", store.apiKey(AiProvider.OPENAI))
        assertEquals(AiProvider.ANTHROPIC, store.selectedProvider)
        assertEquals("old-model", store.customEndpoint.model)
    }
}
