package com.smartwificonnect.feature.scanimage

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smartwificonnect.feature.camera.CameraPreview
import com.smartwificonnect.ui.theme.LocalAppDarkMode
import com.smartwificonnect.ui.theme.SmartWifiAppTheme
import kotlin.math.max
import kotlin.math.min
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
    isOcrLoading: Boolean = false,
    ocrLoadingMessage: String = "",
) {
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var previewBounds by remember { mutableStateOf<Rect?>(null) }
    var scanFrameBounds by remember { mutableStateOf<Rect?>(null) }
    val tabs = listOf(
        ImageScanBottomTab.HOME,
        ImageScanBottomTab.SCAN,
        ImageScanBottomTab.SHARE,
        ImageScanBottomTab.HISTORY,
        ImageScanBottomTab.SETTINGS,
    )

    fun onTabClick(tab: ImageScanBottomTab) {
        if (isOcrLoading) return
        when (tab) {
            ImageScanBottomTab.HOME -> onHomeClick()
            ImageScanBottomTab.SCAN -> onScanClick()
            ImageScanBottomTab.SHARE -> onShareClick()
            ImageScanBottomTab.HISTORY -> onHistoryClick()
            ImageScanBottomTab.SETTINGS -> onSettingsClick()
        }
    }

    BackHandler(enabled = isOcrLoading) {
        onCloseClick()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ImageScanBackground),
    ) {
        ImageScanTopBar(
            onCloseClick = onCloseClick,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF565656))
                .onGloballyPositioned { previewBounds = it.boundsInRoot() },
        ) {
            CameraPreview(
                modifier = Modifier.matchParentSize(),
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
                Spacer(modifier = Modifier.weight(0.8f))

                ImageScanFrame(
                    modifier = Modifier
                        .size(240.dp)
                        .onGloballyPositioned { scanFrameBounds = it.boundsInRoot() },
                )

                Spacer(modifier = Modifier.weight(0.4f))

                ImageScanHintPill()

                Spacer(modifier = Modifier.weight(0.5f))

                ImageScanActionBar(
                    enabled = !isOcrLoading,
                    onOpenGalleryClick = onOpenGalleryClick,
                    onCaptureClick = {
                        if (isOcrLoading) return@ImageScanActionBar
                        val bitmap = previewView?.bitmap
                        if (bitmap == null) {
                            onCaptureUnavailable()
                        } else {
                            onCaptureClick(
                                bitmap.cropToScanFrameOrSelf(
                                    previewBounds = previewBounds,
                                    scanFrameBounds = scanFrameBounds,
                                ),
                            )
                        }
                    },
                    onSwitchToQrClick = onSwitchToQrClick,
                )

                Spacer(modifier = Modifier.weight(0.6f))
            }

            if (isOcrLoading) {
                ImageOcrLoadingOverlay(message = ocrLoadingMessage)
            }
        }

        ImageScanBottomBar(
            tabs = tabs,
            activeTab = activeTab,
            onTabClick = { onTabClick(it) },
        )
    }
}

@Composable
private fun ImageScanTopBar(
    onCloseClick: () -> Unit,
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
                Spacer(modifier = Modifier.size(38.dp))
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
private fun ImageOcrLoadingOverlay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xB3000000)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color(0xFF20242C),
            border = BorderStroke(1.dp, Color(0xFF424856)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(color = Color.White)
                Text(
                    text = "Đang quét ảnh...",
                    color = Color.White,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Text(
                    text = message.ifBlank { "AI đang nhận dạng thông tin Wi-Fi" },
                    color = Color(0xFFD6DCE8),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Text(
                    text = "Vui lòng chờ trong giây lát",
                    color = Color(0xFFAEB6C5),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
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
                text = "Đặt tên Wi-Fi và mật khẩu vào trong khung",
                color = Color(0xFFCED3DC),
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun Bitmap.cropToScanFrameOrSelf(
    previewBounds: Rect?,
    scanFrameBounds: Rect?,
): Bitmap {
    val previewRect = previewBounds ?: return this
    val frameRect = scanFrameBounds ?: return this
    if (previewRect.width <= 0f || previewRect.height <= 0f) return this

    val visibleLeft = max(previewRect.left, frameRect.left)
    val visibleTop = max(previewRect.top, frameRect.top)
    val visibleRight = min(previewRect.right, frameRect.right)
    val visibleBottom = min(previewRect.bottom, frameRect.bottom)
    if (visibleRight <= visibleLeft || visibleBottom <= visibleTop) return this

    val paddingX = min((visibleRight - visibleLeft) * 0.08f, previewRect.width * 0.04f)
    val paddingY = min((visibleBottom - visibleTop) * 0.10f, previewRect.height * 0.05f)

    val cropLeft = ((visibleLeft - paddingX - previewRect.left) / previewRect.width * width)
        .roundToInt()
        .coerceIn(0, width - 1)
    val cropTop = ((visibleTop - paddingY - previewRect.top) / previewRect.height * height)
        .roundToInt()
        .coerceIn(0, height - 1)
    val cropRight = ((visibleRight + paddingX - previewRect.left) / previewRect.width * width)
        .roundToInt()
        .coerceIn(cropLeft + 1, width)
    val cropBottom = ((visibleBottom + paddingY - previewRect.top) / previewRect.height * height)
        .roundToInt()
        .coerceIn(cropTop + 1, height)

    val cropWidth = cropRight - cropLeft
    val cropHeight = cropBottom - cropTop
    if (cropWidth >= width && cropHeight >= height) return this
    if (cropWidth <= 0 || cropHeight <= 0) return this

    return Bitmap.createBitmap(this, cropLeft, cropTop, cropWidth, cropHeight)
}

@Composable
private fun ImageScanActionBar(
    enabled: Boolean,
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
                enabled = enabled,
            )
            CaptureActionButton(onClick = onCaptureClick, enabled = enabled)
            SmallCircleActionButton(
                icon = Icons.Outlined.QrCode2,
                onClick = onSwitchToQrClick,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun SmallCircleActionButton(
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Surface(
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = Color(0xFF373A40),
        border = BorderStroke(1.dp, Color(0xFF5A5E67)),
        onClick = onClick,
        enabled = enabled,
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
private fun CaptureActionButton(onClick: () -> Unit, enabled: Boolean = true) {
    Surface(
        modifier = Modifier.size(66.dp),
        shape = CircleShape,
        color = Color(0xFFEDEFF5),
        border = BorderStroke(3.dp, Color(0xFF8E939E)),
        onClick = onClick,
        enabled = enabled,
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
                .navigationBarsPadding()
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
