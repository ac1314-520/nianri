package com.nianri.app.data

import android.content.Context
import android.location.Geocoder
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.Locale
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
 * 地点搜索优先使用 Android Geocoder，失败时回退到 OpenStreetMap Nominatim；
 * 天气改用 MET Norway Locationforecast 2.0，不需要 API key。
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

    private data class Place(
        val name: String,
        val latitude: Double,
        val longitude: Double,
    )

    fun configureCity(
        context: Context,
        query: String,
        callback: (Boolean, String) -> Unit,
    ) {
        val app = context.applicationContext
        val name = query.trim()
        if (name.isBlank()) {
            callback(false, "请输入区 / 县 / 城市")
            return
        }
        executor.execute {
            try {
                val place = findPlace(app, name)
                if (place == null) {
                    post { callback(false, "没有找到这个地点，试试「区名 + 城市」") }
                    return@execute
                }
                WeatherStore.saveCity(app, place.name, place.latitude, place.longitude)
                val weather = fetch(app, place.name, place.latitude, place.longitude)
                if (weather != null) {
                    WeatherStore.saveWeather(app, weather)
                    post { callback(true, "天气地点已设为 ${place.name}") }
                } else {
                    post { callback(true, "地点已设为 ${place.name}，天气稍后再试") }
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
            callback(null, "先设置一个天气地点")
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

    private fun findPlace(context: Context, query: String): Place? {
        geocodeAndroid(context, query)?.let { return it }
        return geocodeNominatim(context, query)
    }

    private fun geocodeAndroid(context: Context, query: String): Place? {
        if (!Geocoder.isPresent()) return null
        return try {
            val geocoder = Geocoder(context, Locale.CHINA)
            val addresses = geocoder.getFromLocationName(query, 8).orEmpty()
            addresses.firstNotNullOfOrNull { address ->
                val latitude = address.latitude
                val longitude = address.longitude
                if (latitude.isNaN() || longitude.isNaN()) return@firstNotNullOfOrNull null
                val parts = listOfNotNull(
                    address.featureName,
                    address.locality,
                    address.subAdminArea,
                    address.adminArea,
                ).map { it.trim() }.filter { it.isNotBlank() }.distinct()
                val display = parts.joinToString(" · ").ifBlank { query }
                Place(display, latitude, longitude)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun geocodeNominatim(context: Context, query: String): Place? {
        return try {
            val url = "https://nominatim.openstreetmap.org/search" +
                "?q=${URLEncoder.encode("$query 中国", "UTF-8")}" +
                "&format=json&limit=6&accept-language=zh-CN&countrycodes=cn"
            val array = readJsonArray(url)
            var best: Place? = null
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val lat = item.optString("lat").toDoubleOrNull() ?: continue
                val lon = item.optString("lon").toDoubleOrNull() ?: continue
                val display = item.optString("display_name")
                    .split(",").map { it.trim() }.filter { it.isNotBlank() }
                    .take(3).joinToString(" · ")
                    .ifBlank { query }
                best = Place(display, lat, lon)
                break
            }
            best
        } catch (_: Exception) {
            null
        }
    }

    private fun fetch(context: Context, city: String, lat: Double, lon: Double): WeatherNow? {
        val url = "https://api.met.no/weatherapi/locationforecast/2.0/compact" +
            "?lat=$lat&lon=$lon"
        val root = readJson(url)
        val series = root.optJSONObject("properties")?.optJSONArray("timeseries") ?: return null
        val first = series.optJSONObject(0) ?: return null
        val data = first.optJSONObject("data") ?: return null
        val instant = data.optJSONObject("instant")?.optJSONObject("details") ?: return null
        val temp = instant.optDouble("air_temperature", Double.NaN)
        if (temp.isNaN()) return null
        val temps = (0 until minOf(series.length(), 24)).mapNotNull { index ->
            series.optJSONObject(index)
                ?.optJSONObject("data")
                ?.optJSONObject("instant")
                ?.optJSONObject("details")
                ?.optDouble("air_temperature", Double.NaN)
                ?.takeUnless { it.isNaN() }
        }
        val symbol = data.optJSONObject("next_1_hours")
            ?.optJSONObject("summary")
            ?.optString("symbol_code")
            .orEmpty()
            .ifBlank {
                data.optJSONObject("next_6_hours")
                    ?.optJSONObject("summary")
                    ?.optString("symbol_code")
                    .orEmpty()
            }
        val code = metCode(symbol)
        return WeatherNow(
            city = city,
            temperature = temp.roundToInt(),
            high = (temps.maxOrNull() ?: temp).roundToInt(),
            low = (temps.minOrNull() ?: temp).roundToInt(),
            code = code,
            summary = weatherSummary(code),
            tip = weatherTip(code, temp.roundToInt()),
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun metCode(symbol: String): Int {
        val value = symbol.lowercase(Locale.US)
        return when {
            value.contains("thunder") -> 95
            value.contains("heavysnow") -> 75
            value.contains("lightsnow") -> 71
            value.contains("snow") -> 73
            value.contains("heavyrain") -> 65
            value.contains("sleet") -> 66
            value.contains("rain") -> 61
            value.contains("drizzle") -> 51
            value.contains("fog") -> 45
            value.contains("partlycloudy") -> 2
            value.contains("fair") -> 1
            value.contains("cloudy") -> 3
            value.contains("clear") -> 0
            else -> 3
        }
    }

    private fun readJsonArray(url: String): JSONArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", "NianRi/2.21 (Android)")
            conn.setRequestProperty("Accept", "application/json")
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return JSONArray(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun readJson(url: String): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 12_000
            conn.readTimeout = 12_000
            conn.setRequestProperty("User-Agent", "NianRi/2.21 (Android)")
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
