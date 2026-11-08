package org.driftvane.launcher.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.driftvane.launcher.EMPTY
import org.driftvane.launcher.LauncherModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** DriftVane blue palette for the home styles (the accent wins if the user picks another). */
private val Ink = Color(0xFF0D1326)
private val Card = Color(0xFF1B2340)
private val Soft = Color(0xFF8EA3D6)

private fun batteryOf(context: Context): Pair<Int, Boolean> {
    val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    return (if (level < 0) 0 else level * 100 / scale) to
        (st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL)
}

/** Live time / battery / now playing, shared by all the style cards. */
private class Live(val time: String, val day: String, val battery: Int, val charging: Boolean,
                   val playing: NowPlaying?, val access: Boolean)

@Composable
private fun rememberLive(): Live {
    val context = LocalContext.current
    val now by produceState(Date()) {
        while (true) { value = Date(); delay((60 - Calendar.getInstance().get(Calendar.SECOND)) * 1000L) }
    }
    val battery by produceState(batteryOf(context)) { while (true) { value = batteryOf(context); delay(30_000) } }
    val playing by produceState<NowPlaying?>(null) { while (true) { value = nowPlaying(context); delay(1500) } }
    val access by produceState(hasMediaAccess(context)) { while (true) { value = hasMediaAccess(context); delay(3000) } }
    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    return Live(
        SimpleDateFormat(if (is24) "HH:mm" else "h:mm", Locale.getDefault()).format(now),
        SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(now),
        battery.first, battery.second, playing, access,
    )
}

// ---------------------------------------------------------------- cards
@Composable
private fun ClockCard(model: LauncherModel, live: Live, modifier: Modifier, big: Boolean = true) {
    Column(
        modifier.clip(RoundedCornerShape(26.dp)).background(if (model.stackColorful) Color(0xFF6C5CE7) else model.accent).padding(16.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Text(live.time, color = Color.White, fontSize = if (big) 52.sp else 34.sp, lineHeight = if (big) 54.sp else 36.sp,
            fontWeight = FontWeight.W500, maxLines = 1)
        Text(live.day, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, fontWeight = FontWeight.W500, maxLines = 1)
    }
}

@Composable
private fun MusicCard(model: LauncherModel, live: Live, modifier: Modifier, solid: Boolean = false) {
    val context = LocalContext.current
    val np = live.playing
    val art = np?.art
    val artImage = remember(art) { art?.asImageBitmap() }
    Box(
        modifier.clip(RoundedCornerShape(26.dp)).background(if (model.stackColorful) Color(0xFFE84393) else if (solid) model.accent else Card)
            .clickable(remember { MutableInteractionSource() }, indication = null) {
                if (np != null) openPlayer(context, np) else if (!hasMediaAccess(context)) openMediaAccessSettings(context)
            }
    ) {
        // album art behind the text, faded, with a dark wash so the song name stays bright
        if (artImage != null) {
            Image(artImage, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop, alpha = 0.45f)
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.28f)))
        }
        Column(Modifier.matchParentSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(if (np != null) "Now playing" else "Music",
                color = if (solid || artImage != null) Color.White.copy(alpha = 0.85f) else Soft,
                fontSize = 13.sp, fontWeight = FontWeight.W500)
            Column {
                Text(
                    when { np != null -> np.title; !live.access -> "Tap to show what's playing"; else -> "Nothing playing" },
                    color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.W700, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (np != null && np.artist.isNotBlank()) {
                    Text(np.artist, color = if (solid || artImage != null) Color.White.copy(alpha = 0.85f) else Soft,
                        fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun BatteryCard(model: LauncherModel, live: Live, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(26.dp)).background(if (model.stackColorful) Color(0xFF00B894) else Card).padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(if (live.charging) "Charging" else "Battery", color = Soft, fontSize = 13.sp, fontWeight = FontWeight.W500)
        Text("${live.battery}%", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.W600)
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.15f))) {
            Box(Modifier.fillMaxWidth(live.battery / 100f).fillMaxHeight().clip(RoundedCornerShape(3.dp))
                .background(if (live.battery <= 15 && !live.charging) Color(0xFFFF6B6B) else Color(0xFF7FA6FF)))
        }
    }
}

// ---------------------------------------------------------------- H4 Frame
/**
 * Frame: the home screen is a solid dark page, and this card is a window cut into it
 * that shows your real wallpaper, with the time in the corner.
 * [onHole] reports the window's position so the page can cut it out of its background.
 */
@Composable
fun FrameTop(model: LauncherModel, onHole: (Rect) -> Unit) {
    val live = rememberLive()
    Box(
        Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 4.dp)
            .onGloballyPositioned { onHole(it.boundsInRoot()) }
            .clip(RoundedCornerShape(28.dp))
    ) {
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
            Text(live.time, color = Color.White, fontSize = 56.sp, lineHeight = 58.sp, fontWeight = FontWeight.W500)
            Text("${live.day} · ${live.battery}%", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp,
                fontWeight = FontWeight.W500)
        }
    }
}

/** Solid page color behind the Frame style. */
val FrameInk = Ink.copy(alpha = 0.97f)

// ---------------------------------------------------------------- H8 Split
/** Split: a swipeable row of cards (clock, now playing, battery) with dots. */
@Composable
fun SplitTop(model: LauncherModel) {
    val live = rememberLive()
    val pager = rememberPagerState { 3 }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 4.dp)) {
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), pageSpacing = 10.dp) { p ->
            val m = Modifier.fillMaxSize()
            when (p) {
                0 -> ClockCard(model, live, m)
                1 -> MusicCard(model, live, m, solid = true)
                else -> BatteryCard(model, live, m)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            repeat(3) { i ->
                Box(
                    Modifier.padding(horizontal = 3.dp).height(5.dp).width(if (i == pager.currentPage) 16.dp else 5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (i == pager.currentPage) model.accent else Color.White.copy(alpha = 0.3f))
                )
            }
        }
    }
}

// ---------------------------------------------------------------- H10 Stack
/** Stack: a deck of cards. Flick the top card up/down or sideways to send it to the back. */
@Composable
fun StackTop(model: LauncherModel) {
    val live = rememberLive()
    var order by remember { mutableStateOf(listOf(1, 0, 2)) }
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Offset.VectorConverter) }
    Box(Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 6.dp)) {
        order.reversed().forEachIndexed { i, card ->
            val depth = order.size - 1 - i // 0 = top
            val top = depth == 0
            Box(
                Modifier.fillMaxSize()
                    .zIndex(-depth.toFloat())
                    .graphicsLayer {
                        val inset = depth * 0.05f
                        scaleX = 1f - inset; scaleY = 1f - inset
                        translationY = -depth * 9.dp.toPx() + if (top) drag.value.y else 0f
                        translationX = if (top) drag.value.x else 0f
                        rotationZ = if (top) drag.value.x / 40f else 0f
                    }
                    .then(
                        if (top) Modifier.pointerInput(order) {
                            detectDragGestures(
                                onDrag = { c, d -> c.consume(); scope.launch { drag.snapTo(drag.value + d) } },
                                onDragEnd = {
                                    val o = drag.value
                                    if (abs(o.x) > 120f || abs(o.y) > 90f) {
                                        // send the top card to the back
                                        order = order.drop(1) + order.first()
                                        scope.launch { drag.snapTo(androidx.compose.ui.geometry.Offset.Zero) }
                                    } else {
                                        scope.launch { drag.animateTo(androidx.compose.ui.geometry.Offset.Zero, spring(0.7f)) }
                                    }
                                },
                            )
                        } else Modifier
                    )
            ) {
                val m = Modifier.fillMaxSize()
                when (card) {
                    0 -> ClockCard(model, live, m)
                    1 -> MusicCard(model, live, m, solid = true)
                    else -> BatteryCard(model, live, m)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- H11 Columns
/**
 * Columns: info cards on the left, your first-page apps as a labeled list on the right.
 * Tap opens, hold for the menu. (Rearrange on the other pages or with Edit home screen.)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ColumnsPage(model: LauncherModel, items: List<String>, handler: ItemHandler) {
    val live = rememberLive()
    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1.05f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ClockCard(model, live, Modifier.fillMaxWidth().weight(1.2f), big = false)
            MusicCard(model, live, Modifier.fillMaxWidth().weight(1f))
            BatteryCard(model, live, Modifier.fillMaxWidth().weight(1f))
        }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items.filter { it != EMPTY && (model.apps[it] != null || model.folders[it] != null) }.forEach { itemKey ->
              key(itemKey) {
                val name = model.apps[itemKey]?.label ?: model.folders[itemKey]?.name ?: ""
                val bounds = remember { arrayOf(Rect.Zero) }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .combinedClickable(
                            onClick = { handler.tap(itemKey, bounds[0]) },
                            onLongClick = { handler.menu(itemKey, bounds[0]) },
                        )
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HomeItem(model, itemKey, 44f, false, onBounds = { bounds[0] = it })
                    Spacer(Modifier.width(10.dp))
                    Text(name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.W500,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
              }
            }
        }
    }
}

/** DriftVaneOS Stock style: a simple "At A Glance" header; the grid below carries app names. */
@Composable
fun StockTop(model: LauncherModel) {
    Box(Modifier.fillMaxSize().padding(start = 20.dp, top = 12.dp)) {
        Column {
            Text(
                SimpleDateFormat("EEEE", Locale.getDefault()).format(Date()),
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.W600,
            )
            Text(
                SimpleDateFormat("MMMM d", Locale.getDefault()).format(Date()),
                color = Soft, fontSize = 14.sp,
            )
        }
    }
}
