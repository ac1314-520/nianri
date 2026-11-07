package com.nianri.app.model

import com.nianri.app.util.cnAnniversary
import com.nianri.app.util.daysBetween
import com.nianri.app.util.dotDate
import com.nianri.app.util.fullCnWithWeek
import com.nianri.app.util.monthDayCn
import com.nianri.app.util.nextAnnualDate
import com.nianri.app.util.safeDateForYear
import java.time.LocalDate

data class AnniversaryDisplay(
    val kicker: String,
    val big: String,
    val unit: String,
    val caption: String,
    val badge: String,
    val isToday: Boolean,
    val isPast: Boolean,
    val isCountdown: Boolean,
    val daysUntilNext: Long,
    val nextDate: LocalDate,
    val togetherDays: Long?,
    val years: Int?,
) {
    val countPrefix: String
        get() = when {
            isToday -> ""
            isPast -> "已经"
            isCountdown -> "还有"
            else -> "相伴第"
        }

    val countUnit: String get() = if (isToday) "" else "天"

    val countValue: String get() = big
}

fun Anniversary.display(today: LocalDate = LocalDate.now()): AnniversaryDisplay {
    if (lunar && lunarValue != null) {
        return lunarDisplay(today)
    }
    if (recurring) {
        if (date.isAfter(today)) {
            val days = daysBetween(today, date)
            return AnniversaryDisplay(
                kicker = "还有",
                big = days.toString(),
                unit = "天",
                caption = "纪念从 ${date.fullCnWithWeek()} 开始",
                badge = "倒数",
                isToday = false,
                isPast = false,
                isCountdown = true,
                daysUntilNext = days,
                nextDate = date,
                togetherDays = null,
                years = null,
            )
        }
        val together = daysBetween(date, today) + 1
        val next = nextAnnualDate(date, today)
        val daysUntil = daysBetween(today, next)
        val years = next.year - date.year
        val isToday = daysUntil == 0L
        val caption = if (isToday) {
            if (years > 0) "今天，是你们的${cnAnniversary(years)}。" else "今天，是这份纪念的第一天。"
        } else {
            "距离${cnAnniversary(maxOf(1, years))} · 还有 $daysUntil 天"
        }
        return AnniversaryDisplay(
            kicker = "相伴第",
            big = together.toString(),
            unit = "天",
            caption = caption,
            badge = if (isToday) "今天" else "每年",
            isToday = isToday,
            isPast = false,
            isCountdown = false,
            daysUntilNext = daysUntil,
            nextDate = next,
            togetherDays = together,
            years = years,
        )
    }

    val days = daysBetween(today, date)
    return when {
        days > 0 -> AnniversaryDisplay(
            kicker = "还有",
            big = days.toString(),
            unit = "天",
            caption = "期待 · ${date.fullCnWithWeek()}",
            badge = "倒数",
            isToday = false,
            isPast = false,
            isCountdown = true,
            daysUntilNext = days,
            nextDate = date,
            togetherDays = null,
            years = null,
        )
        days == 0L -> AnniversaryDisplay(
            kicker = "",
            big = "今天",
            unit = "",
            caption = "${date.fullCnWithWeek()}，愿这一天被好好收藏。",
            badge = "就是今天",
            isToday = true,
            isPast = false,
            isCountdown = false,
            daysUntilNext = 0,
            nextDate = date,
            togetherDays = null,
            years = null,
        )
        else -> {
            val elapsed = -days
            AnniversaryDisplay(
                kicker = "已经",
                big = elapsed.toString(),
                unit = "天",
                caption = "发生于 ${date.fullCnWithWeek()}",
                badge = "已走过",
                isToday = false,
                isPast = true,
                isCountdown = false,
                daysUntilNext = Long.MAX_VALUE,
                nextDate = date,
                togetherDays = null,
                years = null,
            )
        }
    }
}

private fun Anniversary.lunarDisplay(today: LocalDate): AnniversaryDisplay {
    val value = lunarValue ?: return display(today)
    val target = if (recurring) {
        LunarDate.nextOccurrence(value, today) ?: date
    } else {
        LunarDate.toSolar(value) ?: date
    }
    val days = daysBetween(today, target)
    val targetLunar = LunarDate.fromSolar(target)
    val years = (targetLunar?.year ?: value.year) - value.year
    val startFuture = date.isAfter(today)
    val isFuture = if (recurring) startFuture else target.isAfter(today)
    val isToday = target == today
    val isPast = !recurring && target.isBefore(today)
    val together = if (!date.isAfter(today)) daysBetween(date, today) + 1 else null
    val caption = when {
        isToday && recurring -> "今天，是你们的${cnAnniversary(years)}。"
        isToday -> "农历 ${value.shortLabel}，愿这一天被好好收藏。"
        recurring -> "距离${cnAnniversary(maxOf(1, years))} · 还有 $days 天"
        isPast -> "发生于 农历 ${value.shortLabel}"
        else -> "期待 · 农历 ${value.shortLabel}"
    }
    return AnniversaryDisplay(
        kicker = when {
            isToday -> ""
            isPast -> "已经"
            isFuture -> "还有"
            else -> "相伴第"
        },
        big = when {
            isToday -> "今天"
            recurring && !isFuture -> (together ?: 1L).toString()
            isPast -> (-days).toString()
            else -> days.toString()
        },
        unit = if (isToday) "" else "天",
        caption = caption,
        badge = when {
            isToday -> "今天"
            recurring -> "农历"
            isPast -> "已走过"
            else -> "倒数"
        },
        isToday = isToday,
        isPast = isPast,
        isCountdown = isFuture && !isPast,
        daysUntilNext = if (isPast) Long.MAX_VALUE else days,
        nextDate = target,
        togetherDays = together,
        years = years.takeIf { recurring },
    )
}

/** 首页顺序：今天、即将到来、已经过去的单次倒数日。 */
fun List<Anniversary>.sortedForHome(today: LocalDate = LocalDate.now()): List<Anniversary> {
    val manual = filter { it.manualOrder != null }
        .sortedBy { it.manualOrder ?: Int.MAX_VALUE }
    val automatic = filter { it.manualOrder == null }
        .sortedWith(
            compareByDescending<Anniversary> { it.display(today).isToday }
                .thenBy { it.display(today).isPast }
                .thenBy { it.display(today).daysUntilNext }
                .thenByDescending { it.createdAt },
        )
    return manual + automatic
}

fun List<Anniversary>.heroForHome(today: LocalDate = LocalDate.now()): Anniversary? =
    sortedForHome(today).firstOrNull()

fun Anniversary.dateLine(): String {
    val lunarDate = lunarValue
    if (lunar && lunarDate != null) {
        return if (recurring) {
            "每年农历 ${lunarDate.shortLabel}"
        } else {
            "农历 ${lunarDate.shortLabel} · 倒数日"
        }
    }
    return if (recurring) {
        "每年 ${date.monthDayCn()}"
    } else {
        "${date.dotDate()} · 倒数日"
    }
}

data class Milestone(
    val years: Int,
    val date: LocalDate,
    val daysAway: Long,
)

fun Anniversary.upcomingMilestones(today: LocalDate = LocalDate.now(), limit: Int = 3): List<Milestone> {
    if (!recurring || date.isAfter(today)) return emptyList()
    val lunarDate = lunarValue
    if (lunar && lunarDate != null) {
        val milestones = mutableListOf<Milestone>()
        val startLunarYear = LunarDate.fromSolar(today)?.year ?: lunarDate.year
        for (year in startLunarYear..(startLunarYear + 30).coerceAtMost(2100)) {
            if (milestones.size >= limit) break
            val candidate = LunarDate.toSolar(lunarDate.copy(year = year)) ?: continue
            if (candidate.isBefore(today)) continue
            val years = year - lunarDate.year
            if (years > 0) {
                milestones.add(Milestone(years, candidate, daysBetween(today, candidate)))
            }
        }
        return milestones
    }
    val milestones = mutableListOf<Milestone>()
    var cursor = nextAnnualDate(date, today)
    if (cursor == today) {
        val years = cursor.year - date.year
        if (years > 0) milestones.add(Milestone(years, cursor, 0))
        cursor = date.safeDateForYear(today.year + 1)
    }
    while (milestones.size < limit) {
        val years = cursor.year - date.year
        if (years <= 0) break
        milestones.add(Milestone(years, cursor, daysBetween(today, cursor)))
        cursor = date.safeDateForYear(cursor.year + 1)
    }
    return milestones
}

object DailyNotes {
    private val lines = listOf(
        "把重要的日子，安放在心里柔软的地方。",
        "时间会走远，被记住的日子不会。",
        "日子是细碎的，爱让它们有了名字。",
        "把普通的一天，过成值得回望的一天。",
        "慢慢来，把每一次相聚都过得郑重。",
        "重要的不是哪一天，而是和谁一起走过。",
        "愿你记得来时的路，也珍惜眼前的人。",
        "所有的久处不厌，都是因为用心。",
        "把喜欢藏进岁月，把陪伴过成日常。",
        "今天很好，明天也是。",
        "愿每一年，都有值得纪念的相逢。",
        "生活缓缓，心意长长。",
    )

    fun forDay(today: LocalDate): String {
        val index = Math.floorMod(today.year * 367 + today.dayOfYear, lines.size)
        return lines[index]
    }
}
