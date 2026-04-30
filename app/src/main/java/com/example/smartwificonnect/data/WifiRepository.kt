package com.example.smartwificonnect.data

import com.example.smartwificonnect.data.local.SavedWifiRecord

interface WifiRepository {
    suspend fun checkHealth(baseUrl: String): HealthData
    suspend fun parseOcr(baseUrl: String, ocrText: String): ApiEnvelope<ParsedWifiData>
    suspend fun validateAi(
        baseUrl: String,
        ssid: String?,
        password: String?,
        ocrText: String,
    ): ApiEnvelope<AiValidateData>
    suspend fun fuzzyMatchSsid(
        baseUrl: String,
        ocrSsid: String,
        nearbyNetworks: List<FuzzyNetworkPayload>,
    ): ApiEnvelope<SsidFuzzyMatchData>
    suspend fun saveParsedWifi(
        baseUrl: String,
        ocrText: String,
        parsedWifiData: ParsedWifiData,
        aiValidateData: AiValidateData? = null,
        fuzzyBestMatch: String? = null,
        fuzzyScore: Double? = null,
    ): SavedWifiRecord
    suspend fun saveConnectedNetwork(
        baseUrl: String,
        request: SaveNetworkRequest,
    ): Boolean
    suspend fun saveConnectedNetworkLocal(
        baseUrl: String,
        ocrText: String,
        ssid: String,
        password: String?,
        sourceFormat: String?,
        confidence: Double?,
    ): SavedWifiRecord
    suspend fun getLatestSavedWifi(): SavedWifiRecord?
    suspend fun getSavedWifiHistory(): List<SavedWifiRecord>
    suspend fun deleteSavedWifiRecord(id: Long): Boolean
    suspend fun clearSavedWifiHistory(): Int
}
