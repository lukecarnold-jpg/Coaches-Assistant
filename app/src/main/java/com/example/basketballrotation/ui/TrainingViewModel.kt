package com.example.basketballrotation.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

class TrainingViewModel : ViewModel() {
    // Securely loaded from local.properties via build.gradle.kts
    private val apiKey = BuildConfig.GEMINI_API_KEY
    
    private val _uiState = MutableStateFlow<TrainingUiState>(TrainingUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val generativeModel by lazy {
        GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = apiKey,
        )
    }

    fun generateDrills(ageGroup: String) {
        if (apiKey.isBlank()) {
            _uiState.value = TrainingUiState.Error("Please add your GEMINI_API_KEY to local.properties and rebuild.")
            return
        }

        viewModelScope.launch {
            _uiState.value = TrainingUiState.Loading
            try {
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
}
