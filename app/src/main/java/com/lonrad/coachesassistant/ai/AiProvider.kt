package com.lonrad.coachesassistant.ai

import androidx.annotation.StringRes
import com.lonrad.coachesassistant.R

/**
 * AI providers that can generate training drills. Each requires the user's own API key entered
 * in Settings; there is no "log in with your Gemini/Claude/ChatGPT account" option. Google and
 * Anthropic have both made it a Terms of Service violation (with real account suspensions,
 * including paying subscribers) to use a consumer subscription's login in a third-party app
 * instead of billed API access, and OpenAI's equivalent is scoped to its own Codex tooling only.
 */
enum class AiProvider(
    val storageKey: String,
    @param:StringRes val label: Int,
    @param:StringRes val keyHint: Int,
    /**
     * The model used unless the user names another in Settings, so a retired model can be swapped
     * out without an app update. Null for [OTHER], whose model is part of its endpoint settings.
     */
    val defaultModel: String?,
) {
    GEMINI(
        storageKey = "gemini_api_key",
        label = R.string.provider_gemini,
        keyHint = R.string.provider_gemini_key_hint,
        defaultModel = "gemini-3.8-flash",
    ),
    ANTHROPIC(
        storageKey = "anthropic_api_key",
        label = R.string.provider_anthropic,
        keyHint = R.string.provider_anthropic_key_hint,
        defaultModel = "claude-sonnet-5",
    ),
    OPENAI(
        storageKey = "openai_api_key",
        label = R.string.provider_openai,
        keyHint = R.string.provider_openai_key_hint,
        defaultModel = "gpt-5.5",
    ),
    OTHER(
        storageKey = "other_api_key",
        label = R.string.provider_other,
        keyHint = R.string.provider_other_key_hint,
        defaultModel = null,
    ),
}
