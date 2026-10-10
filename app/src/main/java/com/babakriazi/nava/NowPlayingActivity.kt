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
import com.babakriazi.nava.data.Prefs
import com.babakriazi.nava.playback.EqEngine
import com.babakriazi.nava.playback.MusicService
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
    private lateinit var toolbar: com.google.android.material.appbar.MaterialToolbar
    private lateinit var prefs: Prefs
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
        prefs = Prefs(this)

        toolbar = findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val plName = intent.getStringExtra("playlist_name")
            ?: prefs.getCurrentPlaylistName()
        if (plName.isNotBlank()) {
            toolbar.subtitle = plName
            prefs.setCurrentPlaylistName(plName)
        } else {
            toolbar.subtitle = null
        }

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
            prefs.setShuffle(c.shuffleModeEnabled)
            updateModeButtons()
            Toast.makeText(this, if (c.shuffleModeEnabled) "Shuffle on" else "Shuffle off", Toast.LENGTH_SHORT).show()
        }

        btnRepeat.setOnClickListener {
            val c = PlayerController.controller ?: return@setOnClickListener
            val next = when (c.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            c.repeatMode = next
            prefs.setRepeatMode(next)
            updateModeButtons()
            Toast.makeText(this, txtRepeatMode.text, Toast.LENGTH_SHORT).show()
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) txtPosition.text = format(progress.toLong())
            }
            override fun onStartTrackingTouch(sb: SeekBar?) { userSeeking = true }
            override fun onStopTrackingTouch(sb: SeekBar?) {
                userSeeking = false
                PlayerController.seekTo(seekBar.progress.toLong())
                PlayerController.saveState(this@NowPlayingActivity)
            }
        })

        PlayerController.connect(this) {
            // Re-apply EQ for current session when opening player
            val sid = MusicService.audioSessionId
            if (sid != 0) EqEngine.apply(this, sid)

            PlayerController.controller?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) { refresh() }
                override fun onMediaItemTransition(item: androidx.media3.common.MediaItem?, reason: Int) {
                    refresh()
                    PlayerController.saveState(this@NowPlayingActivity)
                    val id = MusicService.audioSessionId
                    if (id != 0) EqEngine.apply(this@NowPlayingActivity, id)
                }
                override fun onRepeatModeChanged(repeatMode: Int) { updateModeButtons() }
                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { updateModeButtons() }
            })
            refresh()
        }
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
        val name = prefs.getCurrentPlaylistName()
        if (name.isNotBlank()) toolbar.subtitle = name
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
        PlayerController.saveState(this)
    }

    private fun refresh() {
        val c = PlayerController.controller ?: return
        txtTitle.text = c.mediaMetadata.title ?: ""
        txtArtist.text = c.mediaMetadata.artist ?: ""
        btnPlay.setImageResource(
            if (c.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
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
        seekBar.progress = c.currentPosition.toInt().coerceIn(0, seekBar.max)
        txtPosition.text = format(c.currentPosition)
    }

    private fun format(ms: Long): String {
        if (ms < 0 || ms == Long.MAX_VALUE) return "0:00"
        val s = (ms / 1000).toInt()
        return "%d:%02d".format(s / 60, s % 60)
    }
}
