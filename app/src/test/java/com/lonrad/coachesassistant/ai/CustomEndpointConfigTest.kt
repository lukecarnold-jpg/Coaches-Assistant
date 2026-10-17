package com.lonrad.coachesassistant.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomEndpointConfigTest {

    @Test
    fun localNetworkHosts_areRecognised() {
        listOf(
            "localhost", "127.0.0.1", "10.0.2.2", "172.16.0.5", "172.31.255.1", "192.168.1.20",
            "169.254.10.1", "100.100.1.1", "::1", "fd12:3456::1", "fe80::1", "ollama.local",
            "server.lan", "nas.home.arpa", "gpu-box", "LOCALHOST.",
        ).forEach { assertTrue("$it should count as local", isLocalNetworkHost(it)) }
    }

    @Test
    fun publicHosts_areNotLocal() {
        listOf(
            "api.openai.com", "8.8.8.8", "172.32.0.1", "192.169.1.1", "100.128.0.1", "2001:db8::1",
            "local.example.com", "999.1.1.1",
        ).forEach { assertFalse("$it should not count as local", isLocalNetworkHost(it)) }
    }

    @Test
    fun endpointHost_isTheHostNameOrNullForNonUrls() {
        assertEquals("api.example.com", endpointHost("  https://api.example.com/v1/chat/completions "))
        assertEquals("192.168.1.20", endpointHost("http://192.168.1.20:11434/v1/chat/completions"))
        assertNull(endpointHost("not a url"))
        assertNull(endpointHost(""))
    }

    @Test
    fun https_isAlwaysAllowed() {
        assertNull(endpointUrlProblem("https://openrouter.ai/api/v1/chat/completions"))
        assertNull(endpointUrlProblem("https://192.168.1.20:8443/v1/chat/completions"))
    }

    @Test
    fun plainHttp_isAllowedOnlyForLocalServers() {
        assertNull(endpointUrlProblem("http://192.168.1.20:11434/v1/chat/completions"))
        assertNull(endpointUrlProblem("http://[::1]:11434/v1/chat/completions"))
        assertNotNull(endpointUrlProblem("http://api.example.com/v1/chat/completions"))
    }

    @Test
    fun blankUrl_isNotReported_butMalformedOnesAre() {
        assertNull(endpointUrlProblem("  "))
        assertNotNull(endpointUrlProblem("192.168.1.20:11434/v1"))
        assertNotNull(endpointUrlProblem("ftp://files.local/v1"))
    }
}
