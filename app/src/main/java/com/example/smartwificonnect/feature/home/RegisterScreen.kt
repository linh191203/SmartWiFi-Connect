package com.example.smartwificonnect.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartwificonnect.R
import com.example.smartwificonnect.ui.theme.LocalAppDarkMode
import com.example.smartwificonnect.ui.theme.SmartWifiAppTheme

private val RegisterPrimary: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF8D90FF) else Color(0xFF4B4FD9)
private val RegisterPrimaryLight: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF7A7CFF) else Color(0xFF5B5FEF)
private val RegisterTextDark: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFFF4F6FB) else Color(0xFF1D2029)
private val RegisterTextMid: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFFABB2C1) else Color(0xFF595E6B)
private val RegisterTextLight: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF9AA3B2) else Color(0xFF656A78)
private val RegisterInputBg: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF2B3240) else Color(0xFFEDEFF4)
private val RegisterInputIcon: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF8E98AA) else Color(0xFF8C91A0)
private val RegisterInputIconFocused: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF8D90FF) else Color(0xFF4B4FD9)
private val RegisterScreenBg: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF10131B) else Color(0xFFF4F6FC)
private val RegisterDivider: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF2C3342) else Color(0xFFDDE1EA)
private val RegisterDividerText: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF717B8D) else Color(0xFFA8A7BC)

@Composable
fun RegisterScreen(
    state: RegisterUiState,
    onBackClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onLoginNowClick: () -> Unit,
    onGoogleClick: () -> Unit = {},
    onDiscordClick: () -> Unit = {},
    onAppleClick: () -> Unit = {},
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var passwordTouched by remember { mutableStateOf(false) }
    var confirmTouched by remember { mutableStateOf(false) }

    val passwordError: String? = when {
        !passwordTouched -> null
        password.length < 8 -> "Mật khẩu phải có ít nhất 8 ký tự"
        !password.any { it.isDigit() } -> "Mật khẩu phải chứa ít nhất 1 chữ số"
        !password.any { !it.isLetterOrDigit() } -> "Mật khẩu phải chứa ít nhất 1 ký tự đặc biệt"
        else -> null
    }
    val confirmError: String? = when {
        !confirmTouched -> null
        confirmPassword != password -> "Mật khẩu xác nhận không khớp"
        else -> null
    }
    val isFormValid = passwordError == null && confirmError == null
        && password.isNotEmpty() && confirmPassword.isNotEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(RegisterScreenBg),
    ) {
        RegisterDecorativeBackground()

        Column(modifier = Modifier.fillMaxSize()) {
            RegisterTopBar(
                title = state.screenTitle,
                onBackClick = onBackClick,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RegisterBrandHeader(
                    title = state.brandTitle,
                    subtitle = state.brandSubtitle,
                )

                Spacer(modifier = Modifier.height(26.dp))

                RegisterFieldBlock(
                    label = state.fullNameLabel,
                    value = fullName,
                    onValueChange = { fullName = it },
                    placeholder = state.fullNamePlaceholder,
                    leadingIconRes = R.drawable.ic_login_person,
                    keyboardType = KeyboardType.Text,
                )

                Spacer(modifier = Modifier.height(14.dp))

                RegisterFieldBlock(
                    label = state.emailLabel,
                    value = email,
                    onValueChange = { email = it },
                    placeholder = state.emailPlaceholder,
                    leadingIconRes = R.drawable.ic_login_mail,
                    keyboardType = KeyboardType.Email,
                )

                Spacer(modifier = Modifier.height(14.dp))

                RegisterFieldBlock(
                    label = state.passwordLabel,
                    value = password,
                    onValueChange = {
                        password = it
                        passwordTouched = true
                    },
                    placeholder = state.passwordPlaceholder,
                    leadingIconRes = R.drawable.ic_login_lock,
                    trailingIconRes = R.drawable.ic_login_visibility,
                    onTrailingClick = { showPassword = !showPassword },
                    keyboardType = KeyboardType.Password,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    errorMessage = passwordError,
                )

                Spacer(modifier = Modifier.height(14.dp))

                RegisterFieldBlock(
                    label = state.confirmPasswordLabel,
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        confirmTouched = true
                    },
                    placeholder = state.confirmPasswordPlaceholder,
                    leadingIconRes = R.drawable.ic_login_lock_reset,
                    trailingIconRes = R.drawable.ic_login_visibility,
                    onTrailingClick = { showConfirmPassword = !showConfirmPassword },
                    keyboardType = KeyboardType.Password,
                    visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    errorMessage = confirmError,
                )

                Spacer(modifier = Modifier.height(24.dp))

                RegisterPrimaryButton(
                    text = state.registerButton,
                    enabled = isFormValid,
                    onClick = { if (isFormValid) onRegisterClick() },
                )

                Spacer(modifier = Modifier.height(26.dp))

                RegisterSocialDivider(text = state.socialDivider)

                Spacer(modifier = Modifier.height(16.dp))

                RegisterSocialRow(
                    onGoogleClick = onGoogleClick,
                    onDiscordClick = onDiscordClick,
                    onAppleClick = onAppleClick,
                )

                Spacer(modifier = Modifier.height(26.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = state.hasAccountPrefix,
                        color = RegisterTextMid,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = state.loginNow,
                        color = RegisterPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable(onClick = onLoginNowClick),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun RegisterDecorativeBackground() {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 60.dp, y = (-30).dp)
                .size(230.dp)
                .clip(CircleShape)
                .background(Color(0x15C0C1FF)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-64).dp, y = 56.dp)
                .size(250.dp)
                .clip(CircleShape)
                .background(Color(0x1369FAD9)),
        )
    }
}

@Composable
private fun RegisterTopBar(
    title: String,
    onBackClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable(onClick = onBackClick),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_login_arrow_back),
                contentDescription = "Quay lại",
                modifier = Modifier.size(18.dp),
                colorFilter = ColorFilter.tint(RegisterPrimary),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            color = RegisterPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp / 2,
        )
    }
}

@Composable
private fun RegisterBrandHeader(
    title: String,
    subtitle: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(RegisterPrimary, RegisterPrimaryLight),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_splash_wifi),
                contentDescription = "SmartWiFi logo",
                modifier = Modifier.size(38.dp),
                colorFilter = ColorFilter.tint(Color.White),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            color = RegisterTextDark,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            color = RegisterTextLight,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RegisterFieldBlock(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIconRes: Int,
    keyboardType: KeyboardType,
    trailingIconRes: Int? = null,
    onTrailingClick: () -> Unit = {},
    visualTransformation: VisualTransformation = VisualTransformation.None,
    errorMessage: String? = null,
) {
    Text(
        text = label,
        color = RegisterTextMid,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp),
    )
    Spacer(modifier = Modifier.height(6.dp))
    RegisterInputField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        leadingIconRes = leadingIconRes,
        keyboardType = keyboardType,
        trailingIconRes = trailingIconRes,
        onTrailingClick = onTrailingClick,
        visualTransformation = visualTransformation,
        hasError = errorMessage != null,
    )
    if (errorMessage != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = errorMessage,
            color = Color(0xFFD93025),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp),
        )
    }
}

@Composable
private fun RegisterInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIconRes: Int,
    keyboardType: KeyboardType,
    trailingIconRes: Int? = null,
    onTrailingClick: () -> Unit = {},
    visualTransformation: VisualTransformation = VisualTransformation.None,
    hasError: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFE5E7EC))
            .then(
                when {
                    hasError -> Modifier.border(1.4.dp, Color(0xFFD93025), RoundedCornerShape(14.dp))
                    isFocused -> Modifier.border(1.4.dp, Color(0x99A9B0C3), RoundedCornerShape(14.dp))
                    else -> Modifier
                },
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(id = leadingIconRes),
            contentDescription = null,
            modifier = Modifier.size(19.dp),
            colorFilter = ColorFilter.tint(if (isFocused) RegisterInputIconFocused else RegisterInputIcon),
        )
        Spacer(modifier = Modifier.width(10.dp))

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            interactionSource = interactionSource,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = visualTransformation,
            textStyle = TextStyle(
                color = RegisterTextDark,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = Color(0xFF9EA3AF),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                }
                innerTextField()
            },
        )

        if (trailingIconRes != null) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onTrailingClick),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = trailingIconRes),
                    contentDescription = "Hiện / ẩn mật khẩu",
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(RegisterInputIcon),
                )
            }
        }
    }
}

@Composable
private fun RegisterPrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val alpha = if (enabled) 1f else 0.45f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        RegisterPrimary.copy(alpha = alpha),
                        RegisterPrimaryLight.copy(alpha = alpha),
                    ),
                ),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = alpha),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
        )
    }
}

@Composable
private fun RegisterSocialDivider(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = RegisterDivider,
            thickness = 1.dp,
        )
        Text(
            text = text.uppercase(),
            color = RegisterDividerText,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = RegisterDivider,
            thickness = 1.dp,
        )
    }
}

@Composable
private fun RegisterSocialRow(
    onGoogleClick: () -> Unit,
    onDiscordClick: () -> Unit,
    onAppleClick: () -> Unit,
) {
    Row(
        modifier = Modifier.widthIn(max = 260.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RegisterSocialChip(onClick = onGoogleClick) {
            Image(
                painter = painterResource(id = R.drawable.ic_social_google),
                contentDescription = "Google",
                modifier = Modifier.size(20.dp),
            )
        }
        RegisterSocialChip(onClick = onDiscordClick) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF6678FF), Color(0xFF4F5DE9)),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_social_discord_mark),
                    contentDescription = "Discord",
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        RegisterSocialChip(onClick = onAppleClick) {
            Image(
                painter = painterResource(id = R.drawable.ic_social_apple_black),
                contentDescription = "Apple",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun RegisterSocialChip(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 76.dp, height = 46.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xFFECEEF4))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun RegisterScreenPreview() {
    SmartWifiAppTheme {
        RegisterScreen(
            state = RegisterPreviewData.default,
            onBackClick = {},
            onRegisterClick = {},
            onLoginNowClick = {},
        )
    }
}
