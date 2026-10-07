package com.babakriazi.nava

import android.media.audiofx.Equalizer
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.babakriazi.nava.playback.PlayerController

class EqualizerActivity : AppCompatActivity() {

    private var equalizer: Equalizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
            setBackgroundColor(0xFF0F1419.toInt())
        }

        val toolbar = com.google.android.material.appbar.MaterialToolbar(this).apply {
            title = getString(R.string.equalizer)
            setTitleTextColor(0xFFC9A227.toInt())
            setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel)
            setNavigationOnClickListener { finish() }
        }
        root.addView(toolbar)

        // MediaController (Media3) does not expose audioSessionId on the Player interface.
        // Use session 0 (output mix) which works for system-wide EQ on most devices.
        // Prefer live session if available via reflection for better isolation.
        val sessionId = resolveAudioSessionId()

        try {
            equalizer = Equalizer(0, sessionId).apply { enabled = true }
        } catch (e: Exception) {
            equalizer = null
        }

        val eq = equalizer
        if (eq == null) {
            val tv = TextView(this).apply {
                text = "Equalizer unavailable — play a song first, then open Equalizer again"
                setTextColor(0xFF9A9588.toInt())
                textSize = 14f
                setPadding(0, 48, 0, 0)
            }
            root.addView(tv)
        } else {
            val bands = eq.numberOfBands.toInt()
            val minLevel = eq.bandLevelRange[0]
            val maxLevel = eq.bandLevelRange[1]

            for (i in 0 until bands) {
                val band = i.toShort()
                val freq = eq.getCenterFreq(band) / 1000
                val label = TextView(this).apply {
                    text = "$freq Hz"
                    setTextColor(0xFFE8E4D9.toInt())
                    textSize = 13f
                    setPadding(0, 24, 0, 4)
                }
                root.addView(label)

                val seek = SeekBar(this).apply {
                    max = (maxLevel - minLevel).toInt()
                    progress = (eq.getBandLevel(band) - minLevel).toInt()
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                            if (fromUser) {
                                try {
                                    eq.setBandLevel(band, (progress + minLevel).toShort())
                                } catch (_: Exception) {}
                            }
                        }
                        override fun onStartTrackingTouch(sb: SeekBar?) {}
                        override fun onStopTrackingTouch(sb: SeekBar?) {}
                    })
                }
                root.addView(seek)
            }
        }

        setContentView(root)
    }

    private fun resolveAudioSessionId(): Int {
        val c = PlayerController.controller ?: return 0
        return try {
            // Try common method names across Media3 / framework Player implementations
            val method = c.javaClass.methods.firstOrNull {
                it.name == "getAudioSessionId" && it.parameterCount == 0
            }
            (method?.invoke(c) as? Int) ?: 0
        } catch (_: Exception) {
            0
        }
    }

    override fun onDestroy() {
        try {
            equalizer?.release()
        } catch (_: Exception) {}
        equalizer = null
        super.onDestroy()
    }
}
