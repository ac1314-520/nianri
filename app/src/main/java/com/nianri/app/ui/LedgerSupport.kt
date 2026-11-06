package com.nianri.app.ui

import android.graphics.Color
import com.nianri.app.data.LedgerEntry
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.roundToLong

data class LedgerCategory(
    val label: String,
    val color: Int,
) {
    val soft: Int
        get() = Color.argb(34, Color.red(color), Color.green(color), Color.blue(color))
}

data class LedgerDraft(
    val id: String? = null,
    val income: Boolean = false,
    val amountCents: Long = 0,
    val category: String = "餐饮",
    val epochDay: Long = LocalDate.now().toEpochDay(),
    val note: String = "",
)

object LedgerCategories {
    val expense = listOf(
        LedgerCategory("餐饮", Color.rgb(176, 82, 56)),
        LedgerCategory("交通", Color.rgb(91, 114, 133)),
        LedgerCategory("购物", Color.rgb(160, 112, 98)),
        LedgerCategory("日用", Color.rgb(138, 139, 92)),
        LedgerCategory("娱乐", Color.rgb(124, 110, 158)),
        LedgerCategory("医疗", Color.rgb(169, 111, 115)),
        LedgerCategory("学习", Color.rgb(95, 124, 106)),
        LedgerCategory("人情", Color.rgb(181, 138, 90)),
        LedgerCategory("住房", Color.rgb(94, 110, 126)),
        LedgerCategory("通讯", Color.rgb(111, 138, 138)),
        LedgerCategory("服饰", Color.rgb(154, 111, 134)),
        LedgerCategory("美容", Color.rgb(176, 122, 122)),
        LedgerCategory("宠物", Color.rgb(138, 123, 91)),
        LedgerCategory("旅行", Color.rgb(95, 127, 143)),
        LedgerCategory("数码", Color.rgb(110, 106, 134)),
        LedgerCategory("其他", Color.rgb(142, 135, 128)),
    )

    val income = listOf(
        LedgerCategory("工资", Color.rgb(111, 140, 112)),
        LedgerCategory("奖金", Color.rgb(181, 138, 90)),
        LedgerCategory("兼职", Color.rgb(91, 114, 133)),
        LedgerCategory("理财", Color.rgb(124, 110, 158)),
        LedgerCategory("红包", Color.rgb(176, 82, 56)),
        LedgerCategory("报销", Color.rgb(111, 138, 138)),
        LedgerCategory("礼金", Color.rgb(160, 112, 98)),
        LedgerCategory("退款", Color.rgb(138, 139, 92)),
        LedgerCategory("分红", Color.rgb(95, 124, 106)),
        LedgerCategory("其他", Color.rgb(142, 135, 128)),
    )

    fun categories(isIncome: Boolean): List<LedgerCategory> = if (isIncome) income else expense

    fun normalize(label: String, isIncome: Boolean): String {
        val legacy = when (label) {
            "生活" -> "日用"
            "工资", "奖金", "兼职", "理财", "红包", "报销", "礼金", "退款", "分红", "其他" -> label
            else -> label
        }
        return categories(isIncome).firstOrNull { it.label == legacy }?.label ?: "其他"
    }

    fun option(label: String, isIncome: Boolean): LedgerCategory {
        val normalized = normalize(label, isIncome)
        return categories(isIncome).firstOrNull { it.label == normalized }
            ?: categories(isIncome).last()
    }
}

fun List<LedgerEntry>.inMonth(month: YearMonth): List<LedgerEntry> =
    filter { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) == month }

fun List<LedgerEntry>.inYear(year: Int): List<LedgerEntry> =
    filter { LocalDate.ofEpochDay(it.epochDay).year == year }

fun List<LedgerEntry>.expenseTotal(): Long = filter { !it.income }.sumOf { it.amountCents }

fun List<LedgerEntry>.incomeTotal(): Long = filter { it.income }.sumOf { it.amountCents }

fun formatLedgerAmount(cents: Long): String =
    String.format(Locale.US, "%.2f", cents / 100.0)

fun centsFromInput(value: String): Long? {
    val number = value.trim().replace(',', '.').toDoubleOrNull() ?: return null
    if (number <= 0 || number > 10_000_000) return null
    return (number * 100).roundToLong()
}

fun yearMonthLabel(month: YearMonth): String = "${month.year} 年 ${month.monthValue} 月"
