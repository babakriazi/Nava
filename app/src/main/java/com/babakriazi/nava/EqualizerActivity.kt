package com.babakriazi.nava

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.babakriazi.nava.playback.MusicService

class EqualizerActivity : AppCompatActivity() {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 48, 40, 40)
            setBackgroundColor(0xFF0A0E12.toInt())
        }

        val toolbar = com.google.android.material.appbar.MaterialToolbar(this).apply {
            title = "Equalizer"
            setTitleTextColor(0xFFC9A227.toInt())
            setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel)
            setNavigationOnClickListener { finish() }
        }
        root.addView(toolbar)

        var sessionId = MusicService.audioSessionId
        if (sessionId == 0) {
            sessionId = MusicService.playerInstance?.audioSessionId ?: 0
        }

        if (sessionId == 0) {
            val tv = TextView(this).apply {
                text = "Start playing a song, then open Equalizer again."
                setTextColor(0xFF9A9588.toInt())
                textSize = 15f
                setPadding(0, 64, 0, 0)
                gravity = Gravity.CENTER
            }
            root.addView(tv)
            setContentView(root)
            return
        }

        try {
            equalizer = Equalizer(Int.MAX_VALUE, sessionId).apply { enabled = true }
            bassBoost = BassBoost(Int.MAX_VALUE, sessionId).apply { enabled = true; setStrength(0) }
            virtualizer = Virtualizer(Int.MAX_VALUE, sessionId).apply { enabled = true; setStrength(0) }
        } catch (e: Exception) {
            Toast.makeText(this, "EQ error: ${e.message}", Toast.LENGTH_LONG).show()
        }

        val eq = equalizer
        if (eq == null) {
            val tv = TextView(this).apply {
                text = "Equalizer could not attach to this audio session."
                setTextColor(0xFF9A9588.toInt())
                textSize = 14f
                setPadding(0, 48, 0, 0)
            }
            root.addView(tv)
            setContentView(root)
            return
        }

        // Enable switch
        val enableRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 24, 0, 16)
        }
        val enableLabel = TextView(this).apply {
            text = "Enabled"
            setTextColor(0xFFE8E4D9.toInt())
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val enableSwitch = SwitchCompat(this).apply {
            isChecked = eq.enabled
            setOnCheckedChangeListener { _, checked ->
                eq.enabled = checked
                bassBoost?.enabled = checked
                virtualizer?.enabled = checked
            }
        }
        enableRow.addView(enableLabel)
        enableRow.addView(enableSwitch)
        root.addView(enableRow)

        // Bass
        root.addView(sectionLabel("Bass Boost"))
        root.addView(makeSeek(0, 1000, 0) { progress ->
            try { bassBoost?.setStrength(progress.toShort()) } catch (_: Exception) {}
        })

        // Virtualizer
        root.addView(sectionLabel("Virtualizer"))
        root.addView(makeSeek(0, 1000, 0) { progress ->
            try { virtualizer?.setStrength(progress.toShort()) } catch (_: Exception) {}
        })

        // Bands
        val bands = eq.numberOfBands.toInt()
        val minLevel = eq.bandLevelRange[0].toInt()
        val maxLevel = eq.bandLevelRange[1].toInt()

        for (i in 0 until bands) {
            val band = i.toShort()
            val freqHz = eq.getCenterFreq(band) / 1000
            val label = if (freqHz >= 1000) "${freqHz / 1000} kHz" else "$freqHz Hz"
            root.addView(sectionLabel(label))

            val current = (eq.getBandLevel(band) - minLevel)
            root.addView(makeSeek(0, maxLevel - minLevel, current) { progress ->
                try {
                    eq.setBandLevel(band, (progress + minLevel).toShort())
                } catch (_: Exception) {}
            })
        }

        setContentView(root)
    }

    private fun sectionLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(0xFFC9A227.toInt())
            textSize = 13f
            setPadding(0, 28, 0, 6)
        }
    }

    private fun makeSeek(min: Int, max: Int, current: Int, onChange: (Int) -> Unit): SeekBar {
        return SeekBar(this).apply {
            this.max = max
            this.progress = current.coerceIn(min, max)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) onChange(progress)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
    }

    override fun onDestroy() {
        // Keep effects alive while music plays — do not release on leave
        super.onDestroy()
    }
}
