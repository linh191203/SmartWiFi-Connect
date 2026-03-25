package com.example.smartwificonnect.data

import com.example.smartwificonnect.data.local.SavedWifiRecord

interface WifiRepository {
    suspend fun checkHealth(baseUrl: String): HealthData
    suspend fun parseOcr(baseUrl: String, ocrText: String): ApiEnvelope<ParsedWifiData>
    suspend fun saveParsedWifi(
        baseUrl: String,
        ocrText: String,
        parsedWifiData: ParsedWifiData,
    ): SavedWifiRecord
    suspend fun getLatestSavedWifi(): SavedWifiRecord?
}
