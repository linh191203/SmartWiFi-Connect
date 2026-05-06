package com.example.smartwificonnect.feature.networkdetail

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.smartwificonnect.NetworkDetailOrigin
import com.example.smartwificonnect.NetworkDetailUiModel
import com.example.smartwificonnect.NetworkLiveTelemetry
import com.example.smartwificonnect.ui.theme.LocalAppDarkMode
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DetailBackground: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF10131B) else Color(0xFFF7F9FC)
private val DetailBackgroundBottom: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF171B25) else Color(0xFFEFF4FB)
private val DetailSurface: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF1F2430) else Color(0xFFFFFFFF)
private val DetailSurfaceSoft: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF2B3140) else Color(0xFFF1F4FA)
private val DetailStroke: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF2D3443) else Color(0xFFE8EDF5)
private val DetailBrand = Color(0xFF4E53E8)
private val DetailBrandSoft: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF303789) else Color(0xFFE7E9FF)
private val DetailTextPrimary: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFFF3F6FB) else Color(0xFF1A1E27)
private val DetailTextMuted: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFFB0B7C7) else Color(0xFF646B7A)
private val DetailBottomFill: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xF21A1F2B) else Color(0xF7FFFFFF)
private val DetailBottomStroke: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF293142) else Color(0xFFF1F5F9)
private val DetailDanger = Color(0xFFE14646)
private val DetailDangerSoft: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF412126) else Color(0xFFFFECEC)

private enum class DetailBottomTab(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    HOME("Trang chủ", Icons.Outlined.Home),
    SCAN("Quét", Icons.Outlined.QrCode2),
    SHARE("Chia sẻ", Icons.Outlined.IosShare),
    HISTORY("Lịch sử", Icons.Outlined.History),
    SETTINGS("Cài đặt", Icons.Outlined.Settings),
}

@Composable
fun NetworkDetailScreen(
    detail: NetworkDetailUiModel?,
    liveTelemetry: NetworkLiveTelemetry?,
    isConnecting: Boolean,
    onBackClick: () -> Unit,
    onConnectClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRefreshTelemetry: () -> Unit,
    onHomeClick: () -> Unit,
    onScanClick: () -> Unit,
    onShareClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    LaunchedEffect(detail?.ssid, detail?.isConnected) {
        if (detail?.isConnected != true) return@LaunchedEffect
        while (true) {
            onRefreshTelemetry()
            delay(1500)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            NetworkDetailTopBar(
                onBackClick = onBackClick,
                title = "Chi tiết mạng",
            )
        },
        bottomBar = {
            NetworkDetailBottomBar(
                activeTab = when (detail?.origin) {
                    NetworkDetailOrigin.HISTORY -> DetailBottomTab.HISTORY
                    NetworkDetailOrigin.SHARE -> DetailBottomTab.SHARE
                    else -> DetailBottomTab.HOME
                },
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
                        colors = listOf(DetailBackground, DetailBackgroundBottom),
                    ),
                ),
        ) {
            if (detail == null) {
                EmptyNetworkDetail(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 18.dp,
                        top = innerPadding.calculateTopPadding() + 14.dp,
                        end = 18.dp,
                        bottom = innerPadding.calculateBottomPadding() + 28.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    item {
                        NetworkDetailHero(
                            detail = detail,
                            liveTelemetry = liveTelemetry,
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            DetailInfoPill(
                                modifier = Modifier.weight(1f),
                                label = "GIAO THỨC",
                                value = detail.protocolLabel,
                                icon = if (detail.protocolLabel == "OPEN") {
                                    Icons.Rounded.Public
                                } else {
                                    Icons.Rounded.Lock
                                },
                            )
                            DetailInfoPill(
                                modifier = Modifier.weight(1f),
                                label = "TẦN SỐ",
                                value = liveTelemetry.frequencyLabelOr(detail.frequencyLabel),
                                icon = Icons.Rounded.Router,
                            )
                        }
                    }
                    item {
                        SignalStrengthCard(
                            qualityLabel = liveTelemetry.signalQualityOr(detail.signalQualityLabel),
                            signalDbm = liveTelemetry?.signalDbm ?: detail.signalDbm,
                        )
                    }
                    item {
                        SpeedAndUsageCard(
                            detail = detail,
                            liveTelemetry = liveTelemetry,
                        )
                    }
                    item {
                        SourceCard(detail = detail)
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PrimaryActionButton(
                                label = if (isConnecting) "Đang kết nối..." else "Kết nối ngay",
                                icon = Icons.Rounded.Bolt,
                                onClick = onConnectClick,
                                enabled = !isConnecting,
                            )
                            if (detail.canDelete) {
                                SecondaryDangerButton(
                                    label = "Xóa mạng này",
                                    icon = Icons.Rounded.DeleteOutline,
                                    onClick = onDeleteClick,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkDetailTopBar(
    onBackClick: () -> Unit,
    title: String,
) {
    Surface(
        color = DetailBackground.copy(alpha = 0.95f),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = DetailBrand,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable(onClick = onBackClick),
                )
                Text(
                    text = title,
                    color = DetailTextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            Icon(
                imageVector = Icons.Outlined.IosShare,
                contentDescription = null,
                tint = DetailTextMuted,
                modifier = Modifier.size(21.dp),
            )
        }
    }
}

@Composable
private fun NetworkDetailHero(
    detail: NetworkDetailUiModel,
    liveTelemetry: NetworkLiveTelemetry?,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            modifier = Modifier.size(92.dp),
            shape = CircleShape,
            color = DetailBrandSoft,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Wifi,
                    contentDescription = null,
                    tint = DetailBrand,
                    modifier = Modifier.size(46.dp),
                )
            }
        }
        Text(
            text = detail.ssid,
            color = DetailBrand,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (detail.isConnected && liveTelemetry != null) {
                "Đang kết nối • cập nhật ${liveTelemetry.updatedAtMillis.toTimeLabel()}"
            } else {
                detail.lastConnectedLabel
            },
            color = DetailTextMuted,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DetailInfoPill(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = DetailSurface,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, DetailStroke),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = label,
                color = DetailBrand,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = DetailTextPrimary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = value,
                    color = DetailTextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SignalStrengthCard(
    qualityLabel: String,
    signalDbm: Int?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = DetailSurface,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, DetailStroke),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "ĐỘ MẠNH TÍN HIỆU",
                    color = DetailBrand,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = signalDbm?.let { "$qualityLabel ($it dBm)" } ?: qualityLabel,
                    color = DetailTextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            SignalBars(signalDbm = signalDbm)
        }
    }
}

@Composable
private fun SpeedAndUsageCard(
    detail: NetworkDetailUiModel,
    liveTelemetry: NetworkLiveTelemetry?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = DetailSurface,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, DetailStroke),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = if (detail.isConnected && liveTelemetry != null) "Tốc độ thực tế" else "Chưa có số liệu thực tế",
                        color = DetailTextPrimary,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = if (detail.isConnected && liveTelemetry != null) {
                            "Link speed, tốc độ nhận và phát đang lấy trực tiếp từ máy."
                        } else {
                            "Hãy kết nối vào đúng mạng này để đo Mbps và cập nhật thông số thật."
                        },
                        color = DetailTextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Surface(
                    modifier = Modifier.widthIn(min = 132.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = DetailSurfaceSoft,
                ) {
                    Text(
                        text = if (detail.isConnected && liveTelemetry != null) {
                            liveTelemetry.linkSpeedMbps?.let { "$it Mbps" } ?: "Dang do"
                        } else {
                            "Can ket noi"
                        },
                        color = DetailBrand,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
            }

            if (detail.isConnected && liveTelemetry != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SpeedMetric(
                        modifier = Modifier.weight(1f),
                        label = "LINK",
                        value = liveTelemetry.linkSpeedMbps?.let { "$it Mbps" } ?: "--",
                    )
                    SpeedMetric(
                        modifier = Modifier.weight(1f),
                        label = "RX",
                        value = liveTelemetry.rxLinkSpeedMbps?.let { "$it Mbps" } ?: "--",
                    )
                    SpeedMetric(
                        modifier = Modifier.weight(1f),
                        label = "TX",
                        value = liveTelemetry.txLinkSpeedMbps?.let { "$it Mbps" } ?: "--",
                    )
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = DetailSurfaceSoft,
                ) {
                    Text(
                        text = "Chua co du lieu toc do hoac luu luong thuc te cho mang nay.",
                        color = DetailTextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedMetric(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = DetailSurfaceSoft,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                color = DetailTextMuted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = value,
                color = DetailTextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun UsageChart(
    bars: List<Float>,
    highlightLabel: String,
) {
    val highlightIndex = bars.indices.maxByOrNull { bars[it] } ?: 0
    val labels = listOf("T2", "T3", "T4", "T5", "T6", "T7")

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(164.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.Bottom,
            ) {
                bars.forEachIndexed { index, value ->
                    val isHighlight = index == highlightIndex
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (isHighlight) {
                            Text(
                                text = highlightLabel,
                                color = DetailBrand,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        } else {
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                        Box(
                            modifier = Modifier
                                .width(34.dp)
                                .height((value * 98f).dp)
                                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                                .background(
                                    if (isHighlight) {
                                        Brush.verticalGradient(
                                            colors = listOf(DetailBrand, Color(0xFF5E63F2)),
                                        )
                                    } else {
                                        Brush.verticalGradient(
                                            colors = listOf(Color(0xFFE5E8EF), Color(0xFFD8DDE7)),
                                        )
                                    },
                                ),
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DetailStroke),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            labels.forEach { label ->
                Text(
                    text = label,
                    color = DetailTextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun SourceCard(detail: NetworkDetailUiModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = DetailSurface,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, DetailStroke),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFD8F0FF)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocationOn,
                    contentDescription = null,
                    tint = Color(0xFF2E7FBE),
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = detail.sourceTitle,
                    color = DetailTextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = detail.sourceSubtitle,
                    color = DetailTextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = DetailSurfaceSoft,
            ) {
                Text(
                    text = detail.sourceBadgeLabel,
                    color = DetailBrand,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PrimaryActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = Color.Transparent,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = if (enabled) {
                            listOf(Color(0xFF4C53EB), Color(0xFF5A61F4))
                        } else {
                            listOf(Color(0xFF999FD9), Color(0xFFA7ABE0))
                        },
                    ),
                )
                .padding(horizontal = 18.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun SecondaryDangerButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = DetailDangerSoft,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = DetailDanger,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                color = DetailDanger,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun SignalBars(signalDbm: Int?) {
    val activeBars = when {
        signalDbm == null -> 0
        signalDbm >= -50 -> 5
        signalDbm >= -60 -> 4
        signalDbm >= -70 -> 3
        signalDbm >= -80 -> 2
        else -> 1
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        listOf(8.dp, 14.dp, 20.dp, 28.dp, 36.dp).forEachIndexed { index, height ->
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(height)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (index < activeBars) DetailBrand else DetailStroke,
                    ),
            )
        }
    }
}

@Composable
private fun EmptyNetworkDetail(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = DetailBrandSoft,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Wifi,
                        contentDescription = null,
                        tint = DetailBrand,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
            Text(
                text = "Chưa có mạng để hiển thị",
                color = DetailTextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "Hãy chọn một mạng từ Trang chủ hoặc Lịch sử để xem chi tiết.",
                color = DetailTextMuted,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 36.dp),
            )
        }
    }
}

@Composable
private fun NetworkDetailBottomBar(
    activeTab: DetailBottomTab,
    onHomeClick: () -> Unit,
    onScanClick: () -> Unit,
    onShareClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val tabs = listOf(
        DetailBottomTab.HOME,
        DetailBottomTab.SCAN,
        DetailBottomTab.SHARE,
        DetailBottomTab.HISTORY,
        DetailBottomTab.SETTINGS,
    )

    fun onTabClick(tab: DetailBottomTab) {
        when (tab) {
            DetailBottomTab.HOME -> onHomeClick()
            DetailBottomTab.SCAN -> onScanClick()
            DetailBottomTab.SHARE -> onShareClick()
            DetailBottomTab.HISTORY -> onHistoryClick()
            DetailBottomTab.SETTINGS -> onSettingsClick()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = DetailBottomFill,
        shadowElevation = 30.dp,
        border = BorderStroke(2.dp, DetailBottomStroke),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            val spacing = 4.dp
            val indicatorHeight = 64.dp
            val itemWidth = (maxWidth - spacing * (tabs.size - 1)) / tabs.size
            val selectedIndex = tabs.indexOf(activeTab).coerceAtLeast(0)

            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .offset(x = (itemWidth + spacing) * selectedIndex)
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
                        DetailBottomNavItem(
                            modifier = Modifier.width(itemWidth),
                            label = tab.label,
                            icon = tab.icon,
                            selected = tab == activeTab,
                            onClick = { onTabClick(tab) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailBottomNavItem(
    modifier: Modifier = Modifier,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val contentScale by animateFloatAsState(
        targetValue = if (selected) 1.03f else 1f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 720f),
        label = "detailBottomNavScale",
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
            tint = if (selected) Color.White else Color(0xFF090B11),
            modifier = Modifier.size(if (selected) 22.dp else 21.dp),
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = if (selected) Color.White else Color(0xFF090B11),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            modifier = Modifier.graphicsLayer {
                scaleX = contentScale
                scaleY = contentScale
            },
        )
    }
}

private fun NetworkLiveTelemetry?.frequencyLabelOr(fallback: String): String {
    val frequency = this?.frequencyMhz ?: return fallback
    return String.format(Locale.US, "%.1f GHz", frequency / 1000f)
}

private fun NetworkLiveTelemetry?.signalQualityOr(fallback: String): String {
    val dbm = this?.signalDbm ?: return fallback
    return when {
        dbm >= -50 -> "Tuyệt vời"
        dbm >= -60 -> "Rất tốt"
        dbm >= -70 -> "Tốt"
        dbm >= -80 -> "Khá yếu"
        else -> "Yếu"
    }
}

private fun Long.toTimeLabel(): String {
    return SimpleDateFormat("HH:mm:ss", Locale.forLanguageTag("vi-VN")).format(Date(this))
}
