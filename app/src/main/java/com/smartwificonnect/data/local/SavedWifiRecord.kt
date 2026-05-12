package com.smartwificonnect.data.local

data class SavedWifiRecordDraft(
    val baseUrl: String,
    val ocrText: String,
    val ssid: String,
    val password: String,
    val sourceFormat: String,
    val confidence: Double?,
    val aiConfidence: Double?,
    val aiSuggestion: String,
    val aiRecommendation: String,
    val aiShouldAutoConnect: Boolean,
    val aiFlags: List<String>,
    val fuzzyBestMatch: String?,
    val fuzzyScore: Double?,
)

data class SavedWifiRecord(
    val id: Long,
    val baseUrl: String,
    val ocrText: String,
    val ssid: String,
    val password: String,
    val sourceFormat: String,
    val confidence: Double?,
    val aiConfidence: Double?,
    val aiSuggestion: String,
    val aiRecommendation: String,
    val aiShouldAutoConnect: Boolean,
    val aiFlags: List<String>,
    val fuzzyBestMatch: String?,
    val fuzzyScore: Double?,
    val createdAtMillis: Long,
)
