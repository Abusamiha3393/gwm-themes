package com.sahra.dash

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
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.concurrent.CopyOnWriteArraySet

data class NowPlaying(
    val title: String = "",
    val artist: String = "",
    val playing: Boolean = false,
    val app: String = "",
    val artDataUrl: String = "",
)

/** يتابع ما يُشغَّل الآن (أغنية، راديو) من أي تطبيق وسائط، بعد منح صلاحية الإشعارات. */
object MediaTracker {
    @Volatile var current = NowPlaying()
        private set
    private val listeners = CopyOnWriteArraySet<(NowPlaying) -> Unit>()
    private val main = Handler(Looper.getMainLooper())
    private var manager: MediaSessionManager? = null
    private val controllers = mutableListOf<MediaController>()
    private var lastArtKey: String? = null
    private var lastArt = ""

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

    private fun refresh() {
        val c = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull()
        val md = c?.metadata
        val np = NowPlaying(
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
            artist = (md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty(),
            playing = c?.playbackState?.state == PlaybackState.STATE_PLAYING,
            app = c?.packageName.orEmpty(),
            artDataUrl = art(md),
        )
        if (np != current) {
            current = np
            listeners.forEach { it(np) }
        }
    }

    private fun art(md: MediaMetadata?): String {
        if (md == null) return ""
        val bmp = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: return ""
        val key = "${md.getString(MediaMetadata.METADATA_KEY_TITLE)}|${bmp.width}x${bmp.height}"
        if (key == lastArtKey) return lastArt
        val scaled = Bitmap.createScaledBitmap(bmp, 192, 192 * bmp.height / maxOf(1, bmp.width), true)
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
        lastArtKey = key
        lastArt = "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        return lastArt
    }

    // أوامر التحكم من الثيم
    fun command(cmd: String) {
        val t = controllers.firstOrNull()?.transportControls ?: return
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
