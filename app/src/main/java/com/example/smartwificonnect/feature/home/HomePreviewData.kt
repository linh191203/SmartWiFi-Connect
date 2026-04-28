package com.example.smartwificonnect.feature.home

object HomePreviewData {
    val default = HomeUiState(
        greeting = "Xin chào!",
        connectivityStatus = "Bạn hiện đang ngoại tuyến.",
        quickConnectTitle = "Kết nối nhanh",
        quickConnectSubtitle = "Tham gia ngay mạng tốt nhất hiện có trong khu vực của bạn.",
        quickConnectCta = "Kết nối ngay",
        cameraTitle = "Quét bằng Máy ảnh",
        cameraSubtitle = "Sử dụng camera để nhận diện mạng tự động",
        shortcutItems = listOf(
            HomeShortcutUiModel(
                title = "Quét mã QR",
                subtitle = "Tham gia qua mã",
                type = HomeShortcutType.QR,
            ),
            HomeShortcutUiModel(
                title = "Nhập thủ công",
                subtitle = "Nhập SSID & mật khẩu",
                type = HomeShortcutType.MANUAL,
            ),
        ),
        recentNetworksTitle = "Mạng gần đây",
        recentNetworks = listOf(
            RecentNetworkUiModel(
                name = "Home_5G_Network",
                lastConnectedLabel = "Kết nối lần cuối 2 giờ trước",
                type = RecentNetworkType.WIFI,
            ),
            RecentNetworkUiModel(
                name = "Office_Main_Corp",
                lastConnectedLabel = "Kết nối lần cuối Hôm qua",
                type = RecentNetworkType.ROUTER,
            ),
            RecentNetworkUiModel(
                name = "Starbucks_Free_WiFi",
                lastConnectedLabel = "Kết nối lần cuối 3 ngày trước",
                type = RecentNetworkType.BUILDING,
            ),
        ),
        savedNetworksLabel = "Đã lưu",
        savedNetworksCount = "12",
        usageLabel = "Tổng dữ liệu",
        usageValue = "24.8 GB",
    )
}
