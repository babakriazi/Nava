package com.babakriazi.nava

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.Player
import com.babakriazi.nava.playback.PlayerController
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton

class NowPlayingActivity : AppCompatActivity() {

    private lateinit var txtTitle: TextView
    private lateinit var txtArtist: TextView
    private lateinit var txtPosition: TextView
    private lateinit var txtDuration: TextView
    private lateinit var txtRepeatMode: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var btnPlay: FloatingActionButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnRepeat: ImageButton
    private val handler = Handler(Looper.getMainLooper())
    private var userSeeking = false

    private val tick = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_now_playing)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { finish() }

        txtTitle = findViewById(R.id.txtTitle)
        txtArtist = findViewById(R.id.txtArtist)
        txtPosition = findViewById(R.id.txtPosition)
        txtDuration = findViewById(R.id.txtDuration)
        txtRepeatMode = findViewById(R.id.txtRepeatMode)
        seekBar = findViewById(R.id.seekBar)
        btnPlay = findViewById(R.id.btnPlayPause)
        btnShuffle = findViewById(R.id.btnShuffle)
        btnRepeat = findViewById(R.id.btnRepeat)

        findViewById<ImageButton>(R.id.btnPrev).setOnClickListener { PlayerController.previous() }
        findViewById<ImageButton>(R.id.btnNext).setOnClickListener { PlayerController.next() }
        btnPlay.setOnClickListener { PlayerController.togglePlayPause(); refresh() }

        findViewById<MaterialButton>(R.id.btnEq).setOnClickListener {
            startActivity(Intent(this, EqualizerActivity::class.java))
        }

        btnShuffle.setOnClickListener {
            val c = PlayerController.controller ?: return@setOnClickListener
            c.shuffleModeEnabled = !c.shuffleModeEnabled
            updateModeButtons()
            Toast.makeText(
                this,
                if (c.shuffleModeEnabled) "Shuffle on" else "Shuffle off",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Three-state: Normal → Repeat All → Repeat One → Normal
        btnRepeat.setOnClickListener { cycleRepeat() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) txtPosition.text = format(progress.toLong())
            }
            override fun onStartTrackingTouch(sb: SeekBar?) { userSeeking = true }
            override fun onStopTrackingTouch(sb: SeekBar?) {
                userSeeking = false
                PlayerController.seekTo(seekBar.progress.toLong())
            }
        })

        PlayerController.connect(this) {
            PlayerController.controller?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) { refresh() }
                override fun onMediaItemTransition(item: androidx.media3.common.MediaItem?, reason: Int) { refresh() }
                override fun onRepeatModeChanged(repeatMode: Int) { updateModeButtons() }
                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { updateModeButtons() }
            })
            refresh()
        }
    }

    private fun cycleRepeat() {
        val c = PlayerController.controller ?: return
        val next = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        c.repeatMode = next
        updateModeButtons()
        Toast.makeText(this, txtRepeatMode.text, Toast.LENGTH_SHORT).show()
    }

    private fun updateModeButtons() {
        val c = PlayerController.controller ?: return
        btnShuffle.alpha = if (c.shuffleModeEnabled) 1f else 0.45f

        when (c.repeatMode) {
            Player.REPEAT_MODE_ALL -> {
                btnRepeat.alpha = 1f
                txtRepeatMode.text = "Repeat all"
                txtRepeatMode.setTextColor(0xFFC9A227.toInt())
            }
            Player.REPEAT_MODE_ONE -> {
                btnRepeat.alpha = 1f
                txtRepeatMode.text = "Repeat one"
                txtRepeatMode.setTextColor(0xFF4ECDC4.toInt())
            }
            else -> {
                btnRepeat.alpha = 0.45f
                txtRepeatMode.text = "Normal"
                txtRepeatMode.setTextColor(0xFF9A9588.toInt())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
        refresh()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    private fun refresh() {
        val c = PlayerController.controller ?: return
        val meta = c.mediaMetadata
        txtTitle.text = meta.title ?: ""
        txtArtist.text = meta.artist ?: ""
        btnPlay.setImageResource(
            if (c.isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
        val dur = c.duration.coerceAtLeast(0)
        if (dur > 0 && dur != Long.MAX_VALUE) {
            seekBar.max = dur.toInt()
            txtDuration.text = format(dur)
        }
        updateProgress()
        updateModeButtons()
    }

    private fun updateProgress() {
        if (userSeeking) return
        val c = PlayerController.controller ?: return
        val pos = c.currentPosition
        seekBar.progress = pos.toInt().coerceIn(0, seekBar.max)
        txtPosition.text = format(pos)
    }

    private fun format(ms: Long): String {
        if (ms < 0 || ms == Long.MAX_VALUE) return "0:00"
        val s = (ms / 1000).toInt()
        return "%d:%02d".format(s / 60, s % 60)
    }
}
