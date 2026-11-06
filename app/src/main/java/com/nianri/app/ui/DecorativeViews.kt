package com.nianri.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** 空状态里的一枚小拱门：夕阳、地平线和一张还没写上名字的纸。 */
class EmptyRitualView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val archRect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val archW = min(w * 0.74f, context.dpf(240f))
        val archH = min(h * 0.9f, context.dpf(310f))
        val left = (w - archW) / 2f
        val top = (h - archH) / 2f
        archRect.set(left, top, left + archW, top + archH)
        val topRadius = archW / 2f

        path.reset()
        path.addRoundRect(
            archRect,
            floatArrayOf(topRadius, topRadius, topRadius, topRadius, 18f, 18f, 18f, 18f),
            Path.Direction.CW,
        )
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, top, 0f, top + archH,
            intArrayOf(
                Color.rgb(250, 244, 234),
                Color.rgb(242, 232, 217),
            ),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawPath(path, paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = context.dpf(0.9f)
        paint.color = Color.argb(120, 196, 177, 151)
        canvas.drawPath(path, paint)

        // 内层拱线
        val inset = context.dpf(12f)
        path.reset()
        val inner = RectF(
            archRect.left + inset,
            archRect.top + inset,
            archRect.right - inset,
            archRect.bottom - inset,
        )
        val innerRadius = inner.width() / 2f
        path.addRoundRect(
            inner,
            floatArrayOf(innerRadius, innerRadius, innerRadius, innerRadius, 12f, 12f, 12f, 12f),
            Path.Direction.CW,
        )
        paint.color = Color.argb(55, 176, 82, 56)
        canvas.drawPath(path, paint)

        // 一枚安静的落日
        val cx = w / 2f
        val sunY = top + archH * 0.43f
        val sunR = archW * 0.135f
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(28, 176, 82, 56)
        canvas.drawCircle(cx, sunY, sunR * 1.85f, paint)
        paint.color = AppColors.cinnabar
        canvas.drawCircle(cx, sunY, sunR, paint)
        paint.color = Color.argb(150, 255, 249, 239)
        canvas.drawCircle(cx - sunR * 0.32f, sunY - sunR * 0.28f, sunR * 0.2f, paint)

        // 地平线
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = context.dpf(1.4f)
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.argb(185, 52, 46, 39)
        canvas.drawLine(
            cx - archW * 0.25f, top + archH * 0.62f,
            cx + archW * 0.25f, top + archH * 0.62f,
            paint,
        )
        paint.color = Color.argb(60, 52, 46, 39)
        canvas.drawLine(
            cx - archW * 0.17f, top + archH * 0.68f,
            cx + archW * 0.17f, top + archH * 0.68f,
            paint,
        )

        // 小星芒
        val starX = left + archW * 0.79f
        val starY = top + archH * 0.30f
        val starR = context.dpf(5.5f)
        paint.strokeWidth = context.dpf(1f)
        paint.color = Color.argb(180, 196, 162, 107)
        canvas.drawLine(starX - starR, starY, starX + starR, starY, paint)
        canvas.drawLine(starX, starY - starR, starX, starY + starR, paint)

        // 底部三颗小点，像日历上的省略号。
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(90, 145, 135, 119)
        val dotY = top + archH * 0.82f
        for (i in -1..1) {
            canvas.drawCircle(cx + i * context.dpf(9f), dotY, context.dpf(1.5f), paint)
        }
    }
}

/** 在「今天」的卡片上落下几片安静的花瓣，只播放一次。 */
class ConfettiView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private data class Petal(
        val startX: Float,
        val delay: Float,
        val fallSpeed: Float,
        val drift: Float,
        val size: Float,
        val rotation: Float,
        val color: Int,
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val random = Random(20260520)
    private val petals = List(26) {
        Petal(
            startX = random.nextFloat(),
            delay = random.nextFloat() * 0.7f,
            fallSpeed = 0.55f + random.nextFloat() * 0.55f,
            drift = -0.04f + random.nextFloat() * 0.08f,
            size = 2.6f + random.nextFloat() * 3.4f,
            rotation = random.nextFloat() * 360f,
            color = when (random.nextInt(3)) {
                0 -> AppColors.gold
                1 -> Color.rgb(241, 215, 184)
                else -> Color.rgb(210, 139, 111)
            },
        )
    }
    private var progress = 0f
    private var animator: ValueAnimator? = null
    private var hasPlayed = false

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
        isFocusable = false
    }

    fun celebrate() {
        if (hasPlayed || width == 0) return
        hasPlayed = true
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 7_000L
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) postDelayed({ celebrate() }, 480)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (progress <= 0f || progress >= 1f) return
        val fade = when {
            progress > 0.82f -> ((1f - progress) / 0.18f).coerceIn(0f, 1f)
            else -> 1f
        }
        petals.forEach { petal ->
            val local = ((progress - petal.delay) / (1f - petal.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val y = -20f + (height + 40f) * local * petal.fallSpeed
            val sway = sin((local * 6.2f + petal.startX * 9f)) * width * 0.045f
            val x = width * petal.startX + sway + petal.drift * width * local
            val size = context.dpf(petal.size)
            val alpha = (215 * fade * (1f - local * 0.35f)).toInt().coerceIn(0, 255)
            paint.color = petal.color
            paint.alpha = alpha
            canvas.save()
            canvas.rotate(petal.rotation + local * 230f, x, y)
            canvas.drawOval(x - size * 0.45f, y - size, x + size * 0.45f, y + size, paint)
            canvas.restore()
        }
    }
}

/** 一个很轻的数字递增动画，用于首页大数字。 */
fun animateNumber(textView: android.widget.TextView, target: Long) {
    if (target <= 0) {
        textView.text = target.toString()
        return
    }
    val duration = if (target > 9999) 900L else 700L
    val animator = ValueAnimator.ofFloat(0f, target.toFloat())
    animator.duration = duration
    animator.interpolator = DecelerateInterpolator()
    animator.addUpdateListener {
        val value = (it.animatedValue as Float).toLong()
        textView.text = value.toString()
    }
    animator.start()
}
