package com.nianri.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import com.nianri.app.MainActivity
import com.nianri.app.R
import com.nianri.app.data.AnniversaryStore
import com.nianri.app.data.PhotoStore
import com.nianri.app.model.Anniversary
import com.nianri.app.model.dateLine
import com.nianri.app.model.display
import com.nianri.app.model.sortedForHome
import java.time.LocalDate
import java.util.concurrent.Executors

abstract class BaseAnniversaryWidgetProvider : AppWidgetProvider() {
    abstract val layoutRes: Int

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

class AnniversaryWidgetProviderSmall : BaseAnniversaryWidgetProvider() {
    override val layoutRes: Int = R.layout.widget_anniversary_small
}

class AnniversaryWidgetProviderWide : BaseAnniversaryWidgetProvider() {
    override val layoutRes: Int = R.layout.widget_anniversary_wide
}

class AnniversaryWidgetProviderLarge : BaseAnniversaryWidgetProvider() {
    override val layoutRes: Int = R.layout.widget_anniversary_large
}

object WidgetUpdater {
    private data class Spec(
        val provider: Class<out AppWidgetProvider>,
        val layoutRes: Int,
    )

    private val specs = listOf(
        Spec(AnniversaryWidgetProviderSmall::class.java, R.layout.widget_anniversary_small),
        Spec(AnniversaryWidgetProviderWide::class.java, R.layout.widget_anniversary_wide),
        Spec(AnniversaryWidgetProviderLarge::class.java, R.layout.widget_anniversary_large),
    )

    private const val PREFS = "anniversary_widget_prefs"
    private val widgetExecutor = Executors.newSingleThreadExecutor()

    /**
     * Widget 更新会读本地照片并解码。不能放在 Activity.onResume 的主线程里，
     * 否则列表一多、封面一大，点按和返回就会明显卡顿。
     */
    fun updateAll(context: Context) {
        val appContext = context.applicationContext
        widgetExecutor.execute {
            runCatching { updateAllNow(appContext) }
        }
    }

    private fun updateAllNow(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        specs.forEach { spec ->
            val ids = manager.getAppWidgetIds(ComponentName(context, spec.provider))
            ids.forEach { id -> update(context, manager, id, spec.layoutRes) }
        }
    }

    fun selectedEventKey(appWidgetId: Int) = "event_$appWidgetId"

    private fun selectedEvent(context: Context, appWidgetId: Int): Anniversary? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val selectedId = prefs.getString(selectedEventKey(appWidgetId), null)
        val events = AnniversaryStore.all(context)
        return events.firstOrNull { it.id == selectedId }
            ?: nextEvent(events)
    }

    private fun nextEvent(events: List<Anniversary>): Anniversary? {
        val today = LocalDate.now()
        return events
            .map { it to it.display(today) }
            .filter { !it.second.isPast }
            .minByOrNull { it.second.daysUntilNext }
            ?.first
            ?: events.sortedForHome(today).firstOrNull()
    }

    private fun update(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        layoutRes: Int,
    ) {
        val views = RemoteViews(context.packageName, layoutRes)
        val event = selectedEvent(context, appWidgetId)
        val photo = event?.imagePath?.let { name ->
            if (PhotoStore.exists(context, name)) loadBitmap(context, name) else null
        }
        val hasPhoto = photo != null

        if (photo != null) {
            views.setImageViewBitmap(R.id.widget_image, photo)
            views.setViewVisibility(R.id.widget_image, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.widget_scrim, android.view.View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_image, android.view.View.GONE)
            views.setViewVisibility(R.id.widget_scrim, android.view.View.GONE)
        }

        val titleColor = if (hasPhoto) 0xFFFFF9EF.toInt() else 0xFF27231E.toInt()
        val mutedColor = if (hasPhoto) 0xCCFFF9EF.toInt() else 0xFF7C7468.toInt()
        val accentColor = if (hasPhoto) 0xFFFFD9A8.toInt() else 0xFFB05238.toInt()
        views.setTextColor(R.id.widget_brand, accentColor)
        views.setTextColor(R.id.widget_title, titleColor)
        views.setTextColor(R.id.widget_count, titleColor)
        views.setTextColor(R.id.widget_unit, mutedColor)
        views.setTextColor(R.id.widget_status, accentColor)
        views.setTextColor(R.id.widget_date, mutedColor)

        if (event == null) {
            views.setTextViewText(R.id.widget_title, "念日")
            views.setTextViewText(R.id.widget_count, "—")
            views.setTextViewText(R.id.widget_unit, "")
            views.setTextViewText(R.id.widget_status, "写下一个日子")
            views.setTextViewText(R.id.widget_date, "点按打开念日")
        } else {
            val display = event.display(LocalDate.now())
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
            if (event != null) putExtra(MainActivity.EXTRA_EVENT_ID, event.id)
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

    private fun loadBitmap(context: Context, name: String): Bitmap? {
        val file = PhotoStore.fileFor(context, name)
        if (!file.isFile) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 520 &&
                bounds.outHeight / (sample * 2) >= 360
            ) {
                sample *= 2
            }
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.RGB_565
                },
            )
        } catch (_: Throwable) {
            null
        }
    }
}
