package org.driftvane.launcher.ui

import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.driftvane.launcher.AppEntry
import org.driftvane.launcher.Folder
import org.driftvane.launcher.LauncherModel
import org.driftvane.launcher.isFolder

fun labelStyle(size: Float = 12f) = TextStyle(
    color = Color.White,
    fontSize = size.sp,
    fontWeight = FontWeight.W500,
    textAlign = TextAlign.Center,
    letterSpacing = 0.sp,
    shadow = Shadow(Color(0x66000000), Offset(0f, 1f), 6f),
)

val LabelStyle = labelStyle()

/** App tile in the user's chosen icon shape, with a soft drop shadow. */
@Composable
fun AppTile(app: AppEntry, size: Float, modifier: Modifier = Modifier) {
    val shape = LocalIconShape.current
    val edge = LocalIconBorder.current
    // bitDroid: notification count on the icon
    val notifs by org.driftvane.launcher.AppNotifs.byPkg.collectAsState()
    val count = notifs[app.component.packageName]?.size ?: 0
    Box(modifier.size(size.dp)) {
        Box(
            Modifier.fillMaxSize()
                .shadow((size * 0.06f).dp, shape, ambientColor = Color(0x33000000), spotColor = Color(0x59000000))
                .clip(shape)
                .then(if (edge != null) Modifier.border(1.5.dp, edge, shape) else Modifier)
        ) {
            Image(app.icon, contentDescription = app.label, modifier = Modifier.fillMaxSize())
        }
        // bitDroid: no badge (counts were misleading); swipe down on the icon still shows them
        if (false && count > 0 && size >= 36f) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp)
                    .defaultMinSize(minWidth = 21.dp).height(21.dp)
                    .clip(RoundedCornerShape(50)).background(Color(0xFFFF3B30))
                    .border(1.5.dp, Color(0x40000000), RoundedCornerShape(50))
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (count > 99) "99+" else count.toString(), color = Color.White, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

/**
 * bitDroid: swipe down on an icon that has notifications and they pop out of it (the Peek menu).
 * Only watches; it takes over the gesture only once it's clearly a downward swipe on such an icon.
 */
fun Modifier.swipeDownPeek(pkg: String?, enabled: Boolean, onPeek: () -> Unit): Modifier =
    if (!enabled || pkg == null) this else this.pointerInput(pkg) {
        val need = 34.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if ((org.driftvane.launcher.AppNotifs.byPkg.value[pkg]?.size ?: 0) == 0) return@awaitEachGesture
            var fired = false
            while (true) {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                if (!ch.pressed) break
                val d = ch.position - down.position
                if (!fired && d.y > need && kotlin.math.abs(d.x) < d.y * 0.6f) { fired = true; onPeek() }
                if (fired) ch.consume()
            }
        }
    }

/** Folder tile: liquid-glass tile with a 3x3 preview of the apps inside. */
@Composable
fun FolderTile(model: LauncherModel, folder: Folder, size: Float, modifier: Modifier = Modifier) {
    val shape = LocalIconShape.current
    Box(
        modifier.size(size.dp)
            .shadow((size * 0.05f).dp, shape, ambientColor = Color(0x33000000), spotColor = Color(0x4D000000))
            .glassShape(shape, model.glassStrength)
            .padding((size * 0.13f).dp),
    ) {
        val mini = size * 0.74f / 3f
        Column(verticalArrangement = Arrangement.spacedBy((size * 0.02f).dp)) {
            folder.apps.take(9).chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy((size * 0.02f).dp)) {
                    row.forEach { k ->
                        model.apps[k]?.let { a -> Image(a.icon, null, Modifier.size(mini.dp).clip(shape)) }
                    }
                }
            }
        }
    }
}

/** Tile + label for any home item (app or folder). [pressed] drives the squish. */
@Composable
fun HomeItem(
    model: LauncherModel,
    key: String,
    size: Float,
    showLabel: Boolean,
    pressed: Boolean = false,
    modifier: Modifier = Modifier,
    onBounds: (Rect) -> Unit = {},
) {
    val scale by animateFloatAsState(
        if (pressed) 0.93f else 1f,
        spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val dim by animateFloatAsState(if (pressed) 0.9f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "dim")
    val label = if (isFolder(key)) model.folders[key]?.name ?: "" else model.apps[key]?.label ?: ""
    Column(
        modifier.graphicsLayer { scaleX = scale; scaleY = scale; alpha = dim },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val tileMod = Modifier.onGloballyPositioned { onBounds(it.boundsInWindow()) }
        if (isFolder(key)) {
            model.folders[key]?.let { FolderTile(model, it, size, tileMod) }
        } else {
            model.apps[key]?.let { AppTile(it, size, tileMod) }
        }
        if (showLabel) {
            Spacer(Modifier.height(7.dp))
            Text(label, style = labelStyle(model.labelSize), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width((size + 18).dp))
        }
    }
}

/** Simple tappable icon for search results and lists. */
@Composable
fun TapIcon(app: AppEntry, size: Float, showLabel: Boolean, modifier: Modifier = Modifier, onClick: (Rect) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bounds = remember { arrayOf(Rect.Zero) }
    val scale by animateFloatAsState(if (pressed) 0.93f else 1f, spring(0.78f, Spring.StiffnessMedium), label = "p")
    Column(
        Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interaction, indication = null) { onClick(bounds[0]) }
            .then(modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppTile(app, size, Modifier.onGloballyPositioned { bounds[0] = it.boundsInWindow() })
        if (showLabel) {
            Spacer(Modifier.height(7.dp))
            Text(app.label, style = LabelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width((size + 18).dp))
        }
    }
}
