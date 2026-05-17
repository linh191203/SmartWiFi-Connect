package com.smartwificonnect.wifi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.util.Log
import com.smartwificonnect.BuildConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class WifiConnector(
    context: Context,
    private val createSpecifier: (ssid: String, password: String?, security: String?) -> WifiNetworkSpecifier = ::buildWifiSpecifier,
    private val createRequest: (WifiNetworkSpecifier) -> NetworkRequest = ::buildWifiNetworkRequest,
    private val internetValidationGraceMillis: Long = 15_000L,
) {
    private val appContext = context.applicationContext
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = appContext.getSystemService(WifiManager::class.java)
    private var activeCallback: ConnectivityManager.NetworkCallback? = null
    private var boundNetwork: Network? = null

    suspend fun connect(
        ssid: String,
        password: String?,
        security: String?,
    ): WifiConnectResult = coroutineScope {
        var internetFallbackJob: Job? = null
        var joinedWithoutInternet: WifiConnectResult.ConnectedWithoutInternet? = null

        suspendCancellableCoroutine { continuation ->
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

        val normalizedPassword = password?.trim().orEmpty()
        logWifiConnect(
                "connect start api=WifiNetworkSpecifier android=${Build.VERSION.SDK_INT}, " +
                "targetSsid='$normalizedSsid', password=${normalizedPassword.debugPasswordForLog()}, " +
                "security='${security.orEmpty()}', permissions=${appContext.buildPermissionDebugSummary()}, " +
                "current=${wifiManager.currentWifiDebugSnapshot()}",
        )
        val unsupportedSecurityMessage = validateSecurityMode(security)
        if (unsupportedSecurityMessage != null) {
            logWifiConnect("connect rejected unsupported security='$security': $unsupportedSecurityMessage")
            continuation.resume(
                WifiConnectResult.Failed(
                    WifiConnectFailureReason.INVALID_INPUT,
                    unsupportedSecurityMessage,
                ),
            )
            return@suspendCancellableCoroutine
        }

        if (normalizedPassword.isNotEmpty() && normalizedPassword.length !in 8..63) {
            logWifiConnect("connect rejected password length=${normalizedPassword.length}")
            continuation.resume(
                WifiConnectResult.Failed(
                    WifiConnectFailureReason.INVALID_INPUT,
                    "Mật khẩu WPA/WPA2 phải có từ 8 đến 63 ký tự.",
                ),
            )
            return@suspendCancellableCoroutine
        }

        if (normalizedPassword.isNotEmpty() && !normalizedPassword.isPrintableAsciiPassphrase()) {
            logWifiConnect("connect rejected password nonAscii length=${normalizedPassword.length}")
            continuation.resume(
                WifiConnectResult.Failed(
                    WifiConnectFailureReason.INVALID_INPUT,
                    "Mật khẩu có ký tự không được Android hỗ trợ cho kiểu kết nối này. Vui lòng kiểm tra lại mật khẩu.",
                ),
            )
            return@suspendCancellableCoroutine
        }

        val specifier = createSpecifier(
            normalizedSsid,
            normalizedPassword.takeIf { it.isNotEmpty() },
            security,
        )
        val request = createRequest(specifier)
        logWifiConnect("requestNetwork submitting api=WifiNetworkSpecifier target='$normalizedSsid'")

        fun cancelInternetFallback() {
            internetFallbackJob?.cancel()
            internetFallbackJob = null
        }

        fun finish(
            result: WifiConnectResult,
            releaseRequest: Boolean,
        ) {
            cancelInternetFallback()
            logWifiConnect(
                "finish preBind result=${result.toDebugString()}, releaseRequest=$releaseRequest, " +
                    "current=${wifiManager.currentWifiDebugSnapshot()}",
            )
            val finalResult = if (result is WifiConnectResult.Success && result.network != null) {
                if (manager.bindProcessToNetwork(result.network)) {
                    boundNetwork = result.network
                    logWifiConnect("bindProcessToNetwork success network=${result.network}")
                    result
                } else {
                    logWifiConnect("bindProcessToNetwork failed network=${result.network}")
                    WifiConnectResult.Failed(
                        reason = WifiConnectFailureReason.NO_INTERNET,
                        message = "Không thể dùng mạng Wi-Fi vừa kết nối để truy cập Internet.",
                    )
                }
            } else {
                result
            }
            complete(
                continuation = continuation,
                result = finalResult,
                releaseRequest = releaseRequest || finalResult is WifiConnectResult.Failed,
            )
        }

        fun rememberJoinedWithoutInternet(result: WifiConnectResult.ConnectedWithoutInternet) {
            if (!continuation.isActive) return
            joinedWithoutInternet = result
            logWifiConnect(
                "joined without validated internet, waiting grace=${internetValidationGraceMillis}ms, " +
                    "result=${result.toDebugString()}, current=${wifiManager.currentWifiDebugSnapshot()}",
            )
            if (internetFallbackJob?.isActive == true) return

            internetFallbackJob = launch {
                delay(internetValidationGraceMillis.coerceAtLeast(0L))
                val fallback = joinedWithoutInternet ?: return@launch
                if (continuation.isActive) {
                    complete(
                        continuation = continuation,
                        result = fallback,
                        releaseRequest = true,
                    )
                }
            }
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val capabilities = manager.getNetworkCapabilities(network)
                logWifiConnect(
                    "callback onAvailable network=$network, caps=${capabilities.toDebugString()}, " +
                        "current=${wifiManager.currentWifiDebugSnapshot()}",
                )
                val observedResult = capabilities?.toWifiConnectionResult(
                    network = network,
                    expectedSsid = normalizedSsid,
                    fallbackWifiInfo = wifiManager?.connectionInfo,
                )
                if (observedResult == null) {
                    logWifiConnect("callback onAvailable waiting for stable Wi-Fi capabilities target='$normalizedSsid'")
                    return
                }
                when (observedResult) {
                    is WifiConnectResult.Success -> finish(observedResult, releaseRequest = false)
                    is WifiConnectResult.ConnectedWithoutInternet -> rememberJoinedWithoutInternet(observedResult)
                    is WifiConnectResult.Failed -> finish(observedResult, releaseRequest = true)
                }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                if (!continuation.isActive) return
                logWifiConnect(
                    "callback onCapabilitiesChanged network=$network, caps=${networkCapabilities.toDebugString()}, " +
                        "current=${wifiManager.currentWifiDebugSnapshot()}",
                )
                val observedResult = networkCapabilities.toWifiConnectionResult(
                    network = network,
                    expectedSsid = normalizedSsid,
                    fallbackWifiInfo = wifiManager?.connectionInfo,
                )
                if (observedResult != null) {
                    when (observedResult) {
                        is WifiConnectResult.Success -> finish(observedResult, releaseRequest = false)
                        is WifiConnectResult.ConnectedWithoutInternet -> rememberJoinedWithoutInternet(observedResult)
                        is WifiConnectResult.Failed -> finish(observedResult, releaseRequest = true)
                    }
                }
            }

            override fun onLost(network: Network) {
                if (!continuation.isActive) return
                logWifiConnect("callback onLost network=$network, current=${wifiManager.currentWifiDebugSnapshot()}")
                finish(
                    result = WifiConnectResult.Failed(
                        reason = WifiConnectFailureReason.AUTHENTICATION_OR_UNAVAILABLE,
                        message = "Network lost before verification",
                    ),
                    releaseRequest = true,
                )
            }

            override fun onUnavailable() {
                logWifiConnect("callback onUnavailable target='$normalizedSsid', current=${wifiManager.currentWifiDebugSnapshot()}")
                finish(
                    result = WifiConnectResult.Failed(
                        reason = WifiConnectFailureReason.WRONG_PASSWORD_OR_REJECTED,
                        message = "Không thể kết nối. Vui lòng kiểm tra lại mật khẩu hoặc xác nhận yêu cầu kết nối Wi-Fi.",
                    ),
                    releaseRequest = true,
                )
            }
        }

        activeCallback = callback
        continuation.invokeOnCancellation {
            cancelInternetFallback()
            cancelPendingRequest()
        }

        try {
            manager.requestNetwork(request, callback)
        } catch (securityException: SecurityException) {
            logWifiConnect("requestNetwork SecurityException=${securityException.message}")
            finish(
                result = WifiConnectResult.Failed(
                    reason = WifiConnectFailureReason.PERMISSION_DENIED,
                    message = securityException.message,
                ),
                releaseRequest = true,
            )
        } catch (throwable: Throwable) {
            logWifiConnect("requestNetwork Throwable=${throwable.message}")
            finish(
                result = WifiConnectResult.Failed(
                    reason = WifiConnectFailureReason.UNKNOWN,
                    message = throwable.message,
                ),
                releaseRequest = true,
            )
        }
        }
    }

    fun cancelPendingRequest() {
        val manager = connectivityManager ?: return
        if (boundNetwork != null) {
            runCatching { manager.bindProcessToNetwork(null) }
            boundNetwork = null
        }
        val callback = activeCallback ?: return
        runCatching {
            manager.unregisterNetworkCallback(callback)
        }
        activeCallback = null
    }

    private fun complete(
        continuation: kotlinx.coroutines.CancellableContinuation<WifiConnectResult>,
        result: WifiConnectResult,
        releaseRequest: Boolean,
    ) {
        if (continuation.isActive) {
            continuation.resume(result)
        }
        if (releaseRequest) {
            cancelPendingRequest()
        }
    }
}

private fun validateSecurityMode(security: String?): String? {
    val normalized = security.orEmpty()
    return when {
        normalized.contains("enterprise", ignoreCase = true) ->
            "WPA/WPA2 Enterprise cần cấu hình tài khoản riêng trong cài đặt hệ thống."
        normalized.contains("wep", ignoreCase = true) ->
            "WEP không được hỗ trợ trong luồng kết nối tự động an toàn."
        else -> null
    }
}

private fun buildWifiSpecifier(
    ssid: String,
    password: String?,
    security: String?,
): WifiNetworkSpecifier {
    val specifierBuilder = WifiNetworkSpecifier.Builder().setSsid(ssid)
    val normalizedPassword = password.orEmpty()
    if (normalizedPassword.isNotEmpty()) {
        val useWpa3 = security.orEmpty().contains("WPA3", ignoreCase = true)
        if (useWpa3) {
            specifierBuilder.setWpa3Passphrase(normalizedPassword)
        } else {
            specifierBuilder.setWpa2Passphrase(normalizedPassword)
        }
    }
    return specifierBuilder.build()
}

private fun buildWifiNetworkRequest(specifier: WifiNetworkSpecifier): NetworkRequest {
    return NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        // WifiNetworkSpecifier can associate before Android validates internet access.
        // Requiring INTERNET up front makes some devices reject the request immediately.
        .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .setNetworkSpecifier(specifier)
        .build()
}

private fun String.isPrintableAsciiPassphrase(): Boolean {
    return all { it.code in 32..126 }
}

private fun NetworkCapabilities.toWifiConnectionResult(
    network: Network,
    expectedSsid: String,
    fallbackWifiInfo: WifiInfo?,
): WifiConnectResult? {
    if (!hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
        return null
    }
    val actualSsid = observedWifiSsid(
        fallbackWifiInfo = fallbackWifiInfo,
        expectedSsid = expectedSsid,
    )
    if (actualSsid.isNullOrBlank()) {
        return null
    }
    if (!actualSsid.equals(expectedSsid, ignoreCase = true)) {
        return WifiConnectResult.Failed(
            reason = WifiConnectFailureReason.SSID_NOT_FOUND,
            message = "Thiết bị đang ở SSID '$actualSsid', không phải '$expectedSsid'.",
        )
    }
    val hasInternet = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    val isValidated = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    if (hasInternet && isValidated) {
        return WifiConnectResult.Success(network = network, ssid = actualSsid)
    }
    return WifiConnectResult.ConnectedWithoutInternet(
        network = network,
        ssid = actualSsid,
        hasInternetCapability = hasInternet,
        isCaptivePortal = hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL),
    )
}

private fun NetworkCapabilities.observedWifiSsid(
    fallbackWifiInfo: WifiInfo?,
    expectedSsid: String,
): String? {
    val transportSsid = (transportInfo as? WifiInfo)
        ?.ssid
        ?.normalizeWifiSsid()
        ?.takeKnownSsid()
    if (transportSsid != null) {
        return transportSsid
    }
    return fallbackWifiInfo
        ?.ssid
        ?.normalizeWifiSsid()
        ?.takeKnownSsid()
        ?.takeIf { it.equals(expectedSsid, ignoreCase = true) }
}

private fun String.normalizeWifiSsid(): String = trim().removePrefix("\"").removeSuffix("\"")

private fun String.takeKnownSsid(): String? {
    return takeIf { it.isNotBlank() && !it.equals("<unknown ssid>", ignoreCase = true) }
}

private fun logWifiConnect(message: String) {
    if (BuildConfig.DEBUG) {
        runCatching { Log.d(WIFI_CONNECT_LOG_TAG, message) }
    }
}

private fun String?.debugPasswordForLog(): String {
    val value = this.orEmpty()
    if (value.isBlank()) return "blank"
    val visiblePrefix = value.take(1)
    val visibleSuffix = value.takeLast(1).takeIf { value.length > 1 }.orEmpty()
    val ascii = value.all { it.code in 32..126 }
    return "masked='$visiblePrefix***$visibleSuffix', length=${value.length}, ascii=$ascii"
}

@Suppress("DEPRECATION")
private fun WifiManager?.currentWifiDebugSnapshot(): String {
    val info = runCatching { this?.connectionInfo }.getOrNull()
        ?: return "connectionInfo=null"
    return "ssid='${info.ssid.normalizeWifiSsid()}', bssid='${info.bssid}', " +
        "networkId=${info.networkId}, rssi=${info.rssi}, freq=${info.frequency}"
}

private fun NetworkCapabilities?.toDebugString(): String {
    if (this == null) return "null"
    val wifiInfo = transportInfo as? WifiInfo
    return "wifi=${hasTransport(NetworkCapabilities.TRANSPORT_WIFI)}, " +
        "internet=${hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)}, " +
        "validated=${hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}, " +
        "captive=${hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)}, " +
        "transportSsid='${wifiInfo?.ssid?.normalizeWifiSsid().orEmpty()}', " +
        "transportBssid='${wifiInfo?.bssid.orEmpty()}', networkId=${wifiInfo?.networkId}"
}

private fun WifiConnectResult.toDebugString(): String {
    return when (this) {
        is WifiConnectResult.Success ->
            "Success(ssid='$ssid', hasNetwork=${network != null})"
        is WifiConnectResult.ConnectedWithoutInternet ->
            "ConnectedWithoutInternet(ssid='$ssid', hasInternetCapability=$hasInternetCapability, captive=$isCaptivePortal, hasNetwork=${network != null})"
        is WifiConnectResult.Failed ->
            "Failed(reason=$reason, message='${message.orEmpty()}')"
    }
}

private fun Context.buildPermissionDebugSummary(): String {
    fun granted(permission: String): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            runCatching { checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED }
                .getOrDefault(false)
    }
    val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
    val nearby = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        granted(Manifest.permission.NEARBY_WIFI_DEVICES)
    return "fine=$fine, coarse=$coarse, nearbyWifi=$nearby"
}

private const val WIFI_CONNECT_LOG_TAG = "SmartWifiConnector"

sealed class WifiConnectResult {
    data class Success(
        val ssid: String,
        val network: Network? = null,
    ) : WifiConnectResult()

    data class ConnectedWithoutInternet(
        val ssid: String,
        val network: Network? = null,
        val hasInternetCapability: Boolean = false,
        val isCaptivePortal: Boolean = false,
    ) : WifiConnectResult()

    data class Failed(
        val reason: WifiConnectFailureReason,
        val message: String? = null,
    ) : WifiConnectResult()
}

enum class WifiConnectFailureReason {
    INVALID_INPUT,
    PERMISSION_DENIED,
    LOCATION_DISABLED,
    UNSUPPORTED_DEVICE,
    SSID_NOT_FOUND,
    NETWORK_NOT_FOUND,
    WRONG_PASSWORD_OR_REJECTED,
    AUTHENTICATION_OR_UNAVAILABLE,
    NO_INTERNET,
    CAPTIVE_PORTAL,
    TIMEOUT,
    UNKNOWN,
}
