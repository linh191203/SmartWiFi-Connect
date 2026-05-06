package com.example.smartwificonnect.feature.scanimage

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smartwificonnect.feature.camera.CameraPreview
import com.example.smartwificonnect.ui.theme.LocalAppDarkMode
import com.example.smartwificonnect.ui.theme.SmartWifiAppTheme
import kotlin.math.roundToInt

private val ImageScanBackground: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF10131B) else Color(0xFFF7F9FC)
private val ImageScanBar: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xF21A1F2B) else Color(0xFFF7F8FB)
private val ImageScanBarStroke: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF293142) else Color(0xFFE6E9EF)
private val ImageScanBrand: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF8D90FF) else Color(0xFF5A63F5)
private val ImageScanText: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFFE4E8F0) else Color(0xFF1B1E25)

enum class ImageScanBottomTab(
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
fun ImageScanScreen(
    onCloseClick: () -> Unit,
    onFlashClick: () -> Unit,
    onCaptureClick: (Bitmap) -> Unit,
    onCaptureUnavailable: () -> Unit,
    onSwitchToQrClick: () -> Unit,
    onOpenGalleryClick: () -> Unit,
    onHomeClick: () -> Unit,
    onScanClick: () -> Unit,
    onShareClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    activeTab: ImageScanBottomTab = ImageScanBottomTab.SCAN,
) {
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var previewBounds by remember { mutableStateOf<Rect?>(null) }
    var frameBounds by remember { mutableStateOf<Rect?>(null) }
    val tabs = listOf(
        ImageScanBottomTab.HOME,
        ImageScanBottomTab.SCAN,
        ImageScanBottomTab.SHARE,
        ImageScanBottomTab.HISTORY,
        ImageScanBottomTab.SETTINGS,
    )

    fun onTabClick(tab: ImageScanBottomTab) {
        when (tab) {
            ImageScanBottomTab.HOME -> onHomeClick()
            ImageScanBottomTab.SCAN -> onScanClick()
            ImageScanBottomTab.SHARE -> onShareClick()
            ImageScanBottomTab.HISTORY -> onHistoryClick()
            ImageScanBottomTab.SETTINGS -> onSettingsClick()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ImageScanBackground),
    ) {
        ImageScanTopBar(
            onCloseClick = onCloseClick,
            onFlashClick = onFlashClick,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF565656)),
        ) {
            CameraPreview(
                modifier = Modifier
                    .matchParentSize()
                    .onGloballyPositioned { coordinates ->
                        previewBounds = coordinates.toRootRect()
                    },
                onPreviewReady = { previewView = it },
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0x33000000)),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(92.dp))

                ImageScanFrame(
                    modifier = Modifier
                        .size(274.dp)
                        .onGloballyPositioned { coordinates ->
                            frameBounds = coordinates.toRootRect()
                        },
                )

                Spacer(modifier = Modifier.height(40.dp))

                ImageScanHintPill()

                Spacer(modifier = Modifier.height(62.dp))

                ImageScanActionBar(
                    onOpenGalleryClick = onOpenGalleryClick,
                    onCaptureClick = {
                        val croppedBitmap = previewView
                            ?.bitmap
                            ?.cropToFrame(previewBounds, frameBounds)
                        if (croppedBitmap == null) {
                            onCaptureUnavailable()
                        } else {
                            onCaptureClick(croppedBitmap)
                        }
                    },
                    onSwitchToQrClick = onSwitchToQrClick,
                )

                Spacer(modifier = Modifier.weight(1f))
            }
        }

        ImageScanBottomBar(
            tabs = tabs,
            activeTab = activeTab,
            onTabClick = { onTabClick(it) },
        )
    }
}

private fun androidx.compose.ui.layout.LayoutCoordinates.toRootRect(): Rect {
    val position = positionInRoot()
    return Rect(
        position.x.roundToInt(),
        position.y.roundToInt(),
        (position.x + size.width).roundToInt(),
        (position.y + size.height).roundToInt(),
    )
}

private fun Bitmap.cropToFrame(previewBounds: Rect?, frameBounds: Rect?): Bitmap? {
    if (previewBounds == null || frameBounds == null) return this
    if (previewBounds.width() <= 0 || previewBounds.height() <= 0) return this

    val leftInPreview = frameBounds.left - previewBounds.left
    val topInPreview = frameBounds.top - previewBounds.top
    val rightInPreview = frameBounds.right - previewBounds.left
    val bottomInPreview = frameBounds.bottom - previewBounds.top

    val scaleX = width.toFloat() / previewBounds.width().toFloat()
    val scaleY = height.toFloat() / previewBounds.height().toFloat()
    val left = (leftInPreview * scaleX).roundToInt().coerceIn(0, width - 1)
    val top = (topInPreview * scaleY).roundToInt().coerceIn(0, height - 1)
    val right = (rightInPreview * scaleX).roundToInt().coerceIn(left + 1, width)
    val bottom = (bottomInPreview * scaleY).roundToInt().coerceIn(top + 1, height)
    val cropWidth = right - left
    val cropHeight = bottom - top

    if (cropWidth < 32 || cropHeight < 32) return null
    return Bitmap.createBitmap(this, left, top, cropWidth, cropHeight)
}

@Composable
private fun ImageScanTopBar(
    onCloseClick: () -> Unit,
    onFlashClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ImageScanBackground,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Đóng",
                    tint = ImageScanBrand,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable(onClick = onCloseClick),
                )
                Text(
                    text = "Máy quét",
                    color = ImageScanBrand,
                    style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = "Đèn pin",
                    tint = Color(0xFF5F6874),
                    modifier = Modifier
                        .size(18.dp)
                        .clickable(onClick = onFlashClick),
                )
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = CircleShape,
                    color = Color(0xFFF5E3D4),
                    border = BorderStroke(2.dp, ImageScanBrand),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "D",
                            color = Color(0xFF8A5F45),
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageScanFrame(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(34.dp))
                .background(Color(0xFFF6F7FA).copy(alpha = 0.70f)),
        )
        AnimatedScanLine(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(34.dp)),
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val corner = size.width * 0.18f
            val stroke = 6f
            val blue = Color(0xFF5D66FF)
            val white = Color(0xFFDCE1E9)

            drawLine(blue, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(corner, 0f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(blue, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, corner), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(blue, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(size.width - corner, 0f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(blue, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(size.width, corner), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(blue, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(corner, size.height), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(blue, androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(0f, size.height - corner), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(blue, androidx.compose.ui.geometry.Offset(size.width, size.height), androidx.compose.ui.geometry.Offset(size.width - corner, size.height), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(blue, androidx.compose.ui.geometry.Offset(size.width, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height - corner), strokeWidth = stroke, cap = StrokeCap.Round)

            val whiteLen = corner * 0.36f
            drawLine(white, androidx.compose.ui.geometry.Offset(corner - whiteLen, 0f), androidx.compose.ui.geometry.Offset(corner + whiteLen, 0f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(white, androidx.compose.ui.geometry.Offset(size.width - corner - whiteLen, 0f), androidx.compose.ui.geometry.Offset(size.width - corner + whiteLen, 0f), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(white, androidx.compose.ui.geometry.Offset(corner - whiteLen, size.height), androidx.compose.ui.geometry.Offset(corner + whiteLen, size.height), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(white, androidx.compose.ui.geometry.Offset(size.width - corner - whiteLen, size.height), androidx.compose.ui.geometry.Offset(size.width - corner + whiteLen, size.height), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun AnimatedScanLine(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "imageScanLineTransition")
    val progress by transition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "imageScanLineProgress",
    )

    Canvas(modifier = modifier) {
        val y = size.height * progress
        val horizontalPadding = size.width * 0.11f
        val glowHeight = 56f
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0x3DFFFFFF),
                    Color.Transparent,
                ),
                startY = y - glowHeight / 2f,
                endY = y + glowHeight / 2f,
            ),
            topLeft = androidx.compose.ui.geometry.Offset(horizontalPadding, y - glowHeight / 2f),
            size = androidx.compose.ui.geometry.Size(size.width - horizontalPadding * 2f, glowHeight),
        )
        drawLine(
            color = Color(0x7AFFFFFF),
            start = androidx.compose.ui.geometry.Offset(horizontalPadding, y),
            end = androidx.compose.ui.geometry.Offset(size.width - horizontalPadding, y),
            strokeWidth = 12f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(horizontalPadding + 8f, y),
            end = androidx.compose.ui.geometry.Offset(size.width - horizontalPadding - 8f, y),
            strokeWidth = 4f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun ImageScanHintPill() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFF2A2C2F),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = Color(0xFFB5BBC6),
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Căn chỉnh mật khẩu Wi-Fi vào khung",
                color = Color(0xFFCED3DC),
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ImageScanActionBar(
    onOpenGalleryClick: () -> Unit,
    onCaptureClick: () -> Unit,
    onSwitchToQrClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFF2E3034),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SmallCircleActionButton(
                icon = Icons.Outlined.Image,
                onClick = onOpenGalleryClick,
            )
            CaptureActionButton(onClick = onCaptureClick)
            SmallCircleActionButton(
                icon = Icons.Outlined.QrCode2,
                onClick = onSwitchToQrClick,
            )
        }
    }
}

@Composable
private fun SmallCircleActionButton(
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = Color(0xFF373A40),
        border = BorderStroke(1.dp, Color(0xFF5A5E67)),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFC0C6D2),
                modifier = Modifier.size(23.dp),
            )
        }
    }
}

@Composable
private fun CaptureActionButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(66.dp),
        shape = CircleShape,
        color = Color(0xFFEDEFF5),
        border = BorderStroke(3.dp, Color(0xFF8E939E)),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = ImageScanBrand,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.CameraAlt,
                        contentDescription = "Chụp",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageScanBottomBar(
    tabs: List<ImageScanBottomTab>,
    activeTab: ImageScanBottomTab,
    onTabClick: (ImageScanBottomTab) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
        color = ImageScanBar,
        border = BorderStroke(1.dp, ImageScanBarStroke),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                ImageScanBottomItem(
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
private fun ImageScanBottomItem(
    modifier: Modifier,
    item: ImageScanBottomTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) ImageScanBrand else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = if (selected) Color.White else ImageScanText,
            modifier = Modifier.size(21.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.label,
            color = if (selected) Color.White else ImageScanText,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 886)
@Composable
private fun ImageScanScreenPreview() {
    SmartWifiAppTheme {
        ImageScanScreen(
            onCloseClick = {},
            onFlashClick = {},
            onCaptureClick = { _ -> },
            onCaptureUnavailable = {},
            onSwitchToQrClick = {},
            onOpenGalleryClick = {},
            onHomeClick = {},
            onScanClick = {},
            onShareClick = {},
            onHistoryClick = {},
            onSettingsClick = {},
        )
    }
}
