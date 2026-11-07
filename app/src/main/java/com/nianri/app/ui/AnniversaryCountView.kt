package com.nianri.app.ui

import android.graphics.Color
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.nianri.app.MainActivity
import com.nianri.app.model.Anniversary
import com.nianri.app.model.AnniversaryDisplay
import java.time.LocalDate
import java.time.Period

/**
 * 天数区域：轻点大数字或右上角胶囊，在天 / 周 / 年月之间切换。
 * 始终保留总天数换算，避免“年+月”看起来比实际少。
 */
class AnniversaryCountView(
    private val host: MainActivity,
    private val event: Anniversary,
    private val today: LocalDate,
    private val display: AnniversaryDisplay,
) : LinearLayout(host) {

    private var mode = 0
    private val chips = mutableListOf<TextView>()
    private val kicker = newText(
        host,
        display.kicker.ifBlank { "今天" },
        9.5f,
        AppColors.muted,
        AppFont.sansMedium,
        0.14f,
    )
    private val big = newText(
        host,
        display.big,
        76f,
        countColor(),
        AppFont.serif,
    ).apply {
        includeFontPadding = false
        setLineSpacing(0f, 1f)
        fontFeatureSettings = "lnum"
    }
    private val unit = newText(
        host,
        display.unit,
        14f,
        AppColors.muted,
        AppFont.sans,
    )
    private val caption = newText(
        host,
        display.caption,
        12.5f,
        AppColors.inkSoft,
        AppFont.sans,
    ).apply {
        setLineSpacing(host.dpf(5f), 1f)
    }

    init {
        orientation = VERTICAL
        setPadding(0, host.dp(18), 0, host.dp(2))
        isClickable = true
        isFocusable = true

        val header = LinearLayout(host).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(kicker, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val chipWrap = LinearLayout(host).apply {
            orientation = HORIZONTAL
            background = roundedDrawable(host, AppColors.paperDeep, 20f)
            setPadding(host.dp(3), host.dp(3), host.dp(3), host.dp(3))
        }
        listOf("天", "周", "年月").forEachIndexed { index, label ->
            val chip = newText(host, label, 9.5f, AppColors.muted, AppFont.sansMedium).apply {
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setPadding(host.dp(8), 0, host.dp(8), 0)
                setOnClickListener {
                    if (mode != index) {
                        mode = index
                        render()
                        this@AnniversaryCountView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                }
            }
            chipWrap.addView(chip, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                host.dp(25),
            ).apply {
                if (index > 0) marginStart = host.dp(2)
            })
            chips.add(chip)
        }
        header.addView(chipWrap)
        addView(header)

        val countLine = LinearLayout(host).apply {
            orientation = HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(0, host.dp(2), 0, 0)
        }
        countLine.addView(big)
        countLine.addView(unit, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            marginStart = host.dp(8)
            bottomMargin = host.dp(16)
        })
        addView(countLine)

        caption.setPadding(0, host.dp(12), 0, 0)
        addView(caption)

        addView(
            newText(
                host,
                "轻点天数切换单位 · 天 / 周 / 年月",
                9f,
                AppColors.faint,
                AppFont.sans,
            ).apply {
                setPadding(0, host.dp(8), 0, 0)
            },
        )

        addView(View(host).apply {
            setBackgroundColor(AppColors.line)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            host.dp(0.7f).coerceAtLeast(1),
        ).apply { topMargin = host.dp(16) })

        setOnClickListener {
            mode = (mode + 1) % 3
            render()
            this@AnniversaryCountView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        render()
    }

    private fun render() {
        val isToday = display.isToday
        val text: String
        val unitText: String
        val captionText: String
        val bigSize: Float
        when (mode) {
            1 -> {
                text = if (isToday) "今天" else (baseDays() / 7).toString()
                unitText = if (isToday) "" else "周"
                captionText = if (isToday) display.caption else "共 ${baseDays()} 天"
                bigSize = 76f
            }
            2 -> {
                text = if (isToday) "今天" else periodText()
                unitText = ""
                captionText = if (isToday) display.caption else "共 ${baseDays()} 天"
                bigSize = when {
                    isToday -> 54f
                    text.length >= 8 -> 38f
                    text.length >= 5 -> 46f
                    else -> 64f
                }
            }
            else -> {
                text = display.big
                unitText = display.unit
                captionText = display.caption
                bigSize = when {
                    display.big == "今天" -> 54f
                    display.big.length >= 5 -> 64f
                    else -> 80f
                }
            }
        }

        kicker.text = if (isToday) "今天" else display.kicker.ifBlank { "这一天" }
        big.text = text
        big.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, bigSize)
        big.setTextColor(countColor())
        unit.text = unitText
        unit.visibility = if (unitText.isEmpty()) View.GONE else View.VISIBLE
        caption.text = captionText

        chips.forEachIndexed { index, chip ->
            val selected = mode == index
            chip.setTextColor(if (selected) AppColors.cream else AppColors.muted)
            chip.background = if (selected) {
                roundedDrawable(host, AppColors.ink, 20f)
            } else {
                roundedDrawable(host, Color.TRANSPARENT, 20f)
            }
        }
        contentDescription = "${event.title}，${kicker.text}${big.text}${unitText}，$captionText"
    }

    private fun countColor(): Int {
        return if (display.isToday || display.isCountdown) AppColors.cinnabar else AppColors.ink
    }

    private fun baseDays(): Long {
        return when {
            display.isToday -> 0L
            event.recurring -> display.togetherDays ?: display.daysUntilNext.coerceAtLeast(0)
            display.isPast -> display.big.toLongOrNull() ?: 0L
            else -> display.daysUntilNext.coerceAtLeast(0)
        }
    }

    private fun periodText(): String {
        val start = if (display.isCountdown) today else event.date
        val end = if (display.isCountdown) event.date else today
        val period = try {
            Period.between(start, end)
        } catch (_: Throwable) {
            return "${baseDays()}天"
        }
        val years = period.years.coerceAtLeast(0)
        val months = period.months.coerceAtLeast(0)
        val days = period.days.coerceAtLeast(0)
        val builder = StringBuilder()
        if (years > 0) builder.append(years).append("年")
        if (months > 0) builder.append(months).append("个月")
        if (days > 0) builder.append(days).append("天")
        if (builder.isEmpty()) return "今天"
        return builder.toString()
    }
}
