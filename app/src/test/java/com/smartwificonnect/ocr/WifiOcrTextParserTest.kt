package com.smartwificonnect.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class WifiOcrTextParserTest {

    @Test
    fun extractWifiCredentials_parsesPassWifiLabel() {
        val result = WifiOcrTextParser.extractWifiCredentials(
            "Wifi: Nguyễn Hoàng Bakery 2.4g\nPASS WIFI: 2014bakery",
        )

        assertEquals("Nguyễn Hoàng Bakery 2.4g", result.ssid)
        assertEquals("2014bakery", result.password)
    }

    @Test
    fun extractWifiCredentials_treatsSingleUsefulLineAsPassword() {
        val result = WifiOcrTextParser.extractWifiCredentials("99hoanghoatham")

        assertEquals("", result.ssid)
        assertEquals("99hoanghoatham", result.password)
    }

    @Test
    fun extractWifiCredentials_usesTwoUnlabeledLinesAsSsidAndPassword() {
        val result = WifiOcrTextParser.extractWifiCredentials("CAFE61\n61616161")

        assertEquals("CAFE61", result.ssid)
        assertEquals("61616161", result.password)
    }

    @Test
    fun extractWifiCredentials_ignoresMenuNoiseAroundWifiBlock() {
        val result = WifiOcrTextParser.extractWifiCredentials(
            """
            HỦ TIẾU BÒ KHO 45K
            MÌ GÓI BÒ KHO 45K
            THÔNG TIN
            WIFI : Chú Mập
            Pass: xincamon
            OPEN: 4 PM-12AM
            GrabFood, BeFood, ShopeeFood:
            """.trimIndent(),
        )

        assertEquals("Chú Mập", result.ssid)
        assertEquals("xincamon", result.password)
    }

    @Test
    fun extractWifiCredentials_expandsClearVietnameseRepeatPattern() {
        val result = WifiOcrTextParser.extractWifiCredentials("68 4 lần")

        assertEquals("", result.ssid)
        assertEquals("68686868", result.password)
    }

    @Test
    fun extractWifiCredentials_expandsClearMultiplierRepeatPatterns() {
        assertEquals(
            "68686868",
            WifiOcrTextParser.extractWifiCredentials("Pass: 68 x4").password,
        )
        assertEquals(
            "68686868",
            WifiOcrTextParser.extractWifiCredentials("Pass: 68 * 4").password,
        )
        assertEquals(
            "abcabcabc",
            WifiOcrTextParser.extractWifiCredentials("password: abc 3 lần").password,
        )
        assertEquals(
            "wifi123wifi123",
            WifiOcrTextParser.extractWifiCredentials("wifi123 x2").password,
        )
    }

    @Test
    fun extractWifiCredentials_parsesEscapedWifiQrPayload() {
        val result = WifiOcrTextParser.extractWifiCredentials(
            "WIFI:T:WPA;S:Cafe\\;Tang\\:2;P:p\\:ss\\;word\\\\2026;;",
        )

        assertEquals("Cafe;Tang:2", result.ssid)
        assertEquals("p:ss;word\\2026", result.password)
    }

    @Test
    fun extractWifiCredentials_parsesWifiNameWithoutColonFromReceipt() {
        val result = WifiOcrTextParser.extractWifiCredentials(
            "Wifi \"The Moods\"\nPass : xincamon",
        )

        assertEquals("The Moods", result.ssid)
        assertEquals("xincamon", result.password)
    }

    @Test
    fun extractWifiCredentials_repairsCameraIconPrefixBeforePassword() {
        val result = WifiOcrTextParser.extractWifiCredentials(
            "Name: DONG PHUC LINH TRAN\nPassword: W-Alinhtran103",
        )

        assertEquals("DONG PHUC LINH TRAN", result.ssid)
        assertEquals("linhtran103", result.password)
    }

    @Test
    fun extractWifiCredentials_parsesNamePasswordSplitAcrossColumns() {
        val result = WifiOcrTextParser.extractWifiCredentials(
            "Name : Password :\nCAFE MOC    Cf222222",
        )

        assertEquals("CAFE MOC", result.ssid)
        assertEquals("Cf222222", result.password)
    }

    @Test
    fun extractWifiCredentials_stripsRepeatedSsidPrefixFromPassword() {
        val result = WifiOcrTextParser.extractWifiCredentials(
            """
            WiFi: IP OF SON
            Password: IPOFSON:123456789
            """.trimIndent(),
        )

        assertEquals("IP OF SON", result.ssid)
        assertEquals("123456789", result.password)
    }

    @Test
    fun extractWifiCredentials_cleansNoisySsidAndEmbeddedMkPassword() {
        val result = WifiOcrTextParser.extractWifiCredentials(
            """
            D: JUN JUN QUA
            :JUNJUNQUA:MK:123456789
            """.trimIndent(),
        )

        assertEquals("JUN JUN QUA", result.ssid)
        assertEquals("123456789", result.password)
    }
}
