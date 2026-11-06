package com.nianri.app.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import com.nianri.app.MainActivity
import com.nianri.app.R
import com.nianri.app.data.PhotoStore
import com.nianri.app.model.AccentPalette
import com.nianri.app.model.Anniversary
import com.nianri.app.model.DailyNotes
import com.nianri.app.model.dateLine
import com.nianri.app.model.display
import com.nianri.app.model.heroForHome
import com.nianri.app.model.sortedForHome
import com.nianri.app.util.dotDate
import com.nianri.app.util.weekdayCn
import java.time.LocalDate

class HomeView(
    private val host: MainActivity,
    events: List<Anniversary>,
) : FrameLayout(host) {

    private val today: LocalDate = LocalDate.now()
    private val sorted = events.sortedForHome(today)
    private val hero = sorted.heroForHome(today)
    private val rest = sorted.filter { it.id != hero?.id }

    init {
        background = ColorDrawable(AppColors.paper)
        clipChildren = false

        val scroll = ScrollView(host).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
        }
        val column = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                host.contentMargin(),
                0,
                host.contentMargin(),
                if (hero == null) host.dp(136) else host.dp(108),
            )
        }
        scroll.addView(column, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        column.addView(buildHeader())
        column.addView(buildDateLine())

        if (hero == null) {
            column.addView(buildEmptyState())
        } else {
            val heroCard = buildHero(hero)
            val heroSwipe = SwipeToDeleteLayout(host).apply {
                setCornerRadiusDp(30f)
                setContent(heroCard, fillHeight = true)
                onDelete = { host.deleteEventBySwipe(hero) }
            }
            column.addView(heroSwipe, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                host.dp(372),
            ).apply { topMargin = host.dp(20) })

            if (rest.isNotEmpty()) {
                column.addView(buildSectionLabel(sorted.size), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = host.dp(28) })
                column.addView(buildList(rest), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = host.dp(12) })
            } else {
                column.addView(
                    newText(
                        host,
                        "轻点查看 · 左滑删除",
                        10f,
                        AppColors.faint,
                        AppFont.sans,
                        0.02f,
                    ).apply {
                        gravity = Gravity.CENTER
                        setPadding(0, host.dp(12), 0, 0)
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
        }

        column.addView(buildQuote())
        val actionView = if (hero == null) buildAddBar() else buildFabLayer()
        addView(actionView, if (hero == null) {
            LayoutParams(LayoutParams.MATCH_PARENT, host.dp(118)).apply {
                gravity = Gravity.BOTTOM
            }
        } else {
            LayoutParams(LayoutParams.MATCH_PARENT, host.dp(126)).apply {
                gravity = Gravity.BOTTOM
            }
        })
    }

    private fun buildHeader(): View {
        return LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, host.dp(14), 0, 0)
            addView(TextView(host).apply {
                text = "念"
                gravity = Gravity.CENTER
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(AppColors.cream)
                typeface = AppFont.serif
                includeFontPadding = false
                background = roundedDrawable(host, AppColors.cinnabar, 9f)
            }, LinearLayout.LayoutParams(host.dp(34), host.dp(34)))
            addView(newText(host, "念日", 21f, AppColors.ink, AppFont.serif, 0.08f), LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ).apply { marginStart = host.dp(11) })
            addView(iconButton(
                host,
                R.drawable.ic_workbench,
                "打开工作台",
                42,
                19,
                AppColors.inkSoft,
                Color.TRANSPARENT,
                AppColors.line,
            ).apply {
                setOnClickListener { host.openWorkbench() }
            }, LinearLayout.LayoutParams(host.dp(42), host.dp(42)).apply {
                marginEnd = host.dp(8)
            })
            addView(iconButton(
                host,
                R.drawable.ic_settings,
                "打开设置",
                42,
                19,
                AppColors.inkSoft,
                Color.TRANSPARENT,
                AppColors.line,
            ).apply {
                setOnClickListener { host.openSettings() }
            })
        }
    }

    private fun buildDateLine(): View {
        return LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, host.dp(16), 0, 0)
            addView(newText(
                host,
                "${today.year}年${today.monthValue}月${today.dayOfMonth}日",
                10.5f,
                AppColors.muted,
                AppFont.sans,
                0.02f,
            ))
            addView(newText(
                host,
                today.weekdayCn(),
                10.5f,
                AppColors.muted,
                AppFont.sans,
                0.02f,
            ).apply { setPadding(host.dp(7), 0, 0, 0) }, LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ))
            addView(newText(host, "今日", 9f, AppColors.cinnabar, AppFont.sansMedium, 0.1f))
        }
    }

    private fun buildHero(event: Anniversary): View {
        val display = event.display(today)
        val accent = AccentPalette.of(event.accentIndex)
        val card = FrameLayout(host).apply {
            background = roundedDrawable(host, accent.deep, 30f)
            clipToOutline = true
            elevation = host.dpf(7f)
            isClickable = true
            isFocusable = true
            contentDescription = "查看${event.title}，${display.kicker}${display.big}${display.unit}"
            foreground = rippleOn(host, roundedDrawable(host, Color.TRANSPARENT, 30f), 0x24FFF9EF)
        }

        val art = PhotoArtView(host).apply {
            setCornerRadiusDp(30f)
            setAccentColors(accent.color, accent.deep)
            setTopScrimEnabled(true)
            setFitWideImages(true)
            setImageName(event.imagePath)
        }
        card.addView(art, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))

        if (display.isToday) {
            card.addView(ConfettiView(host), FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ))
        }

        val overlay = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(22), host.dp(18), host.dp(22), host.dp(20))
        }
        val topRow = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(View(host), LinearLayout.LayoutParams(0, 1, 1f))
            addView(TextView(host).apply {
                text = display.badge
                gravity = Gravity.CENTER
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10f)
                setTextColor(if (display.isToday) AppColors.cream else Color.argb(230, 255, 249, 239))
                typeface = AppFont.sansMedium
                includeFontPadding = false
                letterSpacing = 0.06f
                setPadding(host.dp(11), host.dp(6), host.dp(11), host.dp(6))
                background = if (display.isToday) {
                    roundedDrawable(host, AppColors.cinnabar, 20f, 0.8f, 0x55FFF9EF)
                } else {
                    roundedDrawable(host, 0x33FFF9EF, 20f, 0.8f, 0x45FFF9EF)
                }
            })
        }
        overlay.addView(topRow)
        overlay.addView(Space(host), LinearLayout.LayoutParams(1, 0, 1f))

        val title = newText(
            host,
            event.title,
            if (event.title.length > 17) 22f else 26f,
            AppColors.cream,
            AppFont.serif,
        ).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setLineSpacing(host.dpf(1f), 1f)
        }
        overlay.addView(title)
        overlay.addView(newText(
            host,
            "${event.date.dotDate()}   ·   ${if (event.recurring) "每年今日" else "仅此一次"}",
            10f,
            Color.argb(190, 255, 249, 239),
            AppFont.sans,
            0.045f,
        ).apply {
            setPadding(0, host.dp(8), 0, 0)
        })

        overlay.addView(newText(
            host,
            display.kicker,
            10f,
            Color.argb(190, 255, 249, 239),
            AppFont.sansMedium,
            0.13f,
        ).apply {
            setPadding(0, host.dp(20), 0, 0)
        })

        val countRow = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(0, host.dp(1), 0, 0)
        }
        val numberSize = when {
            display.big == "今天" -> 54f
            display.big.length >= 5 -> 58f
            else -> 72f
        }
        val number = newText(
            host,
            display.big,
            numberSize,
            AppColors.cream,
            AppFont.serif,
        ).apply {
            includeFontPadding = false
            setLineSpacing(0f, 1f)
            fontFeatureSettings = "lnum"
            applyCountStyle()
        }
        countRow.addView(number)
        if (display.unit.isNotEmpty()) {
            countRow.addView(newText(
                host,
                display.unit,
                13f,
                Color.argb(220, 255, 249, 239),
                AppFont.sans,
            ).apply {
                setPadding(host.dp(7), 0, 0, host.dp(13))
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
        overlay.addView(countRow)

        overlay.addView(newText(
            host,
            display.caption,
            11.5f,
            Color.argb(195, 255, 249, 239),
            AppFont.sans,
        ).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setLineSpacing(host.dpf(3f), 1f)
            setPadding(0, host.dp(8), 0, 0)
        })
        card.addView(overlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))

        card.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            host.openDetail(event)
        }
        addPressScale(card, 0.985f)

        if (display.big.all { ch -> ch.isDigit() }) {
            number.text = "0"
            number.postDelayed({
                animateNumber(number, display.big.toLongOrNull() ?: 0L)
            }, 220)
        }
        return card
    }

    private fun buildAddBar(): View {
        val wrapper = FrameLayout(host).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(AppColors.paper, Color.TRANSPARENT),
            )
        }
        val button = pillButton(
            host,
            "＋  写下第一个日子",
            AppColors.ink,
            AppColors.cream,
            54,
            15f,
        ).apply {
            contentDescription = "写下第一个纪念日"
            setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                host.openEditor(null)
            }
        }
        addPressScale(button, 0.985f)
        wrapper.addView(button, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            host.dp(54),
        ).apply {
            gravity = Gravity.BOTTOM
            leftMargin = host.contentMargin()
            rightMargin = host.contentMargin()
            bottomMargin = host.dp(22)
        })
        return wrapper
    }

    private fun buildFabLayer(): View {
        val layer = FrameLayout(host).apply {
            isClickable = false
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(AppColors.paper, Color.TRANSPARENT),
            )
        }
        layer.addView(buildFab(), FrameLayout.LayoutParams(host.dp(58), host.dp(58)).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            rightMargin = host.contentMargin()
            bottomMargin = host.dp(22)
        })
        return layer
    }

    private fun buildFab(): View {
        val button = FrameLayout(host).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "新建纪念日"
            background = rippleOn(
                host,
                roundedDrawable(host, AppColors.cinnabar, 29f),
                0x40FFF9EF,
            )
            elevation = host.dpf(8f)
            setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                host.openEditor(null)
            }
        }
        val icon = android.widget.ImageView(host).apply {
            setImageResource(R.drawable.ic_add)
            setColorFilter(AppColors.cream)
        }
        button.addView(icon, FrameLayout.LayoutParams(host.dp(25), host.dp(25), Gravity.CENTER))
        addPressScale(button, 0.94f)
        return button
    }

    private fun buildEmptyState(): View {
        val box = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
        }
        box.addView(EmptyRitualView(host), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            host.dp(290),
        ).apply { topMargin = host.dp(8) })
        box.addView(newText(
            host,
            "有些日子，值得被郑重记住。",
            23f,
            AppColors.ink,
            AppFont.serif,
            0.015f,
        ).apply {
            gravity = Gravity.CENTER
            setLineSpacing(host.dpf(4f), 1f)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(4) })
        box.addView(newText(
            host,
            "从相遇、生日，到一场远行。\n选一张照片，给它一个名字。",
            12.5f,
            AppColors.muted,
            AppFont.sans,
        ).apply {
            gravity = Gravity.CENTER
            setLineSpacing(host.dpf(6.5f), 1f)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(12) })
        return box
    }

    private fun buildSectionLabel(count: Int): View {
        return LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(host.dp(2), 0, host.dp(2), 0)
            addView(newText(host, "所有日子", 18f, AppColors.ink, AppFont.serif), LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f,
            ))
            addView(newText(
                host,
                "%02d".format(count),
                10f,
                AppColors.muted,
                AppFont.sansMedium,
                0.12f,
            ))
        }
    }

    private fun buildList(items: List<Anniversary>): View {
        val box = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
        }
        var pastLabelAdded = false
        items.forEachIndexed { index, event ->
            val display = event.display(today)
            if (display.isPast && !pastLabelAdded) {
                pastLabelAdded = true
                box.addView(buildPastLabel(), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = if (index == 0) host.dp(2) else host.dp(18)
                    bottomMargin = host.dp(9)
                })
            }
            val content = buildRowContent(event)
            val swipe = SwipeToDeleteLayout(host).apply {
                setCornerRadiusDp(22f)
                setContent(content, fillHeight = false)
                onDelete = { host.deleteEventBySwipe(event) }
            }
            box.addView(swipe, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                if (index > 0) topMargin = host.dp(10)
            })
        }
        return box
    }

    private fun buildPastLabel(): View {
        return LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(newText(
                host,
                "已经走过",
                9.5f,
                AppColors.faint,
                AppFont.sansMedium,
                0.12f,
            ))
            addView(View(host).apply {
                setBackgroundColor(Color.argb(90, 183, 171, 153))
            }, LinearLayout.LayoutParams(0, 1, 1f).apply {
                marginStart = host.dp(12)
            })
        }
    }

    private fun buildRowContent(event: Anniversary): View {
        val display = event.display(today)
        val accent = AccentPalette.of(event.accentIndex)
        val row = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(host.dp(14), host.dp(13), host.dp(14), host.dp(13))
            isClickable = true
            isFocusable = true
            elevation = host.dpf(1f)
            contentDescription = "${event.title}，${display.kicker}${display.big}${display.unit}。左滑可以删除。"
            background = rippleOn(
                host,
                roundedDrawable(host, AppColors.cream, 22f, 0.8f, Color.argb(150, 228, 217, 202)),
                0x1427231E,
            )
        }

        val seal: View = if (PhotoStore.exists(host, event.imagePath)) {
            PhotoArtView(host).apply {
                setCornerRadiusDp(14f)
                setScrimEnabled(false)
                setAccentColors(accent.color, accent.deep)
                setImageName(event.imagePath)
            }
        } else {
            FrameLayout(host).apply {
                background = roundedDrawable(host, accent.soft, 14f)
                addView(LinearLayout(host).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    addView(newText(host, "${event.date.monthValue}月", 8.5f, accent.deep, AppFont.sansMedium, 0.04f).apply {
                        gravity = Gravity.CENTER
                    })
                    addView(newText(host, "%02d".format(event.date.dayOfMonth), 17f, accent.deep, AppFont.serif).apply {
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                    })
                }, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER,
                ))
            }
        }
        row.addView(seal, LinearLayout.LayoutParams(host.dp(46), host.dp(46)))

        val middle = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(14), 0, host.dp(8), 0)
        }
        middle.addView(newText(host, event.title, 15.5f, AppColors.ink, AppFont.sansMedium).apply {
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        val meta = if (display.isToday) {
            "${event.dateLine()}  ·  今天"
        } else {
            event.dateLine()
        }
        middle.addView(newText(
            host,
            meta,
            if (display.isToday) 10.5f else 10.5f,
            if (display.isToday) AppColors.cinnabar else AppColors.muted,
            if (display.isToday) AppFont.sansMedium else AppFont.sans,
            if (display.isToday) 0.03f else 0.01f,
        ).apply {
            setPadding(0, host.dp(5), 0, 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        row.addView(middle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val countBox = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        if (display.isToday) {
            countBox.addView(newText(host, "今天", 16f, AppColors.cinnabar, AppFont.serif).apply {
                gravity = Gravity.END
            })
        } else {
            countBox.addView(newText(
                host,
                display.countPrefix,
                8.5f,
                AppColors.muted,
                AppFont.sansMedium,
                0.04f,
            ).apply { gravity = Gravity.END })
            val countLine = LinearLayout(host).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM or Gravity.END
            }
            countLine.addView(newText(
                host,
                display.countValue,
                if (display.countValue.length >= 5) 20f else 25f,
                if (display.isPast) AppColors.inkSoft else AppColors.ink,
                AppFont.serif,
            ).apply {
                includeFontPadding = false
                fontFeatureSettings = "lnum"
            })
            countLine.addView(newText(host, display.countUnit, 8.5f, AppColors.muted, AppFont.sans).apply {
                setPadding(host.dp(3), 0, 0, host.dp(3))
            })
            countBox.addView(countLine)
        }
        row.addView(countBox, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))

        row.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
            host.openDetail(event)
        }
        addPressScale(row, 0.988f)
        return row
    }

    private fun buildQuote(): View {
        val box = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(host.dp(14), host.dp(30), host.dp(14), 0)
        }
        box.addView(View(host).apply {
            setBackgroundColor(Color.argb(110, 176, 82, 56))
        }, LinearLayout.LayoutParams(host.dp(22), 1))
        box.addView(newText(
            host,
            DailyNotes.forDay(today),
            13.5f,
            AppColors.inkSoft,
            AppFont.serif,
        ).apply {
            gravity = Gravity.CENTER
            setLineSpacing(host.dpf(6f), 1f)
            setPadding(0, host.dp(16), 0, 0)
        })
        box.addView(newText(
            host,
            "念日 · 今日手记",
            8f,
            AppColors.faint,
            AppFont.sansMedium,
            0.16f,
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, host.dp(9), 0, 0)
        })
        return box
    }
}
