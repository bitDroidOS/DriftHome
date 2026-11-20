package org.driftvane.launcher.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** A soft rounded pill whose edge ripples like a Bloom cookie while [wave] > 0 (from bitMessages). */
private class WavyPill(private val wave: Float, private val phase: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val steps = 160
        for (i in 0..steps) {
            val t = 2 * PI * i / steps
            val c = cos(t)
            val s = sin(t)
            val r = (abs(c).pow(4.0) + abs(s).pow(4.0)).pow(-0.25)
            val w = 1.0 + wave * cos(9 * t + phase)
            val x = cx + (size.width / 2f) * (c * r * w).toFloat()
            val y = cy + (size.height / 2f) * (s * r * w).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return Outline.Generic(p)
    }
}

class BloomTab(val glyph: G, val label: String)

/**
 * bitDroid Bloom tab bar, the bitMessages slider in Material You: a floating tonal bar in your
 * wallpaper colors with a pill on the selected tab. Drag along it and the pill follows your
 * finger, its edge rippling like a cookie, then settles on the nearest tab. Only the selected
 * tab shows its label. The clover on the right opens search.
 */
@Composable
fun BloomTabBar(
    tabs: List<BloomTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = bloomTones()
    val pill = t.accent
    val onPill = t.p
    val ink = t.onS
    val view = LocalView.current
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val barShape = RoundedCornerShape(30.dp)
    Row(
        modifier.fillMaxWidth().navigationBarsPadding().padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxWithConstraints(
            Modifier.weight(1f).height(66.dp)
                .shadow(14.dp, barShape, ambientColor = Color(0x66000000), spotColor = Color(0x80000000))
                .clip(barShape).background(t.s.copy(alpha = 0.96f)).padding(7.dp),
        ) {
            val density = LocalDensity.current
            val tabW = with(density) { maxWidth.toPx() } / tabs.size
            val x = remember { Animatable(selected * tabW) }
            var dragging by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            LaunchedEffect(selected, tabW) {
                if (!dragging) x.animateTo(selected * tabW, spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow))
            }
            val wave by animateFloatAsState(if (dragging) 0.07f else 0f, spring(dampingRatio = 0.5f, stiffness = 300f), label = "wave")
            val phase by rememberInfiniteTransition(label = "ripple")
                .animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(900, easing = LinearEasing)), label = "phase")
            val hover = (x.value / tabW).roundToInt().coerceIn(0, tabs.lastIndex)
            Box(
                Modifier.offset { IntOffset(x.value.roundToInt(), 0) }
                    .width(with(density) { tabW.toDp() }).fillMaxHeight()
                    .graphicsLayer { val s2 = 1f + wave * 0.8f; scaleX = s2; scaleY = s2 }
                    .clip(WavyPill(wave, phase))
                    .background(pill),
            )
            Row(
                Modifier.fillMaxSize().pointerInput(tabs.size, tabW) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            val target = (x.value / tabW).roundToInt().coerceIn(0, tabs.lastIndex)
                            dragging = false
                            scope.launch { x.animateTo(target * tabW, spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMediumLow)) }
                            if (target != currentSelected) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                currentOnSelect(target)
                            }
                        },
                        onDragCancel = {
                            dragging = false
                            scope.launch { x.animateTo(currentSelected * tabW) }
                        },
                    ) { change, dx ->
                        change.consume()
                        scope.launch { x.snapTo((x.value + dx).coerceIn(0f, tabW * tabs.lastIndex)) }
                    }
                },
            ) {
                tabs.forEachIndexed { i, tab ->
                    val on = if (dragging) i == hover else i == selected
                    Row(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).tabSquish {
                            if (i != selected) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onSelect(i)
                        },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Glyph(tab.glyph, if (on) onPill else ink, 21.dp)
                        AnimatedVisibility(on, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
                            Row {
                                Spacer(Modifier.width(7.dp))
                                Text(tab.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = onPill, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.size(66.dp).tabSquish(onSearch)
                .shadow(14.dp, CookieShape(4, 0.2f), ambientColor = Color(0x66000000), spotColor = Color(0x80000000))
                .clip(CookieShape(4, 0.2f)).background(pill),
            contentAlignment = Alignment.Center,
        ) { Glyph(G.SEARCH, onPill, 24.dp) }
    }
}

/** Squish on press, spring back. */
private fun Modifier.tabSquish(onClick: () -> Unit): Modifier = androidx.compose.ui.composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.9f else 1f, spring(0.45f, 600f), label = "sq")
    this.graphicsLayer { scaleX = s; scaleY = s }.clickable(src, indication = null, onClick = onClick)
}
