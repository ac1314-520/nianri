package com.nianri.app.data

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

enum class CheckInCategory(val key: String, val label: String) {
    DAILY("daily", "日常"),
    LOVE("love", "恋爱"),
    FAN("fan", "追星"),
    ;

    companion object {
        fun fromKey(key: String?): CheckInCategory =
            entries.firstOrNull { it.key == key } ?: DAILY
    }
}

data class DiaryEntry(
    val id: String,
    val epochDay: Long,
    val text: String,
    val moodKey: String?,
    val photoPath: String? = null,
    val musicPath: String? = null,
    val musicTitle: String? = null,
)

data class CheckInPlan(
    val id: String,
    val title: String,
    val category: CheckInCategory,
    val createdDay: Long,
    val checkedDays: Set<Long>,
)

data class LedgerEntry(
    val id: String,
    val epochDay: Long,
    val income: Boolean,
    val amountCents: Long,
    val category: String,
    val note: String,
)

data class PeriodRecord(
    val id: String,
    val startDay: Long,
    val endDay: Long?,
)

data class WishItem(
    val id: String,
    val title: String,
    val done: Boolean,
    val createdDay: Long,
)

data class WorkbenchSnapshot(
    val diary: List<DiaryEntry> = emptyList(),
    val plans: List<CheckInPlan> = emptyList(),
    val ledger: List<LedgerEntry> = emptyList(),
    val periods: List<PeriodRecord> = emptyList(),
    val wishes: List<WishItem> = emptyList(),
    val moods: Map<Long, String> = emptyMap(),
    val cycleLength: Int = 28,
) {
    fun diaryOn(day: Long): DiaryEntry? = diary.firstOrNull { it.epochDay == day }
    fun moodOn(day: Long): String? = moods[day]
    fun latestPeriod(): PeriodRecord? = periods.maxByOrNull { it.startDay }
}

/**
 * 工作台的全部本地数据，保存为应用私有目录下的单个 JSON。
 * 没有网络同步，也没有账号；工作台只读取这台手机上的内容。
 */
object WorkbenchStore {
    private const val FILE_NAME = "workbench.json"
    private var cached: WorkbenchSnapshot? = null

    @Synchronized
    fun get(context: Context): WorkbenchSnapshot {
        return cached ?: read(context).also { cached = it }
    }

    @Synchronized
    fun update(context: Context, transform: (WorkbenchSnapshot) -> WorkbenchSnapshot): WorkbenchSnapshot {
        val next = transform(get(context))
        write(context, next)
        cached = next
        return next
    }

    @Synchronized
    fun invalidate() {
        cached = null
    }

    fun newId(): String = UUID.randomUUID().toString()

    private fun read(context: Context): WorkbenchSnapshot {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return WorkbenchSnapshot()
        return try {
            parse(JSONObject(file.readText(Charsets.UTF_8)))
        } catch (_: Exception) {
            runCatching {
                file.copyTo(File(context.filesDir, "$FILE_NAME.bad"), overwrite = true)
            }
            WorkbenchSnapshot()
        }
    }

    private fun write(context: Context, snapshot: WorkbenchSnapshot) {
        val atomic = AtomicFile(File(context.filesDir, FILE_NAME))
        var stream: FileOutputStream? = null
        try {
            stream = atomic.startWrite()
            stream.write(snapshot.toJson().toString().toByteArray(Charsets.UTF_8))
            stream.flush()
            atomic.finishWrite(stream)
        } catch (e: Exception) {
            stream?.let(atomic::failWrite)
            throw e
        }
    }

    private fun parse(root: JSONObject): WorkbenchSnapshot {
        val diary = root.optJSONArray("diary").objects().mapNotNull { o ->
            val id = o.optString("id")
            val text = o.optString("text")
            if (id.isBlank() || text.isBlank()) null
            else DiaryEntry(
                id = id,
                epochDay = o.optLong("epochDay"),
                text = text,
                moodKey = o.optString("mood").takeIf { it.isNotBlank() },
                photoPath = o.optString("photoPath").takeIf {
                    it.isNotBlank() && it != "null"
                },
                musicPath = o.optString("musicPath").takeIf {
                    it.isNotBlank() && it != "null"
                },
                musicTitle = o.optString("musicTitle").takeIf {
                    it.isNotBlank() && it != "null"
                },
            )
        }
        val plans = root.optJSONArray("plans").objects().mapNotNull { o ->
            val id = o.optString("id")
            val title = o.optString("title")
            if (id.isBlank() || title.isBlank()) null
            else CheckInPlan(
                id = id,
                title = title,
                category = CheckInCategory.fromKey(o.optString("category")),
                createdDay = o.optLong("createdDay"),
                checkedDays = o.optJSONArray("checkedDays").longs(),
            )
        }
        val ledger = root.optJSONArray("ledger").objects().mapNotNull { o ->
            val id = o.optString("id")
            if (id.isBlank()) null
            else LedgerEntry(
                id = id,
                epochDay = o.optLong("epochDay"),
                income = o.optBoolean("income"),
                amountCents = o.optLong("amountCents"),
                category = o.optString("category"),
                note = o.optString("note"),
            )
        }
        val periods = root.optJSONArray("periods").objects().mapNotNull { o ->
            val id = o.optString("id")
            if (id.isBlank()) null
            else PeriodRecord(
                id = id,
                startDay = o.optLong("startDay"),
                endDay = if (o.isNull("endDay")) null else o.optLong("endDay"),
            )
        }
        val wishes = root.optJSONArray("wishes").objects().mapNotNull { o ->
            val id = o.optString("id")
            val title = o.optString("title")
            if (id.isBlank() || title.isBlank()) null
            else WishItem(
                id = id,
                title = title,
                done = o.optBoolean("done"),
                createdDay = o.optLong("createdDay"),
            )
        }
        val moods = mutableMapOf<Long, String>()
        root.optJSONObject("moods")?.let { obj ->
            obj.keys().forEach { key ->
                key.toLongOrNull()?.let { day ->
                    obj.optString(key).takeIf { it.isNotBlank() }?.let { moods[day] = it }
                }
            }
        }
        return WorkbenchSnapshot(
            diary = diary.sortedByDescending { it.epochDay },
            plans = plans,
            ledger = ledger.sortedByDescending { it.epochDay },
            periods = periods.sortedByDescending { it.startDay },
            wishes = wishes.sortedByDescending { it.createdDay },
            moods = moods,
            cycleLength = root.optInt("cycleLength", 28).coerceIn(21, 45),
        )
    }

    private fun WorkbenchSnapshot.toJson(): JSONObject {
        val root = JSONObject()
        root.put("cycleLength", cycleLength)
        root.put("diary", JSONArray().also { array ->
            diary.forEach { entry ->
                array.put(JSONObject().apply {
                    put("id", entry.id)
                    put("epochDay", entry.epochDay)
                    put("text", entry.text)
                    put("mood", entry.moodKey ?: "")
                    put("photoPath", entry.photoPath ?: JSONObject.NULL)
                    put("musicPath", entry.musicPath ?: JSONObject.NULL)
                    put("musicTitle", entry.musicTitle ?: JSONObject.NULL)
                })
            }
        })
        root.put("plans", JSONArray().also { array ->
            plans.forEach { plan ->
                array.put(JSONObject().apply {
                    put("id", plan.id)
                    put("title", plan.title)
                    put("category", plan.category.key)
                    put("createdDay", plan.createdDay)
                    put("checkedDays", JSONArray(plan.checkedDays.sorted()))
                })
            }
        })
        root.put("ledger", JSONArray().also { array ->
            ledger.forEach { entry ->
                array.put(JSONObject().apply {
                    put("id", entry.id)
                    put("epochDay", entry.epochDay)
                    put("income", entry.income)
                    put("amountCents", entry.amountCents)
                    put("category", entry.category)
                    put("note", entry.note)
                })
            }
        })
        root.put("periods", JSONArray().also { array ->
            periods.forEach { period ->
                array.put(JSONObject().apply {
                    put("id", period.id)
                    put("startDay", period.startDay)
                    put("endDay", period.endDay ?: JSONObject.NULL)
                })
            }
        })
        root.put("wishes", JSONArray().also { array ->
            wishes.forEach { wish ->
                array.put(JSONObject().apply {
                    put("id", wish.id)
                    put("title", wish.title)
                    put("done", wish.done)
                    put("createdDay", wish.createdDay)
                })
            }
        })
        root.put("moods", JSONObject().also { obj ->
            moods.forEach { (day, mood) -> obj.put(day.toString(), mood) }
        })
        return root
    }

    private fun JSONArray?.objects(): List<JSONObject> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) optJSONObject(i)?.let(::add)
        }
    }

    private fun JSONArray?.longs(): Set<Long> {
        if (this == null) return emptySet()
        return buildSet {
            for (i in 0 until length()) add(optLong(i))
        }
    }
}
