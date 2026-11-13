package org.driftvane.launcher.ui

import android.app.SearchManager
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.driftvane.launcher.LauncherModel

/**
 * bitHome search, Bloom style: a big tonal search pill with a cookie search button, a "top hit"
 * card when you type, your apps on soft Material You tiles, and a web search pill. Everything
 * takes its colors from the wallpaper and pops in with a little bounce.
 */
@Composable
fun SearchOverlay(model: LauncherModel, onLaunch: (String, Rect) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val results = remember(query, model.apps.size, model.hiddenApps.size, model.iconTheme) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) model.visibleApps().sortedBy { it.label.lowercase() }.take(8)
        else model.visibleApps()
            .filter { it.label.lowercase().contains(q) }
            .sortedBy { if (it.label.lowercase().startsWith(q)) 0 else 1 }
    }

    fun webSearch() {
        if (query.isBlank()) return
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        onClose()
    }

    val suggested = remember(model.apps.size, model.hiddenApps.size) {
        model.suggestions(8).ifEmpty { model.visibleApps().sortedBy { it.label.lowercase() }.take(8) }
    }

    // Material You tones from the wallpaper
    val conf = androidx.compose.ui.platform.LocalConfiguration.current
    val tone = remember(conf) {
        fun g(id: Int, fb: Long) = runCatching { Color(context.getColor(id)) }.getOrElse { Color(fb) }
        SearchTones(
            scrim = g(android.R.color.system_neutral1_900, 0xFF141218).copy(alpha = 0.96f),
            field = g(android.R.color.system_accent1_100, 0xFFEADDFF),
            onField = g(android.R.color.system_accent1_900, 0xFF21005D),
            primary = g(android.R.color.system_accent1_800, 0xFF381E72),
            onPrimary = g(android.R.color.system_accent1_100, 0xFFEADDFF),
            card = g(android.R.color.system_accent2_800, 0xFF332D41),
            onCard = g(android.R.color.system_accent2_50, 0xFFF6EDFF),
            sub = g(android.R.color.system_accent2_200, 0xFFCCC2DC),
            hit = g(android.R.color.system_accent1_200, 0xFFD0BCFF),
            onHit = g(android.R.color.system_accent1_900, 0xFF21005D),
            web = g(android.R.color.system_accent3_100, 0xFFFFD8E4),
            onWeb = g(android.R.color.system_accent3_900, 0xFF31111D),
        )
    }

    // the whole screen springs in from a little above, then the results pop in one by one
    val enter = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        enter.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 340f))
    }
    fun Modifier.pop(i: Int) = this.graphicsLayer {
        val e = ((enter.value - i * 0.06f) / 0.8f).coerceIn(0f, 1.2f)
        alpha = e.coerceIn(0f, 1f)
        scaleX = 0.9f + 0.1f * e; scaleY = 0.9f + 0.1f * e
        translationY = (1f - e.coerceAtMost(1f)) * 24.dp.toPx()
    }

    Box(
        Modifier.fillMaxSize()
            .background(tone.scrim)
            .clickable(remember { MutableInteractionSource() }, indication = null) { onClose() }
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(14.dp))
            // the search pill: cookie search button, the field, and a round close button
            Row(
                Modifier.fillMaxWidth().height(60.dp).pop(0)
                    .clip(RoundedCornerShape(30.dp)).background(tone.field)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {}
                    .padding(start = 7.dp, end = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(46.dp).clip(CookieShape()).background(tone.primary)
                        .clickable { webSearch() },
                    contentAlignment = Alignment.Center,
                ) { Glyph(G.SEARCH, tone.onPrimary, 20.dp) }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Search apps and the web", color = tone.onField.copy(alpha = 0.6f), fontSize = 17.sp,
                            fontWeight = FontWeight.W500)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = tone.onField, fontSize = 18.sp, fontWeight = FontWeight.W600),
                        cursorBrush = SolidColor(tone.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            val first = results.firstOrNull()
                            if (first != null && query.isNotBlank()) {
                                onLaunch(first.key, Rect.Zero); onClose()
                            } else webSearch()
                        }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(23.dp))
                        .background(tone.onField.copy(alpha = 0.1f))
                        .clickable { if (query.isNotEmpty()) query = "" else onClose() },
                    contentAlignment = Alignment.Center,
                ) { Text("✕", color = tone.onField, fontSize = 17.sp, fontWeight = FontWeight.W600) }
            }
            Spacer(Modifier.height(18.dp))

            val shown = if (query.isBlank()) suggested else results
            val top = if (query.isNotBlank()) results.firstOrNull() else null

            // top hit: the best match on its own big card, ready to open
            if (top != null) {
                Row(
                    Modifier.fillMaxWidth().pop(1).clip(RoundedCornerShape(28.dp)).background(tone.hit)
                        .clickable { onLaunch(top.key, Rect.Zero); onClose() }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TapIcon(top, 56f, false) { r -> onLaunch(top.key, r); onClose() }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Top hit", color = tone.onHit.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.W600)
                        Text(top.label, color = tone.onHit, fontSize = 20.sp, fontWeight = FontWeight.W700, maxLines = 1)
                    }
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(tone.primary)
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                    ) { Text("Open", color = tone.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.W700) }
                }
                Spacer(Modifier.height(14.dp))
            }

            val rest = if (top != null) shown.drop(1) else shown
            Text(if (query.isBlank()) "Suggested for you" else if (rest.isEmpty() && top != null) "" else "Apps",
                color = tone.sub, fontSize = 14.sp, fontWeight = FontWeight.W700, letterSpacing = 0.6.sp,
                modifier = Modifier.pop(2).padding(start = 6.dp, bottom = 10.dp))
            if (shown.isEmpty()) {
                Text("No apps match “$query”", color = tone.sub, fontSize = 15.sp,
                    modifier = Modifier.pop(2).padding(start = 6.dp, bottom = 12.dp))
            } else if (rest.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).pop(3)
                        .clip(RoundedCornerShape(30.dp)).background(tone.card)
                        .clickable(remember { MutableInteractionSource() }, indication = null) {}
                        .padding(vertical = 18.dp),
                ) {
                    items(rest, key = { it.key }) { app ->
                        Box(contentAlignment = Alignment.Center) {
                            TapIcon(app, 54f, true) { r -> onLaunch(app.key, r); onClose() }
                        }
                    }
                }
            }
            if (query.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().pop(4).clip(RoundedCornerShape(30.dp)).background(tone.web)
                        .clickable { webSearch() }.padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(44.dp).clip(CookieShape()).background(tone.onWeb),
                        contentAlignment = Alignment.Center,
                    ) { Glyph(G.SEARCH, tone.web, 19.dp) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Search the web", color = tone.onWeb, fontSize = 16.sp, fontWeight = FontWeight.W700)
                        Text("“$query”", color = tone.onWeb.copy(alpha = 0.7f), fontSize = 14.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Material You tones for the search screen. */
private data class SearchTones(
    val scrim: Color, val field: Color, val onField: Color,
    val primary: Color, val onPrimary: Color,
    val card: Color, val onCard: Color, val sub: Color,
    val hit: Color, val onHit: Color, val web: Color, val onWeb: Color,
)
