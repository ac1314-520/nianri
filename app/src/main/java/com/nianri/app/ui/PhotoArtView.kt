package com.nianri.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.nianri.app.data.PhotoStore
import com.nianri.app.model.AccentPalette
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * 有照片时居中裁切照片；没有照片时生成一幅和该纪念日颜色相符的抽象落日画。
 * 底部的深色渐变保证所有标题都清晰可读。
 */
class PhotoArtView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var imageName: String? = null
    private var bitmap: Bitmap? = null
    private var loadToken = 0
    private var requestedKey: String? = null
    private var accent: Int = AppColors.cinnabar
    private var accentDeep: Int = AppColors.cinnabarDeep
    private var radiusPx: Float = 0f
    private var scrimEnabled = true
    private var topScrimEnabled = false
    private var fitWideImages = false
    private var backdrop: Bitmap? = null
    private var clipPath = Path()
    private var bounds = RectF()
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setImageName(name: String?) {
        val clean = name?.takeIf { it.isNotBlank() }
        if (clean == imageName && (clean == null || bitmap != null)) return
        imageName = clean
        bitmap = null
        backdrop?.recycle()
        backdrop = null
        requestedKey = null
        loadToken++
        invalidate()
        requestLoad()
    }

    fun setAccentColor(index: Int) {
        val palette = AccentPalette.of(index)
        setAccentColors(palette.color, palette.deep)
    }

    fun setAccentColors(color: Int, deep: Int) {
        if (color == accent && deep == accentDeep) return
        accent = color
        accentDeep = deep
        invalidate()
    }

    fun setCornerRadiusDp(radiusDp: Float) {
        val px = context.dpf(radiusDp)
        if (px == radiusPx) return
        radiusPx = px
        updateClip()
        invalidate()
    }

    fun setScrimEnabled(enabled: Boolean) {
        scrimEnabled = enabled
        invalidate()
    }

    fun setTopScrimEnabled(enabled: Boolean) {
        topScrimEnabled = enabled
        invalidate()
    }

    /**
     * 横图完整显示，空出来的区域用同一张图的柔化背景补齐；
     * 竖图和方图仍然铺满卡片，避免无谓的左右留白。
     */
    fun setFitWideImages(enabled: Boolean) {
        if (fitWideImages == enabled) return
        fitWideImages = enabled
        invalidate()
    }

    fun setRoundedTopOnly(enabled: Boolean) {
        roundedTopOnly = enabled
        updateClip()
        invalidate()
    }

    private var roundedTopOnly = false

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        bounds.set(0f, 0f, w.toFloat(), h.toFloat())
        updateClip()
        requestedKey = null
        requestLoad()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestLoad()
    }

    private fun updateClip() {
        clipPath = Path().apply {
            val radii = if (roundedTopOnly) {
                floatArrayOf(radiusPx, radiusPx, radiusPx, radiusPx, 0f, 0f, 0f, 0f)
            } else {
                floatArrayOf(radiusPx, radiusPx, radiusPx, radiusPx, radiusPx, radiusPx, radiusPx, radiusPx)
            }
            addRoundRect(bounds, radii, Path.Direction.CW)
        }
    }

    private fun requestLoad() {
        val name = imageName ?: return
        if (width <= 0 || height <= 0) return
        val key = "$name:${width}:${height}"
        if (key == requestedKey) return
        requestedKey = key
        val token = ++loadToken
        PhotoStore.loadImage(context, name, width, height) { loaded ->
            if (token != loadToken) return@loadImage
            if (loaded == null) {
                requestedKey = null
            }
            bitmap = loaded
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val save = canvas.save()
        canvas.clipPath(clipPath)
        val loaded = bitmap
        if (loaded != null && !loaded.isRecycled) {
            if (fitWideImages && loaded.width > loaded.height * 1.12f) {
                drawFitWithBackdrop(canvas, loaded)
            } else {
                drawCenterCrop(canvas, loaded)
            }
            drawPhotoVeil(canvas)
        } else {
            drawGeneratedLandscape(canvas)
        }
        if (scrimEnabled) drawScrim(canvas)
        if (topScrimEnabled) drawTopScrim(canvas)
        canvas.restoreToCount(save)
    }

    private fun drawFitWithBackdrop(canvas: Canvas, image: Bitmap) {
        val soft = backdropFor(image)
        val bgScale = max(width / soft.width.toFloat(), height / soft.height.toFloat())
        val bgW = soft.width * bgScale
        val bgH = soft.height * bgScale
        val bgLeft = (width - bgW) / 2f
        val bgTop = (height - bgH) / 2f
        canvas.drawBitmap(
            soft,
            null,
            RectF(bgLeft, bgTop, bgLeft + bgW, bgTop + bgH),
            bitmapPaint,
        )
        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = Color.argb(112, 18, 14, 12)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        val scale = min(width / image.width.toFloat(), height / image.height.toFloat())
        val drawW = image.width * scale
        val drawH = image.height * scale
        val left = (width - drawW) / 2f
        val top = (height - drawH) / 2f
        canvas.drawBitmap(
            image,
            null,
            RectF(left, top, left + drawW, top + drawH),
            bitmapPaint,
        )
    }

    private fun backdropFor(image: Bitmap): Bitmap {
        val current = backdrop
        if (current != null && !current.isRecycled) return current
        val targetWidth = 36
        val targetHeight = (targetWidth.toFloat() * image.height / image.width)
            .toInt()
            .coerceIn(12, 72)
        return Bitmap.createScaledBitmap(image, targetWidth, targetHeight, true).also {
            backdrop = it
        }
    }

    private fun drawCenterCrop(canvas: Canvas, image: Bitmap) {
        val scale = max(width / image.width.toFloat(), height / image.height.toFloat())
        val drawW = image.width * scale
        val drawH = image.height * scale
        val left = (width - drawW) / 2f
        val top = (height - drawH) / 2f
        canvas.drawBitmap(
            image,
            null,
            RectF(left, top, left + drawW, top + drawH),
            bitmapPaint,
        )
    }

    private fun drawPhotoVeil(canvas: Canvas) {
        val shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(0x0A2A1A12, 0x142A1A12, 0x002A1A12),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        paint.shader = shader
        paint.alpha = 255
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
    }

    private fun drawGeneratedLandscape(canvas: Canvas) {
        paint.shader = LinearGradient(
            0f, 0f, width.toFloat() * 0.35f, height.toFloat(),
            intArrayOf(accentDeep, accent, darken(accent, 0.58f)),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP,
        )
        paint.alpha = 255
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null

        val unit = min(width, height).toFloat()
        val cx = width * 0.76f
        val cy = height * 0.30f
        val sunRadius = unit * 0.18f

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(38, 255, 247, 230)
        canvas.drawCircle(cx, cy, sunRadius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = context.dpf(1f)
        paint.color = Color.argb(88, 255, 247, 230)
        canvas.drawCircle(cx, cy, sunRadius, paint)
        paint.color = Color.argb(26, 255, 247, 230)
        canvas.drawCircle(cx, cy, sunRadius * 1.65f, paint)
        canvas.drawCircle(cx, cy, sunRadius * 2.3f, paint)

        // 远处的地平线与一层薄雾，让没有照片的卡片也像一张风景。
        paint.color = Color.argb(75, 255, 247, 230)
        paint.strokeWidth = context.dpf(1.2f)
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(
            width * 0.58f, height * 0.58f,
            width * 0.94f, height * 0.58f,
            paint,
        )
        paint.color = Color.argb(30, 255, 247, 230)
        canvas.drawLine(
            width * 0.48f, height * 0.635f,
            width * 0.88f, height * 0.635f,
            paint,
        )

        val random = Random(accent.toLong())
        paint.style = Paint.Style.FILL
        repeat(54) {
            val x = random.nextFloat() * width
            val y = random.nextFloat() * height * 0.72f
            val r = context.dpf(0.45f + random.nextFloat() * 0.9f)
            paint.color = Color.argb(8 + random.nextInt(12), 255, 250, 240)
            canvas.drawCircle(x, y, r, paint)
        }

        // 一枚小小的金色星芒，给画面一点祝愿。
        val starX = width * 0.24f
        val starY = height * 0.24f
        val starR = context.dpf(5f)
        paint.strokeWidth = context.dpf(1f)
        paint.color = Color.argb(145, 226, 196, 147)
        paint.style = Paint.Style.STROKE
        canvas.drawLine(starX - starR, starY, starX + starR, starY, paint)
        canvas.drawLine(starX, starY - starR, starX, starY + starR, paint)
    }

    private fun drawScrim(canvas: Canvas) {
        val shader = LinearGradient(
            0f, height * 0.18f, 0f, height.toFloat(),
            intArrayOf(0x001B120C, 0x2E1B120C, 0xA61D1510.toInt(), 0xD91A120D.toInt()),
            floatArrayOf(0f, 0.36f, 0.72f, 1f),
            Shader.TileMode.CLAMP,
        )
        paint.shader = shader
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.shader = null
    }

    private fun drawTopScrim(canvas: Canvas) {
        val shader = LinearGradient(
            0f, 0f, 0f, height * 0.28f,
            intArrayOf(0x55000000, 0x00000000),
            null,
            Shader.TileMode.CLAMP,
        )
        paint.shader = shader
        paint.style = Paint.Style.FILL
        canvas.drawRect(0f, 0f, width.toFloat(), height * 0.28f, paint)
        paint.shader = null
    }

    private fun darken(color: Int, factor: Float): Int {
        return Color.rgb(
            (Color.red(color) * factor).toInt().coerceIn(0, 255),
            (Color.green(color) * factor).toInt().coerceIn(0, 255),
            (Color.blue(color) * factor).toInt().coerceIn(0, 255),
        )
    }
}
