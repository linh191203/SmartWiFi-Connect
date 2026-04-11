package com.example.smartwificonnect.navigation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.core.content.ContextCompat
import com.example.smartwificonnect.MainViewModel
import com.example.smartwificonnect.R
import com.example.smartwificonnect.feature.home.HomePreviewData
import com.example.smartwificonnect.feature.home.HomeScreen
import com.example.smartwificonnect.feature.home.LoginScreen
import com.example.smartwificonnect.feature.home.LoginUiState
import com.example.smartwificonnect.feature.home.OnboardingScreen
import com.example.smartwificonnect.feature.home.OnboardingUiState
import com.example.smartwificonnect.feature.home.RegisterScreen
import com.example.smartwificonnect.feature.home.RegisterUiState
import com.example.smartwificonnect.feature.permission.CameraPermissionScreen
import com.example.smartwificonnect.feature.scanimage.ImagePickerScreen
import com.example.smartwificonnect.feature.scanimage.ImageScanScreen
import com.example.smartwificonnect.feature.scanimage.OcrResultScreen
import com.example.smartwificonnect.feature.scanqr.QrScannerScreen

@Composable
fun AppNavHost(
    mainViewModel: MainViewModel,
    navController: NavHostController = rememberNavController(),
) {
    val context = LocalContext.current
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
                state = HomePreviewData.default,
                onScanQrClick = { navController.navigate(Routes.cameraPermissionRoute(Routes.SCAN_QR)) },
                onScanImageClick = { navController.navigate(Routes.cameraPermissionRoute(Routes.SCAN_IMAGE)) },
                onManualEntryClick = { navController.navigate(Routes.REVIEW) },
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
                    val cameraGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (cameraGranted) {
                        captureImageLauncher.launch(null)
                    } else {
                        navController.navigate(Routes.cameraPermissionRoute(Routes.SCAN_IMAGE))
                    }
                },
                onSwitchToQrClick = { navController.navigate(Routes.SCAN_QR) },
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
        composable(Routes.IMAGE_PICKER) {
            ImagePickerScreen(
                onBackClick = { navController.popBackStack() },
                onContinueClick = {
                    pickImageLauncher.launch("image/*")
                },
            )
        }
        composable(Routes.OCR_RESULT) {
            OcrResultScreen(
                state = mainState,
                onBackClick = { navController.popBackStack() },
                onOcrTextChange = mainViewModel::onOcrTextChanged,
                onParseClick = mainViewModel::parseCurrentText,
                onAcceptSuggestion = mainViewModel::acceptSsidSuggestion,
                onDismissSuggestion = mainViewModel::dismissSsidSuggestion,
                onToggleNearby = mainViewModel::toggleNearbyExpanded,
                onSelectNetwork = mainViewModel::selectNearbyNetwork,
            )
        }
        composable(Routes.REVIEW) { Text(text = "ReviewScreen") }
        composable(Routes.HISTORY) { Text(text = "HistoryScreen") }
        composable(Routes.SETTINGS) { Text(text = "SettingsScreen") }
    }
}
