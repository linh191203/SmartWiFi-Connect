package com.example.smartwificonnect.feature.review

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smartwificonnect.AiValidationState
import com.example.smartwificonnect.MainUiState
import com.example.smartwificonnect.SsidSuggestionState
import com.example.smartwificonnect.WifiConnectionState
import com.example.smartwificonnect.ui.theme.LocalAppDarkMode
import com.example.smartwificonnect.ui.theme.SmartWifiAppTheme

// ── Design tokens ─────────────────────────────────────────────────────────────

private val ReviewBg: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF10131B) else Color(0xFFF6F8FC)
private val ReviewSurface: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF1F2430) else Color.White
private val ReviewSoftSurface: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF252B38) else Color(0xFFF0F2F7)
private val ReviewBrand: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF8D90FF) else Color(0xFF4451D7)
private val ReviewTitle: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFFF4F6FB) else Color(0xFF222630)
private val ReviewBody: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFFABB2C1) else Color(0xFF6A7386)
private val ReviewSuccess: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF4CAF50) else Color(0xFF2E7D32)
private val ReviewWarning: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFFFFB74D) else Color(0xFFE65100)

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun ReviewScreen(
    state: MainUiState,
    onBackClick: () -> Unit,
    onSsidChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConnectClick: () -> Unit,
    onScanAgainClick: () -> Unit,
    onApplyAiSuggestion: (ssid: String, password: String) -> Unit,
) {
    Scaffold(
        containerColor = ReviewBg,
        topBar = {
            Surface(
                color = ReviewBg,
                shadowElevation = 1.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Quay lại",
                            tint = ReviewBrand,
                        )
                    }
                    Text(
                        text = "Xem lại thông tin Wi-Fi",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = ReviewTitle,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    ) { innerPadding ->
        if (state.isLoading) {
            ReviewLoadingState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                message = state.statusMessage,
            )
            return@Scaffold
        }

        val hasCredentials = state.ssid.isNotBlank() || state.password.isNotBlank()
        if (!hasCredentials) {
            ReviewEmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onScanAgainClick = onScanAgainClick,
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── AI Validation card ─────────────────────────────────────────
            item {
                AiValidationCard(
                    aiState = state.aiValidation,
                    onApplyAiSuggestion = onApplyAiSuggestion,
                )
            }

            // ── Fuzzy SSID suggestion ──────────────────────────────────────
            item {
                val suggestion = state.ssidSuggestion
                if (suggestion is SsidSuggestionState.Found) {
                    FuzzySuggestionCard(
                        bestMatch = suggestion.bestMatch,
                        score = suggestion.score,
                        onApply = { onSsidChange(suggestion.bestMatch) },
                    )
                }
            }

            // ── Credential editor ──────────────────────────────────────────
            item {
                ReviewCredentialCard(
                    state = state,
                    onSsidChange = onSsidChange,
                    onPasswordChange = onPasswordChange,
                )
            }

            // ── Action buttons ─────────────────────────────────────────────
            item {
                ReviewActions(
                    state = state,
                    onConnectClick = onConnectClick,
                    onScanAgainClick = onScanAgainClick,
                )
            }

            // ── Status message ─────────────────────────────────────────────
            item {
                if (state.statusMessage.isNotBlank()) {
                    Text(
                        text = state.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = ReviewBody,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

// ── AI Validation card ────────────────────────────────────────────────────────

@Composable
private fun AiValidationCard(
    aiState: AiValidationState,
    onApplyAiSuggestion: (ssid: String, password: String) -> Unit,
) {
    when (aiState) {
        is AiValidationState.Loading -> {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = ReviewSurface),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = ReviewBrand,
                    )
                    Text(
                        text = "Đang đánh giá bằng AI...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ReviewBody,
                    )
                }
            }
        }

        is AiValidationState.Ready -> {
            val confidencePct = (aiState.confidence * 100).toInt()
            val isHighConfidence = aiState.confidence >= 0.72
            val accentColor = if (isHighConfidence) ReviewSuccess else ReviewWarning
            val bgColor = if (isHighConfidence) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
            val icon = if (isHighConfidence) Icons.Outlined.CheckCircle else Icons.Outlined.Warning

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = ReviewSurface),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = ReviewBrand,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "Đánh giá AI",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = ReviewTitle,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = bgColor,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = "$confidencePct%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                )
                            }
                        }
                    }

                    Text(
                        text = aiState.suggestion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ReviewBody,
                    )

                    if (aiState.flags.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            aiState.flags.take(3).forEach { flag ->
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = ReviewSoftSurface,
                                ) {
                                    Text(
                                        text = flag.replace("_", " "),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ReviewWarning,
                                    )
                                }
                            }
                        }
                    }

                    val normalizedSsid = aiState.normalizedSsid.orEmpty()
                    val normalizedPassword = aiState.normalizedPassword.orEmpty()
                    if (normalizedSsid.isNotBlank() || normalizedPassword.isNotBlank()) {
                        OutlinedButton(
                            onClick = { onApplyAiSuggestion(normalizedSsid, normalizedPassword) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(999.dp),
                        ) {
                            Text("Áp dụng đề xuất AI", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        is AiValidationState.Failed -> {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = ReviewSurface),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = ReviewWarning,
                    )
                    Text(
                        text = "Đánh giá AI thất bại: ${aiState.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ReviewBody,
                    )
                }
            }
        }

        is AiValidationState.Hidden -> Unit
    }
}

// ── Fuzzy suggestion card ─────────────────────────────────────────────────────

@Composable
private fun FuzzySuggestionCard(
    bestMatch: String,
    score: Double,
    onApply: () -> Unit,
) {
    val pct = (score * 100).toInt()
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ReviewSurface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Gợi ý SSID gần nhất",
                    style = MaterialTheme.typography.labelMedium,
                    color = ReviewBody,
                )
                Text(
                    text = bestMatch,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = ReviewTitle,
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = ReviewSoftSurface,
            ) {
                Text(
                    text = "$pct%",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = ReviewBrand,
                )
            }
            OutlinedButton(
                onClick = onApply,
                shape = RoundedCornerShape(999.dp),
            ) {
                Text("Dùng", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Credential editor ─────────────────────────────────────────────────────────

@Composable
private fun ReviewCredentialCard(
    state: MainUiState,
    onSsidChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = ReviewSurface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Thông tin Wi-Fi",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = ReviewTitle,
            )

            // SSID field
            OutlinedTextField(
                value = state.ssid,
                onValueChange = onSsidChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Tên Wi-Fi (SSID)") },
                placeholder = { Text("VD: Home_WiFi_5G") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Router,
                        contentDescription = null,
                        tint = ReviewBrand,
                    )
                },
                trailingIcon = {
                    if (state.ssid.isNotBlank()) {
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(state.ssid))
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Sao chép SSID",
                                tint = ReviewBody,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
            )

            // Password field
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Mật khẩu") },
                placeholder = { Text("Nhập mật khẩu Wi-Fi") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Key,
                        contentDescription = null,
                        tint = ReviewBrand,
                    )
                },
                trailingIcon = {
                    Row {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                contentDescription = if (passwordVisible) "Ẩn mật khẩu" else "Hiện mật khẩu",
                            )
                        }
                        if (state.password.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(state.password))
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "Sao chép mật khẩu",
                                    tint = ReviewBody,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                shape = RoundedCornerShape(16.dp),
            )

            if (state.security.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = ReviewSoftSurface,
                ) {
                    Text(
                        text = "Bảo mật: ${state.security}",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = ReviewBrand,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

// ── Action buttons ────────────────────────────────────────────────────────────

@Composable
private fun ReviewActions(
    state: MainUiState,
    onConnectClick: () -> Unit,
    onScanAgainClick: () -> Unit,
) {
    val isConnecting = state.wifiConnectionState is WifiConnectionState.Connecting
    val isConnected = state.wifiConnectionState is WifiConnectionState.Connected

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = onConnectClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.ssid.trim().isNotEmpty() && !isConnecting && !isConnected,
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ReviewBrand),
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(18.dp)
                        .padding(end = 8.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
            }
            Text(
                text = when {
                    isConnected -> "Đã kết nối"
                    isConnecting -> "Đang kết nối..."
                    else -> "Kết nối ngay"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        OutlinedButton(
            onClick = onScanAgainClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(999.dp),
        ) {
            Text(
                text = "Quét lại",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun ReviewEmptyState(
    modifier: Modifier = Modifier,
    onScanAgainClick: () -> Unit,
) {
    Box(
        modifier = modifier.background(ReviewBg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Router,
                contentDescription = null,
                tint = ReviewBody,
                modifier = Modifier.size(64.dp),
            )
            Text(
                text = "Chưa có thông tin Wi-Fi",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = ReviewTitle,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Hãy quét mã QR hoặc ảnh có chứa thông tin Wi-Fi, sau đó quay lại đây để xem lại và kết nối.",
                style = MaterialTheme.typography.bodyMedium,
                color = ReviewBody,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onScanAgainClick,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ReviewBrand),
            ) {
                Text("Quét ngay", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Loading state ─────────────────────────────────────────────────────────────

@Composable
private fun ReviewLoadingState(
    modifier: Modifier = Modifier,
    message: String,
) {
    Box(
        modifier = modifier.background(ReviewBg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(color = ReviewBrand)
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = ReviewTitle,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}

// ── Preview ───────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun ReviewScreenPreview() {
    SmartWifiAppTheme {
        ReviewScreen(
            state = MainUiState(
                ssid = "Cafe_WiFi_5G",
                password = "cafe2025",
                security = "WPA2",
                sourceFormat = "wifi_qr_like",
                confidence = 0.93,
                aiValidation = AiValidationState.Ready(
                    validated = true,
                    confidence = 0.91,
                    suggestion = "Dữ liệu hợp lệ, có thể kết nối.",
                    flags = emptyList(),
                    recommendation = "review",
                    shouldAutoConnect = false,
                    normalizedSsid = "Cafe_WiFi_5G",
                    normalizedPassword = "cafe2025",
                ),
                ssidSuggestion = SsidSuggestionState.Found(
                    bestMatch = "Cafe_WiFi_5G",
                    score = 0.95,
                ),
                statusMessage = "Parse thành công.",
            ),
            onBackClick = {},
            onSsidChange = {},
            onPasswordChange = {},
            onConnectClick = {},
            onScanAgainClick = {},
            onApplyAiSuggestion = { _, _ -> },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReviewScreenEmptyPreview() {
    SmartWifiAppTheme {
        ReviewScreen(
            state = MainUiState(),
            onBackClick = {},
            onSsidChange = {},
            onPasswordChange = {},
            onConnectClick = {},
            onScanAgainClick = {},
            onApplyAiSuggestion = { _, _ -> },
        )
    }
}
