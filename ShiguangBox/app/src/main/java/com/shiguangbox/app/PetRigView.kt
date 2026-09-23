package com.shiguangbox.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import java.time.LocalTime
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
        TAIL_WAG,
        TIRED,
        SLEEP,
        WAKE_UP,
        DRAGGING,
        PETTED
    }

    private val petKind: PetKind =
        PetProfiles.fromId(
            context.getSharedPreferences(
                "shiguangbox_settings",
                Context.MODE_PRIVATE
            ).getString("pet_selected_id", PetKind.ORANGE.id)
        )

    private val visualResources =
        PetProfiles.resources(petKind)

    private val idleBitmap: Bitmap =
        BitmapFactory.decodeResource(
            resources,
            visualResources.idle
        )

    private val sleepBitmap: Bitmap =
        BitmapFactory.decodeResource(
            resources,
            visualResources.sleep
        )

    private val tiredBitmap: Bitmap =
        BitmapFactory.decodeResource(
            resources,
            visualResources.tired
        )

    private val wakeBitmap: Bitmap =
        BitmapFactory.decodeResource(
            resources,
            visualResources.wake
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
    private var nextIdleActionNanos = 0L

    private var lastIdleAction = -1
    private var lastTouchReaction = -1
    private var actionFrequencyLevel = 1
    private var autoSleepEnabled = true
    private var autoSleepAfterSeconds = 240.0

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
        scheduleNextIdleAction(now)
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
        scheduleNextIdleAction(now)
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

    fun playTailWag() {
        val now = System.nanoTime()
        lastInteractionNanos = now
        setState(State.TAIL_WAG, now)
    }

    fun playPetted() {
        val now = System.nanoTime()
        lastInteractionNanos = now
        setState(State.PETTED, now)
    }

    fun playTouchReaction() {
        val now = System.nanoTime()
        lastInteractionNanos = now

        if (state == State.SLEEP || state == State.TIRED) {
            setState(State.WAKE_UP, now)
            return
        }

        var candidate = Random.nextInt(3)
        if (candidate == lastTouchReaction) {
            candidate = (candidate + 1 + Random.nextInt(2)) % 3
        }
        lastTouchReaction = candidate

        when (candidate) {
            0 -> {
                blinkStartNanos = now
                nextBlinkNanos =
                    now + BLINK_DURATION_NANOS + randomBlinkDelayNanos()
                postFrame()
            }
            1 -> setState(State.TAIL_WAG, now)
            else -> setState(State.WAVE, now)
        }
    }

    fun configureBehavior(
        actionLevel: Int,
        autoSleep: Boolean,
        sleepMinutes: Int
    ) {
        actionFrequencyLevel = actionLevel.coerceIn(0, 2)
        autoSleepEnabled = autoSleep
        autoSleepAfterSeconds = sleepMinutes.coerceIn(3, 5) * 60.0
        val now = System.nanoTime()
        lastInteractionNanos = now
        if (state == State.IDLE) {
            scheduleNextIdleAction(now)
        }
    }

    fun currentPetId(): String = petKind.id

    fun displayName(): String = petKind.displayName

    fun displayEmoji(): String = petKind.emoji

    fun signatureActionLabel(): String =
        if (petKind == PetKind.YAYA) {
            "耳朵轻晃"
        } else {
            "摇尾巴"
        }

    fun canDoAmbientAction(): Boolean = state == State.IDLE

    fun isSleepingOrTired(): Boolean =
        state == State.SLEEP ||
            state == State.TIRED ||
            state == State.WAKE_UP

    fun startDragging() {
        val now = System.nanoTime()
        lastInteractionNanos = now
        setState(State.DRAGGING, now)
    }

    fun endDragging() {
        val now = System.nanoTime()
        lastInteractionNanos = now
        setState(State.IDLE, now)
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
        setState(State.TIRED, System.nanoTime())
    }

    fun playSleep() {
        setState(State.SLEEP, System.nanoTime())
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
        if (!idleBitmap.isRecycled) idleBitmap.recycle()
        if (!sleepBitmap.isRecycled) sleepBitmap.recycle()
        if (!tiredBitmap.isRecycled) tiredBitmap.recycle()
        if (!wakeBitmap.isRecycled) wakeBitmap.recycle()
    }

    private fun setState(newState: State, now: Long = System.nanoTime()) {
        state = newState
        stateStartNanos = now
        blinkStartNanos = 0L

        if (newState == State.IDLE) {
            idleEpochNanos = now
            scheduleNextBlink(now)
            scheduleNextIdleAction(now)
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

        when (state) {
            State.TIRED -> drawTiredState(canvas, idleSeconds, stateSeconds)

            State.SLEEP -> drawSleepState(canvas, idleSeconds, stateSeconds)

            State.WAKE_UP -> drawWakeState(canvas, idleSeconds, stateSeconds)

            else -> {
                val normalBlink = currentBlinkAmount(now)
                val comboBlink = when (state) {
                    State.TAIL_WAG -> tailComboBlinkAmount(stateSeconds)
                    State.PETTED -> pettingBlinkAmount(stateSeconds)
                    else -> 0.0
                }
                val blink = maxOf(normalBlink, comboBlink)
                buildMesh(idleSeconds, stateSeconds, blink)
                drawIdleMesh(canvas, 1f)
            }
        }
    }

    private fun drawTiredState(
        canvas: Canvas,
        idleSeconds: Double,
        stateSeconds: Double
    ) {
        // 犯困不再只是压眼皮，而是切到专门的打哈欠姿态。
        val enter = smoothStep(
            clamp(stateSeconds / TIRED_CROSSFADE_SECONDS, 0.0, 1.0)
        ).toFloat()

        if (enter < 0.999f) {
            buildMesh(idleSeconds, stateSeconds, 0.45)
            drawIdleMesh(canvas, 1f - enter)
        }

        val sway = sin(stateSeconds * PI * 1.15)
        drawPoseBitmap(
            canvas = canvas,
            bitmap = tiredBitmap,
            alpha = enter,
            scaleX = 1.0f,
            scaleY = 1.0f + (0.006 * abs(sway)).toFloat(),
            yOffset = (2.2 * abs(sway)).toFloat()
        )
    }

    private fun drawSleepState(
        canvas: Canvas,
        idleSeconds: Double,
        stateSeconds: Double
    ) {
        // 从“打哈欠”姿态自然过渡到真正闭眼蜷睡。
        val enter = smoothStep(
            clamp(stateSeconds / SLEEP_CROSSFADE_SECONDS, 0.0, 1.0)
        ).toFloat()

        if (enter < 0.999f) {
            drawPoseBitmap(
                canvas = canvas,
                bitmap = tiredBitmap,
                alpha = 1f - enter,
                scaleX = 1.0f,
                scaleY = 1.0f,
                yOffset = 0f
            )
        }

        val breath = sin(idleSeconds * 2.0 * PI / 3.8)
        drawPoseBitmap(
            canvas = canvas,
            bitmap = sleepBitmap,
            alpha = enter,
            scaleX = 1.0f + (0.0025 * breath).toFloat(),
            scaleY = 1.0f + (0.0070 * breath).toFloat(),
            yOffset = (1.5 * breath).toFloat()
        )
    }

    private fun drawWakeState(
        canvas: Canvas,
        idleSeconds: Double,
        stateSeconds: Double
    ) {
        // 睡姿 -> 伸懒腰 -> 待机，避免突然站起来。
        val firstEnd = 0.38
        val holdEnd = 0.88

        when {
            stateSeconds < firstEnd -> {
                val p = smoothStep(
                    clamp(stateSeconds / firstEnd, 0.0, 1.0)
                ).toFloat()

                val sleepBreath = sin(idleSeconds * 2.0 * PI / 3.8)
                drawPoseBitmap(
                    canvas = canvas,
                    bitmap = sleepBitmap,
                    alpha = 1f - p,
                    scaleX = 1.0f,
                    scaleY = 1.0f + (0.004 * sleepBreath).toFloat(),
                    yOffset = (1.0 * sleepBreath).toFloat()
                )

                drawPoseBitmap(
                    canvas = canvas,
                    bitmap = wakeBitmap,
                    alpha = p,
                    scaleX = 0.985f + 0.015f * p,
                    scaleY = 0.985f + 0.015f * p,
                    yOffset = (-4f * p)
                )
            }

            stateSeconds < holdEnd -> {
                val p = ((stateSeconds - firstEnd) / (holdEnd - firstEnd))
                    .coerceIn(0.0, 1.0)
                val bounce = sin(p * PI)
                drawPoseBitmap(
                    canvas = canvas,
                    bitmap = wakeBitmap,
                    alpha = 1f,
                    scaleX = 1.0f + (0.018 * bounce).toFloat(),
                    scaleY = 1.0f - (0.010 * bounce).toFloat(),
                    yOffset = (-5.0 * bounce).toFloat()
                )
            }

            else -> {
                val p = smoothStep(
                    clamp(
                        (stateSeconds - holdEnd) /
                            (WAKE_DURATION_SECONDS - holdEnd),
                        0.0,
                        1.0
                    )
                ).toFloat()

                drawPoseBitmap(
                    canvas = canvas,
                    bitmap = wakeBitmap,
                    alpha = 1f - p,
                    scaleX = 1.0f,
                    scaleY = 1.0f,
                    yOffset = 0f
                )

                buildMesh(idleSeconds, stateSeconds, 0.0)
                drawIdleMesh(canvas, p)
            }
        }
    }

    private fun drawPoseBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        alpha: Float,
        scaleX: Float,
        scaleY: Float,
        yOffset: Float
    ) {
        if (alpha <= 0f) return

        val save = canvas.save()
        val cx = width / 2f
        val cy = height / 2f

        canvas.translate(0f, yOffset)
        canvas.scale(scaleX, scaleY, cx, cy)

        paint.alpha = (255f * alpha.coerceIn(0f, 1f)).toInt()
        canvas.drawBitmap(
            bitmap,
            null,
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            paint
        )
        paint.alpha = 255
        canvas.restoreToCount(save)
    }

    private fun drawIdleMesh(canvas: Canvas, alpha: Float) {
        if (alpha <= 0f) return
        paint.alpha = (255f * alpha.coerceIn(0f, 1f)).toInt()
        canvas.drawBitmapMesh(
            idleBitmap,
            meshWidth,
            meshHeight,
            verts,
            0,
            null,
            0,
            paint
        )
        paint.alpha = 255
    }

    private fun updateState(now: Long) {
        val elapsedSeconds =
            (now - stateStartNanos).coerceAtLeast(0L) / 1_000_000_000.0

        when (state) {
            State.IDLE -> {
                maybeStartRandomBlink(now)
                maybeStartRandomIdleAction(now)

                val inactiveSeconds =
                    (now - lastInteractionNanos).coerceAtLeast(0L) /
                        1_000_000_000.0

                if (autoSleepEnabled &&
                    inactiveSeconds >= autoSleepAfterSeconds
                ) {
                    setState(State.TIRED, now)
                }
            }

            State.WAVE -> if (elapsedSeconds >= WAVE_DURATION_SECONDS) {
                setState(State.IDLE, now)
            }

            State.REMINDER -> if (elapsedSeconds >= REMINDER_DURATION_SECONDS) {
                setState(State.IDLE, now)
            }

            State.HAPPY -> if (elapsedSeconds >= HAPPY_DURATION_SECONDS) {
                setState(State.IDLE, now)
            }

            State.TAIL_WAG -> if (elapsedSeconds >= TAIL_WAG_DURATION_SECONDS) {
                setState(State.IDLE, now)
            }

            State.DRAGGING -> Unit

            State.PETTED -> if (
                elapsedSeconds >= PETTED_DURATION_SECONDS
            ) {
                setState(State.IDLE, now)
            }

            State.TIRED -> if (elapsedSeconds >= TIRED_DURATION_SECONDS) {
                setState(State.SLEEP, now)
            }

            State.SLEEP -> Unit

            State.WAKE_UP -> if (elapsedSeconds >= WAKE_DURATION_SECONDS) {
                setState(State.IDLE, now)
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

    private fun maybeStartRandomIdleAction(now: Long) {
        if (blinkStartNanos != 0L || now < nextIdleActionNanos) return

        // 0=只眨眼，1=摇尾巴组合，2=挥爪；避免连续相同。
        var candidate = when (Random.nextInt(100)) {
            in 0..24 -> 0
            in 25..69 -> 1
            else -> 2
        }

        if (candidate == lastIdleAction) {
            candidate = (candidate + 1 + Random.nextInt(2)) % 3
        }
        lastIdleAction = candidate

        when (candidate) {
            0 -> {
                blinkStartNanos = now
                nextBlinkNanos =
                    now + BLINK_DURATION_NANOS + randomBlinkDelayNanos()
                scheduleNextIdleAction(now)
            }
            1 -> setState(State.TAIL_WAG, now)
            else -> setState(State.WAVE, now)
        }
    }

    private fun scheduleNextIdleAction(nowNanos: Long) {
        val minDelay: Long
        val maxDelay: Long

        when (actionFrequencyLevel) {
            0 -> {
                minDelay = 9_000L
                maxDelay = 16_000L
            }
            2 -> {
                minDelay = 3_200L
                maxDelay = 7_000L
            }
            else -> {
                minDelay = 5_500L
                maxDelay = 11_000L
            }
        }

        val hour = LocalTime.now().hour
        val nightMultiplier =
            if (hour >= 23 || hour < 7) {
                1.65
            } else {
                1.0
            }

        val personalityMultiplier =
            if (petKind == PetKind.YAYA) {
                1.22
            } else {
                1.0
            }

        val delayMs =
            (
                Random.nextLong(
                    minDelay,
                    maxDelay + 1L
                ) *
                    nightMultiplier *
                    personalityMultiplier
            ).toLong()

        nextIdleActionNanos =
            nowNanos +
                delayMs * 1_000_000L
    }

    private fun currentBlinkAmount(now: Long): Double {
        if (state == State.TIRED) {
            val t =
                (now - stateStartNanos).coerceAtLeast(0L) /
                    1_000_000_000.0
            val slowBlink = (sin(t * PI * 1.05) + 1.0) / 2.0
            return 0.22 + 0.70 * slowBlink
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
            else -> 1.0 - smoothStep((t - 0.17) / 0.17)
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
        if (petKind == PetKind.YAYA) {
            buildYayaMesh(
                idleSeconds,
                stateSeconds,
                blinkAmount
            )
        } else {
            buildOrangeMesh(
                idleSeconds,
                stateSeconds,
                blinkAmount
            )
        }
    }

    private fun buildOrangeMesh(
        idleSeconds: Double,
        stateSeconds: Double,
        blinkAmount: Double
    ) {
        val viewW = width.toFloat()
        val viewH = height.toFloat()

        val breath = sin(idleSeconds * 2.0 * PI / 2.65)

        // 按用户反馈把尾巴幅度明显放大，但仍保持慢速、柔和。
        val hour = LocalTime.now().hour
        val isQuietNight =
            hour >= 23 || hour < 7

        val tailAmplitude = when (state) {
            State.TAIL_WAG -> 30.0
            State.HAPPY -> 26.0
            State.PETTED -> 24.0
            State.REMINDER -> 22.0
            State.WAVE -> 18.0
            State.DRAGGING -> 8.0
            State.TIRED -> 2.0
            else ->
                if (isQuietNight) {
                    7.5
                } else {
                    11.5
                }
        }

        val tailPeriod = when (state) {
            State.TAIL_WAG -> 0.72
            State.HAPPY -> 0.68
            State.PETTED -> 0.82
            State.REMINDER -> 0.86
            State.WAVE -> 1.02
            State.DRAGGING -> 1.35
            State.TIRED -> 3.6
            else -> 2.35
        }

        val tailAngle =
            tailAmplitude * sin(idleSeconds * 2.0 * PI / tailPeriod)

        val wave = when (state) {
            State.WAVE -> waveParams(stateSeconds)
            State.REMINDER -> reminderWaveParams(stateSeconds)
            State.TAIL_WAG -> tailComboWaveParams(stateSeconds)
            else -> WaveParams(false, 0.0, 0.0, 0.0)
        }

        val happyBounce =
            when (state) {
                State.HAPPY -> {
                    val p =
                        clamp(
                            stateSeconds / HAPPY_DURATION_SECONDS,
                            0.0,
                            1.0
                        )
                    -0.022 *
                        abs(sin(p * PI * 3.0)) *
                        (1.0 - p * 0.25)
                }

                State.PETTED ->
                    -0.006 * abs(sin(stateSeconds * PI * 2.2))

                else -> 0.0
            }

        val tiredDrop =
            if (state == State.TIRED) {
                0.008 * smoothStep(
                    clamp(stateSeconds / TIRED_DURATION_SECONDS, 0.0, 1.0)
                )
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

                y += happyBounce + tiredDrop

                if (state == State.DRAGGING) {
                    val hangingWeight = smoothStep(
                        clamp((v - 0.46) / 0.46, 0.0, 1.0)
                    )
                    y += 0.018 * hangingWeight
                    x += 0.004 * sin(idleSeconds * PI * 2.0) * hangingWeight
                }

                val chestWeight = exp(
                    -square((u - 0.50) / 0.30) -
                        square((v - 0.72) / 0.27)
                )
                y += 0.0035 * breath * chestWeight

                // 尾巴局部摆动：尾根固定，尾端更明显。
                val tailPivotU = 0.355
                val tailPivotV = 0.735
                val tailCenterU = 0.205
                val tailCenterV = 0.690

                var tailWeight = exp(
                    -square((u - tailCenterU) / 0.115) -
                        square((v - tailCenterV) / 0.145)
                )

                // 尾巴保护蒙版：
                // 只允许左下方尾巴区域参与旋转，头部、脸、胸口全部锁死。
                val tailHorizontalMask =
                    1.0 - smoothStep(clamp((u - 0.335) / 0.060, 0.0, 1.0))
                val tailVerticalMask =
                    smoothStep(clamp((v - 0.535) / 0.085, 0.0, 1.0))
                val faceProtection =
                    if (v < 0.545 || u > 0.405) 0.0 else 1.0

                val tailDistance = hypot(u - tailPivotU, v - tailPivotV)
                val tailAnchor = clamp(
                    (tailDistance - 0.018) / 0.145,
                    0.0,
                    1.0
                )

                tailWeight *=
                    tailAnchor *
                    tailHorizontalMask *
                    tailVerticalMask *
                    faceProtection

                if (tailWeight > 0.003) {
                    val angle = tailAngle * PI / 180.0
                    val dx = x - tailPivotU
                    val dy = y - tailPivotV

                    val rx =
                        tailPivotU + dx * cos(angle) - dy * sin(angle)
                    val ry =
                        tailPivotV + dx * sin(angle) + dy * cos(angle)

                    x = x * (1.0 - tailWeight) + rx * tailWeight
                    y = y * (1.0 - tailWeight) + ry * tailWeight
                }

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
                    y =
                        eyeCenterV +
                            (y - eyeCenterV) * (1.0 - compression)
                }

                if (wave.active) {
                    val pivotU = 0.570
                    val pivotV = 0.596
                    val centerU = 0.593
                    val centerV = 0.650

                    var localWeight = exp(
                        -square((u - centerU) / 0.077) -
                            square((v - centerV) / 0.101)
                    )

                    // 挥爪同样采用硬保护区：脸、头、左半身绝不参与手臂变形。
                    if (u < 0.505 || u > 0.700 || v < 0.545 || v > 0.805) {
                        localWeight = 0.0
                    } else {
                        val armHorizontalMask =
                            smoothStep(clamp((u - 0.505) / 0.055, 0.0, 1.0)) *
                            (1.0 - smoothStep(clamp((u - 0.655) / 0.045, 0.0, 1.0)))
                        val armVerticalMask =
                            smoothStep(clamp((v - 0.545) / 0.055, 0.0, 1.0)) *
                            (1.0 - smoothStep(clamp((v - 0.755) / 0.050, 0.0, 1.0)))
                        localWeight *= armHorizontalMask * armVerticalMask
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

                    x = x * (1.0 - localWeight) + desiredX * localWeight
                    y = y * (1.0 - localWeight) + desiredY * localWeight
                }

                verts[index++] = (x * viewW).toFloat()
                verts[index++] = (y * viewH).toFloat()
            }
        }
    }

    private fun buildYayaMesh(
        idleSeconds: Double,
        stateSeconds: Double,
        blinkAmount: Double
    ) {
        val viewW = width.toFloat()
        val viewH = height.toFloat()

        val hour = LocalTime.now().hour
        val quietNight = hour >= 23 || hour < 7

        val breath =
            sin(idleSeconds * 2.0 * PI / 3.35)

        val signatureStrength =
            when (state) {
                State.TAIL_WAG -> 1.0
                State.HAPPY -> 0.76
                State.PETTED -> 0.58
                State.REMINDER -> 0.68
                State.WAVE -> 0.24
                State.DRAGGING -> 0.10
                else ->
                    if (quietNight) {
                        0.10
                    } else {
                        0.18
                    }
            }

        val legStrength =
            when (state) {
                State.HAPPY -> 0.70
                State.PETTED -> 0.28
                State.DRAGGING -> 0.45
                else -> 0.0
            }

        val wave =
            when (state) {
                State.WAVE ->
                    yayaWaveParams(stateSeconds)

                State.REMINDER ->
                    yayaReminderWaveParams(
                        stateSeconds
                    )

                State.TAIL_WAG ->
                    yayaComboWaveParams(
                        stateSeconds
                    )

                else ->
                    WaveParams(
                        false,
                        0.0,
                        0.0,
                        0.0
                    )
            }

        val happyBounce =
            when (state) {
                State.HAPPY -> {
                    val p =
                        clamp(
                            stateSeconds /
                                HAPPY_DURATION_SECONDS,
                            0.0,
                            1.0
                        )
                    -0.013 *
                        abs(
                            sin(
                                p *
                                    PI *
                                    2.4
                            )
                        ) *
                        (1.0 - p * 0.20)
                }

                State.PETTED ->
                    -0.0035 *
                        abs(
                            sin(
                                stateSeconds *
                                    PI *
                                    1.9
                            )
                        )

                else -> 0.0
            }

        var index = 0

        for (row in 0..meshHeight) {
            val v =
                row.toDouble() /
                    meshHeight.toDouble()

            for (col in 0..meshWidth) {
                val u =
                    col.toDouble() /
                        meshWidth.toDouble()

                var x = u
                var y = v

                y += happyBounce

                if (state == State.DRAGGING) {
                    val hangingWeight =
                        smoothStep(
                            clamp(
                                (v - 0.52) /
                                    0.42,
                                0.0,
                                1.0
                            )
                        )

                    y +=
                        0.012 *
                            hangingWeight

                    x +=
                        0.0025 *
                            sin(
                                idleSeconds *
                                    PI *
                                    1.7
                            ) *
                            hangingWeight
                }

                // 芽芽呼吸：胸口轻微起伏，围巾和脚掌基本锁定。
                var chestWeight =
                    exp(
                        -square(
                            (u - 0.515) /
                                0.250
                        ) -
                            square(
                                (v - 0.745) /
                                    0.205
                            )
                    )

                val scarfProtected =
                    u in 0.285..0.805 &&
                        v in 0.565..0.815

                if (scarfProtected) {
                    chestWeight *= 0.18
                }

                if (v >= 0.79) {
                    chestWeight *= 0.08
                }

                y +=
                    0.0021 *
                        breath *
                        chestWeight

                val faceProtected =
                    u in 0.245..0.790 &&
                        v in 0.225..0.620

                val headAccessoryProtected =
                    u in 0.585..0.875 &&
                        v in 0.075..0.405

                // 两只垂耳分别绑定：
                // 根部最稳，中段传递，耳尖幅度最大且稍微延迟。
                for (earIndex in 0..1) {
                    val left =
                        earIndex == 0

                    val pivotU =
                        if (left) {
                            0.292
                        } else {
                            0.755
                        }

                    val pivotV =
                        if (left) {
                            0.382
                        } else {
                            0.395
                        }

                    val centerU =
                        if (left) {
                            0.205
                        } else {
                            0.865
                        }

                    val centerV =
                        if (left) {
                            0.565
                        } else {
                            0.575
                        }

                    val radiusU =
                        if (left) {
                            0.148
                        } else {
                            0.132
                        }

                    val radiusV =
                        if (left) {
                            0.235
                        } else {
                            0.225
                        }

                    val period =
                        if (left) {
                            3.05
                        } else {
                            3.25
                        }

                    val phase =
                        if (left) {
                            0.0
                        } else {
                            0.43
                        }

                    val tipDelay =
                        if (left) {
                            0.36
                        } else {
                            0.34
                        }

                    val rootAngle =
                        if (left) {
                            1.4
                        } else {
                            1.2
                        }

                    val midAngle =
                        if (left) {
                            4.6
                        } else {
                            4.1
                        }

                    val tipAngle =
                        if (left) {
                            8.6
                        } else {
                            7.8
                        }

                    var localWeight =
                        exp(
                            -square(
                                (u - centerU) /
                                    radiusU
                            ) -
                                square(
                                    (v - centerV) /
                                        radiusV
                                )
                        )

                    if (faceProtected) {
                        localWeight = 0.0
                    }

                    if (
                        headAccessoryProtected &&
                        !left
                    ) {
                        localWeight *= 0.08
                    }

                    val axisX =
                        if (left) {
                            -0.40
                        } else {
                            0.40
                        }

                    val axisY = 0.92

                    val sourceDx =
                        u - pivotU
                    val sourceDy =
                        v - pivotV

                    val projection =
                        sourceDx *
                            axisX +
                            sourceDy *
                            axisY

                    val progress =
                        clamp(
                            (
                                projection -
                                    0.01
                                ) /
                                0.34,
                            0.0,
                            1.0
                        )

                    val rootWeight =
                        smoothStep(
                            clamp(
                                1.0 -
                                    progress *
                                    2.9,
                                0.0,
                                1.0
                            )
                        )

                    val middleWeight =
                        clamp(
                            1.0 -
                                abs(
                                    progress -
                                        0.52
                                ) /
                                0.34,
                            0.0,
                            1.0
                        )

                    val tipWeight =
                        smoothStep(
                            clamp(
                                (
                                    progress -
                                        0.45
                                    ) /
                                    0.55,
                                0.0,
                                1.0
                            )
                        )

                    val rootOsc =
                        sin(
                            idleSeconds *
                                2.0 *
                                PI /
                                period +
                                phase
                        )

                    val middleOsc =
                        sin(
                            idleSeconds *
                                2.0 *
                                PI /
                                period +
                                phase +
                                0.15
                        )

                    val tipOsc =
                        sin(
                            idleSeconds *
                                2.0 *
                                PI /
                                period +
                                phase +
                                tipDelay
                        )

                    val angleDegrees =
                        signatureStrength *
                            (
                                rootAngle *
                                    rootWeight *
                                    rootOsc +
                                    midAngle *
                                    middleWeight *
                                    middleOsc +
                                    tipAngle *
                                    tipWeight *
                                    tipOsc
                                )

                    localWeight *=
                        clamp(
                            (
                                progress -
                                    0.01
                                ) /
                                0.95,
                            0.0,
                            1.0
                        )

                    if (localWeight > 0.002) {
                        val angle =
                            angleDegrees *
                                PI /
                                180.0

                        val dx =
                            x - pivotU
                        val dy =
                            y - pivotV

                        val rx =
                            pivotU +
                                dx *
                                cos(angle) -
                                dy *
                                sin(angle)

                        var ry =
                            pivotV +
                                dx *
                                sin(angle) +
                                dy *
                                cos(angle)

                        val gravityDrop =
                            (
                                if (left) {
                                    0.0075
                                } else {
                                    0.0070
                                }
                                ) *
                                tipWeight *
                                localWeight *
                                signatureStrength

                        ry += gravityDrop

                        x =
                            x *
                                (1.0 -
                                    localWeight) +
                                rx *
                                localWeight

                        y =
                            y *
                                (1.0 -
                                    localWeight) +
                                ry *
                                localWeight
                    }
                }

                if (blinkAmount > 0.0) {
                    val eyeCenterV =
                        0.443

                    val leftEyeWeight =
                        exp(
                            -square(
                                (u - 0.415) /
                                    0.058
                            ) -
                                square(
                                    (v - 0.447) /
                                        0.048
                                )
                        )

                    val rightEyeWeight =
                        exp(
                            -square(
                                (u - 0.642) /
                                    0.058
                            ) -
                                square(
                                    (v - 0.438) /
                                        0.048
                                )
                        )

                    val eyeWeight =
                        clamp(
                            leftEyeWeight +
                                rightEyeWeight,
                            0.0,
                            1.0
                        )

                    val compression =
                        0.60 *
                            blinkAmount *
                            eyeWeight

                    y =
                        eyeCenterV +
                            (y - eyeCenterV) *
                            (1.0 -
                                compression)
                }

                // 腿部只在开心/摸摸/拖拽时轻动：
                // 从腿根传递到脚掌，不再从中间挤压。
                if (legStrength > 0.0) {
                    for (legIndex in 0..1) {
                        val left =
                            legIndex == 0

                        val pivotU =
                            if (left) {
                                0.405
                            } else {
                                0.645
                            }

                        val pivotV =
                            if (left) {
                                0.785
                            } else {
                                0.790
                            }

                        val centerU =
                            if (left) {
                                0.375
                            } else {
                                0.680
                            }

                        val centerV = 0.865
                        val radiusU = 0.145
                        val radiusV = 0.135

                        var legWeight =
                            exp(
                                -square(
                                    (u - centerU) /
                                        radiusU
                                ) -
                                    square(
                                        (v - centerV) /
                                            radiusV
                                    )
                            )

                        val progress =
                            clamp(
                                (
                                    v -
                                        pivotV -
                                        0.005
                                    ) /
                                    0.20,
                                0.0,
                                1.0
                            )

                        val rootWeight =
                            smoothStep(
                                clamp(
                                    1.0 -
                                        progress *
                                        3.0,
                                    0.0,
                                    1.0
                                )
                            )

                        val middleWeight =
                            clamp(
                                1.0 -
                                    abs(
                                        progress -
                                            0.52
                                    ) /
                                    0.35,
                                0.0,
                                1.0
                            )

                        val tipWeight =
                            smoothStep(
                                clamp(
                                    (
                                        progress -
                                            0.44
                                        ) /
                                        0.56,
                                    0.0,
                                    1.0
                                )
                            )

                        val period =
                            if (left) {
                                3.8
                            } else {
                                4.0
                            }

                        val phase =
                            if (left) {
                                0.0
                            } else {
                                0.35
                            }

                        val rootAngle =
                            if (left) {
                                0.6
                            } else {
                                0.5
                            }

                        val midAngle =
                            if (left) {
                                1.6
                            } else {
                                1.4
                            }

                        val tipAngle =
                            if (left) {
                                2.8
                            } else {
                                2.5
                            }

                        val angleDegrees =
                            legStrength *
                                (
                                    rootAngle *
                                        rootWeight *
                                        sin(
                                            idleSeconds *
                                                2.0 *
                                                PI /
                                                period +
                                                phase
                                        ) +
                                        midAngle *
                                        middleWeight *
                                        sin(
                                            idleSeconds *
                                                2.0 *
                                                PI /
                                                period +
                                                phase +
                                                0.12
                                        ) +
                                        tipAngle *
                                        tipWeight *
                                        sin(
                                            idleSeconds *
                                                2.0 *
                                                PI /
                                                period +
                                                phase +
                                                0.28
                                        )
                                    )

                        legWeight *=
                            clamp(
                                (
                                    progress -
                                        0.01
                                    ) /
                                    0.95,
                                0.0,
                                1.0
                            )

                        if (legWeight > 0.002) {
                            val angle =
                                angleDegrees *
                                    PI /
                                    180.0

                            val dx =
                                x - pivotU
                            val dy =
                                y - pivotV

                            val rx =
                                pivotU +
                                    dx *
                                    cos(angle) -
                                    dy *
                                    sin(angle)

                            val settle =
                                (
                                    if (left) {
                                        0.0045
                                    } else {
                                        0.0040
                                    }
                                    ) *
                                    tipWeight *
                                    legWeight *
                                    legStrength

                            val ry =
                                pivotV +
                                    dx *
                                    sin(angle) +
                                    dy *
                                    cos(angle) +
                                    settle

                            x =
                                x *
                                    (1.0 -
                                        legWeight) +
                                    rx *
                                    legWeight

                            y =
                                y *
                                    (1.0 -
                                        legWeight) +
                                    ry *
                                    legWeight
                        }
                    }
                }

                if (wave.active) {
                    val pivotU = 0.650
                    val pivotV = 0.655
                    val centerU = 0.635
                    val centerV = 0.715

                    var localWeight =
                        exp(
                            -square(
                                (u - centerU) /
                                    0.082
                            ) -
                                square(
                                    (v - centerV) /
                                        0.110
                                )
                        )

                    if (faceProtected) {
                        localWeight = 0.0
                    }

                    if (scarfProtected) {
                        localWeight *= 0.08
                    }

                    val distance =
                        hypot(
                            u - pivotU,
                            v - pivotV
                        )

                    localWeight *=
                        clamp(
                            (
                                distance -
                                    0.012
                                ) /
                                0.120,
                            0.0,
                            1.0
                        )

                    val angle =
                        wave.angleDegrees *
                            PI /
                            180.0

                    val dx =
                        x - pivotU
                    val dy =
                        y - pivotV

                    val rotatedX =
                        pivotU +
                            dx *
                            cos(angle) -
                            dy *
                            sin(angle)

                    val rotatedY =
                        pivotV +
                            dx *
                            sin(angle) +
                            dy *
                            cos(angle)

                    val desiredX =
                        rotatedX +
                            wave.translateX

                    val desiredY =
                        rotatedY +
                            wave.translateY

                    x =
                        x *
                            (1.0 -
                                localWeight) +
                            desiredX *
                            localWeight

                    y =
                        y *
                            (1.0 -
                                localWeight) +
                            desiredY *
                            localWeight
                }

                verts[index++] =
                    (x * viewW).toFloat()

                verts[index++] =
                    (y * viewH).toFloat()
            }
        }
    }

    private fun pettingBlinkAmount(timeSeconds: Double): Double {
        val local = timeSeconds % 0.78
        return when {
            local < 0.12 -> smoothStep(local / 0.12)
            local < 0.35 -> 0.82
            local < 0.48 ->
                0.82 *
                    (1.0 -
                        smoothStep((local - 0.35) / 0.13))
            else -> 0.0
        }
    }

    private fun tailComboBlinkAmount(timeSeconds: Double): Double {
        // 摇尾巴期间自然眨一次眼：脸不做任何整体网格拉伸，只压缩眼睛局部。
        if (timeSeconds < 0.46 || timeSeconds > 0.78) return 0.0
        val t = timeSeconds - 0.46
        return when {
            t < 0.09 -> smoothStep(t / 0.09)
            t < 0.15 -> 1.0
            else -> 1.0 - smoothStep((t - 0.15) / 0.17)
        }
    }

    private fun tailComboWaveParams(timeSeconds: Double): WaveParams {
        // 先明显摇尾巴，再配合一次挥爪；整个过程中尾巴继续动。
        val local = timeSeconds - 0.82
        if (local < 0.0 || local >= WAVE_DURATION_SECONDS) {
            return WaveParams(false, 0.0, 0.0, 0.0)
        }
        return waveParams(local)
    }

    private fun yayaComboWaveParams(
        timeSeconds: Double
    ): WaveParams {
        val local =
            timeSeconds - 1.05

        if (
            local < 0.0 ||
            local >= WAVE_DURATION_SECONDS
        ) {
            return WaveParams(
                false,
                0.0,
                0.0,
                0.0
            )
        }

        return yayaWaveParams(local)
    }

    private fun yayaReminderWaveParams(
        timeSeconds: Double
    ): WaveParams {
        if (
            timeSeconds < 0.0 ||
            timeSeconds >=
                REMINDER_DURATION_SECONDS
        ) {
            return WaveParams(
                false,
                0.0,
                0.0,
                0.0
            )
        }

        return yayaWaveParams(
            timeSeconds %
                WAVE_DURATION_SECONDS
        )
    }

    private fun yayaWaveParams(
        timeSeconds: Double
    ): WaveParams {
        if (
            timeSeconds < 0.0 ||
            timeSeconds >=
                WAVE_DURATION_SECONDS
        ) {
            return WaveParams(
                false,
                0.0,
                0.0,
                0.0
            )
        }

        if (timeSeconds < 0.14) {
            return WaveParams(
                true,
                0.0,
                0.0,
                0.0
            )
        }

        if (timeSeconds < 0.44) {
            val p =
                smoothStep(
                    (timeSeconds -
                        0.14) /
                        0.30
                )

            return WaveParams(
                true,
                -30.0 * p,
                0.012 * p,
                -0.027 * p
            )
        }

        if (timeSeconds < 0.94) {
            val p =
                (timeSeconds -
                    0.44) /
                    0.50

            val swing =
                sin(
                    p *
                        PI *
                        3.0
                )

            return WaveParams(
                true,
                -30.0 -
                    5.0 *
                    swing,
                0.012 +
                    0.0025 *
                    swing,
                -0.027 -
                    0.002 *
                    abs(swing)
            )
        }

        if (timeSeconds < 1.26) {
            val p =
                smoothStep(
                    (timeSeconds -
                        0.94) /
                        0.32
                )

            val remain =
                1.0 - p

            return WaveParams(
                true,
                -30.0 *
                    remain,
                0.012 *
                    remain,
                -0.027 *
                    remain
            )
        }

        return WaveParams(
            true,
            0.0,
            0.0,
            0.0
        )
    }

    private fun reminderWaveParams(timeSeconds: Double): WaveParams {
        if (timeSeconds < 0.0 || timeSeconds >= REMINDER_DURATION_SECONDS) {
            return WaveParams(false, 0.0, 0.0, 0.0)
        }
        return waveParams(timeSeconds % WAVE_DURATION_SECONDS)
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
                -68.0 * p,
                0.0280 * p,
                -0.0520 * p
            )
        }

        if (timeSeconds < 0.92) {
            val p = (timeSeconds - 0.38) / 0.54
            val swing = sin(p * PI * 4.0)
            return WaveParams(
                true,
                -68.0 - 13.0 * swing,
                0.0280 + 0.0060 * swing,
                -0.0520 - 0.0040 * abs(swing)
            )
        }

        if (timeSeconds < 1.22) {
            val p = smoothStep((timeSeconds - 0.92) / 0.30)
            val remain = 1.0 - p
            return WaveParams(
                true,
                -68.0 * remain,
                0.0280 * remain,
                -0.0520 * remain
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
        private const val REMINDER_DURATION_SECONDS = 2.70
        private const val HAPPY_DURATION_SECONDS = 1.15
        private const val TAIL_WAG_DURATION_SECONDS = 2.35
        private const val PETTED_DURATION_SECONDS = 1.85
        private const val TIRED_DURATION_SECONDS = 4.2
        private const val TIRED_CROSSFADE_SECONDS = 0.32
        private const val WAKE_DURATION_SECONDS = 1.45
        private const val SLEEP_CROSSFADE_SECONDS = 0.42


        private const val BLINK_DURATION_NANOS = 340_000_000L
        private const val MIN_BLINK_DELAY_MS = 3_200L
        private const val MAX_BLINK_DELAY_MS = 7_800L
        private const val MIN_IDLE_ACTION_DELAY_MS = 4_800L
        private const val MAX_IDLE_ACTION_DELAY_MS = 10_500L
    }
}
