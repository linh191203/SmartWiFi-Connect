package com.example.smartwificonnect.feature.share

import android.annotation.SuppressLint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smartwificonnect.ui.theme.LocalAppDarkMode
import com.example.smartwificonnect.ui.theme.SmartWifiAppTheme

private val ShareBackground: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF10131B) else Color(0xFFF7F9FC)
private val ShareBackgroundBottom: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF171B24) else Color(0xFFF3F7FE)
private val ShareSurface: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF1F2430) else Color(0xFFFFFFFF)
private val ShareSurfaceSoft: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF252B38) else Color(0xFFF1F4FB)
private val ShareTextPrimary: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFFF4F6FB) else Color(0xFF1E222B)
private val ShareTextMuted: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFFABB2C1) else Color(0xFF5E6473)
private val ShareTextHint: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF7F8797) else Color(0xFF8A8E9A)
private val ShareBrand: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF8D90FF) else Color(0xFF5860F0)
private val ShareBrandDark: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFFA7A8FF) else Color(0xFF4C5AE6)
private val ShareBrandSoft: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF30337D) else Color(0xFFE8E9FF)
private val ShareStroke: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF2B3344) else Color(0xFFE8ECF4)
private val ShareBottomNavFill: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xF21A1F2B) else Color(0xF7FFFFFF)
private val ShareBottomNavStroke: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF293142) else Color(0xFFF1F5F9)

private enum class ShareBottomTab(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Trang chủ", Icons.Outlined.Home),
    SCAN("Quét", Icons.Outlined.QrCode2),
    SHARE("Chia sẻ", Icons.Outlined.IosShare),
    HISTORY("Lịch sử", Icons.Outlined.History),
    SETTINGS("Cài đặt", Icons.Outlined.Settings),
}

private data class ShareDeviceUiModel(
    val name: String,
    val status: String,
    val actionLabel: String,
    val accent: Color,
)

@Composable
fun ShareWifiScreen(
    networkName: String?,
    hasShareableNetwork: Boolean,
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit,
    onScanClick: () -> Unit,
    onShareClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val shareableName = networkName?.takeIf { it.isNotBlank() } ?: "Wi-Fi hiện tại"
    var secondDeviceAccepted by rememberSaveable { mutableStateOf(false) }
    val shareBrand = ShareBrand
    val devices = if (hasShareableNetwork) {
        listOf(
            ShareDeviceUiModel(
                name = "iPhone của Minh",
                status = "Sẵn sàng kết nối",
                actionLabel = "Chia sẻ",
                accent = shareBrand,
            ),
            ShareDeviceUiModel(
                name = "Samsung S23",
                status = if (secondDeviceAccepted) "Đã chấp nhận" else "Đang yêu cầu...",
                actionLabel = if (secondDeviceAccepted) "Đã gửi" else "Chấp nhận",
                accent = if (secondDeviceAccepted) Color(0xFF17876D) else shareBrand,
            ),
        )
    } else {
        emptyList()
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ShareTopBar(onBackClick = onBackClick)
        },
        bottomBar = {
            ShareBottomBar(
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
                        colors = listOf(ShareBackground, ShareBackgroundBottom),
                    ),
                ),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 24.dp,
                    top = innerPadding.calculateTopPadding() + 18.dp,
                    end = 24.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                item {
                    ShareRadar(
                        enabled = hasShareableNetwork,
                    )
                }
                item {
                    ShareHeadline(
                        enabled = hasShareableNetwork,
                        networkName = shareableName,
                    )
                }
                item {
                    ShareSectionLabel(
                        text = if (hasShareableNetwork) "THIẾT BỊ TÌM THẤY" else "TRẠNG THÁI KẾT NỐI",
                    )
                }
                if (hasShareableNetwork) {
                    items(devices.size) { index ->
                        val device = devices[index]
                        ShareDeviceCard(
                            device = device,
                            onActionClick = {
                                if (device.name == "Samsung S23") {
                                    secondDeviceAccepted = true
                                }
                            },
                        )
                    }
                } else {
                    item {
                        ShareEmptyCard(networkName = networkName)
                    }
                }
                item {
                    ShareCancelButton(
                        onClick = onBackClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareTopBar(onBackClick: () -> Unit) {
    Surface(
        color = ShareBackground.copy(alpha = 0.94f),
        shadowElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 16.dp, end = 12.dp, bottom = 18.dp),
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(40.dp),
                shape = CircleShape,
                color = Color.Transparent,
                onClick = onBackClick,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Quay lại",
                        tint = ShareBrandDark,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Text(
                text = "Chia sẻ Wi-Fi",
                color = ShareBrandDark,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.align(Alignment.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ShareRadar(enabled: Boolean) {
    val shareBrand = ShareBrand
    val infiniteTransition = rememberInfiniteTransition(label = "shareRadar")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(animation = tween(2200)),
        label = "shareRadarPulse",
    )
    val orbitRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
        ),
        label = "shareRadarOrbitForward",
    )
    val orbitRotationReverse by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7600, easing = LinearEasing),
        ),
        label = "shareRadarOrbitReverse",
    )
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600),
        ),
        label = "shareRadarRingScale",
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200),
        ),
        label = "shareRadarRingAlpha",
    )
    val centerFill by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.72f,
        animationSpec = tween(400),
        label = "shareRadarCenterFill",
    )

    Box(
        modifier = Modifier.size(310.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = ringScale
                    scaleY = ringScale
                    alpha = if (enabled) ringAlpha else 0.72f
                },
        ) {
            val ringColor = shareBrand.copy(alpha = 0.22f)
            val strokeColor = shareBrand.copy(alpha = 0.28f)
            val radii = listOf(1f, 0.77f, 0.53f)
            radii.forEachIndexed { index, factor ->
                val diameter = size.minDimension * factor
                drawCircle(
                    color = ringColor.copy(alpha = 0.11f + index * 0.03f),
                    radius = diameter / 2f,
                )
                drawCircle(
                    color = strokeColor,
                    radius = diameter / 2f,
                    style = Stroke(width = 2.2f),
                )
            }
        }

        Box(
            modifier = Modifier
                .size((134f * pulseScale).dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            shareBrand.copy(alpha = 0.18f * centerFill),
                            shareBrand.copy(alpha = 0.04f * centerFill),
                        ),
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = orbitRotation },
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 54.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (enabled) Color(0xFF0B7E72) else ShareStroke),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = orbitRotationReverse },
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 20.dp, y = 34.dp)
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(if (enabled) Color(0xFF8B90FF) else ShareStroke),
            )
        }

        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(ShareBrandDark, ShareBrand),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.WifiTethering,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(44.dp),
            )
        }
    }
}

@Composable
private fun ShareHeadline(
    enabled: Boolean,
    networkName: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (enabled) "Đang tìm thiết bị ở gần..." else "Chưa có mạng để chia sẻ",
            color = ShareTextPrimary,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = if (enabled) {
                "Hãy để hai điện thoại gần nhau\nđể chia sẻ mật khẩu an toàn\nqua mạng $networkName"
            } else {
                "Thiết bị của bạn cần kết nối Wi-Fi trước,\nsau đó màn này sẽ cho phép chia sẻ\nvới thiết bị ở gần."
            },
            color = ShareTextMuted,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun ShareSectionLabel(text: String) {
    Text(
        text = text,
        color = ShareTextHint,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ShareDeviceCard(
    device: ShareDeviceUiModel,
    onActionClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = ShareSurface,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, ShareStroke),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(ShareBrandSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhoneAndroid,
                    contentDescription = null,
                    tint = ShareBrand,
                    modifier = Modifier.size(30.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = device.name,
                    color = ShareTextPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = device.status,
                    color = ShareTextMuted,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (device.actionLabel == "Chia sẻ") ShareBrandSoft else device.accent,
                shadowElevation = if (device.actionLabel == "Chấp nhận") 10.dp else 0.dp,
                modifier = Modifier.clickable(onClick = onActionClick),
            ) {
                Text(
                    text = device.actionLabel,
                    color = if (device.actionLabel == "Chia sẻ") ShareBrand else Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun ShareEmptyCard(networkName: String?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = ShareSurface,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, ShareStroke),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(ShareSurfaceSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WifiTethering,
                        contentDescription = null,
                        tint = ShareBrand,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Kết nối một mạng trước",
                        color = ShareTextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = if (networkName.isNullOrBlank()) "Chưa phát hiện Wi-Fi đang hoạt động" else "Mạng gần nhất: $networkName",
                        color = ShareTextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                text = "Khi thiết bị của bạn đã vào Wi-Fi, màn này sẽ cho phép chia sẻ nhanh cho máy ở gần theo kiểu an toàn.",
                color = ShareTextMuted,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ShareCancelButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(999.dp),
        color = ShareSurfaceSoft,
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier.padding(vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Hủy",
                color = ShareTextPrimary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
@SuppressLint("UnusedBoxWithConstraintsScope")
private fun ShareBottomBar(
    onHomeClick: () -> Unit,
    onScanClick: () -> Unit,
    onShareClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    var currentTabName by rememberSaveable { mutableStateOf(ShareBottomTab.SHARE.name) }
    val currentTab = ShareBottomTab.valueOf(currentTabName)
    val tabs = listOf(
        ShareBottomTab.HOME,
        ShareBottomTab.SCAN,
        ShareBottomTab.SHARE,
        ShareBottomTab.HISTORY,
        ShareBottomTab.SETTINGS,
    )

    fun onTabClick(tab: ShareBottomTab) {
        currentTabName = tab.name
        when (tab) {
            ShareBottomTab.HOME -> onHomeClick()
            ShareBottomTab.SCAN -> onScanClick()
            ShareBottomTab.SHARE -> onShareClick()
            ShareBottomTab.HISTORY -> onHistoryClick()
            ShareBottomTab.SETTINGS -> onSettingsClick()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = ShareBottomNavFill,
        shadowElevation = 28.dp,
        border = BorderStroke(2.dp, ShareBottomNavStroke),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            val spacing = 4.dp
            val itemWidth = (maxWidth - spacing * (tabs.size - 1)) / tabs.size
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEach { tab ->
                    ShareBottomItem(
                        modifier = Modifier.width(itemWidth),
                        item = tab,
                        selected = tab == currentTab,
                        onClick = { onTabClick(tab) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareBottomItem(
    modifier: Modifier,
    item: ShareBottomTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val contentScale by animateFloatAsState(
        targetValue = if (selected) 1.03f else 1f,
        animationSpec = tween(250),
        label = "shareBottomScale",
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (selected) {
                    Brush.linearGradient(colors = listOf(ShareBrandDark, ShareBrand))
                } else {
                    Brush.linearGradient(colors = listOf(Color.Transparent, Color.Transparent))
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = if (selected) Color.White else ShareTextPrimary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.label,
            color = if (selected) Color.White else ShareTextPrimary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.offset(y = 0.dp),
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 942)
@Composable
private fun ShareWifiScreenPreview() {
    SmartWifiAppTheme {
        ShareWifiScreen(
            networkName = "Office_Main_Corp",
            hasShareableNetwork = true,
            onBackClick = {},
            onHomeClick = {},
            onScanClick = {},
            onShareClick = {},
            onHistoryClick = {},
            onSettingsClick = {},
        )
    }
}
