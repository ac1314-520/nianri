package com.nianri.app.model

import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/**
 * 一条纪念日记录。
 *
 * recurring = true 时，每年同月同日都会再次到来；
 * recurring = false 时，是一个一次性的倒数日。
 */
data class Anniversary(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val date: LocalDate = LocalDate.now(),
    val recurring: Boolean = true,
    val note: String = "",
    val imagePath: String? = null,
    val accentIndex: Int = 0,
    val reminderEnabled: Boolean = true,
    val reminderAdvanceDays: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("epochDay", date.toEpochDay())
        put("recurring", recurring)
        put("note", note)
        put("imagePath", imagePath ?: JSONObject.NULL)
        put("accentIndex", accentIndex)
        put("reminderEnabled", reminderEnabled)
        put("reminderAdvanceDays", reminderAdvanceDays)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(json: JSONObject): Anniversary? {
            return try {
                val title = json.optString("title").trim()
                if (title.isEmpty()) return null
                val epochDay = json.optLong("epochDay", Long.MIN_VALUE)
                if (epochDay == Long.MIN_VALUE) return null
                Anniversary(
                    id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
                    title = title,
                    date = LocalDate.ofEpochDay(epochDay),
                    recurring = json.optBoolean("recurring", true),
                    note = json.optString("note"),
                    imagePath = json.optString("imagePath").takeIf {
                        it.isNotBlank() && it != "null"
                    },
                    accentIndex = json.optInt("accentIndex", 0).coerceIn(0, AccentPalette.size - 1),
                    reminderEnabled = json.optBoolean("reminderEnabled", true),
                    reminderAdvanceDays = json.optInt("reminderAdvanceDays", 0)
                        .coerceIn(0, 365),
                    createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

object AccentPalette {
    data class Accent(val color: Int, val deep: Int, val soft: Int)

    val colors: IntArray = intArrayOf(
        0xFFB05238.toInt(), // 朱砂
        0xFF7E8B74.toInt(), // 苔绿
        0xFF6F8190.toInt(), // 远山
        0xFFB08A54.toInt(), // 琥珀
        0xFF8C6A78.toInt(), // 绛紫
        0xFF5C6068.toInt(), // 石墨
        0xFFC17F7B.toInt(), // 玫瑰
        0xFF5D8191.toInt(), // 湖蓝
        0xFF8E9563.toInt(), // 橄榄
        0xFFA97B4E.toInt(), // 焦糖
        0xFF8A7AA5.toInt(), // 紫藤
        0xFF6E9188.toInt(), // 青瓷
    )

    val names = arrayOf(
        "朱砂", "苔绿", "远山", "琥珀", "绛紫", "石墨",
        "玫瑰", "湖蓝", "橄榄", "焦糖", "紫藤", "青瓷",
    )

    private val deeps = intArrayOf(
        0xFF7C3524.toInt(),
        0xFF52604A.toInt(),
        0xFF455865.toInt(),
        0xFF7B5A2F.toInt(),
        0xFF5F4350.toInt(),
        0xFF383C43.toInt(),
        0xFF8A4F4C.toInt(),
        0xFF3E5C6A.toInt(),
        0xFF636A42.toInt(),
        0xFF77522F.toInt(),
        0xFF5D5178.toInt(),
        0xFF48675F.toInt(),
    )

    private val softs = intArrayOf(
        0xFFF1DED4.toInt(),
        0xFFE4E8DF.toInt(),
        0xFFDFE6EA.toInt(),
        0xFFF0E5D2.toInt(),
        0xFFEADEE3.toInt(),
        0xFFE0E1E2.toInt(),
        0xFFF4E1DE.toInt(),
        0xFFDDE9ED.toInt(),
        0xFFEBEDDD.toInt(),
        0xFFF1E4D3.toInt(),
        0xFFE9E4F0.toInt(),
        0xFFDFEAE6.toInt(),
    )

    val size: Int get() = colors.size

    fun of(index: Int): Accent {
        val safe = ((index % size) + size) % size
        return Accent(colors[safe], deeps[safe], softs[safe])
    }

    fun nameOf(index: Int): String = names[((index % size) + size) % size]
}

object ReminderAdvance {
    val options = listOf(0, 1, 3, 7, 14, 30)

    fun label(days: Int): String = when (days) {
        0 -> "当天"
        1 -> "提前 1 天"
        3 -> "提前 3 天"
        7 -> "提前 1 周"
        14 -> "提前 2 周"
        30 -> "提前 1 个月"
        else -> "提前 $days 天"
    }
}
