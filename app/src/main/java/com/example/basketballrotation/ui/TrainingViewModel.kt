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
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BasketballDrill(
    val title: String,
    val description: String,
    val teachingPoints: String,
)

sealed class TrainingUiState {
    object Idle : TrainingUiState()
    object Loading : TrainingUiState()
    data class Success(val drills: List<BasketballDrill>) : TrainingUiState()
    data class Error(val message: String) : TrainingUiState()
}

class TrainingViewModel(application: Application) : AndroidViewModel(application) {
    // The API key is a secret, so it is stored in an encrypted preferences file rather than
    // plain SharedPreferences. Older installs may still have the key in the legacy plaintext
    // file; migrateLegacyApiKey() moves it over once and wipes the plaintext copy.
    private val prefs: SharedPreferences = createEncryptedPrefs(application)

    init {
        migrateLegacyApiKey(application)
    }

    // A key saved in Settings takes priority; otherwise falls back to local.properties via BuildConfig.
    val hasBuiltInApiKey: Boolean = BuildConfig.GEMINI_API_KEY.isNotBlank()

    private val _savedApiKey = MutableStateFlow(prefs.getString(KEY_API_KEY, null).orEmpty())
    val savedApiKey = _savedApiKey.asStateFlow()

    private val effectiveApiKey: String
        get() = _savedApiKey.value.ifBlank { BuildConfig.GEMINI_API_KEY }

    private val _uiState = MutableStateFlow<TrainingUiState>(TrainingUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun updateApiKey(key: String) {
        val trimmed = key.trim()
        prefs.edit { putString(KEY_API_KEY, trimmed) }
        _savedApiKey.value = trimmed
    }

    fun clearApiKey() {
        prefs.edit { remove(KEY_API_KEY) }
        _savedApiKey.value = ""
    }

    fun generateDrills(ageGroup: String) {
        val apiKey = effectiveApiKey
        if (apiKey.isBlank()) {
            _uiState.value = TrainingUiState.Error("Add your Gemini API key in Settings to generate drills.")
            return
        }

        viewModelScope.launch {
            _uiState.value = TrainingUiState.Loading
            try {
                val generativeModel = GenerativeModel(
                    modelName = "gemini-3.8-flash",
                    apiKey = apiKey,
                )
                val prompt = "Generate exactly 3 age-appropriate basketball drills for $ageGroup players. " +
                        "Format each drill with a 'TITLE:', 'DESCRIPTION:', and 'TEACHING POINTS:' on new lines. " +
                        "Keep descriptions concise and appropriate for the age group."

                val response = generativeModel.generateContent(prompt)
                val text = response.text ?: ""

                val drills = parseDrills(text)
                if (drills.isEmpty()) {
                    _uiState.value = TrainingUiState.Error("Failed to parse drills. Try again.")
                } else {
                    _uiState.value = TrainingUiState.Success(drills)
                }
            } catch (e: Exception) {
                val errorMessage = if (e.message?.contains("NOT_FOUND") == true) {
                    "The selected AI model was not found. Please ensure you are using a supported Gemini model name."
                } else {
                    e.localizedMessage ?: "Unknown error occurred"
                }
                _uiState.value = TrainingUiState.Error(errorMessage)
            }
        }
    }

    private fun parseDrills(text: String): List<BasketballDrill> {
        val drills = mutableListOf<BasketballDrill>()
        val blocks = text.split(Regex("TITLE:", RegexOption.IGNORE_CASE))

        blocks.forEach { block ->
            if (block.isBlank()) return@forEach

            val lines = block.trim().lines()
            val title = lines.firstOrNull()?.trim() ?: ""

            val descriptionStart = block.indexOf("DESCRIPTION:", ignoreCase = true)
            val teachingPointsStart = block.indexOf("TEACHING POINTS:", ignoreCase = true)

            if ((descriptionStart != -1) && (teachingPointsStart != -1)) {
                val description = block.substring(descriptionStart + 12, teachingPointsStart).trim()
                val teachingPoints = block.substring(teachingPointsStart + 16).trim()

                if (title.isNotEmpty() && description.isNotEmpty()) {
                    drills.add(BasketballDrill(title, description, teachingPoints))
                }
            }
        }
        return drills.take(3)
    }

    private companion object {
        const val LEGACY_PREFS_NAME = "training_prefs"
        const val PREFS_NAME = "training_prefs_secure"
        const val KEY_API_KEY = "gemini_api_key"

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
        val legacyKey = legacyPrefs.getString(KEY_API_KEY, null)
        if (!legacyKey.isNullOrBlank() && prefs.getString(KEY_API_KEY, null).isNullOrBlank()) {
            prefs.edit { putString(KEY_API_KEY, legacyKey) }
        }
        if (legacyPrefs.contains(KEY_API_KEY)) {
            legacyPrefs.edit { clear() }
        }
    }
}
