package com.example.smartwificonnect

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
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
import com.example.smartwificonnect.ocr.WifiOcrCredentials
import com.example.smartwificonnect.ocr.WifiOcrProcessor
import com.example.smartwificonnect.ocr.WifiOcrScanResult
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.example.smartwificonnect.wifi.WifiConnectFailureReason
import com.example.smartwificonnect.wifi.WifiConnectResult
import com.example.smartwificonnect.wifi.WifiConnector
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val OCR_LOG_TAG = "SmartWifiOcr"

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
        refreshWifiEnvironment()
    }

    fun onDarkModeChanged(enabled: Boolean) {
        _state.update { it.copy(isDarkModeEnabled = enabled) }
    }

    fun openNetworkDetailFromRecent(network: RecentNetworkUiModel) {
        val current = _state.value
        val savedRecord = current.historyRecords.firstOrNull { it.id == network.sourceRecordId }
            ?: current.historyRecords.firstOrNull { it.ssid.equals(network.name, ignoreCase = true) }
        val scannedNearby = getAvailableNearbyNetworks()
            .firstOrNull { it.ssid.equals(network.name, ignoreCase = true) }
        val detail = savedRecord?.toNetworkDetailUiModel(
            origin = NetworkDetailOrigin.HOME,
            isConnected = isCurrentNetworkConnected(network.name) || network.isConnected,
            scannedNearby = scannedNearby,
        ) ?: network.toNetworkDetailUiModel(
            origin = NetworkDetailOrigin.HOME,
            scannedNearby = scannedNearby,
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
        val scannedNearby = getAvailableNearbyNetworks()
            .firstOrNull { it.ssid.equals(record.ssid, ignoreCase = true) }
        _state.update {
            it.copy(
                selectedNetworkDetail = record.toNetworkDetailUiModel(
                    origin = NetworkDetailOrigin.HISTORY,
                    isConnected = isCurrentNetworkConnected(record.ssid),
                    scannedNearby = scannedNearby,
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

    fun openAutoConnectTargetFromSettings(): Boolean {
        val detail = buildBestSavedNearbyNetworkDetail(
            preferredSsid = getCurrentConnectedSsid(),
            origin = NetworkDetailOrigin.HOME,
        ) ?: return false
        _state.update {
            it.copy(
                selectedNetworkDetail = detail,
                selectedNetworkTelemetry = null,
            )
        }
        refreshSelectedNetworkTelemetry()
        return true
    }

    fun openPriorityNetworkFromSettings(): Boolean {
        val detail = buildBestSavedNearbyNetworkDetail(
            preferredSsid = null,
            origin = NetworkDetailOrigin.HOME,
        ) ?: return false
        _state.update {
            it.copy(
                selectedNetworkDetail = detail,
                selectedNetworkTelemetry = null,
            )
        }
        refreshSelectedNetworkTelemetry()
        return true
    }

    private fun buildBestSavedNearbyNetworkDetail(
        preferredSsid: String?,
        origin: NetworkDetailOrigin,
    ): NetworkDetailUiModel? {
        val current = _state.value
        val nearbyBySsid = getAvailableNearbyNetworks()
            .associateBy { it.ssid.lowercase(Locale.ROOT) }

        preferredSsid?.takeIf { it.isNotBlank() }?.let { preferred ->
            current.historyRecords.firstOrNull { it.ssid.equals(preferred, ignoreCase = true) }?.let { record ->
                return record.toNetworkDetailUiModel(
                    origin = origin,
                    isConnected = isCurrentNetworkConnected(record.ssid),
                    scannedNearby = nearbyBySsid[record.ssid.lowercase(Locale.ROOT)],
                )
            }
        }

        return current.historyRecords
            .filter { it.ssid.isNotBlank() }
            .mapNotNull { record ->
                nearbyBySsid[record.ssid.lowercase(Locale.ROOT)]?.let { nearby ->
                    record to nearby
                }
            }
            .maxWithOrNull(
                compareBy<Pair<SavedWifiRecord, NearbyNetwork>> { it.second.signalLevel }
                    .thenByDescending { it.first.createdAtMillis },
            )
            ?.let { (record, nearby) ->
                record.toNetworkDetailUiModel(
                    origin = origin,
                    isConnected = isCurrentNetworkConnected(record.ssid),
                    scannedNearby = nearby,
                )
            }
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
                        statusMessage = "Da xoa mang '${detail.ssid}' khoi lich su.",
                    )
                }
            } else {
                _state.update {
                    it.copy(statusMessage = "Chua xoa duoc mang '${detail.ssid}'.")
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
        val password = current.password.trim()

        if (!isWifiEnabledInSystem()) {
            _state.update {
                it.copy(
                    isWifiEnabled = false,
                    wifiConnectionState = WifiConnectionState.Failed(
                        reason = WifiConnectFailureReason.WIFI_DISABLED,
                        message = "Wi-Fi dang tat. Hay bat Wi-Fi roi thu lai.",
                    ),
                    statusMessage = "Wi-Fi dang tat. Hay bat Wi-Fi de tiep tuc.",
                )
            }
            return
        }

        if (isRunningOnEmulator()) {
            _state.update {
                it.copy(
                    wifiConnectionState = WifiConnectionState.Failed(
                        reason = WifiConnectFailureReason.UNKNOWN,
                        message = "Emulator khong ho tro ket noi Wi-Fi that. Hay test tren dien thoai Android that.",
                    ),
                    statusMessage = "Dang chay tren emulator: khong the ket noi truc tiep toi router Wi-Fi that.",
                )
            }
            return
        }

        if (!hasNearbyWifiPermission()) {
            onWifiConnectionPermissionDenied()
            return
        }

        val scannedNearby = getScannedNearbyNetworks()
        if (ssid.isEmpty()) {
            if (password.isBlank()) {
                _state.update {
                    it.copy(
                        wifiConnectionState = WifiConnectionState.Failed(
                            reason = WifiConnectFailureReason.INVALID_INPUT,
                            message = "Chua co du SSID hoac mat khau de ket noi.",
                        ),
                        statusMessage = "Chua co du thong tin Wi-Fi de ket noi.",
                    )
                }
                return
            }

            if (scannedNearby.isEmpty()) {
                _state.update {
                    it.copy(
                        wifiConnectionState = WifiConnectionState.Failed(
                            reason = WifiConnectFailureReason.SSID_NOT_FOUND,
                            message = "Khong co Wi-Fi gan day de thu voi mat khau vua quet.",
                        ),
                        statusMessage = "Khong co Wi-Fi nao o gan day de thu ket noi bang mat khau vua quet.",
                    )
                }
                return
            }

            viewModelScope.launch {
                connectByPasswordAcrossNearbyNetworks(
                    current = current,
                    nearbyNetworks = scannedNearby,
                )
            }
            return
        }

        val targetNetwork = resolveBestConnectionTarget(ssid, scannedNearby)
        if (scannedNearby.isNotEmpty() && targetNetwork == null) {
            _state.update {
                it.copy(
                    wifiConnectionState = WifiConnectionState.Failed(
                        reason = WifiConnectFailureReason.SSID_NOT_FOUND,
                        message = "Khong thay mang Wi-Fi phu hop voi '$ssid' trong cac mang xung quanh.",
                    ),
                    statusMessage = "Khong tim thay mang gan dung voi OCR trong danh sach Wi-Fi thuc te quanh day.",
                )
            }
            return
        }
        val resolvedSsid = targetNetwork?.ssid ?: ssid
        val usedFuzzyMatch = !resolvedSsid.equals(ssid, ignoreCase = true)

        viewModelScope.launch {
            _state.update {
                it.copy(
                    ssid = resolvedSsid,
                    ssidSuggestion = targetNetwork
                        ?.takeIf { usedFuzzyMatch }
                        ?.let { match ->
                            SsidSuggestionState.Found(
                                bestMatch = match.ssid,
                                score = match.score,
                            )
                        } ?: SsidSuggestionState.Hidden,
                    wifiConnectionState = WifiConnectionState.Connecting(ssid = resolvedSsid),
                    statusMessage = if (usedFuzzyMatch) {
                        "Dang ket noi mang gan dung nhat '$resolvedSsid' tu OCR '$ssid' ..."
                    } else {
                        "Dang ket noi Wi-Fi $resolvedSsid ..."
                    },
                )
            }

            val result = performWifiConnectionAttempt(
                ssid = resolvedSsid,
                password = current.password.takeIf { it.isNotBlank() },
                security = current.security.takeIf { it.isNotBlank() },
            )

            when (result) {
                is WifiConnectResult.Success -> {
                    if (!finalizeSuccessfulWifiConnection(current, resolvedSsid)) {
                        return@launch
                    }
                }

                is WifiConnectResult.Failed -> {
                    publishWifiFailure(result)
                }
            }
        }
    }

    private suspend fun connectByPasswordAcrossNearbyNetworks(
        current: MainUiState,
        nearbyNetworks: List<NearbyNetwork>,
    ) {
        val password = current.password.takeIf { it.isNotBlank() }
        val security = current.security.takeIf { it.isNotBlank() }
        val sortedNetworks = nearbyNetworks.sortedByDescending { it.signalLevel }

        for ((index, network) in sortedNetworks.withIndex()) {
            _state.update {
                it.copy(
                    ssid = network.ssid,
                    wifiConnectionState = WifiConnectionState.Connecting(ssid = network.ssid),
                    statusMessage = "Dang thu ${index + 1}/${sortedNetworks.size}: ${network.ssid} bang mat khau vua quet...",
                    ssidSuggestion = SsidSuggestionState.Hidden,
                )
            }

            when (performWifiConnectionAttempt(network.ssid, password, security)) {
                is WifiConnectResult.Success -> {
                    val currentWithResolvedSsid = current.copy(ssid = network.ssid)
                    if (finalizeSuccessfulWifiConnection(currentWithResolvedSsid, network.ssid)) {
                        return
                    }
                }

                is WifiConnectResult.Failed -> Unit
            }
        }

        _state.update {
            it.copy(
                wifiConnectionState = WifiConnectionState.Failed(
                    reason = WifiConnectFailureReason.AUTHENTICATION_OR_UNAVAILABLE,
                    message = "Da thu cac Wi-Fi gan day bang mat khau vua quet nhung chua co mang nao ket noi duoc.",
                ),
                statusMessage = "Da thu ${sortedNetworks.size} Wi-Fi gan day tu manh den yeu, nhung chua co mang nao hop voi mat khau vua quet.",
            )
        }
    }

    private suspend fun performWifiConnectionAttempt(
        ssid: String,
        password: String?,
        security: String?,
    ): WifiConnectResult {
        return runCatching {
            withTimeout(20_000L) {
                connectWifi(
                    ssid = ssid,
                    password = password,
                    security = security,
                )
            }
        }.getOrElse { throwable ->
            if (throwable is TimeoutCancellationException) {
                WifiConnectResult.Failed(
                    reason = WifiConnectFailureReason.TIMEOUT,
                    message = "Timeout khi ket noi Wi-Fi.",
                )
            } else {
                WifiConnectResult.Failed(
                    reason = WifiConnectFailureReason.UNKNOWN,
                    message = throwable.message,
                )
            }
        }
    }

    private suspend fun finalizeSuccessfulWifiConnection(
        current: MainUiState,
        resolvedSsid: String,
    ): Boolean {
        val isActuallyConnected = waitForActualWifiConnection(resolvedSsid)
        if (!isActuallyConnected) {
            _state.update {
                it.copy(
                    wifiConnectionState = WifiConnectionState.Failed(
                        reason = WifiConnectFailureReason.NOT_ACTUALLY_CONNECTED,
                        message = "App da gui yeu cau ket noi nhung may chua vao duoc Wi-Fi '$resolvedSsid'.",
                    ),
                    statusMessage = "He thong chua vao duoc Wi-Fi '$resolvedSsid' tren may that. Hay kiem tra mat khau, man hinh xac nhan he thong hoac lai gan router hon.",
                )
            }
            return false
        }

        val localSavedRecord = runCatching {
            repository.saveConnectedNetworkLocal(
                baseUrl = current.baseUrl,
                ocrText = current.ocrText.ifBlank { "Connected from OCR Result" },
                ssid = resolvedSsid,
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
                    ssid = resolvedSsid,
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
                "Ket noi Wi-Fi thanh cong: $resolvedSsid. Da luu local + server."
            localSavedRecord != null && !savedToApi ->
                "Ket noi Wi-Fi thanh cong: $resolvedSsid. Da luu local, server chua san sang."
            localSavedRecord == null && savedToApi ->
                "Ket noi Wi-Fi thanh cong: $resolvedSsid. Da luu len server."
            else ->
                "Ket noi Wi-Fi thanh cong: $resolvedSsid. Chua luu duoc lich su (thu lai sau)."
        }
        _state.update {
            it.copy(
                isWifiEnabled = true,
                wifiConnectionState = WifiConnectionState.Connected(ssid = resolvedSsid),
                statusMessage = successMessage,
            )
        }
        return true
    }

    private fun publishWifiFailure(result: WifiConnectResult.Failed) {
        val uiMessage = when (result.reason) {
            WifiConnectFailureReason.WIFI_DISABLED ->
                "Wi-Fi dang tat. Hay bat Wi-Fi roi thu lai."
            WifiConnectFailureReason.PERMISSION_DENIED ->
                "Thieu quyen Wi-Fi/Location. Hay cap quyen roi thu lai."
            WifiConnectFailureReason.SSID_NOT_FOUND ->
                "Khong tim thay SSID xung quanh. Hay dung gan router hon hoac test tren may that."
            WifiConnectFailureReason.NOT_ACTUALLY_CONNECTED ->
                "May chua vao duoc Wi-Fi that du app da gui yeu cau ket noi."
            WifiConnectFailureReason.AUTHENTICATION_OR_UNAVAILABLE ->
                "Khong ket noi duoc. Co the sai mat khau hoac mang khong kha dung."
            WifiConnectFailureReason.TIMEOUT ->
                "Ket noi qua lau (timeout). Thu lai hoac dung gan router hon."
            WifiConnectFailureReason.INVALID_INPUT ->
                "Du lieu ket noi khong hop le."
            WifiConnectFailureReason.UNKNOWN ->
                result.message ?: "Khong ket noi duoc Wi-Fi."
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

    fun onWifiConnectionPermissionDenied() {
        _state.update {
            it.copy(
                wifiConnectionState = WifiConnectionState.Failed(
                    reason = WifiConnectFailureReason.PERMISSION_DENIED,
                    message = "Can quyen Location/Nearby Wi-Fi de ket noi mang that.",
                ),
                statusMessage = "Can cap quyen truoc khi ket noi Wi-Fi.",
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
                statusMessage = "Da ap dung SSID tu AI review.",
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
                statusMessage = "Da ap dung mat khau tu AI review.",
            )
        }
    }

    fun startOcrFromGallery(uri: Uri) {
        viewModelScope.launch {
            setOcrLoading("Dang xu ly anh tu thu vien...")
            runCatching {
                val bitmap = decodeBitmapFromUri(uri)
                recognizeReadableText(bitmap)
            }.onSuccess { scanResult ->
                handleOcrRecognitionSuccess(
                    source = "Thu vien anh",
                    text = scanResult.text,
                    blankMessage = "OCR khong doc duoc noi dung. Thu anh ro hon hoac doi goc chup.",
                    localCredentials = scanResult.credentials,
                )
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        scanSource = "Thu vien anh",
                        statusMessage = buildOcrFailureMessage(throwable),
                    )
                }
            }
        }
    }

    fun startQrFromGallery(uri: Uri) {
        viewModelScope.launch {
            setOcrLoading("Dang doc ma QR tu thu vien...")
            runCatching {
                val bitmap = decodeBitmapFromUri(uri)
                val image = InputImage.fromBitmap(bitmap, 0)
                val scanner = BarcodeScanning.getClient(
                    BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                        .build(),
                )
                try {
                    scanner.process(image).await()
                        .firstOrNull { it.rawValue.orEmpty().isNotBlank() }
                        ?.rawValue
                        .orEmpty()
                } finally {
                    scanner.close()
                }
            }.onSuccess { rawQrText ->
                handleOcrRecognitionSuccess(
                    source = "Thu vien QR",
                    text = rawQrText,
                    blankMessage = "Anh thu vien khong co ma QR Wi-Fi hop le. Vui long chon anh khac.",
                    preferLocalCredentials = true,
                )
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        scanSource = "Thu vien QR",
                        statusMessage = "Doc QR that bai: ${throwable.message ?: "Loi khong xac dinh"}",
                    )
                }
            }
        }
    }

    fun startOcrFromCamera(bitmap: Bitmap) {
        viewModelScope.launch {
            setOcrLoading("Dang quet OCR tu camera...")
            runCatching {
                recognizeReadableText(bitmap)
            }.onSuccess { scanResult ->
                handleOcrRecognitionSuccess(
                    source = "May quet",
                    text = scanResult.text,
                    blankMessage = "OCR khong doc duoc noi dung. Thu chup lai ro hon.",
                    localCredentials = scanResult.credentials,
                )
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        scanSource = "May quet",
                        statusMessage = buildOcrFailureMessage(throwable),
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
        localCredentials: WifiOcrCredentials? = null,
    ) {
        val hasLocalCredentials = localCredentials != null &&
            (localCredentials.ssid.isNotBlank() || localCredentials.password.isNotBlank())

        if (text.isBlank() && !hasLocalCredentials) {
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
                statusMessage = "OCR thanh cong. Dang tach text trong khung quet...",
                aiValidation = AiValidationState.Hidden,
            )
        }

        val currentBaseUrl = _state.value.baseUrl
        val resolved = resolveOcrCredentials(
            baseUrl = currentBaseUrl,
            text = text,
            preferLocalCredentials = preferLocalCredentials,
            localCredentials = localCredentials,
        )

        val saveMessage = if (resolved.parsed.ssid.orEmpty().isNotBlank()) {
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
                    "Da luu vao SQLite."
                },
                onFailure = { "Chua luu duoc SQLite: ${it.message}" },
            )
        } else {
            "Chua co du lieu de luu."
        }

        val resolvedSsid = resolved.parsed.ssid.orEmpty()
        val nearbyNetworks = if (resolvedSsid.isNotBlank()) getAvailableNearbyNetworks() else emptyList()
        val nearbyMatch = if (resolvedSsid.isNotBlank()) {
            resolveBestConnectionTarget(resolvedSsid, nearbyNetworks)
        } else {
            null
        }
        val fuzzyResolution = if (resolvedSsid.isNotBlank() && nearbyNetworks.isNotEmpty()) {
            resolveFuzzySuggestion(
                baseUrl = currentBaseUrl,
                ocrSsid = resolvedSsid,
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
        val nearbyStatus = when {
            resolvedSsid.isBlank() -> ""
            nearbyNetworks.isEmpty() -> buildNearbyWifiStatus(emptyList())
            nearbyMatch == null -> "Khong co Wi-Fi '$resolvedSsid' o gan day trong danh sach mang thuc te."
            nearbyMatch.ssid.equals(resolvedSsid, ignoreCase = true) ->
                "Da tim thay Wi-Fi '$resolvedSsid' trong khu vuc gan day."
            else ->
                "Tim thay mang gan dung nhat '${
                    nearbyMatch.ssid
                }' tu ket qua OCR '$resolvedSsid'."
        }

        _state.update {
            it.copy(
                isLoading = false,
                ssid = resolved.parsed.ssid.orEmpty(),
                password = resolved.parsed.password.orEmpty(),
                security = resolved.parsed.security.orEmpty(),
                sourceFormat = resolved.parsed.sourceFormat.orEmpty(),
                confidence = resolved.parsed.confidence,
                statusMessage = listOf(resolved.message, saveMessage, nearbyStatus)
                    .filter { it.isNotBlank() }
                    .joinToString(" "),
                aiValidation = AiValidationState.Hidden,
                ssidSuggestion = fuzzyResolution.state,
                nearbyNetworks = fuzzyResolution.nearbyNetworks.ifEmpty { nearbyNetworks },
                nearbyWifiStatus = nearbyStatus,
                wifiConnectionState = WifiConnectionState.Idle,
                isNearbyExpanded = false,
            )
        }
    }

    private suspend fun resolveOcrCredentials(
        baseUrl: String,
        text: String,
        preferLocalCredentials: Boolean = false,
        localCredentials: WifiOcrCredentials? = null,
    ): OcrCredentialResolution {
        val local = localCredentials
            ?.takeIf { it.ssid.isNotBlank() || it.password.isNotBlank() }
            ?: ocrProcessor.extractWifiCredentials(text)
        val localParsed = local.toParsedWifiData(fallbackSource = "ocr_local_hint")

        if (preferLocalCredentials && localParsed.hasAnyOcrValue()) {
            return OcrCredentialResolution(
                parsed = localParsed.copy(
                    sourceFormat = localParsed.sourceFormat.orEmpty().ifBlank { "qr_local" },
                ),
                aiData = null,
                message = "Da doc thong tin tu ma QR. Hay kiem tra roi bam Ket noi.",
            )
        }

        if (localParsed.hasAnyOcrValue()) {
            return OcrCredentialResolution(
                parsed = localParsed,
                aiData = null,
                message = when {
                    localParsed.sourceFormat.orEmpty().contains("layout") ->
                        "App da doc text OCR trong khung quet va dien vao 2 o. Hay kiem tra roi bam Ket noi."
                    localParsed.sourceFormat.orEmpty().contains("labeled") ->
                        "App da doc text OCR va tach theo nhan Name/ID/Password."
                    else ->
                        "App da doc text OCR va dien thong tin Wi-Fi tim thay."
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
            aiData = null,
            message = "OCR da doc duoc text, nhung chua tach ra ro ten Wi-Fi va mat khau. Vui long nhap thu cong.",
        )
    }

    private fun WifiOcrCredentials.toParsedWifiData(fallbackSource: String): ParsedWifiData {
        return ParsedWifiData(
            ssid = ssid,
            password = password,
            security = security,
            sourceFormat = sourceFormat.ifBlank { fallbackSource },
            confidence = confidence,
        ).normalizedOcrCandidate()
    }

    private fun ParsedWifiData.normalizedOcrCandidate(): ParsedWifiData {
        return copy(
            ssid = sanitizeOcrSsid(ssid),
            password = sanitizeOcrPassword(password),
            security = security.orEmpty().trim(),
            sourceFormat = sourceFormat.orEmpty().trim(),
            confidence = confidence?.coerceIn(0.0, 1.0),
        )
    }

    private fun ParsedWifiData.hasAnyOcrValue(): Boolean {
        return ssid.orEmpty().isNotBlank() || password.orEmpty().isNotBlank()
    }

    private fun ParsedWifiData.hasReliableOcrSsid(ocrText: String): Boolean {
        val ssidValue = ssid.orEmpty().trim()
        val passwordValue = password.orEmpty().trim()
        val source = sourceFormat.orEmpty().lowercase(Locale.ROOT)
        val candidateScore = scoreOcrCandidate(this, ocrText)

        if (ssidValue.isBlank()) return false
        if (ssidValue.length > 32) return false
        if (ssidValue.equals("wifi", ignoreCase = true)) return false
        if (looksLikeUrlValue(ssidValue)) return false
        if (looksLikeNoiseValue(ssidValue)) return false

        return when {
            "wifi_qr" in source || "qr_local" in source -> true
            "labeled" in source -> candidateScore >= 1.15
            "two_line" in source -> passwordValue.isNotBlank() && candidateScore >= 1.35
            "ai" in source || "server" in source -> {
                val confidenceValue = confidence ?: 0.0
                confidenceValue >= 0.76 &&
                    candidateScore >= 1.35 &&
                    containsOcrEvidence(ssidValue, ocrText)
            }
            "heuristic" in source -> passwordValue.isNotBlank() && candidateScore >= 1.45
            else -> passwordValue.isNotBlank() && candidateScore >= 1.45
        }
    }

    private fun ParsedWifiData.isStrongLocalOcrCandidate(ocrText: String): Boolean {
        val source = sourceFormat.orEmpty().lowercase(Locale.ROOT)
        val isLocalSource = "layout_ocr" in source ||
            "labeled" in source ||
            "two_line" in source ||
            "heuristic" in source ||
            "wifi_qr" in source
        return isLocalSource &&
            ssid.orEmpty().isNotBlank() &&
            password.orEmpty().isNotBlank() &&
            hasReliableOcrSsid(ocrText) &&
            scoreOcrCandidate(this, ocrText) >= 1.35
    }

    private fun scoreOcrCandidate(candidate: ParsedWifiData, ocrText: String): Double {
        val ssidValue = candidate.ssid.orEmpty().trim()
        val passwordValue = candidate.password.orEmpty().trim()
        val source = candidate.sourceFormat.orEmpty().lowercase(Locale.ROOT)
        val normalizedText = ocrText.lowercase(Locale.ROOT)

        var score = candidate.confidence ?: when {
            "wifi_qr" in source -> 0.98
            "labeled" in source -> 0.86
            "two_line" in source -> 0.8
            "server" in source -> 0.78
            "ai" in source -> 0.76
            "heuristic" in source -> 0.68
            else -> 0.55
        }

        if (ssidValue.isNotBlank()) score += 0.35 else score -= 0.4
        if (passwordValue.isNotBlank()) score += 0.42

        if (ssidValue.length in 1..32) score += 0.08 else if (ssidValue.length > 32) score -= 0.35
        if (passwordValue.length in 8..63) score += 0.14 else if (passwordValue.length > 63) score -= 0.35
        if (passwordValue.contains(' ')) score -= 0.08
        if (passwordValue.equals(ssidValue, ignoreCase = true)) score -= 0.25
        if (looksLikeNoiseValue(ssidValue)) score -= 0.3
        if (looksLikeUrlValue(passwordValue)) score -= 0.45
        if (isLikelyPasswordValue(ssidValue)) score -= 0.2

        if (ssidValue.isNotBlank() && normalizedText.contains(ssidValue.lowercase(Locale.ROOT))) score += 0.05
        if (passwordValue.isNotBlank() && normalizedText.contains(passwordValue.lowercase(Locale.ROOT))) score += 0.05

        if ("wifi_qr" in source) score += 0.2
        if ("labeled" in source) score += 0.08
        if ("server" in source) score += 0.05
        if ("heuristic" in source) score -= 0.03

        return score
    }

    private fun sanitizeOcrSsid(value: String?): String {
        val sanitized = value.orEmpty()
            .replace(
                Regex(
                    """^(ssid|wifi(?:\s*name)?|wifi\s*id|id|network\s*name|ten\s*wifi|ten\s*mang)\s*[:=-]\s*""",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            )
            .replace(Regex("\\s{2,}"), " ")
            .trim()
            .trim('"', '\'', '`')
            .trim()
        if (sanitized.length > 32) return ""
        if (sanitized.equals("wifi", ignoreCase = true)) return ""
        if (looksLikeUrlValue(sanitized)) return ""
        if (looksLikeNoiseValue(sanitized)) return ""
        return sanitized
    }

    private fun sanitizeOcrPassword(value: String?): String {
        return value.orEmpty()
            .replace(
                Regex(
                    """^(password|pass|pass\s*word|pwd|mat\s*khau|mk)\s*[:=-]\s*""",
                    RegexOption.IGNORE_CASE,
                ),
                "",
            )
            .replace(Regex("\\s{2,}"), " ")
            .trim()
            .trim('"', '\'', '`')
            .trim()
    }

    private fun looksLikeUrlValue(value: String): Boolean {
        return value.isNotBlank() && Regex("""(https?://|www\.|\.com\b|\.net\b|\.org\b)""", RegexOption.IGNORE_CASE)
            .containsMatchIn(value)
    }

    private fun looksLikeNoiseValue(value: String): Boolean {
        return value.isNotBlank() && Regex(
            """(\bfree\s*wifi\b|\bmien\s*phi\b|\bhotline\b|\bemail\b|\busername\b|\bdang\s*nhap\b|\blogin\b|\bwelcome\b|\bxin\s*chao\b|\bcam\s*on\b|\bscan\b|\bqr\b|\bmenu\b|\binternet\b|\bconnected\b|\brouter\b|\bsecurity\b)""",
            RegexOption.IGNORE_CASE,
        ).containsMatchIn(value)
    }

    private fun containsOcrEvidence(value: String, ocrText: String): Boolean {
        val normalizedValue = value.lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .trim()
        val normalizedText = ocrText.lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .trim()
        val compactValue = normalizedValue.replace(Regex("[^a-z0-9]"), "")
        val compactText = normalizedText.replace(Regex("[^a-z0-9]"), "")
        return normalizedValue.isNotBlank() &&
            (normalizedText.contains(normalizedValue) ||
                compactValue.length >= 3 && compactText.contains(compactValue))
    }

    private suspend fun recognizeReadableText(bitmap: Bitmap): WifiOcrScanResult {
        val quality = ocrProcessor.assessImageQuality(bitmap)
        if (!quality.isReadable) {
            throw OcrImageQualityException(quality.message)
        }
        return ocrProcessor.recognizeWifiText(bitmap)
    }

    private fun buildOcrFailureMessage(throwable: Throwable): String {
        return if (throwable is OcrImageQualityException) {
            throwable.message ?: "Khong quet duoc: anh khong du ro. Vui long nhap thu cong."
        } else {
            "OCR that bai: ${throwable.message ?: "Loi khong xac dinh"}"
        }
    }

    private fun logOcrResolution(
        rawText: String,
        local: ParsedWifiData?,
        server: ParsedWifiData?,
        ai: ParsedWifiData?,
        chosen: ParsedWifiData?,
    ) {
        if (!BuildConfig.DEBUG) return
        Log.d(
            OCR_LOG_TAG,
            buildString {
                appendLine("raw=${rawText.take(500).replace("\n", " | ")}")
                appendLine("local=${local?.toOcrLogLine()}")
                appendLine("server=${server?.toOcrLogLine()}")
                appendLine("ai=${ai?.toOcrLogLine()}")
                append("chosen=${chosen?.toOcrLogLine()}")
            },
        )
    }

    private fun ParsedWifiData.toOcrLogLine(): String {
        return "ssid='${ssid.orEmpty()}', password='${password.orEmpty()}', source='${sourceFormat.orEmpty()}', confidence=${confidence}"
    }

    private fun isLikelyPasswordValue(value: String): Boolean {
        if (value.isBlank()) return false
        if (looksLikeUrlValue(value)) return false
        if (value.length !in 8..63) return false
        return value.any(Char::isLetter) || value.any(Char::isDigit)
    }

    fun onImageSelectionCanceled() {
        _state.update {
            it.copy(statusMessage = "Ban chua chon anh nao.")
        }
    }

    fun onCameraPreviewUnavailable() {
        _state.update {
            it.copy(statusMessage = "Camera chua san sang, vui long thu lai sau vai giay.")
        }
    }

    fun checkHealth() {
        viewModelScope.launch {
            setLoading(true, "Dang kiem tra ket noi server...")
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
                        statusMessage = "Khong ket noi duoc server: ${throwable.message}",
                    )
                }
            }
        }
    }

    fun parseCurrentText() {
        val text = _state.value.ocrText.trim()
        if (text.isEmpty()) {
            _state.update { it.copy(statusMessage = "Ban chua co text OCR de parse") }
            return
        }

        viewModelScope.launch {
            setLoading(true, "Dang parse thong tin WiFi...")
            try {
                val currentState = _state.value
                val parseEnvelope = repository.parseOcr(currentState.baseUrl, text)
                if (!parseEnvelope.ok || parseEnvelope.data == null) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = parseEnvelope.error ?: "Khong parse duoc du lieu WiFi",
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
                        statusMessage = "Parse thanh cong. Dang danh gia AI...",
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
                        "Da luu vao SQLite."
                    },
                    onFailure = { "Khong luu duoc SQLite: ${it.message}" },
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
                        statusMessage = "Loi parse: ${throwable.message}",
                        aiValidation = AiValidationState.Failed(
                            throwable.message ?: "Khong danh gia duoc AI",
                        ),
                    )
                }
            }
        }
    }

    fun consumeRecognizedText(text: String) {
        viewModelScope.launch {
            setOcrLoading("Dang doc thong tin tu ma QR...")
            handleOcrRecognitionSuccess(
                source = "Quet QR",
                text = text,
                blankMessage = "QR khong co thong tin Wi-Fi hop le. Vui long quet lai.",
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
                statusMessage = "Da nhan thong tin Wi-Fi tu link chia se. Hay kiem tra roi bam Ket noi.",
                isLoading = false,
                aiValidation = AiValidationState.Hidden,
                ssidSuggestion = SsidSuggestionState.Hidden,
                nearbyNetworks = emptyList(),
                nearbyWifiStatus = "",
                wifiConnectionState = WifiConnectionState.Idle,
                isNearbyExpanded = false,
                sharedLinkRequestId = it.sharedLinkRequestId + 1L,
            )
        }
    }

    fun refreshHistory() {
        viewModelScope.launch {
            runCatching {
                repository.getSavedWifiHistory()
            }.onSuccess { records ->
                _state.update { it.copy(historyRecords = records) }
            }
        }
    }

    fun refreshNearbyWifiNetworks(recalculateFuzzy: Boolean = false) {
        viewModelScope.launch {
            val wifiEnabled = isWifiEnabledInSystem()
            val scannedNetworks = getScannedNearbyNetworks()
            val previousNetworks = _state.value.nearbyNetworks
            val networks = when {
                !wifiEnabled -> emptyList()
                scannedNetworks.isNotEmpty() -> scannedNetworks
                else -> previousNetworks
            }
            val nearbyStatus = when {
                !wifiEnabled -> "Wi-Fi dang tat. Hay bat Wi-Fi de quet va ket noi."
                scannedNetworks.isNotEmpty() -> buildNearbyWifiStatus(scannedNetworks)
                previousNetworks.isNotEmpty() -> "Khong cap nhat duoc danh sach moi, dang hien du lieu scan gan nhat."
                else -> buildNearbyWifiStatus(emptyList())
            }
            _state.update {
                it.copy(
                    isWifiEnabled = wifiEnabled,
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
                    val updated = it.copy(
                        ssidSuggestion = fuzzy.state,
                        nearbyNetworks = fuzzy.nearbyNetworks.ifEmpty { networks },
                    )
                    maybeAutoApplyFuzzyNetwork(updated, fuzzy)
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
            val savedWifi = repository.getLatestSavedWifi() ?: return@launch
            val savedFuzzyMatch = savedWifi.fuzzyBestMatch
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
                    nearbyNetworks = getAvailableNearbyNetworks(),
                    nearbyWifiStatus = buildNearbyWifiStatus(getScannedNearbyNetworks()),
                    wifiConnectionState = WifiConnectionState.Idle,
                    statusMessage = "Da tai du lieu WiFi gan nhat tu SQLite",
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
                "Da tai ket qua AI gan nhat tu SQLite."
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
                    throwable.message ?: "AI validate khong ket noi duoc",
                ),
                persisted = null,
            )
        }

        if (!envelope.ok || envelope.data == null) {
            return AiResolution(
                uiState = AiValidationState.Failed(
                    envelope.error ?: "AI validate khong tra ve du lieu hop le",
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
                    statusMessage = "Da cap nhat SSID thanh '${suggestion.bestMatch}'",
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
                statusMessage = "Da chon mang '$ssid'",
            )
        }
    }

    /**
     * Fallback local khi BE fuzzy endpoint chua san sang.
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
    private fun getScannedNearbyNetworks(): List<NearbyNetwork> {
        deps?.scannedNearbyNetworks?.let { return it.invoke() }
        val app = getApplication<Application>().applicationContext
        if (!hasNearbyWifiPermission()) return emptyList()

        val wifiManager = app.getSystemService(WifiManager::class.java) ?: return emptyList()
        if (!wifiManager.isWifiEnabled) return emptyList()
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
                            rssiDbm = result.level.takeIf { it in -127..0 },
                            frequencyMhz = result.frequency.takeIf { it > 0 },
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
        if (!isWifiEnabledInSystem()) {
            return "Wi-Fi dang tat. Hay bat Wi-Fi de app tiep tuc quet va ket noi."
        }
        if (isRunningOnEmulator()) {
            return "Dang chay emulator: scan/ket noi Wi-Fi that se bi gioi han. Hay test tren may Android that."
        }
        return if (scannedNetworks.isNotEmpty()) {
            "Da tim thay ${scannedNetworks.size} mang Wi-Fi xung quanh."
        } else {
            "Chua lay duoc mang Wi-Fi thuc te. Hay cap quyen Vi tri/Wi-Fi nearby va bat Location tren may."
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

    private fun isWifiEnabledInSystem(): Boolean {
        val app = getApplication<Application>().applicationContext
        val wifiManager = app.getSystemService(WifiManager::class.java) ?: return false
        return wifiManager.isWifiEnabled
    }

    fun refreshWifiEnvironment(recalculateFuzzy: Boolean = false) {
        refreshNearbyWifiNetworks(recalculateFuzzy)
    }

    private fun maybeAutoApplyFuzzyNetwork(
        state: MainUiState,
        fuzzy: FuzzyResolution,
    ): MainUiState {
        val found = fuzzy.state as? SsidSuggestionState.Found ?: return state
        val currentSsid = state.ssid.trim()
        if (currentSsid.isBlank()) return state
        if (found.bestMatch.equals(currentSsid, ignoreCase = true)) return state
        if (found.score < 0.68) return state
        return state.copy(
            ssid = found.bestMatch,
            statusMessage = "Da doi sang mang Wi-Fi gan dung nhat '${found.bestMatch}' tu ket qua OCR.",
        )
    }

    private fun resolveBestConnectionTarget(
        ocrSsid: String,
        nearbyNetworks: List<NearbyNetwork>,
    ): ResolvedNetworkMatch? {
        if (ocrSsid.isBlank()) return null
        if (nearbyNetworks.isEmpty()) {
            return ResolvedNetworkMatch(
                ssid = ocrSsid,
                signalLevel = 0,
                score = 1.0,
            )
        }

        nearbyNetworks.firstOrNull { it.ssid.equals(ocrSsid, ignoreCase = true) }?.let { exact ->
            return ResolvedNetworkMatch(
                ssid = exact.ssid,
                signalLevel = exact.signalLevel,
                score = 1.0,
            )
        }

        return findBestNetworkMatch(ocrSsid, nearbyNetworks)
    }

    private fun findBestNetworkMatch(
        ocrSsid: String,
        networks: List<NearbyNetwork>,
    ): ResolvedNetworkMatch? {
        if (ocrSsid.isBlank() || networks.isEmpty()) return null

        val normalizedOcr = normalizeSsidForMatch(ocrSsid)
        var best: ResolvedNetworkMatch? = null

        for (network in networks) {
            val normalizedNearby = normalizeSsidForMatch(network.ssid)
            val compactOcr = normalizedOcr.replace(" ", "")
            val compactNearby = normalizedNearby.replace(" ", "")
            val charScore = similarityScore(compactOcr, compactNearby)
            val tokenScore = tokenOverlapScore(normalizedOcr, normalizedNearby)
            val containsBonus = when {
                compactNearby.contains(compactOcr) || compactOcr.contains(compactNearby) -> 0.12
                else -> 0.0
            }
            val signalBonus = ((network.signalLevel.coerceIn(1, 4) - 1) / 3.0) * 0.12
            val score = (charScore * 0.58) + (tokenScore * 0.30) + containsBonus + signalBonus

            if (best == null || score > best.score || (score == best.score && network.signalLevel > best.signalLevel)) {
                best = ResolvedNetworkMatch(
                    ssid = network.ssid,
                    signalLevel = network.signalLevel,
                    score = score.coerceAtMost(1.0),
                )
            }
        }

        return best?.takeIf { it.score >= 0.58 }
    }

    private fun normalizeSsidForMatch(value: String): String {
        return value
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun tokenOverlapScore(a: String, b: String): Double {
        val tokensA = a.split(' ').filter { it.isNotBlank() }.toSet()
        val tokensB = b.split(' ').filter { it.isNotBlank() }.toSet()
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0
        val common = tokensA.intersect(tokensB).size.toDouble()
        return common / maxOf(tokensA.size, tokensB.size).toDouble()
    }

    private suspend fun waitForActualWifiConnection(
        targetSsid: String,
        timeoutMs: Long = 12_000L,
    ): Boolean {
        val start = currentTimeMillis()
        while (currentTimeMillis() - start < timeoutMs) {
            if (getCurrentConnectedSsid()?.equals(targetSsid, ignoreCase = true) == true) {
                return true
            }
            delay(500L)
        }
        return false
    }

    // ── End Fuzzy SSID Match ────────────────────────────────

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

private data class ResolvedNetworkMatch(
    val ssid: String,
    val signalLevel: Int,
    val score: Double,
)

private data class OcrCredentialResolution(
    val parsed: ParsedWifiData,
    val aiData: AiValidateData?,
    val message: String,
)

private class OcrImageQualityException(message: String) : Exception(message)

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
    val isWifiEnabled: Boolean = true,
    val ocrText: String = "",
    val ssid: String = "",
    val password: String = "",
    val security: String = "",
    val sourceFormat: String = "",
    val confidence: Double? = null,
    val scanSource: String = "",
    val statusMessage: String = "San sang quet OCR WiFi",
    val isLoading: Boolean = false,
    val aiValidation: AiValidationState = AiValidationState.Hidden,
    val ssidSuggestion: SsidSuggestionState = SsidSuggestionState.Hidden,
    val nearbyNetworks: List<NearbyNetwork> = emptyList(),
    val nearbyWifiStatus: String = "",
    val wifiConnectionState: WifiConnectionState = WifiConnectionState.Idle,
    val isNearbyExpanded: Boolean = false,
    val sharedLinkRequestId: Long = 0L,
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

data class NearbyNetwork(
    val ssid: String,
    val signalLevel: Int,
    val rssiDbm: Int? = null,
    val frequencyMhz: Int? = null,
)

private fun SavedWifiRecord.toNetworkDetailUiModel(
    origin: NetworkDetailOrigin,
    isConnected: Boolean,
    scannedNearby: NearbyNetwork?,
): NetworkDetailUiModel {
    val dbm = scannedNearby?.rssiDbm
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
        frequencyLabel = inferFrequencyLabel(frequencyMhz = scannedNearby?.frequencyMhz),
        signalDbm = dbm,
        signalQualityLabel = inferSignalQualityLabel(dbm),
        usageTotalLabel = if (isConnected) "Dang do..." else "Chua do duoc",
        usageHighlightLabel = "",
        usageBars = emptyList(),
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
    scannedNearby: NearbyNetwork?,
): NetworkDetailUiModel {
    val dbm = scannedNearby?.rssiDbm
    return NetworkDetailUiModel(
        ssid = name,
        lastConnectedLabel = lastConnectedLabel,
        protocolLabel = inferSecurityLabel(
            ssid = name,
            password = "",
            sourceFormat = type.name.lowercase(Locale.ROOT),
            fallbackOpen = type == RecentNetworkType.BUILDING,
        ),
        frequencyLabel = inferFrequencyLabel(frequencyMhz = scannedNearby?.frequencyMhz),
        signalDbm = dbm,
        signalQualityLabel = inferSignalQualityLabel(dbm),
        usageTotalLabel = if (isConnected) "Dang do..." else "Chua do duoc",
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

private fun inferFrequencyLabel(frequencyMhz: Int?): String {
    return when {
        frequencyMhz != null && frequencyMhz >= 5000 -> String.format(Locale.US, "%.1f GHz", frequencyMhz / 1000f)
        frequencyMhz != null -> String.format(Locale.US, "%.1f GHz", frequencyMhz / 1000f)
        else -> "Chua do duoc"
    }
}

private fun inferSignalQualityLabel(dbm: Int?): String {
    return when {
        dbm == null -> "Chua do duoc"
        dbm >= -50 -> "Tuyệt vời"
        dbm >= -60 -> "Rất tốt"
        dbm >= -70 -> "Tốt"
        dbm >= -80 -> "Khá yếu"
        else -> "Yếu"
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
