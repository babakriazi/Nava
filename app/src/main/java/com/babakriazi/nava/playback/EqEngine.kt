package com.babakriazi.nava.playback

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import com.babakriazi.nava.data.Prefs

/**
 * Holds audio effects tied to the player session.
 * Re-applies saved prefs whenever the audio session changes (new song / new player).
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

        // Same session — still refresh levels from prefs (in case UI changed them)
        if (sessionId == attachedSession && equalizer != null) {
            writeLevels(prefs)
            return
        }

        release()
        attachedSession = sessionId

        try {
            equalizer = Equalizer(0, sessionId)
            bassBoost = BassBoost(0, sessionId)
            virtualizer = Virtualizer(0, sessionId)
            writeLevels(prefs)
        } catch (_: Exception) {
            release()
        }
    }

    @Synchronized
    fun refreshFromPrefs(context: Context) {
        if (attachedSession <= 0) return
        writeLevels(Prefs(context.applicationContext))
    }

    private fun writeLevels(prefs: Prefs) {
        val enabled = prefs.getEqEnabled()
        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
            virtualizer?.enabled = enabled
            bassBoost?.setStrength(prefs.getBass().toShort())
            virtualizer?.setStrength(prefs.getVirt().toShort())
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

    @Synchronized
    fun release() {
        try { equalizer?.release() } catch (_: Exception) {}
        try { bassBoost?.release() } catch (_: Exception) {}
        try { virtualizer?.release() } catch (_: Exception) {}
        equalizer = null
        bassBoost = null
        virtualizer = null
        attachedSession = -1
    }
}
