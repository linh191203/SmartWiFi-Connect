package com.example.smartwificonnect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.smartwificonnect.navigation.AppNavHost
import com.example.smartwificonnect.ui.theme.SmartWifiAppTheme

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val mainState by mainViewModel.state.collectAsState()
            SmartWifiAppTheme(darkTheme = mainState.isDarkModeEnabled) {
                AppNavHost(mainViewModel = mainViewModel)
            }
        }
    }
}
