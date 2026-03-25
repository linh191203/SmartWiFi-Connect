package com.example.smartwificonnect.data

import android.content.Context
import com.example.smartwificonnect.BuildConfig
import com.example.smartwificonnect.data.local.SavedWifiRecord
import com.example.smartwificonnect.data.local.SavedWifiRecordDraft
import com.example.smartwificonnect.data.local.WifiHistoryDbHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DefaultWifiRepository(context: Context) : WifiRepository {
    private val dbHelper = WifiHistoryDbHelper(context.applicationContext)

    override suspend fun checkHealth(baseUrl: String): HealthData {
        return ApiClient.getService(baseUrl, enableDebugLogs = BuildConfig.DEBUG).getHealth()
    }

    override suspend fun parseOcr(baseUrl: String, ocrText: String): ApiEnvelope<ParsedWifiData> {
        return ApiClient.getService(baseUrl, enableDebugLogs = BuildConfig.DEBUG)
            .parseOcr(OcrParseRequest(ocrText = ocrText))
    }

    override suspend fun saveParsedWifi(
        baseUrl: String,
        ocrText: String,
        parsedWifiData: ParsedWifiData,
    ): SavedWifiRecord = withContext(Dispatchers.IO) {
        dbHelper.insert(
            SavedWifiRecordDraft(
                baseUrl = baseUrl.trim(),
                ocrText = ocrText,
                ssid = parsedWifiData.ssid.orEmpty(),
                password = parsedWifiData.password.orEmpty(),
                sourceFormat = parsedWifiData.sourceFormat.orEmpty(),
                confidence = parsedWifiData.confidence,
            ),
        )
    }

    override suspend fun getLatestSavedWifi(): SavedWifiRecord? = withContext(Dispatchers.IO) {
        dbHelper.getLatest()
    }
}
