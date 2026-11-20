package org.driftvane.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.driftvane.launcher.AppEntry
import org.driftvane.launcher.LauncherModel

private val FolderBg = Color(0xFF1B1F27)
private val TabBg = Color(0xFF1B1F27)
private val TabOn = Color(0xFF3A404C)
private val Hint = Color(0xFFB8BEC9)

/**
 * The default App Library: "All" / "Categories" tabs at the top, category folders (three big
 * icons and a mini grid of the rest), and a search bar at the bottom.
 */
@Composable
fun FolderLibrary(
    model: LauncherModel,
    handler: ItemHandler,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onOpenCategory: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(1) } // 0 = All, 1 = Categories
    val key = listOf(model.apps.size, model.apps.keys.hashCode(), model.hiddenApps.size, model.iconTheme, model.accentIndex)
    val groups = remember(key) { model.categories() }
    val all = remember(key) { model.visibleApps().sortedBy { it.label.lowercase() } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // top: All / Categories, and a menu button for Home settings
            Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp)) {
                Row(
                    Modifier.align(Alignment.Center).clip(RoundedCornerShape(50)).background(TabBg).padding(3.dp)
                ) {
                    TabChip("All", tab == 0) { tab = 0 }
                    TabChip("Categories", tab == 1) { tab = 1 }
                }
                Box(
                    Modifier.align(Alignment.CenterEnd).size(40.dp).clip(CircleShape).clickable { onSettings() },
                    contentAlignment = Alignment.Center,
                ) { Glyph(G.MORE, Color.White, 22.dp) }
            }

            if (tab == 1) {
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f).padding(horizontal = 14.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(groups.chunked(2), key = { "F" + it.first().first }) { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            pair.forEach { (title, apps) ->
                                FolderTile(title, apps, model, handler, Modifier.weight(1f)) { onOpenCategory(title) }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f).padding(horizontal = 10.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(all.chunked(4), key = { "A" + it.first().key }) { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { app ->
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    LibIcon(app, 54f, true, handler, model)
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }

        // bottom: search
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(52.dp).clip(RoundedCornerShape(26.dp)).background(TabBg)
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(26.dp))
                .clickable { onSearch() }.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(G.SEARCH, Hint, 20.dp)
            Spacer(Modifier.width(10.dp))
            Text("Search", color = Hint, fontSize = 16.sp, fontWeight = FontWeight.W500)
        }
    }
}

@Composable
private fun TabChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) TabOn else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = if (selected) FontWeight.W600 else FontWeight.W500)
    }
}

/** A category folder: three big icons you can open directly, and a mini grid of the rest. */
@Composable
private fun FolderTile(
    title: String,
    apps: List<AppEntry>,
    model: LauncherModel,
    handler: ItemHandler,
    modifier: Modifier,
    onOpen: () -> Unit,
) {
    val big = if (apps.size <= 4) apps else apps.take(3)
    val rest = if (apps.size <= 4) emptyList() else apps.drop(3)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(26.dp)).background(FolderBg)
                .clickable(remember { MutableInteractionSource() }, indication = null) { onOpen() }
                .padding(10.dp),
        ) {
            listOf(0, 1).forEach { r ->
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    listOf(0, 1).forEach { c ->
                        val i = r * 2 + c
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            when {
                                i < big.size -> LibIcon(big[i], 54f, false, handler, model)
                                i == 3 && rest.isNotEmpty() -> MiniGrid(rest, onOpen)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.W500,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MiniGrid(apps: List<AppEntry>, onOpen: () -> Unit) {
    Column(
        Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).clickable { onOpen() }.padding(4.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        apps.take(4).chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { Image(it.icon, null, Modifier.size(21.dp).clip(RoundedCornerShape(6.dp))) }
                if (row.size == 1) Spacer(Modifier.size(21.dp))
            }
        }
    }
}
