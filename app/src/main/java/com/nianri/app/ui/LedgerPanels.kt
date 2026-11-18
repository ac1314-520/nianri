package com.nianri.app.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.nianri.app.MainActivity
import com.nianri.app.data.LedgerEntry
import com.nianri.app.data.WorkbenchSnapshot
import com.nianri.app.data.WorkbenchStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun buildLedgerToolsCard(
    host: MainActivity,
    snapshot: WorkbenchSnapshot,
    onChanged: () -> Unit,
): View {
    val tools = LedgerToolsStore.load(host)
    val card = contentCard(host)
    val titleRow = LinearLayout(host).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    titleRow.addView(newText(host, "快捷记账", 17f, AppColors.ink, AppFont.serif), LinearLayout.LayoutParams(
        0,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        1f,
    ))
    titleRow.addView(newText(host, "点一下，直接记今天", 9f, AppColors.muted, AppFont.sans))
    card.addView(titleRow)

    val scroller = HorizontalScrollView(host).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
    }
    val chips = LinearLayout(host).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, host.dp(10), 0, 0)
    }
    tools.templates.forEach { template ->
        val chip = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = roundedDrawable(host, AppColors.paperDeep, 16f, 0.8f, AppColors.line)
            setPadding(host.dp(12), host.dp(8), host.dp(12), host.dp(8))
            setOnClickListener { addTemplateEntry(host, template, onChanged) }
            setOnLongClickListener {
                val remaining = LedgerToolsStore.load(host).templates.filterNot { it.id == template.id }
                if (remaining.isEmpty()) {
                    host.showNotice("至少保留一个快捷模板")
                } else {
                    LedgerToolsStore.saveTemplates(host, remaining)
                    onChanged()
                    host.showNotice("已移除快捷模板")
                }
                true
            }
        }
        chip.addView(newText(host, template.name, 11.5f, AppColors.ink, AppFont.sansMedium))
        val amount = newText(
            host,
            (if (template.income) "+" else "") + formatLedgerAmount(template.amountCents),
            9.5f,
            if (template.income) AppColors.sage else AppColors.cinnabar,
            AppFont.sans,
        )
        amount.setPadding(0, host.dp(3), 0, 0)
        chip.addView(amount)
        chips.addView(chip, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { marginEnd = host.dp(7) })
    }
    val addChip = LinearLayout(host).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        background = roundedDrawable(host, AppColors.cream, 16f, 0.8f, AppColors.line)
        setPadding(host.dp(12), host.dp(8), host.dp(12), host.dp(8))
        setOnClickListener { showAddTemplateDialog(host, onChanged) }
    }
    addChip.addView(newText(host, "＋ 新模板", 10.5f, AppColors.cinnabar, AppFont.sansMedium))
    addChip.addView(newText(host, "自定义", 9f, AppColors.faint, AppFont.sans).apply {
        setPadding(0, host.dp(3), 0, 0)
    })
    chips.addView(addChip)
    scroller.addView(chips, ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ))
    card.addView(scroller)

    val actionRow = LinearLayout(host).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, host.dp(10), 0, 0)
    }
    actionRow.addView(softAction(host, "账单导入") {
        importLedgerFile(host, LedgerImportKind.BILL, onChanged)
    }, LinearLayout.LayoutParams(0, host.dp(34), 1f).apply { marginEnd = host.dp(4) })
    actionRow.addView(softAction(host, "银行流水") {
        importLedgerFile(host, LedgerImportKind.BANK, onChanged)
    }, LinearLayout.LayoutParams(0, host.dp(34), 1f).apply {
        marginStart = host.dp(4)
        marginEnd = host.dp(4)
    })
    actionRow.addView(softAction(host, "分类规则") {
        showLedgerRulesDialog(host, onChanged)
    }, LinearLayout.LayoutParams(0, host.dp(34), 1f).apply { marginStart = host.dp(4) })
    card.addView(actionRow)

    val pending = snapshot.ledger.filter { it.pending }
    if (pending.isNotEmpty()) {
        val pendingRow = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            background = rippleOn(
                host,
                roundedDrawable(host, AppColors.cinnabarSoft, 15f, 0.8f, 0x33CC785C),
                0x1FB05238,
            )
            setPadding(host.dp(12), host.dp(9), host.dp(12), host.dp(9))
            setOnClickListener { showPendingLedgerDialog(host, snapshot, onChanged) }
        }
        val labels = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        labels.addView(newText(host, "待确认 ${pending.size} 笔", 12f, AppColors.cinnabarDeep, AppFont.sansMedium))
        labels.addView(newText(
            host,
            "${formatLedgerAmount(pending.sumOf { it.amountCents })} 元 · 点开确认或忽略",
            9.5f,
            AppColors.muted,
            AppFont.sans,
        ).apply { setPadding(0, host.dp(2), 0, 0) })
        pendingRow.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        pendingRow.addView(newText(host, "去确认", 11f, AppColors.cinnabarDeep, AppFont.sansMedium))
        card.addView(pendingRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(10) })
    }

    card.addView(newText(
        host,
        "长按快捷模板可移除；支持支付宝/微信 CSV 与银行 CSV/TSV，Excel 请先另存为 CSV。导入只生成待确认记录，不会直接改统计。",
        9f,
        AppColors.faint,
        AppFont.sans,
    ).apply { setPadding(0, host.dp(10), 0, 0) })
    return card
}

private fun showAddTemplateDialog(host: MainActivity, onChanged: () -> Unit) {
    showInputDialog(host, "新模板名称", "例如：早餐、地铁、咖啡", "", "模板名称") { name ->
        if (name.isBlank()) {
            host.showNotice("名称不能为空")
            true
        } else {
            showInputDialog(
                host,
                "模板金额",
                "输入每次大概花多少，之后点一下就按这个金额记一笔",
                "",
                "例如：15",
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            ) { amountText ->
                val cents = centsFromInput(amountText)
                if (cents == null) {
                    host.showNotice("请填 0 到 1000 万之间的金额")
                    true
                } else {
                    showInputDialog(
                        host,
                        "模板分类",
                        "餐饮 / 交通 / 购物 / 日用 / 娱乐 / 医疗 / 学习 / 住房 / 通讯 / 服饰 / 美容 / 宠物 / 旅行 / 数码 / 其他",
                        "餐饮",
                        "例如：餐饮",
                    ) { category ->
                        val normalized = LedgerCategories.normalize(category, false)
                        if (normalized == "其他" && category.trim() != "其他") {
                            host.showNotice("分类名不在列表里")
                            true
                        } else {
                            val tools = LedgerToolsStore.load(host)
                            val template = LedgerTemplate(
                                id = WorkbenchStore.newId(),
                                name = name.trim(),
                                income = false,
                                amountCents = cents,
                                category = normalized,
                                note = name.trim(),
                            )
                            LedgerToolsStore.saveTemplates(host, tools.templates + template)
                            onChanged()
                            host.showNotice("模板已添加")
                            true
                        }
                    }
                    true
                }
            }
            true
        }
    }
}

private fun addTemplateEntry(host: MainActivity, template: LedgerTemplate, onChanged: () -> Unit) {
    val entry = LedgerToolsStore.fromTemplate(template)
    WorkbenchStore.update(host) { data ->
        data.copy(ledger = (data.ledger + entry).sortedByDescending { it.epochDay })
    }
    host.showNotice("已记下")
    onChanged()
}

private fun importLedgerFile(host: MainActivity, kind: LedgerImportKind, onChanged: () -> Unit) {
    host.launchLedgerImport(kind) { uri ->
        if (uri == null) return@launchLedgerImport
        host.showNotice("正在读取文件…")
        Thread {
            try {
                val resolver = host.contentResolver
                val name = runCatching {
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                }.getOrNull().orEmpty().ifBlank { "账单.csv" }
                val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("无法读取文件")
                if (bytes.size > 12 * 1024 * 1024) error("文件超过 12MB")
                val result = LedgerImportParser.parse(host, bytes, name, kind)
                val existing = WorkbenchStore.get(host).ledger
                val keys = existing.map { ledgerKey(it) }.toMutableSet()
                val fresh = result.rows.filter { row ->
                    keys.add("${row.epochDay}|${row.income}|${row.amountCents}|${row.merchant}|${row.note}")
                }.take(3000)
                val entries = fresh.map { row ->
                    LedgerEntry(
                        id = WorkbenchStore.newId(),
                        epochDay = row.epochDay,
                        income = row.income,
                        amountCents = row.amountCents,
                        category = row.category,
                        note = row.note,
                        pending = true,
                        source = row.source,
                        merchant = row.merchant,
                    )
                }
                WorkbenchStore.update(host) { data ->
                    data.copy(ledger = (data.ledger + entries).sortedByDescending { it.epochDay })
                }
                host.runOnUiThread {
                    val skipped = result.rows.size - fresh.size
                    host.showNotice("导入 ${entries.size} 笔，请逐笔确认")
                    if (skipped > 0) host.showNotice("已跳过 $skipped 笔重复记录")
                    onChanged()
                    showPendingLedgerDialog(host, WorkbenchStore.get(host), onChanged)
                }
            } catch (e: Exception) {
                host.runOnUiThread { host.showNotice("导入失败：${e.message ?: "文件格式不支持"}") }
            }
        }.start()
    }
}

private fun ledgerKey(entry: LedgerEntry): String =
    "${entry.epochDay}|${entry.income}|${entry.amountCents}|${entry.merchant}|${entry.note}"

private fun showPendingCategoryPicker(
    host: MainActivity,
    entry: LedgerEntry,
    pendingDialog: Dialog,
    onChanged: () -> Unit,
) {
    val picker = baseDialog(host, 370)
    val card = dialogCard(host)
    card.addView(dialogTitle(host, "这笔归到哪一类"))
    card.addView(dialogMessage(host, "改完会记住商户，下次导入直接归类。"), LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = host.dp(6) })
    val scroll = ScrollView(host)
    val grid = LinearLayout(host).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, host.dp(10), 0, 0)
    }
    val categories = LedgerCategories.categories(entry.income)
    categories.chunked(4).forEach { rowItems ->
        val row = LinearLayout(host).apply { orientation = LinearLayout.HORIZONTAL }
        rowItems.forEach { category ->
            val selected = category.label == entry.category
            val chip = newText(
                host,
                category.label,
                11f,
                if (selected) AppColors.cinnabarDeep else AppColors.inkSoft,
                if (selected) AppFont.sansMedium else AppFont.sans,
            ).apply {
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                background = roundedDrawable(
                    host,
                    if (selected) AppColors.cinnabarSoft else AppColors.paperDeep,
                    14f,
                    0.8f,
                    if (selected) 0x55CC785C else AppColors.line,
                )
                setOnClickListener {
                    WorkbenchStore.update(host) { data ->
                        data.copy(ledger = data.ledger.map {
                            if (it.id == entry.id) it.copy(category = category.label) else it
                        })
                    }
                    val pattern = entry.merchant.ifBlank { entry.note.take(24) }.trim()
                    if (pattern.isNotBlank()) {
                        val tools = LedgerToolsStore.load(host)
                        if (tools.rules.none {
                                it.pattern.equals(pattern, ignoreCase = true) && it.income == entry.income
                            }) {
                            LedgerToolsStore.saveRules(
                                host,
                                tools.rules + LedgerRule(pattern, category.label, entry.income),
                            )
                        }
                    }
                    pendingDialog.dismiss()
                    picker.dismiss()
                    onChanged()
                    host.showNotice("已改成 ${category.label}，下次会自动分类")
                }
            }
            row.addView(chip, LinearLayout.LayoutParams(0, host.dp(38), 1f).apply {
                if (rowItems.last() != category) marginEnd = host.dp(5)
            })
        }
        repeat(4 - rowItems.size) {
            row.addView(View(host), LinearLayout.LayoutParams(0, 1, 1f))
        }
        grid.addView(row, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(6) })
    }
    scroll.addView(grid, ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ))
    card.addView(scroll, LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        host.dp(330),
    ))
    picker.setContentView(card)
    picker.show()
}

fun showPendingLedgerDialog(host: MainActivity, snapshot: WorkbenchSnapshot, onChanged: () -> Unit) {
    val pending = snapshot.ledger.filter { it.pending }.sortedByDescending { it.epochDay }
    if (pending.isEmpty()) {
        showInfoDialog(host, "待确认", "现在没有待确认的账单。")
        return
    }
    val dialog = baseDialog(host, 390)
    val card = dialogCard(host)
    card.addView(dialogTitle(host, "待确认 ${pending.size} 笔"))
    card.addView(dialogMessage(host, "确认后才进入统计；忽略会从待确认里删除。"), LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = host.dp(6) })

    val scroll = ScrollView(host).apply { overScrollMode = View.OVER_SCROLL_NEVER }
    val list = LinearLayout(host).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, host.dp(9), 0, 0)
    }
    pending.forEach { entry ->
        val row = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(host, AppColors.paperDeep, 16f, 0.8f, AppColors.line)
            setPadding(host.dp(11), host.dp(9), host.dp(9), host.dp(8))
        }
        val head = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val labels = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            setOnClickListener {
                showPendingCategoryPicker(host, entry, dialog, onChanged)
            }
        }
        labels.addView(newText(
            host,
            entry.merchant.ifBlank { entry.note.ifBlank { "未命名交易" } },
            12.5f,
            AppColors.ink,
            AppFont.sansMedium,
        ).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        val day = LocalDate.ofEpochDay(entry.epochDay).format(DateTimeFormatter.ofPattern("M月d日"))
        labels.addView(newText(
            host,
            "$day · ${entry.category} · ${entry.source.ifBlank { "导入" }}",
            9f,
            AppColors.muted,
            AppFont.sans,
        ).apply { setPadding(0, host.dp(2), 0, 0) })
        head.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(newText(
            host,
            (if (entry.income) "+" else "-") + formatLedgerAmount(entry.amountCents),
            13f,
            if (entry.income) AppColors.sage else AppColors.cinnabar,
            AppFont.sansMedium,
        ))
        row.addView(head)
        val actions = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, host.dp(6), 0, 0)
        }
        actions.addView(softAction(host, "忽略") {
            WorkbenchStore.update(host) { data ->
                data.copy(ledger = data.ledger.filterNot { it.id == entry.id })
            }
            dialog.dismiss()
            onChanged()
        }, LinearLayout.LayoutParams(0, host.dp(30), 1f).apply { marginEnd = host.dp(6) })
        actions.addView(softAction(host, "确认") {
            WorkbenchStore.update(host) { data ->
                data.copy(ledger = data.ledger.map {
                    if (it.id == entry.id) it.copy(pending = false) else it
                })
            }
            dialog.dismiss()
            onChanged()
            host.showNotice("已确认，计入统计")
        }, LinearLayout.LayoutParams(0, host.dp(30), 1f))
        row.addView(actions)
        list.addView(row, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(7) })
    }
    scroll.addView(list, ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ))
    card.addView(scroll, LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        host.dp(minOf(390, 120 + pending.size * 92)),
    ))
    val confirmAll = dialogButton(host, "全部确认", true)
    confirmAll.setOnClickListener {
        WorkbenchStore.update(host) { data ->
            data.copy(ledger = data.ledger.map { if (it.pending) it.copy(pending = false) else it })
        }
        dialog.dismiss()
        onChanged()
        host.showNotice("${pending.size} 笔已全部确认")
    }
    val close = dialogButton(host, "关闭", false)
    close.setOnClickListener { dialog.dismiss() }
    card.addView(buttonRow(host, close, confirmAll))
    dialog.setContentView(card)
    dialog.show()
}

private fun showLedgerRulesDialog(host: MainActivity, onChanged: () -> Unit) {
    val tools = LedgerToolsStore.load(host)
    val dialog = baseDialog(host, 390)
    val card = dialogCard(host)
    card.addView(dialogTitle(host, "分类规则"))
    card.addView(dialogMessage(
        host,
        "导入时先匹配自定义关键词，再匹配内置词库。例如：星巴克 → 餐饮。",
    ), LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = host.dp(6) })
    val list = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
    list.addView(newText(host, "自定义规则 ${tools.rules.size} 条", 10f, AppColors.muted, AppFont.sans).apply {
        setPadding(0, host.dp(10), 0, host.dp(4))
    })
    if (tools.rules.isEmpty()) {
        list.addView(newText(host, "还没有自定义规则。", 11f, AppColors.faint, AppFont.sans))
    }
    tools.rules.forEach { rule ->
        val row = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(host.dp(10), host.dp(8), host.dp(8), host.dp(8))
            background = roundedDrawable(host, AppColors.paperDeep, 14f, 0.8f, AppColors.line)
        }
        row.addView(newText(host, "${rule.pattern}  →  ${rule.category}", 11.5f, AppColors.inkSoft, AppFont.sans),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(newText(host, "删除", 10.5f, AppColors.cinnabar, AppFont.sans).apply {
            isClickable = true
            setPadding(host.dp(8), host.dp(4), host.dp(4), host.dp(4))
            setOnClickListener {
                LedgerToolsStore.saveRules(host, tools.rules.filterNot { it == rule })
                dialog.dismiss()
                onChanged()
                showLedgerRulesDialog(host, onChanged)
            }
        })
        list.addView(row, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(6) })
    }
    val rulesScroll = ScrollView(host).apply {
        overScrollMode = View.OVER_SCROLL_NEVER
    }
    rulesScroll.addView(list, ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ))
    card.addView(rulesScroll, LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        host.dp(300),
    ).apply { topMargin = host.dp(8) })
    val add = dialogButton(host, "新增规则", true)
    add.setOnClickListener {
        dialog.dismiss()
        showInputDialog(
            host,
            "规则关键词",
            "备注或商户名包含这个词时生效，例如：星巴克",
            "",
            "例如：星巴克",
        ) { pattern ->
            if (pattern.isBlank()) {
                host.showNotice("关键词不能为空")
                true
            } else {
                showInputDialog(
                    host,
                    "归到哪个分类",
                    "餐饮 / 交通 / 购物 / 日用 / 娱乐 / 医疗 / 学习 / 住房 / 通讯 / 服饰 / 美容 / 宠物 / 旅行 / 数码 / 其他",
                    "餐饮",
                    "餐饮",
                ) { category ->
                    val normalized = LedgerCategories.normalize(category, false)
                    if (normalized == "其他" && category.trim() != "其他") {
                        host.showNotice("分类名不在列表里")
                        true
                    } else {
                        LedgerToolsStore.saveRules(
                            host,
                            tools.rules + LedgerRule(pattern.trim(), normalized, false),
                        )
                        onChanged()
                        host.showNotice("规则已保存")
                        true
                    }
                }
                true
            }
        }
    }
    val close = dialogButton(host, "关闭", false)
    close.setOnClickListener { dialog.dismiss() }
    card.addView(buttonRow(host, close, add))
    dialog.setContentView(card)
    dialog.show()
}
