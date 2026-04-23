package com.example.smartwificonnect.feature.scanqr

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
import androidx.compose.ui.res.stringResource
import com.example.smartwificonnect.ui.theme.SmartWifiAppTheme
import com.example.smartwificonnect.R

enum class ScannerBottomTab(
    val labelRes: Int,
    val icon: ImageVector,
) {
    HOME(R.string.tab_home, Icons.Outlined.Home),
    SCAN(R.string.tab_scan, Icons.Outlined.QrCode2),
    SHARE(R.string.tab_share, Icons.Outlined.IosShare),
    HISTORY(R.string.tab_history, Icons.Outlined.History),
    SETTINGS(R.string.tab_settings, Icons.Outlined.Settings),
}

@Composable
fun QrScannerScreen(
    onCloseClick: () -> Unit,
    onHelpClick: () -> Unit,
    onFlashClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onHomeClick: () -> Unit,
    onScanClick: () -> Unit,
    onShareClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    activeTab: ScannerBottomTab = ScannerBottomTab.SCAN,
) {
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
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF888C92),
                        Color(0xFF6A6E75),
                        Color(0xFF3D4249),
                    ),
                ),
            ),
    ) {
        FakeCameraBackdrop()

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
            contentDescription = stringResource(R.string.cd_close),
            onClick = onCloseClick,
        )
        Text(
            text = stringResource(R.string.splash_title),
            color = Color(0xFFF3F5F8),
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
        )
        CircleIconButton(
            icon = Icons.AutoMirrored.Outlined.HelpOutline,
            contentDescription = stringResource(R.string.cd_help),
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
            text = stringResource(R.string.qr_scanner_guide_title),
            color = Color(0xFFD1D5DD),
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.qr_scanner_guide_body),
            color = Color(0xB6C6CBD5),
            textAlign = TextAlign.Center,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            ScannerActionButton(
                label = stringResource(R.string.qr_scanner_action_flashlight),
                icon = Icons.Rounded.FlashlightOn,
                onClick = onFlashClick,
            )
            ScannerActionButton(
                label = stringResource(R.string.qr_scanner_action_library),
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
                    text = stringResource(R.string.qr_scanner_status_ready),
                    color = Color(0xFF8C93A1),
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = stringResource(R.string.qr_scanner_status_searching),
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
        color = Color(0xFFF8F9FB),
        border = BorderStroke(1.dp, Color(0xFFE8EBF1)),
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
            .background(if (selected) Color(0xFF5A63F5) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = stringResource(item.labelRes),
            tint = if (selected) Color.White else Color(0xFF191C23),
            modifier = Modifier.size(21.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(item.labelRes),
            color = if (selected) Color.White else Color(0xFF191C23),
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
            onHomeClick = {},
            onScanClick = {},
            onShareClick = {},
            onHistoryClick = {},
            onSettingsClick = {},
        )
    }
}
