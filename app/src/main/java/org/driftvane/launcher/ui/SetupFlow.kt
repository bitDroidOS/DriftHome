package org.driftvane.launcher.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.driftvane.launcher.LauncherModel
import kotlin.math.cos
import kotlin.math.sin

/** The ROM's name, from the build (ro.driftvane.name), so a rename never needs a DriftHome change. */
fun romName(): String = runCatching {
    val c = Class.forName("android.os.SystemProperties")
    (c.getMethod("get", String::class.java, String::class.java).invoke(null, "ro.driftvane.name", "bitDroid") as String)
        .ifBlank { "bitDroid" }
}.getOrDefault("bitDroid")

private val Ink = Color(0xFF0B0A10)
private val Muted = Color(0xFFB4ADC2)

/**
 * First-boot setup: Welcome → pick a home style (Bloom, Morph or Orbit) → tips.
 * Shown once after the phone's setup, and again from Home settings › Run setup again.
 */
@Composable
fun SetupFlow(model: LauncherModel) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var homeStyle by remember { mutableStateOf(model.devFavStyle) }
    var shape by remember { mutableStateOf(model.iconShape.takeIf { it != "rounded" } ?: "squircle") }
    val last = 2
    BackHandler(enabled = step > 0) { step-- }
    val accent = model.accent

    // slow drifting color behind everything
    val drift = rememberInfiniteTransition(label = "bg")
    val t by drift.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "t")

    Box(
        Modifier.fillMaxSize().background(Ink).swallowTaps()
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            drawCircle(Brush.radialGradient(listOf(lerp(accent, Color(0xFF6D47D1), 0.5f).copy(alpha = 0.55f), Color.Transparent),
                center = Offset(w * (0.15f + 0.2f * t), h * 0.12f), radius = w * 0.9f), w * 0.9f, Offset(w * (0.15f + 0.2f * t), h * 0.12f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFF0F5C63).copy(alpha = 0.6f), Color.Transparent),
                center = Offset(w * (0.9f - 0.15f * t), h * 0.88f), radius = w * 0.8f), w * 0.8f, Offset(w * (0.9f - 0.15f * t), h * 0.88f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFF5A2346).copy(alpha = 0.45f), Color.Transparent),
                center = Offset(w * 0.75f, h * (0.4f + 0.1f * t)), radius = w * 0.6f), w * 0.6f, Offset(w * 0.75f, h * (0.4f + 0.1f * t)))
        }
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 18.dp)
        ) {
            // progress: one pill per step
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(last + 1) { i ->
                    val w by animateFloatAsState(if (i == step) 26f else 8f, spring(0.7f, 400f), label = "p")
                    Box(Modifier.height(8.dp).width(w.dp).clip(RoundedCornerShape(50))
                        .background(if (i <= step) Color.White else Color.White.copy(alpha = 0.25f)))
                }
            }
            Spacer(Modifier.height(26.dp))
            AnimatedContent(
                step,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (fadeIn(tween(260)) + slideInHorizontally(spring(0.85f, 380f)) { it / 4 * dir }) togetherWith
                        (fadeOut(tween(160)) + slideOutHorizontally(tween(200)) { -it / 5 * dir })
                },
                modifier = Modifier.fillMaxWidth().weight(1f),
                label = "step",
            ) { s ->
                Column(Modifier.fillMaxSize()) {
                    when (s) {
                        0 -> Welcome()
                        1 -> {
                            Title("Pick your home", "You can switch any time from Wallpaper & style.")
                            Spacer(Modifier.height(22.dp))
                            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                StyleCard("Bloom", "Big, round, colorful", homeStyle == "bloom", model.devFavStyle == "bloom",
                                    Modifier.weight(1f).fillMaxHeight(), onClick = { homeStyle = "bloom" }) { BloomPreview(shape) }
                                StyleCard("Morph", "Tiles and widgets", homeStyle == "morph", model.devFavStyle == "morph",
                                    Modifier.weight(1f).fillMaxHeight(), onClick = { homeStyle = "morph" }) { MorphPreview(shape, accent) }
                                StyleCard("Orbit", "Apps in a ring", homeStyle == "orbit", model.devFavStyle == "orbit",
                                    Modifier.weight(1f).fillMaxHeight(), onClick = { homeStyle = "orbit" }) { OrbitPreview(shape) }
                            }
                            Spacer(Modifier.height(10.dp))
                            // bitDroid: Liquid, the glass look (the default)
                            StyleCard("Liquid", "Frosted glass, glossy icons", homeStyle == "liquid", model.devFavStyle == "liquid",
                                Modifier.fillMaxWidth().height(96.dp), onClick = { homeStyle = "liquid" }) { LiquidPreview() }
                            Spacer(Modifier.height(10.dp))
                            // bitDroid: Classic, the plain stock look (no Material You)
                            StyleCard("Classic", "Plain stock look", homeStyle == "stock", false,
                                Modifier.fillMaxWidth().height(96.dp), onClick = { homeStyle = "stock" }) { ClassicPreview() }
                        }
                        else -> {
                            Title("Make it yours", "Here's where everything lives.")
                            Spacer(Modifier.height(18.dp))
                            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                // bitDroid: one tap to the system color picker, with Vibrant recommended
                                ActionCard(
                                    "Colors: Vibrant (recommended)",
                                    "bitDroid looks best with the Vibrant color style. Tap to open Wallpaper & style, then Color.",
                                ) {
                                    val opened = runCatching {
                                        context.startActivity(
                                            android.content.Intent("com.android.customization.action.SET_WALLPAPER_COLORS")
                                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }.isSuccess || runCatching {
                                        context.startActivity(
                                            android.content.Intent(android.content.Intent.ACTION_SET_WALLPAPER)
                                                .setPackage("com.android.wallpaper")
                                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }.isSuccess
                                    if (!opened) runCatching {
                                        context.startActivity(
                                            android.content.Intent(android.content.Intent.ACTION_SET_WALLPAPER)
                                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        )
                                    }
                                }
                                TipCard("Change your look", "Hold an empty spot on the home screen, then Wallpaper & style. Switch between Bloom, Morph and Orbit, or change your wallpaper and icon shape.")
                                TipCard("Move or remove anything", "Hold a tile, a widget or the search bar to drag it around or drop it on Remove.")
                                TipCard("Your apps", "Swipe up for all your apps. Swipe down for notifications.")
                                ActionCard("${romName()} Enhancements", "Lock screen, glance bar, Quick Settings and more. It's the first thing in Settings.") {
                                    runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                }
                                ActionCard("Show what's playing", "Let the music tile see what's playing.") { openMediaAccessSettings(context) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) {
                    Text("Back", color = Muted, fontSize = 16.sp, fontWeight = FontWeight.W600,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { step-- }.padding(14.dp))
                }
                Spacer(Modifier.weight(1f))
                OutlinePill(if (step == 0) "Let's go" else if (step == last) "Done" else "Next", filled = step == last) {
                    if (step < last) step++ else model.finishSetup(homeStyle, shape)
                }
            }
        }
    }
}

@Composable
private fun Welcome() {
    // bitDroid "Bloom line" welcome: a turning cookie logo, a stream of bits that blooms into a
    // cookie and bursts into petals (on a loop), and Hello in different languages
    val hellos = remember { listOf("Hello", "Hola", "你好", "Bonjour", "Ciao", "こんにちは", "Hallo", "Olá", "안녕하세요", "Привет", "مرحبا", "Hej") }
    var hi by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1800); hi = (hi + 1) % hellos.size } }
    val tr = androidx.compose.animation.core.rememberInfiniteTransition(label = "bloomline")
    val loop by tr.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(
        androidx.compose.animation.core.tween(3600, easing = androidx.compose.animation.core.LinearEasing)), label = "loop")
    val spin by tr.animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(
        androidx.compose.animation.core.tween(16000, easing = androidx.compose.animation.core.LinearEasing)), label = "spin")
    val lilac = Color(0xFFD0BCFF)
    val deep = Color(0xFF7F67BE)
    val petal = Color(0xFFEFB8C8)
    fun cookie(cx: Float, cy: Float, r: Float, lobes: Int, depth: Float, turn: Float) =
        androidx.compose.ui.graphics.Path().apply {
            val steps = lobes * 20
            for (k in 0..steps) {
                val t = k.toFloat() / steps * 2f * Math.PI.toFloat()
                val rr = r * (1f + depth * kotlin.math.cos(lobes * t)) / (1f + depth)
                val x = cx + rr * kotlin.math.cos(t + turn)
                val y = cy + rr * kotlin.math.sin(t + turn)
                if (k == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    Box(Modifier.fillMaxSize()) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            // faint dot grid
            val gap = 18.dp.toPx()
            var gx = gap / 2
            while (gx < size.width) {
                var gy = gap / 2
                while (gy < size.height) { drawCircle(lilac.copy(alpha = 0.07f), 1.dp.toPx(), androidx.compose.ui.geometry.Offset(gx, gy)); gy += gap }
                gx += gap
            }
            // the logo cookie, turning
            val lc = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.17f)
            val rad = Math.toRadians(spin.toDouble()).toFloat()
            drawCircle(lilac.copy(alpha = 0.16f), 80.dp.toPx(), lc)
            drawPath(cookie(lc.x, lc.y, 52.dp.toPx(), 12, 0.08f, rad), deep)
            drawPath(cookie(lc.x, lc.y, 34.dp.toPx(), 8, 0.1f, -rad * 1.3f), lilac)
            // the bloom line
            val ly = size.height * 0.48f
            drawLine(lilac.copy(alpha = 0.12f), androidx.compose.ui.geometry.Offset(0f, ly), androidx.compose.ui.geometry.Offset(size.width, ly), 1.dp.toPx())
            val cx = size.width / 2f
            val run = (loop / 0.45f).coerceAtMost(1f)          // 0..0.45: bits race to the middle
            val bloom = ((loop - 0.4f) / 0.3f).coerceIn(0f, 1f) // 0.4..0.7: the cookie blooms
            val fade = ((loop - 0.85f) / 0.15f).coerceIn(0f, 1f)
            val head = cx * run
            val step = 12.dp.toPx()
            var x = step / 2
            while (x < head) {
                val a = (1f - (head - x) / (size.width * 0.5f)).coerceIn(0.12f, 1f) * (1f - bloom)
                drawRoundRect(lilac.copy(alpha = a), androidx.compose.ui.geometry.Offset(x - 2.dp.toPx(), ly - 2.dp.toPx()),
                    androidx.compose.ui.geometry.Size(4.dp.toPx(), 4.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
                x += step
            }
            if (run < 1f) drawCircle(Color.White, 5.dp.toPx(), androidx.compose.ui.geometry.Offset(head, ly))
            if (bloom > 0f) {
                val e = 1f - (1f - bloom) * (1f - bloom)
                val a = 1f - fade
                drawCircle(lilac.copy(alpha = 0.22f * a), 70.dp.toPx() * e, androidx.compose.ui.geometry.Offset(cx, ly))
                drawPath(cookie(cx, ly, 46.dp.toPx() * e, 9, 0.1f, rad * 2f), Color(0xFFB69DF8).copy(alpha = a))
                drawPath(cookie(cx, ly, 24.dp.toPx() * e, 6, 0.12f, -rad * 2.4f), Color(0xFFF6EDFF).copy(alpha = a))
                for (k in 0 until 8) {
                    val ang = k * Math.PI.toFloat() / 4f + rad
                    val d = 70.dp.toPx() * e
                    drawCircle(petal.copy(alpha = 0.75f * a), 3.dp.toPx(),
                        androidx.compose.ui.geometry.Offset(cx + d * kotlin.math.cos(ang), ly + d * kotlin.math.sin(ang)))
                }
                // the bits keep flowing out the other side
                var x2 = cx + step
                while (x2 < cx + (size.width - cx) * e) {
                    drawRoundRect(lilac.copy(alpha = 0.6f * a), androidx.compose.ui.geometry.Offset(x2 - 2.dp.toPx(), ly - 2.dp.toPx()),
                        androidx.compose.ui.geometry.Size(4.dp.toPx(), 4.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
                    x2 += step
                }
            }
        }
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.fillMaxHeight(0.28f))
            Text(romName(), color = Color(0xFFF6EDFF), fontSize = 34.sp, fontWeight = FontWeight.W700)
            Text("made to bloom.", color = lilac, fontSize = 14.sp, fontWeight = FontWeight.W500,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            Spacer(Modifier.weight(1f))
            androidx.compose.animation.AnimatedContent(hi, transitionSpec = {
                (fadeIn(tween(420)) + scaleIn(initialScale = 0.85f)) togetherWith (fadeOut(tween(300)) + scaleOut(targetScale = 1.1f))
            }, label = "hello") { idx ->
                Text(hellos[idx], color = Color(0xFFF6EDFF), fontSize = 48.sp, fontWeight = FontWeight.W500)
            }
            Spacer(Modifier.height(10.dp))
            Text("Made by Alex Desrosier", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = FontWeight.W600)
            Spacer(Modifier.fillMaxHeight(0.12f))
        }
    }
}

@Composable
private fun Title(title: String, sub: String) {
    Text(title, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.W600, lineHeight = 38.sp)
    Text(sub, color = Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun OutlinePill(text: String, filled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.clip(shape)
            .background(if (filled) Color.White else Color.White.copy(alpha = 0.08f))
            .border(1.8.dp, Color.White, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 32.dp, vertical = 15.dp)
    ) { Text(text, color = if (filled) Ink else Color.White, fontSize = 16.sp, fontWeight = FontWeight.W700) }
}

@Composable
private fun TipCard(title: String, desc: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp)).padding(18.dp)
    ) {
        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.W700)
        Spacer(Modifier.height(4.dp))
        Text(desc, color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun ActionCard(title: String, desc: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
            .clickable(onClick = onClick).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.W700)
            Spacer(Modifier.height(3.dp))
            Text(desc, color = Muted, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Text("Open", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.W700,
            modifier = Modifier.border(1.5.dp, Color.White, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 7.dp))
    }
}

@Composable
private fun StyleCard(
    name: String, hint: String, selected: Boolean, fav: Boolean, modifier: Modifier,
    onClick: () -> Unit, preview: @Composable () -> Unit,
) {
    val ring by animateColorAsState(if (selected) Color.White else Color.White.copy(alpha = 0.18f), label = "ring")
    val lift by animateFloatAsState(if (selected) 1f else 0.95f, spring(0.6f, 400f), label = "lift")
    Column(
        modifier.graphicsLayer { scaleX = lift; scaleY = lift }
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(26.dp))
                .background(Color.White.copy(alpha = if (selected) 0.12f else 0.05f))
                .border(if (selected) 2.dp else 1.dp, ring, RoundedCornerShape(26.dp)).padding(10.dp),
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.fillMaxHeight().aspectRatio(0.5f)) { preview() } }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.W700)
            if (fav) {
                Spacer(Modifier.width(6.dp))
                Text("Dev's Fav", color = Ink, fontSize = 10.sp, fontWeight = FontWeight.W800,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 7.dp, vertical = 2.dp))
            }
        }
        Text(hint, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

// ---------------------------------------------------------------- tiny previews
private val previewIcons = listOf(
    Color(0xFFFF8A5C), Color(0xFF58D1A8), Color(0xFFFFD166), Color(0xFF8C7CFF),
    Color(0xFFF09CE2), Color(0xFF8CD0F0), Color(0xFFC9C2FF), Color(0xFFFF6B78),
    Color(0xFF7FD1FF), Color(0xFFFFB86B), Color(0xFF9EF0E6), Color(0xFFE0A3FF),
)

@Composable
private fun MorphPreview(shape: String, accent: Color) {
    val sh = tileShape(shape)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cell = maxWidth / 4
        val g = 3.dp
        Box(Modifier.size(cell * 2 - g, cell * 2 - g).clip(sh).background(Brush.linearGradient(listOf(Color(0xFF6D47D1), Color(0xFFB83A5E)))))
        Box(Modifier.offset(cell * 2, 0.dp).size(cell * 2 - g, cell - g).morphTile(sh, accent))
        Box(Modifier.offset(cell * 2, cell).size(cell * 2 - g, cell - g).morphTile(sh, accent, 1))
        for (r in 0 until 3) for (c in 0 until 4) {
            Box(Modifier.offset(cell * c + cell * 0.12f, cell * (2 + r) + cell * 0.12f).size(cell * 0.76f - g).clip(sh)
                .background(previewIcons[(r * 4 + c) % previewIcons.size]))
        }
        Box(Modifier.offset(0.dp, cell * 5).size(cell * 4 - g, cell * 0.8f).morphTile(sh, accent, 2))
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(cell * 0.62f)
                .clip(RoundedCornerShape(50)).border(1.5.dp, Color.White, RoundedCornerShape(50))
        )
    }
}

@Composable
private fun BloomPreview(shape: String) {
    val sh = tileShape(shape)
    val t = bloomTones()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cell = maxWidth / 4
        val g = 3.dp
        Box(Modifier.size(cell * 4 - g, cell * 2 - g).clip(sh).background(t.p)) {
            Box(Modifier.align(Alignment.CenterEnd).offset(x = cell * 0.2f).size(cell * 1.6f).clip(CookieShape()).background(t.accent))
            Box(Modifier.align(Alignment.CenterStart).padding(start = cell * 0.25f).size(cell * 1.4f, cell * 0.5f)
                .clip(RoundedCornerShape(50)).background(t.onP.copy(alpha = 0.85f)))
        }
        Box(Modifier.offset(0.dp, cell * 2).size(cell * 2 - g, cell * 2 - g).clip(sh).background(t.s)) {
            Box(Modifier.padding(cell * 0.18f).size(cell * 0.8f).clip(CookieShape()).background(t.t))
        }
        Box(Modifier.offset(cell * 2, cell * 2).size(cell * 2 - g, cell - g).clip(sh).background(t.accent))
        Box(Modifier.offset(cell * 2, cell * 3).size(cell * 2 - g, cell - g).clip(sh).background(t.t))
        for (r in 0 until 2) for (c in 0 until 4) {
            Box(Modifier.offset(cell * c + cell * 0.12f, cell * (4 + r) + cell * 0.12f).size(cell * 0.76f - g).clip(sh)
                .background(previewIcons[(r * 4 + c) % previewIcons.size]))
        }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(cell * 0.62f)
                .clip(RoundedCornerShape(50)).background(t.p)
        )
    }
}

@Composable
private fun OrbitPreview(shape: String) {
    val sh = tileShape(shape)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val r = w * 0.36f
        val icon = w * 0.17f
        val cx = w / 2
        val cy = maxHeight * 0.6f
        Column(Modifier.padding(start = 4.dp, top = 6.dp)) {
            Box(Modifier.width(w * 0.3f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.6f)))
            Spacer(Modifier.height(4.dp))
            Box(Modifier.width(w * 0.62f).height(10.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
        }
        Box(Modifier.offset(cx - r * 0.55f, cy - r * 0.55f).size(r * 1.1f).clip(CircleShape)
            .border(1.5.dp, Color.White, CircleShape).background(Color.White.copy(alpha = 0.08f)))
        repeat(10) { i ->
            val a = Math.toRadians(-90.0 + i * 36.0)
            Box(Modifier.offset(cx + r * cos(a).toFloat() - icon / 2, cy + r * sin(a).toFloat() - icon / 2).size(icon).clip(sh)
                .background(previewIcons[i]))
        }
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(w * 0.15f)
                .clip(RoundedCornerShape(50)).border(1.5.dp, Color.White, RoundedCornerShape(50))
        )
    }
}


/** bitDroid: a tiny picture of the Classic home style (neutral grey, round icons, white bar). */
@Composable
private fun ClassicPreview() {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF4B5563), androidx.compose.ui.graphics.Color(0xFF111827))))
        val cols = listOf(0xFF34A853, 0xFFE5484D, 0xFF1A56DB, 0xFFFF9F0A)
        val r = size.height * 0.11f
        for (i in 0 until 4) {
            val cx = size.width * (0.2f + 0.2f * i)
            drawCircle(androidx.compose.ui.graphics.Color(cols[i]), r, androidx.compose.ui.geometry.Offset(cx, size.height * 0.42f))
        }
        drawRoundRect(androidx.compose.ui.graphics.Color.White,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.68f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.76f, size.height * 0.16f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height * 0.08f))
    }
}


/** bitDroid: a tiny picture of the Liquid home style (colorful light behind frosted glass). */
@Composable
private fun LiquidPreview() {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF1B2A55), androidx.compose.ui.graphics.Color(0xFF0E1630))))
        fun glow(x: Float, y: Float, r: Float, c: Long) = drawCircle(
            androidx.compose.ui.graphics.Brush.radialGradient(listOf(androidx.compose.ui.graphics.Color(c), androidx.compose.ui.graphics.Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(x, y), radius = r), r, androidx.compose.ui.geometry.Offset(x, y))
        glow(size.width * 0.2f, size.height * 0.2f, size.width * 0.35f, 0xCCFF9F6E)
        glow(size.width * 0.85f, size.height * 0.4f, size.width * 0.35f, 0xCC6E7BFF)
        glow(size.width * 0.5f, size.height * 1.0f, size.width * 0.4f, 0xCC29C5B5)
        val glass = androidx.compose.ui.graphics.Color(0x52FFFFFF)
        val rim = androidx.compose.ui.graphics.Color(0x80FFFFFF)
        val cr = androidx.compose.ui.geometry.CornerRadius(size.height * 0.14f)
        for (i in 0..1) {
            val tl = androidx.compose.ui.geometry.Offset(size.width * (0.08f + 0.44f * i), size.height * 0.12f)
            val sz = androidx.compose.ui.geometry.Size(size.width * 0.4f, size.height * 0.46f)
            drawRoundRect(glass, tl, sz, cr)
            drawRoundRect(rim, tl, sz, cr, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        }
        val tl = androidx.compose.ui.geometry.Offset(size.width * 0.08f, size.height * 0.68f)
        val sz = androidx.compose.ui.geometry.Size(size.width * 0.84f, size.height * 0.22f)
        drawRoundRect(glass, tl, sz, androidx.compose.ui.geometry.CornerRadius(size.height * 0.11f))
        drawRoundRect(rim, tl, sz, androidx.compose.ui.geometry.CornerRadius(size.height * 0.11f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
    }
}
