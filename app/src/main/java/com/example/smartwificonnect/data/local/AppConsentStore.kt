package com.example.smartwificonnect.data.local

import android.content.Context

object AppConsentStore {
    private const val PREFS_NAME = "smartwifi_app_prefs"
    private const val KEY_POLICY_ACCEPTED = "policy_accepted"

    fun hasAccepted(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_POLICY_ACCEPTED, false)

    fun markAccepted(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_POLICY_ACCEPTED, true)
            .apply()
    }
}
