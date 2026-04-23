package com.example.smartwificonnect.data.local

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.smartwificonnect.R

enum class AppLanguage(
    val code: String,
    val localeTag: String,
    val titleRes: Int,
    val helperRes: Int,
) {
    VI(
        code = "vi",
        localeTag = "vi",
        titleRes = R.string.language_option_vi,
        helperRes = R.string.language_option_vi_subtitle,
    ),
    EN(
        code = "en",
        localeTag = "en",
        titleRes = R.string.language_option_en,
        helperRes = R.string.language_option_en_subtitle,
    ),
    ;

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.firstOrNull { it.code == code } ?: VI
        }
    }
}

data class AppPreferences(
    val userName: String,
    val email: String,
    val languageCode: String,
    val autoSavePasswords: Boolean,
)

class AppPreferencesManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadPreferences(): AppPreferences {
        return AppPreferences(
            userName = prefs.getString(KEY_USER_NAME, DEFAULT_USER_NAME).orEmpty(),
            email = prefs.getString(KEY_EMAIL, DEFAULT_EMAIL).orEmpty(),
            languageCode = prefs.getString(KEY_LANGUAGE_CODE, AppLanguage.VI.code).orEmpty(),
            autoSavePasswords = prefs.getBoolean(KEY_AUTO_SAVE_PASSWORDS, false),
        )
    }

    fun savePreferences(preferences: AppPreferences) {
        prefs.edit()
            .putString(KEY_USER_NAME, preferences.userName)
            .putString(KEY_EMAIL, preferences.email)
            .putString(KEY_LANGUAGE_CODE, preferences.languageCode)
            .putBoolean(KEY_AUTO_SAVE_PASSWORDS, preferences.autoSavePasswords)
            .apply()
    }

    fun applyLanguage(languageCode: String) {
        val language = AppLanguage.fromCode(languageCode)
        val locales = LocaleListCompat.forLanguageTags(language.localeTag)
        AppCompatDelegate.setApplicationLocales(locales)
    }

    companion object {
        private const val PREFS_NAME = "smart_wifi_preferences"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_EMAIL = "email"
        private const val KEY_LANGUAGE_CODE = "language_code"
        private const val KEY_AUTO_SAVE_PASSWORDS = "auto_save_passwords"

        private const val DEFAULT_USER_NAME = "Smart User"
        private const val DEFAULT_EMAIL = "smart.user@wifi-connect.app"
    }
}