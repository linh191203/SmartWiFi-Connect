package com.example.smartwificonnect

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartwificonnect.data.AiValidateData
import com.example.smartwificonnect.data.DefaultWifiRepository
import com.example.smartwificonnect.data.FuzzyNetworkPayload
import com.example.smartwificonnect.data.ParsedWifiData
import com.example.smartwificonnect.data.SaveNetworkRequest
import com.example.smartwificonnect.data.WifiRepository
import com.example.smartwificonnect.data.local.SavedWifiRecord
import com.example.smartwificonnect.feature.home.RecentNetworkType
import com.example.smartwificonnect.feature.home.RecentNetworkUiModel
import com.example.smartwificonnect.ocr.WifiOcrProcessor
import com.example.smartwificonnect.wifi.WifiConnectFailureReason
import com.example.smartwificonnect.wifi.WifiConnectResult
import com.example.smartwificonnect.wifi.WifiConnector
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel @JvmOverloads constructor(
    application: Application,
    private val deps: MainViewModelDeps? = null,
) : AndroidViewModel(application) {
    private val repository: WifiRepository =
        deps?.repository ?: DefaultWifiRepository(application.applicationContext)
    private val ocrProcessor: WifiOcrProcessor = deps?.ocrProcessor ?: WifiOcrProcessor()
    private val wifiConnector = WifiConnector(application.applicationContext)
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

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

    fun onPrefer5GhzPreferenceChanged(enabled: Boolean) {
        _state.update { it.copy(prefer5GhzEnabled = enabled) }
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
        val scannedLevel = getAvailableNearbyNetworks()
            .firstOrNull { it.ssid.equals(network.name, ignoreCase = true) }
            ?.signalLevel
        val detail = savedRecord?.toNetworkDetailUiModel(
            origin = NetworkDetailOrigin.HOME,
            isConnected = isCurrentNetworkConnected(network.name) || network.isConnected,
            scannedSignalLevel = scannedLevel,
        ) ?: network.toNetworkDetailUiModel(
            origin = NetworkDetailOrigin.HOME,
            scannedSignalLevel = scannedLevel,
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
        val scannedLevel = getAvailableNearbyNetworks()
            .firstOrNull { it.ssid.equals(record.ssid, ignoreCase = true) }
            ?.signalLevel
        _state.update {
            it.copy(
                selectedNetworkDetail = record.toNetworkDetailUiModel(
                    origin = NetworkDetailOrigin.HISTORY,
                    isConnected = isCurrentNetworkConnected(record.ssid),
                    scannedSignalLevel = scannedLevel,
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
        val ssid = current.ssid.trim()
        if (ssid.isEmpty()) {
            _state.update {
                it.copy(
                    wifiConnectionState = WifiConnectionState.Failed(
                        reason = WifiConnectFailureReason.INVALID_INPUT,
                        message = "SSID trống. Hãy parse OCR hoặc nhập tay SSID trước khi kết nối.",
                    ),
                    statusMessage = "Chưa có SSID để kết nối.",
                )
            }
            return
        }

        if (isRunningOnEmulator()) {
            _state.update {
                it.copy(
                    wifiConnectionState = WifiConnectionState.Failed(
                        reason = WifiConnectFailureReason.UNKNOWN,
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

        val scannedNearby = getScannedNearbyNetworks()
        val hasSsidInRange = scannedNearby.any { it.ssid.equals(ssid, ignoreCase = true) }
        if (scannedNearby.isNotEmpty() && !hasSsidInRange) {
            _state.update {
                it.copy(
                    wifiConnectionState = WifiConnectionState.Failed(
                        reason = WifiConnectFailureReason.SSID_NOT_FOUND,
                        message = "Không thấy SSID '$ssid' trong cac mạng Wi-Fi xung quanh.",
                    ),
                    statusMessage = "SSID không có sẵn quanh đây. Hãy kiểm tra lại tên mạng hoặc test trên máy thật.",
                )
            }
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    wifiConnectionState = WifiConnectionState.Connecting(ssid = ssid),
                    statusMessage = "Đang kết nối Wi-Fi $ssid ...",
                )
            }

            val result = runCatching {
                withTimeout(20_000L) {
                    connectWifi(
                        ssid = ssid,
                        password = current.password.takeIf { it.isNotBlank() },
                        security = current.security.takeIf { it.isNotBlank() },
                    )
                }
            }.getOrElse { throwable ->
                if (throwable is TimeoutCancellationException) {
                    WifiConnectResult.Failed(
                        reason = WifiConnectFailureReason.TIMEOUT,
                        message = "Timeout khi kết nối Wi-Fi.",
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
                    val localSavedRecord = runCatching {
                        repository.saveConnectedNetworkLocal(
                            baseUrl = current.baseUrl,
                            ocrText = current.ocrText.ifBlank { "Connected from OCR Result" },
                            ssid = result.ssid,
                            password = current.password.takeIf { it.isNotBlank() },
                            sourceFormat = current.sourceFormat.takeIf { it.isNotBlank() },
                            confidence = current.confidence,
                        )
                    }.getOrNull()

                    if (localSavedRecord != null) {
                        _state.update { state ->
                            state.copy(
                                historyRecords = listOf(localSavedRecord) +
                                    state.historyRecords.filterNot { it.id == localSavedRecord.id },
                            )
                        }
                    }

                    val savedToApi = runCatching {
                        repository.saveConnectedNetwork(
                            baseUrl = current.baseUrl,
                            request = SaveNetworkRequest(
                                ssid = result.ssid,
                                password = current.password.takeIf { it.isNotBlank() },
                                security = current.security.takeIf { it.isNotBlank() },
                                sourceFormat = current.sourceFormat.takeIf { it.isNotBlank() },
                                confidence = current.confidence,
                                connectedAtEpochMs = currentTimeMillis(),
                            ),
                        )
                    }.getOrDefault(false)

                    val successMessage = when {
                        localSavedRecord != null && savedToApi ->
                            "Kết nối Wi-Fi thành công: ${result.ssid}. Đã lưu local + server."
                        localSavedRecord != null && !savedToApi ->
                            "Kết nối Wi-Fi thành công: ${result.ssid}. Đã lưu local, server chưa sẵn sàng."
                        localSavedRecord == null && savedToApi ->
                            "Kết nối Wi-Fi thành công: ${result.ssid}. Đã lưu lên server."
                        else ->
                            "Kết nối Wi-Fi thành công: ${result.ssid}. Chưa lưu được lịch sử (thử lại sau)."
                    }
                    _state.update {
                        it.copy(
                            wifiConnectionState = WifiConnectionState.Connected(ssid = result.ssid),
                            statusMessage = successMessage,
                        )
                    }
                }

                is WifiConnectResult.Failed -> {
                    val uiMessage = when (result.reason) {
                        WifiConnectFailureReason.PERMISSION_DENIED ->
                            "Thiếu quyền Wi-Fi/Location. Hãy cấp quyền rồi thử lại."
                        WifiConnectFailureReason.SSID_NOT_FOUND ->
                            "Không tìm thấy SSID xung quanh. Hãy đứng gần router hơn hoặc test trên máy thật."
                        WifiConnectFailureReason.AUTHENTICATION_OR_UNAVAILABLE ->
                            "Không kết nối được. Có thể sai mật khẩu hoặc mạng không khả dụng."
                        WifiConnectFailureReason.TIMEOUT ->
                            "Kết nối quá lâu (timeout). Thử lại hoặc đứng gần router hơn."
                        WifiConnectFailureReason.INVALID_INPUT ->
                            "Dữ liệu kết nối không hợp lệ."
                        WifiConnectFailureReason.UNKNOWN ->
                            result.message ?: "Không kết nối được Wi-Fi."
                    }
                    _state.update {
                        it.copy(
                            wifiConnectionState = WifiConnectionState.Failed(
                                reason = result.reason,
                                message = uiMessage,
                            ),
                            statusMessage = uiMessage,
                        )
                    }
                }
            }
        }
    }

    fun onWifiConnectionPermissionDenied() {
        _state.update {
            it.copy(
                wifiConnectionState = WifiConnectionState.Failed(
                    reason = WifiConnectFailureReason.PERMISSION_DENIED,
                    message = "Cần quyền Location/Nearby Wi-Fi để kết nối mạng thật.",
                ),
                statusMessage = "Cần cấp quyền trước khi kết nối Wi-Fi.",
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

    fun startOcrFromGallery(uri: Uri) {
        viewModelScope.launch {
            setOcrLoading("Đang xử lý ảnh từ thư viện...")
            runCatching {
                val bitmap = decodeBitmapFromUri(uri)
                ocrProcessor.recognizeText(bitmap)
            }.onSuccess { text ->
                handleOcrRecognitionSuccess(
                    source = "Thư viện ảnh",
                    text = text,
                    blankMessage = "OCR không đọc được nội dung. Thử ảnh rõ hơn hoặc đổi góc chụp.",
                )
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        scanSource = "Thư viện ảnh",
                        statusMessage = "OCR thất bại: ${throwable.message ?: "Lỗi không xác định"}",
                    )
                }
            }
        }
    }

    fun startOcrFromCamera(bitmap: Bitmap) {
        viewModelScope.launch {
            setOcrLoading("Đang quét OCR từ camera...")
            runCatching {
                ocrProcessor.recognizeText(bitmap)
            }.onSuccess { text ->
                handleOcrRecognitionSuccess(
                    source = "May quet",
                    text = text,
                    blankMessage = "OCR không đọc được nội dung. Thử chụp lại rõ hơn.",
                )
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        scanSource = "May quet",
                        statusMessage = "OCR thất bại: ${throwable.message ?: "Lỗi không xác định"}",
                    )
                }
            }
        }
    }

    private suspend fun handleOcrRecognitionSuccess(
        source: String,
        text: String,
        blankMessage: String,
        preferLocalCredentials: Boolean = false,
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
            preferLocalCredentials = preferLocalCredentials,
        )

        val saveMessage = if (resolved.parsed.ssid.orEmpty().isNotBlank() ||
            resolved.parsed.password.orEmpty().isNotBlank()
        ) {
            runCatching {
                repository.saveParsedWifi(
                    baseUrl = currentBaseUrl,
                    ocrText = text,
                    parsedWifiData = resolved.parsed,
                    aiValidateData = resolved.aiData,
                    fuzzyBestMatch = null,
                    fuzzyScore = null,
                )
            }.fold(
                onSuccess = { savedRecord ->
                    _state.update { state ->
                        state.copy(
                            historyRecords = listOf(savedRecord) +
                                state.historyRecords.filterNot { it.id == savedRecord.id },
                        )
                    }
                    "Đã lưu vào thiết bị."
                },
                onFailure = { "Chưa lưu được: ${it.message}" },
            )
        } else {
            "Chưa có dữ liệu để lưu."
        }

        _state.update {
            it.copy(
                isLoading = false,
                ssid = resolved.parsed.ssid.orEmpty(),
                password = resolved.parsed.password.orEmpty(),
                security = resolved.parsed.security.orEmpty(),
                sourceFormat = resolved.parsed.sourceFormat.orEmpty(),
                confidence = resolved.parsed.confidence,
                statusMessage = "${resolved.message} $saveMessage",
                aiValidation = AiValidationState.Hidden,
                ssidSuggestion = SsidSuggestionState.Hidden,
                nearbyNetworks = emptyList(),
                nearbyWifiStatus = "",
                wifiConnectionState = WifiConnectionState.Idle,
                isNearbyExpanded = false,
            )
        }
    }

    private suspend fun resolveOcrCredentials(
        baseUrl: String,
        text: String,
        preferLocalCredentials: Boolean = false,
    ): OcrCredentialResolution {
        val local = ocrProcessor.extractWifiCredentials(text)
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
                message = "Đã đọc thông tin từ mã QR. Hãy kiểm tra rồi bấm Kết nối.",
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
        if (aiSsid.isNotBlank() || aiPassword.isNotBlank()) {
            return OcrCredentialResolution(
                parsed = ParsedWifiData(
                    ssid = aiSsid,
                    password = aiPassword,
                    security = "",
                    sourceFormat = "ai_ocr",
                    confidence = aiReady?.confidence,
                ),
                aiData = aiResolution.persisted,
                message = "AI đã điền thông tin. Hãy kiểm tra SSID/mật khẩu rồi bấm Kết nối.",
            )
        }

        val parsedByServer = runCatching {
            repository.parseOcr(baseUrl, text)
        }.getOrNull()
        if (parsedByServer?.ok == true && parsedByServer.data != null) {
            val parsed = parsedByServer.data
            if (parsed.ssid.orEmpty().isNotBlank() || parsed.password.orEmpty().isNotBlank()) {
                return OcrCredentialResolution(
                    parsed = parsed.copy(
                        sourceFormat = parsed.sourceFormat.orEmpty().ifBlank { "ocr_server" },
                    ),
                    aiData = aiResolution.persisted,
                    message = "Đã điền thông tin từ kết quả OCR. Hãy kiểm tra rồi bấm Kết nối.",
                )
            }
        }

        if (local.ssid.isNotBlank() || local.password.isNotBlank()) {
            return OcrCredentialResolution(
                parsed = ParsedWifiData(
                    ssid = local.ssid,
                    password = local.password,
                    security = "",
                    sourceFormat = "ocr_local_hint",
                    confidence = null,
                ),
                aiData = aiResolution.persisted,
                message = "AI chưa trả dữ liệu rõ ràng, app đã điền gợi ý từ nội dung OCR.",
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
            message = "Chưa đọc được tên Wi-Fi và mật khẩu. Bạn có thể nhập lại thủ công.",
        )
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
                        statusMessage = "Không kết nối được server: ${throwable.message}",
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
            setLoading(true, "Đang parse thông tin WiFi...")
            try {
                val currentState = _state.value
                val parseEnvelope = repository.parseOcr(currentState.baseUrl, text)
                if (!parseEnvelope.ok || parseEnvelope.data == null) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = parseEnvelope.error ?: "Không parse được dữ liệu WiFi",
                        )
                    }
                    return@launch
                }

                val parsed = parseEnvelope.data
                val parsedSsid = parsed.ssid.orEmpty()
                val hasSsid = parsedSsid.isNotBlank()
                val nearbyNetworks = if (hasSsid) {
                    getAvailableNearbyNetworks()
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

                val saveMessage = runCatching {
                    repository.saveParsedWifi(
                        baseUrl = currentState.baseUrl,
                        ocrText = text,
                        parsedWifiData = parsed,
                        aiValidateData = aiResolution.persisted,
                        fuzzyBestMatch = fuzzyResolution.bestMatch,
                        fuzzyScore = fuzzyResolution.score,
                    )
                }.fold(
                    onSuccess = { savedRecord ->
                        _state.update { state ->
                            state.copy(
                                historyRecords = listOf(savedRecord) +
                                    state.historyRecords.filterNot { it.id == savedRecord.id },
                            )
                        }
                        "Đã lưu vào thiết bị."
                    },
                    onFailure = { "Không lưu được: ${it.message}" },
                )

                _state.update {
                    it.copy(
                        aiValidation = aiResolution.uiState,
                        ssidSuggestion = fuzzyResolution.state,
                        nearbyNetworks = if (fuzzyResolution.nearbyNetworks.isNotEmpty()) {
                            fuzzyResolution.nearbyNetworks
                        } else {
                            it.nearbyNetworks
                        },
                        statusMessage = buildParseDoneStatus(aiResolution.uiState, saveMessage),
                    )
                }
            } catch (throwable: Throwable) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Lỗi parse: ${throwable.message}",
                        aiValidation = AiValidationState.Failed(
                            throwable.message ?: "Không đánh giá được AI",
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
                source = "Quet QR",
                text = text,
                blankMessage = "QR không có thông tin Wi-Fi hợp lệ. Vui lòng quét lại.",
                preferLocalCredentials = true,
            )
        }
    }

    fun consumeSharedWifiLink(uri: Uri?) {
        if (uri?.scheme != "smartwifi" || uri.host != "join") return
        val sharedSsid = uri.getQueryParameter("ssid").orEmpty().trim()
        if (sharedSsid.isBlank()) return

        val sharedPassword = uri.getQueryParameter("password").orEmpty()
        val sharedSecurity = uri.getQueryParameter("security").orEmpty().ifBlank {
            if (sharedPassword.isBlank()) "Open" else "WPA/WPA2"
        }

        _state.update {
            it.copy(
                ocrText = uri.toString(),
                ssid = sharedSsid,
                password = sharedPassword,
                security = sharedSecurity,
                sourceFormat = "share_link",
                confidence = null,
                scanSource = "Link chia se",
                statusMessage = "Đã nhận thông tin Wi-Fi từ link chia sẻ. Hãy kiểm tra rồi bấm Kết nối.",
                isLoading = false,
                aiValidation = AiValidationState.Hidden,
                ssidSuggestion = SsidSuggestionState.Hidden,
                nearbyNetworks = emptyList(),
                nearbyWifiStatus = "",
                wifiConnectionState = WifiConnectionState.Idle,
                isNearbyExpanded = false,
            )
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
            val scannedNetworks = getScannedNearbyNetworks()
            val previousNetworks = _state.value.nearbyNetworks
            val networks = if (scannedNetworks.isNotEmpty()) scannedNetworks else previousNetworks
            val nearbyStatus = when {
                scannedNetworks.isNotEmpty() -> buildNearbyWifiStatus(scannedNetworks)
                previousNetworks.isNotEmpty() -> "Không cập nhật được danh sách mới, đang hiển thị dữ liệu scan gần nhất."
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

    private fun decodeBitmapFromUri(uri: Uri): Bitmap {
        val resolver = getApplication<Application>().contentResolver
        val source = ImageDecoder.createSource(resolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    private fun loadLatestSavedWifi() {
        viewModelScope.launch {
                val savedWifi = runCatching { repository.getLatestSavedWifi() }
                    .getOrNull() ?: return@launch
            val savedFuzzyMatch = savedWifi.fuzzyBestMatch
            val nearbyNetworks = getAvailableNearbyNetworks()
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
                    statusMessage = "Đã tải dữ liệu WiFi gần nhất từ SQLite",
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
        val envelope = runCatching {
            repository.validateAi(
                baseUrl = baseUrl,
                ssid = ssid,
                password = password,
                ocrText = ocrText,
            )
        }.getOrElse { throwable ->
            return AiResolution(
                uiState = AiValidationState.Failed(
                    throwable.message ?: "AI validate không kết nối được",
                ),
                persisted = null,
            )
        }

        if (!envelope.ok || envelope.data == null) {
            return AiResolution(
                uiState = AiValidationState.Failed(
                    envelope.error ?: "AI validate không trả về dữ liệu hợp lệ",
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

    // â”€â”€ Fuzzy SSID Match â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun triggerFuzzyMatch() {
        val current = _state.value
        val ocrSsid = current.ssid.trim()
        if (ocrSsid.isEmpty()) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    ssidSuggestion = SsidSuggestionState.Loading,
                    nearbyNetworks = getAvailableNearbyNetworks(),
                )
            }

            val fuzzy = resolveFuzzySuggestion(
                baseUrl = current.baseUrl,
                ocrSsid = ocrSsid,
                nearbyNetworks = _state.value.nearbyNetworks.ifEmpty { getAvailableNearbyNetworks() },
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

        val apiResult = runCatching {
            repository.fuzzyMatchSsid(
                baseUrl = baseUrl,
                ocrSsid = ocrSsid,
                nearbyNetworks = payload,
            )
        }.getOrNull()

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
        var bestNetwork: NearbyNetwork? = null
        var bestScore = 0.0

        for (network in networks) {
            val score = similarityScore(ocrLower, network.ssid.lowercase(Locale.ROOT))
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
        saveMessage: String,
    ): String {
        return when (aiState) {
            is AiValidationState.Ready -> "Parse thanh cong. AI validate xong. $saveMessage"
            is AiValidationState.Failed -> "Parse thanh cong. AI validate loi: ${aiState.message}. $saveMessage"
            AiValidationState.Hidden, AiValidationState.Loading -> "Parse thanh cong. $saveMessage"
        }
    }

    private fun isCurrentNetworkConnected(targetSsid: String): Boolean {
        if (targetSsid.isBlank()) return false
        val stateSsid = (_state.value.wifiConnectionState as? WifiConnectionState.Connected)?.ssid
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
    private fun getCurrentNetworkTelemetry(targetSsid: String): NetworkLiveTelemetry? {
        if (targetSsid.isBlank() || !hasNearbyWifiPermission()) return null
        val app = getApplication<Application>().applicationContext
        val wifiManager = app.getSystemService(WifiManager::class.java) ?: return null
        val info = runCatching { wifiManager.connectionInfo }.getOrNull() ?: return null
        if (!info.ssid.normalizeWifiSsid().equals(targetSsid, ignoreCase = true)) return null

        val frequencyMhz = info.frequency.takeIf { it > 0 }
        val signalDbm = info.rssi.takeIf { it in -99..-30 }
        val rxSpeed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.rxLinkSpeedMbps.takeIf { it > 0 }
        } else {
            null
        }
        val txSpeed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            info.txLinkSpeedMbps.takeIf { it > 0 }
        } else {
            null
        }

        return NetworkLiveTelemetry(
            linkSpeedMbps = info.linkSpeed.takeIf { it > 0 },
            rxLinkSpeedMbps = rxSpeed,
            txLinkSpeedMbps = txSpeed,
            signalDbm = signalDbm,
            frequencyMhz = frequencyMhz,
            updatedAtMillis = currentTimeMillis(),
        )
    }

    private fun getAvailableNearbyNetworks(): List<NearbyNetwork> {
        val scanned = getScannedNearbyNetworks()
        return if (scanned.isNotEmpty()) scanned else _state.value.nearbyNetworks
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

    @Suppress("DEPRECATION")
    @android.annotation.SuppressLint("MissingPermission") // hasNearbyWifiPermission() checked above; SecurityException caught by runCatching
    private fun getScannedNearbyNetworks(): List<NearbyNetwork> {
        deps?.scannedNearbyNetworks?.let { return it.invoke() }
        val app = getApplication<Application>().applicationContext
        if (!hasNearbyWifiPermission()) return emptyList()

        val wifiManager = app.getSystemService(WifiManager::class.java) ?: return emptyList()
        return runCatching {
            wifiManager.startScan()
            wifiManager.scanResults
                .asSequence()
                .mapNotNull { result ->
                    val ssid = result.SSID?.trim().orEmpty()
                    if (ssid.isBlank()) {
                        null
                    } else {
                        NearbyNetwork(
                            ssid = ssid,
                            signalLevel = result.level.toWifiSignalLevel(),
                        )
                    }
                }
                .distinctBy { it.ssid.lowercase(Locale.ROOT) }
                .sortedByDescending { it.signalLevel }
                .take(12)
                .toList()
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

    private fun Int.toWifiSignalLevel(): Int {
        return when {
            this >= -55 -> 4
            this >= -67 -> 3
            this >= -80 -> 2
            else -> 1
        }
    }

    private fun buildNearbyWifiStatus(scannedNetworks: List<NearbyNetwork>): String {
        if (isRunningOnEmulator()) {
            return "Đang chạy emulator: scan/kết nối Wi-Fi thật sẽ bị giới hạn. Hãy test trên máy Android thật."
        }
        return if (scannedNetworks.isNotEmpty()) {
            "Đã tìm thấy ${scannedNetworks.size} mạng Wi-Fi xung quanh."
        } else {
            "Chưa lấy được mạng Wi-Fi thực tế. Hãy cấp quyền Vị trí/Wi-Fi nearby và bật Location trên máy."
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

    // â”€â”€ End Fuzzy SSID Match â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    override fun onCleared() {
        deps?.cancelWifiRequests?.invoke() ?: wifiConnector.cancelPendingRequest()
        ocrProcessor.release()
        super.onCleared()
    }
}

data class MainViewModelDeps(
    val repository: WifiRepository? = null,
    val ocrProcessor: WifiOcrProcessor? = null,
    val connectWifi: (suspend (ssid: String, password: String?, security: String?) -> WifiConnectResult)? = null,
    val cancelWifiRequests: (() -> Unit)? = null,
    val hasNearbyWifiPermission: (() -> Boolean)? = null,
    val scannedNearbyNetworks: (() -> List<NearbyNetwork>)? = null,
    val isRunningOnEmulator: (() -> Boolean)? = null,
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
    val message: String,
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
    val prefer5GhzEnabled: Boolean = true,
    val ocrText: String = "",
    val ssid: String = "",
    val password: String = "",
    val security: String = "",
    val sourceFormat: String = "",
    val confidence: Double? = null,
    val scanSource: String = "",
    val statusMessage: String = "Sẵn sàng quét OCR WiFi",
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
    data class Connecting(val ssid: String) : WifiConnectionState()
    data class Connected(val ssid: String) : WifiConnectionState()
    data class Failed(
        val reason: WifiConnectFailureReason,
        val message: String,
    ) : WifiConnectionState()
}

sealed class SsidSuggestionState {
    object Hidden : SsidSuggestionState()
    object Loading : SsidSuggestionState()
    data class Found(val bestMatch: String, val score: Double) : SsidSuggestionState()
    object NotFound : SsidSuggestionState()
}

data class NearbyNetwork(val ssid: String, val signalLevel: Int)

private fun SavedWifiRecord.toNetworkDetailUiModel(
    origin: NetworkDetailOrigin,
    isConnected: Boolean,
    scannedSignalLevel: Int?,
): NetworkDetailUiModel {
    val dbm = scannedSignalLevel?.toApproximateDbm()
    return NetworkDetailUiModel(
        savedRecordId = id,
        ssid = ssid.ifBlank { "Wi-Fi đã lưu" },
        lastConnectedLabel = "Kết nối lần cuối: ${createdAtMillis.toNetworkDetailDate()}",
        protocolLabel = inferSecurityLabel(
            ssid = ssid,
            password = password,
            sourceFormat = sourceFormat,
            fallbackOpen = password.isBlank(),
        ),
        frequencyLabel = inferFrequencyLabel(ssid = ssid, frequencyMhz = null),
        signalDbm = dbm,
        signalQualityLabel = inferSignalQualityLabel(dbm),
        usageTotalLabel = buildUsageTotalLabel(ssid, connected = isConnected),
        usageHighlightLabel = buildUsageHighlightLabel(ssid),
        usageBars = buildUsageBars(ssid),
        sourceTitle = "Nguồn kết nối",
        sourceSubtitle = sourceFormat.toSourceSubtitle(),
        sourceBadgeLabel = if (password.isBlank()) "Mạng công cộng" else "Thiết bị tin cậy",
        isConnected = isConnected,
        canDelete = true,
        origin = origin,
        password = password,
        security = inferSecurityLabel(
            ssid = ssid,
            password = password,
            sourceFormat = sourceFormat,
            fallbackOpen = password.isBlank(),
        ),
    )
}

private fun RecentNetworkUiModel.toNetworkDetailUiModel(
    origin: NetworkDetailOrigin,
    scannedSignalLevel: Int?,
): NetworkDetailUiModel {
    val dbm = scannedSignalLevel?.toApproximateDbm()
    return NetworkDetailUiModel(
        ssid = name,
        lastConnectedLabel = lastConnectedLabel,
        protocolLabel = inferSecurityLabel(
            ssid = name,
            password = "",
            sourceFormat = type.name.lowercase(Locale.ROOT),
            fallbackOpen = type == RecentNetworkType.BUILDING,
        ),
        frequencyLabel = inferFrequencyLabel(ssid = name, frequencyMhz = null),
        signalDbm = dbm,
        signalQualityLabel = inferSignalQualityLabel(dbm),
        usageTotalLabel = buildUsageTotalLabel(name, connected = isConnected),
        usageHighlightLabel = buildUsageHighlightLabel(name),
        usageBars = buildUsageBars(name),
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

private fun inferSecurityLabel(
    ssid: String,
    password: String,
    sourceFormat: String,
    fallbackOpen: Boolean,
): String {
    val ssidLower = ssid.lowercase(Locale.ROOT)
    val sourceLower = sourceFormat.lowercase(Locale.ROOT)
    return when {
        fallbackOpen -> "OPEN"
        "wpa3" in sourceLower -> "WPA3-SAE"
        "5g" in ssidLower || "premium" in ssidLower -> "WPA3-SAE"
        password.length >= 8 -> "WPA2/WPA3"
        else -> "WPA2-PSK"
    }
}

private fun inferFrequencyLabel(
    ssid: String,
    frequencyMhz: Int?,
): String {
    return when {
        frequencyMhz != null && frequencyMhz >= 5000 -> String.format(Locale.US, "%.1f GHz", frequencyMhz / 1000f)
        frequencyMhz != null -> String.format(Locale.US, "%.1f GHz", frequencyMhz / 1000f)
        ssid.lowercase(Locale.ROOT).contains("5g") -> "5.8 GHz"
        else -> "2.4 GHz"
    }
}

private fun inferSignalQualityLabel(dbm: Int?): String {
    return when {
        dbm == null -> "Ổn định"
        dbm >= -50 -> "Tuyệt vời"
        dbm >= -60 -> "Rất tốt"
        dbm >= -70 -> "Tốt"
        dbm >= -80 -> "Khá yếu"
        else -> "Yếu"
    }
}

private fun Int.toApproximateDbm(): Int {
    return when (this) {
        4 -> -42
        3 -> -57
        2 -> -68
        else -> -82
    }
}

private fun buildUsageTotalLabel(seed: String, connected: Boolean): String {
    val base = (seed.hashCode().toUInt().toLong() % 90L) / 10.0 + if (connected) 10.0 else 4.0
    return "Tổng cộng: ${String.format(Locale.US, "%.1f", base)} GB"
}

private fun buildUsageHighlightLabel(seed: String): String {
    val peak = (seed.hashCode().toUInt().toLong() % 35L) / 10.0 + 1.8
    return "${String.format(Locale.US, "%.1f", peak)}G"
}

private fun buildUsageBars(seed: String): List<Float> {
    val hash = seed.hashCode().toUInt().toLong()
    return List(6) { index ->
        val raw = ((hash shr (index * 4)) and 0xFL).toFloat()
        0.28f + (raw / 15f) * 0.72f
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

