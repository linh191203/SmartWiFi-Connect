package com.example.smartwificonnect.feature.share

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.IosShare
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smartwificonnect.ui.theme.LocalAppDarkMode
import com.example.smartwificonnect.ui.theme.SmartWifiAppTheme
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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

data class ShareWifiUiModel(
    val ssid: String,
    val password: String,
    val security: String,
)

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

@Composable
fun ShareWifiScreen(
    network: ShareWifiUiModel?,
    onBackClick: () -> Unit,
    onHomeClick: () -> Unit,
    onScanClick: () -> Unit,
    onShareClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var copiedMessage by rememberSaveable { mutableStateOf("") }
    val sharePayload = remember(network) { network?.toWifiQrPayload().orEmpty() }
    val shareLink = remember(network) { network?.toSmartWifiLink().orEmpty() }
    val shareText = remember(network, sharePayload, shareLink) {
        network?.toShareText(sharePayload, shareLink).orEmpty()
    }

    fun shareTextWithSystem(text: String) {
        if (text.isBlank()) return
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Chia sẻ Wi-Fi"))
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { ShareTopBar(onBackClick = onBackClick) },
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
                    start = 20.dp,
                    top = innerPadding.calculateTopPadding() + 14.dp,
                    end = 20.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (network == null || network.ssid.isBlank()) {
                    item { ShareEmptyCard() }
                } else {
                    item {
                        ShareHero(network = network)
                    }
                    item {
                        WifiQrCard(
                            ssid = network.ssid,
                            payload = sharePayload,
                        )
                    }
                    item {
                        ShareActionGrid(
                            link = shareLink,
                            copiedMessage = copiedMessage,
                            onCopyLink = {
                                clipboard.setText(AnnotatedString(shareLink))
                                copiedMessage = "Đã sao chép link"
                            },
                            onShareLink = { shareTextWithSystem(shareLink) },
                            onShareDetails = { shareTextWithSystem(shareText) },
                            onCopyPassword = {
                                clipboard.setText(AnnotatedString(network.password))
                                copiedMessage = "Đã sao chép mật khẩu"
                            },
                            canCopyPassword = network.password.isNotBlank(),
                        )
                    }
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
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.align(Alignment.Center),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ShareHero(network: ShareWifiUiModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
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
                    .clip(RoundedCornerShape(18.dp))
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
                    modifier = Modifier.size(30.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = network.ssid,
                    color = ShareTextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (network.password.isBlank()) "Mạng mở, không cần mật khẩu" else "Sẵn sàng chia sẻ bằng QR hoặc link",
                    color = ShareTextMuted,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun WifiQrCard(
    ssid: String,
    payload: String,
) {
    val qrBitmap = remember(payload) { generateQrBitmap(payload, 720) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = ShareSurface,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, ShareStroke),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "QR Wi-Fi offline",
                color = ShareTextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
            )
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.White,
                border = BorderStroke(8.dp, Color.White),
                shadowElevation = 1.dp,
            ) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "Mã QR Wi-Fi của $ssid",
                    modifier = Modifier
                        .size(236.dp)
                        .padding(6.dp),
                )
            }
            Text(
                text = "Máy khác chỉ cần mở Camera và quét mã này. Không cần có Internet trước.",
                color = ShareTextMuted,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ShareActionGrid(
    link: String,
    copiedMessage: String,
    onCopyLink: () -> Unit,
    onShareLink: () -> Unit,
    onShareDetails: () -> Unit,
    onCopyPassword: () -> Unit,
    canCopyPassword: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShareSectionLabel(text = "CHIA SẺ BẰNG LINK")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = ShareSurface,
            border = BorderStroke(1.dp, ShareStroke),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = link,
                    color = ShareTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShareActionButton(
                        modifier = Modifier.weight(1f),
                        label = "Copy link",
                        icon = Icons.Outlined.ContentCopy,
                        primary = false,
                        onClick = onCopyLink,
                    )
                    ShareActionButton(
                        modifier = Modifier.weight(1f),
                        label = "Gửi link",
                        icon = Icons.Outlined.IosShare,
                        primary = true,
                        onClick = onShareLink,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShareActionButton(
                        modifier = Modifier.weight(1f),
                        label = "Gửi mã",
                        icon = Icons.Outlined.QrCode2,
                        primary = false,
                        onClick = onShareDetails,
                    )
                    ShareActionButton(
                        modifier = Modifier.weight(1f),
                        label = "Copy pass",
                        icon = Icons.Outlined.ContentCopy,
                        primary = false,
                        enabled = canCopyPassword,
                        onClick = onCopyPassword,
                    )
                }
                if (copiedMessage.isNotBlank()) {
                    Text(
                        text = copiedMessage,
                        color = ShareBrand,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareActionButton(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    primary: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .height(50.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.42f }
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (primary) ShareBrand else ShareSurfaceSoft,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (primary) Color.White else ShareBrand,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = label,
                color = if (primary) Color.White else ShareTextPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
private fun ShareEmptyCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = ShareSurface,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, ShareStroke),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(ShareBrandSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.WifiTethering,
                    contentDescription = null,
                    tint = ShareBrand,
                    modifier = Modifier.size(32.dp),
                )
            }
            Text(
                text = "Chưa có Wi-Fi để chia sẻ",
                color = ShareTextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Hãy kết nối hoặc lưu một mạng Wi-Fi trước. Khi có SSID và mật khẩu, app sẽ tạo QR offline cho bạn.",
                color = ShareTextMuted,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
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
            modifier = Modifier
                .offset(y = 0.dp)
                .graphicsLayer {
                    scaleX = contentScale
                    scaleY = contentScale
                },
        )
    }
}

private fun ShareWifiUiModel.toWifiQrPayload(): String {
    val type = when {
        password.isBlank() -> "nopass"
        security.contains("WEP", ignoreCase = true) -> "WEP"
        else -> "WPA"
    }
    return "WIFI:T:${type};S:${ssid.escapeWifiQrValue()};P:${password.escapeWifiQrValue()};H:false;;"
}

private fun ShareWifiUiModel.toSmartWifiLink(): String {
    // NOTE: password is intentionally omitted from the deep-link URL.
    // Embedding passwords in URLs exposes them in browser history, share sheets,
    // and server logs. The QR code (WIFI: format) is the secure channel for
    // credential sharing; the link is only used for SSID identification.
    return buildString {
        append("smartwifi://join")
        append("?ssid=${ssid.urlEncode()}")
        append("&security=${security.urlEncode()}")
    }
}

private fun ShareWifiUiModel.toShareText(
    qrPayload: String,
    link: String,
): String {
    return buildString {
        appendLine("SmartWiFi Connect")
        appendLine("Wi-Fi: $ssid")
        appendLine("Bảo mật: ${if (password.isBlank()) "Mở" else security}")
        if (password.isNotBlank()) {
            appendLine("Mật khẩu: $password")
        }
        appendLine("Link: $link")
        append("QR payload: $qrPayload")
    }
}

private fun String.escapeWifiQrValue(): String {
    return replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace(":", "\\:")
        .replace("\"", "\\\"")
}

private fun String.urlEncode(): String {
    return URLEncoder.encode(this, StandardCharsets.UTF_8.name())
}

private fun generateQrBitmap(
    content: String,
    size: Int,
): Bitmap {
    val hints = mapOf(
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 1,
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] = if (matrix[x, y]) {
                android.graphics.Color.BLACK
            } else {
                android.graphics.Color.WHITE
            }
        }
    }
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, size, 0, 0, size, size)
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 942)
@Composable
private fun ShareWifiScreenPreview() {
    SmartWifiAppTheme {
        ShareWifiScreen(
            network = ShareWifiUiModel(
                ssid = "Office_Main_Corp",
                password = "office-password",
                security = "WPA/WPA2",
            ),
            onBackClick = {},
            onHomeClick = {},
            onScanClick = {},
            onShareClick = {},
            onHistoryClick = {},
            onSettingsClick = {},
        )
    }
}
