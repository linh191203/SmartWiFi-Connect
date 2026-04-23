package com.example.smartwificonnect.feature.scanimage

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.SignalWifi4Bar
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.smartwificonnect.NearbyNetwork
import com.example.smartwificonnect.R
import com.example.smartwificonnect.SsidSuggestionState
import com.example.smartwificonnect.ui.theme.SmartWifiAppTheme

// ── Colors ─────────────────────────────────────────────────
private val PrimaryBlue = Color(0xFF4451D7)
private val PrimaryPurple = Color(0xFF7C6DD8)
private val OcrChipBg = Color(0xFFFFF3E0)
private val OcrChipText = Color(0xFFE65100)
private val MatchChipBg = Color(0xFFE8F5E9)
private val MatchChipText = Color(0xFF1B5E20)
private val BarTrack = Color(0xFFE0E0E0)
private val CardBg = Color.White
private val HeaderGradient = Brush.linearGradient(
    colors = listOf(PrimaryBlue, PrimaryPurple),
    start = Offset.Zero,
    end = Offset(400f, 0f),
)

/**
 * Main entry point — renders the right UI based on [SsidSuggestionState].
 */
@Composable
fun SsidSuggestionCard(
    state: SsidSuggestionState,
    ocrSsid: String,
    nearbyNetworks: List<NearbyNetwork>,
    isNearbyExpanded: Boolean,
    onAcceptSuggestion: () -> Unit,
    onDismiss: () -> Unit,
    onToggleNearby: () -> Unit,
    onSelectNetwork: (String) -> Unit,
) {
    if (state is SsidSuggestionState.Hidden) return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (state) {
            is SsidSuggestionState.Loading -> SuggestionLoading()
            is SsidSuggestionState.Found -> SuggestionFound(
                ocrSsid = ocrSsid,
                bestMatch = state.bestMatch,
                score = state.score,
                onAccept = onAcceptSuggestion,
                onDismiss = onDismiss,
            )
            is SsidSuggestionState.NotFound -> SuggestionNotFound(onDismiss = onDismiss)
            else -> { /* Hidden – already handled */ }
        }

        // Danh sách mạng gần đây (hiện ở mọi state trừ Hidden)
        if (nearbyNetworks.isNotEmpty()) {
            NearbyNetworksList(
                networks = nearbyNetworks,
                isExpanded = isNearbyExpanded,
                onToggle = onToggleNearby,
                onSelect = onSelectNetwork,
            )
        }
    }
}

// ── State: Loading ──────────────────────────────────────────

@Composable
private fun SuggestionLoading() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmerAlpha",
    )

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Gradient header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HeaderGradient, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = stringResource(R.string.ssid_suggestion_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                    )
                }
            }

            // Loading content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.ssid_suggestion_loading),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF5B6272).copy(alpha = alpha),
                    fontWeight = FontWeight.SemiBold,
                )
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(99.dp)),
                    color = PrimaryBlue,
                    trackColor = BarTrack,
                )
            }
        }
    }
}

// ── State: Found ────────────────────────────────────────────

@Composable
private fun SuggestionFound(
    ocrSsid: String,
    bestMatch: String,
    score: Double,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scorePercent = (score * 100).toInt()

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Gradient header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HeaderGradient, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = stringResource(R.string.ssid_suggestion_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                    )
                }
            }

            // Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // OCR chip row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.ssid_suggestion_ocr_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF5B6272),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = OcrChipBg,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Warning,
                                contentDescription = null,
                                tint = OcrChipText,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = ocrSsid,
                                style = MaterialTheme.typography.labelLarge,
                                color = OcrChipText,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // Arrow down
                Text(
                    text = "↓",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize = 20.sp,
                    color = Color(0xFF9CA3AF),
                )

                // Match chip row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.ssid_suggestion_best_match_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF5B6272),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MatchChipBg,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MatchChipText,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = bestMatch,
                                style = MaterialTheme.typography.labelLarge,
                                color = MatchChipText,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // Confidence bar
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.ssid_suggestion_match_percent, scorePercent),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF3B4151),
                    )
                    LinearProgressIndicator(
                        progress = { score.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(99.dp)),
                        color = if (scorePercent >= 80) Color(0xFF66BB6A) else PrimaryBlue,
                        trackColor = BarTrack,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = PrimaryBlue,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.ssid_suggestion_dismiss),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Button(
                        onClick = onAccept,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryBlue,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.ssid_suggestion_use_this),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

// ── State: NotFound ─────────────────────────────────────────

@Composable
private fun SuggestionNotFound(onDismiss: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Gradient header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HeaderGradient, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = stringResource(R.string.ssid_suggestion_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                    )
                }
            }

            // Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.SignalWifi4Bar,
                    contentDescription = null,
                    tint = Color(0xFFBDBDBD),
                    modifier = Modifier.size(36.dp),
                )
                Text(
                    text = stringResource(R.string.ssid_suggestion_not_found_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF62697A),
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.ssid_suggestion_not_found_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9CA3AF),
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = PrimaryBlue,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.ssid_suggestion_keep_current),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ── Nearby Networks List ────────────────────────────────────

@Composable
private fun NearbyNetworksList(
    networks: List<NearbyNetwork>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onSelect: (String) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = tween(300)),
        ) {
            // Toggle header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Wifi,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.ssid_suggestion_nearby_toggle, networks.size),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF3B4151),
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (isExpanded) "Thu gọn" else "Mở rộng",
                    tint = Color(0xFF9CA3AF),
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        color = Color(0xFFF0F0F0),
                    )
                    networks.forEach { network ->
                        NearbyNetworkItem(
                            network = network,
                            onClick = { onSelect(network.ssid) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NearbyNetworkItem(
    network: NearbyNetwork,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Signal indicator
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = PrimaryBlue.copy(alpha = 0.1f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Wifi,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(18.dp),
                )
            }

            Text(
                text = network.ssid,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF272C37),
            )
        }

        // Signal level dots
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(4) { index ->
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = (8 + index * 4).dp)
                        .background(
                            color = if (index < network.signalLevel) PrimaryBlue else BarTrack,
                            shape = RoundedCornerShape(2.dp),
                        ),
                )
            }
        }
    }
}

// ── Previews ────────────────────────────────────────────────

private val previewNetworks = listOf(
    NearbyNetwork("Cafe_WiFi_5G", 4),
    NearbyNetwork("Hieu_Mobile_4G", 3),
    NearbyNetwork("Family_Connect", 3),
    NearbyNetwork("Cafe_Visitor", 2),
    NearbyNetwork("Public_Guest", 1),
)

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun PreviewLoading() {
    SmartWifiAppTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            SsidSuggestionCard(
                state = SsidSuggestionState.Loading,
                ocrSsid = "Cafe_WíFi_5g",
                nearbyNetworks = previewNetworks,
                isNearbyExpanded = false,
                onAcceptSuggestion = {},
                onDismiss = {},
                onToggleNearby = {},
                onSelectNetwork = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun PreviewFound() {
    SmartWifiAppTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            SsidSuggestionCard(
                state = SsidSuggestionState.Found("Cafe_WiFi_5G", 0.92),
                ocrSsid = "Cafe_WíFi_5g",
                nearbyNetworks = previewNetworks,
                isNearbyExpanded = true,
                onAcceptSuggestion = {},
                onDismiss = {},
                onToggleNearby = {},
                onSelectNetwork = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun PreviewNotFound() {
    SmartWifiAppTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            SsidSuggestionCard(
                state = SsidSuggestionState.NotFound,
                ocrSsid = "xyz_unknown",
                nearbyNetworks = previewNetworks,
                isNearbyExpanded = false,
                onAcceptSuggestion = {},
                onDismiss = {},
                onToggleNearby = {},
                onSelectNetwork = {},
            )
        }
    }
}
