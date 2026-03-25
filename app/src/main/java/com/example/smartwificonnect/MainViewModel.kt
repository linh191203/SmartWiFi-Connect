package com.example.smartwificonnect

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smartwificonnect.data.DefaultWifiRepository
import com.example.smartwificonnect.data.WifiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: WifiRepository = DefaultWifiRepository(application.applicationContext)
    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    init {
        loadLatestSavedWifi()
    }

    fun onBaseUrlChanged(value: String) {
        _state.update { it.copy(baseUrl = value) }
    }

    fun onOcrTextChanged(value: String) {
        _state.update { it.copy(ocrText = value) }
    }

    fun onSsidChanged(value: String) {
        _state.update { it.copy(ssid = value) }
    }

    fun onPasswordChanged(value: String) {
        _state.update { it.copy(password = value) }
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
                        sourceFormat = result.sourceFormat.orEmpty(),
                        confidence = result.confidence,
                        statusMessage = "Parse thanh cong. $saveMessage",
                    )
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

    private fun loadLatestSavedWifi() {
        viewModelScope.launch {
            val savedWifi = repository.getLatestSavedWifi() ?: return@launch
            _state.update {
                it.copy(
                    baseUrl = savedWifi.baseUrl,
                    ocrText = savedWifi.ocrText,
                    ssid = savedWifi.ssid,
                    password = savedWifi.password,
                    sourceFormat = savedWifi.sourceFormat,
                    confidence = savedWifi.confidence,
                    statusMessage = "Da tai du lieu WiFi gan nhat tu SQLite",
                )
            }
        }
    }
}

data class MainUiState(
    val baseUrl: String = BuildConfig.API_BASE_URL,
    val ocrText: String = "",
    val ssid: String = "",
    val password: String = "",
    val sourceFormat: String = "",
    val confidence: Double? = null,
    val statusMessage: String = "San sang quet OCR WiFi",
    val isLoading: Boolean = false,
)
