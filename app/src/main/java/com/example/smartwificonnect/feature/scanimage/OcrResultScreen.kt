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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.example.smartwificonnect.MainUiState
import com.example.smartwificonnect.R
import com.example.smartwificonnect.ui.theme.SmartWifiAppTheme
import androidx.compose.ui.res.stringResource

@Composable
fun OcrResultScreen(
    state: MainUiState,
    onBackClick: () -> Unit,
    onOcrTextChange: (String) -> Unit,
    onParseClick: () -> Unit,
    onConnectClick: () -> Unit,
    onSavePasswordChange: (Boolean) -> Unit,
    onAcceptSuggestion: () -> Unit = {},
    onDismissSuggestion: () -> Unit = {},
    onToggleNearby: () -> Unit = {},
    onSelectNetwork: (String) -> Unit = {},
) {
    Scaffold(
        containerColor = Color(0xFFF6F8FC),
        topBar = {
            Surface(shadowElevation = 1.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = Color(0xFF4451D7),
                        )
                    }
                    Text(
                        text = stringResource(R.string.ocr_result_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF222630),
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
                item {
                    InfoBanner(
                        title = stringResource(R.string.ocr_result_ready_banner),
                        body = state.statusMessage,
                    )
                }

                if (state.scanSource.isNotBlank()) {
                    item {
                        SourceChip(source = state.scanSource)
                    }
                }

                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DocumentScanner,
                                    contentDescription = null,
                                    tint = Color(0xFF4451D7),
                                )
                                Text(
                                    text = stringResource(R.string.ocr_result_text_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = stringResource(R.string.ocr_result_text_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF5B6272),
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedTextField(
                                value = state.ocrText,
                                onValueChange = onOcrTextChange,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp),
                                shape = RoundedCornerShape(18.dp),
                                label = { Text(stringResource(R.string.ocr_result_text_label)) },
                                placeholder = { Text(stringResource(R.string.ocr_result_text_placeholder)) },
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = onParseClick,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = state.ocrText.isNotBlank(),
                            ) {
                                Text(stringResource(R.string.ocr_result_parse_button))
                            }
                        }
                    }
                }

                item {
                    if (state.ssid.isBlank() && state.password.isBlank()) {
                        EmptyParsedState()
                    } else {
                        ParsedWifiCard(
                            state = state,
                            onConnectClick = onConnectClick,
                            onSavePasswordChange = onSavePasswordChange,
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
private fun LoadingState(
    modifier: Modifier = Modifier,
    message: String,
) {
    Box(
        modifier = modifier.background(Color(0xFFF6F8FC)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(color = Color(0xFF4451D7))
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
        color = Color(0xFFF0E7DA),
    ) {
        Text(
            text = stringResource(R.string.ocr_result_source_format, source),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF7B5A2D),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun EmptyParsedState() {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
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
                tint = Color(0xFF4451D7),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.ocr_result_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF272C37),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.ocr_result_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF62697A),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ParsedWifiCard(
    state: MainUiState,
    onConnectClick: () -> Unit,
    onSavePasswordChange: (Boolean) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.ocr_result_parsed_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF272C37),
            )
            WifiInfoRow(label = stringResource(R.string.ocr_result_label_ssid), value = state.ssid)
            WifiInfoRow(label = stringResource(R.string.ocr_result_label_password), value = state.password)
            WifiInfoRow(label = stringResource(R.string.ocr_result_label_security), value = state.security)
            WifiInfoRow(label = stringResource(R.string.ocr_result_label_source), value = state.sourceFormat)
            WifiInfoRow(label = stringResource(R.string.ocr_result_label_confidence), value = state.confidence?.toString().orEmpty())
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Checkbox(
                    checked = state.savePasswordOnDevice,
                    onCheckedChange = onSavePasswordChange,
                )
                Column {
                    Text(
                        text = stringResource(R.string.ocr_result_save_password_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFF20242D),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (state.savePasswordOnDevice) {
                            stringResource(R.string.ocr_result_save_password_on)
                        } else {
                            stringResource(R.string.ocr_result_save_password_off)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF62697A),
                    )
                }
            }
            Button(
                onClick = onConnectClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.ssid.isNotBlank(),
            ) {
                Text(stringResource(R.string.ocr_result_connect_button))
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
            color = Color(0xFF657089),
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (value.isBlank()) "-" else value,
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFF20242D),
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
            onConnectClick = {},
            onSavePasswordChange = {},
        )
    }
}
