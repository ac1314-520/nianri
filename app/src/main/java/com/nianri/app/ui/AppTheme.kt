package com.nianri.app.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

object AppColors {
    val paper = Color.rgb(246, 241, 232)
    val paperDeep = Color.rgb(239, 230, 217)
    val cream = Color.rgb(255, 250, 242)
    val creamDeep = Color.rgb(252, 245, 234)
    val ink = Color.rgb(39, 35, 30)
    val inkSoft = Color.rgb(86, 78, 67)
    val muted = Color.rgb(145, 135, 119)
    val faint = Color.rgb(184, 172, 155)
    val line = Color.rgb(228, 217, 202)
    val cinnabar = Color.rgb(176, 82, 56)
    val cinnabarDeep = Color.rgb(129, 54, 36)
    val cinnabarSoft = Color.rgb(241, 222, 212)
    val gold = Color.rgb(196, 162, 107)
    val sage = Color.rgb(126, 139, 116)
    val whiteWarm = Color.rgb(255, 252, 247)
}

object AppFont {
    val serif: Typeface by lazy { Typeface.create("serif", Typeface.NORMAL) }
    val serifBold: Typeface by lazy { Typeface.create("serif", Typeface.BOLD) }
    val sans: Typeface by lazy { Typeface.create("sans-serif", Typeface.NORMAL) }
    val sansMedium: Typeface by lazy { Typeface.create("sans-serif-medium", Typeface.NORMAL) }
    val sansLight: Typeface by lazy { Typeface.create("sans-serif-light", Typeface.NORMAL) }
}

fun Context.dp(value: Number): Int =
    (value.toFloat() * resources.displayMetrics.density).roundToInt()

fun Context.dpf(value: Number): Float = value.toFloat() * resources.displayMetrics.density

fun Context.contentMargin(): Int {
    val width = resources.displayMetrics.widthPixels
    return if (width < dp(620)) dp(22)
    else ((width - dp(560)) / 2).coerceAtLeast(dp(22))
}

fun newText(
    context: Context,
    text: CharSequence = "",
    sizeSp: Float,
    color: Int = AppColors.ink,
    font: Typeface = AppFont.sans,
    letterSpacing: Float = 0f,
): TextView {
    return TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        typeface = font
        this.letterSpacing = letterSpacing
        includeFontPadding = false
        setLineSpacing(context.dpf(2.5f), 1f)
        gravity = Gravity.START
    }
}

fun newLabel(
    context: Context,
    text: CharSequence,
    color: Int = AppColors.muted,
): TextView = newText(context, text, 9.5f, color, AppFont.sansMedium, 0.14f)

fun roundedDrawable(
    context: Context,
    color: Int,
    radiusDp: Float,
    strokeWidthDp: Float = 0f,
    strokeColor: Int = Color.TRANSPARENT,
): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = context.dpf(radiusDp)
        if (strokeWidthDp > 0f) setStroke(context.dpf(strokeWidthDp).roundToInt(), strokeColor)
    }
}

fun roundedDrawableWithRadii(
    context: Context,
    color: Int,
    radiiDp: FloatArray,
): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadii = radiiDp.map { context.dpf(it) }.toFloatArray()
    }
}

fun rippleOn(
    context: Context,
    content: GradientDrawable,
    rippleColor: Int = 0x1A27231E,
): RippleDrawable {
    val mask = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(Color.WHITE)
        val radii = content.cornerRadii
        if (radii != null) {
            cornerRadii = radii
        } else {
            cornerRadius = content.cornerRadius
        }
    }
    return RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask)
}

fun verticalGradient(
    colors: IntArray,
    positions: FloatArray? = null,
): GradientDrawable = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, colors).apply {
    if (positions != null) gradientType = GradientDrawable.LINEAR_GRADIENT
}

fun iconButton(
    context: Context,
    @androidx.annotation.DrawableRes iconRes: Int,
    contentDescription: String,
    sizeDp: Int = 42,
    iconSizeDp: Int = 19,
    tint: Int = AppColors.inkSoft,
    backgroundColor: Int = Color.TRANSPARENT,
    borderColor: Int? = AppColors.line,
): FrameLayout {
    val frame = FrameLayout(context).apply {
        layoutParams = ViewGroup.LayoutParams(context.dp(sizeDp), context.dp(sizeDp))
        isClickable = true
        isFocusable = true
        this.contentDescription = contentDescription
        background = rippleOn(
            context,
            roundedDrawable(
                context,
                backgroundColor,
                sizeDp / 2f,
                if (borderColor != null) 1f else 0f,
                borderColor ?: Color.TRANSPARENT,
            ),
            0x1F27231E,
        )
    }
    val icon = ImageView(context).apply {
        setImageResource(iconRes)
        setColorFilter(tint)
        scaleType = ImageView.ScaleType.FIT_CENTER
    }
    frame.addView(icon, FrameLayout.LayoutParams(context.dp(iconSizeDp), context.dp(iconSizeDp), Gravity.CENTER))
    return frame
}

fun pillButton(
    context: Context,
    label: CharSequence,
    backgroundColor: Int = AppColors.ink,
    textColor: Int = AppColors.cream,
    heightDp: Int = 54,
    textSizeSp: Float = 15f,
): TextView {
    return TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        setTextColor(textColor)
        typeface = AppFont.sansMedium
        letterSpacing = 0.03f
        includeFontPadding = false
        isClickable = true
        isFocusable = true
        background = rippleOn(
            context,
            roundedDrawable(context, backgroundColor, heightDp / 2f),
            0x33FFFFFF,
        )
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            context.dp(heightDp),
        )
    }
}

fun textButton(
    context: Context,
    label: CharSequence,
    color: Int = AppColors.cinnabar,
): TextView {
    return newText(context, label, 14f, color, AppFont.sansMedium).apply {
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
        background = rippleOn(
            context,
            roundedDrawable(context, Color.TRANSPARENT, 14f),
            0x1FB05238,
        )
    }
}

fun hairline(context: Context, color: Int = AppColors.line): View {
    return View(context).apply {
        setBackgroundColor(color)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (context.resources.displayMetrics.density * 0.72f).roundToInt().coerceAtLeast(1),
        )
    }
}

fun addPressScale(view: View, pressedScale: Float = 0.975f) {
    view.setOnTouchListener { v, event ->
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> v.animate()
                .scaleX(pressedScale).scaleY(pressedScale).setDuration(110).start()
            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_CANCEL,
            -> v.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
        }
        false
    }
}

fun TextView.applyCountStyle() {
    includeFontPadding = false
    typeface = AppFont.serif
    fontFeatureSettings = "lnum"
}

fun View.setPaddingDp(left: Int, top: Int, right: Int, bottom: Int) {
    setPadding(context.dp(left), context.dp(top), context.dp(right), context.dp(bottom))
}

fun View.setMargins(left: Int, top: Int, right: Int, bottom: Int) {
    val lp = layoutParams as? ViewGroup.MarginLayoutParams ?: return
    lp.setMargins(context.dp(left), context.dp(top), context.dp(right), context.dp(bottom))
    layoutParams = lp
}

fun Context.colorWithAlpha(color: Int, alpha: Float): Int {
    return Color.argb(
        (Color.alpha(color) * alpha).roundToInt().coerceIn(0, 255),
        Color.red(color),
        Color.green(color),
        Color.blue(color),
    )
}
