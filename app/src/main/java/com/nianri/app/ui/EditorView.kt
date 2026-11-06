package com.nianri.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import com.nianri.app.MainActivity
import com.nianri.app.R
import com.nianri.app.data.AnniversaryStore
import com.nianri.app.data.AppPreferences
import com.nianri.app.data.PhotoStore
import com.nianri.app.model.AccentPalette
import com.nianri.app.model.Anniversary
import com.nianri.app.model.ReminderAdvance
import com.nianri.app.reminder.ReminderEngine
import com.nianri.app.reminder.ReminderScheduler
import com.nianri.app.util.parseReminderTime
import com.nianri.app.util.weekdayCn
import java.time.LocalDate
import java.util.UUID

class EditorView(
    private val host: MainActivity,
    private val original: Anniversary?,
) : FrameLayout(host) {

    private val isEditing = original != null
    private var selectedDate: LocalDate = original?.date ?: LocalDate.now()
    private var recurring: Boolean = original?.recurring ?: true
    private var accentIndex: Int = original?.accentIndex ?: 0
    private var reminderEnabled: Boolean = original?.reminderEnabled ?: true
    private var advanceDays: Int = original?.reminderAdvanceDays ?: 0
    private var selectedPhotoName: String? = original?.imagePath?.takeIf {
        PhotoStore.exists(host, it)
    }
    private val originalPhotoName: String? = selectedPhotoName
    private var importingPhoto = false
    private var cancelled = false
    private var saved = false

    private lateinit var titleInput: EditText
    private lateinit var noteInput: EditText
    private lateinit var dateValue: TextView
    private lateinit var typeHelper: TextView
    private lateinit var yearlySegment: TextView
    private lateinit var onceSegment: TextView
    private lateinit var photoArt: PhotoArtView
    private lateinit var photoEmpty: View
    private lateinit var photoControls: FrameLayout
    private lateinit var photoBusy: View
    private lateinit var advancedValue: TextView
    private lateinit var timeValue: TextView
    private lateinit var reminderDetail: LinearLayout
    private lateinit var reminderSwitch: NianRiSwitch
    private lateinit var reminderHint: TextView
    private lateinit var noteCount: TextView
    private val accentMarkers = mutableListOf<FrameLayout>()
    private val accentChecks = mutableListOf<ImageView>()

    init {
        background = ColorDrawable(AppColors.paper)

        val main = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
        }
        addView(main, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        main.addView(buildTopBar())

        val scroll = ScrollView(host).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
        }
        val column = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.contentMargin(), 0, host.contentMargin(), host.dp(44))
        }
        scroll.addView(column, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        main.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))

        column.addView(buildPhotoPicker(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            host.dp(220),
        ).apply { topMargin = host.dp(8) })

        column.addView(sectionTitle("名字", "只属于这一天的名字"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(24) })
        column.addView(buildTitleInput())
        column.addView(hairline(host))

        column.addView(sectionTitle("日子", "哪一天值得被记住？"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(22) })
        column.addView(buildDateRow(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            host.dp(54),
        ))
        column.addView(buildTypeSwitch(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            host.dp(50),
        ).apply { topMargin = host.dp(10) })
        typeHelper = newText(host, "", 10.5f, AppColors.muted, AppFont.sans).apply {
            setPadding(host.dp(3), host.dp(9), 0, 0)
        }
        column.addView(typeHelper)
        updateTypeUi()

        column.addView(sectionTitle("颜色", "挑一种这一天的心情"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(22) })
        column.addView(buildAccentPicker(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(2) })

        column.addView(sectionTitle("故事", "写下那一天的光线或一句话"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(24) })
        column.addView(buildNoteInput(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))

        column.addView(sectionTitle("提醒", "在重要的日子到来前提醒你"), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(26) })
        column.addView(buildReminderCard(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(10) })

        column.addView(buildBottomActions(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = host.dp(30) })

        updatePhotoUi()
        updateReminderUi()
        updateDateUi()
    }

    private fun buildTopBar(): View {
        return LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(host.contentMargin(), host.dp(12), host.contentMargin(), host.dp(8))
            addView(iconButton(
                host,
                R.drawable.ic_arrow_back,
                "返回",
                42,
                19,
                AppColors.inkSoft,
                Color.TRANSPARENT,
                AppColors.line,
            ).apply {
                setOnClickListener { host.closeEditor(this@EditorView) }
            })
            addView(newText(
                host,
                if (isEditing) "编辑这一天" else "写下这一天",
                15f,
                AppColors.ink,
                AppFont.serif,
                0.02f,
            ).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(textButton(host, "保存", AppColors.cinnabar).apply {
                setOnClickListener { save() }
            })
        }
    }

    private fun sectionTitle(title: String, subtitle: String): View {
        return LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            addView(newText(host, title, 18f, AppColors.ink, AppFont.serif))
            addView(newText(host, subtitle, 10f, AppColors.muted, AppFont.sans).apply {
                setPadding(host.dp(9), 0, 0, host.dp(2))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun buildTitleInput(): View {
        titleInput = EditText(host).apply {
            hint = "例如：我们相遇的那一天"
            setText(original?.title ?: "")
            setSelection(text.length)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 26f)
            setTextColor(AppColors.ink)
            setHintTextColor(AppColors.faint)
            typeface = AppFont.serif
            includeFontPadding = false
            background = null
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_NEXT
            filters = arrayOf(InputFilter.LengthFilter(24))
            maxLines = 2
            setPadding(0, host.dp(12), 0, host.dp(13))
        }
        return titleInput
    }

    private fun buildDateRow(): View {
        val row = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            contentDescription = "选择日期"
            background = rippleOn(
                host,
                roundedDrawable(host, AppColors.cream, 16f, 0.8f, AppColors.line),
                0x1427231E,
            )
            setPadding(host.dp(15), 0, host.dp(13), 0)
        }
        val icon = ImageView(host).apply {
            setImageResource(R.drawable.ic_calendar)
            setColorFilter(AppColors.cinnabar)
        }
        row.addView(icon, LinearLayout.LayoutParams(host.dp(19), host.dp(19)))
        dateValue = newText(host, "", 14f, AppColors.ink, AppFont.sansMedium)
        row.addView(dateValue, LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f,
        ).apply { marginStart = host.dp(12) })
        val chevron = ImageView(host).apply {
            setImageResource(R.drawable.ic_chevron_right)
            setColorFilter(AppColors.faint)
        }
        row.addView(chevron, LinearLayout.LayoutParams(host.dp(18), host.dp(18)))
        row.setOnClickListener { openDatePicker() }
        return row
    }

    private fun buildTypeSwitch(): View {
        val container = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(host.dp(4), host.dp(4), host.dp(4), host.dp(4))
            background = roundedDrawable(host, AppColors.paperDeep, 18f)
        }
        yearlySegment = newText(host, "每年纪念", 13f, AppColors.inkSoft, AppFont.sansMedium).apply {
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = "每年纪念"
            setOnClickListener {
                recurring = true
                updateTypeUi()
            }
        }
        onceSegment = newText(host, "单次倒数", 13f, AppColors.inkSoft, AppFont.sansMedium).apply {
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = "单次倒数"
            setOnClickListener {
                recurring = false
                updateTypeUi()
            }
        }
        container.addView(yearlySegment, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        container.addView(onceSegment, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        return container
    }

    private fun updateTypeUi() {
        if (!::yearlySegment.isInitialized) return
        styleSegment(yearlySegment, recurring)
        styleSegment(onceSegment, !recurring)
        typeHelper.text = if (recurring) {
            "每年同一天都会再次被点亮，主页面会显示相伴天数。"
        } else {
            "只倒数这一次，适合旅行、考试、约定和重要计划。"
        }
    }

    private fun styleSegment(view: TextView, selected: Boolean) {
        view.setTextColor(if (selected) AppColors.cream else AppColors.inkSoft)
        view.background = if (selected) {
            rippleOn(host, roundedDrawable(host, AppColors.ink, 15f), 0x33FFFFFF)
        } else {
            rippleOn(host, roundedDrawable(host, Color.TRANSPARENT, 15f), 0x1427231E)
        }
    }

    private fun buildAccentPicker(): View {
        val container = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
        }
        for (rowIndex in 0..1) {
            val row = LinearLayout(host).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, if (rowIndex == 0) host.dp(4) else host.dp(6), 0, 0)
            }
            for (columnIndex in 0 until 6) {
                val index = rowIndex * 6 + columnIndex
                if (index >= AccentPalette.size) break
                val accent = AccentPalette.of(index)
                val outer = FrameLayout(host).apply {
                    isClickable = true
                    isFocusable = true
                    contentDescription = AccentPalette.nameOf(index)
                    setOnClickListener {
                        accentIndex = index
                        photoArt.setAccentColor(accentIndex)
                        updateAccentUi()
                    }
                }
                val inner = View(host).apply {
                    background = roundedDrawable(host, accent.color, 15f)
                }
                outer.addView(inner, FrameLayout.LayoutParams(host.dp(30), host.dp(30), Gravity.CENTER))
                val check = ImageView(host).apply {
                    setImageResource(R.drawable.ic_check)
                    setColorFilter(AppColors.cream)
                    visibility = View.GONE
                }
                outer.addView(check, FrameLayout.LayoutParams(host.dp(14), host.dp(14), Gravity.CENTER))
                accentChecks.add(check)
                row.addView(outer, LinearLayout.LayoutParams(host.dp(42), host.dp(42)).apply {
                    marginEnd = host.dp(3)
                })
                accentMarkers.add(outer)
            }
            container.addView(row)
        }
        updateAccentUi()
        return container
    }

    private fun updateAccentUi() {
        for (index in accentMarkers.indices) {
            val accent = AccentPalette.of(index)
            val selected = index == accentIndex
            accentMarkers[index].background = roundedDrawable(
                host,
                if (selected) accent.soft else Color.TRANSPARENT,
                21f,
                if (selected) 1.3f else 0f,
                if (selected) accent.color else Color.TRANSPARENT,
            )
            accentMarkers[index].scaleX = if (selected) 1.04f else 1f
            accentMarkers[index].scaleY = if (selected) 1.04f else 1f
            accentChecks.getOrNull(index)?.visibility = if (selected) View.VISIBLE else View.GONE
        }
    }

    private fun buildPhotoPicker(): View {
        val picker = FrameLayout(host).apply {
            background = roundedDrawable(host, AppColors.ink, 28f)
            clipToOutline = true
            isClickable = true
            isFocusable = true
            contentDescription = "选择一张照片"
            setOnClickListener { openPhotoPicker() }
            elevation = host.dpf(2f)
        }
        photoArt = PhotoArtView(host).apply {
            setCornerRadiusDp(28f)
            setAccentColor(accentIndex)
            setFitWideImages(true)
            setImageName(selectedPhotoName)
            setScrimEnabled(true)
        }
        picker.addView(photoArt, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))

        photoEmpty = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(host.dp(24), host.dp(18), host.dp(24), host.dp(18))
            background = roundedDrawable(host, 0x3A1A120D, 24f, 0.8f, 0x33FFF9EF)
            addView(FrameLayout(host).apply {
                background = roundedDrawable(host, 0x2EFFF9EF, 24f, 1f, 0x50FFF9EF)
                addView(ImageView(host).apply {
                    setImageResource(R.drawable.ic_image)
                    setColorFilter(AppColors.cream)
                }, FrameLayout.LayoutParams(host.dp(22), host.dp(22), Gravity.CENTER))
            }, LinearLayout.LayoutParams(host.dp(48), host.dp(48)))
            addView(newText(host, "从相册选择照片", 14f, AppColors.cream, AppFont.sansMedium).apply {
                gravity = Gravity.CENTER
                setPadding(0, host.dp(11), 0, 0)
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
            addView(newText(host, "让这一天有画面", 10f, 0xB8FFF9EF.toInt(), AppFont.sans).apply {
                gravity = Gravity.CENTER
                setPadding(0, host.dp(4), 0, 0)
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
        picker.addView(photoEmpty, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER,
        ))

        photoControls = FrameLayout(host)
        val change = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(host.dp(11), host.dp(7), host.dp(12), host.dp(7))
            background = roundedDrawable(host, 0xA6000000.toInt(), 16f, 0.8f, 0x33FFF9EF)
            addView(ImageView(host).apply {
                setImageResource(R.drawable.ic_image)
                setColorFilter(AppColors.cream)
            }, LinearLayout.LayoutParams(host.dp(14), host.dp(14)))
            addView(newText(host, "更换照片", 10.5f, AppColors.cream, AppFont.sansMedium).apply {
                setPadding(host.dp(6), 0, 0, 0)
            })
        }
        photoControls.addView(change, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.START,
        ).apply {
            leftMargin = host.dp(15)
            bottomMargin = host.dp(15)
        })
        val remove = FrameLayout(host).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "移除照片"
            background = rippleOn(
                host,
                roundedDrawable(host, 0xA6000000.toInt(), 17f, 0.8f, 0x33FFF9EF),
                0x33FFFFFF,
            )
            setOnClickListener { removePhoto() }
            addView(ImageView(host).apply {
                setImageResource(R.drawable.ic_close)
                setColorFilter(AppColors.cream)
            }, FrameLayout.LayoutParams(host.dp(15), host.dp(15), Gravity.CENTER))
        }
        photoControls.addView(remove, FrameLayout.LayoutParams(host.dp(34), host.dp(34), Gravity.TOP or Gravity.END).apply {
            topMargin = host.dp(14)
            rightMargin = host.dp(14)
        })
        picker.addView(photoControls, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))

        photoBusy = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xB3000000.toInt())
            addView(ProgressBar(host).apply {
                indeterminateTintList = ColorStateList.valueOf(AppColors.cream)
            }, LinearLayout.LayoutParams(host.dp(26), host.dp(26)))
            addView(newText(host, "正在装裱照片…", 11f, AppColors.cream, AppFont.sans).apply {
                gravity = Gravity.CENTER
                setPadding(0, host.dp(10), 0, 0)
            })
        }
        photoBusy.visibility = View.GONE
        picker.addView(photoBusy, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))
        return picker
    }

    private fun updatePhotoUi() {
        if (!::photoArt.isInitialized) return
        val hasPhoto = selectedPhotoName != null
        photoEmpty.visibility = if (hasPhoto) View.GONE else View.VISIBLE
        photoControls.visibility = if (hasPhoto && !importingPhoto) View.VISIBLE else View.GONE
        photoBusy.visibility = if (importingPhoto) View.VISIBLE else View.GONE
        photoArt.setImageName(selectedPhotoName)
    }

    private fun openPhotoPicker() {
        if (importingPhoto) return
        host.launchPhotoPicker { uri ->
            if (uri != null) importPhoto(uri)
        }
    }

    private fun importPhoto(uri: Uri) {
        importingPhoto = true
        updatePhotoUi()
        PhotoStore.importImage(host, uri) { name, error ->
            if (saved) {
                if (name != null) PhotoStore.delete(host, name)
                return@importImage
            }
            importingPhoto = false
            if (name == null) {
                updatePhotoUi()
                host.showNotice(error ?: "照片处理失败")
                return@importImage
            }
            if (cancelled) {
                PhotoStore.delete(host, name)
                return@importImage
            }
            val previous = selectedPhotoName
            if (previous != null && previous != originalPhotoName) {
                PhotoStore.delete(host, previous)
            }
            selectedPhotoName = name
            updatePhotoUi()
            photoArt.setAccentColor(accentIndex)
        }
    }

    private fun removePhoto() {
        val current = selectedPhotoName
        if (importingPhoto) {
            host.showNotice("照片正在处理中，请稍候")
            return
        }
        selectedPhotoName = null
        if (current != null && current != originalPhotoName) {
            PhotoStore.delete(host, current)
        }
        updatePhotoUi()
    }

    private fun buildNoteInput(): View {
        val wrapper = FrameLayout(host).apply {
            background = roundedDrawable(host, AppColors.cream, 18f, 0.8f, AppColors.line)
        }
        noteInput = EditText(host).apply {
            hint = "那天的风、光线，或者一句想留下的话…"
            setText(original?.note ?: "")
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(AppColors.inkSoft)
            setHintTextColor(AppColors.faint)
            typeface = AppFont.serif
            includeFontPadding = false
            background = null
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 4
            maxLines = 9
            filters = arrayOf(InputFilter.LengthFilter(500))
            setLineSpacing(host.dpf(5.5f), 1f)
            setPadding(host.dp(16), host.dp(14), host.dp(16), host.dp(14))
        }
        wrapper.addView(noteInput, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ))
        noteCount = newText(host, "", 9f, AppColors.faint, AppFont.sans).apply {
            gravity = Gravity.END
            setPadding(0, 0, host.dp(14), host.dp(10))
        }
        wrapper.addView(noteCount, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM,
        ))
        noteInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateNoteCount()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        updateNoteCount()
        return wrapper
    }

    private fun updateNoteCount() {
        if (::noteCount.isInitialized) {
            noteCount.text = "${noteInput.text.length} / 500"
        }
    }

    private fun buildReminderCard(): View {
        val card = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(host, AppColors.cream, 22f, 0.8f, AppColors.line)
            setPadding(host.dp(16), host.dp(14), host.dp(16), host.dp(14))
        }
        val head = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val iconWrap = FrameLayout(host).apply {
            background = roundedDrawable(host, AppColors.cinnabarSoft, 18f)
        }
        iconWrap.addView(ImageView(host).apply {
            setImageResource(R.drawable.ic_bell)
            setColorFilter(AppColors.cinnabarDeep)
        }, FrameLayout.LayoutParams(host.dp(18), host.dp(18), Gravity.CENTER))
        head.addView(iconWrap, LinearLayout.LayoutParams(host.dp(38), host.dp(38)))
        val textBox = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(host.dp(12), 0, 0, 0)
        }
        textBox.addView(newText(host, "提醒我", 14f, AppColors.ink, AppFont.sansMedium))
        textBox.addView(newText(host, "保存后会按每日检查时间送达", 10f, AppColors.muted, AppFont.sans).apply {
            setPadding(0, host.dp(3), 0, 0)
        })
        head.addView(textBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        reminderSwitch = NianRiSwitch(host).apply {
            contentDescription = "提醒开关"
            setChecked(reminderEnabled)
            onCheckedChange = { checked ->
                reminderEnabled = checked
                updateReminderUi()
            }
        }
        head.addView(reminderSwitch)
        card.addView(head)

        reminderDetail = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
        }
        reminderDetail.addView(hairline(host, Color.argb(130, 228, 217, 202)))
        val advancedRow = settingsRow("提前提醒", "${ReminderAdvance.label(advanceDays)}") { openAdvanceOptions() }
        advancedValue = advancedRow.second
        reminderDetail.addView(advancedRow.first, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            host.dp(52),
        ))
        val timeRow = settingsRow("每日检查时间", parseReminderTime(AppPreferences.get(host).reminderMinutes)) {
            openTimePicker()
        }
        timeValue = timeRow.second
        reminderDetail.addView(timeRow.first, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            host.dp(52),
        ))
        reminderDetail.addView(newText(host, "所有纪念日共用这个检查时间", 9.5f, AppColors.faint, AppFont.sans).apply {
            setPadding(0, host.dp(1), 0, host.dp(8))
        })
        card.addView(reminderDetail)

        reminderHint = newText(host, "", 9.5f, AppColors.cinnabar, AppFont.sans).apply {
            setPadding(0, host.dp(8), 0, 0)
        }
        card.addView(reminderHint)
        return card
    }

    private fun settingsRow(label: String, value: String, onClick: () -> Unit): Pair<View, TextView> {
        val row = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            background = rippleOn(host, roundedDrawable(host, Color.TRANSPARENT, 12f), 0x1427231E)
        }
        row.addView(newText(host, label, 12.5f, AppColors.inkSoft, AppFont.sans))
        val valueView = newText(host, value, 12f, AppColors.muted, AppFont.sans).apply {
            gravity = Gravity.END
        }
        row.addView(valueView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(ImageView(host).apply {
            setImageResource(R.drawable.ic_chevron_right)
            setColorFilter(AppColors.faint)
        }, LinearLayout.LayoutParams(host.dp(16), host.dp(16)))
        row.setOnClickListener { onClick() }
        return row to valueView
    }

    private fun updateReminderUi() {
        if (!::reminderDetail.isInitialized) return
        reminderDetail.visibility = if (reminderEnabled) View.VISIBLE else View.GONE
        advancedValue.text = ReminderAdvance.label(advanceDays)
        timeValue.text = parseReminderTime(AppPreferences.get(host).reminderMinutes)
        reminderHint.text = when {
            !reminderEnabled -> ""
            !ReminderEngine.notificationsAllowed(host) ->
                "通知权限未开启，保存后会询问一次。"
            !ReminderScheduler.hasExactAlarmAccess(host) ->
                "建议在设置中开启精确提醒，送达更准时。"
            else -> ""
        }
        reminderHint.visibility = if (reminderHint.text.isBlank()) View.GONE else View.VISIBLE
    }

    private fun openAdvanceOptions() {
        val actions = ReminderAdvance.options.map { days ->
            SheetAction(
                label = ReminderAdvance.label(days),
                subtitle = when (days) {
                    0 -> "在纪念日当天提醒"
                    1 -> "前一天留一点准备的时间"
                    7 -> "整整一周前开始期待"
                    30 -> "给远方的礼物留出时间"
                    else -> null
                },
                selected = days == advanceDays,
                onClick = {
                    advanceDays = days
                    updateReminderUi()
                },
            )
        }
        showActionSheet(host, "提前多久提醒？", actions)
    }

    private fun openTimePicker() {
        val prefs = AppPreferences.get(host)
        val time = prefs.reminderTime
        TimePickerDialog(
            host,
            R.style.Theme_NianRi_Picker,
            { _, hour, minute ->
                prefs.setReminderTime(hour, minute)
                ReminderScheduler.ensureScheduled(host)
                updateReminderUi()
            },
            time.hour,
            time.minute,
            true,
        ).show()
    }

    private fun openDatePicker() {
        showDateStepperDialog(host, selectedDate) { date ->
            selectedDate = date
            updateDateUi()
        }
    }

    private fun updateDateUi() {
        if (!::dateValue.isInitialized) return
        dateValue.text = "${selectedDate.year}年${selectedDate.monthValue}月${selectedDate.dayOfMonth}日  ${selectedDate.weekdayCn()}"
    }

    private fun buildBottomActions(): View {
        val box = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
        }
        val button = pillButton(
            host,
            if (isEditing) "保存修改" else "保存这个日子",
            AppColors.ink,
            AppColors.cream,
            54,
            15f,
        )
        button.setOnClickListener { save() }
        addPressScale(button, 0.985f)
        box.addView(button)
        box.addView(newText(
            host,
            "照片与文字只保存在这台手机",
            9f,
            AppColors.faint,
            AppFont.sans,
            0.08f,
        ).apply {
            gravity = Gravity.CENTER
            setPadding(0, host.dp(12), 0, 0)
        })
        return box
    }

    fun save() {
        if (saved) return
        if (importingPhoto) {
            host.showNotice("照片正在装裱，稍等一下")
            return
        }
        val title = titleInput.text.toString().trim()
        if (title.isEmpty()) {
            host.showNotice("先给这一天一个名字吧")
            titleInput.requestFocus()
            host.showKeyboard(titleInput)
            return
        }
        val event = Anniversary(
            id = original?.id ?: UUID.randomUUID().toString(),
            title = title,
            date = selectedDate,
            recurring = recurring,
            note = noteInput.text.toString().trim(),
            imagePath = selectedPhotoName,
            accentIndex = accentIndex,
            reminderEnabled = reminderEnabled,
            reminderAdvanceDays = advanceDays,
            createdAt = original?.createdAt ?: System.currentTimeMillis(),
        )
        try {
            AnniversaryStore.upsert(host, event)
        } catch (_: Exception) {
            host.showNotice("保存失败，请稍后再试")
            return
        }
        saved = true
        host.hideKeyboard()
        if (originalPhotoName != null && originalPhotoName != selectedPhotoName) {
            PhotoStore.delete(host, originalPhotoName)
        }
        if (original != null &&
            (original.reminderEnabled != event.reminderEnabled ||
                original.reminderAdvanceDays != event.reminderAdvanceDays ||
                original.date != event.date)
        ) {
            AppPreferences.get(host).clearNotificationMark(event.id)
        }
        host.onEventSaved(event, original)
    }

    fun hasUnsavedChanges(): Boolean {
        if (saved) return false
        if (original == null) {
            return titleInput.text.isNotBlank() ||
                noteInput.text.isNotBlank() ||
                selectedPhotoName != null ||
                selectedDate != LocalDate.now() ||
                !recurring ||
                accentIndex != 0 ||
                advanceDays != 0 ||
                !reminderEnabled
        }
        return titleInput.text.toString().trim() != original.title ||
            noteInput.text.toString().trim() != original.note ||
            selectedDate != original.date ||
            recurring != original.recurring ||
            selectedPhotoName != originalPhotoName ||
            accentIndex != original.accentIndex ||
            reminderEnabled != original.reminderEnabled ||
            advanceDays != original.reminderAdvanceDays
    }

    fun abandon() {
        if (saved) return
        cancelled = true
        val current = selectedPhotoName
        if (current != null && current != originalPhotoName) {
            PhotoStore.delete(host, current)
        }
    }

    fun focusTitle() {
        titleInput.requestFocus()
    }
}
