package com.smartwificonnect.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartwificonnect.ui.theme.SmartWifiAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun homeScreen_isDisplayed() {
        composeTestRule.setContent {
            SmartWifiAppTheme {
                HomeScreen(
                    state = HomePreviewData.default,
                    onScanQrClick = {},
                    onScanImageClick = {},
                    onManualEntryClick = {},
                    onRecentNetworkClick = {},
                    onShareClick = {},
                    onHistoryClick = {},
                    onSettingsClick = {},
                )
            }
        }

        composeTestRule
            .onNodeWithText(HomePreviewData.default.quickConnectTitle)
            .assertIsDisplayed()
    }

    @Test
    fun homeScreen_quickConnect_invokesImageScanCallback() {
        var scanImageClicked = false

        composeTestRule.setContent {
            SmartWifiAppTheme {
                HomeScreen(
                    state = HomePreviewData.default,
                    onScanQrClick = {},
                    onScanImageClick = { scanImageClicked = true },
                    onManualEntryClick = {},
                    onRecentNetworkClick = {},
                    onShareClick = {},
                    onHistoryClick = {},
                    onSettingsClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText(HomePreviewData.default.quickConnectCta).performClick()

        assertTrue(scanImageClicked)
    }

    @Test
    fun homeScreen_recentNetwork_invokesSelectionCallback() {
        var selectedNetwork: RecentNetworkUiModel? = null

        composeTestRule.setContent {
            SmartWifiAppTheme {
                HomeScreen(
                    state = HomePreviewData.default,
                    onScanQrClick = {},
                    onScanImageClick = {},
                    onManualEntryClick = {},
                    onRecentNetworkClick = { selectedNetwork = it },
                    onShareClick = {},
                    onHistoryClick = {},
                    onSettingsClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText(HomePreviewData.default.recentNetworks.first().name).performClick()

        assertEquals(HomePreviewData.default.recentNetworks.first(), selectedNetwork)
    }
}
