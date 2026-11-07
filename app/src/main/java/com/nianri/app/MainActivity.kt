package com.nianri.app

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import com.nianri.app.data.AnniversaryStore
import com.nianri.app.data.AppPreferences
import com.nianri.app.data.PhotoStore
import com.nianri.app.data.WeatherManager
import com.nianri.app.data.WeatherStore
import com.nianri.app.model.Anniversary
import com.nianri.app.model.sortedForHome
import com.nianri.app.reminder.ReminderEngine
import com.nianri.app.reminder.ReminderScheduler
import com.nianri.app.ui.AppColors
import com.nianri.app.ui.AppFont
import com.nianri.app.ui.DetailView
import com.nianri.app.ui.EditorView
import com.nianri.app.ui.HomeView
import com.nianri.app.ui.ProgressDialogHandle
import com.nianri.app.ui.RecordsTab
import com.nianri.app.ui.RecordsView
import com.nianri.app.ui.SettingsView
import com.nianri.app.ui.WorkbenchView
import com.nianri.app.ui.createProgressDialog
import com.nianri.app.ui.dp
import com.nianri.app.ui.dpf
import com.nianri.app.ui.newText
import com.nianri.app.ui.rippleOn
import com.nianri.app.ui.roundedDrawable
import com.nianri.app.ui.showConfirmDialog
import com.nianri.app.ui.showInfoDialog
import com.nianri.app.ui.showInputDialog
import com.nianri.app.ui.showInstallReadyDialog
import com.nianri.app.ui.showSortDialog
import com.nianri.app.ui.showUpdateAvailableDialog
import com.nianri.app.update.InstallResult
import com.nianri.app.update.UpdateCheckResult
import com.nianri.app.update.UpdateInfo
import com.nianri.app.update.UpdateManager
import com.nianri.app.widget.WidgetUpdater
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.math.max

class MainActivity : Activity() {

    private enum class Screen { HOME, WORKBENCH, RECORDS, DETAIL, EDITOR, SETTINGS }

    private lateinit var root: FrameLayout
    private var currentScreenView: View? = null
    private var screen = Screen.HOME
    private var editorView: EditorView? = null
    private var settingsView: SettingsView? = null
    private var workbenchView: WorkbenchView? = null
    private var recordsView: RecordsView? = null
    private var detailEventId: String? = null
    private var editorOriginalId: String? = null
    private var photoPickerCallback: ((Uri?) -> Unit)? = null
    private var refreshOnResume = false
    private var checkingUpdates = false
    private var downloadingUpdate = false
    private var autoUpdateAttempted = false
    private var notificationPermissionInFlight = false
    private var noticeView: View? = null
    private var noticeDismissRunnable: Runnable? = null
    private var pendingSwipeDeleted: Anniversary? = null
    private var pendingSwipeCleanup: Runnable? = null
    private var pendingInstallApk: File? = null
    private var pendingInstallInfo: UpdateInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupWindow()
        root = FrameLayout(this).apply {
            setBackgroundColor(AppColors.paper)
        }
        setContentView(root)
        applyEdgeToEdge()
        applyLightSystemBars()
        applyInsets()

        ReminderEngine.ensureChannel(this)
        ReminderScheduler.ensureScheduled(this)

        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            ) { handleBack() }
        }

        showHome(false)
        handleNotificationIntent(intent)
        val referencedPhotos = AnniversaryStore.all(this).mapNotNull { it.imagePath }.toSet()
        PhotoStore.cleanupOrphans(this, referencedPhotos)
        root.postDelayed({ maybeAutoCheckUpdates() }, 2_200L)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return
        setIntent(intent)
        if (screen == Screen.EDITOR) {
            showNotice("还有一个页面没有保存，先完成它吧")
            return
        }
        handleNotificationIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        WidgetUpdater.updateAll(this)
        if (screen == Screen.SETTINGS) {
            settingsView?.refresh()
        }
        if (screen == Screen.WORKBENCH) {
            workbenchView?.refresh()
        }
        if (refreshOnResume) {
            refreshOnResume = false
            if (screen == Screen.DETAIL) {
                val id = detailEventId
                val event = id?.let { AnniversaryStore.find(this, it) }
                if (event == null) showHome(false) else openDetail(event, animate = false)
            } else if (screen == Screen.SETTINGS) {
                settingsView?.refresh()
            }
        }
        maybeShowPendingInstall()
    }

    private fun setupWindow() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun applyEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.decorView.setForceDarkAllowed(false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Calling this before setContentView() touches a missing decor
            // view on some Android builds, so it is applied after content.
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    private fun applyLightSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // getInsetsController() before the decor view is attached can
            // crash on some Android builds, so this is only done after the
            // window has content.
            window.decorView.post {
                window.decorView.windowInsetsController?.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                        WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                )
            }
        }
    }

    private fun applyInsets() {
        root.setOnApplyWindowInsetsListener { view, insets ->
            val top: Int
            val bottom: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val system = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout(),
                )
                val ime = insets.getInsets(WindowInsets.Type.ime()).bottom
                top = system.top
                bottom = max(system.bottom, ime)
            } else {
                @Suppress("DEPRECATION")
                top = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                val systemBottom = insets.systemWindowInsetBottom
                bottom = max(systemBottom, 0)
            }
            if (view.paddingTop != top || view.paddingBottom != bottom) {
                view.setPadding(0, top, 0, bottom)
            }
            insets
        }
        root.requestApplyInsets()
    }

    fun showHome(animated: Boolean = true) {
        editorView = null
        settingsView = null
        workbenchView = null
        recordsView = null
        detailEventId = null
        editorOriginalId = null
        val home = HomeView(this, AnniversaryStore.all(this))
        swapScreen(home, Screen.HOME, animated)
    }

    fun openSortDialog() {
        if (screen != Screen.HOME) return
        val ordered = AnniversaryStore.all(this).sortedForHome()
        if (ordered.size < 2) {
            showNotice("再多写一个日子，就可以调整顺序了")
            return
        }
        showSortDialog(this, ordered) { ids ->
            AnniversaryStore.reorder(this, ids)
            showHome(false)
            showNotice("首页顺序已保存")
        }
    }

    fun openWorkbench() {
        if (screen == Screen.WORKBENCH) return
        val workbench = WorkbenchView(this)
        workbenchView = workbench
        recordsView = null
        editorView = null
        settingsView = null
        detailEventId = null
        editorOriginalId = null
        swapScreen(workbench, Screen.WORKBENCH, true)
    }

    fun openRecords(tab: RecordsTab) {
        val records = RecordsView(this, tab)
        recordsView = records
        workbenchView = null
        editorView = null
        settingsView = null
        detailEventId = null
        editorOriginalId = null
        swapScreen(records, Screen.RECORDS, true)
    }

    fun openDetail(event: Anniversary, animate: Boolean = true) {
        val latest = AnniversaryStore.find(this, event.id) ?: run {
            showHome(animate)
            return
        }
        editorView = null
        settingsView = null
        detailEventId = latest.id
        editorOriginalId = null
        val detail = DetailView(this, latest)
        swapScreen(detail, Screen.DETAIL, animate)
    }

    fun openEditor(event: Anniversary?) {
        if (screen == Screen.EDITOR) return
        editorOriginalId = event?.id
        val editor = EditorView(this, event)
        editorView = editor
        settingsView = null
        swapScreen(editor, Screen.EDITOR, true)
    }

    fun openSettings() {
        if (screen == Screen.SETTINGS) return
        val settings = SettingsView(this)
        settingsView = settings
        editorView = null
        workbenchView = null
        recordsView = null
        swapScreen(settings, Screen.SETTINGS, true)
    }

    fun returnHome() {
        if (screen == Screen.EDITOR) {
            editorView?.let { closeEditor(it) } ?: showHome()
        } else {
            showHome()
        }
    }

    private fun swapScreen(view: View, next: Screen, animated: Boolean) {
        currentScreenView?.let { root.removeView(it) }
        root.addView(view, 0, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))
        currentScreenView = view
        screen = next
        hideKeyboard()
        if (animated) {
            view.alpha = 0f
            view.translationY = dp(10).toFloat()
            view.animate().alpha(1f).translationY(0f).setDuration(230L).start()
        } else {
            view.alpha = 1f
            view.translationY = 0f
        }
    }

    fun closeEditor(editor: EditorView) {
        if (screen != Screen.EDITOR || editorView !== editor) return
        if (editor.hasUnsavedChanges()) {
            val message = if (editorOriginalId == null) {
                "写下的内容和选择的照片还没有保存。离开后，它们会被清空。"
            } else {
                "修改还没有保存。离开后，这一天会恢复成原来的样子。"
            }
            showConfirmDialog(
                this,
                "要放下这一页吗？",
                message,
                "放下",
                "继续写",
                destructive = true,
            ) {
                finishClosingEditor(editor)
            }
        } else {
            finishClosingEditor(editor)
        }
    }

    private fun finishClosingEditor(editor: EditorView) {
        editor.abandon()
        val originalId = editorOriginalId
        editorView = null
        if (originalId != null) {
            val event = AnniversaryStore.find(this, originalId)
            if (event != null) openDetail(event) else showHome()
        } else {
            showHome()
        }
    }

    fun onEventSaved(event: Anniversary, original: Anniversary?) {
        editorView = null
        ReminderScheduler.ensureScheduled(this)
        WidgetUpdater.updateAll(this)
        openDetail(event)
        window.decorView.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
        showNotice(if (original == null) "这一天，已经被好好收下了" else "这一页，已经重新写好")
        if (event.reminderEnabled) {
            root.postDelayed({ requestNotificationPermissionIfNeeded() }, 500L)
        }
    }

    fun deleteEvent(event: Anniversary) {
        finalizePendingSwipeDelete()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(ReminderEngine.notificationId(event.id))
        PhotoStore.delete(this, event.imagePath)
        AnniversaryStore.delete(this, event.id)
        AppPreferences.get(this).clearNotificationMark(event.id)
        ReminderScheduler.ensureScheduled(this)
        WidgetUpdater.updateAll(this)
        showHome()
        showNotice("这一天，已经从手帐里取下")
    }

    /**
     * 首页左滑删除：立即移出列表，给 5 秒撤销时间；照片等撤销窗口结束后再清理。
     */
    fun deleteEventBySwipe(event: Anniversary) {
        finalizePendingSwipeDelete()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(ReminderEngine.notificationId(event.id))
        AnniversaryStore.delete(this, event.id)
        AppPreferences.get(this).clearNotificationMark(event.id)
        ReminderScheduler.ensureScheduled(this)
        WidgetUpdater.updateAll(this)
        showHome(animated = false)

        pendingSwipeDeleted = event
        val cleanup = Runnable {
            if (pendingSwipeDeleted?.id == event.id) {
                pendingSwipeDeleted = null
                pendingSwipeCleanup = null
                if (!event.imagePath.isNullOrBlank()) {
                    PhotoStore.delete(this, event.imagePath)
                }
            }
        }
        pendingSwipeCleanup = cleanup
        root.postDelayed(cleanup, 6_000L)

        showActionNotice(
            message = "「${event.title}」已删除",
            actionLabel = "撤销",
            durationMs = 5_200L,
        ) {
            if (pendingSwipeDeleted?.id != event.id) return@showActionNotice
            pendingSwipeCleanup?.let(root::removeCallbacks)
            pendingSwipeCleanup = null
            pendingSwipeDeleted = null
            val restored = if (PhotoStore.exists(this, event.imagePath)) {
                event
            } else {
                event.copy(imagePath = null)
            }
            AnniversaryStore.upsert(this, restored)
            ReminderScheduler.ensureScheduled(this)
            WidgetUpdater.updateAll(this)
            showHome(animated = false)
            showNotice("已撤销删除")
        }
    }

    private fun finalizePendingSwipeDelete() {
        pendingSwipeCleanup?.let(root::removeCallbacks)
        pendingSwipeCleanup = null
        val event = pendingSwipeDeleted ?: return
        pendingSwipeDeleted = null
        if (!event.imagePath.isNullOrBlank()) {
            PhotoStore.delete(this, event.imagePath)
        }
    }

    fun showNotice(message: String) {
        showNoticeInternal(message, null, null, 2_300L)
    }

    fun showActionNotice(
        message: String,
        actionLabel: String,
        durationMs: Long = 5_200L,
        action: () -> Unit,
    ) {
        showNoticeInternal(message, actionLabel, action, durationMs)
    }

    private fun showNoticeInternal(
        message: String,
        actionLabel: String?,
        action: (() -> Unit)?,
        durationMs: Long,
    ) {
        clearNotice()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(10), if (action == null) dp(18) else dp(8), dp(10))
            background = roundedDrawable(this@MainActivity, 0xF027231E.toInt(), 22f)
            elevation = dp(24).toFloat()
            alpha = 0f
            translationY = dp(8).toFloat()
        }
        val messageView = newText(
            this,
            message,
            12.5f,
            AppColors.cream,
            AppFont.sansMedium,
        ).apply {
            maxLines = 3
            maxWidth = resources.displayMetrics.widthPixels - dp(132)
            setLineSpacing(dpf(3f), 1f)
        }
        container.addView(messageView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        if (action != null && actionLabel != null) {
            container.addView(View(this).apply {
                setBackgroundColor(0x33FFF9EF)
            }, LinearLayout.LayoutParams(dp(1), dp(16)).apply {
                marginStart = dp(12)
                marginEnd = dp(8)
            })
            val actionView = newText(
                this,
                actionLabel,
                12.5f,
                0xFFF3D6A8.toInt(),
                AppFont.sansMedium,
            ).apply {
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setPadding(dp(12), dp(6), dp(12), dp(6))
                background = rippleOn(
                    this@MainActivity,
                    roundedDrawable(this@MainActivity, Color.TRANSPARENT, 14f),
                    0x33FFF9EF,
                )
                setOnClickListener {
                    clearNotice()
                    action()
                }
            }
            container.addView(actionView)
        }
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        ).apply {
            leftMargin = dp(24)
            rightMargin = dp(24)
            bottomMargin = dp(96)
        }
        root.addView(container, lp)
        noticeView = container
        container.animate().alpha(1f).translationY(0f).setDuration(180L).start()
        container.announceForAccessibility(
            if (actionLabel == null) message else "$message，$actionLabel",
        )
        val dismiss = Runnable {
            if (container.parent === root) {
                container.animate().alpha(0f).translationY(dp(6).toFloat()).setDuration(220L)
                    .withEndAction {
                        if (container.parent === root) root.removeView(container)
                        if (noticeView === container) noticeView = null
                    }.start()
            }
        }
        noticeDismissRunnable = dismiss
        root.postDelayed(dismiss, durationMs)
    }

    private fun clearNotice() {
        noticeDismissRunnable?.let(root::removeCallbacks)
        noticeDismissRunnable = null
        noticeView?.let { view ->
            view.animate().cancel()
            if (view.parent === root) root.removeView(view)
        }
        noticeView = null
    }

    fun showKeyboard(view: View) {
        view.requestFocus()
        view.postDelayed({
            val imm = getSystemService(InputMethodManager::class.java)
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }, 120L)
    }

    fun hideKeyboard() {
        val focused = currentFocus ?: return
        val imm = getSystemService(InputMethodManager::class.java)
        imm.hideSoftInputFromWindow(focused.windowToken, 0)
        focused.clearFocus()
    }

    fun launchPhotoPicker(onResult: (Uri?) -> Unit) {
        photoPickerCallback = onResult
        val candidates = mutableListOf<Intent>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            candidates += Intent(MediaStore.ACTION_PICK_IMAGES).apply {
                type = "image/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        candidates += Intent(
            Intent.ACTION_PICK,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        ).apply {
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        candidates += Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        for (intent in candidates) {
            try {
                startActivityForResult(intent, REQUEST_PHOTO)
                return
            } catch (_: ActivityNotFoundException) {
                // Try the next gallery/photo picker source.
            } catch (_: Exception) {
                // Some OEM galleries reject extras unexpectedly; fall through.
            }
        }
        photoPickerCallback = null
        showNotice("这台设备没有可用的相册或照片选择器")
    }

    @Deprecated("Activity result API is used to stay dependency-free")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PHOTO) {
            val callback = photoPickerCallback
            photoPickerCallback = null
            callback?.invoke(if (resultCode == RESULT_OK) data?.data else null)
        }
    }

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }
        if (notificationPermissionInFlight) return
        notificationPermissionInFlight = true
        requestPermissions(
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_NOTIFICATION_PERMISSION,
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_NOTIFICATION_PERMISSION) return
        notificationPermissionInFlight = false
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        if (granted) {
            ReminderEngine.ensureChannel(this)
            ReminderScheduler.ensureScheduled(this)
            showNotice("提醒已开启，重要的日子不会再错过")
        } else {
            showNotice("通知权限未开启，提醒暂时无法送达")
        }
        if (screen == Screen.SETTINGS) settingsView?.refresh()
        if (screen == Screen.DETAIL) {
            detailEventId?.let { id ->
                AnniversaryStore.find(this, id)?.let { openDetail(it, animate = false) }
            }
        }
    }

    fun openNotificationSettings() {
        refreshOnResume = true
        ReminderEngine.openNotificationSettings(this)
    }

    fun showWeatherCityDialog() {
        showInputDialog(
            this,
            "天气城市",
            "天气使用 Open-Meteo 的公开数据，只要城市名，不需要定位权限。设置后只有打开工作台或手动刷新时才联网。",
            WeatherStore.city(this),
            "例如：北京",
        ) { value ->
            val city = value.trim()
            if (city.isEmpty()) {
                showNotice("先写一个城市名")
                return@showInputDialog false
            }
            showNotice("正在查找城市…")
            WeatherManager.configureCity(this, city) { ok, message ->
                if (isFinishing || isDestroyed) return@configureCity
                showNotice(message)
                if (ok) workbenchView?.refresh()
            }
            true
        }
    }

    fun openExactAlarmSettings() {
        refreshOnResume = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:$packageName"),
            )
            try {
                startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
        openApplicationDetails()
    }

    fun openApplicationDetails() {
        refreshOnResume = true
        try {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            })
        } catch (_: Exception) {
            showNotice("无法打开系统设置")
        }
    }

    /** 打开反馈 Issue；没有浏览器时把链接复制到剪贴板，不丢反馈入口。 */
    fun openExternalUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            copyFeedbackUrl(url)
        } catch (_: Exception) {
            copyFeedbackUrl(url)
        }
    }

    fun copyTextToClipboard(label: String, text: String, notice: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
            showNotice(notice)
        } else {
            showNotice("无法访问剪贴板")
        }
    }

    private fun copyFeedbackUrl(url: String) {
        copyTextToClipboard(
            "念日反馈链接",
            url,
            "没有可用浏览器，反馈链接已复制",
        )
    }

    fun openBatteryOptimizationSettings() {
        refreshOnResume = true
        val powerManager = getSystemService(android.os.PowerManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !powerManager.isIgnoringBatteryOptimizations(packageName)
        ) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName"),
                    ),
                )
                return
            } catch (_: Exception) {
            }
        }
        try {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Exception) {
            openApplicationDetails()
        }
    }

    fun openMiuiAutostart() {
        refreshOnResume = true
        val candidates = listOf(
            Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity",
                )
            },
            Intent("miui.intent.action.OP_AUTO_START").apply {
                addCategory(Intent.CATEGORY_DEFAULT)
            },
        )
        for (intent in candidates) {
            if (intent.resolveActivity(packageManager) != null) {
                try {
                    startActivity(intent)
                    return
                } catch (_: Exception) {
                }
            }
        }
        showInfoDialog(
            this,
            "在 HyperOS 中允许自启动",
            "打开「设置 → 应用设置 → 应用管理 → 念日」，允许「自启动」，并把省电策略改为「无限制」。不同机型的入口名称可能略有不同。",
            "去应用详情",
        )
    }

    fun showUpdateSourceDialog() {
        val current = AppPreferences.get(this).updateManifestUrl
        showInputDialog(
            this,
            "在线更新源",
            "默认使用念日 GitHub 公开清单；也可以替换成你自己的 HTTPS JSON 地址。清单需包含 versionCode、versionName、apkUrl 和 sha256，留空即可关闭在线检查。",
            current,
            "https://…/update.json",
        ) { value ->
            if (value.isNotEmpty()) {
                val valid = try {
                    val uri = URI(value)
                    uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
                } catch (_: Exception) {
                    false
                }
                if (!valid) {
                    Toast.makeText(this, "请填写以 https:// 开头的有效地址", Toast.LENGTH_SHORT).show()
                    return@showInputDialog false
                }
            }
            AppPreferences.get(this).updateManifestUrl = value
            settingsView?.refresh()
            showNotice(if (value.isEmpty()) "已关闭在线更新检查" else "更新源已保存")
            true
        }
    }

    fun checkForUpdates(manual: Boolean) {
        if (checkingUpdates) {
            if (manual) showNotice("正在检查更新，请稍候")
            return
        }
        val prefs = AppPreferences.get(this)
        val url = prefs.updateManifestUrl.trim()
        if (url.isEmpty()) {
            if (manual) {
                showConfirmDialog(
                    this,
                    "还没有设置更新源",
                    "念日不会擅自连接任何服务器。在线更新需要你填写自己的 HTTPS 更新清单地址，之后每一次检查都由你主动发起。",
                    "填写更新源",
                    "以后再说",
                ) { showUpdateSourceDialog() }
            }
            return
        }
        checkingUpdates = true
        var progress: ProgressDialogHandle? = null
        if (manual) {
            progress = createProgressDialog(
                this,
                "正在检查更新",
                "正在读取最新的更新清单…",
                true,
            )
            progress.show()
        }
        val progressRef = progress
        UpdateManager.check(url) { result ->
            checkingUpdates = false
            progressRef?.dismissSafely()
            if (isFinishing || isDestroyed) return@check
            when (result) {
                is UpdateCheckResult.UpToDate -> {
                    if (manual) {
                        showInfoDialog(
                            this,
                            "已经是最新的一页。",
                            "当前版本 ${BuildConfig.VERSION_NAME}，没有发现新的版本。\n\n${UpdateManager.currentVersionText()}",
                            "好",
                        )
                    }
                }
                is UpdateCheckResult.Available -> {
                    if (manual || screen == Screen.HOME) {
                        showUpdateAvailableDialog(this, result.info) {
                            startUpdateDownload(result.info)
                        }
                    }
                }
                is UpdateCheckResult.Failed -> {
                    if (manual) {
                        showInfoDialog(
                            this,
                            "暂时没有连上更新源",
                            result.message,
                            "知道了",
                        )
                    }
                }
            }
        }
    }

    private fun maybeAutoCheckUpdates() {
        if (autoUpdateAttempted) return
        autoUpdateAttempted = true
        if (screen != Screen.HOME) return
        val prefs = AppPreferences.get(this)
        if (!prefs.autoCheckUpdates) return
        if (prefs.updateManifestUrl.trim().isEmpty()) return
        val elapsed = System.currentTimeMillis() - prefs.lastAutoUpdateCheck
        if (elapsed < TimeUnit.DAYS.toMillis(1)) return
        prefs.lastAutoUpdateCheck = System.currentTimeMillis()
        checkForUpdates(manual = false)
    }

    private fun startUpdateDownload(info: UpdateInfo) {
        if (downloadingUpdate) {
            showNotice("更新正在下载，请稍候")
            return
        }
        downloadingUpdate = true
        val progress = createProgressDialog(
            this,
            "正在下载更新",
            "版本 ${info.versionName} 正在下载…",
            true,
        )
        progress.show()
        var lastPercent = -1
        var lastMegabyte = -1L
        UpdateManager.download(
            this,
            info,
            onProgress = progress@{ downloaded, total ->
                if (isFinishing || isDestroyed) return@progress
                if (total > 0) {
                    val percent = ((downloaded * 100) / total).toInt().coerceIn(0, 100)
                    if (percent != lastPercent) {
                        lastPercent = percent
                        progress.progress(
                            percent,
                            "正在下载版本 ${info.versionName}…",
                        )
                    }
                } else {
                    val megabyte = downloaded / (1024 * 1024)
                    if (megabyte != lastMegabyte) {
                        lastMegabyte = megabyte
                        progress.indeterminate("已下载 ${megabyte} MB…")
                    }
                }
            },
            onComplete = complete@{ file ->
                downloadingUpdate = false
                progress.dismissSafely()
                if (isFinishing || isDestroyed) return@complete
                showInstallReadyDialog(this, info.versionName) {
                    beginInstallUpdate(file, info)
                }
            },
            onError = { message ->
                downloadingUpdate = false
                progress.dismissSafely()
                if (!isFinishing && !isDestroyed) {
                    showInfoDialog(this, "更新没有下载完成", message, "知道了")
                }
            },
        )
    }

    private fun beginInstallUpdate(apk: File, info: UpdateInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            pendingInstallApk = apk
            pendingInstallInfo = info
            showConfirmDialog(
                this,
                "还需要允许安装",
                "Android 需要你允许念日安装更新。开启后回到念日，会继续完成安装。",
                "去开启",
                "稍后",
            ) {
                UpdateManager.requestInstallPermission(this)
            }
            return
        }
        when (UpdateManager.install(this, apk)) {
            InstallResult.STARTED -> showNotice("正在打开系统安装器…")
            InstallResult.NEED_INSTALL_PERMISSION -> {
                pendingInstallApk = apk
                pendingInstallInfo = info
                showNotice("请在系统设置中允许念日安装更新")
            }
            InstallResult.FAILED -> showInfoDialog(
                this,
                "暂时无法安装",
                "系统安装器没有打开。请确认已允许念日安装未知应用，然后再试一次。",
                "知道了",
            )
        }
    }

    private fun maybeShowPendingInstall() {
        val apk = pendingInstallApk ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            return
        }
        val info = pendingInstallInfo
        pendingInstallApk = null
        pendingInstallInfo = null
        if (info != null) {
            root.post {
                showInstallReadyDialog(this, info.versionName) {
                    beginInstallUpdate(apk, info)
                }
            }
        }
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val eventId = intent?.getStringExtra(EXTRA_EVENT_ID) ?: return
        intent.removeExtra(EXTRA_EVENT_ID)
        val event = AnniversaryStore.find(this, eventId) ?: return
        openDetail(event)
    }

    private fun handleBack() {
        when (screen) {
            Screen.HOME -> finish()
            Screen.WORKBENCH -> showHome()
            Screen.RECORDS -> openWorkbench()
            Screen.EDITOR -> editorView?.let { closeEditor(it) } ?: showHome()
            Screen.DETAIL, Screen.SETTINGS -> showHome()
        }
    }

    @Deprecated("Used on Android 12 and below")
    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT < 33) {
            handleBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        clearNotice()
        finalizePendingSwipeDelete()
        photoPickerCallback = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_EVENT_ID = "com.nianri.app.extra.EVENT_ID"
        private const val REQUEST_PHOTO = 4201
        private const val REQUEST_NOTIFICATION_PERMISSION = 4202
    }
}
