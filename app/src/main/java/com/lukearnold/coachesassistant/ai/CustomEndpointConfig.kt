package com.lukearnold.coachesassistant.ai

/** User-supplied connection details for [AiProvider.OTHER]. */
data class CustomEndpointConfig(
    val endpointUrl: String = "",
    val model: String = "",
)
