package com.example.smartwificonnect.feature.home

data class HomeUiState(
    val greeting: String,
    val connectivityStatus: String,
    val quickConnectTitle: String,
    val quickConnectSubtitle: String,
    val quickConnectCta: String,
    val cameraTitle: String,
    val cameraSubtitle: String,
    val shortcutItems: List<HomeShortcutUiModel>,
    val recentNetworksTitle: String,
    val recentNetworks: List<RecentNetworkUiModel>,
    val savedNetworksLabel: String,
    val savedNetworksCount: String,
    val usageLabel: String,
    val usageValue: String,
    val isLoading: Boolean = false,
)

data class HomeShortcutUiModel(
    val title: String,
    val subtitle: String,
    val type: HomeShortcutType,
)

data class RecentNetworkUiModel(
    val name: String,
    val lastConnectedLabel: String,
    val type: RecentNetworkType,
)

enum class HomeShortcutType {
    QR,
    IMAGE,
}

enum class RecentNetworkType {
    WIFI,
    ROUTER,
    BUILDING,
}
