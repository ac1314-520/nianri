package com.nianri.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import kotlin.math.min
import kotlin.math.roundToInt

/** 与「念日」纸张色系一致的小开关，避免系统 Switch 在浅色背景上显得发灰。 */
class NianRiSwitch @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackRect = RectF()
    private var progress = 0f
    private var animator: ValueAnimator? = null

    var onCheckedChange: ((Boolean) -> Unit)? = null

    var isChecked: Boolean = false
        private set

    init {
        isClickable = true
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        setOnClickListener {
            setChecked(!isChecked, animate = true)
            onCheckedChange?.invoke(isChecked)
            performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    fun setChecked(value: Boolean, animate: Boolean = false) {
        if (value == isChecked) {
            progress = if (value) 1f else 0f
            invalidate()
            return
        }
        isChecked = value
        animator?.cancel()
        if (!animate || !isAttachedToWindow) {
            progress = if (value) 1f else 0f
            invalidate()
        } else {
            animator = ValueAnimator.ofFloat(progress, if (value) 1f else 0f).apply {
                duration = 190L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    progress = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
        if (value) {
            announceForAccessibility("已开启")
        } else {
            announceForAccessibility("已关闭")
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = resolveSize(context.dp(56), widthMeasureSpec)
        val height = resolveSize(context.dp(48), heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val trackW = min(context.dpf(46f), width.toFloat())
        val trackH = context.dpf(28f)
        val left = (width - trackW) / 2f
        val top = (height - trackH) / 2f
        trackRect.set(left, top, left + trackW, top + trackH)

        trackPaint.color = blendColor(
            android.graphics.Color.rgb(214, 201, 181),
            AppColors.cinnabar,
            progress,
        )
        canvas.drawRoundRect(trackRect, trackH / 2f, trackH / 2f, trackPaint)

        val thumbRadius = context.dpf(11f)
        val margin = context.dpf(3f)
        val startX = trackRect.left + margin + thumbRadius
        val endX = trackRect.right - margin - thumbRadius
        val cx = startX + (endX - startX) * progress
        val cy = trackRect.centerY()

        thumbPaint.color = AppColors.cream
        canvas.drawCircle(cx, cy, thumbRadius, thumbPaint)
        thumbBorderPaint.style = Paint.Style.STROKE
        thumbBorderPaint.strokeWidth = context.dpf(0.8f)
        thumbBorderPaint.color = android.graphics.Color.argb(
            (70 * progress).roundToInt(),
            127,
            74,
            51,
        )
        canvas.drawCircle(cx, cy, thumbRadius, thumbBorderPaint)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.Switch"
        info.isCheckable = true
        info.isChecked = isChecked
        info.isClickable = true
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private fun blendColor(from: Int, to: Int, fraction: Float): Int {
        val t = fraction.coerceIn(0f, 1f)
        return android.graphics.Color.rgb(
            (android.graphics.Color.red(from) * (1 - t) + android.graphics.Color.red(to) * t).roundToInt(),
            (android.graphics.Color.green(from) * (1 - t) + android.graphics.Color.green(to) * t).roundToInt(),
            (android.graphics.Color.blue(from) * (1 - t) + android.graphics.Color.blue(to) * t).roundToInt(),
        )
    }
}
