package com.nianri.app.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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
import com.nianri.app.data.AppPreferences
import com.nianri.app.model.AccentPalette
import com.nianri.app.model.Anniversary
import com.nianri.app.model.ReminderAdvance
import com.nianri.app.model.display
import com.nianri.app.model.upcomingMilestones
import com.nianri.app.reminder.ReminderEngine
import com.nianri.app.reminder.ReminderScheduler
import com.nianri.app.util.cnAnniversary
import com.nianri.app.util.fullCnWithWeek
import com.nianri.app.util.parseReminderTime
import com.nianri.app.util.shortYearDate
import java.time.LocalDate

class DetailView(
    private val host: MainActivity,
    private val event: Anniversary,
) : FrameLayout(host) {

    private val today: LocalDate = LocalDate.now()

    init {
        background = ColorDrawable(AppColors.paper)
        val display = event.display(today)
        val accent = AccentPalette.of(event.accentIndex)

        val scroll = ScrollView(host).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
        }
        val column = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.contentMargin(), 0, host.contentMargin(), host.dp(120))
        }
        scroll.addView(column, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        column.addView(buildCover(event, display.isToday), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            host.dp(300),
        ).apply { topMargin = host.dp(12) })

        column.addView(buildIdentity(event, display.badge), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(22) })

        column.addView(buildCount(display), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(22) })

        column.addView(buildMilestones(event), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(28) })

        if (event.note.isNotBlank()) {
            column.addView(buildNote(event.note), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = host.dp(26) })
        }

        column.addView(buildReminder(event), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(28) })

        column.addView(buildEditButton(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            host.dp(52),
        ).apply { topMargin = host.dp(24) })

        column.addView(newText(
            host,
            when {
                display.isPast -> "愿每一次回望，都有温度。"
                event.recurring -> "愿每一年，都有值得纪念的相逢。"
                else -> "愿所有等待，都有回音。"
            },
            11f,
            AppColors.faint,
            AppFont.serif,
            0.03f,
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, host.dp(28), 0, 0)
        })
    }

    private fun buildCover(event: Anniversary, isToday: Boolean): View {
        val accent = AccentPalette.of(event.accentIndex)
        val cover = FrameLayout(host).apply {
            background = roundedDrawable(host, accent.deep, 30f)
            clipToOutline = true
            elevation = host.dpf(4f)
        }
        val art = PhotoArtView(host).apply {
            setCornerRadiusDp(30f)
            setAccentColors(accent.color, accent.deep)
            setTopScrimEnabled(true)
            setImageName(event.imagePath)
        }
        cover.addView(art, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))
        if (isToday) {
            cover.addView(ConfettiView(host), FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ))
        }

        val bar = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(host.dp(14), host.dp(13), host.dp(14), 0)
        }
        bar.addView(iconButton(
            host,
            R.drawable.ic_arrow_back,
            "返回",
            42,
            19,
            AppColors.ink,
            0xEFFFF9EF.toInt(),
            null,
        ).apply {
            setOnClickListener { host.returnHome() }
        })
        bar.addView(View(host), LinearLayout.LayoutParams(0, 1, 1f))
        bar.addView(iconButton(
            host,
            R.drawable.ic_more,
            "更多操作",
            42,
            19,
            AppColors.ink,
            0xEFFFF9EF.toInt(),
            null,
        ).apply {
            setOnClickListener { openMoreMenu() }
        })
        cover.addView(bar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ))
        return cover
    }

    private fun buildIdentity(event: Anniversary, badge: String): View {
        val box = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
        }
        val overline = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        overline.addView(newText(
            host,
            if (event.recurring) "纪念日" else "倒数日",
            9f,
            AppColors.cinnabar,
            AppFont.sansMedium,
            0.13f,
        ), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        overline.addView(TextView(host).apply {
            text = badge
            gravity = Gravity.CENTER
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 9.5f)
            setTextColor(if (badge == "今天" || badge == "就是今天") AppColors.cream else AppColors.inkSoft)
            typeface = AppFont.sansMedium
            includeFontPadding = false
            setPadding(host.dp(10), host.dp(5), host.dp(10), host.dp(5))
            background = if (badge == "今天" || badge == "就是今天") {
                roundedDrawable(host, AppColors.cinnabar, 18f)
            } else {
                roundedDrawable(host, AppColors.paperDeep, 18f, 0.8f, AppColors.line)
            }
        })
        box.addView(overline)
        box.addView(newText(host, event.title, 28f, AppColors.ink, AppFont.serif).apply {
            setPadding(0, host.dp(10), 0, 0)
            maxLines = 3
            setLineSpacing(host.dpf(3f), 1f)
        })
        box.addView(newText(
            host,
            if (event.recurring) "始于 ${event.date.fullCnWithWeek()}"
            else event.date.fullCnWithWeek(),
            11f,
            AppColors.muted,
            AppFont.sans,
            0.015f,
        ).apply {
            setPadding(0, host.dp(8), 0, 0)
        })
        return box
    }

    private fun buildCount(display: com.nianri.app.model.AnniversaryDisplay): View {
        val box = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, host.dp(20), 0, host.dp(2))
        }
        box.addView(newText(
            host,
            display.kicker.ifBlank { "这一天" },
            9.5f,
            AppColors.muted,
            AppFont.sansMedium,
            0.14f,
        ))
        val countLine = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(0, host.dp(2), 0, 0)
        }
        val big = newText(
            host,
            display.big,
            when {
                display.big == "今天" -> 54f
                display.big.length >= 5 -> 64f
                else -> 80f
            },
            if (display.isToday || display.isCountdown) AppColors.cinnabar else AppColors.ink,
            AppFont.serif,
        ).apply {
            includeFontPadding = false
            setLineSpacing(0f, 1f)
            fontFeatureSettings = "lnum"
        }
        countLine.addView(big)
        if (display.unit.isNotEmpty()) {
            countLine.addView(newText(
                host,
                display.unit,
                14f,
                AppColors.muted,
                AppFont.sans,
            ).apply { setPadding(host.dp(8), 0, 0, host.dp(16)) })
        }
        box.addView(countLine)
        box.addView(newText(
            host,
            display.caption,
            12.5f,
            AppColors.inkSoft,
            AppFont.sans,
        ).apply {
            setPadding(0, host.dp(12), 0, 0)
            setLineSpacing(host.dpf(5f), 1f)
        })
        box.addView(hairline(host).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (host.resources.displayMetrics.density * 0.7f).toInt().coerceAtLeast(1),
            ).apply { topMargin = host.dp(24) }
        })
        return box
    }

    private fun buildMilestones(event: Anniversary): View {
        val box = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
        }
        val header = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(newText(host, "时光刻度", 18f, AppColors.ink, AppFont.serif), LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f,
        ))
        header.addView(newText(host, "MILESTONES", 8f, AppColors.faint, AppFont.sansMedium, 0.14f))
        box.addView(header)

        val milestones = event.upcomingMilestones(today)
        if (milestones.isEmpty()) {
            box.addView(newText(
                host,
                when {
                    event.recurring -> "纪念开始后，这里会长出一个个温柔的刻度。"
                    event.display(today).isPast -> "这一天已经走过，故事仍留在这里。"
                    else -> "这一页只倒数一次，愿等待也有回音。"
                },
                12f,
                AppColors.muted,
                AppFont.sans,
            ).apply {
                setPadding(0, host.dp(12), 0, 0)
                setLineSpacing(host.dpf(5f), 1f)
            })
            return box
        }

        val card = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(host, AppColors.cream, 22f, 0.8f, AppColors.line)
            setPadding(host.dp(16), host.dp(8), host.dp(16), host.dp(8))
        }
        milestones.forEachIndexed { index, milestone ->
            val row = LinearLayout(host).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, host.dp(9), 0, host.dp(9))
            }
            val mark = FrameLayout(host)
            mark.addView(View(host).apply {
                background = roundedDrawable(host, AppColors.line, 2f)
            }, FrameLayout.LayoutParams(host.dp(1), host.dp(24)).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = host.dp(24)
            })
            mark.addView(View(host).apply {
                background = roundedDrawable(
                    host,
                    if (milestone.daysAway == 0L) AppColors.cinnabar else AppColors.gold,
                    4f,
                )
            }, FrameLayout.LayoutParams(host.dp(7), host.dp(7)).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = host.dp(20)
            })
            row.addView(mark, LinearLayout.LayoutParams(host.dp(22), host.dp(58)))

            val labels = LinearLayout(host).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(host.dp(8), 0, host.dp(8), 0)
            }
            labels.addView(newText(host, cnAnniversary(milestone.years), 14.5f, AppColors.ink, AppFont.sansMedium))
            labels.addView(newText(host, milestone.date.shortYearDate(), 10f, AppColors.muted, AppFont.sans).apply {
                setPadding(0, host.dp(3), 0, 0)
            })
            row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            row.addView(TextView(host).apply {
                text = if (milestone.daysAway == 0L) "今天" else "还有 ${milestone.daysAway} 天"
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10.5f)
                setTextColor(if (milestone.daysAway == 0L) AppColors.cinnabar else AppColors.inkSoft)
                typeface = AppFont.sansMedium
                includeFontPadding = false
                gravity = Gravity.CENTER
                setPadding(host.dp(9), host.dp(5), host.dp(9), host.dp(5))
                background = roundedDrawable(
                    host,
                    if (milestone.daysAway == 0L) AppColors.cinnabarSoft else AppColors.paper,
                    14f,
                )
            })
            card.addView(row)
            if (index != milestones.lastIndex) {
                card.addView(hairline(host, Color.argb(120, 228, 217, 202)).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        1,
                    ).apply { leftMargin = host.dp(30) }
                })
            }
        }
        box.addView(card, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(12) })
        return box
    }

    private fun buildNote(note: String): View {
        val box = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
        }
        box.addView(newText(host, "写在这天", 18f, AppColors.ink, AppFont.serif))
        val card = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(host, AppColors.cream, 22f, 0.8f, AppColors.line)
            setPadding(host.dp(20), host.dp(10), host.dp(20), host.dp(20))
        }
        card.addView(newText(host, "“", 44f, AppColors.cinnabarSoft, AppFont.serif).apply {
            includeFontPadding = false
            alpha = 0.95f
        })
        card.addView(newText(host, note, 14.5f, AppColors.inkSoft, AppFont.serif).apply {
            setLineSpacing(host.dpf(7f), 1f)
            setPadding(0, host.dp(1), 0, 0)
        })
        box.addView(card, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(12) })
        return box
    }

    private fun buildReminder(event: Anniversary): View {
        val prefs = AppPreferences.get(host)
        val box = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
        }
        box.addView(newText(host, "这一天的提醒", 18f, AppColors.ink, AppFont.serif))
        val card = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(host, AppColors.cream, 22f, 0.8f, AppColors.line)
            isClickable = true
            isFocusable = true
            setPadding(host.dp(16), host.dp(14), host.dp(16), host.dp(14))
            background = rippleOn(
                host,
                roundedDrawable(host, AppColors.cream, 22f, 0.8f, AppColors.line),
                0x1427231E,
            )
        }
        val row = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val accent = AccentPalette.of(event.accentIndex)
        val iconWrap = FrameLayout(host).apply {
            background = roundedDrawable(host, accent.soft, 18f)
        }
        iconWrap.addView(ImageView(host).apply {
            setImageResource(R.drawable.ic_bell)
            setColorFilter(accent.deep)
        }, FrameLayout.LayoutParams(host.dp(18), host.dp(18), Gravity.CENTER))
        row.addView(iconWrap, LinearLayout.LayoutParams(host.dp(38), host.dp(38)))

        val labels = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(12), 0, 0, 0)
        }
        labels.addView(newText(
            host,
            if (event.reminderEnabled) "提醒已开启" else "提醒已关闭",
            14f,
            AppColors.ink,
            AppFont.sansMedium,
        ))
        labels.addView(newText(
            host,
            if (event.reminderEnabled) {
                "${ReminderAdvance.label(event.reminderAdvanceDays)} · 每天 ${parseReminderTime(prefs.reminderMinutes)} 检查"
            } else {
                "点按为这一天开启提醒"
            },
            10.5f,
            AppColors.muted,
            AppFont.sans,
        ).apply { setPadding(0, host.dp(4), 0, 0) })
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(ImageView(host).apply {
            setImageResource(R.drawable.ic_chevron_right)
            setColorFilter(AppColors.faint)
        }, LinearLayout.LayoutParams(host.dp(16), host.dp(16)))
        card.addView(row)

        if (event.reminderEnabled && !ReminderEngine.notificationsAllowed(host)) {
            card.addView(hairline(host, Color.argb(120, 228, 217, 202)).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1,
                ).apply { topMargin = host.dp(12) }
            })
            card.addView(newText(
                host,
                "通知权限未开启，提醒无法送达。点按去开启 →",
                10.5f,
                AppColors.cinnabar,
                AppFont.sansMedium,
            ).apply {
                setPadding(0, host.dp(11), 0, 0)
                isClickable = true
                setOnClickListener { host.openNotificationSettings() }
            })
        } else if (event.reminderEnabled && !ReminderScheduler.hasExactAlarmAccess(host)) {
            card.addView(hairline(host, Color.argb(120, 228, 217, 202)).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1,
                ).apply { topMargin = host.dp(12) }
            })
            card.addView(newText(
                host,
                "精确提醒未开启，提醒可能稍有延迟。点按去开启 →",
                10.5f,
                AppColors.cinnabar,
                AppFont.sansMedium,
            ).apply {
                setPadding(0, host.dp(11), 0, 0)
                isClickable = true
                setOnClickListener { host.openExactAlarmSettings() }
            })
        }
        card.setOnClickListener { host.openEditor(event) }
        box.addView(card, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(12) })
        return box
    }

    private fun buildEditButton(): View {
        val button = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = rippleOn(
                host,
                roundedDrawable(host, AppColors.cream, 26f, 1f, AppColors.line),
                0x1427231E,
            )
        }
        val icon = ImageView(host).apply {
            setImageResource(R.drawable.ic_edit)
            setColorFilter(AppColors.inkSoft)
        }
        button.addView(icon, LinearLayout.LayoutParams(host.dp(17), host.dp(17)))
        button.addView(newText(host, "编辑这个日子", 14f, AppColors.ink, AppFont.sansMedium).apply {
            setPadding(host.dp(9), 0, 0, 0)
        })
        button.setOnClickListener { host.openEditor(event) }
        addPressScale(button, 0.985f)
        return button
    }

    private fun openMoreMenu() {
        showActionSheet(
            host,
            null,
            listOf(
                SheetAction(
                    label = "编辑这一天",
                    subtitle = "修改名字、照片、日期与提醒",
                    iconRes = R.drawable.ic_edit,
                    onClick = { host.openEditor(event) },
                ),
                SheetAction(
                    label = "删除这一天",
                    subtitle = "删除后无法恢复",
                    iconRes = R.drawable.ic_trash,
                    destructive = true,
                    onClick = {
                        showConfirmDialog(
                            host,
                            "删除这一天？",
                            "「${event.title}」的照片与记录会一起删除，此操作无法撤销。",
                            "删除",
                            "先留着",
                            destructive = true,
                        ) {
                            host.deleteEvent(event)
                        }
                    },
                ),
            ),
        )
    }
}
