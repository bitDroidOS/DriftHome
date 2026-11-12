package org.driftvane.launcher.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.driftvane.launcher.EMPTY
import org.driftvane.launcher.LauncherModel
import org.driftvane.launcher.WidgetItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Built-in DriftHome tiles. They live on the home grid like widgets (negative ids), so you can
 * hold one and drag it anywhere, resize it from any edge, or drop it on Remove.
 */
object DriftTiles {
    const val MUSIC = -1
    const val CLOCK = -2
    const val DATE = -3
    const val BATTERY = -4
    val all = listOf(MUSIC to "Now playing", CLOCK to "Clock", DATE to "Date & alarm", BATTERY to "Battery")
    /** Default size (spanX to spanY). */
    fun span(id: Int): Pair<Int, Int> = when (id) { MUSIC -> 2 to 2; BATTERY -> 4 to 1; else -> 2 to 1 }
}

/** Home styles that use the Morph look (uniform tiles, outlined search bar). */
fun isMorphLike(style: String) = style == "morph" || style == "orbit"

/** The one shape every tile and widget uses, following the icon shape picked in Wallpaper & style. */
fun tileShape(iconShape: String): Shape = when (iconShape) {
    "circle" -> RoundedCornerShape(50)
    "square" -> RoundedCornerShape(14.dp)
    "rounded" -> RoundedCornerShape(26.dp)
    "teardrop" -> RoundedCornerShape(30.dp, 30.dp, 30.dp, 10.dp)
    "pebble" -> SquircleShape(3.2)
    else -> SquircleShape(5.2)
}

/** Glass tile surface tinted with the accent, with a fine white edge. */
fun Modifier.morphTile(shape: Shape, accent: Color, tone: Int = 0): Modifier {
    val base = when (tone) {
        1 -> lerp(accent, Color(0xFF0E3B40), 0.6f)
        2 -> lerp(accent, Color(0xFF3D1630), 0.6f)
        else -> lerp(accent, Color(0xFF14111C), 0.68f)
    }
    return this.clip(shape)
        .background(Brush.linearGradient(listOf(base.copy(alpha = 0.86f), base.copy(alpha = 0.70f))))
        .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
}

private fun nextAlarm(context: Context): String? = runCatching {
    val t = context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime ?: return null
    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    SimpleDateFormat(if (is24) "EEE HH:mm" else "EEE h:mm a", Locale.getDefault()).format(Date(t))
}.getOrNull()

private fun openClockApp(context: Context) {
    runCatching { context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Draws a built-in tile (called by the grid for widgets with a negative id). */
@Composable
fun DriftTile(model: LauncherModel, w: WidgetItem, modifier: Modifier) {
    val live = rememberLive()
    val context = LocalContext.current
    val shape = tileShape(model.iconShape)
    val tall = w.spanY >= 2
    when (w.id) {
        DriftTiles.MUSIC -> {
            val np = live.playing
            val art = remember(np?.art) { np?.art?.asImageBitmap() }
            Box(
                modifier.clip(shape)
                    .background(Brush.linearGradient(listOf(Color(0xFF6D47D1), Color(0xFFB83A5E))))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {
                        if (np != null) openPlayer(context, np) else if (!live.access) openMediaAccessSettings(context)
                    }
            ) {
                if (art != null) {
                    Image(art, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000)))))
                }
                if (np != null) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(12.dp).size(34.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.9f))
                            .clickable { runCatching { np.controller.transportControls.pause() } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            repeat(2) { Box(Modifier.width(3.5.dp).height(12.dp).background(Color(0xFF21005D), RoundedCornerShape(2.dp))) }
                        }
                    }
                }
                Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                    Text(
                        when { np != null -> np.title; !live.access -> "Tap to show music"; else -> "Nothing playing" },
                        color = Color.White, fontSize = if (tall) 16.sp else 14.sp, fontWeight = FontWeight.W700,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (np != null && np.artist.isNotBlank()) {
                        Text(np.artist, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        DriftTiles.CLOCK -> Box(
            modifier.morphTile(shape, model.accent)
                .clickable(remember { MutableInteractionSource() }, indication = null) { openClockApp(context) },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(live.time, color = Color.White, fontSize = if (tall) 48.sp else 32.sp, fontWeight = FontWeight.W500,
                    lineHeight = if (tall) 50.sp else 34.sp, maxLines = 1)
                Text(SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date()), color = Color.White.copy(alpha = 0.72f),
                    fontSize = 11.sp, fontWeight = FontWeight.W500)
            }
        }
        DriftTiles.DATE -> {
            val alarm by produceState(nextAlarm(context)) { while (true) { value = nextAlarm(context); delay(60_000) } }
            Row(
                modifier.morphTile(shape, model.accent, tone = 1)
                    .clickable(remember { MutableInteractionSource() }, indication = null) { openClockApp(context) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(SimpleDateFormat("EEEE", Locale.getDefault()).format(Date()), color = Color(0xFF9EF0E6),
                        fontSize = 11.sp, fontWeight = FontWeight.W600, maxLines = 1)
                    Text(SimpleDateFormat("MMM d", Locale.getDefault()).format(Date()), color = Color.White,
                        fontSize = 20.sp, fontWeight = FontWeight.W600, maxLines = 1)
                    if (alarm != null) Text("⏰ $alarm", color = Color.White.copy(alpha = 0.72f), fontSize = 10.sp, maxLines = 1)
                }
            }
        }
        else -> { // battery
            val low = live.battery <= 15 && !live.charging
            val fill = if (low) Color(0xFFFF6B78) else Color(0xFFFFB8D2)
            Row(
                modifier.morphTile(shape, model.accent, tone = 2).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (live.charging) "⚡" else "🔋", fontSize = 15.sp)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.16f))) {
                    Box(Modifier.fillMaxWidth(live.battery / 100f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(fill))
                }
                Spacer(Modifier.width(10.dp))
                Text("${live.battery}%", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.W600)
            }
        }
    }
}

/**
 * The Morph search bar: wide, glassy, with a bold white outline. Turns into page dots while
 * you swipe between pages.
 */
@Composable
fun MorphSearchBar(model: LauncherModel, count: Int, current: Int, swiping: Boolean, onSearch: () -> Unit) {
    var showDots by remember { mutableStateOf(false) }
    LaunchedEffect(swiping, current) { if (swiping) showDots = true else { delay(900); showDots = false } }
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(50.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.8.dp, Color.White.copy(alpha = 0.92f), shape)
            .clickable(remember { MutableInteractionSource() }, indication = null) { onSearch() }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        AnimatedContent(
            showDots && count > 1,
            transitionSpec = { (fadeIn(tween(160)) + scaleIn(initialScale = 0.92f)) togetherWith (fadeOut(tween(120)) + scaleOut(targetScale = 0.92f)) },
            label = "bar",
        ) { dots ->
            if (dots) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    repeat(count) { i ->
                        val w by animateFloatAsState(if (i == current) 18f else 7f, spring(0.6f, 500f), label = "d")
                        Box(Modifier.padding(horizontal = 3.dp).height(7.dp).width(w.dp)
                            .background(if (i == current) Color.White else Color(0x66FFFFFF), RoundedCornerShape(50)))
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(17.dp)) {
                        val r = size.minDimension * 0.34f
                        val c = Offset(size.width * 0.42f, size.height * 0.42f)
                        drawCircle(Color.White, r, c, style = Stroke(size.minDimension * 0.13f))
                        drawLine(Color.White, c + Offset(r * 0.72f, r * 0.72f), Offset(size.width * 0.92f, size.height * 0.92f),
                            size.minDimension * 0.14f, StrokeCap.Round)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("Search", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.W600)
                    Spacer(Modifier.weight(1f))
                    // mic glyph
                    Canvas(Modifier.size(16.dp)) {
                        val w = size.width
                        drawRoundRect(Color.White, topLeft = Offset(w * 0.34f, 0f),
                            size = androidx.compose.ui.geometry.Size(w * 0.32f, size.height * 0.62f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.16f))
                        drawArc(Color.White, 0f, 180f, false, topLeft = Offset(w * 0.18f, size.height * 0.22f),
                            size = androidx.compose.ui.geometry.Size(w * 0.64f, size.height * 0.56f), style = Stroke(w * 0.1f))
                        drawLine(Color.White, Offset(w / 2, size.height * 0.78f), Offset(w / 2, size.height), w * 0.1f)
                    }
                }
            }
        }
    }
}

/**
 * Orbit: the first page's apps ride a ring around the clock. Drag anywhere on the ring to spin
 * it (it coasts when you let go). Tap an app to open it, hold it for its menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OrbitPage(model: LauncherModel, items: List<String>, handler: ItemHandler) {
    val live = rememberLive()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val spin = remember { Animatable(0f) }
    val apps = items.filter { it != EMPTY && (model.apps[it] != null || model.folders[it] != null) }.take(ORBIT_SLOTS)
    val alarm by produceState(nextAlarm(context)) { while (true) { value = nextAlarm(context); delay(60_000) } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val icon = 58f * model.iconScale.coerceIn(0.8f, 1.2f)
        val iconPx = with(density) { icon.dp.toPx() }
        val radius = min(wPx * 0.5f - iconPx * 0.85f, hPx * 0.30f)
        val center = Offset(wPx / 2f, hPx - radius - iconPx * 0.9f)
        val clockDp = with(density) { (radius * 1.05f).toDp() }
        val accent = model.accent
        // drag on (or near) the ring spins it; it coasts when you let go and settles on an app.
        // Sits on the parent so it works when the drag starts on an app too (the tap just cancels).
        Box(Modifier.fillMaxSize().pointerInput(apps.size, center, radius) {
            val tracker = VelocityTracker()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val dist = (down.position - center).getDistance()
                if (dist < radius - iconPx || dist > radius + iconPx * 1.3f) return@awaitEachGesture
                scope.launch { spin.stop() }
                tracker.resetTracking()
                val slop = awaitTouchSlopOrCancellation(down.id) { c, _ -> c.consume() } ?: return@awaitEachGesture
                var last = down.position
                var angle = spin.value
                fun step(p: Offset, t: Long) {
                    val a0 = atan2(last.y - center.y, last.x - center.x)
                    val a1 = atan2(p.y - center.y, p.x - center.x)
                    var d = (a1 - a0) * 180f / PI.toFloat()
                    if (d > 180f) d -= 360f
                    if (d < -180f) d += 360f
                    last = p
                    angle += d
                    val now = angle
                    scope.launch { spin.snapTo(now) }
                    tracker.addPosition(t, Offset(now, 0f))
                }
                step(slop.position, slop.uptimeMillis)
                drag(slop.id) { c -> step(c.position, c.uptimeMillis); c.consume() }
                val v = tracker.calculateVelocity().x.coerceIn(-1400f, 1400f)
                scope.launch {
                    spin.animateDecay(v, exponentialDecay(frictionMultiplier = 1.6f))
                    if (apps.isNotEmpty()) {
                        val stepDeg = 360f / apps.size
                        spin.animateTo((spin.value / stepDeg).roundToInt() * stepDeg, spring(0.7f, 300f))
                    }
                }
            }
        }) {

        // header
        Column(Modifier.padding(start = 24.dp, top = 18.dp)) {
            Text(SimpleDateFormat("EEEE", Locale.getDefault()).format(Date()).uppercase(), color = lerp(accent, Color.White, 0.55f),
                fontSize = 13.sp, fontWeight = FontWeight.W600, letterSpacing = 1.2.sp)
            Text(SimpleDateFormat("MMMM d", Locale.getDefault()).format(Date()), color = Color.White, fontSize = 40.sp,
                fontWeight = FontWeight.W500, lineHeight = 44.sp)
            Text(listOfNotNull("${live.battery}%" + if (live.charging) " charging" else "", alarm?.let { "⏰ $it" }).joinToString("  ·  "),
                color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }

        // ring track
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(alpha = 0.16f), radius, center, style = Stroke(1.dp.toPx()))
            drawCircle(Color.White.copy(alpha = 0.06f), radius + iconPx * 0.62f, center, style = Stroke(iconPx * 0.5f))
        }

        // clock in the middle (outlined, same shape language as the search bar)
        Box(
            Modifier.offset { IntOffset((center.x - with(density) { clockDp.toPx() } / 2).roundToInt(), (center.y - with(density) { clockDp.toPx() } / 2).roundToInt()) }
                .size(clockDp).clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .border(1.8.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                .clickable(remember { MutableInteractionSource() }, indication = null) { openClockApp(context) },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(live.time, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.W500, lineHeight = 36.sp)
                Text("spin the ring", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
            }
        }

        // apps on the ring
        apps.forEachIndexed { i, k ->
            key(k) {
                val bounds = remember { arrayOf(Rect.Zero) }
                val ang = (spin.value + i * 360f / apps.size - 90f) * PI.toFloat() / 180f
                val x = center.x + radius * cos(ang) - iconPx / 2
                val y = center.y + radius * sin(ang) - iconPx / 2
                // the app nearest the top is "focused": a bit bigger and fully bright
                val nearTop = ((spin.value + i * 360f / apps.size) % 360f + 360f) % 360f
                val focus = 1f - (min(nearTop, 360f - nearTop) / 90f).coerceIn(0f, 1f)
                Box(
                    Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                        .graphicsLayer { val s = 0.9f + 0.18f * focus; scaleX = s; scaleY = s; alpha = 0.75f + 0.25f * focus }
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null,
                            onClick = { handler.tap(k, bounds[0]) },
                            onLongClick = { handler.menu(k, bounds[0]) },
                        )
                ) { HomeItem(model, k, icon, false, onBounds = { bounds[0] = it }) }
            }
        }
        }
    }
}

/** How many apps ride the Orbit ring on page one (the rest flow to page two). */
const val ORBIT_SLOTS = org.driftvane.launcher.ORBIT_RING

/**
 * Wallpaper & style: change wallpaper, pick Morph or Orbit, one shape for every tile and icon,
 * and the accent the tiles are tinted with. Opens from the home menu and from edit mode.
 */
@Composable
fun StyleSheet(model: LauncherModel, onWallpaper: () -> Unit) {
    val context = LocalContext.current
    val wall = remember {
        runCatching {
            val d = android.app.WallpaperManager.getInstance(context).drawable ?: return@runCatching null
            val w = 360
            val h = (w * 2.1f).toInt()
            val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(bmp)
            val iw = d.intrinsicWidth.coerceAtLeast(1); val ih = d.intrinsicHeight.coerceAtLeast(1)
            val sc = maxOf(w.toFloat() / iw, h.toFloat() / ih)
            val dw = (iw * sc).toInt(); val dh = (ih * sc).toInt()
            d.setBounds((w - dw) / 2, (h - dh) / 2, (w - dw) / 2 + dw, (h - dh) / 2 + dh)
            d.draw(c)
            bmp.asImageBitmap()
        }.getOrNull()
    }
    val accent = model.accent
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
            .background(Color(0xF2141218))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
            .swallowTaps()
            .padding(horizontal = 20.dp)
            .padding(top = 10.dp, bottom = 26.dp)
            .navigationBarsPadding(),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp).background(Color(0x55FFFFFF), RoundedCornerShape(50)))
        Spacer(Modifier.height(14.dp))
        Text("Wallpaper & style", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.W600)
        Spacer(Modifier.height(14.dp))

        // wallpaper + mini preview of the home screen
        Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.width(76.dp).fillMaxHeight().clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(lerp(accent, Color.Black, 0.4f), Color(0xFF0D0C12))))
                    .border(1.5.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            ) {
                if (wall != null) Image(wall, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                // tiny Morph layout on top
                val sh = tileShape(model.iconShape)
                Column(Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(29.dp).clip(sh).background(Brush.linearGradient(listOf(Color(0xFF6D47D1), Color(0xFFB83A5E)))))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.width(29.dp).height(12.5.dp).morphTile(sh, accent))
                            Box(Modifier.width(29.dp).height(12.5.dp).morphTile(sh, accent, 1))
                        }
                    }
                    repeat(3) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            repeat(4) { Box(Modifier.size(12.5.dp).clip(sh).background(Color.White.copy(alpha = 0.3f))) }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StyleButton("Change wallpaper", true, accent, Modifier.fillMaxWidth().weight(1f)) { onWallpaper() }
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    model.homeStyles.forEach { (id, name) ->
                        StyleButton(name, model.topStyle == id, accent, Modifier.weight(1f).fillMaxHeight()) {
                            if (model.topStyle != id) model.applyHomeStyle(id)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Shape · every tile, widget and icon", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.W600)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("squircle", "pebble", "rounded", "square", "circle").forEach { id ->
                val sel = model.iconShape == id
                Box(
                    Modifier.size(50.dp)
                        .border(2.dp, if (sel) Color.White else Color.Transparent, tileShape(id))
                        .padding(5.dp).clip(tileShape(id))
                        .background(if (sel) accent.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.16f))
                        .clickable { model.changeShape(id) }
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Tile color", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.W600)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            org.driftvane.launcher.Accents.forEachIndexed { i, c ->
                val sel = model.accentIndex == i
                Box(
                    Modifier.size(40.dp).border(2.dp, if (sel) Color.White else Color.Transparent, CircleShape)
                        .padding(4.dp).clip(CircleShape).background(Brush.linearGradient(listOf(c, lerp(c, Color.Black, 0.55f))))
                        .clickable { model.changeAccent(i) }
                )
            }
        }
    }
}

@Composable
private fun StyleButton(text: String, selected: Boolean, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier.clip(shape)
            .background(if (selected) lerp(accent, Color(0xFF14111C), 0.35f) else Color.White.copy(alpha = 0.08f))
            .border(1.5.dp, if (selected) Color.White else Color.White.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.W600) }
}
