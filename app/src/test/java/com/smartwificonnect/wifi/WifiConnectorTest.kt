package com.smartwificonnect.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WifiConnectorTest {

    private lateinit var context: Context
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var wifiManager: WifiManager
    private lateinit var wifiConnector: WifiConnector

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        connectivityManager = mockk(relaxed = true)
        wifiManager = mockk(relaxed = true)

        every { context.applicationContext } returns context
        every { context.getSystemService(ConnectivityManager::class.java) } returns connectivityManager
        every { context.getSystemService(WifiManager::class.java) } returns wifiManager
        every { connectivityManager.bindProcessToNetwork(any()) } returns true
        every { connectivityManager.bindProcessToNetwork(null) } returns true
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
        runCurrent()

        val mockNetwork = mockk<Network>()
        callbackSlot.captured.onCapabilitiesChanged(mockNetwork, validatedWifiCapabilities("TestSSID"))
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
            connectivityManager.bindProcessToNetwork(mockNetwork)
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
        runCurrent()

        callbackSlot.captured.onCapabilitiesChanged(mockk(), validatedWifiCapabilities("TestSSID"))
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
        runCurrent()

        callbackSlot.captured.onCapabilitiesChanged(mockk(), validatedWifiCapabilities("OpenNetwork"))
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
    fun `connect waits when onAvailable has no stable Wi-Fi capabilities yet`() = runTest {
        wifiConnector = buildConnector()

        val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
        every {
            connectivityManager.requestNetwork(any<NetworkRequest>(), capture(callbackSlot))
        } just Runs

        val mockNetwork = mockk<Network>()
        every { connectivityManager.getNetworkCapabilities(mockNetwork) } returns null

        val connectionTask = async {
            wifiConnector.connect(
                ssid = "CafeReady",
                password = "password123",
                security = "WPA2",
            )
        }
        runCurrent()

        callbackSlot.captured.onAvailable(mockNetwork)
        runCurrent()

        assertEquals(false, connectionTask.isCompleted)
        verify(exactly = 0) {
            connectivityManager.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>())
        }

        callbackSlot.captured.onCapabilitiesChanged(mockNetwork, validatedWifiCapabilities("CafeReady"))
        advanceUntilIdle()

        val result = connectionTask.await()
        assertTrue(result is WifiConnectResult.Success)
        assertEquals("CafeReady", (result as WifiConnectResult.Success).ssid)
    }

    @Test
    fun `connect ignores stale WifiManager SSID until transport info matches target`() = runTest {
        val oldWifiInfo = mockk<WifiInfo>(relaxed = true) {
            every { ssid } returns "\"OldNetwork\""
        }
        every { wifiManager.connectionInfo } returns oldWifiInfo
        wifiConnector = buildConnector()

        val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
        every {
            connectivityManager.requestNetwork(any<NetworkRequest>(), capture(callbackSlot))
        } just Runs

        val mockNetwork = mockk<Network>()
        every {
            connectivityManager.getNetworkCapabilities(mockNetwork)
        } returns wifiCapabilitiesWithoutTransportInfo()

        val connectionTask = async {
            wifiConnector.connect(
                ssid = "TargetCafe",
                password = "password123",
                security = "WPA2",
            )
        }
        runCurrent()

        callbackSlot.captured.onAvailable(mockNetwork)
        runCurrent()

        assertEquals(false, connectionTask.isCompleted)
        verify(exactly = 0) {
            connectivityManager.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>())
        }

        callbackSlot.captured.onCapabilitiesChanged(mockNetwork, validatedWifiCapabilities("TargetCafe"))
        advanceUntilIdle()

        val result = connectionTask.await()
        assertTrue(result is WifiConnectResult.Success)
        assertEquals("TargetCafe", (result as WifiConnectResult.Success).ssid)
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
        runCurrent()

        callbackSlot.captured.onCapabilitiesChanged(mockk(), joinedWifiCapabilities(ssid = "CafeNoInternet"))
        advanceUntilIdle()

        val result = connectionTask.await()
        assertTrue(result is WifiConnectResult.ConnectedWithoutInternet)
        val joinedResult = result as WifiConnectResult.ConnectedWithoutInternet
        assertEquals("CafeNoInternet", joinedResult.ssid)
        assertEquals(false, joinedResult.hasInternetCapability)
        verify { connectivityManager.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>()) }
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
        runCurrent()

        callbackSlot.captured.onCapabilitiesChanged(
            mockk(),
            joinedWifiCapabilities(
                ssid = "CafeWithUnvalidatedInternet",
                hasInternet = true,
                isValidated = false,
            ),
        )
        advanceUntilIdle()

        val result = connectionTask.await()
        assertTrue(result is WifiConnectResult.ConnectedWithoutInternet)
        val joinedResult = result as WifiConnectResult.ConnectedWithoutInternet
        assertEquals("CafeWithUnvalidatedInternet", joinedResult.ssid)
        assertEquals(true, joinedResult.hasInternetCapability)
        verify { connectivityManager.unregisterNetworkCallback(any<ConnectivityManager.NetworkCallback>()) }
    }

    private fun validatedWifiCapabilities(ssid: String = "TestSSID"): NetworkCapabilities {
        return joinedWifiCapabilities(
            ssid = ssid,
            hasInternet = true,
            isValidated = true,
        )
    }

    private fun joinedWifiCapabilities(
        ssid: String = "TestSSID",
        hasInternet: Boolean = false,
        isValidated: Boolean = false,
        isCaptivePortal: Boolean = false,
    ): NetworkCapabilities {
        val wifiInfo = mockk<WifiInfo>(relaxed = true) {
            every { this@mockk.ssid } returns "\"$ssid\""
            every { bssid } returns "00:11:22:33:44:55"
            every { networkId } returns 42
        }
        return mockk(relaxed = true) {
            every { hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns hasInternet
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns isValidated
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) } returns isCaptivePortal
            every { transportInfo } returns wifiInfo
        }
    }

    private fun wifiCapabilitiesWithoutTransportInfo(): NetworkCapabilities {
        return mockk(relaxed = true) {
            every { hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } returns true
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns false
            every { hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) } returns false
            every { transportInfo } returns null
        }
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
