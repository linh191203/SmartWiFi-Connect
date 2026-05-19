package com.smartwificonnect.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiNetworkSpecifier
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WifiConnectorTest {

    private lateinit var context: Context
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var wifiManager: android.net.wifi.WifiManager
    private lateinit var wifiConnector: WifiConnector
    private var connectedSsid: String? = null

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        connectivityManager = mockk(relaxed = true)
        wifiManager = mockk(relaxed = true)
        connectedSsid = null

        every { context.applicationContext } returns context
        every { context.getSystemService(ConnectivityManager::class.java) } returns connectivityManager
        every { context.getSystemService(android.net.wifi.WifiManager::class.java) } returns wifiManager
        every { connectivityManager.bindProcessToNetwork(any()) } returns true
        every { connectivityManager.bindProcessToNetwork(null) } returns true
        // WifiManager: by default, no SSID associated and addNetworkSuggestions returns SUCCESS.
        every { wifiManager.connectionInfo } answers { wifiInfo(connectedSsid) }
        every { wifiManager.addNetworkSuggestions(any()) } returns
            android.net.wifi.WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS
        every { wifiManager.removeNetworkSuggestions(any()) } returns
            android.net.wifi.WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS
    }

    @Test
    fun `connect with valid SSID succeeds`() = runTest {
        wifiConnector = buildConnector()

        val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
        every {
            connectivityManager.requestNetwork(any<NetworkRequest>(), capture(callbackSlot))
        } just Runs

        val result = async {
            wifiConnector.connect(
                ssid = "TestSSID",
                password = "password123",
                security = "WPA2"
            )
        }
        advanceTimeBy(2_500)
        runCurrent()

        val mockNetwork = mockk<Network>()
        setConnectedSsid("TestSSID")
        callbackSlot.captured.onCapabilitiesChanged(mockNetwork, validatedWifiCapabilities())
        advanceUntilIdle()

        val connectResult = result.await()
        assertTrue(connectResult is WifiConnectResult.Success)
        val successResult = connectResult as WifiConnectResult.Success
        assertEquals("TestSSID", successResult.ssid)
        assertEquals(mockNetwork, successResult.network)

        verify {
            connectivityManager.requestNetwork(
                any<NetworkRequest>(),
                any<ConnectivityManager.NetworkCallback>(),
            )
        }
        // We deliberately do NOT call bindProcessToNetwork() to avoid Vivo OEM
        // dialog "Đã xảy ra lỗi. Ứng dụng đã hủy yêu cầu chọn thiết bị".
        io.mockk.verify(exactly = 0) {
            connectivityManager.bindProcessToNetwork(any())
        }
    }

    @Test
    fun `connect with empty SSID fails`() = runTest {
        wifiConnector = buildConnector()

        val result = wifiConnector.connect(
            ssid = "",
            password = "password123",
            security = "WPA2"
        )

        assertTrue(result is WifiConnectResult.Failed)
        val failedResult = result as WifiConnectResult.Failed
        assertEquals(WifiConnectFailureReason.INVALID_INPUT, failedResult.reason)
    }

    @Test
    fun `connect with whitespace SSID is trimmed and fails`() = runTest {
        wifiConnector = buildConnector()

        val result = wifiConnector.connect(
            ssid = "   ",
            password = "password123",
            security = "WPA2"
        )

        assertTrue(result is WifiConnectResult.Failed)
        val failedResult = result as WifiConnectResult.Failed
        assertEquals(WifiConnectFailureReason.INVALID_INPUT, failedResult.reason)
    }

    @Test
    fun `connect with short WPA password fails before requesting network`() = runTest {
        wifiConnector = buildConnector()

        val result = wifiConnector.connect(
            ssid = "TestSSID",
            password = "12345",
            security = "WPA2",
        )

        assertTrue(result is WifiConnectResult.Failed)
        val failedResult = result as WifiConnectResult.Failed
        assertEquals(WifiConnectFailureReason.INVALID_INPUT, failedResult.reason)
        verify(exactly = 0) {
            connectivityManager.requestNetwork(
                any<NetworkRequest>(),
                any<ConnectivityManager.NetworkCallback>(),
            )
        }
    }

    @Test
    fun `connect without ConnectivityManager returns error`() = runTest {
        every { context.getSystemService(ConnectivityManager::class.java) } returns null

        wifiConnector = buildConnector()

        val result = wifiConnector.connect(
            ssid = "TestSSID",
            password = "password123",
            security = "WPA2"
        )

        assertTrue(result is WifiConnectResult.Failed)
        val failedResult = result as WifiConnectResult.Failed
        assertEquals(WifiConnectFailureReason.UNKNOWN, failedResult.reason)
    }

    @Test
    fun `connect with WPA3 security can complete successfully`() = runTest {
        var capturedSecurity: String? = null
        wifiConnector = buildConnector { _, _, security ->
            capturedSecurity = security
        }

        val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
        every {
            connectivityManager.requestNetwork(any<NetworkRequest>(), capture(callbackSlot))
        } just Runs

        val result = async {
            wifiConnector.connect(
                ssid = "TestSSID",
                password = "password123",
                security = "WPA3"
            )
        }
        advanceTimeBy(2_500)
        runCurrent()

        setConnectedSsid("TestSSID")
        callbackSlot.captured.onCapabilitiesChanged(mockk(), validatedWifiCapabilities())
        advanceUntilIdle()

        assertTrue(result.await() is WifiConnectResult.Success)
        assertEquals("WPA3", capturedSecurity)

        verify {
            connectivityManager.requestNetwork(
                any<NetworkRequest>(),
                any<ConnectivityManager.NetworkCallback>(),
            )
        }
    }

    @Test
    fun `connect without password attempts open network connection`() = runTest {
        var capturedPassword: String? = "sentinel"
        wifiConnector = buildConnector { _, password, _ ->
            capturedPassword = password
        }

        val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
        every {
            connectivityManager.requestNetwork(any<NetworkRequest>(), capture(callbackSlot))
        } just Runs

        val result = async {
            wifiConnector.connect(
                ssid = "OpenNetwork",
                password = null,
                security = null
            )
        }
        advanceTimeBy(2_500)
        runCurrent()

        setConnectedSsid("OpenNetwork")
        callbackSlot.captured.onCapabilitiesChanged(mockk(), validatedWifiCapabilities())
        advanceUntilIdle()

        assertTrue(result.await() is WifiConnectResult.Success)
        assertEquals(null, capturedPassword)

        verify {
            connectivityManager.requestNetwork(
                any<NetworkRequest>(),
                any<ConnectivityManager.NetworkCallback>(),
            )
        }
    }

    @Test
    fun `cancelPendingRequest unregisters active callback`() = runTest {
        wifiConnector = buildConnector()

        val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
        every {
            connectivityManager.requestNetwork(any<NetworkRequest>(), capture(callbackSlot))
        } just Runs

        val pendingConnection = launch {
            wifiConnector.connect(
                ssid = "TestSSID",
                password = "password123",
                security = "WPA2"
            )
        }
        advanceTimeBy(2_500)
        runCurrent()

        wifiConnector.cancelPendingRequest()
        pendingConnection.cancel()
        advanceUntilIdle()

        verify { connectivityManager.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>()) }
    }

    @Test
    fun `connect returns failure when network is unavailable`() = runTest {
        wifiConnector = buildConnector()

        val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
        every {
            connectivityManager.requestNetwork(any<NetworkRequest>(), capture(callbackSlot))
        } just Runs

        val connectionTask = async {
            wifiConnector.connect(
                ssid = "TestSSID",
                password = "password123",
                security = "WPA2"
            )
        }
        advanceTimeBy(2_500)
        runCurrent()

        callbackSlot.captured.onUnavailable()
        advanceUntilIdle()

        val result = connectionTask.await()
        assertTrue(result is WifiConnectResult.Failed)
        val failedResult = result as WifiConnectResult.Failed
        assertEquals(WifiConnectFailureReason.WRONG_PASSWORD_OR_REJECTED, failedResult.reason)

        verify {
            connectivityManager.requestNetwork(
                any<NetworkRequest>(),
                any<ConnectivityManager.NetworkCallback>(),
            )
        }
    }

    @Test
    fun `connect returns joined without internet when validation is missing`() = runTest {
        wifiConnector = buildConnector()

        val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
        every {
            connectivityManager.requestNetwork(any<NetworkRequest>(), capture(callbackSlot))
        } just Runs

        val connectionTask = async {
            wifiConnector.connect(
                ssid = "CafeNoInternet",
                password = "password123",
                security = "WPA2",
            )
        }
        advanceTimeBy(2_500)
        runCurrent()

        // Deliberately keep WifiManager.connectionInfo blank so the new
        // SSID-match override does NOT escalate to Success — we want to
        // exercise the capabilities-only "no internet" code path here.
        callbackSlot.captured.onCapabilitiesChanged(mockk(), joinedWifiCapabilities())
        advanceUntilIdle()

        val result = connectionTask.await()
        assertTrue(result is WifiConnectResult.ConnectedWithoutInternet)
        val joinedResult = result as WifiConnectResult.ConnectedWithoutInternet
        assertEquals("CafeNoInternet", joinedResult.ssid)
        assertEquals(false, joinedResult.hasInternetCapability)
        verify(exactly = 0) {
            connectivityManager.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>())
        }
        assertEquals("CafeNoInternet", wifiConnector.getKeptAliveSsid())
    }

    @Test
    fun `connect does not succeed when internet capability is present but validation is missing`() = runTest {
        wifiConnector = buildConnector()

        val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
        every {
            connectivityManager.requestNetwork(any<NetworkRequest>(), capture(callbackSlot))
        } just Runs

        val connectionTask = async {
            wifiConnector.connect(
                ssid = "CafeWithUnvalidatedInternet",
                password = "password123",
                security = "WPA2",
            )
        }
        advanceTimeBy(2_500)
        runCurrent()

        // Deliberately keep WifiManager.connectionInfo blank so the SSID-match
        // override does NOT short-circuit to Success.
        callbackSlot.captured.onCapabilitiesChanged(
            mockk(),
            joinedWifiCapabilities(hasInternet = true, isValidated = false),
        )
        advanceUntilIdle()

        val result = connectionTask.await()
        assertTrue(result is WifiConnectResult.ConnectedWithoutInternet)
        val joinedResult = result as WifiConnectResult.ConnectedWithoutInternet
        assertEquals("CafeWithUnvalidatedInternet", joinedResult.ssid)
        assertEquals(true, joinedResult.hasInternetCapability)
        verify(exactly = 0) {
            connectivityManager.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>())
        }
        assertEquals("CafeWithUnvalidatedInternet", wifiConnector.getKeptAliveSsid())
    }

    @Test
    fun `validated callback is ignored until WifiManager reports requested SSID`() = runTest {
        setConnectedSsid("OldNetwork")
        wifiConnector = buildConnector()

        val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
        every {
            connectivityManager.requestNetwork(any<NetworkRequest>(), capture(callbackSlot))
        } just Runs

        val connectionTask = async {
            wifiConnector.connect(
                ssid = "NewNetwork",
                password = "password123",
                security = "WPA2",
            )
        }
        // Advance past the suggestion-based switch wait so requestNetwork is
        // invoked and the callback slot is captured.
        advanceTimeBy(4_500)
        runCurrent()

        val oldNetwork = mockk<Network>()
        callbackSlot.captured.onCapabilitiesChanged(oldNetwork, validatedWifiCapabilities())
        runCurrent()

        assertFalse(connectionTask.isCompleted)

        val newNetwork = mockk<Network>()
        setConnectedSsid("NewNetwork")
        callbackSlot.captured.onCapabilitiesChanged(newNetwork, validatedWifiCapabilities())
        advanceUntilIdle()

        val result = connectionTask.await()
        assertTrue(result is WifiConnectResult.Success)
        val successResult = result as WifiConnectResult.Success
        assertEquals("NewNetwork", successResult.ssid)
        assertEquals(newNetwork, successResult.network)
    }

    @Test
    fun `onAvailable is ignored until WifiManager reports requested SSID`() = runTest {
        setConnectedSsid("OldNetwork")
        wifiConnector = buildConnector()

        val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
        every {
            connectivityManager.requestNetwork(any<NetworkRequest>(), capture(callbackSlot))
        } just Runs
        every { connectivityManager.getNetworkCapabilities(any()) } returns validatedWifiCapabilities()

        val connectionTask = async {
            wifiConnector.connect(
                ssid = "NewNetwork",
                password = "password123",
                security = "WPA2",
            )
        }
        // Advance past the suggestion-based switch wait.
        advanceTimeBy(4_500)
        runCurrent()

        callbackSlot.captured.onAvailable(mockk())
        runCurrent()

        assertFalse(connectionTask.isCompleted)

        val newNetwork = mockk<Network>()
        setConnectedSsid("NewNetwork")
        callbackSlot.captured.onAvailable(newNetwork)
        advanceUntilIdle()

        val result = connectionTask.await()
        assertTrue(result is WifiConnectResult.Success)
        val successResult = result as WifiConnectResult.Success
        assertEquals("NewNetwork", successResult.ssid)
        assertEquals(newNetwork, successResult.network)
    }

    @Test
    fun `connect completes when WifiManager reports requested SSID without another callback`() = runTest {
        wifiConnector = buildConnector()

        val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
        every {
            connectivityManager.requestNetwork(any<NetworkRequest>(), capture(callbackSlot))
        } just Runs

        val connectionTask = async {
            wifiConnector.connect(
                ssid = "LateNetwork",
                password = "password123",
                security = "WPA2",
            )
        }
        advanceTimeBy(2_500)
        runCurrent()

        assertFalse(connectionTask.isCompleted)

        setConnectedSsid("LateNetwork")
        advanceTimeBy(500)
        advanceUntilIdle()

        // The association watcher polls WifiManager every 500ms; once the
        // device reports the requested SSID, we trust the OS-level association
        // and report Success directly (internet validation happens async).
        val result = connectionTask.await()
        assertTrue(result is WifiConnectResult.Success)
        assertEquals("LateNetwork", (result as WifiConnectResult.Success).ssid)
    }

    @Test
    fun `onLost from previous WiFi is ignored while waiting for requested SSID`() = runTest {
        setConnectedSsid("OldNetwork")
        wifiConnector = buildConnector()

        val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
        every {
            connectivityManager.requestNetwork(any<NetworkRequest>(), capture(callbackSlot))
        } just Runs

        val connectionTask = async {
            wifiConnector.connect(
                ssid = "NewNetwork",
                password = "password123",
                security = "WPA2",
            )
        }
        // Advance past the suggestion-based switch wait.
        advanceTimeBy(4_500)
        runCurrent()

        callbackSlot.captured.onLost(mockk())
        runCurrent()

        assertFalse(connectionTask.isCompleted)

        setConnectedSsid("NewNetwork")
        advanceTimeBy(500)
        advanceUntilIdle()

        // Same as above — when WifiManager catches up to the requested SSID,
        // we report Success directly via the association watcher.
        val result = connectionTask.await()
        assertTrue(result is WifiConnectResult.Success)
        assertEquals("NewNetwork", (result as WifiConnectResult.Success).ssid)
    }

    @Test
    fun `switching from kept alive network back to previous SSID requests target network`() = runTest {
        wifiConnector = buildConnector()

        val callbacks = mutableListOf<ConnectivityManager.NetworkCallback>()
        every {
            connectivityManager.requestNetwork(any<NetworkRequest>(), capture(callbacks))
        } just Runs

        val connectToB = async {
            wifiConnector.connect(
                ssid = "WifiB",
                password = "password123",
                security = "WPA2",
            )
        }
        advanceTimeBy(2_500)
        runCurrent()

        val networkB = mockk<Network>()
        setConnectedSsid("WifiB")
        callbacks[0].onCapabilitiesChanged(networkB, validatedWifiCapabilities())
        advanceUntilIdle()

        assertTrue(connectToB.await() is WifiConnectResult.Success)
        assertEquals("WifiB", wifiConnector.getKeptAliveSsid())

        val connectBackToA = async {
            wifiConnector.connect(
                ssid = "WifiA",
                password = "password123",
                security = "WPA2",
            )
        }
        advanceTimeBy(4_500)
        runCurrent()

        assertEquals(2, callbacks.size)

        val networkA = mockk<Network>()
        setConnectedSsid("WifiA")
        callbacks[1].onCapabilitiesChanged(networkA, validatedWifiCapabilities())
        advanceUntilIdle()

        val result = connectBackToA.await()
        assertTrue(result is WifiConnectResult.Success)
        assertEquals("WifiA", (result as WifiConnectResult.Success).ssid)
        assertEquals("WifiA", wifiConnector.getKeptAliveSsid())
    }

    private fun validatedWifiCapabilities(): NetworkCapabilities {
        return joinedWifiCapabilities(
            hasInternet = true,
            isValidated = true,
        )
    }

    private fun joinedWifiCapabilities(
        hasInternet: Boolean = false,
        isValidated: Boolean = false,
        isCaptivePortal: Boolean = false,
    ): NetworkCapabilities {
        return mockk(relaxed = true) {
            every { hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns hasInternet
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns isValidated
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) } returns isCaptivePortal
            every { transportInfo } returns null
        }
    }

    private fun setConnectedSsid(ssid: String?) {
        connectedSsid = ssid
    }

    private fun wifiInfo(ssid: String?): WifiInfo {
        val infoSsid = ssid ?: "<unknown ssid>"
        val info = mockk<WifiInfo>(relaxed = true)
        every { info.ssid } returns infoSsid
        every { info.bssid } returns null
        every { info.networkId } returns if (ssid == null) -1 else 1
        return info
    }

    private fun buildConnector(
        onCreateSpecifier: (ssid: String, password: String?, security: String?) -> Unit = { _, _, _ -> },
    ): WifiConnector {
        return WifiConnector(
            context = context,
            createSpecifier = { ssid, password, security ->
                onCreateSpecifier(ssid, password, security)
                mockk(relaxed = true)
            },
            createRequest = { mockk(relaxed = true) },
            internetValidationGraceMillis = 0L,
        )
    }
}
