package com.babakriazi.nava.playback

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import com.babakriazi.nava.data.Prefs
import kotlin.math.pow

/**
 * EQ + Bass + Virtualizer on audio session.
 * Gain is applied via ExoPlayer.volume (reliable on all devices including Samsung).
 */
object EqEngine {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var attachedSession: Int = -1

    @Synchronized
    fun apply(context: Context, sessionId: Int) {
        if (sessionId == 0) return
        val prefs = Prefs(context.applicationContext)

        if (sessionId == attachedSession && equalizer != null) {
            writeLevels(prefs)
            return
        }

        releaseEffectsOnly()
        attachedSession = sessionId

        try {
            equalizer = Equalizer(0, sessionId)
            bassBoost = BassBoost(0, sessionId)
            virtualizer = Virtualizer(0, sessionId)
            writeLevels(prefs)
        } catch (_: Exception) {
            releaseEffectsOnly()
        }
    }

    @Synchronized
    fun refreshFromPrefs(context: Context) {
        writeLevels(Prefs(context.applicationContext))
    }

    /** Apply digital gain on ExoPlayer. gainMb is millibels 0..2000 → 0..+20 dB */
    fun applyPlayerGain(gainMb: Int) {
        val player = MusicService.playerInstance ?: return
        val db = gainMb / 100.0
        // volume linear = 10^(dB/20); allow > 1.0 for boost past system 100%
        val linear = 10.0.pow(db / 20.0).toFloat().coerceIn(0.05f, 10f)
        try {
            player.volume = linear
        } catch (_: Exception) {}
    }

    private fun writeLevels(prefs: Prefs) {
        val enabled = prefs.getEqEnabled()
        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
            virtualizer?.enabled = enabled
            bassBoost?.setStrength(prefs.getBass().toShort())
            virtualizer?.setStrength(prefs.getVirt().toShort())

            // Gain always applied (independent of EQ toggle) so user hears boost
            applyPlayerGain(prefs.getGain())

            val eq = equalizer ?: return
            if (prefs.getBandsSaved()) {
                val bands = eq.numberOfBands.toInt()
                for (i in 0 until bands) {
                    try {
                        eq.setBandLevel(i.toShort(), prefs.getBandLevel(i, 0).toShort())
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    private fun releaseEffectsOnly() {
        try { equalizer?.release() } catch (_: Exception) {}
        try { bassBoost?.release() } catch (_: Exception) {}
        try { virtualizer?.release() } catch (_: Exception) {}
        equalizer = null
        bassBoost = null
        virtualizer = null
        attachedSession = -1
    }

    @Synchronized
    fun release() {
        releaseEffectsOnly()
        try {
            MusicService.playerInstance?.volume = 1f
        } catch (_: Exception) {}
    }
}
