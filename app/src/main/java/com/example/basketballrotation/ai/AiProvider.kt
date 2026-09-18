package com.example.basketballrotation.ai

/**
 * AI providers that can generate training drills. Each requires the user's own API key entered
 * in Settings; there is no "log in with your Gemini/Claude/ChatGPT account" option. Google and
 * Anthropic have both made it a Terms of Service violation (with real account suspensions,
 * including paying subscribers) to use a consumer subscription's login in a third-party app
 * instead of billed API access, and OpenAI's equivalent is scoped to its own Codex tooling only.
 */
enum class AiProvider(val storageKey: String, val displayName: String, val getKeyHint: String) {
    GEMINI(
        storageKey = "gemini_api_key",
        displayName = "Google Gemini",
        getKeyHint = "Get a free key from Google AI Studio.",
    ),
    ANTHROPIC(
        storageKey = "anthropic_api_key",
        displayName = "Anthropic Claude",
        getKeyHint = "Get a key from the Anthropic Console.",
    ),
    OPENAI(
        storageKey = "openai_api_key",
        displayName = "OpenAI ChatGPT",
        getKeyHint = "Get a key from the OpenAI Platform.",
    ),
}
