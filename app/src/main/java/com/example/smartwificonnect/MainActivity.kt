package com.example.smartwificonnect

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.smartwificonnect.navigation.AppNavHost
import com.example.smartwificonnect.navigation.Routes
import com.example.smartwificonnect.ui.theme.SmartWifiAppTheme

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startDestination = if (intent.isSmartWifiJoinLink()) {
            mainViewModel.consumeSharedWifiLink(intent?.data)
            Routes.OCR_RESULT
        } else {
            Routes.ONBOARDING
        }
        setContent {
            val mainState by mainViewModel.state.collectAsState()
            SmartWifiAppTheme(darkTheme = mainState.isDarkModeEnabled) {
                AppNavHost(
                    mainViewModel = mainViewModel,
                    startDestination = startDestination,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        mainViewModel.consumeSharedWifiLink(intent.data)
    }

    private fun Intent?.isSmartWifiJoinLink(): Boolean {
        val data = this?.data ?: return false
        return data.scheme == "smartwifi" && data.host == "join"
    }
}
