package com.example.smartwificonnect.data

import android.content.Context
import com.example.smartwificonnect.BuildConfig
import com.example.smartwificonnect.data.local.SavedWifiRecord
import com.example.smartwificonnect.data.local.SavedWifiRecordDraft
import com.example.smartwificonnect.data.local.room.SmartWiFiDatabase
import com.example.smartwificonnect.data.local.room.toEntity
import com.example.smartwificonnect.data.local.room.toDomain

class DefaultWifiRepository(context: Context) : WifiRepository {
    private val wifiDao = SmartWiFiDatabase.getInstance(context.applicationContext).savedWifiDao()

    override suspend fun checkHealth(baseUrl: String): HealthData {
        return ApiClient.getService(baseUrl, enableDebugLogs = BuildConfig.DEBUG).getHealth()
    }

    override suspend fun parseOcr(baseUrl: String, ocrText: String): ApiEnvelope<ParsedWifiData> {
        return ApiClient.getService(baseUrl, enableDebugLogs = BuildConfig.DEBUG)
            .parseOcr(OcrParseRequest(ocrText = ocrText))
    }

    override suspend fun validateAi(
        baseUrl: String,
        ssid: String?,
        password: String?,
        ocrText: String,
    ): ApiEnvelope<AiValidateData> {
        return ApiClient.getService(baseUrl, enableDebugLogs = BuildConfig.DEBUG)
            .validateAi(
                AiValidateRequest(
                    ssid = ssid,
                    password = password,
                    ocrText = ocrText,
                ),
            )
    }

    override suspend fun fuzzyMatchSsid(
        baseUrl: String,
        ocrSsid: String,
        nearbyNetworks: List<FuzzyNetworkPayload>,
    ): ApiEnvelope<SsidFuzzyMatchData> {
        return ApiClient.getService(baseUrl, enableDebugLogs = BuildConfig.DEBUG)
            .fuzzyMatchSsid(
                SsidFuzzyMatchRequest(
                    ocrSsid = ocrSsid,
                    nearbyNetworks = nearbyNetworks,
                ),
            )
    }

    override suspend fun saveParsedWifi(
        baseUrl: String,
        ocrText: String,
        parsedWifiData: ParsedWifiData,
        aiValidateData: AiValidateData?,
        fuzzyBestMatch: String?,
        fuzzyScore: Double?,
    ): SavedWifiRecord {
        val draft = SavedWifiRecordDraft(
            baseUrl = baseUrl.trim(),
            ocrText = ocrText,
            ssid = parsedWifiData.ssid.orEmpty(),
            password = parsedWifiData.password.orEmpty(),
            sourceFormat = parsedWifiData.sourceFormat.orEmpty(),
            confidence = parsedWifiData.confidence,
            aiConfidence = aiValidateData?.confidence,
            aiSuggestion = aiValidateData?.suggestion.orEmpty(),
            aiRecommendation = aiValidateData?.parseRecommendation.orEmpty(),
            aiShouldAutoConnect = aiValidateData?.shouldAutoConnect == true,
            aiFlags = aiValidateData?.flags.orEmpty(),
            fuzzyBestMatch = fuzzyBestMatch,
            fuzzyScore = fuzzyScore,
        )
        val createdAtMillis = System.currentTimeMillis()
        val entity = draft.toEntity(createdAtMillis)
        val id = wifiDao.insert(entity)
        return entity.copy(id = id).toDomain()
    }

    override suspend fun saveConnectedNetwork(
        baseUrl: String,
        request: SaveNetworkRequest,
    ): Boolean {
        val response = ApiClient.getService(baseUrl, enableDebugLogs = BuildConfig.DEBUG)
            .saveNetwork(request)
        return response.ok && response.data != null
    }

    override suspend fun saveConnectedNetworkLocal(
        baseUrl: String,
        ocrText: String,
        ssid: String,
        password: String?,
        sourceFormat: String?,
        confidence: Double?,
    ): SavedWifiRecord {
        val draft = SavedWifiRecordDraft(
            baseUrl = baseUrl.trim(),
            ocrText = ocrText,
            ssid = ssid,
            password = password.orEmpty(),
            sourceFormat = sourceFormat.orEmpty().ifBlank { "connected_manual" },
            confidence = confidence,
            aiConfidence = null,
            aiSuggestion = "",
            aiRecommendation = "",
            aiShouldAutoConnect = false,
            aiFlags = emptyList(),
            fuzzyBestMatch = null,
            fuzzyScore = null,
        )
        val createdAtMillis = System.currentTimeMillis()
        val entity = draft.toEntity(createdAtMillis)
        val id = wifiDao.insert(entity)
        return entity.copy(id = id).toDomain()
    }

    override suspend fun getLatestSavedWifi(): SavedWifiRecord? {
        return wifiDao.getLatest()?.toDomain()
    }

    override suspend fun getSavedWifiHistory(): List<SavedWifiRecord> {
        return wifiDao.getAll().map { it.toDomain() }
    }

    override suspend fun deleteSavedWifiRecord(id: Long): Boolean {
        return wifiDao.deleteById(id) > 0
    }

    override suspend fun clearSavedWifiHistory(): Int {
        return wifiDao.deleteAll()
    }
}
