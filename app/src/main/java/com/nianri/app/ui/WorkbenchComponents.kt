package com.nianri.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.nianri.app.MainActivity
import com.nianri.app.data.CheckInCategory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

data class MoodOption(val key: String, val label: String, val color: Int)

object WorkbenchMood {
    val all = listOf(
        MoodOption("happy", "开心", AppColors.gold),
        MoodOption("calm", "平静", AppColors.sage),
        MoodOption("tired", "累了", AppColors.muted),
        MoodOption("low", "低落", Color.rgb(120, 132, 148)),
        MoodOption("anxious", "焦虑", AppColors.cinnabar),
        MoodOption("grateful", "感恩", Color.rgb(176, 134, 128)),
    )

    fun label(key: String?): String = all.firstOrNull { it.key == key }?.label ?: "还没选"
    fun option(key: String?): MoodOption? = all.firstOrNull { it.key == key }
}

object DailyCopy {
    private val lines = listOf(
        "把普通的一天，过成值得记住的一天。",
        "慢一点也没关系，日子会等你。",
        "今天也请把自己照顾好。",
        "认真生活的人，自带光。",
        "愿你有事做，有人爱，有所期待。",
        "不必用力证明，安静生长就好。",
        "把喜欢的事，放进今天。",
        "日子滚烫，又暖又亮。",
        "允许一切发生，也允许自己休息。",
        "好好吃饭，好好睡觉，好好想念。",
    )

    fun today(): String = lines[(LocalDate.now().toEpochDay() % lines.size).toInt()]
}

fun featureTopBar(
    host: MainActivity,
    title: String,
    onBack: () -> Unit,
    trailing: View? = null,
): View {
    return LinearLayout(host).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(host.contentMargin(), host.dp(12), host.contentMargin(), host.dp(8))
        addView(iconButton(
            host,
            com.nianri.app.R.drawable.ic_arrow_back,
            "返回",
            42,
            19,
            AppColors.inkSoft,
            Color.TRANSPARENT,
            AppColors.line,
        ).apply { setOnClickListener { onBack() } })
        addView(newText(host, title, 25f, AppColors.ink, AppFont.serif), LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f,
        ).apply { marginStart = host.dp(13) })
        trailing?.let {
            addView(it, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
    }
}

fun featurePage(
    host: MainActivity,
    title: String,
    onBack: () -> Unit,
): Pair<FrameLayout, LinearLayout> {
    val root = FrameLayout(host).apply { background = ColorDrawable(AppColors.paper) }
    val main = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
    root.addView(main, FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT,
    ))
    main.addView(featureTopBar(host, title, onBack))
    val scroll = ScrollView(host).apply {
        isFillViewport = true
        overScrollMode = View.OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
    }
    val column = LinearLayout(host).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(host.contentMargin(), 0, host.contentMargin(), host.dp(72))
    }
    scroll.addView(column, ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ))
    main.addView(scroll, LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        0,
        1f,
    ))
    return root to column
}

fun contentCard(host: MainActivity, padding: Int = 17): LinearLayout {
    return LinearLayout(host).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(host.dp(padding), host.dp(padding), host.dp(padding), host.dp(padding))
        background = roundedDrawable(host, AppColors.cream, 22f, 0.8f, AppColors.line)
        clipToOutline = true
    }
}

fun sectionLabel(host: MainActivity, text: String): TextView {
    return newText(host, text, 12f, AppColors.inkSoft, AppFont.sansMedium, 0.08f).apply {
        setPadding(host.dp(4), 0, 0, 0)
    }
}

fun primaryAction(host: MainActivity, label: String, onClick: () -> Unit): TextView {
    return newText(host, label, 14f, AppColors.cream, AppFont.sansMedium, 0.03f).apply {
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        background = rippleOn(host, roundedDrawable(host, AppColors.cinnabar, 16f), 0x33FFFFFF)
        setOnClickListener { onClick() }
    }
}

fun softAction(host: MainActivity, label: String, onClick: () -> Unit): TextView {
    return newText(host, label, 11.5f, AppColors.cinnabar, AppFont.sansMedium).apply {
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        setPadding(host.dp(5), host.dp(8), host.dp(5), host.dp(8))
        setOnClickListener { onClick() }
    }
}

fun iconSeal(host: MainActivity, text: String, fg: Int, bg: Int, sizeDp: Int = 36): TextView {
    return newText(host, text, 14f, fg, AppFont.serif, 0.02f).apply {
        gravity = Gravity.CENTER
        includeFontPadding = false
        background = roundedDrawable(host, bg, sizeDp / 2f)
    }.also {
        it.layoutParams = LinearLayout.LayoutParams(host.dp(sizeDp), host.dp(sizeDp))
    }
}

fun textInput(
    host: MainActivity,
    hint: String,
    multiline: Boolean = false,
    inputType: Int = InputType.TYPE_CLASS_TEXT,
): EditText {
    return EditText(host).apply {
        this.hint = hint
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(AppColors.ink)
        setHintTextColor(AppColors.faint)
        typeface = AppFont.sans
        includeFontPadding = false
        background = roundedDrawable(host, AppColors.cream, 16f, 1f, AppColors.line)
        setPadding(host.dp(14), host.dp(11), host.dp(14), host.dp(11))
        this.inputType = if (multiline) {
            inputType or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        } else {
            inputType
        }
        if (multiline) {
            gravity = Gravity.TOP or Gravity.START
            minHeight = host.dp(112)
            maxLines = 8
            setSingleLine(false)
        } else {
            maxLines = 1
            setSingleLine(true)
        }
    }
}

fun categoryChip(
    host: MainActivity,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
): TextView {
    return newText(
        host,
        label,
        11.5f,
        if (selected) AppColors.cinnabar else AppColors.muted,
        if (selected) AppFont.sansMedium else AppFont.sans,
    ).apply {
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        setPadding(host.dp(11), host.dp(6), host.dp(11), host.dp(6))
        background = rippleOn(
            host,
            roundedDrawable(
                host,
                if (selected) AppColors.cinnabarSoft else Color.TRANSPARENT,
                14f,
                if (selected) 0f else 0.8f,
                if (selected) Color.TRANSPARENT else AppColors.line,
            ),
            0x1FB05238,
        )
        setOnClickListener { onClick() }
    }
}

fun cardDivider(host: MainActivity): View {
    return hairline(host, Color.argb(140, 228, 217, 202)).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            1,
        ).apply { topMargin = host.dp(10); bottomMargin = host.dp(10) }
    }
}

fun formatMoney(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    return String.format(Locale.CHINA, "%s¥%.2f", sign, kotlin.math.abs(cents) / 100.0)
}

fun epochDate(day: Long, pattern: String = "M月d日"): String =
    LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern(pattern, Locale.CHINA))

fun categoryColor(category: CheckInCategory): Int = when (category) {
    CheckInCategory.DAILY -> AppColors.sage
    CheckInCategory.LOVE -> AppColors.cinnabar
    CheckInCategory.FAN -> Color.rgb(126, 112, 154)
}

fun categorySoft(category: CheckInCategory): Int = when (category) {
    CheckInCategory.DAILY -> Color.rgb(231, 237, 228)
    CheckInCategory.LOVE -> AppColors.cinnabarSoft
    CheckInCategory.FAN -> Color.rgb(238, 232, 242)
}

fun simpleChip(
    host: Context,
    text: String,
    bg: Int = AppColors.creamDeep,
    fg: Int = AppColors.muted,
): TextView = newText(host, text, 10f, fg, AppFont.sansMedium).apply {
    gravity = Gravity.CENTER
    setPadding(host.dp(9), host.dp(4), host.dp(9), host.dp(4))
    background = roundedDrawable(host, bg, 11f)
}
