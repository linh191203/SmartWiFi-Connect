package com.example.smartwificonnect.feature.history

data class HistoryNetworkUiModel(
    val id: Long,
    val ssid: String,
    val security: String,
    val password: String?,
    val passwordSaved: Boolean,
    val lastConnectedAtMillis: Long,
)

data class HistoryUiState(
    val isLoading: Boolean = false,
    val networks: List<HistoryNetworkUiModel> = emptyList(),
)
