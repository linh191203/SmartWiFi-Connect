package com.example.smartwificonnect.navigation

object Routes {
    const val ONBOARDING = "onboarding"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
    const val CAMERA_PERMISSION = "camera_permission"
    const val CAMERA_PERMISSION_PATTERN = "$CAMERA_PERMISSION/{next}"
    const val SCAN_QR = "scan_qr"
    const val SCAN_IMAGE = "scan_image"
    const val IMAGE_PICKER = "image_picker"
    const val OCR_RESULT = "ocr_result"
    const val REVIEW = "review"
    const val HISTORY = "history"
    const val SETTINGS = "settings"

    fun cameraPermissionRoute(next: String): String = "$CAMERA_PERMISSION/$next"
}
