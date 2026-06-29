package com.fogwalk.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Thin wrapper around [SharedPreferences] for persistent app preferences and
 * lightweight runtime state.
 *
 * - [autoFollowEnabled] is the user's "always-on" preference. When enabled the
 *   service is (re)started on boot and kept running.
 * - [trackingActive] is a flag the service maintains so the UI can reflect the
 *   real tracking state even after the app process was killed and recreated.
 */
class Settings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var autoFollowEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_FOLLOW, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_FOLLOW, value).apply()

    var trackingActive: Boolean
        get() = prefs.getBoolean(KEY_TRACKING_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_TRACKING_ACTIVE, value).apply()

    companion object {
        private const val PREFS_NAME = "fogwalk_settings"
        private const val KEY_AUTO_FOLLOW = "auto_follow_enabled"
        private const val KEY_TRACKING_ACTIVE = "tracking_active"
    }
}
