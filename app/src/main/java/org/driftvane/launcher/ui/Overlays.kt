package org.driftvane.launcher.ui

import android.appwidget.AppWidgetProviderInfo
import android.content.pm.PackageManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import org.driftvane.launcher.AppShortcut
import org.driftvane.launcher.LauncherModel
import org.driftvane.launcher.Source
import org.driftvane.launcher.isFolder

/** Blocks taps from falling through to whatever is behind a card. */
@Composable
fun Modifier.swallowTaps(): Modifier = clickable(remember { MutableInteractionSource() }, indication = null) {}

/**
 * Places a [card] right under (or above, if there's no room) [anchor], with the long-pressed
 * icon [lifted] floating above the blur at its original spot. Springs open from the icon.
 */
@Composable
fun AnchoredMenu(anchor: Rect, lifted: (@Composable () -> Unit)?, card: @Composable () -> Unit) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 460f)) }
    val below = remember { mutableStateOf(true) }
    Layout(
        content = {
            Box(Modifier.graphicsLayer {
                val s = 1f + 0.08f * appear.value
                scaleX = s; scaleY = s
            }) { lifted?.invoke() }
            Box(Modifier.graphicsLayer {
                val a = appear.value
                alpha = a.coerceIn(0f, 1f)
                scaleX = 0.82f + 0.18f * a; scaleY = 0.82f + 0.18f * a
                transformOrigin = TransformOrigin(0.5f, if (below.value) 0f else 1f)
            }) { card() }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val margin = 14.dp.roundToPx()
        val gap = 14.dp.roundToPx()
        val liftedP = measurables[0].measure(loose)
        val maxW = minOf(constraints.maxWidth - margin * 2, 300.dp.roundToPx())
        val cardP = measurables[1].measure(loose.copy(maxWidth = maxW))
        val hasAnchor = anchor.width > 0f || anchor.height > 0f
        val fitsBelow = anchor.bottom + gap + cardP.height < constraints.maxHeight - margin * 3
        below.value = !hasAnchor || fitsBelow
        val x = if (!hasAnchor) (constraints.maxWidth - cardP.width) / 2
        else (anchor.center.x - cardP.width / 2f).toInt().coerceIn(margin, constraints.maxWidth - margin - cardP.width)
        val y = when {
            !hasAnchor -> constraints.maxHeight - cardP.height - margin * 4
            fitsBelow -> anchor.bottom.toInt() + gap
            else -> (anchor.top.toInt() - gap - cardP.height).coerceAtLeast(margin * 3)
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            if (hasAnchor) {
                liftedP.place((anchor.center.x - liftedP.width / 2f).toInt(), (anchor.center.y - liftedP.height / 2f).toInt())
            }
            cardP.place(x, y)
        }
    }
}

/** A row in a glass menu: label on the left, line icon on the right. */
@Composable
fun MenuRow(text: String, glyph: G, color: Color = Color.White, divider: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column {
        Row(
            Modifier.fillMaxWidth()
                .background(if (pressed) Color(0x22FFFFFF) else Color.Transparent)
                .clickable(interaction, indication = null, onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, color = color, fontWeight = FontWeight.W600, fontSize = 16.sp, modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Glyph(glyph, color, 20.dp)
        }
        if (divider) Box(Modifier.fillMaxWidth().padding(start = 18.dp).height(0.6.dp).background(Color(0x26FFFFFF)))
    }
}

/** Square quick-action button at the top of a menu. */
@Composable
private fun QuickAction(text: String, glyph: G, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(
        modifier.clip(RoundedCornerShape(18.dp))
            .background(if (pressed) Color(0x33FFFFFF) else Color(0x1AFFFFFF))
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Glyph(glyph, color, 22.dp)
        Spacer(Modifier.height(6.dp))
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.W600)
    }
}

/** Long-press on an app or folder: lifted icon + liquid-glass menu with app shortcuts. */
@Composable
fun ItemMenu(
    model: LauncherModel,
    key: String,
    anchor: Rect,
    onEdit: () -> Unit,
    onInfo: () -> Unit,
    onUninstall: () -> Unit,
    onShortcut: (AppShortcut) -> Unit,
    onOpenFolder: () -> Unit,
    onClose: () -> Unit,
) {
    val folder = isFolder(key)
    if (!folder && model.apps[key] == null) return
    val density = LocalDensity.current
    val shortcuts = remember(key) { if (folder) emptyList() else model.shortcutsFor(key) }
    val inDock = key in model.dock
    val onHome = inDock || model.pages.any { key in it.items } || model.folders.values.any { key in it.apps }
    val iconSize = with(density) { anchor.width.toDp().value }.takeIf { it > 10f } ?: 60f

    AnchoredMenu(
        anchor = anchor,
        lifted = { HomeItem(model, key, iconSize, false) },
    ) {
        Column(Modifier.width(290.dp).glass(26.dp, 0.95f).swallowTaps().padding(vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!model.lockLayout) QuickAction("Edit", G.EDIT, Color.White, Modifier.weight(1f), onEdit)
                if (folder) QuickAction("Open", G.GRID, Color.White, Modifier.weight(1f), onOpenFolder)
                else QuickAction("Info", G.INFO, Color.White, Modifier.weight(1f), onInfo)
                if (folder) QuickAction("Ungroup", G.REMOVE, Color(0xFFFF6B78), Modifier.weight(1f)) { model.removeFromHome(key); onClose() }
                else QuickAction("Delete", G.TRASH, Color(0xFFFF6B78), Modifier.weight(1f), onUninstall)
            }
            shortcuts.forEach { sc -> MenuRow(sc.label, G.SHORTCUT) { onShortcut(sc) } }
            if (!folder && !model.lockLayout) {
                MenuRow(if (inDock) "Remove from dock" else "Add to dock", G.DOCK) { model.toggleDock(key); onClose() }
            }
            if (!model.lockLayout) {
                if (onHome) MenuRow(if (folder) "Remove folder" else "Remove from home", G.REMOVE) { model.removeFromHome(key); onClose() }
                else MenuRow("Add to home", G.HOME) { model.addToHome(key); onClose() }
            }
            if (!folder) MenuRow("Hide app", G.HIDE, divider = false) { model.setHidden(key, true); onClose() }
        }
    }
}

/** Long-press on an empty spot: menu right where you pressed. */
@Composable
fun HomeMenu(
    model: LauncherModel,
    anchor: Rect,
    onEdit: () -> Unit,
    onWidgets: () -> Unit,
    onSettings: () -> Unit,
    onWallpaper: () -> Unit,
) {
    AnchoredMenu(anchor = anchor, lifted = null) {
        Column(Modifier.width(260.dp).glass(26.dp, 0.95f).swallowTaps().padding(vertical = 6.dp)) {
            if (!model.lockLayout) {
                MenuRow("Edit home screen", G.EDIT) { onEdit() }
                MenuRow("Add widget", G.WIDGET) { onWidgets() }
            }
            MenuRow("Wallpaper & style", G.WALLPAPER) { onWallpaper() }
            MenuRow("Home settings", G.GEAR, divider = false) { onSettings() }
        }
    }
}

/** Open folder: big liquid-glass card. Rename it, open apps, or drag apps out. */
@Composable
fun FolderOverlay(model: LauncherModel, id: String, handler: ItemHandler) {
    val folder = model.folders[id] ?: return
    var name by remember(id) { mutableStateOf(folder.name) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicTextField(
            value = name,
            onValueChange = { name = it; model.renameFolder(id, it) },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.W700, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(model.accent),
            modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
        )
        Column(Modifier.fillMaxWidth().glass(38.dp, 0.9f).swallowTaps().padding(horizontal = 12.dp, vertical = 20.dp)) {
            folder.apps.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    row.forEach { k ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            var pressed by remember { mutableStateOf(false) }
                            val topLeft = remember { arrayOf(Offset.Zero) }
                            val bounds = remember { arrayOf(Rect.Zero) }
                            Box(
                                Modifier.onGloballyPositioned { topLeft[0] = it.positionInRoot() }
                                    .iconGestures(
                                        editMode = false,
                                        rootTopLeft = { topLeft[0] },
                                        onPress = { pressed = it },
                                        onTap = { handler.tap(k, bounds[0]) },
                                        onMenu = { handler.menu(k, bounds[0]) },
                                        onDragStart = { handler.dragStart(k, Source.InFolder(id), it) },
                                        onDragMove = { handler.dragMove(it) },
                                        onDragEnd = { handler.dragEnd() },
                                    )
                            ) { HomeItem(model, k, 58f, true, pressed, onBounds = { bounds[0] = it }) }
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("${folder.apps.size} apps · hold and drag to take one out", color = Color(0xCCFFFFFF), fontSize = 13.sp, fontWeight = FontWeight.W600)
    }
}

/** Pick a widget: grouped by app, with previews. */
@Composable
fun WidgetPicker(
    model: LauncherModel,
    providers: List<AppWidgetProviderInfo>,
    onTile: (Int) -> Unit = {},
    onPick: (AppWidgetProviderInfo) -> Unit,
) {
    val context = LocalContext.current
    val pm = context.packageManager
    fun appName(pkg: String) = runCatching {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))).toString()
    }.getOrDefault(pkg)
    val grouped = remember(providers) {
        providers.groupBy { it.provider.packageName }.toList().sortedBy { (pkg, _) -> appName(pkg).lowercase() }
    }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp)
            .glass(34.dp, 0.95f).swallowTaps().padding(18.dp)
    ) {
        Text("Widgets", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.W700)
        Text("Tap one to add it to this page", color = Color(0xB3FFFFFF), fontSize = 14.sp, fontWeight = FontWeight.W500)
        Spacer(Modifier.height(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // the search bar, if it was removed
            if (!model.searchPill) item(key = "search-bar") {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0x14FFFFFF))
                    .clickable { onTile(DriftTiles.SEARCH) }.padding(14.dp)) {
                    Text("Search bar", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.W700)
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(50))
                        .border(1.2.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(50)).background(Color(0x22FFFFFF)),
                        contentAlignment = Alignment.CenterStart) {
                        Text("Search", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.W600, modifier = Modifier.padding(start = 18.dp))
                    }
                }
            }
            // DriftHome's own tiles (only the ones not already on a page)
            val placed = model.allWidgetIds().toSet()
            val tiles = DriftTiles.all.filter { it.first !in placed }
            if (tiles.isNotEmpty()) item(key = "drift-tiles") {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0x14FFFFFF)).padding(14.dp)) {
                    Text("bitHome tiles", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.W700)
                    tiles.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { (id, name) ->
                                val (sx, sy) = DriftTiles.span(id)
                                Box(
                                    Modifier.weight(1f).height(if (sy > 1) 120.dp else 64.dp).clip(tileShape(model.iconShape)),
                                ) {
                                    DriftTile(model, org.driftvane.launcher.WidgetItem(id, 0, 0, sx, sy), Modifier.fillMaxSize())
                                    Box(Modifier.matchParentSize().clickable { onTile(id) })
                                    Text(name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.W700,
                                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                                            .background(Color(0x66000000), RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 2.dp))
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            grouped.forEach { (pkg, list) ->
                item(key = pkg) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0x14FFFFFF)).padding(14.dp)) {
                        Text(appName(pkg), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.W700)
                        list.forEach { info ->
                            val label = runCatching { info.loadLabel(pm) }.getOrDefault("Widget")
                            val preview = remember(info) {
                                runCatching {
                                    val d = info.loadPreviewImage(context, 0) ?: info.loadIcon(context, 0)
                                    val w = d.intrinsicWidth.takeIf { it > 0 } ?: 300
                                    val h = d.intrinsicHeight.takeIf { it > 0 } ?: 200
                                    val f = 420f / maxOf(w, h)
                                    d.toBitmap((w * f).toInt().coerceAtLeast(1), (h * f).toInt().coerceAtLeast(1)).asImageBitmap()
                                }.getOrNull()
                            }
                            Column(
                                Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(18.dp))
                                    .clickable { onPick(info) }.padding(4.dp)
                            ) {
                                Box(
                                    Modifier.fillMaxWidth().heightIn(min = 70.dp, max = 160.dp).clip(RoundedCornerShape(16.dp))
                                        .background(Color(0x1FFFFFFF)),
                                    contentAlignment = Alignment.Center,
                                ) { if (preview != null) Image(preview, null, Modifier.padding(8.dp)) }
                                Text(label, color = Color(0xE6FFFFFF), fontSize = 13.sp, fontWeight = FontWeight.W600,
                                    modifier = Modifier.padding(top = 6.dp, start = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
