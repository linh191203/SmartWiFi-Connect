package com.example.smartwificonnect.feature.history

import android.annotation.SuppressLint
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smartwificonnect.data.local.SavedWifiRecord
import com.example.smartwificonnect.ui.theme.LocalAppDarkMode
import com.example.smartwificonnect.ui.theme.SmartWifiAppTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

private val ScreenBackground: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF10131B) else Color(0xFFF7F9FC)
private val ScreenBackgroundBottom: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF171A24) else Color(0xFFEFF4FB)
private val SurfaceWhite: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF1F2430) else Color(0xFFFFFFFF)
private val SurfaceStroke: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF2C3342) else Color(0xFFE7EBF3)
private val SurfaceMuted: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF303746) else Color(0xFFE8ECF3)
private val BrandPrimary = Color(0xFF4E47D9)
private val BrandPrimaryDark = Color(0xFF3238C8)
private val BrandSoft = Color(0xFFEDEBFF)
private val TextPrimary: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFFF4F6FB) else Color(0xFF171A22)
private val TextMuted: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFFABB2C1) else Color(0xFF656B78)
private val Success: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF6EE2C2) else Color(0xFF0D8B67)
private val SuccessSoft: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF173F38) else Color(0xFFD9F3EA)
private val Teal = Color(0xFF58E7CB)
private val AnalyticsFill: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF202837) else Color(0xFFEAF1FB)
private val AnalyticsFillBottom: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF17222D) else Color(0xFFE5F2F7)
private val BottomNavFill: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xF21A1F2B) else Color(0xF7FFFFFF)
private val BottomNavStroke: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF293142) else Color(0xFFF1F5F9)

private enum class HistoryFilter(
    val label: String,
    val icon: ImageVector,
) {
    ALL("Tất cả", Icons.Outlined.GridView),
    SECURE("Bảo mật", Icons.Outlined.Lock),
    PUBLIC("Công cộng", Icons.Outlined.Public),
}

private enum class HistoryBottomTab(
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
fun HistoryScreen(
    records: List<SavedWifiRecord>,
        statusMessage: String = "",
    onNetworkClick: (SavedWifiRecord) -> Unit,
    onHomeClick: () -> Unit,
    onScanClick: () -> Unit,
    onShareClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    var selectedFilter by rememberSaveable { mutableStateOf(HistoryFilter.ALL.name) }
    val activeFilter = HistoryFilter.valueOf(selectedFilter)
    val filteredRecords = records.filterFor(activeFilter)

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { HistoryTopBar() },
        bottomBar = {
            HistoryBottomBar(
                onHomeClick = onHomeClick,
                onScanClick = onScanClick,
                onShareClick = onShareClick,
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
                    start = 26.dp,
                    top = innerPadding.calculateTopPadding() + 28.dp,
                    end = 26.dp,
                    bottom = innerPadding.calculateBottomPadding() + 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { HistoryHeader() }
                    if (statusMessage.isNotBlank()) {
                        item {
                            val isError = statusMessage.contains("không", ignoreCase = true) ||
                                statusMessage.contains("lỗi", ignoreCase = true) ||
                                statusMessage.contains("chưa", ignoreCase = true)
                            androidx.compose.material3.Surface(
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                color = if (isError) Color(0xFFFFEDED) else Color(0xFFE8F5E9),
                            ) {
                                androidx.compose.foundation.layout.Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isError) Color(0xFFD93025) else Color(0xFF2E7D32)),
                                    )
                                    androidx.compose.material3.Text(
                                        text = statusMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isError) Color(0xFFD93025) else Color(0xFF2E7D32),
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                item {
                    FilterTabs(
                        selected = activeFilter,
                        onSelect = { selectedFilter = it.name },
                    )
                }

                if (filteredRecords.isEmpty()) {
                    item { EmptyHistoryCard(activeFilter = activeFilter) }
                } else {
                    items(
                        items = filteredRecords,
                        key = { it.id },
                    ) { record ->
                        HistoryNetworkCard(
                            model = record.toHistoryUiModel(
                                isLatest = record.id == records.firstOrNull()?.id,
                            ),
                            onClick = { onNetworkClick(record) },
                        )
                    }
                }

                item {
                    AnalyticsCard(records = records)
                }
            }
        }
    }
}

@Composable
private fun HistoryTopBar() {
    Surface(
        color = ScreenBackground.copy(alpha = 0.94f),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 26.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = "Menu",
                    tint = BrandPrimary,
                    modifier = Modifier.size(23.dp),
                )
                Text(
                    text = "SmartWiFi-Connect",
                    color = BrandPrimaryDark,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = Color(0xFFF5D8C6),
                shadowElevation = 2.dp,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFFFFEEE0), Color(0xFFD28A67)),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "D",
                        color = SurfaceWhite,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryHeader() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Lịch sử kết nối",
            color = TextPrimary,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = "Quản lý và kết nối lại với các mạng đã sử dụng.",
            color = Color(0xFF4F5564),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun FilterTabs(
    selected: HistoryFilter,
    onSelect: (HistoryFilter) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(HistoryFilter.entries) { filter ->
            val isSelected = filter == selected
            Surface(
                onClick = { onSelect(filter) },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) BrandPrimary else SurfaceWhite,
                shadowElevation = if (isSelected) 8.dp else 1.dp,
                border = if (isSelected) null else BorderStroke(1.dp, SurfaceStroke),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = filter.icon,
                        contentDescription = null,
                        tint = if (isSelected) SurfaceWhite else Color(0xFF585E6B),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = filter.label,
                        color = if (isSelected) SurfaceWhite else Color(0xFF555B68),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryNetworkCard(
    model: HistoryNetworkUiModel,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = SurfaceWhite,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, SurfaceStroke),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (model.isPublic) SurfaceMuted else BrandSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (model.isPublic) Icons.Rounded.WifiOff else Icons.Rounded.Wifi,
                        contentDescription = null,
                        tint = if (model.isPublic) Color(0xFF555864) else BrandPrimary,
                        modifier = Modifier.size(32.dp),
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = model.name,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        if (model.connected) {
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = SuccessSoft,
                            ) {
                                Text(
                                    text = "ĐÃ KẾT NỐI",
                                    color = Success,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                            }
                        }
                        Text(
                            text = model.timeLabel,
                            color = TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Icon(
                        imageVector = if (model.isPublic) Icons.Outlined.Public else Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = if (model.isPublic) Color(0xFF777B87) else Success,
                        modifier = Modifier.size(18.dp),
                    )
                    Box(
                        modifier = Modifier
                            .width(22.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            .background(if (model.isPublic) Color(0xFF4F5160) else Color(0xFF05735E)),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0xFFF0F2F7)),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    model.tags.forEach { tag ->
                        HistoryTag(tag = tag)
                    }
                }
                if (model.connected) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = BrandSoft,
                    ) {
                        Text(
                            text = "Quản lý",
                            color = BrandPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryTag(tag: HistoryTagUiModel) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = tag.background,
    ) {
        Text(
            text = tag.label,
            color = tag.foreground,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun EmptyHistoryCard(activeFilter: HistoryFilter) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = SurfaceWhite,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, SurfaceStroke),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BrandSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = null,
                    tint = BrandPrimary,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(
                text = "Chưa có lịch sử phù hợp",
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = when (activeFilter) {
                    HistoryFilter.ALL -> "Sau khi bạn quét QR/OCR và parse thành công, mạng Wi-Fi sẽ xuất hiện ở đây."
                    HistoryFilter.SECURE -> "Chưa có mạng bảo mật nào trong lịch sử đã lưu."
                    HistoryFilter.PUBLIC -> "Chưa có mạng công cộng nào trong lịch sử đã lưu."
                },
                color = TextMuted,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun AnalyticsCard(records: List<SavedWifiRecord>) {
    val total = records.size
    val thisMonth = records.count { it.createdAtMillis.isInCurrentMonth() }
    val mostUsed = records
        .filter { it.ssid.isNotBlank() }
        .groupingBy { it.ssid }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
        ?: "Chưa có"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, Color(0xFFDCE4F1)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(AnalyticsFill, AnalyticsFillBottom),
                    ),
                )
                .padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Analytics,
                        contentDescription = null,
                        tint = BrandPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Phân tích mạng",
                        color = BrandPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Text(
                    text = "30 NGÀY QUA",
                    color = Color(0xFF8787D8),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AnalyticsMetricCard(
                    modifier = Modifier.weight(1f),
                    label = "TỔNG KẾT NỐI",
                    value = total.toString(),
                    footer = "+$thisMonth tháng này",
                    footerColor = Success,
                )
                AnalyticsMetricCard(
                    modifier = Modifier.weight(1f),
                    label = "DÙNG NHIỀU NHẤT",
                    value = mostUsed,
                    footer = "SSID gần đây",
                    icon = Icons.Outlined.VerifiedUser,
                    footerColor = TextMuted,
                )
            }
        }
    }
}

@Composable
private fun AnalyticsMetricCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    footer: String,
    footerColor: Color,
    icon: ImageVector? = null,
) {
    Surface(
        modifier = modifier.height(102.dp),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceWhite,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                color = TextMuted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = BrandPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = value,
                    color = TextPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = footer,
                color = footerColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
@SuppressLint("UnusedBoxWithConstraintsScope")
private fun HistoryBottomBar(
    onHomeClick: () -> Unit,
    onScanClick: () -> Unit,
    onShareClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val tabs = listOf(
        HistoryBottomTab.HOME,
        HistoryBottomTab.SCAN,
        HistoryBottomTab.SHARE,
        HistoryBottomTab.HISTORY,
        HistoryBottomTab.SETTINGS,
    )
    val selectedTab = HistoryBottomTab.HISTORY

    fun onTabClick(tab: HistoryBottomTab) {
        when (tab) {
            HistoryBottomTab.HOME -> onHomeClick()
            HistoryBottomTab.SCAN -> onScanClick()
            HistoryBottomTab.SHARE -> onShareClick()
            HistoryBottomTab.HISTORY -> onHistoryClick()
            HistoryBottomTab.SETTINGS -> onSettingsClick()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
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
            val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
            val animatedOffset by animateDpAsState(
                targetValue = (itemWidth + spacing) * selectedIndex,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 620f),
                label = "historyBottomNavIndicatorOffset",
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
                        HistoryBottomNavItem(
                            modifier = Modifier.width(itemWidth),
                            label = tab.label,
                            icon = tab.icon,
                            selected = tab == selectedTab,
                            onClick = { onTabClick(tab) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryBottomNavItem(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val contentScale by animateFloatAsState(
        targetValue = if (selected) 1.03f else 1f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 720f),
        label = "historyBottomNavContentScale",
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
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
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

private data class HistoryNetworkUiModel(
    val name: String,
    val timeLabel: String,
    val connected: Boolean,
    val isPublic: Boolean,
    val tags: List<HistoryTagUiModel>,
)

private data class HistoryTagUiModel(
    val label: String,
    val background: Color,
    val foreground: Color,
)

private fun List<SavedWifiRecord>.filterFor(filter: HistoryFilter): List<SavedWifiRecord> {
    return when (filter) {
        HistoryFilter.ALL -> this
        HistoryFilter.SECURE -> filter { it.password.isNotBlank() }
        HistoryFilter.PUBLIC -> filter { it.password.isBlank() }
    }
}

private fun SavedWifiRecord.toHistoryUiModel(isLatest: Boolean): HistoryNetworkUiModel {
    val inferredTags = buildList {
        if (ssid.contains("5g", ignoreCase = true)) {
            add(HistoryTagUiModel("5G", Color(0xFFE2F4FF), Color(0xFF2575A9)))
        }
        if (password.isNotBlank()) {
            add(HistoryTagUiModel("WPA", Teal, Color(0xFF006A56)))
        } else {
            add(HistoryTagUiModel("OPEN", Color(0xFFE9EDF4), Color(0xFF555B68)))
        }
        if (aiConfidence != null) {
            add(HistoryTagUiModel("AI ${(aiConfidence * 100).roundToInt()}%", BrandSoft, BrandPrimary))
        } else if (sourceFormat.isNotBlank()) {
            add(HistoryTagUiModel(sourceFormat.uppercase(Locale.ROOT), BrandSoft, BrandPrimary))
        }
    }.take(3)

    return HistoryNetworkUiModel(
        name = ssid.ifBlank { "Unknown Wi-Fi" },
        timeLabel = createdAtMillis.toRelativeLabel(),
        connected = isLatest,
        isPublic = password.isBlank(),
        tags = inferredTags,
    )
}

private fun Long.toRelativeLabel(nowMillis: Long = System.currentTimeMillis()): String {
    val diff = (nowMillis - this).coerceAtLeast(0)
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour

    return when {
        diff < minute -> "Vừa xong"
        diff < hour -> "${diff / minute} phút trước"
        diff < day -> "${diff / hour} giờ trước"
        diff < 2 * day -> "Hôm qua"
        else -> SimpleDateFormat("dd 'tháng' MM, yyyy", Locale.forLanguageTag("vi-VN")).format(this)
    }
}

private fun Long.isInCurrentMonth(): Boolean {
    val current = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = this@isInCurrentMonth }
    return current.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
        current.get(Calendar.MONTH) == target.get(Calendar.MONTH)
}

@Preview(showBackground = true, backgroundColor = 0xFFF7F9FC)
@Composable
private fun HistoryScreenPreview() {
    val now = System.currentTimeMillis()
    val sampleRecords = listOf(
        SavedWifiRecord(
            id = 3,
            baseUrl = "",
            ocrText = "",
            ssid = "Home_Cloud_5G",
            password = "secret",
            sourceFormat = "qr",
            confidence = 0.98,
            aiConfidence = 0.94,
            aiSuggestion = "",
            aiRecommendation = "",
            aiShouldAutoConnect = true,
            aiFlags = emptyList(),
            fuzzyBestMatch = null,
            fuzzyScore = null,
            createdAtMillis = now - 2 * 60_000L,
        ),
        SavedWifiRecord(
            id = 2,
            baseUrl = "",
            ocrText = "",
            ssid = "Starbucks_Guest",
            password = "",
            sourceFormat = "ocr",
            confidence = 0.82,
            aiConfidence = null,
            aiSuggestion = "",
            aiRecommendation = "",
            aiShouldAutoConnect = false,
            aiFlags = emptyList(),
            fuzzyBestMatch = null,
            fuzzyScore = null,
            createdAtMillis = now - 26 * 60 * 60_000L,
        ),
    )

    SmartWifiAppTheme {
        HistoryScreen(
            records = sampleRecords,
            onNetworkClick = {},
            onHomeClick = {},
            onScanClick = {},
            onShareClick = {},
            onHistoryClick = {},
            onSettingsClick = {},
        )
    }
}
