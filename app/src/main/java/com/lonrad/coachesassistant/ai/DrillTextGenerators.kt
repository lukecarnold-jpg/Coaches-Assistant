package com.lonrad.coachesassistant.ai

import com.lonrad.coachesassistant.R
import com.lonrad.coachesassistant.data.UserFacingException
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * A provider's API rejected a request: [httpCode] is the HTTP status it answered with, and
 * [apiMessage] the explanation it gave, if any (in the provider's language, not translated).
 */
class AiRequestException(val httpCode: Int, val apiMessage: String?) :
    IOException(apiMessage ?: "Request failed (HTTP $httpCode)")

/** Generates raw model text for a prompt using one provider's API. */
interface DrillTextGenerator {
    suspend fun generateText(prompt: String, apiKey: String): String
}

/**
 * The generator for [provider], using [model] (blank for the provider's default model). The Other
 * provider's model comes from [customEndpoint] instead.
 */
fun textGeneratorFor(
    provider: AiProvider,
    customEndpoint: CustomEndpointConfig = CustomEndpointConfig(),
    model: String = "",
): DrillTextGenerator {
    val chosenModel = model.trim().ifBlank { provider.defaultModel.orEmpty() }
    return when (provider) {
        AiProvider.GEMINI -> GeminiTextGenerator(chosenModel)
        AiProvider.ANTHROPIC -> AnthropicTextGenerator(chosenModel)
        AiProvider.OPENAI -> OpenAiTextGenerator(chosenModel)
        AiProvider.OTHER -> CustomTextGenerator(customEndpoint)
    }
}

private val httpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    // Generous, because a local server (e.g. Ollama) loads its model on the first request after
    // being idle, which can take a minute on its own before any text comes back.
    .readTimeout(120, TimeUnit.SECONDS)
    // The key travels in a header, and OkHttp would resend it to wherever a redirect points. None of
    // the providers' APIs redirect, so a redirect is reported as an error rather than followed.
    .followRedirects(false)
    .followSslRedirects(false)
    // The platform allows cleartext so the Other provider can reach local servers (see
    // network_security_config.xml); this enforces the local-only part on every request actually
    // sent, including redirects, so an https endpoint can't be redirected to plain http.
    .addNetworkInterceptor { chain ->
        val url = chain.request().url
        if (!url.isHttps && !isLocalNetworkHost(url.host)) {
            throw UserFacingException(
                R.string.refusing_plain_http,
                listOf(url.host),
                "Refusing to send a request over plain http to ${url.host}, which isn't on the local network.",
            )
        }
        chain.proceed(chain.request())
    }
    .build()

private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

/** The most of a reply that's read. A drill reply is a few kilobytes, so this only stops a runaway server. */
private const val MAX_REPLY_BYTES = 1L shl 20

/**
 * The generateContent URL for [model]. The model name is one path segment, so a name containing `/`
 * or `..` stays inside the models path rather than reaching another endpoint on the same host.
 */
internal fun geminiUrl(model: String): HttpUrl = "https://generativelanguage.googleapis.com/".toHttpUrl().newBuilder()
    .addPathSegment("v1beta")
    .addPathSegment("models")
    .addPathSegment("$model:generateContent")
    .build()

/**
 * Calls the Gemini API's REST endpoint directly, like the Anthropic and OpenAI generators, rather
 * than through Google's deprecated Android client SDK.
 */
class GeminiTextGenerator(private val model: String) : DrillTextGenerator {
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
            .url(geminiUrl(model))
            .addHeader("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()

        executeForText(request, ::geminiResponseText, ::geminiWasCutOff)
    }
}

class AnthropicTextGenerator(private val model: String) : DrillTextGenerator {
    override suspend fun generateText(prompt: String, apiKey: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("model", model)
            // Room for three drills with headroom, since the translated languages take more
            // tokens than English; a reply that still runs out is reported (see anthropicWasCutOff).
            put("max_tokens", 4096)
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

        executeForText(request, ::anthropicResponseText, ::anthropicWasCutOff)
    }
}

class OpenAiTextGenerator(private val model: String) : DrillTextGenerator {
    override suspend fun generateText(prompt: String, apiKey: String): String =
        openAiCompatibleRequest(
            url = "https://api.openai.com/v1/chat/completions",
            apiKey = apiKey,
            model = model,
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
            throw UserFacingException(R.string.other_provider_not_set_up)
        }
        endpointUrlProblem(config.endpointUrl)?.let { throw UserFacingException(it) }
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
            // Local servers such as Ollama usually don't take a key, so the header is optional.
            .apply { if (apiKey.isNotBlank()) addHeader("Authorization", "Bearer $apiKey") }
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()

        executeForText(request, ::openAiResponseText, ::openAiWasCutOff)
    }

/**
 * Sends [request] and reads the text out of the reply with [extract]. The call is cancelled along
 * with the coroutine (e.g. Cancel on the Training tab) rather than running on to the read timeout.
 * A reply that [wasCutOff] at the model's length limit is refused, since its last drill would be
 * missing its end.
 */
private suspend fun executeForText(
    request: Request,
    extract: (JSONObject) -> String,
    wasCutOff: (JSONObject) -> Boolean,
): String {
    httpClient.newCall(request).executeAsync().use { response ->
        val bodyString = withContext(Dispatchers.IO) {
            response.body.source().use { source ->
                source.request(MAX_REPLY_BYTES + 1)
                if (source.buffer.size > MAX_REPLY_BYTES) throw UserFacingException(R.string.ai_reply_too_large)
                source.readUtf8()
            }
        }

        if (!response.isSuccessful) {
            val apiMessage = runCatching {
                JSONObject(bodyString).optJSONObject("error")?.optString("message")
            }.getOrNull()
            throw AiRequestException(response.code, apiMessage?.takeIf { it.isNotBlank() })
        }

        // A success that isn't JSON at all, e.g. a web page from a wrong endpoint URL or a captive
        // portal, rather than a generic "something went wrong".
        val json = try {
            JSONObject(bodyString)
        } catch (_: JSONException) {
            throw UserFacingException(
                R.string.ai_unexpected_response,
                listOf(request.url.host),
                "The reply from ${request.url.host} wasn't JSON.",
            )
        }
        if (wasCutOff(json)) throw UserFacingException(R.string.drills_cut_off)
        return extract(json)
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

/** Whether a Gemini reply stopped at its output token limit. */
internal fun geminiWasCutOff(json: JSONObject): Boolean =
    json.optJSONArray("candidates")?.optJSONObject(0)?.optString("finishReason") == "MAX_TOKENS"

/** Whether an Anthropic reply stopped at `max_tokens`. */
internal fun anthropicWasCutOff(json: JSONObject): Boolean = json.optString("stop_reason") == "max_tokens"

/** Whether an OpenAI-compatible reply stopped at its length limit. */
internal fun openAiWasCutOff(json: JSONObject): Boolean =
    json.optJSONArray("choices")?.optJSONObject(0)?.optString("finish_reason") == "length"
