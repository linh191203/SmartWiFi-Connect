package com.smartwificonnect.ui

import android.content.Context
import android.content.res.Configuration

/**
 * Clamps the system [Configuration.fontScale] to a safe range so the app
 * always renders close to its design, regardless of user-side font size.
 *
 * Why this exists:
 *   On Android, users can set "Font size" up to 1.30x and "Display size" up to
 *   1.50x in Accessibility settings. Combined, text and icons can be 90%+ larger
 *   than design — breaking layouts, overflowing buttons, truncating tab labels
 *   ("Trang chủ" → "Tran..."). Many devices ship with these set high by default.
 *
 * Strategy:
 *   We respect a small accessibility lift (up to [MAX_FONT_SCALE]) so users with
 *   visual needs still get slightly larger text, but cap it before layouts break.
 *   Applied via [android.app.Activity.attachBaseContext] so every screen
 *   inherits the clamp.
 *
 * Usage in an Activity:
 *
 *     override fun attachBaseContext(newBase: Context) {
 *         super.attachBaseContext(DisplayScaleGuard.wrap(newBase))
 *     }
 */
object DisplayScaleGuard {

    /** Hard upper bound for font scale. 1.15 = 15% larger than design. */
    const val MAX_FONT_SCALE = 1.15f

    fun wrap(base: Context): Context {
        val cfg = base.resources.configuration
        if (cfg.fontScale <= MAX_FONT_SCALE) return base

        val clamped = Configuration(cfg).apply { fontScale = MAX_FONT_SCALE }
        return base.createConfigurationContext(clamped)
    }
}
