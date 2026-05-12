package com.smartwificonnect.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiNetworkSpecifier
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
        val unsupportedSecurityMessage = validateSecurityMode(security)
        if (unsupportedSecurityMessage != null) {
            continuation.resume(
                WifiConnectResult.Failed(
                    WifiConnectFailureReason.INVALID_INPUT,
                    unsupportedSecurityMessage,
                ),
            )
            return@suspendCancellableCoroutine
        }

        if (normalizedPassword.isNotEmpty() && normalizedPassword.length !in 8..63) {
            continuation.resume(
                WifiConnectResult.Failed(
                    WifiConnectFailureReason.INVALID_INPUT,
                    "Mật khẩu WPA/WPA2 phải có từ 8 đến 63 ký tự.",
                ),
            )
            return@suspendCancellableCoroutine
        }

        if (normalizedPassword.isNotEmpty() && !normalizedPassword.isPrintableAsciiPassphrase()) {
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

        fun cancelInternetFallback() {
            internetFallbackJob?.cancel()
            internetFallbackJob = null
        }

        fun finish(
            result: WifiConnectResult,
            releaseRequest: Boolean,
        ) {
            cancelInternetFallback()
            val finalResult = if (result is WifiConnectResult.Success && result.network != null) {
                if (manager.bindProcessToNetwork(result.network)) {
                    boundNetwork = result.network
                    result
                } else {
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
                val observedResult = capabilities?.toWifiConnectionResult(
                    network = network,
                    expectedSsid = normalizedSsid,
                ) ?: WifiConnectResult.ConnectedWithoutInternet(
                    network = network,
                    ssid = normalizedSsid,
                    hasInternetCapability = false,
                    isCaptivePortal = false,
                )
                when (observedResult) {
                    is WifiConnectResult.Success -> finish(observedResult, releaseRequest = false)
                    is WifiConnectResult.ConnectedWithoutInternet -> rememberJoinedWithoutInternet(observedResult)
                    is WifiConnectResult.Failed -> finish(observedResult, releaseRequest = true)
                }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                if (!continuation.isActive) return
                val observedResult = networkCapabilities.toWifiConnectionResult(
                    network = network,
                    expectedSsid = normalizedSsid,
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
                finish(
                    result = WifiConnectResult.Failed(
                        reason = WifiConnectFailureReason.AUTHENTICATION_OR_UNAVAILABLE,
                        message = "Network lost before verification",
                    ),
                    releaseRequest = true,
                )
            }

            override fun onUnavailable() {
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
            finish(
                result = WifiConnectResult.Failed(
                    reason = WifiConnectFailureReason.PERMISSION_DENIED,
                    message = securityException.message,
                ),
                releaseRequest = true,
            )
        } catch (throwable: Throwable) {
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
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .setNetworkSpecifier(specifier)
        .build()
}

private fun String.isPrintableAsciiPassphrase(): Boolean {
    return all { it.code in 32..126 }
}

private fun NetworkCapabilities.toWifiConnectionResult(
    network: Network,
    expectedSsid: String,
): WifiConnectResult? {
    if (!hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
        return null
    }
    if (wifiSsidMismatch(expectedSsid)) {
        return WifiConnectResult.Failed(
            reason = WifiConnectFailureReason.SSID_NOT_FOUND,
            message = "Thiết bị không kết nối vào đúng SSID đã chọn.",
        )
    }
    val hasInternet = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    val isValidated = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    if (hasInternet && isValidated) {
        return WifiConnectResult.Success(network = network, ssid = expectedSsid)
    }
    return WifiConnectResult.ConnectedWithoutInternet(
        network = network,
        ssid = expectedSsid,
        hasInternetCapability = hasInternet,
        isCaptivePortal = hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL),
    )
}

private fun NetworkCapabilities.wifiSsidMismatch(expectedSsid: String): Boolean {
    val wifiInfo = transportInfo as? WifiInfo ?: return false
    val actualSsid = wifiInfo.ssid.normalizeWifiSsid()
    return actualSsid.isNotBlank() &&
        !actualSsid.equals("<unknown ssid>", ignoreCase = true) &&
        !actualSsid.equals(expectedSsid, ignoreCase = true)
}

private fun String.normalizeWifiSsid(): String = trim().removePrefix("\"").removeSuffix("\"")

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
