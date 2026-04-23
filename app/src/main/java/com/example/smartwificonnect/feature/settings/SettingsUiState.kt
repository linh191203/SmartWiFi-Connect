package com.example.smartwificonnect.feature.settings

import com.example.smartwificonnect.BuildConfig
import com.example.smartwificonnect.data.local.AppLanguage

data class SettingsUiState(
    val userName: String = "Smart User",
    val email: String = "smart.user@wifi-connect.app",
    val selectedLanguageCode: String = AppLanguage.VI.code,
    val autoSavePasswords: Boolean = false,
    val savedNetworksCount: String = "0",
    val latestSavedNetworkName: String = "Chưa có",
    val appVersion: String = BuildConfig.VERSION_NAME,
)

data class LanguageOptionUiModel(
    val code: String,
    val titleRes: Int,
    val subtitleRes: Int,
)