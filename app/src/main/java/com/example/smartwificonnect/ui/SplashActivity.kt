package com.example.smartwificonnect.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.example.smartwificonnect.MainActivity
import com.example.smartwificonnect.R
import com.example.smartwificonnect.ui.components.SplashScreen
import com.example.smartwificonnect.ui.theme.SmartWifiAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Launcher Activity (AndroidManifest).
 * Hiển thị SplashScreen rồi điều hướng sang MainActivity sau 2.2 giây.
 * Theo ui-flow.md: Splash → Home.
 */
class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartWifiAppTheme {
                SplashScreen(
                    appTitle = getString(R.string.splash_title),
                    tagline = getString(R.string.splash_tagline),
                    loadingText = getString(R.string.splash_loading),
                    versionText = getString(R.string.splash_version),
                )
            }
        }

        lifecycleScope.launch {
            delay(SPLASH_DELAY_MS)
            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            finish()
        }
    }

    companion object {
        private const val SPLASH_DELAY_MS = 2200L
    }
}
