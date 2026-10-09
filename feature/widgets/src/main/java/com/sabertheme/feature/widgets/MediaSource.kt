package com.sabertheme.feature.widgets

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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton

/** Never reads notifications; being enabled is what grants media-session access. */
class MediaListenerService : NotificationListenerService()

data class MediaData(val title: String, val artist: String, val app: String, val art: Bitmap?, val playing: Boolean)

/** The most relevant active media session (playing first); `Ready(null)` when there is none. */
@Singleton
class MediaSource @Inject constructor(
    @ApplicationContext private val context: Context,
    permissions: WidgetPermissions,
    scope: WidgetScope,
) : WidgetDataSource<MediaData?> {

    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, MediaListenerService::class.java)

    @Volatile private var controller: MediaController? = null

    override val state: Flow<WidgetState<MediaData?>> = permissions
        .gated(WidgetPermission.NotificationListener) { sessions() }
        .shareIn(scope, WhileShown, replay = 1)

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() {
        controller?.transportControls?.skipToNext()
    }

    fun previous() {
        controller?.transportControls?.skipToPrevious()
    }

    // Runs on the main thread, where session callbacks arrive too.
    private fun sessions(): Flow<WidgetState<MediaData?>> = callbackFlow {
        val handler = Handler(Looper.getMainLooper())
        var active: MediaController? = null

        fun publish() {
            controller = active
            trySend(WidgetState.Ready(active?.let(::toData)))
        }

        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) = publish()

            override fun onPlaybackStateChanged(state: PlaybackState?) = publish()

            override fun onSessionDestroyed() {
                active?.unregisterCallback(this)
                active = null
                publish()
            }
        }

        fun pick(list: List<MediaController>?) {
            val all = list.orEmpty()
            val next = all.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: all.firstOrNull()
            if (next?.sessionToken != active?.sessionToken) {
                active?.unregisterCallback(callback)
                active = next
                next?.registerCallback(callback, handler)
            }
            publish()
        }

        val changes = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }
        try {
            sessions.addOnActiveSessionsChangedListener(changes, listener, handler)
            pick(sessions.getActiveSessions(listener))
        } catch (e: SecurityException) {
            trySend(WidgetState.NeedsPermission(WidgetPermission.NotificationListener))
        }
        awaitClose {
            sessions.removeOnActiveSessionsChangedListener(changes)
            active?.unregisterCallback(callback)
            controller = null
        }
    }.flowOn(Dispatchers.Main)

    private fun toData(c: MediaController): MediaData? {
        val meta = c.metadata ?: return null
        val title = meta.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() } ?: return null
        val artist = meta.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty()
        val art = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val app = runCatching {
            context.packageManager.run { getApplicationLabel(getApplicationInfo(c.packageName, 0)).toString() }
        }.getOrDefault("")
        return MediaData(title, artist, app, art, c.playbackState?.state == PlaybackState.STATE_PLAYING)
    }
}
