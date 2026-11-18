package com.nianri.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.nianri.app.MainActivity
import com.nianri.app.data.PhotoStore
import kotlin.math.max

/**
 * 固定裁剪框，拖动照片决定封面中心；保存的是焦点而不是一张新图，
 * 这样首页、详情、编辑页和以后新增的封面比例都能复用同一位置。
 */
class PhotoFocusView(context: Context) : View(context) {

    private var bitmap: Bitmap? = null
    private var matrix = Matrix()
    private val inverse = Matrix()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpf(1.5f)
        color = Color.argb(220, 255, 249, 239)
    }
    private var lastX = 0f
    private var lastY = 0f
    private var initialFocusX = 0.5f
    private var initialFocusY = 0.5f
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val image = bitmap ?: return false
            matrix.postScale(detector.scaleFactor, detector.scaleFactor, detector.focusX, detector.focusY)
            constrain(image)
            invalidate()
            return true
        }
    })

    fun setPhoto(bitmap: Bitmap, focusX: Float, focusY: Float) {
        this.bitmap = bitmap
        initialFocusX = focusX.coerceIn(0f, 1f)
        initialFocusY = focusY.coerceIn(0f, 1f)
        resetMatrix()
        invalidate()
    }

    fun resetToCenter() {
        initialFocusX = 0.5f
        initialFocusY = 0.5f
        resetMatrix()
        invalidate()
    }

    fun focus(): Pair<Float, Float> {
        val image = bitmap ?: return 0.5f to 0.5f
        matrix.invert(inverse)
        val point = floatArrayOf(width / 2f, height / 2f)
        inverse.mapPoints(point)
        return (point[0] / image.width).coerceIn(0f, 1f) to
            (point[1] / image.height).coerceIn(0f, 1f)
    }

    private fun resetMatrix() {
        val image = bitmap ?: return
        if (width == 0 || height == 0) return
        val scale = max(width / image.width.toFloat(), height / image.height.toFloat())
        matrix.reset()
        matrix.setScale(scale, scale)
        matrix.postTranslate(
            (width - image.width * scale) * initialFocusX,
            (height - image.height * scale) * initialFocusY,
        )
        constrain(image)
    }

    private fun constrain(image: Bitmap) {
        val rect = RectF(0f, 0f, image.width.toFloat(), image.height.toFloat())
        matrix.mapRect(rect)
        var dx = 0f
        var dy = 0f
        if (rect.width() < width) {
            val up = width / rect.width()
            matrix.postScale(up, up, width / 2f, height / 2f)
            matrix.mapRect(rect)
        }
        if (rect.height() < height) {
            val up = height / rect.height()
            matrix.postScale(up, up, width / 2f, height / 2f)
            matrix.mapRect(rect)
        }
        if (rect.left > 0f) dx -= rect.left
        if (rect.right < width) dx += width - rect.right
        if (rect.top > 0f) dy -= rect.top
        if (rect.bottom < height) dy += height - rect.bottom
        if (dx != 0f || dy != 0f) matrix.postTranslate(dx, dy)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetMatrix()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(24, 20, 18))
        val image = bitmap ?: return
        canvas.drawBitmap(image, matrix, paint)
        canvas.drawRect(
            border.strokeWidth / 2f,
            border.strokeWidth / 2f,
            width - border.strokeWidth / 2f,
            height - border.strokeWidth / 2f,
            border,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (bitmap == null) return false
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    lastX = event.x
                    lastY = event.y
                    matrix.postTranslate(dx, dy)
                    constrain(bitmap!!)
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }
}

fun showPhotoFocusDialog(
    host: MainActivity,
    photoName: String,
    focusX: Float,
    focusY: Float,
    onConfirm: (Float, Float) -> Unit,
) {
    if (PhotoStore.isAnimated(photoName)) {
        host.showNotice("动图会保持整段播放，暂不支持调整封面位置")
        return
    }
    PhotoStore.loadImage(host, photoName, 2000, 2000) { bitmap ->
        if (bitmap == null) {
            host.showNotice("照片读取失败，无法调整位置")
            return@loadImage
        }
        val dialog = baseDialog(host, 390)
        val card = dialogCard(host)
        card.addView(dialogTitle(host, "调整封面"))
        card.addView(dialogMessage(
            host,
            "拖动照片（或双指缩放），让想保留的画面落在框内。封面会按这个中心显示。",
        ), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(6) })

        val holder = FrameLayout(host).apply {
            background = roundedDrawable(host, Color.rgb(24, 20, 18), 18f)
            clipToOutline = true
        }
        val crop = PhotoFocusView(host).apply {
            setPhoto(bitmap, focusX, focusY)
        }
        holder.addView(crop, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
        card.addView(holder, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            host.dp(280),
        ).apply { topMargin = host.dp(12) })

        val cancel = dialogButton(host, "取消", false)
        val center = dialogButton(host, "居中", false)
        val use = dialogButton(host, "使用这个位置", true)
        cancel.setOnClickListener { dialog.dismiss() }
        center.setOnClickListener { crop.resetToCenter() }
        use.setOnClickListener {
            val focus = crop.focus()
            onConfirm(focus.first, focus.second)
            dialog.dismiss()
        }
        card.addView(buttonRow(host, cancel, center, use))
        dialog.setContentView(card)
        dialog.show()
    }
}
