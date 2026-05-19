package com.smartwificonnect.feature.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartwificonnect.data.local.PolicyConsentManager
import com.smartwificonnect.ui.theme.LocalAppDarkMode
import com.smartwificonnect.ui.theme.SmartWifiAppTheme

@Composable
fun PolicyConsentScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val dark = LocalAppDarkMode.current
    val screenBg = if (dark) Color(0xFF10131B) else Color(0xFFF7F9FC)
    val cardBg = if (dark) Color(0xFF1F2430) else Color.White
    val brand = if (dark) Color(0xFF8D90FF) else Color(0xFF4A4FD3)
    val textPrimary = if (dark) Color(0xFFF4F6FB) else Color(0xFF161922)
    val textMuted = if (dark) Color(0xFFABB2C1) else Color(0xFF6D7180)
    val sectionBg = if (dark) Color(0xFF262C3A) else Color(0xFFF0F4FF)
    val borderColor = if (dark) Color(0xFF2C3342) else Color(0xFFE8ECF3)
    val uriHandler = LocalUriHandler.current

    var privacyChecked by rememberSaveable { mutableStateOf(false) }
    val canProceed = privacyChecked

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(screenBg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Header icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(brand.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = brand,
                    modifier = Modifier.size(40.dp),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Chính sách & Quyền riêng tư",
                color = textPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Vui lòng đọc và đồng ý với các điều khoản trước khi sử dụng SmartWiFi-Connect.",
                color = textMuted,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Permissions info card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = cardBg,
                tonalElevation = 0.dp,
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Ứng dụng sẽ sử dụng",
                        color = textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PermissionItem(
                        icon = Icons.Outlined.CameraAlt,
                        iconTint = brand,
                        title = "Camera",
                        description = "Quét mã QR và chụp ảnh để nhận diện thông tin Wi-Fi bằng OCR.",
                        sectionBg = sectionBg,
                        textPrimary = textPrimary,
                        textMuted = textMuted,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PermissionItem(
                        icon = Icons.Outlined.Wifi,
                        iconTint = Color(0xFF2FA8A0),
                        title = "Vị trí & Thiết bị lân cận",
                        description = "Android có thể yêu cầu vị trí gần đúng/chính xác và Nearby Wi-Fi để quét mạng xung quanh. Ứng dụng không lưu vị trí của bạn.",
                        sectionBg = sectionBg,
                        textPrimary = textPrimary,
                        textMuted = textMuted,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PermissionItem(
                        icon = Icons.Outlined.Check,
                        iconTint = Color(0xFF047B62),
                        title = "Dữ liệu Wi-Fi",
                        description = "SSID, mật khẩu và nội dung OCR chỉ dùng cho thao tác kết nối/chia sẻ/lưu lịch sử do bạn thực hiện. Ứng dụng không tự thử mật khẩu với mạng bạn chưa chọn.",
                        sectionBg = sectionBg,
                        textPrimary = textPrimary,
                        textMuted = textMuted,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Privacy policy checkbox
            ConsentCheckboxRow(
                checked = privacyChecked,
                onCheckedChange = { privacyChecked = it },
                cardBg = cardBg,
                borderColor = if (privacyChecked) brand else borderColor,
                brand = brand,
                textPrimary = textPrimary,
                textMuted = textMuted,
                label = "Tôi đã đọc và đồng ý với",
                linkText = "Chính sách Bảo mật",
                onLinkClick = { uriHandler.openUri(PolicyConsentManager.PRIVACY_POLICY_URL) },
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Accept button
            Button(
                onClick = onAccept,
                enabled = canProceed,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = brand,
                    disabledContainerColor = brand.copy(alpha = 0.35f),
                ),
            ) {
                Text(
                    text = "Đồng ý & Tiếp tục",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Decline button
            TextButton(
                onClick = onDecline,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Từ chối và thoát ứng dụng",
                    color = textMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PermissionItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    description: String,
    sectionBg: Color,
    textPrimary: Color,
    textMuted: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(sectionBg)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                color = textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = textMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
private fun ConsentCheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    cardBg: Color,
    borderColor: Color,
    brand: Color,
    textPrimary: Color,
    textMuted: Color,
    label: String,
    linkText: String,
    onLinkClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = brand,
                uncheckedColor = textMuted,
            ),
        )
        val annotatedText = buildAnnotatedString {
            withStyle(SpanStyle(color = textPrimary, fontSize = 14.sp)) {
                append("$label ")
            }
            withStyle(
                SpanStyle(
                    color = brand,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                ),
            ) {
                append(linkText)
            }
        }
        Text(
            text = annotatedText,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onLinkClick),
        )
        Icon(
            imageVector = Icons.Outlined.OpenInBrowser,
            contentDescription = "Mở chính sách",
            tint = brand.copy(alpha = 0.7f),
            modifier = Modifier
                .size(16.dp)
                .clickable(onClick = onLinkClick),
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PolicyConsentScreenPreview() {
    SmartWifiAppTheme {
        PolicyConsentScreen(
            onAccept = {},
            onDecline = {},
        )
    }
}
