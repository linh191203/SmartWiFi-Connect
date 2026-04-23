package com.example.smartwificonnect.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.smartwificonnect.R
import com.example.smartwificonnect.ui.theme.SmartWifiAppTheme
import androidx.compose.ui.res.stringResource

private val SettingsTop = Color(0xFFF7FAFF)
private val SettingsBottom = Color(0xFFEAF1FF)
private val SettingsCard = Color(0xFFFFFFFF)
private val SettingsPrimary = Color(0xFF2E5BFF)
private val SettingsPrimarySoft = Color(0xFFDDE6FF)
private val SettingsText = Color(0xFF1D2430)
private val SettingsMuted = Color(0xFF667085)
private val LogoutColor = Color(0xFFD92D20)

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    languageOptions: List<LanguageOptionUiModel>,
    onBackClick: () -> Unit,
    onUserNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onAutoSavePasswordsChange: (Boolean) -> Unit,
    onSaveClick: () -> Unit,
    onLogoutClick: () -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Surface(color = Color.White.copy(alpha = 0.94f), shadowElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                            tint = SettingsPrimary,
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = SettingsText,
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(listOf(SettingsTop, SettingsBottom)),
                ),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    end = 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    SettingsHero(
                        userName = state.userName,
                        email = state.email,
                    )
                }

                item {
                    SettingsSectionCard(
                        icon = Icons.Outlined.PersonOutline,
                        title = stringResource(R.string.settings_user_section),
                        subtitle = stringResource(R.string.settings_user_section_subtitle),
                    ) {
                        OutlinedTextField(
                            value = state.userName,
                            onValueChange = onUserNameChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.settings_user_name_label)) },
                            singleLine = true,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = state.email,
                            onValueChange = onEmailChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.settings_user_email_label)) },
                            singleLine = true,
                        )
                    }
                }

                item {
                    SettingsSectionCard(
                        icon = Icons.Outlined.Language,
                        title = stringResource(R.string.settings_language_section),
                        subtitle = stringResource(R.string.settings_language_section_subtitle),
                    ) {
                        languageOptions.forEach { option ->
                            LanguageOptionRow(
                                option = option,
                                selected = state.selectedLanguageCode == option.code,
                                onClick = { onLanguageChange(option.code) },
                            )
                        }
                    }
                }

                item {
                    SettingsSectionCard(
                        icon = Icons.Outlined.Security,
                        title = stringResource(R.string.settings_connection_section),
                        subtitle = stringResource(R.string.settings_connection_section_subtitle),
                    ) {
                        SettingSwitchRow(
                            title = stringResource(R.string.settings_auto_save_passwords_label),
                            subtitle = stringResource(R.string.settings_auto_save_passwords_subtitle),
                            checked = state.autoSavePasswords,
                            onCheckedChange = onAutoSavePasswordsChange,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        SavedNetworksSummary(
                            count = state.savedNetworksCount,
                            latestSsid = state.latestSavedNetworkName,
                        )
                    }
                }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onSaveClick,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.settings_save))
                        }
                        OutlinedButton(
                            onClick = onLogoutClick,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Logout,
                                contentDescription = null,
                                tint = LogoutColor,
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.settings_logout),
                                color = LogoutColor,
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = stringResource(R.string.settings_version, state.appVersion),
                        style = MaterialTheme.typography.bodySmall,
                        color = SettingsMuted,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsHero(
    userName: String,
    email: String,
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF2E5BFF), Color(0xFF5D88FF)),
                    ),
                )
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(SettingsPrimarySoft, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = userName.trim().take(1).ifBlank { "S" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = SettingsPrimary,
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = userName.ifBlank { stringResource(R.string.settings_default_user_name) },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                    )
                    Text(
                        text = email.ifBlank { stringResource(R.string.settings_default_user_email) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFDCE7FF),
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.settings_hero_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFE7EEFF),
            )
        }
    }
}

@Composable
private fun SettingsSectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SettingsCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFFE8EEFF), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = SettingsPrimary)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = SettingsText,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = SettingsMuted,
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun LanguageOptionRow(
    option: LanguageOptionUiModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(option.titleRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = SettingsText,
            )
            Text(
                text = stringResource(option.subtitleRes),
                style = MaterialTheme.typography.bodySmall,
                color = SettingsMuted,
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = SettingsText,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SettingsMuted,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SavedNetworksSummary(
    count: String,
    latestSsid: String,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFF5F8FF),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.settings_saved_networks_count),
                    style = MaterialTheme.typography.labelLarge,
                    color = SettingsMuted,
                )
                Text(
                    text = count,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = SettingsPrimary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.settings_latest_network),
                    style = MaterialTheme.typography.labelLarge,
                    color = SettingsMuted,
                )
                Text(
                    text = latestSsid,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SettingsText,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    SmartWifiAppTheme {
        SettingsScreen(
            state = SettingsUiState(
                userName = "Nguyen Duc",
                email = "duc@smartwifi.app",
                selectedLanguageCode = "vi",
                autoSavePasswords = true,
                savedNetworksCount = "8",
                latestSavedNetworkName = "Home_5G",
            ),
            languageOptions = listOf(
                LanguageOptionUiModel("vi", R.string.language_option_vi, R.string.language_option_vi_subtitle),
                LanguageOptionUiModel("en", R.string.language_option_en, R.string.language_option_en_subtitle),
            ),
            onBackClick = {},
            onUserNameChange = {},
            onEmailChange = {},
            onLanguageChange = {},
            onAutoSavePasswordsChange = {},
            onSaveClick = {},
            onLogoutClick = {},
        )
    }
}