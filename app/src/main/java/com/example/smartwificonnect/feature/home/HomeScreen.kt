package com.example.smartwificonnect.feature.home

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smartwificonnect.ui.theme.SmartWifiAppTheme

private val ScreenBackground = Color(0xFFF7F9FC)
private val ScreenBackgroundBottom = Color(0xFFEFF3FB)
private val SurfaceWhite = Color(0xFFFFFFFF)
private val SurfaceLow = Color(0xFFF1F4FA)
private val SurfaceSoft = Color(0xFFE7ECF5)
private val SurfaceCard = Color(0xFFF4F5F8)
private val BrandPrimary = Color(0xFF5B5FEF)
private val BrandPrimaryDark = Color(0xFF474ADB)
private val BrandPrimarySoft = Color(0xFFC6CBFF)
private val TextPrimary = Color(0xFF1A1D25)
private val TextMuted = Color(0xFF707684)
private val MintCard = Color(0xFF81EFD3)
private val SkyCard = Color(0xFFD2E7FF)
private val SecondaryTint = Color(0xFFE7FFF8)
private val TertiaryTint = Color(0xFFE4F5FF)
private val BottomNavFill = Color(0xF2FFFFFF)
private val BottomNavStroke = Color(0xFFF1F5F9)
private val CameraActionCardHeight = 122.dp
private val ShortcutActionCardHeight = 138.dp

private enum class HomeBottomTab(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Trang chủ", Icons.Outlined.Home),
    SCAN("Quét", Icons.Outlined.QrCode2),
    SHARE("Chia sẻ", Icons.Outlined.IosShare),
    HISTORY("Lịch sử", Icons.Outlined.History),
    SETTINGS("Cài đặt", Icons.Outlined.Settings),
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onScanQrClick: () -> Unit,
    onScanImageClick: () -> Unit,
    onManualEntryClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { HomeTopBar() },
        bottomBar = {
            HomeBottomBar(
                onScanClick = onScanQrClick,
                onShareClick = onManualEntryClick,
                onHistoryClick = onHistoryClick,
                onSettingsClick = onSettingsClick,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(ScreenBackground, ScreenBackgroundBottom),
                    ),
                ),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 10.dp,
                    top = innerPadding.calculateTopPadding() + 10.dp,
                    end = 10.dp,
                    bottom = innerPadding.calculateBottomPadding() + 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item {
                    WelcomeSection(
                        title = state.greeting,
                        subtitle = state.connectivityStatus,
                    )
                }

                item {
                    QuickConnectCard(
                        title = state.quickConnectTitle,
                        subtitle = state.quickConnectSubtitle,
                        buttonLabel = state.quickConnectCta,
                        onClick = onManualEntryClick,
                    )
                }

                item {
                    CameraActionCard(
                        title = state.cameraTitle,
                        subtitle = state.cameraSubtitle,
                        onClick = onScanImageClick,
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        state.shortcutItems.forEach { shortcut ->
                            ShortcutCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(ShortcutActionCardHeight),
                                item = shortcut,
                                onClick = {
                                    when (shortcut.type) {
                                        HomeShortcutType.QR -> onScanQrClick()
                                        HomeShortcutType.IMAGE -> onScanImageClick()
                                    }
                                },
                            )
                        }
                    }
                }

                item {
                    RecentNetworksSection(
                        title = state.recentNetworksTitle,
                        items = state.recentNetworks,
                    )
                }

                item {
                    StatsSection(
                        savedLabel = state.savedNetworksLabel,
                        savedValue = state.savedNetworksCount,
                        usageLabel = state.usageLabel,
                        usageValue = state.usageValue,
                    )
                }

                item {
                    SmartTipsSection()
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar() {
    Surface(
        color = ScreenBackground.copy(alpha = 0.94f),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SoftIconButton(icon = Icons.Rounded.Menu, contentDescription = "Menu")
                Text(
                    text = "SmartWiFi",
                    color = BrandPrimary,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            Surface(
                modifier = Modifier.size(34.dp),
                shape = CircleShape,
                color = Color(0xFFF5E6D9),
                shadowElevation = 2.dp,
                border = BorderStroke(2.dp, BrandPrimarySoft),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFFFFF5EC), Color(0xFFF0C7A8)),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "D",
                        color = BrandPrimaryDark,
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeSection(
    title: String,
    subtitle: String,
) {
    Column(
        modifier = Modifier.padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = title,
            color = TextPrimary,
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = subtitle,
            color = TextMuted,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun QuickConnectCard(
    title: String,
    subtitle: String,
    buttonLabel: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        border = BorderStroke(1.dp, Color(0xCC7377F3)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(BrandPrimary, Color(0xFF6B6CE8)),
                    ),
                )
                .padding(horizontal = 22.dp, vertical = 20.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f)),
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WifiOff,
                        contentDescription = null,
                        tint = SurfaceWhite,
                        modifier = Modifier.size(32.dp),
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = title,
                        color = SurfaceWhite,
                        style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = subtitle,
                        color = Color(0xFFE1E5FF),
                        textAlign = TextAlign.Center,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                    )
                }

                Surface(
                    modifier = Modifier.width(168.dp),
                    onClick = onClick,
                    shape = RoundedCornerShape(999.dp),
                    color = SurfaceWhite,
                    shadowElevation = 2.dp,
                    border = BorderStroke(1.dp, Color(0xFFE7EAFF)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = buttonLabel,
                            color = BrandPrimaryDark,
                            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = BrandPrimaryDark,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraActionCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(CameraActionCardHeight),
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = SurfaceCard,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, Color(0xFFD8DCE5)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFEDEBFC)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PhotoCamera,
                    contentDescription = null,
                    tint = BrandPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    color = TextPrimary,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = subtitle,
                    color = TextMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ShortcutCard(
    modifier: Modifier = Modifier,
    item: HomeShortcutUiModel,
    onClick: () -> Unit,
) {
    val tintColor = when (item.type) {
        HomeShortcutType.QR -> SecondaryTint
        HomeShortcutType.IMAGE -> TertiaryTint
    }
    val iconTint = when (item.type) {
        HomeShortcutType.QR -> Color(0xFF27B59A)
        HomeShortcutType.IMAGE -> Color(0xFF4A8DCF)
    }
    val icon = when (item.type) {
        HomeShortcutType.QR -> Icons.Rounded.QrCodeScanner
        HomeShortcutType.IMAGE -> Icons.Rounded.Image
    }

    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = SurfaceCard,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, Color(0xFFD8DCE5)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tintColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.title,
                    color = TextPrimary,
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.subtitle,
                    color = TextMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RecentNetworksSection(
    title: String,
    items: List<RecentNetworkUiModel>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = TextPrimary,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "Xem tất cả",
                color = BrandPrimary,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceLow),
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items.forEach { network ->
                    NetworkRow(item = network)
                }
            }
        }
    }
}

@Composable
private fun NetworkRow(item: RecentNetworkUiModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = SurfaceWhite,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SurfaceSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.type.icon(),
                    contentDescription = null,
                    tint = BrandPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    color = TextPrimary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = item.lastConnectedLabel,
                    color = TextMuted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun StatsSection(
    savedLabel: String,
    savedValue: String,
    usageLabel: String,
    usageValue: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.weight(0.95f),
            shape = RoundedCornerShape(22.dp),
            color = MintCard,
            shadowElevation = 3.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = savedLabel.uppercase(),
                    color = Color(0xFF1E7E69),
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = savedValue,
                    color = Color(0xFF0C4437),
                    style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }

        Surface(
            modifier = Modifier.weight(1.35f),
            shape = RoundedCornerShape(22.dp),
            color = SkyCard,
            shadowElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = usageLabel.uppercase(),
                        color = Color(0xFF4B79A8),
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = usageValue,
                        color = TextPrimary,
                        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ShowChart,
                    contentDescription = null,
                    tint = Color(0xFF93B4D8),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun SmartTipsSection() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Gợi ý thông minh",
            color = TextPrimary,
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 2.dp),
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFFE9F6FF),
            shadowElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFD4EDFF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Speed,
                        contentDescription = null,
                        tint = Color(0xFF387FB8),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Khuyến nghị tốc độ",
                        color = TextPrimary,
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = "Bạn đang gần mạng có tốc độ ổn định hơn 32%",
                        color = TextMuted,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFFE9FFF7),
            shadowElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFD1F7EA)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = Color(0xFF288869),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Bảo mật đề xuất",
                        color = TextPrimary,
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = "Bật xác thực mạng tin cậy để vào Wi-Fi nhanh hơn",
                        color = TextMuted,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
@SuppressLint("UnusedBoxWithConstraintsScope")
private fun HomeBottomBar(
    onScanClick: () -> Unit,
    onShareClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    activeTab: HomeBottomTab = HomeBottomTab.HOME,
) {
    var currentTabName by rememberSaveable { mutableStateOf(activeTab.name) }
    val currentTab = HomeBottomTab.valueOf(currentTabName)

    val tabs = listOf(
        HomeBottomTab.HOME,
        HomeBottomTab.SCAN,
        HomeBottomTab.SHARE,
        HomeBottomTab.HISTORY,
        HomeBottomTab.SETTINGS,
    )

    fun onTabClick(tab: HomeBottomTab) {
        currentTabName = tab.name
        when (tab) {
            HomeBottomTab.HOME -> Unit
            HomeBottomTab.SCAN -> onScanClick()
            HomeBottomTab.SHARE -> onShareClick()
            HomeBottomTab.HISTORY -> onHistoryClick()
            HomeBottomTab.SETTINGS -> onSettingsClick()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp),
        shape = RoundedCornerShape(
            topStart = 24.dp,
            topEnd = 24.dp,
            bottomStart = 0.dp,
            bottomEnd = 0.dp,
        ),
        color = BottomNavFill,
        shadowElevation = 30.dp,
        border = BorderStroke(2.dp, BottomNavStroke),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            val spacing = 4.dp
            val indicatorHeight = 64.dp
            val itemWidth = (maxWidth - spacing * (tabs.size - 1)) / tabs.size
            val selectedIndex = tabs.indexOf(currentTab).coerceAtLeast(0)
            val animatedOffset by animateDpAsState(
                targetValue = (itemWidth + spacing) * selectedIndex,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 620f),
                label = "bottomNavIndicatorOffset",
            )

            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .offset(x = animatedOffset)
                        .width(itemWidth)
                        .height(indicatorHeight)
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF5961F4), Color(0xFF5A69F8)),
                            ),
                        ),
                )

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEach { tab ->
                        BottomNavItem(
                            modifier = Modifier.width(itemWidth),
                            label = tab.label,
                            icon = tab.icon,
                            selected = tab == currentTab,
                            onClick = { onTabClick(tab) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val contentScale by animateFloatAsState(
        targetValue = if (selected) 1.03f else 1f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 720f),
        label = "bottomNavContentScale",
    )

    Column(
        modifier = modifier
            .height(64.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) SurfaceWhite else Color(0xFF090B11),
            modifier = Modifier.size(if (selected) 22.dp else 21.dp),
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = if (selected) SurfaceWhite else Color(0xFF090B11),
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.ExtraBold,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    scaleX = contentScale
                    scaleY = contentScale
                },
        )
    }
}

@Composable
private fun SoftIconButton(
    icon: ImageVector,
    contentDescription: String,
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = BrandPrimary,
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun RecentNetworkType.icon(): ImageVector = when (this) {
    RecentNetworkType.WIFI -> Icons.Rounded.Wifi
    RecentNetworkType.ROUTER -> Icons.Rounded.Router
    RecentNetworkType.BUILDING -> Icons.Rounded.Apartment
}

@Preview(showBackground = true, backgroundColor = 0xFFF7F9FC)
@Composable
private fun HomeScreenPreview() {
    SmartWifiAppTheme {
        HomeScreen(
            state = HomePreviewData.default,
            onScanQrClick = {},
            onScanImageClick = {},
            onManualEntryClick = {},
            onHistoryClick = {},
            onSettingsClick = {},
        )
    }
}
