package com.nianri.app.data

import android.content.Context
import com.nianri.app.BuildConfig
import java.time.LocalTime

class AppPreferences private constructor(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("nianri_preferences", Context.MODE_PRIVATE)

    var reminderMinutes: Int
        get() = prefs.getInt(KEY_REMINDER_MINUTES, DEFAULT_REMINDER_MINUTES)
        set(value) = prefs.edit().putInt(KEY_REMINDER_MINUTES, value).apply()

    val reminderTime: LocalTime
        get() = LocalTime.of(reminderMinutes / 60, reminderMinutes % 60)

    fun setReminderTime(hour: Int, minute: Int) {
        reminderMinutes = hour * 60 + minute
    }

    var autoCheckUpdates: Boolean
        get() = prefs.getBoolean(KEY_AUTO_UPDATE, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_UPDATE, value).apply()

    var updateManifestUrl: String
        get() {
            val saved = prefs.getString(KEY_UPDATE_URL, null)
            return saved ?: BuildConfig.DEFAULT_UPDATE_MANIFEST
        }
        set(value) = prefs.edit().putString(KEY_UPDATE_URL, value.trim()).apply()

    var lastAutoUpdateCheck: Long
        get() = prefs.getLong(KEY_LAST_AUTO_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_AUTO_CHECK, value).apply()

    fun wasNotifiedOn(eventId: String, epochDay: Long): Boolean =
        prefs.getLong(notifiedKey(eventId), Long.MIN_VALUE) == epochDay

    fun markNotifiedOn(eventId: String, epochDay: Long) {
        prefs.edit().putLong(notifiedKey(eventId), epochDay).apply()
    }

    fun clearNotificationMark(eventId: String) {
        prefs.edit().remove(notifiedKey(eventId)).apply()
    }

    fun clearAllNotificationMarks() {
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith(KEY_NOTIFIED_PREFIX) }
            .forEach(editor::remove)
        editor.apply()
    }

    companion object {
        private const val KEY_REMINDER_MINUTES = "reminder_minutes"
        private const val KEY_AUTO_UPDATE = "auto_update"
        private const val KEY_UPDATE_URL = "update_manifest_url"
        private const val KEY_LAST_AUTO_CHECK = "last_auto_update_check"
        private const val KEY_NOTIFIED_PREFIX = "notified:"
        private const val DEFAULT_REMINDER_MINUTES = 9 * 60

        @Volatile
        private var instance: AppPreferences? = null

        fun get(context: Context): AppPreferences {
            return instance ?: synchronized(this) {
                instance ?: AppPreferences(context).also { instance = it }
            }
        }

        private fun notifiedKey(id: String) = "$KEY_NOTIFIED_PREFIX$id"
    }
}
