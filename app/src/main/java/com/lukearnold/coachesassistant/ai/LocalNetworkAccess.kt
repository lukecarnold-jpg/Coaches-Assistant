package com.lukearnold.coachesassistant.ai

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Android 17 (API 37) blocks apps targeting it from connecting to devices on the local network
 * (e.g. Ollama on a laptop) unless the user grants this runtime permission; without it, the
 * connection just times out. Requested only when the Other provider points at such a server.
 */
const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

/** The first Android version that requires [ACCESS_LOCAL_NETWORK] for local connections. */
const val LOCAL_NETWORK_PERMISSION_MIN_SDK = 37

/**
 * Whether reaching [provider] needs [ACCESS_LOCAL_NETWORK] on a device running [sdkInt]: only the
 * Other provider can point at a local server, and only its host decides (https to a local address
 * needs it too).
 */
fun needsLocalNetworkPermission(provider: AiProvider, endpoint: CustomEndpointConfig, sdkInt: Int): Boolean {
    if (sdkInt < LOCAL_NETWORK_PERMISSION_MIN_SDK || provider != AiProvider.OTHER) return false
    val host = endpoint.endpointUrl.trim().toHttpUrlOrNull()?.host ?: return false
    return isLocalNetworkHost(host)
}

fun Context.hasLocalNetworkPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
