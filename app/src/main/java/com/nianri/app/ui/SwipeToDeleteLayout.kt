package com.nianri.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.min

/**
 * 左滑删除容器：内容跟手左移，右侧露出删除区；滑过阈值后直接删除。
 * 调用方负责提供撤销入口与真正的数据删除。
 */
class SwipeToDeleteLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    var onDelete: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AppColors.cream
        textSize = context.dpf(12f)
        typeface = AppFont.sansMedium
        textAlign = Paint.Align.RIGHT
    }
    private val clipPath = Path()
    private val clipRect = RectF()
    private var radiusPx = context.dpf(22f)
    private var gradient: Shader? = null
    private var front: View? = null
    private var downX = 0f
    private var downY = 0f
    private var swiping = false
    private var deleting = false
    private var thresholdCrossed = false
    private var trash: Drawable? = null

    init {
        clipChildren = false
        setWillNotDraw(false)
        trash = context.getDrawable(com.nianri.app.R.drawable.ic_trash)?.mutate()?.apply {
            setTint(AppColors.cream)
        }
    }

    fun setContent(content: View, fillHeight: Boolean) {
        front = content
        addView(
            content,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                if (fillHeight) LayoutParams.MATCH_PARENT else LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    fun setCornerRadiusDp(radiusDp: Float) {
        radiusPx = context.dpf(radiusDp)
        invalidation()
    }

    private fun invalidation() {
        updateClip()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateClip()
        gradient = LinearGradient(
            0f, 0f, w.toFloat(), 0f,
            intArrayOf(AppColors.cinnabar, AppColors.cinnabarDeep),
            null,
            Shader.TileMode.CLAMP,
        )
    }

    private fun updateClip() {
        clipRect.set(0f, 0f, width.toFloat(), height.toFloat())
        clipPath.reset()
        clipPath.addRoundRect(
            clipRect,
            floatArrayOf(radiusPx, radiusPx, radiusPx, radiusPx, radiusPx, radiusPx, radiusPx, radiusPx),
            Path.Direction.CW,
        )
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!isEnabled || deleting) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                swiping = false
                thresholdCrossed = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - downX
                val dy = ev.rawY - downY
                if (dx < -touchSlop && abs(dx) > abs(dy) * 1.25f) {
                    swiping = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!isEnabled || deleting) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                swiping = false
                thresholdCrossed = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - downX
                val dy = ev.rawY - downY
                if (!swiping) {
                    if (dx < -touchSlop && abs(dx) > abs(dy) * 1.25f) {
                        swiping = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                    } else {
                        return true
                    }
                }
                val offset = dx.coerceAtMost(0f).coerceAtLeast(-width.toFloat())
                front?.translationX = offset
                val threshold = min(width.toFloat() * 0.34f, context.dpf(150f))
                if (!thresholdCrossed && -offset >= threshold) {
                    thresholdCrossed = true
                    performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!swiping) {
                    return performClick()
                }
                val offset = front?.translationX ?: 0f
                val threshold = min(width.toFloat() * 0.34f, context.dpf(150f))
                if (-offset >= threshold) {
                    animateOut()
                } else {
                    springBack()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (swiping) springBack() else front?.translationX = 0f
                swiping = false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(ev)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private fun springBack() {
        swiping = false
        front?.animate()
            ?.translationX(0f)
            ?.setDuration(180L)
            ?.start()
        thresholdCrossed = false
        invalidate()
    }

    private fun animateOut() {
        deleting = true
        val view = front ?: return
        view.animate()
            .translationX(-width.toFloat())
            .setDuration(190L)
            .withEndAction {
                onDelete?.invoke()
                front?.translationX = 0f
                deleting = false
            }
            .start()
    }

    override fun dispatchDraw(canvas: Canvas) {
        val translation = front?.translationX ?: 0f
        if (translation < -1f && width > 0) {
            val save = canvas.save()
            canvas.clipPath(clipPath)
            paint.shader = gradient
            canvas.drawRect(clipRect, paint)
            paint.shader = null

            val reveal = -translation
            val alpha = (min(1f, reveal / context.dpf(72f)) * 255).toInt().coerceIn(0, 255)
            val right = width.toFloat() - context.dpf(24f)
            val centerY = height / 2f
            val iconSize = context.dpf(19f)
            trash?.let {
                it.alpha = alpha
                val top = (centerY - iconSize / 2f).toInt()
                it.setBounds(
                    (right - iconSize).toInt(),
                    top,
                    right.toInt(),
                    top + iconSize.toInt(),
                )
                it.draw(canvas)
            }
            textPaint.alpha = alpha
            val metrics = textPaint.fontMetrics
            val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
            canvas.drawText("删除", right - iconSize - context.dpf(10f), baseline, textPaint)
            canvas.restoreToCount(save)
        }
        super.dispatchDraw(canvas)
    }

    override fun onDetachedFromWindow() {
        front?.animate()?.cancel()
        super.onDetachedFromWindow()
    }
}
