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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

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

    // 比上一版更密的网格，眨眼/尾巴/挥爪的局部形变更顺。
    private val meshWidth = 28
    private val meshHeight = 28
    private val verts = FloatArray((meshWidth + 1) * (meshHeight + 1) * 2)

    private var callbackPosted = false
    private var paused = false

    private var idleEpochNanos = 0L
    private var waveStartNanos = 0L
    private var blinkStartNanos = 0L
    private var nextBlinkNanos = 0L

    init {
        isClickable = true
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val now = System.nanoTime()
        idleEpochNanos = now
        scheduleNextBlink(now)
        postFrame()
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(this)
        callbackPosted = false
        super.onDetachedFromWindow()
    }

    fun startIdle() {
        val now = System.nanoTime()
        waveStartNanos = 0L
        blinkStartNanos = 0L
        idleEpochNanos = now
        scheduleNextBlink(now)
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

    fun playBlink() {
        val now = System.nanoTime()
        blinkStartNanos = now
        nextBlinkNanos = now + BLINK_DURATION_NANOS + randomBlinkDelayNanos()
        paused = false
        postFrame()
    }

    fun release() {
        Choreographer.getInstance().removeFrameCallback(this)
        callbackPosted = false
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    private fun postFrame() {
        if (!callbackPosted && !paused && isAttachedToWindow) {
            callbackPosted = true
            Choreographer.getInstance().postFrameCallback(this)
        }
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

        if (nextBlinkNanos == 0L) {
            scheduleNextBlink(now)
        } else if (blinkStartNanos == 0L && now >= nextBlinkNanos) {
            blinkStartNanos = now
        }

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

        val blink = blinkAmount(now)

        buildMesh(
            idleSeconds = idleSeconds,
            waveT = waveT,
            blinkAmount = blink
        )

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

    private fun blinkAmount(nowNanos: Long): Double {
        if (blinkStartNanos == 0L) return 0.0

        val elapsed = nowNanos - blinkStartNanos
        if (elapsed >= BLINK_DURATION_NANOS) {
            blinkStartNanos = 0L
            scheduleNextBlink(nowNanos)
            return 0.0
        }

        val t = elapsed.toDouble() / 1_000_000_000.0

        return when {
            t < 0.11 -> smoothStep(t / 0.11)
            t < 0.17 -> 1.0
            else -> {
                val p = smoothStep((t - 0.17) / 0.17)
                1.0 - p
            }
        }
    }

    private fun scheduleNextBlink(nowNanos: Long) {
        nextBlinkNanos = nowNanos + randomBlinkDelayNanos()
    }

    private fun randomBlinkDelayNanos(): Long {
        return Random.nextLong(
            MIN_BLINK_DELAY_MS,
            MAX_BLINK_DELAY_MS + 1L
        ) * 1_000_000L
    }

    private fun buildMesh(
        idleSeconds: Double,
        waveT: Double,
        blinkAmount: Double
    ) {
        val viewW = width.toFloat()
        val viewH = height.toFloat()

        val breath = sin(idleSeconds * 2.0 * PI / 2.65)
        val tailAngle = 3.4 * sin(idleSeconds * 2.0 * PI / 2.35)
        val wave = waveParams(waveT)

        var index = 0

        for (row in 0..meshHeight) {
            val v = row.toDouble() / meshHeight.toDouble()

            for (col in 0..meshWidth) {
                val u = col.toDouble() / meshWidth.toDouble()

                var x = u
                var y = v

                // 1) 呼吸：只轻微作用于胸腹，头身仍然是同一张完整母版。
                val chestWeight = exp(
                    -square((u - 0.50) / 0.30) -
                        square((v - 0.72) / 0.27)
                )
                y += 0.0035 * breath * chestWeight

                // 2) 尾巴：只让左侧尾部轻摆，尾根固定，身体不会一起扭。
                val tailPivotU = 0.355
                val tailPivotV = 0.735
                val tailCenterU = 0.205
                val tailCenterV = 0.690

                var tailWeight = exp(
                    -square((u - tailCenterU) / 0.17) -
                        square((v - tailCenterV) / 0.19)
                )

                val tailDistance = hypot(u - tailPivotU, v - tailPivotV)
                val tailAnchor = clamp(
                    (tailDistance - 0.020) / 0.17,
                    0.0,
                    1.0
                )
                tailWeight *= tailAnchor

                if (tailWeight > 0.002) {
                    val angle = tailAngle * PI / 180.0
                    val dx = x - tailPivotU
                    val dy = y - tailPivotV
                    val rx = tailPivotU + dx * cos(angle) - dy * sin(angle)
                    val ry = tailPivotV + dx * sin(angle) + dy * cos(angle)

                    x = x * (1.0 - tailWeight) + rx * tailWeight
                    y = y * (1.0 - tailWeight) + ry * tailWeight
                }

                // 3) 随机眨眼：只压缩双眼附近的局部网格，不替换整张脸。
                if (blinkAmount > 0.0) {
                    val eyeCenterV = 0.392

                    val leftEyeWeight = exp(
                        -square((u - 0.400) / 0.073) -
                            square((v - eyeCenterV) / 0.060)
                    )
                    val rightEyeWeight = exp(
                        -square((u - 0.606) / 0.073) -
                            square((v - eyeCenterV) / 0.060)
                    )
                    val eyeWeight = clamp(
                        leftEyeWeight + rightEyeWeight,
                        0.0,
                        1.0
                    )

                    val compression = 0.69 * blinkAmount * eyeWeight
                    y = eyeCenterV + (y - eyeCenterV) * (1.0 - compression)
                }

                // 4) 挥爪：只改变右前肢附近的网格。
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

                    val rotatedX =
                        pivotU + dx * cos(angle) - dy * sin(angle)
                    val rotatedY =
                        pivotV + dx * sin(angle) + dy * cos(angle)

                    val desiredX = rotatedX + wave.translateX
                    val desiredY = rotatedY + wave.translateY

                    x =
                        x * (1.0 - localWeight) +
                            desiredX * localWeight
                    y =
                        y * (1.0 - localWeight) +
                            desiredY * localWeight
                }

                verts[index++] = (x * viewW).toFloat()
                verts[index++] = (y * viewH).toFloat()
            }
        }
    }

    private fun waveParams(timeSeconds: Double): WaveParams {
        if (
            timeSeconds < 0.0 ||
            timeSeconds >= WAVE_DURATION_SECONDS
        ) {
            return WaveParams(
                false,
                0.0,
                0.0,
                0.0
            )
        }

        if (timeSeconds < 0.12) {
            return WaveParams(
                true,
                0.0,
                0.0,
                0.0
            )
        }

        if (timeSeconds < 0.38) {
            val p = smoothStep(
                (timeSeconds - 0.12) / 0.26
            )
            return WaveParams(
                true,
                -52.0 * p,
                0.0215 * p,
                -0.0383 * p
            )
        }

        if (timeSeconds < 0.92) {
            val p =
                (timeSeconds - 0.38) / 0.54
            val swing = sin(
                p * PI * 4.0
            )

            return WaveParams(
                true,
                -52.0 - 8.0 * swing,
                0.0215 + 0.0040 * swing,
                -0.0383 -
                    0.0025 * abs(swing)
            )
        }

        if (timeSeconds < 1.22) {
            val p = smoothStep(
                (timeSeconds - 0.92) / 0.30
            )
            val remain = 1.0 - p

            return WaveParams(
                true,
                -52.0 * remain,
                0.0215 * remain,
                -0.0383 * remain
            )
        }

        return WaveParams(
            true,
            0.0,
            0.0,
            0.0
        )
    }

    private fun smoothStep(
        value: Double
    ): Double {
        val x = clamp(
            value,
            0.0,
            1.0
        )
        return x * x * (3.0 - 2.0 * x)
    }

    private fun square(
        value: Double
    ): Double = value * value

    private fun clamp(
        value: Double,
        min: Double,
        max: Double
    ): Double {
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
        private const val BLINK_DURATION_NANOS = 340_000_000L
        private const val MIN_BLINK_DELAY_MS = 3_200L
        private const val MAX_BLINK_DELAY_MS = 7_800L
    }
}
