package com.smartwificonnect

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import com.smartwificonnect.data.AiValidateData
import com.smartwificonnect.data.ApiEnvelope
import com.smartwificonnect.data.FuzzyNetworkPayload
import com.smartwificonnect.data.HealthData
import com.smartwificonnect.data.ParsedWifiData
import com.smartwificonnect.data.SaveNetworkRequest
import com.smartwificonnect.data.SsidFuzzyMatchData
import com.smartwificonnect.data.WifiRepository
import com.smartwificonnect.data.local.SavedWifiRecord
import com.smartwificonnect.ocr.WifiOcrCredentials
import com.smartwificonnect.ocr.WifiOcrEngine
import com.smartwificonnect.ocr.WifiOcrRecognitionResult
import com.smartwificonnect.wifi.WifiConnectFailureReason
import com.smartwificonnect.wifi.WifiConnectResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelOcrFlowTest {

    @Test
    fun startOcrFromCamera_highConfidenceLocalResult_usesLocalConfidentFlow() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = buildRepositoryMock()
            val ocrProcessor = FakeOcrEngine()
            val bitmap = mockk<Bitmap>()

            ocrProcessor.recognitionResult = WifiOcrRecognitionResult(
                text = "WIFI : CameraNet\nPASS : Cam12345",
                credentials = WifiOcrCredentials(
                    ssid = "CameraNet",
                    password = "Cam12345",
                ),
                confidence = 0.91,
            )

            val viewModel = buildViewModel(repository, ocrProcessor)
            advanceUntilIdle()

            viewModel.startOcrFromCamera(bitmap)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals("WIFI : CameraNet\nPASS : Cam12345", state.ocrText)
            assertEquals("CameraNet", state.ssid)
            assertEquals("Cam12345", state.password)
            assertEquals("ocr_local_confident", state.sourceFormat)
            assertEquals(0.91, state.confidence ?: 0.0, 0.0001)
            assertFalse(state.isLoading)
            assertTrue(state.aiValidation is AiValidationState.Hidden)

            assertEquals(0, repository.validateAiCalls)
            assertEquals(0, repository.parseOcrCalls)
            assertTrue(repository.savedParsedRequests.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun startOcrFromCamera_highConfidencePasswordOnlyResult_requiresReviewWithSsid() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = buildRepositoryMock()
            val ocrProcessor = FakeOcrEngine()
            val bitmap = mockk<Bitmap>()

            ocrProcessor.recognitionResult = WifiOcrRecognitionResult(
                text = "68 x4",
                credentials = WifiOcrCredentials(
                    ssid = "",
                    password = "68686868",
                ),
                confidence = 0.91,
            )

            val viewModel = buildViewModel(repository, ocrProcessor)
            advanceUntilIdle()

            viewModel.startOcrFromCamera(bitmap)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals("", state.ssid)
            assertEquals("68686868", state.password)
            assertEquals("ocr_local_review", state.sourceFormat)
            assertEquals(0.91, state.confidence ?: 0.0, 0.0001)
            assertTrue(state.statusMessage.contains("SSID"))
            assertEquals(0, repository.validateAiCalls)
            assertEquals(0, repository.parseOcrCalls)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun startOcrFromCamera_lowConfidenceLocalResult_fallsBackToReviewFlow() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = buildRepositoryMock()
            val ocrProcessor = FakeOcrEngine()
            val bitmap = mockk<Bitmap>()

            ocrProcessor.recognitionResult = WifiOcrRecognitionResult(
                text = "WiFi Name: ReviewNet\nPassword: Review123",
                credentials = WifiOcrCredentials(
                    ssid = "ReviewNet",
                    password = "Review123",
                ),
                confidence = 0.55,
            )
            repository.validateAiResponse = ApiEnvelope(
                ok = true,
                data = AiValidateData(
                    validated = false,
                    confidence = 0.41,
                    suggestion = "Review manually",
                ),
            )
            repository.parseOcrResponse = ApiEnvelope(
                ok = false,
                error = "No parse",
            )

            val viewModel = buildViewModel(repository, ocrProcessor)
            advanceUntilIdle()

            viewModel.startOcrFromCamera(bitmap)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals("ReviewNet", state.ssid)
            assertEquals("Review123", state.password)
            assertEquals("ocr_local_review", state.sourceFormat)
            assertEquals(0.55, state.confidence ?: 0.0, 0.0001)
            assertFalse(state.isLoading)
            assertTrue(state.aiValidation is AiValidationState.Ready)

            assertEquals(1, repository.validateAiCalls)
            assertEquals(0, repository.parseOcrCalls)
            assertTrue(repository.savedParsedRequests.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun startOcrFromCamera_lowConfidenceLocalResult_keepsLocalCredentialsWhenAiDisagrees() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = buildRepositoryMock()
            val ocrProcessor = FakeOcrEngine()
            val bitmap = mockk<Bitmap>()

            ocrProcessor.recognitionResult = WifiOcrRecognitionResult(
                text = "ID DONG PHUC LINH TRAN\nlinhtran103",
                credentials = WifiOcrCredentials(
                    ssid = "DONG PHUC LINH TRAN",
                    password = "linhtran103",
                ),
                confidence = 0.58,
            )
            repository.validateAiResponse = ApiEnvelope(
                ok = true,
                data = AiValidateData(
                    validated = false,
                    confidence = 0.33,
                    suggestion = "AI mismatch",
                    normalizedSsid = "DONG PHUC LINH TRAN",
                    normalizedPassword = "olinhtran103",
                ),
            )
            repository.parseOcrResponse = ApiEnvelope(
                ok = true,
                data = ParsedWifiData(
                    ssid = "DONG PHUC LINH TRAN",
                    password = "olinhtran103",
                    sourceFormat = "ocr_server",
                    confidence = 0.22,
                ),
            )

            val viewModel = buildViewModel(repository, ocrProcessor)
            advanceUntilIdle()

            viewModel.startOcrFromCamera(bitmap)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals("DONG PHUC LINH TRAN", state.ssid)
            assertEquals("linhtran103", state.password)
            assertEquals("ocr_local_review", state.sourceFormat)
            assertEquals(0.58, state.confidence ?: 0.0, 0.0001)
            assertTrue(state.aiValidation is AiValidationState.Ready)

            assertEquals(1, repository.validateAiCalls)
            assertEquals(0, repository.parseOcrCalls)
            assertTrue(repository.savedParsedRequests.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun startOcrFromCamera_localMissingPassword_usesAiPasswordAsFallback() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = buildRepositoryMock()
            val ocrProcessor = FakeOcrEngine()
            val bitmap = mockk<Bitmap>()

            ocrProcessor.recognitionResult = WifiOcrRecognitionResult(
                text = "WiFi Name: Cafe MOC",
                credentials = WifiOcrCredentials(
                    ssid = "Cafe MOC",
                    password = "",
                ),
                confidence = 0.54,
            )
            repository.validateAiResponse = ApiEnvelope(
                ok = true,
                data = AiValidateData(
                    validated = true,
                    confidence = 0.81,
                    suggestion = "Filled missing password",
                    normalizedPassword = "Cf222222",
                ),
            )
            repository.parseOcrResponse = ApiEnvelope(
                ok = false,
                error = "unused",
            )

            val viewModel = buildViewModel(repository, ocrProcessor)
            advanceUntilIdle()

            viewModel.startOcrFromCamera(bitmap)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals("Cafe MOC", state.ssid)
            assertEquals("Cf222222", state.password)
            assertEquals("ai_ocr", state.sourceFormat)
            assertEquals(0.81, state.confidence ?: 0.0, 0.0001)
            assertTrue(state.aiValidation is AiValidationState.Ready)

            assertEquals(1, repository.validateAiCalls)
            assertEquals(0, repository.parseOcrCalls)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun consumeRecognizedText_wifiQrPayload_prefersQrLocalFlow() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = buildRepositoryMock()
            val ocrProcessor = FakeOcrEngine()
            val qrText = "WIFI:T:WPA2;S:QrNet;P:QrPass123;;"

            ocrProcessor.extractedCredentials[qrText] = WifiOcrCredentials(
                ssid = "QrNet",
                password = "QrPass123",
            )

            val viewModel = buildViewModel(repository, ocrProcessor)
            advanceUntilIdle()

            viewModel.consumeRecognizedText(qrText)
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(qrText, state.ocrText)
            assertEquals("QrNet", state.ssid)
            assertEquals("QrPass123", state.password)
            assertEquals("qr_local", state.sourceFormat)
            assertNull(state.confidence)
            assertFalse(state.isLoading)
            assertTrue(state.aiValidation is AiValidationState.Hidden)

            assertEquals(0, repository.validateAiCalls)
            assertEquals(0, repository.parseOcrCalls)
            assertTrue(repository.savedParsedRequests.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun connectToParsedWifi_ssidMissingFromNearbyStillAttemptsDirectConnection() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = buildRepositoryMock()
            val ocrProcessor = FakeOcrEngine()
            val connectCalls = mutableListOf<Triple<String, String?, String?>>()

            val viewModel = buildViewModel(
                repository = repository,
                ocrProcessor = ocrProcessor,
                hasNearbyWifiPermission = { true },
                scannedNearbyNetworks = {
                    listOf(
                        NearbyNetwork(
                            ssid = "OtherCafeWifi",
                            signalLevel = 4,
                        ),
                    )
                },
                connectWifi = { ssid, password, security ->
                    connectCalls += Triple(ssid, password, security)
                    WifiConnectResult.Success(ssid = ssid)
                },
            )
            advanceUntilIdle()

            viewModel.onSsidChanged("CafeNet")
            viewModel.onPasswordChanged("Secret123")
            viewModel.connectToParsedWifi()
            advanceUntilIdle()

            assertEquals(1, connectCalls.size)
            assertEquals(Triple("CafeNet", "Secret123", null), connectCalls.single())

            val state = viewModel.state.value
            assertTrue(state.wifiConnectionState is WifiConnectionState.Connected)
            assertEquals("CafeNet", (state.wifiConnectionState as WifiConnectionState.Connected).ssid)
            assertTrue(state.statusMessage.contains("CafeNet"))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun connectToParsedWifi_joinedWithoutInternetDoesNotSaveHistory() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = buildRepositoryMock()
            val ocrProcessor = FakeOcrEngine()

            val viewModel = buildViewModel(
                repository = repository,
                ocrProcessor = ocrProcessor,
                hasNearbyWifiPermission = { true },
                connectWifi = { ssid, _, _ ->
                    WifiConnectResult.ConnectedWithoutInternet(
                        ssid = ssid,
                        hasInternetCapability = false,
                    )
                },
            )
            advanceUntilIdle()

            viewModel.onSsidChanged("CafeNoInternet")
            viewModel.onPasswordChanged("Secret123")
            viewModel.connectToParsedWifi()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state.wifiConnectionState is WifiConnectionState.ConnectedWithoutInternet)
            assertEquals(
                "CafeNoInternet",
                (state.wifiConnectionState as WifiConnectionState.ConnectedWithoutInternet).ssid,
            )
            assertTrue(state.historyRecords.isEmpty())
            assertEquals(0, repository.saveConnectedLocalCalls)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun connectToParsedWifi_passwordOnlyFailsWithoutTryingNearbyNetworks() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = buildRepositoryMock()
            val ocrProcessor = FakeOcrEngine()
            val connectCalls = mutableListOf<Triple<String, String?, String?>>()

            val viewModel = buildViewModel(
                repository = repository,
                ocrProcessor = ocrProcessor,
                hasNearbyWifiPermission = { true },
                scannedNearbyNetworks = {
                    listOf(
                        NearbyNetwork(ssid = "WeakCafe", signalLevel = 1),
                        NearbyNetwork(ssid = "StrongCafe", signalLevel = 4),
                        NearbyNetwork(ssid = "MidCafe", signalLevel = 3),
                    )
                },
                connectWifi = { ssid, password, security ->
                    connectCalls += Triple(ssid, password, security)
                    when (ssid) {
                        "StrongCafe" -> WifiConnectResult.Failed(
                            reason = WifiConnectFailureReason.AUTHENTICATION_OR_UNAVAILABLE,
                            message = "Wrong password",
                        )

                        "MidCafe" -> WifiConnectResult.Success(ssid = ssid)

                        else -> WifiConnectResult.Failed(
                            reason = WifiConnectFailureReason.AUTHENTICATION_OR_UNAVAILABLE,
                            message = "Should not reach weaker network after success",
                        )
                    }
                },
            )
            advanceUntilIdle()

            viewModel.onPasswordChanged("ZonePassword123")
            viewModel.connectToParsedWifi()
            advanceUntilIdle()

            assertTrue(connectCalls.isEmpty())

            val state = viewModel.state.value
            assertTrue(state.wifiConnectionState is WifiConnectionState.Failed)
            assertEquals(
                WifiConnectFailureReason.INVALID_INPUT,
                (state.wifiConnectionState as WifiConnectionState.Failed).reason,
            )
            assertTrue(state.statusMessage.contains("SSID"))
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun connectToParsedWifi_passwordOnlyDoesNotUseHistoryAsImplicitSsid() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = buildRepositoryMock()
            val ocrProcessor = FakeOcrEngine()
            val connectCalls = mutableListOf<String>()

            repository.history = listOf(
                buildSavedRecord(
                    baseUrl = "http://test/",
                    ocrText = "old",
                    parsed = ParsedWifiData(
                        ssid = "KnownCafe",
                        password = "SharedPassword123",
                    ),
                ),
            )

            val viewModel = buildViewModel(
                repository = repository,
                ocrProcessor = ocrProcessor,
                hasNearbyWifiPermission = { true },
                scannedNearbyNetworks = {
                    listOf(
                        NearbyNetwork(ssid = "StrongUnknown", signalLevel = 4),
                        NearbyNetwork(ssid = "KnownCafe", signalLevel = 2),
                        NearbyNetwork(ssid = "MidUnknown", signalLevel = 3),
                        NearbyNetwork(ssid = "WeakUnknown", signalLevel = 1),
                    )
                },
                connectWifi = { ssid, _, _ ->
                    connectCalls += ssid
                    WifiConnectResult.Failed(
                        reason = WifiConnectFailureReason.AUTHENTICATION_OR_UNAVAILABLE,
                        message = "No match",
                    )
                },
            )
            advanceUntilIdle()

            viewModel.onPasswordChanged("SharedPassword123")
            viewModel.connectToParsedWifi()
            advanceUntilIdle()

            assertTrue(connectCalls.isEmpty())
            val state = viewModel.state.value
            assertTrue(state.wifiConnectionState is WifiConnectionState.Failed)
            assertTrue(state.statusMessage.contains("SSID"))
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun buildViewModel(
        repository: FakeWifiRepository,
        ocrProcessor: WifiOcrEngine,
        hasNearbyWifiPermission: () -> Boolean = { false },
        scannedNearbyNetworks: () -> List<NearbyNetwork> = { emptyList() },
        connectWifi: (suspend (ssid: String, password: String?, security: String?) -> WifiConnectResult)? = null,
    ): MainViewModel {
        val appContext = mockk<Context>()
        val application = mockk<Application>()

        every { application.applicationContext } returns appContext
        every { appContext.applicationContext } returns appContext
        every { appContext.getSystemService(ConnectivityManager::class.java) } returns null
        every { appContext.getSystemService(android.net.wifi.WifiManager::class.java) } returns null

        return MainViewModel(
            application = application,
            deps = MainViewModelDeps(
                repository = repository,
                ocrProcessor = ocrProcessor,
                ocrDispatcher = Dispatchers.Main,
                connectWifi = connectWifi,
                hasNearbyWifiPermission = hasNearbyWifiPermission,
                scannedNearbyNetworks = scannedNearbyNetworks,
                isRunningOnEmulator = { false },
                isWifiEnabled = { true },
                isLocationServiceEnabled = { true },
                nowMillis = { 1L },
            ),
        )
    }

    private fun buildRepositoryMock(): FakeWifiRepository {
        return FakeWifiRepository()
    }

    private fun buildSavedRecord(
        baseUrl: String,
        ocrText: String,
        parsed: ParsedWifiData,
    ): SavedWifiRecord {
        return SavedWifiRecord(
            id = 1L,
            baseUrl = baseUrl,
            ocrText = ocrText,
            ssid = parsed.ssid.orEmpty(),
            password = parsed.password.orEmpty(),
            sourceFormat = parsed.sourceFormat.orEmpty(),
            confidence = parsed.confidence,
            aiConfidence = null,
            aiSuggestion = "",
            aiRecommendation = "",
            aiShouldAutoConnect = false,
            aiFlags = emptyList(),
            fuzzyBestMatch = null,
            fuzzyScore = null,
            createdAtMillis = 1L,
        )
    }

    private class FakeOcrEngine : WifiOcrEngine {
        var recognitionResult: WifiOcrRecognitionResult = WifiOcrRecognitionResult()
        val extractedCredentials = mutableMapOf<String, WifiOcrCredentials>()

        override suspend fun recognize(bitmap: Bitmap): WifiOcrRecognitionResult = recognitionResult

        override fun extractWifiCredentials(text: String): WifiOcrCredentials {
            return extractedCredentials[text] ?: WifiOcrCredentials()
        }
    }

    private data class SavedParsedRequest(
        val baseUrl: String,
        val ocrText: String,
        val parsedWifiData: ParsedWifiData,
    )

    private inner class FakeWifiRepository : WifiRepository {
        var validateAiCalls = 0
        var parseOcrCalls = 0
        var validateAiResponse: ApiEnvelope<AiValidateData> = ApiEnvelope(ok = false, error = "unused")
        var parseOcrResponse: ApiEnvelope<ParsedWifiData> = ApiEnvelope(ok = false, error = "unused")
        val savedParsedRequests = mutableListOf<SavedParsedRequest>()
        var history: List<SavedWifiRecord> = emptyList()
        var saveConnectedLocalCalls = 0

        override suspend fun checkHealth(baseUrl: String): HealthData {
            return HealthData(ok = true, service = "test", uptimeSeconds = 1, timestamp = "now")
        }

        override suspend fun parseOcr(baseUrl: String, ocrText: String): ApiEnvelope<ParsedWifiData> {
            parseOcrCalls += 1
            return parseOcrResponse
        }

        override suspend fun validateAi(
            baseUrl: String,
            ssid: String?,
            password: String?,
            ocrText: String,
        ): ApiEnvelope<AiValidateData> {
            validateAiCalls += 1
            return validateAiResponse
        }

        override suspend fun fuzzyMatchSsid(
            baseUrl: String,
            ocrSsid: String,
            nearbyNetworks: List<FuzzyNetworkPayload>,
        ): ApiEnvelope<SsidFuzzyMatchData> = ApiEnvelope(ok = false, error = "unused")

        override suspend fun saveParsedWifi(
            baseUrl: String,
            ocrText: String,
            parsedWifiData: ParsedWifiData,
            aiValidateData: AiValidateData?,
            fuzzyBestMatch: String?,
            fuzzyScore: Double?,
        ): SavedWifiRecord {
            savedParsedRequests += SavedParsedRequest(
                baseUrl = baseUrl,
                ocrText = ocrText,
                parsedWifiData = parsedWifiData,
            )
            return buildSavedRecord(baseUrl, ocrText, parsedWifiData)
        }

        override suspend fun saveConnectedNetwork(
            baseUrl: String,
            request: SaveNetworkRequest,
        ): Boolean = true

        override suspend fun saveConnectedNetworkLocal(
            baseUrl: String,
            ocrText: String,
            ssid: String,
            password: String?,
            sourceFormat: String?,
            confidence: Double?,
        ): SavedWifiRecord {
            saveConnectedLocalCalls += 1
            return buildSavedRecord(
                baseUrl = baseUrl,
                ocrText = ocrText,
                parsed = ParsedWifiData(
                    ssid = ssid,
                    password = password,
                    sourceFormat = sourceFormat,
                    confidence = confidence,
                ),
            )
        }

        override suspend fun getLatestSavedWifi(): SavedWifiRecord? = null

        override suspend fun getSavedWifiHistory(): List<SavedWifiRecord> = history

        override suspend fun deleteSavedWifiRecord(id: Long): Boolean = true

        override suspend fun clearSavedWifiHistory(): Int = 0
    }
}
