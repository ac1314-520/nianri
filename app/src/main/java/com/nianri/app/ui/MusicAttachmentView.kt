package com.nianri.app.ui

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nianri.app.MainActivity
import com.nianri.app.R
import com.nianri.app.data.MusicPlayer
import com.nianri.app.data.MusicStore

/**
 * 日记与纪念日共用的音乐附件卡片。
 * 有音乐时点按播放/暂停；编辑模式还可以更换或移除。
 */
class MusicAttachmentView(
    private val host: MainActivity,
    private val editable: Boolean,
    private val onPick: () -> Unit,
    private val onRemove: () -> Unit,
) : LinearLayout(host) {

    private var path: String? = null
    private var title: String? = null

    private val iconWrap = FrameLayout(host)
    private val titleView = newText(host, "", 13f, AppColors.ink, AppFont.sansMedium)
    private val subtitle = newText(host, "", 9.8f, AppColors.muted, AppFont.sans)
    private val action = FrameLayout(host)
    private val actionIcon = ImageView(host)
    private val footer = LinearLayout(host)

    init {
        orientation = VERTICAL
        background = roundedDrawable(host, AppColors.cream, 20f, 0.8f, AppColors.line)
        setPadding(host.dp(14), host.dp(12), host.dp(14), host.dp(10))
        isClickable = true
        isFocusable = true

        val row = LinearLayout(host).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        iconWrap.background = roundedDrawable(host, AppColors.cinnabarSoft, 13f)
        iconWrap.addView(ImageView(host).apply {
            setImageResource(R.drawable.ic_music)
            setColorFilter(AppColors.cinnabarDeep)
        }, FrameLayout.LayoutParams(host.dp(19), host.dp(19), Gravity.CENTER))
        row.addView(iconWrap, LinearLayout.LayoutParams(host.dp(40), host.dp(40)))

        val labels = LinearLayout(host).apply {
            orientation = VERTICAL
            setPadding(host.dp(11), 0, host.dp(8), 0)
        }
        labels.addView(titleView)
        labels.addView(subtitle.apply { setPadding(0, host.dp(3), 0, 0) })
        row.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        action.background = roundedDrawable(host, AppColors.cinnabar, 19f)
        action.addView(actionIcon, FrameLayout.LayoutParams(host.dp(17), host.dp(17), Gravity.CENTER))
        row.addView(action, LinearLayout.LayoutParams(host.dp(38), host.dp(38)))
        addView(row)

        footer.orientation = HORIZONTAL
        footer.gravity = Gravity.END
        footer.setPadding(0, host.dp(7), 0, 0)
        footer.addView(softText("更换", AppColors.cinnabar) {
            if (path == null) onPick() else onPick()
        })
        footer.addView(softText("移除", AppColors.muted) {
            if (path != null) {
                if (MusicPlayer.isPlaying(MusicPlayer.filePath(host, path!!))) {
                    MusicPlayer.stop()
                }
                onRemove()
                bind(null, null)
            }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = host.dp(14) })
        addView(footer)

        setOnClickListener {
            if (path == null) {
                if (editable) onPick()
            } else {
                togglePlay()
            }
        }
        action.setOnClickListener {
            if (path == null) {
                if (editable) onPick()
            } else {
                togglePlay()
            }
        }
        bind(null, null)
    }

    fun bind(newPath: String?, newTitle: String?) {
        val clean = newPath?.takeIf { MusicStore.exists(host, it) }
        path = clean
        title = newTitle
        val playing = clean != null && MusicPlayer.isPlaying(MusicPlayer.filePath(host, clean))
        if (clean == null) {
            titleView.text = "添加一段声音"
            subtitle.text = if (editable) "从文件选择音乐，只保存在本机" else "还没有音乐"
            action.background = roundedDrawable(host, AppColors.paperDeep, 19f)
            actionIcon.setImageResource(R.drawable.ic_add)
            actionIcon.setColorFilter(AppColors.inkSoft)
            action.contentDescription = "添加音乐"
            iconWrap.background = roundedDrawable(host, AppColors.paperDeep, 13f)
            (iconWrap.getChildAt(0) as? ImageView)?.setColorFilter(AppColors.muted)
        } else {
            titleView.text = newTitle?.takeIf { it.isNotBlank() } ?: "这一天的声音"
            subtitle.text = "轻点播放 · 已保存在本机"
            action.background = roundedDrawable(host, AppColors.cinnabar, 19f)
            actionIcon.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
            actionIcon.setColorFilter(AppColors.cream)
            action.contentDescription = if (playing) "暂停音乐" else "播放音乐"
            iconWrap.background = roundedDrawable(host, AppColors.cinnabarSoft, 13f)
            (iconWrap.getChildAt(0) as? ImageView)?.setColorFilter(AppColors.cinnabarDeep)
        }
        footer.visibility = if (editable && clean != null) View.VISIBLE else View.GONE
        isClickable = clean != null || editable
        contentDescription = when {
            clean == null -> "添加音乐"
            playing -> "暂停${titleView.text}"
            else -> "播放${titleView.text}"
        }
    }

    private fun togglePlay() {
        val file = path ?: return
        val started = MusicPlayer.toggle(MusicPlayer.filePath(host, file)) { playing ->
            actionIcon.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
            action.contentDescription = if (playing) "暂停音乐" else "播放音乐"
        }
        if (!started) {
            host.showNotice("这段音乐无法播放，请换一个标准 MP3、M4A 或 FLAC 文件")
        }
    }

    private fun softText(text: String, color: Int, action: () -> Unit): TextView {
        return newText(host, text, 10f, color, AppFont.sansMedium).apply {
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            setPadding(host.dp(4), host.dp(2), host.dp(4), host.dp(2))
            setOnClickListener { action() }
        }
    }
}
