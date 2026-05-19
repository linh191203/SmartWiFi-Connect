package com.smartwificonnect

import android.app.Application
import com.smartwificonnect.wifi.WifiConnector

/**
 * Application-scoped holder for [WifiConnector].
 *
 * The connector lives for the entire app process lifetime, NOT for a single
 * ViewModel. This is critical because [android.net.wifi.WifiNetworkSpecifier]
 * tied connections are dropped the moment the registering callback is
 * unregistered (which happens when ViewModel is cleared on screen rotation,
 * back navigation, etc.).
 *
 * By holding the connector at app level, the WiFi stays connected even when
 * the user navigates away from the OCR screen. The connection is only released
 * if:
 *   - The user explicitly turns WiFi off in system settings, OR
 *   - The user terminates the app from recents (process death), OR
 *   - The user explicitly disconnects via app UI.
 */
class SmartWifiApp : Application() {

    val wifiConnector: WifiConnector by lazy { WifiConnector(applicationContext) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        private var instance: SmartWifiApp? = null

        fun getWifiConnector(application: Application): WifiConnector {
            return (application as? SmartWifiApp)?.wifiConnector
                ?: instance?.wifiConnector
                ?: WifiConnector(application.applicationContext)
        }
    }
}
