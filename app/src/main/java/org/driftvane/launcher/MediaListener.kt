package org.driftvane.launcher

import android.app.Notification
import android.app.PendingIntent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lets bitHome see which media is playing, and (bitDroid) which apps have notifications, so
 * icons can show a count, swipe down on an icon fans its notifications out, and long-press
 * Peek can show what's inside. Nothing leaves the phone and nothing is stored.
 */
class MediaListener : NotificationListenerService() {
    override fun onListenerConnected() { refresh() }
    override fun onListenerDisconnected() { AppNotifs.set(emptyMap()) }
    override fun onNotificationPosted(sbn: StatusBarNotification?) { refresh() }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { refresh() }

    private fun refresh() {
        val all = runCatching { activeNotifications?.toList() }.getOrNull() ?: return
        val map = all.asSequence()
            .filter { !it.isOngoing && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .mapNotNull { sbn ->
                val ex = sbn.notification.extras
                val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
                if (title.isBlank() && text.isBlank()) null
                else sbn.packageName to AppNotif(sbn.key, title, text, sbn.postTime, sbn.notification.contentIntent)
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, v) -> v.sortedByDescending { it.time } }
        AppNotifs.set(map)
    }

    fun dismiss(key: String) = runCatching { cancelNotification(key) }

    companion object {
        @Volatile var instance: MediaListener? = null
    }
    override fun onCreate() { super.onCreate(); instance = this }
    override fun onDestroy() { instance = null; super.onDestroy() }
}

data class AppNotif(val key: String, val title: String, val text: String, val time: Long, val intent: PendingIntent?)

/** Notifications per package, newest first. */
object AppNotifs {
    private val _byPkg = MutableStateFlow<Map<String, List<AppNotif>>>(emptyMap())
    val byPkg: StateFlow<Map<String, List<AppNotif>>> = _byPkg
    fun set(m: Map<String, List<AppNotif>>) { _byPkg.value = m }
    fun open(n: AppNotif) { runCatching { n.intent?.send() } }
    fun dismiss(n: AppNotif) { MediaListener.instance?.dismiss(n.key) }
}
