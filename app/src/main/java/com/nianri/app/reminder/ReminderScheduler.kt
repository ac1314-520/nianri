package com.nianri.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.nianri.app.data.AnniversaryStore
import com.nianri.app.data.AppPreferences
import com.nianri.app.model.Anniversary
import com.nianri.app.util.nextAnnualDate
import com.nianri.app.util.safeDateForYear
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 只保留一个“下一次提醒”闹钟。
 *
 * 每次保存、重启或闹钟送达后，都会重新计算所有纪念日里最近的一次提醒时间，
 * 因此不同纪念日可以使用不同的 时:分，且不需要为每一条单独维护闹钟。
 */
object ReminderScheduler {
    const val ACTION_DAILY_CHECK = "com.nianri.app.action.DAILY_REMINDER_CHECK"
    private const val REQUEST_CODE = 72031

    fun ensureScheduled(context: Context) {
        if (nextReminderAt(context) == null) {
            cancel(context)
        } else {
            scheduleNext(context)
        }
    }

    fun hasActiveReminders(context: Context): Boolean = nextReminderAt(context) != null

    fun scheduleNext(context: Context) {
        val next = nextReminderAt(context)
        if (next == null) {
            cancel(context)
            return
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

    private fun nextReminderAt(context: Context): ZonedDateTime? {
        return nextReminderAt(context, ZonedDateTime.now(ZoneId.systemDefault()))
    }

    internal fun nextReminderAt(context: Context, now: ZonedDateTime): ZonedDateTime? {
        val prefs = AppPreferences.get(context)
        return AnniversaryStore.all(context)
            .asSequence()
            .filter { it.reminderEnabled }
            .mapNotNull { nextReminderAt(it, now, prefs.reminderMinutes) }
            .minOrNull()
    }

    private fun nextReminderAt(
        event: Anniversary,
        now: ZonedDateTime,
        defaultMinutes: Int,
    ): ZonedDateTime? {
        if (!event.reminderEnabled) return null
        val zone = now.zone
        val today = now.toLocalDate()
        val minutes = event.reminderMinuteOfDay ?: defaultMinutes
        val time = LocalTime.of((minutes / 60).coerceIn(0, 23), (minutes % 60).coerceIn(0, 59))

        var occurrence: LocalDate = event.nextOccurrence(today)
        if (!event.recurring && occurrence.isBefore(today)) return null

        repeat(6) {
            val dueDate = occurrence.minusDays(event.reminderAdvanceDays.toLong())
            val dueAt = dueDate.atTime(time).atZone(zone)
            if (dueAt.isAfter(now)) return dueAt
            if (!event.recurring) return null
            occurrence = event.occurrenceAfter(occurrence) ?: return null
        }
        return null
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
