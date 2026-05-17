package com.smartwificonnect

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.location.LocationManager
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
import com.smartwificonnect.ocr.WifiOcrTextParser
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
    private val wifiConnector = WifiConnector(application.applicationContext)
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()
    private var ocrJob: Job? = null
    private var cachedNearbyNetworks: List<NearbyNetwork> = emptyList()
    private var lastWifiScanMillis: Long = 0L

    init {
        loadLatestSavedWifi()
        refreshHistory()
    }

    fun onDarkModeChanged(enabled: Boolean) {
        _state.update { it.copy(isDarkModeEnabled = enabled) }
    }

    fun onAutoConnectPreferenceChanged(enabled: Boolean) {
        _state.update { it.copy(autoConnectEnabled = enabled) }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            runCatching {
                repository.clearSavedWifiHistory()
            }.onSuccess { deletedCount ->
                _state.update {
                    it.copy(
                        historyRecords = emptyList(),
                        selectedNetworkDetail = null,
                        selectedNetworkTelemetry = null,
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
            isConnected = isCurrentNetworkConnected(network.name) || network.isConnected,
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
                    isConnected = isCurrentNetworkConnected(record.ssid),
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
                    isConnected = telemetry != null || isCurrentNetworkConnected(latestDetail.ssid),
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
            val deleted = runCatching {
                repository.deleteSavedWifiRecord(recordId)
            }.getOrDefault(false)
            if (deleted) {
                _state.update {
                    it.copy(
                        historyRecords = it.historyRecords.filterNot { record -> record.id == recordId },
                        selectedNetworkDetail = null,
                        selectedNetworkTelemetry = null,
                        statusMessage = "Đã xóa mạng '${detail.ssid}' khỏi lịch sử.",
                    )
                }
            } else {
                _state.update {
                    it.copy(statusMessage = "Chưa xóa được mạng '${detail.ssid}'.")
                }
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
                ocrAutoConnectState = OcrAutoConnectState.Idle,
                isNearbyExpanded = false,
            )
        }
    }

    fun onSsidChanged(value: String) {
        _state.update {
            it.copy(
                ssid = value,
                wifiConnectionState = WifiConnectionState.Idle,
                ocrAutoConnectState = OcrAutoConnectState.NeedUserReview,
            )
        }
    }

    fun onPasswordChanged(value: String) {
        _state.update {
            it.copy(
                password = value,
                wifiConnectionState = WifiConnectionState.Idle,
                ocrAutoConnectState = OcrAutoConnectState.NeedUserReview,
            )
        }
    }

    fun onSecurityChanged(value: String) {
        _state.update {
            it.copy(
                security = value,
                wifiConnectionState = WifiConnectionState.Idle,
                ocrAutoConnectState = OcrAutoConnectState.NeedUserReview,
            )
        }
    }

    fun clearWifiConnectionState() {
        _state.update { it.copy(wifiConnectionState = WifiConnectionState.Idle) }
    }

    fun connectToParsedWifi() {
        val current = _state.value
        val requestedSsid = current.ssid.trim()
        val password = current.password.trim().takeIf { it.isNotEmpty() }
        val security = current.security.trim().takeIf { it.isNotEmpty() }
        logWifiFlow(
            "connectToParsedWifi input ssid='${requestedSsid.debugWifiValue()}', " +
                "password=${password.debugPasswordForLog()}, security='${security.orEmpty()}', " +
                "source=${current.sourceFormat}, android=${Build.VERSION.SDK_INT}, " +
                "permissions=${buildPermissionDebugSummary()}, current=${buildCurrentWifiDebugSnapshot()}",
        )

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

        viewModelScope.launch {
            val scannedNearby = getNearbyNetworksOffMain()
            logWifiFlow(
                "nearby scan before connect count=${scannedNearby.size}: ${scannedNearby.toDebugScanList()}",
            )
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
                _state.update {
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
            logWifiFlow(
                "connection plan candidates=${connectionPlan.candidateSsids}, " +
                    "requiresSelection=${connectionPlan.requiresUserSelection}, " +
                    "suggested=${connectionPlan.suggestedSsid}, score=${connectionPlan.suggestedScore}, " +
                    "reason='${connectionPlan.selectionReason}'",
            )

            if (connectionPlan.requiresUserSelection) {
                val uiMessage = connectionPlan.reviewMessage
                    ?: "Tên Wi-Fi từ OCR chưa khớp chắc chắn với danh sách Wi-Fi thật. Hãy chọn đúng SSID trong danh sách xung quanh rồi kết nối."
                _state.update {
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
                        ocrAutoConnectState = if (current.sourceFormat.isOcrDerivedSource()) {
                            when {
                                connectionPlan.noMatchingWifiFound -> OcrAutoConnectState.NoMatchingWifiFound(uiMessage)
                                else -> OcrAutoConnectState.MultipleMatchesNeedSelection(uiMessage)
                            }
                        } else {
                            it.ocrAutoConnectState
                        },
                        isNearbyExpanded = true,
                    )
                }
                return@launch
            }

            var lastFailure: WifiConnectResult.Failed? = null

            for ((index, targetSsid) in connectionPlan.candidateSsids.withIndex()) {
                _state.update {
                    it.copy(
                        wifiConnectionState = WifiConnectionState.Connecting(
                            ssid = targetSsid,
                            phase = WifiConnectionPhase.CONNECTING_WIFI,
                        ),
                        ocrAutoConnectState = if (current.sourceFormat.isOcrDerivedSource()) {
                            OcrAutoConnectState.AutoConnecting(targetSsid)
                        } else {
                            it.ocrAutoConnectState
                        },
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
                    withTimeout(wifiConnectionTimeoutMillis) {
                        logWifiFlow(
                            "calling WifiConnector api=WifiNetworkSpecifier target='$targetSsid', " +
                                "attempt=${index + 1}/${connectionPlan.candidateSsids.size}, " +
                                "security='${security.orEmpty()}', password=${password.debugPasswordForLog()}, " +
                                "permissions=${buildPermissionDebugSummary()}",
                        )
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
                logWifiFlow(
                    "WifiConnector result for '$targetSsid': ${result.toDebugString()}, " +
                        "currentAfter=${buildCurrentWifiDebugSnapshot()}",
                )

                when (result) {
                    is WifiConnectResult.Success -> {
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

                        _state.update {
                            it.copy(
                                ssid = result.ssid,
                                wifiConnectionState = WifiConnectionState.Connected(ssid = result.ssid),
                                ocrAutoConnectState = if (current.sourceFormat.isOcrDerivedSource()) {
                                    OcrAutoConnectState.AutoConnectSuccess(result.ssid)
                                } else {
                                    it.ocrAutoConnectState
                                },
                                statusMessage = "Kết nối Wi-Fi thành công: ${result.ssid}. Đang lưu lịch sử...",
                            )
                        }

                        val successMessage = when {
                            localSavedRecord != null ->
                                "Kết nối Wi-Fi thành công: ${result.ssid}. Đã lưu lịch sử trên thiết bị."
                            else ->
                                "Kết nối Wi-Fi thành công: ${result.ssid}. Chưa lưu được lịch sử, hãy thử lại sau."
                        }
                        _state.update {
                            it.copy(
                                ssid = result.ssid,
                                statusMessage = successMessage,
                                transientUserMessage = "Kết nối Wi-Fi thành công.",
                                ocrAutoConnectState = if (current.sourceFormat.isOcrDerivedSource()) {
                                    OcrAutoConnectState.AutoConnectSuccess(result.ssid)
                                } else {
                                    it.ocrAutoConnectState
                                },
                            )
                        }
                        return@launch
                    }

                    is WifiConnectResult.ConnectedWithoutInternet -> {
                        val uiMessage = buildWifiConnectedWithoutInternetMessage(result)
                        _state.update {
                            it.copy(
                                ssid = result.ssid,
                                wifiConnectionState = WifiConnectionState.ConnectedWithoutInternet(
                                    ssid = result.ssid,
                                    message = uiMessage,
                                    isCaptivePortal = result.isCaptivePortal,
                                ),
                                ocrAutoConnectState = if (current.sourceFormat.isOcrDerivedSource()) {
                                    OcrAutoConnectState.AutoConnectFailed(uiMessage)
                                } else {
                                    it.ocrAutoConnectState
                                },
                                statusMessage = uiMessage,
                                transientUserMessage = uiMessage,
                            )
                        }
                        return@launch
                    }

                    is WifiConnectResult.Failed -> {
                        if (
                            result.reason == WifiConnectFailureReason.NO_INTERNET ||
                            result.reason == WifiConnectFailureReason.CAPTIVE_PORTAL
                        ) {
                            val joinedResult = WifiConnectResult.ConnectedWithoutInternet(
                                ssid = targetSsid,
                                hasInternetCapability = result.reason == WifiConnectFailureReason.CAPTIVE_PORTAL,
                                isCaptivePortal = result.reason == WifiConnectFailureReason.CAPTIVE_PORTAL,
                            )
                            val uiMessage = buildWifiConnectedWithoutInternetMessage(joinedResult)
                            _state.update {
                                it.copy(
                                    ssid = targetSsid,
                                    wifiConnectionState = WifiConnectionState.ConnectedWithoutInternet(
                                        ssid = targetSsid,
                                        message = uiMessage,
                                        isCaptivePortal = joinedResult.isCaptivePortal,
                                    ),
                                    ocrAutoConnectState = if (current.sourceFormat.isOcrDerivedSource()) {
                                        OcrAutoConnectState.AutoConnectFailed(uiMessage)
                                    } else {
                                        it.ocrAutoConnectState
                                    },
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
            _state.update {
                it.copy(
                    wifiConnectionState = WifiConnectionState.Failed(
                        reason = failure.reason,
                        message = uiMessage,
                    ),
                    ocrAutoConnectState = if (current.sourceFormat.isOcrDerivedSource()) {
                        OcrAutoConnectState.AutoConnectFailed(uiMessage)
                    } else {
                        it.ocrAutoConnectState
                    },
                    statusMessage = uiMessage,
                )
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
                if (sourceFormat.isOcrDerivedSource()) {
                    val passwordValidation = WifiOcrAutoConnectPolicy.validatePasswordForAutoConnect(
                        password = _state.value.password,
                        securityLabel = exactScannedNetwork.securityLabel ?: _state.value.security,
                    )
                    if (passwordValidation is AutoConnectPasswordValidation.Invalid) {
                        return WifiConnectionPlan(
                            candidateSsids = emptyList(),
                            directConnectionMissingFromScan = false,
                            requiresUserSelection = true,
                            suggestedSsid = exactScannedNetwork.ssid,
                            suggestedScore = 1.0,
                            reviewMessage = passwordValidation.message,
                            selectionReason = "Exact scanned SSID found but password rejected before connect.",
                        )
                    }
                }
                return WifiConnectionPlan(
                    candidateSsids = listOf(exactScannedNetwork.ssid),
                    directConnectionMissingFromScan = false,
                    selectionReason = "Exact SSID match from Android Wi-Fi scan.",
                )
            }

            if (sourceFormat.isOcrDerivedSource() && nearbyNetworks.isNotEmpty()) {
                return when (val decision = WifiOcrAutoConnectPolicy.decideSsidMatch(requestedSsid, nearbyNetworks)) {
                    is SsidMatchDecision.AutoConnect -> {
                        val passwordValidation = WifiOcrAutoConnectPolicy.validatePasswordForAutoConnect(
                            password = _state.value.password,
                            securityLabel = decision.candidate.network.securityLabel ?: _state.value.security,
                        )
                        if (passwordValidation is AutoConnectPasswordValidation.Invalid) {
                            WifiConnectionPlan(
                                candidateSsids = emptyList(),
                                directConnectionMissingFromScan = false,
                                requiresUserSelection = true,
                                suggestedSsid = decision.candidate.network.ssid,
                                suggestedScore = decision.candidate.score,
                                reviewMessage = passwordValidation.message,
                                selectionReason = "High-confidence fuzzy SSID found but password rejected before connect.",
                            )
                        } else {
                            WifiConnectionPlan(
                                candidateSsids = listOf(decision.candidate.network.ssid),
                                directConnectionMissingFromScan = false,
                                resolvedFromFuzzyMatch = !decision.candidate.isExactNormalized,
                                suggestedSsid = decision.candidate.network.ssid,
                                suggestedScore = decision.candidate.score,
                                selectionReason = "Single high-confidence fuzzy SSID match selected from scan.",
                            )
                        }
                    }

                    is SsidMatchDecision.SingleMatchNeedsReview -> {
                        WifiConnectionPlan(
                            candidateSsids = emptyList(),
                            directConnectionMissingFromScan = false,
                            requiresUserSelection = true,
                            suggestedSsid = decision.candidate.network.ssid,
                            suggestedScore = decision.candidate.score,
                            reviewMessage = "Tên Wi-Fi gần giống kết quả OCR nhưng chưa đủ chắc chắn. Vui lòng kiểm tra lại trước khi kết nối.",
                            selectionReason = "Single fuzzy SSID below auto-connect threshold; requires review.",
                        )
                    }

                    is SsidMatchDecision.MultipleMatchesNeedSelection -> {
                        val best = decision.candidates.firstOrNull()
                        WifiConnectionPlan(
                            candidateSsids = emptyList(),
                            directConnectionMissingFromScan = false,
                            requiresUserSelection = true,
                            suggestedSsid = best?.network?.ssid,
                            suggestedScore = best?.score,
                            reviewMessage = "Có nhiều Wi-Fi gần giống tên OCR đọc được. Vui lòng chọn đúng mạng trước khi kết nối.",
                            selectionReason = "Multiple fuzzy SSID matches; auto-connect disabled.",
                        )
                    }

                    is SsidMatchDecision.NoMatchingWifiFound -> {
                        WifiConnectionPlan(
                            candidateSsids = emptyList(),
                            directConnectionMissingFromScan = false,
                            requiresUserSelection = true,
                            suggestedSsid = decision.bestCandidate?.network?.ssid,
                            suggestedScore = decision.bestCandidate?.score,
                            noMatchingWifiFound = true,
                            reviewMessage = "Không tìm thấy mạng Wi-Fi phù hợp trong khu vực. Vui lòng kiểm tra lại tên mạng.",
                            selectionReason = "No scanned SSID reached review threshold.",
                        )
                    }
                }
            }

            val directConnectionMissingFromScan = nearbyNetworks.isNotEmpty()
            return WifiConnectionPlan(
                candidateSsids = listOf(requestedSsid),
                directConnectionMissingFromScan = directConnectionMissingFromScan,
                selectionReason = if (directConnectionMissingFromScan) {
                    "Manual/direct connection requested; SSID not found in current scan."
                } else {
                    "Manual/direct connection requested without scan candidates."
                },
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
                "Không thể kết nối. Vui lòng kiểm tra lại mật khẩu."
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
                    ocrAutoConnectState = OcrAutoConnectState.Idle,
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
                if (runCatching { !bitmap.isRecycled }.getOrDefault(false)) {
                    runCatching { bitmap.recycle() }
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
                    ocrAutoConnectState = OcrAutoConnectState.NeedUserReview,
                    isNearbyExpanded = false,
                )
            }
            return
        }

        _state.update {
            it.copy(
                scanSource = source,
                ocrText = text,
                statusMessage = "Đang đọc thông tin Wi-Fi...",
                ocrAutoConnectState = OcrAutoConnectState.OcrProcessing,
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

        val parsedSsid = resolved.parsed.ssid.orEmpty().cleanOcrSsidForConnection()
        val parsedPassword = resolved.parsed.password.orEmpty().cleanOcrPasswordForConnection()
        logWifiFlow(
            "OCR final result source=$source, ssid='${parsedSsid.debugWifiValue()}', " +
                "password=${parsedPassword.debugPasswordForLog()}, security='${resolved.parsed.security.orEmpty()}', " +
                "confidence=${resolved.parsed.confidence}, format='${resolved.parsed.sourceFormat.orEmpty()}'",
        )
        _state.update {
            it.copy(
                ssid = parsedSsid,
                password = parsedPassword,
                security = resolved.parsed.security.orEmpty(),
                sourceFormat = resolved.parsed.sourceFormat.orEmpty(),
                confidence = resolved.parsed.confidence,
                statusMessage = "Đã đọc thông tin Wi-Fi. Đang chuẩn bị quét mạng xung quanh...",
                ocrAutoConnectState = OcrAutoConnectState.OcrParsed,
            )
        }
        _state.update {
            it.copy(
                statusMessage = "Đang tìm mạng Wi-Fi phù hợp...",
                ocrAutoConnectState = OcrAutoConnectState.NearbyWifiScanning,
            )
        }
        val nearbyNetworks = if (parsedSsid.isNotBlank() || parsedPassword.isNotBlank()) {
            getScannedNearbyNetworksForOcr()
                .ifEmpty { getCachedNearbyNetworks() }
        } else {
            emptyList()
        }
        logWifiFlow(
            "OCR nearby scan count=${nearbyNetworks.size}: ${nearbyNetworks.toDebugScanList()}",
        )
        _state.update {
            it.copy(
                statusMessage = "Đang tìm mạng Wi-Fi phù hợp...",
                ocrAutoConnectState = OcrAutoConnectState.MatchingSsid,
            )
        }
        val matchDecision = WifiOcrAutoConnectPolicy.decideSsidMatch(
            ocrSsid = parsedSsid,
            nearbyNetworks = nearbyNetworks,
        )
        logWifiFlow("OCR SSID match decision: ${matchDecision.toDebugString()}")
        val autoCandidate = (matchDecision as? SsidMatchDecision.AutoConnect)?.candidate
        val passwordValidation = autoCandidate?.let { candidate ->
            WifiOcrAutoConnectPolicy.validatePasswordForAutoConnect(
                password = parsedPassword,
                securityLabel = candidate.network.securityLabel ?: resolved.parsed.security,
            )
        }
        val passwordOnlyAutoNetwork = if (autoCandidate == null) {
            findPasswordOnlyAutoConnectNetwork(
                parsedSsid = parsedSsid,
                parsedPassword = parsedPassword,
                nearbyNetworks = nearbyNetworks,
            )
        } else {
            null
        }
        val resolvedPasswordValidation = passwordValidation
            ?: passwordOnlyAutoNetwork?.let { network ->
                WifiOcrAutoConnectPolicy.validatePasswordForAutoConnect(
                    password = parsedPassword,
                    securityLabel = network.securityLabel ?: resolved.parsed.security,
                )
            }
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
        val reviewState = buildOcrAutoConnectReviewState(
            parsedSsid = parsedSsid,
            parsedPassword = parsedPassword,
            sourceFormat = resolved.parsed.sourceFormat.orEmpty(),
            confidence = resolved.parsed.confidence,
            securityLabel = resolved.parsed.security.orEmpty(),
            matchDecision = matchDecision,
            passwordValidation = resolvedPasswordValidation,
        )
        val finalReviewState = if (reviewState is OcrAutoConnectState.NeedUserReview && passwordOnlyAutoNetwork != null) {
            OcrAutoConnectState.AutoConnecting(passwordOnlyAutoNetwork.ssid)
        } else {
            reviewState
        }
        val finalSsid = when {
            finalReviewState is OcrAutoConnectState.AutoConnecting && autoCandidate != null ->
                autoCandidate.network.ssid
            finalReviewState is OcrAutoConnectState.AutoConnecting && passwordOnlyAutoNetwork != null ->
                passwordOnlyAutoNetwork.ssid
            else ->
                autoCandidate?.network?.ssid ?: parsedSsid
        }
        val finalSecurity = resolved.parsed.security.orEmpty()
            .ifBlank { autoCandidate?.network?.securityLabel.orEmpty() }
            .ifBlank { passwordOnlyAutoNetwork?.securityLabel.orEmpty() }
        logWifiFlow(
            "OCR selected ssid='$finalSsid', security='$finalSecurity', " +
                "autoState=${finalReviewState::class.simpleName}, reason=${matchDecision.toDebugString()}, " +
                "passwordOnlyCandidate='${passwordOnlyAutoNetwork?.ssid.orEmpty()}'",
        )
        val suggestionState = when {
            matchDecision is SsidMatchDecision.SingleMatchNeedsReview ->
                SsidSuggestionState.Found(matchDecision.candidate.network.ssid, matchDecision.candidate.score)
            matchDecision is SsidMatchDecision.MultipleMatchesNeedSelection ->
                matchDecision.candidates.firstOrNull()?.let {
                    SsidSuggestionState.Found(it.network.ssid, it.score)
                } ?: fuzzyResolution.state
            autoCandidate != null && !autoCandidate.network.ssid.equals(parsedSsid, ignoreCase = true) ->
                SsidSuggestionState.Found(autoCandidate.network.ssid, autoCandidate.score)
            passwordOnlyAutoNetwork != null ->
                SsidSuggestionState.Found(passwordOnlyAutoNetwork.ssid, 1.0)
            else -> fuzzyResolution.state
        }
        val finalStatusMessage = when {
            finalReviewState is OcrAutoConnectState.AutoConnecting ->
                buildOcrAutoConnectStatusMessage(
                    reviewState = finalReviewState,
                    fallback = resolved.message,
                )
            parsedSsid.isBlank() -> resolved.message
            resolvedPasswordValidation is AutoConnectPasswordValidation.Invalid -> resolvedPasswordValidation.message
            else -> buildOcrAutoConnectStatusMessage(
                reviewState = finalReviewState,
                fallback = resolved.message,
            )
        }

        _state.update {
            it.copy(
                isLoading = false,
                ssid = finalSsid,
                password = parsedPassword,
                security = finalSecurity,
                sourceFormat = resolved.parsed.sourceFormat.orEmpty(),
                confidence = resolved.parsed.confidence,
                statusMessage = finalStatusMessage,
                aiValidation = resolved.aiState,
                ssidSuggestion = suggestionState,
                nearbyNetworks = fuzzyResolution.nearbyNetworks.ifEmpty { nearbyNetworks },
                nearbyWifiStatus = buildNearbyWifiStatus(fuzzyResolution.nearbyNetworks.ifEmpty { nearbyNetworks }),
                wifiConnectionState = WifiConnectionState.Idle,
                ocrAutoConnectState = finalReviewState,
                isNearbyExpanded = finalReviewState is OcrAutoConnectState.MultipleMatchesNeedSelection ||
                    parsedSsid.isBlank() && parsedPassword.isNotBlank(),
            )
        }
    }

    private fun findPasswordOnlyAutoConnectNetwork(
        parsedSsid: String,
        parsedPassword: String,
        nearbyNetworks: List<NearbyNetwork>,
    ): NearbyNetwork? {
        if (parsedSsid.isNotBlank() || parsedPassword.isBlank()) return null
        val securedNetworks = nearbyNetworks.filter { network ->
            network.securityLabel.requiresPasswordForConnection() &&
                WifiOcrAutoConnectPolicy.validatePasswordForAutoConnect(
                    password = parsedPassword,
                    securityLabel = network.securityLabel,
                ) is AutoConnectPasswordValidation.Valid
        }
        val onlyNetwork = securedNetworks.singleOrNull() ?: return null
        logWifiFlow(
            "password-only OCR auto candidate selected ssid='${onlyNetwork.ssid}', " +
                "reason='Only one secured nearby Wi-Fi and password format is valid.'",
        )
        return onlyNetwork
    }

    private suspend fun getScannedNearbyNetworksForOcr(): List<NearbyNetwork> {
        return getNearbyNetworksOffMain(forceRefresh = true)
    }

    private fun buildOcrAutoConnectReviewState(
        parsedSsid: String,
        parsedPassword: String,
        sourceFormat: String,
        confidence: Double?,
        securityLabel: String,
        matchDecision: SsidMatchDecision,
        passwordValidation: AutoConnectPasswordValidation?,
    ): OcrAutoConnectState {
        if (parsedSsid.isBlank()) {
            return OcrAutoConnectState.NeedUserReview
        }
        if (sourceFormat == "qr_local") {
            val validation = passwordValidation ?: WifiOcrAutoConnectPolicy.validatePasswordForAutoConnect(
                password = parsedPassword,
                securityLabel = securityLabel,
            )
            return if (validation is AutoConnectPasswordValidation.Invalid) {
                OcrAutoConnectState.NeedUserReview
            } else {
                OcrAutoConnectState.AutoConnecting(parsedSsid)
            }
        }
        if (!sourceFormat.isEligibleForOcrAutoConnect(confidence)) {
            return OcrAutoConnectState.NeedUserReview
        }
        return when (matchDecision) {
            is SsidMatchDecision.AutoConnect -> {
                when (passwordValidation) {
                    is AutoConnectPasswordValidation.Invalid ->
                        OcrAutoConnectState.NeedUserReview
                    AutoConnectPasswordValidation.Valid ->
                        OcrAutoConnectState.AutoConnecting(matchDecision.candidate.network.ssid)
                    null -> {
                        val validation = WifiOcrAutoConnectPolicy.validatePasswordForAutoConnect(
                            password = parsedPassword,
                            securityLabel = matchDecision.candidate.network.securityLabel,
                        )
                        if (validation is AutoConnectPasswordValidation.Valid) {
                            OcrAutoConnectState.AutoConnecting(matchDecision.candidate.network.ssid)
                        } else {
                            OcrAutoConnectState.NeedUserReview
                        }
                    }
                }
            }

            is SsidMatchDecision.SingleMatchNeedsReview ->
                OcrAutoConnectState.NeedUserReview

            is SsidMatchDecision.MultipleMatchesNeedSelection ->
                OcrAutoConnectState.MultipleMatchesNeedSelection(
                    "Có nhiều Wi-Fi gần giống tên OCR đọc được. Vui lòng chọn đúng mạng trước khi kết nối.",
                )

            is SsidMatchDecision.NoMatchingWifiFound ->
                OcrAutoConnectState.NoMatchingWifiFound(
                    "Không tìm thấy mạng Wi-Fi phù hợp trong khu vực. Vui lòng kiểm tra lại tên mạng.",
                )
        }
    }

    private fun buildOcrAutoConnectStatusMessage(
        reviewState: OcrAutoConnectState,
        fallback: String,
    ): String {
        return when (reviewState) {
            is OcrAutoConnectState.AutoConnecting ->
                "Đang thử kết nối tự động..."
            is OcrAutoConnectState.MultipleMatchesNeedSelection ->
                reviewState.message
            is OcrAutoConnectState.NoMatchingWifiFound ->
                reviewState.message
            is OcrAutoConnectState.AutoConnectFailed ->
                reviewState.message
            OcrAutoConnectState.NeedUserReview ->
                "Không thể kết nối tự động. Vui lòng kiểm tra lại thông tin Wi-Fi."
            else -> fallback
        }
    }

    private fun String.isEligibleForOcrAutoConnect(confidence: Double?): Boolean {
        return when (this) {
            "ocr_local_confident" -> (confidence ?: 0.0) >= localOcrHighConfidenceThreshold
            "ai_ocr", "ocr_server" -> (confidence ?: 0.0) >= localOcrHighConfidenceThreshold
            else -> false
        }
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

    private fun logWifiFlow(message: String) {
        if (BuildConfig.DEBUG) {
            runCatching { Log.d(WIFI_FLOW_LOG_TAG, message) }
        }
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
            val qrPayload = WifiOcrTextParser.parseWifiQrPayloadData(text)
            return OcrCredentialResolution(
                parsed = ParsedWifiData(
                    ssid = local.ssid,
                    password = local.password,
                    security = qrPayload?.security.orEmpty(),
                    sourceFormat = "qr_local",
                    confidence = null,
                ),
                aiData = null,
                aiState = AiValidationState.Hidden,
                message = "Đã đọc thông tin từ mã QR. Ứng dụng sẽ thử kết nối Wi-Fi ngay.",
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

    private fun String.cleanOcrSsidForConnection(): String {
        return removeInvisibleOcrChars()
            .replace('\r', ' ')
            .replace('\n', ' ')
            .replace('\t', ' ')
            .replace("\\s+".toRegex(), " ")
            .trim()
            .removeSurrounding("\"")
    }

    private fun String.cleanOcrPasswordForConnection(): String {
        return removeInvisibleOcrChars()
            .replace("\r", "")
            .replace("\n", "")
            .replace("\t", "")
            .trim()
    }

    private fun String.removeInvisibleOcrChars(): String {
        return replace("[\\u200B-\\u200D\\uFEFF]".toRegex(), "")
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
                val parsedSsid = parsed.ssid.orEmpty().cleanOcrSsidForConnection()
                val parsedPassword = parsed.password.orEmpty().cleanOcrPasswordForConnection()
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
                        password = parsedPassword,
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

                _state.update {
                    it.copy(
                        aiValidation = aiResolution.uiState,
                        ssidSuggestion = fuzzyResolution.state,
                        nearbyNetworks = if (fuzzyResolution.nearbyNetworks.isNotEmpty()) {
                            fuzzyResolution.nearbyNetworks
                        } else {
                            it.nearbyNetworks
                        },
                        statusMessage = buildParseDoneStatus(aiResolution.uiState),
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
                        ocrAutoConnectState = OcrAutoConnectState.Idle,
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
                        ocrAutoConnectState = OcrAutoConnectState.NeedUserReview,
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
                ocrAutoConnectState = OcrAutoConnectState.OcrProcessing,
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
                bestScore >= SSID_REVIEW_SCORE &&
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
        val best = when (val decision = WifiOcrAutoConnectPolicy.decideSsidMatch(ocrSsid, networks)) {
            is SsidMatchDecision.AutoConnect -> decision.candidate
            is SsidMatchDecision.SingleMatchNeedsReview -> decision.candidate
            is SsidMatchDecision.MultipleMatchesNeedSelection -> decision.candidates.firstOrNull()
            is SsidMatchDecision.NoMatchingWifiFound -> decision.bestCandidate
        } ?: return null

        if (best.network.ssid.equals(ocrSsid, ignoreCase = true)) return null
        return SsidSuggestionState.Found(
            bestMatch = best.network.ssid,
            score = best.score,
        )
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

    private fun isCurrentNetworkConnected(targetSsid: String): Boolean {
        if (targetSsid.isBlank()) return false
        val stateSsid = when (val connectionState = _state.value.wifiConnectionState) {
            is WifiConnectionState.Connected -> connectionState.ssid
            is WifiConnectionState.ConnectedWithoutInternet -> connectionState.ssid
            else -> null
        }
        if (!stateSsid.isNullOrBlank() && stateSsid.equals(targetSsid, ignoreCase = true)) {
            return true
        }
        return getCurrentConnectedSsid()?.equals(targetSsid, ignoreCase = true) == true
    }

    @Suppress("DEPRECATION")
    private fun getCurrentConnectedSsid(): String? {
        val app = getApplication<Application>().applicationContext
        val wifiManager = app.getSystemService(WifiManager::class.java) ?: return null
        val ssid = runCatching { wifiManager.connectionInfo?.ssid.orEmpty() }.getOrDefault("")
        return ssid.normalizeWifiSsid().takeIf { it.isNotBlank() && !it.equals("<unknown ssid>", ignoreCase = true) }
    }

    @Suppress("DEPRECATION")
    private fun buildCurrentWifiDebugSnapshot(): String {
        val app = getApplication<Application>().applicationContext
        val wifiManager = app.getSystemService(WifiManager::class.java)
            ?: return "wifiManager=null"
        val info = runCatching { wifiManager.connectionInfo }.getOrNull()
            ?: return "connectionInfo=null"
        return "ssid='${info.ssid.normalizeWifiSsid()}', bssid='${info.bssid}', " +
            "networkId=${info.networkId}, rssi=${info.rssi}, freq=${info.frequency}"
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
            cachedNearbyNetworks = emptyList()
            return emptyList()
        }

        val wifiManager = app.getSystemService(WifiManager::class.java) ?: return emptyList()
        val now = currentTimeMillis()
        if (!forceRefresh && cachedNearbyNetworks.isNotEmpty() && now - lastWifiScanMillis < wifiScanCacheMillis) {
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

    private fun buildPermissionDebugSummary(): String {
        val app = getApplication<Application>().applicationContext
        fun granted(permission: String): Boolean {
            return runCatching {
                ContextCompat.checkSelfPermission(app, permission) == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
        }
        val fine = granted(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = granted(Manifest.permission.ACCESS_COARSE_LOCATION)
        val nearby = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            granted(Manifest.permission.NEARBY_WIFI_DEVICES)
        return "fine=$fine, coarse=$coarse, nearbyWifi=$nearby, " +
            "wifiEnabled=${isWifiEnabled()}, locationEnabled=${isLocationServiceEnabled()}"
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
        deps?.cancelWifiRequests?.invoke() ?: wifiConnector.cancelPendingRequest()
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
private const val maxOcrDecodeSide = 2200
private const val wifiScanCacheMillis = 15_000L
private const val wifiConnectionTimeoutMillis = 90_000L
private const val WIFI_FLOW_LOG_TAG = "SmartWifiFlow"

private data class WifiConnectionPlan(
    val candidateSsids: List<String>,
    val directConnectionMissingFromScan: Boolean,
    val resolvedFromFuzzyMatch: Boolean = false,
    val requiresUserSelection: Boolean = false,
    val suggestedSsid: String? = null,
    val suggestedScore: Double? = null,
    val noMatchingWifiFound: Boolean = false,
    val reviewMessage: String? = null,
    val selectionReason: String = "",
)

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
    val ocrAutoConnectState: OcrAutoConnectState = OcrAutoConnectState.Idle,
    val isNearbyExpanded: Boolean = false,
    val historyRecords: List<SavedWifiRecord> = emptyList(),
    val selectedNetworkDetail: NetworkDetailUiModel? = null,
    val selectedNetworkTelemetry: NetworkLiveTelemetry? = null,
    val transientUserMessage: String? = null,
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

sealed class OcrAutoConnectState {
    object Idle : OcrAutoConnectState()
    object OcrProcessing : OcrAutoConnectState()
    object OcrParsed : OcrAutoConnectState()
    object NearbyWifiScanning : OcrAutoConnectState()
    object MatchingSsid : OcrAutoConnectState()
    data class AutoConnecting(val ssid: String) : OcrAutoConnectState()
    data class AutoConnectSuccess(val ssid: String) : OcrAutoConnectState()
    data class AutoConnectFailed(val message: String) : OcrAutoConnectState()
    object NeedUserReview : OcrAutoConnectState()
    data class MultipleMatchesNeedSelection(val message: String) : OcrAutoConnectState()
    data class NoMatchingWifiFound(val message: String) : OcrAutoConnectState()
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

private fun String?.requiresPasswordForConnection(): Boolean {
    val normalized = orEmpty().uppercase(Locale.ROOT)
    return when {
        normalized.isBlank() -> true
        normalized.contains("OPEN") -> false
        normalized.contains("OWE") -> false
        else -> true
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

private fun String.debugWifiValue(): String {
    return replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}

private fun List<NearbyNetwork>.toDebugScanList(): String {
    if (isEmpty()) return "[]"
    return joinToString(prefix = "[", postfix = "]") { network ->
        "{ssid='${network.ssid.debugWifiValue()}', rssi=${network.signalDbm}, " +
            "level=${network.signalLevel}, bssid='${network.bssid}', security='${network.securityLabel}', " +
            "freq=${network.frequencyMhz}}"
    }
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

private fun SsidMatchDecision.toDebugString(): String {
    return when (this) {
        is SsidMatchDecision.AutoConnect ->
            "AutoConnect(ssid='${candidate.network.ssid}', score=${candidate.score}, exact=${candidate.isExactNormalized})"
        is SsidMatchDecision.SingleMatchNeedsReview ->
            "SingleMatchNeedsReview(ssid='${candidate.network.ssid}', score=${candidate.score})"
        is SsidMatchDecision.MultipleMatchesNeedSelection ->
            "MultipleMatches(${candidates.joinToString { "${it.network.ssid}:${it.score}" }})"
        is SsidMatchDecision.NoMatchingWifiFound ->
            "NoMatchingWifiFound(best=${bestCandidate?.network?.ssid}, score=${bestCandidate?.score})"
    }
}
