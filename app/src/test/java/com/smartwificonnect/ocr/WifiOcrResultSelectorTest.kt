package com.smartwificonnect.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiOcrResultSelectorTest {

    @Test
    fun selectBestResult_prefersCandidateWithBothFields() {
        val result = WifiOcrResultSelector.selectBestResult(
            listOf(
                "Welcome to the cafe\nOpen daily from 7AM to 10PM\nAsk staff for Wi-Fi",
                "WIFI : The Monday Coffee\nPASS : TheMondayCoffee",
            ),
        )

        assertEquals("The Monday Coffee", result.credentials.ssid)
        assertEquals("TheMondayCoffee", result.credentials.password)
        assertEquals(
            "WIFI : The Monday Coffee\nPASS : TheMondayCoffee",
            result.text,
        )
        assertNotNull(result.confidence)
        assertTrue(result.confidence!! >= 0.84)
    }

    @Test
    fun selectBestResult_mergesSsidAndPasswordAcrossCandidates() {
        val result = WifiOcrResultSelector.selectBestResult(
            listOf(
                "THÔNG TIN\nWIFI : Chú Mập\nOPEN: 4 PM-12AM",
                "Pass: xincamon",
            ),
        )

        assertEquals("Chú Mập", result.credentials.ssid)
        assertEquals("xincamon", result.credentials.password)
        assertNotNull(result.confidence)
        assertTrue(result.confidence!! < 0.84)
    }

    @Test
    fun selectBestResult_keepsBestOverallTextWhileUsingMergedCredentials() {
        val result = WifiOcrResultSelector.selectBestResult(
            listOf(
                "Wi-Fi\nID\nbepbaha",
                "Password: babaloveu",
            ),
        )

        assertEquals("bepbaha", result.credentials.ssid)
        assertEquals("babaloveu", result.credentials.password)
        assertEquals("Wi-Fi\nID\nbepbaha", result.text)
        assertNotNull(result.confidence)
        assertTrue(result.confidence!! >= 0.60)
    }

    @Test
    fun selectBestResult_overridesWrongPasswordWhenAnotherCandidateMatchesChosenSsid() {
        val result = WifiOcrResultSelector.selectBestResult(
            listOf(
                "WIFI : The Monday Coffee\nPASS : TheMondayCofMee\nOpen daily from 7AM to 10PM",
                "Pass : TheMondayCoffee",
            ),
        )

        assertEquals("The Monday Coffee", result.credentials.ssid)
        assertEquals("TheMondayCoffee", result.credentials.password)
        assertEquals(
            "WIFI : The Monday Coffee\nPASS : TheMondayCofMee\nOpen daily from 7AM to 10PM",
            result.text,
        )
        assertNotNull(result.confidence)
        assertTrue(result.confidence!! >= 0.60)
    }
}