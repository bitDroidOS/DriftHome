package org.driftvane.launcher.ui

import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.driftvane.launcher.AppEntry
import org.driftvane.launcher.LauncherModel
import org.driftvane.launcher.LibraryStyle
import org.driftvane.launcher.Source

/** An app icon in the library: tap to open, hold for the menu, hold and drag to put it on home. */
@Composable
fun LibIcon(app: AppEntry, size: Float, showLabel: Boolean, handler: ItemHandler, model: LauncherModel) {
    var pressed by remember { mutableStateOf(false) }
    val topLeft = remember { arrayOf(Offset.Zero) }
    val bounds = remember { arrayOf(Rect.Zero) }
    Box(
        Modifier
            .onGloballyPositioned { topLeft[0] = it.positionInRoot() }
            .iconGestures(
                editMode = false,
                rootTopLeft = { topLeft[0] },
                onPress = { pressed = it },
                onTap = { handler.tap(app.key, bounds[0]) },
                onMenu = { handler.menu(app.key, bounds[0]) },
                onDragStart = { handler.dragStart(app.key, Source.Library, it) },
                onDragMove = { handler.dragMove(it) },
                onDragEnd = { handler.dragEnd() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        HomeItem(model, app.key, size, showLabel, pressed, onBounds = { bounds[0] = it })
    }
}

@Composable
private fun SectionTitle(text: String, accent: Color, count: Int? = null) {
    Row(Modifier.padding(start = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), color = accent, fontSize = 12.sp, fontWeight = FontWeight.W600, letterSpacing = 2.sp)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text("$count", color = Color(0xFFA6ADB2), fontSize = 12.sp, fontWeight = FontWeight.W700)
        }
    }
}

@Composable
private fun IconRows(apps: List<AppEntry>, perRow: Int, size: Float, model: LauncherModel, handler: ItemHandler) {
    apps.chunked(perRow).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            row.forEach { app ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    LibIcon(app, size, true, handler, model)
                }
            }
            repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** The App Library page. Style comes from settings: smart categories, 2x2 cards or an A–Z list. */
@Composable
fun AppLibrary(
    model: LauncherModel,
    handler: ItemHandler,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onOpenCategory: (String) -> Unit,
) {
    if (model.libraryStyle == LibraryStyle.FOLDERS) {
        FolderLibrary(model, handler, onSearch, onSettings, onOpenCategory)
        return
    }
    val groups = remember(model.apps.size, model.apps.keys.hashCode(), model.hiddenApps.size, model.iconTheme, model.accentIndex) { model.categories() }
    val accent = model.accent
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("App Library", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.W700, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(44.dp).background(accent.copy(alpha = 0.22f), CircleShape).clickable { onSettings() },
                    contentAlignment = Alignment.Center,
                ) { Text("⚙", color = Color.White, fontSize = 20.sp) }
            }
        }
        item {
            Box(
                Modifier.fillMaxWidth()
                    .then(
                        // Morph / Orbit: the same bold outlined pill as the home search bar
                        if (isMorphLike(model.topStyle)) {
                            Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.10f))
                                .border(1.8.dp, Color.White.copy(alpha = 0.92f), RoundedCornerShape(50))
                        } else Modifier.glass(22.dp, model.glassStrength)
                    )
                    .clickable { onSearch() }
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Text("Search apps", color = if (isMorphLike(model.topStyle)) Color.White else Color(0xFFA6ADB2),
                    fontSize = 16.sp, fontWeight = FontWeight.W700)
            }
        }

        when (model.libraryStyle) {
            LibraryStyle.LIST -> {
                val all = model.visibleApps().sortedBy { it.label.lowercase() }
                val byLetter = all.groupBy { it.label.firstOrNull()?.uppercaseChar()?.takeIf { c -> c.isLetter() } ?: '#' }
                byLetter.forEach { (letter, apps) ->
                    item(key = "L$letter") {
                        Column(Modifier.fillMaxWidth().glass(22.dp, model.glassStrength).padding(vertical = 6.dp)) {
                            Text("$letter", color = accent, fontSize = 14.sp, fontWeight = FontWeight.W700,
                                modifier = Modifier.padding(start = 18.dp, top = 6.dp, bottom = 2.dp))
                            apps.forEach { app -> ListRow(app, model, handler) }
                        }
                    }
                }
            }
            LibraryStyle.CARDS -> {
                groups.chunked(2).forEach { pair ->
                    item(key = "C" + pair.first().first) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            pair.forEach { (title, apps) ->
                                CategoryCard(title, apps, model, handler, Modifier.weight(1f)) { onOpenCategory(title) }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            else -> { // SMART
                val sugg = model.suggestions(model.columns)
                if (sugg.isNotEmpty()) item(key = "sugg") {
                    Column(Modifier.fillMaxWidth().glass(26.dp, model.glassStrength).padding(14.dp)) {
                        SectionTitle("Suggestions", accent)
                        IconRows(sugg, model.columns, 50f, model, handler)
                    }
                }
                val recent = model.recentlyAdded(model.columns)
                if (recent.isNotEmpty()) item(key = "recent") {
                    Column(Modifier.fillMaxWidth().glass(26.dp, model.glassStrength).padding(14.dp)) {
                        SectionTitle("Recently added", accent)
                        IconRows(recent, model.columns, 50f, model, handler)
                    }
                }
                groups.forEach { (title, apps) ->
                    item(key = "S$title") {
                        Column(Modifier.fillMaxWidth().glass(26.dp, model.glassStrength).padding(14.dp)) {
                            SectionTitle(title, accent, apps.size)
                            IconRows(apps, 4, 52f, model, handler)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(140.dp)) }
    }
}

@Composable
private fun ListRow(app: AppEntry, model: LauncherModel, handler: ItemHandler) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibIcon(app, 42f, false, handler, model)
        Spacer(Modifier.width(14.dp))
        Text(app.label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.W700,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Category card: a solid card with the category name and count, and the first apps in a 2x2
 * grid. Tap an app to open it; tap anywhere else on the card (or the +N tile) to see them all.
 */
@Composable
private fun CategoryCard(
    title: String,
    apps: List<AppEntry>,
    model: LauncherModel,
    handler: ItemHandler,
    modifier: Modifier,
    onOpen: () -> Unit,
) {
    val accent = model.accent
    val showAll = apps.size > 4
    val shown = if (showAll) apps.take(3) else apps.take(4)
    Column(
        modifier.solidCard(24.dp)
            .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { onOpen() }
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.W600,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("${apps.size}", color = accent, fontSize = 13.sp, fontWeight = FontWeight.W600)
        }
        Spacer(Modifier.height(10.dp))
        listOf(0, 1).forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(0, 1).forEach { c ->
                    val i = r * 2 + c
                    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                        when {
                            i < shown.size -> LibIcon(shown[i], 50f, false, handler, model)
                            i == 3 && showAll -> Box(
                                Modifier.size(50.dp).clip(LocalIconShape.current).background(accent.copy(alpha = 0.22f))
                                    .clickable { android.util.Log.i("DriftHome", "open category $title"); onOpen() },
                                contentAlignment = Alignment.Center,
                            ) { Text("+${apps.size - 3}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.W600) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Cluster(apps: List<AppEntry>, onOpen: () -> Unit) {
    Column(
        Modifier.size(54.dp).clip(Squircle).background(Color(0x22FFFFFF)).clickable { onOpen() }.padding(6.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        apps.take(4).chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { Image(it.icon, null, Modifier.size(19.dp).clip(Squircle)) }
            }
        }
    }
}

/** All apps of one category, opened from a 2x2 card. Tap opens the app, hold shows its menu. */
@Composable
fun CategorySheet(model: LauncherModel, title: String, handler: ItemHandler) {
    val apps = model.categories().firstOrNull { it.first == title }?.second ?: emptyList()
    Column(
        Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 620.dp)
            .solidCard(28.dp).swallowTaps().padding(18.dp)
    ) {
        Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.W700)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            apps.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    row.forEach { app ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            // tap opens the app; hold and drag puts it on the home screen
                            SheetDragIcon(app, handler)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * An app in an opened category: tap opens it (the proven plain click), hold shows the app menu,
 * hold and drag moves it to the home screen. The hold detector runs before the click, and eats
 * the touch once it's a hold, so a hold never also opens the app.
 */
@Composable
private fun SheetDragIcon(app: AppEntry, handler: ItemHandler) {
    val topLeft = remember { arrayOf(Offset.Zero) }
    val bounds = remember { arrayOf(Rect.Zero) }
    TapIcon(
        app, 54f, true,
        modifier = Modifier
            .onGloballyPositioned { topLeft[0] = it.positionInRoot(); bounds[0] = it.boundsInWindow() }
            .pointerInput(app.key) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val lp = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                    lp.consume()
                    val start = lp.position
                    val slop = viewConfiguration.touchSlop
                    var dragging = false
                    try {
                        drag(lp.id) { c ->
                            if (!dragging && (c.position - start).getDistance() > slop) {
                                dragging = true
                                handler.dragStart(app.key, Source.Library, topLeft[0] + c.position)
                            }
                            if (dragging) handler.dragMove(topLeft[0] + c.position)
                            c.consume()
                        }
                        currentEvent.changes.forEach { it.consume() }
                        if (!dragging) handler.menu(app.key, bounds[0])
                    } finally {
                        if (dragging) handler.dragEnd()
                    }
                }
            },
    ) { b -> handler.tap(app.key, b) }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun SheetIcon(app: AppEntry, model: LauncherModel, handler: ItemHandler) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bounds = remember { arrayOf(Rect.Zero) }
    Box(
        Modifier.combinedClickable(
            interactionSource = interaction,
            indication = null,
            onClick = { handler.tap(app.key, bounds[0]) },
            onLongClick = { handler.menu(app.key, bounds[0]) },
        )
    ) {
        HomeItem(model, app.key, 54f, true, pressed, onBounds = { bounds[0] = it })
    }
}

/** Swipe-up app drawer (when the library style is "Swipe up"). */
@Composable
fun AppDrawer(model: LauncherModel, handler: ItemHandler, onClose: () -> Unit, onSearch: () -> Unit) {
    val all = remember(model.apps.size, model.hiddenApps.size, model.iconTheme, model.accentIndex) { model.visibleApps().sortedBy { it.label.lowercase() } }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        item {
            // grab handle: tap it to close (or pull the list down)
            Box(
                Modifier.fillMaxWidth().clickable { onClose() }.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.width(44.dp).height(5.dp).background(Color(0x66FFFFFF), RoundedCornerShape(50)))
            }
            Box(
                Modifier.fillMaxWidth()
                    .then(
                        // Morph / Orbit: the same bold outlined pill as the home search bar
                        if (isMorphLike(model.topStyle)) {
                            Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.10f))
                                .border(1.8.dp, Color.White.copy(alpha = 0.92f), RoundedCornerShape(50))
                        } else Modifier.glass(22.dp, model.glassStrength)
                    )
                    .clickable { onSearch() }
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Text("Search apps", color = if (isMorphLike(model.topStyle)) Color.White else Color(0xFFA6ADB2),
                    fontSize = 16.sp, fontWeight = FontWeight.W700)
            }
            Spacer(Modifier.height(12.dp))
        }
        items(all.chunked(model.columns), key = { it.first().key }) { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                row.forEach { app ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { LibIcon(app, 54f, true, handler, model) }
                }
                repeat(model.columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}
