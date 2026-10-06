package com.lukearnold.coachesassistant.ui

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.lukearnold.coachesassistant.R
import com.lukearnold.coachesassistant.ai.AiProvider
import com.lukearnold.coachesassistant.ai.AiRequestException
import com.lukearnold.coachesassistant.ai.AiSettingsStore
import com.lukearnold.coachesassistant.ai.CustomEndpointConfig
import com.lukearnold.coachesassistant.ai.DrillTextGenerator
import com.lukearnold.coachesassistant.ai.SecretCipher
import com.lukearnold.coachesassistant.data.Sport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [TrainingViewModel] with a fake generator in place of the real network calls, so what it sends
 * (key and model) and how it reports each outcome can be checked directly.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrainingViewModelTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val prefs = application.getSharedPreferences("training_vm_test", Context.MODE_PRIVATE)

    // Robolectric has no Android Keystore; the stored form doesn't matter here.
    private val plainCipher = object : SecretCipher {
        override fun encrypt(plaintext: String) = plaintext
        override fun decrypt(encoded: String) = encoded
    }

    private data class Call(val provider: AiProvider, val model: String, val apiKey: String)

    private val calls = mutableListOf<Call>()
    private var reply: () -> String = { WELL_FORMED_DRILLS }

    private lateinit var store: AiSettingsStore

    @Before
    fun setUp() {
        prefs.edit().clear().commit()
        store = AiSettingsStore(prefs, plainCipher)
    }

    private fun viewModel(builtInGeminiKey: String = ""): TrainingViewModel = TrainingViewModel(
        application,
        store,
        generatorFor = { provider: AiProvider, _: CustomEndpointConfig, model: String ->
            object : DrillTextGenerator {
                override suspend fun generateText(prompt: String, apiKey: String): String {
                    calls += Call(provider, model, apiKey)
                    return reply()
                }
            }
        },
        builtInGeminiKey = builtInGeminiKey,
    )

    private fun TrainingViewModel.generate(): TrainingUiState {
        generateDrills(Sport.BASKETBALL, "Under 10's", "en")
        shadowOf(Looper.getMainLooper()).idle()
        return uiState.value
    }

    @Test
    fun withoutAKey_explainsOneIsNeededAndCallsNothing() {
        val state = viewModel().generate()

        assertEquals(TrainingUiState.Error(uiText(R.string.add_api_key_to_generate, uiText(AiProvider.GEMINI.label))), state)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun usesTheSavedKeyAndTheModelChosenInSettings() {
        val vm = viewModel()
        vm.selectProvider(AiProvider.ANTHROPIC)
        vm.updateApiKey(AiProvider.ANTHROPIC, " sk-test ")
        vm.updateModel(AiProvider.ANTHROPIC, "claude-next")

        val state = vm.generate()

        assertEquals(listOf(Call(AiProvider.ANTHROPIC, "claude-next", "sk-test")), calls)
        assertTrue(state is TrainingUiState.Success && state.drills.size == 2)
    }

    @Test
    fun geminiFallsBackToTheBuiltInKey() {
        viewModel(builtInGeminiKey = "built-in").generate()

        assertEquals("built-in", calls.single().apiKey)
        assertEquals("", calls.single().model) // the generator then uses the provider's default
    }

    @Test
    fun aRetiredGeminiModel_pointsToTheModelSetting() {
        reply = { throw AiRequestException(404, "models/gemini-old is not found") }
        val vm = viewModel()
        vm.updateApiKey(AiProvider.GEMINI, "key")

        assertEquals(TrainingUiState.Error(uiText(R.string.gemini_model_not_found)), vm.generate())
    }

    @Test
    fun anApiError_showsTheProvidersOwnMessage() {
        reply = { throw AiRequestException(401, "invalid x-api-key") }
        val vm = viewModel()
        vm.selectProvider(AiProvider.OPENAI)
        vm.updateApiKey(AiProvider.OPENAI, "key")

        assertEquals(TrainingUiState.Error(UiText.Plain("invalid x-api-key")), vm.generate())
    }

    @Test
    fun aReplyWithNoDrillsInIt_saysItCouldntBeRead() {
        reply = { "Sorry, I can't help with that." }
        val vm = viewModel()
        vm.updateApiKey(AiProvider.GEMINI, "key")

        assertEquals(TrainingUiState.Error(uiText(R.string.drills_parse_failed)), vm.generate())
    }

    private companion object {
        val WELL_FORMED_DRILLS = """
            TITLE: Dribble Relay
            DESCRIPTION: Players dribble cones and back.
            TEACHING POINTS: Keep head up.

            TITLE: Passing Circle
            DESCRIPTION: Players pass around a circle.
            TEACHING POINTS: Chest pass with both hands.
        """.trimIndent()
    }
}
