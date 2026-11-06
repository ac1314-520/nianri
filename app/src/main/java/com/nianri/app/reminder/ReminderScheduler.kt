package com.nianri.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.nianri.app.data.AnniversaryStore
import com.nianri.app.data.AppPreferences
import com.nianri.app.model.display
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object ReminderScheduler {
    const val ACTION_DAILY_CHECK = "com.nianri.app.action.DAILY_REMINDER_CHECK"
    private const val REQUEST_CODE = 72031

    fun ensureScheduled(context: Context) {
        if (hasActiveReminders(context)) {
            scheduleNext(context)
        } else {
            cancel(context)
        }
    }

    fun hasActiveReminders(context: Context): Boolean {
        val today = LocalDate.now()
        return AnniversaryStore.all(context).any { event ->
            event.reminderEnabled && !event.display(today).isPast
        }
    }

    fun scheduleNext(context: Context) {
        if (!hasActiveReminders(context)) {
            cancel(context)
            return
        }
        val prefs = AppPreferences.get(context)
        val time: LocalTime = prefs.reminderTime
        val zone = ZoneId.systemDefault()
        var next: ZonedDateTime = ZonedDateTime.now(zone)
            .toLocalDate()
            .atTime(time)
            .atZone(zone)
        if (!next.isAfter(ZonedDateTime.now(zone))) {
            next = next.plusDays(1)
        }

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pending = pendingIntent(context)
        val triggerAt = next.toInstant().toEpochMilli()
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        try {
            if (exactAllowed) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pending,
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pending,
                )
            }
        } catch (_: SecurityException) {
            runCatching {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pending,
                )
            }
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(pendingIntent(context))
    }

    fun hasExactAlarmAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_DAILY_CHECK
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
