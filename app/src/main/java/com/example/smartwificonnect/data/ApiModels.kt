package com.example.smartwificonnect.data

data class OcrParseRequest(
    val ocrText: String,
)

data class AiValidateRequest(
    val ssid: String? = null,
    val password: String? = null,
    val ocrText: String? = null,
)

data class AiValidateData(
    val validated: Boolean,
    val confidence: Double,
    val suggestion: String,
    val flags: List<String> = emptyList(),
    val normalizedSsid: String? = null,
    val normalizedPassword: String? = null,
    val parseRecommendation: String = "review",
    val shouldAutoConnect: Boolean = false,
)

data class FuzzyNetworkPayload(
    val ssid: String,
    val signalLevel: Int = 0,
)

data class SsidFuzzyMatchRequest(
    val ocrSsid: String,
    val nearbyNetworks: List<FuzzyNetworkPayload>,
)

data class SsidFuzzyMatchItem(
    val ssid: String,
    val signalLevel: Int? = null,
    val score: Double? = null,
)

data class SsidFuzzyMatchData(
    val ocrSsid: String? = null,
    val bestMatch: String? = null,
    val score: Double? = null,
    val matches: List<SsidFuzzyMatchItem> = emptyList(),
)

data class ParsedWifiData(
    val ssid: String? = null,
    val password: String? = null,
    val security: String? = null,
    val sourceFormat: String? = null,
    val confidence: Double? = null,
    val passwordOnly: Boolean? = null,
)

data class SaveNetworkRequest(
    val ssid: String,
    val password: String? = null,
    val security: String? = null,
    val sourceFormat: String? = null,
    val confidence: Double? = null,
    val connectedAtEpochMs: Long,
)

data class SaveNetworkResponse(
    val id: String,
    val ssid: String,
    val security: String? = null,
    val sourceFormat: String? = null,
    val confidence: Double? = null,
    val connectedAtEpochMs: Long,
    val savedAtEpochMs: Long,
)

data class NetworkListData(
    val records: List<SaveNetworkResponse> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val limit: Int = 20,
)

data class ApiEnvelope<T>(
    val ok: Boolean,
    val data: T? = null,
    val error: String? = null,
)

data class HealthData(
    val ok: Boolean,
    val service: String,
    val uptimeSeconds: Long,
    val timestamp: String,
)
