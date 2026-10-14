package com.lonrad.coachesassistant.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkAccessTest {

    private val ollama = CustomEndpointConfig("http://192.168.1.20:11434/v1/chat/completions", "gemma")
    private val hosted = CustomEndpointConfig("https://openrouter.ai/api/v1/chat/completions", "some-model")

    @Test
    fun neededForAnOtherEndpointOnTheLocalNetwork_onAndroid17AndLater() {
        assertTrue(needsLocalNetworkPermission(AiProvider.OTHER, ollama, sdkInt = 37))
        assertTrue(needsLocalNetworkPermission(AiProvider.OTHER, ollama, sdkInt = 38))
        // https to a local address needs it too; the rule is about where the server is.
        assertTrue(needsLocalNetworkPermission(AiProvider.OTHER, CustomEndpointConfig("https://nas.local/v1", "m"), sdkInt = 37))
    }

    @Test
    fun notNeededOnOlderAndroid_forHostedEndpoints_orForOtherProviders() {
        assertFalse(needsLocalNetworkPermission(AiProvider.OTHER, ollama, sdkInt = 36))
        assertFalse(needsLocalNetworkPermission(AiProvider.OTHER, hosted, sdkInt = 37))
        assertFalse(needsLocalNetworkPermission(AiProvider.GEMINI, ollama, sdkInt = 37))
    }

    @Test
    fun notNeededForAMissingOrMalformedEndpoint() {
        assertFalse(needsLocalNetworkPermission(AiProvider.OTHER, CustomEndpointConfig(), sdkInt = 37))
        assertFalse(needsLocalNetworkPermission(AiProvider.OTHER, CustomEndpointConfig("192.168.1.20:11434", "m"), sdkInt = 37))
    }
}
