package com.obdvis.android

import android.content.Context
import com.obdvis.android.domain.health.FindingSeverity

class AppSettings(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("obdvis_settings", Context.MODE_PRIVATE)

    var backgroundCheckIntervalMs: Long
        get() = prefs.getLong("bg_check_interval_ms", DEFAULT_BG_INTERVAL_MS)
        set(v) { prefs.edit().putLong("bg_check_interval_ms", v).apply() }

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean("notifications_enabled", false)
        set(v) { prefs.edit().putBoolean("notifications_enabled", v).apply() }

    var notificationMinSeverity: FindingSeverity
        get() = FindingSeverity.valueOf(
            prefs.getString("notification_min_severity", FindingSeverity.MEDIUM.name)!!
        )
        set(v) { prefs.edit().putString("notification_min_severity", v.name).apply() }

    companion object {
        const val DEFAULT_BG_INTERVAL_MS = 120_000L // 2 minutes
    }
}
