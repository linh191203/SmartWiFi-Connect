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
                title = "Quét ảnh",
                subtitle = "Nhập từ thư viện",
                type = HomeShortcutType.IMAGE,
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
        savedNetworksCount = "0",
        usageLabel = "Mạng mới nhất",
        usageValue = "Chưa có",
        smartInsightsTitle = "Gợi ý thông minh",
        smartInsights = listOf(
            SmartInsightUiModel(
                title = "Khuyến nghị tốc độ",
                summary = "Home_5G_Network mạnh hơn 28% so với các mạng gần đó.",
                type = SmartInsightType.SPEED,
                networkDetails = listOf(
                    NetworkQualityUiModel("Home_5G_Network", 92, 28),
                    NetworkQualityUiModel("Office_Main_Corp", 76, 5),
                    NetworkQualityUiModel("Starbucks_Free_WiFi", 60, -16),
                ),
            ),
            SmartInsightUiModel(
                title = "Bảo mật đề xuất",
                summary = "Office_Main_Corp có bảo mật tốt hơn 18% so với mạng lân cận.",
                type = SmartInsightType.SECURITY,
                networkDetails = listOf(
                    NetworkQualityUiModel("Office_Main_Corp", 88, 18),
                    NetworkQualityUiModel("Home_5G_Network", 79, 7),
                    NetworkQualityUiModel("Starbucks_Free_WiFi", 52, -21),
                ),
            ),
        ),
    )
}
