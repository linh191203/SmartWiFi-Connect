package com.example.smartwificonnect

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

        val viewModel = MainViewModel(application)
        viewModel.onBaseUrlChanged(server.url("/").toString())
        viewModel.onOcrTextChanged("WiFi Name: Cafe_Wifi\nPassword: 12345678\nSecurity: WPA2")

        viewModel.parseCurrentText()

        waitUntil {
            val state = viewModel.state.value
            !state.isLoading && state.ssid == "Cafe_Wifi"
        }

        val state = viewModel.state.value
        assertEquals("Cafe_Wifi", state.ssid)
        assertEquals("12345678", state.password)
        assertEquals("WPA2", state.security)
        assertEquals("wifi_qr_like", state.sourceFormat)
        assertEquals(0.97, state.confidence ?: 0.0, 0.0001)
        assertTrue(state.statusMessage.contains("Parse thanh cong"))

        val request = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull(request)
        assertEquals("/api/v1/ocr/parse", request!!.path)
        assertTrue(request.body.readUtf8().contains("Cafe_Wifi"))
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
