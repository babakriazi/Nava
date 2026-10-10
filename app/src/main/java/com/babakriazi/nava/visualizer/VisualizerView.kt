package com.babakriazi.nava.visualizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.media.audiofx.Visualizer
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class VisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var mode: VizMode = VizMode.BARS
        set(value) {
            field = value
            invalidate()
        }

    private var visualizer: Visualizer? = null
    private var fft: ByteArray? = null
    private var waveform: ByteArray? = null

    private val gold = 0xFFC9A227.toInt()
    private val teal = 0xFF4ECDC4.toInt()
    private val goldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = gold
        style = Paint.Style.FILL
    }
    private val tealPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = teal
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = gold
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = gold
        style = Paint.Style.FILL
    }

    private data class Particle(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var life: Float, var maxLife: Float,
        var size: Float
    )

    private val particles = mutableListOf<Particle>()
    private var tunnelPhase = 0f
    private var lastEnergy = 0f

    fun attach(sessionId: Int) {
        release()
        if (sessionId == 0) return
        try {
            visualizer = Visualizer(sessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        bytes: ByteArray?,
                        samplingRate: Int
                    ) {
                        waveform = bytes
                        postInvalidateOnAnimation()
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        bytes: ByteArray?,
                        samplingRate: Int
                    ) {
                        fft = bytes
                        postInvalidateOnAnimation()
                    }
                }, Visualizer.getMaxCaptureRate() / 2, true, true)
                enabled = true
            }
        } catch (_: Exception) {
            visualizer = null
        }
    }

    fun release() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Exception) {}
        visualizer = null
    }

    override fun onDetachedFromWindow() {
        release()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        when (mode) {
            VizMode.BARS -> drawBars(canvas, w, h)
            VizMode.CIRCULAR -> drawCircular(canvas, w, h)
            VizMode.WAVEFORM -> drawWaveform(canvas, w, h)
            VizMode.STARBURST -> drawStarburst(canvas, w, h)
            VizMode.PARTICLES -> drawParticles(canvas, w, h)
            VizMode.TUNNEL -> drawTunnel(canvas, w, h)
        }
    }

    private fun magnitudes(count: Int): FloatArray {
        val data = fft ?: return FloatArray(count)
        val out = FloatArray(count)
        val n = min(count, (data.size / 2) - 1)
        for (i in 0 until n) {
            val re = data[i * 2].toInt()
            val im = data[i * 2 + 1].toInt()
            val mag = kotlin.math.sqrt((re * re + im * im).toDouble()).toFloat()
            out[i] = min(1f, mag / 90f)
        }
        return out
    }

    private fun energy(): Float {
        val m = magnitudes(16)
        var s = 0f
        for (v in m) s += v
        return (s / m.size).coerceIn(0f, 1f)
    }

    private fun drawBars(canvas: Canvas, w: Float, h: Float) {
        val bars = 48
        val mags = magnitudes(bars)
        val barW = w / bars
        for (i in 0 until bars) {
            val v = mags[i]
            val barH = v * h * 0.85f
            goldPaint.alpha = (120 + v * 135).toInt().coerceIn(80, 255)
            val left = i * barW + 2
            canvas.drawRoundRect(left, h - barH, left + barW - 4, h, 6f, 6f, goldPaint)
            if (i % 3 == 0) {
                tealPaint.alpha = (80 + v * 100).toInt()
                tealPaint.strokeWidth = 2f
                canvas.drawLine(left + barW / 2, h - barH, left + barW / 2, h, tealPaint)
            }
        }
    }

    private fun drawCircular(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2
        val cy = h / 2
        val bars = 64
        val mags = magnitudes(bars)
        val baseR = min(w, h) * 0.22f
        val maxLen = min(w, h) * 0.28f
        for (i in 0 until bars) {
            val angle = (i / bars.toFloat()) * Math.PI * 2 - Math.PI / 2
            val len = baseR + mags[i] * maxLen
            val x1 = cx + cos(angle).toFloat() * baseR
            val y1 = cy + sin(angle).toFloat() * baseR
            val x2 = cx + cos(angle).toFloat() * len
            val y2 = cy + sin(angle).toFloat() * len
            linePaint.color = if (i % 2 == 0) gold else teal
            linePaint.alpha = (140 + mags[i] * 115).toInt()
            linePaint.strokeWidth = 4f
            canvas.drawLine(x1, y1, x2, y2, linePaint)
        }
        goldPaint.alpha = 40
        canvas.drawCircle(cx, cy, baseR * 0.85f, goldPaint)
    }

    private fun drawWaveform(canvas: Canvas, w: Float, h: Float) {
        val data = waveform ?: return
        val path = Path()
        val mid = h / 2
        val step = max(1, data.size / 200)
        var first = true
        var i = 0
        while (i < data.size) {
            val x = (i.toFloat() / data.size) * w
            val y = mid + ((data[i].toInt() - 128) / 128f) * mid * 0.9f
            if (first) {
                path.moveTo(x, y)
                first = false
            } else path.lineTo(x, y)
            i += step
        }
        linePaint.color = gold
        linePaint.alpha = 220
        linePaint.strokeWidth = 3f
        canvas.drawPath(path, linePaint)
        // mirror faint teal
        linePaint.color = teal
        linePaint.alpha = 80
        canvas.save()
        canvas.scale(1f, -0.4f, 0f, mid)
        canvas.drawPath(path, linePaint)
        canvas.restore()
    }

    private fun drawStarburst(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2
        val cy = h / 2
        val rays = 36
        val mags = magnitudes(rays)
        val maxLen = min(w, h) * 0.48f
        for (i in 0 until rays) {
            val angle = (i / rays.toFloat()) * Math.PI * 2
            val len = mags[i] * maxLen + 20f
            val x2 = cx + cos(angle).toFloat() * len
            val y2 = cy + sin(angle).toFloat() * len
            linePaint.color = if (i % 2 == 0) gold else teal
            linePaint.alpha = (100 + mags[i] * 155).toInt()
            linePaint.strokeWidth = 2.5f + mags[i] * 4f
            canvas.drawLine(cx, cy, x2, y2, linePaint)
        }
        goldPaint.alpha = (60 + energy() * 120).toInt()
        canvas.drawCircle(cx, cy, 18f + energy() * 30f, goldPaint)
    }

    private fun drawParticles(canvas: Canvas, w: Float, h: Float) {
        val e = energy()
        val burst = e - lastEnergy
        lastEnergy = e
        if (burst > 0.05f || particles.size < 40) {
            val n = if (burst > 0.08f) 12 else 3
            repeat(n) {
                val angle = Random.nextFloat() * Math.PI * 2
                val speed = 2f + e * 10f + Random.nextFloat() * 4f
                particles.add(
                    Particle(
                        x = w / 2, y = h / 2,
                        vx = cos(angle).toFloat() * speed,
                        vy = sin(angle).toFloat() * speed,
                        life = 1f,
                        maxLife = 0.8f + Random.nextFloat() * 0.8f,
                        size = 3f + e * 8f + Random.nextFloat() * 4f
                    )
                )
            }
        }
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.x += p.vx
            p.y += p.vy
            p.life -= 0.02f
            p.vx *= 0.98f
            p.vy *= 0.98f
            if (p.life <= 0f) {
                it.remove()
                continue
            }
            particlePaint.color = if (p.size > 6) gold else teal
            particlePaint.alpha = (p.life / p.maxLife * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(p.x, p.y, p.size * p.life, particlePaint)
        }
        if (particles.size > 300) {
            particles.subList(0, particles.size - 250).clear()
        }
    }

    private fun drawTunnel(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2
        val cy = h / 2
        val e = energy()
        tunnelPhase += 0.04f + e * 0.12f
        val rings = 14
        val mags = magnitudes(24)
        for (r in 0 until rings) {
            val t = ((r / rings.toFloat()) + tunnelPhase) % 1f
            val scale = 0.05f + t * 1.2f
            val radius = min(w, h) * 0.5f * scale
            val alpha = ((1f - t) * 200).toInt().coerceIn(20, 200)
            linePaint.color = if (r % 2 == 0) gold else teal
            linePaint.alpha = alpha
            linePaint.strokeWidth = 2f + e * 3f
            val segs = 32
            val path = Path()
            for (i in 0..segs) {
                val a = (i / segs.toFloat()) * Math.PI * 2
                val wobble = 1f + mags[i % mags.size] * 0.15f * (1f - t)
                val x = cx + cos(a).toFloat() * radius * wobble
                val y = cy + sin(a).toFloat() * radius * wobble
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            canvas.drawPath(path, linePaint)
        }
    }
}
