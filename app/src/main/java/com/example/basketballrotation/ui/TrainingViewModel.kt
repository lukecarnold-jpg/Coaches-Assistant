package com.example.basketballrotation.ui

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.basketballrotation.BuildConfig
import com.example.basketballrotation.ai.AiProvider
import com.example.basketballrotation.ai.BasketballDrill
import com.example.basketballrotation.ai.buildDrillPrompt
import com.example.basketballrotation.ai.parseDrills
import com.example.basketballrotation.ai.textGeneratorFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class TrainingUiState {
    object Idle : TrainingUiState()
    object Loading : TrainingUiState()
    data class Success(val drills: List<BasketballDrill>) : TrainingUiState()
    data class Error(val message: String) : TrainingUiState()
}

class TrainingViewModel(application: Application) : AndroidViewModel(application) {
    // API keys are secrets, so they're stored in an encrypted preferences file rather than
    // plain SharedPreferences. Older installs may still have a Gemini key in the legacy
    // plaintext file; migrateLegacyApiKey() moves it over once and wipes the plaintext copy.
    private val prefs: SharedPreferences = createEncryptedPrefs(application)

    init {
        migrateLegacyApiKey(application)
    }

    // A Gemini key saved in Settings takes priority; otherwise falls back to local.properties
    // via BuildConfig. Anthropic and OpenAI have no build-time fallback.
    val hasBuiltInApiKey: Boolean = BuildConfig.GEMINI_API_KEY.isNotBlank()

    private val _apiKeys = MutableStateFlow(loadAllKeys())
    val apiKeys = _apiKeys.asStateFlow()

    private val _selectedProvider = MutableStateFlow(loadSelectedProvider())
    val selectedProvider = _selectedProvider.asStateFlow()

    private val _uiState = MutableStateFlow<TrainingUiState>(TrainingUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private fun loadAllKeys(): Map<AiProvider, String> =
        AiProvider.entries.associateWith { prefs.getString(it.storageKey, null).orEmpty() }

    private fun loadSelectedProvider(): AiProvider {
        val raw = prefs.getString(KEY_SELECTED_PROVIDER, null)
        return AiProvider.entries.firstOrNull { it.name == raw } ?: AiProvider.GEMINI
    }

    fun selectProvider(provider: AiProvider) {
        prefs.edit { putString(KEY_SELECTED_PROVIDER, provider.name) }
        _selectedProvider.value = provider
    }

    fun updateApiKey(provider: AiProvider, key: String) {
        val trimmed = key.trim()
        prefs.edit { putString(provider.storageKey, trimmed) }
        _apiKeys.value = _apiKeys.value + (provider to trimmed)
    }

    fun clearApiKey(provider: AiProvider) {
        prefs.edit { remove(provider.storageKey) }
        _apiKeys.value = _apiKeys.value + (provider to "")
    }

    private fun effectiveApiKey(provider: AiProvider): String {
        val saved = _apiKeys.value[provider].orEmpty()
        return when {
            saved.isNotBlank() -> saved
            provider == AiProvider.GEMINI -> BuildConfig.GEMINI_API_KEY
            else -> ""
        }
    }

    fun generateDrills(ageGroup: String) {
        val provider = _selectedProvider.value
        val apiKey = effectiveApiKey(provider)
        if (apiKey.isBlank()) {
            _uiState.value = TrainingUiState.Error(
                "Add your ${provider.displayName} API key in Settings to generate drills.",
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = TrainingUiState.Loading
            try {
                val text = textGeneratorFor(provider).generateText(buildDrillPrompt(ageGroup), apiKey)
                val drills = parseDrills(text)
                _uiState.value = if (drills.isEmpty()) {
                    TrainingUiState.Error("Failed to parse drills. Try again.")
                } else {
                    TrainingUiState.Success(drills)
                }
            } catch (e: Exception) {
                _uiState.value = TrainingUiState.Error(describeError(provider, e))
            }
        }
    }

    private fun describeError(provider: AiProvider, e: Exception): String {
        val message = e.message.orEmpty()
        return when {
            provider == AiProvider.GEMINI && message.contains("NOT_FOUND") ->
                "The selected Gemini model was not found. It may have been retired; please check for an app update."
            else -> e.localizedMessage ?: "Unknown error occurred"
        }
    }

    private companion object {
        const val LEGACY_PREFS_NAME = "training_prefs"
        const val PREFS_NAME = "training_prefs_secure"
        const val LEGACY_GEMINI_KEY = "gemini_api_key"
        const val KEY_SELECTED_PROVIDER = "selected_ai_provider"

        fun createEncryptedPrefs(application: Application): SharedPreferences {
            val masterKey = MasterKey.Builder(application)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                application,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }

    private fun migrateLegacyApiKey(application: Application) {
        val legacyPrefs = application.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        val legacyKey = legacyPrefs.getString(LEGACY_GEMINI_KEY, null)
        if (!legacyKey.isNullOrBlank() && prefs.getString(AiProvider.GEMINI.storageKey, null).isNullOrBlank()) {
            prefs.edit { putString(AiProvider.GEMINI.storageKey, legacyKey) }
        }
        if (legacyPrefs.contains(LEGACY_GEMINI_KEY)) {
            legacyPrefs.edit { clear() }
        }
    }
}
