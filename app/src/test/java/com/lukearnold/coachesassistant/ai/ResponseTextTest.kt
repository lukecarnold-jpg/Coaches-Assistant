package com.lukearnold.coachesassistant.ai

import org.json.JSONObject
import org.junit.Assert.assertEquals
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
}
