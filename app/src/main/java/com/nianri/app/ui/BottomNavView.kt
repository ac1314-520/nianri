package com.nianri.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nianri.app.MainActivity
import com.nianri.app.R
import kotlin.math.roundToInt

enum class NavDestination { HOME, WORKBENCH, RECORDS, SETTINGS }

/**
 * Android 版全宽悬浮底部导航。Liquid Glass 在 Android 上不是系统组件，
 * 这里用半透明纸面、发丝边、柔和投影和弹簧式标签展开来近似；
 * 只让它浮在内容之上，不把效果铺进正文。
 */
class BottomNavView(
    private val host: MainActivity,
    private val onSelect: (NavDestination) -> Unit,
) : FrameLayout(host) {

    private data class Item(
        val destination: NavDestination,
        val label: String,
        val icon: Int,
        val view: LinearLayout,
        val text: TextView,
        val image: ImageView,
    )

    private val items = mutableListOf<Item>()
    private var selected: NavDestination = NavDestination.HOME

    init {
        background = roundedDrawable(host, 0xF4FFFDF9.toInt(), 30f, 0.9f, AppColors.line)
        elevation = host.dpf(12f)
        clipToOutline = false
        setPadding(host.dp(6), host.dp(6), host.dp(6), host.dp(6))

        val row = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, host.dp(62))
        }

        addItem(row, NavDestination.HOME, "首页", R.drawable.ic_nav_home)
        addItem(row, NavDestination.WORKBENCH, "工作台", R.drawable.ic_nav_grid)
        addItem(row, NavDestination.RECORDS, "记录", R.drawable.ic_nav_book)
        addItem(row, NavDestination.SETTINGS, "设置", R.drawable.ic_nav_settings)

        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, host.dp(62)))
        select(NavDestination.HOME, animate = false)
    }

    private fun addItem(row: LinearLayout, destination: NavDestination, label: String, icon: Int) {
        val item = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = label
            setPadding(host.dp(7), 0, host.dp(7), 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }
        val image = ImageView(host).apply {
            setImageResource(icon)
            setColorFilter(AppColors.muted)
            layoutParams = LinearLayout.LayoutParams(host.dp(20), host.dp(20))
        }
        val text = newText(host, label, 11.5f, AppColors.ink, AppFont.sansMedium).apply {
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(host.dp(5), 0, 0, 0)
            visibility = View.GONE
        }
        item.addView(image)
        item.addView(text)
        item.setOnClickListener {
            if (destination != selected) {
                host.window.decorView.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                select(destination, animate = true)
                onSelect(destination)
            }
        }
        row.addView(item)
        items += Item(destination, label, icon, item, text, image)
    }

    fun select(destination: NavDestination, animate: Boolean = true) {
        selected = destination
        items.forEach { item ->
            val active = item.destination == destination
            item.view.background = if (active) {
                roundedDrawable(host, AppColors.cinnabarSoft, 24f, 0.8f, 0x33CC785C)
            } else {
                null
            }
            item.image.setColorFilter(if (active) AppColors.cinnabarDeep else AppColors.muted)
            item.image.layoutParams = (item.image.layoutParams as LinearLayout.LayoutParams).apply {
                width = host.dp(if (active) 21 else 20)
                height = host.dp(if (active) 21 else 20)
            }
            if (active) {
                item.text.visibility = View.VISIBLE
                item.text.alpha = if (animate) 0f else 1f
                item.text.scaleX = if (animate) 0.8f else 1f
                item.text.scaleY = if (animate) 0.8f else 1f
                item.text.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(220L).start()
            } else {
                item.text.visibility = View.GONE
            }
            item.view.animate().scaleX(if (active) 1f else 1f).scaleY(1f).setDuration(160L).start()
        }
    }
}
