package org.driftvane.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.driftvane.launcher.LauncherModel
import org.driftvane.launcher.Source
import org.driftvane.launcher.WidgetItem
import org.driftvane.launcher.isFolder
import kotlin.math.abs
import kotlin.math.roundToInt

/** Callbacks for anything you can tap, long-press or drag. */
interface ItemHandler {
    fun tap(key: String, bounds: Rect)
    fun menu(key: String, bounds: Rect)
    fun dragStart(key: String, source: Source, at: Offset)
    fun dragMove(at: Offset)
    fun dragEnd()
    /** Something was held long enough to start editing (widgets and tiles). */
    fun enterEdit() {}
}

/** Bumped every time you come back home, so icons can cascade in. */
val LocalResumeTick = compositionLocalOf { 0 }

fun cellHeight(model: LauncherModel): Dp = if (model.showLabels) model.cellH else model.cellH * 0.88f

/** Wiggle shared by everything in edit mode. */
@Composable
fun wiggleAngle(): Float {
    val wiggle = rememberInfiniteTransition(label = "wiggle")
    val a by wiggle.animateFloat(-1.4f, 1.4f, infiniteRepeatable(tween(140, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    return a
}

/** One home page: optional clock tiles at the top, then one grid that widgets and icons share. */
@Composable
fun HomePage(
    model: LauncherModel,
    page: Int,
    editMode: Boolean,
    drag: DragState,
    handler: ItemHandler,
    widget: @Composable (WidgetItem, Modifier) -> Unit,
) {
    val cellH = cellHeight(model)
    val p = model.pages.getOrNull(page) ?: return
    if (page == 0 && model.showGlance && model.topStyle == "orbit") {
        Box(Modifier.fillMaxWidth().height(cellH * model.rows)) { OrbitPage(model, p.items, handler) }
        return
    }
    if (page == 0 && model.showGlance && model.topStyle == "columns") {
        // Columns style: info on the left, apps as a list on the right
        Box(Modifier.fillMaxWidth().height(cellH * model.rows)) { ColumnsPage(model, p.items, handler) }
        return
    }
    Column(Modifier.fillMaxWidth()) {
        if (page == 0 && model.showGlance && model.topRows() > 0) {
            Box(Modifier.fillMaxWidth().height(cellH * model.topRows()).padding(vertical = 6.dp)) {
                when (model.topStyle) {
                    "bold" -> BoldClock(model, Modifier.align(Alignment.TopStart))
                    "dashboard" -> DashboardTop(model)
                    "stock" -> StockTop(model)
                    "frame" -> FrameTop(model) { model.frameHole = it }
                    "split" -> SplitTop(model)
                    else -> StackTop(model)
                }
                if (editMode) RemoveBadge(Modifier.align(Alignment.TopStart).offset((-6).dp, (-6).dp)) {
                    model.changeGlance(false)
                }
            }
        }
        HomeGrid(model, page, p.items, p.widgets, editMode, drag, handler, widget)
    }
}

private enum class Edge { LEFT, TOP, RIGHT, BOTTOM }

/**
 * A widget on the grid. In edit mode: drag it to any spot (or past the screen edge to the
 * next page), drag any of its 4 edges to resize, – to remove. It snaps to the grid on release.
 */
@Composable
private fun WidgetCell(
    model: LauncherModel,
    page: Int,
    w: WidgetItem,
    editMode: Boolean,
    cellW: Float,
    cellH: Float,
    drag: DragState,
    handler: ItemHandler,
    widget: @Composable (WidgetItem, Modifier) -> Unit,
) {
    key("w${w.id}") {
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        // live drag offset of the whole widget, and live edge offsets while resizing (px)
        val move = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
        var dl by remember { mutableFloatStateOf(0f) }
        var dt by remember { mutableFloatStateOf(0f) }
        var dr by remember { mutableFloatStateOf(0f) }
        var db by remember { mutableFloatStateOf(0f) }
        val cur by rememberUpdatedState(w)
        val angle = if (editMode) wiggleAngle() else 0f
        val active = move.value != Offset.Zero || dl != 0f || dt != 0f || dr != 0f || db != 0f
        val lifted by animateFloatAsState(if (move.value != Offset.Zero) 1.04f else 1f, spring(0.7f), label = "lift")

        val target = IntOffset((w.col * cellW).roundToInt(), (w.row * cellH).roundToInt())
        val pos = remember { Animatable(target, IntOffset.VectorConverter) }
        LaunchedEffect(target) { pos.animateTo(target, spring(dampingRatio = 0.8f, stiffness = 420f)) }

        val minW = cellW * 0.9f
        val minH = cellH * 0.9f
        val wPx = (w.spanX * cellW - dl + dr).coerceAtLeast(minW)
        val hPx = (w.spanY * cellH - dt + db).coerceAtLeast(minH)
        val wDp = with(density) { wPx.toDp() }
        val hDp = with(density) { hPx.toDp() }

        fun snapResize() {
            val c = cur
            val left = ((c.col * cellW + dl) / cellW).roundToInt()
            val top = ((c.row * cellH + dt) / cellH).roundToInt()
            val right = ((c.col * cellW + c.spanX * cellW + dr) / cellW).roundToInt()
            val bottom = ((c.row * cellH + c.spanY * cellH + db) / cellH).roundToInt()
            val nl = left.coerceIn(0, model.columns - 1)
            val nt = top.coerceIn(0, model.iconRows(page) - 1)
            val sx = (right - nl).coerceIn(1, model.columns - nl)
            val sy = (bottom - nt).coerceIn(1, model.iconRows(page) - nt)
            model.setWidgetRect(c.id, nl, nt, sx, sy)
            dl = 0f; dt = 0f; dr = 0f; db = 0f
        }

        val shape = if (isMorphLike(model.topStyle)) tileShape(model.iconShape) else RoundedCornerShape(24.dp)
        val editNow by rememberUpdatedState(editMode)
        val view = androidx.compose.ui.platform.LocalView.current

        /** Finger position on screen while the widget is lifted. */
        fun fingerRoot(local: Offset): Offset {
            val g = drag.grids[page]?.bounds?.topLeft ?: Offset.Zero
            return g + Offset(pos.value.x.toFloat(), pos.value.y.toFloat()) + move.value + local
        }
        fun trackRemove(local: Offset) {
            val over = drag.removeBounds != Rect.Zero && drag.removeBounds.inflate(28f).contains(fingerRoot(local))
            if (over != drag.widgetOverRemove) {
                drag.widgetOverRemove = over
                if (over && model.haptics) view.performHapticFeedback(android.view.HapticFeedbackConstants.SEGMENT_TICK)
            }
        }
        /** Let go: drop on Remove, flip pages past the edge, or snap to the nearest free spot. */
        fun drop() {
            val c = cur
            val o = move.value
            val overRemove = drag.widgetOverRemove
            drag.widgetDrag = false
            drag.widgetOverRemove = false
            if (overRemove) {
                if (model.haptics) view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                model.removeWidget(c.id)
                return
            }
            val screenW = cellW * model.columns
            val x = c.col * cellW + o.x
            var placed = false
            when {
                x + c.spanX * cellW * 0.5f > screenW + cellW * 0.4f -> model.moveWidgetPage(c.id, 1)
                x + c.spanX * cellW * 0.5f < -cellW * 0.4f -> model.moveWidgetPage(c.id, -1)
                else -> {
                    val nc = ((c.col * cellW + o.x) / cellW).roundToInt().coerceIn(0, model.columns - c.spanX)
                    val nr = ((c.row * cellH + o.y) / cellH).roundToInt().coerceIn(0, model.iconRows(page) - c.spanY)
                    if (model.setWidgetRect(c.id, nc, nr, c.spanX, c.spanY)) {
                        // hand the finger offset over to the grid position, then always settle
                        // exactly on the cell (also when it lands back in the same spot)
                        placed = true
                        val dest = IntOffset((nc * cellW).roundToInt(), (nr * cellH).roundToInt())
                        scope.launch {
                            pos.snapTo(pos.value + IntOffset(o.x.roundToInt(), o.y.roundToInt()))
                            move.snapTo(Offset.Zero)
                            pos.animateTo(dest, spring(dampingRatio = 0.8f, stiffness = 500f))
                        }
                    }
                }
            }
            if (!placed) scope.launch { move.animateTo(Offset.Zero, spring(dampingRatio = 0.75f, stiffness = 380f)) }
        }

        Box(
            Modifier
                .offset { pos.value + IntOffset((move.value.x + dl).roundToInt(), (move.value.y + dt).roundToInt()) }
                .size(wDp, hDp)
                .zIndex(if (active) 3f else 1f)
                .graphicsLayer {
                    rotationZ = if (active) 0f else angle * 0.35f; scaleX = lifted; scaleY = lifted
                    // shrink a little over the Remove pill
                    if (drag.widgetOverRemove && move.value != Offset.Zero) { scaleX = 0.82f; scaleY = 0.82f; alpha = 0.7f }
                }
                // Hold a widget (outside edit mode) to lift it and drag it straight away. Watches the
                // Initial pass so the widget's own taps still work until the long-press fires.
                .pointerInput(w.id) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        if (editNow || model.lockLayout) return@awaitEachGesture
                        val slopPx = viewConfiguration.touchSlop
                        val early = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                val c = e.changes.firstOrNull { it.id == down.id } ?: break
                                if (!c.pressed || (c.position - down.position).getDistance() > slopPx) break
                            }
                            true
                        }
                        if (early != null) return@awaitEachGesture
                        if (model.haptics) view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        handler.enterEdit()
                        drag.widgetDrag = true
                        var moved = false
                        try {
                            while (true) {
                                val e = awaitPointerEvent(PointerEventPass.Initial)
                                val c = e.changes.firstOrNull { it.id == down.id } ?: break
                                c.consume()
                                if (!c.pressed) break
                                val d = c.position - c.previousPosition
                                if (d != Offset.Zero) {
                                    moved = true
                                    scope.launch { move.snapTo(move.value + d) }
                                    trackRemove(c.position)
                                }
                            }
                        } finally {
                            if (moved) drop() else { drag.widgetDrag = false; drag.widgetOverRemove = false }
                        }
                    }
                }
                .padding(6.dp)
        ) {
            val inner = Modifier.fillMaxSize().clip(shape)
            if (w.id < 0) DriftTile(model, w, inner) else widget(w, inner)
            if (editMode) {
                // body: drag to move
                Box(
                    Modifier.fillMaxSize()
                        .clip(shape)
                        .background(Color(0x1AFFFFFF))
                        .border(1.5.dp, Color.White.copy(alpha = if (active) 0.8f else 0.35f), shape)
                        .pointerInput(w.id) {
                            detectDragGestures(
                                onDragStart = { drag.widgetDrag = true },
                                onDrag = { c, d -> c.consume(); scope.launch { move.snapTo(move.value + d) }; trackRemove(c.position) },
                                onDragEnd = { drop() },
                                onDragCancel = {
                                    drag.widgetDrag = false; drag.widgetOverRemove = false
                                    scope.launch { move.animateTo(Offset.Zero, spring(0.75f)) }
                                },
                            )
                        }
                )
                // resize handles on all 4 sides
                Edge.entries.forEach { e ->
                    val align = when (e) {
                        Edge.LEFT -> Alignment.CenterStart; Edge.RIGHT -> Alignment.CenterEnd
                        Edge.TOP -> Alignment.TopCenter; Edge.BOTTOM -> Alignment.BottomCenter
                    }
                    val horizontal = e == Edge.LEFT || e == Edge.RIGHT
                    Box(
                        Modifier.align(align)
                            .offset(
                                x = when (e) { Edge.LEFT -> (-12).dp; Edge.RIGHT -> 12.dp; else -> 0.dp },
                                y = when (e) { Edge.TOP -> (-12).dp; Edge.BOTTOM -> 12.dp; else -> 0.dp },
                            )
                            .size(if (horizontal) 28.dp else 64.dp, if (horizontal) 64.dp else 28.dp)
                            .pointerInput(w.id, e) {
                                detectDragGestures(
                                    onDrag = { c, d ->
                                        c.consume()
                                        when (e) {
                                            Edge.LEFT -> dl = (dl + d.x).coerceAtMost(cur.spanX * cellW - minW)
                                            Edge.RIGHT -> dr = (dr + d.x).coerceAtLeast(minW - cur.spanX * cellW)
                                            Edge.TOP -> dt = (dt + d.y).coerceAtMost(cur.spanY * cellH - minH)
                                            Edge.BOTTOM -> db = (db + d.y).coerceAtLeast(minH - cur.spanY * cellH)
                                        }
                                    },
                                    onDragEnd = { snapResize() },
                                    onDragCancel = { dl = 0f; dt = 0f; dr = 0f; db = 0f },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(if (horizontal) 6.dp else 40.dp, if (horizontal) 40.dp else 6.dp)
                                .shadow(4.dp, RoundedCornerShape(50))
                                .background(Color.White, RoundedCornerShape(50))
                        )
                    }
                }
                RemoveBadge(Modifier.align(Alignment.TopStart).offset((-8).dp, (-8).dp)) { model.removeWidget(w.id) }
            }
        }
    }
}

/** The grid: widgets sit on their cells; icon i sits in the i-th cell no widget covers. */
@Composable
private fun HomeGrid(
    model: LauncherModel,
    page: Int,
    items: List<String>,
    widgets: List<WidgetItem>,
    editMode: Boolean,
    drag: DragState,
    handler: ItemHandler,
    widget: @Composable (WidgetItem, Modifier) -> Unit,
) {
    val cols = model.columns
    val rows = model.iconRows(page)
    val cellHdp = cellHeight(model)
    val tick = LocalResumeTick.current
    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp).height(cellHdp * rows)
    ) {
        val density = LocalDensity.current
        val cellW = constraints.maxWidth.toFloat() / cols
        val cellH = with(density) { cellHdp.toPx() }
        val cellWdp = with(density) { cellW.toDp() }
        val dens = density.density

        Box(Modifier.fillMaxSize().onGloballyPositioned {
            drag.grids[page] = GridInfo(it.boundsInRoot(), cellW, cellH, cols)
        })

        val free = remember(widgets, cols, rows) { model.freeCells(page) }

        // solid tonal panel behind the rows that have apps or widgets
        if (model.gridPanel) {
            var lastRow = -1
            items.forEachIndexed { i, k -> if (k != org.driftvane.launcher.EMPTY) free.getOrNull(i)?.let { lastRow = maxOf(lastRow, it / cols) } }
            widgets.forEach { lastRow = maxOf(lastRow, it.row + it.spanY - 1) }
            if (lastRow >= 0) {
                Box(
                    Modifier.fillMaxWidth().height(cellHdp * (lastRow + 1))
                        .clip(RoundedCornerShape(30.dp)).background(Tonal.quiet(model.accent))
                )
            }
        }

        widgets.forEach { w -> WidgetCell(model, page, w, editMode, cellW, cellH, drag, handler, widget) }
        val angle = if (editMode) wiggleAngle() else 0f
        val order = previewOrder(items, page, drag.key, drag.hover)
        val ontoTarget = (drag.hover as? Hover.Onto)?.takeIf { it.page == page }?.target
        val iconSize = 60f * model.iconScale * (if (cols >= 6) 0.84f else if (cols == 5) 0.94f else 1f)

        order.forEachIndexed { index, itemKey ->
            if (!isFolder(itemKey) && model.apps[itemKey] == null) return@forEachIndexed
            if (isFolder(itemKey) && model.folders[itemKey] == null) return@forEachIndexed
            val cell = free.getOrNull(index) ?: return@forEachIndexed
            key(itemKey) {
                val target = IntOffset(((cell % cols) * cellW).toInt(), ((cell / cols) * cellH).toInt())
                val pos = remember { Animatable(target, IntOffset.VectorConverter) }
                LaunchedEffect(target) { pos.animateTo(target, spring(dampingRatio = 0.78f, stiffness = 420f)) }
                // cascade in when you come back home (row by row, like a wave)
                val enter = remember { Animatable(1f) }
                LaunchedEffect(tick) {
                    if (tick > 1) {
                        enter.snapTo(0f)
                        delay(((cell / cols) * 28L + (cell % cols) * 10L))
                        enter.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 300f))
                    }
                }
                var pressed by remember { mutableStateOf(false) }
                val topLeft = remember { arrayOf(Offset.Zero) }
                val bounds = remember { arrayOf(Rect.Zero) }
                val hidden = drag.key == itemKey
                val grow by animateFloatAsState(if (ontoTarget == itemKey) 1.16f else 1f, spring(0.55f), label = "g")
                val phase = if (itemKey.hashCode() % 2 == 0) 1f else -1f

                Box(
                    Modifier
                        .offset { pos.value }
                        .size(cellWdp, cellHdp)
                        .zIndex(if (hidden) -1f else 0f)
                        .onGloballyPositioned { topLeft[0] = it.positionInRoot() }
                        .graphicsLayer {
                            val e = enter.value
                            alpha = if (hidden) 0f else e.coerceIn(0f, 1f)
                            rotationZ = if (editMode) angle * phase else 0f
                            val sc = grow * (0.84f + 0.16f * e)
                            scaleX = sc; scaleY = sc
                            translationY = (1f - e) * 26f * dens
                        }
                        .iconGestures(
                            editMode = editMode,
                            rootTopLeft = { topLeft[0] },
                            onPress = { pressed = it },
                            onTap = { handler.tap(itemKey, bounds[0]) },
                            onMenu = { handler.menu(itemKey, bounds[0]) },
                            onDragStart = { handler.dragStart(itemKey, Source.OnPage(page), it) },
                            onDragMove = { handler.dragMove(it) },
                            onDragEnd = { handler.dragEnd() },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    HomeItem(model, itemKey, iconSize, model.showLabels, pressed, onBounds = { bounds[0] = it })
                    if (editMode && !hidden && !model.lockLayout) {
                        RemoveBadge(Modifier.align(Alignment.TopStart).offset(8.dp, 4.dp)) {
                            model.removeFromHome(itemKey)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RemoveBadge(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(26.dp).shadow(6.dp, CircleShape)
            .glassShape(CircleShape, 0.95f)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.width(11.dp).height(2.5.dp).background(Color.White, RoundedCornerShape(50))) }
}
