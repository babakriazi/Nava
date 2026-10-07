package com.babakriazi.nava.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.babakriazi.nava.data.Prefs
import com.babakriazi.nava.data.Song
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors

object PlayerController {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    var controller: MediaController? = null
        private set

    fun connect(context: Context, onReady: () -> Unit = {}) {
        if (controller != null) {
            onReady()
            return
        }
        val token = SessionToken(context, ComponentName(context, MusicService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync()
        controllerFuture?.addListener({
            controller = controllerFuture?.get()
            onReady()
        }, MoreExecutors.directExecutor())
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0, startPositionMs: Long = 0) {
        val c = controller ?: return
        val items = songs.map { song ->
            MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(song.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .build()
                )
                .build()
        }
        val idx = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        c.setMediaItems(items, idx, startPositionMs)
        c.prepare()
        c.play()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }
    fun seekTo(pos: Long) { controller?.seekTo(pos) }
    fun setShuffle(enabled: Boolean) { controller?.shuffleModeEnabled = enabled }
    fun setRepeat(mode: Int) { controller?.repeatMode = mode }

    fun saveState(context: Context) {
        val c = controller ?: return
        val prefs = Prefs(context)
        val ids = mutableListOf<Long>()
        for (i in 0 until c.mediaItemCount) {
            c.getMediaItemAt(i).mediaId.toLongOrNull()?.let { ids.add(it) }
        }
        if (ids.isNotEmpty()) {
            prefs.setLastSongIds(ids)
            prefs.setLastIndex(c.currentMediaItemIndex.coerceAtLeast(0))
            prefs.setLastPosition(c.currentPosition.coerceAtLeast(0))
            prefs.setShuffle(c.shuffleModeEnabled)
            prefs.setRepeatMode(c.repeatMode)
        }
    }
}
