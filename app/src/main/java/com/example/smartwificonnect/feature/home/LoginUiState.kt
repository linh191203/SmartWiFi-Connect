package com.example.smartwificonnect.feature.home

data class LoginUiState(
    val screenTitle: String,
    val brandTitle: String,
    val brandSubtitle: String,
    val emailLabel: String,
    val emailPlaceholder: String,
    val passwordLabel: String,
    val passwordPlaceholder: String,
    val forgotPassword: String,
    val loginButton: String,
    val socialDivider: String,
    val noAccountPrefix: String,
    val signUpNow: String,
)

object LoginPreviewData {
    val default = LoginUiState(
        screenTitle = "Đăng nhập",
        brandTitle = "SmartWiFi-Connect",
        brandSubtitle = "Trải nghiệm kết nối không giới hạn",
        emailLabel = "Email",
        emailPlaceholder = "example@gmail.com",
        passwordLabel = "Mật khẩu",
        passwordPlaceholder = "••••••••",
        forgotPassword = "Quên mật khẩu?",
        loginButton = "Đăng nhập",
        socialDivider = "Hoặc đăng nhập với",
        noAccountPrefix = "Chưa có tài khoản?",
        signUpNow = "Đăng ký ngay",
    )
}
