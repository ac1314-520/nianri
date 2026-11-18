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
    private var focusX = 0.5f
    private var focusY = 0.5f
    private var zoom = 1f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.dpf(1.5f)
        color = Color.argb(220, 255, 249, 239)
    }
    private var lastX = 0f
    private var lastY = 0f
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoom = (zoom * detector.scaleFactor).coerceIn(1f, 4f)
            invalidate()
            return true
        }
    })

    fun setPhoto(bitmap: Bitmap, focusX: Float, focusY: Float) {
        this.bitmap = bitmap
        this.focusX = focusX.coerceIn(0f, 1f)
        this.focusY = focusY.coerceIn(0f, 1f)
        zoom = 1f
        invalidate()
    }

    fun resetToCenter() {
        focusX = 0.5f
        focusY = 0.5f
        zoom = 1f
        invalidate()
    }

    fun focus(): Pair<Float, Float> = focusX to focusY

    private fun drawSize(): Pair<Float, Float> {
        val image = bitmap ?: return 0f to 0f
        if (width == 0 || height == 0) return 0f to 0f
        val cover = max(width / image.width.toFloat(), height / image.height.toFloat())
        val scale = cover * zoom
        return image.width * scale to image.height * scale
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(24, 20, 18))
        val image = bitmap ?: return
        val (drawW, drawH) = drawSize()
        if (drawW <= 0f || drawH <= 0f) return
        val left = (width - drawW) * focusX
        val top = (height - drawH) * focusY
        canvas.drawBitmap(
            image,
            null,
            RectF(left, top, left + drawW, top + drawH),
            paint,
        )
        canvas.drawRect(
            border.strokeWidth / 2f,
            border.strokeWidth / 2f,
            width - border.strokeWidth / 2f,
            height - border.strokeWidth / 2f,
            border,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val image = bitmap ?: return false
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress) {
                    val (drawW, drawH) = drawSize()
                    val overflowX = (drawW - width).coerceAtLeast(1f)
                    val overflowY = (drawH - height).coerceAtLeast(1f)
                    if (drawW > width) {
                        focusX = (focusX - (event.x - lastX) / overflowX).coerceIn(0f, 1f)
                    }
                    if (drawH > height) {
                        focusY = (focusY - (event.y - lastY) / overflowY).coerceIn(0f, 1f)
                    }
                    lastX = event.x
                    lastY = event.y
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
