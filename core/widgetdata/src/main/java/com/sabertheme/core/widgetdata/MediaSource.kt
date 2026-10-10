package com.sabertheme.core.widgetdata

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Being enabled is what grants media-session access. It also reads WhatsApp
 * notifications (and no others) into [ChatNotifications] for the mascot's
 * message cloud.
 */
class MediaListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        ChatNotifications.reset(runCatching { activeNotifications.toList() }.getOrNull().orEmpty())
    }

    override fun onListenerDisconnected() {
        ChatNotifications.reset(emptyList())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let(ChatNotifications::post)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let { ChatNotifications.remove(it.key) }
    }
}

data class MediaData(
    val title: String,
    val artist: String,
    val app: String,
    val art: Bitmap?,
    val playing: Boolean,
    val packageName: String,
    /** VLC-style app whose thumbnails need READ_MEDIA_VIDEO, not granted yet. */
    val needsVideoPermission: Boolean = false,
)

/** A switcher entry (installed apps from [MediaSource.SWITCHER_APPS] only). */
data class MediaApp(val packageName: String, val label: String, val playing: Boolean, val hasSession: Boolean)

/**
 * [selected] is the app the widget controls; [now] is its track, or null when
 * it has no session (or nothing to show) yet.
 */
data class MediaState(val now: MediaData?, val apps: List<MediaApp>, val selected: String?) {
    /** The selected app has nothing loaded: offer to resume it. */
    val resumeApp: MediaApp? get() = if (now == null) apps.firstOrNull { it.packageName == selected } else null
}

/**
 * Active media sessions plus a Spotify / VLC switcher. Which app is shown
 * follows [MediaPicker]; pressing play on an app without a session resumes
 * it through [MediaResumer].
 */
@Singleton
class MediaSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissions: WidgetPermissions,
    scope: WidgetScope,
    private val resumer: MediaResumer,
) : WidgetDataSource<MediaState> {

    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, MediaListenerService::class.java)
    private val main = Handler(Looper.getMainLooper())

    // Main thread only, like the session callbacks. Kept across flow restarts.
    private var controllers: List<MediaController> = emptyList()
    private var shown: String? = null
    private var pick: String? = null
    private var wasPlaying: Set<String> = emptySet()
    private var publish: () -> Unit = {}
    private val art = MediaArt(context.contentResolver)
    private val artLoading = mutableSetOf<String>()
    private var loadArt: (String) -> Unit = {}

    override val state: Flow<WidgetState<MediaState>> = permissions
        .gated(WidgetPermission.NotificationListener) { sessions() }
        .shareIn(scope, WhileShown, replay = 1)

    private val controller: MediaController? get() = controllers.firstOrNull { it.packageName == shown }

    fun playPause() {
        val c = controller
        if (c != null && c.isPlaying()) {
            c.transportControls.pause()
            return
        }
        val pkg = c?.packageName ?: shown ?: SWITCHER_APPS.firstOrNull(::installed) ?: return
        // Starting one app stops the others: Android ignores audio-focus requests from
        // apps started in the background (AudioHardening), so focus alone won't pause them.
        controllers.filter { it.packageName != pkg && it.isPlaying() }.forEach { it.transportControls.pause() }
        if (c != null) {
            c.transportControls.play()
        } else {
            resumer.resume(pkg) { controllers.any { it.packageName == pkg && it.isPlaying() } }
        }
    }

    fun next() {
        controller?.transportControls?.skipToNext()
    }

    fun previous() {
        controller?.transportControls?.skipToPrevious()
    }

    /** Show and control [pkg] (a switcher app), even before it has a session. */
    fun select(pkg: String) {
        pick = pkg
        publish()
    }

    /** Next switcher app after the shown one. */
    fun cycle() {
        val apps = SWITCHER_APPS.filter(::installed)
        if (apps.isEmpty()) return
        select(apps[(apps.indexOf(shown) + 1).mod(apps.size)])
    }

    private fun sessions(): Flow<WidgetState<MediaState>> = callbackFlow {
        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) = publish()

            override fun onPlaybackStateChanged(state: PlaybackState?) = publish()

            override fun onSessionDestroyed() = publish()
        }

        loadArt = { key ->
            if (artLoading.add(key)) {
                launch(Dispatchers.IO) {
                    if (key.startsWith(LOCAL)) art.loadLocal(key.removePrefix(LOCAL)) else art.load(key)
                    withContext(Dispatchers.Main) {
                        artLoading.remove(key)
                        publish()
                    }
                }
            }
        }

        publish = {
            val list = controllers.map { MediaPicker.Session(it.packageName, it.isPlaying()) }
            val result = MediaPicker.pick(list, pick, wasPlaying)
            pick = result.pick
            shown = result.shown
            wasPlaying = list.filter { it.playing }.mapTo(mutableSetOf()) { it.packageName }
            val apps = SWITCHER_APPS.mapNotNull { pkg ->
                label(pkg)?.let { MediaApp(pkg, it, list.any { s -> s.packageName == pkg && s.playing }, list.any { s -> s.packageName == pkg }) }
            }
            trySend(WidgetState.Ready(MediaState(controller?.let(::toData), apps, result.shown)))
        }

        fun track(list: List<MediaController>?) {
            controllers.forEach { it.unregisterCallback(callback) }
            // One controller per app: the system lists the most recent session first.
            controllers = list.orEmpty().distinctBy { it.packageName }
            controllers.forEach { it.registerCallback(callback, main) }
            publish()
        }

        // A video-permission grant should show thumbnails without waiting for a track change.
        launch { permissions.changes.collect { publish() } }

        val changes = MediaSessionManager.OnActiveSessionsChangedListener { track(it) }
        try {
            sessions.addOnActiveSessionsChangedListener(changes, listener, main)
            track(sessions.getActiveSessions(listener))
        } catch (e: SecurityException) {
            trySend(WidgetState.NeedsPermission(WidgetPermission.NotificationListener))
        }
        awaitClose {
            sessions.removeOnActiveSessionsChangedListener(changes)
            controllers.forEach { it.unregisterCallback(callback) }
            controllers = emptyList()
            publish = {}
            loadArt = {}
        }
    }.flowOn(Dispatchers.Main)

    private fun MediaController.isPlaying() = playbackState?.state == PlaybackState.STATE_PLAYING

    private fun installed(pkg: String) = label(pkg) != null

    private fun label(pkg: String): String? = runCatching {
        context.packageManager.run { getApplicationLabel(getApplicationInfo(pkg, 0)).toString() }
    }.getOrNull()

    private fun toData(c: MediaController): MediaData? {
        val meta = c.metadata ?: return null
        val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: return null
        val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty()
        // Better art loads async and wins once cached: the app's own artwork URI, or for
        // LOCAL_ART_APPS the media store thumbnail of the same-titled video.
        val local = c.packageName in LOCAL_ART_APPS
        val videos = local && permissions.granted(WidgetPermission.Videos)
        val key = if (videos) art.localKey(title) else art.uriOf(meta)
        if (key != null && art.needsLoad(key)) loadArt(if (videos) LOCAL + title else key)
        val image = key?.let(art::cached)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
        return MediaData(title, artist, label(c.packageName).orEmpty(), image, c.isPlaying(), c.packageName, needsVideoPermission = local && !videos)
    }

    companion object {
        /** Apps offered in the widget's switcher, in order. */
        val SWITCHER_APPS = listOf("com.spotify.music", "org.videolan.vlc")

        /** Apps whose art comes from the media store by title (see MediaArt). */
        private val LOCAL_ART_APPS = setOf("org.videolan.vlc")
        private const val LOCAL = "local:"
    }
}
