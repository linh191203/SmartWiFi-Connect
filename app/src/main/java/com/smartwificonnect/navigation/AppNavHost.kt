package com.smartwificonnect.navigation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.smartwificonnect.MainUiState
import com.smartwificonnect.MainViewModel
import com.smartwificonnect.R
import com.smartwificonnect.SharedWifiLinkResult
import com.smartwificonnect.WifiConnectionState
import com.smartwificonnect.feature.connection.ConnectionFailedScreen
import com.smartwificonnect.feature.history.HistoryScreen
import com.smartwificonnect.feature.home.HomePreviewData
import com.smartwificonnect.feature.home.HomeScreen
import com.smartwificonnect.feature.home.HomeUiState
import com.smartwificonnect.feature.home.RecentNetworkType
import com.smartwificonnect.feature.home.RecentNetworkUiModel
import com.smartwificonnect.feature.manual.ManualEntryScreen
import com.smartwificonnect.feature.networkdetail.NetworkDetailScreen
import com.smartwificonnect.feature.permission.CameraPermissionScreen
import com.smartwificonnect.feature.scanimage.ImagePickerScreen
import com.smartwificonnect.feature.scanimage.ImageScanScreen
import com.smartwificonnect.feature.scanimage.OcrResultScreen
import com.smartwificonnect.feature.scanqr.QrScannerScreen
import com.smartwificonnect.feature.share.ShareWifiScreen
import com.smartwificonnect.feature.share.ShareWifiUiModel
import com.smartwificonnect.feature.review.ReviewScreen
import com.smartwificonnect.feature.settings.SettingsScreen
import java.util.Locale

private val removedAccountRoutes = setOf(
    "login",
    "register",
    "forgot_password",
    "profile",
    "onboarding",
    "policy_consent",
)

@Composable
fun AppNavHost(
    mainViewModel: MainViewModel,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.HOME,
) {
    val context = LocalContext.current
    val mainState by mainViewModel.state.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var awaitingImageOcrResult by remember { mutableStateOf(false) }
    var pendingImageOcrAutoConnect by remember { mutableStateOf(false) }
    var lastSharedAutoConnectKey by remember { mutableStateOf<String?>(null) }
    var pendingCameraRoute by remember { mutableStateOf<String?>(null) }
    val currentOcrFailureReviewKey = remember(
        mainState.ocrText,
        mainState.scanSource,
        mainState.sourceFormat,
    ) {
        ocrFailureReviewKeyFor(mainState)
    }
    val currentSharedWifiKey = remember(
        mainState.sourceFormat,
        mainState.ocrText,
        mainState.ssid,
        mainState.password,
        mainState.security,
    ) {
        sharedWifiAutoConnectKeyFor(mainState)
    }
    var pendingOcrFailureReviewKey by remember { mutableStateOf<String?>(null) }
    var activeFailureRedirectedToOcrResult by remember { mutableStateOf(false) }
    val hasPendingOcrFailureReview =
        pendingOcrFailureReviewKey != null && pendingOcrFailureReviewKey == currentOcrFailureReviewKey
    val resolvedStartDestination = remember(startDestination) {
        when {
            startDestination in removedAccountRoutes ->
                Routes.HOME
            else -> startDestination
        }
    }
    val scanTriggerRoutes = setOf(Routes.SCAN_IMAGE, Routes.SCAN_QR)
    val openHome: () -> Unit = {
        navController.navigate(Routes.HOME) {
            popUpTo(Routes.HOME) { inclusive = true }
        }
    }
    val openOcrResult: () -> Unit = {
        navController.navigate(Routes.OCR_RESULT) {
            launchSingleTop = true
        }
    }
    val openRouteWithCameraPermission: (String) -> Unit = { nextRoute ->
        val cameraGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

        if (cameraGranted) {
            navController.navigate(nextRoute)
        } else {
            navController.navigate(Routes.cameraPermissionRoute(nextRoute))
        }
    }
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) {
            awaitingImageOcrResult = false
            mainViewModel.onImageSelectionCanceled()
            return@rememberLauncherForActivityResult
        }
        awaitingImageOcrResult = true
        mainViewModel.startOcrFromGallery(uri)
    }
    val nearbyWifiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        mainViewModel.refreshNearbyWifiNetworks(recalculateFuzzy = true)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val nextRoute = pendingCameraRoute ?: Routes.SCAN_QR
        pendingCameraRoute = null
        navController.popBackStack()
        if (granted) {
            navController.navigate(nextRoute)
        } else {
            mainViewModel.postTransientUserMessage("Cần cấp quyền camera để quét QR hoặc chụp ảnh OCR.")
        }
    }
    val connectWifiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val allGranted = wifiConnectPermissions().all { permission ->
            permissions[permission] == true ||
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            mainViewModel.connectToParsedWifi()
        } else {
            mainViewModel.onWifiConnectionPermissionDenied()
        }
    }
    val refreshNearbyWifi: () -> Unit = {
        val missingPermissions = nearbyWifiPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isEmpty()) {
            mainViewModel.refreshNearbyWifiNetworks(recalculateFuzzy = true)
        } else {
            nearbyWifiPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
    val connectWifiWithPermission: () -> Unit = {
        val missingPermissions = wifiConnectPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isEmpty()) {
            mainViewModel.connectToParsedWifi()
        } else {
            connectWifiPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
    val openSystemNetworkSettings: () -> Unit = {
        val preferredIntent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        runCatching {
            context.startActivity(preferredIntent)
        }.onFailure {
            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
    }
    val openAppNotificationSettings: () -> Unit = {
        val notificationIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        runCatching {
            context.startActivity(notificationIntent)
        }.onFailure {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                },
            )
        }
    }
    val showQrHelp: () -> Unit = {
        mainViewModel.postTransientUserMessage("Đưa mã QR Wi-Fi vào giữa khung quét hoặc mở ảnh từ thư viện.")
    }
    val homeState = buildHomeState(mainState, null)

    LaunchedEffect(mainState.transientUserMessage) {
        val message = mainState.transientUserMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        mainViewModel.consumeTransientUserMessage()
    }

    LaunchedEffect(
        currentSharedWifiKey,
        mainState.isLoading,
        mainState.wifiConnectionState,
    ) {
        if (
            currentSharedWifiKey != null &&
            currentSharedWifiKey != lastSharedAutoConnectKey &&
            shouldAutoConnectAfterSharedWifi(mainState)
        ) {
            lastSharedAutoConnectKey = currentSharedWifiKey
            connectWifiWithPermission()
        }
    }

    LaunchedEffect(mainState.sourceFormat, currentRoute) {
        if (mainState.sourceFormat in sharedWifiAutoConnectFormats && currentRoute != Routes.OCR_RESULT) {
            navController.navigate(Routes.OCR_RESULT) {
                launchSingleTop = true
            }
        } else if (mainState.sourceFormat == "share_invalid" && currentRoute != Routes.CONNECTION_FAILED) {
            navController.navigate(Routes.CONNECTION_FAILED) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(currentOcrFailureReviewKey) {
        if (currentOcrFailureReviewKey != pendingOcrFailureReviewKey) {
            pendingOcrFailureReviewKey = null
            activeFailureRedirectedToOcrResult = false
        }
    }

    LaunchedEffect(mainState.wifiConnectionState, currentRoute) {
        when (mainState.wifiConnectionState) {
            is WifiConnectionState.Failed -> {
                when {
                    // Auto-connect from OCR failed → go straight to CONNECTION_FAILED
                    // with "Quét lại" button. No intermediate OCR result screen.
                    pendingImageOcrAutoConnect && currentRoute in scanTriggerRoutes -> {
                        pendingImageOcrAutoConnect = false
                        pendingOcrFailureReviewKey = null
                        activeFailureRedirectedToOcrResult = false
                        navController.navigate(Routes.CONNECTION_FAILED) {
                            launchSingleTop = true
                        }
                    }

                    shouldOpenConnectionFailedScreen(
                        currentRoute = currentRoute,
                        state = mainState,
                        hasPendingOcrFailureReview = hasPendingOcrFailureReview,
                        activeFailureRedirectedToOcrResult = activeFailureRedirectedToOcrResult,
                    ) -> {
                        pendingImageOcrAutoConnect = false
                        pendingOcrFailureReviewKey = null
                        activeFailureRedirectedToOcrResult = false
                        navController.navigate(Routes.CONNECTION_FAILED) {
                            launchSingleTop = true
                        }
                    }

                    pendingImageOcrAutoConnect -> {
                        pendingImageOcrAutoConnect = false
                    }
                }
            }

            is WifiConnectionState.ConnectedWithoutInternet -> {
                pendingOcrFailureReviewKey = null
                activeFailureRedirectedToOcrResult = false
                when {
                    pendingImageOcrAutoConnect && currentRoute in scanTriggerRoutes -> {
                        pendingImageOcrAutoConnect = false
                        if (currentRoute != Routes.OCR_RESULT) {
                            navController.navigate(Routes.OCR_RESULT) {
                                launchSingleTop = true
                            }
                        }
                    }

                    mainState.sourceFormat in sharedWifiAutoConnectFormats &&
                        currentRoute != Routes.OCR_RESULT -> {
                        pendingImageOcrAutoConnect = false
                        navController.navigate(Routes.OCR_RESULT) {
                            launchSingleTop = true
                        }
                    }

                    currentRoute == Routes.CONNECTION_FAILED -> {
                        pendingImageOcrAutoConnect = false
                        navController.popBackStack()
                    }
                }
            }

            is WifiConnectionState.Connected -> {
                pendingOcrFailureReviewKey = null
                activeFailureRedirectedToOcrResult = false
                when {
                    mainState.sourceFormat in sharedWifiAutoConnectFormats &&
                        currentRoute in shareSuccessReturnRoutes -> {
                        pendingImageOcrAutoConnect = false
                        openHome()
                    }

                    pendingImageOcrAutoConnect && currentRoute in scanTriggerRoutes -> {
                        pendingImageOcrAutoConnect = false
                        openHome()
                    }

                    currentRoute == Routes.CONNECTION_FAILED -> {
                        pendingImageOcrAutoConnect = false
                        navController.popBackStack()
                    }

                    // Manual click Connect from OCR_RESULT or REVIEW screens.
                    // After a real successful connection, take the user back
                    // to the Home (final) screen instead of stranding them
                    // on the credential editor.
                    currentRoute == Routes.OCR_RESULT ||
                        currentRoute == Routes.REVIEW ||
                        currentRoute == Routes.MANUAL_ENTRY -> {
                        pendingImageOcrAutoConnect = false
                        openHome()
                    }

                    pendingImageOcrAutoConnect -> {
                        pendingImageOcrAutoConnect = false
                    }
                }
            }

            is WifiConnectionState.Connecting,
            WifiConnectionState.Idle,
            -> {
                activeFailureRedirectedToOcrResult = false
            }
        }
    }

    LaunchedEffect(
        awaitingImageOcrResult,
        currentRoute,
        mainState.isLoading,
        mainState.scanSource,
        mainState.sourceFormat,
        mainState.ssid,
        mainState.autoConnectEnabled,
        mainState.wifiConnectionState,
    ) {
        if (!awaitingImageOcrResult || currentRoute !in scanTriggerRoutes || mainState.isLoading) {
            return@LaunchedEffect
        }

        awaitingImageOcrResult = false

        when {
            shouldAutoConnectAfterImageScan(mainState) -> {
                pendingImageOcrAutoConnect = true
                connectWifiWithPermission()
            }

            shouldOpenScanResultAfterImageScan(mainState) -> {
                navController.navigate(scanResultRouteFor(mainState)) {
                    launchSingleTop = true
                }
            }
        }
    }

    LaunchedEffect(mainState, currentRoute) {
        if (
            currentRoute == Routes.OCR_RESULT &&
            scanResultRouteFor(mainState) == Routes.REVIEW &&
            !hasPendingOcrFailureReview &&
            !activeFailureRedirectedToOcrResult &&
            // Skip the REVIEW detour entirely when we already have full credentials
            // AND the SSID is in nearby networks. The user wants the connect sheet
            // to appear right after OCR — REVIEW would just add an extra screen
            // (and thus an extra Connect button) on top of OCR_RESULT.
            !shouldAutoConnectAfterImageScan(mainState) &&
            !hasFullCredentialsAndNearbyMatch(mainState)
        ) {
            navController.navigate(Routes.REVIEW) {
                launchSingleTop = true
                popUpTo(Routes.OCR_RESULT) {
                    inclusive = true
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = resolvedStartDestination,
    ) {

        composable(Routes.HOME) {
            HomeScreen(
                state = homeState,
                onScanQrClick = { openRouteWithCameraPermission(Routes.SCAN_QR) },
                onScanImageClick = { openRouteWithCameraPermission(Routes.SCAN_IMAGE) },
                onManualEntryClick = { navController.navigate(Routes.MANUAL_ENTRY) },
                onRecentNetworkClick = { network ->
                    mainViewModel.openNetworkDetailFromRecent(network)
                    navController.navigate(Routes.NETWORK_DETAIL)
                },
                onShareClick = { navController.navigate(Routes.SHARE) },
                onHistoryClick = { navController.navigate(Routes.HISTORY) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
            )
        }


        composable(
            route = Routes.CAMERA_PERMISSION_PATTERN,
            arguments = listOf(
                navArgument("next") {
                    type = NavType.StringType
                    defaultValue = Routes.SCAN_QR
                },
            ),
        ) { backStackEntry ->
            val rawNext = backStackEntry.arguments?.getString("next")
            val nextRoute = when (rawNext) {
                Routes.SCAN_IMAGE -> Routes.SCAN_IMAGE
                else -> Routes.SCAN_QR
            }
            val cameraGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED

            if (cameraGranted) {
                LaunchedEffect(nextRoute) {
                    navController.popBackStack()
                    navController.navigate(nextRoute)
                }
            } else {
                CameraPermissionScreen(
                    onAllowClick = {
                        pendingCameraRoute = nextRoute
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onDenyClick = {
                        navController.popBackStack()
                    },
                )
            }
        }

        composable(Routes.SCAN_QR) {
            QrScannerScreen(
                onCloseClick = { navController.popBackStack() },
                onHelpClick = showQrHelp,
                onGalleryClick = { pickImageLauncher.launch("image/*") },
                onQrCodeDetected = { rawQrText ->
                    when (mainViewModel.consumeSharedWifiLink(rawQrText.trim().toUri())) {
                        SharedWifiLinkResult.CONSUMED -> Unit
                        SharedWifiLinkResult.INVALID -> {
                            navController.navigate(Routes.CONNECTION_FAILED) {
                                launchSingleTop = true
                            }
                        }
                        SharedWifiLinkResult.NOT_SUPPORTED -> {
                            mainViewModel.consumeRecognizedText(rawQrText)
                            openOcrResult()
                        }
                    }
                },
                onHomeClick = openHome,
                onScanClick = {},
                onShareClick = { navController.navigate(Routes.SHARE) },
                onHistoryClick = { navController.navigate(Routes.HISTORY) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SCAN_IMAGE) {
            ImageScanScreen(
                onCloseClick = {
                    mainViewModel.cancelOcr()
                    navController.popBackStack()
                },
                onCaptureClick = { bitmap ->
                    awaitingImageOcrResult = true
                    mainViewModel.startOcrFromCamera(bitmap)
                },
                onCaptureUnavailable = mainViewModel::onCameraPreviewUnavailable,
                onSwitchToQrClick = { navController.navigate(Routes.SCAN_QR) },
                onOpenGalleryClick = { pickImageLauncher.launch("image/*") },
                onHomeClick = openHome,
                onScanClick = {},
                onShareClick = { navController.navigate(Routes.SHARE) },
                onHistoryClick = { navController.navigate(Routes.HISTORY) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                isOcrLoading = mainState.isLoading,
                ocrLoadingMessage = mainState.statusMessage,
            )
        }
        composable(Routes.IMAGE_PICKER) {
            ImagePickerScreen(
                onBackClick = { navController.popBackStack() },
                onContinueClick = {
                    pickImageLauncher.launch("image/*")
                },
            )
        }
        composable(Routes.OCR_RESULT) {
            LaunchedEffect(mainState.isLoading, mainState.ssid, mainState.password) {
                if (!mainState.isLoading && (mainState.ssid.isNotBlank() || mainState.password.isNotBlank())) {
                    refreshNearbyWifi()
                }
            }
            OcrResultScreen(
                state = mainState,
                onBackClick = { navController.popBackStack() },
                onSsidChange = mainViewModel::onSsidChanged,
                onPasswordChange = mainViewModel::onPasswordChanged,
                onRefreshNearbyWifi = refreshNearbyWifi,
                onSelectNearbyNetwork = mainViewModel::selectNearbyNetwork,
                onConnectWifi = connectWifiWithPermission,
            )
        }
        composable(Routes.MANUAL_ENTRY) {
            ManualEntryScreen(
                state = mainState,
                onBackClick = { navController.popBackStack() },
                onSsidChange = mainViewModel::onSsidChanged,
                onSecurityChange = mainViewModel::onSecurityChanged,
                onPasswordChange = mainViewModel::onPasswordChanged,
                onConnectAndSaveClick = connectWifiWithPermission,
                onCancelClick = { navController.popBackStack() },
            )
        }
        composable(Routes.CONNECTION_FAILED) {
            val failureMessage = (mainState.wifiConnectionState as? WifiConnectionState.Failed)?.message
                ?: mainState.statusMessage
            ConnectionFailedScreen(
                isRetrying = mainState.wifiConnectionState is WifiConnectionState.Connecting,
                message = failureMessage,
                onCloseClick = {
                    mainViewModel.clearWifiConnectionState()
                    if (!navController.popBackStack()) {
                        openHome()
                    }
                },
                onRetryClick = {
                    mainViewModel.clearWifiConnectionState()
                    if (!navController.popBackStack()) {
                        openHome()
                    }
                },
                onNetworkSettingsClick = {
                    mainViewModel.clearWifiConnectionState()
                    openSystemNetworkSettings()
                },
                onHomeClick = {
                    mainViewModel.clearWifiConnectionState()
                    openHome()
                },
                onScanClick = {
                    mainViewModel.clearWifiConnectionState()
                    openRouteWithCameraPermission(Routes.SCAN_QR)
                },
                onShareClick = {
                    mainViewModel.clearWifiConnectionState()
                    navController.navigate(Routes.SHARE)
                },
                onHistoryClick = {
                    mainViewModel.clearWifiConnectionState()
                    navController.navigate(Routes.HISTORY)
                },
                onSettingsClick = {
                    mainViewModel.clearWifiConnectionState()
                    navController.navigate(Routes.SETTINGS)
                },
            )
        }
        composable(Routes.SHARE) {
            ShareWifiScreen(
                network = mainState.toShareWifiUiModel(),
                onBackClick = { navController.popBackStack() },
                onHomeClick = openHome,
                onScanClick = { openRouteWithCameraPermission(Routes.SCAN_QR) },
                onShareClick = {},
                onHistoryClick = { navController.navigate(Routes.HISTORY) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.NETWORK_DETAIL) {
            NetworkDetailScreen(
                detail = mainState.selectedNetworkDetail,
                liveTelemetry = mainState.selectedNetworkTelemetry,
                isConnecting = mainState.wifiConnectionState is WifiConnectionState.Connecting,
                onBackClick = {
                    mainViewModel.clearSelectedNetworkDetail()
                    navController.popBackStack()
                },
                onConnectClick = mainViewModel::connectToSelectedNetworkDetail,
                onDeleteClick = {
                    mainViewModel.deleteSelectedNetworkDetail()
                    navController.popBackStack()
                },
                onRefreshTelemetry = mainViewModel::refreshSelectedNetworkTelemetry,
                onHomeClick = openHome,
                onScanClick = { openRouteWithCameraPermission(Routes.SCAN_QR) },
                onShareClick = { navController.navigate(Routes.SHARE) },
                onHistoryClick = { navController.navigate(Routes.HISTORY) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.REVIEW) {
            LaunchedEffect(mainState.isLoading, mainState.ssid, mainState.password) {
                if (!mainState.isLoading && (mainState.ssid.isNotBlank() || mainState.password.isNotBlank())) {
                    refreshNearbyWifi()
                }
            }
            ReviewScreen(
                state = mainState,
                onBackClick = { navController.popBackStack() },
                onSsidChange = mainViewModel::onSsidChanged,
                onPasswordChange = mainViewModel::onPasswordChanged,
                onRefreshNearbyWifi = refreshNearbyWifi,
                onSelectNearbyNetwork = mainViewModel::selectNearbyNetwork,
                onConnectClick = connectWifiWithPermission,
                onScanAgainClick = {
                    navController.popBackStack()
                    openRouteWithCameraPermission(Routes.SCAN_QR)
                },
                onApplyAiSuggestion = { suggestedSsid, suggestedPassword ->
                    mainViewModel.onSsidChanged(suggestedSsid)
                    mainViewModel.onPasswordChanged(suggestedPassword)
                },
            )
        }
        composable(Routes.HISTORY) {
            LaunchedEffect(Unit) {
                mainViewModel.refreshHistory()
            }
            HistoryScreen(
                records = mainState.historyRecords,
                    statusMessage = mainState.statusMessage.let { msg ->
                        if (msg == "Sẵn sàng quét OCR Wi-Fi") "" else msg
                    },
                onNetworkClick = { record ->
                    mainViewModel.openNetworkDetailFromHistory(record)
                    navController.navigate(Routes.NETWORK_DETAIL)
                },
                onHomeClick = openHome,
                onScanClick = { openRouteWithCameraPermission(Routes.SCAN_QR) },
                onShareClick = { navController.navigate(Routes.SHARE) },
                onHistoryClick = {},
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                isDarkModeEnabled = mainState.isDarkModeEnabled,
                autoConnectEnabled = mainState.autoConnectEnabled,
                historyCount = mainState.historyRecords.size,
                onDarkModeChange = mainViewModel::onDarkModeChanged,
                onAutoConnectChange = mainViewModel::onAutoConnectPreferenceChanged,
                onNotificationSettingsClick = openAppNotificationSettings,
                onClearHistoryClick = mainViewModel::clearAllHistory,
                onHomeClick = openHome,
                onScanClick = { openRouteWithCameraPermission(Routes.SCAN_QR) },
                onShareClick = { navController.navigate(Routes.SHARE) },
                onHistoryClick = { navController.navigate(Routes.HISTORY) },
                onSettingsClick = {},
            )
        }
    }
}

internal fun scanResultRouteFor(state: MainUiState): String {
    val passwordOnlyOcr = state.sourceFormat == "ocr_local_review" &&
        state.ssid.isBlank() &&
        state.password.isNotBlank()
    return if (!state.isLoading && state.sourceFormat in reviewSourceFormats && !passwordOnlyOcr) {
        Routes.REVIEW
    } else {
        Routes.OCR_RESULT
    }
}

internal fun ocrFailureReviewKeyFor(state: MainUiState): String? {
    val keyParts = listOf(
        state.scanSource.trim(),
        state.sourceFormat.trim(),
        state.ocrText.trim(),
    )
    return if (keyParts.any { it.isNotBlank() }) {
        keyParts.joinToString("::")
    } else {
        null
    }
}

internal fun shouldShowOcrResultAfterConnectionFailure(
    currentRoute: String?,
    state: MainUiState,
    pendingImageOcrAutoConnect: Boolean,
    hasPendingOcrFailureReview: Boolean,
    activeFailureRedirectedToOcrResult: Boolean,
): Boolean {
    if (state.wifiConnectionState !is WifiConnectionState.Failed) return false
    if (activeFailureRedirectedToOcrResult || hasPendingOcrFailureReview) return false

    return when {
        pendingImageOcrAutoConnect && currentRoute in scanTriggerRoutesForFailures -> true
        state.sourceFormat in sharedWifiAutoConnectFormats && ocrFailureReviewKeyFor(state) != null -> true
        currentRoute in ocrFailureReviewRoutes && ocrFailureReviewKeyFor(state) != null -> true
        else -> false
    }
}

internal fun shouldOpenConnectionFailedScreen(
    currentRoute: String?,
    state: MainUiState,
    hasPendingOcrFailureReview: Boolean,
    activeFailureRedirectedToOcrResult: Boolean,
): Boolean {
    if (state.wifiConnectionState !is WifiConnectionState.Failed) return false
    if (state.sourceFormat == "share_invalid") return currentRoute != Routes.CONNECTION_FAILED
    if (currentRoute == Routes.OCR_RESULT) {
        return hasPendingOcrFailureReview && !activeFailureRedirectedToOcrResult
    }
    return currentRoute in directFailureRoutes
}

internal fun shouldAutoConnectAfterImageScan(state: MainUiState): Boolean {
    val hasConnectableCredentials = state.ssid.isNotBlank() && state.password.isNotBlank()
    val hasExactScannedSsid = state.nearbyNetworks.any { network ->
        network.ssid.equals(state.ssid, ignoreCase = true)
    }
    // Auto-connect immediately after OCR when we have SSID + password and
    // the SSID is visible in nearby scan. No intermediate "OCR result" screen.
    // If connect fails → CONNECTION_FAILED screen with "Quét lại" button.
    val isOcrSource = state.sourceFormat in imageOcrResultFormats
    return !state.isLoading &&
        state.autoConnectEnabled &&
        hasConnectableCredentials &&
        isOcrSource &&
        hasExactScannedSsid &&
        state.wifiConnectionState == WifiConnectionState.Idle
}

internal fun shouldAutoConnectAfterSharedWifi(state: MainUiState): Boolean {
    return !state.isLoading &&
        state.sourceFormat in sharedWifiAutoConnectFormats &&
        state.ssid.isNotBlank() &&
        state.wifiConnectionState == WifiConnectionState.Idle
}

internal fun shouldOpenScanResultAfterImageScan(state: MainUiState): Boolean {
    return !state.isLoading &&
        state.sourceFormat in imageOcrResultFormats &&
        state.wifiConnectionState == WifiConnectionState.Idle &&
        !shouldAutoConnectAfterImageScan(state)
}

/**
 * True when the OCR result already has a usable SSID + password and the SSID
 * is currently visible in the device's nearby network scan. In that case
 * we skip the REVIEW detour and let the user act on a single connect sheet
 * (OCR_RESULT) — no duplicate Connect buttons, no extra hop.
 */
internal fun hasFullCredentialsAndNearbyMatch(state: MainUiState): Boolean {
    val hasCreds = state.ssid.isNotBlank() && state.password.isNotBlank()
    if (!hasCreds) return false
    return state.nearbyNetworks.any { it.ssid.equals(state.ssid, ignoreCase = true) }
}

private fun sharedWifiAutoConnectKeyFor(state: MainUiState): String? {
    return if (state.sourceFormat in sharedWifiAutoConnectFormats && state.ssid.isNotBlank()) {
        listOf(
            state.sourceFormat.trim(),
            state.ocrText.trim(),
            state.ssid.trim(),
            state.password,
            state.security.trim(),
        ).joinToString("::")
    } else {
        null
    }
}

private val reviewSourceFormats = setOf("ocr_local_review", "ai_ocr", "ocr_server")
private val imageOcrResultFormats = setOf("ocr_local_confident", "ocr_local_review", "ai_ocr", "ocr_server")
private val sharedWifiAutoConnectFormats = setOf("share_link")
private val ocrFailureReviewRoutes = setOf(Routes.OCR_RESULT, Routes.REVIEW)
private val directFailureRoutes = setOf(Routes.MANUAL_ENTRY, Routes.NETWORK_DETAIL)
private val scanTriggerRoutesForFailures = setOf(Routes.SCAN_IMAGE, Routes.SCAN_QR)
private val shareSuccessReturnRoutes = setOf(Routes.SCAN_QR, Routes.OCR_RESULT)

private fun nearbyWifiPermissions(): List<String> {
    return buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }
}

private fun wifiConnectPermissions(): List<String> {
    return nearbyWifiPermissions()
}

private fun MainUiState.toShareWifiUiModel(): ShareWifiUiModel? {
    val connectedSsid = (wifiConnectionState as? WifiConnectionState.Connected)?.ssid
    val connectedRecord = connectedSsid?.let { activeSsid ->
        historyRecords.firstOrNull { it.ssid.equals(activeSsid, ignoreCase = true) }
    }
    val fallbackRecord = historyRecords.firstOrNull { it.ssid.isNotBlank() }

    if (connectedSsid != null && ssid.equals(connectedSsid, ignoreCase = true)) {
        return ShareWifiUiModel(
            ssid = connectedSsid,
            password = password,
            security = security.ifBlank { if (password.isBlank()) "Open" else "WPA/WPA2" },
        )
    }

    val sourceRecord = connectedRecord ?: fallbackRecord ?: return null
    return ShareWifiUiModel(
        ssid = sourceRecord.ssid,
        password = sourceRecord.password,
        security = if (sourceRecord.password.isBlank()) "Open" else "WPA/WPA2",
    )
}

private fun buildHomeState(mainState: MainUiState, userFullName: String?): HomeUiState {
    val fallback = HomePreviewData.default
    val normalizedName = userFullName?.trim().orEmpty()
    val displayName = normalizedName.ifBlank { "Bạn" }
    // Prefer the REAL device-level SSID over the in-app connection state.
    // This way the "Đang kết nối tới ..." line reflects what the OS is
    // actually associated with — even if the user toggled WiFi from the
    // system settings panel, switched networks outside the app, or the
    // app-driven connection was released. Fall back to the in-app
    // Connected state's SSID only when the OS reports nothing (Vivo
    // location-off masking).
    val liveSsid = mainState.liveConnectedSsid?.takeIf { it.isNotBlank() }
    val inAppSsid = (mainState.wifiConnectionState as? WifiConnectionState.Connected)?.ssid
    val connectedSsid = liveSsid ?: inAppSsid
    val recentFromHistory = mainState.historyRecords
        .filter { it.ssid.isNotBlank() }
        .distinctBy { it.ssid.lowercase(Locale.ROOT) }
        .take(3)
        .map { record ->
            RecentNetworkUiModel(
                name = record.ssid,
                lastConnectedLabel = "Kết nối lần cuối ${record.createdAtMillis.toRelativeLabel()}",
                type = when {
                    record.ssid.contains("office", ignoreCase = true) -> RecentNetworkType.ROUTER
                    record.password.isBlank() -> RecentNetworkType.BUILDING
                    else -> RecentNetworkType.WIFI
                },
                sourceRecordId = record.id,
                isConnected = connectedSsid?.equals(record.ssid, ignoreCase = true) == true,
            )
        }
    val recentNetworks = if (recentFromHistory.isNotEmpty()) recentFromHistory else fallback.recentNetworks
    return fallback.copy(
        greeting = "Xin chào, $displayName!",
        connectivityStatus = connectedSsid?.let { "Đang kết nối tới $it." } ?: fallback.connectivityStatus,
        recentNetworks = recentNetworks,
    )
}

private fun Long.toRelativeLabel(): String {
    val diffMillis = System.currentTimeMillis() - this
    val hours = diffMillis / 3_600_000L
    val days = diffMillis / 86_400_000L
    return when {
        hours < 1 -> "vài phút trước"
        hours < 24 -> "$hours giờ trước"
        days == 1L -> "hôm qua"
        days < 7 -> "$days ngày trước"
        else -> "gần đây"
    }
}
