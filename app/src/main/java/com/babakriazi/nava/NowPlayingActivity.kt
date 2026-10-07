package com.babakriazi.nava

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.Player
import com.babakriazi.nava.playback.PlayerController
import com.google.android.material.floatingactionbutton.FloatingActionButton

class NowPlayingActivity : AppCompatActivity() {

    private lateinit var txtTitle: TextView
    private lateinit var txtArtist: TextView
    private lateinit var txtPosition: TextView
    private lateinit var txtDuration: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var btnPlay: FloatingActionButton
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
        seekBar = findViewById(R.id.seekBar)
        btnPlay = findViewById(R.id.btnPlayPause)

        findViewById<ImageButton>(R.id.btnPrev).setOnClickListener { PlayerController.previous() }
        findViewById<ImageButton>(R.id.btnNext).setOnClickListener { PlayerController.next() }
        btnPlay.setOnClickListener { PlayerController.togglePlayPause(); refresh() }
        findViewById<ImageButton>(R.id.btnEq).setOnClickListener {
            startActivity(Intent(this, EqualizerActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnShuffle).setOnClickListener {
            val c = PlayerController.controller
            c?.shuffleModeEnabled = !(c?.shuffleModeEnabled ?: false)
        }

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
            })
            refresh()
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
        seekBar.max = dur.toInt().coerceAtLeast(1)
        txtDuration.text = format(dur)
        updateProgress()
    }

    private fun updateProgress() {
        if (userSeeking) return
        val c = PlayerController.controller ?: return
        val pos = c.currentPosition
        seekBar.progress = pos.toInt()
        txtPosition.text = format(pos)
    }

    private fun format(ms: Long): String {
        if (ms < 0 || ms == Long.MAX_VALUE) return "0:00"
        val s = (ms / 1000).toInt()
        return "%d:%02d".format(s / 60, s % 60)
    }
}
