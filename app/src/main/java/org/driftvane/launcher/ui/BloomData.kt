package org.driftvane.launcher.ui

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** bitDroid Bloom 2: weather and next event for the home tiles (all on the phone, Open-Meteo for weather). */
internal fun hasPerm(context: Context, p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

internal fun askPerm(context: Context, vararg p: String) {
    (context as? Activity)?.requestPermissions(arrayOf(*p), 42)
}

internal data class BWeather(val city: String, val temp: Int, val hi: Int, val lo: Int, val code: Int)

internal fun weatherEmoji(code: Int): Pair<String, String> = when (code) {
    0 -> "☀️" to "Clear"
    1, 2 -> "⛅" to "Partly cloudy"
    3 -> "☁️" to "Cloudy"
    45, 48 -> "🌫️" to "Fog"
    in 51..57 -> "🌦️" to "Drizzle"
    in 61..67, in 80..82 -> "🌧️" to "Rain"
    in 71..77, 85, 86 -> "🌨️" to "Snow"
    in 95..99 -> "⛈️" to "Storms"
    else -> "⛅" to "Weather"
}

@android.annotation.SuppressLint("MissingPermission")
internal suspend fun loadWeather(context: Context): BWeather? = withContext(Dispatchers.IO) {
    runCatching {
        val lm = context.getSystemService(LocationManager::class.java)
        val loc = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstNotNullOfOrNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() } ?: return@runCatching null
        val f = Locale.getDefault().country in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")
        val url = java.net.URL(
            "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f".format(Locale.US, loc.latitude, loc.longitude) +
                "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto" +
                (if (f) "&temperature_unit=fahrenheit" else "")
        )
        val con = url.openConnection() as java.net.HttpURLConnection
        con.connectTimeout = 8000; con.readTimeout = 8000
        val js = org.json.JSONObject(con.inputStream.bufferedReader().readText())
        val cur = js.getJSONObject("current"); val day = js.getJSONObject("daily")
        val city = runCatching {
            @Suppress("DEPRECATION")
            android.location.Geocoder(context).getFromLocation(loc.latitude, loc.longitude, 1)
                ?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
        }.getOrNull() ?: "My location"
        BWeather(
            city, Math.round(cur.getDouble("temperature_2m")).toInt(),
            Math.round(day.getJSONArray("temperature_2m_max").getDouble(0)).toInt(),
            Math.round(day.getJSONArray("temperature_2m_min").getDouble(0)).toInt(),
            cur.getInt("weather_code"),
        )
    }.getOrNull()
}

internal data class BEvent(val title: String, val time: String)

internal suspend fun loadNextEvent(context: Context): BEvent? = withContext(Dispatchers.IO) {
    runCatching {
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(now.toString()).appendPath((now + 36 * 3600_000L).toString()).build()
        context.contentResolver.query(uri,
            arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY),
            null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                val end = c.getLong(2)
                if (end < now) continue
                val f = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                val time = if (c.getInt(3) == 1) "today" else f.format(java.util.Date(c.getLong(1)))
                return@runCatching BEvent(c.getString(0) ?: "Event", time)
            }
            null
        }
    }.getOrNull()
}
