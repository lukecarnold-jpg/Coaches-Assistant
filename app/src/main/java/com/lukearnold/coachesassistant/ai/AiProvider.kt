package com.lukearnold.coachesassistant.ai

import androidx.annotation.StringRes
import com.lukearnold.coachesassistant.R

/**
 * AI providers that can generate training drills. Each requires the user's own API key entered
 * in Settings; there is no "log in with your Gemini/Claude/ChatGPT account" option. Google and
 * Anthropic have both made it a Terms of Service violation (with real account suspensions,
 * including paying subscribers) to use a consumer subscription's login in a third-party app
 * instead of billed API access, and OpenAI's equivalent is scoped to its own Codex tooling only.
 */
enum class AiProvider(val storageKey: String, @param:StringRes val label: Int, @param:StringRes val keyHint: Int) {
    GEMINI(
        storageKey = "gemini_api_key",
        label = R.string.provider_gemini,
        keyHint = R.string.provider_gemini_key_hint,
    ),
    ANTHROPIC(
        storageKey = "anthropic_api_key",
        label = R.string.provider_anthropic,
        keyHint = R.string.provider_anthropic_key_hint,
    ),
    OPENAI(
        storageKey = "openai_api_key",
        label = R.string.provider_openai,
        keyHint = R.string.provider_openai_key_hint,
    ),
    OTHER(
        storageKey = "other_api_key",
        label = R.string.provider_other,
        keyHint = R.string.provider_other_key_hint,
    ),
}
