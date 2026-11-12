package org.driftvane.launcher.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.driftvane.launcher.LauncherModel
import org.driftvane.launcher.MediaListener
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class NowPlaying(
    val title: String,
    val artist: String,
    val controller: MediaController,
    /** Album art, if the player shares it. */
    val art: android.graphics.Bitmap? = null,
)

/** What's playing right now, or null (nothing playing, or no notification access yet). */
fun nowPlaying(context: Context): NowPlaying? = runCatching {
    val msm = context.getSystemService(MediaSessionManager::class.java) ?: return null
    val sessions = msm.getActiveSessions(ComponentName(context, MediaListener::class.java))
    val c = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: return null
    val md = c.metadata ?: return null
    val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: return null
    val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
        ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
    val art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
        ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
    NowPlaying(title, artist, c, art)
}.getOrNull()

/**
 * The current player even when it's paused (playing first, then paused), so the Morph music
 * tile can resume it. [playing] says which.
 */
fun mediaNow(context: Context): Pair<NowPlaying, Boolean>? = runCatching {
    val msm = context.getSystemService(MediaSessionManager::class.java) ?: return null
    val sessions = msm.getActiveSessions(ComponentName(context, MediaListener::class.java))
    val c = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        ?: sessions.firstOrNull {
            val st = it.playbackState?.state
            (st == PlaybackState.STATE_PAUSED || st == PlaybackState.STATE_BUFFERING) && it.metadata != null
        }
        ?: return null
    val md = c.metadata ?: return null
    val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: return null
    val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
        ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
    val art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
        ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
    NowPlaying(title, artist, c, art) to (c.playbackState?.state == PlaybackState.STATE_PLAYING)
}.getOrNull()

/** Whether DriftHome may read media sessions (notification access is on). */
fun hasMediaAccess(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    return enabled.contains(ComponentName(context, MediaListener::class.java).flattenToString())
}

fun openMediaAccessSettings(context: Context) {
    val cn = ComponentName(context, MediaListener::class.java).flattenToString()
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, cn)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

private fun batteryLevelOf(context: Context): Int {
    val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    return if (level < 0) 0 else level * 100 / scale
}

/** Mixes [a] toward white by [t] (0..1). */
fun soften(a: Color, t: Float) = Color(
    a.red + (1f - a.red) * t, a.green + (1f - a.green) * t, a.blue + (1f - a.blue) * t, 1f,
)

/**
 * Top of the first page: a huge two-line clock (hours in the accent, minutes softer),
 * the date and battery above it, and what's playing below it.
 */
@Composable
fun BoldClock(model: LauncherModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val accent = model.accent
    val now by produceState(Date()) {
        while (true) {
            value = Date()
            delay((60 - Calendar.getInstance().get(Calendar.SECOND)) * 1000L)
        }
    }
    val battery by produceState(batteryLevelOf(context)) {
        while (true) { value = batteryLevelOf(context); delay(30_000) }
    }
    val playing by produceState<NowPlaying?>(null) {
        while (true) { value = nowPlaying(context); delay(1500) }
    }
    val access by produceState(hasMediaAccess(context)) {
        while (true) { value = hasMediaAccess(context); delay(3000) }
    }
    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    val hours = SimpleDateFormat(if (is24) "HH" else "h", Locale.getDefault()).format(now)
    val minutes = SimpleDateFormat("mm", Locale.getDefault()).format(now)
    val date = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(now)
    val soft = soften(accent, 0.62f)

    Column(modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
        Text("$date · $battery%", color = soft.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.W500)
        Spacer(Modifier.width(1.dp))
        Text(hours, color = accent, fontSize = 104.sp, lineHeight = 94.sp, fontWeight = FontWeight.W700, letterSpacing = (-4).sp)
        Text(minutes, color = soft, fontSize = 104.sp, lineHeight = 94.sp, fontWeight = FontWeight.W700, letterSpacing = (-4).sp)
        val np = playing
        val tap = remember { MutableInteractionSource() }
        Row(
            Modifier.padding(top = 6.dp).clickable(tap, indication = null) {
                if (np == null) {
                    if (!hasMediaAccess(context)) openMediaAccessSettings(context)
                } else {
                    runCatching {
                        val pi = np.controller.sessionActivity ?: error("no session activity")
                        // we're the visible home screen, so let the player's screen open
                        if (android.os.Build.VERSION.SDK_INT >= 34) {
                            val opts = android.app.ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(
                                android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                            )
                            pi.send(context, 0, null, null, null, null, opts.toBundle())
                        } else {
                            pi.send()
                        }
                    }.onFailure {
                        context.packageManager.getLaunchIntentForPackage(np.controller.packageName)?.let {
                            context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                }
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (np != null) {
                Text("Now playing  ", color = accent, fontSize = 14.sp, fontWeight = FontWeight.W600)
                Text(
                    if (np.artist.isNotBlank()) "${np.title} · ${np.artist}" else np.title,
                    color = soft, fontSize = 14.sp, fontWeight = FontWeight.W500, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            } else if (!access) {
                Text("Tap to show what's playing here", color = soft.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.W500)
            } else {
                Text("Nothing playing", color = soft.copy(alpha = 0.55f), fontSize = 14.sp, fontWeight = FontWeight.W500)
            }
        }
    }
}
