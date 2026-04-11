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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartwificonnect.R
import com.example.smartwificonnect.ui.theme.SmartWifiAppTheme

// ── Design tokens ────────────────────────────────────────────────
private val BrandPrimary = Color(0xFF4B4FD9)
private val BrandPrimaryLight = Color(0xFF5B5FEF)
private val TextDark = Color(0xFF1D2029)
private val TextMedium = Color(0xFF595E6B)
private val TextLight = Color(0xFF656A78)
private val InputBg = Color(0xFFF2F4F7)
private val InputIconDefault = Color(0xFF8C91A0)
private val InputIconFocused = Color(0xFF4B4FD9)
private val ScreenBg = Color(0xFFF7F9FC)
private val DividerColor = Color(0xFFE0E3E6)
private val DividerTextColor = Color(0xFFA8A7BC)

// ── Main screen ──────────────────────────────────────────────────

@Composable
fun LoginScreen(
    state: LoginUiState,
    onLoginClick: () -> Unit,
    onForgotPasswordClick: () -> Unit = {},
    onSignUpClick: () -> Unit = {},
    onGoogleClick: () -> Unit = {},
    onDiscordClick: () -> Unit = {},
    onAppleClick: () -> Unit = {},
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg),
    ) {
        DecorativeBackground()

        Column(modifier = Modifier.fillMaxSize()) {
            LoginTopBar(
                title = state.screenTitle,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                BrandHeader(
                    title = state.brandTitle,
                    subtitle = state.brandSubtitle,
                )

                Spacer(modifier = Modifier.height(32.dp))

                LoginFormCard(
                    state = state,
                    email = email,
                    onEmailChange = { email = it },
                    password = password,
                    onPasswordChange = { password = it },
                    showPassword = showPassword,
                    onTogglePassword = { showPassword = !showPassword },
                    onForgotPasswordClick = onForgotPasswordClick,
                    onLoginClick = onLoginClick,
                    onGoogleClick = onGoogleClick,
                    onDiscordClick = onDiscordClick,
                    onAppleClick = onAppleClick,
                )

                Spacer(modifier = Modifier.height(32.dp))

                SignUpFooter(
                    prefix = state.noAccountPrefix,
                    action = state.signUpNow,
                    onClick = onSignUpClick,
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// ── Decorative background circles ────────────────────────────────

@Composable
private fun DecorativeBackground() {
    Box(modifier = Modifier.fillMaxSize()) {
        // Bottom-left teal circle
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-60).dp, y = 50.dp)
                .size(250.dp)
                .clip(CircleShape)
                .background(Color(0x1469FAD9)),
        )
    }
}

// ── Top bar with back arrow ──────────────────────────────────────

@Composable
private fun LoginTopBar(
    title: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = TextDark,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
        )
    }
}

// ── Brand header: WiFi icon + title + subtitle ───────────────────

@Composable
private fun BrandHeader(
    title: String,
    subtitle: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // WiFi icon in gradient rounded square
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(BrandPrimary, BrandPrimaryLight),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_splash_wifi),
                contentDescription = "WiFi icon",
                modifier = Modifier.size(36.dp),
                colorFilter = ColorFilter.tint(Color.White),
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = title,
            color = TextDark,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = subtitle,
            color = TextLight,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Form card ────────────────────────────────────────────────────

@Composable
private fun LoginFormCard(
    state: LoginUiState,
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    showPassword: Boolean,
    onTogglePassword: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onLoginClick: () -> Unit,
    onGoogleClick: () -> Unit,
    onDiscordClick: () -> Unit,
    onAppleClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .widthIn(max = 460.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 8.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp)) {
            // ── Email ──
            Text(
                text = state.emailLabel,
                color = TextDark,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(10.dp))
            IconTextField(
                value = email,
                onValueChange = onEmailChange,
                placeholder = state.emailPlaceholder,
                leadingIconRes = R.drawable.ic_login_mail,
                keyboardType = KeyboardType.Email,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── Password label row ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.passwordLabel,
                    color = TextDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                Text(
                    text = state.forgotPassword,
                    color = BrandPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable(onClick = onForgotPasswordClick),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            IconTextField(
                value = password,
                onValueChange = onPasswordChange,
                placeholder = state.passwordPlaceholder,
                leadingIconRes = R.drawable.ic_login_lock,
                trailingIconRes = R.drawable.ic_login_visibility,
                onTrailingClick = onTogglePassword,
                keyboardType = KeyboardType.Password,
                visualTransformation = if (showPassword) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ── Login button with dashed border ──
            LoginButton(
                text = state.loginButton,
                onClick = onLoginClick,
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ── Social divider ──
            SocialDivider(text = state.socialDivider)

            Spacer(modifier = Modifier.height(18.dp))

            // ── Social buttons ──
            SocialButtonRow(
                onGoogleClick = onGoogleClick,
                onDiscordClick = onDiscordClick,
                onAppleClick = onAppleClick,
            )
        }
    }
}

// ── Input field with leading/trailing icons ──────────────────────

@Composable
private fun IconTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIconRes: Int,
    keyboardType: KeyboardType,
    trailingIconRes: Int? = null,
    onTrailingClick: () -> Unit = {},
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val bgColor = if (isFocused) Color.White else InputBg
    val borderMod = if (isFocused) {
        Modifier.border(1.5.dp, Color(0xFFD0D1E8), RoundedCornerShape(14.dp))
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(borderMod)
            .background(bgColor)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Leading icon
        Image(
            painter = painterResource(id = leadingIconRes),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            colorFilter = ColorFilter.tint(
                if (isFocused) InputIconFocused else InputIconDefault,
            ),
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Text field
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                color = TextDark,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = visualTransformation,
            interactionSource = interactionSource,
            singleLine = true,
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = Color(0xFF9CA0AE),
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp,
                    )
                }
                innerTextField()
            },
        )

        // Trailing icon
        if (trailingIconRes != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onTrailingClick),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = trailingIconRes),
                    contentDescription = "Toggle visibility",
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(InputIconDefault),
                )
            }
        }
    }
}

// ── Login button ─────────────────────────────────────────────────

@Composable
private fun LoginButton(
    text: String,
    onClick: () -> Unit,
) {
    // Outer box with dashed-style border effect
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 2.dp,
                color = Color(0x4D5B5FEF),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(4.dp),
    ) {
        // Inner gradient button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(BrandPrimary, BrandPrimaryLight),
                    ),
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }
    }
}

// ── Social divider ───────────────────────────────────────────────

@Composable
private fun SocialDivider(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = DividerColor,
            thickness = 1.dp,
        )
        Text(
            text = text.uppercase(),
            color = DividerTextColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = DividerColor,
            thickness = 1.dp,
        )
    }
}

// ── Social button row ────────────────────────────────────────────

@Composable
private fun SocialButtonRow(
    onGoogleClick: () -> Unit,
    onDiscordClick: () -> Unit,
    onAppleClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Google
        SocialChip(
            modifier = Modifier.weight(1f),
            onClick = onGoogleClick,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_social_google),
                contentDescription = "Google",
                modifier = Modifier.size(24.dp),
            )
        }

        // Discord
        SocialChip(
            modifier = Modifier.weight(1f),
            onClick = onDiscordClick,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
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
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // Apple / iOS
        SocialChip(
            modifier = Modifier.weight(1f),
            onClick = onAppleClick,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_social_apple_black),
                contentDescription = "Apple",
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun SocialChip(
    modifier: Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(InputBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// ── Sign-up footer ───────────────────────────────────────────────

@Composable
private fun SignUpFooter(
    prefix: String,
    action: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = prefix,
            color = TextMedium,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = action,
            color = BrandPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.clickable(onClick = onClick),
        )
    }
}

// ── Preview ──────────────────────────────────────────────────────

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun LoginScreenPreview() {
    SmartWifiAppTheme {
        LoginScreen(
            state = LoginPreviewData.default,
            onLoginClick = {},
        )
    }
}
