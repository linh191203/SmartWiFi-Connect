package com.example.smartwificonnect.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartwificonnect.R

/**
 * Stateless Splash UI component.
 * Hiển thị logo, tên app, tagline, và loading bar animation.
 * Không chứa business logic — chỉ là UI thuần (theo coding-rules.md).
 */
@Composable
fun SplashScreen(
    appTitle: String,
    tagline: String,
    loadingText: String,
    versionText: String,
) {
    // ── Animations ──────────────────────────────────────────────
    val infinite = rememberInfiniteTransition(label = "splash-transition")

    val ringScaleA by infinite.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring-scale-a",
    )
    val ringAlphaA by infinite.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring-alpha-a",
    )
    val ringScaleB by infinite.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, delayMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring-scale-b",
    )
    val ringAlphaB by infinite.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, delayMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring-alpha-b",
    )
    val loadingFraction by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "loading-fraction",
    )

    // ── Layout ──────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF5B5FEF), Color(0xFF4143D5)),
                ),
            ),
    ) {
        // Decorative circles
        Box(
            modifier = Modifier
                .offset(x = (-130).dp, y = (-130).dp)
                .size(300.dp)
                .background(color = Color(0x33C0C1FF), shape = CircleShape),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 130.dp, y = 130.dp)
                .size(300.dp)
                .background(color = Color(0x1A46DDBE), shape = CircleShape),
        )

        // ── Center content: logo + title ────────────────────────
        CenterContent(
            appTitle = appTitle,
            tagline = tagline,
            ringScaleA = ringScaleA,
            ringAlphaA = ringAlphaA,
            ringScaleB = ringScaleB,
            ringAlphaB = ringAlphaB,
        )

        // ── Bottom content: loading bar + version ───────────────
        BottomContent(
            loadingText = loadingText,
            versionText = versionText,
            loadingFraction = loadingFraction,
        )

        // Bottom gradient overlay
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0x524143D5)),
                    ),
                ),
        )
    }
}

// ── Sub-components (tách theo coding-rules: không viết composable 300 dòng) ──

@Composable
private fun CenterContent(
    appTitle: String,
    tagline: String,
    ringScaleA: Float,
    ringAlphaA: Float,
    ringScaleB: Float,
    ringAlphaB: Float,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.offset(y = (-26).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            // Pulsing rings + logo
            Box(contentAlignment = Alignment.Center) {
                PulseRing(scale = ringScaleA, alpha = ringAlphaA, color = Color(0x24FFFFFF))
                PulseRing(scale = ringScaleB, alpha = ringAlphaB, color = Color(0x16FFFFFF))
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .clip(CircleShape)
                        .background(Color(0x1FFFFFFF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_splash_wifi),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(58.dp),
                    )
                }
            }

            // Title + tagline
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = appTitle,
                    color = Color.White,
                    fontSize = 39.sp,
                    lineHeight = 42.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(8.dp))
                TaglineRow(tagline = tagline)
            }
        }
    }
}

@Composable
private fun PulseRing(scale: Float, alpha: Float, color: Color) {
    Box(
        modifier = Modifier
            .size(132.dp)
            .scale(scale)
            .alpha(alpha)
            .clip(CircleShape)
            .background(Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(color),
        )
    }
}

@Composable
private fun TaglineRow(tagline: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 18.dp, height = 1.dp)
                .background(Color(0x52FFFFFF)),
        )
        Text(
            text = tagline.uppercase(),
            color = Color(0xCCFFFFFF),
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Medium,
        )
        Box(
            modifier = Modifier
                .size(width = 18.dp, height = 1.dp)
                .background(Color(0x52FFFFFF)),
        )
    }
}

@Composable
private fun BottomContent(
    loadingText: String,
    versionText: String,
    loadingFraction: Float,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier.padding(bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LoadingBar(fraction = loadingFraction)
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = loadingText.uppercase(),
                color = Color(0x99FFFFFF),
                fontSize = 10.sp,
                letterSpacing = 1.6.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = versionText,
                color = Color(0x66FFFFFF),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun LoadingBar(fraction: Float) {
    val density = LocalDensity.current
    val indicatorWidthPx = with(density) { 72.dp.toPx() }
    var trackWidthPx by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxWidth(0.55f)
            .height(3.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0x22FFFFFF))
            .onSizeChanged { trackWidthPx = it.width },
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(72.dp)
                .graphicsLayer {
                    val startX = -indicatorWidthPx
                    val endX = trackWidthPx.toFloat()
                    translationX = startX + ((endX - startX) * fraction)
                }
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White),
        )
    }
}
