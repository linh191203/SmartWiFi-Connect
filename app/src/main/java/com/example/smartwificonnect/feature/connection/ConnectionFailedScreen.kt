package com.example.smartwificonnect.feature.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smartwificonnect.R
import com.example.smartwificonnect.ui.theme.LocalAppDarkMode
import com.example.smartwificonnect.ui.theme.SmartWifiAppTheme

private val FailBackground: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF10131B) else Color(0xFFF7F9FC)
private val FailBrand: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF8D90FF) else Color(0xFF4143D5)
private val FailTitle: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFFF4F6FB) else Color(0xFF1C2029)
private val FailSubtitle: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFFABB2C1) else Color(0xFF505563)
private val FailHeroRing: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF332631) else Color(0xFFF1EDF2)
private val FailHeroInner: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF4B2327) else Color(0xFFEFDCE1)
private val FailDanger: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFFFF6D68) else Color(0xFFC81F1F)
private val FailTipCard: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF1F2430) else Color(0xFFFFFFFF)
private val FailTipIconBg: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF30337D) else Color(0xFFE8E9FA)
private val FailTipArrow: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xFF7D8595) else Color(0xFFC5C9D6)
private val FailBottomBar: Color
    @Composable get() = if (LocalAppDarkMode.current) Color(0xF21A1F2B) else Color(0xFFFFFFFF)

@Composable
fun ConnectionFailedScreen(
    isRetrying: Boolean,
    onCloseClick: () -> Unit,
    onRetryClick: () -> Unit,
    onNetworkSettingsClick: () -> Unit,
    onHomeClick: () -> Unit,
    onScanClick: () -> Unit,
    onShareClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Scaffold(
        containerColor = FailBackground,
        bottomBar = {
            FailureBottomBar(
                onHomeClick = onHomeClick,
                onScanClick = onScanClick,
                onShareClick = onShareClick,
                onHistoryClick = onHistoryClick,
                onSettingsClick = onSettingsClick,
            )
        },
    ) { innerPadding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCloseClick) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.connection_failed_cd_close),
                        tint = Color(0xFF566074),
                        modifier = Modifier.size(30.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.connection_failed_brand_title),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = FailBrand,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.width(48.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))
            ErrorIllustration()
            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = stringResource(R.string.connection_failed_title),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = FailTitle,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.connection_failed_subtitle),
                modifier = Modifier.fillMaxWidth(0.94f),
                style = MaterialTheme.typography.bodyLarge,
                color = FailSubtitle,
                textAlign = TextAlign.Center,
                lineHeight = 30.sp,
            )

            Spacer(modifier = Modifier.height(24.dp))

            ConnectionHelpCard(
                icon = Icons.Rounded.Router,
                title = stringResource(R.string.connection_failed_tip_router_title),
                description = stringResource(R.string.connection_failed_tip_router_body),
            )

            Spacer(modifier = Modifier.height(14.dp))

            ConnectionHelpCard(
                icon = Icons.Rounded.Wifi,
                title = stringResource(R.string.connection_failed_tip_signal_title),
                description = stringResource(R.string.connection_failed_tip_signal_body),
            )

            Spacer(modifier = Modifier.height(26.dp))

            Button(
                onClick = onRetryClick,
                enabled = !isRetrying,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(66.dp)
                    .shadow(
                        elevation = 18.dp,
                        shape = RoundedCornerShape(999.dp),
                        ambientColor = FailBrand.copy(alpha = 0.3f),
                        spotColor = FailBrand.copy(alpha = 0.3f),
                    ),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FailBrand,
                    disabledContainerColor = FailBrand.copy(alpha = 0.42f),
                ),
            ) {
                Text(
                    text = if (isRetrying) {
                        stringResource(R.string.connection_failed_retrying)
                    } else {
                        stringResource(R.string.connection_failed_retry)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            Text(
                text = stringResource(R.string.connection_failed_open_settings),
                modifier = Modifier
                    .padding(top = 18.dp, bottom = 16.dp)
                    .clickable(onClick = onNetworkSettingsClick),
                style = MaterialTheme.typography.headlineSmall,
                color = FailBrand,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun ErrorIllustration() {
    Column(
        modifier = Modifier
            .width(160.dp)
            .height(192.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .background(FailHeroRing, CircleShape),
            )
            Box(
                modifier = Modifier
                    .size(122.dp)
                    .background(FailHeroInner, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.WifiOff,
                    contentDescription = null,
                    tint = FailDanger,
                    modifier = Modifier.size(66.dp),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = 6.dp)
                    .size(46.dp)
                    .shadow(4.dp, CircleShape)
                    .background(FailDanger, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ConnectionHelpCard(
    icon: ImageVector,
    title: String,
    description: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = FailTipCard,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(FailTipIconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = FailBrand,
                    modifier = Modifier.size(28.dp),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = FailTitle,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FailSubtitle,
                    lineHeight = 22.sp,
                )
            }

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = FailTipArrow,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun FailureBottomBar(
    onHomeClick: () -> Unit,
    onScanClick: () -> Unit,
    onShareClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Surface(
        color = FailBottomBar,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = 22.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FailureNavItem(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.connection_failed_tab_home),
                icon = Icons.Outlined.Home,
                selected = false,
                onClick = onHomeClick,
            )
            FailureNavItem(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.connection_failed_tab_scan),
                icon = Icons.Outlined.QrCode2,
                selected = true,
                onClick = onScanClick,
            )
            FailureNavItem(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.connection_failed_tab_share),
                icon = Icons.Outlined.IosShare,
                selected = false,
                onClick = onShareClick,
            )
            FailureNavItem(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.connection_failed_tab_history),
                icon = Icons.Outlined.History,
                selected = false,
                onClick = onHistoryClick,
            )
            FailureNavItem(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.connection_failed_tab_settings),
                icon = Icons.Outlined.Settings,
                selected = false,
                onClick = onSettingsClick,
            )
        }
    }
}

@Composable
private fun FailureNavItem(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (selected) FailBrand else Color.Transparent
    val contentColor = if (selected) Color.White else Color(0xFF11141B)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onClick)
                .background(containerColor)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 893)
@Composable
private fun ConnectionFailedScreenPreview() {
    SmartWifiAppTheme {
        ConnectionFailedScreen(
            isRetrying = false,
            onCloseClick = {},
            onRetryClick = {},
            onNetworkSettingsClick = {},
            onHomeClick = {},
            onScanClick = {},
            onShareClick = {},
            onHistoryClick = {},
            onSettingsClick = {},
        )
    }
}
