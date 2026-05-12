package com.smartwificonnect.feature.share

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareWifiLinkTest {

    @Test
    fun toSmartWifiLink_includesPasswordWhenPresent() {
        val model = ShareWifiUiModel(
            ssid = "Cafe WiFi",
            password = "P@ss word",
            security = "WPA2/WPA3",
        )

        val link = model.toSmartWifiLink()

        assertEquals(
            "smartwificonnect://connect?ssid=Cafe%20WiFi&security=WPA2%2FWPA3&password=P%40ss%20word",
            link,
        )
    }

    @Test
    fun toSmartWifiLink_omitsPasswordForOpenNetwork() {
        val model = ShareWifiUiModel(
            ssid = "Guest",
            password = "",
            security = "Open",
        )

        val link = model.toSmartWifiLink()

        assertEquals("smartwificonnect://connect?ssid=Guest&security=Open", link)
    }
}
