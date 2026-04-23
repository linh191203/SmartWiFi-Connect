package com.example.smartwificonnect

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartwificonnect.data.DefaultWifiRepository
import com.example.smartwificonnect.data.WifiConnectionManager
import com.example.smartwificonnect.data.WifiRepository
import com.example.smartwificonnect.data.local.AppLanguage
import com.example.smartwificonnect.data.local.AppPreferences
import com.example.smartwificonnect.data.local.AppPreferencesManager
import com.example.smartwificonnect.R
import com.example.smartwificonnect.feature.home.HomePreviewData
import com.example.smartwificonnect.feature.home.HomeUiState
import com.example.smartwificonnect.feature.home.NetworkQualityUiModel
import com.example.smartwificonnect.feature.home.RecentNetworkType
import com.example.smartwificonnect.feature.home.RecentNetworkUiModel
import com.example.smartwificonnect.feature.home.SmartInsightType
import com.example.smartwificonnect.feature.home.SmartInsightUiModel
import com.example.smartwificonnect.feature.history.HistoryNetworkUiModel
import com.example.smartwificonnect.feature.history.HistoryUiState
import com.example.smartwificonnect.feature.settings.LanguageOptionUiModel
import com.example.smartwificonnect.feature.settings.SettingsUiState
import com.example.smartwificonnect.ocr.WifiOcrProcessor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: WifiRepository = DefaultWifiRepository(application.applicationContext)
    private val wifiConnectionManager = WifiConnectionManager(application.applicationContext)
    private val preferencesManager = AppPreferencesManager(application.applicationContext)
    private val ocrProcessor = WifiOcrProcessor()
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()
    val languageOptions: List<LanguageOptionUiModel> = AppLanguage.entries.map { language ->
        LanguageOptionUiModel(
            code = language.code,
            titleRes = language.titleRes,
            subtitleRes = language.helperRes,
        )
    }

    // Mock danh sách mạng gần đây (sau này thay bằng Wi-Fi scan thật)
    private val mockNearbyNetworks = listOf(
        NearbyNetwork("Cafe_WiFi_5G", signalLevel = 4),
        NearbyNetwork("Hieu_Mobile_4G", signalLevel = 3),
        NearbyNetwork("Family_Connect", signalLevel = 3),
        NearbyNetwork("Cafe_Visitor", signalLevel = 2),
        NearbyNetwork("Public_Guest", signalLevel = 1),
    )

    init {
        loadPreferences()
        loadLatestSavedWifi()
        refreshSavedNetworksSummary()
        refreshHistoryNetworks()
        refreshHomeNearbyNetworks(hasPermission = hasWifiScanPermission())
    }

    fun refreshHomeNearbyNetworks(hasPermission: Boolean = hasWifiScanPermission()) {
        val connectivityStatus = if (isInternetAvailable()) {
            getString(R.string.home_connectivity_online)
        } else {
            getString(R.string.home_connectivity_offline)
        }

        if (!hasPermission) {
            _state.update {
                it.copy(
                    homeState = it.homeState.copy(
                        connectivityStatus = getString(R.string.home_connectivity_need_permission),
                        recentNetworks = emptyList(),
                        smartInsightsTitle = getString(R.string.home_smart_insights_title),
                        smartInsights = emptyList(),
                        isLoading = false,
                    ),
                )
            }
            return
        }

        val appContext = getApplication<Application>().applicationContext
        val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

        if (wifiManager == null) {
            _state.update {
                it.copy(
                    homeState = it.homeState.copy(
                        connectivityStatus = getString(R.string.home_connectivity_no_wifi_manager),
                        recentNetworks = emptyList(),
                        smartInsightsTitle = getString(R.string.home_smart_insights_title),
                        smartInsights = emptyList(),
                        isLoading = false,
                    ),
                )
            }
            return
        }

        val scanNetworks = runCatching {
            wifiManager.scanResults
                .orEmpty()
                .asSequence()
                .filter { scan -> scan.SSID.isNotBlank() }
                .distinctBy { scan -> scan.SSID }
                .sortedByDescending { scan -> scan.level }
                .take(8)
                .toList()
        }.getOrElse { emptyList() }

        val networks = scanNetworks
            .map { scan ->
                RecentNetworkUiModel(
                    name = scan.SSID,
                    lastConnectedLabel = getString(R.string.home_nearby_signal_format, signalLabel(scan.level)),
                    type = classifyNetworkType(scan.capabilities),
                )
            }

        val smartInsights = buildSmartInsights(scanNetworks)

        _state.update {
            it.copy(
                homeState = it.homeState.copy(
                    connectivityStatus = connectivityStatus,
                    recentNetworks = networks,
                    smartInsightsTitle = getString(R.string.home_smart_insights_title),
                    smartInsights = smartInsights,
                    isLoading = false,
                ),
            )
        }
    }

    private fun buildSmartInsights(scanNetworks: List<ScanResult>): List<SmartInsightUiModel> {
        if (scanNetworks.isEmpty()) return emptyList()

        val candidates = scanNetworks.map { scan ->
            val speedScore = signalScore(scan.level)
            val securityScore = securityScore(scan.capabilities)
            InsightCandidate(
                ssid = scan.SSID,
                speedScore = speedScore,
                securityScore = securityScore,
            )
        }

        val speedDetails = buildNetworkDetails(candidates) { it.speedScore }
        val securityDetails = buildNetworkDetails(candidates) { it.securityScore }

        val topSpeed = speedDetails.firstOrNull()
        val topSecurity = securityDetails.firstOrNull()

        return listOf(
            SmartInsightUiModel(
                title = getString(R.string.home_insight_speed_title),
                summary = if (topSpeed != null) {
                    getString(R.string.home_insight_speed_summary, topSpeed.ssid, topSpeed.comparisonPercent)
                } else {
                    getString(R.string.home_insight_speed_empty)
                },
                type = SmartInsightType.SPEED,
                networkDetails = speedDetails,
            ),
            SmartInsightUiModel(
                title = getString(R.string.home_insight_security_title),
                summary = if (topSecurity != null) {
                    getString(R.string.home_insight_security_summary, topSecurity.ssid, topSecurity.comparisonPercent)
                } else {
                    getString(R.string.home_insight_security_empty)
                },
                type = SmartInsightType.SECURITY,
                networkDetails = securityDetails,
            ),
        )
    }

    private fun buildNetworkDetails(
        candidates: List<InsightCandidate>,
        selector: (InsightCandidate) -> Int,
    ): List<NetworkQualityUiModel> {
        return candidates
            .map { candidate ->
                val score = selector(candidate)
                val others = candidates.filterNot { it.ssid == candidate.ssid }
                val averageOther = if (others.isEmpty()) {
                    score.toDouble()
                } else {
                    others.map { selector(it) }.average()
                }
                val deltaPercent = if (averageOther <= 0.0) {
                    0
                } else {
                    (((score - averageOther) / averageOther) * 100).roundToInt()
                }

                NetworkQualityUiModel(
                    ssid = candidate.ssid,
                    qualityScore = score,
                    comparisonPercent = deltaPercent,
                )
            }
            .sortedByDescending { it.qualityScore }
            .take(5)
    }

    private fun signalScore(level: Int): Int {
        val normalized = (level + 100).coerceIn(0, 70)
        return ((normalized / 70.0) * 100).roundToInt()
    }

    private fun securityScore(capabilities: String): Int {
        return when {
            capabilities.contains("WPA3", ignoreCase = true) -> 95
            capabilities.contains("WPA2", ignoreCase = true) -> 84
            capabilities.contains("WPA", ignoreCase = true) -> 70
            capabilities.contains("WEP", ignoreCase = true) -> 62
            else -> 45
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
            )
        }
    }

    fun onSsidChanged(value: String) {
        _state.update { it.copy(ssid = value) }
    }

    fun onPasswordChanged(value: String) {
        _state.update { it.copy(password = value) }
    }

    fun onSavePasswordOnDeviceChanged(value: Boolean) {
        _state.update { it.copy(savePasswordOnDevice = value) }
    }

    fun onSettingsUserNameChanged(value: String) {
        _state.update {
            it.copy(
                settingsState = it.settingsState.copy(userName = value),
            )
        }
    }

    fun onSettingsEmailChanged(value: String) {
        _state.update {
            it.copy(
                settingsState = it.settingsState.copy(email = value),
            )
        }
    }

    fun onSettingsLanguageChanged(languageCode: String) {
        _state.update {
            it.copy(
                settingsState = it.settingsState.copy(selectedLanguageCode = languageCode),
            )
        }
    }

    fun onSettingsAutoSavePasswordsChanged(value: Boolean) {
        _state.update {
            it.copy(
                savePasswordOnDevice = value,
                settingsState = it.settingsState.copy(autoSavePasswords = value),
            )
        }
    }

    fun saveSettings() {
        val settings = _state.value.settingsState
        val preferences = AppPreferences(
            userName = settings.userName.ifBlank { "Smart User" },
            email = settings.email.ifBlank { "smart.user@wifi-connect.app" },
            languageCode = settings.selectedLanguageCode,
            autoSavePasswords = settings.autoSavePasswords,
        )
        preferencesManager.savePreferences(preferences)
        preferencesManager.applyLanguage(preferences.languageCode)

        _state.update {
            it.copy(
                savePasswordOnDevice = preferences.autoSavePasswords,
                homeState = localizedHomeState(
                    current = it.homeState,
                    greeting = buildGreeting(
                        userName = preferences.userName,
                        languageCode = preferences.languageCode,
                    ),
                ),
                settingsState = it.settingsState.copy(
                    userName = preferences.userName,
                    email = preferences.email,
                    selectedLanguageCode = preferences.languageCode,
                    autoSavePasswords = preferences.autoSavePasswords,
                ),
                statusMessage = buildSettingsSavedMessage(preferences.languageCode),
            )
        }
    }

    fun logout() {
        val selectedLanguageCode = _state.value.settingsState.selectedLanguageCode
        _state.update {
            it.copy(
                ocrText = "",
                ssid = "",
                password = "",
                security = "",
                sourceFormat = "",
                confidence = null,
                scanSource = "",
                isLoading = false,
                ssidSuggestion = SsidSuggestionState.Hidden,
                nearbyNetworks = emptyList(),
                isNearbyExpanded = false,
                savePasswordOnDevice = it.settingsState.autoSavePasswords,
                statusMessage = buildLogoutMessage(selectedLanguageCode),
            )
        }
    }

    fun connectCurrentWifi() {
        val currentState = _state.value
        val ssid = currentState.ssid.trim()
        if (ssid.isEmpty()) {
            _state.update { it.copy(statusMessage = getString(R.string.status_connect_missing_ssid)) }
            return
        }
        if (requiresPassword(currentState.security) && currentState.password.isBlank()) {
            _state.update { it.copy(statusMessage = getString(R.string.status_connect_missing_password)) }
            return
        }

        viewModelScope.launch {
            setLoading(true, getString(R.string.status_connect_loading, ssid))
            val connectResult = wifiConnectionManager.connectToWifi(
                ssid = ssid,
                password = currentState.password,
                security = currentState.security,
                saveNetwork = currentState.savePasswordOnDevice,
            )

            if (!connectResult.success) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = connectResult.message,
                    )
                }
                return@launch
            }

            val localSaveMessage = runCatching {
                repository.saveConnectedWifi(
                    ssid = ssid,
                    password = currentState.password,
                    security = currentState.security.ifBlank { "Unknown" },
                    sourceFormat = currentState.sourceFormat.ifBlank { "manual_or_ocr" },
                    savePassword = currentState.savePasswordOnDevice,
                )
            }.fold(
                onSuccess = {
                    if (currentState.savePasswordOnDevice && currentState.password.isNotBlank()) {
                        getString(R.string.status_connect_local_saved)
                    } else {
                        getString(R.string.status_connect_local_name_only)
                    }
                },
                onFailure = {
                    getString(
                        R.string.status_connect_local_save_failed,
                        it.message ?: getString(R.string.status_unknown_error),
                    )
                },
            )

            refreshSavedNetworksSummary()
            refreshHistoryNetworks()
            _state.update {
                it.copy(
                    isLoading = false,
                    statusMessage = "${connectResult.message} $localSaveMessage",
                )
            }
        }
    }

    fun deleteHistoryNetwork(id: Long) {
        viewModelScope.launch {
            val deleted = repository.deleteSavedNetworkById(id)
            refreshSavedNetworksSummary()
            refreshHistoryNetworks()
            _state.update {
                it.copy(
                    statusMessage = if (deleted) {
                        getString(R.string.status_history_delete_success)
                    } else {
                        getString(R.string.status_history_delete_failed)
                    },
                )
            }
        }
    }

    fun clearAllSavedNetworksHistory() {
        viewModelScope.launch {
            val deletedCount = repository.clearSavedNetworks()
            refreshSavedNetworksSummary()
            refreshHistoryNetworks()
            _state.update {
                it.copy(
                    statusMessage = if (deletedCount > 0) {
                        getString(R.string.status_history_clear_success, deletedCount)
                    } else {
                        getString(R.string.status_history_clear_empty)
                    },
                )
            }
        }
    }

    fun startOcrFromGallery(uri: Uri) {
        viewModelScope.launch {
            setOcrLoading(getString(R.string.status_ocr_gallery_loading))
            runCatching {
                val bitmap = decodeBitmapFromUri(uri)
                ocrProcessor.recognizeText(bitmap)
            }.onSuccess { text ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        scanSource = getString(R.string.status_ocr_source_gallery),
                        ocrText = text,
                        statusMessage = if (text.isBlank()) {
                            getString(R.string.status_ocr_gallery_empty)
                        } else {
                            getString(R.string.status_ocr_success_edit)
                        },
                    )
                }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        scanSource = getString(R.string.status_ocr_source_gallery),
                        statusMessage = getString(
                            R.string.status_ocr_failed,
                            throwable.message ?: getString(R.string.status_unknown_error),
                        ),
                    )
                }
            }
        }
    }

    fun startOcrFromCamera(bitmap: Bitmap) {
        viewModelScope.launch {
            setOcrLoading(getString(R.string.status_ocr_camera_loading))
            runCatching {
                ocrProcessor.recognizeText(bitmap)
            }.onSuccess { text ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        scanSource = getString(R.string.status_ocr_source_camera),
                        ocrText = text,
                        statusMessage = if (text.isBlank()) {
                            getString(R.string.status_ocr_camera_empty)
                        } else {
                            getString(R.string.status_ocr_success_edit)
                        },
                    )
                }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        scanSource = getString(R.string.status_ocr_source_camera),
                        statusMessage = getString(
                            R.string.status_ocr_failed,
                            throwable.message ?: getString(R.string.status_unknown_error),
                        ),
                    )
                }
            }
        }
    }

    fun onImageSelectionCanceled() {
        _state.update {
            it.copy(statusMessage = getString(R.string.status_image_not_selected))
        }
    }

    fun checkHealth() {
        viewModelScope.launch {
            setLoading(true, getString(R.string.status_server_check_loading))
            runCatching {
                repository.checkHealth(_state.value.baseUrl)
            }.onSuccess { health ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = getString(
                            R.string.status_server_ok,
                            health.service,
                            health.uptimeSeconds,
                        ),
                    )
                }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = getString(
                            R.string.status_server_failed,
                            throwable.message ?: getString(R.string.status_unknown_error),
                        ),
                    )
                }
            }
        }
    }

    fun parseCurrentText() {
        val text = _state.value.ocrText.trim()
        if (text.isEmpty()) {
            _state.update { it.copy(statusMessage = getString(R.string.status_parse_text_missing)) }
            return
        }

        viewModelScope.launch {
            setLoading(true, getString(R.string.status_parse_loading))
            try {
                val currentState = _state.value
                val envelope = repository.parseOcr(currentState.baseUrl, text)
                if (!envelope.ok || envelope.data == null) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = envelope.error ?: getString(R.string.status_parse_failed),
                        )
                    }
                    return@launch
                }

                val result = envelope.data
                val saveMessage = runCatching {
                    repository.saveParsedWifi(currentState.baseUrl, text, result)
                }.fold(
                    onSuccess = { getString(R.string.status_sqlite_saved) },
                    onFailure = {
                        getString(
                            R.string.status_sqlite_save_failed,
                            it.message ?: getString(R.string.status_unknown_error),
                        )
                    },
                )

                _state.update {
                    it.copy(
                        isLoading = false,
                        ssid = result.ssid.orEmpty(),
                        password = result.password.orEmpty(),
                        security = result.security.orEmpty(),
                        sourceFormat = result.sourceFormat.orEmpty(),
                        confidence = result.confidence,
                        statusMessage = getString(R.string.status_parse_success, saveMessage),
                        ssidSuggestion = SsidSuggestionState.Hidden,
                    )
                }

                // Tự động trigger fuzzy match sau khi parse thành công
                if (result.ssid?.isNotBlank() == true) {
                    triggerFuzzyMatch()
                }
            } catch (throwable: Throwable) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = getString(
                            R.string.status_parse_error,
                            throwable.message ?: getString(R.string.status_unknown_error),
                        ),
                    )
                }
            }
        }
    }

    fun consumeRecognizedText(text: String) {
        _state.update {
            it.copy(
                ocrText = text,
                statusMessage = if (text.isBlank()) {
                    getString(R.string.status_ocr_no_text_detected)
                } else {
                    getString(R.string.status_ocr_received_ready)
                },
            )
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

    private fun hasWifiScanPermission(): Boolean {
        val appContext = getApplication<Application>().applicationContext
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

    private fun isInternetAvailable(): Boolean {
        val appContext = getApplication<Application>().applicationContext
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun signalLabel(level: Int): String {
        return when {
            level >= -55 -> getString(R.string.signal_very_strong)
            level >= -65 -> getString(R.string.signal_strong)
            level >= -75 -> getString(R.string.signal_medium)
            level >= -85 -> getString(R.string.signal_weak)
            else -> getString(R.string.signal_very_weak)
        }
    }

    private fun classifyNetworkType(capabilities: String): RecentNetworkType {
        return when {
            capabilities.contains("WPA3", ignoreCase = true) -> RecentNetworkType.ROUTER
            capabilities.contains("WPA", ignoreCase = true) ||
                capabilities.contains("WEP", ignoreCase = true) -> RecentNetworkType.WIFI
            else -> RecentNetworkType.BUILDING
        }
    }

    private fun loadPreferences() {
        val preferences = preferencesManager.loadPreferences()
        preferencesManager.applyLanguage(preferences.languageCode)
        _state.update {
            it.copy(
                savePasswordOnDevice = preferences.autoSavePasswords,
                homeState = localizedHomeState(
                    current = it.homeState,
                    greeting = buildGreeting(preferences.userName, preferences.languageCode),
                ),
                settingsState = it.settingsState.copy(
                    userName = preferences.userName,
                    email = preferences.email,
                    selectedLanguageCode = preferences.languageCode,
                    autoSavePasswords = preferences.autoSavePasswords,
                ),
            )
        }
    }

    private fun buildGreeting(userName: String, languageCode: String): String {
        val firstName = userName.trim().split(" ").lastOrNull().orEmpty().ifBlank { "User" }
        return if (languageCode == AppLanguage.EN.code) {
            "Hello, $firstName!"
        } else {
            "Xin chào, $firstName!"
        }
    }

    private fun buildSettingsSavedMessage(languageCode: String): String {
        return if (languageCode == AppLanguage.EN.code) {
            "Settings saved successfully."
        } else {
            "Đã lưu cài đặt thành công."
        }
    }

    private fun buildLogoutMessage(languageCode: String): String {
        return if (languageCode == AppLanguage.EN.code) {
            "You have been logged out."
        } else {
            "Bạn đã đăng xuất khỏi ứng dụng."
        }
    }

    private fun requiresPassword(security: String): Boolean {
        if (security.isBlank()) return false
        return security.contains("WPA", ignoreCase = true) ||
            security.contains("WEP", ignoreCase = true)
    }

    private fun loadLatestSavedWifi() {
        viewModelScope.launch {
            val savedWifi = repository.getLatestSavedWifi() ?: return@launch
            _state.update {
                it.copy(
                    baseUrl = savedWifi.baseUrl,
                    ocrText = savedWifi.ocrText,
                    ssid = savedWifi.ssid,
                    password = savedWifi.password,
                    security = "",
                    sourceFormat = savedWifi.sourceFormat,
                    confidence = savedWifi.confidence,
                    statusMessage = getString(R.string.status_latest_wifi_loaded),
                )
            }
        }
    }

    private fun refreshSavedNetworksSummary() {
        viewModelScope.launch {
            val summary = repository.getSavedNetworksSummary()
            _state.update {
                it.copy(
                    homeState = it.homeState.copy(
                        savedNetworksCount = summary.count.toString(),
                        usageLabel = getString(R.string.home_latest_network_label),
                        usageValue = summary.latestSsid ?: getString(R.string.home_latest_network_none),
                    ),
                    settingsState = it.settingsState.copy(
                        savedNetworksCount = summary.count.toString(),
                        latestSavedNetworkName = summary.latestSsid ?: getString(R.string.home_latest_network_none),
                    ),
                )
            }
        }
    }

    private fun refreshHistoryNetworks() {
        viewModelScope.launch {
            val networks = repository.getSavedNetworks().map { record ->
                HistoryNetworkUiModel(
                    id = record.id,
                    ssid = record.ssid,
                    security = record.security,
                    password = record.password,
                    passwordSaved = record.passwordSaved,
                    lastConnectedAtMillis = record.lastConnectedAtMillis,
                )
            }
            _state.update {
                it.copy(
                    historyState = HistoryUiState(networks = networks),
                )
            }
        }
    }

    private fun localizedHomeState(current: HomeUiState, greeting: String): HomeUiState {
        return current.copy(
            greeting = greeting,
            quickConnectTitle = getString(R.string.home_quick_connect_title),
            quickConnectSubtitle = getString(R.string.home_quick_connect_subtitle),
            quickConnectCta = getString(R.string.home_quick_connect_cta),
            cameraTitle = getString(R.string.home_camera_title),
            cameraSubtitle = getString(R.string.home_camera_subtitle),
            shortcutItems = listOf(
                com.example.smartwificonnect.feature.home.HomeShortcutUiModel(
                    title = getString(R.string.home_shortcut_qr_title),
                    subtitle = getString(R.string.home_shortcut_qr_subtitle),
                    type = com.example.smartwificonnect.feature.home.HomeShortcutType.QR,
                ),
                com.example.smartwificonnect.feature.home.HomeShortcutUiModel(
                    title = getString(R.string.home_shortcut_image_title),
                    subtitle = getString(R.string.home_shortcut_image_subtitle),
                    type = com.example.smartwificonnect.feature.home.HomeShortcutType.IMAGE,
                ),
            ),
            recentNetworksTitle = getString(R.string.home_recent_networks_title),
            savedNetworksLabel = getString(R.string.home_saved_networks_label),
            usageLabel = getString(R.string.home_latest_network_label),
            usageValue = current.usageValue.ifBlank { getString(R.string.home_latest_network_none) },
            smartInsightsTitle = getString(R.string.home_smart_insights_title),
        )
    }

    private fun getString(@StringRes resId: Int, vararg formatArgs: Any): String {
        return getApplication<Application>().getString(resId, *formatArgs)
    }

    // ── Fuzzy SSID Match ────────────────────────────────────

    fun triggerFuzzyMatch() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    ssidSuggestion = SsidSuggestionState.Loading,
                    nearbyNetworks = mockNearbyNetworks,
                )
            }

            // Giả lập delay mạng (sau này thay bằng gọi BE)
            delay(1200L)

            val ocrSsid = _state.value.ssid
            val bestMatch = findBestMatch(ocrSsid, mockNearbyNetworks)

            _state.update {
                it.copy(
                    ssidSuggestion = bestMatch
                        ?: SsidSuggestionState.NotFound,
                )
            }
        }
    }

    fun acceptSsidSuggestion() {
        val suggestion = _state.value.ssidSuggestion
        if (suggestion is SsidSuggestionState.Found) {
            _state.update {
                it.copy(
                    ssid = suggestion.bestMatch,
                    ssidSuggestion = SsidSuggestionState.Hidden,
                    statusMessage = getString(R.string.status_fuzzy_ssid_updated, suggestion.bestMatch),
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
                statusMessage = getString(R.string.status_network_selected, ssid),
            )
        }
    }

    /**
     * Mock fuzzy match đơn giản dùng Levenshtein-like similarity.
     * Sau này thay bằng gọi BE endpoint /api/v1/ssid/fuzzy-match (Fuse.js).
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

        // Chỉ trả gợi ý nếu score >= 60% và tên không giống hệt
        if (bestNetwork == null || bestScore < 0.6) return null
        if (bestNetwork.ssid == ocrSsid) return null

        return SsidSuggestionState.Found(
            bestMatch = bestNetwork.ssid,
            score = bestScore,
        )
    }

    /** Tính độ tương đồng chuỗi đơn giản (dựa trên common subsequence ratio). */
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

    // ── End Fuzzy SSID Match ────────────────────────────────

    override fun onCleared() {
        ocrProcessor.release()
        super.onCleared()
    }
}

private data class InsightCandidate(
    val ssid: String,
    val speedScore: Int,
    val securityScore: Int,
)

data class MainUiState(
    val baseUrl: String = BuildConfig.API_BASE_URL,
    val ocrText: String = "",
    val ssid: String = "",
    val password: String = "",
    val security: String = "",
    val sourceFormat: String = "",
    val confidence: Double? = null,
    val scanSource: String = "",
    val statusMessage: String = "San sang quet OCR WiFi",
    val isLoading: Boolean = false,
    val savePasswordOnDevice: Boolean = false,
    // Fuzzy SSID match
    val ssidSuggestion: SsidSuggestionState = SsidSuggestionState.Hidden,
    val nearbyNetworks: List<NearbyNetwork> = emptyList(),
    val isNearbyExpanded: Boolean = false,
    val homeState: HomeUiState = HomePreviewData.default,
    val historyState: HistoryUiState = HistoryUiState(),
    val settingsState: SettingsUiState = SettingsUiState(),
)

sealed class SsidSuggestionState {
    object Hidden : SsidSuggestionState()
    object Loading : SsidSuggestionState()
    data class Found(val bestMatch: String, val score: Double) : SsidSuggestionState()
    object NotFound : SsidSuggestionState()
}

data class NearbyNetwork(val ssid: String, val signalLevel: Int)
