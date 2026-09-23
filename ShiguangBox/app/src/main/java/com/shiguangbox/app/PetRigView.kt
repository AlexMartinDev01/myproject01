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

    enum class State {
        IDLE,
        WAVE,
        REMINDER,
        HAPPY,
        TIRED,
        SLEEP,
        WAKE_UP
    }

    private val bitmap: Bitmap = BitmapFactory.decodeResource(
        resources,
        R.drawable.pet_orange_idle
    )

    private val paint = Paint(
        Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG
    )

    private val meshWidth = 28
    private val meshHeight = 28
    private val verts = FloatArray((meshWidth + 1) * (meshHeight + 1) * 2)

    private var callbackPosted = false
    private var paused = false

    private var state: State = State.IDLE
    private var stateStartNanos = 0L
    private var idleEpochNanos = 0L
    private var lastInteractionNanos = 0L

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
        stateStartNanos = now
        lastInteractionNanos = now
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
        state = State.IDLE
        stateStartNanos = now
        idleEpochNanos = now
        lastInteractionNanos = now
        blinkStartNanos = 0L
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

    fun onUserInteraction() {
        val now = System.nanoTime()
        lastInteractionNanos = now

        if (state == State.SLEEP || state == State.TIRED) {
            setState(State.WAKE_UP, now)
        }
    }

    fun playWave() {
        val now = System.nanoTime()
        lastInteractionNanos = now
        setState(State.WAVE, now)
    }

    fun playReminder() {
        val now = System.nanoTime()
        lastInteractionNanos = now
        setState(State.REMINDER, now)
    }

    fun playHappy() {
        val now = System.nanoTime()
        lastInteractionNanos = now
        setState(State.HAPPY, now)
    }

    fun playTired() {
        val now = System.nanoTime()
        setState(State.TIRED, now)
    }

    fun playSleep() {
        val now = System.nanoTime()
        setState(State.SLEEP, now)
    }

    fun wakeUp() {
        val now = System.nanoTime()
        lastInteractionNanos = now
        setState(State.WAKE_UP, now)
    }

    fun playBlink() {
        val now = System.nanoTime()
        if (state == State.SLEEP) {
            setState(State.WAKE_UP, now)
        } else {
            blinkStartNanos = now
            nextBlinkNanos = now + BLINK_DURATION_NANOS + randomBlinkDelayNanos()
        }
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

    private fun setState(newState: State, now: Long = System.nanoTime()) {
        state = newState
        stateStartNanos = now
        blinkStartNanos = 0L

        if (newState == State.IDLE) {
            idleEpochNanos = now
            scheduleNextBlink(now)
        }

        paused = false
        postFrame()
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
        updateState(now)

        val stateSeconds =
            (now - stateStartNanos).coerceAtLeast(0L) / 1_000_000_000.0

        val idleSeconds =
            (now - idleEpochNanos).coerceAtLeast(0L) / 1_000_000_000.0

        val blink = currentBlinkAmount(now)

        buildMesh(
            idleSeconds = idleSeconds,
            stateSeconds = stateSeconds,
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

    private fun updateState(now: Long) {
        val elapsedSeconds =
            (now - stateStartNanos).coerceAtLeast(0L) / 1_000_000_000.0

        when (state) {
            State.IDLE -> {
                maybeStartRandomBlink(now)

                val inactiveSeconds =
                    (now - lastInteractionNanos).coerceAtLeast(0L) /
                        1_000_000_000.0

                if (inactiveSeconds >= AUTO_TIRED_AFTER_SECONDS) {
                    setState(State.TIRED, now)
                }
            }

            State.WAVE -> {
                if (elapsedSeconds >= WAVE_DURATION_SECONDS) {
                    setState(State.IDLE, now)
                }
            }

            State.REMINDER -> {
                if (elapsedSeconds >= REMINDER_DURATION_SECONDS) {
                    setState(State.IDLE, now)
                }
            }

            State.HAPPY -> {
                if (elapsedSeconds >= HAPPY_DURATION_SECONDS) {
                    setState(State.IDLE, now)
                }
            }

            State.TIRED -> {
                if (elapsedSeconds >= TIRED_DURATION_SECONDS) {
                    setState(State.SLEEP, now)
                }
            }

            State.SLEEP -> Unit

            State.WAKE_UP -> {
                if (elapsedSeconds >= WAKE_DURATION_SECONDS) {
                    setState(State.IDLE, now)
                }
            }
        }
    }

    private fun maybeStartRandomBlink(now: Long) {
        if (nextBlinkNanos == 0L) {
            scheduleNextBlink(now)
        } else if (blinkStartNanos == 0L && now >= nextBlinkNanos) {
            blinkStartNanos = now
        }
    }

    private fun currentBlinkAmount(now: Long): Double {
        if (state == State.SLEEP) return 1.0

        if (state == State.TIRED) {
            val t =
                (now - stateStartNanos).coerceAtLeast(0L) /
                    1_000_000_000.0
            val slowBlink = (sin(t * PI * 1.05) + 1.0) / 2.0
            return 0.18 + 0.68 * slowBlink
        }

        if (state == State.WAKE_UP) {
            val t =
                (now - stateStartNanos).coerceAtLeast(0L) /
                    1_000_000_000.0
            return when {
                t < 0.18 -> 1.0
                t < 0.55 -> 1.0 - smoothStep((t - 0.18) / 0.37)
                else -> 0.0
            }
        }

        if (blinkStartNanos == 0L) return 0.0

        val elapsed = now - blinkStartNanos
        if (elapsed >= BLINK_DURATION_NANOS) {
            blinkStartNanos = 0L
            scheduleNextBlink(now)
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
        stateSeconds: Double,
        blinkAmount: Double
    ) {
        val viewW = width.toFloat()
        val viewH = height.toFloat()

        val sleepBreathMultiplier = if (state == State.SLEEP) 0.48 else 1.0
        val breath =
            sin(idleSeconds * 2.0 * PI / if (state == State.SLEEP) 3.6 else 2.65) *
                sleepBreathMultiplier

        val tailAmplitude = when (state) {
            State.HAPPY -> 8.0
            State.SLEEP -> 1.1
            State.TIRED -> 1.8
            else -> 3.4
        }

        val tailAngle =
            tailAmplitude *
                sin(idleSeconds * 2.0 * PI / if (state == State.HAPPY) 0.72 else 2.35)

        val wave = when (state) {
            State.WAVE -> waveParams(stateSeconds)
            State.REMINDER -> reminderWaveParams(stateSeconds)
            else -> WaveParams(false, 0.0, 0.0, 0.0)
        }

        val happyBounce =
            if (state == State.HAPPY) {
                val p = clamp(stateSeconds / HAPPY_DURATION_SECONDS, 0.0, 1.0)
                -0.022 * abs(sin(p * PI * 3.0)) * (1.0 - p * 0.25)
            } else {
                0.0
            }

        val tiredDrop =
            if (state == State.TIRED) {
                0.010 * smoothStep(
                    clamp(stateSeconds / TIRED_DURATION_SECONDS, 0.0, 1.0)
                )
            } else {
                0.0
            }

        val sleepDrop = if (state == State.SLEEP) 0.013 else 0.0

        val wakeBounce =
            if (state == State.WAKE_UP) {
                val p = clamp(stateSeconds / WAKE_DURATION_SECONDS, 0.0, 1.0)
                -0.010 * sin(p * PI)
            } else {
                0.0
            }

        var index = 0

        for (row in 0..meshHeight) {
            val v = row.toDouble() / meshHeight.toDouble()

            for (col in 0..meshWidth) {
                val u = col.toDouble() / meshWidth.toDouble()

                var x = u
                var y = v

                // 整体只做极轻的状态位移，不拆头身。
                y += happyBounce + tiredDrop + sleepDrop + wakeBounce

                // 呼吸只作用于胸腹区域。
                val chestWeight = exp(
                    -square((u - 0.50) / 0.30) -
                        square((v - 0.72) / 0.27)
                )
                y += 0.0035 * breath * chestWeight

                // 睡眠时身体稍微放松下沉，仍然是同一张母版。
                if (state == State.SLEEP) {
                    val bodyRelaxWeight = exp(
                        -square((u - 0.50) / 0.38) -
                            square((v - 0.73) / 0.30)
                    )
                    y += 0.0045 * bodyRelaxWeight
                }

                // 尾巴局部摆动。
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
                    val rx =
                        tailPivotU + dx * cos(angle) - dy * sin(angle)
                    val ry =
                        tailPivotV + dx * sin(angle) + dy * cos(angle)

                    x =
                        x * (1.0 - tailWeight) +
                            rx * tailWeight
                    y =
                        y * (1.0 - tailWeight) +
                            ry * tailWeight
                }

                // 双眼局部压缩，实现眨眼 / 困倦 / 睡觉。
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

                    val compression =
                        0.69 * blinkAmount * eyeWeight

                    y =
                        eyeCenterV +
                            (y - eyeCenterV) *
                                (1.0 - compression)
                }

                // 右前爪局部网格挥动。
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

                    val angle =
                        wave.angleDegrees * PI / 180.0

                    val dx = x - pivotU
                    val dy = y - pivotV

                    val rotatedX =
                        pivotU +
                            dx * cos(angle) -
                            dy * sin(angle)
                    val rotatedY =
                        pivotV +
                            dx * sin(angle) +
                            dy * cos(angle)

                    val desiredX =
                        rotatedX + wave.translateX
                    val desiredY =
                        rotatedY + wave.translateY

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

    private fun reminderWaveParams(timeSeconds: Double): WaveParams {
        if (
            timeSeconds < 0.0 ||
            timeSeconds >= REMINDER_DURATION_SECONDS
        ) {
            return WaveParams(false, 0.0, 0.0, 0.0)
        }

        // 提醒时连续挥两轮，中间只停很短时间。
        val cycle = timeSeconds % WAVE_DURATION_SECONDS
        return waveParams(cycle)
    }

    private fun waveParams(timeSeconds: Double): WaveParams {
        if (
            timeSeconds < 0.0 ||
            timeSeconds >= WAVE_DURATION_SECONDS
        ) {
            return WaveParams(false, 0.0, 0.0, 0.0)
        }

        if (timeSeconds < 0.12) {
            return WaveParams(true, 0.0, 0.0, 0.0)
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
            val swing =
                sin(p * PI * 4.0)

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

        return WaveParams(true, 0.0, 0.0, 0.0)
    }

    private fun smoothStep(value: Double): Double {
        val x = clamp(value, 0.0, 1.0)
        return x * x * (3.0 - 2.0 * x)
    }

    private fun square(value: Double): Double = value * value

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
        private const val REMINDER_DURATION_SECONDS = 2.70
        private const val HAPPY_DURATION_SECONDS = 1.15
        private const val TIRED_DURATION_SECONDS = 7.0
        private const val WAKE_DURATION_SECONDS = 0.85

        // 正式逻辑：5 分钟没有互动，先进入犯困，再自动睡觉。
        private const val AUTO_TIRED_AFTER_SECONDS = 300.0

        private const val BLINK_DURATION_NANOS = 340_000_000L
        private const val MIN_BLINK_DELAY_MS = 3_200L
        private const val MAX_BLINK_DELAY_MS = 7_800L
    }
}
