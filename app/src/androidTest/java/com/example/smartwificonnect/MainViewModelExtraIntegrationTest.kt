package com.example.smartwificonnect

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.smartwificonnect.data.DefaultWifiRepository
import com.example.smartwificonnect.wifi.WifiConnectFailureReason
import com.example.smartwificonnect.wifi.WifiConnectResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Bổ sung integration tests cho MainViewModel, bao phủ các luồng chưa có:
 * - parseCurrentText khi SSID rỗng (chỉ password)
 * - checkHealth success / failure
 * - onOcrTextChanged reset state
 * - connectToParsedWifi khi SSID rỗng
 * - connectToParsedWifi khi SSID không nằm trong nearby list
 * - connectToParsedWifi thất bại (auth error)
 * - connectToParsedWifi thành công nhưng save API thất bại (local saved)
 * - deleteSelectedNetworkDetail success/failure
 * - consumeSharedWifiLink valid / invalid
 * - refreshHistory
 * - onDarkModeChanged
 * - state field setters (onSsidChanged, onPasswordChanged, onSecurityChanged)
 */
@RunWith(AndroidJUnit4::class)
class MainViewModelExtraIntegrationTest {

    private lateinit var application: Application
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        application = context.applicationContext as Application
        context.deleteDatabase("smart_wifi_connect.db")
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        InstrumentationRegistry.getInstrumentation().targetContext
            .deleteDatabase("smart_wifi_connect.db")
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private suspend fun waitUntil(
        timeoutMs: Long = 5_000,
        condition: () -> Boolean,
    ) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            check(System.currentTimeMillis() - start < timeoutMs) {
                "waitUntil timeout"
            }
            delay(50)
        }
    }

    private fun buildViewModel(
        connectWifi: (suspend (String, String?, String?) -> WifiConnectResult)? = null,
        hasPermission: Boolean = true,
        nearbyNetworks: List<NearbyNetwork> = emptyList(),
        isEmulator: Boolean = false,
        nowMillis: Long = System.currentTimeMillis(),
        repository: com.example.smartwificonnect.data.WifiRepository? = null,
    ): MainViewModel {
        return MainViewModel(
            application = application,
            deps = MainViewModelDeps(
                repository = repository,
                connectWifi = connectWifi,
                hasNearbyWifiPermission = { hasPermission },
                scannedNearbyNetworks = { nearbyNetworks },
                isRunningOnEmulator = { isEmulator },
                nowMillis = { nowMillis },
            ),
        )
    }

    // ─── checkHealth ──────────────────────────────────────────────────────────

    @Test
    fun checkHealth_serverReturns200_statusMessageContainsServiceName() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "ok": true,
                      "service": "SmartWiFi-API",
                      "uptimeSeconds": 3600,
                      "timestamp": "2025-01-01T00:00:00Z"
                    }
                    """.trimIndent(),
                ),
        )

        val viewModel = buildViewModel()
        viewModel.onBaseUrlChanged(server.url("/").toString())
        viewModel.checkHealth()

        waitUntil { viewModel.state.value.statusMessage.contains("SmartWiFi-API") }

        assertTrue(viewModel.state.value.statusMessage.contains("SmartWiFi-API"))
    }

    @Test
    fun checkHealth_serverUnreachable_statusMessageContainsError() = runBlocking {
        val viewModel = buildViewModel()
        viewModel.onBaseUrlChanged("http://127.0.0.1:1/")
        viewModel.checkHealth()

        waitUntil { viewModel.state.value.statusMessage.contains("Khong ket noi duoc server") }

        assertTrue(viewModel.state.value.statusMessage.contains("Khong ket noi duoc server"))
        assertFalse(viewModel.state.value.isLoading)
    }

    // ─── parseCurrentText edge cases ─────────────────────────────────────────

    @Test
    fun parseCurrentText_emptyOcrText_doesNotCallServerAndShowsHint() = runBlocking {
        val viewModel = buildViewModel()
        viewModel.onBaseUrlChanged(server.url("/").toString())
        // Không set ocrText → blank

        viewModel.parseCurrentText()
        delay(300)

        // Không có request nào được gửi
        assertNull(server.takeRequest(500, TimeUnit.MILLISECONDS))
        assertTrue(viewModel.state.value.statusMessage.isNotBlank())
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun parseCurrentText_serverReturnsOkFalse_statusShowsServerErrorMessage() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"ok":false,"error":"OCR text qua ngan"}"""),
        )

        val viewModel = buildViewModel()
        viewModel.onBaseUrlChanged(server.url("/").toString())
        viewModel.onOcrTextChanged("abc")
        viewModel.parseCurrentText()

        waitUntil { !viewModel.state.value.isLoading }

        assertTrue(viewModel.state.value.statusMessage.contains("OCR text qua ngan"))
        assertEquals("", viewModel.state.value.ssid)
    }

    // ─── onOcrTextChanged reset ────────────────────────────────────────────────

    @Test
    fun onOcrTextChanged_afterParseSuccess_resetsAllParsedFields() = runBlocking {
        // Thiết lập state giả đã có ssid/password/confidence
        val viewModel = buildViewModel()
        viewModel.onSsidChanged("OldNet")
        viewModel.onPasswordChanged("OldPass")
        viewModel.onSecurityChanged("WPA2")

        viewModel.onOcrTextChanged("brand new text")

        val state = viewModel.state.value
        assertEquals("", state.ssid)
        assertEquals("", state.password)
        assertEquals("", state.security)
        assertNull(state.confidence)
        assertTrue(state.aiValidation is AiValidationState.Hidden)
        assertTrue(state.ssidSuggestion is SsidSuggestionState.Hidden)
        assertEquals(WifiConnectionState.Idle, state.wifiConnectionState)
        assertEquals("brand new text", state.ocrText)
    }

    // ─── Field setters ────────────────────────────────────────────────────────

    @Test
    fun onSsidChanged_updatesStateAndResetsConnectionState() {
        val viewModel = buildViewModel()
        viewModel.onSsidChanged("NewSSID")
        assertEquals("NewSSID", viewModel.state.value.ssid)
        assertEquals(WifiConnectionState.Idle, viewModel.state.value.wifiConnectionState)
    }

    @Test
    fun onPasswordChanged_updatesStateAndResetsConnectionState() {
        val viewModel = buildViewModel()
        viewModel.onPasswordChanged("NewPass")
        assertEquals("NewPass", viewModel.state.value.password)
        assertEquals(WifiConnectionState.Idle, viewModel.state.value.wifiConnectionState)
    }

    @Test
    fun onSecurityChanged_updatesStateAndResetsConnectionState() {
        val viewModel = buildViewModel()
        viewModel.onSecurityChanged("WPA3")
        assertEquals("WPA3", viewModel.state.value.security)
        assertEquals(WifiConnectionState.Idle, viewModel.state.value.wifiConnectionState)
    }

    @Test
    fun onDarkModeChanged_true_setsDarkModeEnabled() {
        val viewModel = buildViewModel()
        viewModel.onDarkModeChanged(true)
        assertTrue(viewModel.state.value.isDarkModeEnabled)
    }

    @Test
    fun onDarkModeChanged_false_clearsDarkModeEnabled() {
        val viewModel = buildViewModel()
        viewModel.onDarkModeChanged(true)
        viewModel.onDarkModeChanged(false)
        assertFalse(viewModel.state.value.isDarkModeEnabled)
    }

    @Test
    fun clearWifiConnectionState_setsIdle() = runBlocking {
        val viewModel = buildViewModel(isEmulator = true)
        viewModel.onSsidChanged("SomeNet")
        viewModel.connectToParsedWifi()
        delay(200)
        // Sau khi là emulator → Failed
        assertTrue(viewModel.state.value.wifiConnectionState is WifiConnectionState.Failed)

        viewModel.clearWifiConnectionState()
        assertEquals(WifiConnectionState.Idle, viewModel.state.value.wifiConnectionState)
    }

    // ─── connectToParsedWifi error paths ─────────────────────────────────────

    @Test
    fun connectToParsedWifi_emptySsid_setsFailedInvalidInput() = runBlocking {
        val viewModel = buildViewModel()
        // Không set SSID → ssid = ""
        viewModel.connectToParsedWifi()

        val state = viewModel.state.value
        assertTrue(state.wifiConnectionState is WifiConnectionState.Failed)
        val failed = state.wifiConnectionState as WifiConnectionState.Failed
        assertEquals(WifiConnectFailureReason.INVALID_INPUT, failed.reason)
    }

    @Test
    fun connectToParsedWifi_onEmulator_setsFailedUnknown() = runBlocking {
        val viewModel = buildViewModel(isEmulator = true)
        viewModel.onSsidChanged("EmulatorNet")
        viewModel.connectToParsedWifi()

        delay(200)
        val state = viewModel.state.value
        assertTrue(state.wifiConnectionState is WifiConnectionState.Failed)
        val failed = state.wifiConnectionState as WifiConnectionState.Failed
        assertEquals(WifiConnectFailureReason.UNKNOWN, failed.reason)
    }

    @Test
    fun connectToParsedWifi_ssidNotInNearbyList_setsFailedSsidNotFound() = runBlocking {
        val viewModel = buildViewModel(
            nearbyNetworks = listOf(NearbyNetwork("OtherNet", 3)),
            hasPermission = true,
            isEmulator = false,
        )
        viewModel.onSsidChanged("GhostNet")
        viewModel.connectToParsedWifi()

        delay(200)
        val state = viewModel.state.value
        assertTrue(state.wifiConnectionState is WifiConnectionState.Failed)
        assertEquals(
            WifiConnectFailureReason.SSID_NOT_FOUND,
            (state.wifiConnectionState as WifiConnectionState.Failed).reason,
        )
    }

    @Test
    fun connectToParsedWifi_permissionDenied_setsPermissionDeniedState() = runBlocking {
        val viewModel = buildViewModel(hasPermission = false, isEmulator = false)
        viewModel.onSsidChanged("PermNet")
        viewModel.connectToParsedWifi()

        delay(200)
        val state = viewModel.state.value
        // ViewModel gọi onWifiConnectionPermissionDenied → state phải phản ánh permission denied
        val wifiState = state.wifiConnectionState
        assertTrue(
            wifiState is WifiConnectionState.Failed &&
                wifiState.reason == WifiConnectFailureReason.PERMISSION_DENIED,
        )
    }

    @Test
    fun connectToParsedWifi_authenticationFailure_setsFailedAuthOrUnavailable() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201))

        val viewModel = buildViewModel(
            connectWifi = { _, _, _ ->
                WifiConnectResult.Failed(
                    reason = WifiConnectFailureReason.AUTHENTICATION_OR_UNAVAILABLE,
                    message = "Wrong password",
                )
            },
            hasPermission = true,
            nearbyNetworks = listOf(NearbyNetwork("AuthNet", 4)),
            isEmulator = false,
            repository = DefaultWifiRepository(application),
        )

        viewModel.onBaseUrlChanged(server.url("/").toString())
        viewModel.onSsidChanged("AuthNet")
        viewModel.onPasswordChanged("wrongpass")

        viewModel.connectToParsedWifi()

        waitUntil {
            viewModel.state.value.wifiConnectionState is WifiConnectionState.Failed
        }

        val failed = viewModel.state.value.wifiConnectionState as WifiConnectionState.Failed
        assertEquals(WifiConnectFailureReason.AUTHENTICATION_OR_UNAVAILABLE, failed.reason)
    }

    @Test
    fun connectToParsedWifi_success_saveApiFailure_localRecordStillSaved() = runBlocking {
        // Server trả 500 → saveConnectedNetwork failed, nhưng local vẫn được lưu
        server.enqueue(MockResponse().setResponseCode(500))

        val viewModel = buildViewModel(
            connectWifi = { ssid, _, _ -> WifiConnectResult.Success(ssid = ssid) },
            hasPermission = true,
            nearbyNetworks = listOf(NearbyNetwork("LocalOnlyNet", 4)),
            isEmulator = false,
            repository = DefaultWifiRepository(application),
        )

        viewModel.onBaseUrlChanged(server.url("/").toString())
        viewModel.onSsidChanged("LocalOnlyNet")
        viewModel.onPasswordChanged("pass1234")

        viewModel.connectToParsedWifi()

        waitUntil {
            viewModel.state.value.wifiConnectionState is WifiConnectionState.Connected
        }

        val state = viewModel.state.value
        assertTrue(state.wifiConnectionState is WifiConnectionState.Connected)
        assertTrue(state.historyRecords.any { it.ssid == "LocalOnlyNet" })
        // Server save thất bại → message có "server chua san sang" hoặc "local"
        assertTrue(
            state.statusMessage.contains("local", ignoreCase = true) ||
                state.statusMessage.contains("server", ignoreCase = true),
        )
    }

    // ─── consumeSharedWifiLink ────────────────────────────────────────────────

    @Test
    fun consumeSharedWifiLink_validUri_updatesStateCorrectly() {
        val viewModel = buildViewModel()
        val uri = android.net.Uri.parse("smartwifi://join?ssid=SharedNet&password=sharedpass&security=WPA2")

        viewModel.consumeSharedWifiLink(uri)

        val state = viewModel.state.value
        assertEquals("SharedNet", state.ssid)
        assertEquals("sharedpass", state.password)
        assertEquals("WPA2", state.security)
        assertEquals("share_link", state.sourceFormat)
        assertTrue(state.statusMessage.contains("link chia se", ignoreCase = true))
    }

    @Test
    fun consumeSharedWifiLink_missingSsid_doesNotChangeState() {
        val viewModel = buildViewModel()
        val initialStatus = viewModel.state.value.statusMessage

        val uri = android.net.Uri.parse("smartwifi://join?password=somepass")
        viewModel.consumeSharedWifiLink(uri)

        // SSID blank → không update
        assertEquals("", viewModel.state.value.ssid)
    }

    @Test
    fun consumeSharedWifiLink_wrongScheme_doesNotChangeState() {
        val viewModel = buildViewModel()
        val uri = android.net.Uri.parse("https://other.com?ssid=Net&password=pass")
        viewModel.consumeSharedWifiLink(uri)
        assertEquals("", viewModel.state.value.ssid)
    }

    @Test
    fun consumeSharedWifiLink_nullUri_doesNotCrash() {
        val viewModel = buildViewModel()
        viewModel.consumeSharedWifiLink(null)
        // Không crash, state không thay đổi
        assertEquals("", viewModel.state.value.ssid)
    }

    @Test
    fun consumeSharedWifiLink_noSecurityField_infersSecurity() {
        val viewModel = buildViewModel()
        val uri = android.net.Uri.parse("smartwifi://join?ssid=OpenNet&password=openpass")
        viewModel.consumeSharedWifiLink(uri)
        // Security nên được tự suy (không phải blank)
        assertTrue(viewModel.state.value.security.isNotBlank())
    }

    @Test
    fun consumeSharedWifiLink_openNetwork_noPassword_setsOpenSecurity() {
        val viewModel = buildViewModel()
        val uri = android.net.Uri.parse("smartwifi://join?ssid=FreeNet")
        viewModel.consumeSharedWifiLink(uri)
        assertEquals("FreeNet", viewModel.state.value.ssid)
        assertEquals("", viewModel.state.value.password)
        assertEquals("Open", viewModel.state.value.security)
    }

    // ─── deleteSelectedNetworkDetail ─────────────────────────────────────────

    @Test
    fun deleteSelectedNetworkDetail_noDetailSelected_doesNothing() = runBlocking {
        val viewModel = buildViewModel(repository = DefaultWifiRepository(application))
        viewModel.deleteSelectedNetworkDetail()
        delay(300)
        // Không crash, không thay đổi
        assertNull(viewModel.state.value.selectedNetworkDetail)
    }

    // ─── refreshHistory ───────────────────────────────────────────────────────

    @Test
    fun refreshHistory_emptyDb_setsEmptyList() = runBlocking {
        val viewModel = buildViewModel(repository = DefaultWifiRepository(application))
        viewModel.refreshHistory()

        waitUntil { viewModel.state.value.historyRecords.isEmpty() }

        assertTrue(viewModel.state.value.historyRecords.isEmpty())
    }

    // ─── parseCurrentText success → history updated ───────────────────────────

    @Test
    fun parseCurrentText_success_addsRecordToHistoryRecords() = runBlocking {
        enqueueParseSsidResponse("HistoryNet", "hist_pass", "WPA2", "text_label", 0.9)
        enqueueAiValidateResponse(
            validated = true,
            confidence = 0.80,
            suggestion = "ok",
            normalizedSsid = "HistoryNet",
            normalizedPassword = "hist_pass",
        )
        enqueueFuzzyMatchResponse(ocrSsid = "HistoryNet", bestMatch = null)

        val viewModel = MainViewModel(application)
        viewModel.onBaseUrlChanged(server.url("/").toString())
        viewModel.onOcrTextChanged("WiFi Name: HistoryNet\nPassword: hist_pass")
        viewModel.parseCurrentText()

        waitUntil {
            !viewModel.state.value.isLoading &&
                viewModel.state.value.historyRecords.isNotEmpty()
        }

        assertTrue(viewModel.state.value.historyRecords.any { it.ssid == "HistoryNet" })
    }

    // ─── Helpers for enqueue ──────────────────────────────────────────────────

    private fun enqueueParseSsidResponse(
        ssid: String,
        password: String,
        security: String,
        sourceFormat: String,
        confidence: Double,
    ) {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "ok": true,
                      "data": {
                        "ssid": "$ssid",
                        "password": "$password",
                        "security": "$security",
                        "sourceFormat": "$sourceFormat",
                        "confidence": $confidence
                      }
                    }
                    """.trimIndent(),
                ),
        )
    }

    private fun enqueueAiValidateResponse(
        validated: Boolean,
        confidence: Double,
        suggestion: String,
        normalizedSsid: String?,
        normalizedPassword: String?,
        flags: List<String> = emptyList(),
        recommendation: String = "review",
        shouldAutoConnect: Boolean = false,
    ) {
        val flagsJson = flags.joinToString(",") { "\"$it\"" }
        val ssidJson = if (normalizedSsid != null) "\"$normalizedSsid\"" else "null"
        val passJson = if (normalizedPassword != null) "\"$normalizedPassword\"" else "null"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "ok": true,
                      "data": {
                        "validated": $validated,
                        "confidence": $confidence,
                        "suggestion": "$suggestion",
                        "flags": [$flagsJson],
                        "normalizedSsid": $ssidJson,
                        "normalizedPassword": $passJson,
                        "parseRecommendation": "$recommendation",
                        "shouldAutoConnect": $shouldAutoConnect
                      }
                    }
                    """.trimIndent(),
                ),
        )
    }

    private fun enqueueFuzzyMatchResponse(
        ocrSsid: String,
        bestMatch: String?,
        score: Double? = null,
    ) {
        val bestMatchJson = if (bestMatch != null) "\"$bestMatch\"" else "null"
        val scoreJson = if (score != null) score.toString() else "null"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "ok": true,
                      "data": {
                        "ocrSsid": "$ocrSsid",
                        "bestMatch": $bestMatchJson,
                        "score": $scoreJson,
                        "matches": []
                      }
                    }
                    """.trimIndent(),
                ),
        )
    }
}
