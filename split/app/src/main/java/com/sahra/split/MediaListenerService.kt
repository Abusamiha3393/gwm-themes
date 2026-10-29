package com.sahra.split

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import java.util.concurrent.CopyOnWriteArraySet

data class NowPlaying(
    val title: String = "",
    val artist: String = "",
    val playing: Boolean = false,
    val app: String = "",
    val art: Bitmap? = null,
)

/** يتابع ما يُشغَّل الآن (أغنية، راديو، بودكاست) من أي تطبيق وسائط، بعد منح صلاحية الإشعارات. */
object MediaTracker {
    @Volatile var current = NowPlaying()
        private set
    private val listeners = CopyOnWriteArraySet<(NowPlaying) -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private var manager: MediaSessionManager? = null
    private val controllers = mutableListOf<MediaController>()

    fun addListener(l: (NowPlaying) -> Unit) { listeners.add(l); l(current) }
    fun removeListener(l: (NowPlaying) -> Unit) { listeners.remove(l) }

    fun hasAccess(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
        return enabled.contains(context.packageName)
    }

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { list -> bind(list ?: emptyList()) }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
    }

    fun attach(context: Context) {
        val m = context.getSystemService(MediaSessionManager::class.java) ?: return
        val component = ComponentName(context, MediaListenerService::class.java)
        try {
            m.addOnActiveSessionsChangedListener(sessionsChanged, component, main)
            manager = m
            bind(m.getActiveSessions(component))
        } catch (_: SecurityException) {
            // الصلاحية لم تُمنح بعد
        }
    }

    fun detach() {
        manager?.removeOnActiveSessionsChangedListener(sessionsChanged)
        manager = null
        bind(emptyList())
    }

    private fun bind(list: List<MediaController>) {
        controllers.forEach { it.unregisterCallback(callback) }
        controllers.clear()
        controllers.addAll(list)
        controllers.forEach { it.registerCallback(callback, main) }
        refresh()
    }

    private fun active(): MediaController? =
        controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: controllers.firstOrNull()

    private fun refresh() {
        val c = active()
        val md = c?.metadata
        val np = NowPlaying(
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            artist = (md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty(),
            playing = c?.playbackState?.state == PlaybackState.STATE_PLAYING,
            app = c?.packageName.orEmpty(),
            art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART),
        )
        if (np != current) {
            current = np
            listeners.forEach { it(np) }
        }
    }

    fun command(cmd: String) {
        val t = active()?.transportControls ?: return
        when (cmd) {
            "play" -> t.play()
            "pause" -> t.pause()
            "toggle" -> if (current.playing) t.pause() else t.play()
            "next" -> t.skipToNext()
            "prev" -> t.skipToPrevious()
        }
    }
}

class MediaListenerService : NotificationListenerService() {
    override fun onListenerConnected() = MediaTracker.attach(this)
    override fun onListenerDisconnected() = MediaTracker.detach()
}
