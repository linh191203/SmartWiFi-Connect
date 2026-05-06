package com.example.smartwificonnect.feature.scanimage

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smartwificonnect.MainUiState
import com.example.smartwificonnect.WifiConnectionState

private val OcrBackground: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF10131B) else Color(0xFFF6F8FC)
private val OcrSurface: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF1F2430) else Color.White
private val OcrSoftSurface: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF252B38) else Color(0xFFF0F2F7)
private val OcrBrand: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF8D90FF) else Color(0xFF4451D7)
private val OcrTitle: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFF4F6FB) else Color(0xFF222630)
private val OcrBody: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFABB2C1) else Color(0xFF6A7386)

@Composable
fun OcrResultScreen(
    state: MainUiState,
    onBackClick: () -> Unit,
    onSsidChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
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
                        text = "Kết quả OCR",
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
                item {
                    RecognitionStatusBanner(
                        isDetected = state.ssid.isNotBlank() || state.password.isNotBlank(),
                    )
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Thông tin Wi-Fi",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = OcrTitle,
                        )
                        Text(
                            text = "OCR đã đọc trong khung. Bạn có thể sửa lại trước khi kết nối.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = OcrBody,
                        )
                    }
                }

                item {
                    CredentialEditorCard(
                        state = state,
                        onSsidChange = onSsidChange,
                        onPasswordChange = onPasswordChange,
                        onConnectWifi = onConnectWifi,
                    )
                }

                item {
                    WifiConnectionStatus(state = state.wifiConnectionState)
                }
            }
        }
    }
}

@Composable
private fun CredentialEditorCard(
    state: MainUiState,
    onSsidChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConnectWifi: () -> Unit,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val isConnecting = state.wifiConnectionState is WifiConnectionState.Connecting

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = OcrSurface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.scanSource.isNotBlank()) {
                SourceChip(source = state.scanSource)
            }

            OutlinedTextField(
                value = state.ssid,
                onValueChange = onSsidChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Tên Wi-Fi") },
                placeholder = { Text("VD: Home_WiFi_5G") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Router,
                        contentDescription = null,
                        tint = OcrBrand,
                    )
                },
                shape = RoundedCornerShape(16.dp),
            )

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
                        tint = OcrBrand,
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Outlined.VisibilityOff
                            } else {
                                Icons.Outlined.Visibility
                            },
                            contentDescription = if (passwordVisible) "Ẩn mật khẩu" else "Hiện mật khẩu",
                        )
                    }
                },
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(16.dp),
            )

            if (state.statusMessage.isNotBlank()) {
                Text(
                    text = state.statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OcrBody,
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Button(
                onClick = onConnectWifi,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.ssid.trim().isNotEmpty() && !isConnecting,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OcrBrand,
                    contentColor = OcrSurface,
                ),
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .height(18.dp)
                            .padding(end = 8.dp),
                        strokeWidth = 2.dp,
                        color = OcrSurface,
                    )
                }
                Text(
                    text = if (isConnecting) "Đang kết nối..." else "Kết nối",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
private fun RecognitionStatusBanner(isDetected: Boolean) {
    val icon = if (isDetected) Icons.Outlined.CheckCircle else Icons.Outlined.WifiOff
    val text = if (isDetected) "Đã đọc được thông tin Wi-Fi" else "Chưa đọc được thông tin Wi-Fi"
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
                color = OcrTitle,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SourceChip(source: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = OcrSoftSurface,
    ) {
        Text(
            text = "Nguồn OCR: $source",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = OcrBrand,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun WifiConnectionStatus(state: WifiConnectionState) {
    when (state) {
        WifiConnectionState.Idle -> Unit

        is WifiConnectionState.Connecting -> {
            StatusSurface(
                text = "Đang kết nối tới ${state.ssid} ...",
                background = Color(0xFFEAF0FF),
                content = Color(0xFF2E3E71),
            )
        }

        is WifiConnectionState.Connected -> {
            StatusSurface(
                text = "Kết nối thành công: ${state.ssid}",
                background = Color(0xFFE6F6EE),
                content = Color(0xFF146C43),
            )
        }

        is WifiConnectionState.Failed -> {
            StatusSurface(
                text = state.message,
                background = Color(0xFFFDECEC),
                content = Color(0xFF9B1C1C),
            )
        }
    }
}

@Composable
private fun StatusSurface(
    text: String,
    background: Color,
    content: Color,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = background,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = content,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun OcrResultScreenPreview() {
    MaterialTheme {
        OcrResultScreen(
            state = MainUiState(
                ocrText = "WiFi Name: Cafe_Wifi\nPassword: 12345678",
                ssid = "Cafe_Wifi",
                password = "12345678",
                sourceFormat = "ai_ocr",
                confidence = 0.98,
                scanSource = "Thu vien anh",
                statusMessage = "Hay kiem tra SSID/mat khau roi bam Ket noi.",
            ),
            onBackClick = {},
            onSsidChange = {},
            onPasswordChange = {},
        )
    }
}
