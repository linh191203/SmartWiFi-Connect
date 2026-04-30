package com.example.smartwificonnect

import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.smartwificonnect.navigation.AppNavHost
import com.example.smartwificonnect.navigation.Routes
import com.example.smartwificonnect.ui.theme.SmartWifiAppTheme

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()
    // false only when Wi-Fi radio is completely OFF — being on but unconnected is fine
    private val wifiRadioOn = mutableStateOf(true)
    // user can dismiss the banner; resets when radio turns back on
    private val bannerDismissed = mutableStateOf(false)

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
            val showBanner = !wifiRadioOn.value && !bannerDismissed.value

            SmartWifiAppTheme(darkTheme = mainState.isDarkModeEnabled) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // App is fully usable regardless of banner state
                    AppNavHost(
                        mainViewModel = mainViewModel,
                        startDestination = startDestination,
                    )
                    AnimatedVisibility(
                        visible = showBanner,
                        modifier = Modifier.align(Alignment.TopCenter),
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding(),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            tonalElevation = 4.dp,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.WifiOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Wi-Fi đang tắt — bật để quét mạng",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = {
                                    bannerDismissed.value = true
                                    val wifiIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        Intent(Settings.Panel.ACTION_WIFI)
                                    } else {
                                        @Suppress("DEPRECATION")
                                        Intent(Settings.ACTION_WIFI_SETTINGS)
                                    }
                                    startActivity(wifiIntent)
                                }) {
                                    Text("Bật", color = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(
                                    onClick = { bannerDismissed.value = true },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Đóng",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // isWifiEnabled = true when radio is ON (even if not associated with any network)
        val isRadioOn = getSystemService(WifiManager::class.java)?.isWifiEnabled ?: true
        wifiRadioOn.value = isRadioOn
        if (isRadioOn) bannerDismissed.value = false
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
