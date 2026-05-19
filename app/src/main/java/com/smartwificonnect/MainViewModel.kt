package com.smartwificonnect

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartwificonnect.data.AiValidateData
import com.smartwificonnect.data.DefaultWifiRepository
import com.smartwificonnect.data.FuzzyNetworkPayload
import com.smartwificonnect.data.ParsedWifiData
import com.smartwificonnect.data.SaveNetworkRequest
import com.smartwificonnect.data.WifiRepository
import com.smartwificonnect.data.local.SavedWifiRecord
import com.smartwificonnect.feature.home.RecentNetworkType
import com.smartwificonnect.feature.home.RecentNetworkUiModel
import com.smartwificonnect.feature.share.SharedWifiPayloadParseResult
import com.smartwificonnect.feature.share.SmartWifiSharePayloadCodec
import com.smartwificonnect.ocr.WifiOcrCredentials
import com.smartwificonnect.ocr.WifiOcrEngine
import com.smartwificonnect.ocr.WifiOcrProcessor
import com.smartwificonnect.wifi.WifiConnectFailureReason
import com.smartwificonnect.wifi.WifiConnectResult
import com.smartwificonnect.wifi.WifiConnector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class SharedWifiLinkResult {
    NOT_SUPPORTED,
    CONSUMED,
    INVALID,
}

class MainViewModel @JvmOverloads constructor(
    application: Application,
    private val deps: MainViewModelDeps? = null,
) : AndroidViewModel(application) {
    private val repository: WifiRepository =
        deps?.repository ?: DefaultWifiRepository(application.applicationContext)
    private val ocrProcessor: WifiOcrEngine = deps?.ocrProcessor ?: WifiOcrProcessor()
    private val ocrDispatcher: CoroutineDispatcher = deps?.ocrDispatcher ?: Dispatchers.Default
    // Use the application-scoped WifiConnector so the WiFi stays connected
    // even after the ViewModel is cleared (e.g. user navigates away from
    // OCR screen or rotates the device). The connector is released only
    // when the app process dies or user explicitly disconnects.
    private val wifiConnector = SmartWifiApp.getWifiConnector(application)
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()
    private var ocrJob: Job? = null
    private var cachedNearbyNetworks: List<NearbyNetwork> = emptyList()
    private var lastWifiScanMillis: Long = 0L

    /**
     * Monotonic ID for the most recent connect attempt the user initiated.
     * Each call to [connectToParsedWifi] increments this. Coroutines belonging
     * to older attempts must check `attemptId == currentAttemptId` before
     * touching `_state` so a stale callback for WiFi A cannot overwrite the
     * UI for the user's newer WiFi B request.
     */
    @Volatile private var currentAttemptId: Long = 0L

    /**
     * Active connect job, kept so a new connect call can cancel the previous
     * coroutine immediately. Cancelling propagates cooperatively into the
     * suspending wait inside [WifiConnector.connect].
     */
    private var connectJob: Job? = null
    private var activeConnectRequest: WifiConnectRequestKey? = null

    companion object {
        private const val TAG = "MainViewModel_WiFi"
        // Outer guard. WifiConnector itself caps at ~25-30s; this is just a
        // belt-and-braces ceiling for the coroutine. Generous so we never
        // pre-empt a real connection that just lands slowly on the OS side.
        private const val WIFI_CONNECT_TIMEOUT_MS = 45_000L
        // Grace window for one final SSID re-check after WifiConnector returns
        // Failed. Connector itself uses event-driven NetworkCallback so the
        // happy path is instant. The grace mostly covers:
        //   - User tapping the system Wi-Fi panel a few seconds after we
        //     opened it (callback fires while we're already in the grace).
        //   - OEMs that deliver onCapabilitiesChanged after the request slot
        //     was already released.
        private const val WIFI_POST_TIMEOUT_ASSOCIATION_GRACE_MS = 12_000L
        // Hotspots and tethered Wi-Fi often need a couple extra seconds before
        // Android updates WifiManager/validation state, even though the user
        // can already use the network. Give them a short grace window before
        // showing a "no internet" warning.
        private const val WIFI_CONNECTED_WITHOUT_INTERNET_GRACE_MS = 3_500L
    }

    init {
        loadLatestSavedWifi()
        refreshHistory()
        startLiveSsidWatcher()
    }

    /**
     * Polls the actual SSID the device is associated with at the OS level
     * (via WifiManager.connectionInfo) and pushes it into [MainUiState.liveConnectedSsid]
     * so the Home screen "Đang kết nối tới ..." reflects reality, not just
     * the in-app connection state. Also reacts to default-network changes
     * via ConnectivityManager for instant updates when the user toggles WiFi
     * or switches networks outside the app.
     */
    private fun startLiveSsidWatcher() {
        // Initial snapshot
        refreshLiveConnectedSsid()

        // Poll loop — 2s cadence is enough for "Home" badge UX without
        // burning battery. WifiManager.connectionInfo is cheap (~µs).
        viewModelScope.launch {
            while (true) {
                delay(2_000L)
                refreshLiveConnectedSsid()
            }
        }

        // Listen for default network changes for instant updates when user
        // turns WiFi on/off or switches networks via system settings.
        runCatching {
            val cm = getApplication<Application>()
                .getSystemService(ConnectivityManager::class.java) ?: return@runCatching
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) = refreshLiveConnectedSsid()
                override fun onLost(network: android.net.Network) = refreshLiveConnectedSsid()
                override fun onCapabilitiesChanged(
                    network: android.net.Network,
                    networkCapabilities: NetworkCapabilities,
                ) = refreshLiveConnectedSsid()
            })
        }
    }

    private fun refreshLiveConnectedSsid() {
        // Priority: actual OS-level SSID > app's keep-alive SSID.
        // On Vivo, WifiManager.connectionInfo may still report the OLD system-
        // level WiFi while the Specifier-bound network (app-bound) is actually
        // connected to the NEW WiFi. In that case, use the keep-alive SSID
        // which reflects what the app successfully connected to.
        val osSsid = wifiConnector.getActualConnectedSsid()
        val keepAlive = wifiConnector.getKeptAliveSsid()
        val live = osSsid ?: keepAlive
        val current = _state.value.liveConnectedSsid
        if (live != current) {
            _state.update { it.copy(liveConnectedSsid = live) }
        }
    }

    fun onDarkModeChanged(enabled: Boolean) {
        _state.update { it.copy(isDarkModeEnabled = enabled) }
    }

    fun onAutoConnectPreferenceChanged(enabled: Boolean) {
        _state.update { it.copy(autoConnectEnabled = enabled) }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            // Snapshot SSIDs BEFORE wiping the DB so we can per-SSID forget
            // suggestions and only disconnect the WiFi if it is among the
            // cleared records. This preserves the rule "delete must not
            // disconnect a WiFi that is not the one being deleted".
            val ssidsBeingDeleted = runCatching { repository.getSavedWifiHistory() }
                .getOrDefault(emptyList())
                .map { it.ssid }
                .filter { it.isNotBlank() }
                .distinct()

            runCatching {
                repository.clearSavedWifiHistory()
            }.onSuccess { deletedCount ->
                // Per-SSID forget: removes the WifiNetworkSuggestion for each
                // saved SSID. forgetNetwork() internally only calls
                // releaseAll() when the current keep-alive SSID matches —
                // so the active WiFi connection is preserved as long as the
                // user is not connected to one of the deleted SSIDs.
                ssidsBeingDeleted.forEach { ssid ->
                    runCatching { wifiConnector.forgetNetwork(ssid) }
                }
                _state.update {
                    it.copy(
                        historyRecords = emptyList(),
                        selectedNetworkDetail = null,
                        selectedNetworkTelemetry = null,
                        // Only flip wifiConnectionState back to Idle when the
                        // current connection was actually severed by one of
                        // the forgetNetwork() calls above. Otherwise leave it
                        // alone so the UI keeps showing "Connected".
                        wifiConnectionState = if (wifiConnector.getKeptAliveSsid() == null) {
                            WifiConnectionState.Idle
                        } else {
                            it.wifiConnectionState
                        },
                        statusMessage = if (deletedCount > 0) {
                            "Đã xóa $deletedCount mục lịch sử kết nối."
                        } else {
                            "Lịch sử đã trống sẵn."
                        },
                    )
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(statusMessage = "Không xóa được lịch sử: ${err.message ?: "lỗi không xác định"}")
                }
            }
        }
    }

    fun openNetworkDetailFromRecent(network: RecentNetworkUiModel) {
        val current = _state.value
        val savedRecord = current.historyRecords.firstOrNull { it.id == network.sourceRecordId }
            ?: current.historyRecords.firstOrNull { it.ssid.equals(network.name, ignoreCase = true) }
        val scannedNetwork = getCachedNearbyNetworks()
            .firstOrNull { it.ssid.equals(network.name, ignoreCase = true) }
        val detail = savedRecord?.toNetworkDetailUiModel(
            origin = NetworkDetailOrigin.HOME,
            isConnected = isCurrentNetworkInternetValidated(network.name) || network.isConnected,
            scannedNetwork = scannedNetwork,
        ) ?: network.toNetworkDetailUiModel(
            origin = NetworkDetailOrigin.HOME,
            scannedNetwork = scannedNetwork,
        )

        _state.update {
            it.copy(
                selectedNetworkDetail = detail,
                selectedNetworkTelemetry = null,
            )
        }
        refreshSelectedNetworkTelemetry()
    }

    fun openNetworkDetailFromHistory(record: SavedWifiRecord) {
        val scannedNetwork = getCachedNearbyNetworks()
            .firstOrNull { it.ssid.equals(record.ssid, ignoreCase = true) }
        _state.update {
            it.copy(
                selectedNetworkDetail = record.toNetworkDetailUiModel(
                    origin = NetworkDetailOrigin.HISTORY,
                    isConnected = isCurrentNetworkInternetValidated(record.ssid),
                    scannedNetwork = scannedNetwork,
                ),
                selectedNetworkTelemetry = null,
            )
        }
        refreshSelectedNetworkTelemetry()
    }

    fun clearSelectedNetworkDetail() {
        _state.update {
            it.copy(
                selectedNetworkDetail = null,
                selectedNetworkTelemetry = null,
            )
        }
    }


    fun postTransientUserMessage(message: String) {
        _state.update { it.copy(transientUserMessage = message) }
    }

    fun consumeTransientUserMessage() {
        _state.update { it.copy(transientUserMessage = null) }
    }

    fun refreshSelectedNetworkTelemetry() {
        val selected = _state.value.selectedNetworkDetail ?: return
        val telemetry = getCurrentNetworkTelemetry(selected.ssid)
        _state.update { current ->
            val latestDetail = current.selectedNetworkDetail ?: return@update current
            current.copy(
                selectedNetworkDetail = latestDetail.copy(
                    isConnected = isCurrentNetworkInternetValidated(latestDetail.ssid),
                ),
                selectedNetworkTelemetry = telemetry,
            )
        }
    }

    fun connectToSelectedNetworkDetail() {
        val detail = _state.value.selectedNetworkDetail ?: return
        _state.update {
            it.copy(
                ssid = detail.ssid,
                password = detail.password,
                security = detail.security.ifBlank { detail.protocolLabel },
                wifiConnectionState = WifiConnectionState.Idle,
            )
        }
        connectToParsedWifi()
    }

    fun deleteSelectedNetworkDetail() {
        val detail = _state.value.selectedNetworkDetail ?: return
        val recordId = detail.savedRecordId ?: return
        viewModelScope.launch {
            // Determine BEFORE deletion whether the SSID being deleted is the
            // one the device is currently using. We must NOT disconnect or
            // touch any other WiFi the user is actively connected to.
            val keepAliveSsid = wifiConnector.getKeptAliveSsid()
            val osLevelSsid = wifiConnector.getActualConnectedSsid()
            val isDeletingActiveWifi = listOfNotNull(keepAliveSsid, osLevelSsid).any { active ->
                active.equals(detail.ssid, ignoreCase = true)
            }

            val deleted = runCatching {
                repository.deleteSavedWifiRecord(recordId)
            }.getOrDefault(false)
            if (!deleted) {
                _state.update {
                    it.copy(statusMessage = "Chưa xóa được mạng '${detail.ssid}'.")
                }
                return@launch
            }

            // forgetNetwork() is per-SSID safe: it only releases the active
            // callback / keep-alive when the active SSID equals the one being
            // forgotten. Suggestions are removed strictly for this SSID,
            // so any other saved WiFi is untouched.
            wifiConnector.forgetNetwork(detail.ssid)

            _state.update { state ->
                state.copy(
                    historyRecords = state.historyRecords.filterNot { record -> record.id == recordId },
                    selectedNetworkDetail = null,
                    selectedNetworkTelemetry = null,
                    // Only reset to Idle if the deleted WiFi was the active
                    // one. Otherwise leave the existing Connected state alone
                    // so the UI keeps reflecting the live connection.
                    wifiConnectionState = if (isDeletingActiveWifi) {
                        WifiConnectionState.Idle
                    } else {
                        state.wifiConnectionState
                    },
                    statusMessage = if (isDeletingActiveWifi) {
                        "Đã xóa '${detail.ssid}'. Mạng đang dùng có thể bị ngắt — hãy chọn Wi-Fi khác nếu cần."
                    } else {
                        "Đã xóa mạng '${detail.ssid}' khỏi lịch sử. Wi-Fi đang dùng vẫn được giữ nguyên."
                    },
                    transientUserMessage = if (isDeletingActiveWifi) {
                        "Đã xóa Wi-Fi đang dùng '${detail.ssid}'. Có thể mất kết nối."
                    } else {
                        state.transientUserMessage
                    },
                )
            }
        }
    }

    fun onBaseUrlChanged(value: String) {
        _state.update { it.copy(baseUrl = value) }
    }

    fun onOcrTextChanged(value: String) {
        _state.update {
            it.copy(
                ocrText = value,
                ssid = "",
                password = "",
                security = "",
                sourceFormat = "",
                confidence = null,
                aiValidation = AiValidationState.Hidden,
                ssidSuggestion = SsidSuggestionState.Hidden,
                nearbyNetworks = emptyList(),
                wifiConnectionState = WifiConnectionState.Idle,
                isNearbyExpanded = false,
            )
        }
    }

    fun onSsidChanged(value: String) {
        _state.update { it.copy(ssid = value, wifiConnectionState = WifiConnectionState.Idle) }
    }

    fun onPasswordChanged(value: String) {
        _state.update { it.copy(password = value, wifiConnectionState = WifiConnectionState.Idle) }
    }

    fun onSecurityChanged(value: String) {
        _state.update { it.copy(security = value, wifiConnectionState = WifiConnectionState.Idle) }
    }

    fun clearWifiConnectionState() {
        _state.update { it.copy(wifiConnectionState = WifiConnectionState.Idle) }
    }

    fun connectToParsedWifi() {
        val current = _state.value
        val requestedSsid = current.ssid.trim()
        val password = current.password.trim().takeIf { it.isNotEmpty() }
        val security = current.security.trim().takeIf { it.isNotEmpty() }
        val requestKey = WifiConnectRequestKey.from(
            ssid = requestedSsid,
            password = password,
            security = security,
        )

        if (connectJob?.isActive == true && activeConnectRequest == requestKey) {
            Log.d(TAG, "▶ Duplicate connect request for '$requestedSsid' ignored — existing request is still active")
            return
        }

        // ── Allocate a fresh attemptId. Every coroutine forked from this
        //    call captures `myAttempt` and uses applyIfCurrent() before
        //    mutating _state — so a stale callback from WiFi A can NEVER
        //    overwrite the UI for the user's newer WiFi B attempt. ──
        val myAttempt = ++currentAttemptId
        val priorActiveSsid = wifiConnector.getActualConnectedSsid()
        val priorKeepAlive = wifiConnector.getKeptAliveSsid()

        // ── DEBUG: Log connection request from UI ──
        Log.d(TAG, "════════════════════════════════════════════════════════════")
        Log.d(TAG, "▶ connectToParsedWifi() CALLED — attemptId=$myAttempt")
        Log.d(TAG, "  Current SSID:    '$priorActiveSsid' (OS-level)")
        Log.d(TAG, "  Keep-alive SSID: '$priorKeepAlive'")
        Log.d(TAG, "  Target SSID:     '$requestedSsid'")
        Log.d(TAG, "  Password length: ${password?.length ?: 0}")
        Log.d(TAG, "  Security:        '${security ?: "<auto>"}'")
        Log.d(TAG, "  Source format:   '${current.sourceFormat}'")
        Log.d(TAG, "  Auto-connect:    ${current.autoConnectEnabled}")
        Log.d(TAG, "  Android version: ${Build.VERSION.SDK_INT}")
        Log.d(TAG, "  Device:          ${Build.MANUFACTURER} ${Build.MODEL}")
        Log.d(TAG, "════════════════════════════════════════════════════════════")

        if (requestedSsid.isBlank()) {
            val message = if (password != null) {
                "OCR chỉ đọc được mật khẩu. Hãy nhập hoặc chọn SSID trước khi kết nối."
            } else {
                "Cần nhập SSID trước khi kết nối Wi-Fi."
            }
            _state.update {
                it.copy(
                    wifiConnectionState = WifiConnectionState.Failed(
                        reason = WifiConnectFailureReason.INVALID_INPUT,
                        message = message,
                    ),
                    statusMessage = message,
                )
            }
            return
        }

        if (!isWifiEnabled()) {
            val message = "Wi-Fi đang tắt. Hãy bật Wi-Fi để kết nối hoặc thoát ứng dụng."
            _state.update {
                it.copy(
                    wifiConnectionState = WifiConnectionState.Failed(
                        reason = WifiConnectFailureReason.INVALID_INPUT,
                        message = message,
                    ),
                    statusMessage = message,
                )
            }
            return
        }

        if (isRunningOnEmulator()) {
            _state.update {
                it.copy(
                    wifiConnectionState = WifiConnectionState.Failed(
                        reason = WifiConnectFailureReason.UNSUPPORTED_DEVICE,
                        message = "Emulator không hỗ trợ kết nối Wi-Fi thật. Hãy test trên điện thoại Android thật.",
                    ),
                    statusMessage = "Đang chạy trên emulator: không thể kết nối trực tiếp tới router Wi-Fi thật.",
                )
            }
            return
        }

        if (!hasNearbyWifiPermission()) {
            onWifiConnectionPermissionDenied()
            return
        }

        if (!isLocationServiceEnabled()) {
            val message = "Vui lòng bật Dịch vụ vị trí để quét Wi-Fi."
            _state.update {
                it.copy(
                    wifiConnectionState = WifiConnectionState.Failed(
                        reason = WifiConnectFailureReason.LOCATION_DISABLED,
                        message = message,
                    ),
                    statusMessage = message,
                )
            }
            return
        }

        activeConnectRequest = requestKey

        // Cancel only a genuinely different in-flight connect request. A
        // duplicate tap for the same credentials is ignored above so Android's
        // system chooser is not canceled while it is still on screen.
        connectJob?.let { existing ->
            if (existing.isActive) {
                Log.d(TAG, "▶ Cancelling prior connectJob (attempt #${myAttempt - 1}) — superseded by attempt #$myAttempt")
                existing.cancel()
            }
        }

        connectJob = viewModelScope.launch {
            // Coroutine-local guard: a coroutine started for attempt N must
            // never mutate _state if the user has already started attempt N+1.
            // This protects the UI from stale callbacks for the previous SSID.
            val isCurrent: () -> Boolean = { currentAttemptId == myAttempt }
            val applyIfCurrent: (String, (MainUiState) -> MainUiState) -> Unit = { tag, transform ->
                if (isCurrent()) {
                    _state.update(transform)
                } else {
                    Log.d(TAG, "  [attempt #$myAttempt] DROPPED state update '$tag' — superseded by attempt #$currentAttemptId")
                }
            }

            // Single funnel for "device joined SSID — verify internet then
            // either flip to Connected or to ConnectedWithoutInternet".
            // Every escalation path calls this so we never show a premature
            // green checkmark while Android is still validating.
            //
            // Returns true if the funnel committed a final UI state (Connected
            // or ConnectedWithoutInternet), false if the attempt was
            // superseded by a newer one and nothing should follow.
            val finalizeAfterAssociated: suspend (String, String) -> Boolean = label@{ ssid, debugSource ->
                if (!isCurrent()) {
                    Log.d(TAG, "  ▸ [attempt #$myAttempt] finalize($debugSource) skipped — superseded")
                    return@label false
                }

                Log.d(
                    TAG,
                    "  ▸ [attempt #$myAttempt] Verifying Internet ($debugSource) for '$ssid'...",
                )
                applyIfCurrent("verifying-internet-$debugSource") {
                    it.copy(
                        ssid = ssid,
                        wifiConnectionState = WifiConnectionState.Connecting(
                            ssid = ssid,
                            phase = WifiConnectionPhase.VERIFYING_INTERNET,
                        ),
                        statusMessage = "Đã kết nối Wi-Fi '$ssid', đang kiểm tra Internet...",
                    )
                }

                val outcome = runCatching {
                    wifiConnector.checkRealInternetWithRetries(ssid)
                }.getOrNull()

                if (!isCurrent()) {
                    Log.d(TAG, "  ▸ [attempt #$myAttempt] finalize($debugSource) post-probe skipped — superseded")
                    return@label false
                }

                val ssidNow = wifiConnector.getActualConnectedSsid()
                val ssidStillMatches = ssidNow != null && ssidNow.equals(ssid, ignoreCase = true)
                val ssidNotSilentlyChanged = ssidStillMatches || ssidNow == null
                Log.d(
                    TAG,
                    "  ▸ [attempt #$myAttempt] finalize($debugSource): ssidNow='$ssidNow', match=$ssidStillMatches, probe.success=${outcome?.success}, probe.reason='${outcome?.reason}'",
                )

                if (!ssidNotSilentlyChanged) {
                    val msg = "Thiết bị đã rời khỏi mạng '$ssid' (hiện đang dùng '$ssidNow'). Vui lòng thử lại."
                    Log.w(TAG, "  ▸ [attempt #$myAttempt] SSID rolled back during finalize($debugSource)")
                    applyIfCurrent("ssid-rolled-back-$debugSource") {
                        it.copy(
                            wifiConnectionState = WifiConnectionState.Failed(
                                reason = WifiConnectFailureReason.AUTHENTICATION_OR_UNAVAILABLE,
                                message = msg,
                            ),
                            statusMessage = msg,
                        )
                    }
                    return@label true
                }

                val saved = runCatching {
                    repository.saveConnectedNetworkLocal(
                        baseUrl = current.baseUrl,
                        ocrText = current.ocrText.ifBlank { "Kết nối từ kết quả OCR" },
                        ssid = ssid,
                        password = password,
                        sourceFormat = current.sourceFormat.takeIf { it.isNotBlank() },
                        confidence = current.confidence,
                    )
                }.getOrNull()
                if (saved != null) {
                    _state.update { state ->
                        state.copy(
                            historyRecords = listOf(saved) +
                                state.historyRecords.filterNot {
                                    it.id == saved.id ||
                                        it.ssid.equals(saved.ssid, ignoreCase = true)
                                },
                        )
                    }
                }

                if (outcome?.success == true) {
                    val successMessage = if (saved != null) {
                        "Kết nối Wi-Fi thành công: $ssid. Đã lưu lịch sử trên thiết bị."
                    } else {
                        "Kết nối Wi-Fi thành công: $ssid. Chưa lưu được lịch sử, hãy thử lại sau."
                    }
                    Log.d(TAG, "  ✓ [attempt #$myAttempt] FINAL SUCCESS for '$ssid' via $debugSource (probe='${outcome.reason}')")
                    applyIfCurrent("connected-final-$debugSource") {
                        it.copy(
                            ssid = ssid,
                            wifiConnectionState = WifiConnectionState.Connected(ssid = ssid),
                            statusMessage = successMessage,
                            transientUserMessage = "Kết nối Wi-Fi thành công.",
                        )
                    }
                } else {
                    val noInternetMsg =
                        "Đã kết nối Wi-Fi '$ssid' nhưng chưa xác thực được Internet. Hãy đảm bảo điểm phát có dữ liệu di động/Internet."
                    Log.w(
                        TAG,
                        "  ▸ [attempt #$myAttempt] CONNECTED_NO_INTERNET for '$ssid' via $debugSource — probeReason='${outcome?.reason}'",
                    )
                    applyIfCurrent("connected-no-internet-$debugSource") {
                        it.copy(
                            ssid = ssid,
                            wifiConnectionState = WifiConnectionState.ConnectedWithoutInternet(
                                ssid = ssid,
                                message = noInternetMsg,
                                isCaptivePortal = false,
                            ),
                            statusMessage = noInternetMsg,
                            transientUserMessage = noInternetMsg,
                        )
                    }
                }
                true
            }

            val scannedNearby = getNearbyNetworksOffMain()

            // ── DEBUG: Log scan results used for connection ──
            Log.d(TAG, "▶ WiFi scan for connection: ${scannedNearby.size} networks")
            scannedNearby.forEachIndexed { i, net ->
                val matchIndicator = if (net.ssid.equals(requestedSsid, ignoreCase = true)) " ← EXACT MATCH" else ""
                Log.d(TAG, "  [$i] '${net.ssid}' RSSI=${net.signalDbm}dBm${matchIndicator}")
            }

            val connectionPlan = resolveWifiConnectionPlan(
                requestedSsid = requestedSsid,
                nearbyNetworks = scannedNearby,
                sourceFormat = current.sourceFormat,
            ) ?: run {
                val failure = WifiConnectResult.Failed(
                    reason = WifiConnectFailureReason.INVALID_INPUT,
                    message = "Cần nhập SSID trước khi kết nối Wi-Fi.",
                )
                val uiMessage = failure.message ?: "Dữ liệu kết nối không hợp lệ."
                applyIfCurrent("plan-resolution-failed") {
                    it.copy(
                        wifiConnectionState = WifiConnectionState.Failed(
                            reason = failure.reason,
                            message = uiMessage,
                        ),
                        statusMessage = uiMessage,
                    )
                }
                return@launch
            }

            // ── DEBUG: Log connection plan ──
            Log.d(TAG, "▶ CONNECTION PLAN [attempt #$myAttempt]:")
            Log.d(TAG, "  Candidate SSIDs: ${connectionPlan.candidateSsids}")
            Log.d(TAG, "  Missing from scan: ${connectionPlan.directConnectionMissingFromScan}")
            Log.d(TAG, "  Resolved from fuzzy: ${connectionPlan.resolvedFromFuzzyMatch}")
            Log.d(TAG, "  Requires user selection: ${connectionPlan.requiresUserSelection}")
            Log.d(TAG, "  Suggested SSID: ${connectionPlan.suggestedSsid}")
            Log.d(TAG, "  Suggested score: ${connectionPlan.suggestedScore}")

            if (connectionPlan.requiresUserSelection) {
                val uiMessage = "Tên Wi-Fi từ OCR chưa khớp chắc chắn với danh sách Wi-Fi thật. Hãy chọn đúng SSID trong danh sách xung quanh rồi kết nối."
                applyIfCurrent("requires-user-selection") {
                    it.copy(
                        wifiConnectionState = WifiConnectionState.Failed(
                            reason = WifiConnectFailureReason.INVALID_INPUT,
                            message = uiMessage,
                        ),
                        statusMessage = uiMessage,
                        nearbyNetworks = scannedNearby,
                        nearbyWifiStatus = buildNearbyWifiStatus(scannedNearby),
                        ssidSuggestion = connectionPlan.suggestedSsid?.let { suggested ->
                            SsidSuggestionState.Found(
                                bestMatch = suggested,
                                score = connectionPlan.suggestedScore ?: 0.0,
                            )
                        } ?: it.ssidSuggestion,
                        isNearbyExpanded = true,
                    )
                }
                return@launch
            }

            var lastFailure: WifiConnectResult.Failed? = null

            for ((index, targetSsid) in connectionPlan.candidateSsids.withIndex()) {
                applyIfCurrent("connecting-phase") {
                    it.copy(
                        wifiConnectionState = WifiConnectionState.Connecting(
                            ssid = targetSsid,
                            phase = WifiConnectionPhase.CONNECTING_WIFI,
                        ),
                        statusMessage = buildWifiConnectionAttemptMessage(
                            targetSsid = targetSsid,
                            plan = connectionPlan,
                            attemptIndex = index,
                        ),
                    )
                }

                launch {
                    delay(6_000L)
                    _state.update { state ->
                        val connecting = state.wifiConnectionState as? WifiConnectionState.Connecting
                        if (connecting?.ssid == targetSsid) {
                            state.copy(
                                wifiConnectionState = WifiConnectionState.Connecting(
                                    ssid = targetSsid,
                                    phase = WifiConnectionPhase.VERIFYING_INTERNET,
                                ),
                                statusMessage = "Đang xác minh Internet cho Wi-Fi $targetSsid...",
                            )
                        } else {
                            state
                        }
                    }
                }

                val result = runCatching {
                    withTimeout(WIFI_CONNECT_TIMEOUT_MS) {
                        connectWifi(
                            ssid = targetSsid,
                            password = password,
                            security = security,
                        )
                    }
                }.getOrElse { throwable ->
                    if (throwable is TimeoutCancellationException) {
                        WifiConnectResult.Failed(
                            reason = WifiConnectFailureReason.TIMEOUT,
                            message = "Kết nối Wi-Fi quá lâu.",
                        )
                    } else {
                        WifiConnectResult.Failed(
                            reason = WifiConnectFailureReason.UNKNOWN,
                            message = throwable.message,
                        )
                    }
                }

                when (result) {
                    is WifiConnectResult.Success -> {
                        Log.d(TAG, "════════════════════════════════════════════════════════════")
                        Log.d(
                            TAG,
                            "▶ [attempt #$myAttempt] WifiConnector returned Success for '${result.ssid}' — entering finalizeAfterAssociated",
                        )
                        Log.d(TAG, "════════════════════════════════════════════════════════════")
                        finalizeAfterAssociated(result.ssid, "specifier-success")
                        return@launch
                    }

                    is WifiConnectResult.ConnectedWithoutInternet -> {
                        // Double-check the device's REAL associated SSID. The
                        // "no validated internet" signal often arrives slower
                        // than association on real devices (esp. Vivo/Xiaomi)
                        // — by the time we get here, the WiFi is already
                        // usable. Don't punish the user with a "no internet"
                        // screen + bounce back to OCR if the device is
                        // actually on the right WiFi.
                        val actualSsid = wifiConnector.getActualConnectedSsid()
                        val deviceIsOnTargetSsid = actualSsid != null &&
                            actualSsid.equals(result.ssid, ignoreCase = true)
                        Log.d(
                            TAG,
                            "  ConnectedWithoutInternet check: actualSsid='$actualSsid', target='${result.ssid}', match=$deviceIsOnTargetSsid, captive=${result.isCaptivePortal}",
                        )

                        val joinedAfterGrace =
                            if (!deviceIsOnTargetSsid && !result.isCaptivePortal && isCurrent()) {
                                applyIfCurrent("connected-without-internet-grace") {
                                    it.copy(
                                        wifiConnectionState = WifiConnectionState.Connecting(
                                            ssid = result.ssid,
                                            phase = WifiConnectionPhase.VERIFYING_INTERNET,
                                        ),
                                        statusMessage = "Đang chờ Wi-Fi ${result.ssid} hoàn tất xác minh kết nối...",
                                    )
                                }
                                waitForActualConnectedSsid(
                                    expectedSsid = result.ssid,
                                    timeoutMillis = WIFI_CONNECTED_WITHOUT_INTERNET_GRACE_MS,
                                )
                            } else {
                                false
                            }
                        val deviceJoinedTargetSsid = deviceIsOnTargetSsid || joinedAfterGrace

                        if (deviceIsOnTargetSsid && !result.isCaptivePortal) {
                            Log.d(TAG, "  ✓ Device IS on target SSID — routing through finalizeAfterAssociated")
                            finalizeAfterAssociated(result.ssid, "joined-without-internet-immediate")
                            return@launch
                        }

                        if (deviceJoinedTargetSsid && !result.isCaptivePortal) {
                            Log.d(TAG, "  ✓ Device joined '${result.ssid}' during grace — routing through finalizeAfterAssociated")
                            finalizeAfterAssociated(result.ssid, "joined-without-internet-grace")
                            return@launch
                        }

                        // Genuine "joined but no internet" (rare) — show the
                        // soft warning state, do NOT bounce to OCR.
                        // FINAL CHECK: real HTTP probe with retries (1s/3s/5s)
                        // through the bound network. Personal hotspots (iPhone,
                        // Android) often never get NET_CAPABILITY_VALIDATED
                        // but DO serve internet after a 2-5s auth handshake.
                        // Falling straight to "no internet" UI would punish
                        // the user even though the WiFi is fine.
                        Log.d(
                            TAG,
                            "  ▸ [attempt #$myAttempt] Running internet probe with retries for '${result.ssid}' (target='${result.ssid}', actual='${wifiConnector.getActualConnectedSsid()}')",
                        )
                        val httpOutcome = runCatching {
                            wifiConnector.checkRealInternetWithRetries(result.ssid)
                        }.getOrNull()
                        Log.d(
                            TAG,
                            "  ▸ Internet probe outcome: success=${httpOutcome?.success} reason='${httpOutcome?.reason}' probedNetwork=${httpOutcome?.probedNetwork} associatedSsid='${httpOutcome?.associatedSsid}'",
                        )
                        val httpProbeOk = httpOutcome?.success == true
                        if (httpProbeOk && !result.isCaptivePortal) {
                            Log.d(TAG, "  ✓ HTTP probe succeeded — escalating to Success (likely personal hotspot)")
                            val localSavedRecord = runCatching {
                                repository.saveConnectedNetworkLocal(
                                    baseUrl = current.baseUrl,
                                    ocrText = current.ocrText.ifBlank { "Kết nối từ kết quả OCR" },
                                    ssid = result.ssid,
                                    password = password,
                                    sourceFormat = current.sourceFormat.takeIf { it.isNotBlank() },
                                    confidence = current.confidence,
                                )
                            }.getOrNull()

                            if (localSavedRecord != null) {
                                _state.update { state ->
                                    state.copy(
                                        historyRecords = listOf(localSavedRecord) +
                                            state.historyRecords.filterNot {
                                                it.id == localSavedRecord.id ||
                                                    it.ssid.equals(localSavedRecord.ssid, ignoreCase = true)
                                            },
                                    )
                                }
                            }

                            applyIfCurrent("http-probe-success") {
                                it.copy(
                                    ssid = result.ssid,
                                    wifiConnectionState = WifiConnectionState.Connected(ssid = result.ssid),
                                    statusMessage = "Kết nối Wi-Fi thành công: ${result.ssid}.",
                                    transientUserMessage = "Kết nối Wi-Fi thành công.",
                                )
                            }
                            return@launch
                        }

                        // Probe failed but the device IS on the target SSID:
                        // emit the friendlier "WiFi joined, internet auth not
                        // confirmed yet" state instead of a hard failure.
                        // Specifically required by the hotspot-internet-auth
                        // flow: we never want to show "Fail" when WifiManager
                        // confirms the requested SSID is the live SSID.
                        val nowSsid = wifiConnector.getActualConnectedSsid()
                        val ssidActuallyMatches = nowSsid != null &&
                            nowSsid.equals(result.ssid, ignoreCase = true)
                        val uiMessage = if (ssidActuallyMatches) {
                            "Đã kết nối Wi-Fi '${result.ssid}' nhưng chưa xác thực được Internet. Kiểm tra hotspot/router rồi thử lại."
                        } else {
                            buildWifiConnectedWithoutInternetMessage(result)
                        }
                        Log.d(
                            TAG,
                            "  ▸ [attempt #$myAttempt] CONNECTED_NO_INTERNET for '${result.ssid}' — ssidActuallyMatches=$ssidActuallyMatches, finalUi='$uiMessage'",
                        )
                        applyIfCurrent("connected-no-internet") {
                            it.copy(
                                ssid = result.ssid,
                                wifiConnectionState = WifiConnectionState.ConnectedWithoutInternet(
                                    ssid = result.ssid,
                                    message = uiMessage,
                                    isCaptivePortal = result.isCaptivePortal,
                                ),
                                statusMessage = uiMessage,
                                transientUserMessage = uiMessage,
                            )
                        }
                        return@launch
                    }

                    is WifiConnectResult.Failed -> {
                        // Soft-failure grace: the OS may still be in the
                        // middle of switching to the target WiFi when the
                        // connector gives up. Re-poll getActualConnectedSsid()
                        // for WIFI_POST_TIMEOUT_ASSOCIATION_GRACE_MS before
                        // committing to a UI failure. Only "hard" failures
                        // (permissions, invalid input, unsupported device)
                        // bypass this grace.
                        val isSoftFailure = when (result.reason) {
                            WifiConnectFailureReason.TIMEOUT,
                            WifiConnectFailureReason.WRONG_PASSWORD_OR_REJECTED,
                            WifiConnectFailureReason.AUTHENTICATION_OR_UNAVAILABLE,
                            WifiConnectFailureReason.SSID_NOT_FOUND,
                            WifiConnectFailureReason.NETWORK_NOT_FOUND,
                            WifiConnectFailureReason.UNKNOWN -> true
                            else -> false
                        }
                        if (isSoftFailure) {
                            applyIfCurrent("verifying-internet") {
                                it.copy(
                                    wifiConnectionState = WifiConnectionState.Connecting(
                                        ssid = targetSsid,
                                        phase = WifiConnectionPhase.VERIFYING_INTERNET,
                                    ),
                                    statusMessage = "Đang chờ thiết bị hoàn tất chuyển sang Wi-Fi $targetSsid...",
                                )
                            }
                            // If the user already started a newer attempt for
                            // a different SSID, abort this stale wait — the
                            // newer connect coroutine is in charge of the UI.
                            if (!isCurrent()) {
                                Log.d(TAG, "  ▸ [attempt #$myAttempt] aborting grace wait — superseded by attempt #$currentAttemptId")
                                return@launch
                            }
                            val deviceJoinedAfterTimeout = waitForActualConnectedSsid(
                                expectedSsid = targetSsid,
                                timeoutMillis = WIFI_POST_TIMEOUT_ASSOCIATION_GRACE_MS,
                            )
                            Log.d(
                                TAG,
                                "  Soft-failure grace check [attempt #$myAttempt]: reason=${result.reason}, target='$targetSsid', joinedAfterGrace=$deviceJoinedAfterTimeout",
                            )
                            if (deviceJoinedAfterTimeout) {
                                Log.d(TAG, "  ✓ Device joined '$targetSsid' during grace — routing through finalizeAfterAssociated")
                                finalizeAfterAssociated(targetSsid, "soft-failure-grace")
                                return@launch
                            }
                        }

                        if (
                            result.reason == WifiConnectFailureReason.NO_INTERNET ||
                            result.reason == WifiConnectFailureReason.CAPTIVE_PORTAL
                        ) {
                            // Same escalation logic for Failed-NO_INTERNET:
                            // if the device truly is on the target WiFi, treat
                            // as Success rather than bouncing to OCR.
                            val actualSsid = wifiConnector.getActualConnectedSsid()
                            val deviceIsOnTargetSsid = actualSsid != null &&
                                actualSsid.equals(targetSsid, ignoreCase = true)
                            val isCaptive = result.reason == WifiConnectFailureReason.CAPTIVE_PORTAL
                            Log.d(
                                TAG,
                                "  Failed.NO_INTERNET/CAPTIVE check: actualSsid='$actualSsid', target='$targetSsid', match=$deviceIsOnTargetSsid, captive=$isCaptive",
                            )

                            if (deviceIsOnTargetSsid && !isCaptive) {
                                Log.d(TAG, "  ✓ Device IS on target SSID — routing through finalizeAfterAssociated")
                                finalizeAfterAssociated(targetSsid, "no-internet-escalation")
                                return@launch
                            }

                            val joinedResult = WifiConnectResult.ConnectedWithoutInternet(
                                ssid = targetSsid,
                                hasInternetCapability = result.reason == WifiConnectFailureReason.CAPTIVE_PORTAL,
                                isCaptivePortal = result.reason == WifiConnectFailureReason.CAPTIVE_PORTAL,
                            )
                            val uiMessage = buildWifiConnectedWithoutInternetMessage(joinedResult)
                            applyIfCurrent("connected-no-internet-final") {
                                it.copy(
                                    ssid = targetSsid,
                                    wifiConnectionState = WifiConnectionState.ConnectedWithoutInternet(
                                        ssid = targetSsid,
                                        message = uiMessage,
                                        isCaptivePortal = joinedResult.isCaptivePortal,
                                    ),
                                    statusMessage = uiMessage,
                                    transientUserMessage = uiMessage,
                                )
                            }
                            return@launch
                        }
                        lastFailure = result
                        if (!shouldTryNextNearbyCandidate(result, connectionPlan, index)) {
                            break
                        }
                    }
                }
            }

            val failure = lastFailure ?: WifiConnectResult.Failed(
                reason = WifiConnectFailureReason.UNKNOWN,
                message = "Không kết nối được Wi-Fi.",
            )
            val uiMessage = buildWifiConnectFailureMessage(
                result = failure,
                plan = connectionPlan,
            )

            // If we still have a kept-alive WiFi (the user was already
            // connected to another network before this attempt), do NOT flip
            // UI into a hard "Failed" state — that bounces them to the OCR
            // screen and looks like everything broke. Instead keep the
            // existing connection visible and surface the failure as a
            // transient toast, so the user can retry without losing their
            // current internet.
            val stillConnectedSsid = wifiConnector.getKeptAliveSsid()
            if (stillConnectedSsid != null) {
                Log.d(TAG, "  ▸ [attempt #$myAttempt] Connect to '$requestedSsid' failed but user is still on '$stillConnectedSsid' — keeping Connected state")
                applyIfCurrent("fallback-still-connected") {
                    it.copy(
                        ssid = stillConnectedSsid,
                        wifiConnectionState = WifiConnectionState.Connected(ssid = stillConnectedSsid),
                        statusMessage = "Vẫn đang dùng Wi-Fi '$stillConnectedSsid'. $uiMessage",
                        transientUserMessage = uiMessage,
                    )
                }
                return@launch
            }

            // Final terminal Failed flip. We re-check `isCurrent()` here so a
            // stale failure for WiFi A cannot overwrite the in-flight UI for
            // the user's newer WiFi B attempt.
            //
            // CRITICAL last-mile check (per hotspot-internet-auth requirement):
            // if WifiManager confirms device IS on the target SSID right now,
            // we MUST NOT show a hard "Fail". Instead probe internet with
            // retries 1s/3s/5s. The result is either Success or
            // ConnectedWithoutInternet — never Failed.
            val terminalActualSsid = wifiConnector.getActualConnectedSsid()
            val terminalSsidMatches = terminalActualSsid != null &&
                terminalActualSsid.equals(requestedSsid, ignoreCase = true)
            if (terminalSsidMatches) {
                Log.d(
                    TAG,
                    "  ▸ [attempt #$myAttempt] Connector reported Failed but device IS on target '$requestedSsid' — running internet retry probe",
                )
                val terminalProbe = runCatching {
                    wifiConnector.checkRealInternetWithRetries(requestedSsid)
                }.getOrNull()
                Log.d(
                    TAG,
                    "  ▸ Terminal probe outcome: success=${terminalProbe?.success} reason='${terminalProbe?.reason}' net=${terminalProbe?.probedNetwork} ssid='${terminalProbe?.associatedSsid}'",
                )
                if (terminalProbe?.success == true) {
                    Log.d(TAG, "  ✓ [attempt #$myAttempt] Terminal probe SUCCESS — escalating to Connected for '$requestedSsid'")
                    val localSavedRecord = runCatching {
                        repository.saveConnectedNetworkLocal(
                            baseUrl = current.baseUrl,
                            ocrText = current.ocrText.ifBlank { "Kết nối từ kết quả OCR" },
                            ssid = requestedSsid,
                            password = password,
                            sourceFormat = current.sourceFormat.takeIf { it.isNotBlank() },
                            confidence = current.confidence,
                        )
                    }.getOrNull()
                    if (localSavedRecord != null) {
                        _state.update { state ->
                            state.copy(
                                historyRecords = listOf(localSavedRecord) +
                                    state.historyRecords.filterNot {
                                        it.id == localSavedRecord.id ||
                                            it.ssid.equals(localSavedRecord.ssid, ignoreCase = true)
                                    },
                            )
                        }
                    }
                    applyIfCurrent("terminal-probe-success") {
                        it.copy(
                            ssid = requestedSsid,
                            wifiConnectionState = WifiConnectionState.Connected(ssid = requestedSsid),
                            statusMessage = "Kết nối Wi-Fi thành công: $requestedSsid.",
                            transientUserMessage = "Kết nối Wi-Fi thành công.",
                        )
                    }
                    return@launch
                }
                // SSID matches but probe failed — show the precise hotspot
                // message instead of a generic Fail. Per requirement: never
                // return "Fail" while currentSSID equals the selected SSID.
                val noInternetMessage =
                    "Đã kết nối Wi-Fi '$requestedSsid' nhưng chưa xác thực được Internet. Kiểm tra hotspot/router rồi thử lại."
                Log.w(
                    TAG,
                    "  ▸ [attempt #$myAttempt] SSID matches but internet probe failed — show ConnectedWithoutInternet (reason='${terminalProbe?.reason}')",
                )
                applyIfCurrent("terminal-probe-no-internet") {
                    it.copy(
                        ssid = requestedSsid,
                        wifiConnectionState = WifiConnectionState.ConnectedWithoutInternet(
                            ssid = requestedSsid,
                            message = noInternetMessage,
                            isCaptivePortal = false,
                        ),
                        statusMessage = noInternetMessage,
                        transientUserMessage = noInternetMessage,
                    )
                }
                return@launch
            }

            Log.d(TAG, "  ▸ [attempt #$myAttempt] Final FAILURE for '$requestedSsid' — reason=${failure.reason}, msg='$uiMessage'")
            applyIfCurrent("final-failed") {
                it.copy(
                    wifiConnectionState = WifiConnectionState.Failed(
                        reason = failure.reason,
                        message = uiMessage,
                    ),
                    statusMessage = uiMessage,
                )
            }
        }
        connectJob?.invokeOnCompletion {
            if (currentAttemptId == myAttempt && activeConnectRequest == requestKey) {
                activeConnectRequest = null
            }
        }
    }

    private fun resolveWifiConnectionPlan(
        requestedSsid: String,
        nearbyNetworks: List<NearbyNetwork>,
        sourceFormat: String,
    ): WifiConnectionPlan? {
        if (requestedSsid.isNotBlank()) {
            val exactScannedNetwork = nearbyNetworks.firstOrNull {
                it.ssid.equals(requestedSsid, ignoreCase = true)
            }
            if (exactScannedNetwork != null) {
                return WifiConnectionPlan(
                    candidateSsids = listOf(exactScannedNetwork.ssid),
                    directConnectionMissingFromScan = false,
                )
            }

            if (sourceFormat.isOcrDerivedSource() && nearbyNetworks.isNotEmpty()) {
                val fuzzyMatch = findBestMatch(requestedSsid, nearbyNetworks)
                if (fuzzyMatch != null && fuzzyMatch.score >= autoApplyFuzzySsidThreshold) {
                    return WifiConnectionPlan(
                        candidateSsids = listOf(fuzzyMatch.bestMatch),
                        directConnectionMissingFromScan = false,
                        resolvedFromFuzzyMatch = true,
                        suggestedSsid = fuzzyMatch.bestMatch,
                        suggestedScore = fuzzyMatch.score,
                    )
                }
                return WifiConnectionPlan(
                    candidateSsids = emptyList(),
                    directConnectionMissingFromScan = true,
                    requiresUserSelection = true,
                    suggestedSsid = fuzzyMatch?.bestMatch,
                    suggestedScore = fuzzyMatch?.score,
                )
            }

            val directConnectionMissingFromScan = nearbyNetworks.isNotEmpty()
            return WifiConnectionPlan(
                candidateSsids = listOf(requestedSsid),
                directConnectionMissingFromScan = directConnectionMissingFromScan,
            )
        }

        return null
    }

    private fun buildWifiConnectionAttemptMessage(
        targetSsid: String,
        plan: WifiConnectionPlan,
        attemptIndex: Int,
    ): String {
        return when {
            plan.resolvedFromFuzzyMatch ->
                "Đã khớp SSID từ OCR với '$targetSsid'. Đang kết nối và xác minh Internet..."
            plan.directConnectionMissingFromScan ->
                "Chưa thấy '$targetSsid' trong danh sách quét. Đang kết nối trực tiếp và xác minh Internet..."
            else ->
                "Đang kết nối Wi-Fi $targetSsid và xác minh Internet..."
        }
    }

    private fun shouldTryNextNearbyCandidate(
        failure: WifiConnectResult.Failed,
        plan: WifiConnectionPlan,
        attemptIndex: Int,
    ): Boolean {
        if (attemptIndex >= plan.candidateSsids.lastIndex) {
            return false
        }

        return when (failure.reason) {
            WifiConnectFailureReason.PERMISSION_DENIED,
            WifiConnectFailureReason.LOCATION_DISABLED,
            WifiConnectFailureReason.UNSUPPORTED_DEVICE,
            WifiConnectFailureReason.INVALID_INPUT -> false
            else -> true
        }
    }

    private fun buildWifiConnectedWithoutInternetMessage(
        result: WifiConnectResult.ConnectedWithoutInternet,
    ): String {
        return if (result.isCaptivePortal) {
            "Mạng Wi-Fi yêu cầu đăng nhập hoặc xác nhận trên trình duyệt."
        } else if (result.hasInternetCapability) {
            "Đã kết nối Wi-Fi nhưng mạng không có Internet."
        } else {
            "Đã kết nối Wi-Fi nhưng mạng không có Internet. Vui lòng kiểm tra router hoặc thử mạng khác."
        }
    }

    private suspend fun waitForActualConnectedSsid(
        expectedSsid: String,
        timeoutMillis: Long,
    ): Boolean {
        // 200ms cadence — fast enough that "device on target SSID" is detected
        // within ~200ms of the OS association, which is the same instant the
        // status-bar WiFi icon appears.
        val intervalMillis = 200L
        val attempts = (timeoutMillis / intervalMillis).coerceAtLeast(1L).toInt()
        repeat(attempts) {
            val actualSsid = wifiConnector.getActualConnectedSsid()
            if (actualSsid != null && actualSsid.equals(expectedSsid, ignoreCase = true)) {
                return true
            }
            delay(intervalMillis)
        }
        val actualSsid = wifiConnector.getActualConnectedSsid()
        return actualSsid != null && actualSsid.equals(expectedSsid, ignoreCase = true)
    }

    private fun buildWifiConnectFailureMessage(
        result: WifiConnectResult.Failed,
        plan: WifiConnectionPlan,
    ): String {
        val rawMessage = result.message.orEmpty()
        if (rawMessage.contains("passphrase not ASCII encodable", ignoreCase = true)) {
            return "Mật khẩu có ký tự không được Android hỗ trợ cho kiểu kết nối này. Vui lòng kiểm tra lại mật khẩu."
        }
        return when (result.reason) {
            WifiConnectFailureReason.PERMISSION_DENIED ->
                "Ứng dụng cần quyền Wi-Fi/Vị trí để quét và kết nối mạng."
            WifiConnectFailureReason.LOCATION_DISABLED ->
                "Vui lòng bật Dịch vụ vị trí để quét Wi-Fi."
            WifiConnectFailureReason.UNSUPPORTED_DEVICE ->
                "Thiết bị hiện tại không hỗ trợ kết nối Wi-Fi thực tế. Vui lòng thử trên điện thoại thật."
            WifiConnectFailureReason.SSID_NOT_FOUND ->
                "Không tìm thấy mạng Wi-Fi này trong khu vực."
            WifiConnectFailureReason.NETWORK_NOT_FOUND ->
                "Không tìm thấy mạng Wi-Fi này trong khu vực."
            WifiConnectFailureReason.WRONG_PASSWORD_OR_REJECTED ->
                "Không thể kết nối. Hãy kiểm tra mật khẩu hoặc bấm 'Kết nối' trên dialog xác nhận Wi-Fi của hệ thống."
            WifiConnectFailureReason.AUTHENTICATION_OR_UNAVAILABLE ->
                "Không thể kết nối Wi-Fi. Vui lòng kiểm tra lại tên mạng hoặc mật khẩu."
            WifiConnectFailureReason.NO_INTERNET ->
                "Đã kết nối Wi-Fi nhưng mạng không có Internet. Vui lòng kiểm tra router hoặc thử mạng khác."
            WifiConnectFailureReason.CAPTIVE_PORTAL ->
                "Mạng Wi-Fi yêu cầu đăng nhập hoặc xác nhận trên trình duyệt."
            WifiConnectFailureReason.TIMEOUT ->
                "Không thể xác minh Internet. Vui lòng thử lại hoặc đứng gần router hơn."
            WifiConnectFailureReason.INVALID_INPUT ->
                result.message?.takeIf { it.isFriendlyVietnameseMessage() }
                    ?: "Dữ liệu kết nối chưa hợp lệ. Hãy kiểm tra lại SSID và mật khẩu."
            WifiConnectFailureReason.UNKNOWN ->
                "Không thể kết nối Wi-Fi. Vui lòng thử lại hoặc kiểm tra cài đặt Wi-Fi của thiết bị."
        }
    }

    private fun String.isFriendlyVietnameseMessage(): Boolean {
        val lower = lowercase(Locale.ROOT)
        if (lower.contains("exception") || lower.contains("java.") || lower.contains("android.")) return false
        if (lower.contains("10.0.2.2") || lower.contains("127.0.0.1") || lower.contains("localhost")) return false
        if (lower.contains("failed to connect") || lower.contains("passphrase not ascii encodable")) return false
        return true
    }

    private fun String.isOcrDerivedSource(): Boolean {
        val normalized = lowercase(Locale.ROOT)
        return normalized == "ai_ocr" ||
            normalized == "ocr_server" ||
            normalized.startsWith("ocr_local")
    }

    fun onWifiConnectionPermissionDenied() {
        _state.update {
            it.copy(
                wifiConnectionState = WifiConnectionState.Failed(
                    reason = WifiConnectFailureReason.PERMISSION_DENIED,
                    message = "Ứng dụng cần quyền Wi-Fi/Vị trí để quét và kết nối mạng.",
                ),
                statusMessage = "Ứng dụng cần quyền Wi-Fi/Vị trí để quét và kết nối mạng.",
            )
        }
    }

    fun applyAiNormalizedSsid() {
        val aiState = _state.value.aiValidation as? AiValidationState.Ready ?: return
        val normalized = aiState.normalizedSsid?.takeIf { it.isNotBlank() } ?: return
        _state.update {
            it.copy(
                ssid = normalized,
                wifiConnectionState = WifiConnectionState.Idle,
                statusMessage = "Đã áp dụng SSID từ AI review.",
            )
        }
    }

    fun applyAiNormalizedPassword() {
        val aiState = _state.value.aiValidation as? AiValidationState.Ready ?: return
        val normalized = aiState.normalizedPassword?.takeIf { it.isNotBlank() } ?: return
        _state.update {
            it.copy(
                password = normalized,
                wifiConnectionState = WifiConnectionState.Idle,
                statusMessage = "Đã áp dụng mật khẩu từ AI review.",
            )
        }
    }

    fun cancelOcr() {
        ocrJob?.cancel()
        ocrJob = null
        _state.update {
            if (it.isLoading) {
                it.copy(
                    isLoading = false,
                    statusMessage = "Đã hủy quét OCR.",
                )
            } else {
                it
            }
        }
    }

    fun startOcrFromGallery(uri: Uri) {
        ocrJob?.cancel()
        ocrJob = viewModelScope.launch {
            setOcrLoading("Đang xử lý ảnh từ thư viện...")
            runCatching {
                val bitmap = withContext(Dispatchers.IO) { decodeBitmapFromUri(uri) }
                withContext(ocrDispatcher) { ocrProcessor.recognize(bitmap) }
            }.onSuccess { result ->
                handleOcrRecognitionSuccess(
                    source = "Thư viện ảnh",
                    text = result.text,
                    localCredentials = result.credentials,
                    localConfidence = result.confidence,
                    blankMessage = "OCR không đọc được nội dung. Thử ảnh rõ hơn hoặc đổi góc chụp.",
                )
            }.onFailure { throwable ->
                if (throwable is CancellationException) return@launch
                _state.update {
                    it.copy(
                        isLoading = false,
                        scanSource = "Thư viện ảnh",
                        statusMessage = "OCR không xử lý được ảnh này. Hãy thử ảnh rõ hơn, đủ sáng và chụp thẳng giấy.",
                    )
                }
            }
        }
    }

    fun startOcrFromCamera(bitmap: Bitmap) {
        ocrJob?.cancel()
        ocrJob = viewModelScope.launch {
            setOcrLoading("Đang quét OCR từ camera...")
            var ocrBitmap: Bitmap? = null
            try {
                runCatching {
                    ocrBitmap = withContext(ocrDispatcher) { bitmap.downscaleForOcrInput() }
                    withContext(ocrDispatcher) { ocrProcessor.recognize(ocrBitmap ?: bitmap) }
                }.onSuccess { result ->
                    handleOcrRecognitionSuccess(
                        source = "Máy quét",
                        text = result.text,
                        localCredentials = result.credentials,
                        localConfidence = result.confidence,
                        blankMessage = "OCR không đọc được nội dung. Thử chụp lại rõ hơn.",
                    )
                }.onFailure { throwable ->
                    if (throwable is CancellationException) return@launch
                    _state.update {
                        it.copy(
                            isLoading = false,
                            scanSource = "Máy quét",
                            statusMessage = "OCR không xử lý được khung hình này. Hãy chụp lại rõ hơn hoặc dùng ảnh từ thư viện.",
                        )
                    }
                }
            } finally {
                val processedBitmap = ocrBitmap
                if (processedBitmap != null && processedBitmap !== bitmap && !processedBitmap.isRecycled) {
                    processedBitmap.recycle()
                }
            }
        }
    }

    private suspend fun handleOcrRecognitionSuccess(
        source: String,
        text: String,
        blankMessage: String,
        preferLocalCredentials: Boolean = false,
        localCredentials: WifiOcrCredentials = WifiOcrCredentials(),
        localConfidence: Double? = null,
    ) {
        if (text.isBlank()) {
            _state.update {
                it.copy(
                    isLoading = false,
                    scanSource = source,
                    ocrText = "",
                    ssid = "",
                    password = "",
                    security = "",
                    sourceFormat = "",
                    confidence = null,
                    statusMessage = blankMessage,
                    aiValidation = AiValidationState.Hidden,
                    ssidSuggestion = SsidSuggestionState.Hidden,
                    nearbyNetworks = emptyList(),
                    nearbyWifiStatus = "",
                    wifiConnectionState = WifiConnectionState.Idle,
                    isNearbyExpanded = false,
                )
            }
            return
        }

        _state.update {
            it.copy(
                scanSource = source,
                ocrText = text,
                statusMessage = "OCR thành công. Đang dùng AI để lấy tên Wi-Fi và mật khẩu...",
                aiValidation = AiValidationState.Loading,
            )
        }

        val currentBaseUrl = _state.value.baseUrl
        val resolved = resolveOcrCredentials(
            baseUrl = currentBaseUrl,
            text = text,
            localCredentials = localCredentials,
            localConfidence = localConfidence,
            preferLocalCredentials = preferLocalCredentials,
        )

        val parsedSsid = resolved.parsed.ssid.orEmpty()
        val parsedPassword = resolved.parsed.password.orEmpty()

        // ── DEBUG: Log OCR final credentials ──
        Log.d(TAG, "════════════════════════════════════════════════════════════")
        Log.d(TAG, "▶ OCR CREDENTIALS RESOLVED")
        Log.d(TAG, "  SSID (raw from OCR): '${resolved.parsed.ssid}'")
        Log.d(TAG, "  SSID (after trim/normalize): '$parsedSsid'")
        Log.d(TAG, "  Password (length): ${parsedPassword.length}")
        Log.d(TAG, "  Password (masked): ${if (parsedPassword.isNotEmpty()) "${parsedPassword.first()}${"*".repeat((parsedPassword.length - 2).coerceAtLeast(0))}${parsedPassword.lastOrNull() ?: ""}" else "<empty>"}")
        Log.d(TAG, "  Security: '${resolved.parsed.security.orEmpty()}'")
        Log.d(TAG, "  Source format: '${resolved.parsed.sourceFormat.orEmpty()}'")
        Log.d(TAG, "  Confidence: ${resolved.parsed.confidence}")
        Log.d(TAG, "  AI state: ${resolved.aiState}")
        Log.d(TAG, "════════════════════════════════════════════════════════════")

        val nearbyNetworks = if (parsedSsid.isNotBlank() || parsedPassword.isNotBlank()) {
            getScannedNearbyNetworksForOcr()
                .ifEmpty { getCachedNearbyNetworks() }
        } else {
            emptyList()
        }
        val exactNearbySsid = nearbyNetworks.firstOrNull {
            it.ssid.equals(parsedSsid, ignoreCase = true)
        }?.ssid
        val fuzzyResolution = if (parsedSsid.isNotBlank() && nearbyNetworks.isNotEmpty()) {
            resolveFuzzySuggestion(
                baseUrl = currentBaseUrl,
                ocrSsid = parsedSsid,
                nearbyNetworks = nearbyNetworks,
            )
        } else {
            FuzzyResolution(
                state = SsidSuggestionState.Hidden,
                nearbyNetworks = nearbyNetworks,
                bestMatch = null,
                score = null,
            )
        }
        val fuzzyAutoMatch = fuzzyResolution.bestMatch?.takeIf {
            (fuzzyResolution.score ?: 0.0) >= autoApplyFuzzySsidThreshold
        }
        val resolvedNearbySsid = exactNearbySsid ?: fuzzyAutoMatch
        val resolvedSsid = resolvedNearbySsid ?: parsedSsid
        val resolvedSuggestionState = if (resolvedNearbySsid != null) {
            SsidSuggestionState.Hidden
        } else {
            fuzzyResolution.state
        }
        val resolvedStatusMessage = if (exactNearbySsid == null && fuzzyAutoMatch != null) {
            "Đã khớp tên Wi-Fi từ OCR với '$fuzzyAutoMatch'. Hãy kiểm tra mật khẩu rồi bấm Kết nối."
        } else {
            resolved.message
        }

        _state.update {
            it.copy(
                isLoading = false,
                ssid = resolvedSsid,
                password = parsedPassword,
                security = resolved.parsed.security.orEmpty(),
                sourceFormat = resolved.parsed.sourceFormat.orEmpty(),
                confidence = resolved.parsed.confidence,
                statusMessage = resolvedStatusMessage,
                aiValidation = resolved.aiState,
                ssidSuggestion = resolvedSuggestionState,
                nearbyNetworks = fuzzyResolution.nearbyNetworks.ifEmpty { nearbyNetworks },
                nearbyWifiStatus = buildNearbyWifiStatus(fuzzyResolution.nearbyNetworks.ifEmpty { nearbyNetworks }),
                wifiConnectionState = WifiConnectionState.Idle,
                isNearbyExpanded = resolvedSsid.isBlank() && parsedPassword.isNotBlank(),
            )
        }
    }

    private suspend fun getScannedNearbyNetworksForOcr(): List<NearbyNetwork> {
        return getNearbyNetworksOffMain(forceRefresh = true)
    }

    private suspend fun getNearbyNetworksOffMain(forceRefresh: Boolean = false): List<NearbyNetwork> {
        return if (deps?.scannedNearbyNetworks != null) {
            getScannedNearbyNetworks(forceRefresh = forceRefresh)
        } else {
            withContext(Dispatchers.IO) { getScannedNearbyNetworks(forceRefresh = forceRefresh) }
        }
    }

    private fun getCachedNearbyNetworks(): List<NearbyNetwork> {
        return cachedNearbyNetworks.ifEmpty { _state.value.nearbyNetworks }
    }

    private suspend fun resolveOcrCredentials(
        baseUrl: String,
        text: String,
        localCredentials: WifiOcrCredentials,
        localConfidence: Double?,
        preferLocalCredentials: Boolean = false,
    ): OcrCredentialResolution {
        val local = if (localCredentials.ssid.isNotBlank() || localCredentials.password.isNotBlank()) {
            localCredentials
        } else {
            ocrProcessor.extractWifiCredentials(text)
        }
        if (preferLocalCredentials && (local.ssid.isNotBlank() || local.password.isNotBlank())) {
            return OcrCredentialResolution(
                parsed = ParsedWifiData(
                    ssid = local.ssid,
                    password = local.password,
                    security = "",
                    sourceFormat = "qr_local",
                    confidence = null,
                ),
                aiData = null,
                aiState = AiValidationState.Hidden,
                message = "Đã đọc thông tin từ mã QR. Hãy kiểm tra rồi bấm Kết nối.",
            )
        }

        val hasStrongLocalCredentials =
            local.ssid.isNotBlank() &&
                local.password.isNotBlank() &&
                (localConfidence ?: 0.0) >= localOcrHighConfidenceThreshold
        val hasStrongPasswordOnlyCredentials =
            local.ssid.isBlank() &&
                local.password.isNotBlank() &&
                (localConfidence ?: 0.0) >= localOcrHighConfidenceThreshold
        val localHasAnyCredentials = local.ssid.isNotBlank() || local.password.isNotBlank()
        val localHasBothCredentials = local.ssid.isNotBlank() && local.password.isNotBlank()

        if (hasStrongLocalCredentials) {
            return OcrCredentialResolution(
                parsed = ParsedWifiData(
                    ssid = local.ssid,
                    password = local.password,
                    security = "",
                    sourceFormat = "ocr_local_confident",
                    confidence = localConfidence,
                ),
                aiData = null,
                aiState = AiValidationState.Hidden,
                message = "OCR đã đọc rõ thông tin từ ảnh. Hãy kiểm tra nhanh rồi bấm Kết nối.",
            )
        }

        if (hasStrongPasswordOnlyCredentials) {
            return OcrCredentialResolution(
                parsed = ParsedWifiData(
                    ssid = "",
                    password = local.password,
                    security = "",
                    sourceFormat = "ocr_local_review",
                    confidence = localConfidence,
                ),
                aiData = null,
                aiState = AiValidationState.Hidden,
                message = "OCR đã đọc rõ mật khẩu từ ảnh, nhưng cần SSID để kết nối. Hãy nhập tên Wi-Fi rồi bấm Kết nối.",
            )
        }

        val aiResolution = resolveAiValidation(
            baseUrl = baseUrl,
            ssid = null,
            password = null,
            ocrText = text,
        )
        val aiReady = aiResolution.uiState as? AiValidationState.Ready
        val aiSsid = aiReady?.normalizedSsid.orEmpty().trim()
        val aiPassword = aiReady?.normalizedPassword.orEmpty().trim()
        val aiImprovesLocal =
            (local.ssid.isBlank() && aiSsid.isNotBlank()) ||
                (local.password.isBlank() && aiPassword.isNotBlank())

        if (aiImprovesLocal || (!localHasAnyCredentials && (aiSsid.isNotBlank() || aiPassword.isNotBlank()))) {
            return OcrCredentialResolution(
                parsed = ParsedWifiData(
                    ssid = firstNonBlank(local.ssid, aiSsid),
                    password = firstNonBlank(local.password, aiPassword),
                    security = "",
                    sourceFormat = "ai_ocr",
                    confidence = aiReady?.confidence,
                ),
                aiData = aiResolution.persisted,
                aiState = aiResolution.uiState,
                message = if (localHasAnyCredentials) {
                    "AI đã bổ sung thêm thông tin từ ảnh. Hãy kiểm tra SSID/mật khẩu rồi bấm Kết nối."
                } else {
                    "AI đã điền thông tin. Hãy kiểm tra SSID/mật khẩu rồi bấm Kết nối."
                },
            )
        }

        if (localHasBothCredentials) {
            val needsReview = (localConfidence ?: 0.0) < localOcrReviewConfidenceThreshold

            return OcrCredentialResolution(
                parsed = ParsedWifiData(
                    ssid = local.ssid,
                    password = local.password,
                    security = "",
                    sourceFormat = if (needsReview) "ocr_local_review" else "ocr_local_confident",
                    confidence = localConfidence,
                ),
                aiData = aiResolution.persisted,
                aiState = aiResolution.uiState,
                message = if (needsReview) {
                    "OCR đã điền gợi ý từ ảnh nhưng độ chắc chưa cao. Hãy kiểm tra kỹ SSID/mật khẩu trước khi kết nối."
                } else {
                    "OCR đã điền thông tin từ ảnh. Hãy kiểm tra rồi bấm Kết nối."
                },
            )
        }

        val parsedByServer = if (shouldUseRemoteAssistance(baseUrl)) {
            runCatching {
                withTimeoutOrNull(1800L) { repository.parseOcr(baseUrl, text) }
            }.getOrNull()
        } else {
            null
        }
        if (parsedByServer?.ok == true && parsedByServer.data != null) {
            val parsed = parsedByServer.data
            val serverImprovesLocal =
                (local.ssid.isBlank() && parsed.ssid.orEmpty().isNotBlank()) ||
                    (local.password.isBlank() && parsed.password.orEmpty().isNotBlank())

            if (serverImprovesLocal || (!localHasAnyCredentials && parsed.hasAnyCredentials())) {
                return OcrCredentialResolution(
                    parsed = parsed.copy(
                        ssid = firstNonBlank(local.ssid, parsed.ssid),
                        password = firstNonBlank(local.password, parsed.password),
                        sourceFormat = parsed.sourceFormat.orEmpty().ifBlank { "ocr_server" },
                    ),
                    aiData = aiResolution.persisted,
                    aiState = aiResolution.uiState,
                    message = "Đã điền thông tin từ kết quả OCR. Hãy kiểm tra rồi bấm Kết nối.",
                )
            }
        }

        if (local.ssid.isNotBlank() || local.password.isNotBlank()) {
            val needsReview =
                local.ssid.isBlank() ||
                    local.password.isBlank() ||
                    (localConfidence ?: 0.0) < localOcrReviewConfidenceThreshold

            return OcrCredentialResolution(
                parsed = ParsedWifiData(
                    ssid = local.ssid,
                    password = local.password,
                    security = "",
                    sourceFormat = if (needsReview) "ocr_local_review" else "ocr_local_confident",
                    confidence = localConfidence,
                ),
                aiData = aiResolution.persisted,
                aiState = aiResolution.uiState,
                message = if (needsReview) {
                    "OCR đã điền gợi ý từ ảnh nhưng độ chắc chưa cao. Hãy kiểm tra kỹ SSID/mật khẩu trước khi kết nối."
                } else {
                    "OCR đã điền thông tin từ ảnh. Hãy kiểm tra rồi bấm Kết nối."
                },
            )
        }

        return OcrCredentialResolution(
            parsed = ParsedWifiData(
                ssid = "",
                password = "",
                security = "",
                sourceFormat = "",
                confidence = null,
            ),
            aiData = aiResolution.persisted,
            aiState = AiValidationState.Hidden,
            message = "Chưa đọc được tên Wi-Fi và mật khẩu. Bạn có thể nhập lại thủ công.",
        )
    }

    private fun ParsedWifiData.hasAnyCredentials(): Boolean {
        return ssid.orEmpty().isNotBlank() || password.orEmpty().isNotBlank()
    }

    private fun firstNonBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }?.trim()
    }

    fun onImageSelectionCanceled() {
        _state.update {
            it.copy(statusMessage = "Bạn chưa chọn ảnh nào.")
        }
    }

    fun onCameraPreviewUnavailable() {
        _state.update {
            it.copy(statusMessage = "Camera chưa sẵn sàng, vui lòng thử lại sau vài giây.")
        }
    }

    fun checkHealth() {
        viewModelScope.launch {
            setLoading(true, "Đang kiểm tra kết nối server...")
            if (!shouldUseRemoteAssistance(_state.value.baseUrl)) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Tính năng server không khả dụng trong bản này. Ứng dụng sẽ dùng OCR trên thiết bị.",
                    )
                }
                return@launch
            }
            runCatching {
                repository.checkHealth(_state.value.baseUrl)
            }.onSuccess { health ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Server OK: ${health.service} | uptime ${health.uptimeSeconds}s",
                    )
                }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Không thể kết nối server. Ứng dụng sẽ dùng OCR trên thiết bị.",
                    )
                }
            }
        }
    }

    fun parseCurrentText() {
        val text = _state.value.ocrText.trim()
        if (text.isEmpty()) {
            _state.update { it.copy(statusMessage = "Bạn chưa có text OCR để parse") }
            return
        }

        viewModelScope.launch {
            setLoading(true, "Đang phân tích thông tin Wi-Fi...")
            try {
                val currentState = _state.value
                val parsed = if (shouldUseRemoteAssistance(currentState.baseUrl)) {
                    val parseEnvelope = repository.parseOcr(currentState.baseUrl, text)
                    if (!parseEnvelope.ok || parseEnvelope.data == null) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                statusMessage = "Không phân tích được dữ liệu Wi-Fi. Hãy kiểm tra lại kết quả OCR.",
                            )
                        }
                        return@launch
                    }
                    parseEnvelope.data
                } else {
                    val local = ocrProcessor.extractWifiCredentials(text)
                    ParsedWifiData(
                        ssid = local.ssid,
                        password = local.password,
                        security = "",
                        sourceFormat = "ocr_local_review",
                        confidence = null,
                    )
                }
                val parsedSsid = parsed.ssid.orEmpty()
                val hasSsid = parsedSsid.isNotBlank()
                val nearbyNetworks = if (hasSsid) {
                    getNearbyNetworksOffMain()
                } else {
                    emptyList()
                }

                _state.update {
                    it.copy(
                        isLoading = false,
                        ssid = parsedSsid,
                        password = parsed.password.orEmpty(),
                        security = parsed.security.orEmpty(),
                        sourceFormat = parsed.sourceFormat.orEmpty(),
                        confidence = parsed.confidence,
                        statusMessage = "Parse thành công. Đang đánh giá AI...",
                        aiValidation = AiValidationState.Loading,
                        ssidSuggestion = if (hasSsid) SsidSuggestionState.Loading else SsidSuggestionState.Hidden,
                        nearbyNetworks = nearbyNetworks,
                        nearbyWifiStatus = buildNearbyWifiStatus(nearbyNetworks),
                        wifiConnectionState = WifiConnectionState.Idle,
                        isNearbyExpanded = false,
                    )
                }

                val aiResolution = resolveAiValidation(
                    baseUrl = currentState.baseUrl,
                    ssid = parsed.ssid,
                    password = parsed.password,
                    ocrText = text,
                )
                val fuzzyResolution = if (hasSsid) {
                    resolveFuzzySuggestion(
                        baseUrl = currentState.baseUrl,
                        ocrSsid = parsedSsid,
                        nearbyNetworks = nearbyNetworks,
                    )
                } else {
                    FuzzyResolution(
                        state = SsidSuggestionState.Hidden,
                        nearbyNetworks = emptyList(),
                        bestMatch = null,
                        score = null,
                    )
                }
                val fuzzyAutoMatch = fuzzyResolution.bestMatch?.takeIf {
                    (fuzzyResolution.score ?: 0.0) >= autoApplyFuzzySsidThreshold
                }

                _state.update {
                    it.copy(
                        ssid = fuzzyAutoMatch ?: it.ssid,
                        aiValidation = aiResolution.uiState,
                        ssidSuggestion = if (fuzzyAutoMatch != null) {
                            SsidSuggestionState.Hidden
                        } else {
                            fuzzyResolution.state
                        },
                        nearbyNetworks = if (fuzzyResolution.nearbyNetworks.isNotEmpty()) {
                            fuzzyResolution.nearbyNetworks
                        } else {
                            it.nearbyNetworks
                        },
                        statusMessage = if (fuzzyAutoMatch != null) {
                            "Đã khớp tên Wi-Fi từ OCR với '$fuzzyAutoMatch'. Hãy kiểm tra mật khẩu rồi bấm Kết nối."
                        } else {
                            buildParseDoneStatus(aiResolution.uiState)
                        },
                    )
                }
            } catch (throwable: Throwable) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Không phân tích được dữ liệu Wi-Fi. Hãy kiểm tra lại kết quả OCR.",
                        aiValidation = AiValidationState.Failed(
                            "Không đánh giá được AI lúc này.",
                        ),
                    )
                }
            }
        }
    }

    fun consumeRecognizedText(text: String) {
        viewModelScope.launch {
            setOcrLoading("Đang đọc thông tin từ mã QR...")
            handleOcrRecognitionSuccess(
                source = "Quét QR",
                text = text,
                blankMessage = "QR không có thông tin Wi-Fi hợp lệ. Vui lòng quét lại.",
                preferLocalCredentials = true,
            )
        }
    }

    fun consumeSharedWifiLink(uri: Uri?): SharedWifiLinkResult {
        return when (val result = SmartWifiSharePayloadCodec.parse(uri)) {
            is SharedWifiPayloadParseResult.Success -> {
                val credentials = result.credentials
                _state.update {
                    it.copy(
                        ocrText = uri.toString(),
                        ssid = credentials.ssid,
                        password = credentials.password,
                        security = credentials.security,
                        sourceFormat = "share_link",
                        confidence = 0.99,
                        scanSource = "Link chia sẻ",
                        statusMessage = "Đã nhận thông tin Wi-Fi từ link chia sẻ. Đang chuẩn bị kết nối...",
                        isLoading = false,
                        aiValidation = AiValidationState.Hidden,
                        ssidSuggestion = SsidSuggestionState.Hidden,
                        nearbyNetworks = emptyList(),
                        nearbyWifiStatus = "",
                        wifiConnectionState = WifiConnectionState.Idle,
                        isNearbyExpanded = false,
                    )
                }
                SharedWifiLinkResult.CONSUMED
            }

            is SharedWifiPayloadParseResult.Invalid -> {
                val message = "${result.message} Vui lòng kiểm tra lại link hoặc mã QR."
                _state.update {
                    it.copy(
                        ocrText = uri?.toString().orEmpty(),
                        ssid = "",
                        password = "",
                        security = "",
                        sourceFormat = "share_invalid",
                        confidence = null,
                        scanSource = "Link chia sẻ",
                        statusMessage = message,
                        isLoading = false,
                        aiValidation = AiValidationState.Hidden,
                        ssidSuggestion = SsidSuggestionState.Hidden,
                        nearbyNetworks = emptyList(),
                        nearbyWifiStatus = "",
                        wifiConnectionState = WifiConnectionState.Failed(
                            reason = WifiConnectFailureReason.INVALID_INPUT,
                            message = message,
                        ),
                        isNearbyExpanded = false,
                    )
                }
                SharedWifiLinkResult.INVALID
            }

            SharedWifiPayloadParseResult.NotSupported -> SharedWifiLinkResult.NOT_SUPPORTED
        }
    }

    fun refreshHistory() {
        viewModelScope.launch {
            runCatching {
                repository.getSavedWifiHistory()
            }.onSuccess { records ->
                _state.update { it.copy(historyRecords = records) }
            }.onFailure { err ->
                _state.update { s ->
                    s.copy(statusMessage = "Không tải được lịch sử: ${err.message ?: "lỗi không xác định"}")
                }
            }
        }
    }

    fun refreshNearbyWifiNetworks(recalculateFuzzy: Boolean = false) {
        viewModelScope.launch {
            _state.update {
                it.copy(nearbyWifiStatus = "Đang quét Wi-Fi xung quanh...")
            }
            val scannedNetworks = withContext(Dispatchers.IO) {
                getScannedNearbyNetworks(forceRefresh = true)
            }
            val previousNetworks = _state.value.nearbyNetworks
            val canReusePrevious = scannedNetworks.isEmpty() &&
                previousNetworks.isNotEmpty() &&
                isWifiEnabled() &&
                hasNearbyWifiPermission() &&
                isLocationServiceEnabled() &&
                !isRunningOnEmulator()
            val networks = when {
                scannedNetworks.isNotEmpty() -> scannedNetworks
                canReusePrevious -> previousNetworks
                else -> emptyList()
            }
            val nearbyStatus = when {
                scannedNetworks.isNotEmpty() -> buildNearbyWifiStatus(scannedNetworks)
                canReusePrevious -> "Không cập nhật được danh sách mới, đang hiển thị dữ liệu scan gần nhất."
                else -> buildNearbyWifiStatus(emptyList())
            }
            _state.update {
                it.copy(
                    nearbyNetworks = networks,
                    nearbyWifiStatus = nearbyStatus,
                )
            }

            val current = _state.value
            if (recalculateFuzzy && current.ssid.isNotBlank() && networks.isNotEmpty()) {
                val fuzzy = resolveFuzzySuggestion(
                    baseUrl = current.baseUrl,
                    ocrSsid = current.ssid,
                    nearbyNetworks = networks,
                )
                _state.update {
                    it.copy(
                        ssidSuggestion = fuzzy.state,
                        nearbyNetworks = fuzzy.nearbyNetworks.ifEmpty { networks },
                    )
                }
            }
        }
    }

    private fun setLoading(isLoading: Boolean, statusMessage: String) {
        _state.update { it.copy(isLoading = isLoading, statusMessage = statusMessage) }
    }

    private fun setOcrLoading(statusMessage: String) {
        _state.update {
            it.copy(
                isLoading = true,
                statusMessage = statusMessage,
                ocrText = "",
                ssid = "",
                password = "",
                security = "",
                sourceFormat = "",
                confidence = null,
                aiValidation = AiValidationState.Hidden,
                ssidSuggestion = SsidSuggestionState.Hidden,
                nearbyNetworks = emptyList(),
                nearbyWifiStatus = "",
                wifiConnectionState = WifiConnectionState.Idle,
                isNearbyExpanded = false,
            )
        }
    }

    private fun Bitmap.downscaleForOcrInput(): Bitmap {
        val sourceWidth = runCatching { width }.getOrDefault(0)
        val sourceHeight = runCatching { height }.getOrDefault(0)
        val longestSide = maxOf(sourceWidth, sourceHeight)
        if (longestSide <= maxOcrDecodeSide) return this

        val scale = maxOcrDecodeSide.toFloat() / longestSide
        return Bitmap.createScaledBitmap(
            this,
            (sourceWidth * scale).roundToInt().coerceAtLeast(1),
            (sourceHeight * scale).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    private fun decodeBitmapFromUri(uri: Uri): Bitmap {
        val resolver = getApplication<Application>().contentResolver
        val source = ImageDecoder.createSource(resolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longestSide = maxOf(info.size.width, info.size.height)
            if (longestSide > maxOcrDecodeSide) {
                val scale = maxOcrDecodeSide.toFloat() / longestSide
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1),
                )
            }
        }
    }

    private fun loadLatestSavedWifi() {
        viewModelScope.launch {
                val savedWifi = runCatching { repository.getLatestSavedWifi() }
                    .getOrNull() ?: return@launch
            val savedFuzzyMatch = savedWifi.fuzzyBestMatch
            val nearbyNetworks = getNearbyNetworksOffMain()
            val nearbyStatus = buildNearbyWifiStatus(nearbyNetworks)
            _state.update {
                it.copy(
                    baseUrl = savedWifi.baseUrl,
                    ocrText = savedWifi.ocrText,
                    ssid = savedWifi.ssid,
                    password = savedWifi.password,
                    security = "",
                    sourceFormat = savedWifi.sourceFormat,
                    confidence = savedWifi.confidence,
                    aiValidation = restoreAiValidation(savedWifi),
                    ssidSuggestion = if (
                        !savedFuzzyMatch.isNullOrBlank() &&
                        savedWifi.fuzzyScore != null &&
                        !savedFuzzyMatch.equals(savedWifi.ssid, ignoreCase = true)
                    ) {
                        SsidSuggestionState.Found(
                            bestMatch = savedFuzzyMatch,
                            score = savedWifi.fuzzyScore,
                        )
                    } else {
                        SsidSuggestionState.Hidden
                    },
                    nearbyNetworks = nearbyNetworks,
                    nearbyWifiStatus = nearbyStatus,
                    wifiConnectionState = WifiConnectionState.Idle,
                    statusMessage = "Đã tải dữ liệu Wi-Fi gần nhất từ SQLite",
                )
            }
        }
    }

    private fun restoreAiValidation(savedWifi: SavedWifiRecord): AiValidationState {
        val hasAiInfo = savedWifi.aiConfidence != null ||
            savedWifi.aiSuggestion.isNotBlank() ||
            savedWifi.aiRecommendation.isNotBlank() ||
            savedWifi.aiFlags.isNotEmpty()

        if (!hasAiInfo) return AiValidationState.Hidden

        return AiValidationState.Ready(
            validated = savedWifi.ssid.isNotBlank() || savedWifi.password.isNotBlank(),
            confidence = (savedWifi.aiConfidence ?: 0.0).coerceIn(0.0, 1.0),
            suggestion = savedWifi.aiSuggestion.ifBlank {
                "Đã tải kết quả AI gần nhất từ SQLite."
            },
            flags = savedWifi.aiFlags,
            recommendation = savedWifi.aiRecommendation.ifBlank { "review" },
            shouldAutoConnect = savedWifi.aiShouldAutoConnect,
            normalizedSsid = savedWifi.ssid.takeIf { it.isNotBlank() },
            normalizedPassword = savedWifi.password.takeIf { it.isNotBlank() },
        )
    }

    private suspend fun resolveAiValidation(
        baseUrl: String,
        ssid: String?,
        password: String?,
        ocrText: String,
    ): AiResolution {
        if (!shouldUseRemoteAssistance(baseUrl)) {
            return AiResolution(
                uiState = AiValidationState.Hidden,
                persisted = null,
            )
        }

        val envelope = runCatching {
            withTimeoutOrNull(1800L) {
                repository.validateAi(
                    baseUrl = baseUrl,
                    ssid = ssid,
                    password = password,
                    ocrText = ocrText,
                )
            } ?: return AiResolution(
                uiState = AiValidationState.Hidden,
                persisted = null,
            )
        }.getOrElse {
            return AiResolution(
                uiState = AiValidationState.Failed(
                    "Không thể dùng đánh giá AI lúc này. Ứng dụng sẽ dùng kết quả OCR trên máy.",
                ),
                persisted = null,
            )
        }

        if (!envelope.ok || envelope.data == null) {
            return AiResolution(
                uiState = AiValidationState.Failed(
                    "Đánh giá AI chưa sẵn sàng. Hãy kiểm tra kết quả OCR trước khi kết nối.",
                ),
                persisted = null,
            )
        }

        val data = envelope.data
        return AiResolution(
            uiState = AiValidationState.Ready(
                validated = data.validated,
                confidence = data.confidence.coerceIn(0.0, 1.0),
                suggestion = data.suggestion,
                flags = data.flags,
                recommendation = data.parseRecommendation,
                shouldAutoConnect = data.shouldAutoConnect,
                normalizedSsid = data.normalizedSsid,
                normalizedPassword = data.normalizedPassword,
            ),
            persisted = data,
        )
    }

    // ── Fuzzy SSID Match ────────────────────────────────────

    fun triggerFuzzyMatch() {
        val current = _state.value
        val ocrSsid = current.ssid.trim()
        if (ocrSsid.isEmpty()) return

        viewModelScope.launch {
            val nearbyNetworks = getNearbyNetworksOffMain()
            _state.update {
                it.copy(
                    ssidSuggestion = SsidSuggestionState.Loading,
                    nearbyNetworks = nearbyNetworks,
                )
            }

            val fuzzy = resolveFuzzySuggestion(
                baseUrl = current.baseUrl,
                ocrSsid = ocrSsid,
                nearbyNetworks = _state.value.nearbyNetworks.ifEmpty { nearbyNetworks },
            )

            _state.update {
                it.copy(
                    ssidSuggestion = fuzzy.state,
                    nearbyNetworks = fuzzy.nearbyNetworks,
                )
            }
        }
    }

    private suspend fun resolveFuzzySuggestion(
        baseUrl: String,
        ocrSsid: String,
        nearbyNetworks: List<NearbyNetwork>,
    ): FuzzyResolution {
        val payload = nearbyNetworks.map { network ->
            FuzzyNetworkPayload(
                ssid = network.ssid,
                signalLevel = network.signalLevel,
            )
        }

        val apiResult = if (shouldUseRemoteAssistance(baseUrl)) {
            runCatching {
                withTimeoutOrNull(1800L) {
                    repository.fuzzyMatchSsid(
                        baseUrl = baseUrl,
                        ocrSsid = ocrSsid,
                        nearbyNetworks = payload,
                    )
                }
            }.getOrNull()
        } else {
            null
        }

        if (apiResult?.ok == true && apiResult.data != null) {
            val data = apiResult.data
            val apiNearby = data.matches.mapIndexed { index, item ->
                NearbyNetwork(
                    ssid = item.ssid,
                    signalLevel = item.signalLevel ?: (4 - index).coerceAtLeast(1),
                )
            }

            val bestMatch = data.bestMatch?.trim().orEmpty()
            val bestScore = data.score ?: data.matches.firstOrNull {
                it.ssid.equals(bestMatch, ignoreCase = true)
            }?.score

            if (
                bestMatch.isNotBlank() &&
                bestScore != null &&
                bestScore >= 0.55 &&
                !bestMatch.equals(ocrSsid, ignoreCase = true)
            ) {
                return FuzzyResolution(
                    state = SsidSuggestionState.Found(
                        bestMatch = bestMatch,
                        score = bestScore,
                    ),
                    nearbyNetworks = if (apiNearby.isNotEmpty()) apiNearby else nearbyNetworks,
                    bestMatch = bestMatch,
                    score = bestScore,
                )
            }

            return FuzzyResolution(
                state = SsidSuggestionState.NotFound,
                nearbyNetworks = if (apiNearby.isNotEmpty()) apiNearby else nearbyNetworks,
                bestMatch = null,
                score = null,
            )
        }

        val fallback = findBestMatch(ocrSsid, nearbyNetworks)
        return FuzzyResolution(
            state = fallback ?: SsidSuggestionState.NotFound,
            nearbyNetworks = nearbyNetworks,
            bestMatch = fallback?.bestMatch,
            score = fallback?.score,
        )
    }

    fun acceptSsidSuggestion() {
        val suggestion = _state.value.ssidSuggestion
        if (suggestion is SsidSuggestionState.Found) {
            _state.update {
                it.copy(
                    ssid = suggestion.bestMatch,
                    ssidSuggestion = SsidSuggestionState.Hidden,
                    wifiConnectionState = WifiConnectionState.Idle,
                    statusMessage = "Đã cập nhật SSID thành '${suggestion.bestMatch}'",
                )
            }
        }
    }

    fun dismissSsidSuggestion() {
        _state.update { it.copy(ssidSuggestion = SsidSuggestionState.Hidden) }
    }

    fun toggleNearbyExpanded() {
        _state.update { it.copy(isNearbyExpanded = !it.isNearbyExpanded) }
    }

    fun selectNearbyNetwork(ssid: String) {
        _state.update {
            it.copy(
                ssid = ssid,
                ssidSuggestion = SsidSuggestionState.Hidden,
                wifiConnectionState = WifiConnectionState.Idle,
                statusMessage = "Đã chọn mạng '$ssid'",
            )
        }
    }

    /**
     * Fallback local khi BE fuzzy endpoint chưa sẵn sàng.
     */
    private fun findBestMatch(
        ocrSsid: String,
        networks: List<NearbyNetwork>,
    ): SsidSuggestionState.Found? {
        if (ocrSsid.isBlank() || networks.isEmpty()) return null

        val ocrLower = ocrSsid.lowercase(Locale.ROOT)
        val ocrComparable = ocrSsid.normalizeForFuzzySsid()
        var bestNetwork: NearbyNetwork? = null
        var bestScore = 0.0

        for (network in networks) {
            val rawScore = similarityScore(ocrLower, network.ssid.lowercase(Locale.ROOT))
            val normalizedScore = similarityScore(ocrComparable, network.ssid.normalizeForFuzzySsid())
            val score = maxOf(rawScore, normalizedScore)
            if (score > bestScore) {
                bestScore = score
                bestNetwork = network
            }
        }

        if (bestNetwork == null || bestScore < 0.6) return null
        if (bestNetwork.ssid.equals(ocrSsid, ignoreCase = true)) return null

        return SsidSuggestionState.Found(
            bestMatch = bestNetwork.ssid,
            score = bestScore,
        )
    }

    private fun String.normalizeForFuzzySsid(): String {
        val withoutLeadingOcrMarker = replace(
            "^\\s*[a-z0-9]\\s*[:：]\\s+".toRegex(RegexOption.IGNORE_CASE),
            "",
        )
        return java.text.Normalizer.normalize(withoutLeadingOcrMarker, java.text.Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
            .lowercase(Locale.ROOT)
            .replace("[^a-z0-9._\\-\\s]".toRegex(), "")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    private fun similarityScore(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0

        val maxLen = maxOf(a.length, b.length)
        val distance = levenshtein(a, b)
        return 1.0 - (distance.toDouble() / maxLen)
    }

    private fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost,
                )
            }
        }
        return dp[m][n]
    }

    private fun buildParseDoneStatus(
        aiState: AiValidationState,
    ): String {
        return when (aiState) {
            is AiValidationState.Ready -> "Phân tích thành công. AI đã đánh giá xong. Lịch sử chỉ lưu sau khi kết nối thành công."
            is AiValidationState.Failed -> "Phân tích thành công. AI đánh giá lỗi: ${aiState.message}. Lịch sử chỉ lưu sau khi kết nối thành công."
            AiValidationState.Hidden, AiValidationState.Loading -> "Phân tích thành công. Hãy kiểm tra thông tin rồi bấm Kết nối."
        }
    }

    private fun isCurrentNetworkInternetValidated(targetSsid: String): Boolean {
        if (targetSsid.isBlank()) return false
        val stateSsid = when (val connectionState = _state.value.wifiConnectionState) {
            is WifiConnectionState.Connected -> connectionState.ssid
            is WifiConnectionState.ConnectedWithoutInternet -> connectionState.ssid
            else -> null
        }
        if (!stateSsid.isNullOrBlank() && stateSsid.equals(targetSsid, ignoreCase = true)) {
            return true
        }
        if (getCurrentConnectedSsid()?.equals(targetSsid, ignoreCase = true) != true) return false

        // For UI presence, a Wi-Fi tether/hotspot should still count as the
        // current connection even if Android has not yet marked it VALIDATED.
        val app = getApplication<Application>().applicationContext
        val connectivityManager = app.getSystemService(ConnectivityManager::class.java) ?: return true
        val activeNetwork = connectivityManager.activeNetwork ?: return true
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return true
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            (
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                )
    }

    @Suppress("DEPRECATION")
    private fun getCurrentConnectedSsid(): String? {
        val app = getApplication<Application>().applicationContext
        val wifiManager = app.getSystemService(WifiManager::class.java) ?: return null
        val ssid = runCatching { wifiManager.connectionInfo?.ssid.orEmpty() }.getOrDefault("")
        return ssid.normalizeWifiSsid().takeIf { it.isNotBlank() && !it.equals("<unknown ssid>", ignoreCase = true) }
    }

    @Suppress("DEPRECATION")
    private fun getCurrentNetworkTelemetry(targetSsid: String): NetworkLiveTelemetry? {
        if (targetSsid.isBlank() || !hasNearbyWifiPermission()) return null
        val app = getApplication<Application>().applicationContext
        val wifiManager = app.getSystemService(WifiManager::class.java) ?: return null
        val info = runCatching { wifiManager.connectionInfo }.getOrNull() ?: return null
        if (!info.ssid.normalizeWifiSsid().equals(targetSsid, ignoreCase = true)) return null

        val frequencyMhz = info.frequency.takeIf { it > 0 }
        val signalDbm = info.rssi.takeIf { it in -99..-30 }
        val rxSpeed = info.rxLinkSpeedMbps.takeIf { it > 0 }
        val txSpeed = info.txLinkSpeedMbps.takeIf { it > 0 }

        return NetworkLiveTelemetry(
            linkSpeedMbps = info.linkSpeed.takeIf { it > 0 },
            rxLinkSpeedMbps = rxSpeed,
            txLinkSpeedMbps = txSpeed,
            signalDbm = signalDbm,
            frequencyMhz = frequencyMhz,
            updatedAtMillis = currentTimeMillis(),
        )
    }

    private suspend fun connectWifi(
        ssid: String,
        password: String?,
        security: String?,
    ): WifiConnectResult {
        val override = deps?.connectWifi
        return if (override != null) {
            override.invoke(ssid, password, security)
        } else {
            wifiConnector.connect(ssid, password, security)
        }
    }

    private fun currentTimeMillis(): Long {
        return deps?.nowMillis?.invoke() ?: System.currentTimeMillis()
    }

    private fun shouldUseRemoteAssistance(baseUrl: String): Boolean {
        if (deps?.repository != null) return true
        val normalized = baseUrl.trim().lowercase(Locale.ROOT)
        if (normalized.isBlank()) return false
        if (normalized.contains("10.0.2.2") || normalized.contains("localhost") || normalized.contains("127.0.0.1")) {
            return false
        }
        if (normalized.contains("smartwifi.example.com")) return false
        return normalized.startsWith("https://")
    }

    @Suppress("DEPRECATION")
    @android.annotation.SuppressLint("MissingPermission") // hasNearbyWifiPermission() checked above; SecurityException caught by runCatching
    private fun getScannedNearbyNetworks(forceRefresh: Boolean = false): List<NearbyNetwork> {
        deps?.scannedNearbyNetworks?.let { return it.invoke() }
        val app = getApplication<Application>().applicationContext
        if (!hasNearbyWifiPermission() || !isWifiEnabled() || !isLocationServiceEnabled() || isRunningOnEmulator()) {
            Log.d(TAG, "  WiFi scan skipped: permission=${hasNearbyWifiPermission()}, wifiEnabled=${isWifiEnabled()}, locationEnabled=${isLocationServiceEnabled()}, emulator=${isRunningOnEmulator()}")
            cachedNearbyNetworks = emptyList()
            return emptyList()
        }

        val wifiManager = app.getSystemService(WifiManager::class.java) ?: return emptyList()
        val now = currentTimeMillis()
        if (!forceRefresh && cachedNearbyNetworks.isNotEmpty() && now - lastWifiScanMillis < wifiScanCacheMillis) {
            Log.d(TAG, "  WiFi scan: using cache (${cachedNearbyNetworks.size} networks, age=${now - lastWifiScanMillis}ms)")
            return cachedNearbyNetworks
        }
        return runCatching {
            if (forceRefresh || now - lastWifiScanMillis >= wifiScanCacheMillis) {
                wifiManager.startScan()
                lastWifiScanMillis = now
            }
            val networks = wifiManager.scanResults
                .asSequence()
                .mapNotNull { result ->
                    val ssid = result.SSID?.trim().orEmpty()
                    if (ssid.isBlank()) {
                        null
                    } else {
                        NearbyNetwork(
                            ssid = ssid,
                            bssid = result.BSSID?.trim()?.takeIf { it.isNotBlank() },
                            signalLevel = result.level.toWifiSignalLevel(),
                            frequencyMhz = result.frequency.takeIf { frequency -> frequency > 0 },
                            securityLabel = result.capabilities.orEmpty().toWifiSecurityLabel(),
                            signalDbm = result.level.takeIf { level -> level in -99..-20 },
                        )
                    }
                }
                .distinctBy { it.ssid.lowercase(Locale.ROOT) }
                .sortedWith(
                    compareByDescending<NearbyNetwork> { it.signalDbm ?: -100 }
                        .thenByDescending { it.signalLevel },
                )
                .take(12)
                .toList()
            cachedNearbyNetworks = networks

            // ── DEBUG: Log scanned WiFi networks ──
            Log.d(TAG, "════════════════════════════════════════════════════════════")
            Log.d(TAG, "▶ WIFI SCAN RESULTS (${networks.size} networks found)")
            networks.forEachIndexed { index, net ->
                Log.d(TAG, "  [$index] SSID='${net.ssid}', RSSI=${net.signalDbm}dBm, signal=${net.signalLevel}/4, freq=${net.frequencyMhz}MHz, security=${net.securityLabel}, BSSID=${net.bssid}")
            }
            Log.d(TAG, "════════════════════════════════════════════════════════════")

            networks
        }.getOrDefault(emptyList())
    }

    private fun hasNearbyWifiPermission(): Boolean {
        deps?.hasNearbyWifiPermission?.let { return it.invoke() }
        val app = getApplication<Application>().applicationContext
        val hasLocation = ContextCompat.checkSelfPermission(
            app,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                app,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

        val hasNearbyWifi = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                app,
                Manifest.permission.NEARBY_WIFI_DEVICES,
            ) == PackageManager.PERMISSION_GRANTED

        return hasLocation && hasNearbyWifi
    }

    private fun isWifiEnabled(): Boolean {
        deps?.isWifiEnabled?.let { return it.invoke() }
        val app = getApplication<Application>().applicationContext
        val wifiManager = app.getSystemService(WifiManager::class.java) ?: return true
        return runCatching { wifiManager.isWifiEnabled }.getOrDefault(true)
    }

    private fun isLocationServiceEnabled(): Boolean {
        deps?.isLocationServiceEnabled?.let { return it.invoke() }
        val app = getApplication<Application>().applicationContext
        val locationManager = app.getSystemService(LocationManager::class.java) ?: return true
        return runCatching {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(true)
    }

    private fun Int.toWifiSignalLevel(): Int {
        return when {
            this >= -55 -> 4
            this >= -67 -> 3
            this >= -80 -> 2
            else -> 1
        }
    }

    private fun String.toWifiSecurityLabel(): String {
        val caps = uppercase(Locale.ROOT)
        return when {
            caps.contains("SAE") || caps.contains("WPA3") -> "WPA3-SAE"
            caps.contains("WPA2") && caps.contains("WPA") -> "WPA/WPA2"
            caps.contains("WPA2") || caps.contains("PSK") -> "WPA2-PSK"
            caps.contains("WPA") -> "WPA-PSK"
            caps.contains("WEP") -> "WEP"
            caps.contains("OWE") -> "OWE"
            caps.isBlank() || caps == "[ESS]" -> "OPEN"
            else -> "Không có dữ liệu"
        }
    }

    private fun buildNearbyWifiStatus(scannedNetworks: List<NearbyNetwork>): String {
        if (!isWifiEnabled()) {
            return "Wi-Fi đang tắt. Hãy bật Wi-Fi để quét và kết nối mạng thật."
        }
        if (!hasNearbyWifiPermission()) {
            return "Cần cấp quyền Vị trí/Nearby Wi-Fi để quét danh sách mạng thật."
        }
        if (!isLocationServiceEnabled()) {
            return "Dịch vụ vị trí đang tắt. Android cần Location để trả về danh sách Wi-Fi xung quanh."
        }
        if (isRunningOnEmulator()) {
            return "Thiết bị hiện tại không hỗ trợ quét Wi-Fi thực tế. Vui lòng thử trên điện thoại thật."
        }
        return if (scannedNetworks.isNotEmpty()) {
            "Đã tìm thấy ${scannedNetworks.size} mạng Wi-Fi xung quanh."
        } else {
            "Chưa lấy được mạng Wi-Fi thực tế. Hãy cấp quyền Vị trí/Nearby Wi-Fi và bật Location trên máy."
        }
    }

    private fun isRunningOnEmulator(): Boolean {
        deps?.isRunningOnEmulator?.let { return it.invoke() }
        val fingerprint = Build.FINGERPRINT.orEmpty()
        val model = Build.MODEL.orEmpty()
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val brand = Build.BRAND.orEmpty()
        val device = Build.DEVICE.orEmpty()
        val product = Build.PRODUCT.orEmpty()
        return fingerprint.startsWith("generic", ignoreCase = true) ||
            fingerprint.contains("emulator", ignoreCase = true) ||
            model.contains("Emulator", ignoreCase = true) ||
            model.contains("Android SDK built for", ignoreCase = true) ||
            manufacturer.contains("Genymotion", ignoreCase = true) ||
            (brand.startsWith("generic", ignoreCase = true) &&
                device.startsWith("generic", ignoreCase = true)) ||
            product.contains("sdk", ignoreCase = true)
    }

    // ── End Fuzzy SSID Match ────────────────────────────────

    override fun onCleared() {
        ocrJob?.cancel()
        // IMPORTANT: We do NOT call wifiConnector.cancelPendingRequest() here.
        // The connector lives at Application scope (see SmartWifiApp) so the
        // kept-alive WiFi connection persists across ViewModel recreation.
        // Tests that supply deps?.cancelWifiRequests can still clean up.
        deps?.cancelWifiRequests?.invoke()
        ocrProcessor.release()
        super.onCleared()
    }
}

data class MainViewModelDeps(
    val repository: WifiRepository? = null,
    val ocrProcessor: WifiOcrEngine? = null,
    val ocrDispatcher: CoroutineDispatcher? = null,
    val connectWifi: (suspend (ssid: String, password: String?, security: String?) -> WifiConnectResult)? = null,
    val cancelWifiRequests: (() -> Unit)? = null,
    val hasNearbyWifiPermission: (() -> Boolean)? = null,
    val scannedNearbyNetworks: (() -> List<NearbyNetwork>)? = null,
    val isRunningOnEmulator: (() -> Boolean)? = null,
    val isWifiEnabled: (() -> Boolean)? = null,
    val isLocationServiceEnabled: (() -> Boolean)? = null,
    val nowMillis: (() -> Long)? = null,
)

private data class AiResolution(
    val uiState: AiValidationState,
    val persisted: AiValidateData?,
)

private data class FuzzyResolution(
    val state: SsidSuggestionState,
    val nearbyNetworks: List<NearbyNetwork>,
    val bestMatch: String?,
    val score: Double?,
)

private data class OcrCredentialResolution(
    val parsed: ParsedWifiData,
    val aiData: AiValidateData?,
    val aiState: AiValidationState,
    val message: String,
)

private const val localOcrHighConfidenceThreshold = 0.84
private const val localOcrReviewConfidenceThreshold = 0.72
private const val autoApplyFuzzySsidThreshold = 0.86
private const val maxOcrDecodeSide = 1600
private const val wifiScanCacheMillis = 15_000L

private data class WifiConnectionPlan(
    val candidateSsids: List<String>,
    val directConnectionMissingFromScan: Boolean,
    val resolvedFromFuzzyMatch: Boolean = false,
    val requiresUserSelection: Boolean = false,
    val suggestedSsid: String? = null,
    val suggestedScore: Double? = null,
)

private data class WifiConnectRequestKey(
    val ssid: String,
    val password: String,
    val security: String,
) {
    companion object {
        fun from(
            ssid: String,
            password: String?,
            security: String?,
        ): WifiConnectRequestKey {
            return WifiConnectRequestKey(
                ssid = ssid.trim().lowercase(Locale.ROOT),
                password = password.orEmpty(),
                security = security.orEmpty().trim().lowercase(Locale.ROOT),
            )
        }
    }
}

enum class NetworkDetailOrigin {
    HOME,
    HISTORY,
    SHARE,
}

data class NetworkDetailUiModel(
    val savedRecordId: Long? = null,
    val ssid: String,
    val lastConnectedLabel: String,
    val protocolLabel: String,
    val frequencyLabel: String,
    val signalDbm: Int?,
    val signalQualityLabel: String,
    val usageTotalLabel: String,
    val usageHighlightLabel: String,
    val usageBars: List<Float>,
    val sourceTitle: String,
    val sourceSubtitle: String,
    val sourceBadgeLabel: String,
    val isConnected: Boolean,
    val canDelete: Boolean,
    val origin: NetworkDetailOrigin,
    val password: String = "",
    val security: String = "",
)

data class NetworkLiveTelemetry(
    val linkSpeedMbps: Int?,
    val rxLinkSpeedMbps: Int?,
    val txLinkSpeedMbps: Int?,
    val signalDbm: Int?,
    val frequencyMhz: Int?,
    val updatedAtMillis: Long,
)

data class MainUiState(
    val baseUrl: String = BuildConfig.API_BASE_URL,
    val isDarkModeEnabled: Boolean = false,
    val autoConnectEnabled: Boolean = true,
    val ocrText: String = "",
    val ssid: String = "",
    val password: String = "",
    val security: String = "",
    val sourceFormat: String = "",
    val confidence: Double? = null,
    val scanSource: String = "",
    val statusMessage: String = "Sẵn sàng quét OCR Wi-Fi",
    val isLoading: Boolean = false,
    val aiValidation: AiValidationState = AiValidationState.Hidden,
    val ssidSuggestion: SsidSuggestionState = SsidSuggestionState.Hidden,
    val nearbyNetworks: List<NearbyNetwork> = emptyList(),
    val nearbyWifiStatus: String = "",
    val wifiConnectionState: WifiConnectionState = WifiConnectionState.Idle,
    val isNearbyExpanded: Boolean = false,
    val historyRecords: List<SavedWifiRecord> = emptyList(),
    val selectedNetworkDetail: NetworkDetailUiModel? = null,
    val selectedNetworkTelemetry: NetworkLiveTelemetry? = null,
    val transientUserMessage: String? = null,
    /**
     * Realtime SSID currently associated by the device, regardless of whether
     * the user kicked off the connection from this app or from system settings.
     * Null = not connected to any WiFi or SSID hidden by OEM (Vivo location-off).
     */
    val liveConnectedSsid: String? = null,
)

sealed class AiValidationState {
    object Hidden : AiValidationState()
    object Loading : AiValidationState()
    data class Ready(
        val validated: Boolean,
        val confidence: Double,
        val suggestion: String,
        val flags: List<String>,
        val recommendation: String,
        val shouldAutoConnect: Boolean,
        val normalizedSsid: String?,
        val normalizedPassword: String?,
    ) : AiValidationState()

    data class Failed(val message: String) : AiValidationState()
}

sealed class WifiConnectionState {
    object Idle : WifiConnectionState()
    data class Connecting(
        val ssid: String,
        val phase: WifiConnectionPhase = WifiConnectionPhase.CONNECTING_WIFI,
    ) : WifiConnectionState()
    data class Connected(val ssid: String) : WifiConnectionState()
    data class ConnectedWithoutInternet(
        val ssid: String,
        val message: String,
        val isCaptivePortal: Boolean = false,
    ) : WifiConnectionState()
    data class Failed(
        val reason: WifiConnectFailureReason,
        val message: String,
    ) : WifiConnectionState()
}

enum class WifiConnectionPhase {
    CONNECTING_WIFI,
    VERIFYING_INTERNET,
}

sealed class SsidSuggestionState {
    object Hidden : SsidSuggestionState()
    object Loading : SsidSuggestionState()
    data class Found(val bestMatch: String, val score: Double) : SsidSuggestionState()
    object NotFound : SsidSuggestionState()
}

data class NearbyNetwork(
    val ssid: String,
    val signalLevel: Int,
    val bssid: String? = null,
    val frequencyMhz: Int? = null,
    val securityLabel: String? = null,
    val signalDbm: Int? = null,
)

private fun SavedWifiRecord.toNetworkDetailUiModel(
    origin: NetworkDetailOrigin,
    isConnected: Boolean,
    scannedNetwork: NearbyNetwork?,
): NetworkDetailUiModel {
    val dbm = scannedNetwork?.signalDbm
    val protocol = scannedNetwork?.securityLabel
        ?: if (password.isBlank()) "OPEN" else "Không có dữ liệu"
    return NetworkDetailUiModel(
        savedRecordId = id,
        ssid = ssid.ifBlank { "Wi-Fi đã lưu" },
        lastConnectedLabel = "Kết nối lần cuối: ${createdAtMillis.toNetworkDetailDate()}",
        protocolLabel = protocol,
        frequencyLabel = scannedNetwork?.frequencyMhz.toFrequencyLabelOrUnavailable(),
        signalDbm = dbm,
        signalQualityLabel = inferSignalQualityLabel(dbm),
        usageTotalLabel = "Không có dữ liệu",
        usageHighlightLabel = "",
        usageBars = emptyList(),
        sourceTitle = "Nguồn kết nối",
        sourceSubtitle = sourceFormat.toSourceSubtitle(),
        sourceBadgeLabel = if (isConnected) "Đang kết nối" else "Đã lưu",
        isConnected = isConnected,
        canDelete = true,
        origin = origin,
        password = password,
        security = protocol,
    )
}

private fun RecentNetworkUiModel.toNetworkDetailUiModel(
    origin: NetworkDetailOrigin,
    scannedNetwork: NearbyNetwork?,
): NetworkDetailUiModel {
    val dbm = scannedNetwork?.signalDbm
    return NetworkDetailUiModel(
        ssid = name,
        lastConnectedLabel = lastConnectedLabel,
        protocolLabel = scannedNetwork?.securityLabel ?: "Không có dữ liệu",
        frequencyLabel = scannedNetwork?.frequencyMhz.toFrequencyLabelOrUnavailable(),
        signalDbm = dbm,
        signalQualityLabel = inferSignalQualityLabel(dbm),
        usageTotalLabel = "Không có dữ liệu",
        usageHighlightLabel = "",
        usageBars = emptyList(),
        sourceTitle = "Nguồn kết nối",
        sourceSubtitle = when (type) {
            RecentNetworkType.WIFI -> "Thiết bị của bạn đã từng dùng mạng này gần đây."
            RecentNetworkType.ROUTER -> "Router ưu tiên thường xuất hiện trong khu vực hiện tại."
            RecentNetworkType.BUILDING -> "Điểm Wi-Fi công cộng từng được phát hiện trước đó."
        },
        sourceBadgeLabel = when (type) {
            RecentNetworkType.BUILDING -> "Khách"
            RecentNetworkType.ROUTER -> "Ưu tiên"
            RecentNetworkType.WIFI -> "Đã lưu"
        },
        isConnected = isConnected,
        canDelete = false,
        origin = origin,
    )
}

private fun inferSignalQualityLabel(dbm: Int?): String {
    return when {
        dbm == null -> "Không có dữ liệu"
        dbm >= -50 -> "Tuyệt vời"
        dbm >= -60 -> "Rất tốt"
        dbm >= -70 -> "Tốt"
        dbm >= -80 -> "Khá yếu"
        else -> "Yếu"
    }
}

private fun Int?.toFrequencyLabelOrUnavailable(): String {
    val mhz = this ?: return "Không có dữ liệu"
    return when (mhz) {
        in 2400..2500 -> "2.4 GHz"
        in 4900..5900 -> "5 GHz"
        in 5925..7125 -> "6 GHz"
        else -> String.format(Locale.US, "%.1f GHz", mhz / 1000f)
    }
}

private fun String.toSourceSubtitle(): String {
    val normalized = lowercase(Locale.ROOT)
    return when {
        "qr" in normalized -> "Thông tin mạng được lấy từ QR hoặc OCR gần nhất."
        "camera" in normalized || "image" in normalized -> "Mạng này được nhận diện từ ảnh hoặc camera."
        "manual" in normalized -> "Thông tin do bạn nhập tay trên thiết bị này."
        "connect" in normalized -> "Mạng đã từng được kết nối trực tiếp từ thiết bị này."
        else -> "Thiết bị của bạn đã từng lưu và dùng mạng này."
    }
}

private fun Long.toNetworkDetailDate(): String {
    return SimpleDateFormat(
        "dd 'thg' MM, yyyy",
        Locale.forLanguageTag("vi-VN"),
    ).format(Date(this))
}

private fun String.normalizeWifiSsid(): String = trim().removePrefix("\"").removeSuffix("\"")
