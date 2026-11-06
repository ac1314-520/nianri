package com.nianri.app.ui

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Paint
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.nianri.app.MainActivity
import com.nianri.app.R
import com.nianri.app.data.CheckInCategory
import com.nianri.app.data.CheckInPlan
import com.nianri.app.data.DiaryEntry
import com.nianri.app.data.LedgerEntry
import com.nianri.app.data.PeriodRecord
import com.nianri.app.data.WishItem
import com.nianri.app.data.WorkbenchSnapshot
import com.nianri.app.data.WorkbenchStore
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

enum class RecordsTab(val label: String) {
    DIARY("日记"),
    CHECKIN("打卡"),
    LEDGER("记账"),
    PERIOD("生理期"),
    WISH("愿望"),
    ;

    companion object {
        fun fromIndex(index: Int): RecordsTab = entries.getOrElse(index) { DIARY }
    }
}

private enum class LedgerMode { MONTH, YEAR }

private data class PeriodSummary(
    val sorted: List<PeriodRecord>,
    val intervals: List<Long>,
    val lengths: List<Long>,
    val avgCycle: Int?,
    val avgPeriod: Int?,
    val minCycle: Int?,
    val maxCycle: Int?,
    val regularity: String,
)

class RecordsView(
    private val host: MainActivity,
    initialTab: RecordsTab,
) : FrameLayout(host) {

    private var tab = initialTab
    private var didRender = false
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var tabRow: LinearLayout
    private val tabViews = mutableMapOf<RecordsTab, TextView>()

    private var diaryInitialized = false
    private var diaryEditingId: String? = null
    private var diaryEditingDay: Long = LocalDate.now().toEpochDay()
    private var diaryMood: String? = null
    private var diaryFilterMonth: YearMonth? = null
    private var diaryFilterMood: String? = null
    private val diaryMoodViews = mutableListOf<Pair<TextView, MoodOption>>()

    private var ledgerMode = LedgerMode.MONTH
    private var ledgerMonth: YearMonth = YearMonth.now()
    private var ledgerYear: Int = LocalDate.now().year
    private var periodMonth: YearMonth = YearMonth.now()

    init {
        background = android.graphics.drawable.ColorDrawable(AppColors.paper)
        buildShell()
        selectTab(initialTab, scrollTop = false)
    }

    private fun buildShell() {
        val main = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        addView(main, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        main.addView(featureTopBar(host, "记录", { host.openWorkbench() }))

        tabRow = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(host.contentMargin(), host.dp(3), host.contentMargin(), host.dp(7))
        }
        RecordsTab.entries.forEach { item ->
            val view = newText(host, item.label, 12f, AppColors.muted, AppFont.sans).apply {
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setOnClickListener { selectTab(item) }
            }
            tabViews[item] = view
            tabRow.addView(view, LinearLayout.LayoutParams(0, host.dp(36), 1f).apply {
                if (item != RecordsTab.WISH) marginEnd = host.dp(4)
            })
        }
        main.addView(tabRow)

        scroll = ScrollView(host).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
        }
        content = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.contentMargin(), host.dp(4), host.contentMargin(), host.dp(80))
        }
        scroll.addView(content, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        main.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))
    }

    private fun selectTab(next: RecordsTab, scrollTop: Boolean = true) {
        if (tab == next && didRender) {
            styleTabs()
            return
        }
        tab = next
        if (tab == RecordsTab.DIARY && !diaryInitialized) {
            initializeDiary()
        }
        styleTabs()
        render(preserveScroll = !scrollTop)
        didRender = true
    }

    private fun styleTabs() {
        tabViews.forEach { (item, view) ->
            val selected = item == tab
            view.setTextColor(if (selected) AppColors.cinnabar else AppColors.muted)
            view.typeface = if (selected) AppFont.sansMedium else AppFont.sans
            view.background = rippleOn(
                host,
                roundedDrawable(
                    host,
                    if (selected) AppColors.cinnabarSoft else Color.TRANSPARENT,
                    18f,
                ),
                0x1FB05238,
            )
        }
    }

    private fun render(preserveScroll: Boolean = true) {
        val snapshot = WorkbenchStore.get(host)
        val y = if (::scroll.isInitialized) scroll.scrollY else 0
        content.removeAllViews()
        when (tab) {
            RecordsTab.DIARY -> buildDiary(snapshot)
            RecordsTab.CHECKIN -> buildCheckIn(snapshot)
            RecordsTab.LEDGER -> buildLedger(snapshot)
            RecordsTab.PERIOD -> buildPeriod(snapshot)
            RecordsTab.WISH -> buildWishlist(snapshot)
        }
        styleTabs()
        if (::scroll.isInitialized) {
            scroll.post { scroll.scrollTo(0, if (preserveScroll) y else 0) }
        }
    }

    // ---------------------------------------------------------------- diary

    private fun initializeDiary() {
        val snapshot = WorkbenchStore.get(host)
        val today = LocalDate.now().toEpochDay()
        val entry = snapshot.diaryOn(today)
        diaryEditingId = entry?.id
        diaryEditingDay = today
        diaryMood = entry?.moodKey ?: snapshot.moodOn(today)
        diaryInitialized = true
    }

    private fun buildDiary(snapshot: WorkbenchSnapshot) {
        val today = LocalDate.now().toEpochDay()
        var editing = diaryEditingId?.let { id -> snapshot.diary.firstOrNull { it.id == id } }
        if (editing == null) {
            diaryEditingId = null
            if (diaryEditingDay <= 0) diaryEditingDay = today
        }
        if (diaryMood == null) {
            diaryMood = editing?.moodKey ?: snapshot.moodOn(diaryEditingDay)
        }
        val day = editing?.epochDay ?: diaryEditingDay
        diaryEditingDay = day

        val composer = contentCard(host)
        val head = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val heading = if (editing == null) "写下今天" else {
            if (day == today) "今天的一页" else "编辑 ${epochDate(day, "M月d日")}"
        }
        head.addView(newText(host, heading, 15f, AppColors.ink, AppFont.serif),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(newText(host, epochDate(day, "M月d日 · EEEE"), 9.5f, AppColors.muted, AppFont.sans))
        composer.addView(head)
        composer.addView(newText(
            host,
            "不必写得完整。一句话，也算把今天留住。",
            10.5f,
            AppColors.faint,
            AppFont.sans,
        ).apply { setPadding(0, host.dp(5), 0, host.dp(10)) })

        val moodTitle = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, host.dp(7))
        }
        moodTitle.addView(newText(host, "给今天一个心情", 10.5f, AppColors.muted, AppFont.sans))
        if (day != today) {
            moodTitle.addView(LinearLayout(host).apply { weightSum = 1f }, LinearLayout.LayoutParams(0, 1, 1f))
            moodTitle.addView(softAction(host, "写今天") {
                diaryEditingId = snapshot.diaryOn(today)?.id
                diaryEditingDay = today
                diaryMood = snapshot.diaryOn(today)?.moodKey ?: snapshot.moodOn(today)
                render(preserveScroll = false)
            })
        }
        composer.addView(moodTitle)
        diaryMoodViews.clear()
        val moodRow = LinearLayout(host).apply { orientation = LinearLayout.HORIZONTAL }
        WorkbenchMood.all.forEachIndexed { index, mood ->
            val selected = diaryMood == mood.key
            val chip = newText(
                host,
                mood.label,
                10f,
                if (selected) mood.color else AppColors.muted,
                if (selected) AppFont.sansMedium else AppFont.sans,
            ).apply {
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                background = moodChipBackground(mood, selected)
                setOnClickListener {
                    diaryMood = if (diaryMood == mood.key) null else mood.key
                    WorkbenchStore.update(host) { data ->
                        val moods = data.moods.toMutableMap()
                        diaryMood?.let { moods[day] = it } ?: moods.remove(day)
                        data.copy(moods = moods)
                    }
                    refreshDiaryMoods()
                }
            }
            diaryMoodViews += chip to mood
            moodRow.addView(chip, LinearLayout.LayoutParams(0, host.dp(32), 1f).apply {
                if (index < WorkbenchMood.all.lastIndex) marginEnd = host.dp(4)
            })
        }
        composer.addView(moodRow)

        val input = textInput(host, "今天发生了什么？", multiline = true).apply {
            setText(editing?.text.orEmpty())
            setSelection(text.length)
        }
        composer.addView(input, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(10) })
        val save = primaryAction(host, if (editing == null) "收下这一页" else "保存修改") {
            val text = input.text.toString().trim()
            if (text.isEmpty()) {
                host.showNotice("写一点点，再收下这一页")
                return@primaryAction
            }
            val id = diaryEditingId ?: WorkbenchStore.newId()
            val finalMood = diaryMood
            WorkbenchStore.update(host) { data ->
                val notes = data.diary.filterNot { it.id == id } +
                    DiaryEntry(id, day, text, finalMood)
                val moods = data.moods.toMutableMap()
                finalMood?.let { moods[day] = it } ?: moods.remove(day)
                data.copy(
                    diary = notes.sortedByDescending { it.epochDay },
                    moods = moods,
                )
            }
            diaryEditingId = id
            diaryEditingDay = day
            diaryInitialized = true
            host.showNotice(if (editing == null) "这一页，收好了" else "这一页，改好了")
            render(preserveScroll = false)
        }
        composer.addView(save, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            host.dp(46),
        ).apply { topMargin = host.dp(10) })
        content.addView(composer, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))

        val diary = snapshot.diary
        if (diary.isEmpty()) {
            return
        }
        addDiaryFilterBar()
        addDiaryMonthlyStats(diary, diaryFilterMonth)
        val filtered = diary.filter { entry ->
            val inMonth = diaryFilterMonth == null ||
                YearMonth.from(LocalDate.ofEpochDay(entry.epochDay)) == diaryFilterMonth
            val inMood = diaryFilterMood == null || entry.moodKey == diaryFilterMood
            inMonth && inMood
        }
        val moodLabel = diaryFilterMood?.let { WorkbenchMood.option(it)?.label }
        val filterName = when {
            diaryFilterMonth != null && moodLabel != null -> "${yearMonthLabel(diaryFilterMonth!!)} · $moodLabel"
            diaryFilterMonth != null -> yearMonthLabel(diaryFilterMonth!!)
            moodLabel != null -> "全部 · $moodLabel"
            else -> "全部日记"
        }
        content.addView(sectionLabel(host, "$filterName · ${filtered.size} 篇"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(24) })
        if (filtered.isEmpty()) {
            val empty = contentCard(host)
            empty.addView(newText(host, "这个筛选下还没有日记", 17f, AppColors.ink, AppFont.serif))
            empty.addView(newText(host, "换个心情或月份看看，也可以现在写一页。", 10.5f, AppColors.muted, AppFont.sans)
                .apply { setPadding(0, host.dp(6), 0, 0) })
            empty.addView(softAction(host, "看全部") {
                diaryFilterMonth = null
                diaryFilterMood = null
                render(preserveScroll = false)
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                host.dp(36),
            ).apply { topMargin = host.dp(9) })
            content.addView(empty, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = host.dp(10) })
            return
        }
        val list = contentCard(host, 0)
        filtered.take(60).forEachIndexed { index, entry ->
            if (index > 0) list.addView(cardDivider(host))
            list.addView(diaryRow(entry, entry.id == diaryEditingId), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
        content.addView(list, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(10) })
        if (filtered.size > 60) {
            content.addView(newText(host, "仅显示最近 60 篇", 9f, AppColors.faint, AppFont.sans).apply {
                setPadding(0, host.dp(7), 0, 0)
            })
        }
    }

    private fun addDiaryFilterBar() {
        val card = contentCard(host)
        val monthRow = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        monthRow.addView(navArrow("‹", "上个月") {
            diaryFilterMonth = (diaryFilterMonth ?: YearMonth.now()).minusMonths(1)
            render(preserveScroll = false)
        }, LinearLayout.LayoutParams(host.dp(32), host.dp(32)))
        monthRow.addView(newText(
            host,
            diaryFilterMonth?.let { yearMonthLabel(it) } ?: "全部日记",
            12.5f,
            AppColors.ink,
            AppFont.sansMedium,
        ).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(0, host.dp(32), 1f))
        monthRow.addView(navArrow("›", "下个月") {
            diaryFilterMonth = (diaryFilterMonth ?: YearMonth.now()).plusMonths(1)
            render(preserveScroll = false)
        }, LinearLayout.LayoutParams(host.dp(32), host.dp(32)))
        if (diaryFilterMonth != null || diaryFilterMood != null) {
            monthRow.addView(softAction(host, "重置") {
                diaryFilterMonth = null
                diaryFilterMood = null
                render(preserveScroll = false)
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                host.dp(32),
            ).apply { marginStart = host.dp(5) })
        }
        card.addView(monthRow)
        val moodRow = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, host.dp(9), 0, 0)
        }
        val allChip = newText(host, "全部", 9.5f,
            if (diaryFilterMood == null) AppColors.cinnabar else AppColors.muted,
            if (diaryFilterMood == null) AppFont.sansMedium else AppFont.sans).apply {
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = rippleOn(
                host,
                roundedDrawable(
                    host,
                    if (diaryFilterMood == null) AppColors.cinnabarSoft else Color.TRANSPARENT,
                    15f,
                    if (diaryFilterMood == null) 0f else 0.8f,
                    if (diaryFilterMood == null) Color.TRANSPARENT else AppColors.line,
                ),
                0x1FB05238,
            )
            setOnClickListener {
                diaryFilterMood = null
                render(preserveScroll = false)
            }
        }
        moodRow.addView(allChip, LinearLayout.LayoutParams(0, host.dp(30), 1f).apply { marginEnd = host.dp(3) })
        WorkbenchMood.all.forEachIndexed { index, mood ->
            val selected = diaryFilterMood == mood.key
            val chip = newText(host, mood.label, 9.5f,
                if (selected) mood.color else AppColors.muted,
                if (selected) AppFont.sansMedium else AppFont.sans).apply {
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                background = moodChipBackground(mood, selected)
                setOnClickListener {
                    diaryFilterMood = if (diaryFilterMood == mood.key) null else mood.key
                    render(preserveScroll = false)
                }
            }
            moodRow.addView(chip, LinearLayout.LayoutParams(0, host.dp(30), 1f).apply {
                if (index < WorkbenchMood.all.lastIndex) marginEnd = host.dp(3)
            })
        }
        card.addView(moodRow)
        content.addView(card, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(12) })
    }

    private fun addDiaryMonthlyStats(diary: List<DiaryEntry>, selectedMonth: YearMonth?) {
        val month = selectedMonth ?: YearMonth.now()
        val monthEntries = diary.filter { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) == month }
        val days = monthEntries.map { it.epochDay }.distinct().size
        val daySet = diary.map { it.epochDay }.toSet()
        var cursor = LocalDate.now().toEpochDay()
        if (cursor !in daySet) cursor--
        var streak = 0
        while (cursor in daySet) {
            streak++
            cursor--
        }
        content.addView(sectionLabel(host, if (selectedMonth == null) "这个月" else yearMonthLabel(month)),
            LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(24) })
        val card = contentCard(host)
        val head = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(newText(host, "${monthEntries.size}", 29f, AppColors.cinnabar, AppFont.serif).apply {
            includeFontPadding = false
        })
        head.addView(newText(
            host,
            " 篇 · 记录了 $days 天" + if (streak > 0) " · 连续 $streak 天" else "",
            10.5f,
            AppColors.muted,
            AppFont.sans,
        ).apply {
            setPadding(host.dp(7), 0, 0, host.dp(3))
        })
        card.addView(head)
        val counts = monthEntries.mapNotNull { it.moodKey }.groupingBy { it }.eachCount()
        if (counts.isEmpty()) {
            card.addView(newText(host, if (selectedMonth == null) "这个月还没有记录心情。" else "这个月还没有记录心情。", 10f, AppColors.faint, AppFont.sans).apply {
                setPadding(0, host.dp(7), 0, 0)
            })
        } else {
            val moodRow = LinearLayout(host).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, host.dp(8), 0, 0)
            }
            WorkbenchMood.all.forEach { mood ->
                val count = counts[mood.key] ?: 0
                if (count > 0) {
                    moodRow.addView(simpleChip(host, "${mood.label} $count", moodChipColor(mood), mood.color),
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply { marginEnd = host.dp(5) })
                }
            }
            card.addView(moodRow)
        }
        content.addView(card, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(10) })
    }

    private fun diaryRow(entry: DiaryEntry, editing: Boolean): View {
        val row = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(host.dp(16), host.dp(14), host.dp(16), host.dp(14))
            isClickable = true
            isFocusable = true
            background = rippleOn(host, roundedDrawable(host, Color.TRANSPARENT, 18f), 0x1427231E)
            setOnClickListener {
                diaryEditingId = entry.id
                diaryEditingDay = entry.epochDay
                diaryMood = entry.moodKey
                render(preserveScroll = false)
            }
        }
        val labels = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
        }
        val titleRow = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val isToday = entry.epochDay == LocalDate.now().toEpochDay()
        titleRow.addView(newText(
            host,
            if (isToday) "今天 · ${epochDate(entry.epochDay, "M月d日")}" else epochDate(entry.epochDay, "M月d日 · EEEE"),
            10f,
            if (editing) AppColors.cinnabar else AppColors.muted,
            AppFont.sansMedium,
        ))
        WorkbenchMood.option(entry.moodKey)?.let { mood ->
            titleRow.addView(simpleChip(host, mood.label, moodChipColor(mood), mood.color),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = host.dp(7) })
        }
        labels.addView(titleRow)
        labels.addView(newText(host, entry.text, 12.5f, AppColors.inkSoft, AppFont.serif).apply {
            setPadding(0, host.dp(6), 0, 0)
            maxLines = 4
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(host.dpf(3f), 1f)
        })
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(ImageView(host).apply {
            setImageResource(R.drawable.ic_trash)
            setColorFilter(AppColors.faint)
            contentDescription = "删除这篇日记"
            setOnClickListener {
                showConfirmDialog(
                    host,
                    "删除这一页？",
                    "${epochDate(entry.epochDay, "M月d日")} 的日记会从本机删除。",
                    "删除",
                    "留着",
                ) {
                    WorkbenchStore.update(host) { data ->
                        data.copy(diary = data.diary.filterNot { it.id == entry.id })
                    }
                    if (diaryEditingId == entry.id) {
                        diaryEditingId = null
                        diaryMood = null
                    }
                    render(preserveScroll = true)
                }
            }
        }, LinearLayout.LayoutParams(host.dp(18), host.dp(18)).apply {
            marginStart = host.dp(10)
            topMargin = host.dp(2)
        })
        return row
    }

    private fun refreshDiaryMoods() {
        diaryMoodViews.forEach { (view, mood) ->
            val selected = diaryMood == mood.key
            view.setTextColor(if (selected) mood.color else AppColors.muted)
            view.typeface = if (selected) AppFont.sansMedium else AppFont.sans
            view.background = moodChipBackground(mood, selected)
        }
    }

    private fun moodChipBackground(mood: MoodOption, selected: Boolean): android.graphics.drawable.Drawable {
        return rippleOn(
            host,
            roundedDrawable(
                host,
                if (selected) Color.argb(38, Color.red(mood.color), Color.green(mood.color), Color.blue(mood.color))
                else Color.TRANSPARENT,
                16f,
                if (selected) 0f else 0.8f,
                if (selected) Color.TRANSPARENT else AppColors.line,
            ),
            0x1FB05238,
        )
    }

    private fun moodChipColor(mood: MoodOption): Int =
        Color.argb(38, Color.red(mood.color), Color.green(mood.color), Color.blue(mood.color))

    // -------------------------------------------------------------- check-in

    private fun buildCheckIn(snapshot: WorkbenchSnapshot) {
        val today = LocalDate.now().toEpochDay()
        val plans = snapshot.plans
        val checked = plans.count { today in it.checkedDays }
        val summary = contentCard(host)
        summary.addView(newText(host, "今日打卡", 10f, AppColors.muted, AppFont.sansMedium, 0.08f))
        val countRow = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(0, host.dp(4), 0, 0)
        }
        countRow.addView(newText(host, "$checked", 32f, AppColors.cinnabar, AppFont.serif).apply {
            includeFontPadding = false
        })
        countRow.addView(newText(host, " / ${plans.size} 个小约定", 11f, AppColors.muted, AppFont.sans).apply {
            setPadding(host.dp(6), 0, 0, host.dp(4))
        })
        summary.addView(countRow)
        summary.addView(newText(
            host,
            if (plans.isEmpty()) "先给今天定一个小约定，完成它，就算赢了一点点。"
            else "每天完成一件小事，日子会慢慢长出秩序。",
            10.5f,
            AppColors.faint,
            AppFont.sans,
        ).apply { setPadding(0, host.dp(5), 0, 0) })

        if (plans.isNotEmpty()) {
            val weekStart = today - 6
            val weekDone = plans.sumOf { plan -> plan.checkedDays.count { it in weekStart..today } }
            val weekTotal = plans.size * 7
            val weekRatio = (weekDone.toFloat() / weekTotal).coerceIn(0f, 1f)
            val weekRow = LinearLayout(host).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, host.dp(10), 0, 0)
            }
            weekRow.addView(newText(host, "近 7 天", 10f, AppColors.muted, AppFont.sans),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            weekRow.addView(newText(host, "$weekDone / $weekTotal 次", 10f,
                if (weekDone > 0) AppColors.cinnabar else AppColors.faint, AppFont.sansMedium))
            summary.addView(weekRow)
            val weekTrack = LinearLayout(host).apply {
                orientation = LinearLayout.HORIZONTAL
                background = roundedDrawable(host, AppColors.line, 2f)
            }
            if (weekRatio > 0f) {
                weekTrack.addView(View(host).apply {
                    background = roundedDrawable(host, AppColors.cinnabar, 2f)
                }, LinearLayout.LayoutParams(0, host.dp(4), weekRatio))
            }
            if (weekRatio < 1f) {
                weekTrack.addView(View(host), LinearLayout.LayoutParams(0, host.dp(4), 1f - weekRatio))
            }
            summary.addView(weekTrack, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                host.dp(4),
            ).apply { topMargin = host.dp(6) })
        }

        val addRow = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, host.dp(9), 0, 0)
        }
        CheckInCategory.entries.forEachIndexed { index, category ->
            val chip = categoryChip(host, "＋ ${category.label}", false) {
                showAddPlanDialog(category)
            }
            addRow.addView(chip, LinearLayout.LayoutParams(0, host.dp(34), 1f).apply {
                if (index < CheckInCategory.entries.lastIndex) marginEnd = host.dp(4)
            })
        }
        summary.addView(addRow)
        content.addView(summary, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))

        if (plans.isEmpty()) {
            return
        }

        CheckInCategory.entries.forEach { category ->
            val group = plans.filter { it.category == category }.sortedBy { it.createdDay }
            if (group.isEmpty()) return@forEach
            content.addView(sectionLabel(host, "${category.label} · ${group.size}"), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = host.dp(22) })
            val card = contentCard(host, 0)
            group.forEachIndexed { index, plan ->
                if (index > 0) card.addView(cardDivider(host))
                card.addView(planRow(plan, today), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ))
            }
            content.addView(card, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = host.dp(10) })
        }
    }

    private fun showAddPlanDialog(category: CheckInCategory) {
        showInputDialog(
            host,
            "添加${category.label}打卡",
            "每天完成这个小约定，慢慢把日子过成喜欢的样子。",
            "",
            "例如：${exampleFor(category)}",
        ) { value ->
            val title = value.trim()
            if (title.isEmpty()) {
                host.showNotice("给这个打卡起个名字吧")
                return@showInputDialog false
            }
            val id = WorkbenchStore.newId()
            WorkbenchStore.update(host) { data ->
                data.copy(
                    plans = data.plans + CheckInPlan(
                        id = id,
                        title = title,
                        category = category,
                        createdDay = LocalDate.now().toEpochDay(),
                        checkedDays = emptySet(),
                    ),
                )
            }
            host.showNotice("打卡已经放进「${category.label}」")
            render(preserveScroll = false)
            true
        }
    }

    private fun exampleFor(category: CheckInCategory): String = when (category) {
        CheckInCategory.DAILY -> "喝够八杯水"
        CheckInCategory.LOVE -> "认真抱抱对方"
        CheckInCategory.FAN -> "给喜欢的人留一句话"
    }

    private fun planRow(plan: CheckInPlan, today: Long): View {
        val isChecked = today in plan.checkedDays
        val row = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(host.dp(16), host.dp(12), host.dp(12), host.dp(12))
            isClickable = true
            isFocusable = true
            background = rippleOn(host, roundedDrawable(host, Color.TRANSPARENT, 18f), 0x1427231E)
            setOnClickListener {
                WorkbenchStore.update(host) { data ->
                    data.copy(plans = data.plans.map { current ->
                        if (current.id != plan.id) current else current.copy(
                            checkedDays = if (isChecked) current.checkedDays - today
                            else current.checkedDays + today,
                        )
                    })
                }
                render(preserveScroll = true)
            }
        }
        row.addView(newText(host, if (isChecked) "✓" else "", 12f, AppColors.cream, AppFont.sansMedium).apply {
            gravity = Gravity.CENTER
            background = if (isChecked) {
                roundedDrawable(host, categoryColor(plan.category), 15f)
            } else {
                roundedDrawable(host, Color.TRANSPARENT, 15f, 1f, AppColors.line)
            }
        }, LinearLayout.LayoutParams(host.dp(30), host.dp(30)))
        val labels = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(10), 0, host.dp(8), 0)
        }
        labels.addView(newText(host, plan.title, 13.5f, AppColors.ink, AppFont.sans))
        val streak = streakOf(plan, today)
        labels.addView(newText(
            host,
            if (streak > 0) "连续 $streak 天" else "累计 ${plan.checkedDays.size} 天",
            9.5f,
            if (streak > 0) categoryColor(plan.category) else AppColors.faint,
            AppFont.sans,
        ).apply { setPadding(0, host.dp(3), 0, 0) })
        val dots = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, host.dp(5), 0, 0)
        }
        for (offset in 6 downTo 0) {
            val day = today - offset
            val checked = day in plan.checkedDays
            val dot = View(host).apply {
                background = if (checked) {
                    roundedDrawable(host, categoryColor(plan.category), 3f)
                } else {
                    roundedDrawable(host, Color.TRANSPARENT, 3f, 1f, AppColors.line)
                }
            }
            dots.addView(dot, LinearLayout.LayoutParams(host.dp(6), host.dp(6)).apply {
                if (offset > 0) marginEnd = host.dp(4)
            })
        }
        labels.addView(dots)
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(ImageView(host).apply {
            setImageResource(R.drawable.ic_edit)
            setColorFilter(AppColors.faint)
            contentDescription = "编辑这个打卡"
            setOnClickListener {
                showInputDialog(
                    host,
                    "编辑打卡",
                    "给这个小约定换一个更顺手的名字。",
                    plan.title,
                    "例如：喝够八杯水",
                ) { value ->
                    val title = value.trim()
                    if (title.isEmpty()) {
                        host.showNotice("给这个打卡起个名字吧")
                        return@showInputDialog false
                    }
                    WorkbenchStore.update(host) { data ->
                        data.copy(plans = data.plans.map {
                            if (it.id == plan.id) it.copy(title = title) else it
                        })
                    }
                    host.showNotice("打卡名字改好了")
                    render(preserveScroll = true)
                    true
                }
            }
        }, LinearLayout.LayoutParams(host.dp(17), host.dp(17)).apply { marginEnd = host.dp(8) })
        row.addView(ImageView(host).apply {
            setImageResource(R.drawable.ic_trash)
            setColorFilter(AppColors.faint)
            contentDescription = "删除这个打卡"
            setOnClickListener {
                showConfirmDialog(
                    host,
                    "删除打卡？",
                    "「${plan.title}」和它的所有打卡记录都会从本机删除。",
                    "删除",
                    "留着",
                ) {
                    WorkbenchStore.update(host) { data ->
                        data.copy(plans = data.plans.filterNot { it.id == plan.id })
                    }
                    render(preserveScroll = true)
                }
            }
        }, LinearLayout.LayoutParams(host.dp(17), host.dp(17)).apply { marginEnd = host.dp(2) })
        return row
    }

    private fun streakOf(plan: CheckInPlan, today: Long): Int {
        var day = today
        var count = 0
        while (day in plan.checkedDays) {
            count++
            day--
        }
        return count
    }

    // ---------------------------------------------------------------- ledger

    private fun buildLedger(snapshot: WorkbenchSnapshot) {
        content.addView(ledgerModeBar(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        content.addView(ledgerPeriodBar(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(10) })

        when (ledgerMode) {
            LedgerMode.MONTH -> buildMonthLedger(snapshot)
            LedgerMode.YEAR -> buildYearLedger(snapshot)
        }
    }

    private fun ledgerModeBar(): View {
        val row = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val segment = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(host.dp(3), host.dp(3), host.dp(3), host.dp(3))
            background = roundedDrawable(host, AppColors.paperDeep, 18f)
        }
        listOf("月账单" to LedgerMode.MONTH, "年账单" to LedgerMode.YEAR).forEachIndexed { index, (label, mode) ->
            val selected = ledgerMode == mode
            val tab = newText(
                host,
                label,
                12f,
                if (selected) AppColors.ink else AppColors.muted,
                if (selected) AppFont.sansMedium else AppFont.sans,
            ).apply {
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                background = if (selected) {
                    roundedDrawable(host, AppColors.cream, 15f, 0.8f, AppColors.line)
                } else {
                    roundedDrawable(host, Color.TRANSPARENT, 15f)
                }
                setOnClickListener {
                    if (ledgerMode != mode) {
                        if (mode == LedgerMode.YEAR) ledgerYear = ledgerMonth.year
                        if (mode == LedgerMode.MONTH) ledgerMonth = YearMonth.of(ledgerYear, ledgerMonth.monthValue)
                        ledgerMode = mode
                        render(preserveScroll = false)
                    }
                }
            }
            segment.addView(tab, LinearLayout.LayoutParams(
                0,
                host.dp(34),
                1f,
            ).apply { if (index == 0) marginEnd = host.dp(3) })
        }
        row.addView(segment, LinearLayout.LayoutParams(0, host.dp(40), 1f))
        row.addView(softAction(host, "＋ 记一笔") { openLedgerEditor(null) }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            host.dp(40),
        ).apply { marginStart = host.dp(8) })
        return row
    }

    private fun ledgerPeriodBar(): View {
        val row = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val isMonth = ledgerMode == LedgerMode.MONTH
        val label = if (isMonth) yearMonthLabel(ledgerMonth) else "${ledgerYear} 年"
        val prev = navArrow("‹", if (isMonth) "上个月" else "上一年") {
            if (isMonth) {
                ledgerMonth = ledgerMonth.minusMonths(1)
                ledgerYear = ledgerMonth.year
            } else {
                ledgerYear--
                ledgerMonth = YearMonth.of(ledgerYear, ledgerMonth.monthValue)
            }
            render(preserveScroll = false)
        }
        val next = navArrow("›", if (isMonth) "下个月" else "下一年") {
            if (isMonth) {
                ledgerMonth = ledgerMonth.plusMonths(1)
                ledgerYear = ledgerMonth.year
            } else {
                ledgerYear++
                ledgerMonth = YearMonth.of(ledgerYear, ledgerMonth.monthValue)
            }
            render(preserveScroll = false)
        }
        row.addView(prev, LinearLayout.LayoutParams(host.dp(34), host.dp(34)))
        row.addView(newText(host, label, 13.5f, AppColors.ink, AppFont.sansMedium).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, host.dp(34), 1f))
        row.addView(next, LinearLayout.LayoutParams(host.dp(34), host.dp(34)))
        val now = LocalDate.now()
        val isCurrent = if (isMonth) ledgerMonth == YearMonth.from(now) else ledgerYear == now.year
        if (!isCurrent) {
            row.addView(softAction(host, if (isMonth) "本月" else "今年") {
                if (isMonth) ledgerMonth = YearMonth.from(now) else ledgerYear = now.year
                render(preserveScroll = false)
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                host.dp(34),
            ).apply { marginStart = host.dp(6) })
        }
        return row
    }

    private fun navArrow(label: String, description: String, onClick: () -> Unit): TextView {
        return newText(host, label, 23f, AppColors.inkSoft, AppFont.serif).apply {
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = description
            background = rippleOn(
                host,
                roundedDrawable(host, AppColors.cream, 17f, 0.8f, AppColors.line),
                0x1427231E,
            )
            setOnClickListener { onClick() }
        }
    }

    private fun buildMonthLedger(snapshot: WorkbenchSnapshot) {
        val entries = snapshot.ledger.inMonth(ledgerMonth).sortedByDescending { it.epochDay }
        val expense = entries.expenseTotal()
        val income = entries.incomeTotal()
        content.addView(ledgerSummaryCard("${ledgerMonth.monthValue} 月支出", expense, income, entries.size),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = host.dp(10) })

        if (entries.isEmpty()) {
            content.addView(emptyLedgerCard(), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = host.dp(12) })
            return
        }

        val expenseEntries = entries.filter { !it.income }
        if (expenseEntries.isNotEmpty()) {
            content.addView(sectionLabel(host, "支出分类"), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = host.dp(24) })
            content.addView(categoryBreakdownCard(expenseEntries, income = false), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = host.dp(10) })
        }
        val incomeEntries = entries.filter { it.income }
        if (incomeEntries.isNotEmpty()) {
            content.addView(sectionLabel(host, "收入分类"), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = host.dp(24) })
            content.addView(categoryBreakdownCard(incomeEntries, income = true), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = host.dp(10) })
        }

        content.addView(sectionLabel(host, "流水 · ${entries.size} 笔"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(24) })
        content.addView(ledgerFlowCard(entries), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(10) })
    }

    private fun buildYearLedger(snapshot: WorkbenchSnapshot) {
        val entries = snapshot.ledger.inYear(ledgerYear).sortedByDescending { it.epochDay }
        val expense = entries.expenseTotal()
        val income = entries.incomeTotal()
        content.addView(ledgerSummaryCard("${ledgerYear} 年支出", expense, income, entries.size),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = host.dp(10) })

        val monthValues = (1..12).map { month ->
            entries.filter { LocalDate.ofEpochDay(it.epochDay).monthValue == month }.expenseTotal()
        }
        val chartCard = contentCard(host)
        val head = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(newText(host, "每月支出", 10f, AppColors.muted, AppFont.sansMedium, 0.08f),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val average = if (entries.isEmpty()) 0L else expense / 12
        head.addView(newText(host, "月均 ${formatMoney(average)}", 10f, AppColors.muted, AppFont.sans))
        chartCard.addView(head)
        val chart = LedgerYearChartView(host).apply {
            setValues(monthValues, if (ledgerYear == LocalDate.now().year) LocalDate.now().monthValue else null)
        }
        chartCard.addView(chart, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            host.dp(158),
        ).apply { topMargin = host.dp(8) })
        content.addView(chartCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(12) })

        if (entries.isNotEmpty()) {
            content.addView(sectionLabel(host, "全年支出分类"), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = host.dp(24) })
            content.addView(categoryBreakdownCard(entries.filter { !it.income }, income = false),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = host.dp(10) })
        }

        content.addView(sectionLabel(host, "月度明细"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(24) })
        content.addView(yearMonthsCard(entries), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(10) })
    }

    private fun ledgerSummaryCard(label: String, expense: Long, income: Long, count: Int): View {
        val summary = contentCard(host)
        val head = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(newText(host, label, 10f, AppColors.muted, AppFont.sansMedium, 0.08f),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(newText(host, "$count 笔", 9.5f, AppColors.faint, AppFont.sans))
        summary.addView(head)
        summary.addView(newText(host, formatMoney(expense), 31f, AppColors.ink, AppFont.serif).apply {
            setPadding(0, host.dp(4), 0, 0)
            includeFontPadding = false
        })
        val line = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, host.dp(7), 0, 0)
        }
        line.addView(newText(host, "收入 ${formatMoney(income)}", 10.5f, AppColors.sage, AppFont.sans))
        line.addView(newText(host, "  ·  结余 ${formatMoney(income - expense)}", 10.5f, AppColors.muted, AppFont.sans))
        summary.addView(line)
        return summary
    }

    private fun emptyLedgerCard(): View {
        val card = contentCard(host)
        card.addView(newText(host, "这个${if (ledgerMode == LedgerMode.MONTH) "月" else "年"}还没有记账",
            19f, AppColors.ink, AppFont.serif))
        card.addView(newText(host, "记下第一笔，月底就能看见钱都去了哪里。", 10.5f, AppColors.muted, AppFont.sans)
            .apply { setPadding(0, host.dp(6), 0, 0) })
        val action = primaryAction(host, "记下第一笔") { openLedgerEditor(null) }
        card.addView(action, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            host.dp(44),
        ).apply { topMargin = host.dp(12) })
        return card
    }

    private fun categoryBreakdownCard(entries: List<LedgerEntry>, income: Boolean): View {
        val card = contentCard(host, 0)
        val grouped = entries.groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amountCents } }
            .toList()
            .sortedByDescending { it.second }
        val total = grouped.sumOf { it.second }.coerceAtLeast(1)
        grouped.forEachIndexed { index, (category, amount) ->
            if (index > 0) card.addView(cardDivider(host))
            val option = LedgerCategories.option(category, income)
            val wrapper = LinearLayout(host).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(host.dp(16), host.dp(11), host.dp(16), host.dp(9))
            }
            val top = LinearLayout(host).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            top.addView(View(host).apply {
                background = roundedDrawable(host, option.color, 4f)
            }, LinearLayout.LayoutParams(host.dp(8), host.dp(8)))
            top.addView(newText(host, option.label, 12f, AppColors.ink, AppFont.sansMedium).apply {
                setPadding(host.dp(8), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            top.addView(newText(host, formatMoney(amount), 11.5f, AppColors.inkSoft, AppFont.sansMedium))
            wrapper.addView(top)
            val percent = (amount.toFloat() / total).coerceIn(0f, 1f)
            val track = LinearLayout(host).apply {
                orientation = LinearLayout.HORIZONTAL
                background = roundedDrawable(host, AppColors.line, 2f)
            }
            if (percent > 0f) {
                track.addView(View(host).apply {
                    background = roundedDrawable(host, option.color, 2f)
                }, LinearLayout.LayoutParams(0, host.dp(4), percent))
            }
            if (percent < 1f) {
                track.addView(View(host), LinearLayout.LayoutParams(0, host.dp(4), 1f - percent))
            }
            wrapper.addView(track, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                host.dp(4),
            ).apply { topMargin = host.dp(7) })
            card.addView(wrapper, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
        return card
    }

    private fun ledgerFlowCard(entries: List<LedgerEntry>): View {
        val card = contentCard(host, 0)
        val groups = entries.groupBy { it.epochDay }
        var firstGroup = true
        groups.forEach { (day, dayEntries) ->
            if (!firstGroup) {
                card.addView(View(host).apply { setBackgroundColor(AppColors.line) }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    host.dp(1),
                ))
            }
            firstGroup = false
            val dayExpense = dayEntries.filter { !it.income }.sumOf { it.amountCents }
            val dayIncome = dayEntries.filter { it.income }.sumOf { it.amountCents }
            val head = LinearLayout(host).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(host.dp(16), host.dp(11), host.dp(16), host.dp(3))
            }
            head.addView(newText(host, epochDate(day, "M月d日 · EEEE"), 10f, AppColors.muted, AppFont.sans),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val dayText = when {
                dayExpense > 0 && dayIncome > 0 -> "支出 ${formatMoney(dayExpense)} · 收入 ${formatMoney(dayIncome)}"
                dayExpense > 0 -> "支出 ${formatMoney(dayExpense)}"
                dayIncome > 0 -> "收入 ${formatMoney(dayIncome)}"
                else -> ""
            }
            head.addView(newText(host, dayText, 9f, AppColors.faint, AppFont.sans))
            card.addView(head)
            dayEntries.forEachIndexed { index, entry ->
                if (index > 0) card.addView(cardDivider(host).apply {
                    setPadding(host.dp(16), 0, host.dp(16), 0)
                })
                card.addView(ledgerRow(entry), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ))
            }
        }
        return card
    }

    private fun yearMonthsCard(entries: List<LedgerEntry>): View {
        val card = contentCard(host, 0)
        val now = LocalDate.now()
        (1..12).forEach { month ->
            if (month > 1) card.addView(cardDivider(host))
            val monthEntries = entries.filter { LocalDate.ofEpochDay(it.epochDay).monthValue == month }
            val monthExpense = monthEntries.expenseTotal()
            val monthIncome = monthEntries.incomeTotal()
            val isNow = ledgerYear == now.year && month == now.monthValue
            val row = LinearLayout(host).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(host.dp(16), host.dp(11), host.dp(16), host.dp(11))
                isClickable = true
                isFocusable = true
                background = rippleOn(host, roundedDrawable(host, Color.TRANSPARENT, 16f), 0x1427231E)
                setOnClickListener {
                    ledgerMonth = YearMonth.of(ledgerYear, month)
                    ledgerMode = LedgerMode.MONTH
                    render(preserveScroll = false)
                }
            }
            row.addView(newText(host, "$month 月", 11.5f,
                if (isNow) AppColors.cinnabar else AppColors.inkSoft,
                if (isNow) AppFont.sansMedium else AppFont.sans))
            row.addView(newText(host,
                when {
                    monthEntries.isEmpty() -> "没有记录"
                    monthExpense > 0 && monthIncome > 0 -> "支出 ${formatMoney(monthExpense)} · 收入 ${formatMoney(monthIncome)}"
                    monthExpense > 0 -> "支出 ${formatMoney(monthExpense)}"
                    else -> "收入 ${formatMoney(monthIncome)}"
                },
                10.5f,
                if (monthEntries.isEmpty()) AppColors.faint else AppColors.muted,
                AppFont.sans,
            ).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ))
            row.addView(newText(host, "›", 17f, AppColors.faint, AppFont.serif).apply {
                setPadding(host.dp(7), 0, 0, 0)
            })
            card.addView(row)
        }
        return card
    }

    private fun openLedgerEditor(entry: LedgerEntry?) {
        val now = LocalDate.now()
        val defaultDay = when {
            entry != null -> entry.epochDay
            ledgerMode == LedgerMode.MONTH && ledgerMonth != YearMonth.from(now) -> ledgerMonth.atDay(1).toEpochDay()
            ledgerMode == LedgerMode.YEAR && ledgerYear != now.year -> LocalDate.of(ledgerYear, 1, 1).toEpochDay()
            else -> now.toEpochDay()
        }
        val draft = if (entry == null) {
            LedgerDraft(epochDay = defaultDay, category = "餐饮")
        } else {
            LedgerDraft(
                id = entry.id,
                income = entry.income,
                amountCents = entry.amountCents,
                category = entry.category,
                epochDay = entry.epochDay,
                note = entry.note,
            )
        }
        showLedgerEditorDialog(host, draft) { result ->
            val saved = LedgerEntry(
                id = result.id ?: WorkbenchStore.newId(),
                epochDay = result.epochDay,
                income = result.income,
                amountCents = result.amountCents,
                category = result.category,
                note = result.note,
            )
            WorkbenchStore.update(host) { data ->
                data.copy(ledger = (data.ledger.filterNot { it.id == saved.id } + saved)
                    .sortedByDescending { it.epochDay })
            }
            ledgerMode = LedgerMode.MONTH
            ledgerMonth = YearMonth.from(LocalDate.ofEpochDay(saved.epochDay))
            host.showNotice(if (entry == null) "记好了" else "改好了")
            render(preserveScroll = false)
        }
    }

    private fun ledgerRow(entry: LedgerEntry): View {
        val option = LedgerCategories.option(entry.category, entry.income)
        val row = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(host.dp(14), host.dp(10), host.dp(10), host.dp(10))
            isClickable = true
            isFocusable = true
            contentDescription = "编辑${entry.category}这笔记录"
            background = rippleOn(host, roundedDrawable(host, Color.TRANSPARENT, 16f), 0x1427231E)
            setOnClickListener { openLedgerEditor(entry) }
        }
        row.addView(iconSeal(host, entry.category.take(1), option.color, option.soft, 32),
            LinearLayout.LayoutParams(host.dp(32), host.dp(32)))
        val labels = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(10), 0, host.dp(8), 0)
        }
        labels.addView(newText(host, entry.category, 12.5f, AppColors.ink, AppFont.sansMedium))
        if (entry.note.isNotBlank()) {
            labels.addView(newText(host, entry.note, 9.5f, AppColors.faint, AppFont.sans).apply {
                setPadding(0, host.dp(3), 0, 0)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(newText(
            host,
            (if (entry.income) "+" else "-") + formatMoney(entry.amountCents),
            12.5f,
            if (entry.income) AppColors.sage else AppColors.cinnabar,
            AppFont.sansMedium,
        ))
        row.addView(ImageView(host).apply {
            setImageResource(R.drawable.ic_trash)
            setColorFilter(AppColors.faint)
            contentDescription = "删除这笔记录"
            setOnClickListener {
                showConfirmDialog(
                    host,
                    "删除这笔记录？",
                    "${entry.category} ${formatMoney(entry.amountCents)} 会从本机删除。",
                    "删除",
                    "留着",
                ) {
                    WorkbenchStore.update(host) { data ->
                        data.copy(ledger = data.ledger.filterNot { it.id == entry.id })
                    }
                    render(preserveScroll = true)
                }
            }
        }, LinearLayout.LayoutParams(host.dp(17), host.dp(17)).apply { marginStart = host.dp(9) })
        return row
    }

    // ---------------------------------------------------------------- period

    private val periodQuotes = listOf(
        "身体有自己的节律，慢一点也没关系。",
        "这几天，先把自己放在第一位。",
        "不必逞强，休息也是在好好生活。",
        "记录不是为了焦虑，而是更懂自己。",
        "愿你有热水、有软枕，也有一点点被照顾的感觉。",
        "经期不是需要被纠正的日子，只是需要被温柔对待。",
        "今天的任务只有一个：舒服一点。",
        "累的时候就停一停，日子不会因此落下。",
    )

    private fun buildPeriod(snapshot: WorkbenchSnapshot) {
        val todayDate = LocalDate.now()
        val today = todayDate.toEpochDay()
        val latest = snapshot.latestPeriod()
        val summary = periodSummary(snapshot)

        content.addView(periodHero(snapshot, latest, summary, today), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        content.addView(periodCalendarCard(snapshot, summary, today), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(12) })
        if (snapshot.periods.isNotEmpty()) {
            content.addView(periodAnalysisCard(snapshot, latest, summary), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = host.dp(12) })
        }
        content.addView(periodCareCard(latest, today, snapshot.cycleLength), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(12) })
        addPeriodHistory(snapshot, summary, today)
    }

    private fun periodHero(
        snapshot: WorkbenchSnapshot,
        latest: PeriodRecord?,
        summary: PeriodSummary,
        today: Long,
    ): View {
        val card = contentCard(host)
        val head = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(newText(host, "周期记录", 10f, AppColors.muted, AppFont.sansMedium, 0.08f),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(newText(host, "只存在本机", 9f, AppColors.faint, AppFont.sans))
        card.addView(head)

        val status: String
        val subtitle: String
        if (latest == null) {
            status = "从一次记录开始"
            subtitle = "记下开始和结束，慢慢看见自己的节律。"
        } else if (latest.endDay == null) {
            val day = (today - latest.startDay + 1).coerceAtLeast(1)
            status = "经期第 $day 天"
            subtitle = "从 ${epochDate(latest.startDay, "M月d日")} 开始 · 周期 ${snapshot.cycleLength} 天"
        } else {
            val next = latest.startDay + snapshot.cycleLength
            val days = next - today
            status = when {
                days > 0 -> "预计 $days 天后"
                days == 0L -> "预计就是今天"
                else -> "预计时间已到"
            }
            subtitle = "上次 ${epochDate(latest.startDay, "M月d日")} 开始 · 周期 ${snapshot.cycleLength} 天"
        }
        card.addView(newText(host, status, 28f, AppColors.ink, AppFont.serif).apply {
            setPadding(0, host.dp(6), 0, 0)
            includeFontPadding = false
        })
        card.addView(newText(host, subtitle, 10.5f, AppColors.muted, AppFont.sans).apply {
            setPadding(0, host.dp(6), 0, 0)
        })

        val averageText = when {
            summary.avgCycle != null && summary.avgPeriod != null ->
                "平均周期 ${summary.avgCycle} 天 · 平均经期 ${summary.avgPeriod} 天"
            summary.avgCycle != null -> "平均周期 ${summary.avgCycle} 天"
            else -> "再记录一次，就能看到平均周期。"
        }
        card.addView(newText(host, averageText, 10f, AppColors.faint, AppFont.sans).apply {
            setPadding(0, host.dp(5), 0, 0)
        })

        val cycleRow = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, host.dp(9), 0, 0)
        }
        cycleRow.addView(newText(host, "周期长度 ${snapshot.cycleLength} 天", 10.5f, AppColors.muted, AppFont.sans),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        cycleRow.addView(softAction(host, "修改") { editCycleLength(snapshot.cycleLength) })
        card.addView(cycleRow)

        if (latest != null) {
            val cycleLength = snapshot.cycleLength.coerceAtLeast(1)
            val cycleDay = (today - latest.startDay + 1).coerceAtLeast(1)
            val ratio = ((today - latest.startDay).toFloat() / cycleLength).coerceIn(0f, 1f)
            val next = latest.startDay + cycleLength
            val progressHead = LinearLayout(host).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, host.dp(10), 0, 0)
            }
            progressHead.addView(newText(host, "周期第 $cycleDay 天", 9.5f, AppColors.muted, AppFont.sans),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            progressHead.addView(newText(
                host,
                if (next > today) "预计 ${epochDate(next, "M月d日")}" else "预计时间已到",
                9.5f,
                AppColors.faint,
                AppFont.sans,
            ))
            card.addView(progressHead)
            val track = LinearLayout(host).apply {
                orientation = LinearLayout.HORIZONTAL
                background = roundedDrawable(host, AppColors.line, 2f)
            }
            if (ratio > 0f) {
                track.addView(View(host).apply {
                    background = roundedDrawable(host, Color.rgb(168, 112, 118), 2f)
                }, LinearLayout.LayoutParams(0, host.dp(4), ratio))
            }
            if (ratio < 1f) {
                track.addView(View(host), LinearLayout.LayoutParams(0, host.dp(4), 1f - ratio))
            }
            card.addView(track, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                host.dp(4),
            ).apply { topMargin = host.dp(6) })
        }

        val actions = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, host.dp(11), 0, 0)
        }
        actions.addView(primaryAction(host, if (latest != null && latest.endDay == null) "记录新的开始" else "记录开始") {
            pickPeriodStart()
        }, LinearLayout.LayoutParams(0, host.dp(44), 1f))
        actions.addView(secondaryAction(host, "今天结束", enabled = latest != null && latest.endDay == null) {
            endPeriod()
        }, LinearLayout.LayoutParams(0, host.dp(44), 1f).apply { marginStart = host.dp(8) })
        card.addView(actions)
        return card
    }

    private fun periodCalendarCard(
        snapshot: WorkbenchSnapshot,
        summary: PeriodSummary,
        today: Long,
    ): View {
        val card = contentCard(host)
        val head = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(navArrow("‹", "上个月") {
            periodMonth = periodMonth.minusMonths(1)
            render(preserveScroll = false)
        }, LinearLayout.LayoutParams(host.dp(32), host.dp(32)))
        head.addView(newText(host, yearMonthLabel(periodMonth), 12.5f, AppColors.ink, AppFont.sansMedium).apply {
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(0, host.dp(32), 1f))
        head.addView(navArrow("›", "下个月") {
            periodMonth = periodMonth.plusMonths(1)
            render(preserveScroll = false)
        }, LinearLayout.LayoutParams(host.dp(32), host.dp(32)))
        if (periodMonth != YearMonth.now()) {
            head.addView(softAction(host, "本月") {
                periodMonth = YearMonth.now()
                render(preserveScroll = false)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, host.dp(32)).apply {
                marginStart = host.dp(5)
            })
        }
        card.addView(head)

        val (actual, predicted) = periodCalendarDays(snapshot, summary, today)
        val calendar = PeriodCalendarView(host).apply {
            setData(periodMonth, actual, predicted, today)
        }
        card.addView(calendar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            host.dp(228),
        ).apply { topMargin = host.dp(4) })

        val legend = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, host.dp(3), 0, 0)
        }
        legend.addView(newText(host, "●", 9f, AppColors.cinnabar, AppFont.sans))
        legend.addView(newText(host, " 经期   ", 8.8f, AppColors.faint, AppFont.sans))
        legend.addView(newText(host, "○", 9f, AppColors.cinnabar, AppFont.sans))
        legend.addView(newText(host, " 预计   ", 8.8f, AppColors.faint, AppFont.sans))
        legend.addView(newText(host, "◎", 9f, AppColors.inkSoft, AppFont.sans))
        legend.addView(newText(host, " 今天", 8.8f, AppColors.faint, AppFont.sans))
        card.addView(legend)
        return card
    }

    private fun periodCalendarDays(
        snapshot: WorkbenchSnapshot,
        summary: PeriodSummary,
        today: Long,
    ): Pair<Set<Long>, Set<Long>> {
        val actual = mutableSetOf<Long>()
        snapshot.periods.forEach { period ->
            val end = (period.endDay ?: today).coerceAtMost(today)
            if (period.startDay <= end) {
                for (day in period.startDay..end) actual += day
            }
        }
        val predicted = mutableSetOf<Long>()
        val latest = snapshot.latestPeriod()
        if (latest != null) {
            val length = (summary.avgPeriod ?: 5).coerceIn(2, 10)
            val starts = predictedStarts(latest.startDay, snapshot.cycleLength, today, 3)
            starts.forEach { start ->
                for (day in start until start + length) {
                    if (day !in actual) predicted += day
                }
            }
        }
        return actual to predicted
    }

    private fun predictedStarts(latestStart: Long, cycleLength: Int, today: Long, count: Int): List<Long> {
        val cycle = cycleLength.coerceAtLeast(1)
        var next = latestStart + cycle
        while (next < today - 7) next += cycle
        return (0 until count).map { next + cycle.toLong() * it }
    }

    private fun periodAnalysisCard(
        snapshot: WorkbenchSnapshot,
        latest: PeriodRecord?,
        summary: PeriodSummary,
    ): View {
        val card = contentCard(host)
        val head = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(newText(host, "周期分析", 10f, AppColors.muted, AppFont.sansMedium, 0.08f),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(newText(host, "记录 ${snapshot.periods.size} 次", 9.5f, AppColors.faint, AppFont.sans))
        card.addView(head)

        fun statCell(label: String, value: String, unit: String = ""): View {
            return LinearLayout(host).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, host.dp(9), host.dp(8), 0)
                addView(newText(host, label, 9f, AppColors.faint, AppFont.sans))
                val valueRow = LinearLayout(host).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.BOTTOM
                    setPadding(0, host.dp(2), 0, 0)
                }
                valueRow.addView(newText(host, value, 20f, AppColors.ink, AppFont.serif).apply {
                    includeFontPadding = false
                })
                if (unit.isNotBlank()) {
                    valueRow.addView(newText(host, " $unit", 9f, AppColors.muted, AppFont.sans).apply {
                        setPadding(0, 0, 0, host.dp(3))
                    })
                }
                addView(valueRow)
            }
        }

        val firstRow = LinearLayout(host).apply { orientation = LinearLayout.HORIZONTAL }
        firstRow.addView(statCell("平均周期", summary.avgCycle?.toString() ?: "—", "天"),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        firstRow.addView(statCell("平均经期", summary.avgPeriod?.toString() ?: "—", "天"),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(firstRow)
        val secondRow = LinearLayout(host).apply { orientation = LinearLayout.HORIZONTAL }
        secondRow.addView(statCell("规律度", summary.regularity),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val range = if (summary.minCycle != null && summary.maxCycle != null) {
            "${summary.minCycle}–${summary.maxCycle}"
        } else "—"
        secondRow.addView(statCell("周期范围", range, "天"),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(secondRow)

        if (summary.intervals.isNotEmpty()) {
            card.addView(newText(host,
                "最近周期 " + summary.intervals.takeLast(6).joinToString(" · ") + " 天",
                9.5f, AppColors.muted, AppFont.sans).apply {
                setPadding(0, host.dp(9), 0, 0)
            })
        }
        if (latest != null) {
            val starts = predictedStarts(latest.startDay, snapshot.cycleLength, LocalDate.now().toEpochDay(), 3)
            card.addView(newText(host,
                "接下来三次预计：" + starts.joinToString("、") { epochDate(it, "M月d日") },
                9.5f, AppColors.cinnabar, AppFont.sansMedium).apply {
                setPadding(0, host.dp(9), 0, 0)
            })
        }
        card.addView(newText(host,
            "按你的记录和周期长度估算，仅供参考，不用于诊断或避孕。",
            8.8f, AppColors.faint, AppFont.sans).apply {
            setPadding(0, host.dp(6), 0, 0)
        })
        return card
    }

    private fun periodCareCard(latest: PeriodRecord?, today: Long, cycleLength: Int): View {
        val card = contentCard(host)
        val quote = periodQuotes[(today % periodQuotes.size).toInt()]
        card.addView(newText(host, "今天想对你说", 9.5f, AppColors.muted, AppFont.sans, 0.08f))
        card.addView(newText(host, quote, 16f, AppColors.ink, AppFont.serif).apply {
            setPadding(0, host.dp(7), 0, 0)
            setLineSpacing(host.dpf(4f), 1f)
        })
        val tips = periodCareTips(latest, today, cycleLength)
        tips.forEach { tip ->
            val row = LinearLayout(host).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, host.dp(7), 0, 0)
            }
            row.addView(newText(host, "·", 12f, AppColors.cinnabar, AppFont.sans),
                LinearLayout.LayoutParams(host.dp(12), ViewGroup.LayoutParams.WRAP_CONTENT))
            row.addView(newText(host, tip, 10.3f, AppColors.inkSoft, AppFont.sans).apply {
                setLineSpacing(host.dpf(2f), 1f)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            card.addView(row)
        }
        card.addView(softAction(host, "更多建议") {
            showInfoDialog(
                host,
                "这几天，可以对自己好一点",
                fullPeriodTips(),
                "知道了",
            )
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            host.dp(34),
        ).apply { topMargin = host.dp(7) })
        return card
    }

    private fun periodCareTips(latest: PeriodRecord?, today: Long, cycleLength: Int): List<String> {
        return when {
            latest != null && latest.endDay == null -> listOf(
                "热敷小腹，或洗个热水澡，让身体慢慢放松。",
                "能躺就躺一会儿；轻走、拉伸也可以，不必硬撑。",
                "多喝水，吃点含铁食物：瘦肉、豆类、深绿色蔬菜。",
            )
            latest != null && latest.startDay + cycleLength - today in 0..5L -> listOf(
                "这几天把安排放松一点，给自己留些余地。",
                "提前准备卫生用品、热水袋和舒服的衣服。",
                "早点睡，少熬一点；情绪有起伏时先照顾自己。",
            )
            else -> listOf(
                "记录周期，也记录身体的感觉，慢慢更懂自己。",
                "保持轻走、拉伸和规律作息，不用追求完美。",
                "有不确定的变化时，先观察，必要时咨询医生。",
            )
        }
    }

    private fun fullPeriodTips(): String =
        "· 热敷小腹，或洗个热水澡，让身体慢慢放松。\n" +
            "· 轻走、拉伸都可以；累了就休息，不必硬撑。\n" +
            "· 多喝水，吃点含铁食物：瘦肉、豆类、深绿色蔬菜。\n" +
            "· 疼痛可按说明书使用止痛药；有哮喘、胃/肝肾问题或正在服其他药，先咨询医生或药师；16 岁以下不要用阿司匹林。\n" +
            "· 如果疼痛影响日常生活、出血量突然很大（例如每小时湿透一片）、经期超过 7 天，或伴发热、晕厥，请及时就医。\n" +
            "· 记录症状和周期，就诊时更有帮助。\n\n" +
            "以上是一般生活建议，不替代医生诊断，也不用于避孕。参考 NHS 与 ACOG 公开资料。"

    private fun addPeriodHistory(snapshot: WorkbenchSnapshot, summary: PeriodSummary, today: Long) {
        val header = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, host.dp(24), 0, 0)
        }
        header.addView(sectionLabel(host, "周期记录 · ${summary.sorted.size}"),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (summary.sorted.isNotEmpty()) {
            header.addView(newText(host, "左滑删除", 9f, AppColors.faint, AppFont.sans))
        }
        content.addView(header)

        if (summary.sorted.isEmpty()) {
            content.addView(emptyCard("还没有周期记录。点「记录开始」，先记下这一次。"), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = host.dp(10) })
            return
        }
        summary.sorted.sortedByDescending { it.startDay }.forEach { period ->
            val index = summary.sorted.indexOfFirst { it.id == period.id }
            val previous = if (index > 0) summary.sorted[index - 1] else null
            val row = periodCardRow(period, previous, today)
            val swipe = SwipeToDeleteLayout(host).apply {
                setCornerRadiusDp(18f)
                setContent(row, fillHeight = false)
                onDelete = { deletePeriod(period) }
            }
            content.addView(swipe, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = host.dp(8) })
        }
    }

    private fun periodCardRow(period: PeriodRecord, previous: PeriodRecord?, today: Long): View {
        val row = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(host.dp(15), host.dp(12), host.dp(15), host.dp(12))
            background = roundedDrawable(host, AppColors.cream, 18f, 0.8f, AppColors.line)
        }
        row.addView(iconSeal(host, "期", Color.rgb(168, 112, 118), Color.rgb(246, 231, 230), 32),
            LinearLayout.LayoutParams(host.dp(32), host.dp(32)))
        val labels = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(10), 0, host.dp(8), 0)
        }
        val range = if (period.endDay == null) {
            "${epochDate(period.startDay, "M月d日")} · 进行中"
        } else {
            "${epochDate(period.startDay, "M月d日")} – ${epochDate(period.endDay, "M月d日")}"
        }
        labels.addView(newText(host, range, 12.5f, AppColors.ink, AppFont.sansMedium))
        val meta = buildList {
            previous?.let { add("周期 ${period.startDay - it.startDay} 天") }
            val end = period.endDay
            if (end == null) {
                add("已持续 ${(today - period.startDay + 1).coerceAtLeast(1)} 天")
            } else {
                add("经期 ${end - period.startDay + 1} 天")
            }
        }.joinToString(" · ")
        labels.addView(newText(host, meta, 9.5f, AppColors.faint, AppFont.sans).apply {
            setPadding(0, host.dp(3), 0, 0)
        })
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (period.endDay == null) {
            row.addView(newText(host, "进行中", 9f, AppColors.cinnabar, AppFont.sansMedium).apply {
                setPadding(host.dp(7), host.dp(3), host.dp(7), host.dp(3))
                background = roundedDrawable(host, AppColors.cinnabarSoft, 12f)
            })
        }
        return row
    }

    private fun deletePeriod(record: PeriodRecord) {
        WorkbenchStore.update(host) { data ->
            data.copy(periods = data.periods.filterNot { it.id == record.id })
        }
        render(preserveScroll = true)
        host.showActionNotice(
            "已删除 ${epochDate(record.startDay, "M月d日")} 的周期记录",
            "撤销",
            5_200L,
        ) {
            WorkbenchStore.update(host) { data ->
                if (data.periods.any { it.id == record.id }) data
                else data.copy(periods = (data.periods + record).sortedByDescending { it.startDay })
            }
            periodMonth = YearMonth.from(LocalDate.ofEpochDay(record.startDay))
            render(preserveScroll = true)
            host.showNotice("已恢复这次记录")
        }
    }

    private fun periodSummary(snapshot: WorkbenchSnapshot): PeriodSummary {
        val sorted = snapshot.periods.sortedBy { it.startDay }
        val intervals = sorted.zipWithNext { a, b -> b.startDay - a.startDay }.filter { it in 15..60 }
        val lengths = sorted.mapNotNull { period -> period.endDay?.let { it - period.startDay + 1 } }
            .filter { it in 1..15 }
        val avgCycle = intervals.takeIf { it.isNotEmpty() }?.average()?.toInt()
        val avgPeriod = lengths.takeIf { it.isNotEmpty() }?.average()?.toInt()
        val minCycle = intervals.minOrNull()?.toInt()
        val maxCycle = intervals.maxOrNull()?.toInt()
        val regularity = when {
            intervals.size < 2 -> "记录中"
            maxCycle != null && minCycle != null && maxCycle - minCycle <= 3 -> "较规律"
            maxCycle != null && minCycle != null && maxCycle - minCycle <= 7 -> "有波动"
            else -> "波动较大"
        }
        return PeriodSummary(sorted, intervals, lengths, avgCycle, avgPeriod, minCycle, maxCycle, regularity)
    }

    private fun secondaryAction(
        host: MainActivity,
        label: String,
        enabled: Boolean = true,
        onClick: () -> Unit,
    ): TextView {
        return newText(host, label, 13f, if (enabled) AppColors.inkSoft else AppColors.faint, AppFont.sansMedium).apply {
            gravity = Gravity.CENTER
            isEnabled = enabled
            isClickable = enabled
            isFocusable = enabled
            alpha = if (enabled) 1f else 0.55f
            background = rippleOn(
                host,
                roundedDrawable(host, Color.TRANSPARENT, 16f, 1f, AppColors.line),
                0x1427231E,
            )
            setOnClickListener { if (enabled) onClick() }
        }
    }

    private fun pickPeriodStart() {
        val now = LocalDate.now()
        val picker = DatePickerDialog(
            host,
            R.style.Theme_NianRi_Picker,
            { _, year, month, day ->
                val start = LocalDate.of(year, month + 1, day).toEpochDay()
                if (start > now.toEpochDay()) {
                    host.showNotice("未来的日子还不能记录")
                } else {
                    WorkbenchStore.update(host) { data ->
                        val periods = data.periods.toMutableList()
                        val active = periods.firstOrNull { it.endDay == null && it.startDay <= start }
                        if (active != null) {
                            val idx = periods.indexOf(active)
                            periods[idx] = active.copy(endDay = maxOf(active.startDay, start - 1))
                        }
                        periods += PeriodRecord(
                            id = WorkbenchStore.newId(),
                            startDay = start,
                            endDay = null,
                        )
                        data.copy(periods = periods.sortedByDescending { it.startDay })
                    }
                    periodMonth = YearMonth.from(LocalDate.ofEpochDay(start))
                    host.showNotice("已经记下这次开始")
                    render(preserveScroll = false)
                }
            },
            now.year,
            now.monthValue - 1,
            now.dayOfMonth,
        )
        picker.datePicker.maxDate = System.currentTimeMillis()
        picker.show()
    }

    private fun endPeriod() {
        val snapshot = WorkbenchStore.get(host)
        val active = snapshot.periods.firstOrNull { it.endDay == null } ?: run {
            host.showNotice("还没有进行中的记录")
            return
        }
        val today = LocalDate.now().toEpochDay()
        WorkbenchStore.update(host) { data ->
            data.copy(periods = data.periods.map {
                if (it.id == active.id) it.copy(endDay = maxOf(today, it.startDay)) else it
            })
        }
        host.showNotice("这次记录结束了，辛苦了")
        render(preserveScroll = false)
    }

    private fun editCycleLength(current: Int) {
        showInputDialog(
            host,
            "周期长度",
            "一般可按 21–45 天记录。这个数字只用于预测，不用于避孕或诊断。",
            current.toString(),
            "28",
        ) { value ->
            val days = value.trim().toIntOrNull()
            if (days == null || days !in 21..45) {
                host.showNotice("请输入 21–45 之间的天数")
                return@showInputDialog false
            }
            WorkbenchStore.update(host) { data -> data.copy(cycleLength = days) }
            host.showNotice("周期长度已改为 $days 天")
            render(preserveScroll = false)
            true
        }
    }

    // ----------------------------------------------------------------- wishes

    private fun buildWishlist(snapshot: WorkbenchSnapshot) {
        val done = snapshot.wishes.count { it.done }
        val summary = contentCard(host)
        summary.addView(newText(host, "愿望清单", 10f, AppColors.muted, AppFont.sansMedium, 0.08f))
        summary.addView(newText(host, "${snapshot.wishes.size}", 31f, AppColors.ink, AppFont.serif).apply {
            setPadding(0, host.dp(4), 0, 0)
            includeFontPadding = false
        })
        summary.addView(newText(
            host,
            if (snapshot.wishes.isEmpty()) "把想做的事写下来，愿望就有了形状。"
            else "$done 个已经实现 · 还有 ${snapshot.wishes.size - done} 个在路上",
            10.5f,
            AppColors.muted,
            AppFont.sans,
        ).apply { setPadding(0, host.dp(5), 0, 0) })
        if (snapshot.wishes.isNotEmpty()) {
            val ratio = (done.toFloat() / snapshot.wishes.size).coerceIn(0f, 1f)
            val track = LinearLayout(host).apply {
                orientation = LinearLayout.HORIZONTAL
                background = roundedDrawable(host, AppColors.line, 2f)
            }
            if (ratio > 0f) {
                track.addView(View(host).apply {
                    background = roundedDrawable(host, AppColors.cinnabar, 2f)
                }, LinearLayout.LayoutParams(0, host.dp(4), ratio))
            }
            if (ratio < 1f) {
                track.addView(View(host), LinearLayout.LayoutParams(0, host.dp(4), 1f - ratio))
            }
            summary.addView(track, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                host.dp(4),
            ).apply { topMargin = host.dp(10) })
        }
        content.addView(summary, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))

        val form = contentCard(host)
        form.addView(newText(host, "写下一个愿望", 14f, AppColors.ink, AppFont.sansMedium))
        val input = textInput(host, "想去看的海、想见的人、想学会的事…")
        form.addView(input, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            host.dp(48),
        ).apply { topMargin = host.dp(10) })
        val add = primaryAction(host, "收进清单") {
            val title = input.text.toString().trim()
            if (title.isEmpty()) {
                host.showNotice("先写下一个小愿望")
                return@primaryAction
            }
            WorkbenchStore.update(host) { data ->
                data.copy(wishes = data.wishes + WishItem(
                    id = WorkbenchStore.newId(),
                    title = title,
                    done = false,
                    createdDay = LocalDate.now().toEpochDay(),
                ))
            }
            host.showNotice("愿望已经收好")
            render(preserveScroll = false)
        }
        form.addView(add, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            host.dp(44),
        ).apply { topMargin = host.dp(9) })
        content.addView(form, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(12) })

        if (snapshot.wishes.isEmpty()) {
            return
        }
        val pending = snapshot.wishes.filter { !it.done }.sortedByDescending { it.createdDay }
        val completed = snapshot.wishes.filter { it.done }.sortedByDescending { it.createdDay }

        fun addWishSection(title: String, items: List<WishItem>) {
            content.addView(sectionLabel(host, title), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = host.dp(24) })
            val list = contentCard(host, 0)
            items.forEachIndexed { index, wish ->
                if (index > 0) list.addView(cardDivider(host))
                list.addView(wishRow(wish), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ))
            }
            content.addView(list, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = host.dp(10) })
        }

        if (pending.isNotEmpty()) addWishSection("在路上的 · ${pending.size}", pending)
        if (completed.isNotEmpty()) addWishSection("已实现 · ${completed.size}", completed)
    }

    private fun wishRow(wish: WishItem): View {
        val row = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(host.dp(16), host.dp(12), host.dp(12), host.dp(12))
            isClickable = true
            isFocusable = true
            background = rippleOn(host, roundedDrawable(host, Color.TRANSPARENT, 18f), 0x1427231E)
            setOnClickListener {
                val nextDone = !wish.done
                WorkbenchStore.update(host) { data ->
                    data.copy(wishes = data.wishes.map {
                        if (it.id == wish.id) it.copy(done = nextDone) else it
                    })
                }
                host.showNotice(if (nextDone) "愿望实现啦" else "放回在路上的")
                render(preserveScroll = true)
            }
        }
        row.addView(newText(host, if (wish.done) "✓" else "", 12f, AppColors.cream, AppFont.sansMedium).apply {
            gravity = Gravity.CENTER
            background = if (wish.done) {
                roundedDrawable(host, AppColors.sage, 15f)
            } else {
                roundedDrawable(host, Color.TRANSPARENT, 15f, 1f, AppColors.line)
            }
        }, LinearLayout.LayoutParams(host.dp(30), host.dp(30)))
        val labels = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(10), 0, host.dp(8), 0)
        }
        labels.addView(newText(host, wish.title, 13f, if (wish.done) AppColors.muted else AppColors.ink, AppFont.sans).apply {
            paintFlags = if (wish.done) paintFlags or Paint.STRIKE_THRU_TEXT_FLAG else paintFlags
            maxLines = 2
        })
        labels.addView(newText(host, epochDate(wish.createdDay, "M月d日") + " 写下", 9f, AppColors.faint, AppFont.sans).apply {
            setPadding(0, host.dp(3), 0, 0)
        })
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(ImageView(host).apply {
            setImageResource(R.drawable.ic_edit)
            setColorFilter(AppColors.faint)
            contentDescription = "编辑这个愿望"
            setOnClickListener {
                showInputDialog(
                    host,
                    "编辑愿望",
                    "把愿望写得再具体一点，它会更有形状。",
                    wish.title,
                    "例如：去看一次海",
                ) { value ->
                    val title = value.trim()
                    if (title.isEmpty()) {
                        host.showNotice("先写下一个小愿望")
                        return@showInputDialog false
                    }
                    WorkbenchStore.update(host) { data ->
                        data.copy(wishes = data.wishes.map {
                            if (it.id == wish.id) it.copy(title = title) else it
                        })
                    }
                    host.showNotice("愿望改好了")
                    render(preserveScroll = true)
                    true
                }
            }
        }, LinearLayout.LayoutParams(host.dp(17), host.dp(17)).apply { marginEnd = host.dp(8) })
        row.addView(ImageView(host).apply {
            setImageResource(R.drawable.ic_trash)
            setColorFilter(AppColors.faint)
            contentDescription = "删除这个愿望"
            setOnClickListener {
                showConfirmDialog(
                    host,
                    "删除这个愿望？",
                    "「${wish.title}」会从本机删除。",
                    "删除",
                    "留着",
                ) {
                    WorkbenchStore.update(host) { data ->
                        data.copy(wishes = data.wishes.filterNot { it.id == wish.id })
                    }
                    render(preserveScroll = true)
                }
            }
        }, LinearLayout.LayoutParams(host.dp(17), host.dp(17)))
        return row
    }

    private fun emptyCard(text: String): View {
        return contentCard(host).apply {
            addView(newText(host, text, 11.5f, AppColors.muted, AppFont.sans).apply {
                setLineSpacing(host.dpf(3f), 1f)
            })
        }
    }
}
