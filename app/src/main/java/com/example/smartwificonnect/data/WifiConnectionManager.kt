package com.example.smartwificonnect.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.smartwificonnect.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class WifiConnectResult(
    val success: Boolean,
    val message: String,
)

class WifiConnectionManager(context: Context) {
    private val appContext = context.applicationContext

    suspend fun connectToWifi(
        ssid: String,
        password: String,
        security: String,
        saveNetwork: Boolean,
    ): WifiConnectResult {
        if (ssid.isBlank()) {
            return WifiConnectResult(false, appContext.getString(R.string.status_wifi_empty_ssid))
        }
        if (!hasWifiPermissions()) {
            return WifiConnectResult(false, appContext.getString(R.string.status_wifi_permission_required))
        }

        val temporaryConnectionResult = requestTemporaryWifiConnection(
            ssid = ssid,
            password = password,
            security = security,
        )
        if (!temporaryConnectionResult.success || !saveNetwork) {
            return if (!saveNetwork && temporaryConnectionResult.success) {
                temporaryConnectionResult.copy(
                    message = "${temporaryConnectionResult.message} ${appContext.getString(R.string.status_wifi_password_not_saved_suffix)}",
                )
            } else {
                temporaryConnectionResult
            }
        }

        val suggestionResult = addNetworkSuggestion(
            ssid = ssid,
            password = password,
            security = security,
        )

        return if (suggestionResult.success) {
            WifiConnectResult(
                success = true,
                message = "${temporaryConnectionResult.message} ${suggestionResult.message}",
            )
        } else {
            WifiConnectResult(
                success = true,
                message = "${temporaryConnectionResult.message} ${suggestionResult.message}",
            )
        }
    }

    private suspend fun requestTemporaryWifiConnection(
        ssid: String,
        password: String,
        security: String,
    ): WifiConnectResult = withContext(Dispatchers.Main.immediate) {
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@withContext WifiConnectResult(false, appContext.getString(R.string.status_wifi_no_connectivity_manager))

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(
                WifiNetworkSpecifier.Builder()
                    .setSsid(ssid)
                    .applySecurity(password = password, security = security)
                    .build(),
            )
            .build()

        withTimeoutOrNull(15000L) {
            suspendCancellableCoroutine { continuation ->
                var finished = false
                lateinit var callback: ConnectivityManager.NetworkCallback

                fun finish(result: WifiConnectResult) {
                    if (finished) return
                    finished = true
                    runCatching { connectivityManager.unregisterNetworkCallback(callback) }
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }

                callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        connectivityManager.bindProcessToNetwork(network)
                        finish(WifiConnectResult(true, appContext.getString(R.string.status_wifi_connected, ssid)))
                    }

                    override fun onUnavailable() {
                        finish(WifiConnectResult(false, appContext.getString(R.string.status_wifi_unavailable, ssid)))
                    }
                }

                continuation.invokeOnCancellation {
                    runCatching { connectivityManager.unregisterNetworkCallback(callback) }
                }

                runCatching {
                    connectivityManager.requestNetwork(request, callback)
                }.onFailure { error ->
                    finish(
                        WifiConnectResult(
                            false,
                            appContext.getString(
                                R.string.status_wifi_request_failed,
                                error.message ?: appContext.getString(R.string.status_unknown_error),
                            ),
                        ),
                    )
                }
            }
        } ?: WifiConnectResult(false, appContext.getString(R.string.status_wifi_timeout, ssid))
    }

    private suspend fun addNetworkSuggestion(
        ssid: String,
        password: String,
        security: String,
    ): WifiConnectResult = withContext(Dispatchers.IO) {
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return@withContext WifiConnectResult(false, appContext.getString(R.string.status_wifi_no_manager))

        val suggestion = WifiNetworkSuggestion.Builder()
            .setSsid(ssid)
            .applySecurity(password = password, security = security)
            .build()

        when (wifiManager.addNetworkSuggestions(listOf(suggestion))) {
            WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS -> {
                WifiConnectResult(true, appContext.getString(R.string.status_wifi_saved_on_device))
            }

            WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE -> {
                WifiConnectResult(true, appContext.getString(R.string.status_wifi_already_saved_on_device))
            }

            else -> {
                WifiConnectResult(false, appContext.getString(R.string.status_wifi_not_saved_on_device))
            }
        }
    }

    private fun hasWifiPermissions(): Boolean {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNearbyWifi = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.NEARBY_WIFI_DEVICES,
            ) == PackageManager.PERMISSION_GRANTED
            hasFineLocation && hasNearbyWifi
        } else {
            hasFineLocation
        }
    }
}

private fun WifiNetworkSpecifier.Builder.applySecurity(
    password: String,
    security: String,
): WifiNetworkSpecifier.Builder {
    if (password.isBlank()) return this
    return if (security.contains("WPA3", ignoreCase = true)) {
        setWpa3Passphrase(password)
    } else {
        setWpa2Passphrase(password)
    }
}

private fun WifiNetworkSuggestion.Builder.applySecurity(
    password: String,
    security: String,
): WifiNetworkSuggestion.Builder {
    if (password.isBlank()) return this
    return if (security.contains("WPA3", ignoreCase = true)) {
        setWpa3Passphrase(password)
    } else {
        setWpa2Passphrase(password)
    }
}