package com.example.smartwificonnect.data

data class OcrParseRequest(
    val ocrText: String,
)

data class ParsedWifiData(
    val ssid: String? = null,
    val password: String? = null,
    val security: String? = null,
    val sourceFormat: String? = null,
    val confidence: Double? = null,
    val passwordOnly: Boolean? = null,
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
