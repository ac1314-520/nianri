package com.nianri.app.ui

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import com.nianri.app.MainActivity
import com.nianri.app.R
import com.nianri.app.model.Anniversary
import com.nianri.app.model.dateLine
import com.nianri.app.update.UpdateInfo
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

data class SheetAction(
    val label: String,
    val subtitle: String? = null,
    val iconRes: Int? = null,
    val destructive: Boolean = false,
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

data class ProgressDialogHandle(
    val dialog: Dialog,
    val progressBar: ProgressBar,
    val messageView: TextView,
    val percentView: TextView,
) {
    fun show() = dialog.show()

    fun dismissSafely() {
        if (dialog.isShowing) dialog.dismiss()
    }

    fun indeterminate(message: String) {
        progressBar.isIndeterminate = true
        messageView.text = message
        percentView.text = ""
    }

    fun progress(percent: Int, message: String) {
        val safe = percent.coerceIn(0, 100)
        progressBar.isIndeterminate = false
        progressBar.progress = safe
        percentView.text = "$safe%"
        messageView.text = message
    }
}

private fun baseDialog(context: Context, maxWidthDp: Int = 420): Dialog {
    return Dialog(context).apply {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.38f)
            setGravity(Gravity.CENTER)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val screenWidth = context.resources.displayMetrics.widthPixels
        val width = min(screenWidth - context.dp(40), context.dp(maxWidthDp))
        setOnShowListener {
            window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }
}

private fun dialogCard(context: Context): LinearLayout {
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(24), context.dp(24), context.dp(24), context.dp(20))
        background = roundedDrawable(context, AppColors.cream, 28f, 0.8f, AppColors.line)
        elevation = context.dpf(20f)
    }
}

private fun dialogTitle(context: Context, text: String): TextView =
    newText(context, text, 23f, AppColors.ink, AppFont.serif).apply {
        setLineSpacing(context.dpf(2f), 1f)
    }

private fun dialogMessage(context: Context, text: String): TextView =
    newText(context, text, 13.5f, AppColors.inkSoft, AppFont.sans).apply {
        setLineSpacing(context.dpf(4.5f), 1f)
    }

private fun dialogButton(
    context: Context,
    label: String,
    filled: Boolean,
    color: Int = AppColors.ink,
): TextView {
    if (filled) {
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(AppColors.cream)
            typeface = AppFont.sansMedium
            includeFontPadding = false
            isClickable = true
            isFocusable = true
            background = rippleOn(
                context,
                roundedDrawable(context, color, 22f),
                0x33FFFFFF,
            )
            setPadding(context.dp(20), 0, context.dp(20), 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                context.dp(44),
            ).apply { marginStart = context.dp(8) }
        }
    }
    return TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(AppColors.muted)
        typeface = AppFont.sansMedium
        includeFontPadding = false
        isClickable = true
        isFocusable = true
        background = rippleOn(
            context,
            roundedDrawable(context, Color.TRANSPARENT, 22f),
            0x1A27231E,
        )
        setPadding(context.dp(16), 0, context.dp(16), 0)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            context.dp(44),
        )
    }
}

private fun buttonRow(context: Context, vararg views: View): LinearLayout {
    return LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        setPadding(0, context.dp(18), 0, 0)
        views.forEach(::addView)
    }
}

private fun dateArrow(
    context: Context,
    label: String,
    description: String,
    sizeDp: Int,
    onClick: () -> Unit,
): TextView {
    return newText(context, label, 23f, AppColors.inkSoft, AppFont.serif).apply {
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        contentDescription = description
        background = rippleOn(
            context,
            roundedDrawable(context, AppColors.cream, sizeDp / 2f, 0.8f, AppColors.line),
            0x1427231E,
        )
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(context.dp(sizeDp), context.dp(sizeDp))
    }
}

/**
 * 日期选择器不再依赖系统日历头部的小年份入口：
 * 年份放在最上方，左右箭头和“上一年/下一年”内容描述都很明确。
 */
fun showDateStepperDialog(
    context: Context,
    initial: LocalDate,
    onSelected: (LocalDate) -> Unit,
) {
    val minYear = 1900
    val maxYear = LocalDate.now().year + 100
    var year = initial.year.coerceIn(minYear, maxYear)
    var month = initial.monthValue.coerceIn(1, 12)
    var day = initial.dayOfMonth

    fun clampDay() {
        day = day.coerceIn(1, YearMonth.of(year, month).lengthOfMonth())
    }

    val dialog = baseDialog(context, 380)
    val card = dialogCard(context)
    card.addView(dialogTitle(context, "选择日期"))
    card.addView(dialogMessage(context, "年份在最上面，点两侧箭头就能切换。"), LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = context.dp(7) })

    val yearLabel = newText(context, "年份", 10f, AppColors.muted, AppFont.sansMedium, 0.12f).apply {
        setPadding(context.dp(4), context.dp(15), 0, context.dp(5))
    }
    card.addView(yearLabel)
    val yearValue = newText(context, "", 30f, AppColors.ink, AppFont.serif)
    val monthValue = newText(context, "", 17f, AppColors.ink, AppFont.sansMedium)
    val dayValue = newText(context, "", 17f, AppColors.ink, AppFont.sansMedium)
    val summary = newText(context, "", 11.5f, AppColors.cinnabar, AppFont.sansMedium).apply {
        gravity = Gravity.CENTER
        setPadding(0, context.dp(12), 0, 0)
    }

    fun updateLabels() {
        yearValue.text = "$year 年"
        monthValue.text = "$month 月"
        dayValue.text = "$day 日"
        val date = LocalDate.of(year, month, day)
        summary.text = date.format(DateTimeFormatter.ofPattern("yyyy年M月d日 · EEEE", Locale.CHINA))
    }

    val prevYear = dateArrow(context, "‹", "上一年", 46) {
        if (year > minYear) {
            year--
            clampDay()
            updateLabels()
        }
    }
    val nextYear = dateArrow(context, "›", "下一年", 46) {
        if (year < maxYear) {
            year++
            clampDay()
            updateLabels()
        }
    }
    val yearRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(5), context.dp(5), context.dp(5), context.dp(5))
        background = roundedDrawable(context, AppColors.paperDeep, 18f)
    }
    yearRow.addView(prevYear)
    yearRow.addView(yearValue, LinearLayout.LayoutParams(0, context.dp(52), 1f).apply {
        gravity = Gravity.CENTER
    })
    yearValue.gravity = Gravity.CENTER
    yearRow.addView(nextYear)
    card.addView(yearRow, LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ))

    fun adjustRow(
        label: String,
        value: TextView,
        onPrev: () -> Unit,
        onNext: () -> Unit,
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(8), 0, 0)
            addView(newText(context, label, 11f, AppColors.muted, AppFont.sans).apply {
                setPadding(context.dp(4), 0, 0, 0)
            }, LinearLayout.LayoutParams(context.dp(54), ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(dateArrow(context, "‹", "上一个$label", 34, onPrev))
            addView(value, LinearLayout.LayoutParams(0, context.dp(34), 1f).apply {
                gravity = Gravity.CENTER
            })
            value.gravity = Gravity.CENTER
            addView(dateArrow(context, "›", "下一个$label", 34, onNext))
        }
    }

    card.addView(adjustRow("月份", monthValue, {
        month = if (month > 1) month - 1 else 12
        clampDay()
        updateLabels()
    }, {
        month = if (month < 12) month + 1 else 1
        clampDay()
        updateLabels()
    }))
    card.addView(adjustRow("日期", dayValue, {
        day = if (day > 1) day - 1 else YearMonth.of(year, month).lengthOfMonth()
        updateLabels()
    }, {
        val maxDay = YearMonth.of(year, month).lengthOfMonth()
        day = if (day < maxDay) day + 1 else 1
        updateLabels()
    }))
    card.addView(summary)

    val actions = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        setPadding(0, context.dp(12), 0, 0)
    }
    val today = textButton(context, "今天", AppColors.cinnabar).apply {
        setOnClickListener {
            val now = LocalDate.now()
            year = now.year.coerceIn(minYear, maxYear)
            month = now.monthValue
            day = now.dayOfMonth
            updateLabels()
        }
    }
    actions.addView(today, LinearLayout.LayoutParams(0, context.dp(44), 1f))
    val cancel = dialogButton(context, "取消", false)
    cancel.setOnClickListener { dialog.dismiss() }
    actions.addView(cancel)
    val confirm = dialogButton(context, "选好了", true)
    confirm.setOnClickListener {
        onSelected(LocalDate.of(year, month, day))
        dialog.dismiss()
    }
    actions.addView(confirm)
    card.addView(actions)

    updateLabels()
    dialog.setContentView(card)
    dialog.show()
}


fun showInfoDialog(
    context: Context,
    title: String,
    message: String,
    positiveLabel: String = "知道了",
    onDismiss: (() -> Unit)? = null,
) {
    val dialog = baseDialog(context)
    val card = dialogCard(context)
    card.addView(dialogTitle(context, title))
    card.addView(dialogMessage(context, message), LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = context.dp(10) })
    val positive = dialogButton(context, positiveLabel, true)
    positive.setOnClickListener { dialog.dismiss() }
    card.addView(buttonRow(context, positive))
    dialog.setContentView(card)
    dialog.setOnDismissListener { onDismiss?.invoke() }
    dialog.show()
}

fun showConfirmDialog(
    context: Context,
    title: String,
    message: String,
    confirmLabel: String = "确定",
    cancelLabel: String = "取消",
    destructive: Boolean = false,
    onConfirm: () -> Unit,
) {
    val dialog = baseDialog(context)
    val card = dialogCard(context)
    card.addView(dialogTitle(context, title))
    card.addView(dialogMessage(context, message), LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = context.dp(10) })
    val cancel = dialogButton(context, cancelLabel, false)
    val confirm = dialogButton(
        context,
        confirmLabel,
        true,
        if (destructive) AppColors.cinnabar else AppColors.ink,
    )
    cancel.setOnClickListener { dialog.dismiss() }
    confirm.setOnClickListener {
        dialog.dismiss()
        onConfirm()
    }
    card.addView(buttonRow(context, cancel, confirm))
    dialog.setContentView(card)
    dialog.show()
}

/**
 * @param onConfirm 返回 true 时关闭对话框；返回 false 可保留对话框继续编辑。
 */
fun showInputDialog(
    context: Context,
    title: String,
    message: String,
    initialValue: String,
    hint: String,
    inputKind: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
    positiveLabel: String = "保存",
    onConfirm: (String) -> Boolean,
) {
    val dialog = baseDialog(context)
    val card = dialogCard(context)
    card.addView(dialogTitle(context, title))
    card.addView(dialogMessage(context, message), LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = context.dp(10) })
    val input = EditText(context).apply {
        this.hint = hint
        setText(initialValue)
        setSelection(text.length)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
        setTextColor(AppColors.ink)
        setHintTextColor(AppColors.faint)
        typeface = AppFont.sans
        includeFontPadding = false
        this.inputType = inputKind
        imeOptions = EditorInfo.IME_ACTION_DONE
        minLines = 1
        maxLines = 3
        setPadding(context.dp(14), context.dp(12), context.dp(14), context.dp(12))
        background = roundedDrawable(context, AppColors.creamDeep, 14f, 1f, AppColors.line)
        highlightColor = 0x33B05238
    }
    card.addView(input, LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = context.dp(16) })

    val cancel = dialogButton(context, "取消", false)
    val confirm = dialogButton(context, positiveLabel, true)
    cancel.setOnClickListener { dialog.dismiss() }
    fun submit() {
        if (onConfirm(input.text.toString().trim())) dialog.dismiss()
    }
    confirm.setOnClickListener { submit() }
    input.setOnEditorActionListener { _, actionId, _ ->
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            submit()
            true
        } else false
    }
    card.addView(buttonRow(context, cancel, confirm))
    dialog.setContentView(card)
    dialog.window?.setSoftInputMode(
        WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
    )
    dialog.show()
    input.requestFocus()
    input.postDelayed({
        val imm = context.getSystemService(
            android.view.inputmethod.InputMethodManager::class.java,
        )
        imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }, 140L)
}

fun showActionSheet(
    context: Context,
    title: String?,
    actions: List<SheetAction>,
) {
    val dialog = Dialog(context).apply {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.38f)
            setGravity(Gravity.BOTTOM)
            setWindowAnimations(android.R.style.Animation_Dialog)
        }
    }
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(18), context.dp(10), context.dp(18), context.dp(18))
        background = roundedDrawableWithRadii(
            context,
            AppColors.cream,
            floatArrayOf(28f, 28f, 28f, 28f, 0f, 0f, 0f, 0f),
        )
    }
    root.addView(View(context).apply {
        setBackgroundColor(Color.argb(40, 39, 35, 30))
        layoutParams = LinearLayout.LayoutParams(context.dp(36), context.dp(4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            bottomMargin = context.dp(18)
        }
    })
    if (!title.isNullOrBlank()) {
        root.addView(newText(context, title, 18f, AppColors.ink, AppFont.serif).apply {
            setPadding(context.dp(6), 0, context.dp(6), context.dp(10))
        })
    }
    actions.forEach { action ->
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(12), 0, context.dp(12), 0)
            isClickable = true
            isFocusable = true
            background = rippleOn(
                context,
                roundedDrawable(context, Color.TRANSPARENT, 16f),
                if (action.destructive) 0x1FB05238 else 0x1A27231E,
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                context.dp(56),
            )
        }
        action.iconRes?.let { iconRes ->
            val icon = ImageView(context).apply {
                setImageResource(iconRes)
                setColorFilter(if (action.destructive) AppColors.cinnabar else AppColors.inkSoft)
            }
            row.addView(icon, LinearLayout.LayoutParams(context.dp(19), context.dp(19)).apply {
                marginEnd = context.dp(14)
            })
        }
        val labels = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        labels.addView(
            newText(
                context,
                action.label,
                15f,
                if (action.destructive) AppColors.cinnabar else AppColors.ink,
                AppFont.sansMedium,
            )
        )
        action.subtitle?.let {
            labels.addView(newText(context, it, 11.5f, AppColors.muted).apply {
                setPadding(0, context.dp(3), 0, 0)
            })
        }
        row.addView(labels)
        if (action.selected) {
            val check = ImageView(context).apply {
                setImageResource(com.nianri.app.R.drawable.ic_check)
                setColorFilter(AppColors.cinnabar)
            }
            row.addView(check, LinearLayout.LayoutParams(context.dp(19), context.dp(19)))
        }
        row.setOnClickListener {
            dialog.dismiss()
            action.onClick()
        }
        root.addView(row)
    }
    root.addView(textButton(context, "取消", AppColors.muted).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            context.dp(48),
        ).apply { topMargin = context.dp(6) }
        setOnClickListener { dialog.dismiss() }
    })
    dialog.setContentView(root)
    dialog.setOnShowListener {
        val width = context.resources.displayMetrics.widthPixels
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    dialog.show()
}

fun createProgressDialog(
    context: Context,
    title: String,
    message: String,
    indeterminate: Boolean = true,
): ProgressDialogHandle {
    val dialog = baseDialog(context, 360)
    dialog.setCancelable(false)
    dialog.setCanceledOnTouchOutside(false)
    val card = dialogCard(context)
    card.addView(newText(context, title, 20f, AppColors.ink, AppFont.serif))
    val bodyRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, context.dp(16), 0, 0)
    }
    val bar = ProgressBar(
        context,
        null,
        android.R.attr.progressBarStyleHorizontal,
    ).apply {
        isIndeterminate = indeterminate
        max = 100
        progressTintList = ColorStateList.valueOf(AppColors.cinnabar)
        progressBackgroundTintList = ColorStateList.valueOf(AppColors.line)
        layoutParams = LinearLayout.LayoutParams(0, context.dp(4), 1f)
    }
    bodyRow.addView(bar)
    val percent = newText(context, "", 12f, AppColors.cinnabar, AppFont.sansMedium).apply {
        gravity = Gravity.END
        minWidth = context.dp(42)
    }
    bodyRow.addView(percent)
    card.addView(bodyRow)
    val messageView = dialogMessage(context, message).apply {
        setPadding(0, context.dp(12), 0, 0)
    }
    card.addView(messageView)
    dialog.setContentView(card)
    return ProgressDialogHandle(dialog, bar, messageView, percent)
}

fun showScrollableInfoDialog(
    context: Context,
    title: String,
    message: String,
    positiveLabel: String = "知道了",
    extraView: View? = null,
) {
    val dialog = baseDialog(context)
    val card = dialogCard(context)
    card.addView(dialogTitle(context, title))
    card.addView(dialogMessage(context, message), LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = context.dp(10) })
    extraView?.let {
        card.addView(it, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = context.dp(14) })
    }
    val positive = dialogButton(context, positiveLabel, true)
    positive.setOnClickListener { dialog.dismiss() }
    card.addView(buttonRow(context, positive))
    dialog.setContentView(card)
    dialog.show()
}

fun showUpdateAvailableDialog(
    context: Context,
    info: UpdateInfo,
    onDownload: () -> Unit,
) {
    val dialog = baseDialog(context)
    val card = dialogCard(context)
    val mark = FrameLayout(context).apply {
        background = roundedDrawable(context, AppColors.cinnabarSoft, 22f)
    }
    mark.addView(ImageView(context).apply {
        setImageResource(R.drawable.ic_download)
        setColorFilter(AppColors.cinnabarDeep)
    }, FrameLayout.LayoutParams(context.dp(21), context.dp(21), Gravity.CENTER))
    card.addView(mark, LinearLayout.LayoutParams(context.dp(44), context.dp(44)))
    card.addView(newText(context, "NEW VERSION", 8.5f, AppColors.cinnabar, AppFont.sansMedium, 0.15f).apply {
        setPadding(0, context.dp(13), 0, 0)
    })
    card.addView(dialogTitle(context, "新的念日，已经写好了。").apply {
        setPadding(0, context.dp(5), 0, 0)
    })
    card.addView(dialogMessage(
        context,
        "版本 ${info.versionName}（${info.versionCode}）" +
            if (info.publishedAt.isNotBlank()) "  ·  ${info.publishedAt}" else "",
    ).apply {
        setPadding(0, context.dp(7), 0, 0)
    })
    if (info.notes.isNotBlank()) {
        card.addView(TextView(context).apply {
            text = info.notes.take(2000)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTextColor(AppColors.inkSoft)
            typeface = AppFont.sans
            includeFontPadding = false
            setLineSpacing(context.dpf(4f), 1f)
            maxLines = 9
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(context.dp(13), context.dp(11), context.dp(13), context.dp(11))
            background = roundedDrawable(context, AppColors.paper, 13f)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = context.dp(13) })
    }
    val security = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.TOP
        setPadding(0, context.dp(13), 0, 0)
    }
    security.addView(ImageView(context).apply {
        setImageResource(R.drawable.ic_shield)
        setColorFilter(AppColors.sage)
    }, LinearLayout.LayoutParams(context.dp(15), context.dp(15)).apply {
        topMargin = context.dp(1)
    })
    security.addView(newText(
        context,
        "下载完成后会校验 SHA-256 文件指纹与 APK 签名，通过后才会交给系统安装。",
        9.8f,
        AppColors.muted,
        AppFont.sans,
    ).apply {
        setPadding(context.dp(7), 0, 0, 0)
        setLineSpacing(context.dpf(3f), 1f)
    }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    card.addView(security)

    val cancel = dialogButton(context, "稍后", false)
    val download = dialogButton(context, "下载更新", true)
    cancel.setOnClickListener { dialog.dismiss() }
    download.setOnClickListener {
        dialog.dismiss()
        onDownload()
    }
    card.addView(buttonRow(context, cancel, download))
    dialog.setContentView(card)
    dialog.show()
}

fun showInstallReadyDialog(
    context: Context,
    versionName: String,
    onInstall: () -> Unit,
) {
    val dialog = baseDialog(context)
    val card = dialogCard(context)
    card.addView(newText(context, "READY TO INSTALL", 8.5f, AppColors.cinnabar, AppFont.sansMedium, 0.15f))
    card.addView(dialogTitle(context, "更新已经准备好。").apply {
        setPadding(0, context.dp(6), 0, 0)
    })
    card.addView(dialogMessage(
        context,
        "版本 $versionName 已下载完成，文件指纹与签名校验通过。安装时系统可能会再次确认，这是 Android 的保护机制。",
    ).apply {
        setPadding(0, context.dp(9), 0, 0)
    })
    val cancel = dialogButton(context, "稍后", false)
    val install = dialogButton(context, "安装更新", true)
    cancel.setOnClickListener { dialog.dismiss() }
    install.setOnClickListener {
        dialog.dismiss()
        onInstall()
    }
    card.addView(buttonRow(context, cancel, install))
    dialog.setContentView(card)
    dialog.show()
}


fun showLedgerEditorDialog(
    context: MainActivity,
    draft: LedgerDraft?,
    onSave: (LedgerDraft) -> Unit,
) {
    var income = draft?.income ?: false
    var category = LedgerCategories.normalize(draft?.category ?: "餐饮", income)
    var epochDay = draft?.epochDay ?: LocalDate.now().toEpochDay()

    val dialog = baseDialog(context, 390)
    val card = dialogCard(context)
    card.addView(dialogTitle(context, if (draft?.id == null) "记一笔" else "编辑这笔"))
    card.addView(dialogMessage(
        context,
        if (draft?.id == null) "分类更细，记起来也更清楚。" else "金额、分类、日期和备注都可以改。",
    ), LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = context.dp(6) })

    val typeRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, context.dp(12), 0, 0)
    }
    lateinit var expensePill: TextView
    lateinit var incomePill: TextView
    fun stylePill(view: TextView, selected: Boolean) {
        view.setTextColor(if (selected) AppColors.cinnabar else AppColors.muted)
        view.typeface = if (selected) AppFont.sansMedium else AppFont.sans
        view.background = rippleOn(
            context,
            roundedDrawable(
                context,
                if (selected) AppColors.cinnabarSoft else Color.TRANSPARENT,
                16f,
                if (selected) 0f else 0.8f,
                if (selected) Color.TRANSPARENT else AppColors.line,
            ),
            0x1FB05238,
        )
    }
    fun pill(label: String, selected: Boolean): TextView =
        newText(context, label, 12f, AppColors.inkSoft, AppFont.sans).apply {
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            stylePill(this, selected)
        }
    expensePill = pill("支出", !income)
    incomePill = pill("收入", income)
    typeRow.addView(expensePill, LinearLayout.LayoutParams(0, context.dp(36), 1f).apply {
        marginEnd = context.dp(6)
    })
    typeRow.addView(incomePill, LinearLayout.LayoutParams(0, context.dp(36), 1f))
    card.addView(typeRow)

    val amount = textInput(
        context,
        "金额（元）",
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
    ).apply {
        setText(draft?.takeIf { it.amountCents > 0 }?.let { formatLedgerAmount(it.amountCents) } ?: "")
        setSelection(text.length)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20f)
    }
    card.addView(amount, LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        context.dp(52),
    ).apply { topMargin = context.dp(10) })

    val dateRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
        isFocusable = true
        contentDescription = "选择日期"
        background = rippleOn(
            context,
            roundedDrawable(context, AppColors.cream, 14f, 0.8f, AppColors.line),
            0x1427231E,
        )
        setPadding(context.dp(13), 0, context.dp(10), 0)
    }
    val dateText = newText(context, "", 12.5f, AppColors.inkSoft, AppFont.sansMedium)
    dateRow.addView(ImageView(context).apply {
        setImageResource(R.drawable.ic_calendar)
        setColorFilter(AppColors.cinnabar)
    }, LinearLayout.LayoutParams(context.dp(17), context.dp(17)))
    dateRow.addView(dateText, LinearLayout.LayoutParams(
        0,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        1f,
    ).apply { marginStart = context.dp(9) })
    dateRow.addView(ImageView(context).apply {
        setImageResource(R.drawable.ic_chevron_right)
        setColorFilter(AppColors.faint)
    }, LinearLayout.LayoutParams(context.dp(16), context.dp(16)))
    fun refreshDate() {
        dateText.text = epochDate(epochDay, "yyyy年M月d日 · EEEE")
    }
    dateRow.setOnClickListener {
        showDateStepperDialog(context, LocalDate.ofEpochDay(epochDay)) { date ->
            epochDay = date.toEpochDay()
            refreshDate()
        }
    }
    card.addView(dateRow, LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        context.dp(46),
    ).apply { topMargin = context.dp(9) })

    card.addView(newText(context, "分类", 10f, AppColors.muted, AppFont.sansMedium, 0.1f).apply {
        setPadding(context.dp(2), context.dp(12), 0, context.dp(5))
    })
    val categoryGrid = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }
    fun rebuildCategories() {
        categoryGrid.removeAllViews()
        val options = LedgerCategories.categories(income)
        options.chunked(4).forEachIndexed { rowIndex, rowItems ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, if (rowIndex == 0) 0 else context.dp(4), 0, 0)
            }
            rowItems.forEachIndexed { index, option ->
                val chip = categoryChip(context, option.label, option.label == category) {
                    category = option.label
                    rebuildCategories()
                }
                row.addView(chip, LinearLayout.LayoutParams(0, context.dp(34), 1f).apply {
                    if (index < 3) marginEnd = context.dp(4)
                })
            }
            repeat(4 - rowItems.size) {
                row.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            }
            categoryGrid.addView(row)
        }
    }
    rebuildCategories()
    card.addView(categoryGrid)

    val note = textInput(context, "备注（可选）").apply {
        setText(draft?.note.orEmpty())
        setSelection(text.length)
    }
    card.addView(note, LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        context.dp(46),
    ).apply { topMargin = context.dp(9) })

    fun refreshType() {
        stylePill(expensePill, !income)
        stylePill(incomePill, income)
        category = LedgerCategories.categories(income).first().label
        rebuildCategories()
    }
    expensePill.setOnClickListener {
        if (income) {
            income = false
            refreshType()
        }
    }
    incomePill.setOnClickListener {
        if (!income) {
            income = true
            refreshType()
        }
    }

    val cancel = dialogButton(context, "取消", false)
    cancel.setOnClickListener { dialog.dismiss() }
    val save = dialogButton(context, "保存", true)
    save.setOnClickListener {
        val cents = centsFromInput(amount.text.toString())
        if (cents == null) {
            context.showNotice("填一个 0 到 1000 万之间的金额")
            return@setOnClickListener
        }
        val finalCategory = LedgerCategories.normalize(category, income)
        onSave(
            LedgerDraft(
                id = draft?.id,
                income = income,
                amountCents = cents,
                category = finalCategory,
                epochDay = epochDay,
                note = note.text.toString().trim(),
            ),
        )
        dialog.dismiss()
    }
    card.addView(buttonRow(context, cancel, save))

    refreshDate()
    dialog.setContentView(card)
    dialog.show()
}


fun showSortDialog(
    context: Context,
    events: List<Anniversary>,
    onSave: (List<String>) -> Unit,
) {
    if (events.size < 2) return
    val dialog = baseDialog(context, 390)
    val card = dialogCard(context)
    card.addView(dialogTitle(context, "调整顺序"))
    card.addView(
        dialogMessage(context, "长按首页卡片可以再次打开这里。用箭头把最重要的日子放到前面，保存后会立即生效。"),
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = context.dp(9) },
    )

    val ordered = events.toMutableList()
    val rows = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }
    val scroll = ScrollView(context).apply {
        isFillViewport = false
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        addView(rows, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
    }
    card.addView(
        scroll,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            context.dp(minOf(events.size, 5) * 58),
        ).apply { topMargin = context.dp(12) },
    )

    fun rebuild() {
        rows.removeAllViews()
        ordered.forEachIndexed { index, event ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(context.dp(11), context.dp(7), context.dp(7), context.dp(7))
                background = rippleOn(
                    context,
                    roundedDrawable(context, AppColors.paperDeep, 16f),
                    0x1427231E,
                )
            }
            row.addView(
                newText(context, "%02d".format(index + 1), 9.5f, AppColors.faint, AppFont.sansMedium, 0.12f),
                LinearLayout.LayoutParams(context.dp(26), ViewGroup.LayoutParams.WRAP_CONTENT),
            )
            val textBox = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(context.dp(5), 0, context.dp(8), 0)
            }
            textBox.addView(
                newText(context, event.title, 14f, AppColors.ink, AppFont.sansMedium).apply {
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                },
            )
            textBox.addView(
                newText(context, event.dateLine(), 9.5f, AppColors.muted, AppFont.sans).apply {
                    setPadding(0, context.dp(3), 0, 0)
                },
            )
            row.addView(
                textBox,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )

            val up = newText(
                context,
                "↑",
                19f,
                if (index > 0) AppColors.inkSoft else AppColors.faint,
                AppFont.serif,
            ).apply {
                gravity = Gravity.CENTER
                isClickable = index > 0
                isFocusable = index > 0
                contentDescription = "上移${event.title}"
                alpha = if (index > 0) 1f else 0.35f
                setOnClickListener {
                    if (index > 0) {
                        ordered.add(index - 1, ordered.removeAt(index))
                        rebuild()
                    }
                }
            }
            row.addView(up, LinearLayout.LayoutParams(context.dp(34), context.dp(38)))

            val down = newText(
                context,
                "↓",
                19f,
                if (index < ordered.lastIndex) AppColors.inkSoft else AppColors.faint,
                AppFont.serif,
            ).apply {
                gravity = Gravity.CENTER
                isClickable = index < ordered.lastIndex
                isFocusable = index < ordered.lastIndex
                contentDescription = "下移${event.title}"
                alpha = if (index < ordered.lastIndex) 1f else 0.35f
                setOnClickListener {
                    if (index < ordered.lastIndex) {
                        ordered.add(index + 1, ordered.removeAt(index))
                        rebuild()
                    }
                }
            }
            row.addView(down, LinearLayout.LayoutParams(context.dp(34), context.dp(38)))

            rows.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    context.dp(52),
                ).apply { if (index > 0) topMargin = context.dp(7) },
            )
        }
    }
    rebuild()

    val cancel = dialogButton(context, "取消", false)
    cancel.setOnClickListener { dialog.dismiss() }
    val save = dialogButton(context, "保存顺序", true)
    save.setOnClickListener {
        onSave(ordered.map { it.id })
        dialog.dismiss()
    }
    card.addView(buttonRow(context, cancel, save))
    dialog.setContentView(card)
    dialog.show()
}
