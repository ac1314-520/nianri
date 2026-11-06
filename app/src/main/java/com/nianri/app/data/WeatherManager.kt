package com.nianri.app.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

data class WeatherNow(
    val city: String,
    val temperature: Int,
    val high: Int,
    val low: Int,
    val code: Int,
    val summary: String,
    val tip: String,
    val updatedAt: Long,
) {
    val glyph: String
        get() = when (code) {
            0, 1 -> "晴"
            2, 3 -> "云"
            45, 48 -> "雾"
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "雨"
            71, 73, 75, 77, 85, 86 -> "雪"
            95, 96, 99 -> "雷"
            else -> "天"
        }
}

object WeatherStore {
    private const val PREFS = "nianri_weather"
    private const val KEY_CITY = "city"
    private const val KEY_LAT = "latitude"
    private const val KEY_LON = "longitude"
    private const val KEY_CACHE = "cache"
    private const val KEY_UPDATED = "updated_at"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun city(context: Context): String = prefs(context).getString(KEY_CITY, "").orEmpty()

    fun hasCity(context: Context): Boolean = city(context).isNotBlank()

    fun cached(context: Context): WeatherNow? {
        val p = prefs(context)
        val raw = p.getString(KEY_CACHE, null) ?: return null
        return try {
            val o = JSONObject(raw)
            WeatherNow(
                city = o.optString("city", city(context)),
                temperature = o.optInt("temperature"),
                high = o.optInt("high"),
                low = o.optInt("low"),
                code = o.optInt("code"),
                summary = o.optString("summary"),
                tip = o.optString("tip"),
                updatedAt = o.optLong("updatedAt", p.getLong(KEY_UPDATED, 0L)),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun saveCity(context: Context, city: String, latitude: Double, longitude: Double) {
        prefs(context).edit()
            .putString(KEY_CITY, city)
            .putString(KEY_LAT, latitude.toString())
            .putString(KEY_LON, longitude.toString())
            .apply()
    }

    fun saveWeather(context: Context, weather: WeatherNow) {
        val o = JSONObject().apply {
            put("city", weather.city)
            put("temperature", weather.temperature)
            put("high", weather.high)
            put("low", weather.low)
            put("code", weather.code)
            put("summary", weather.summary)
            put("tip", weather.tip)
            put("updatedAt", weather.updatedAt)
        }
        prefs(context).edit()
            .putString(KEY_CACHE, o.toString())
            .putLong(KEY_UPDATED, weather.updatedAt)
            .apply()
    }

    fun latitude(context: Context): Double? =
        prefs(context).getString(KEY_LAT, null)?.toDoubleOrNull()

    fun longitude(context: Context): Double? =
        prefs(context).getString(KEY_LON, null)?.toDoubleOrNull()
}

/**
 * 用 Open-Meteo 的公开接口获取天气，不需要 API key。
 * 城市由用户手动设置，不申请定位权限；只有打开工作台或手动刷新时才联网。
 */
object WeatherManager {
    private const val CACHE_MINUTES = 30L
    private val main = Handler(Looper.getMainLooper())
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "nianri-weather").apply { isDaemon = true }
    }

    fun cached(context: Context): WeatherNow? = WeatherStore.cached(context)

    fun isFresh(weather: WeatherNow): Boolean =
        System.currentTimeMillis() - weather.updatedAt < CACHE_MINUTES * 60_000L

    fun city(context: Context): String = WeatherStore.city(context)

    fun configureCity(
        context: Context,
        query: String,
        callback: (Boolean, String) -> Unit,
    ) {
        val app = context.applicationContext
        val name = query.trim()
        if (name.isBlank()) {
            callback(false, "请输入城市名")
            return
        }
        executor.execute {
            try {
                val geoUrl = "https://geocoding-api.open-meteo.com/v1/search" +
                    "?name=${URLEncoder.encode(name, "UTF-8")}&count=1&language=zh&format=json"
                val geo = readJson(geoUrl).optJSONArray("results")
                val first = if (geo != null && geo.length() > 0) geo.optJSONObject(0) else null
                if (first == null) {
                    post { callback(false, "没有找到这个城市，换个名字试试") }
                    return@execute
                }
                val display = first.optString("name", name).ifBlank { name }
                val latitude = first.optDouble("latitude", Double.NaN)
                val longitude = first.optDouble("longitude", Double.NaN)
                if (latitude.isNaN() || longitude.isNaN()) {
                    post { callback(false, "这个城市暂时没有天气数据") }
                    return@execute
                }
                WeatherStore.saveCity(app, display, latitude, longitude)
                val weather = fetch(app, display, latitude, longitude)
                if (weather != null) {
                    WeatherStore.saveWeather(app, weather)
                    post { callback(true, "天气城市已设为 $display") }
                } else {
                    post { callback(true, "城市已设为 $display，天气稍后再试") }
                }
            } catch (_: Exception) {
                post { callback(false, "暂时连不上天气服务，请稍后再试") }
            }
        }
    }

    fun refresh(context: Context, callback: (WeatherNow?, String?) -> Unit) {
        val app = context.applicationContext
        val city = WeatherStore.city(app)
        val lat = WeatherStore.latitude(app)
        val lon = WeatherStore.longitude(app)
        if (city.isBlank() || lat == null || lon == null) {
            callback(null, "先设置一个城市")
            return
        }
        executor.execute {
            val weather = try {
                fetch(app, city, lat, lon)
            } catch (_: Exception) {
                null
            }
            if (weather != null) {
                WeatherStore.saveWeather(app, weather)
                post { callback(weather, null) }
            } else {
                post { callback(cached(app), "天气暂未更新") }
            }
        }
    }

    private fun fetch(context: Context, city: String, lat: Double, lon: Double): WeatherNow? {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
            "&timezone=auto&forecast_days=1"
        val root = readJson(url)
        val current = root.optJSONObject("current") ?: return null
        val daily = root.optJSONObject("daily")
        val temp = current.optDouble("temperature_2m", Double.NaN)
        if (temp.isNaN()) return null
        val code = current.optInt(
            "weather_code",
            daily?.optJSONArray("weather_code")?.optInt(0, -1) ?: -1,
        )
        val high = daily?.optJSONArray("temperature_2m_max")?.optDouble(0, temp) ?: temp
        val low = daily?.optJSONArray("temperature_2m_min")?.optDouble(0, temp) ?: temp
        return WeatherNow(
            city = city,
            temperature = temp.roundToInt(),
            high = high.roundToInt(),
            low = low.roundToInt(),
            code = code,
            summary = weatherSummary(code),
            tip = weatherTip(code, temp.roundToInt()),
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun readJson(url: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 12_000
            conn.readTimeout = 12_000
            conn.setRequestProperty("User-Agent", "NianRi/2.0 (Android)")
            conn.setRequestProperty("Accept", "application/json")
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun weatherSummary(code: Int): String = when (code) {
        0 -> "晴"
        1 -> "晴间多云"
        2 -> "多云"
        3 -> "阴"
        45, 48 -> "有雾"
        51, 53, 55 -> "毛毛雨"
        56, 57, 66, 67 -> "冻雨"
        61 -> "小雨"
        63 -> "中雨"
        65 -> "大雨"
        71, 77 -> "小雪"
        73 -> "中雪"
        75 -> "大雪"
        80, 81, 82 -> "阵雨"
        85, 86 -> "阵雪"
        95, 96, 99 -> "雷雨"
        else -> "天气变化中"
    }

    private fun weatherTip(code: Int, temp: Int): String = when {
        code in listOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99) ->
            "带把伞，路上慢一点"
        code in listOf(71, 73, 75, 77, 85, 86) -> "路滑，注意保暖"
        temp >= 30 -> "防晒补水，避开正午"
        temp <= 8 -> "添一件外套，别着凉"
        code in listOf(0, 1) -> "有光，适合把今天过好"
        else -> "云层很软，适合慢慢走"
    }

    private fun post(block: () -> Unit) {
        main.post(block)
    }
}
