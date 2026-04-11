package com.example.smartwificonnect

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartwificonnect.data.DefaultWifiRepository
import com.example.smartwificonnect.data.WifiRepository
import com.example.smartwificonnect.ocr.WifiOcrProcessor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: WifiRepository = DefaultWifiRepository(application.applicationContext)
    private val ocrProcessor = WifiOcrProcessor()
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    // Mock danh sách mạng gần đây (sau này thay bằng Wi-Fi scan thật)
    private val mockNearbyNetworks = listOf(
        NearbyNetwork("Cafe_WiFi_5G", signalLevel = 4),
        NearbyNetwork("Hieu_Mobile_4G", signalLevel = 3),
        NearbyNetwork("Family_Connect", signalLevel = 3),
        NearbyNetwork("Cafe_Visitor", signalLevel = 2),
        NearbyNetwork("Public_Guest", signalLevel = 1),
    )

    init {
        loadLatestSavedWifi()
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

    fun startOcrFromGallery(uri: Uri) {
        viewModelScope.launch {
            setOcrLoading("Dang xu ly anh tu thu vien...")
            runCatching {
                val bitmap = decodeBitmapFromUri(uri)
                ocrProcessor.recognizeText(bitmap)
            }.onSuccess { text ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        scanSource = "Thu vien anh",
                        ocrText = text,
                        statusMessage = if (text.isBlank()) {
                            "OCR khong doc duoc noi dung. Thu anh ro hon hoac doi goc chup."
                        } else {
                            "OCR thanh cong. Ban co the sua text truoc khi parse server."
                        },
                    )
                }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        scanSource = "Thu vien anh",
                        statusMessage = "OCR that bai: ${throwable.message ?: "Loi khong xac dinh"}",
                    )
                }
            }
        }
    }

    fun startOcrFromCamera(bitmap: Bitmap) {
        viewModelScope.launch {
            setOcrLoading("Dang quet OCR tu camera...")
            runCatching {
                ocrProcessor.recognizeText(bitmap)
            }.onSuccess { text ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        scanSource = "May quet",
                        ocrText = text,
                        statusMessage = if (text.isBlank()) {
                            "OCR khong doc duoc noi dung. Thu chup lai ro hon."
                        } else {
                            "OCR thanh cong. Ban co the sua text truoc khi parse server."
                        },
                    )
                }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        scanSource = "May quet",
                        statusMessage = "OCR that bai: ${throwable.message ?: "Loi khong xac dinh"}",
                    )
                }
            }
        }
    }

    fun onImageSelectionCanceled() {
        _state.update {
            it.copy(statusMessage = "Ban chua chon anh nao.")
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
                val envelope = repository.parseOcr(currentState.baseUrl, text)
                if (!envelope.ok || envelope.data == null) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = envelope.error ?: "Khong parse duoc du lieu WiFi",
                        )
                    }
                    return@launch
                }

                val result = envelope.data
                val saveMessage = runCatching {
                    repository.saveParsedWifi(currentState.baseUrl, text, result)
                }.fold(
                    onSuccess = { "Da luu vao SQLite." },
                    onFailure = { "Khong luu duoc SQLite: ${it.message}" },
                )

                _state.update {
                    it.copy(
                        isLoading = false,
                        ssid = result.ssid.orEmpty(),
                        password = result.password.orEmpty(),
                        security = result.security.orEmpty(),
                        sourceFormat = result.sourceFormat.orEmpty(),
                        confidence = result.confidence,
                        statusMessage = "Parse thanh cong. $saveMessage",
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
                        statusMessage = "Loi parse: ${throwable.message}",
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
                    "OCR khong nhan duoc ky tu nao, vui long thu lai"
                } else {
                    "Da nhan OCR, bam Parse de trich xuat WiFi"
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
                    statusMessage = "Da tai du lieu WiFi gan nhat tu SQLite",
                )
            }
        }
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
                statusMessage = "Da chon mang '$ssid'",
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
    // Fuzzy SSID match
    val ssidSuggestion: SsidSuggestionState = SsidSuggestionState.Hidden,
    val nearbyNetworks: List<NearbyNetwork> = emptyList(),
    val isNearbyExpanded: Boolean = false,
)

sealed class SsidSuggestionState {
    object Hidden : SsidSuggestionState()
    object Loading : SsidSuggestionState()
    data class Found(val bestMatch: String, val score: Double) : SsidSuggestionState()
    object NotFound : SsidSuggestionState()
}

data class NearbyNetwork(val ssid: String, val signalLevel: Int)
