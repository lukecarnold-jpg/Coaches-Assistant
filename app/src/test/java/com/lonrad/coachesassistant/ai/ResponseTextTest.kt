package com.lonrad.coachesassistant.ai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseTextTest {

    @Test
    fun gemini_joinsEveryPartOfTheFirstCandidate() {
        val json = JSONObject(
            """{"candidates":[{"content":{"parts":[{"text":"TITLE: A"},{"text":" and more"}]}}]}""",
        )

        assertEquals("TITLE: A and more", geminiResponseText(json))
    }

    @Test
    fun gemini_withNoCandidates_isEmpty() {
        // e.g. a prompt blocked by safety settings
        assertEquals("", geminiResponseText(JSONObject("""{"promptFeedback":{"blockReason":"SAFETY"}}""")))
    }

    @Test
    fun anthropic_skipsNonTextBlocks() {
        val json = JSONObject(
            """{"content":[{"type":"thinking","thinking":"hmm"},{"type":"text","text":"TITLE: B"}]}""",
        )

        assertEquals("TITLE: B", anthropicResponseText(json))
    }

    @Test
    fun openAi_readsTheFirstChoicesMessage() {
        val json = JSONObject("""{"choices":[{"message":{"role":"assistant","content":"TITLE: C"}}]}""")

        assertEquals("TITLE: C", openAiResponseText(json))
    }

    @Test
    fun eachProvider_reportsAReplyCutOffAtItsLengthLimit() {
        assertTrue(geminiWasCutOff(JSONObject("""{"candidates":[{"finishReason":"MAX_TOKENS"}]}""")))
        assertFalse(geminiWasCutOff(JSONObject("""{"candidates":[{"finishReason":"STOP"}]}""")))
        assertTrue(anthropicWasCutOff(JSONObject("""{"stop_reason":"max_tokens"}""")))
        assertFalse(anthropicWasCutOff(JSONObject("""{"stop_reason":"end_turn"}""")))
        assertTrue(openAiWasCutOff(JSONObject("""{"choices":[{"finish_reason":"length"}]}""")))
        assertFalse(openAiWasCutOff(JSONObject("""{"choices":[{"finish_reason":"stop"}]}""")))
    }
}
