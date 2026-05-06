package com.example.smartwificonnect.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class WifiConnector(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
    private var activeCallback: ConnectivityManager.NetworkCallback? = null

    suspend fun connect(
        ssid: String,
        password: String?,
        security: String?,
    ): WifiConnectResult = suspendCancellableCoroutine { continuation ->
        val manager = connectivityManager
            ?: return@suspendCancellableCoroutine continuation.resume(
                WifiConnectResult.Failed(WifiConnectFailureReason.UNKNOWN, "ConnectivityManager unavailable"),
            )

        cancelPendingRequest()

        val normalizedSsid = ssid.trim()
        if (normalizedSsid.isEmpty()) {
            continuation.resume(
                WifiConnectResult.Failed(WifiConnectFailureReason.INVALID_INPUT, "SSID is empty"),
            )
            return@suspendCancellableCoroutine
        }

        val specifierBuilder = WifiNetworkSpecifier.Builder().setSsid(normalizedSsid)
        val normalizedPassword = password?.trim().orEmpty()
        if (normalizedPassword.isNotEmpty()) {
            val useWpa3 = security.orEmpty().contains("WPA3", ignoreCase = true)
            if (useWpa3 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                specifierBuilder.setWpa3Passphrase(normalizedPassword)
            } else {
                specifierBuilder.setWpa2Passphrase(normalizedPassword)
            }
        }

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(specifierBuilder.build())
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                complete(
                    continuation = continuation,
                    result = WifiConnectResult.Success(network = network, ssid = normalizedSsid),
                )
            }

            override fun onUnavailable() {
                complete(
                    continuation = continuation,
                    result = WifiConnectResult.Failed(
                        reason = WifiConnectFailureReason.AUTHENTICATION_OR_UNAVAILABLE,
                        message = "Network unavailable",
                    ),
                )
            }
        }

        activeCallback = callback
        continuation.invokeOnCancellation { cancelPendingRequest() }

        try {
            manager.requestNetwork(request, callback)
        } catch (securityException: SecurityException) {
            complete(
                continuation = continuation,
                result = WifiConnectResult.Failed(
                    reason = WifiConnectFailureReason.PERMISSION_DENIED,
                    message = securityException.message,
                ),
            )
        } catch (throwable: Throwable) {
            complete(
                continuation = continuation,
                result = WifiConnectResult.Failed(
                    reason = WifiConnectFailureReason.UNKNOWN,
                    message = throwable.message,
                ),
            )
        }
    }

    fun cancelPendingRequest() {
        val manager = connectivityManager ?: return
        val callback = activeCallback ?: return
        runCatching {
            manager.unregisterNetworkCallback(callback)
        }
        activeCallback = null
    }

    private fun complete(
        continuation: kotlinx.coroutines.CancellableContinuation<WifiConnectResult>,
        result: WifiConnectResult,
    ) {
        if (continuation.isActive) {
            continuation.resume(result)
        }
        cancelPendingRequest()
    }
}

sealed class WifiConnectResult {
    data class Success(
        val ssid: String,
        val network: Network? = null,
    ) : WifiConnectResult()

    data class Failed(
        val reason: WifiConnectFailureReason,
        val message: String? = null,
    ) : WifiConnectResult()
}

enum class WifiConnectFailureReason {
    INVALID_INPUT,
    PERMISSION_DENIED,
    WIFI_DISABLED,
    SSID_NOT_FOUND,
    NOT_ACTUALLY_CONNECTED,
    AUTHENTICATION_OR_UNAVAILABLE,
    TIMEOUT,
    UNKNOWN,
}
