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
import com.nianri.app.R
import com.nianri.app.update.UpdateInfo
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

