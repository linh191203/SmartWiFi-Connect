package com.example.smartwificonnect.data

import com.example.smartwificonnect.data.local.SavedConnectedWifiRecord
import com.example.smartwificonnect.data.local.SavedWifiRecord

data class SavedNetworksSummary(
    val count: Int,
    val latestSsid: String? = null,
)

interface WifiRepository {
    suspend fun checkHealth(baseUrl: String): HealthData
    suspend fun parseOcr(baseUrl: String, ocrText: String): ApiEnvelope<ParsedWifiData>
    suspend fun saveParsedWifi(
        baseUrl: String,
        ocrText: String,
        parsedWifiData: ParsedWifiData,
    ): SavedWifiRecord
    suspend fun getLatestSavedWifi(): SavedWifiRecord?
    suspend fun saveConnectedWifi(
        ssid: String,
        password: String,
        security: String,
        sourceFormat: String,
        savePassword: Boolean,
    ): SavedConnectedWifiRecord
    suspend fun getSavedNetworks(): List<SavedConnectedWifiRecord>
    suspend fun deleteSavedNetworkById(id: Long): Boolean
    suspend fun clearSavedNetworks(): Int
    suspend fun getSavedNetworksSummary(): SavedNetworksSummary
}
