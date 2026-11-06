package com.nianri.app.util

import java.time.LocalDate
import java.time.Month
import java.time.Year
import java.time.temporal.ChronoUnit

val CN_WEEKDAYS = arrayOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")

fun LocalDate.weekdayCn(): String = CN_WEEKDAYS[dayOfWeek.value - 1]

fun LocalDate.fullCn(): String = "${year}年${monthValue}月${dayOfMonth}日"

fun LocalDate.fullCnWithWeek(): String = "${fullCn()} · ${weekdayCn()}"

fun LocalDate.dotDate(): String = "%04d.%02d.%02d".format(year, monthValue, dayOfMonth)

fun LocalDate.monthDayCn(): String = "${monthValue}月${dayOfMonth}日"

fun LocalDate.shortYearDate(): String = "${year}.${"%02d".format(monthValue)}.${"%02d".format(dayOfMonth)}"

fun LocalDate.safeDateForYear(targetYear: Int): LocalDate {
    val month = Month.of(monthValue)
    val day = minOf(dayOfMonth, month.length(Year.isLeap(targetYear.toLong())))
    return LocalDate.of(targetYear, month, day)
}

/** 下一次每年纪念日；如果今天就是，则返回今天。 */
fun nextAnnualDate(start: LocalDate, today: LocalDate): LocalDate {
    var candidate = start.safeDateForYear(today.year)
    if (candidate.isBefore(today)) {
        candidate = start.safeDateForYear(today.year + 1)
    }
    return candidate
}

fun nextAnnualDateAfter(start: LocalDate, date: LocalDate): LocalDate {
    return start.safeDateForYear(date.year + 1)
}

fun daysBetween(start: LocalDate, end: LocalDate): Long =
    ChronoUnit.DAYS.between(start, end)

fun cnNumber(value: Int): String {
    if (value < 0) return value.toString()
    if (value < 10) return arrayOf("零", "一", "二", "三", "四", "五", "六", "七", "八", "九")[value]
    if (value == 10) return "十"
    if (value < 20) return "十" + cnNumber(value - 10)
    if (value < 100) {
        val tens = value / 10
        val ones = value % 10
        return cnNumber(tens) + "十" + if (ones == 0) "" else cnNumber(ones)
    }
    return value.toString()
}

fun cnAnniversary(years: Int): String {
    return when {
        years <= 0 -> "纪念日"
        years == 2 -> "两周年"
        else -> cnNumber(years) + "周年"
    }
}

fun LocalDate.humanTargetDate(): String {
    val today = LocalDate.now()
    return if (year == today.year) monthDayCn() else "${year}年${monthDayCn()}"
}

fun parseReminderTime(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
