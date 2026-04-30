package com.example.smartwificonnect.feature.manual

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartwificonnect.MainUiState
import com.example.smartwificonnect.R
import com.example.smartwificonnect.WifiConnectionState
import com.example.smartwificonnect.ui.theme.LocalAppDarkMode
import com.example.smartwificonnect.ui.theme.SmartWifiAppTheme

private val ManualBackground: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF10131B) else Color(0xFFF7F9FC)
private val ManualBrand: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF8D90FF) else Color(0xFF4143D5)
private val ManualHeroBackground: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF252B38) else Color(0xFFE2E4F5)
private val ManualCardBackground: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF1F2430) else Color(0xFFFDFDFE)
private val ManualInputBackground: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF2B3240) else Color(0xFFEFF1F6)
private val ManualInputText: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFFF4F6FB) else Color(0xFF1F2430)
private val ManualPlaceholder: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF8E98AA) else Color(0xFFB0B5C2)
private val ManualLabel: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFFA7A8FF) else Color(0xFF4143D5)
private val ManualBody: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFFABB2C1) else Color(0xFF4F5461)
private val ManualPrimaryButton: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF6D70F6) else Color(0xFF4143D5)
private val ManualTipBackground: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF173545) else Color(0xFFDCE7F4)
private val ManualTipStroke: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF245069) else Color(0xFFBFD3EA)
private val ManualTipBody: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF7ADFFF) else Color(0xFF0E5A83)

@Composable
fun ManualEntryScreen(
    state: MainUiState,
    onBackClick: () -> Unit,
    onSsidChange: (String) -> Unit,
    onSecurityChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConnectAndSaveClick: () -> Unit,
    onCancelClick: () -> Unit,
) {
    var isSecurityMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }
    val securityOptions = listOf(
        stringResource(R.string.manual_entry_security_wpa_wpa2),
        stringResource(R.string.manual_entry_security_wpa3),
        stringResource(R.string.manual_entry_security_wpa2_enterprise),
        stringResource(R.string.manual_entry_security_wep),
        stringResource(R.string.manual_entry_security_open),
    )

    LaunchedEffect(state.security) {
        if (state.security.isBlank()) {
            onSecurityChange(securityOptions.first())
        }
    }

    val selectedSecurity = state.security.ifBlank { securityOptions.first() }
    val isConnecting = state.wifiConnectionState is WifiConnectionState.Connecting
    val relevantStatus = state.statusMessage.takeIf { msg ->
        msg.isNotBlank() && msg != "Sẵn sàng quét OCR WiFi"
    }
    val isStatusError = relevantStatus != null && (
        relevantStatus.contains("lỗi", ignoreCase = true) ||
        relevantStatus.contains("thất bại", ignoreCase = true) ||
        relevantStatus.contains("không", ignoreCase = true) ||
        relevantStatus.contains("chưa", ignoreCase = true)
    )

    Scaffold(
        containerColor = ManualBackground,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ManualTopBar(onBackClick = onBackClick)
            Spacer(modifier = Modifier.height(28.dp))

            Box(
                modifier = Modifier
                    .size(92.dp)
                    .background(color = ManualHeroBackground, shape = RoundedCornerShape(36.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Link,
                    contentDescription = stringResource(R.string.manual_entry_cd_manual_icon),
                    tint = ManualBrand,
                    modifier = Modifier.size(36.dp),
                )
            }

            Spacer(modifier = Modifier.height(30.dp))

            Text(
                text = stringResource(R.string.manual_entry_title),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF1E232D),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.manual_entry_subtitle),
                style = MaterialTheme.typography.titleMedium,
                color = ManualBody,
                textAlign = TextAlign.Center,
                lineHeight = 36.sp,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = ManualCardBackground,
                shape = RoundedCornerShape(32.dp),
                shadowElevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    FieldLabel(text = stringResource(R.string.manual_entry_label_ssid))
                    ManualInputField(
                        value = state.ssid,
                        onValueChange = onSsidChange,
                        placeholder = stringResource(R.string.manual_entry_placeholder_ssid),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        trailing = {
                            Icon(
                                imageVector = Icons.Rounded.Router,
                                contentDescription = stringResource(R.string.manual_entry_cd_ssid_icon),
                                tint = ManualPlaceholder,
                            )
                        },
                    )

                    FieldLabel(text = stringResource(R.string.manual_entry_label_security))
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isSecurityMenuExpanded = true },
                            color = ManualInputBackground,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = selectedSecurity,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = ManualInputText,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.manual_entry_cd_security_dropdown),
                                    tint = Color(0xFF9BA2B1),
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = isSecurityMenuExpanded,
                            onDismissRequest = { isSecurityMenuExpanded = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White),
                        ) {
                            securityOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(text = option) },
                                    onClick = {
                                        onSecurityChange(option)
                                        isSecurityMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    FieldLabel(text = stringResource(R.string.manual_entry_label_password))
                    ManualInputField(
                        value = state.password,
                        onValueChange = onPasswordChange,
                        placeholder = stringResource(R.string.manual_entry_placeholder_password),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = if (isPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailing = {
                            IconButton(
                                onClick = { isPasswordVisible = !isPasswordVisible },
                            ) {
                                Icon(
                                    imageVector = if (isPasswordVisible) {
                                        Icons.Rounded.VisibilityOff
                                    } else {
                                        Icons.Rounded.Visibility
                                    },
                                    contentDescription = if (isPasswordVisible) {
                                        stringResource(R.string.manual_entry_cd_hide_password)
                                    } else {
                                        stringResource(R.string.manual_entry_cd_show_password)
                                    },
                                    tint = ManualPlaceholder,
                                )
                            }
                        },
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(62.dp)
                            .shadow(
                                elevation = 14.dp,
                                shape = RoundedCornerShape(10.dp),
                                ambientColor = ManualBrand.copy(alpha = 0.36f),
                                spotColor = ManualBrand.copy(alpha = 0.36f),
                            ),
                        onClick = onConnectAndSaveClick,
                        enabled = state.ssid.trim().isNotEmpty() && !isConnecting,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ManualPrimaryButton,
                            disabledContainerColor = ManualPrimaryButton.copy(alpha = 0.38f),
                        ),
                    ) {
                        Text(
                            text = if (isConnecting) {
                                stringResource(R.string.manual_entry_connecting)
                            } else {
                                stringResource(R.string.manual_entry_action_connect_save)
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                        )
                    }

                    Text(
                        text = stringResource(R.string.manual_entry_action_cancel),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onCancelClick)
                            .padding(vertical = 8.dp),
                        style = MaterialTheme.typography.headlineSmall,
                        color = ManualBrand,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.ExtraBold,
                    )

                        if (relevantStatus != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = relevantStatus,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isStatusError) Color(0xFFD93025) else ManualBody,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = ManualTipStroke,
                        shape = RoundedCornerShape(32.dp),
                    ),
                color = ManualTipBackground,
                shape = RoundedCornerShape(32.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = Color(0xFF0C6998),
                        modifier = Modifier.size(30.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.manual_entry_tip_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0D2B3F),
                        )
                        Text(
                            text = stringResource(R.string.manual_entry_tip_body),
                            style = MaterialTheme.typography.titleMedium,
                            color = ManualTipBody,
                            lineHeight = 34.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.ExtraBold,
        color = ManualLabel,
        letterSpacing = 1.2.sp,
    )
}

@Composable
private fun ManualInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardOptions: KeyboardOptions,
    trailing: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.titleLarge,
                color = ManualPlaceholder,
            )
        },
        textStyle = MaterialTheme.typography.titleLarge.copy(
            color = ManualInputText,
            fontWeight = FontWeight.ExtraBold,
        ),
        keyboardOptions = keyboardOptions,
        singleLine = true,
        trailingIcon = trailing,
        visualTransformation = visualTransformation,
        shape = RoundedCornerShape(10.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = ManualInputBackground,
            unfocusedContainerColor = ManualInputBackground,
            disabledContainerColor = ManualInputBackground,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = ManualBrand,
        ),
    )
}

@Composable
private fun ManualTopBar(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.manual_entry_cd_back),
                tint = ManualBrand,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = stringResource(R.string.manual_entry_brand_title),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = ManualBrand,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = Color(0xFFA2CFCC),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = stringResource(R.string.manual_entry_cd_profile),
                    tint = Color(0xFF2F4B59),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 1218)
@Composable
private fun ManualEntryScreenPreview() {
    SmartWifiAppTheme {
        ManualEntryScreen(
            state = MainUiState(
                ssid = "",
                password = "1234567890",
                security = "WPA/WPA2-Personal",
            ),
            onBackClick = {},
            onSsidChange = {},
            onSecurityChange = {},
            onPasswordChange = {},
            onConnectAndSaveClick = {},
            onCancelClick = {},
        )
    }
}
