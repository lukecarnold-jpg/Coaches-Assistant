package com.lukearnold.coachesassistant.ui

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lukearnold.coachesassistant.BuildConfig
import com.lukearnold.coachesassistant.R
import com.lukearnold.coachesassistant.ai.AiProvider
import com.lukearnold.coachesassistant.ai.AiRequestException
import com.lukearnold.coachesassistant.ai.AiSettingsStore
import com.lukearnold.coachesassistant.ai.Drill
import com.lukearnold.coachesassistant.ai.CustomEndpointConfig
import com.lukearnold.coachesassistant.ai.buildDrillPrompt
import com.lukearnold.coachesassistant.ai.hasLocalNetworkPermission
import com.lukearnold.coachesassistant.ai.needsLocalNetworkPermission
import com.lukearnold.coachesassistant.ai.parseDrills
import com.lukearnold.coachesassistant.ai.textGeneratorFor
import com.lukearnold.coachesassistant.data.Sport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class TrainingUiState {
    object Idle : TrainingUiState()
    object Loading : TrainingUiState()
    data class Success(val drills: List<Drill>) : TrainingUiState()
    data class Error(val message: UiText) : TrainingUiState()
}

class TrainingViewModel(application: Application) : AndroidViewModel(application) {
    // API keys are encrypted with an Android Keystore key; see AiSettingsStore, which also moves
    // settings over from the storage older versions of the app used.
    private val store = AiSettingsStore.create(application)

    // A Gemini key saved in Settings takes priority; otherwise falls back to local.properties
    // via BuildConfig. Anthropic and OpenAI have no build-time fallback.
    val hasBuiltInApiKey: Boolean = BuildConfig.GEMINI_API_KEY.isNotBlank()

    private val _apiKeys = MutableStateFlow(AiProvider.entries.associateWith(store::apiKey))
    val apiKeys = _apiKeys.asStateFlow()

    private val _modelOverrides = MutableStateFlow(AiProvider.entries.associateWith(store::modelOverride))

    /** Each provider's model chosen in Settings, or "" for its default (see AiProvider.defaultModel). */
    val modelOverrides = _modelOverrides.asStateFlow()

    private val _selectedProvider = MutableStateFlow(store.selectedProvider)
    val selectedProvider = _selectedProvider.asStateFlow()

    private val _customEndpoint = MutableStateFlow(store.customEndpoint)
    val customEndpoint = _customEndpoint.asStateFlow()

    private val _uiState = MutableStateFlow<TrainingUiState>(TrainingUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun selectProvider(provider: AiProvider) {
        store.selectedProvider = provider
        _selectedProvider.value = provider
    }

    fun updateCustomEndpoint(config: CustomEndpointConfig) {
        val trimmed = CustomEndpointConfig(config.endpointUrl.trim(), config.model.trim())
        store.customEndpoint = trimmed
        _customEndpoint.value = trimmed
    }

    fun updateApiKey(provider: AiProvider, key: String) {
        val trimmed = key.trim()
        store.setApiKey(provider, trimmed)
        _apiKeys.value = _apiKeys.value + (provider to trimmed)
    }

    fun updateModel(provider: AiProvider, model: String) {
        val trimmed = model.trim()
        store.setModelOverride(provider, trimmed)
        _modelOverrides.value = _modelOverrides.value + (provider to trimmed)
    }

    fun clearApiKey(provider: AiProvider) {
        store.clearApiKey(provider)
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

    /** Whether the Other provider's local server can't be reached until the user grants local network access. */
    fun localNetworkPermissionNeeded(): Boolean =
        needsLocalNetworkPermission(_selectedProvider.value, _customEndpoint.value, Build.VERSION.SDK_INT) &&
            !getApplication<Application>().hasLocalNetworkPermission()

    /** Explains why drills can't be generated after the user declines local network access. */
    fun reportLocalNetworkDenied() {
        _uiState.value = TrainingUiState.Error(uiText(R.string.local_network_denied))
    }

    /** Asks for drills in [languageTag], the language the app is showing (see buildDrillPrompt). */
    fun generateDrills(sport: Sport, ageGroup: String, languageTag: String) {
        val provider = _selectedProvider.value
        val apiKey = effectiveApiKey(provider)
        // The Other provider may be a local server that doesn't need a key.
        if (apiKey.isBlank() && provider != AiProvider.OTHER) {
            _uiState.value = TrainingUiState.Error(uiText(R.string.add_api_key_to_generate, uiText(provider.label)))
            return
        }
        val customEndpoint = _customEndpoint.value
        if (provider == AiProvider.OTHER && (customEndpoint.endpointUrl.isBlank() || customEndpoint.model.isBlank())) {
            _uiState.value = TrainingUiState.Error(uiText(R.string.other_provider_not_set_up))
            return
        }

        viewModelScope.launch {
            _uiState.value = TrainingUiState.Loading
            try {
                val text = textGeneratorFor(provider, customEndpoint, _modelOverrides.value[provider].orEmpty())
                    .generateText(buildDrillPrompt(sport, ageGroup, languageTag), apiKey)
                val drills = parseDrills(text)
                _uiState.value = if (drills.isEmpty()) {
                    TrainingUiState.Error(uiText(R.string.drills_parse_failed))
                } else {
                    TrainingUiState.Success(drills)
                }
            } catch (e: Exception) {
                _uiState.value = TrainingUiState.Error(describeError(provider, e))
            }
        }
    }

    private fun describeError(provider: AiProvider, e: Exception): UiText = when {
        provider == AiProvider.GEMINI && e is AiRequestException && e.httpCode == 404 -> uiText(R.string.gemini_model_not_found)
        e is AiRequestException -> e.apiMessage?.let { UiText.Plain(it) } ?: uiText(R.string.request_failed_http, e.httpCode)
        else -> e.toUiText(fallback = R.string.unknown_error)
    }
}
