package com.nianri.app.data

import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File

/**
 * 全应用只保留一个 MediaPlayer。日记、纪念日详情和编辑页共用，
 * 切换页面时不会叠着播放。
 */
object MusicPlayer {
    private var player: MediaPlayer? = null
    private var currentPath: String? = null
    private var stateListener: ((Boolean) -> Unit)? = null

    fun isPlaying(path: String?): Boolean {
        if (path.isNullOrBlank() || currentPath != path) return false
        return player?.isPlaying == true
    }

    fun toggle(path: String, onState: (Boolean) -> Unit): Boolean {
        val existing = player
        if (currentPath == path && existing != null) {
            try {
                if (existing.isPlaying) {
                    existing.pause()
                    stateListener = onState
                    onState(false)
                } else {
                    existing.start()
                    stateListener = onState
                    onState(true)
                }
            } catch (_: Exception) {
                release()
                onState(false)
                return false
            }
            return true
        }

        release()
        try {
            val next = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(path)
                prepare()
                setOnCompletionListener {
                    release()
                    onState(false)
                }
                setOnErrorListener { _, _, _ ->
                    release()
                    onState(false)
                    true
                }
                start()
            }
            player = next
            currentPath = path
            stateListener = onState
            onState(true)
            return true
        } catch (_: Exception) {
            release()
            onState(false)
            return false
        }
    }

    fun stop() {
        release()
    }

    fun release() {
        val callback = stateListener
        val wasPlaying = player?.isPlaying == true
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        currentPath = null
        stateListener = null
        if (wasPlaying) callback?.invoke(false)
    }

    fun filePath(context: android.content.Context, name: String): String {
        val file: File = MusicStore.fileFor(context, name)
        return file.absolutePath
    }
}
