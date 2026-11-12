package org.driftvane.launcher.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import org.driftvane.launcher.Source

/** Grid geometry each page reports so drops can be mapped to a cell. */
data class GridInfo(val bounds: Rect, val cellW: Float, val cellH: Float, val cols: Int)

/** What a drop over the current page means. */
sealed class Hover {
    data object None : Hover()
    data class Insert(val page: Int, val index: Int) : Hover()
    data class Onto(val page: Int, val target: String) : Hover()
    data class Dock(val index: Int) : Hover()
    /** Over the Remove pill at the top of the screen. */
    data object Remove : Hover()
}

@Stable
class DragState {
    var key by mutableStateOf<String?>(null)
        private set
    var source by mutableStateOf<Source?>(null)
        private set
    var pointer by mutableStateOf(Offset.Zero)
    var hover by mutableStateOf<Hover>(Hover.None)

    val grids = mutableStateMapOf<Int, GridInfo>()
    var dockBounds by mutableStateOf(Rect.Zero)
    /** The Remove pill (top of the screen while something is lifted). */
    var removeBounds by mutableStateOf(Rect.Zero)
    /** A widget or tile is being dragged (they move themselves, not through [key]). */
    var widgetDrag by mutableStateOf(false)
    /** The dragged widget is over the Remove pill. */
    var widgetOverRemove by mutableStateOf(false)

    val active get() = key != null

    fun start(k: String, s: Source, at: Offset) { key = k; source = s; pointer = at }
    fun end() { key = null; source = null; hover = Hover.None }
}

/**
 * One gesture handler for icons:
 *  - tap -> [onTap]
 *  - long-press and let go -> [onMenu]
 *  - long-press and move (or, in edit mode, just move) -> drag: [onDragStart] / [onDragMove] / [onDragEnd]
 * Positions given to the drag callbacks are in root (screen) coordinates.
 *
 * The gesture is never restarted when edit mode flips on mid-drag, and a started drag always
 * ends (even if the gesture gets cancelled), so the dragged icon can't get stuck.
 */
fun Modifier.iconGestures(
    editMode: Boolean,
    rootTopLeft: () -> Offset,
    onPress: (Boolean) -> Unit,
    onTap: () -> Unit,
    onMenu: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit,
): Modifier = composed {
    val edit by rememberUpdatedState(editMode)
    val topLeft by rememberUpdatedState(rootTopLeft)
    val press by rememberUpdatedState(onPress)
    val tap by rememberUpdatedState(onTap)
    val menu by rememberUpdatedState(onMenu)
    val dStart by rememberUpdatedState(onDragStart)
    val dMove by rememberUpdatedState(onDragMove)
    val dEnd by rememberUpdatedState(onDragEnd)
    pointerInput(Unit) {
        awaitEachGesture {
            var dragging = false
            try {
                val down = awaitFirstDown(requireUnconsumed = false)
                press(true)
                if (edit) {
                    val slop = awaitTouchSlopOrCancellation(down.id) { c, _ -> c.consume() }
                    if (slop == null) return@awaitEachGesture
                    dragging = true
                    press(false)
                    dStart(topLeft() + slop.position)
                    drag(slop.id) { c -> dMove(topLeft() + c.position); c.consume() }
                    return@awaitEachGesture
                }
                val lp = awaitLongPressOrCancellation(down.id)
                if (lp == null) {
                    press(false)
                    val up = currentEvent.changes.firstOrNull { it.id == down.id }
                    if (up != null && up.changedToUpIgnoreConsumed() && !up.isConsumed) tap()
                    return@awaitEachGesture
                }
                val start = lp.position
                val slopPx = viewConfiguration.touchSlop
                // the menu pops up the moment the hold is long enough (no need to let go);
                // moving your finger from there closes it and starts dragging the icon
                menu()
                drag(lp.id) { c ->
                    if (!dragging && (c.position - start).getDistance() > slopPx) {
                        dragging = true
                        press(false)
                        dStart(topLeft() + c.position)
                    }
                    if (dragging) dMove(topLeft() + c.position)
                    c.consume()
                }
            } finally {
                press(false)
                if (dragging) dEnd()
            }
        }
    }
}

/**
 * The order a page shows right now: while something is dragged over it, a gap opens at the
 * hovered spot (the dragged item itself sits there, invisible). The dragged item always stays
 * in its source list so its gesture keeps running.
 */
fun previewOrder(items: List<String>, page: Int, dragKey: String?, hover: Hover): List<String> {
    if (dragKey == null) return items
    // the dragged app leaves a gap where it was; everyone else stays put
    val base = items.map { if (it == dragKey) org.driftvane.launcher.EMPTY else it }.toMutableList()
    if (hover is Hover.Insert && hover.page == page) {
        val i = hover.index.coerceAtLeast(0)
        while (base.size <= i) base.add(org.driftvane.launcher.EMPTY)
        if (base[i] == org.driftvane.launcher.EMPTY) {
            base[i] = dragKey
        } else {
            // cell taken: shift forward into the next gap
            base.add(i, dragKey)
            val gap = base.subList(i + 1, base.size).indexOf(org.driftvane.launcher.EMPTY)
            if (gap >= 0) base.removeAt(i + 1 + gap)
        }
        return base
    }
    return if (dragKey in items) items else base
}
