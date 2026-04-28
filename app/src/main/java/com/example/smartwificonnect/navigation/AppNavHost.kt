package com.example.smartwificonnect.navigation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.core.content.ContextCompat
import com.example.smartwificonnect.MainUiState
import com.example.smartwificonnect.MainViewModel
import com.example.smartwificonnect.R
import com.example.smartwificonnect.WifiConnectionState
import com.example.smartwificonnect.feature.connection.ConnectionFailedScreen
import com.example.smartwificonnect.feature.history.HistoryScreen
import com.example.smartwificonnect.feature.home.HomePreviewData
import com.example.smartwificonnect.feature.home.HomeScreen
import com.example.smartwificonnect.feature.home.HomeUiState
import com.example.smartwificonnect.feature.home.LoginScreen
import com.example.smartwificonnect.feature.home.LoginUiState
import com.example.smartwificonnect.feature.home.OnboardingScreen
import com.example.smartwificonnect.feature.home.OnboardingUiState
import com.example.smartwificonnect.feature.home.RecentNetworkType
import com.example.smartwificonnect.feature.home.RecentNetworkUiModel
import com.example.smartwificonnect.feature.home.RegisterScreen
import com.example.smartwificonnect.feature.home.RegisterUiState
import com.example.smartwificonnect.feature.manual.ManualEntryScreen
import com.example.smartwificonnect.feature.networkdetail.NetworkDetailScreen
import com.example.smartwificonnect.feature.permission.CameraPermissionScreen
import com.example.smartwificonnect.feature.scanimage.ImagePickerScreen
import com.example.smartwificonnect.feature.scanimage.ImageScanScreen
import com.example.smartwificonnect.feature.scanimage.OcrResultScreen
import com.example.smartwificonnect.feature.scanqr.QrScannerScreen
import com.example.smartwificonnect.feature.share.ShareWifiScreen
import com.example.smartwificonnect.feature.settings.SettingsScreen
import java.util.Locale

@Composable
fun AppNavHost(
    mainViewModel: MainViewModel,
    navController: NavHostController = rememberNavController(),
) {
    val context = LocalContext.current
    val mainState by mainViewModel.state.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val failureTriggerRoutes = setOf(Routes.MANUAL_ENTRY, Routes.OCR_RESULT, Routes.NETWORK_DETAIL)
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
            mainViewModel.onImageSelectionCanceled()
            return@rememberLauncherForActivityResult
        }
        mainViewModel.startOcrFromGallery(uri)
        openOcrResult()
    }
    val nearbyWifiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        mainViewModel.refreshNearbyWifiNetworks(recalculateFuzzy = true)
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
        val preferredIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }
        runCatching {
            context.startActivity(preferredIntent)
        }.onFailure {
            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
    }
    val homeState = buildHomeState(mainState)

    LaunchedEffect(mainState.wifiConnectionState, currentRoute) {
        when (mainState.wifiConnectionState) {
            is WifiConnectionState.Failed -> {
                if (currentRoute in failureTriggerRoutes) {
                    navController.navigate(Routes.CONNECTION_FAILED) {
                        launchSingleTop = true
                    }
                }
            }

            is WifiConnectionState.Connected -> {
                if (currentRoute == Routes.CONNECTION_FAILED) {
                    navController.popBackStack()
                }
            }

            is WifiConnectionState.Connecting,
            WifiConnectionState.Idle,
            -> Unit
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.ONBOARDING,
    ) {
        composable(Routes.ONBOARDING) {
            val onboardingState = OnboardingUiState(
                titleLineOne = stringResource(R.string.onboarding_title_line_1),
                titleLineTwo = stringResource(R.string.onboarding_title_line_2),
                subtitle = stringResource(R.string.onboarding_subtitle),
                ctaText = stringResource(R.string.onboarding_cta_start),
                loginPrompt = stringResource(R.string.onboarding_login_prompt),
                appName = stringResource(R.string.onboarding_brand_name),
                appTagline = stringResource(R.string.onboarding_brand_tagline),
            )
            OnboardingScreen(
                state = onboardingState,
                onStartClick = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
                onLoginClick = {
                    navController.navigate(Routes.LOGIN)
                },
            )
        }

        composable(Routes.LOGIN) {
            val loginState = LoginUiState(
                screenTitle = stringResource(R.string.login_screen_title),
                brandTitle = stringResource(R.string.login_brand_title),
                brandSubtitle = stringResource(R.string.login_brand_subtitle),
                emailLabel = stringResource(R.string.login_email_label),
                emailPlaceholder = stringResource(R.string.login_email_placeholder),
                passwordLabel = stringResource(R.string.login_password_label),
                passwordPlaceholder = stringResource(R.string.login_password_placeholder),
                forgotPassword = stringResource(R.string.login_forgot_password),
                loginButton = stringResource(R.string.login_cta),
                socialDivider = stringResource(R.string.login_social_divider),
                noAccountPrefix = stringResource(R.string.login_no_account_prefix),
                signUpNow = stringResource(R.string.login_sign_up_now),
            )
            LoginScreen(
                state = loginState,
                onLoginClick = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
                onSignUpClick = {
                    navController.navigate(Routes.REGISTER)
                },
            )
        }

        composable(Routes.REGISTER) {
            val registerState = RegisterUiState(
                screenTitle = stringResource(R.string.register_screen_title),
                brandTitle = stringResource(R.string.register_brand_title),
                brandSubtitle = stringResource(R.string.register_brand_subtitle),
                fullNameLabel = stringResource(R.string.register_full_name_label),
                fullNamePlaceholder = stringResource(R.string.register_full_name_placeholder),
                emailLabel = stringResource(R.string.register_email_label),
                emailPlaceholder = stringResource(R.string.register_email_placeholder),
                passwordLabel = stringResource(R.string.register_password_label),
                passwordPlaceholder = stringResource(R.string.register_password_placeholder),
                confirmPasswordLabel = stringResource(R.string.register_confirm_password_label),
                confirmPasswordPlaceholder = stringResource(R.string.register_confirm_password_placeholder),
                registerButton = stringResource(R.string.register_cta),
                socialDivider = stringResource(R.string.register_social_divider),
                hasAccountPrefix = stringResource(R.string.register_has_account_prefix),
                loginNow = stringResource(R.string.register_login_now),
            )
            RegisterScreen(
                state = registerState,
                onBackClick = { navController.popBackStack() },
                onRegisterClick = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
                onLoginNowClick = { navController.popBackStack() },
            )
        }

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
                        navController.popBackStack()
                        navController.navigate(nextRoute)
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
                onHelpClick = {},
                onFlashClick = {},
                onGalleryClick = { navController.navigate(Routes.SCAN_IMAGE) },
                onQrCodeDetected = { rawQrText ->
                    mainViewModel.consumeRecognizedText(rawQrText)
                    openOcrResult()
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
                onCloseClick = { navController.popBackStack() },
                onFlashClick = {},
                onCaptureClick = { bitmap ->
                    mainViewModel.startOcrFromCamera(bitmap)
                    openOcrResult()
                },
                onCaptureUnavailable = mainViewModel::onCameraPreviewUnavailable,
                onSwitchToQrClick = { navController.navigate(Routes.SCAN_QR) },
                onOpenGalleryClick = { pickImageLauncher.launch("image/*") },
                onHomeClick = openHome,
                onScanClick = {},
                onShareClick = { navController.navigate(Routes.SHARE) },
                onHistoryClick = { navController.navigate(Routes.HISTORY) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
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
            LaunchedEffect(Unit) {
                refreshNearbyWifi()
            }
            OcrResultScreen(
                state = mainState,
                onBackClick = { navController.popBackStack() },
                onOcrTextChange = mainViewModel::onOcrTextChanged,
                onParseClick = mainViewModel::parseCurrentText,
                onAcceptSuggestion = mainViewModel::acceptSsidSuggestion,
                onDismissSuggestion = mainViewModel::dismissSsidSuggestion,
                onToggleNearby = mainViewModel::toggleNearbyExpanded,
                onSelectNetwork = mainViewModel::selectNearbyNetwork,
                onUseAiSsid = mainViewModel::applyAiNormalizedSsid,
                onUseAiPassword = mainViewModel::applyAiNormalizedPassword,
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
            ConnectionFailedScreen(
                isRetrying = mainState.wifiConnectionState is WifiConnectionState.Connecting,
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
            val shareableSsid = when (val connectionState = mainState.wifiConnectionState) {
                is WifiConnectionState.Connected -> connectionState.ssid
                else -> mainState.historyRecords.firstOrNull()?.ssid ?: mainState.ssid
            }
            ShareWifiScreen(
                networkName = shareableSsid,
                hasShareableNetwork = shareableSsid.isNotBlank(),
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
        composable(Routes.REVIEW) { Text(text = "ReviewScreen") }
        composable(Routes.HISTORY) {
            LaunchedEffect(Unit) {
                mainViewModel.refreshHistory()
            }
            HistoryScreen(
                records = mainState.historyRecords,
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
                onDarkModeChange = mainViewModel::onDarkModeChanged,
                onHomeClick = openHome,
                onScanClick = { openRouteWithCameraPermission(Routes.SCAN_QR) },
                onShareClick = { navController.navigate(Routes.SHARE) },
                onHistoryClick = { navController.navigate(Routes.HISTORY) },
                onSettingsClick = {},
            )
        }
    }
}

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

private fun buildHomeState(mainState: MainUiState): HomeUiState {
    val fallback = HomePreviewData.default
    val connectedSsid = (mainState.wifiConnectionState as? WifiConnectionState.Connected)?.ssid
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
        connectivityStatus = connectedSsid?.let { "Đang kết nối tới $it." } ?: fallback.connectivityStatus,
        recentNetworks = recentNetworks,
        savedNetworksCount = mainState.historyRecords.size.toString(),
        usageValue = if (connectedSsid != null) "Live" else fallback.usageValue,
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
