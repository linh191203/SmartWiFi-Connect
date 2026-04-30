package com.example.smartwificonnect.feature.scanqr

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import com.example.smartwificonnect.feature.camera.CameraPreview
import com.example.smartwificonnect.ui.theme.LocalAppDarkMode
import com.example.smartwificonnect.ui.theme.SmartWifiAppTheme
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

private val ScannerBottomSurface: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xF21A1F2B) else Color(0xFFF8F9FB)
private val ScannerBottomStroke: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF293142) else Color(0xFFE8EBF1)
private val ScannerBottomBrand: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF8D90FF) else Color(0xFF5A63F5)
private val ScannerBottomText: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFFE4E8F0) else Color(0xFF191C23)

enum class ScannerBottomTab(
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
fun QrScannerScreen(
    onCloseClick: () -> Unit,
    onHelpClick: () -> Unit,
    onFlashClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onQrCodeDetected: (String) -> Unit,
    onHomeClick: () -> Unit,
    onScanClick: () -> Unit,
    onShareClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    activeTab: ScannerBottomTab = ScannerBottomTab.SCAN,
) {
    val hasDetectedQr = remember { AtomicBoolean(false) }
    val qrAnalyzer = remember {
        WifiQrAnalyzer(
            hasDetectedQr = hasDetectedQr,
            onQrCodeDetected = onQrCodeDetected,
        )
    }
    val tabs = listOf(
        ScannerBottomTab.HOME,
        ScannerBottomTab.SCAN,
        ScannerBottomTab.SHARE,
        ScannerBottomTab.HISTORY,
        ScannerBottomTab.SETTINGS,
    )

    fun onTabClick(tab: ScannerBottomTab) {
        when (tab) {
            ScannerBottomTab.HOME -> onHomeClick()
            ScannerBottomTab.SCAN -> onScanClick()
            ScannerBottomTab.SHARE -> onShareClick()
            ScannerBottomTab.HISTORY -> onHistoryClick()
            ScannerBottomTab.SETTINGS -> onSettingsClick()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF22262D)),
    ) {
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            analyzer = qrAnalyzer,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.50f to Color(0x35000000),
                        0.70f to Color(0xD40A0C10),
                        1.0f to Color(0xFF06080B),
                    ),
                ),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            ScannerTopBar(
                onCloseClick = onCloseClick,
                onHelpClick = onHelpClick,
            )

            Spacer(modifier = Modifier.height(34.dp))

            ScanTargetFrame(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(250.dp),
            )

            Spacer(modifier = Modifier.weight(1f))

            ScannerGuideSection(
                onFlashClick = onFlashClick,
                onGalleryClick = onGalleryClick,
            )

            Spacer(modifier = Modifier.height(16.dp))

            ScanStatusCard(
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(14.dp))

            ScannerBottomBar(
                tabs = tabs,
                activeTab = activeTab,
                onTabClick = { onTabClick(it) },
            )
        }
    }
}

@Composable
private fun FakeCameraBackdrop() {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .width(22.dp)
                .fillMaxSize()
                .background(Color(0x20000000)),
        )
        Box(
            modifier = Modifier
                .width(8.dp)
                .fillMaxSize()
                .background(Color(0x4D000000)),
        )
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .width(8.dp)
                .fillMaxSize()
                .background(Color(0x4D000000)),
        )
        Box(
            modifier = Modifier
                .width(22.dp)
                .fillMaxSize()
                .background(Color(0x25000000)),
        )
    }
}

@Composable
private fun ScannerTopBar(
    onCloseClick: () -> Unit,
    onHelpClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(
            icon = Icons.Outlined.Close,
            contentDescription = "Đóng",
            onClick = onCloseClick,
        )
        Text(
            text = "SmartWiFi-Connect",
            color = Color(0xFFF3F5F8),
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
        )
        CircleIconButton(
            icon = Icons.AutoMirrored.Outlined.HelpOutline,
            contentDescription = "Trợ giúp",
            onClick = onHelpClick,
        )
    }
}

@Composable
private fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.size(42.dp),
        shape = CircleShape,
        color = Color(0xB330333A),
        border = BorderStroke(1.dp, Color(0x55C9CFD9)),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color(0xFFF3F5F8),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun ScanTargetFrame(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0x08FFFFFF), Color(0x03FFFFFF)),
                    ),
                ),
        )

        AnimatedScanLine(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(26.dp)),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .align(Alignment.TopCenter)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color(0xB9FFFFFF), Color.Transparent),
                    ),
                ),
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 7f
            val corner = size.width * 0.23f
            val c = Color(0xFF5A6AFF)

            drawLine(c, start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(corner, 0f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(c, start = androidx.compose.ui.geometry.Offset(0f, 0f), end = androidx.compose.ui.geometry.Offset(0f, corner), strokeWidth = stroke, cap = StrokeCap.Round)

            drawLine(c, start = androidx.compose.ui.geometry.Offset(size.width, 0f), end = androidx.compose.ui.geometry.Offset(size.width - corner, 0f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(c, start = androidx.compose.ui.geometry.Offset(size.width, 0f), end = androidx.compose.ui.geometry.Offset(size.width, corner), strokeWidth = stroke, cap = StrokeCap.Round)

            drawLine(c, start = androidx.compose.ui.geometry.Offset(0f, size.height), end = androidx.compose.ui.geometry.Offset(corner, size.height), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(c, start = androidx.compose.ui.geometry.Offset(0f, size.height), end = androidx.compose.ui.geometry.Offset(0f, size.height - corner), strokeWidth = stroke, cap = StrokeCap.Round)

            drawLine(c, start = androidx.compose.ui.geometry.Offset(size.width, size.height), end = androidx.compose.ui.geometry.Offset(size.width - corner, size.height), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(c, start = androidx.compose.ui.geometry.Offset(size.width, size.height), end = androidx.compose.ui.geometry.Offset(size.width, size.height - corner), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun AnimatedScanLine(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "qrScanLineTransition")
    val progress by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "qrScanLineProgress",
    )

    Canvas(modifier = modifier) {
        val y = size.height * progress
        val horizontalPadding = size.width * 0.10f
        val glowHeight = 52f
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0x33FFFFFF),
                    Color.Transparent,
                ),
                startY = y - glowHeight / 2f,
                endY = y + glowHeight / 2f,
            ),
            topLeft = androidx.compose.ui.geometry.Offset(horizontalPadding, y - glowHeight / 2f),
            size = androidx.compose.ui.geometry.Size(size.width - horizontalPadding * 2f, glowHeight),
        )
        drawLine(
            color = Color(0x70FFFFFF),
            start = androidx.compose.ui.geometry.Offset(horizontalPadding, y),
            end = androidx.compose.ui.geometry.Offset(size.width - horizontalPadding, y),
            strokeWidth = 11f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(horizontalPadding + 8f, y),
            end = androidx.compose.ui.geometry.Offset(size.width - horizontalPadding - 8f, y),
            strokeWidth = 3.5f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun ScannerGuideSection(
    onFlashClick: () -> Unit,
    onGalleryClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Căn chỉnh mã QR",
            color = Color(0xFFD1D5DD),
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Đặt mã QR Wi-Fi vào trong khung để\nkết nối tự động.",
            color = Color(0xB6C6CBD5),
            textAlign = TextAlign.Center,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            ScannerActionButton(
                label = "ĐÈN PIN",
                icon = Icons.Rounded.FlashlightOn,
                onClick = onFlashClick,
            )
            ScannerActionButton(
                label = "THƯ VIỆN",
                icon = Icons.Outlined.Image,
                onClick = onGalleryClick,
            )
        }
    }
}

@Composable
private fun ScannerActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(58.dp),
            shape = CircleShape,
            color = Color(0x25363B44),
            border = BorderStroke(1.dp, Color(0x4AC6CCD7)),
            onClick = onClick,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color(0xFFD7DCE5),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = Color(0xCCD8DDE6),
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun ScanStatusCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF9FAFC),
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF1F2FA)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Wifi,
                    contentDescription = null,
                    tint = Color(0xFF5961F4),
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SẴN SÀNG QUÉT",
                    color = Color(0xFF8C93A1),
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "Đang tìm kiếm mạng...",
                    color = Color(0xFF202430),
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2A8B70)),
            )
        }
    }
}

@Composable
private fun ScannerBottomBar(
    tabs: List<ScannerBottomTab>,
    activeTab: ScannerBottomTab,
    onTabClick: (ScannerBottomTab) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = ScannerBottomSurface,
        border = BorderStroke(1.dp, ScannerBottomStroke),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                ScannerBottomItem(
                    modifier = Modifier.weight(1f),
                    item = tab,
                    selected = tab == activeTab,
                    onClick = { onTabClick(tab) },
                )
            }
        }
    }
}

@Composable
private fun ScannerBottomItem(
    modifier: Modifier,
    item: ScannerBottomTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) ScannerBottomBrand else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = if (selected) Color.White else ScannerBottomText,
            modifier = Modifier.size(21.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.label,
            color = if (selected) Color.White else ScannerBottomText,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private class WifiQrAnalyzer(
    private val hasDetectedQr: AtomicBoolean,
    private val onQrCodeDetected: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build(),
    )

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: androidx.camera.core.ImageProxy) {
        if (hasDetectedQr.get()) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees,
        )

        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val rawValue = barcodes.firstNotNullOfOrNull { it.rawValue?.trim() }
                if (!rawValue.isNullOrBlank() && hasDetectedQr.compareAndSet(false, true)) {
                    onQrCodeDetected(rawValue)
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun QrScannerScreenPreview() {
    SmartWifiAppTheme {
        QrScannerScreen(
            onCloseClick = {},
            onHelpClick = {},
            onFlashClick = {},
            onGalleryClick = {},
            onQrCodeDetected = {},
            onHomeClick = {},
            onScanClick = {},
            onShareClick = {},
            onHistoryClick = {},
            onSettingsClick = {},
        )
    }
}
