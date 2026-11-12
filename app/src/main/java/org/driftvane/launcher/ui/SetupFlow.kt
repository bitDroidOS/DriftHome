package org.driftvane.launcher.ui

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

private const val HELIBOARD_PKG = "helium314.keyboard"

/** The ROM's name, from the build (ro.driftvane.name), so a rename never needs a DriftHome change. */
fun romName(): String = runCatching {
    val c = Class.forName("android.os.SystemProperties")
    (c.getMethod("get", String::class.java, String::class.java).invoke(null, "ro.driftvane.name", "bitDroid") as String)
        .ifBlank { "bitDroid" }
}.getOrDefault("bitDroid")

private val Ink = Color(0xFF0B0A10)
private val Muted = Color(0xFFB4ADC2)

/**
 * First-boot setup, in the Morph look: Welcome → Home (Morph or Orbit) → Shape → Extras.
 * Shown once after the phone's setup, and again from Home settings › Run setup again.
 */
@Composable
fun SetupFlow(model: LauncherModel) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var homeStyle by remember { mutableStateOf(model.devFavStyle) }
    var shape by remember { mutableStateOf(model.iconShape.takeIf { it != "rounded" } ?: "squircle") }
    val heliboard = remember { runCatching { context.packageManager.getLaunchIntentForPackage(HELIBOARD_PKG) }.getOrNull() }
    val last = 3
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
                            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                model.homeStyles.forEach { (id, name) ->
                                    StyleCard(name, when (id) { "morph" -> "Tiles you can move, resize and remove"; "bloom" -> "Big Material You tiles in your wallpaper's colors"; else -> "Apps on a ring you spin" },
                                        homeStyle == id, id == model.devFavStyle, Modifier.weight(1f).fillMaxHeight(), onClick = { homeStyle = id }) {
                                        when (id) { "orbit" -> OrbitPreview(shape); "bloom" -> BloomPreview(shape); else -> MorphPreview(shape, accent) }
                                    }
                                }
                            }
                        }
                        2 -> {
                            Title("One shape", "Every tile, widget and icon uses it.")
                            Spacer(Modifier.height(18.dp))
                            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                Box(
                                    Modifier.fillMaxHeight().aspectRatio(0.5f).clip(RoundedCornerShape(26.dp))
                                        .background(Color(0x22FFFFFF)).border(1.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(26.dp))
                                        .padding(10.dp)
                                ) { when (homeStyle) { "orbit" -> OrbitPreview(shape); "bloom" -> BloomPreview(shape); else -> MorphPreview(shape, accent) } }
                            }
                            Spacer(Modifier.height(18.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                listOf("squircle", "pebble", "rounded", "square", "circle").forEach { id ->
                                    val sel = shape == id
                                    val ring by animateColorAsState(if (sel) Color.White else Color.Transparent, label = "r")
                                    Box(
                                        Modifier.size(56.dp).border(2.dp, ring, tileShape(id)).padding(6.dp).clip(tileShape(id))
                                            .background(if (sel) accent.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.16f))
                                            .clickable { shape = id }
                                    )
                                }
                            }
                        }
                        else -> {
                            Title("Last bits", "Optional. You can do these later.")
                            Spacer(Modifier.height(22.dp))
                            ActionCard(
                                if (heliboard != null) "Set up HeliBoard" else "Keyboard settings",
                                if (heliboard != null) "Themes, clipboard history, one-handed mode." else "Pick and manage your keyboards.",
                            ) {
                                runCatching {
                                    context.startActivity((heliboard?.let { Intent(it) } ?: Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            ActionCard("${romName()} Enhancements", "Lock screen, Quick Settings, volume panel and more. First item in Settings.") {
                                runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }
                            Spacer(Modifier.height(12.dp))
                            ActionCard("Show what's playing", "Let the Now playing tile read your music.") { openMediaAccessSettings(context) }
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
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        // the real logo vector
        androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(org.driftvane.launcher.R.drawable.ic_rom_logo), null,
            Modifier.size(128.dp, 64.dp),
        )
        Spacer(Modifier.height(28.dp))
        Text("Hey.", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.W600, lineHeight = 66.sp)
        Text("Welcome to ${romName()}.", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.W500, lineHeight = 32.sp)
        Spacer(Modifier.height(12.dp))
        Text("Let's set up your home screen. It takes about ten seconds.", color = Muted, fontSize = 16.sp, lineHeight = 22.sp)
        Spacer(Modifier.height(24.dp))
        Text("Made by Alex Desrosier", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = FontWeight.W600)
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
