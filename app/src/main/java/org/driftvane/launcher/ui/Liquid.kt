package org.driftvane.launcher.ui

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.produceState
import android.Manifest
import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.LocationManager
import android.provider.CalendarContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.driftvane.launcher.LauncherModel
import java.util.Calendar
import java.util.Locale

/*
 * bitDroid "Liquid" home style: frosted glass widgets, a floating glass dock and search pill, and
 * glossy system app icons. The glass is real: each glass surface shows a blurred copy of the exact
 * part of your wallpaper behind it, then adds a milky sheen, a bright rim and a top highlight.
 */

/** True while the Liquid style is on (set at the root of the home screen). */
val LocalLiquid = staticCompositionLocalOf { false }

/** The system apps whose icons get the Liquid gloss (other apps keep their own icons). */
val LIQUID_GLOSS_PACKAGES = setOf(
    "com.android.contacts", "com.android.deskclock", "com.android.documentsui", "ws.xsoh.etar",
    "com.android.calculator2", "org.lineageos.glimpse", "org.lineageos.jelly", "org.lineageos.recorder",
    "org.lineageos.aperture", "com.android.settings",
)

private object LiquidWall {
    var id = Int.MIN_VALUE
    var bmp: ImageBitmap? = null
    var size = IntSize.Zero
}

/** A blurred copy of the wallpaper at screen size (cached; refreshed when the wallpaper changes). */
@Composable
fun rememberLiquidWallpaper(): ImageBitmap? {
    val context = LocalContext.current
    val tick = LocalResumeTick.current
    var img by remember { mutableStateOf(LiquidWall.bmp) }
    LaunchedEffect(tick) {
        img = withContext(Dispatchers.Default) { loadBlurred(context) } ?: img
    }
    return img
}

private fun loadBlurred(context: Context): ImageBitmap? = runCatching {
    val wm = WallpaperManager.getInstance(context)
    val id = runCatching { wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM) }.getOrDefault(0)
    if (id == LiquidWall.id && LiquidWall.bmp != null) return LiquidWall.bmp
    val d = wm.drawable ?: return null
    val dm = context.resources.displayMetrics
    val sw = dm.widthPixels; val sh = dm.heightPixels
    // draw the wallpaper center-cropped at a tiny size, then scale it up: a smooth, cheap blur
    val small = 14
    val w = (sw / small).coerceAtLeast(8); val h = (sh / small).coerceAtLeast(8)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val iw = d.intrinsicWidth.coerceAtLeast(1); val ih = d.intrinsicHeight.coerceAtLeast(1)
    val sc = maxOf(w.toFloat() / iw, h.toFloat() / ih)
    val dw = (iw * sc).toInt(); val dh = (ih * sc).toInt()
    d.setBounds((w - dw) / 2, (h - dh) / 2, (w - dw) / 2 + dw, (h - dh) / 2 + dh)
    d.draw(c)
    val mid = Bitmap.createScaledBitmap(bmp, (sw / 4).coerceAtLeast(8), (sh / 4).coerceAtLeast(8), true)
    LiquidWall.id = id
    LiquidWall.size = IntSize(sw, sh)
    LiquidWall.bmp = mid.asImageBitmap()
    LiquidWall.bmp
}.getOrNull()

/**
 * Liquid glass behind this element: the blurred wallpaper right under it, a milky sheen, a soft
 * top highlight and a bright 1dp rim. [dark] makes the sheen smoky instead of milky.
 */
fun Modifier.liquidGlass(shape: Shape, dark: Boolean = false): Modifier = composed {
    val wall = rememberLiquidWallpaper()
    val density = LocalDensity.current
    var pos by remember { mutableStateOf(Offset.Zero) }
    val screen = remember { LiquidWall }
    this
        .onGloballyPositioned { pos = it.positionInWindow() }
        .drawWithContent {
            val outline = shape.createOutline(size, layoutDirection, density)
            val path = Path().apply {
                when (outline) {
                    is Outline.Rectangle -> addRect(outline.rect)
                    is Outline.Rounded -> addRoundRect(outline.roundRect)
                    is Outline.Generic -> addPath(outline.path)
                }
            }
            clipPath(path) {
                val w = wall
                if (w != null && screen.size.width > 0) {
                    // the part of the (blurred) wallpaper that is right behind us
                    drawImage(
                        w,
                        dstOffset = IntOffset(-pos.x.toInt(), -pos.y.toInt()),
                        dstSize = IntSize(screen.size.width, screen.size.height),
                    )
                } else {
                    drawRect(Color(0x33FFFFFF))
                }
                drawRect(
                    Brush.linearGradient(
                        if (dark) listOf(Color(0x59202028), Color(0x40101016))
                        else listOf(Color(0x59FFFFFF), Color(0x1FFFFFFF)),
                        start = Offset.Zero, end = Offset(size.width, size.height),
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Color(0x4DFFFFFF), Color.Transparent),
                        center = Offset(size.width * 0.2f, 0f), radius = size.maxDimension * 0.7f,
                    )
                )
            }
            drawPath(path, Color(0x66FFFFFF), style = Stroke(1.dp.toPx()))
            drawContent()
        }
}

/** The glossy highlight drawn over a Liquid system app icon. */
fun Modifier.liquidGloss(shape: Shape): Modifier = drawWithContent {
    drawContent()
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply {
        when (outline) {
            is Outline.Rectangle -> addRect(outline.rect)
            is Outline.Rounded -> addRoundRect(outline.roundRect)
            is Outline.Generic -> addPath(outline.path)
        }
    }
    clipPath(path) {
        drawRect(
            Brush.verticalGradient(listOf(Color(0x73FFFFFF), Color(0x00FFFFFF)), startY = 0f, endY = size.height * 0.45f),
            size = Size(size.width, size.height * 0.45f),
        )
    }
    drawPath(path, Color(0x47FFFFFF), style = Stroke(1.dp.toPx()))
}

// ---------------------------------------------------------------- widgets

private val CardShape = RoundedCornerShape(28.dp)

/**
 * Page one of the bitDroid home: a big thin clock and the date on the left, the weather on the
 * right, and under them a glass smart stack (what's playing, or your next event).
 */
@Composable
fun LiquidTop(model: LauncherModel) {
    // bitDroid Bloom x Liquid Glass: frosted glass cut into Bloom shapes. A cookie-shaped glass
    // clock on the left; a glass weather pill and a round now-playing pill on the right.
    val tick = LocalResumeTick.current
    val now by produceState(System.currentTimeMillis(), tick) {
        while (true) { value = System.currentTimeMillis(); delay(1000L * (60 - (value / 1000 % 60)).coerceAtLeast(1)) }
    }
    val time = remember(now) { java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(now)).replace(Regex("\\s?[AaPp]\\.?[Mm]\\.?$"), "") }
    val date = remember(now) { java.text.SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(java.util.Date(now)) }
    Row(
        Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val cookie = CookieShape(9, 0.06f)
        Box(
            Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true).liquidGlass(cookie).clip(cookie),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(time, color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp, maxLines = 1)
                Text(date, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
            BloomGlassWeather(Modifier.fillMaxWidth().weight(1f))
            BloomGlassMusic(Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@Composable
private fun BloomGlassWeather(modifier: Modifier) {
    val context = LocalContext.current
    val tick = LocalResumeTick.current
    var w by remember { mutableStateOf<Weather?>(null) }
    val allowed = has(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    LaunchedEffect(tick, allowed) {
        if (!allowed) return@LaunchedEffect
        while (true) { fetchWeather(context)?.let { w = it }; delay(30 * 60 * 1000L) }
    }
    val pill = RoundedCornerShape(50)
    Row(
        modifier.liquidGlass(pill).clip(pill)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (!allowed) ask(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val cur = w
        val (emoji, text) = if (cur != null) weatherText(cur.code) else ("⛅" to if (allowed) "Loading…" else "Tap for weather")
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.width(8.dp))
        Column {
            if (cur != null) Text("${cur.temp}°", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Light, lineHeight = 22.sp)
            Text(text, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BloomGlassMusic(modifier: Modifier) {
    val context = LocalContext.current
    val tick = LocalResumeTick.current
    val media by produceState<Pair<NowPlaying, Boolean>?>(null, tick) { while (true) { value = mediaNow(context); delay(1500) } }
    val allowed = has(context, Manifest.permission.READ_CALENDAR)
    var ev by remember { mutableStateOf<NextEvent?>(null) }
    LaunchedEffect(tick, allowed) { if (allowed) ev = nextEvent(context) }
    val pill = RoundedCornerShape(50)
    val m = media
    Row(
        modifier.liquidGlass(pill).clip(pill)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (m != null) runCatching { if (m.second) m.first.controller.transportControls.pause() else m.first.controller.transportControls.play() }
                else if (!allowed) ask(context, Manifest.permission.READ_CALENDAR)
            }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (m != null) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(Color(0x33FFFFFF))) {
                m.first.art?.let { androidx.compose.foundation.Image(it.asImageBitmap(), null, Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(m.first.title, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (m.second) "playing" else "paused", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
            }
        } else {
            Box(Modifier.padding(start = 8.dp).width(3.dp).height(30.dp).liquidBar())
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                val e = ev
                Text(e?.title ?: if (allowed) "Nothing next" else "Next event", color = Color.White, fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(e?.time ?: if (allowed) "Free time" else "Tap to show", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun LiquidWeatherMini() {
    val context = LocalContext.current
    val tick = LocalResumeTick.current
    var w by remember { mutableStateOf<Weather?>(null) }
    val allowed = has(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    LaunchedEffect(tick, allowed) {
        if (!allowed) return@LaunchedEffect
        while (true) { fetchWeather(context)?.let { w = it }; delay(30 * 60 * 1000L) }
    }
    Column(
        Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
            if (!allowed) ask(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        }.padding(bottom = 4.dp),
        horizontalAlignment = Alignment.End,
    ) {
        val cur = w
        if (cur == null) {
            Text("⛅", fontSize = 26.sp)
            Text(if (allowed) "Loading…" else "Tap for weather", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        } else {
            val (emoji, text) = weatherText(cur.code)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 20.sp)
                Spacer(Modifier.width(4.dp))
                Text("${cur.temp}°", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Light)
            }
            Text("$text · ${cur.city}", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp))
        }
    }
}

/** The glass smart stack: the song that's playing (with controls), otherwise your next event. */
@Composable
private fun LiquidStack(modifier: Modifier) {
    val context = LocalContext.current
    val tick = LocalResumeTick.current
    val media by produceState<Pair<NowPlaying, Boolean>?>(null, tick) { while (true) { value = mediaNow(context); delay(1500) } }
    val allowed = has(context, Manifest.permission.READ_CALENDAR)
    var ev by remember { mutableStateOf<NextEvent?>(null) }
    LaunchedEffect(tick, allowed) { if (allowed) ev = nextEvent(context) }
    val shape = RoundedCornerShape(26.dp)
    Box(modifier.heightIn(max = 96.dp).liquidGlass(shape), contentAlignment = Alignment.CenterStart) {
        androidx.compose.animation.Crossfade(media?.first?.title, label = "stack") { title ->
            val m = media
            if (title != null && m != null) {
                val (np, playing) = m
                Row(
                    Modifier.fillMaxSize()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            runCatching { np.controller.sessionActivity?.send() }
                        }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x33FFFFFF))) {
                        np.art?.let { androidx.compose.foundation.Image(it.asImageBitmap(), null, Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(np.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(np.artist, color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(
                        Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.95f))
                            .clickable { runCatching { if (playing) np.controller.transportControls.pause() else np.controller.transportControls.play() } },
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.foundation.Canvas(Modifier.size(16.dp)) {
                            if (playing) {
                                drawRoundRect(Color(0xFF111114), Offset(size.width * 0.12f, 0f), Size(size.width * 0.28f, size.height), androidx.compose.ui.geometry.CornerRadius(3f))
                                drawRoundRect(Color(0xFF111114), Offset(size.width * 0.6f, 0f), Size(size.width * 0.28f, size.height), androidx.compose.ui.geometry.CornerRadius(3f))
                            } else {
                                drawPath(Path().apply { moveTo(size.width * 0.15f, 0f); lineTo(size.width, size.height / 2); lineTo(size.width * 0.15f, size.height); close() }, Color(0xFF111114))
                            }
                        }
                    }
                }
            } else {
                val e = ev
                Row(
                    Modifier.fillMaxSize()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            if (!allowed) ask(context, Manifest.permission.READ_CALENDAR)
                            else runCatching {
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW)
                                    .setData(CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build())
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(4.dp).height(40.dp).liquidBar())
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(e?.title ?: if (allowed) "Nothing coming up" else "Your next event",
                            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(e?.time ?: if (allowed) "Enjoy the free time" else "Tap to show it here",
                            color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun has(context: Context, p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

private fun ask(context: Context, vararg p: String) {
    (context as? Activity)?.requestPermissions(arrayOf(*p), 42)
}

private data class Weather(val city: String, val temp: Int, val hi: Int, val lo: Int, val code: Int)

private fun weatherText(code: Int): Pair<String, String> = when (code) {
    0 -> "☀️" to "Clear"
    1, 2 -> "⛅" to "Partly Cloudy"
    3 -> "☁️" to "Cloudy"
    45, 48 -> "🌫️" to "Fog"
    in 51..57 -> "🌦️" to "Drizzle"
    in 61..67, in 80..82 -> "🌧️" to "Rain"
    in 71..77, 85, 86 -> "🌨️" to "Snow"
    in 95..99 -> "⛈️" to "Storms"
    else -> "⛅" to "Weather"
}

@android.annotation.SuppressLint("MissingPermission")
private suspend fun fetchWeather(context: Context): Weather? = withContext(Dispatchers.IO) {
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
        }.getOrNull() ?: "My Location"
        Weather(
            city, Math.round(cur.getDouble("temperature_2m")).toInt(),
            Math.round(day.getJSONArray("temperature_2m_max").getDouble(0)).toInt(),
            Math.round(day.getJSONArray("temperature_2m_min").getDouble(0)).toInt(),
            cur.getInt("weather_code"),
        )
    }.getOrNull()
}

@Composable
private fun LiquidWeather(modifier: Modifier) {
    val context = LocalContext.current
    val tick = LocalResumeTick.current
    var w by remember { mutableStateOf<Weather?>(null) }
    val allowed = has(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    LaunchedEffect(tick, allowed) {
        if (!allowed) return@LaunchedEffect
        while (true) {
            fetchWeather(context)?.let { w = it }
            delay(30 * 60 * 1000L)
        }
    }
    Column(
        modifier.liquidGlass(CardShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (!allowed) ask(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        val cur = w
        if (cur == null) {
            Text(if (allowed) "Weather" else "Weather", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text("⛅", fontSize = 28.sp)
            Text(if (allowed) "Loading…" else "Tap to show the weather here", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
        } else {
            val (emoji, text) = weatherText(cur.code)
            Text(cur.city, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${cur.temp}°", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.weight(1f))
            Text(emoji, fontSize = 24.sp)
            Text("$text · H:${cur.hi}° L:${cur.lo}°", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, maxLines = 2)
        }
    }
}

private data class NextEvent(val title: String, val time: String)

private suspend fun nextEvent(context: Context): NextEvent? = withContext(Dispatchers.IO) {
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
                val time = if (c.getInt(3) == 1) "All day" else "${f.format(java.util.Date(c.getLong(1)))} – ${f.format(java.util.Date(end))}"
                return@runCatching NextEvent(c.getString(0) ?: "Event", time)
            }
            null
        }
    }.getOrNull()
}

@Composable
private fun LiquidCalendar(modifier: Modifier) {
    val context = LocalContext.current
    val tick = LocalResumeTick.current
    val allowed = has(context, Manifest.permission.READ_CALENDAR)
    var ev by remember { mutableStateOf<NextEvent?>(null) }
    LaunchedEffect(tick, allowed) { if (allowed) ev = nextEvent(context) }
    val cal = remember(tick) { Calendar.getInstance() }
    val day = remember(tick) { cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault())?.uppercase() ?: "" }
    Column(
        modifier.liquidGlass(CardShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (!allowed) ask(context, Manifest.permission.READ_CALENDAR)
                else runCatching {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW)
                        .setData(CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build())
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(day, color = Color(0xFFFF6B6B), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Text("${cal.get(Calendar.DAY_OF_MONTH)}", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.weight(1f))
        val e = ev
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).height(32.dp).liquidBar())
            Column(Modifier.padding(start = 8.dp)) {
                Text(e?.title ?: if (allowed) "Nothing next" else "Your next event",
                    color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(e?.time ?: if (allowed) "Enjoy the free time" else "Tap to show it here",
                    color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

private fun Modifier.liquidBar(): Modifier = drawWithContent {
    drawRoundRect(Color(0xFF7CC4FF), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width / 2))
}

/** The small floating glass "Search" pill above the dock. */
@Composable
fun LiquidSearchPill(onSearch: () -> Unit) {
    Row(
        Modifier.width(132.dp).height(36.dp)
            .liquidGlass(RoundedCornerShape(50))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSearch() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(15.dp)) {
            val r = size.minDimension * 0.34f
            val c = Offset(size.width * 0.42f, size.height * 0.42f)
            drawCircle(Color.White, r, c, style = Stroke(size.minDimension * 0.14f))
            drawLine(Color.White, c + Offset(r * 0.72f, r * 0.72f), Offset(size.width * 0.94f, size.height * 0.94f), size.minDimension * 0.15f)
        }
        Spacer(Modifier.width(7.dp))
        Text("Search", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
