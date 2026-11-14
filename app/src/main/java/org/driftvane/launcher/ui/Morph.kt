package org.driftvane.launcher.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.composed
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.withStyle
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
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
    /** Not a tile: puts the search bar back at the bottom. */
    const val SEARCH = -9
    val all = listOf(MUSIC to "Now playing", CLOCK to "Clock", DATE to "Date & alarm", BATTERY to "Battery")
    /** Default size (spanX to spanY). */
    fun span(id: Int): Pair<Int, Int> = when (id) { MUSIC -> 2 to 2; BATTERY -> 4 to 1; else -> 2 to 1 }
}

/** Home styles that use the Morph look (uniform tiles, outlined search bar). */
fun isMorphLike(style: String) = style == "morph" || style == "orbit" || style == "bloom"

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
    val t = context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime
    if (t == null) {
        // some clock apps only publish the old-style text
        @Suppress("DEPRECATION")
        return@runCatching android.provider.Settings.System.getString(
            context.contentResolver, android.provider.Settings.System.NEXT_ALARM_FORMATTED
        )?.takeIf { it.isNotBlank() }
    }
    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    val time = SimpleDateFormat(if (is24) "HH:mm" else "h:mm a", Locale.getDefault()).format(Date(t))
    val cal = java.util.Calendar.getInstance()
    val today = cal.get(java.util.Calendar.DAY_OF_YEAR) to cal.get(java.util.Calendar.YEAR)
    cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
    val tomorrow = cal.get(java.util.Calendar.DAY_OF_YEAR) to cal.get(java.util.Calendar.YEAR)
    cal.timeInMillis = t
    val day = cal.get(java.util.Calendar.DAY_OF_YEAR) to cal.get(java.util.Calendar.YEAR)
    when (day) {
        today -> time
        tomorrow -> "Tomorrow $time"
        else -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(t)) + " " + time
    }
}.getOrNull()

/** The next alarm, kept fresh: updates the moment an alarm is set or cleared, and on return home. */
@Composable
private fun rememberNextAlarm(context: Context): String? {
    var alarm by remember { mutableStateOf(nextAlarm(context)) }
    DisposableEffect(Unit) {
        val rx = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { alarm = nextAlarm(context) }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        runCatching { context.registerReceiver(rx, filter, Context.RECEIVER_EXPORTED) }
        onDispose { runCatching { context.unregisterReceiver(rx) } }
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) alarm = nextAlarm(context)
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(Unit) { while (true) { delay(30_000); alarm = nextAlarm(context) } }
    return alarm
}

private fun openClockApp(context: Context) {
    runCatching { context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Draws a built-in tile (called by the grid for widgets with a negative id). */
@Composable
fun DriftTile(model: LauncherModel, w: WidgetItem, modifier: Modifier) {
    if (model.topStyle == "bloom") { BloomTile(model, w, modifier); return }
    val live = rememberLive()
    val context = LocalContext.current
    val shape = tileShape(model.iconShape)
    val tall = w.spanY >= 2
    when (w.id) {
        DriftTiles.MUSIC -> {
            // follows the player even when it's paused, so you can always resume it
            var tick by remember { mutableStateOf(0) }
            val media by produceState<Pair<NowPlaying, Boolean>?>(null, tick) {
                while (true) { value = mediaNow(context); delay(800) }
            }
            val np = media?.first
            val playing = media?.second == true
            val art = remember(np?.art) { np?.art?.asImageBitmap() }
            val ctl = np?.controller?.transportControls
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
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp)) {
                    Text(
                        when { np != null -> np.title; !live.access -> "Tap to show music"; else -> "Nothing playing" },
                        color = Color.White, fontSize = if (tall) 16.sp else 14.sp, fontWeight = FontWeight.W700,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (np != null && np.artist.isNotBlank()) {
                        Text(np.artist, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (np != null && tall) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TileButton(30.dp, false, { runCatching { ctl?.skipToPrevious() }; tick++ }) { skipGlyph(it, false) }
                            TileButton(38.dp, true, {
                                runCatching { if (playing) ctl?.pause() else ctl?.play() }
                                tick++
                            }) { if (playing) pauseGlyph(it) else playGlyph(it) }
                            TileButton(30.dp, false, { runCatching { ctl?.skipToNext() }; tick++ }) { skipGlyph(it, true) }
                        }
                    }
                }
                if (np != null && !tall) {
                    TileButton(34.dp, true, {
                        runCatching { if (playing) ctl?.pause() else ctl?.play() }
                        tick++
                    }, Modifier.align(Alignment.TopEnd).padding(10.dp)) { if (playing) pauseGlyph(it) else playGlyph(it) }
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
            val alarm = rememberNextAlarm(context)
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

/** A round play / pause / skip button on the music tile; squishes when pressed. */
@Composable
private fun TileButton(
    size: androidx.compose.ui.unit.Dp,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.84f else 1f, spring(0.45f, 600f), label = "tb")
    val fg = if (filled) Color(0xFF21005D) else Color.White
    Box(
        modifier.size(size).graphicsLayer { scaleX = sc; scaleY = sc }.clip(CircleShape)
            .background(if (filled) Color.White.copy(alpha = 0.92f) else Color(0x40000000))
            .clickable(src, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Canvas(Modifier.size(size * 0.36f)) { glyph(fg) } }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.playGlyph(c: Color) {
    val w = size.width; val h = size.height
    drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(w * 0.18f, 0f); lineTo(w, h / 2); lineTo(w * 0.18f, h); close() }, c)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.pauseGlyph(c: Color) {
    val w = size.width; val h = size.height
    drawRect(c, Offset(w * 0.12f, 0f), androidx.compose.ui.geometry.Size(w * 0.28f, h))
    drawRect(c, Offset(w * 0.6f, 0f), androidx.compose.ui.geometry.Size(w * 0.28f, h))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.skipGlyph(c: Color, forward: Boolean) {
    val w = size.width; val h = size.height
    val p = androidx.compose.ui.graphics.Path()
    if (forward) { p.moveTo(0f, 0f); p.lineTo(w * 0.75f, h / 2); p.lineTo(0f, h); p.close(); drawRect(c, Offset(w * 0.8f, 0f), androidx.compose.ui.geometry.Size(w * 0.2f, h)) }
    else { p.moveTo(w, 0f); p.lineTo(w * 0.25f, h / 2); p.lineTo(w, h); p.close(); drawRect(c, Offset.Zero, androidx.compose.ui.geometry.Size(w * 0.2f, h)) }
    drawPath(p, c)
}

/**
 * The Morph search bar: wide, glassy, with a bold white outline. Turns into page dots while
 * you swipe between pages.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MorphSearchBar(model: LauncherModel, count: Int, current: Int, swiping: Boolean, onSearch: () -> Unit) {
    var showDots by remember { mutableStateOf(false) }
    LaunchedEffect(swiping, current) { if (swiping) showDots = true else { delay(900); showDots = false } }
    // bitDroid Bloom: the bar is a scalloped pill in Material You tones, the search badge a
    // turning cookie and the mic a clover, in every home style
    val shape = remember { BloomPillShape() }
    val bloom = true
    val tones = bloomTones()
    val tr = rememberInfiniteTransition(label = "bar")
    val drift by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    // hold the bar: it lifts and a Remove button pops up over it (like removing a widget)
    var lifted by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    LaunchedEffect(lifted) { if (lifted) { delay(4000); lifted = false } }
    val squish by animateFloatAsState(
        when { removing -> 0f; pressed -> 0.96f; lifted -> 1.04f; else -> 1f },
        if (removing) tween(260) else spring(0.45f, 600f), label = "sq",
        finishedListener = { if (removing) model.changeSearchPill(false) },
    )
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(Modifier.fillMaxWidth()) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(56.dp)
            .graphicsLayer { scaleX = squish; scaleY = squish; alpha = squish.coerceIn(0f, 1f) }
            .clip(shape)
            .drawBehind {
                if (bloom) {
                    drawRect(tones.p.copy(alpha = 0.92f))
                } else {
                    // a slow moving Glow wash
                    drawRect(Color(0xCC0B0A10))
                    val w = size.width
                    drawCircle(Brush.radialGradient(listOf(Color(0xCC5B3FC8), Color.Transparent),
                        Offset(w * (0.1f + 0.5f * drift), 0f), w * 0.45f), w * 0.45f, Offset(w * (0.1f + 0.5f * drift), 0f))
                    drawCircle(Brush.radialGradient(listOf(Color(0xCC0F5C63), Color.Transparent),
                        Offset(w * (0.95f - 0.4f * drift), size.height), w * 0.4f), w * 0.4f, Offset(w * (0.95f - 0.4f * drift), size.height))
                    drawCircle(Brush.radialGradient(listOf(Color(0x995A2346), Color.Transparent),
                        Offset(w * 0.55f, size.height * 0.5f), w * 0.3f), w * 0.3f, Offset(w * 0.55f, size.height * 0.5f))
                }
            }
            .border(1.2.dp, if (bloom) tones.accent.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.55f), shape)
            .combinedClickable(
                interactionSource = src, indication = null,
                onClick = { if (lifted) lifted = false else onSearch() },
                onLongClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    lifted = true
                },
            )
            .padding(start = 8.dp, end = 8.dp),
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
                val ink = if (bloom) tones.onP else Color.White
                val badge = if (bloom) tones.accent else Color(0xFFEADDFF)
                val onBadge = if (bloom) tones.p else Color(0xFF21005D)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // round search badge that gives a little wiggle now and then
                    val spin by tr.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "spin")
                    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.matchParentSize().graphicsLayer { rotationZ = spin }
                            .clip(remember { BloomCookieShape(9) }).background(badge))
                        Canvas(Modifier.size(18.dp)) {
                            val r = size.minDimension * 0.33f
                            val c = Offset(size.width * 0.42f, size.height * 0.42f)
                            drawCircle(onBadge, r, c, style = Stroke(size.minDimension * 0.15f))
                            drawLine(onBadge, c + Offset(r * 0.72f, r * 0.72f), Offset(size.width * 0.92f, size.height * 0.92f),
                                size.minDimension * 0.16f, StrokeCap.Round)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    // the hint changes every few seconds
                    val hints = remember { listOf("Search", "Find an app", "Look up anything", "Jump to a setting") }
                    var hint by remember { mutableStateOf(0) }
                    LaunchedEffect(Unit) { while (true) { delay(3200); hint = (hint + 1) % hints.size } }
                    AnimatedContent(
                        hint,
                        transitionSpec = {
                            (fadeIn(tween(260)) + androidx.compose.animation.slideInVertically(spring(0.75f, 400f)) { it }) togetherWith
                                (fadeOut(tween(160)) + androidx.compose.animation.slideOutVertically(tween(200)) { -it })
                        },
                        label = "hint",
                        modifier = Modifier.weight(1f),
                    ) { h ->
                        Text(hints[h], color = ink, fontSize = 16.sp, fontWeight = FontWeight.W600, maxLines = 1)
                    }
                    // mic in its own little bubble
                    Box(Modifier.size(40.dp).clip(remember { BloomCookieShape(4, 0.2f) }).background(ink.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(16.dp)) {
                            val w = size.width
                            drawRoundRect(ink, topLeft = Offset(w * 0.34f, 0f),
                                size = androidx.compose.ui.geometry.Size(w * 0.32f, size.height * 0.62f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.16f))
                            drawArc(ink, 0f, 180f, false, topLeft = Offset(w * 0.18f, size.height * 0.22f),
                                size = androidx.compose.ui.geometry.Size(w * 0.64f, size.height * 0.56f), style = Stroke(w * 0.1f))
                            drawLine(ink, Offset(w / 2, size.height * 0.78f), Offset(w / 2, size.height), w * 0.1f)
                        }
                    }
                }
            }
        }
    }
    // the Remove button that pops out of the bar
    androidx.compose.animation.AnimatedVisibility(
        lifted && !removing,
        modifier = Modifier.align(Alignment.Center),
        enter = fadeIn(tween(120)) + scaleIn(spring(0.5f, 500f), initialScale = 0.5f),
        exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.6f),
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFFFB4AB))
                .clickable {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    removing = true
                }
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("✕", color = Color(0xFF690005), fontSize = 14.sp, fontWeight = FontWeight.W800)
            Spacer(Modifier.width(8.dp))
            Text("Remove search bar", color = Color(0xFF690005), fontSize = 14.sp, fontWeight = FontWeight.W700)
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
    val alarm = rememberNextAlarm(context)
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

// ------------------------------------------------------------------------------------- Bloom

/** Material You colors pulled from the wallpaper (dark tones, so white-ish text works). */
data class BloomTones(
    val p: Color, val onP: Color,
    val s: Color, val onS: Color,
    val t: Color, val onT: Color,
    val accent: Color,
)

@Composable
fun bloomTones(): BloomTones {
    val context = LocalContext.current
    val conf = androidx.compose.ui.platform.LocalConfiguration.current
    return remember(conf) {
        fun g(id: Int, fallback: Long) = runCatching { Color(context.getColor(id)) }.getOrElse { Color(fallback) }
        BloomTones(
            p = g(android.R.color.system_accent1_700, 0xFF4F378B), onP = g(android.R.color.system_accent1_100, 0xFFEADDFF),
            s = g(android.R.color.system_accent2_700, 0xFF4A4458), onS = g(android.R.color.system_accent2_100, 0xFFE8DEF8),
            t = g(android.R.color.system_accent3_700, 0xFF633B48), onT = g(android.R.color.system_accent3_100, 0xFFFFD8E4),
            accent = g(android.R.color.system_accent1_200, 0xFFD0BCFF),
        )
    }
}

/** The Material 3 Expressive scalloped "cookie". */
class CookieShape(private val lobes: Int = 9, private val depth: Float = 0.07f) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline {
        val p = androidx.compose.ui.graphics.Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r0 = minOf(cx, cy)
        val steps = 180
        for (i in 0..steps) {
            val th = 2.0 * Math.PI * i / steps
            val r = r0 * (1f - depth + depth * kotlin.math.cos(lobes * th).toFloat())
            val x = cx + r * kotlin.math.cos(th).toFloat()
            val y = cy + r * kotlin.math.sin(th).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return androidx.compose.ui.graphics.Outline.Generic(p)
    }
}

/** Bloom: big solid Material You tiles, a giant clock and spinning cookies. */
@Composable
private fun BloomTile(model: LauncherModel, w: WidgetItem, modifier: Modifier) {
    val live = rememberLive()
    val context = LocalContext.current
    val shape = tileShape(model.iconShape)
    val tones = bloomTones()
    val tr = rememberInfiniteTransition(label = "bloom")
    val spin by tr.animateFloat(0f, 360f, infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "spin")
    // tiles pop in one after another
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(70L * (-w.id).coerceIn(0, 6))
        enter.animateTo(1f, spring(0.55f, 380f))
    }
    val pop = Modifier.graphicsLayer {
        val e = enter.value
        scaleX = 0.8f + 0.2f * e; scaleY = 0.8f + 0.2f * e; alpha = e.coerceIn(0f, 1f)
    }
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sq by animateFloatAsState(if (pressed) 0.95f else 1f, spring(0.45f, 600f), label = "sq")
    val press = Modifier.graphicsLayer { scaleX = sq; scaleY = sq }
    when (w.id) {
        DriftTiles.CLOCK -> Box(
            modifier.then(pop).then(press).clip(shape).background(tones.p)
                .clickable(src, indication = null) { openClockApp(context) },
        ) {
            val big = w.spanY >= 2
            // giant cookie with today's date, slowly turning
            Box(
                Modifier.align(Alignment.CenterEnd).offset(x = if (big) 18.dp else 24.dp)
                    .size(if (big) 150.dp else 84.dp).graphicsLayer { rotationZ = spin }
                    .clip(CookieShape()).background(tones.accent),
            )
            Column(Modifier.align(Alignment.CenterEnd).padding(end = if (big) 30.dp else 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(SimpleDateFormat("d", Locale.getDefault()).format(Date()), color = tones.p,
                    fontSize = if (big) 44.sp else 24.sp, fontWeight = FontWeight.W800, lineHeight = if (big) 44.sp else 24.sp)
                Text(SimpleDateFormat("MMM", Locale.getDefault()).format(Date()).uppercase(), color = tones.p,
                    fontSize = if (big) 13.sp else 10.sp, fontWeight = FontWeight.W700, letterSpacing = 2.sp)
            }
            Column(Modifier.align(Alignment.CenterStart).padding(start = 20.dp)) {
                val parts = live.time.split(":")
                Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.W300)) { append(parts.getOrElse(0) { "" }) }
                        withStyle(androidx.compose.ui.text.SpanStyle(color = tones.accent, fontWeight = FontWeight.W300)) { append(":") }
                        withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.W800)) { append(parts.getOrElse(1) { "" }) }
                    },
                    color = tones.onP, fontSize = if (big) 76.sp else 38.sp, lineHeight = if (big) 78.sp else 40.sp, maxLines = 1,
                )
                Text(SimpleDateFormat("EEEE", Locale.getDefault()).format(Date()), color = tones.onP.copy(alpha = 0.75f),
                    fontSize = if (big) 16.sp else 11.sp, fontWeight = FontWeight.W600)
            }
        }
        DriftTiles.DATE -> {
            val alarm = rememberNextAlarm(context)
            Row(
                modifier.then(pop).then(press).clip(shape).background(tones.accent)
                    .clickable(src, indication = null) { openClockApp(context) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(34.dp).graphicsLayer { rotationZ = -spin * 2 }.clip(CookieShape(6, 0.1f)).background(tones.p),
                    contentAlignment = Alignment.Center) {
                    Text("⏰", fontSize = 14.sp, modifier = Modifier.graphicsLayer { rotationZ = spin * 2 })
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(if (alarm != null) "Alarm" else "No alarms", color = tones.p.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.W700)
                    Text(alarm ?: "Sleep in", color = tones.p, fontSize = 18.sp, fontWeight = FontWeight.W800, maxLines = 1)
                }
            }
        }
        DriftTiles.MUSIC -> {
            var tick by remember { mutableStateOf(0) }
            val media by produceState<Pair<NowPlaying, Boolean>?>(null, tick) {
                while (true) { value = mediaNow(context); delay(800) }
            }
            val np = media?.first
            val playing = media?.second == true
            val art = remember(np?.art) { np?.art?.asImageBitmap() }
            val ctl = np?.controller?.transportControls
            val turn = remember { Animatable(0f) }
            LaunchedEffect(playing) {
                while (playing) { turn.animateTo(turn.value + 360f, tween(8000, easing = LinearEasing)) }
            }
            Box(
                modifier.then(pop).then(press).clip(shape).background(tones.s)
                    .clickable(src, indication = null) {
                        if (np != null) openPlayer(context, np) else if (!live.access) openMediaAccessSettings(context)
                    }
                    .padding(14.dp),
            ) {
                Box(Modifier.align(Alignment.TopStart).size(if (w.spanY >= 2) 74.dp else 40.dp)
                    .graphicsLayer { rotationZ = turn.value }.clip(CookieShape()).background(tones.t),
                    contentAlignment = Alignment.Center) {
                    if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Text("♪", color = tones.onT, fontSize = 24.sp)
                }
                if (np != null) {
                    Box(
                        Modifier.align(Alignment.TopEnd).size(44.dp).bloomBounce {
                            runCatching { if (playing) ctl?.pause() else ctl?.play() }; tick++
                        }.clip(RoundedCornerShape(if (playing) 14.dp else 22.dp)).background(tones.onS),
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(Modifier.size(18.dp)) { if (playing) pauseGlyph(tones.s) else playGlyph(tones.s) }
                    }
                }
                Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                    Text(
                        when { np != null -> np.title; !live.access -> "Tap to show music"; else -> "Nothing playing" },
                        color = tones.onS, fontSize = 16.sp, fontWeight = FontWeight.W800, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (np != null && np.artist.isNotBlank()) {
                        Text(np.artist, color = tones.onS.copy(alpha = 0.75f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        else -> { // battery: a wavy Material 3 Expressive progress line
            val low = live.battery <= 15 && !live.charging
            val phase by tr.animateFloat(0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(if (live.charging) 900 else 2600, easing = LinearEasing)), label = "wave")
            val lvl by animateFloatAsState(live.battery / 100f, spring(0.7f, 120f), label = "lvl")
            Row(
                modifier.then(pop).clip(shape).background(if (low) Color(0xFF8C1D18) else tones.t).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (live.charging) "⚡" else "${live.battery}", color = tones.onT, fontSize = 18.sp, fontWeight = FontWeight.W800)
                Spacer(Modifier.width(12.dp))
                Canvas(Modifier.weight(1f).height(18.dp)) {
                    val mid = size.height / 2
                    val end = size.width * lvl
                    val amp = size.height * 0.28f
                    val path = androidx.compose.ui.graphics.Path()
                    var x = 0f
                    path.moveTo(0f, mid)
                    while (x <= end) {
                        path.lineTo(x, mid + amp * kotlin.math.sin(x / (size.height * 0.9f) + phase))
                        x += 2f
                    }
                    drawPath(path, tones.onT, style = Stroke(size.height * 0.22f, cap = StrokeCap.Round))
                    if (end < size.width - 8f) {
                        drawLine(tones.onT.copy(alpha = 0.3f), Offset(end + 10f, mid), Offset(size.width, mid),
                            size.height * 0.22f, StrokeCap.Round)
                    }
                }
                if (live.charging) {
                    Spacer(Modifier.width(10.dp))
                    Text("${live.battery}%", color = tones.onT, fontSize = 14.sp, fontWeight = FontWeight.W700)
                }
            }
        }
    }
}

/** Squish on press, spring back with a bounce. */
private fun Modifier.bloomBounce(onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.82f else 1f, spring(0.4f, 600f), label = "bb")
    this.graphicsLayer { scaleX = s; scaleY = s }.clickable(src, indication = null, onClick = onClick)
}
