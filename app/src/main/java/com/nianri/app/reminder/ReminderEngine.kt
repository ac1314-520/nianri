package com.nianri.app.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.nianri.app.MainActivity
import com.nianri.app.R
import com.nianri.app.data.AnniversaryStore
import com.nianri.app.data.AppPreferences
import com.nianri.app.model.Anniversary
import com.nianri.app.model.display
import com.nianri.app.util.cnAnniversary
import com.nianri.app.util.daysBetween
import com.nianri.app.util.nextAnnualDate
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object ReminderEngine {
    private const val CHANNEL_ID = "anniversary_reminders"

    fun deliverDueReminders(context: Context) {
        val prefs = AppPreferences.get(context)
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        val today = now.toLocalDate()
        val nowTime = now.toLocalTime()
        val events = AnniversaryStore.all(context)

        events.forEach { event ->
            if (!event.reminderEnabled) return@forEach
            if (prefs.wasNotifiedOn(event.id, today.toEpochDay())) return@forEach
            val occurrence = nextReminderOccurrence(event, today) ?: return@forEach
            val dueDate = occurrence.minusDays(event.reminderAdvanceDays.toLong())
            if (dueDate != today) return@forEach
            val minutes = event.reminderMinuteOfDay ?: prefs.reminderMinutes
            val reminderTime = LocalTime.of(
                (minutes / 60).coerceIn(0, 23),
                (minutes % 60).coerceIn(0, 59),
            )
            if (nowTime.isBefore(reminderTime)) return@forEach

            val aheadDays = daysBetween(today, occurrence)
            if (postNotification(context, event, occurrence, aheadDays)) {
                prefs.markNotifiedOn(event.id, today.toEpochDay())
            }
        }
    }

    private fun nextReminderOccurrence(event: Anniversary, today: LocalDate): LocalDate? {
        return if (event.recurring) {
            if (event.date.isAfter(today)) {
                // 每年纪念尚未开始，先把开始日当作第一次。
                event.date
            } else {
                nextAnnualDate(event.date, today)
            }
        } else {
            if (event.date.isBefore(today)) null else event.date
        }
    }

    private fun postNotification(
        context: Context,
        event: Anniversary,
        occurrence: LocalDate,
        daysAhead: Long,
    ): Boolean {
        if (!notificationsAllowed(context)) return false
        createChannel(context)

        val title: String
        val body: String
        if (daysAhead == 0L) {
            title = "今天是「${event.title}」"
            body = if (event.recurring && !event.date.isAfter(LocalDate.now())) {
                val years = occurrence.year - event.date.year
                if (years > 0) {
                    "是你们的${cnAnniversary(years)}。愿岁岁年年，都有此刻。"
                } else {
                    "从今天开始，把这一天好好收藏。"
                }
            } else {
                "就是今天。愿所有期待，都有回音。"
            }
        } else {
            title = "还有 $daysAhead 天 · ${event.title}"
            body = if (event.recurring) {
                "重要的日子正在靠近，记得为它留一点时间。"
            } else {
                "倒数第 $daysAhead 天，愿等待也温柔。"
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_EVENT_ID, event.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            event.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFFB05238.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setShowWhen(true)

        val manager = context.getSystemService(NotificationManager::class.java)
        return try {
            manager.notify(notificationId(event.id), builder.build())
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
            enableVibration(true)
            enableLights(true)
            lightColor = 0xFFB05238.toInt()
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            setSound(
                android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION),
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        manager.createNotificationChannel(channel)
    }

    fun notificationsAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannel(CHANNEL_ID)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun ensureChannel(context: Context) = createChannel(context)

    fun notificationId(id: String): Int = (id.hashCode() and 0x7fffffff).let {
        if (it == 0) 7411 else it
    }

    fun openNotificationSettings(context: Context) {
        val intent = Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, CHANNEL_ID)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            val fallback = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }
}
