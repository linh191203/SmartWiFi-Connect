package com.smartwificonnect.feature.share

import android.net.Uri
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal data class SharedWifiCredentials(
    val ssid: String,
    val password: String,
    val security: String,
)

internal sealed interface SharedWifiPayloadParseResult {
    data class Success(val credentials: SharedWifiCredentials) : SharedWifiPayloadParseResult
    data class Invalid(val message: String) : SharedWifiPayloadParseResult
    object NotSupported : SharedWifiPayloadParseResult
}

internal object SmartWifiSharePayloadCodec {
    private const val SCHEME = "smartwificonnect"
    private const val HOST = "connect"
    private const val LEGACY_SCHEME = "smartwifi"
    private const val LEGACY_HOST = "join"
    private const val DEFAULT_SECURITY = "WPA/WPA2"

    fun buildLink(
        ssid: String,
        password: String,
        security: String,
    ): String {
        val normalizedSsid = ssid.trim()
        val normalizedSecurity = security.trim().ifBlank {
            if (password.isBlank()) "Open" else DEFAULT_SECURITY
        }
        return buildString {
            append("$SCHEME://$HOST")
            append("?ssid=${normalizedSsid.urlEncodeQueryValue()}")
            append("&security=${normalizedSecurity.urlEncodeQueryValue()}")
            if (password.isNotBlank()) {
                append("&password=${password.urlEncodeQueryValue()}")
            }
        }
    }

    fun parse(uri: Uri?): SharedWifiPayloadParseResult {
        if (!isSupportedUri(uri)) return SharedWifiPayloadParseResult.NotSupported

        val ssid = uri?.getQueryParameter("ssid").orEmpty().trim()
        if (ssid.isBlank()) {
            return SharedWifiPayloadParseResult.Invalid(
                message = "Link chia sẻ Wi-Fi thiếu SSID.",
            )
        }

        val password = uri?.getQueryParameter("password").orEmpty()
        val security = uri?.getQueryParameter("security").orEmpty().trim().ifBlank {
            if (password.isBlank()) "Open" else DEFAULT_SECURITY
        }

        return SharedWifiPayloadParseResult.Success(
            credentials = SharedWifiCredentials(
                ssid = ssid,
                password = password,
                security = security,
            ),
        )
    }

    fun isSupportedUri(uri: Uri?): Boolean {
        val scheme = uri?.scheme?.lowercase().orEmpty()
        val host = uri?.host?.lowercase().orEmpty()
        return (scheme == SCHEME && host == HOST) ||
            (scheme == LEGACY_SCHEME && host == LEGACY_HOST)
    }
}

internal fun ShareWifiUiModel.toSmartWifiLink(): String {
    return SmartWifiSharePayloadCodec.buildLink(
        ssid = ssid,
        password = password,
        security = security,
    )
}

private fun String.urlEncodeQueryValue(): String {
    return URLEncoder.encode(this, StandardCharsets.UTF_8.name()).replace("+", "%20")
}
