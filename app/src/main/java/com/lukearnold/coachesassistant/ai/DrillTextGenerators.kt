package com.lukearnold.coachesassistant.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A provider's API rejected a request; [httpCode] is the HTTP status it answered with. */
class AiRequestException(val httpCode: Int, message: String) : IOException(message)

/** Generates raw model text for a prompt using one provider's API. */
interface DrillTextGenerator {
    suspend fun generateText(prompt: String, apiKey: String): String
}

fun textGeneratorFor(provider: AiProvider, customEndpoint: CustomEndpointConfig = CustomEndpointConfig()): DrillTextGenerator =
    when (provider) {
        AiProvider.GEMINI -> GeminiTextGenerator()
        AiProvider.ANTHROPIC -> AnthropicTextGenerator()
        AiProvider.OPENAI -> OpenAiTextGenerator()
        AiProvider.OTHER -> CustomTextGenerator(customEndpoint)
    }

private val httpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()

private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

/**
 * Calls the Gemini API's REST endpoint directly, like the Anthropic and OpenAI generators, rather
 * than through Google's deprecated Android client SDK.
 */
class GeminiTextGenerator : DrillTextGenerator {
    override suspend fun generateText(prompt: String, apiKey: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt))),
                ),
            )
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent")
            .addHeader("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()

        executeForText(request, ::geminiResponseText)
    }
}

class AnthropicTextGenerator : DrillTextGenerator {
    override suspend fun generateText(prompt: String, apiKey: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("model", "claude-sonnet-5")
            put("max_tokens", 2048)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    },
                ),
            )
        }

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()

        executeForText(request, ::anthropicResponseText)
    }
}

class OpenAiTextGenerator : DrillTextGenerator {
    override suspend fun generateText(prompt: String, apiKey: String): String =
        openAiCompatibleRequest(
            url = "https://api.openai.com/v1/chat/completions",
            apiKey = apiKey,
            model = "gpt-5.5",
            prompt = prompt,
        )
}

/**
 * For [AiProvider.OTHER]: any provider whose chat endpoint follows the same request/response
 * shape as OpenAI's `/v1/chat/completions` — which covers most third-party and self-hosted
 * options (OpenRouter, Groq, Mistral, Azure OpenAI, Ollama, vLLM, etc.).
 */
class CustomTextGenerator(private val config: CustomEndpointConfig) : DrillTextGenerator {
    override suspend fun generateText(prompt: String, apiKey: String): String {
        if (config.endpointUrl.isBlank() || config.model.isBlank()) {
            throw IOException("Set an endpoint URL and model name for the Other provider in Settings.")
        }
        return openAiCompatibleRequest(
            url = config.endpointUrl,
            apiKey = apiKey,
            model = config.model,
            prompt = prompt,
        )
    }
}

/** Shared request/response shape for any OpenAI-compatible chat-completions endpoint. */
private suspend fun openAiCompatibleRequest(url: String, apiKey: String, model: String, prompt: String): String =
    withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("model", model)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    },
                ),
            )
        }

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()

        executeForText(request, ::openAiResponseText)
    }

private fun executeForText(request: Request, extract: (JSONObject) -> String): String {
    httpClient.newCall(request).execute().use { response ->
        val bodyString = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            val apiMessage = runCatching {
                JSONObject(bodyString).optJSONObject("error")?.optString("message")
            }.getOrNull()
            throw AiRequestException(
                response.code,
                apiMessage?.takeIf { it.isNotBlank() } ?: "Request failed (HTTP ${response.code})",
            )
        }

        return extract(JSONObject(bodyString))
    }
}

/** The generated text from a Gemini `generateContent` response: every part of the first candidate. */
internal fun geminiResponseText(json: JSONObject): String {
    val parts = json.optJSONArray("candidates")
        ?.optJSONObject(0)
        ?.optJSONObject("content")
        ?.optJSONArray("parts")
        ?: return ""
    return (0 until parts.length()).joinToString("") { parts.optJSONObject(it)?.optString("text").orEmpty() }
}

/**
 * The generated text from an Anthropic Messages response. Content is a list of typed blocks and
 * the first isn't guaranteed to be text, so every text block is joined rather than assuming index 0.
 */
internal fun anthropicResponseText(json: JSONObject): String {
    val content = json.optJSONArray("content") ?: return ""
    return (0 until content.length())
        .mapNotNull { content.optJSONObject(it) }
        .filter { it.optString("type") == "text" }
        .joinToString("") { it.optString("text") }
}

/** The generated text from an OpenAI-compatible chat-completions response. */
internal fun openAiResponseText(json: JSONObject): String =
    json.optJSONArray("choices")
        ?.optJSONObject(0)
        ?.optJSONObject("message")
        ?.optString("content")
        .orEmpty()
