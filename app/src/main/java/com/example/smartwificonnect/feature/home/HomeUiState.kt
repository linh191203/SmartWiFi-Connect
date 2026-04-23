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
    val smartInsightsTitle: String,
    val smartInsights: List<SmartInsightUiModel>,
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

data class SmartInsightUiModel(
    val title: String,
    val summary: String,
    val type: SmartInsightType,
    val networkDetails: List<NetworkQualityUiModel>,
)

data class NetworkQualityUiModel(
    val ssid: String,
    val qualityScore: Int,
    val comparisonPercent: Int,
)

enum class SmartInsightType {
    SPEED,
    SECURITY,
}
