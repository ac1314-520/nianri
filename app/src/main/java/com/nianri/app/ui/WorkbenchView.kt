package com.nianri.app.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.nianri.app.MainActivity
import com.nianri.app.R
import com.nianri.app.data.CheckInCategory
import com.nianri.app.data.CheckInPlan
import com.nianri.app.data.WeatherManager
import com.nianri.app.data.WeatherNow
import com.nianri.app.data.WeatherStore
import com.nianri.app.data.WorkbenchSnapshot
import com.nianri.app.data.WorkbenchStore
import java.time.LocalDate
import java.util.Locale

class WorkbenchView(
    private val host: MainActivity,
) : FrameLayout(host) {

    private var snapshot: WorkbenchSnapshot = WorkbenchStore.get(host)
    private var scroll: ScrollView? = null
    private var weather: WeatherNow? = WeatherManager.cached(host)
    private var weatherLoading = false
    private var weatherError = false

    init {
        background = android.graphics.drawable.ColorDrawable(AppColors.paper)
        build()
        post { maybeRefreshWeather() }
    }

    fun refresh() {
        snapshot = WorkbenchStore.get(host)
        weather = WeatherManager.cached(host)
        rebuild()
        post { maybeRefreshWeather() }
    }

    private fun rebuild() {
        val y = scroll?.scrollY ?: 0
        removeAllViews()
        build()
        scroll?.post { scroll?.scrollTo(0, y) }
    }

    private fun build() {
        val main = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        addView(main, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        main.addView(featureTopBar(host, "工作台", { host.showHome() }))

        val scrollView = ScrollView(host).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
        }
        scroll = scrollView
        val column = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.contentMargin(), 0, host.contentMargin(), host.dp(72))
        }
        scrollView.addView(column, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        main.addView(scrollView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))

        column.addView(buildHero(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        column.addView(buildMoodCard(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(12) })

        column.addView(sectionLabel(host, "今天"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(24) })
        column.addView(buildCheckInCard(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(10) })
        column.addView(buildDiaryCard(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(10) })

        column.addView(sectionLabel(host, "记录"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(24) })
        column.addView(buildRecordsCard(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(10) })

        column.addView(newText(
            host,
            "所有记录只保存在这台手机。",
            9.5f,
            AppColors.faint,
            AppFont.sans,
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, host.dp(28), 0, 0)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
    }

    private fun buildHero(): View {
        val card = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(20), host.dp(18), host.dp(20), host.dp(16))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(AppColors.cream, AppColors.paperDeep),
            ).apply { cornerRadius = host.dpf(26f) }
            clipToOutline = true
        }
        val today = LocalDate.now()
        val dateRow = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        dateRow.addView(newText(
            host,
            "${today.monthValue}月${today.dayOfMonth}日 · ${weekdayCn(today)}",
            10.5f,
            AppColors.muted,
            AppFont.sans,
            0.04f,
        ), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        dateRow.addView(simpleChip(host, "今日", AppColors.cinnabarSoft, AppColors.cinnabar))
        card.addView(dateRow)

        card.addView(newText(
            host,
            DailyCopy.today(),
            23f,
            AppColors.ink,
            AppFont.serif,
            0.02f,
        ).apply {
            setPadding(0, host.dp(14), 0, host.dp(16))
            setLineSpacing(host.dpf(4f), 1f)
            maxLines = 2
        })

        val weatherRow = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, host.dp(11), 0, host.dp(2))
            isClickable = true
            isFocusable = true
            background = rippleOn(host, roundedDrawable(host, Color.TRANSPARENT, 14f), 0x1427231E)
            setOnClickListener {
                if (WeatherStore.hasCity(host)) refreshWeather() else host.showWeatherCityDialog()
            }
        }
        val now = weather
        val city = WeatherStore.city(host)
        weatherRow.addView(iconSeal(
            host,
            now?.glyph ?: "天",
            AppColors.cinnabarDeep,
            AppColors.cinnabarSoft,
            38,
        ).apply {
            contentDescription = now?.summary ?: "天气"
        })
        val labels = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(11), 0, host.dp(8), 0)
        }
        val primary: String
        val secondary: String
        val tip: String
        when {
            now != null -> {
                primary = "$city  ${now.temperature}°"
                secondary = "${now.summary} · ${now.low}°–${now.high}°"
                tip = if (weatherLoading) "正在更新天气…" else now.tip + " · MET Norway"
            }
            city.isNotBlank() -> {
                primary = if (weatherLoading) "正在获取天气…" else "天气暂不可用"
                secondary = city
                tip = if (weatherError) "点一下再试一次" else "稍等一下"
            }
            else -> {
                primary = "给今天加一点天气"
                secondary = "设置城市，看看窗外的温度"
                tip = ""
            }
        }
        labels.addView(newText(host, primary, 14f, AppColors.ink, AppFont.sansMedium))
        labels.addView(newText(host, secondary, 10f, AppColors.muted, AppFont.sans).apply {
            setPadding(0, host.dp(3), 0, 0)
        })
        if (tip.isNotBlank()) {
            labels.addView(newText(host, tip, 10f, AppColors.cinnabar, AppFont.sans).apply {
                setPadding(0, host.dp(3), 0, 0)
            })
        }
        weatherRow.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        weatherRow.addView(softAction(
            host,
            if (city.isBlank()) "设置" else "换城市",
        ) { host.showWeatherCityDialog() })
        card.addView(weatherRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        return card
    }

    private fun buildMoodCard(): View {
        val card = contentCard(host, 14)
        val today = LocalDate.now().toEpochDay()
        val selected = snapshot.moodOn(today)
        val head = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(newText(host, "今天心情", 12f, AppColors.inkSoft, AppFont.sansMedium, 0.05f),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(newText(host, WorkbenchMood.label(selected), 10f, AppColors.muted, AppFont.sans))
        card.addView(head)
        val row = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, host.dp(10), 0, 0)
        }
        WorkbenchMood.all.forEachIndexed { index, mood ->
            val active = selected == mood.key
            val chip = newText(
                host,
                mood.label,
                10.5f,
                if (active) mood.color else AppColors.muted,
                if (active) AppFont.sansMedium else AppFont.sans,
            ).apply {
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                background = rippleOn(
                    host,
                    roundedDrawable(
                        host,
                        if (active) Color.argb(38, Color.red(mood.color), Color.green(mood.color), Color.blue(mood.color))
                        else Color.TRANSPARENT,
                        15f,
                        if (active) 0f else 0.8f,
                        if (active) Color.TRANSPARENT else AppColors.line,
                    ),
                    0x1FB05238,
                )
                setOnClickListener {
                    WorkbenchStore.update(host) { data ->
                        val next = data.moods.toMutableMap()
                        if (active) next.remove(today) else next[today] = mood.key
                        data.copy(moods = next)
                    }
                    snapshot = WorkbenchStore.get(host)
                    rebuild()
                }
            }
            row.addView(chip, LinearLayout.LayoutParams(0, host.dp(34), 1f).apply {
                if (index < WorkbenchMood.all.lastIndex) marginEnd = host.dp(4)
            })
        }
        card.addView(row)
        return card
    }

    private fun buildCheckInCard(): View {
        val card = contentCard(host)
        val today = LocalDate.now().toEpochDay()
        val plans = snapshot.plans
        val checked = plans.count { today in it.checkedDays }
        val head = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(newText(host, "今日打卡", 14f, AppColors.ink, AppFont.sansMedium),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (plans.isNotEmpty()) {
            head.addView(newText(host, "$checked / ${plans.size}", 11f, AppColors.cinnabar, AppFont.sansMedium))
        }
        head.addView(softAction(host, "查看全部") { host.openRecords(RecordsTab.CHECKIN) })
        card.addView(head)

        if (plans.isEmpty()) {
            card.addView(newText(
                host,
                "给今天定一个小约定。喝水、早睡，或者给喜欢的人留一句话。",
                11.5f,
                AppColors.muted,
                AppFont.sans,
            ).apply {
                setPadding(0, host.dp(9), 0, host.dp(10))
                setLineSpacing(host.dpf(3f), 1f)
            })
            val add = primaryAction(host, "添加第一个打卡") { host.openRecords(RecordsTab.CHECKIN) }
            card.addView(add, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                host.dp(44),
            ))
            return card
        }

        val quick = plans.sortedWith(compareBy<CheckInPlan> { it.category.ordinal }.thenBy { it.createdDay }).take(3)
        quick.forEachIndexed { index, plan ->
            if (index > 0) {
                card.addView(cardDivider(host))
            }
            val row = LinearLayout(host).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                background = rippleOn(host, roundedDrawable(host, Color.TRANSPARENT, 12f), 0x1427231E)
                setOnClickListener {
                    WorkbenchStore.update(host) { data ->
                        data.copy(plans = data.plans.map { p ->
                            if (p.id != plan.id) p else p.copy(
                                checkedDays = if (today in p.checkedDays) p.checkedDays - today
                                else p.checkedDays + today,
                            )
                        })
                    }
                    snapshot = WorkbenchStore.get(host)
                    rebuild()
                }
            }
            val isChecked = today in plan.checkedDays
            row.addView(newText(
                host,
                if (isChecked) "✓" else "",
                12f,
                AppColors.cream,
                AppFont.sansMedium,
            ).apply {
                gravity = Gravity.CENTER
                background = if (isChecked) {
                    roundedDrawable(host, AppColors.cinnabar, 14f)
                } else {
                    roundedDrawable(host, Color.TRANSPARENT, 14f, 1f, AppColors.line)
                }
            }, LinearLayout.LayoutParams(host.dp(28), host.dp(28)))
            row.addView(newText(host, plan.title, 13f, AppColors.ink, AppFont.sans).apply {
                setPadding(host.dp(10), 0, host.dp(8), 0)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(simpleChip(
                host,
                plan.category.label,
                categorySoft(plan.category),
                categoryColor(plan.category),
            ), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
            card.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                host.dp(42),
            ))
        }
        if (plans.size > 3) {
            card.addView(softAction(host, "还有 ${plans.size - 3} 个 · 查看全部") {
                host.openRecords(RecordsTab.CHECKIN)
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
        return card
    }

    private fun buildDiaryCard(): View {
        val card = contentCard(host)
        card.isClickable = true
        card.isFocusable = true
        card.setOnClickListener { host.openRecords(RecordsTab.DIARY) }
        val today = LocalDate.now().toEpochDay()
        val entry = snapshot.diaryOn(today)
        val head = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(newText(host, "今天的一页", 14f, AppColors.ink, AppFont.sansMedium),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(softAction(host, if (entry == null) "写日记" else "继续写") {
            host.openRecords(RecordsTab.DIARY)
        })
        card.addView(head)
        if (entry == null) {
            card.addView(newText(
                host,
                "不必等到有大事发生。今天的一顿饭、一阵风，也值得写下来。",
                12f,
                AppColors.muted,
                AppFont.sans,
            ).apply {
                setPadding(0, host.dp(8), 0, 0)
                setLineSpacing(host.dpf(3f), 1f)
            })
        } else {
            card.addView(newText(
                host,
                entry.text.replace('\n', ' '),
                13f,
                AppColors.inkSoft,
                AppFont.serif,
            ).apply {
                setPadding(0, host.dp(9), 0, 0)
                maxLines = 3
                ellipsize = android.text.TextUtils.TruncateAt.END
                setLineSpacing(host.dpf(3f), 1f)
            })
            val mood = WorkbenchMood.option(entry.moodKey)?.label
            if (mood != null) {
                card.addView(simpleChip(host, mood, AppColors.creamDeep, AppColors.muted),
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = host.dp(9) })
            }
        }
        return card
    }

    private fun buildRecordsCard(): View {
        val card = contentCard(host, 0)
        val today = LocalDate.now().toEpochDay()
        val monthStart = LocalDate.now().withDayOfMonth(1).toEpochDay()
        val expense = snapshot.ledger
            .filter { !it.income && it.epochDay >= monthStart }
            .sumOf { it.amountCents }
        card.addView(recordRow(
            host,
            "¥",
            AppColors.gold,
            Color.rgb(247, 239, 222),
            "记账",
            "本月支出 ${formatMoney(expense)}",
            R.drawable.ic_chevron_right,
        ) { host.openRecords(RecordsTab.LEDGER) })
        card.addView(recordDivider(host))
        card.addView(recordRow(
            host,
            "期",
            Color.rgb(168, 112, 118),
            Color.rgb(246, 231, 230),
            "生理期",
            periodSummary(today),
            R.drawable.ic_chevron_right,
        ) { host.openRecords(RecordsTab.PERIOD) })
        card.addView(recordDivider(host))
        val done = snapshot.wishes.count { it.done }
        card.addView(recordRow(
            host,
            "愿",
            Color.rgb(126, 112, 154),
            Color.rgb(238, 232, 242),
            "愿望清单",
            if (snapshot.wishes.isEmpty()) "写下一个想实现的小愿望"
            else "${snapshot.wishes.size} 个愿望 · $done 个实现",
            R.drawable.ic_chevron_right,
        ) { host.openRecords(RecordsTab.WISH) })
        return card
    }

    private fun recordRow(
        host: MainActivity,
        glyph: String,
        glyphColor: Int,
        glyphBg: Int,
        title: String,
        subtitle: String,
        iconRes: Int,
        onClick: () -> Unit,
    ): View {
        val row = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(host.dp(16), host.dp(13), host.dp(16), host.dp(13))
            isClickable = true
            isFocusable = true
            background = rippleOn(host, roundedDrawable(host, Color.TRANSPARENT, 18f), 0x1427231E)
            setOnClickListener { onClick() }
        }
        row.addView(iconSeal(host, glyph, glyphColor, glyphBg, 36))
        val labels = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(11), 0, host.dp(8), 0)
        }
        labels.addView(newText(host, title, 13.5f, AppColors.ink, AppFont.sansMedium))
        labels.addView(newText(host, subtitle, 9.8f, AppColors.muted, AppFont.sans).apply {
            setPadding(0, host.dp(3), 0, 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(ImageView(host).apply {
            setImageResource(iconRes)
            setColorFilter(AppColors.faint)
        }, LinearLayout.LayoutParams(host.dp(16), host.dp(16)))
        return row
    }

    private fun recordDivider(host: MainActivity): View {
        return hairline(host, Color.argb(140, 228, 217, 202)).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                1,
            ).apply {
                leftMargin = host.dp(63)
                rightMargin = host.dp(16)
            }
        }
    }

    private fun periodSummary(today: Long): String {
        val period = snapshot.latestPeriod()
            ?: return "记录一次开始，慢慢了解自己的周期"
        if (period.endDay == null) {
            val day = (today - period.startDay + 1).coerceAtLeast(1)
            return "经期第 $day 天 · 记得照顾好自己"
        }
        val next = period.startDay + snapshot.cycleLength
        val days = next - today
        return when {
            days > 0 -> "预计 ${epochDate(next)} · 还有 $days 天"
            days == 0L -> "预计就是今天 · 留意身体"
            else -> "预计时间已到 · 可以记录新的开始"
        }
    }

    private fun maybeRefreshWeather() {
        if (!isAttachedToWindow) return
        if (!WeatherStore.hasCity(host)) return
        val current = weather
        if ((current == null || !WeatherManager.isFresh(current)) && !weatherLoading) {
            refreshWeather()
        }
    }

    private fun refreshWeather() {
        if (weatherLoading) return
        if (!WeatherStore.hasCity(host)) {
            host.showWeatherCityDialog()
            return
        }
        weatherLoading = true
        weatherError = false
        rebuild()
        WeatherManager.refresh(host) { result, message ->
            if (!isAttachedToWindow) return@refresh
            weatherLoading = false
            weatherError = result == null
            if (result != null) weather = result
            if (result == null && message != null && weather == null) {
                host.showNotice(message)
            }
            rebuild()
        }
    }

    private fun weekdayCn(day: LocalDate): String {
        return when (day.dayOfWeek.value) {
            1 -> "星期一"
            2 -> "星期二"
            3 -> "星期三"
            4 -> "星期四"
            5 -> "星期五"
            6 -> "星期六"
            else -> "星期日"
        }
    }
}
