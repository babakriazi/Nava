package com.babakriazi.nava.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.babakriazi.nava.NowPlayingActivity

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
                syncSession(player)
            }
        })

        // Tap on notification body opens Now Playing
        val openIntent = Intent(this, NowPlayingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pending)
            .build()
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
