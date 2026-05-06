package com.example.smartwificonnect.feature.settings

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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.ui.unit.sp
import com.example.smartwificonnect.ui.theme.SmartWifiAppTheme

private data class SettingsColors(
    val screenBackground: Color,
    val screenBackgroundBottom: Color,
    val surface: Color,
    val surfaceStroke: Color,
    val divider: Color,
    val brandPrimary: Color,
    val brandPrimaryDark: Color,
    val brandSoft: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val sectionLabel: Color,
    val success: Color,
    val danger: Color,
    val bottomNavFill: Color,
    val bottomNavStroke: Color,
    val neutralIconBackground: Color,
    val neutralIconTint: Color,
    val chevron: Color,
    val navUnselected: Color,
    val switchOffTrack: Color,
    val footerPrimary: Color,
    val footerSecondary: Color,
)

private val LightSettingsColors = SettingsColors(
    screenBackground = Color(0xFFF7F9FC),
    screenBackgroundBottom = Color(0xFFEFF3FB),
    surface = Color(0xFFFFFFFF),
    surfaceStroke = Color(0xFFE8ECF3),
    divider = Color(0xFFF0F2F6),
    brandPrimary = Color(0xFF5857F2),
    brandPrimaryDark = Color(0xFF4043D5),
    brandSoft = Color(0xFFEDEBFF),
    textPrimary = Color(0xFF161922),
    textMuted = Color(0xFF656B78),
    sectionLabel = Color(0xFF8A88E8),
    success = Color(0xFF047B62),
    danger = Color(0xFFE11919),
    bottomNavFill = Color(0xF7FFFFFF),
    bottomNavStroke = Color(0xFFF1F5F9),
    neutralIconBackground = Color(0xFFEDEFF3),
    neutralIconTint = Color(0xFF5F6470),
    chevron = Color(0xFFB5BAC4),
    navUnselected = Color(0xFF090B11),
    switchOffTrack = Color(0xFFD9DEE3),
    footerPrimary = Color(0xFF969BA8),
    footerSecondary = Color(0xFFB2B7C1),
)

private val DarkSettingsColors = SettingsColors(
    screenBackground = Color(0xFF10131B),
    screenBackgroundBottom = Color(0xFF171A24),
    surface = Color(0xFF1F2430),
    surfaceStroke = Color(0xFF2C3342),
    divider = Color(0xFF2A3140),
    brandPrimary = Color(0xFF7A7CFF),
    brandPrimaryDark = Color(0xFFA7A8FF),
    brandSoft = Color(0xFF292A58),
    textPrimary = Color(0xFFF4F6FB),
    textMuted = Color(0xFFABB2C1),
    sectionLabel = Color(0xFFA9A8FF),
    success = Color(0xFF6EE2C2),
    danger = Color(0xFFFF6D68),
    bottomNavFill = Color(0xF21A1F2B),
    bottomNavStroke = Color(0xFF293142),
    neutralIconBackground = Color(0xFF303746),
    neutralIconTint = Color(0xFFC3CAD7),
    chevron = Color(0xFF7D8595),
    navUnselected = Color(0xFFE4E8F0),
    switchOffTrack = Color(0xFF525B68),
    footerPrimary = Color(0xFF9AA3B2),
    footerSecondary = Color(0xFF717B8D),
)

private val LocalSettingsColors = staticCompositionLocalOf { LightSettingsColors }

private enum class SettingsBottomTab(
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
fun SettingsScreen(
    isDarkModeEnabled: Boolean,
    autoConnectSubtitle: String,
    priorityNetworkSubtitle: String,
    onDarkModeChange: (Boolean) -> Unit,
    onAutoConnectClick: () -> Unit,
    onPriorityNetworkClick: () -> Unit,
    onHomeClick: () -> Unit,
    onScanClick: () -> Unit,
    onShareClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    CompositionLocalProvider(
        LocalSettingsColors provides if (isDarkModeEnabled) DarkSettingsColors else LightSettingsColors,
    ) {
        val colors = LocalSettingsColors.current
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { SettingsTopBar() },
            bottomBar = {
                SettingsBottomBar(
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
                            colors = listOf(colors.screenBackground, colors.screenBackgroundBottom),
                        ),
                    ),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        top = innerPadding.calculateTopPadding() + 26.dp,
                        end = 12.dp,
                        bottom = innerPadding.calculateBottomPadding() + 30.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    item {
                        SettingsSection(title = "KẾT NỐI") {
                            SettingsRow(
                                icon = Icons.Outlined.AutoAwesome,
                                iconBackground = if (isDarkModeEnabled) Color(0xFF173F38) else Color(0xFFD8FFF2),
                                iconTint = if (isDarkModeEnabled) Color(0xFF7EF5D8) else Color(0xFF008A74),
                                title = "Tự động kết nối",
                                subtitle = autoConnectSubtitle,
                                trailing = { Chevron() },
                                onClick = onAutoConnectClick,
                            )
                            SettingsDivider()
                            SettingsRow(
                                icon = Icons.Outlined.WifiTethering,
                                iconBackground = if (isDarkModeEnabled) Color(0xFF173545) else Color(0xFFE5F8FF),
                                iconTint = if (isDarkModeEnabled) Color(0xFF74DFFF) else Color(0xFF007D9C),
                                title = "Ưu tiên mạng",
                                subtitle = priorityNetworkSubtitle,
                                trailing = { Chevron() },
                                onClick = onPriorityNetworkClick,
                            )
                        }
                    }
                    item {
                        SettingsSection(title = "HỆ THỐNG & QUYỀN RIÊNG TƯ") {
                            SettingsRow(
                                icon = Icons.Outlined.NotificationsNone,
                                iconBackground = colors.neutralIconBackground,
                                iconTint = colors.neutralIconTint,
                                title = "Cài đặt thông báo",
                                subtitle = "Quản lý cảnh báo và âm thanh",
                                trailing = { Chevron() },
                            )
                            SettingsDivider()
                            SettingsRow(
                                icon = Icons.Outlined.DarkMode,
                                iconBackground = colors.neutralIconBackground,
                                iconTint = colors.neutralIconTint,
                                title = "Chế độ tối",
                                subtitle = if (isDarkModeEnabled) "Đang dùng giao diện tối" else "Chuyển sang giao diện tối",
                                trailing = {
                                    SettingsSwitch(
                                        checked = isDarkModeEnabled,
                                        onCheckedChange = onDarkModeChange,
                                    )
                                },
                                onClick = { onDarkModeChange(!isDarkModeEnabled) },
                            )
                            SettingsDivider()
                            SettingsRow(
                                icon = Icons.Outlined.DeleteOutline,
                                iconBackground = if (isDarkModeEnabled) Color(0xFF4B2327) else Color(0xFFFFF2F0),
                                iconTint = colors.danger,
                                title = "Xóa lịch sử",
                                subtitle = "Xóa tất cả nhật ký kết nối",
                                titleColor = colors.danger,
                            )
                        }
                    }
                    item {
                        SettingsSection(title = "GIỚI THIỆU") {
                            SettingsRow(
                                icon = Icons.Outlined.Info,
                                iconBackground = colors.neutralIconBackground,
                                iconTint = colors.neutralIconTint,
                                title = "Về ứng dụng",
                                subtitle = "v2.4.0 • Bản ổn định mới nhất",
                                trailing = { Chevron() },
                            )
                        }
                    }
                    item { SettingsFooter() }
                }
            }
        }
    }
}

@Composable
private fun SettingsTopBar() {
    val colors = LocalSettingsColors.current
    Surface(
        color = colors.screenBackground.copy(alpha = 0.94f),
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, top = 16.dp, end = 24.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = "Menu",
                    tint = colors.brandPrimaryDark,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = "Cài đặt",
                    color = colors.brandPrimaryDark,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = colors.brandSoft,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WifiTethering,
                        contentDescription = null,
                        tint = colors.brandPrimaryDark,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Wi-Fi",
                        color = colors.brandPrimaryDark,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalSettingsColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = title,
            color = colors.sectionLabel,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.sp,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = colors.surface,
            shadowElevation = 2.dp,
            border = BorderStroke(1.dp, colors.surfaceStroke),
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconBackground: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    titleColor: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalSettingsColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                color = titleColor ?: colors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = colors.textMuted,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
private fun SettingsDivider() {
    val colors = LocalSettingsColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 72.dp)
            .height(1.dp)
            .background(colors.divider),
    )
}

@Composable
private fun Chevron() {
    val colors = LocalSettingsColors.current
    Icon(
        imageVector = Icons.Rounded.ChevronRight,
        contentDescription = null,
        tint = colors.chevron,
        modifier = Modifier.size(25.dp),
    )
}

@Composable
private fun SettingsSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalSettingsColors.current
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.size(width = 48.dp, height = 28.dp),
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.surface,
            checkedTrackColor = colors.brandPrimaryDark,
            uncheckedThumbColor = colors.surface,
            uncheckedTrackColor = colors.switchOffTrack,
            uncheckedBorderColor = Color.Transparent,
            checkedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
private fun SettingsFooter() {
    val colors = LocalSettingsColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 38.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "SMARTWIFI-CONNECT FRAMEWORK",
            color = colors.footerPrimary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "© 2024 Connectivity Solutions Inc.",
            color = colors.footerSecondary,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
@SuppressLint("UnusedBoxWithConstraintsScope")
private fun SettingsBottomBar(
    onHomeClick: () -> Unit,
    onScanClick: () -> Unit,
    onShareClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val colors = LocalSettingsColors.current
    var currentTabName by rememberSaveable { mutableStateOf(SettingsBottomTab.SETTINGS.name) }
    val currentTab = SettingsBottomTab.valueOf(currentTabName)
    val tabs = listOf(
        SettingsBottomTab.HOME,
        SettingsBottomTab.SCAN,
        SettingsBottomTab.SHARE,
        SettingsBottomTab.HISTORY,
        SettingsBottomTab.SETTINGS,
    )

    fun onTabClick(tab: SettingsBottomTab) {
        currentTabName = tab.name
        when (tab) {
            SettingsBottomTab.HOME -> onHomeClick()
            SettingsBottomTab.SCAN -> onScanClick()
            SettingsBottomTab.SHARE -> onShareClick()
            SettingsBottomTab.HISTORY -> onHistoryClick()
            SettingsBottomTab.SETTINGS -> onSettingsClick()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = colors.bottomNavFill,
        shadowElevation = 30.dp,
        border = BorderStroke(2.dp, colors.bottomNavStroke),
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
                label = "settingsBottomNavIndicatorOffset",
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
    val colors = LocalSettingsColors.current
    val contentScale by animateFloatAsState(
        targetValue = if (selected) 1.03f else 1f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 720f),
        label = "settingsBottomNavContentScale",
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
            tint = if (selected) Color.White else colors.navUnselected,
            modifier = Modifier.size(if (selected) 22.dp else 21.dp),
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = if (selected) Color.White else colors.navUnselected,
            style = MaterialTheme.typography.labelLarge.copy(
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

@Preview(showBackground = true, widthDp = 390, heightDp = 1112)
@Composable
private fun SettingsScreenPreview() {
    SmartWifiAppTheme {
        SettingsScreen(
            isDarkModeEnabled = false,
            autoConnectSubtitle = "Đã tìm thấy mạng đã lưu ở gần đây",
            priorityNetworkSubtitle = "Ưu tiên mạng mạnh nhất nếu đang khả dụng",
            onDarkModeChange = {},
            onAutoConnectClick = {},
            onPriorityNetworkClick = {},
            onHomeClick = {},
            onScanClick = {},
            onShareClick = {},
            onHistoryClick = {},
            onSettingsClick = {},
        )
    }
}
