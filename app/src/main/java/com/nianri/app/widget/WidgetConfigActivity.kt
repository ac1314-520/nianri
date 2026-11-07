package com.nianri.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import com.nianri.app.MainActivity
import com.nianri.app.data.AnniversaryStore
import com.nianri.app.model.dateLine
import com.nianri.app.model.sortedForHome
import com.nianri.app.ui.AppColors
import com.nianri.app.ui.AppFont
import com.nianri.app.ui.dp
import com.nianri.app.ui.newText
import com.nianri.app.ui.rippleOn
import com.nianri.app.ui.roundedDrawable

class WidgetConfigActivity : Activity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContentView(buildContent())
    }

    private fun buildContent(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppColors.paper)
            setPadding(dp(22), dp(30), dp(22), dp(24))
        }
        root.addView(newText(this, "选择要显示的纪念日", 23f, AppColors.ink, AppFont.serif))
        root.addView(
            newText(
                this,
                "每个桌面小组件可以单独选择。之后长按小组件 → 重新配置即可更换。",
                11f,
                AppColors.muted,
                AppFont.sans,
            ).apply {
                setLineSpacing(dp(3).toFloat(), 1f)
                setPadding(0, dp(8), 0, dp(18))
            },
        )

        val events = AnniversaryStore.all(this).sortedForHome()
        if (events.isEmpty()) {
            root.addView(
                newText(
                    this,
                    "还没有纪念日。先在念日里写下一个日子，再回来添加小组件。",
                    13f,
                    AppColors.inkSoft,
                    AppFont.sans,
                ).apply { setLineSpacing(dp(4).toFloat(), 1f) },
            )
            root.addView(
                newText(this, "打开念日", 14f, AppColors.cream, AppFont.sansMedium).apply {
                    gravity = Gravity.CENTER
                    isClickable = true
                    background = rippleOn(this@WidgetConfigActivity, roundedDrawable(this@WidgetConfigActivity, AppColors.ink, 22f), 0x33FFFFFF)
                    setPadding(dp(20), 0, dp(20), 0)
                    setOnClickListener {
                        startActivity(Intent(this@WidgetConfigActivity, MainActivity::class.java))
                        finish()
                    }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
                    topMargin = dp(18)
                },
            )
            return root
        }

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        events.forEach { event ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(13), dp(16), dp(13))
                background = rippleOn(
                    this@WidgetConfigActivity,
                    roundedDrawable(this@WidgetConfigActivity, AppColors.cream, 18f, 0.8f, AppColors.line),
                    0x1427231E,
                )
                isClickable = true
                isFocusable = true
                setOnClickListener { select(event.id) }
            }
            row.addView(newText(this, event.title, 15f, AppColors.ink, AppFont.sansMedium))
            row.addView(
                newText(this, event.dateLine(), 10f, AppColors.muted, AppFont.sans).apply {
                    setPadding(0, dp(4), 0, 0)
                },
            )
            list.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { if (event != events.first()) topMargin = dp(8) },
            )
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                list,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        root.addView(
            newText(this, "取消", 13f, AppColors.muted, AppFont.sansMedium).apply {
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setPadding(0, dp(12), 0, 0)
                setOnClickListener { finish() }
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)),
        )
        return root
    }

    private fun select(eventId: String) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(WidgetUpdater.selectedEventKey(appWidgetId), eventId)
            .apply()
        WidgetUpdater.updateAll(this)
        setResult(
            RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        )
        finish()
    }

    private companion object {
        const val PREFS = "anniversary_widget_prefs"
    }
}
