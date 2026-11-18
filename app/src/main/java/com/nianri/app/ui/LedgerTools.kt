package com.nianri.app.ui

import android.content.Context
import com.nianri.app.data.LedgerEntry
import com.nianri.app.data.WorkbenchStore
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.nio.charset.Charset
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class LedgerTemplate(
    val id: String,
    val name: String,
    val income: Boolean,
    val amountCents: Long,
    val category: String,
    val note: String = "",
)

data class LedgerRule(
    val pattern: String,
    val category: String,
    val income: Boolean = false,
)

data class LedgerTools(
    val templates: List<LedgerTemplate>,
    val rules: List<LedgerRule>,
)

data class ParsedLedgerRow(
    val epochDay: Long,
    val income: Boolean,
    val amountCents: Long,
    val category: String,
    val note: String,
    val merchant: String,
    val source: String,
)

data class LedgerImportResult(
    val rows: List<ParsedLedgerRow>,
    val sourceLabel: String,
    val skipped: Int,
)

enum class LedgerImportKind(val label: String) {
    BILL("账单导入"),
    BANK("银行流水"),
}

/**
 * 模板、分类规则都只保存在本机 SharedPreferences。
 * 这一版没有云同步，也不读取通知或短信。
 */
object LedgerToolsStore {
    private const val PREFS = "nianri_ledger_tools"
    private const val KEY_TEMPLATES = "templates"
    private const val KEY_RULES = "rules"

    private val defaultTemplates = listOf(
        LedgerTemplate("tpl_breakfast", "早餐", false, 1500, "餐饮", "早餐"),
        LedgerTemplate("tpl_lunch", "午餐", false, 2500, "餐饮", "午餐"),
        LedgerTemplate("tpl_metro", "地铁", false, 400, "交通", "地铁"),
        LedgerTemplate("tpl_coffee", "咖啡", false, 2000, "餐饮", "咖啡"),
        LedgerTemplate("tpl_grocery", "买菜", false, 5000, "日用", "买菜"),
    )

    fun load(context: Context): LedgerTools {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val templates = runCatching {
            val root = JSONArray(prefs.getString(KEY_TEMPLATES, null) ?: "[]")
            buildList {
                for (i in 0 until root.length()) {
                    val o = root.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    val name = o.optString("name")
                    if (id.isBlank() || name.isBlank()) continue
                    add(
                        LedgerTemplate(
                            id = id,
                            name = name,
                            income = o.optBoolean("income"),
                            amountCents = o.optLong("amountCents").coerceAtLeast(1),
                            category = LedgerCategories.normalize(o.optString("category"), o.optBoolean("income")),
                            note = o.optString("note"),
                        ),
                    )
                }
            }
        }.getOrElse { emptyList() }.ifEmpty { defaultTemplates }

        val rules = runCatching {
            val root = JSONArray(prefs.getString(KEY_RULES, null) ?: "[]")
            buildList {
                for (i in 0 until root.length()) {
                    val o = root.optJSONObject(i) ?: continue
                    val pattern = o.optString("pattern").trim()
                    if (pattern.isBlank()) continue
                    val income = o.optBoolean("income")
                    add(
                        LedgerRule(
                            pattern = pattern,
                            category = LedgerCategories.normalize(o.optString("category"), income),
                            income = income,
                        ),
                    )
                }
            }
        }.getOrElse { emptyList() }
        return LedgerTools(templates, rules)
    }

    fun save(context: Context, tools: LedgerTools) {
        val templates = JSONArray().also { array ->
            tools.templates.forEach { template ->
                array.put(JSONObject().apply {
                    put("id", template.id)
                    put("name", template.name)
                    put("income", template.income)
                    put("amountCents", template.amountCents)
                    put("category", template.category)
                    put("note", template.note)
                })
            }
        }
        val rules = JSONArray().also { array ->
            tools.rules.forEach { rule ->
                array.put(JSONObject().apply {
                    put("pattern", rule.pattern)
                    put("category", rule.category)
                    put("income", rule.income)
                })
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_TEMPLATES, templates.toString())
            .putString(KEY_RULES, rules.toString())
            .apply()
    }

    fun saveTemplates(context: Context, templates: List<LedgerTemplate>) {
        save(context, load(context).copy(templates = templates))
    }

    fun saveRules(context: Context, rules: List<LedgerRule>) {
        save(context, load(context).copy(rules = rules))
    }

    fun fromTemplate(template: LedgerTemplate, epochDay: Long = LocalDate.now().toEpochDay()): LedgerEntry =
        LedgerEntry(
            id = WorkbenchStore.newId(),
            epochDay = epochDay,
            income = template.income,
            amountCents = template.amountCents,
            category = LedgerCategories.normalize(template.category, template.income),
            note = template.note,
            source = "快捷模板",
            merchant = template.name,
        )
}

object LedgerClassifier {
    private data class BuiltIn(val words: List<String>, val category: String)

    private val expenseRules = listOf(
        BuiltIn(listOf("美团", "饿了么", "肯德基", "麦当劳", "星巴克", "瑞幸", "蜜雪", "喜茶", "咖啡", "奶茶", "早餐", "午餐", "晚餐", "外卖", "餐厅", "饭店", "美食", "火锅", "烧烤"), "餐饮"),
        BuiltIn(listOf("滴滴", "高德打车", "地铁", "公交", "打车", "出租车", "加油", "停车", "铁路", "12306", "机票", "航空", "充电桩"), "交通"),
        BuiltIn(listOf("淘宝", "天猫", "京东", "拼多多", "唯品会", "抖音商城", "商城", "旗舰店", "网购"), "购物"),
        BuiltIn(listOf("超市", "便利店", "盒马", "山姆", "沃尔玛", "永辉", "菜市场", "买菜", "生鲜", "日用", "纸巾", "洗衣"), "日用"),
        BuiltIn(listOf("电影", "影院", "游戏", "KTV", "演出", "会员", "视频", "音乐", "网吧", "桌游"), "娱乐"),
        BuiltIn(listOf("医院", "药店", "药房", "诊所", "挂号", "体检", "医疗"), "医疗"),
        BuiltIn(listOf("书店", "图书", "课程", "培训", "学费", "考试", "文具"), "学习"),
        BuiltIn(listOf("房租", "物业", "水费", "电费", "燃气", "宽带", "住房"), "住房"),
        BuiltIn(listOf("话费", "流量", "中国移动", "中国联通", "中国电信", "通讯"), "通讯"),
        BuiltIn(listOf("还款", "还贷", "房贷", "车贷", "信用卡还款"), "还贷"),
        BuiltIn(listOf("衣服", "鞋", "裤", "外套", "优衣库", "服饰"), "服饰"),
        BuiltIn(listOf("理发", "美容", "美发", "化妆", "护肤"), "美容"),
        BuiltIn(listOf("宠物", "猫粮", "狗粮", "宠物医院"), "宠物"),
        BuiltIn(listOf("酒店", "旅行", "旅游", "景点", "门票", "携程", "飞猪"), "旅行"),
        BuiltIn(listOf("数码", "手机", "电脑", "耳机", "相机", "配件"), "数码"),
        BuiltIn(listOf("红包", "礼金", "份子", "送礼", "人情"), "人情"),
    )

    private val incomeRules = listOf(
        BuiltIn(listOf("工资", "薪资", "薪酬", "代发工资"), "工资"),
        BuiltIn(listOf("奖金", "绩效", "年终奖"), "奖金"),
        BuiltIn(listOf("兼职", "稿费", "劳务", "外快"), "兼职"),
        BuiltIn(listOf("理财", "利息", "基金", "收益"), "理财"),
        BuiltIn(listOf("红包", "礼金", "转账收入"), "红包"),
        BuiltIn(listOf("退款", "退货"), "退款"),
        BuiltIn(listOf("报销"), "报销"),
        BuiltIn(listOf("贷款", "借款", "放款"), "贷款"),
        BuiltIn(listOf("分红", "股息"), "分红"),
    )

    fun classify(text: String, income: Boolean, rules: List<LedgerRule>): String {
        val normalized = text.lowercase(Locale.ROOT)
        val user = rules.firstOrNull {
            it.income == income && normalized.contains(it.pattern.lowercase(Locale.ROOT))
        }
        if (user != null) return LedgerCategories.normalize(user.category, income)

        val builtIn = (if (income) incomeRules else expenseRules).firstOrNull { group ->
            group.words.any { normalized.contains(it.lowercase(Locale.ROOT)) }
        }
        return LedgerCategories.normalize(builtIn?.category ?: "其他", income)
    }
}

object LedgerImportParser {

    fun parse(
        context: Context,
        bytes: ByteArray,
        fileName: String,
        kind: LedgerImportKind,
    ): LedgerImportResult {
        val text = decode(bytes)
        val table = Csv.read(text)
        if (table.isEmpty()) error("文件是空的")
        val headerIndex = table.indexOfFirst { row ->
            val joined = row.joinToString(" ").lowercase(Locale.ROOT)
            (joined.contains("交易时间") || joined.contains("交易日期") || joined.contains("记账日期") ||
                joined.contains("日期")) &&
                (joined.contains("金额") || joined.contains("收/支") || joined.contains("收支") ||
                    joined.contains("借方") || joined.contains("贷方"))
        }
        if (headerIndex < 0) error("没有找到账单表头，请导出 CSV 格式")
        val headers = table[headerIndex].map { normalizeHeader(it) }
        val body = table.drop(headerIndex + 1)
        val source = detectSource(
            headers = headers,
            fileName = fileName,
            kind = kind,
            preamble = table.take(headerIndex).joinToString(" ") { it.joinToString(" ") },
        )
        val tools = LedgerToolsStore.load(context)
        val rows = mutableListOf<ParsedLedgerRow>()
        var skipped = 0

        body.forEach { raw ->
            if (raw.all { it.isBlank() }) return@forEach
            fun cell(vararg names: String): String {
                val index = findColumn(headers, names.toList())
                return if (index >= 0) raw.getOrElse(index) { "" }.trim() else ""
            }

            val dateText = cell("交易时间", "交易日期", "记账日期", "日期", "入账日期", "时间")
            val epochDay = parseDay(dateText)
            if (epochDay == null) {
                skipped++
                return@forEach
            }
            val direction = cell("收/支", "收支", "方向", "借贷标志", "借贷", "交易方向")
            val amountText = cell("金额", "金额(元)", "交易金额", "发生额", "人民币金额")
            val incomeAmountText = cell("收入金额", "收入", "贷方金额", "贷方发生额")
            val expenseAmountText = cell("支出金额", "支出", "借方金额", "借方发生额")
            val incomeAmount = money(incomeAmountText)
            val expenseAmount = money(expenseAmountText)
            val genericAmount = money(amountText)
            val income = when {
                direction.contains("不计") -> {
                    skipped++
                    return@forEach
                }
                direction.contains("收入") || direction == "收" || direction.contains("贷方") ||
                    direction == "贷" -> true
                direction.contains("支出") || direction == "支" || direction.contains("借方") ||
                    direction == "借" -> false
                incomeAmount != null && incomeAmount > 0 -> true
                expenseAmount != null && expenseAmount > 0 -> false
                genericAmount != null -> genericAmount >= 0
                else -> {
                    skipped++
                    return@forEach
                }
            }
            val cents = when {
                income && incomeAmount != null && incomeAmount > 0 -> incomeAmount
                !income && expenseAmount != null && expenseAmount > 0 -> expenseAmount
                genericAmount != null -> kotlin.math.abs(genericAmount)
                incomeAmount != null -> kotlin.math.abs(incomeAmount)
                expenseAmount != null -> kotlin.math.abs(expenseAmount)
                else -> {
                    skipped++
                    return@forEach
                }
            }
            if (cents <= 0) {
                skipped++
                return@forEach
            }
            val status = cell("交易状态", "当前状态", "状态")
            if (status.contains("关闭") || status.contains("失败") || status.contains("撤销")) {
                skipped++
                return@forEach
            }
            val merchant = cell("交易对方", "对方户名", "商户名称", "收款方", "对方", "商户")
            val description = cell("商品说明", "商品", "摘要", "用途", "备注", "交易说明")
            val note = listOf(merchant, description).filter { it.isNotBlank() }.distinct().joinToString(" · ")
            val category = LedgerClassifier.classify("$merchant $description", income, tools.rules)
            rows += ParsedLedgerRow(
                epochDay = epochDay,
                income = income,
                amountCents = cents,
                category = category,
                note = note,
                merchant = merchant.ifBlank { description.take(40) },
                source = source,
            )
        }
        if (rows.isEmpty()) error("没有解析到有效交易，可能不是支持的 CSV 账单")
        return LedgerImportResult(rows, source, skipped)
    }

    private fun detectSource(
        headers: List<String>,
        fileName: String,
        kind: LedgerImportKind,
        preamble: String,
    ): String {
        val joined = (headers + fileName + preamble).joinToString(" ").lowercase(Locale.ROOT)
        return when {
            joined.contains("支付宝") || joined.contains("alipay") -> "支付宝账单"
            joined.contains("微信") || joined.contains("wechat") -> "微信账单"
            kind == LedgerImportKind.BANK -> "银行流水"
            else -> "账单导入"
        }
    }

    private fun normalizeHeader(value: String): String =
        value.replace("\uFEFF", "").replace(" ", "").replace("\n", "").trim()

    private fun findColumn(headers: List<String>, names: List<String>): Int {
        val normalized = names.map { normalizeHeader(it) }
        return headers.indexOfFirst { header ->
            normalized.any { name -> header == name || header.contains(name) }
        }
    }

    private fun decode(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val utf8 = runCatching {
            Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        }.getOrNull()
        if (utf8 != null && !utf8.contains('\uFFFD')) return utf8.removePrefix("\uFEFF")
        return runCatching { String(bytes, Charset.forName("GBK")) }
            .getOrElse { String(bytes, Charsets.ISO_8859_1) }
            .removePrefix("\uFEFF")
    }

    private fun parseDay(value: String): Long? {
        val cleaned = value.trim().replace("年", "-").replace("月", "-").replace("日", "")
            .replace("/", "-").replace(".", "-")
        val dateTime = cleaned.substringBefore(" ")
        val formats = listOf(
            "yyyy-M-d", "yyyy-M-d H:mm:ss", "yyyy-M-d H:mm", "M-d-yyyy", "yyyyMMdd",
        )
        formats.forEach { pattern ->
            runCatching {
                if (pattern.contains("H")) {
                    LocalDateTime.parse(cleaned, DateTimeFormatter.ofPattern(pattern))
                } else {
                    LocalDate.parse(dateTime, DateTimeFormatter.ofPattern(pattern)).atStartOfDay()
                }
            }.getOrNull()?.let { return it.toLocalDate().toEpochDay() }
        }
        return null
    }

    private fun money(value: String): Long? {
        if (value.isBlank()) return null
        var text = value.trim().replace("￥", "").replace("¥", "").replace("元", "")
            .replace(",", "").replace(" ", "")
        if (text.startsWith("(") && text.endsWith(")")) text = "-${text.substring(1, text.length - 1)}"
        val number = text.toBigDecimalOrNull() ?: return null
        return number.multiply(BigDecimal(100)).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
    }
}

private object Csv {
    fun read(text: String): List<List<String>> {
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        val delimiter = if (firstLine.contains('\t') && !firstLine.contains(',')) '\t' else ','
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        quoted = false
                    }
                } else {
                    field.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> quoted = true
                    delimiter -> {
                        row += field.toString().trim()
                        field.clear()
                    }
                    '\n' -> {
                        row += field.toString().trim()
                        if (row.any { it.isNotBlank() }) rows += row.toList()
                        row.clear()
                        field.clear()
                    }
                    '\r' -> Unit
                    else -> field.append(ch)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += field.toString().trim()
            if (row.any { it.isNotBlank() }) rows += row.toList()
        }
        return rows
    }
}
