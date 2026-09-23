package com.shiguangbox.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

class PetRigView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs), Choreographer.FrameCallback {

    private val bitmap: Bitmap = BitmapFactory.decodeResource(
        resources,
        R.drawable.pet_orange_idle
    )

    private val paint = Paint(
        Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG
    )

    private val meshWidth = 20
    private val meshHeight = 20
    private val verts = FloatArray((meshWidth + 1) * (meshHeight + 1) * 2)

    private var callbackPosted = false
    private var paused = false
    private var waveStartNanos = 0L
    private var idleEpochNanos = 0L

    init {
        isClickable = true
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        idleEpochNanos = System.nanoTime()
        postFrame()
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(this)
        callbackPosted = false
        super.onDetachedFromWindow()
    }

    fun startIdle() {
        waveStartNanos = 0L
        idleEpochNanos = System.nanoTime()
        paused = false
        postFrame()
    }

    fun pauseMotion() {
        paused = true
    }

    fun resumeMotion() {
        paused = false
        postFrame()
    }

    fun playWave() {
        waveStartNanos = System.nanoTime()
        paused = false
        postFrame()
    }

    fun release() {
        Choreographer.getInstance().removeFrameCallback(this)
        callbackPosted = false
    }

    override fun doFrame(frameTimeNanos: Long) {
        callbackPosted = false
        if (!paused && isAttachedToWindow) {
            invalidate()
            postFrame()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val now = System.nanoTime()
        val idleSeconds = if (idleEpochNanos == 0L) {
            0.0
        } else {
            (now - idleEpochNanos) / 1_000_000_000.0
        }

        var waveT = -1.0
        if (waveStartNanos != 0L) {
            waveT = (now - waveStartNanos) / 1_000_000_000.0
            if (waveT >= WAVE_DURATION_SECONDS) {
                waveStartNanos = 0L
                waveT = -1.0
            }
        }

        buildMesh(idleSeconds, waveT)

        canvas.drawBitmapMesh(
            bitmap,
            meshWidth,
            meshHeight,
            verts,
            0,
            null,
            0,
            paint
        )
    }

    private fun buildMesh(idleSeconds: Double, waveT: Double) {
        val viewW = width.toFloat()
        val viewH = height.toFloat()

        val breath = sin(idleSeconds * 2.0 * PI / 2.6)
        val wave = waveParams(waveT)

        var index = 0

        for (row in 0..meshHeight) {
            val v = row.toDouble() / meshHeight.toDouble()

            for (col in 0..meshWidth) {
                val u = col.toDouble() / meshWidth.toDouble()

                var x = u
                var y = v

                // 呼吸只作用于胸腹区域，头和身体仍然是一张连续母版。
                val chestWeight = exp(
                    -square((u - 0.50) / 0.31) -
                        square((v - 0.72) / 0.27)
                )
                y += 0.0042 * breath * chestWeight

                if (wave.active) {
                    val pivotU = 0.570
                    val pivotV = 0.596
                    val centerU = 0.593
                    val centerV = 0.650

                    var localWeight = exp(
                        -square((u - centerU) / 0.077) -
                            square((v - centerV) / 0.101)
                    )

                    if (u < 0.50 || v < 0.53) {
                        localWeight *= 0.15
                    }

                    val distance = hypot(u - pivotU, v - pivotV)
                    val shoulderAnchor = clamp(
                        (distance - 0.014) / 0.105,
                        0.0,
                        1.0
                    )
                    localWeight *= shoulderAnchor

                    val angle = wave.angleDegrees * PI / 180.0
                    val dx = x - pivotU
                    val dy = y - pivotV

                    val rotatedX = pivotU + dx * cos(angle) - dy * sin(angle)
                    val rotatedY = pivotV + dx * sin(angle) + dy * cos(angle)

                    val desiredX = rotatedX + wave.translateX
                    val desiredY = rotatedY + wave.translateY

                    x = x * (1.0 - localWeight) + desiredX * localWeight
                    y = y * (1.0 - localWeight) + desiredY * localWeight
                }

                verts[index++] = (x * viewW).toFloat()
                verts[index++] = (y * viewH).toFloat()
            }
        }
    }

    private fun waveParams(timeSeconds: Double): WaveParams {
        if (timeSeconds < 0.0 || timeSeconds >= WAVE_DURATION_SECONDS) {
            return WaveParams(false, 0.0, 0.0, 0.0)
        }

        if (timeSeconds < 0.12) {
            return WaveParams(true, 0.0, 0.0, 0.0)
        }

        if (timeSeconds < 0.38) {
            val p = smoothStep((timeSeconds - 0.12) / 0.26)
            return WaveParams(
                true,
                -52.0 * p,
                0.0215 * p,
                -0.0383 * p
            )
        }

        if (timeSeconds < 0.92) {
            val p = (timeSeconds - 0.38) / 0.54
            val swing = sin(p * PI * 4.0)

            return WaveParams(
                true,
                -52.0 - 8.0 * swing,
                0.0215 + 0.0040 * swing,
                -0.0383 - 0.0025 * kotlin.math.abs(swing)
            )
        }

        if (timeSeconds < 1.22) {
            val p = smoothStep((timeSeconds - 0.92) / 0.30)
            val remain = 1.0 - p

            return WaveParams(
                true,
                -52.0 * remain,
                0.0215 * remain,
                -0.0383 * remain
            )
        }

        return WaveParams(true, 0.0, 0.0, 0.0)
    }

    private fun smoothStep(value: Double): Double {
        val x = clamp(value, 0.0, 1.0)
        return x * x * (3.0 - 2.0 * x)
    }

    private fun square(value: Double): Double = value * value

    private fun clamp(value: Double, min: Double, max: Double): Double {
        return when {
            value < min -> min
            value > max -> max
            else -> value
        }
    }

    private data class WaveParams(
        val active: Boolean,
        val angleDegrees: Double,
        val translateX: Double,
        val translateY: Double
    )

    companion object {
        private const val WAVE_DURATION_SECONDS = 1.35
    }
}
