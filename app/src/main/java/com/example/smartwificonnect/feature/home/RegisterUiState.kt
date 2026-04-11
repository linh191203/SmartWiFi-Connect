package com.example.smartwificonnect.feature.home

data class RegisterUiState(
    val screenTitle: String,
    val brandTitle: String,
    val brandSubtitle: String,
    val fullNameLabel: String,
    val fullNamePlaceholder: String,
    val emailLabel: String,
    val emailPlaceholder: String,
    val passwordLabel: String,
    val passwordPlaceholder: String,
    val confirmPasswordLabel: String,
    val confirmPasswordPlaceholder: String,
    val registerButton: String,
    val socialDivider: String,
    val hasAccountPrefix: String,
    val loginNow: String,
)

object RegisterPreviewData {
    val default = RegisterUiState(
        screenTitle = "Đăng ký tài khoản",
        brandTitle = "SmartWiFi-Connect",
        brandSubtitle = "Tham gia cộng đồng SmartWiFi ngay hôm nay",
        fullNameLabel = "Họ và tên",
        fullNamePlaceholder = "Nguyễn Văn A",
        emailLabel = "Email",
        emailPlaceholder = "example@gmail.com",
        passwordLabel = "Mật khẩu",
        passwordPlaceholder = "••••••••",
        confirmPasswordLabel = "Xác nhận mật khẩu",
        confirmPasswordPlaceholder = "••••••••",
        registerButton = "Đăng ký",
        socialDivider = "Hoặc đăng ký với",
        hasAccountPrefix = "Đã có tài khoản?",
        loginNow = "Đăng nhập ngay",
    )
}
