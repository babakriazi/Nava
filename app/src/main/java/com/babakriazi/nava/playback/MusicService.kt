package com.babakriazi.nava.playback

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class MusicService : MediaSessionService() {

    companion object {
        @Volatile
        var audioSessionId: Int = 0

        @Volatile
        var playerInstance: ExoPlayer? = null

        fun readAudioSessionId(player: Any?): Int {
            if (player == null) return 0
            return try {
                val m = player.javaClass.methods.firstOrNull {
                    it.name == "getAudioSessionId" && it.parameterCount == 0
                }
                (m?.invoke(player) as? Int) ?: 0
            } catch (_: Exception) {
                0
            }
        }
    }

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        playerInstance = player
        syncSession(player)

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                syncSession(player)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                syncSession(player)
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                // New track can change audio path — re-apply EQ
                syncSession(player)
            }
        })

        mediaSession = MediaSession.Builder(this, player).build()
    }

    private fun syncSession(player: ExoPlayer) {
        val id = readAudioSessionId(player)
        audioSessionId = id
        if (id != 0) {
            EqEngine.apply(applicationContext, id)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        EqEngine.release()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        playerInstance = null
        audioSessionId = 0
        super.onDestroy()
    }
}
