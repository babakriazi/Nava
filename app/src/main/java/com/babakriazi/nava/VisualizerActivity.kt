package com.babakriazi.nava

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.babakriazi.nava.playback.MusicService
import com.babakriazi.nava.playback.PlayerController
import com.babakriazi.nava.visualizer.VizMode
import com.babakriazi.nava.visualizer.VisualizerView
import kotlin.random.Random

class VisualizerActivity : AppCompatActivity() {

    private lateinit var vizView: VisualizerView
    private lateinit var overlay: View
    private lateinit var txtMode: TextView
    private lateinit var txtHint: TextView

    private var randomMode = false
    private var locked = false
    private var controlsVisible = true
    private var modeIndex = 0
    private var lastModeIndex = -1

    private val handler = Handler(Looper.getMainLooper())
    private var secondsLeft = 30

    private val randomTick = object : Runnable {
        override fun run() {
            if (!randomMode || locked) return
            secondsLeft--
            if (secondsLeft <= 0) {
                pickRandomMode()
                secondsLeft = 30
            }
            updateLabels()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = 0xFF000000.toInt()
        window.navigationBarColor = 0xFF000000.toInt()

        setContentView(R.layout.activity_visualizer)

        vizView = findViewById(R.id.vizView)
        overlay = findViewById(R.id.overlayControls)
        txtMode = findViewById(R.id.txtMode)
        txtHint = findViewById(R.id.txtHint)

        randomMode = intent.getBooleanExtra("random", false)
        modeIndex = intent.getIntExtra("mode", 0).coerceIn(0, VizMode.entries.lastIndex)

        if (randomMode) {
            pickRandomMode()
            secondsLeft = 30
            handler.post(randomTick)
        } else {
            applyMode(modeIndex)
        }

        val sessionId = MusicService.audioSessionId.let {
            if (it != 0) it else MusicService.readAudioSessionId(MusicService.playerInstance)
        }
        vizView.attach(sessionId)

        val gesture = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                controlsVisible = !controlsVisible
                overlay.visibility = if (controlsVisible) View.VISIBLE else View.GONE
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                PlayerController.togglePlayPause()
                Toast.makeText(
                    this@VisualizerActivity,
                    if (PlayerController.controller?.isPlaying == true) "Playing" else "Paused",
                    Toast.LENGTH_SHORT
                ).show()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (randomMode) {
                    locked = !locked
                    Toast.makeText(
                        this@VisualizerActivity,
                        if (locked) "Mode locked" else "Random unlocked",
                        Toast.LENGTH_SHORT
                    ).show()
                    updateLabels()
                }
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (kotlin.math.abs(dy) > kotlin.math.abs(dx) && dy > 200) {
                    finish()
                    return true
                }
                if (kotlin.math.abs(dx) > 120 && kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
                    if (randomMode && !locked) {
                        // manual skip in random
                        pickRandomMode()
                        secondsLeft = 30
                    } else if (!randomMode) {
                        if (dx < 0) {
                            modeIndex = (modeIndex + 1) % VizMode.entries.size
                        } else {
                            modeIndex = (modeIndex - 1 + VizMode.entries.size) % VizMode.entries.size
                        }
                        applyMode(modeIndex)
                    }
                    updateLabels()
                    return true
                }
                return false
            }
        })

        vizView.setOnTouchListener { _, event ->
            gesture.onTouchEvent(event)
            true
        }

        findViewById<View>(R.id.btnClose).setOnClickListener { finish() }
        updateLabels()
    }

    private fun applyMode(index: Int) {
        modeIndex = index
        vizView.mode = VizMode.fromOrdinalSafe(index)
        lastModeIndex = index
    }

    private fun pickRandomMode() {
        var next: Int
        do {
            next = Random.nextInt(VizMode.entries.size)
        } while (next == lastModeIndex && VizMode.entries.size > 1)
        applyMode(next)
    }

    private fun updateLabels() {
        val name = VizMode.fromOrdinalSafe(modeIndex).label
        txtMode.text = if (randomMode) {
            if (locked) "Random · Locked · $name"
            else "Random · ${secondsLeft}s · $name"
        } else name
        txtHint.text = "Tap controls · Double-tap play · Swipe change · Long-press lock · Swipe down exit"
    }

    override fun onResume() {
        super.onResume()
        val sessionId = MusicService.audioSessionId.let {
            if (it != 0) it else MusicService.readAudioSessionId(MusicService.playerInstance)
        }
        vizView.attach(sessionId)
    }

    override fun onPause() {
        vizView.release()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(randomTick)
        vizView.release()
        super.onDestroy()
    }
}
