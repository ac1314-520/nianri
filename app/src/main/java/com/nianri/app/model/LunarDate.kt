package com.nianri.app.model

import android.icu.util.ChineseCalendar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import android.icu.util.TimeZone

/**
 * 用 Android 自带的 ICU ChineseCalendar 做公历/农历互转。
 * 不引入第三方农历表，支持闰月，范围按 1900–2100 约束。
 */
data class LunarDateValue(
    val year: Int,
    val month: Int,
    val day: Int,
    val leap: Boolean = false,
) {
    val shortLabel: String
        get() = buildString {
            if (leap) append("闰")
            append(LunarDate.monthName(month))
            append(LunarDate.dayName(day))
        }

    val fullLabel: String
        get() = "${year}年$shortLabel"
}

object LunarDate {
    // ICU ChineseCalendar 的 EXTENDED_YEAR 从黄帝纪元起算，
    // 不是公历年份。2024 农历年对应 4661，而不是 2024。
    private const val ICU_YEAR_OFFSET = 2637
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val monthNames = arrayOf(
        "正月", "二月", "三月", "四月", "五月", "六月",
        "七月", "八月", "九月", "十月", "冬月", "腊月",
    )
    private val dayNames = arrayOf(
        "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
        "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
        "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十",
    )

    fun monthName(month: Int): String = monthNames.getOrElse(month - 1) { "${month}月" }
    fun dayName(day: Int): String = dayNames.getOrElse(day - 1) { "$day" }

    fun fromSolar(date: LocalDate): LunarDateValue? {
        if (date.year !in 1900..2100) return null
        return runCatching {
            val calendar = ChineseCalendar().apply {
                timeZone = TimeZone.getDefault()
                timeInMillis = date.atStartOfDay(zone).toInstant().toEpochMilli()
            }
            LunarDateValue(
                year = calendar.get(ChineseCalendar.EXTENDED_YEAR) - ICU_YEAR_OFFSET,
                month = calendar.get(ChineseCalendar.MONTH) + 1,
                day = calendar.get(ChineseCalendar.DAY_OF_MONTH),
                leap = calendar.get(ChineseCalendar.IS_LEAP_MONTH) == 1,
            )
        }.getOrNull()
    }

    fun toSolar(value: LunarDateValue): LocalDate? {
        if (value.year !in 1900..2100) return null
        if (value.month !in 1..12 || value.day !in 1..30) return null
        return runCatching {
            val calendar = ChineseCalendar().apply {
                timeZone = TimeZone.getDefault()
                clear()
                set(ChineseCalendar.EXTENDED_YEAR, value.year + ICU_YEAR_OFFSET)
                set(ChineseCalendar.MONTH, value.month - 1)
                set(ChineseCalendar.IS_LEAP_MONTH, if (value.leap) 1 else 0)
                set(ChineseCalendar.DAY_OF_MONTH, value.day)
            }
            val year = calendar.get(ChineseCalendar.EXTENDED_YEAR) - ICU_YEAR_OFFSET
            val month = calendar.get(ChineseCalendar.MONTH) + 1
            val day = calendar.get(ChineseCalendar.DAY_OF_MONTH)
            val leap = calendar.get(ChineseCalendar.IS_LEAP_MONTH) == 1
            if (year != value.year || month != value.month || day != value.day || leap != value.leap) {
                return@runCatching null
            }
            Instant.ofEpochMilli(calendar.timeInMillis).atZone(zone).toLocalDate()
        }.getOrNull()
    }

    fun daysInMonth(year: Int, month: Int, leap: Boolean): Int {
        var lastValid = 29
        for (day in 29..30) {
            if (toSolar(LunarDateValue(year, month, day, leap)) != null) {
                lastValid = day
            }
        }
        return lastValid
    }

    /** 返回这一年闰几月；没有闰月返回 null。 */
    fun leapMonthOf(year: Int): Int? {
        for (month in 1..12) {
            if (toSolar(LunarDateValue(year, month, 1, true)) != null) {
                return month
            }
        }
        return null
    }

    /** 找下一个有效的农历日期，闰月年份没有该闰月时自动跳到下一个有闰月的年份。 */
    fun nextOccurrence(value: LunarDateValue, from: LocalDate): LocalDate? {
        val startYear = fromSolar(from)?.year ?: from.year
        for (year in startYear..(startYear + 30).coerceAtMost(2100)) {
            val candidate = toSolar(value.copy(year = year)) ?: continue
            if (!candidate.isBefore(from)) return candidate
        }
        if (value.leap) {
            return nextOccurrence(value.copy(leap = false), from)
        }
        return null
    }
}
