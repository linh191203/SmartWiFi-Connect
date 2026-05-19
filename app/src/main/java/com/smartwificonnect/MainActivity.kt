package com.smartwificonnect

import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.isImmediateUpdateAllowed
import com.smartwificonnect.navigation.AppNavHost
import com.smartwificonnect.navigation.Routes
import com.smartwificonnect.data.local.PolicyConsentManager
import com.smartwificonnect.feature.home.PolicyConsentScreen
import com.smartwificonnect.feature.share.SmartWifiSharePayloadCodec
import com.smartwificonnect.ui.DisplayScaleGuard
import com.smartwificonnect.ui.theme.SmartWifiAppTheme

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()
    private lateinit var appUpdateManager: AppUpdateManager
    private lateinit var policyConsentManager: PolicyConsentManager
    // false only when Wi-Fi radio is completely OFF — being on but unconnected is fine
    private val wifiRadioOn = mutableStateOf(true)
    private val hasAcceptedPolicy = mutableStateOf(false)

    /**
     * Clamp font scale before any view inflation so layouts render as designed
     * even when the user has set Settings → Display → Font size to maximum.
     * Without this, tab labels truncate ("Trang chủ" → "Tran...") and buttons
     * overflow on devices with high default font scaling (e.g. some Vivo,
     * Samsung, Xiaomi setups).
     */
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(DisplayScaleGuard.wrap(newBase))
    }

    private val appUpdateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        // User canceled an immediate update flow; we'll prompt again on next resume.
        if (result.resultCode != RESULT_OK) {
            checkForInAppUpdate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appUpdateManager = AppUpdateManagerFactory.create(this)
        policyConsentManager = PolicyConsentManager(this)
        hasAcceptedPolicy.value = policyConsentManager.hasConsented()
        val startDestination = if (intent.isSmartWifiShareLink()) {
            mainViewModel.consumeSharedWifiLink(intent?.data)
            Routes.HOME
        } else {
            Routes.HOME
        }
        setContent {
            val mainState by mainViewModel.state.collectAsState()
            val isWifiRequired = !wifiRadioOn.value

            SmartWifiAppTheme(darkTheme = mainState.isDarkModeEnabled) {
                if (!hasAcceptedPolicy.value) {
                    BackHandler(onBack = ::exitApp)
                    PolicyConsentScreen(
                        onAccept = {
                            policyConsentManager.saveConsent()
                            hasAcceptedPolicy.value = true
                        },
                        onDecline = ::exitApp,
                    )
                } else if (isWifiRequired) {
                    WifiRequiredScreen(
                        onEnableWifi = ::openWifiSettings,
                        onExitApp = ::exitApp,
                    )
                } else {
                    AppNavHost(
                        mainViewModel = mainViewModel,
                        startDestination = startDestination,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // isWifiEnabled = true when radio is ON (even if not associated with any network)
        val isRadioOn = getSystemService(WifiManager::class.java)?.isWifiEnabled ?: true
        wifiRadioOn.value = isRadioOn
        checkForInAppUpdate()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        mainViewModel.consumeSharedWifiLink(intent.data)
    }

    private fun Intent?.isSmartWifiShareLink(): Boolean {
        val data = this?.data ?: return false
        return SmartWifiSharePayloadCodec.isSupportedUri(data)
    }

    private fun openWifiSettings() {
        runCatching {
            startActivity(Intent(Settings.Panel.ACTION_WIFI))
        }.onFailure {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
    }

    private fun exitApp() {
        finishAffinity()
    }

    private fun checkForInAppUpdate() {
        if (!isInstalledFromPlayStore()) return

        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { appUpdateInfo ->
                val shouldStartImmediateUpdate =
                    appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                        appUpdateInfo.isImmediateUpdateAllowed

                val shouldResumeImmediateUpdate =
                    appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS

                if (shouldStartImmediateUpdate || shouldResumeImmediateUpdate) {
                    val options = AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                    appUpdateManager.startUpdateFlowForResult(appUpdateInfo, appUpdateLauncher, options)
                }
            }
    }

    private fun isInstalledFromPlayStore(): Boolean {
        return try {
            val installerPackage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                packageManager.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstallerPackageName(packageName)
            }
            installerPackage == "com.android.vending"
        } catch (_: Exception) {
            false
        }
    }
}

@androidx.compose.runtime.Composable
private fun WifiRequiredScreen(
    onEnableWifi: () -> Unit,
    onExitApp: () -> Unit,
) {
    BackHandler(onBack = onExitApp)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                modifier = Modifier.size(88.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 24.dp)
                            .size(40.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Wi-Fi đang tắt",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Ứng dụng cần Wi-Fi để hoạt động. Hãy bật Wi-Fi để tiếp tục hoặc thoát ứng dụng.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onEnableWifi,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .align(Alignment.CenterHorizontally),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text("Bật Wi-Fi")
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onExitApp,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .align(Alignment.CenterHorizontally),
                shape = RoundedCornerShape(999.dp),
            ) {
                Text("Thoát ứng dụng")
            }
        }
    }
}
