package com.smartwificonnect.navigation

import com.smartwificonnect.MainUiState
import com.smartwificonnect.NearbyNetwork
import com.smartwificonnect.WifiConnectionState
import com.smartwificonnect.wifi.WifiConnectFailureReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanResultRouteTest {

    @Test
    fun scanResultRouteFor_keepsLoadingOnOcrResult() {
        val route = scanResultRouteFor(
            MainUiState(
                isLoading = true,
                sourceFormat = "ocr_local_review",
            ),
        )

        assertEquals(Routes.OCR_RESULT, route)
    }

    @Test
    fun scanResultRouteFor_sendsReviewFormatsToReviewScreen() {
        assertEquals(Routes.REVIEW, scanResultRouteFor(MainUiState(sourceFormat = "ocr_local_review")))
        assertEquals(Routes.REVIEW, scanResultRouteFor(MainUiState(sourceFormat = "ai_ocr")))
        assertEquals(Routes.REVIEW, scanResultRouteFor(MainUiState(sourceFormat = "ocr_server")))
    }

    @Test
    fun scanResultRouteFor_keepsConfidentFormatsOnOcrResult() {
        assertEquals(Routes.OCR_RESULT, scanResultRouteFor(MainUiState(sourceFormat = "qr_local")))
        assertEquals(Routes.OCR_RESULT, scanResultRouteFor(MainUiState(sourceFormat = "ocr_local_confident")))
        assertEquals(Routes.OCR_RESULT, scanResultRouteFor(MainUiState(sourceFormat = "share_link")))
    }

    @Test
    fun shouldAutoConnectAfterImageScan_returnsTrueForConfidentImageOcr() {
        val shouldAutoConnect = shouldAutoConnectAfterImageScan(
            MainUiState(
                scanSource = "Máy quét",
                sourceFormat = "ocr_local_confident",
                ssid = "CafeNet",
                password = "Secret123",
                nearbyNetworks = listOf(NearbyNetwork(ssid = "CafeNet", signalLevel = 4)),
            ),
        )

        assertTrue(shouldAutoConnect)
    }

    @Test
    fun shouldAutoConnectAfterImageScan_rejectsConfidentPasswordOnlyOcr() {
        val shouldAutoConnect = shouldAutoConnectAfterImageScan(
            MainUiState(
                scanSource = "Thư viện ảnh",
                sourceFormat = "ocr_local_review",
                password = "99hoanghoatham",
                confidence = 0.88,
            ),
        )

        assertFalse(shouldAutoConnect)
        assertTrue(
            shouldOpenScanResultAfterImageScan(
                MainUiState(
                    scanSource = "Thư viện ảnh",
                    sourceFormat = "ocr_local_review",
                    password = "99hoanghoatham",
                    confidence = 0.88,
                ),
            ),
        )
    }

    @Test
    fun shouldAutoConnectAfterImageScan_returnsFalseWhenAutoConnectDisabled() {
        val shouldAutoConnect = shouldAutoConnectAfterImageScan(
            MainUiState(
                autoConnectEnabled = false,
                scanSource = "Máy quét",
                sourceFormat = "ocr_local_confident",
                ssid = "CafeNet",
            ),
        )

        assertFalse(shouldAutoConnect)
        assertTrue(
            shouldOpenScanResultAfterImageScan(
                MainUiState(
                    autoConnectEnabled = false,
                    scanSource = "Máy quét",
                    sourceFormat = "ocr_local_confident",
                    ssid = "CafeNet",
                    password = "Secret123",
                ),
            ),
        )
    }

    @Test
    fun shouldAutoConnectAfterSharedWifi_returnsTrueForValidShareLink() {
        assertTrue(
            shouldAutoConnectAfterSharedWifi(
                MainUiState(
                    sourceFormat = "share_link",
                    ssid = "CafeNet",
                    password = "Secret123",
                ),
            ),
        )
    }

    @Test
    fun shouldAutoConnectAfterSharedWifi_requiresSsid() {
        assertFalse(
            shouldAutoConnectAfterSharedWifi(
                MainUiState(
                    sourceFormat = "share_link",
                    password = "Secret123",
                ),
            ),
        )
    }

    @Test
    fun shouldOpenScanResultAfterImageScan_returnsTrueForReviewFlow() {
        val shouldOpenResult = shouldOpenScanResultAfterImageScan(
            MainUiState(
                scanSource = "Thư viện ảnh",
                sourceFormat = "ocr_local_review",
                ssid = "ReviewNet",
            ),
        )

        assertTrue(shouldOpenResult)
        assertFalse(
            shouldAutoConnectAfterImageScan(
                MainUiState(
                    scanSource = "Thư viện ảnh",
                    sourceFormat = "ocr_local_review",
                    ssid = "ReviewNet",
                ),
            ),
        )
    }

    @Test
    fun shouldOpenScanResultAfterImageScan_returnsFalseOnceConnectionStarts() {
        val state = MainUiState(
            scanSource = "Máy quét",
            sourceFormat = "ocr_local_confident",
            ssid = "CafeNet",
            password = "Secret123",
            wifiConnectionState = WifiConnectionState.Connecting(ssid = "CafeNet"),
        )

        assertFalse(shouldAutoConnectAfterImageScan(state))
        assertFalse(shouldOpenScanResultAfterImageScan(state))
    }

    @Test
    fun shouldShowOcrResultAfterConnectionFailure_returnsTrueForFirstFailureOnOcrResult() {
        val state = failureState()

        assertTrue(
            shouldShowOcrResultAfterConnectionFailure(
                currentRoute = Routes.OCR_RESULT,
                state = state,
                pendingImageOcrAutoConnect = false,
                hasPendingOcrFailureReview = false,
                activeFailureRedirectedToOcrResult = false,
            ),
        )
        assertFalse(
            shouldOpenConnectionFailedScreen(
                currentRoute = Routes.OCR_RESULT,
                state = state,
                hasPendingOcrFailureReview = false,
                activeFailureRedirectedToOcrResult = false,
            ),
        )
    }

    @Test
    fun shouldOpenConnectionFailedScreen_returnsTrueForSecondFailureOnOcrResult() {
        val state = failureState()

        assertFalse(
            shouldShowOcrResultAfterConnectionFailure(
                currentRoute = Routes.OCR_RESULT,
                state = state,
                pendingImageOcrAutoConnect = false,
                hasPendingOcrFailureReview = true,
                activeFailureRedirectedToOcrResult = false,
            ),
        )
        assertTrue(
            shouldOpenConnectionFailedScreen(
                currentRoute = Routes.OCR_RESULT,
                state = state,
                hasPendingOcrFailureReview = true,
                activeFailureRedirectedToOcrResult = false,
            ),
        )
    }

    @Test
    fun shouldShowOcrResultAfterConnectionFailure_returnsTrueForFirstFailureFromReview() {
        assertTrue(
            shouldShowOcrResultAfterConnectionFailure(
                currentRoute = Routes.REVIEW,
                state = failureState(sourceFormat = "ocr_local_review"),
                pendingImageOcrAutoConnect = false,
                hasPendingOcrFailureReview = false,
                activeFailureRedirectedToOcrResult = false,
            ),
        )
    }

    @Test
    fun shouldShowOcrResultAfterConnectionFailure_returnsTrueForShareLinkFailureFromQrScanner() {
        assertTrue(
            shouldShowOcrResultAfterConnectionFailure(
                currentRoute = Routes.SCAN_QR,
                state = failureState(sourceFormat = "share_link", scanSource = "Link chia sẻ"),
                pendingImageOcrAutoConnect = false,
                hasPendingOcrFailureReview = false,
                activeFailureRedirectedToOcrResult = false,
            ),
        )
    }

    @Test
    fun shouldOpenConnectionFailedScreen_returnsTrueForInvalidSharePayload() {
        assertTrue(
            shouldOpenConnectionFailedScreen(
                currentRoute = Routes.OCR_RESULT,
                state = failureState(sourceFormat = "share_invalid", scanSource = "Link chia sẻ"),
                hasPendingOcrFailureReview = false,
                activeFailureRedirectedToOcrResult = false,
            ),
        )
    }

    @Test
    fun shouldOpenConnectionFailedScreen_keepsManualEntryFailuresDirect() {
        assertTrue(
            shouldOpenConnectionFailedScreen(
                currentRoute = Routes.MANUAL_ENTRY,
                state = failureState(scanSource = ""),
                hasPendingOcrFailureReview = false,
                activeFailureRedirectedToOcrResult = false,
            ),
        )
    }

    private fun failureState(
        sourceFormat: String = "ocr_local_confident",
        scanSource: String = "Máy quét",
    ): MainUiState {
        return MainUiState(
            ocrText = "WIFI : CafeNet\nPASS : Secret123",
            scanSource = scanSource,
            sourceFormat = sourceFormat,
            ssid = "CafeNet",
            password = "Secret123",
            wifiConnectionState = WifiConnectionState.Failed(
                reason = WifiConnectFailureReason.AUTHENTICATION_OR_UNAVAILABLE,
                message = "Không kết nối được.",
            ),
        )
    }
}
