package com.lonrad.coachesassistant.ai

import com.lonrad.coachesassistant.R
import com.lonrad.coachesassistant.data.UserFacingException
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * The real request and reply handling, against a local server standing in for an
 * OpenAI-compatible endpoint (plain http is allowed because it's on this machine).
 */
class DrillTextGeneratorsTest {

    private val server = MockWebServer()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun generate(): String = runBlocking {
        CustomTextGenerator(CustomEndpointConfig(server.url("/v1/chat/completions").toString(), "local-model"))
            .generateText("prompt", apiKey = "")
    }

    private fun reply(code: Int = 200, body: String) {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }

    private fun failure(): UserFacingException = try {
        generate()
        fail("Expected the reply to be refused")
        throw AssertionError()
    } catch (e: UserFacingException) {
        e
    }

    @Test
    fun readsTheReplysText() {
        reply(body = """{"choices":[{"message":{"content":"TITLE: A"},"finish_reason":"stop"}]}""")

        assertEquals("TITLE: A", generate())
    }

    @Test
    fun aReplyThatIsntJson_namesTheServer() {
        reply(body = "<html>Sign in to the hotel Wi-Fi</html>")

        val e = failure()

        assertEquals(R.string.ai_unexpected_response, e.messageRes)
        assertEquals(listOf(server.hostName), e.formatArgs)
    }

    @Test
    fun aReplyCutOffAtTheLengthLimit_isRefused() {
        reply(body = """{"choices":[{"message":{"content":"TITLE: A\nDESCRIPTION: Half"},"finish_reason":"length"}]}""")

        assertEquals(R.string.drills_cut_off, failure().messageRes)
    }

    @Test
    fun geminiModelName_staysInsideTheModelsPath() {
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent",
            geminiUrl("gemini-3.8-flash").toString(),
        )
        // A slash or a dot-dot in the name is encoded as part of the one segment, so it can't reach another path.
        val escaped = geminiUrl("../../other/x").encodedPath
        assertEquals("/v1beta/models/..%2F..%2Fother%2Fx:generateContent", escaped)
    }

    @Test
    fun aRedirect_isReported_andTheKeyIsNotSentOn() {
        server.enqueue(
            MockResponse.Builder().code(302).addHeader("Location", server.url("/elsewhere").toString()).build(),
        )

        try {
            runBlocking {
                CustomTextGenerator(CustomEndpointConfig(server.url("/v1/chat/completions").toString(), "local-model"))
                    .generateText("prompt", apiKey = "secret")
            }
            fail("Expected the redirect to be reported")
        } catch (e: AiRequestException) {
            assertEquals(302, e.httpCode)
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun anErrorReply_carriesTheStatusAndTheApisMessage() {
        reply(code = 401, body = """{"error":{"message":"bad key"}}""")

        try {
            generate()
            fail("Expected the request to fail")
        } catch (e: AiRequestException) {
            assertEquals(401, e.httpCode)
            assertEquals("bad key", e.apiMessage)
        }
    }
}
