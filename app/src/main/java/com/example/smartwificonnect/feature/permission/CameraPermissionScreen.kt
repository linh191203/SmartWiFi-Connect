package com.example.smartwificonnect.feature.permission

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.smartwificonnect.ui.theme.LocalAppDarkMode
import com.example.smartwificonnect.ui.theme.SmartWifiAppTheme

@Composable
fun CameraPermissionScreen(
    onAllowClick: () -> Unit,
    onDenyClick: () -> Unit,
) {
    val context = LocalContext.current
    val dark = LocalAppDarkMode.current
    val backgroundTop = if (dark) Color(0xFF10131B) else Color(0xFFF7F9FC)
    val backgroundBottom = if (dark) Color(0xFF171A24) else Color(0xFFEBF2FC)
    val titleColor = if (dark) Color(0xFFF4F6FB) else Color(0xFF20242E)
    val bodyColor = if (dark) Color(0xFFABB2C1) else Color(0xFF5E6371)
    val brandColor = if (dark) Color(0xFF6D70F6) else Color(0xFF474ADB)
    val secondaryButton = if (dark) Color(0xFF2B3240) else Color(0xFFD9DEE5)
    val secondaryText = if (dark) Color(0xFFE4E8F0) else Color(0xFF4D5361)
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            onAllowClick()
        } else {
            onDenyClick()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(backgroundTop, backgroundBottom),
                ),
            ),
    ) {
        DecorativeDotGrid(modifier = Modifier.padding(start = 20.dp, top = 44.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            PermissionHero()

            Spacer(modifier = Modifier.size(26.dp))

            Text(
                text = "Quyền truy cập camera",
                color = titleColor,
                style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = "SmartWiFi-Connect cần quyền truy\ncập camera để quét mã QR.",
                color = bodyColor,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.size(22.dp))

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFF8DECD7),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Security,
                        contentDescription = null,
                        tint = Color(0xFF166857),
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "Riêng tư & Bảo mật tuyệt đối",
                        color = Color(0xFF166857),
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }

            Spacer(modifier = Modifier.size(54.dp))

            Surface(
                onClick = {
                    val cameraGranted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (cameraGranted) {
                        onAllowClick()
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp),
                color = brandColor,
                shadowElevation = 8.dp,
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Cho phép truy cập",
                        color = Color.White,
                        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }

            Spacer(modifier = Modifier.size(14.dp))

            Surface(
                onClick = onDenyClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(999.dp),
                color = secondaryButton,
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Không cho phép",
                        color = secondaryText,
                        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }

            Spacer(modifier = Modifier.size(34.dp))

            Text(
                text = "CÀI ĐẶT > QUYỀN TRUY CẬP > CAMERA",
                color = if (dark) Color(0xFF717B8D) else Color(0xFFB2BAC7),
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun PermissionHero() {
    Box(
        modifier = Modifier.size(372.dp),
        contentAlignment = Alignment.Center,
    ) {
        val ringBlue = Color(0xFF6470FF)
        Surface(
            modifier = Modifier.size(372.dp),
            shape = CircleShape,
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(2.dp, ringBlue.copy(alpha = 0.06f)),
        ) {}
        Surface(
            modifier = Modifier.size(344.dp),
            shape = CircleShape,
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(2.dp, ringBlue.copy(alpha = 0.09f)),
        ) {}
        Surface(
            modifier = Modifier.size(320.dp),
            shape = CircleShape,
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(2.dp, ringBlue.copy(alpha = 0.10f)),
        ) {}
        Surface(
            modifier = Modifier.size(286.dp),
            shape = CircleShape,
            color = Color.Transparent,
            border = androidx.compose.foundation.BorderStroke(2.dp, ringBlue.copy(alpha = 0.16f)),
        ) {}
        Surface(
            modifier = Modifier.size(246.dp),
            shape = CircleShape,
            color = Color(0xFFF8FAFC),
            shadowElevation = 0.dp,
        ) {}

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.size(94.dp),
                    shape = CircleShape,
                    color = Color(0xFFDCDDF0),
                ) {}
                Surface(
                    modifier = Modifier.size(62.dp),
                    shape = CircleShape,
                    color = Color(0xFFC6C8EA),
                ) {}
                PermissionCameraLogo(
                    modifier = Modifier.size(width = 50.dp, height = 45.dp),
                )
            }
            Spacer(modifier = Modifier.size(18.dp))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color(0xFFDDE1EA),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF5A63F5), CircleShape),
                    )
                    Text(
                        text = "ĐANG CHỜ LỆNH",
                        color = Color(0xFF4D5461),
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionCameraLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.12f
        val blue = Color(0xFF3E46D8)
        val w = size.width
        val h = size.height

        val bodyLeft = w * 0.08f
        val bodyTop = h * 0.24f
        val bodyWidth = w * 0.84f
        val bodyHeight = h * 0.66f
        val bodyRadius = h * 0.10f

        drawRoundRect(
            color = blue,
            topLeft = androidx.compose.ui.geometry.Offset(bodyLeft, bodyTop),
            size = androidx.compose.ui.geometry.Size(bodyWidth, bodyHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(bodyRadius, bodyRadius),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        val topCapPath = Path().apply {
            moveTo(w * 0.30f, bodyTop)
            lineTo(w * 0.40f, h * 0.08f)
            lineTo(w * 0.60f, h * 0.08f)
            lineTo(w * 0.70f, bodyTop)
        }
        drawPath(
            path = topCapPath,
            color = blue,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )

        drawCircle(
            color = blue,
            radius = h * 0.155f,
            center = androidx.compose.ui.geometry.Offset(w * 0.50f, bodyTop + bodyHeight * 0.53f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

@Composable
private fun DecorativeDotGrid(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val dotColor = Color(0xFFCAD1FB)
        val gap = 8f
        val radius = 1.6f
        for (x in 0..2) {
            for (y in 0..2) {
                drawCircle(
                    color = dotColor,
                    radius = radius,
                    center = androidx.compose.ui.geometry.Offset(x * gap + 3f, y * gap + 3f),
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 884)
@Composable
private fun CameraPermissionScreenPreview() {
    SmartWifiAppTheme {
        CameraPermissionScreen(
            onAllowClick = {},
            onDenyClick = {},
        )
    }
}
