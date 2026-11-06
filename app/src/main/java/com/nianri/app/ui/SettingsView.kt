package com.nianri.app.ui

import android.Manifest
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.nianri.app.BuildConfig
import com.nianri.app.MainActivity
import com.nianri.app.R
import com.nianri.app.data.AppPreferences
import com.nianri.app.reminder.ReminderEngine
import com.nianri.app.reminder.ReminderScheduler
import com.nianri.app.util.parseReminderTime
import java.net.URI

class SettingsView(
    private val host: MainActivity,
) : FrameLayout(host) {

    private lateinit var notificationStatus: TextView
    private lateinit var exactStatus: TextView
    private lateinit var batteryStatus: TextView
    private lateinit var timeValue: TextView
    private lateinit var updateSourceValue: TextView
    private lateinit var checkUpdateValue: TextView
    private lateinit var autoUpdateSwitch: NianRiSwitch
    private var suppressAutoToggle = false

    init {
        background = ColorDrawable(AppColors.paper)
        val main = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
        }
        addView(main, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        main.addView(buildTopBar())

        val scroll = ScrollView(host).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
        }
        val column = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.contentMargin(), 0, host.contentMargin(), host.dp(60))
        }
        scroll.addView(column, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        main.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))

        column.addView(newText(
            host,
            "让提醒准时，让心意安静。",
            12f,
            AppColors.muted,
            AppFont.serif,
            0.02f,
        ).apply {
            setPadding(0, host.dp(2), 0, 0)
        })

        column.addView(sectionLabel("提醒"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(24) })
        column.addView(buildReminderGroup(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(10) })

        if (isXiaomiFamily()) {
            column.addView(buildXiaomiCard(), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = host.dp(12) })
        }

        column.addView(sectionLabel("在线更新"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(26) })
        column.addView(buildUpdateGroup(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(10) })

        column.addView(sectionLabel("数据与隐私"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(26) })
        column.addView(buildPrivacyCard(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(10) })

        column.addView(sectionLabel("关于"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(26) })
        column.addView(buildAboutCard(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(10) })

        column.addView(newText(
            host,
            "愿所有重要的日子，\n都被温柔以待。",
            12f,
            AppColors.faint,
            AppFont.serif,
        ).apply {
            gravity = Gravity.CENTER
            setLineSpacing(host.dpf(6f), 1f)
            setPadding(0, host.dp(34), 0, host.dp(16))
        })

        refresh()
    }

    private fun buildTopBar(): View {
        return LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(host.contentMargin(), host.dp(12), host.contentMargin(), host.dp(8))
            addView(iconButton(
                host,
                R.drawable.ic_arrow_back,
                "返回",
                42,
                19,
                AppColors.inkSoft,
                Color.TRANSPARENT,
                AppColors.line,
            ).apply {
                setOnClickListener { host.returnHome() }
            })
            addView(newText(host, "设置", 25f, AppColors.ink, AppFont.serif), LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ).apply { marginStart = host.dp(13) })
            addView(newText(host, "v${BuildConfig.VERSION_NAME}", 9.5f, AppColors.faint, AppFont.sans, 0.05f))
        }
    }

    private fun sectionLabel(text: String): View {
        return newText(host, text, 12f, AppColors.inkSoft, AppFont.sansMedium, 0.08f).apply {
            setPadding(host.dp(4), 0, 0, 0)
        }
    }

    private fun groupCard(): LinearLayout {
        return LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(host, AppColors.cream, 22f, 0.8f, AppColors.line)
            clipToOutline = true
        }
    }

    private fun divider(): View {
        return hairline(host, Color.argb(140, 228, 217, 202)).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                1,
            ).apply { leftMargin = host.dp(16); rightMargin = host.dp(16) }
        }
    }

    private fun settingRow(
        title: String,
        subtitle: String? = null,
        trailing: View? = null,
        onClick: (() -> Unit)? = null,
    ): View {
        val row = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(host.dp(16), host.dp(11), host.dp(16), host.dp(11))
            if (onClick != null) {
                isClickable = true
                isFocusable = true
                background = rippleOn(host, roundedDrawable(host, Color.TRANSPARENT, 18f), 0x1427231E)
                setOnClickListener { onClick() }
            }
        }
        val labels = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
        }
        labels.addView(newText(host, title, 13.5f, AppColors.ink, AppFont.sansMedium))
        if (subtitle != null) {
            labels.addView(newText(host, subtitle, 9.8f, AppColors.muted, AppFont.sans).apply {
                setPadding(0, host.dp(4), 0, 0)
            })
        }
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        trailing?.let {
            row.addView(it, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = host.dp(12) })
        }
        return row
    }

    private fun valueText(initial: String = ""): TextView {
        return newText(host, initial, 10.8f, AppColors.muted, AppFont.sansMedium).apply {
            gravity = Gravity.END
        }
    }

    private fun chevron(): ImageView {
        return ImageView(host).apply {
            setImageResource(R.drawable.ic_chevron_right)
            setColorFilter(AppColors.faint)
        }
    }

    private fun statusTrailing(value: TextView): LinearLayout {
        return LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(value)
            addView(chevron(), LinearLayout.LayoutParams(host.dp(16), host.dp(16)).apply {
                marginStart = host.dp(4)
            })
        }
    }

    private fun buildReminderGroup(): View {
        val card = groupCard()
        notificationStatus = valueText()
        card.addView(settingRow(
            "通知权限",
            "提醒送达的前提",
            statusTrailing(notificationStatus),
        ) { host.openNotificationSettings() })
        card.addView(divider())

        exactStatus = valueText()
        card.addView(settingRow(
            "精确提醒",
            "关闭时，系统仍可能延迟送达",
            statusTrailing(exactStatus),
        ) { host.openExactAlarmSettings() })
        card.addView(divider())

        batteryStatus = valueText()
        card.addView(settingRow(
            "电池优化",
            "允许应用在后台完成每日检查",
            statusTrailing(batteryStatus),
        ) { host.openBatteryOptimizationSettings() })
        card.addView(divider())

        timeValue = valueText()
        card.addView(settingRow(
            "每日检查时间",
            "所有纪念日共用这个时间",
            statusTrailing(timeValue),
        ) { openReminderTimePicker() })
        return card
    }

    private fun buildXiaomiCard(): View {
        val card = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(17), host.dp(15), host.dp(17), host.dp(14))
            background = roundedDrawable(
                host,
                Color.rgb(247, 237, 227),
                22f,
                0.8f,
                Color.argb(110, 176, 82, 56),
            )
        }
        val head = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(ImageView(host).apply {
            setImageResource(R.drawable.ic_shield)
            setColorFilter(AppColors.cinnabar)
        }, LinearLayout.LayoutParams(host.dp(17), host.dp(17)))
        head.addView(newText(host, "HyperOS 提醒保障", 13f, AppColors.cinnabarDeep, AppFont.sansMedium).apply {
            setPadding(host.dp(8), 0, 0, 0)
        })
        card.addView(head)
        card.addView(newText(
            host,
            "小米系统会管理后台活动。允许「自启动」，并把省电策略改为「无限制」，提醒会更准时。",
            10.8f,
            AppColors.inkSoft,
            AppFont.sans,
        ).apply {
            setPadding(0, host.dp(8), 0, 0)
            setLineSpacing(host.dpf(4f), 1f)
        })
        card.addView(textButton(host, "打开自启动设置 →", AppColors.cinnabarDeep).apply {
            setPadding(0, host.dp(9), 0, 0)
            gravity = Gravity.START
            setOnClickListener { host.openMiuiAutostart() }
        })
        return card
    }

    private fun buildUpdateGroup(): View {
        val card = groupCard()
        checkUpdateValue = valueText()
        val checkRow = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(host.dp(16), host.dp(11), host.dp(16), host.dp(11))
            isClickable = true
            isFocusable = true
            background = rippleOn(host, roundedDrawable(host, Color.TRANSPARENT, 18f), 0x1427231E)
            setOnClickListener { host.checkForUpdates(manual = true) }
        }
        val labels = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
        }
        labels.addView(newText(host, "检查更新", 13.5f, AppColors.ink, AppFont.sansMedium))
        labels.addView(newText(host, "手动检查并安全下载新版本", 9.8f, AppColors.muted, AppFont.sans).apply {
            setPadding(0, host.dp(4), 0, 0)
        })
        checkRow.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        checkRow.addView(checkUpdateValue, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = host.dp(12) })
        checkRow.addView(ImageView(host).apply {
            setImageResource(R.drawable.ic_refresh)
            setColorFilter(AppColors.faint)
        }, LinearLayout.LayoutParams(host.dp(16), host.dp(16)).apply {
            marginStart = host.dp(6)
        })
        card.addView(checkRow)
        card.addView(divider())

        autoUpdateSwitch = NianRiSwitch(host).apply {
            contentDescription = "自动检查更新开关"
            setChecked(AppPreferences.get(host).autoCheckUpdates)
            onCheckedChange = { checked ->
                if (!suppressAutoToggle) handleAutoUpdateToggle(checked)
            }
        }
        card.addView(settingRow(
            "自动检查",
            "每天最多检查一次，发现更新时再询问你",
            autoUpdateSwitch,
        ))
        card.addView(divider())

        updateSourceValue = valueText()
        card.addView(settingRow(
            "更新源",
            "你自己的 HTTPS 更新清单",
            statusTrailing(updateSourceValue),
        ) { host.showUpdateSourceDialog() })
        val tip = newText(
            host,
            "仅支持 HTTPS；安装前会校验文件指纹与签名。",
            9.5f,
            AppColors.faint,
            AppFont.sans,
        ).apply {
            setPadding(host.dp(16), 0, host.dp(16), host.dp(12))
        }
        card.addView(tip)
        return card
    }

    private fun handleAutoUpdateToggle(checked: Boolean) {
        val prefs = AppPreferences.get(host)
        if (checked && prefs.updateManifestUrl.trim().isEmpty()) {
            suppressAutoToggle = true
            autoUpdateSwitch.setChecked(false)
            suppressAutoToggle = false
            showConfirmDialog(
                host,
                "先设置一个更新源",
                "自动检查只会访问你自己填写的 HTTPS 清单地址。设置完成后，再开启它。",
                "填写更新源",
                "暂不开启",
            ) { host.showUpdateSourceDialog() }
            return
        }
        prefs.autoCheckUpdates = checked
        host.showNotice(if (checked) "自动检查已开启" else "自动检查已关闭")
    }

    private fun buildPrivacyCard(): View {
        val card = groupCard()
        val head = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(host.dp(16), host.dp(15), host.dp(16), host.dp(4))
        }
        head.addView(ImageView(host).apply {
            setImageResource(R.drawable.ic_shield)
            setColorFilter(AppColors.sage)
        }, LinearLayout.LayoutParams(host.dp(18), host.dp(18)).apply {
            topMargin = host.dp(1)
        })
        head.addView(newText(host, "只属于这台手机", 14f, AppColors.ink, AppFont.sansMedium).apply {
            setPadding(host.dp(9), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(head)
        card.addView(newText(
            host,
            "无账号、无广告、无统计 SDK。\n照片与纪念日保存在应用私有目录，只有你主动检查更新时才会联网。",
            10.5f,
            AppColors.inkSoft,
            AppFont.sans,
        ).apply {
            setPadding(host.dp(16), host.dp(5), host.dp(16), host.dp(4))
            setLineSpacing(host.dpf(4.5f), 1f)
        })
        card.addView(settingRow(
            "隐私说明",
            "查看数据保存与网络使用的完整说明",
            chevron(),
        ) { showPrivacyDialog() })
        return card
    }

    private fun showPrivacyDialog() {
        showScrollableInfoDialog(
            host,
            "隐私与数据",
            "念日没有账号，也没有广告、追踪或统计 SDK。\n\n" +
                "1. 纪念日、照片和设置保存在应用私有目录。其他应用默认无法读取。\n" +
                "2. 只有你手动检查更新，或主动开启自动检查后，念日才会访问你填写的 HTTPS 更新源。\n" +
                "3. 更新下载完成后，必须通过 SHA-256 指纹、包名、版本号和签名校验，系统才允许继续安装。\n" +
                "4. 是否把应用数据纳入系统云备份，由手机系统备份设置决定。\n" +
                "5. 卸载念日会删除本地数据，系统备份可以帮助你在新设备上恢复。",
            "明白了",
        )
    }

    private fun buildAboutCard(): View {
        val card = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(17), host.dp(16), host.dp(17), host.dp(16))
            background = roundedDrawable(host, AppColors.cream, 22f, 0.8f, AppColors.line)
        }
        val head = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(TextView(host).apply {
            text = "念"
            gravity = Gravity.CENTER
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(AppColors.cream)
            typeface = AppFont.serif
            includeFontPadding = false
            background = roundedDrawable(host, AppColors.cinnabar, 11f)
        }, LinearLayout.LayoutParams(host.dp(42), host.dp(42)))
        val labels = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(12), 0, 0, 0)
        }
        labels.addView(newText(host, "念日", 18f, AppColors.ink, AppFont.serif, 0.06f))
        labels.addView(newText(
            host,
            "NIANRI · 纪念日手帐  v${BuildConfig.VERSION_NAME}",
            8.5f,
            AppColors.muted,
            AppFont.sans,
            0.08f,
        ).apply { setPadding(0, host.dp(3), 0, 0) })
        head.addView(labels)
        card.addView(head)
        card.addView(newText(
            host,
            "一些干净、安静、只属于你的日子。",
            11f,
            AppColors.inkSoft,
            AppFont.serif,
        ).apply { setPadding(0, host.dp(13), 0, 0) })
        card.addView(newText(
            host,
            "无广告  ·  无追踪  ·  无需登录",
            8.8f,
            AppColors.faint,
            AppFont.sans,
            0.1f,
        ).apply { setPadding(0, host.dp(6), 0, 0) })
        return card
    }

    private fun openReminderTimePicker() {
        val prefs = AppPreferences.get(host)
        val time = prefs.reminderTime
        TimePickerDialog(
            host,
            R.style.Theme_NianRi_Picker,
            { _, hour, minute ->
                prefs.setReminderTime(hour, minute)
                ReminderScheduler.ensureScheduled(host)
                refresh()
                host.showNotice("每日提醒时间已改为 ${parseReminderTime(hour * 60 + minute)}")
            },
            time.hour,
            time.minute,
            true,
        ).show()
    }

    fun refresh() {
        if (!::notificationStatus.isInitialized) return
        val runtimeGranted = Build.VERSION.SDK_INT < 33 ||
            host.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val appEnabled = host.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        when {
            !runtimeGranted -> setStatus(notificationStatus, "待授权", AppColors.cinnabar)
            !appEnabled -> setStatus(notificationStatus, "系统已关闭", AppColors.cinnabar)
            else -> setStatus(notificationStatus, "已开启", AppColors.sage)
        }

        setStatus(
            exactStatus,
            if (ReminderScheduler.hasExactAlarmAccess(host)) "已开启" else "未开启",
            if (ReminderScheduler.hasExactAlarmAccess(host)) AppColors.sage else AppColors.cinnabar,
        )

        val power = host.getSystemService(PowerManager::class.java)
        val ignoring = power.isIgnoringBatteryOptimizations(host.packageName)
        setStatus(
            batteryStatus,
            if (ignoring) "无限制" else "系统管理",
            if (ignoring) AppColors.sage else AppColors.muted,
        )

        timeValue.text = parseReminderTime(AppPreferences.get(host).reminderMinutes)
        checkUpdateValue.text = "v${BuildConfig.VERSION_NAME}"
        val url = AppPreferences.get(host).updateManifestUrl.trim()
        updateSourceValue.text = if (url.isEmpty()) "未设置" else {
            try {
                URI(url).host ?: "已设置"
            } catch (_: Exception) {
                "已设置"
            }
        }
        suppressAutoToggle = true
        autoUpdateSwitch.setChecked(AppPreferences.get(host).autoCheckUpdates)
        suppressAutoToggle = false
    }

    private fun setStatus(view: TextView, text: String, color: Int) {
        view.text = text
        view.setTextColor(color)
    }

    private fun isXiaomiFamily(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer.contains("xiaomi") ||
            manufacturer.contains("redmi") ||
            manufacturer.contains("poco") ||
            brand.contains("xiaomi") ||
            brand.contains("redmi") ||
            brand.contains("poco")
    }
}
