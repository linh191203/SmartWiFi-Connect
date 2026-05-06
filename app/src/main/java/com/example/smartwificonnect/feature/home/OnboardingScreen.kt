package com.example.smartwificonnect.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartwificonnect.R
import kotlinx.coroutines.delay

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onStartClick: () -> Unit,
) {
    val dark = isSystemInDarkTheme()
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
) {
    val slideImages = remember {
        listOf(
            R.drawable.onboarding_wifi_slide_1,
            R.drawable.onboarding_wifi_slide_2,
            R.drawable.onboarding_wifi_slide_3,
        )
    }
    var currentPage by rememberSaveable { mutableIntStateOf(0) }
    var previousPage by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(slideImages.size, currentPage) {
        while (slideImages.isNotEmpty()) {
            delay(3000L)
            previousPage = currentPage
            currentPage = (currentPage + 1) % slideImages.size
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopVisualArea(
            images = slideImages,
            currentPage = currentPage,
            previousPage = previousPage,
            onPageChange = { nextPage ->
                previousPage = currentPage
                currentPage = nextPage
            },
        )
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
        PagerDots(pageIndex = currentPage, totalPages = slideImages.size)
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
    }
}

@Composable
private fun TopVisualArea(
    images: List<Int>,
    currentPage: Int,
    previousPage: Int,
    onPageChange: (Int) -> Unit,
) {
    var dragDistance by remember { mutableFloatStateOf(0f) }
    val direction = if (currentPage >= previousPage) 1 else -1

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(290.dp)
            .pointerInput(images.size, currentPage) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val threshold = 72f
                        when {
                            dragDistance <= -threshold -> {
                                onPageChange((currentPage + 1) % images.size)
                            }

                            dragDistance >= threshold -> {
                                onPageChange((currentPage - 1 + images.size) % images.size)
                            }
                        }
                        dragDistance = 0f
                    },
                    onDragCancel = {
                        dragDistance = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        dragDistance += dragAmount
                    },
                )
            },
    ) {
        AnimatedContent(
            targetState = currentPage,
            transitionSpec = {
                val forward = if (targetState == initialState) {
                    direction >= 0
                } else {
                    ((targetState - initialState + images.size) % images.size) == 1
                }
                val offset = { fullWidth: Int -> if (forward) fullWidth else -fullWidth }
                slideInHorizontally(initialOffsetX = offset) + fadeIn() togetherWith
                    slideOutHorizontally(targetOffsetX = { -offset(it) }) + fadeOut()
            },
            label = "onboarding-slideshow",
        ) { page ->
            Image(
                painter = painterResource(id = images[page]),
                contentDescription = "Minh hoa auto connect Wi-Fi",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(36.dp)),
            )
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

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun OnboardingScreenPreview() {
    MaterialTheme {
        OnboardingScreen(
            state = OnboardingPreviewData.default,
            onStartClick = {},
        )
    }
}
