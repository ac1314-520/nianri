package com.nianri.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.min

/**
 * 轻量经期日历：不引入日历库，只用 Canvas 画 6 行日期。
 * 实心为已记录经期，描边为预计，圆环标今天。
 */
class PeriodCalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var month: YearMonth = YearMonth.now()
    private var periodDays: Set<Long> = emptySet()
    private var predictedDays: Set<Long> = emptySet()
    private var today: Long = LocalDate.now().toEpochDay()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AppColors.cinnabar
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AppColors.cinnabar
        style = Paint.Style.STROKE
        strokeWidth = context.dpf(1.4f)
    }
    private val todayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AppColors.inkSoft
        style = Paint.Style.STROKE
        strokeWidth = context.dpf(1f)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = AppFont.sans
    }
    private val weekPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = context.dpf(8.5f)
        color = AppColors.faint
        typeface = AppFont.sans
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "经期日历，实心为已记录，描边为预计，圆环为今天"
        isClickable = false
    }

    fun setData(
        month: YearMonth,
        periodDays: Set<Long>,
        predictedDays: Set<Long>,
        today: Long,
    ) {
        this.month = month
        this.periodDays = periodDays
        this.predictedDays = predictedDays
        this.today = today
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(context.dp(240), widthMeasureSpec)
        val height = resolveSize(context.dp(228), heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cellWidth = width / 7f
        val top = context.dpf(25f)
        val bottom = context.dpf(4f)
        val cellHeight = (height - top - bottom) / 6f
        val labels = listOf("一", "二", "三", "四", "五", "六", "日")
        labels.forEachIndexed { index, label ->
            val x = cellWidth * index + cellWidth / 2f
            canvas.drawText(label, x, context.dpf(12f), weekPaint)
        }
        val firstOffset = month.atDay(1).dayOfWeek.value - 1
        val firstEpoch = month.atDay(1).toEpochDay()
        val days = month.lengthOfMonth()
        for (day in 1..days) {
            val index = firstOffset + day - 1
            val col = index % 7
            val row = index / 7
            if (row > 5) continue
            val cx = cellWidth * col + cellWidth / 2f
            val cy = top + cellHeight * row + cellHeight / 2f
            val radius = min(cellWidth, cellHeight) * 0.30f
            val epoch = firstEpoch + day - 1
            val isPeriod = epoch in periodDays
            val isPredicted = epoch in predictedDays
            val isToday = epoch == today

            if (isPeriod) {
                canvas.drawCircle(cx, cy, radius, fillPaint)
            } else if (isPredicted) {
                canvas.drawCircle(cx, cy, radius, strokePaint)
            }
            if (isToday) {
                canvas.drawCircle(cx, cy, radius + context.dpf(2.6f), todayPaint)
            }

            textPaint.textSize = context.dpf(9.5f)
            textPaint.typeface = if (isToday) AppFont.sansMedium else AppFont.sans
            textPaint.color = when {
                isPeriod -> AppColors.cream
                isPredicted -> AppColors.cinnabar
                else -> AppColors.inkSoft
            }
            val baseline = cy - (textPaint.ascent() + textPaint.descent()) / 2f
            canvas.drawText(day.toString(), cx, baseline, textPaint)
        }
    }
}

