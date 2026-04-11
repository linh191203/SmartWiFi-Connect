package com.example.smartwificonnect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.smartwificonnect.navigation.AppNavHost
import com.example.smartwificonnect.ui.theme.SmartWifiAppTheme

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartWifiAppTheme {
                AppNavHost(mainViewModel = mainViewModel)
            }
        }
    }
}
