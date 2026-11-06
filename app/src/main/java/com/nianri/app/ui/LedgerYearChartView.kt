package com.nianri.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class LedgerYearChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var values: List<Long> = List(12) { 0L }
    private var selectedMonth: Int? = null
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barRect = RectF()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = "全年每月支出柱状图"
        labelPaint.typeface = AppFont.sans
        labelPaint.textAlign = Paint.Align.CENTER
        linePaint.color = AppColors.line
        linePaint.strokeWidth = context.dpf(0.8f)
    }

    fun setValues(data: List<Long>, selected: Int? = null) {
        values = List(12) { index -> data.getOrElse(index) { 0L } }
        selectedMonth = selected
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val top = context.dp(10).toFloat()
        val bottom = context.dp(23).toFloat()
        val side = context.dp(4).toFloat()
        val chartHeight = height - top - bottom
        if (chartHeight <= 0) return
        val maxValue = max(values.maxOrNull() ?: 0L, 1L).toFloat()
        val slot = (width - side * 2) / 12f
        val barWidth = slot * 0.48f
        val labelY = height - context.dp(6).toFloat()

        canvas.drawLine(
            side,
            height - bottom,
            width - side,
            height - bottom,
            linePaint,
        )

        values.forEachIndexed { index, value ->
            val centerX = side + slot * index + slot / 2f
            val ratio = value / maxValue
            val barHeight = max(chartHeight * ratio, if (value > 0) context.dpf(2f) else 0f)
            if (value <= 0) {
                barPaint.color = AppColors.line
                barRect.set(
                    centerX - barWidth / 2f,
                    height - bottom - context.dpf(2f),
                    centerX + barWidth / 2f,
                    height - bottom,
                )
            } else {
                val selected = selectedMonth == index + 1
                barPaint.color = if (selected) {
                    AppColors.cinnabar
                } else {
                    Color.argb(150, 176, 82, 56)
                }
                barRect.set(
                    centerX - barWidth / 2f,
                    height - bottom - barHeight,
                    centerX + barWidth / 2f,
                    height - bottom,
                )
            }
            canvas.drawRoundRect(
                barRect,
                context.dpf(3f),
                context.dpf(3f),
                barPaint,
            )
            labelPaint.color = if (selectedMonth == index + 1) AppColors.cinnabar else AppColors.faint
            labelPaint.textSize = context.dpf(8.5f)
            labelPaint.typeface = if (selectedMonth == index + 1) AppFont.sansMedium else AppFont.sans
            canvas.drawText("${index + 1}", centerX, labelY, labelPaint)
        }
    }
}
