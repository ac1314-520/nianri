package com.nianri.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.nianri.app.MainActivity
import com.nianri.app.R
import com.nianri.app.data.AnniversaryStore
import com.nianri.app.model.Anniversary
import com.nianri.app.model.display
import com.nianri.app.model.dateLine
import com.nianri.app.model.sortedForHome
import java.time.LocalDate

class AnniversaryWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        WidgetUpdater.updateAll(context)
    }

    override fun onEnabled(context: Context) {
        WidgetUpdater.updateAll(context)
    }
}

object WidgetUpdater {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, AnniversaryWidgetProvider::class.java),
        )
        ids.forEach { id -> update(context, manager, id) }
    }

    private fun update(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_anniversary)
        val today = LocalDate.now()
        val events = AnniversaryStore.all(context)
        val next = events
            .map { it to it.display(today) }
            .filter { !it.second.isPast }
            .minByOrNull { it.second.daysUntilNext }
            ?: events.sortedForHome(today).firstOrNull()?.let { it to it.display(today) }

        if (next == null) {
            views.setTextViewText(R.id.widget_title, "念日")
            views.setTextViewText(R.id.widget_count, "—")
            views.setTextViewText(R.id.widget_unit, "")
            views.setTextViewText(R.id.widget_status, "写下一个日子")
            views.setTextViewText(R.id.widget_date, "点按打开念日")
        } else {
            val (event, display) = next
            views.setTextViewText(R.id.widget_title, event.title)
            if (display.isToday) {
                views.setTextViewText(R.id.widget_count, "今天")
                views.setTextViewText(R.id.widget_unit, "")
                views.setTextViewText(R.id.widget_status, "就是今天")
            } else {
                views.setTextViewText(R.id.widget_count, display.big)
                views.setTextViewText(R.id.widget_unit, display.unit)
                views.setTextViewText(R.id.widget_status, display.countPrefix)
            }
            views.setTextViewText(R.id.widget_date, event.dateLine())
        }

        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            appWidgetId,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, pending)
        manager.updateAppWidget(appWidgetId, views)
    }
}
