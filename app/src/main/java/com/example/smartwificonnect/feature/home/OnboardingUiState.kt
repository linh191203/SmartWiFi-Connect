package com.example.smartwificonnect.feature.home

data class OnboardingUiState(
    val titleLineOne: String,
    val titleLineTwo: String,
    val subtitle: String,
    val ctaText: String,
    val loginPrompt: String,
    val appName: String,
    val appTagline: String,
    val pageIndex: Int = 0,
    val totalPages: Int = 3,
)

object OnboardingPreviewData {
    val default = OnboardingUiState(
        titleLineOne = "Kết nối Wifi không dây",
        titleLineTwo = "mượt mà",
        subtitle = "Tự động kết nối với các mạng tốc độ cao đã xác thực.",
        ctaText = "Bắt đầu ngay",
        loginPrompt = "Đã có tài khoản? Đăng nhập",
        appName = "SmartWiFi-Connect",
        appTagline = "SECURITY FIRST · GLOBAL ACCESS",
    )
}
