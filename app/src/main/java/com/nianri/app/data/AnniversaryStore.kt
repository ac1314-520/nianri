package com.nianri.app.data

import android.content.Context
import android.util.AtomicFile
import com.nianri.app.model.Anniversary
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream

/**
 * 全部纪念日保存在应用私有目录中的单个 JSON 文件里。
 * 应用没有账号、没有云同步，也不会上传任何纪念日内容。
 */
object AnniversaryStore {
    private const val FILE_NAME = "anniversaries.json"
    private var cached: MutableList<Anniversary>? = null

    @Synchronized
    fun all(context: Context): List<Anniversary> {
        return cached ?: read(context).toMutableList().also { cached = it }
    }

    @Synchronized
    fun find(context: Context, id: String): Anniversary? =
        all(context).firstOrNull { it.id == id }

    @Synchronized
    fun upsert(context: Context, anniversary: Anniversary): List<Anniversary> {
        val list = all(context).toMutableList()
        val index = list.indexOfFirst { it.id == anniversary.id }
        if (index >= 0) {
            list[index] = anniversary
        } else {
            val nextOrder = if (list.any { it.manualOrder != null }) {
                (list.mapNotNull { it.manualOrder }.maxOrNull() ?: -1) + 1
            } else {
                null
            }
            list.add(anniversary.copy(manualOrder = nextOrder))
        }
        write(context, list)
        cached = list
        return list
    }

    @Synchronized
    fun reorder(context: Context, ids: List<String>): List<Anniversary> {
        val positions = ids.withIndex().associate { (index, id) -> id to index }
        val list = all(context).map { event ->
            event.copy(manualOrder = positions[event.id] ?: event.manualOrder)
        }.toMutableList()
        write(context, list)
        cached = list
        return list
    }

    @Synchronized
    fun delete(context: Context, id: String): List<Anniversary> {
        val list = all(context).toMutableList()
        list.removeAll { it.id == id }
        write(context, list)
        cached = list
        return list
    }

    @Synchronized
    fun invalidate() {
        cached = null
    }

    private fun read(context: Context): List<Anniversary> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText(Charsets.UTF_8))
            buildList {
                for (i in 0 until array.length()) {
                    array.optJSONObject(i)?.let { Anniversary.fromJson(it) }?.let(::add)
                }
            }
        } catch (_: Exception) {
            // 保留损坏文件，方便以后排查，同时不让应用打不开。
            runCatching {
                file.copyTo(File(context.filesDir, "$FILE_NAME.bad"), overwrite = true)
            }
            emptyList()
        }
    }

    private fun write(context: Context, list: List<Anniversary>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        val atomic = AtomicFile(File(context.filesDir, FILE_NAME))
        var stream: FileOutputStream? = null
        try {
            stream = atomic.startWrite()
            stream.write(array.toString().toByteArray(Charsets.UTF_8))
            stream.flush()
            atomic.finishWrite(stream)
        } catch (e: Exception) {
            stream?.let(atomic::failWrite)
            throw e
        }
    }
}
