package com.example.smartwificonnect

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.smartwificonnect.data.DefaultWifiRepository
import com.example.smartwificonnect.wifi.WifiConnectResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MainViewModelMockApiIntegrationTest {
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
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("smart_wifi_connect.db")
    }

    @Test
    fun parseCurrentText_success_updatesParsedFields() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "ok": true,
                      "data": {
                        "ssid": "Cafe_Wifi",
                        "password": "12345678",
                        "security": "WPA2",
                        "sourceFormat": "wifi_qr_like",
                        "confidence": 0.97
                      }
                    }
                    """.trimIndent(),
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "ok": true,
                      "data": {
                        "validated": true,
                        "confidence": 0.89,
                        "suggestion": "Du lieu on, co the review nhanh.",
                        "flags": [],
                        "normalizedSsid": "Cafe_Wifi",
                        "normalizedPassword": "12345678",
                        "parseRecommendation": "review",
                        "shouldAutoConnect": false
                      }
                    }
                    """.trimIndent(),
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "ok": true,
                      "data": {
                        "ocrSsid": "Cafe_Wifi",
                        "bestMatch": null,
                        "score": null,
                        "matches": [
                          { "ssid": "Cafe_Wifi", "signalLevel": 4, "score": 1.0 }
                        ]
                      }
                    }
                    """.trimIndent(),
                ),
        )

        val viewModel = MainViewModel(application)
        viewModel.onBaseUrlChanged(server.url("/").toString())
        viewModel.onOcrTextChanged("WiFi Name: Cafe_Wifi\nPassword: 12345678\nSecurity: WPA2")

        viewModel.parseCurrentText()

        waitUntil {
            val state = viewModel.state.value
            !state.isLoading &&
                state.ssid == "Cafe_Wifi" &&
                state.aiValidation !is AiValidationState.Loading &&
                state.ssidSuggestion !is SsidSuggestionState.Loading
        }

        val state = viewModel.state.value
        assertEquals("Cafe_Wifi", state.ssid)
        assertEquals("12345678", state.password)
        assertEquals("WPA2", state.security)
        assertEquals("wifi_qr_like", state.sourceFormat)
        assertEquals(0.97, state.confidence ?: 0.0, 0.0001)
        assertTrue(state.aiValidation is AiValidationState.Ready)
        assertTrue(state.statusMessage.contains("Parse thanh cong"))

        val parseRequest = server.takeRequest(2, TimeUnit.SECONDS)
        val aiRequest = server.takeRequest(2, TimeUnit.SECONDS)
        val fuzzyRequest = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull(parseRequest)
        assertNotNull(aiRequest)
        assertNotNull(fuzzyRequest)
        assertEquals("/api/v1/ocr/parse", parseRequest!!.path)
        assertEquals("/api/ai/validate", aiRequest!!.path)
        assertEquals("/api/v1/ssid/fuzzy-match", fuzzyRequest!!.path)
        assertTrue(parseRequest.body.readUtf8().contains("Cafe_Wifi"))
    }

    @Test
    fun parseCurrentText_aiValidateAndFuzzy_success_updatesAiStateAndSuggestion() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "ok": true,
                      "data": {
                        "ssid": "Cafe_Wif1_5g",
                        "password": "12345678",
                        "security": "WPA2",
                        "sourceFormat": "wifi_qr_like",
                        "confidence": 0.93
                      }
                    }
                    """.trimIndent(),
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "ok": true,
                      "data": {
                        "validated": true,
                        "confidence": 0.81,
                        "suggestion": "Du lieu kha on, nen review truoc khi connect.",
                        "flags": ["ocr_ambiguous_characters"],
                        "normalizedSsid": "Cafe_WiFi_5G",
                        "normalizedPassword": "12345678",
                        "parseRecommendation": "review",
                        "shouldAutoConnect": false
                      }
                    }
                    """.trimIndent(),
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "ok": true,
                      "data": {
                        "ocrSsid": "Cafe_Wif1_5g",
                        "bestMatch": "Cafe_WiFi_5G",
                        "score": 0.91,
                        "matches": [
                          { "ssid": "Cafe_WiFi_5G", "signalLevel": 4, "score": 0.91 },
                          { "ssid": "Cafe_Visitor", "signalLevel": 2, "score": 0.62 }
                        ]
                      }
                    }
                    """.trimIndent(),
                ),
        )

        val viewModel = MainViewModel(application)
        viewModel.onBaseUrlChanged(server.url("/").toString())
        viewModel.onOcrTextChanged("WiFi Name: Cafe_Wif1_5g\nPassword: 12345678\nSecurity: WPA2")

        viewModel.parseCurrentText()

        waitUntil {
            val state = viewModel.state.value
            state.aiValidation is AiValidationState.Ready &&
                state.ssidSuggestion is SsidSuggestionState.Found
        }

        val state = viewModel.state.value
        val aiState = state.aiValidation as AiValidationState.Ready
        assertEquals("review", aiState.recommendation)
        assertEquals("Cafe_WiFi_5G", aiState.normalizedSsid)
        assertTrue(aiState.flags.contains("ocr_ambiguous_characters"))

        val suggestion = state.ssidSuggestion as SsidSuggestionState.Found
        assertEquals("Cafe_WiFi_5G", suggestion.bestMatch)
        assertEquals(0.91, suggestion.score, 0.0001)

        assertEquals("/api/v1/ocr/parse", server.takeRequest(2, TimeUnit.SECONDS)!!.path)
        assertEquals("/api/ai/validate", server.takeRequest(2, TimeUnit.SECONDS)!!.path)
        assertEquals("/api/v1/ssid/fuzzy-match", server.takeRequest(2, TimeUnit.SECONDS)!!.path)
    }

    @Test
    fun parseCurrentText_apiBusinessError_showsBackendMessage() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "ok": false,
                      "error": "Khong tim thay thong tin WiFi hop le"
                    }
                    """.trimIndent(),
                ),
        )

        val viewModel = MainViewModel(application)
        viewModel.onBaseUrlChanged(server.url("/").toString())
        viewModel.onOcrTextChanged("random text khong co format wifi")

        viewModel.parseCurrentText()

        waitUntil {
            val state = viewModel.state.value
            !state.isLoading && state.statusMessage.contains("Khong tim thay thong tin WiFi hop le")
        }

        val state = viewModel.state.value
        assertTrue(state.ssid.isBlank())
        assertTrue(state.password.isBlank())
        assertTrue(state.statusMessage.contains("Khong tim thay thong tin WiFi hop le"))
    }

    @Test
    fun parseCurrentText_networkError_showsParseError() = runBlocking {
        val unreachableBaseUrl = "http://127.0.0.1:1/"

        val viewModel = MainViewModel(application)
        viewModel.onBaseUrlChanged(unreachableBaseUrl)
        viewModel.onOcrTextChanged("WiFi Name: Home\nPassword: 99999999")

        viewModel.parseCurrentText()

        waitUntil {
            val state = viewModel.state.value
            !state.isLoading && state.statusMessage.startsWith("Loi parse:")
        }

        val state = viewModel.state.value
        assertTrue(state.statusMessage.startsWith("Loi parse:"))
        assertTrue(state.ssid.isBlank())
        assertTrue(state.password.isBlank())
    }

    @Test
    fun connectSaveHistory_success_savesLocalAndCallsSaveNetworkApi() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(201),
        )

        val repository = DefaultWifiRepository(application.applicationContext)
        val viewModel = MainViewModel(
            application = application,
            deps = MainViewModelDeps(
                repository = repository,
                connectWifi = { ssid, _, _ ->
                    WifiConnectResult.Success(ssid = ssid)
                },
                hasNearbyWifiPermission = { true },
                scannedNearbyNetworks = { listOf(NearbyNetwork("Home_Cloud_5G", signalLevel = 4)) },
                isRunningOnEmulator = { false },
                nowMillis = { 1_700_000_000_000L },
            ),
        )

        viewModel.onBaseUrlChanged(server.url("/").toString())
        viewModel.onOcrTextChanged("WiFi Name: Home_Cloud_5G\nPassword: 12345678")
        viewModel.onSsidChanged("Home_Cloud_5G")
        viewModel.onPasswordChanged("12345678")

        viewModel.connectToParsedWifi()

        waitUntil {
            val state = viewModel.state.value
            state.wifiConnectionState is WifiConnectionState.Connected &&
                state.historyRecords.any { it.ssid == "Home_Cloud_5G" }
        }

        val state = viewModel.state.value
        assertTrue(state.wifiConnectionState is WifiConnectionState.Connected)
        assertTrue(state.historyRecords.isNotEmpty())
        val latest = state.historyRecords.first()
        assertEquals("Home_Cloud_5G", latest.ssid)
        assertEquals("12345678", latest.password)

        val request = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull(request)
        assertEquals("/api/networks", request!!.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"ssid\":\"Home_Cloud_5G\""))
        assertTrue(body.contains("\"connectedAtEpochMs\":1700000000000"))
    }

    private suspend fun waitUntil(
        timeoutMs: Long = 5_000,
        condition: () -> Boolean,
    ) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            if (System.currentTimeMillis() - start > timeoutMs) {
                throw AssertionError("Timeout while waiting for condition")
            }
            delay(50)
        }
    }
}
