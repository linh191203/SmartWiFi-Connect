package com.smartwificonnect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiOcrAutoConnectPolicyTest {

    @Test
    fun decideSsidMatch_exactNormalizedMatch_autoConnectsWithRealScannedSsid() {
        val decision = WifiOcrAutoConnectPolicy.decideSsidMatch(
            ocrSsid = "CAFE MOC",
            nearbyNetworks = listOf(NearbyNetwork(ssid = "CAFE MỘC", signalLevel = 4)),
        )

        assertTrue(decision is SsidMatchDecision.AutoConnect)
        assertEquals("CAFE MỘC", (decision as SsidMatchDecision.AutoConnect).candidate.network.ssid)
        assertEquals(1.0, decision.candidate.score, 0.0001)
    }

    @Test
    fun decideSsidMatch_fuzzyMissingPrefix_autoConnectsOnlyClearSingleMatch() {
        val decision = WifiOcrAutoConnectPolicy.decideSsidMatch(
            ocrSsid = "tel Hien Nguyen",
            nearbyNetworks = listOf(
                NearbyNetwork(ssid = "Viettel Hien Nguyen", signalLevel = 4),
                NearbyNetwork(ssid = "Guest Wifi", signalLevel = 2),
            ),
        )

        assertTrue(decision is SsidMatchDecision.AutoConnect)
        val candidate = (decision as SsidMatchDecision.AutoConnect).candidate
        assertEquals("Viettel Hien Nguyen", candidate.network.ssid)
        assertTrue(candidate.score >= SSID_AUTO_CONNECT_SCORE)
    }

    @Test
    fun decideSsidMatch_handlesVietnameseAccentsAndExtraSpaces() {
        val decision = WifiOcrAutoConnectPolicy.decideSsidMatch(
            ocrSsid = " Long   Banh Khot ",
            nearbyNetworks = listOf(NearbyNetwork(ssid = "Long Bánh Khọt", signalLevel = 4)),
        )

        assertTrue(decision is SsidMatchDecision.AutoConnect)
        assertEquals("Long Bánh Khọt", (decision as SsidMatchDecision.AutoConnect).candidate.network.ssid)
    }

    @Test
    fun decideSsidMatch_mediumOrAmbiguousMatches_requireSelection() {
        val decision = WifiOcrAutoConnectPolicy.decideSsidMatch(
            ocrSsid = "The Monday Cof fee",
            nearbyNetworks = listOf(
                NearbyNetwork(ssid = "The Monday Coffee", signalLevel = 4),
                NearbyNetwork(ssid = "The Monday Coffee 2G", signalLevel = 3),
            ),
        )

        assertTrue(decision is SsidMatchDecision.MultipleMatchesNeedSelection)
    }

    @Test
    fun validatePasswordForAutoConnect_rejectsNonAsciiWpaPassword() {
        val validation = WifiOcrAutoConnectPolicy.validatePasswordForAutoConnect(
            password = "mật khẩu123",
            securityLabel = "WPA2-PSK",
        )

        assertTrue(validation is AutoConnectPasswordValidation.Invalid)
    }

    @Test
    fun validatePasswordForAutoConnect_acceptsPrintableAsciiWpaPassword() {
        val validation = WifiOcrAutoConnectPolicy.validatePasswordForAutoConnect(
            password = "Secret123",
            securityLabel = "WPA/WPA2",
        )

        assertEquals(AutoConnectPasswordValidation.Valid, validation)
    }
}
