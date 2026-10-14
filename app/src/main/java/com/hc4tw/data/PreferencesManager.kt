package com.hc4tw.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var trendWeightApiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    var isBackgroundSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_BACKGROUND_SYNC, false)
        set(value) = prefs.edit().putBoolean(KEY_BACKGROUND_SYNC, value).apply()

    var lastSyncTimestamp: Long
        get() = prefs.getLong(KEY_LAST_SYNC_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC_TIME, value).apply()

    var lastSyncStatus: String
        get() = prefs.getString(KEY_LAST_SYNC_STATUS, "IDLE") ?: "IDLE"
        set(value) = prefs.edit().putString(KEY_LAST_SYNC_STATUS, value).apply()

    var lastSyncMessage: String
        get() = prefs.getString(KEY_LAST_SYNC_MESSAGE, "Never synced") ?: "Never synced"
        set(value) = prefs.edit().putString(KEY_LAST_SYNC_MESSAGE, value).apply()

    var lastSyncCount: Int
        get() = prefs.getInt(KEY_LAST_SYNC_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_LAST_SYNC_COUNT, value).apply()

    var lastWatermark: Long
        get() = prefs.getLong(KEY_LAST_WATERMARK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_WATERMARK, value).apply()

    var syncIntervalMinutes: Long
        get() = prefs.getLong(KEY_SYNC_INTERVAL_MINUTES, 60L)
        set(value) = prefs.edit().putLong(KEY_SYNC_INTERVAL_MINUTES, value).apply()

    var syncNowDays: Int
        get() = prefs.getInt(KEY_SYNC_NOW_DAYS, 7)
        set(value) = prefs.edit().putInt(KEY_SYNC_NOW_DAYS, value.coerceAtLeast(1)).apply()

    var forceFullDays: Int
        get() = prefs.getInt(KEY_FORCE_FULL_DAYS, 7)
        set(value) = prefs.edit().putInt(KEY_FORCE_FULL_DAYS, value.coerceAtLeast(1)).apply()

    fun getLastSyncedValue(dateStr: String): String? {
        return prefs.getString("synced_val_$dateStr", null)
    }

    fun setLastSyncedValue(dateStr: String, valueStr: String) {
        prefs.edit().putString("synced_val_$dateStr", valueStr).apply()
    }

    fun hasApiKey(): Boolean = trendWeightApiKey.isNotBlank()

    fun getMaskedApiKey(): String {
        val key = trendWeightApiKey
        if (key.length <= 6) {
            return if (key.isEmpty()) "Not Set" else "******"
        }
        val prefix = key.take(3)
        val suffix = key.takeLast(4)
        return "$prefix****$suffix"
    }

    companion object {
        private const val PREFS_NAME = "trendweight_bridge_prefs"
        private const val KEY_API_KEY = "trendweight_api_key"
        private const val KEY_BACKGROUND_SYNC = "background_sync_enabled"
        private const val KEY_LAST_SYNC_TIME = "last_sync_timestamp"
        private const val KEY_LAST_SYNC_STATUS = "last_sync_status"
        private const val KEY_LAST_SYNC_MESSAGE = "last_sync_message"
        private const val KEY_LAST_SYNC_COUNT = "last_sync_count"
        private const val KEY_LAST_WATERMARK = "last_watermark"
        private const val KEY_SYNC_INTERVAL_MINUTES = "sync_interval_minutes"
        private const val KEY_SYNC_NOW_DAYS = "sync_now_days"
        private const val KEY_FORCE_FULL_DAYS = "force_full_days"
    }
}
