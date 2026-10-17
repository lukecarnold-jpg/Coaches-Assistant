package com.lonrad.coachesassistant.ai

import androidx.annotation.StringRes
import com.lonrad.coachesassistant.R
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** User-supplied connection details for [AiProvider.OTHER]. */
data class CustomEndpointConfig(
    val endpointUrl: String = "",
    val model: String = "",
)

/**
 * Why [url] can't be used as the "Other" provider's endpoint (a string resource), or null if it can. A blank URL isn't
 * reported here; callers treat "not set yet" separately.
 *
 * Plain `http://` is only allowed to servers on the local network (e.g. Ollama on a laptop), so an
 * API key or prompt is never sent unencrypted across the internet.
 */
@StringRes
fun endpointUrlProblem(url: String): Int? {
    if (url.isBlank()) return null
    val parsed = url.trim().toHttpUrlOrNull()
        ?: return R.string.endpoint_not_a_url
    if (!parsed.isHttps && !isLocalNetworkHost(parsed.host)) {
        return R.string.endpoint_needs_https
    }
    return null
}

/** The host name of [url], or null if it isn't a URL. Used to tell when an endpoint moves to another service. */
fun endpointHost(url: String): String? = url.trim().toHttpUrlOrNull()?.host

/**
 * Whether [host] names a machine on the local network rather than the public internet: loopback,
 * private and link-local IP ranges (including 100.64.0.0/10, which Tailscale uses), local-only
 * domain suffixes like `.local`, or a bare single-label hostname resolved by local DNS.
 *
 * This looks only at the name, without resolving it, so it's a guard against mistakes rather than
 * a guarantee: a hostname on a private suffix could still be pointed at a public address.
 */
fun isLocalNetworkHost(host: String): Boolean {
    val name = host.lowercase().trimEnd('.')
    ipv4Octets(name)?.let { (a, b) ->
        return a == 127 || a == 10 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 169 && b == 254) ||
            (a == 100 && b in 64..127)
    }
    if (':' in name) {
        if (name == "::1") return true
        val firstHextet = name.substringBefore(':').toIntOrNull(16) ?: return false
        return (firstHextet and 0xfe00) == 0xfc00 || // fc00::/7 unique local
            (firstHextet and 0xffc0) == 0xfe80 // fe80::/10 link-local
    }
    if (name == "localhost" || '.' !in name) return true
    return LOCAL_SUFFIXES.any { name.endsWith(it) }
}

private val LOCAL_SUFFIXES = listOf(".localhost", ".local", ".lan", ".home.arpa", ".internal")

/** The first two octets of a dotted IPv4 address, or null if [name] isn't one. */
private fun ipv4Octets(name: String): Pair<Int, Int>? {
    val parts = name.split('.')
    if (parts.size != 4) return null
    val octets = parts.map { part -> part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null }
    return octets[0] to octets[1]
}
