package com.example.smartwificonnect.navigation

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.core.content.ContextCompat
import com.example.smartwificonnect.MainViewModel
import com.example.smartwificonnect.R
import com.example.smartwificonnect.feature.home.HomeScreen
import com.example.smartwificonnect.feature.home.LoginScreen
import com.example.smartwificonnect.feature.home.LoginUiState
import com.example.smartwificonnect.feature.home.OnboardingScreen
import com.example.smartwificonnect.feature.home.OnboardingUiState
import com.example.smartwificonnect.feature.home.RegisterScreen
import com.example.smartwificonnect.feature.home.RegisterUiState
import com.example.smartwificonnect.feature.history.HistoryScreen
import com.example.smartwificonnect.feature.permission.CameraPermissionScreen
import com.example.smartwificonnect.feature.scanimage.ImageScanScreen
import com.example.smartwificonnect.feature.scanimage.OcrResultScreen
import com.example.smartwificonnect.feature.scanqr.QrScannerScreen
import com.example.smartwificonnect.feature.settings.SettingsScreen

@Composable
fun AppNavHost(
    mainViewModel: MainViewModel,
    navController: NavHostController = rememberNavController(),
) {
    val context = LocalContext.current
    val hasCameraPermission: () -> Boolean = {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
    }
    val wifiPermissionList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.NEARBY_WIFI_DEVICES,
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val hasWifiPermission: () -> Boolean = {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNearbyWifi = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES,
            ) == PackageManager.PERMISSION_GRANTED
            hasFineLocation && hasNearbyWifi
        } else {
            hasFineLocation
        }
    }
    val mainState by mainViewModel.state.collectAsState()
    val openOcrResult: () -> Unit = {
        navController.navigate(Routes.OCR_RESULT) {
            launchSingleTop = true
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
    val captureImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview(),
    ) { bitmap ->
        if (bitmap == null) {
            mainViewModel.onImageSelectionCanceled()
            return@rememberLauncherForActivityResult
        }
        mainViewModel.startOcrFromCamera(bitmap)
        openOcrResult()
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
                onExitClick = {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                        launchSingleTop = true
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
                onExitClick = {
                    navController.navigate(Routes.ONBOARDING) {
                        popUpTo(Routes.REGISTER) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(Routes.HOME) {
            var requestedWifiPermission by remember { mutableStateOf(false) }
            val wifiPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions(),
            ) {
                mainViewModel.refreshHomeNearbyNetworks(hasPermission = hasWifiPermission())
            }

            LaunchedEffect(Unit) {
                if (hasWifiPermission()) {
                    mainViewModel.refreshHomeNearbyNetworks(hasPermission = true)
                } else if (!requestedWifiPermission) {
                    requestedWifiPermission = true
                    wifiPermissionLauncher.launch(wifiPermissionList)
                } else {
                    mainViewModel.refreshHomeNearbyNetworks(hasPermission = false)
                }
            }

            HomeScreen(
                state = mainState.homeState,
                onScanQrClick = {
                    if (hasCameraPermission()) {
                        navController.navigate(Routes.SCAN_QR)
                    } else {
                        navController.navigate(Routes.cameraPermissionRoute(Routes.SCAN_QR))
                    }
                },
                onScanImageClick = {
                    if (hasCameraPermission()) {
                        navController.navigate(Routes.SCAN_IMAGE)
                    } else {
                        navController.navigate(Routes.cameraPermissionRoute(Routes.SCAN_IMAGE))
                    }
                },
                onManualEntryClick = { navController.navigate(Routes.REVIEW) },
                onHistoryClick = { navController.navigate(Routes.HISTORY) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onRefreshNearbyNetworks = {
                    if (hasWifiPermission()) {
                        mainViewModel.refreshHomeNearbyNetworks(hasPermission = true)
                    } else {
                        requestedWifiPermission = false
                        wifiPermissionLauncher.launch(wifiPermissionList)
                    }
                },
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

        composable(Routes.SCAN_QR) {
            QrScannerScreen(
                onCloseClick = { navController.popBackStack() },
                onHelpClick = {},
                onFlashClick = {},
                onGalleryClick = { navController.navigate(Routes.SCAN_IMAGE) },
                onHomeClick = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onScanClick = {},
                onShareClick = { navController.navigate(Routes.REVIEW) },
                onHistoryClick = { navController.navigate(Routes.HISTORY) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SCAN_IMAGE) {
            ImageScanScreen(
                onCloseClick = { navController.popBackStack() },
                onFlashClick = {},
                onCaptureClick = {
                    if (hasCameraPermission()) {
                        captureImageLauncher.launch(null)
                    } else {
                        navController.navigate(Routes.cameraPermissionRoute(Routes.SCAN_IMAGE))
                    }
                },
                onSwitchToQrClick = {
                    if (hasCameraPermission()) {
                        navController.navigate(Routes.SCAN_QR)
                    } else {
                        navController.navigate(Routes.cameraPermissionRoute(Routes.SCAN_QR))
                    }
                },
                onOpenGalleryClick = { pickImageLauncher.launch("image/*") },
                onHomeClick = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                },
                onScanClick = {},
                onShareClick = { navController.navigate(Routes.REVIEW) },
                onHistoryClick = { navController.navigate(Routes.HISTORY) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.OCR_RESULT) {
            OcrResultScreen(
                state = mainState,
                onBackClick = { navController.popBackStack() },
                onOcrTextChange = mainViewModel::onOcrTextChanged,
                onParseClick = mainViewModel::parseCurrentText,
                onConnectClick = mainViewModel::connectCurrentWifi,
                onSavePasswordChange = mainViewModel::onSavePasswordOnDeviceChanged,
                onAcceptSuggestion = mainViewModel::acceptSsidSuggestion,
                onDismissSuggestion = mainViewModel::dismissSsidSuggestion,
                onToggleNearby = mainViewModel::toggleNearbyExpanded,
                onSelectNetwork = mainViewModel::selectNearbyNetwork,
            )
        }
        composable(Routes.REVIEW) {
            PlaceholderFeatureScreen(
                title = stringResource(R.string.placeholder_review_title),
                description = stringResource(R.string.placeholder_review_description),
                onBack = { navController.popBackStack() },
                primaryActionText = stringResource(R.string.placeholder_open_ocr_result),
                onPrimaryAction = {
                    navController.navigate(Routes.OCR_RESULT) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                state = mainState.historyState,
                onBackClick = { navController.popBackStack() },
                onDeleteNetwork = mainViewModel::deleteHistoryNetwork,
                onClearAll = mainViewModel::clearAllSavedNetworksHistory,
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                state = mainState.settingsState,
                languageOptions = mainViewModel.languageOptions,
                onBackClick = { navController.popBackStack() },
                onUserNameChange = mainViewModel::onSettingsUserNameChanged,
                onEmailChange = mainViewModel::onSettingsEmailChanged,
                onLanguageChange = mainViewModel::onSettingsLanguageChanged,
                onAutoSavePasswordsChange = mainViewModel::onSettingsAutoSavePasswordsChanged,
                onSaveClick = {
                    mainViewModel.saveSettings()
                    (context as? Activity)?.recreate()
                },
                onLogoutClick = {
                    mainViewModel.logout()
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}

@Composable
private fun PlaceholderFeatureScreen(
    title: String,
    description: String,
    onBack: () -> Unit,
    primaryActionText: String,
    onPrimaryAction: () -> Unit,
) {
    Scaffold(
        topBar = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = description,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onPrimaryAction,
                modifier = Modifier.padding(top = 20.dp),
            ) {
                Text(text = primaryActionText)
            }
        }
    }
}
