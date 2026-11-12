package org.driftvane.launcher.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.driftvane.launcher.Accents
import org.driftvane.launcher.IconTheme
import org.driftvane.launcher.LauncherModel
import org.driftvane.launcher.LibraryStyle
import org.driftvane.launcher.PageAnim
import org.driftvane.launcher.SwipeDown

private enum class SPage(val title: String) {
    ROOT("Home settings"), ICONS("Icons"), HOME("Home screen"), DOCK("Dock"), LIBRARY("App Library"),
    GESTURES("Gestures & feel"), LOOK("Look"), HIDDEN("Hidden apps"),
}

/** Full-screen Home settings with sub-pages that slide in, like a phone's own Settings. */
@Composable
fun SettingsPage(model: LauncherModel, onClose: () -> Unit) {
    var page by remember { mutableStateOf(SPage.ROOT) }
    BackHandler { if (page == SPage.ROOT) onClose() else page = SPage.ROOT }
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xF2100E16), Color(0xF7070609))))
            .swallowTaps()
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState != SPage.ROOT
                (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 420f)) { if (forward) it else -it / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally(spring(dampingRatio = 0.9f, stiffness = 420f)) { if (forward) -it / 3 else it } + fadeOut())
            },
            label = "settings",
        ) { p ->
            LazyColumn(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { Header(p, model.accent, onBack = { page = SPage.ROOT }, onDone = onClose) }
                when (p) {
                    SPage.ROOT -> {
                        item { Preview(model) }
                        item {
                            Group {
                                NavRow("Icons", "Shape, theme, size", G.SHAPE, Color(0xFF8CD0F0)) { page = SPage.ICONS }
                                NavRow("Home screen", "Grid, clock card, search pill", G.GRID, Color(0xFFFF8A3D)) { page = SPage.HOME }
                                NavRow("Dock", dockName(model.dockStyle), G.DOCK, Color(0xFF7CD992)) { page = SPage.DOCK }
                                NavRow("App Library", libraryName(model.libraryStyle), G.LIBRARY, Color(0xFFF09CE2), last = true) { page = SPage.LIBRARY }
                            }
                        }
                        item {
                            Group {
                                NavRow("Gestures & feel", "Swipe down, haptics, lock layout", G.HAND, Color(0xFFFFB59C)) { page = SPage.GESTURES }
                                NavRow("Look", "Accent color, glass", G.PALETTE, model.accent) { page = SPage.LOOK }
                                NavRow("Hidden apps", "${model.hiddenApps.size} hidden", G.HIDE, Color(0xFFA6ADB2), last = true) { page = SPage.HIDDEN }
                            }
                        }
                        item {
                            val activity = LocalActivity.current
                            Group {
                                NavRow("Wallpaper & style", "Wallpapers, lock screen clock", G.WALLPAPER, Color(0xFF9D8CFF)) {
                                    runCatching { activity?.startActivity(Intent(Intent.ACTION_SET_WALLPAPER)) }
                                }
                                NavRow("Phone settings", "Open Settings", G.GEAR, Color(0xFF8E8E93), last = true) {
                                    runCatching { activity?.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                                }
                            }
                        }
                        item {
                            Group { ActionRow("Reset home screen layout", Color(0xFFFF6B78)) { model.resetLayout(); onClose() } }
                        }
                        item {
                            Text("DriftHome 0.11 · made for bitDroid", color = Color(0x80FFFFFF), fontSize = 12.sp,
                                fontWeight = FontWeight.W600, modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    SPage.ICONS -> {
                        item { Preview(model) }
                        item {
                            Group(title = "Shape") {
                                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    IconShapes.all.forEach { (id, name) -> ShapeChoice(model, id, name) }
                                }
                            }
                        }
                        item {
                            Group(title = "Theme") {
                                IconTheme.all.forEachIndexed { i, (id, name) ->
                                    CheckRow(name, themeHint(id), model.iconTheme == id, model.accent, last = i == IconTheme.all.lastIndex) { model.changeTheme(id) }
                                }
                            }
                        }
                        item {
                            Group(title = "Size") {
                                Seg(listOf("S" to 1f, "M" to 1.1f, "L" to 1.18f, "XL" to 1.26f), model.iconScale, model.accent) { model.changeIconScale(it) }
                            }
                        }
                        item {
                            Group(title = "Labels") {
                                ToggleRow("Show app names", model.showLabels, model.accent) { model.changeLabels(it) }
                                ToggleRow("Colorful stack cards", model.stackColorful, model.accent) { model.changeStackColorful(it) }
                                Seg(listOf("Small" to 11f, "Medium" to 12f, "Large" to 13.5f), model.labelSize, model.accent) { model.changeLabelSize(it) }
                            }
                        }
                    }
                    SPage.HOME -> {
                        item {
                            Group(title = "Wallpaper blur") {
                                ToggleRow("Blur the wallpaper on the home screen", model.homeBlur, model.accent) { model.changeHomeBlur(it) }
                                if (model.homeBlur) {
                                    Slider(
                                        value = model.homeBlurAmount,
                                        onValueChange = { model.changeHomeBlurAmount(it) },
                                        valueRange = 0.1f..1f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = model.accent,
                                            activeTrackColor = model.accent,
                                            inactiveTrackColor = Color(0x33FFFFFF),
                                        ),
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                                    )
                                }
                            }
                        }
                        item {
                            Group(title = "Page animation") {
                                Seg(PageAnim.all, model.pageAnim, model.accent) { model.changePageAnim(it) }
                            }
                        }
                        item {
                            Group(title = "Home style") {
                                model.homeStyles.forEachIndexed { i, (id, n) ->
                                    CheckRow(if (id == model.devFavStyle) "$n · Dev's Fav" else n, styleHint(id), model.topStyle == id, model.accent) { model.applyHomeStyle(id) }
                                }
                                ActionRow("Run setup again", model.accent) { model.restartSetup(); onClose() }
                            }
                        }
                        item {
                            Group(title = "Grid") {
                                Seg(listOf("4 columns" to 4, "5 columns" to 5, "6 columns" to 6), model.columns, model.accent) { model.changeColumns(it) }
                            }
                        }
                        item {
                            val ctx = androidx.compose.ui.platform.LocalContext.current
                            Group(title = "Now playing") {
                                NavRow(
                                    "Show what's playing",
                                    if (hasMediaAccess(ctx)) "On: the song shows under the clock" else "Turn on notification access for DriftHome",
                                    G.SPARK, model.accent, last = true,
                                ) { openMediaAccessSettings(ctx) }
                            }
                        }
                        item {
                            Group {
                                ToggleRow("Top of first page", model.showGlance, model.accent) { model.changeGlance(it) }
                                ToggleRow("Panel behind apps", model.gridPanel, model.accent) { model.changeGridPanel(it) }
                                ToggleRow("Page dots", model.showDots, model.accent) { model.changeDots(it) }
                                ToggleRow("Search pill above the dock", model.searchPill, model.accent, last = true) { model.changeSearchPill(it) }
                            }
                        }
                    }
                    SPage.DOCK -> {
                        item {
                            Group {
                                ToggleRow("Show dock", model.showDock, model.accent, last = true) { model.changeDock(it) }
                            }
                        }
                        item {
                            Group(title = "Style") {
                                listOf("tonal" to "Tonal", "line" to "Line", "glass" to "Tinted card", "clear" to "Clear", "solid" to "Solid").forEachIndexed { i, (id, n) ->
                                    CheckRow(n, null, model.dockStyle == id, model.accent, last = i == 4) { model.changeDockStyle(id) }
                                }
                            }
                        }
                    }
                    SPage.LIBRARY -> {
                        item {
                            Group(title = "Style") {
                                listOf(
                                    Triple(LibraryStyle.FOLDERS, "Folders", "All / Categories tabs, folders, search at the bottom"),
                                    Triple(LibraryStyle.SMART, "Smart categories", "Suggestions, recently added, auto-sorted groups"),
                                    Triple(LibraryStyle.CARDS, "Category cards", "2×2 cards for each category"),
                                    Triple(LibraryStyle.LIST, "A–Z list", "Every app in one list"),
                                    Triple(LibraryStyle.DRAWER, "Swipe-up drawer", "No library page; swipe up anywhere"),
                                ).forEachIndexed { i, (id, n, h) ->
                                    CheckRow(n, h, model.libraryStyle == id, model.accent, last = i == 4) { model.changeLibrary(id) }
                                }
                            }
                        }
                    }
                    SPage.GESTURES -> {
                        item {
                            Group(title = "Swipe down on home") {
                                listOf(SwipeDown.SEARCH to "Search", SwipeDown.NOTIFICATIONS to "Notifications", SwipeDown.NOTHING to "Nothing")
                                    .forEachIndexed { i, (id, n) ->
                                        CheckRow(n, null, model.swipeDown == id, model.accent, last = i == 2) { model.changeSwipeDown(id) }
                                    }
                            }
                        }
                        item {
                            Group {
                                ToggleRow("Haptic feedback", model.haptics, model.accent) { model.changeHaptics(it) }
                                ToggleRow("Lock home layout", model.lockLayout, model.accent, last = true) { model.changeLock(it) }
                            }
                        }
                    }
                    SPage.LOOK -> {
                        item {
                            Group(title = "Accent") {
                                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Accents.forEachIndexed { i, c ->
                                        val ring by animateColorAsState(if (i == model.accentIndex) Color.White else Color.Transparent, label = "r")
                                        Box(Modifier.size(46.dp).border(3.dp, ring, CircleShape).padding(6.dp)
                                            .background(c, CircleShape).clickable { model.changeAccent(i) })
                                    }
                                }
                            }
                        }
                        item {
                            Group(title = "Glass") {
                                Seg(listOf("Light" to 0.5f, "Normal" to 0.72f, "Strong" to 0.9f), model.glassStrength, model.accent) { model.changeGlass(it) }
                            }
                        }
                    }
                    SPage.HIDDEN -> {
                        val all = model.apps.values.sortedBy { it.label.lowercase() }
                        item {
                            Text("Hidden apps don't show on home, in the App Library or in search.", color = Color(0xB3FFFFFF),
                                fontSize = 14.sp, modifier = Modifier.padding(horizontal = 6.dp))
                        }
                        items(all, key = { it.key }) { app ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x12FFFFFF))
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CompositionLocalProvider(LocalIconShape provides IconShapes.shape(model.iconShape)) { AppTile(app, 38f) }
                                Spacer(Modifier.width(14.dp))
                                Text(app.label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.W600, modifier = Modifier.weight(1f))
                                Switch(app.key in model.hiddenApps, { model.setHidden(app.key, it) },
                                    colors = SwitchDefaults.colors(checkedTrackColor = model.accent))
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

@Composable
private fun Header(p: SPage, accent: Color, onBack: () -> Unit, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            if (p != SPage.ROOT) {
                Row(Modifier.clip(RoundedCornerShape(50)).clickable { onBack() }.padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Glyph(G.BACK, accent, 22.dp)
                    Text("Settings", color = accent, fontSize = 17.sp, fontWeight = FontWeight.W600)
                }
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.clip(RoundedCornerShape(50)).background(accent).clickable { onDone() }.padding(horizontal = 18.dp, vertical = 8.dp)) {
                Text("Done", color = Color.White, fontWeight = FontWeight.W700, fontSize = 15.sp)
            }
        }
        Text(p.title, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.W700, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
    }
}

/** Live preview: a few of your real apps in the current shape, theme, size and dock style. */
@Composable
private fun Preview(model: LauncherModel) {
    val sample = model.visibleApps().sortedBy { it.label.lowercase() }.take(model.columns.coerceAtMost(5))
    CompositionLocalProvider(LocalIconShape provides IconShapes.shape(model.iconShape)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp))
                .background(Brush.linearGradient(listOf(model.accent.copy(alpha = 0.55f), Color(0xFF241C3A), Color(0xFF0E1A2B))))
                .padding(18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                sample.forEach { a ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AppTile(a, 52f * model.iconScale)
                        if (model.showLabels) {
                            Spacer(Modifier.height(6.dp))
                            Text(a.label, style = labelStyle(model.labelSize), maxLines = 1,
                                modifier = Modifier.width((52f * model.iconScale + 10).dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (model.showDock) {
                Row(
                    Modifier.fillMaxWidth().then(dockModifier(model)).padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) { model.dock.mapNotNull { model.apps[it] }.take(4).forEach { AppTile(it, 44f * model.iconScale) } }
            }
        }
    }
}

/** Dock background for the chosen dock style. */
fun dockModifier(model: LauncherModel): Modifier = when (model.dockStyle) {
    "clear" -> Modifier
    // a thin accent line above the dock icons
    "line" -> Modifier.drawBehind {
        drawLine(model.accent.copy(alpha = 0.35f), androidx.compose.ui.geometry.Offset(24f, 0f),
            androidx.compose.ui.geometry.Offset(size.width - 24f, 0f), 2f)
    }
    "solid" -> Modifier.clip(RoundedCornerShape(32.dp)).background(Color(0xE6141218))
    // solid tonal pill in the accent
    "tonal" -> Modifier.clip(RoundedCornerShape(34.dp)).background(Tonal.strong(model.accent))
    else -> Modifier.glass(32.dp, model.glassStrength * 0.85f)
}

private fun styleHint(id: String) = when (id) {
    "morph" -> "Tiles and apps in one shape; hold anything to move it"
    "orbit" -> "Apps on a ring around the clock; spin it"
    "frame" -> "Your wallpaper in a window with the clock"
    "split" -> "Swipeable cards on top, apps below"
    "stack" -> "A deck of cards you flick through"
    "stock" -> "Classic grid with app names, like stock"
    "dashboard" -> "Big clock with glance cards"
    else -> "Info on the left, apps on the right"
}

private fun dockName(id: String) = when (id) { "clear" -> "Clear"; "solid" -> "Solid"; "line" -> "Line"; "tonal" -> "Tonal"; else -> "Tinted card" }
private fun libraryName(id: String) = when (id) {
    LibraryStyle.FOLDERS -> "Folders"; LibraryStyle.CARDS -> "Category cards"; LibraryStyle.LIST -> "A–Z list"; LibraryStyle.DRAWER -> "Swipe-up drawer"; else -> "Smart categories"
}
private fun themeHint(id: String) = when (id) {
    IconTheme.THEMED -> "Icons tinted with your accent color"
    IconTheme.LIGHT -> "Light tiles, colored symbols"
    IconTheme.GLASS -> "App symbols on a clear tile"
    IconTheme.OUTLINE -> "Accent symbols on dark tiles with an outline"
    IconTheme.MONO -> "Clean white symbols on dark tiles"
    else -> "Each app's own icon"
}

@Composable
private fun Group(title: String? = null, content: @Composable () -> Unit) {
    Column {
        if (title != null) {
            Text(title.uppercase(), color = Color(0x99FFFFFF), fontSize = 12.sp, fontWeight = FontWeight.W700, letterSpacing = 1.2.sp,
                modifier = Modifier.padding(start = 18.dp, bottom = 8.dp))
        }
        Column(Modifier.fillMaxWidth().glass(24.dp, 0.75f)) { content() }
    }
}

@Composable
private fun IconBadge(glyph: G, color: Color) {
    Box(Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)).background(color), contentAlignment = Alignment.Center) {
        Glyph(glyph, Color.White, 19.dp)
    }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().padding(start = 62.dp).height(0.6.dp).background(Color(0x22FFFFFF)))

@Composable
private fun NavRow(title: String, sub: String?, glyph: G, color: Color, last: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(glyph, color)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.W600)
            if (sub != null) Text(sub, color = Color(0x99FFFFFF), fontSize = 13.sp)
        }
        Glyph(G.CHEVRON, Color(0x66FFFFFF), 18.dp)
    }
    if (!last) Divider()
}

@Composable
private fun ToggleRow(title: String, value: Boolean, accent: Color, last: Boolean = false, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.W600, modifier = Modifier.weight(1f))
        Switch(value, onChange, colors = SwitchDefaults.colors(checkedTrackColor = accent))
    }
    if (!last) Box(Modifier.fillMaxWidth().padding(start = 18.dp).height(0.6.dp).background(Color(0x22FFFFFF)))
}

@Composable
private fun CheckRow(title: String, sub: String?, selected: Boolean, accent: Color, last: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.W600)
            if (sub != null) Text(sub, color = Color(0x99FFFFFF), fontSize = 13.sp)
        }
        val c by animateColorAsState(if (selected) accent else Color.Transparent, label = "chk")
        Box(Modifier.size(24.dp).clip(CircleShape).background(c).border(2.dp, if (selected) accent else Color(0x55FFFFFF), CircleShape),
            contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(9.dp).background(Color.White, CircleShape))
        }
    }
    if (!last) Box(Modifier.fillMaxWidth().padding(start = 18.dp).height(0.6.dp).background(Color(0x22FFFFFF)))
}

@Composable
private fun ActionRow(title: String, color: Color, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), contentAlignment = Alignment.Center) {
        Text(title, color = color, fontSize = 16.sp, fontWeight = FontWeight.W700)
    }
}

@Composable
private fun <T> Seg(options: List<Pair<String, T>>, selected: T, accent: Color, onPick: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(10.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x1FFFFFFF)).padding(3.dp)) {
        options.forEach { (label, value) ->
            val bg by animateColorAsState(if (value == selected) accent else Color.Transparent, label = "s")
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(bg).clickable { onPick(value) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = Color.White, fontWeight = FontWeight.W700, fontSize = 14.sp) }
        }
    }
}

@Composable
private fun ShapeChoice(model: LauncherModel, id: String, name: String) {
    val selected = model.iconShape == id
    val ring by animateColorAsState(if (selected) model.accent else Color.Transparent, label = "sh")
    val app = remember(model.apps.size) { model.visibleApps().minByOrNull { it.label.lowercase() } }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { model.changeShape(id) }) {
        Box(Modifier.size(50.dp).border(2.5.dp, ring, IconShapes.shape(id)).padding(4.dp), contentAlignment = Alignment.Center) {
            if (app != null) Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.fillMaxSize().aspectRatio(1f).clip(IconShapes.shape(id)))
            else Box(Modifier.fillMaxSize().clip(IconShapes.shape(id)).background(Color(0x55FFFFFF)))
        }
        Spacer(Modifier.height(4.dp))
        Text(name, color = if (selected) Color.White else Color(0x99FFFFFF), fontSize = 10.sp, fontWeight = FontWeight.W600)
    }
}
