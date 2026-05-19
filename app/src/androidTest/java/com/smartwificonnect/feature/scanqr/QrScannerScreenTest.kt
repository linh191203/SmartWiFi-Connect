package com.smartwificonnect.feature.scanqr

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartwificonnect.ui.theme.SmartWifiAppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QrScannerScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun qrScannerScreen_renders() {
        composeTestRule.setContent {
            SmartWifiAppTheme {
                QrScannerScreen(
                    onCloseClick = {},
                    onHelpClick = {},
                    onGalleryClick = {},
                    onQrCodeDetected = {},
                    onHomeClick = {},
                    onScanClick = {},
                    onShareClick = {},
                    onHistoryClick = {},
                    onSettingsClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Căn chỉnh mã QR").assertIsDisplayed()
    }

    @Test
    fun qrScannerScreen_has_close_button() {
        var closeClicked = false

        composeTestRule.setContent {
            SmartWifiAppTheme {
                QrScannerScreen(
                    onCloseClick = { closeClicked = true },
                    onHelpClick = {},
                    onGalleryClick = {},
                    onQrCodeDetected = {},
                    onHomeClick = {},
                    onScanClick = {},
                    onShareClick = {},
                    onHistoryClick = {},
                    onSettingsClick = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Đóng").performClick()

        assertTrue(closeClicked)
    }

    @Test
    fun qrScannerScreen_galleryAction_invokesCallback() {
        var galleryClicked = false

        composeTestRule.setContent {
            SmartWifiAppTheme {
                QrScannerScreen(
                    onCloseClick = {},
                    onHelpClick = {},
                    onGalleryClick = { galleryClicked = true },
                    onQrCodeDetected = {},
                    onHomeClick = {},
                    onScanClick = {},
                    onShareClick = {},
                    onHistoryClick = {},
                    onSettingsClick = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("THƯ VIỆN").performClick()

        assertTrue(galleryClicked)
    }
}
