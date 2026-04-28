package com.example.smartwificonnect.feature.home

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartwificonnect.ui.theme.LocalAppDarkMode
import com.example.smartwificonnect.ui.theme.SmartWifiAppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

private const val ONBOARDING_ILLUSTRATION_URL =
    "https://lh3.googleusercontent.com/aida-public/AB6AXuBn6i_bVlMEnVLNbc5kIoFpd0RxFeQZqM9KWQvUoI7q8BsTHwCRxzwBpEWrOKCgTuog3BeyI-vzjTTM5rt91vnKUCsCevVULJTsmaSKbJ9yEtmMOuzIS9aSIJ02sZIE73W_o7DQ8LCtB947sKUxE9KDWz8qzos670kubnX56UiP0_3SNJPc1H8DOdtk3Nw7lhLMmAWfpZKD-wzDak2cJDZrlv1SVqpRlDMSG3Ypyf4A5Lw1LCAVqY8JSwZNfbe5iErgwEtZ6Qaoi8s"

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onStartClick: () -> Unit,
    onLoginClick: () -> Unit,
) {
    val dark = LocalAppDarkMode.current
    val screenBg = if (dark) Color(0xFF10131B) else Color(0xFFF7F9FC)
    val cardBg = if (dark) Color(0xFF1F2430) else Color.White
    val brand = if (dark) Color(0xFF8D90FF) else Color(0xFF4A4FD3)
    val muted = if (dark) Color(0xFFABB2C1) else Color(0xFF6D7180)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBg)
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(48.dp),
                color = cardBg,
                shadowElevation = 12.dp,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    OnboardingCardContent(
                        state = state,
                        onStartClick = onStartClick,
                        onLoginClick = onLoginClick,
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "⌁",
                        color = brand,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = state.appName,
                        color = brand,
                        fontSize = 29.sp / 2,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.appTagline,
                    color = muted,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun OnboardingCardContent(
    state: OnboardingUiState,
    onStartClick: () -> Unit,
    onLoginClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopVisualArea()
        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = state.titleLineOne,
            color = Color(0xFF1F232C),
            fontSize = 47.sp / 2,
            lineHeight = 54.sp / 2,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = state.titleLineTwo,
            color = Color(0xFF4B4FD9),
            fontSize = 47.sp / 2,
            lineHeight = 54.sp / 2,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = state.subtitle,
            color = Color(0xFF6E7280),
            fontSize = 15.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))
        PagerDots(pageIndex = state.pageIndex, totalPages = state.totalPages)
        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onStartClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF474BD2)),
        ) {
            Text(
                text = state.ctaText,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp,
            )
        }

        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = state.loginPrompt,
            color = Color(0xFF4A4FD3),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(onClick = onLoginClick),
        )
    }
}

@Composable
private fun TopVisualArea() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(290.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(36.dp))
                .background(Color(0xFFECEEF1)),
        ) {
            val bitmap by rememberNetworkBitmap(url = ONBOARDING_ILLUSTRATION_URL)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Minh họa kết nối wifi toàn cầu",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFFD6E2E1), Color(0xFFCAD8D7)),
                            ),
                    ),
                )
            }
        }
    }
}

@Composable
private fun PagerDots(
    pageIndex: Int,
    totalPages: Int,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(totalPages) { index ->
            val selected = index == pageIndex
            Box(
                modifier = Modifier
                    .width(if (selected) 30.dp else 9.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (selected) Color(0xFF4A4FD3) else Color(0xFFD5D7DF)),
            )
        }
    }
}

@Composable
private fun rememberNetworkBitmap(url: String): androidx.compose.runtime.State<Bitmap?> =
    produceState<Bitmap?>(initialValue = null, key1 = url) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                URL(url).openStream().use(BitmapFactory::decodeStream)
            }.getOrNull()
        }
    }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingScreenPreview() {
    SmartWifiAppTheme {
        OnboardingScreen(
            state = OnboardingPreviewData.default,
            onStartClick = {},
            onLoginClick = {},
        )
    }
}
