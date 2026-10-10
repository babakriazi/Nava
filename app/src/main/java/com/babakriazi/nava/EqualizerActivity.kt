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
import com.babakriazi.nava.data.Prefs
import com.babakriazi.nava.playback.EqEngine
import com.babakriazi.nava.playback.MusicService

class EqualizerActivity : AppCompatActivity() {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

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
            sessionId = MusicService.readAudioSessionId(MusicService.playerInstance)
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

        EqEngine.apply(this, sessionId)

        try {
            equalizer = Equalizer(0, sessionId)
            bassBoost = BassBoost(0, sessionId)
            virtualizer = Virtualizer(0, sessionId)
        } catch (e: Exception) {
            Toast.makeText(this, "EQ error: ${e.message}", Toast.LENGTH_LONG).show()
        }

        val eq = equalizer
        if (eq == null) {
            val tv = TextView(this).apply {
                text = "Equalizer could not attach."
                setTextColor(0xFF9A9588.toInt())
                textSize = 14f
                setPadding(0, 48, 0, 0)
            }
            root.addView(tv)
            setContentView(root)
            return
        }

        val enabled = prefs.getEqEnabled()
        eq.enabled = enabled
        bassBoost?.enabled = enabled
        virtualizer?.enabled = enabled

        try {
            bassBoost?.setStrength(prefs.getBass().toShort())
            virtualizer?.setStrength(prefs.getVirt().toShort())
        } catch (_: Exception) {}

        val minLevel = eq.bandLevelRange[0].toInt()
        val maxLevel = eq.bandLevelRange[1].toInt()
        val bands = eq.numberOfBands.toInt()

        if (prefs.getBandsSaved()) {
            for (i in 0 until bands) {
                try {
                    eq.setBandLevel(i.toShort(), prefs.getBandLevel(i, 0).toShort())
                } catch (_: Exception) {}
            }
        }

        val enableRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 24, 0, 16)
        }
        enableRow.addView(TextView(this).apply {
            text = "Enabled"
            setTextColor(0xFFE8E4D9.toInt())
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        enableRow.addView(SwitchCompat(this).apply {
            isChecked = enabled
            setOnCheckedChangeListener { _, checked ->
                eq.enabled = checked
                bassBoost?.enabled = checked
                virtualizer?.enabled = checked
                prefs.setEqEnabled(checked)
                EqEngine.refreshFromPrefs(this@EqualizerActivity)
            }
        })
        root.addView(enableRow)

        // Gain (0–2000 millibels ≈ 0 to +20 dB)
        val gainLabel = sectionLabel("Gain  +${prefs.getGain() / 100} dB")
        root.addView(gainLabel)
        root.addView(makeSeek(0, 2000, prefs.getGain()) { progress ->
            prefs.setGain(progress)
            gainLabel.text = "Gain  +${progress / 100} dB"
            EqEngine.refreshFromPrefs(this)
        })

        root.addView(sectionLabel("Bass Boost"))
        root.addView(makeSeek(0, 1000, prefs.getBass()) { progress ->
            try {
                bassBoost?.setStrength(progress.toShort())
                prefs.setBass(progress)
                EqEngine.refreshFromPrefs(this)
            } catch (_: Exception) {}
        })

        root.addView(sectionLabel("Virtualizer"))
        root.addView(makeSeek(0, 1000, prefs.getVirt()) { progress ->
            try {
                virtualizer?.setStrength(progress.toShort())
                prefs.setVirt(progress)
                EqEngine.refreshFromPrefs(this)
            } catch (_: Exception) {}
        })

        for (i in 0 until bands) {
            val band = i.toShort()
            val freqHz = eq.getCenterFreq(band) / 1000
            val label = if (freqHz >= 1000) "${freqHz / 1000} kHz" else "$freqHz Hz"
            root.addView(sectionLabel(label))
            val currentLevel = eq.getBandLevel(band).toInt()
            val progress = (currentLevel - minLevel).coerceIn(0, maxLevel - minLevel)
            root.addView(makeSeek(0, maxLevel - minLevel, progress) { prog ->
                try {
                    val level = prog + minLevel
                    eq.setBandLevel(band, level.toShort())
                    prefs.saveBandLevel(i, level)
                    prefs.setBandsSaved(true)
                    EqEngine.refreshFromPrefs(this)
                } catch (_: Exception) {}
            })
        }

        setContentView(root)
    }

    override fun onPause() {
        EqEngine.refreshFromPrefs(this)
        super.onPause()
    }

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(0xFFC9A227.toInt())
        textSize = 13f
        setPadding(0, 28, 0, 6)
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
}
