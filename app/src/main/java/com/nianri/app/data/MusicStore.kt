package com.nianri.app.data

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.Executors

/**
 * 音频只保存到应用私有目录，JSON 里只记录文件名。
 * 选择器统一使用 SAF（ACTION_OPEN_DOCUMENT），不需要存储权限。
 */
object MusicStore {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private const val MAX_BYTES = 80L * 1024 * 1024
    private val supportedExtensions = setOf(
        "mp3", "m4a", "m4b", "aac", "wav", "ogg", "oga", "opus", "flac",
        "amr", "aiff", "aif", "wma", "mid", "midi", "mp4", "webm", "3gp",
        "spx", "mka", "mp2", "ac3",
    )
    private val encryptedExtensions = setOf(
        "ncm", "qmc", "qmc0", "qmc2", "qmc3", "qmc4", "qmc5", "qmc6",
        "qmc7", "qmc8", "qmcflac", "qmcogg", "mflac", "mflac0", "mgg",
        "mgg0", "mgg1", "mggl", "kgm", "kgma", "kwm", "tkm", "bkcmp3",
        "bkcm4a", "bkcflac", "bkcwav", "mmp4",
    )

    fun directory(context: Context): File {
        return File(context.filesDir, "music").apply { mkdirs() }
    }

    fun fileFor(context: Context, name: String): File {
        return if (name.startsWith("/")) File(name) else File(directory(context), name)
    }

    fun exists(context: Context, name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        return fileFor(context, name).isFile
    }

    fun importAudio(
        context: Context,
        uri: Uri,
        onFinished: (name: String?, title: String?, error: String?) -> Unit,
    ) {
        io.execute {
            var target: File? = null
            try {
                val resolver = context.contentResolver
                val mime = resolver.getType(uri).orEmpty()

                var display = "这一天的声音"
                resolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) {
                            display = cursor.getString(index)?.takeIf { it.isNotBlank() } ?: display
                        }
                    }
                }

                val nameExt = display.substringAfterLast('.', "").lowercase()
                val mimeExt = MimeTypeMap.getSingleton()
                    .getExtensionFromMimeType(mime)
                    ?.lowercase()
                if (nameExt in encryptedExtensions) {
                    throw IllegalArgumentException(
                        "这是音乐平台的加密缓存，不能直接播放；请选择导出的 MP3、FLAC 等标准音频",
                    )
                }
                if (mime.isNotEmpty() && !mime.startsWith("audio/") && nameExt !in supportedExtensions) {
                    throw IllegalArgumentException("请选择音频文件")
                }
                val ext = (nameExt.takeIf { it in supportedExtensions }
                    ?: mimeExt?.takeIf { it in supportedExtensions }
                    ?: nameExt.takeIf { it.matches(Regex("[a-z0-9]{1,5}")) && mime.startsWith("audio/") }
                    ?: "mp3")
                val title = display.substringBeforeLast('.', display)
                    .trim()
                    .takeIf { it.isNotBlank() }
                    ?: "这一天的声音"

                val dir = directory(context)
                val destination = File(dir, UUID.randomUUID().toString() + "." + ext)
                target = destination
                var copied = 0L
                resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destination).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            copied += read
                            if (copied > MAX_BYTES) {
                                throw IllegalArgumentException("音乐文件太大了，请选择 80MB 以内的音频")
                            }
                            output.write(buffer, 0, read)
                        }
                        output.flush()
                    }
                } ?: throw IllegalArgumentException("无法读取这段音乐")

                if (copied == 0L) throw IllegalArgumentException("音乐文件是空的")
                main.post { onFinished(destination.name, title, null) }
            } catch (e: Exception) {
                target?.delete()
                val message = when (e) {
                    is IllegalArgumentException -> e.message
                    else -> "音乐导入失败，请换一个文件试试"
                }
                main.post { onFinished(null, null, message) }
            }
        }
    }

    fun delete(context: Context, name: String?) {
        if (name.isNullOrBlank()) return
        runCatching { fileFor(context, name).delete() }
    }

    fun cleanupOrphans(context: Context, referencedNames: Set<String>) {
        io.execute {
            val cutoff = System.currentTimeMillis() - 10 * 60 * 1000L
            directory(context).listFiles()?.forEach { file ->
                if (file.isFile && file.name !in referencedNames && file.lastModified() < cutoff) {
                    file.delete()
                }
            }
        }
    }
}
