package com.example.smartwificonnect.feature.scanimage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smartwificonnect.AiValidationState
import com.example.smartwificonnect.MainUiState
import com.example.smartwificonnect.WifiConnectionState
import com.example.smartwificonnect.ui.theme.LocalAppDarkMode
import com.example.smartwificonnect.ui.theme.SmartWifiAppTheme

private val OcrBackground: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF10131B) else Color(0xFFF6F8FC)
private val OcrSurface: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF1F2430) else Color.White
private val OcrSoftSurface: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF252B38) else Color(0xFFF0F2F7)
private val OcrBrand: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF8D90FF) else Color(0xFF4451D7)
private val OcrTitle: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFFF4F6FB) else Color(0xFF222630)
private val OcrBody: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFFABB2C1) else Color(0xFF6A7386)

@Composable
fun OcrResultScreen(
    state: MainUiState,
    onBackClick: () -> Unit,
    onOcrTextChange: (String) -> Unit,
    onParseClick: () -> Unit,
    onAcceptSuggestion: () -> Unit = {},
    onDismissSuggestion: () -> Unit = {},
    onToggleNearby: () -> Unit = {},
    onSelectNetwork: (String) -> Unit = {},
    onUseAiSsid: () -> Unit = {},
    onUseAiPassword: () -> Unit = {},
    onConnectWifi: () -> Unit = {},
) {
    Scaffold(
        containerColor = OcrBackground,
        topBar = {
            Surface(
                color = OcrBackground,
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
                            tint = OcrBrand,
                        )
                    }
                    Text(
                        text = "Kết quả quét ảnh",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = OcrTitle,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    ) { innerPadding ->
        if (state.isLoading) {
            LoadingState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                message = state.statusMessage,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val isDetected = state.ocrText.isNotBlank() ||
                    state.ssid.isNotBlank() ||
                    state.password.isNotBlank()
                item {
                    RecognitionStatusBanner(isDetected = isDetected)
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Văn bản nhận diện được",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF1E232D),
                        )
                        Text(
                            text = "Sửa lỗi nếu cần trước khi tiếp tục.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = OcrBody,
                        )
                    }
                }

                item {
                    Card(
                        shape = RoundedCornerShape(26.dp),
                        colors = CardDefaults.cardColors(containerColor = OcrSoftSurface),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Nội dung OCR",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFF4A5264),
                                    fontWeight = FontWeight.Bold,
                                )
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "Chỉnh sửa OCR",
                                    tint = Color(0xFF8E95FF),
                                )
                            }

                            OutlinedTextField(
                                value = state.ocrText,
                                onValueChange = onOcrTextChange,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 180.dp),
                                shape = RoundedCornerShape(18.dp),
                                placeholder = { Text("WiFi Name: ...") },
                            )
                            if (state.scanSource.isNotBlank()) {
                                SourceChip(source = state.scanSource)
                            }
                        }
                    }
                }

                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFFE8F4FF),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = Color(0xFF0F7CA6),
                            )
                            Text(
                                text = "Vui lòng kiểm tra lại thông tin mạng trước khi phân tích.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF0F6C8F),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                item {
                    Button(
                        onClick = onParseClick,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.ocrText.isNotBlank(),
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4B4BE3),
                            contentColor = OcrSurface,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DocumentScanner,
                                contentDescription = null,
                            )
                            Text(
                                text = "Phân tích & Kết nối",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }

                item {
                    if (state.ssid.isBlank() && state.password.isBlank()) {
                        EmptyParsedState()
                    } else {
                        ParsedWifiCard(
                            state = state,
                            onConnectWifi = onConnectWifi,
                        )
                    }
                }

                if (state.aiValidation !is AiValidationState.Hidden) {
                    item {
                        AiValidationCard(
                            aiState = state.aiValidation,
                            parsedSsid = state.ssid,
                            parsedPassword = state.password,
                            onUseAiSsid = onUseAiSsid,
                            onUseAiPassword = onUseAiPassword,
                        )
                    }
                }

                // Fuzzy SSID suggestion card (sau khi parse xong)
                if (state.ssid.isNotBlank()) {
                    item {
                        SsidSuggestionCard(
                            state = state.ssidSuggestion,
                            ocrSsid = state.ssid,
                            nearbyNetworks = state.nearbyNetworks,
                            nearbyStatus = state.nearbyWifiStatus,
                            isNearbyExpanded = state.isNearbyExpanded,
                            onAcceptSuggestion = onAcceptSuggestion,
                            onDismiss = onDismissSuggestion,
                            onToggleNearby = onToggleNearby,
                            onSelectNetwork = onSelectNetwork,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecognitionStatusBanner(isDetected: Boolean) {
    val icon = if (isDetected) Icons.Outlined.CheckCircle else Icons.Outlined.Cancel
    val text = if (isDetected) "Nhận diện thành công" else "Nhận diện không thành công"
    val tint = if (isDetected) OcrBrand else Color(0xFFB3261E)
    val bg = if (isDetected) Color(0xFFEAF0FF) else Color(0xFFFDECEC)

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bg,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = tint,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun AiValidationCard(
    aiState: AiValidationState,
    parsedSsid: String,
    parsedPassword: String,
    onUseAiSsid: () -> Unit,
    onUseAiPassword: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = OcrSurface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "AI Validation",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = OcrTitle,
            )

            when (aiState) {
                AiValidationState.Loading -> {
                    Text(
                        text = "Dang danh gia ket qua OCR...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OcrBody,
                    )
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = OcrBrand,
                    )
                }

                is AiValidationState.Failed -> {
                    Text(
                        text = "Khong lay duoc AI review: ${aiState.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB3261E),
                    )
                }

                is AiValidationState.Ready -> {
                    val confidencePercent = (aiState.confidence * 100).toInt().coerceIn(0, 100)
                    Text(
                        text = "Do tin cay AI: $confidencePercent%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF223159),
                    )
                    LinearProgressIndicator(
                        progress = { aiState.confidence.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (aiState.shouldAutoConnect) Color(0xFF2E7D32) else OcrBrand,
                        trackColor = Color(0xFFDDE3F5),
                    )
                    Text(
                        text = aiState.suggestion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OcrBody,
                    )
                    ChoiceRow(recommendation = aiState.recommendation)

                    if (aiState.flags.isNotEmpty()) {
                        Text(
                            text = "Flags: ${aiState.flags.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF7D8597),
                        )
                    }

                    val aiSsid = aiState.normalizedSsid.orEmpty()
                    if (aiSsid.isNotBlank() && !aiSsid.equals(parsedSsid, ignoreCase = true)) {
                        OutlinedButton(
                            onClick = onUseAiSsid,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Dung SSID AI: $aiSsid")
                        }
                    }

                    val aiPassword = aiState.normalizedPassword.orEmpty()
                    if (aiPassword.isNotBlank() && aiPassword != parsedPassword) {
                        OutlinedButton(
                            onClick = onUseAiPassword,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Dung password AI")
                        }
                    }
                }

                AiValidationState.Hidden -> Unit
            }
        }
    }
}

@Composable
private fun ChoiceRow(recommendation: String) {
    val normalized = recommendation.trim().lowercase()
    val choices = listOf(
        "connect" to "Auto connect",
        "review" to "Review thu cong",
        "retry_ocr" to "OCR lai",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        choices.forEach { (value, label) ->
            val selected = normalized == value
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (selected) OcrBrand else OcrSoftSurface,
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) OcrSurface else Color(0xFF556070),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun LoadingState(
    modifier: Modifier = Modifier,
    message: String,
) {
    Box(
        modifier = modifier.background(OcrBackground),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(color = OcrBrand)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF3B4151),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun InfoBanner(
    title: String,
    body: String,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF0FF)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF223159),
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF445070),
            )
        }
    }
}

@Composable
private fun SourceChip(source: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color(0xFFE4E8F9),
    ) {
        Text(
            text = "Nguon OCR: $source",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF3F4FA8),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun EmptyParsedState() {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = OcrSurface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = OcrBrand,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Chưa có dữ liệu parse",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = OcrTitle,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Sau khi bam Parse voi server, SSID va mat khau se hien o day.",
                style = MaterialTheme.typography.bodyMedium,
                color = OcrBody,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ParsedWifiCard(
    state: MainUiState,
    onConnectWifi: () -> Unit,
) {
    val isConnecting = state.wifiConnectionState is WifiConnectionState.Connecting
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = OcrSurface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Parsed Wi-Fi",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = OcrTitle,
            )
            WifiInfoRow(label = "SSID", value = state.ssid)
            WifiInfoRow(label = "Password", value = state.password)
            WifiInfoRow(label = "Security", value = state.security)
            WifiInfoRow(label = "Source", value = state.sourceFormat)
            WifiInfoRow(label = "Confidence", value = state.confidence?.toString().orEmpty())

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = onConnectWifi,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.ssid.isNotBlank() && !isConnecting,
            ) {
                Text(
                    if (isConnecting) "Dang ket noi Wi-Fi..." else "Ket noi Wi-Fi ",
                )
            }

            WifiConnectionStatus(state = state.wifiConnectionState)
        }
    }
}

@Composable
private fun WifiConnectionStatus(state: WifiConnectionState) {
    when (state) {
        WifiConnectionState.Idle -> Unit

        is WifiConnectionState.Connecting -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFEAF0FF),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp,
                        color = OcrBrand,
                    )
                    Text(
                        text = "Dang ket noi toi ${state.ssid} ...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF2E3E71),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        is WifiConnectionState.Connected -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFE6F6EE),
            ) {
                Text(
                    text = "Ket noi thanh cong: ${state.ssid}",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF146C43),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        is WifiConnectionState.Failed -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFDECEC),
            ) {
                Text(
                    text = state.message,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF9B1C1C),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun WifiInfoRow(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = OcrBody,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (value.isBlank()) "-" else value,
            style = MaterialTheme.typography.titleMedium,
            color = OcrTitle,
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun OcrResultScreenPreview() {
    SmartWifiAppTheme {
        OcrResultScreen(
            state = MainUiState(
                ocrText = "WiFi Name: Cafe_Wifi\nPassword: 12345678\nSecurity: WPA2",
                ssid = "Cafe_Wifi",
                password = "12345678",
                security = "WPA2",
                sourceFormat = "mock_local",
                confidence = 0.98,
                scanSource = "Thu vien anh",
                statusMessage = "Da nhan OCR. Ban co the sua text truoc khi parse.",
                aiValidation = AiValidationState.Ready(
                    validated = true,
                    confidence = 0.87,
                    suggestion = "Du lieu kha on, nen review nhanh roi ket noi.",
                    flags = listOf("ocr_ambiguous_characters"),
                    recommendation = "review",
                    shouldAutoConnect = false,
                    normalizedSsid = "Cafe_WiFi_5G",
                    normalizedPassword = "12345678",
                ),
                ssidSuggestion = com.example.smartwificonnect.SsidSuggestionState.Found(
                    bestMatch = "Cafe_WiFi_5G",
                    score = 0.92,
                ),
                nearbyNetworks = listOf(
                    com.example.smartwificonnect.NearbyNetwork("Cafe_WiFi_5G", 4),
                    com.example.smartwificonnect.NearbyNetwork("Hieu_Mobile_4G", 3),
                    com.example.smartwificonnect.NearbyNetwork("Public_Guest", 1),
                ),
            ),
            onBackClick = {},
            onOcrTextChange = {},
            onParseClick = {},
        )
    }
}
