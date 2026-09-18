package com.example.basketballrotation.ai

import com.google.ai.client.generativeai.GenerativeModel
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

/** Generates raw model text for a prompt using one provider's API. */
interface DrillTextGenerator {
    suspend fun generateText(prompt: String, apiKey: String): String
}

fun textGeneratorFor(provider: AiProvider): DrillTextGenerator = when (provider) {
    AiProvider.GEMINI -> GeminiTextGenerator()
    AiProvider.ANTHROPIC -> AnthropicTextGenerator()
    AiProvider.OPENAI -> OpenAiTextGenerator()
}

private val httpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()

private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

class GeminiTextGenerator : DrillTextGenerator {
    override suspend fun generateText(prompt: String, apiKey: String): String {
        val model = GenerativeModel(modelName = "gemini-3.8-flash", apiKey = apiKey)
        return model.generateContent(prompt).text.orEmpty()
    }
}

class AnthropicTextGenerator : DrillTextGenerator {
    override suspend fun generateText(prompt: String, apiKey: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("model", "claude-sonnet-5")
            put("max_tokens", 1024)
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

        executeForText(request) { json ->
            json.optJSONArray("content")?.optJSONObject(0)?.optString("text").orEmpty()
        }
    }
}

class OpenAiTextGenerator : DrillTextGenerator {
    override suspend fun generateText(prompt: String, apiKey: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("model", "gpt-5.5")
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
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()

        executeForText(request) { json ->
            json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
        }
    }
}

private fun executeForText(request: Request, extract: (JSONObject) -> String): String {
    httpClient.newCall(request).execute().use { response ->
        val bodyString = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            val apiMessage = runCatching {
                JSONObject(bodyString).optJSONObject("error")?.optString("message")
            }.getOrNull()
            throw IOException(apiMessage?.takeIf { it.isNotBlank() } ?: "Request failed (HTTP ${response.code})")
        }

        return extract(JSONObject(bodyString))
    }
}
