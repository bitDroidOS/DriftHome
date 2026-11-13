package org.driftvane.launcher.ui

import android.appwidget.AppWidgetProviderInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.driftvane.launcher.LauncherModel
import androidx.compose.ui.graphics.TransformOrigin
import org.driftvane.launcher.PageAnim
import org.driftvane.launcher.LibraryStyle
import org.driftvane.launcher.Source
import org.driftvane.launcher.SwipeDown
import org.driftvane.launcher.WidgetItem
import org.driftvane.launcher.isFolder
import kotlin.math.abs

interface HomeActions {
    fun launch(key: String, bounds: Rect)
    fun appInfo(key: String)
    fun uninstall(key: String)
    fun expandNotifications()
    fun openWallpaper()
    fun widgetProviders(): List<AppWidgetProviderInfo>
    fun pickWidget(info: AppWidgetProviderInfo, page: Int)
}

@Composable
fun HomeScreen(
    model: LauncherModel,
    homePresses: StateFlow<Int>,
    resumes: StateFlow<Int>,
    actions: HomeActions,
    widget: @Composable (WidgetItem, Modifier) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val hapticsRaw = LocalHapticFeedback.current
    val buzz: (HapticFeedbackType) -> Unit = { t -> if (model.haptics) hapticsRaw.performHapticFeedback(t) }
    val density = LocalDensity.current
    val screenW = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val statusTop = WindowInsets.statusBars.getTop(density)
    Tone.accent = model.accent
    var pagerH by remember { mutableIntStateOf(0) }
    var bottomBarH by remember { mutableIntStateOf(0) }
    val bottomBarDp = with(density) { bottomBarH.toDp() }
    // spread the 6 rows evenly between the status bar and the dock
    LaunchedEffect(pagerH, bottomBarH, statusTop) {
        if (pagerH > 0 && bottomBarH > 0) {
            val top = statusTop + with(density) { 14.dp.toPx() }
            val availPx = pagerH - top - bottomBarH
            // as many rows as fit (about 100dp each), so the grid reaches right down to the dock
            val fit = (availPx / with(density) { 100.dp.toPx() }).toInt()
            model.changeRows(fit)
            val avail = with(density) { (availPx / model.rows).toDp() }
            model.cellH = avail.coerceIn(80.dp, 140.dp)
        }
    }
    val drawerMode = model.libraryStyle == LibraryStyle.DRAWER || model.libraryStyle == LibraryStyle.BENTO
    val pageCount = model.pages.size + if (drawerMode) 0 else 1
    val pager = rememberPagerState { pageCount }
    val drag = remember { DragState() }

    var editMode by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var menuKey by remember { mutableStateOf<String?>(null) }
    var menuAnchor by remember { mutableStateOf(Rect.Zero) }
    var homeMenu by remember { mutableStateOf(false) }
    var homeAnchor by remember { mutableStateOf(Rect.Zero) }
    var settingsOpen by remember { mutableStateOf(false) }
    var widgetPicker by remember { mutableStateOf(false) }
    var openFolder by remember { mutableStateOf<String?>(null) }
    var openCategory by remember { mutableStateOf<String?>(null) }
    var drawerOpen by remember { mutableStateOf(false) }
    // How far the swipe-up drawer is open (0 closed, 1 open). It follows your finger.
    val drawerReveal = remember { androidx.compose.animation.core.Animatable(0f) }
    // App opening: home zooms in toward the icon you tapped and fades while the app grows out
    // of it; it springs back when you come home.
    val launchZoom = remember { androidx.compose.animation.core.Animatable(0f) }
    var launchOrigin by remember { mutableStateOf(Offset.Zero) }
    val screenH = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    var styleOpen by remember { mutableStateOf(false) }

    // Swipe-up drawer: pulling down at the top of the list drags the drawer down; let go to close.
    var drawerPull by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(drawerOpen) { if (drawerOpen) drawerPull = 0f }
    val drawerScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // dragging back up while pulled down: shrink the pull first
                if (available.y < 0 && drawerPull > 0f) {
                    val new = (drawerPull + available.y).coerceAtLeast(0f)
                    val used = new - drawerPull
                    drawerPull = new
                    return Offset(0f, used)
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // list is already at the top and you keep pulling down
                if (available.y > 0 && source == NestedScrollSource.UserInput) {
                    drawerPull += available.y
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (drawerPull <= 0f) return Velocity.Zero
                val close = drawerPull > with(density) { 140.dp.toPx() } || available.y > 1800f
                if (close) {
                    // carry on from where your finger left it, then slide the rest of the way down
                    drawerReveal.snapTo((1f - drawerPull / screenH).coerceIn(0f, 1f))
                    drawerPull = 0f
                    drawerOpen = false
                } else {
                    animate(drawerPull, 0f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f)) { v, _ -> drawerPull = v }
                }
                return available
            }
        }
    }

    fun closeAll() {
        searchOpen = false; menuKey = null; homeMenu = false; settingsOpen = false
        widgetPicker = false; openFolder = null; openCategory = null; drawerOpen = false; editMode = false
        styleOpen = false
    }

    fun hoverAt(pos: Offset): Hover {
        val k = drag.key ?: return Hover.None
        if (drag.removeBounds != Rect.Zero && drag.removeBounds.inflate(28f).contains(pos) && drag.source !is Source.Library) return Hover.Remove
        if (model.showDock && drag.dockBounds.contains(pos)) {
            val n = model.dock.count { it != k }
            val slot = drag.dockBounds.width / (n + 1)
            return Hover.Dock(((pos.x - drag.dockBounds.left) / slot).toInt().coerceIn(0, n))
        }
        val page = pager.currentPage
        if (page >= model.pages.size) return Hover.None
        val g = drag.grids[page] ?: return Hover.None
        val rows = model.iconRows(page)
        if (rows <= 0) return Hover.None
        val col = ((pos.x - g.bounds.left) / g.cellW).toInt().coerceIn(0, g.cols - 1)
        val row = ((pos.y - g.bounds.top) / g.cellH).toInt().coerceIn(0, rows - 1)
        // widgets cover some cells; icons use the rest, so translate the cell into an icon slot
        val idx = model.freeCells(page).indexOf(row * g.cols + col)
        if (idx < 0) return Hover.None
        val items = model.pages[page].items
        val current = previewOrder(items, page, k, drag.hover)
        if (idx < current.size) {
            val under = current[idx]
            if (under == k) return drag.hover
            if (under != org.driftvane.launcher.EMPTY) {
                val cx = g.bounds.left + (col + 0.5f) * g.cellW
                val cy = g.bounds.top + (row + 0.5f) * g.cellH
                val near = abs(pos.x - cx) < g.cellW * 0.3f && abs(pos.y - cy) < g.cellH * 0.28f
                if (near && !isFolder(k)) return Hover.Onto(page, under)
            }
        }
        // any cell on the grid, empty or not
        return Hover.Insert(page, idx)
    }

    val handler = remember(model) {
        object : ItemHandler {
            override fun tap(key: String, bounds: Rect) {
                if (isFolder(key)) openFolder = key
                else {
                    launchOrigin = bounds.center
                    scope.launch {
                        launchZoom.animateTo(1f, tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing))
                        // if the app never opened, don't leave home zoomed in
                        kotlinx.coroutines.delay(2500)
                        if (launchZoom.value == 1f) launchZoom.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 240f))
                    }
                    actions.launch(key, bounds); openFolder = null; drawerOpen = false; openCategory = null
                }
            }
            override fun menu(key: String, bounds: Rect) {
                buzz(HapticFeedbackType.LongPress)
                menuAnchor = bounds
                menuKey = key
            }
            override fun dragStart(key: String, source: Source, at: Offset) {
                if (model.lockLayout) return
                buzz(HapticFeedbackType.LongPress)
                // an open category stays (invisible) until the drag ends, so a drag that started
                // in it keeps going; dragEnd closes it
                menuKey = null
                editMode = true
                drag.start(key, source, at)
                if (source is Source.Library && pager.currentPage >= model.pages.size) {
                    scope.launch { pager.animateScrollToPage(model.pages.lastIndex) }
                }
            }
            override fun dragMove(at: Offset) {
                drag.pointer = at
                val h = hoverAt(at)
                if (h != drag.hover) {
                    if (h is Hover.Onto || h is Hover.Dock || h is Hover.Remove) buzz(HapticFeedbackType.TextHandleMove)
                    drag.hover = h
                }
            }
            override fun enterEdit() {
                menuKey = null
                editMode = true
            }
            override fun dragEnd() {
                val k = drag.key
                val src = drag.source
                val h = drag.hover
                // Apply the move while the preview still shows it, then end the drag, so the
                // layout never flashes back to the old order.
                if (k != null && src != null) when (h) {
                    is Hover.Dock -> model.moveToDock(k, src, h.index)
                    is Hover.Insert -> model.moveToPage(k, src, h.page, h.index)
                    is Hover.Onto -> model.dropOnto(k, src, h.page, h.target)
                    Hover.Remove -> {
                        if (src is Source.InFolder) model.detach(k, src)
                        model.removeFromHome(k)
                    }
                    Hover.None -> Unit
                }
                drag.end()
                model.tidy()
                if (src is Source.InFolder) openFolder = null
                if (src is Source.Library) { drawerOpen = false; openCategory = null }
            }
        }
    }

    // Safety net: leaving edit mode always clears any drag that didn't finish.
    LaunchedEffect(editMode) {
        if (!editMode && drag.active) { drag.end(); model.tidy(); openCategory = null }
    }

    // Drag to a screen edge and hold: flip to the next page (a new page appears at the end).
    LaunchedEffect(drag.active) {
        var held = 0
        val edge = with(density) { 36.dp.toPx() }
        while (drag.active) {
            delay(60)
            val x = drag.pointer.x
            val dir = when { x < edge -> -1; x > screenW - edge -> 1; else -> 0 }
            if (dir == 0) { held = 0; continue }
            held += 60
            if (held >= 480) {
                held = 0
                val target = pager.currentPage + dir
                if (target >= 0) {
                    if (target >= model.pages.size) model.ensurePage(target)
                    pager.animateScrollToPage(target, animationSpec = spring(dampingRatio = 0.85f, stiffness = 320f))
                }
            }
        }
    }

    val home by homePresses.collectAsStateCompat()
    LaunchedEffect(home) {
        if (home > 0) {
            closeAll()
            pager.animateScrollToPage(0, animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f))
        }
    }
    val resumed by resumes.collectAsStateCompat()
    LaunchedEffect(resumed) {
        if (launchZoom.value > 0f) launchZoom.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 240f))
    }
    LaunchedEffect(model.lockLayout) { if (model.lockLayout) editMode = false }

    val anyOverlay = searchOpen || menuKey != null || homeMenu || widgetPicker || openFolder != null ||
        openCategory != null || drawerOpen || settingsOpen || styleOpen
    BackHandler(anyOverlay || editMode) {
        when {
            settingsOpen -> settingsOpen = false
            styleOpen -> styleOpen = false
            widgetPicker -> widgetPicker = false
            menuKey != null -> menuKey = null
            homeMenu -> homeMenu = false
            openFolder != null -> openFolder = null
            openCategory != null -> openCategory = null
            searchOpen -> searchOpen = false
            drawerOpen -> drawerOpen = false
            else -> editMode = false
        }
    }

    val blur by animateDpAsState(if (anyOverlay && !drag.active && !settingsOpen && !styleOpen) 26.dp else 0.dp, tween(240), label = "blur")
    val pull = with(density) { 90.dp.toPx() }
    val onLibrary = pager.currentPage >= model.pages.size

    // Wallpaper blur (Android's window blur; skipped when the system has blur off, e.g. battery
    // saver): the home screen blur from Home settings, rising to full blur on the App Library.
    val libBlurPx = with(density) { 36.dp.toPx() }
    val homeBlurMaxPx = with(density) { 48.dp.toPx() }
    LaunchedEffect(pager, drawerMode) {
        snapshotFlow {
            val home = if (model.homeBlur) model.homeBlurAmount.coerceIn(0f, 1f) * homeBlurMaxPx else 0f
            val lib = if (drawerMode) 0f
                else (pager.currentPage + pager.currentPageOffsetFraction - (model.pages.size - 1)).coerceIn(0f, 1f)
            val r = home + (maxOf(home, libBlurPx) - home) * lib
            (r / 4f).toInt() * 4
        }.collect { r ->
            runCatching { (actions as? android.app.Activity)?.window?.setBackgroundBlurRadius(r) }
        }
    }

    CompositionLocalProvider(
        LocalIconShape provides IconShapes.shape(model.iconShape),
        LocalIconBorder provides (if (model.iconTheme == org.driftvane.launcher.IconTheme.OUTLINE) soften(model.accent, 0.35f).copy(alpha = 0.9f) else null),
        LocalResumeTick provides resumed,
    ) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    val p = (drawerReveal.value - drawerPull / screenH).coerceIn(0f, 1f)
                    val z = launchZoom.value
                    val sc = (1f - 0.06f * p) * (1f + 0.14f * z)
                    scaleX = sc; scaleY = sc; alpha = (1f - 0.7f * p) * (1f - 0.9f * z)
                    if (z > 0f && size.width > 0f) {
                        transformOrigin = TransformOrigin(
                            (launchOrigin.x / size.width).coerceIn(0f, 1f),
                            (launchOrigin.y / size.height).coerceIn(0f, 1f),
                        )
                    }
                }
                .blur(blur)
                .pointerInput(editMode, onLibrary, model.swipeDown, drawerMode) {
                    if (editMode || onLibrary) return@pointerInput
                    var dy = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dy = 0f },
                        onVerticalDrag = { _, d ->
                            dy += d
                            // the drawer comes up under your finger
                            if (drawerMode && !drawerOpen) {
                                val f = (-dy / screenH).coerceIn(0f, 1f)
                                scope.launch { drawerReveal.snapTo(f) }
                            }
                        },
                        onDragEnd = {
                            if (dy > pull) when (model.swipeDown) {
                                SwipeDown.SEARCH -> searchOpen = true
                                SwipeDown.NOTIFICATIONS -> actions.expandNotifications()
                            }
                            if (drawerMode && !drawerOpen) {
                                if (dy < -pull) drawerOpen = true
                                else scope.launch { drawerReveal.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 500f)) }
                            }
                        },
                        onDragCancel = {
                            if (!drawerOpen) scope.launch { drawerReveal.animateTo(0f) }
                        },
                    )
                }
        ) {
            // Pages fill the whole screen; the search pill + dock float on top at the bottom and
            // slide away when you swipe to the App Library, so the library gets the full screen.
            Box(Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pager,
                    userScrollEnabled = !drag.active,
                    beyondViewportPageCount = 2,
                    modifier = Modifier.fillMaxSize().onSizeChanged { sz -> pagerH = sz.height },
                ) { page ->
                    if (page < model.pages.size) {
                        val pageTopLeft = remember { arrayOf(Offset.Zero) }
                        Box(
                            Modifier.fillMaxSize().onGloballyPositioned { pageTopLeft[0] = it.positionInRoot() }
                                .graphicsLayer {
                                    // page swipe animation picked in Home settings ("Slide" = none)
                                    val off = (pager.currentPage - page) + pager.currentPageOffsetFraction
                                    val a = kotlin.math.abs(off).coerceIn(0f, 1f)
                                    when (model.pageAnim) {
                                        PageAnim.FADE -> { translationX = off * size.width; alpha = 1f - a }
                                        PageAnim.ZOOM -> { scaleX = 1f - 0.18f * a; scaleY = 1f - 0.18f * a; alpha = 1f - 0.6f * a }
                                        PageAnim.CUBE -> {
                                            cameraDistance = 12f * this.density
                                            transformOrigin = TransformOrigin(if (off > 0f) 1f else 0f, 0.5f)
                                            rotationY = -90f * off.coerceIn(-1f, 1f)
                                        }
                                        else -> {}
                                    }
                                }
                                .then(frameBackground(model, if (page == 0) pageTopLeft else null))
                        ) {
                            // empty space: long-press for the home menu, tap to leave edit mode
                            Box(
                                Modifier.matchParentSize().pointerInput(editMode) {
                                    detectTapGestures(
                                        onLongPress = { at ->
                                            buzz(HapticFeedbackType.LongPress)
                                            val p = pageTopLeft[0] + at
                                            homeAnchor = Rect(p.x - 1f, p.y - 1f, p.x + 1f, p.y + 1f)
                                            homeMenu = true
                                        },
                                        onTap = { if (editMode) editMode = false },
                                    )
                                }
                            )
                            Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 8.dp, bottom = bottomBarDp)) {
                                HomePage(model, page, editMode, drag, handler, widget)
                            }
                        }
                    } else {
                        // dark tint behind the library (with the blur above, or on its own if blur is off)
                        Box(Modifier.fillMaxSize().background(Color(0x8C05070C)).then(frameBackground(model, null)).statusBarsPadding()) {
                            AppLibrary(model, handler,
                                onSearch = { searchOpen = true },
                                onSettings = { settingsOpen = true },
                                onOpenCategory = { openCategory = it })
                        }
                    }
                }
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .onSizeChanged { bottomBarH = it.height }
                        .graphicsLayer {
                            val lib = if (drawerMode || drag.active) 0f
                            else (pager.currentPage + pager.currentPageOffsetFraction - (model.pages.size - 1)).coerceIn(0f, 1f)
                            translationY = (bottomBarH + 40f) * lib
                            alpha = 1f - lib
                        }
                ) {
                // slim strip: page dots (or the Search pill if turned on); edit buttons float above it
                val morphBar = isMorphLike(model.topStyle) && model.searchPill
                Box(Modifier.fillMaxWidth().height(if (morphBar) 62.dp else if (model.searchPill) 48.dp else 16.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.animation.AnimatedVisibility(
                        (model.showDots || morphBar) && !editMode, enter = fadeIn(), exit = fadeOut(),
                    ) {
                        if (morphBar) {
                            MorphSearchBar(model, pageCount, pager.currentPage, pager.isScrollInProgress) { searchOpen = true }
                        } else if (model.searchPill) {
                            SearchPill(model, pageCount, pager.currentPage, pager.isScrollInProgress) { searchOpen = true }
                        } else {
                            PageDots(pageCount, pager.currentPage, model.accent)
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        editMode,
                        modifier = Modifier.offset(y = if (model.searchPill) 0.dp else (-22).dp),
                        enter = fadeIn() + scaleIn(initialScale = 0.85f),
                        exit = fadeOut() + scaleOut(targetScale = 0.85f),
                    ) {
                        // requiredHeight: the strip above the dock is only 16dp tall (page dots), so
                        // without this the "+ Widget" and "Done" buttons got squashed flat.
                        Row(
                            Modifier.fillMaxWidth().requiredHeight(48.dp).padding(horizontal = 18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Pill("+ Widget", Color(0x66FFFFFF).copy(alpha = 0.18f)) { widgetPicker = true }
                            if (isMorphLike(model.topStyle)) Pill("Style", Color(0x66FFFFFF).copy(alpha = 0.18f)) { styleOpen = true }
                            Pill("Done", model.accent) { editMode = false }
                        }
                    }
                }
                if (model.showDock) Dock(model, editMode, drag, handler)
                else Spacer(Modifier.navigationBarsPadding().height(12.dp))
                }
            }

        }

        // Remove pill at the top while something is lifted: drop an app, widget or tile on it
        val lifting = drag.active || drag.widgetDrag
        val overRemove = drag.hover is Hover.Remove || drag.widgetOverRemove
        androidx.compose.animation.AnimatedVisibility(
            editMode && lifting,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 6.dp),
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
        ) {
            val grow by androidx.compose.animation.core.animateFloatAsState(if (overRemove) 1.12f else 1f, spring(0.55f), label = "rm")
            Row(
                Modifier.graphicsLayer { scaleX = grow; scaleY = grow }
                    .onGloballyPositioned { drag.removeBounds = it.boundsInRoot() }
                    .background(if (overRemove) Color(0xFFE5484D) else Color(0x40FF5A6A), RoundedCornerShape(50))
                    .border(1.5.dp, if (overRemove) Color.White else Color(0x99FFB3BA), RoundedCornerShape(50))
                    .padding(horizontal = 22.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Remove", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.W700)
            }
        }
        LaunchedEffect(lifting) { if (!lifting) drag.removeBounds = Rect.Zero }

        // the icon under your finger while dragging
        val dk = drag.key
        if (dk != null) {
            val size = 60f * model.iconScale
            val half = with(density) { (size / 2).dp.toPx() }
            Box(
                Modifier
                    .offset { IntOffset((drag.pointer.x - half).toInt(), (drag.pointer.y - half).toInt()) }
                    .graphicsLayer { scaleX = 1.12f; scaleY = 1.12f }
            ) { HomeItem(model, dk, size, false) }
        }

        val hideForDrag = drag.active
        Overlay(openFolder != null, onDismiss = { openFolder = null }, center = true, hidden = hideForDrag && drag.source is Source.InFolder) {
            val id = openFolder
            if (id != null) FolderOverlay(model, id, handler)
        }
        LaunchedEffect(drawerOpen) {
            if (drawerOpen) drawerReveal.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 380f))
            else drawerReveal.animateTo(0f, spring(dampingRatio = 1f, stiffness = 420f))
        }
        if (drawerOpen || drawerReveal.value > 0.001f) {
            Box(
                Modifier.fillMaxSize()
                    .offset { IntOffset(0, ((1f - drawerReveal.value) * screenH + drawerPull).toInt()) }
                    .graphicsLayer {
                        alpha = if (hideForDrag && drag.source is Source.Library) 0f
                        else (0.4f + 0.6f * drawerReveal.value) - (drawerPull / (screenW * 2f)).coerceIn(0f, 0.5f)
                    }
                    .background(Color(0xD90B0A0D), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .statusBarsPadding()
                    .nestedScroll(drawerScroll)
            ) { AppDrawer(model, handler, onClose = { drawerOpen = false }, onOpenCategory = { openCategory = it }) { searchOpen = true } }
        }
        // search sits above the App Library, so searching from the drawer opens on top of it
        Overlay(searchOpen) { SearchOverlay(model, actions::launch) { searchOpen = false } }
        Overlay(openCategory != null, onDismiss = { openCategory = null }, center = true, hidden = hideForDrag) {
            val c = openCategory
            if (c != null) CategorySheet(model, c, handler)
        }
        Overlay(homeMenu, onDismiss = { homeMenu = false }, plain = true) {
            HomeMenu(model, homeAnchor,
                onEdit = { homeMenu = false; editMode = true },
                onWidgets = { homeMenu = false; widgetPicker = true },
                onSettings = { homeMenu = false; settingsOpen = true },
                onWallpaper = { homeMenu = false; if (isMorphLike(model.topStyle)) styleOpen = true else actions.openWallpaper() })
        }
        Overlay(menuKey != null, onDismiss = { menuKey = null }, plain = true) {
            val k = menuKey
            if (k != null) ItemMenu(model, k, menuAnchor,
                onEdit = { menuKey = null; openFolder = null; editMode = true },
                onInfo = { menuKey = null; actions.appInfo(k) },
                onUninstall = { menuKey = null; actions.uninstall(k) },
                onShortcut = { sc -> menuKey = null; model.startShortcut(sc) },
                onOpenFolder = { menuKey = null; openFolder = k },
                onClose = { menuKey = null })
        }
        Overlay(widgetPicker, onDismiss = { widgetPicker = false }, center = true) {
            WidgetPicker(model, remember { actions.widgetProviders() }, onTile = { id ->
                widgetPicker = false
                model.addTile(id, pager.currentPage.coerceAtMost(model.pages.lastIndex))
            }) { info ->
                widgetPicker = false
                editMode = false
                actions.pickWidget(info, pager.currentPage.coerceAtMost(model.pages.lastIndex))
            }
        }
        Overlay(styleOpen, onDismiss = { styleOpen = false }) {
            StyleSheet(model) { styleOpen = false; actions.openWallpaper() }
        }
        AnimatedVisibility(
            settingsOpen,
            enter = fadeIn() + slideInVertically(spring(dampingRatio = 0.9f, stiffness = 380f)) { it / 4 },
            exit = fadeOut() + slideOutVertically { it / 4 },
        ) { SettingsPage(model) { settingsOpen = false } }
        // first-boot setup: pick your home screen
        AnimatedVisibility(model.loaded && !model.setupDone, enter = fadeIn(), exit = fadeOut()) {
            SetupFlow(model)
        }
    }
    }
}

@Composable
private fun Overlay(
    visible: Boolean,
    onDismiss: (() -> Unit)? = null,
    center: Boolean = false,
    hidden: Boolean = false,
    plain: Boolean = false,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible,
        enter = if (plain) fadeIn(tween(160))
        else fadeIn(tween(180)) + scaleIn(initialScale = 0.94f, animationSpec = spring(dampingRatio = 0.8f, stiffness = 420f)),
        exit = if (plain) fadeOut(tween(140)) else fadeOut(tween(150)) + scaleOut(targetScale = 0.96f),
    ) {
        if (onDismiss == null) {
            content()
        } else {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = if (hidden) 0f else 1f }
                    .background(Color(0x55000000))
                    .clickable(remember { MutableInteractionSource() }, indication = null) { onDismiss() },
                contentAlignment = if (center) Alignment.Center else Alignment.BottomCenter,
            ) { content() }
        }
    }
}

@Composable
private fun Pill(text: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.background(color, RoundedCornerShape(50)).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) { Text(text, color = Color.White, fontWeight = FontWeight.W600, fontSize = 15.sp) }
}

/**
 * DriftHome's pill above the dock: says "Search" (tap to search), and turns into page dots
 * while you swipe between pages.
 */
@Composable
private fun SearchPill(model: LauncherModel, count: Int, current: Int, swiping: Boolean, onSearch: () -> Unit) {
    var showDots by remember { mutableStateOf(false) }
    LaunchedEffect(swiping, current) {
        if (swiping) showDots = true else { delay(900); showDots = false }
    }
    Box(
        Modifier.height(34.dp).glass(17.dp, model.glassStrength * 0.8f)
            .clickable(remember { MutableInteractionSource() }, indication = null) { onSearch() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.animation.AnimatedContent(
            showDots && count > 1,
            transitionSpec = {
                (fadeIn(tween(160)) + scaleIn(initialScale = 0.9f)) togetherWith (fadeOut(tween(120)) + scaleOut(targetScale = 0.9f))
            },
            label = "pill",
        ) { dots ->
            if (dots) {
                Box(Modifier.width((count * 13 + 10).dp), contentAlignment = Alignment.Center) {
                    PageDots(count, current, model.accent)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(14.dp)) {
                        val r = size.minDimension * 0.34f
                        val c = androidx.compose.ui.geometry.Offset(size.width * 0.42f, size.height * 0.42f)
                        drawCircle(Color.White, r, c, style = Stroke(size.minDimension * 0.13f))
                        drawLine(Color.White, c + androidx.compose.ui.geometry.Offset(r * 0.72f, r * 0.72f),
                            androidx.compose.ui.geometry.Offset(size.width * 0.92f, size.height * 0.92f),
                            size.minDimension * 0.14f, androidx.compose.ui.graphics.StrokeCap.Round)
                    }
                    Spacer(Modifier.width(7.dp))
                    Text("Search", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.W600)
                }
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int, accent: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(count) { i ->
            val w by animateDpAsState(if (i == current) 16.dp else 7.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "dot")
            Box(
                Modifier.padding(horizontal = 3.dp).height(7.dp).width(w)
                    .background(if (i == current) Color.White else Color(0x59FFFFFF), RoundedCornerShape(50))
            )
        }
    }
}

@Composable
private fun Dock(model: LauncherModel, editMode: Boolean, drag: DragState, handler: ItemHandler) {
    val dk = drag.key
    val base = model.dock.filter { it != dk }
    val h = drag.hover
    val order = when {
        dk == null -> model.dock.toList()
        h is Hover.Dock -> base.toMutableList().apply { add(h.index.coerceIn(0, base.size), dk) }
        dk in model.dock -> base + dk
        else -> model.dock.toList()
    }
    val size = 58f * model.iconScale
    Row(
        Modifier.navigationBarsPadding().padding(horizontal = 14.dp).padding(bottom = 10.dp)
            .fillMaxWidth()
            .onGloballyPositioned { drag.dockBounds = it.boundsInRoot() }
            .then(dockModifier(model))
            .padding(vertical = 14.dp)
            .height((size + 4).dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (order.isEmpty()) {
            Text("Drag apps here", color = Color(0xFFA6ADB2), fontWeight = FontWeight.W700, fontSize = 13.sp)
        }
        order.forEach { k ->
            key(k) {
                var pressed by remember { mutableStateOf(false) }
                val topLeft = remember { arrayOf(Offset.Zero) }
                val bounds = remember { arrayOf(Rect.Zero) }
                val hidden = dk == k
                Box(
                    Modifier
                        .onGloballyPositioned { topLeft[0] = it.positionInRoot() }
                        .graphicsLayer { alpha = if (hidden) 0f else 1f }
                        .iconGestures(
                            editMode = editMode,
                            rootTopLeft = { topLeft[0] },
                            onPress = { pressed = it },
                            onTap = { handler.tap(k, bounds[0]) },
                            onMenu = { handler.menu(k, bounds[0]) },
                            onDragStart = { handler.dragStart(k, Source.Dock, it) },
                            onDragMove = { handler.dragMove(it) },
                            onDragEnd = { handler.dragEnd() },
                        )
                ) {
                    HomeItem(model, k, size, false, pressed, onBounds = { bounds[0] = it })
                    if (editMode && !hidden && !model.lockLayout) RemoveBadge(Modifier.align(Alignment.TopStart).offset((-8).dp, (-8).dp)) {
                        model.toggleDock(k)
                    }
                }
            }
        }
    }
}


/**
 * Frame style: pages get a solid dark background. On page one, the frame card is cut out of
 * it so your real wallpaper shows through the window.
 */
private fun frameBackground(model: LauncherModel, pageTopLeft: Array<Offset>?): Modifier {
    if (!model.showGlance || model.topStyle != "frame") return Modifier
    return Modifier
        .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
        .drawBehind {
            drawRect(FrameInk)
            val hole = model.frameHole
            if (pageTopLeft != null && hole != Rect.Zero) {
                val tl = hole.topLeft - pageTopLeft[0]
                drawRoundRect(
                    Color.Black, topLeft = tl, size = hole.size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(28.dp.toPx()),
                    blendMode = androidx.compose.ui.graphics.BlendMode.Clear,
                )
            }
        }
}
