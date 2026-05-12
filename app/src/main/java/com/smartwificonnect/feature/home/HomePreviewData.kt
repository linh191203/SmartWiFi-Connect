package com.smartwificonnect.feature.home

object HomePreviewData {
    val default = HomeUiState(
        greeting = "Xin chào!",
        connectivityStatus = "Bạn hiện đang ngoại tuyến.",
        quickConnectTitle = "Quét nhanh",
        quickConnectSubtitle = "Quét ảnh hoặc QR có SSID/mật khẩu rồi kết nối sau khi kiểm tra.",
        quickConnectCta = "Mở máy quét",
        cameraTitle = "Quét bằng Máy ảnh",
        cameraSubtitle = "Sử dụng camera để nhận diện mạng tự động",
        shortcutItems = listOf(
            HomeShortcutUiModel(
                title = "Nhập thủ công",
                subtitle = "Nhập SSID & mật khẩu",
                type = HomeShortcutType.MANUAL,
            ),
        ),
        recentNetworksTitle = "Mạng gần đây",
        recentNetworks = emptyList(),
    )
}
