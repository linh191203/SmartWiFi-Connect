package com.example.smartwificonnect.navigation

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.example.smartwificonnect.data.local.AppConsentStore
import com.example.smartwificonnect.feature.connection.ConnectionFailedScreen
import com.example.smartwificonnect.feature.history.HistoryScreen
import com.example.smartwificonnect.feature.home.HomePreviewData
import com.example.smartwificonnect.feature.home.HomeScreen
import com.example.smartwificonnect.feature.home.HomeUiState
import com.example.smartwificonnect.feature.home.OnboardingScreen
import com.example.smartwificonnect.feature.home.OnboardingUiState
import com.example.smartwificonnect.feature.home.RecentNetworkType
import com.example.smartwificonnect.feature.home.RecentNetworkUiModel
import com.example.smartwificonnect.feature.manual.ManualEntryScreen
import com.example.smartwificonnect.feature.networkdetail.NetworkDetailScreen
import com.example.smartwificonnect.feature.permission.CameraPermissionScreen
import com.example.smartwificonnect.feature.policy.ConsentScreen
import com.example.smartwificonnect.feature.scanimage.ImagePickerScreen
import com.example.smartwificonnect.feature.scanimage.ImageScanScreen
import com.example.smartwificonnect.feature.scanimage.OcrResultScreen
import com.example.smartwificonnect.feature.scanqr.QrScannerScreen
import com.example.smartwificonnect.feature.share.ShareWifiScreen
import com.example.smartwificonnect.feature.share.ShareWifiUiModel
import com.example.smartwificonnect.feature.settings.SettingsScreen
import java.util.Locale

@Composable
fun AppNavHost(
    mainViewModel: MainViewModel,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.ONBOARDING,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainState by mainViewModel.state.collectAsState()
    var hasAcceptedConsent by remember { mutableStateOf(AppConsentStore.hasAccepted(context)) }
    val awaitingWifiEnable = remember { mutableStateOf(false) }
    val wifiResumeCount = remember { mutableIntStateOf(0) }
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
        if (currentRoute == Routes.SCAN_QR) {
            mainViewModel.startQrFromGallery(uri)
        } else {
            mainViewModel.startOcrFromGallery(uri)
        }
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
    val openAutoConnectSetting: () -> Unit = {
        when {
            !mainState.isWifiEnabled -> openSystemNetworkSettings()
            mainViewModel.openAutoConnectTargetFromSettings() -> navController.navigate(Routes.NETWORK_DETAIL)
            else -> Toast.makeText(
                context,
                "Chua co mang da luu nao o gan day de tu dong ket noi.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
    val openPriorityNetworkSetting: () -> Unit = {
        when {
            !mainState.isWifiEnabled -> openSystemNetworkSettings()
            mainViewModel.openPriorityNetworkFromSettings() -> navController.navigate(Routes.NETWORK_DETAIL)
            else -> Toast.makeText(
                context,
                "Chua tim thay mang uu tien nao trong khu vuc hien tai.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        mainViewModel.refreshWifiEnvironment(recalculateFuzzy = true)
    }

    DisposableEffect(lifecycleOwner, mainViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mainViewModel.refreshWifiEnvironment(recalculateFuzzy = true)
                if (awaitingWifiEnable.value) {
                    wifiResumeCount.intValue += 1
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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

    LaunchedEffect(mainState.sharedLinkRequestId, currentRoute, hasAcceptedConsent) {
        if (!hasAcceptedConsent) return@LaunchedEffect
        if (mainState.sharedLinkRequestId <= 0L || currentRoute == null) return@LaunchedEffect
        if (currentRoute != Routes.OCR_RESULT) {
            openOcrResult()
        }
    }

    LaunchedEffect(mainState.isWifiEnabled, wifiResumeCount.intValue) {
        when {
            mainState.isWifiEnabled -> {
                awaitingWifiEnable.value = false
            }

            !awaitingWifiEnable.value -> {
                awaitingWifiEnable.value = true
                openSystemNetworkSettings()
            }

            wifiResumeCount.intValue > 0 -> {
                Toast.makeText(
                    context,
                    "Can bat Wi-Fi de su dung ung dung. App se dong lai.",
                    Toast.LENGTH_LONG,
                ).show()
                activity?.finishAffinity()
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(Routes.ONBOARDING) {
            val onboardingState = OnboardingUiState(
                titleLineOne = stringResource(R.string.onboarding_title_line_1),
                titleLineTwo = stringResource(R.string.onboarding_title_line_2),
                subtitle = stringResource(R.string.onboarding_subtitle),
                ctaText = stringResource(R.string.onboarding_cta_start),
                appName = stringResource(R.string.onboarding_brand_name),
                appTagline = stringResource(R.string.onboarding_brand_tagline),
            )
            OnboardingScreen(
                state = onboardingState,
                onStartClick = {
                    val destination = when {
                        !hasAcceptedConsent -> Routes.CONSENT
                        mainState.sharedLinkRequestId > 0L -> Routes.OCR_RESULT
                        else -> Routes.HOME
                    }
                    navController.navigate(destination) {
                        if (destination == Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                },
            )
        }

        composable(Routes.CONSENT) {
            ConsentScreen(
                onAcceptClick = {
                    AppConsentStore.markAccepted(context)
                    hasAcceptedConsent = true
                    val destination = if (mainState.sharedLinkRequestId > 0L) {
                        Routes.OCR_RESULT
                    } else {
                        Routes.HOME
                    }
                    navController.navigate(destination) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
                onDeclineClick = {
                    Toast.makeText(
                        context,
                        "Ban da tu choi chinh sach. Ung dung se dong lai.",
                        Toast.LENGTH_LONG,
                    ).show()
                    activity?.finishAffinity()
                },
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
                onGalleryClick = { pickImageLauncher.launch("image/*") },
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
                onSsidChange = mainViewModel::onSsidChanged,
                onPasswordChange = mainViewModel::onPasswordChanged,
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
                autoConnectSubtitle = buildAutoConnectSettingSubtitle(mainState),
                priorityNetworkSubtitle = buildPrioritySettingSubtitle(mainState),
                onDarkModeChange = mainViewModel::onDarkModeChanged,
                onAutoConnectClick = openAutoConnectSetting,
                onPriorityNetworkClick = openPriorityNetworkSetting,
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

private fun MainUiState.toShareWifiUiModel(): ShareWifiUiModel? {
    val connectedSsid = (wifiConnectionState as? WifiConnectionState.Connected)?.ssid
    val connectedRecord = connectedSsid?.let { activeSsid ->
        historyRecords.firstOrNull { it.ssid.equals(activeSsid, ignoreCase = true) }
    }
    val fallbackRecord = historyRecords.firstOrNull { it.ssid.isNotBlank() }
    val shareSsid = connectedSsid
        ?: ssid.takeIf { it.isNotBlank() }
        ?: fallbackRecord?.ssid
        ?: return null
    val sharePassword = when {
        connectedSsid != null && ssid.equals(connectedSsid, ignoreCase = true) -> password
        ssid.equals(shareSsid, ignoreCase = true) && password.isNotBlank() -> password
        connectedRecord != null -> connectedRecord.password
        fallbackRecord?.ssid.equals(shareSsid, ignoreCase = true) -> fallbackRecord?.password.orEmpty()
        else -> ""
    }
    val shareSecurity = security
        .takeIf { it.isNotBlank() && ssid.equals(shareSsid, ignoreCase = true) }
        ?: if (sharePassword.isBlank()) "Open" else "WPA/WPA2"

    return ShareWifiUiModel(
        ssid = shareSsid,
        password = sharePassword,
        security = shareSecurity,
    )
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
        connectivityStatus = when {
            !mainState.isWifiEnabled -> "Wi-Fi dang tat. Bat Wi-Fi de quet va ket noi."
            connectedSsid != null -> "Dang ket noi toi $connectedSsid."
            else -> fallback.connectivityStatus
        },
        recentNetworks = recentNetworks,
    )
}

private fun buildAutoConnectSettingSubtitle(mainState: MainUiState): String {
    val connectedSsid = (mainState.wifiConnectionState as? WifiConnectionState.Connected)?.ssid
    if (!mainState.isWifiEnabled) {
        return "Bat Wi-Fi de tim mang da luu va tiep tuc ket noi tu dong."
    }
    if (!connectedSsid.isNullOrBlank()) {
        return "Dang ket noi thuc te toi $connectedSsid."
    }
    val nearbySaved = strongestSavedNearbyNetwork(mainState)
    return if (nearbySaved != null) {
        "Da tim thay mang da luu '${nearbySaved.ssid}' o gan day."
    } else {
        "Chua thay mang da luu nao o gan day. Hay quet hoac nhap Wi-Fi truoc."
    }
}

private fun buildPrioritySettingSubtitle(mainState: MainUiState): String {
    if (!mainState.isWifiEnabled) {
        return "Bat Wi-Fi de doi chieu mang uu tien theo thuc te."
    }
    val strongest = strongestSavedNearbyNetwork(mainState)
    return if (strongest != null) {
        "Mang uu tien hien tai: ${strongest.ssid} (${strongest.signalLevel}/4)."
    } else {
        "Chua co mang uu tien kha dung. App se goi y khi co mang da luu xuat hien."
    }
}

private fun strongestSavedNearbyNetwork(mainState: MainUiState) =
    mainState.nearbyNetworks
        .filter { nearby ->
            mainState.historyRecords.any { it.ssid.equals(nearby.ssid, ignoreCase = true) }
        }
        .maxByOrNull { it.signalLevel }

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
