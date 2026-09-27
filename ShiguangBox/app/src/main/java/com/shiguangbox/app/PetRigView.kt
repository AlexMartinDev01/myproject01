package com.shiguangbox.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
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

    private fun decodePetBitmap(
        resourceId: Int,
        fallbackResourceId: Int
    ): Bitmap {
        val decoded =
            runCatching {
                BitmapFactory.decodeResource(
                    resources,
                    resourceId
                )
            }.getOrNull()

        if (decoded != null) {
            return decoded
        }

        val fallback =
            runCatching {
                BitmapFactory.decodeResource(
                    resources,
                    fallbackResourceId
                )
            }.getOrNull()

        return fallback
            ?: Bitmap.createBitmap(
                2,
                2,
                Bitmap.Config.ARGB_8888
            )
    }

    private fun mirrorBitmapHorizontally(
        source: Bitmap
    ): Bitmap {
        val output =
            Bitmap.createBitmap(
                source.width,
                source.height,
                Bitmap.Config.ARGB_8888
            )

        output.density =
            source.density

        val canvas =
            Canvas(
                output
            )

        val mirrorPaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG or
                    Paint.FILTER_BITMAP_FLAG
            )

        canvas.save()

        canvas.scale(
            -1f,
            1f,
            source.width /
                2f,
            source.height /
                2f
        )

        canvas.drawBitmap(
            source,
            0f,
            0f,
            mirrorPaint
        )

        canvas.restore()

        return output
    }

    private val yutuanBitmap: Bitmap? =
        if (
            petKind ==
            PetKind.YUTUAN
        ) {
            YutuanEmbeddedAsset.bitmap
        } else {
            null
        }

    private val idleBitmap: Bitmap =
        yutuanBitmap
            ?: decodePetBitmap(
                visualResources.idle,
                R.drawable.pet_orange_idle
            )

    private val sleepBitmap: Bitmap =
        yutuanBitmap
            ?: decodePetBitmap(
                visualResources.sleep,
                R.drawable.pet_orange_sleep
            )

    private val tiredBitmap: Bitmap =
        yutuanBitmap
            ?: decodePetBitmap(
                visualResources.tired,
                R.drawable.pet_orange_tired
            )

    private val wakeBitmap: Bitmap =
        yutuanBitmap
            ?: decodePetBitmap(
                visualResources.wake,
                R.drawable.pet_orange_wake
            )

    // V2.4.1 橘团方向资源修复：
    // 实机发现旧版左 3/4 / 左侧面 WebP 文件本身损坏，
    // Android 解码后会出现黑影/异常色块。现在只加载经过验证的右向资源，
    // 左向图在内存中由右向图水平镜像生成，从源头保证左右像素质量一致。
    // 正面仍继续使用现有 idleBitmap，不改变原始橘团形象。
    private val orange3qRightBitmap: Bitmap? =
        if (
            petKind ==
            PetKind.ORANGE
        ) {
            decodePetBitmap(
                R.drawable.pet_orange_3q_right,
                R.drawable.pet_orange_idle
            )
        } else {
            null
        }

    private val orange3qLeftBitmap: Bitmap? =
        orange3qRightBitmap
            ?.let {
                mirrorBitmapHorizontally(
                    it
                )
            }

    private val orangeSideRightBitmap: Bitmap? =
        if (
            petKind ==
            PetKind.ORANGE
        ) {
            decodePetBitmap(
                R.drawable.pet_orange_side_right,
                R.drawable.pet_orange_idle
            )
        } else {
            null
        }

    private val orangeSideLeftBitmap: Bitmap? =
        orangeSideRightBitmap
            ?.let {
                mirrorBitmapHorizontally(
                    it
                )
            }

    private data class NormalizedAlphaBounds(
        val left: Double,
        val top: Double,
        val right: Double,
        val bottom: Double
    ) {
        val width:
            Double
            get() =
                (
                    right -
                        left
                    )
                    .coerceAtLeast(
                        0.001
                    )

        val height:
            Double
            get() =
                (
                    bottom -
                        top
                    )
                    .coerceAtLeast(
                        0.001
                    )

        val centerX:
            Double
            get() =
                (
                    left +
                        right
                    ) /
                    2.0
    }

    private fun findAlphaBounds(
        bitmap: Bitmap
    ): NormalizedAlphaBounds {
        val bitmapW =
            bitmap.width
                .coerceAtLeast(
                    1
                )

        val bitmapH =
            bitmap.height
                .coerceAtLeast(
                    1
                )

        val pixels =
            IntArray(
                bitmapW *
                    bitmapH
            )

        bitmap.getPixels(
            pixels,
            0,
            bitmapW,
            0,
            0,
            bitmapW,
            bitmapH
        )

        var minX =
            bitmapW

        var minY =
            bitmapH

        var maxX =
            -1

        var maxY =
            -1

        var index =
            0

        for (
            y in
            0 until bitmapH
        ) {
            for (
                x in
                0 until bitmapW
            ) {
                val alpha =
                    (
                        pixels[
                            index++
                        ] ushr
                            24
                        ) and
                        0xff

                if (
                    alpha >
                    14
                ) {
                    if (
                        x <
                        minX
                    ) {
                        minX =
                            x
                    }

                    if (
                        x >
                        maxX
                    ) {
                        maxX =
                            x
                    }

                    if (
                        y <
                        minY
                    ) {
                        minY =
                            y
                    }

                    if (
                        y >
                        maxY
                    ) {
                        maxY =
                            y
                    }
                }
            }
        }

        if (
            maxX <
            minX ||
            maxY <
            minY
        ) {
            return NormalizedAlphaBounds(
                left =
                    0.0,
                top =
                    0.0,
                right =
                    1.0,
                bottom =
                    1.0
            )
        }

        return NormalizedAlphaBounds(
            left =
                minX
                    .toDouble() /
                    bitmapW
                        .toDouble(),
            top =
                minY
                    .toDouble() /
                    bitmapH
                        .toDouble(),
            right =
                (
                    maxX +
                        1
                    )
                    .toDouble() /
                    bitmapW
                        .toDouble(),
            bottom =
                (
                    maxY +
                        1
                    )
                    .toDouble() /
                    bitmapH
                        .toDouble()
        )
    }

    // 这些边界在 View 初始化阶段一次性计算。
    // 不使用 lazy，避免用户第一次触发转身时才扫描像素造成首帧卡顿。
    private val orangeFrontAlphaBounds =
        findAlphaBounds(
            idleBitmap
        )

    private val orange3qRightAlphaBounds =
        findAlphaBounds(
            orange3qRightBitmap
                ?: idleBitmap
        )

    private val orange3qLeftAlphaBounds =
        NormalizedAlphaBounds(
            left =
                1.0 -
                    orange3qRightAlphaBounds.right,
            top =
                orange3qRightAlphaBounds.top,
            right =
                1.0 -
                    orange3qRightAlphaBounds.left,
            bottom =
                orange3qRightAlphaBounds.bottom
        )

    private val orangeSideRightAlphaBounds =
        findAlphaBounds(
            orangeSideRightBitmap
                ?: idleBitmap
        )

    private val orangeSideLeftAlphaBounds =
        NormalizedAlphaBounds(
            left =
                1.0 -
                    orangeSideRightAlphaBounds.right,
            top =
                orangeSideRightAlphaBounds.top,
            right =
                1.0 -
                    orangeSideRightAlphaBounds.left,
            bottom =
                orangeSideRightAlphaBounds.bottom
        )

    private fun orangeAlphaBoundsFor(
        bitmap: Bitmap
    ): NormalizedAlphaBounds =
        when {
            bitmap ===
                orange3qRightBitmap ->
                orange3qRightAlphaBounds

            bitmap ===
                orange3qLeftBitmap ->
                orange3qLeftAlphaBounds

            bitmap ===
                orangeSideRightBitmap ->
                orangeSideRightAlphaBounds

            bitmap ===
                orangeSideLeftBitmap ->
                orangeSideLeftAlphaBounds

            else ->
                orangeFrontAlphaBounds
        }

    private val paint = Paint(
        Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG
    )

    // V2.1.5 桌面氛围试验：
    // 不改变宠物原图，只在宠物周围增加轻量环境光与少量漂浮粒子。
    // 所有特效均由 Canvas 实时绘制，无新增复杂 UI、无贴图资源。
    private val ambientGlowPaint =
        Paint(Paint.ANTI_ALIAS_FLAG)

    private val ambientParticlePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            strokeCap = Paint.Cap.ROUND
        }

    private var ambientGlowShader:
        RadialGradient? =
        null

    private var ambientGlowWidth =
        -1

    private var ambientGlowHeight =
        -1

    private var ambientGlowNight =
        false

    private val ambientParticleAnchors =
        arrayOf(
            floatArrayOf(0.17f, 0.24f, 0.88f, 0.10f),
            floatArrayOf(0.83f, 0.27f, 1.16f, 1.80f),
            floatArrayOf(0.12f, 0.53f, 0.96f, 3.10f),
            floatArrayOf(0.88f, 0.55f, 1.08f, 4.40f),
            floatArrayOf(0.23f, 0.76f, 0.82f, 5.70f),
            floatArrayOf(0.77f, 0.78f, 1.02f, 0.90f),
            floatArrayOf(0.30f, 0.13f, 0.78f, 2.30f),
            floatArrayOf(0.70f, 0.14f, 0.92f, 3.80f),
            floatArrayOf(0.16f, 0.70f, 1.12f, 5.20f),
            floatArrayOf(0.84f, 0.68f, 0.86f, 1.40f),
            floatArrayOf(0.48f, 0.075f, 0.76f, 4.90f),
            floatArrayOf(0.52f, 0.86f, 0.90f, 2.80f)
        )


    private val yutuanRainSystem: YutuanRainSystem? =
        if (
            petKind ==
            PetKind.YUTUAN
        ) {
            YutuanRainSystem()
        } else {
            null
        }

    private val meshWidth = 28
    private val meshHeight = 28
    private val verts = FloatArray((meshWidth + 1) * (meshHeight + 1) * 2)

    private var callbackPosted = false
    private var paused = false

    private var state: State = State.IDLE
    private var stateChangeListener:
        ((State, State) -> Unit)? = null
    private var stateStartNanos = 0L
    private var idleEpochNanos = 0L
    private var lastInteractionNanos = 0L

    private var blinkStartNanos = 0L
    private var nextBlinkNanos = 0L
    private var nextIdleActionNanos = 0L

    private var lastIdleAction = -1
    private var lastTouchReaction = -1

    private var currentMotion =
        PetMotion.NONE

    private var motionStartNanos =
        0L

    private var motionModifier =
        MotionModifier()

    // Locomotion：
    // WALK 不作为一次性 PetMotion，而是独立的持续移动层。
    // 三只宠物共享真实屏幕位移控制，但保留各自独立的 Mesh 步态。
    // V2.3.1 开始让 Service 的根位移按这里的 gait cycle 锁相，减少“腿动身体滑”的感觉。
    private var locomotionActive =
        false

    private var locomotionDirection =
        1f

    // 由 Overlay 的真实位移进度驱动。
    // 起步/刹车阶段降低到约 0.5，巡航阶段保持 1.0，
    // 这样窗口减速时脚步也会同步收小，不会原地“空踩”。
    private var locomotionIntensity =
        1f

    private var locomotionStartNanos =
        0L

    // V2.4.0 Directional Locomotion：
    // amount 0 = 正面，1 = 3/4，2 = 纯侧面。
    // 结束行走时记录当下 amount，再平滑倒放到 0，
    // 因此即使用户在刚起步时打断，也不会突然闪成完整侧面。
    private var orangeDirectionalReturnStartNanos =
        0L

    private var orangeDirectionalReturnFromAmount =
        0f

    private var orangeDirectionalReturnDirection =
        1f

    // Walking Attention V2.3.3：
    // 不再用固定正弦让头机械左右摆，而是使用“随机目标 + 眼睛先到 + 头后跟 + 停留”的注意力状态机。
    private var locomotionLookFrom =
        0f

    private var locomotionLookTarget =
        0f

    private var locomotionLookTransitionStartNanos =
        0L

    private var locomotionEyeTurnDurationNanos =
        220_000_000L

    private var locomotionHeadTurnDurationNanos =
        420_000_000L

    private var locomotionNextLookNanos =
        0L

    private var locomotionEyeLook =
        0f

    private var locomotionHeadLook =
        0f

    private var locomotionHeadLift =
        0f

    private val recentMotions =
        mutableListOf<PetMotion>()

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
        val now =
            System.nanoTime()
        val oldState =
            state

        state =
            State.IDLE
        stateStartNanos =
            now
        idleEpochNanos =
            now
        lastInteractionNanos =
            now
        blinkStartNanos =
            0L
        clearMotion()

        scheduleNextBlink(
            now
        )
        scheduleNextIdleAction(
            now
        )

        if (
            oldState !=
            State.IDLE
        ) {
            stateChangeListener
                ?.invoke(
                    oldState,
                    State.IDLE
                )
        }

        paused =
            false
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

    fun playMotion(
        motion: PetMotion,
        modifier:
            MotionModifier =
            MotionModifier(),
        replaceCurrent: Boolean = false
    ): Boolean {
        if (
            motion ==
            PetMotion.NONE
        ) {
            clearMotion()
            return true
        }

        if (
            state !=
            State.IDLE
        ) {
            return false
        }

        if (
            currentMotion !=
            PetMotion.NONE &&
            !replaceCurrent
        ) {
            return false
        }

        currentMotion =
            motion
        motionStartNanos =
            System.nanoTime()
        motionModifier =
            modifier.copy(
                intensity =
                    modifier.intensity
                        .coerceIn(
                            0.55f,
                            1.45f
                        ),
                speed =
                    modifier.speed
                        .coerceIn(
                            0.55f,
                            1.55f
                        ),
                direction =
                    if (
                        modifier.direction <
                        0f
                    ) {
                        -1f
                    } else {
                        1f
                    }
            )

        rememberMotion(
            motion
        )

        blinkStartNanos =
            0L

        paused =
            false
        postFrame()

        return true
    }

    fun startWalking(
        direction: Float
    ): Boolean {
        if (
            state ==
            State.SLEEP ||
            state ==
            State.TIRED ||
            state ==
            State.WAKE_UP ||
            state ==
            State.DRAGGING
        ) {
            return false
        }

        val now =
            System.nanoTime()

        if (
            state !=
            State.IDLE
        ) {
            setState(
                State.IDLE,
                now
            )
        }

        clearMotion()

        locomotionDirection =
            if (
                direction <
                0f
            ) {
                -1f
            } else {
                1f
            }

        locomotionStartNanos =
            now

        if (
            petKind ==
            PetKind.ORANGE
        ) {
            orangeDirectionalReturnStartNanos =
                0L
            orangeDirectionalReturnFromAmount =
                0f
        }

        resetLocomotionAttention(
            now
        )

        locomotionIntensity =
            1f

        locomotionActive =
            true

        lastInteractionNanos =
            now

        blinkStartNanos =
            0L

        paused =
            false

        postFrame()

        return true
    }

    fun stopWalking() {
        if (
            !locomotionActive
        ) {
            return
        }

        val now =
            System.nanoTime()

        if (
            petKind ==
            PetKind.ORANGE
        ) {
            orangeDirectionalReturnDirection =
                locomotionDirection

            orangeDirectionalReturnFromAmount =
                orangeDirectionalAmountWhileWalking(
                    now
                )

            orangeDirectionalReturnStartNanos =
                if (
                    orangeDirectionalReturnFromAmount >
                    0.02f
                ) {
                    now
                } else {
                    0L
                }
        }

        locomotionActive =
            false

        locomotionIntensity =
            1f

        locomotionStartNanos =
            0L

        clearLocomotionAttention()

        scheduleNextIdleAction(
            now
        )

        paused =
            false

        postFrame()
    }

    fun isWalking():
        Boolean =
        locomotionActive

    fun walkingCycleSeconds():
        Double =
        when (
            petKind
        ) {
            PetKind.ORANGE ->
                0.48

            PetKind.YAYA ->
                0.60

            PetKind.YUTUAN ->
                0.76
        }

    fun walkingElapsedSeconds(
        nowNanos: Long =
            System.nanoTime()
    ):
        Double {
        if (
            !locomotionActive ||
            locomotionStartNanos <=
            0L
        ) {
            return 0.0
        }

        return (
            nowNanos -
                locomotionStartNanos
            )
            .coerceAtLeast(
                0L
            ) /
            1_000_000_000.0
    }

    fun setWalkingIntensity(
        intensity: Float
    ) {
        locomotionIntensity =
            intensity.coerceIn(
                0.42f,
                1.12f
            )

        if (
            locomotionActive
        ) {
            paused =
                false
            postFrame()
        }
    }

    fun currentMotion():
        PetMotion =
        currentMotion

    private fun clearMotion() {
        currentMotion =
            PetMotion.NONE
        motionStartNanos =
            0L
        motionModifier =
            MotionModifier()
    }

    private fun rememberMotion(
        motion: PetMotion
    ) {
        if (
            motion ==
            PetMotion.NONE
        ) {
            return
        }

        recentMotions
            .remove(
                motion
            )

        recentMotions
            .add(
                motion
            )

        while (
            recentMotions.size >
            3
        ) {
            recentMotions
                .removeAt(
                    0
                )
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

    fun setYutuanRainTarget(
        intensity: Float
    ) {
        yutuanRainSystem
            ?.setRainTargetScale(
                intensity
            )

        paused = false
        postFrame()
    }

    fun setStateChangeListener(
        listener:
            ((State, State) -> Unit)?
    ) {
        stateChangeListener =
            listener
    }

    fun currentState():
        State =
        state

    fun currentPetId(): String = petKind.id

    fun displayName(): String = petKind.displayName

    fun displayEmoji(): String = petKind.emoji

    fun signatureActionLabel(): String =
        when (petKind) {
            PetKind.YAYA -> "耳朵轻晃"
            PetKind.YUTUAN -> "抖水甩耳"
            else -> "摇尾巴"
        }

    fun canDoAmbientAction(): Boolean =
        state ==
            State.IDLE &&
            currentMotion ==
                PetMotion.NONE &&
            !locomotionActive

    fun isSleepingOrTired(): Boolean =
        state == State.SLEEP ||
            state == State.TIRED ||
            state == State.WAKE_UP

    fun startDragging() {
        stopWalking()

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
        paused = true
        Choreographer.getInstance()
            .removeFrameCallback(this)
        callbackPosted = false
        stateChangeListener = null
        clearMotion()
        locomotionActive =
            false
        locomotionStartNanos =
            0L
        orangeDirectionalReturnStartNanos =
            0L
        orangeDirectionalReturnFromAmount =
            0f
        clearLocomotionAttention()
        yutuanRainSystem?.reset()

        // Resource bitmaps are intentionally NOT recycled manually.
        // Android may still have a pending draw while the overlay is
        // being replaced. Recycling here can crash Canvas with
        // "trying to use a recycled bitmap".
    }

    private fun setState(
        newState: State,
        now: Long = System.nanoTime()
    ) {
        val oldState =
            state

        state =
            newState
        stateStartNanos =
            now
        blinkStartNanos =
            0L

        if (
            newState !=
            State.IDLE
        ) {
            locomotionActive =
                false
            locomotionStartNanos =
                0L
            orangeDirectionalReturnStartNanos =
                0L
            orangeDirectionalReturnFromAmount =
                0f
            clearLocomotionAttention()
        }

        if (
            newState !=
            State.IDLE
        ) {
            clearMotion()
        }

        if (
            newState ==
            State.IDLE
        ) {
            idleEpochNanos =
                now
            scheduleNextBlink(
                now
            )
            scheduleNextIdleAction(
                now
            )
        }

        if (
            oldState !=
            newState
        ) {
            stateChangeListener
                ?.invoke(
                    oldState,
                    newState
                )
        }

        paused =
            false
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

        drawAmbientGlow(
            canvas = canvas,
            idleSeconds = idleSeconds
        )

        drawAmbientParticles(
            canvas = canvas,
            idleSeconds = idleSeconds,
            frontLayer = false
        )

        val petContentSave =
            canvas.save()

        canvas.scale(
            AMBIENT_CONTENT_SCALE,
            AMBIENT_CONTENT_SCALE,
            width / 2f,
            height / 2f
        )

        if (
            petKind ==
            PetKind.YUTUAN
        ) {
            yutuanRainSystem?.update(
                nowNanos = now,
                width = width.toFloat(),
                height = height.toFloat(),
                state = state,
                stateSeconds = stateSeconds
            )

            yutuanRainSystem?.drawBehind(
                canvas = canvas,
                width = width.toFloat(),
                height = height.toFloat()
            )
        }

        when (state) {
            State.TIRED -> drawTiredState(canvas, idleSeconds, stateSeconds)

            State.SLEEP -> drawSleepState(canvas, idleSeconds, stateSeconds)

            State.WAKE_UP -> drawWakeState(canvas, idleSeconds, stateSeconds)

            else -> {
                val normalBlink =
                    currentBlinkAmount(
                        now
                    )

                val comboBlink =
                    when (state) {
                        State.TAIL_WAG ->
                            tailComboBlinkAmount(
                                stateSeconds
                            )

                        State.PETTED ->
                            pettingBlinkAmount(
                                stateSeconds
                            )

                        else ->
                            0.0
                    }

                val motionBlink =
                    currentMotionBlinkAmount(
                        now
                    )

                val blink =
                    maxOf(
                        normalBlink,
                        comboBlink,
                        motionBlink
                    )

                if (
                    petKind ==
                    PetKind.ORANGE &&
                    (
                        locomotionActive ||
                        orangeDirectionalReturnStartNanos >
                        0L
                    )
                ) {
                    drawOrangeDirectionalLocomotion(
                        canvas =
                            canvas,
                        now =
                            now,
                        idleSeconds =
                            idleSeconds,
                        stateSeconds =
                            stateSeconds,
                        blinkAmount =
                            blink
                    )
                } else {
                    buildMesh(
                        idleSeconds,
                        stateSeconds,
                        blink
                    )

                    applyLocomotionToMesh(
                        now
                    )

                    applyCurrentMotionToMesh(
                        now
                    )

                    drawIdleMesh(
                        canvas,
                        1f
                    )
                }
            }
        }

        if (
            petKind ==
            PetKind.YUTUAN
        ) {
            yutuanRainSystem?.drawFront(
                canvas = canvas,
                width = width.toFloat(),
                height = height.toFloat(),
                state = state,
                stateSeconds = stateSeconds
            )
        }

        canvas.restoreToCount(
            petContentSave
        )

        drawAmbientParticles(
            canvas = canvas,
            idleSeconds = idleSeconds,
            frontLayer = true
        )
    }

    private fun isAmbientNight():
        Boolean {
        val hour =
            LocalTime
                .now()
                .hour

        return hour >=
            20 ||
            hour <
            7
    }

    private fun ambientRgb(
        night: Boolean
    ): IntArray {
        return when (
            petKind
        ) {
            PetKind.ORANGE ->
                if (
                    night
                ) {
                    intArrayOf(
                        255,
                        187,
                        117
                    )
                } else {
                    intArrayOf(
                        255,
                        198,
                        112
                    )
                }

            PetKind.YAYA ->
                if (
                    night
                ) {
                    intArrayOf(
                        157,
                        224,
                        203
                    )
                } else {
                    intArrayOf(
                        166,
                        235,
                        190
                    )
                }

            PetKind.YUTUAN ->
                if (
                    night
                ) {
                    intArrayOf(
                        151,
                        184,
                        255
                    )
                } else {
                    intArrayOf(
                        164,
                        211,
                        255
                    )
                }
        }
    }

    private fun ensureAmbientGlowShader(
        night: Boolean
    ) {
        if (
            ambientGlowShader !=
            null &&
            ambientGlowWidth ==
            width &&
            ambientGlowHeight ==
            height &&
            ambientGlowNight ==
            night
        ) {
            return
        }

        val rgb =
            ambientRgb(
                night
            )

        val centerAlpha =
            if (
                night
            ) {
                132
            } else {
                104
            }

        val middleAlpha =
            if (
                night
            ) {
                58
            } else {
                42
            }

        ambientGlowShader =
            RadialGradient(
                width *
                    0.50f,
                height *
                    0.55f,
                minOf(
                    width,
                    height
                ) *
                    0.60f,
                intArrayOf(
                    Color.argb(
                        centerAlpha,
                        rgb[0],
                        rgb[1],
                        rgb[2]
                    ),
                    Color.argb(
                        middleAlpha,
                        rgb[0],
                        rgb[1],
                        rgb[2]
                    ),
                    Color.TRANSPARENT
                ),
                floatArrayOf(
                    0.0f,
                    0.55f,
                    1.0f
                ),
                Shader.TileMode.CLAMP
            )

        ambientGlowPaint.shader =
            ambientGlowShader

        ambientGlowWidth =
            width

        ambientGlowHeight =
            height

        ambientGlowNight =
            night
    }

    private fun drawAmbientGlow(
        canvas: Canvas,
        idleSeconds: Double
    ) {
        val night =
            isAmbientNight()

        ensureAmbientGlowShader(
            night
        )

        val stateStrength =
            when (
                state
            ) {
                State.HAPPY ->
                    1.18

                State.PETTED ->
                    1.12

                State.REMINDER ->
                    1.08

                State.SLEEP ->
                    0.58

                State.TIRED ->
                    0.72

                else ->
                    1.0
            }

        val pulse =
            0.88 +
                0.12 *
                    (
                        sin(
                            idleSeconds *
                                2.0 *
                                PI /
                                5.2
                        ) +
                            1.0
                        ) /
                    2.0

        ambientGlowPaint.alpha =
            (
                255.0 *
                    pulse *
                    stateStrength
                )
                .toInt()
                .coerceIn(
                    0,
                    255
                )

        val save =
            canvas.save()

        canvas.scale(
            1.0f,
            0.82f,
            width *
                0.50f,
            height *
                0.57f
        )

        canvas.drawCircle(
            width *
                0.50f,
            height *
                0.55f,
            minOf(
                width,
                height
            ) *
                0.60f,
            ambientGlowPaint
        )

        canvas.restoreToCount(
            save
        )

        ambientGlowPaint.alpha =
            255
    }

    private fun drawAmbientParticles(
        canvas: Canvas,
        idleSeconds: Double,
        frontLayer: Boolean
    ) {
        val night =
            isAmbientNight()

        val rgb =
            ambientRgb(
                night
            )

        val sleepFactor =
            when (
                state
            ) {
                State.SLEEP ->
                    0.45

                State.TIRED ->
                    0.66

                else ->
                    1.0
            }

        val happyFactor =
            when (
                state
            ) {
                State.HAPPY ->
                    1.30

                State.PETTED ->
                    1.20

                else ->
                    1.0
            }

        val minDimension =
            minOf(
                width,
                height
            )
                .toFloat()

        for (
            index in
            ambientParticleAnchors
                .indices
        ) {
            if (
                (
                    index %
                        2 ==
                        1
                    ) !=
                frontLayer
            ) {
                continue
            }

            val spec =
                ambientParticleAnchors[
                    index
                ]

            val baseX =
                spec[0]

            val baseY =
                spec[1]

            val speed =
                spec[2]

            val phase =
                spec[3]

            val x =
                width *
                    (
                        baseX +
                            0.018 *
                                sin(
                                    idleSeconds *
                                        speed +
                                        phase
                                )
                        )
                    .toFloat()

            val y =
                height *
                    (
                        baseY +
                            0.020 *
                                cos(
                                    idleSeconds *
                                        speed *
                                        0.78 +
                                        phase *
                                        1.17
                                )
                        )
                    .toFloat()

            val shimmer =
                (
                    0.38 +
                        0.62 *
                            (
                                sin(
                                    idleSeconds *
                                        (
                                            speed +
                                                0.42
                                            ) *
                                        1.45 +
                                        phase
                                ) +
                                    1.0
                                ) /
                            2.0
                    )
                    .coerceIn(
                        0.0,
                        1.0
                    )

            val baseAlpha =
                if (
                    night
                ) {
                    192
                } else {
                    154
                }

            val layerAlpha =
                if (
                    frontLayer
                ) {
                    0.82
                } else {
                    0.62
                }

            val alpha =
                (
                    baseAlpha *
                        shimmer *
                        layerAlpha *
                        sleepFactor *
                        happyFactor
                    )
                    .toInt()
                    .coerceIn(
                        0,
                        205
                    )

            if (
                alpha <=
                3
            ) {
                continue
            }

            val radius =
                minDimension *
                    (
                        0.0100f +
                            (
                                index %
                                    3
                                ) *
                                0.0022f
                        )

            // 很淡的外圈，让粒子像光点，而不是实心圆珠。
            ambientParticlePaint.color =
                Color.argb(
                    (
                        alpha *
                            0.24
                        )
                        .toInt(),
                    rgb[0],
                    rgb[1],
                    rgb[2]
                )

            canvas.drawCircle(
                x,
                y,
                radius *
                    2.70f,
                ambientParticlePaint
            )

            ambientParticlePaint.color =
                Color.argb(
                    alpha,
                    rgb[0],
                    rgb[1],
                    rgb[2]
                )

            canvas.drawCircle(
                x,
                y,
                radius,
                ambientParticlePaint
            )

            if (
                frontLayer &&
                index %
                    4 ==
                    1 &&
                shimmer >
                    0.68
            ) {
                ambientParticlePaint.color =
                    Color.argb(
                        (
                            alpha *
                                0.72
                            )
                            .toInt(),
                        255,
                        255,
                        255
                    )

                ambientParticlePaint.strokeWidth =
                    maxOf(
                        1.0f,
                        radius *
                            0.42f
                    )

                canvas.drawLine(
                    x -
                        radius *
                            1.65f,
                    y,
                    x +
                        radius *
                            1.65f,
                    y,
                    ambientParticlePaint
                )

                canvas.drawLine(
                    x,
                    y -
                        radius *
                            1.65f,
                    x,
                    y +
                        radius *
                            1.65f,
                    ambientParticlePaint
                )
            }
        }
    }

    private fun drawTiredState(
        canvas: Canvas,
        idleSeconds: Double,
        stateSeconds: Double
    ) {
        if (petKind == PetKind.YUTUAN) {
            val p =
                smoothStep(
                    clamp(
                        stateSeconds /
                            TIRED_DURATION_SECONDS,
                        0.0,
                        1.0
                    )
                )

            // 雨团犯困：不是直接“闭眼”，而是头越来越低、
            // 身体慢慢泄力、耳朵更垂，最后自然进入睡姿。
            val nod =
                abs(
                    sin(
                        stateSeconds *
                            PI *
                            0.72
                    )
                ) *
                    p

            val tiredBlink =
                (
                    0.30 +
                        0.56 *
                            p +
                        0.10 *
                            nod
                    )
                    .coerceIn(
                        0.0,
                        0.96
                    )

            val save =
                canvas.save()

            canvas.translate(
                0f,
                (
                    height *
                        (
                            0.010 +
                                0.022 *
                                    p +
                                0.010 *
                                    nod
                            )
                    ).toFloat()
            )

            canvas.scale(
                (
                    1.0 +
                        0.012 *
                            p
                    ).toFloat(),
                (
                    1.0 -
                        0.038 *
                            p
                    ).toFloat(),
                width / 2f,
                height * 0.72f
            )

            canvas.rotate(
                (
                    -1.4 *
                        p *
                        sin(
                            stateSeconds *
                                PI *
                                0.42
                        )
                    ).toFloat(),
                width / 2f,
                height * 0.48f
            )

            buildYutuanMesh(
                idleSeconds,
                stateSeconds,
                tiredBlink
            )

            drawIdleMesh(
                canvas,
                1f
            )

            canvas.restoreToCount(
                save
            )
            return
        }

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
        if (petKind == PetKind.YUTUAN) {
            val enter =
                smoothStep(
                    clamp(
                        stateSeconds /
                            YUTUAN_SLEEP_SETTLE_SECONDS,
                        0.0,
                        1.0
                    )
                )

            val breath =
                sin(
                    idleSeconds *
                        2.0 *
                        PI /
                        4.6
                )

            val save =
                canvas.save()

            // 真正的睡姿：整体压低并稍微侧卧。
            // 这样不会再出现“闭眼但仍然站着”的感觉。
            canvas.translate(
                (
                    -width *
                        0.018 *
                        enter
                    ).toFloat(),
                (
                    height *
                        (
                            0.118 *
                                enter +
                                0.005 *
                                    breath *
                                    enter
                            )
                    ).toFloat()
            )

            canvas.rotate(
                (
                    -7.5 *
                        enter
                    ).toFloat(),
                width * 0.52f,
                height * 0.72f
            )

            canvas.scale(
                (
                    1.0 +
                        0.090 *
                            enter +
                        0.003 *
                            breath *
                            enter
                    ).toFloat(),
                (
                    1.0 -
                        0.285 *
                            enter +
                        0.008 *
                            breath *
                            enter
                    ).toFloat(),
                width / 2f,
                height * 0.78f
            )

            buildYutuanMesh(
                idleSeconds,
                stateSeconds,
                1.0
            )

            drawIdleMesh(
                canvas,
                1f
            )

            canvas.restoreToCount(
                save
            )
            return
        }

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
        if (petKind == PetKind.YUTUAN) {
            val p =
                smoothStep(
                    clamp(
                        stateSeconds /
                            WAKE_DURATION_SECONDS,
                        0.0,
                        1.0
                    )
                )

            val bounce =
                sin(
                    p *
                        PI
                )

            val save =
                canvas.save()

            // 从侧卧压低的睡姿慢慢撑起来，
            // 中段带一个轻微伸懒腰回弹，最后回到待机。
            canvas.translate(
                (
                    -width *
                        0.018 *
                        (1.0 - p)
                    ).toFloat(),
                (
                    height *
                        (
                            0.118 *
                                (1.0 - p) -
                                0.030 *
                                    bounce
                            )
                    ).toFloat()
            )

            canvas.rotate(
                (
                    -7.5 *
                        (1.0 - p)
                    ).toFloat(),
                width * 0.52f,
                height * 0.72f
            )

            canvas.scale(
                (
                    1.090 -
                        0.090 *
                            p +
                        0.018 *
                            bounce
                    ).toFloat(),
                (
                    0.715 +
                        0.285 *
                            p -
                        0.018 *
                            bounce
                    ).toFloat(),
                width / 2f,
                height * 0.78f
            )

            buildYutuanMesh(
                idleSeconds,
                stateSeconds,
                (
                    1.0 -
                        p
                    )
                    .coerceIn(
                        0.0,
                        1.0
                    )
            )

            drawIdleMesh(
                canvas,
                1f
            )

            canvas.restoreToCount(
                save
            )
            return
        }

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

    private fun drawIdleMesh(
        canvas: Canvas,
        alpha: Float
    ) {
        drawCurrentMeshBitmap(
            canvas =
                canvas,
            bitmap =
                idleBitmap,
            alpha =
                alpha
        )
    }

    private fun updateState(now: Long) {
        val elapsedSeconds =
            (now - stateStartNanos).coerceAtLeast(0L) / 1_000_000_000.0

        when (state) {
            State.IDLE -> {
                updateMotion(
                    now
                )
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

    private fun maybeStartRandomIdleAction(
        now: Long
    ) {
        if (
            locomotionActive ||
            currentMotion !=
            PetMotion.NONE ||
            blinkStartNanos !=
            0L ||
            now <
            nextIdleActionNanos
        ) {
            return
        }

        val roll =
            Random.nextInt(
                100
            )

        when {
            // 10%：什么都不发生。真正的待机需要留白。
            roll <
                10 -> {
                scheduleNextIdleAction(
                    now
                )
            }

            // 15%：只有眨眼。
            roll <
                25 -> {
                blinkStartNanos =
                    now
                nextBlinkNanos =
                    now +
                        BLINK_DURATION_NANOS +
                        randomBlinkDelayNanos()

                scheduleNextIdleAction(
                    now
                )
            }

            // 15%：保留经典签名动作 / 挥爪。
            roll <
                40 -> {
                var candidate =
                    Random.nextInt(
                        2
                    )

                if (
                    candidate ==
                    lastIdleAction
                ) {
                    candidate =
                        1 -
                            candidate
                }

                lastIdleAction =
                    candidate

                if (
                    candidate ==
                    0
                ) {
                    setState(
                        State.TAIL_WAG,
                        now
                    )
                } else {
                    setState(
                        State.WAVE,
                        now
                    )
                }
            }

            // 42%：新的微动作和中动作。
            roll <
                82 -> {
                val pool =
                    listOf(
                        PetMotion.LOOK_AROUND,
                        PetMotion.HEAD_TILT,
                        PetMotion.LOOK_UP,
                        PetMotion.STRETCH,
                        PetMotion.DOZE_NOD,
                        PetMotion.SHY
                    )

                playMotion(
                    chooseMotion(
                        pool
                    ),
                    randomMotionModifier()
                )
            }

            // 18%：存在感更强的大动作。
            else -> {
                val pool =
                    listOf(
                        PetMotion.SMALL_JUMP,
                        PetMotion.SEEK_ATTENTION
                    )

                playMotion(
                    chooseMotion(
                        pool
                    ),
                    randomMotionModifier()
                )
            }
        }
    }

    private fun chooseMotion(
        pool: List<PetMotion>
    ): PetMotion {
        val fresh =
            pool.filter {
                it !in
                    recentMotions
            }

        return (
            if (
                fresh.isNotEmpty()
            ) {
                fresh
            } else {
                pool
            }
            )
            .random()
    }

    private fun randomMotionModifier():
        MotionModifier {
        val personalityIntensity =
            when (
                petKind
            ) {
                PetKind.ORANGE ->
                    Random.nextDouble(
                        0.95,
                        1.18
                    )

                PetKind.YAYA ->
                    Random.nextDouble(
                        0.78,
                        0.98
                    )

                PetKind.YUTUAN ->
                    Random.nextDouble(
                        0.82,
                        1.03
                    )
            }

        return MotionModifier(
            intensity =
                personalityIntensity
                    .toFloat(),
            speed =
                Random.nextDouble(
                    0.90,
                    1.10
                )
                    .toFloat(),
            direction =
                if (
                    Random.nextBoolean()
                ) {
                    1f
                } else {
                    -1f
                }
        )
    }

    private fun updateMotion(
        now: Long
    ) {
        val motion =
            currentMotion

        if (
            motion ==
            PetMotion.NONE
        ) {
            return
        }

        val durationNanos =
            (
                motion
                    .durationSeconds *
                    1_000_000_000.0 /
                    motionModifier
                        .speed
                        .coerceAtLeast(
                            0.55f
                        )
            )
                .toLong()
                .coerceAtLeast(
                    1L
                )

        if (
            now -
                motionStartNanos >=
            durationNanos
        ) {
            clearMotion()
            scheduleNextIdleAction(
                now
            )
        }
    }

    private fun motionProgress(
        now: Long
    ): Double {
        val motion =
            currentMotion

        if (
            motion ==
            PetMotion.NONE ||
            motionStartNanos <=
            0L
        ) {
            return 0.0
        }

        val duration =
            (
                motion
                    .durationSeconds /
                    motionModifier
                        .speed
                        .coerceAtLeast(
                            0.55f
                        )
            )
                .coerceAtLeast(
                    0.1
                )

        return clamp(
            (
                now -
                    motionStartNanos
                )
                .coerceAtLeast(
                    0L
                )
                .toDouble() /
                1_000_000_000.0 /
                duration,
            0.0,
            1.0
        )
    }

    private fun currentMotionBlinkAmount(
        now: Long
    ): Double {
        val p =
            motionProgress(
                now
            )

        return when (
            currentMotion
        ) {
            PetMotion.DOZE_NOD -> {
                val close =
                    smoothStep(
                        clamp(
                            (
                                p -
                                    0.18
                                ) /
                                0.28,
                            0.0,
                            1.0
                        )
                    )

                val open =
                    1.0 -
                        smoothStep(
                            clamp(
                                (
                                    p -
                                        0.72
                                    ) /
                                    0.20,
                                0.0,
                                1.0
                            )
                        )

                0.92 *
                    close *
                    open
            }

            PetMotion.HEAD_TILT ->
                if (
                    p in
                    0.48..0.64
                ) {
                    sin(
                        (
                            p -
                                0.48
                            ) /
                            0.16 *
                            PI
                    )
                        .coerceAtLeast(
                            0.0
                        )
                } else {
                    0.0
                }

            PetMotion.SHY ->
                if (
                    p in
                    0.36..0.54
                ) {
                    0.72 *
                        sin(
                            (
                                p -
                                    0.36
                                ) /
                                0.18 *
                                PI
                        )
                            .coerceAtLeast(
                                0.0
                            )
                } else {
                    0.0
                }

            else ->
                0.0
        }
    }

    /**
     * 芽芽 Walking Prototype V1
     *
     * 原始素材仍是一张正面 2D 图，因此这里不伪造“侧身大步走”，
     * 而是利用现有 BitmapMesh 做 Q 版小碎步：
     * - 左右脚交替轻抬 / 轻移
     * - 身体重心左右交换
     * - 头部做很小的反向补偿
     * - 身体产生极轻的上下踩踏节奏
     *
     * 这层只负责“看起来在走”，屏幕上的真实 x 位移由 Service 完成。
     */
    private fun clearLocomotionAttention() {
        locomotionLookFrom =
            0f
        locomotionLookTarget =
            0f
        locomotionLookTransitionStartNanos =
            0L
        locomotionNextLookNanos =
            0L
        locomotionEyeLook =
            0f
        locomotionHeadLook =
            0f
        locomotionHeadLift =
            0f
    }

    private fun resetLocomotionAttention(
        now: Long
    ) {
        locomotionLookFrom =
            0f

        locomotionLookTarget =
            locomotionDirection *
                when (
                    petKind
                ) {
                    PetKind.ORANGE ->
                        0.62f

                    PetKind.YAYA ->
                        0.48f

                    PetKind.YUTUAN ->
                        0.36f
                }

        locomotionLookTransitionStartNanos =
            now

        configureLocomotionTurnDurations()

        locomotionNextLookNanos =
            now +
                locomotionHeadTurnDurationNanos +
                randomLocomotionLookHoldNanos()
    }

    private fun configureLocomotionTurnDurations() {
        when (
            petKind
        ) {
            PetKind.ORANGE -> {
                locomotionEyeTurnDurationNanos =
                    Random.nextLong(
                        130_000_000L,
                        210_000_001L
                    )

                locomotionHeadTurnDurationNanos =
                    Random.nextLong(
                        260_000_000L,
                        390_000_001L
                    )
            }

            PetKind.YAYA -> {
                locomotionEyeTurnDurationNanos =
                    Random.nextLong(
                        170_000_000L,
                        270_000_001L
                    )

                locomotionHeadTurnDurationNanos =
                    Random.nextLong(
                        340_000_000L,
                        530_000_001L
                    )
            }

            PetKind.YUTUAN -> {
                locomotionEyeTurnDurationNanos =
                    Random.nextLong(
                        240_000_000L,
                        370_000_001L
                    )

                locomotionHeadTurnDurationNanos =
                    Random.nextLong(
                        500_000_000L,
                        760_000_001L
                    )
            }
        }
    }

    private fun randomLocomotionLookHoldNanos():
        Long =
        when (
            petKind
        ) {
            PetKind.ORANGE ->
                Random.nextLong(
                    520_000_000L,
                    1_050_000_001L
                )

            PetKind.YAYA ->
                Random.nextLong(
                    680_000_000L,
                    1_360_000_001L
                )

            PetKind.YUTUAN ->
                Random.nextLong(
                    900_000_000L,
                    1_720_000_001L
                )
        }

    private fun chooseNextLocomotionLookTarget():
        Float {
        val direction =
            locomotionDirection

        val roll =
            Random.nextFloat()

        return when (
            petKind
        ) {
            PetKind.ORANGE ->
                when {
                    roll <
                        0.56f ->
                        direction *
                            Random.nextDouble(
                                0.58,
                                0.96
                            )
                                .toFloat()

                    roll <
                        0.80f ->
                        Random.nextDouble(
                            -0.16,
                            0.16
                        )
                            .toFloat()

                    else ->
                        -direction *
                            Random.nextDouble(
                                0.32,
                                0.62
                            )
                                .toFloat()
                }

            PetKind.YAYA ->
                when {
                    roll <
                        0.48f ->
                        direction *
                            Random.nextDouble(
                                0.45,
                                0.84
                            )
                                .toFloat()

                    roll <
                        0.79f ->
                        Random.nextDouble(
                            -0.14,
                            0.14
                        )
                            .toFloat()

                    else ->
                        -direction *
                            Random.nextDouble(
                                0.38,
                                0.70
                            )
                                .toFloat()
                }

            PetKind.YUTUAN ->
                when {
                    roll <
                        0.42f ->
                        direction *
                            Random.nextDouble(
                                0.34,
                                0.66
                            )
                                .toFloat()

                    roll <
                        0.86f ->
                        Random.nextDouble(
                            -0.11,
                            0.11
                        )
                            .toFloat()

                    else ->
                        -direction *
                            Random.nextDouble(
                                0.28,
                                0.52
                            )
                                .toFloat()
                }
        }
    }

    private fun updateLocomotionAttention(
        now: Long
    ) {
        if (
            !locomotionActive
        ) {
            return
        }

        if (
            locomotionNextLookNanos <=
            0L
        ) {
            resetLocomotionAttention(
                now
            )
        }

        if (
            now >=
            locomotionNextLookNanos
        ) {
            // 上一个观察已经完成并停留，下一次从稳定目标继续转。
            locomotionLookFrom =
                locomotionLookTarget

            locomotionLookTarget =
                chooseNextLocomotionLookTarget()

            locomotionLookTransitionStartNanos =
                now

            configureLocomotionTurnDurations()

            locomotionNextLookNanos =
                now +
                    locomotionHeadTurnDurationNanos +
                    randomLocomotionLookHoldNanos()
        }

        fun transitionProgress(
            durationNanos:
                Long
        ): Double {
            if (
                durationNanos <=
                0L
            ) {
                return 1.0
            }

            return smoothStep(
                clamp(
                    (
                        now -
                            locomotionLookTransitionStartNanos
                        )
                        .coerceAtLeast(
                            0L
                        )
                        .toDouble() /
                        durationNanos
                            .toDouble(),
                    0.0,
                    1.0
                )
            )
        }

        val eyeProgress =
            transitionProgress(
                locomotionEyeTurnDurationNanos
            )

        val headProgress =
            transitionProgress(
                locomotionHeadTurnDurationNanos
            )

        val from =
            locomotionLookFrom
                .toDouble()

        val target =
            locomotionLookTarget
                .toDouble()

        val elapsed =
            walkingElapsedSeconds(
                now
            )

        // 极轻的眼球小扫视，不改变主观察方向，只避免“盯死一个点”。
        val saccade =
            when (
                petKind
            ) {
                PetKind.ORANGE ->
                    0.055 *
                        sin(
                            elapsed *
                                2.0 *
                                PI /
                                0.74 +
                                0.65
                        )

                PetKind.YAYA ->
                    0.040 *
                        sin(
                            elapsed *
                                2.0 *
                                PI /
                                0.91 +
                                1.10
                        )

                PetKind.YUTUAN ->
                    0.026 *
                        sin(
                            elapsed *
                                2.0 *
                                PI /
                                1.24 +
                                0.35
                        )
            }

        locomotionEyeLook =
            (
                from +
                    (
                        target -
                            from
                        ) *
                        eyeProgress +
                    saccade
                )
                .coerceIn(
                    -1.0,
                    1.0
                )
                .toFloat()

        locomotionHeadLook =
            (
                from +
                    (
                        target -
                            from
                        ) *
                        headProgress
                )
                .coerceIn(
                    -1.0,
                    1.0
                )
                .toFloat()

        // 抬头/低头非常轻，只用来打破“头永远锁在同一高度”的僵硬感。
        locomotionHeadLift =
            (
                when (
                    petKind
                ) {
                    PetKind.ORANGE ->
                        0.62 *
                            sin(
                                elapsed *
                                    2.0 *
                                    PI /
                                    1.68 +
                                    0.40
                            ) +
                            0.22 *
                                sin(
                                    elapsed *
                                        2.0 *
                                        PI /
                                        0.82 +
                                        1.30
                                )

                    PetKind.YAYA ->
                        0.52 *
                            sin(
                                elapsed *
                                    2.0 *
                                    PI /
                                    2.10 +
                                    0.72
                            ) +
                            0.18 *
                                sin(
                                    elapsed *
                                        2.0 *
                                        PI /
                                        1.06 +
                                        1.55
                                )

                    PetKind.YUTUAN ->
                        0.42 *
                            sin(
                                elapsed *
                                    2.0 *
                                    PI /
                                    2.78 +
                                    0.18
                            )
                }
                )
                .coerceIn(
                    -1.0,
                    1.0
                )
                .toFloat()
    }

    private fun applyLocomotionToMesh(
        now: Long
    ) {
        if (
            !locomotionActive ||
            width <=
            0 ||
            height <=
            0
        ) {
            return
        }

        updateLocomotionAttention(
            now
        )

        when (
            petKind
        ) {
            PetKind.ORANGE ->
                applyOrangeLocomotionToMesh(
                    now
                )

            PetKind.YAYA ->
                applyYayaLocomotionToMesh(
                    now
                )

            PetKind.YUTUAN ->
                applyYutuanLocomotionToMesh(
                    now
                )
        }
    }

    private fun applyYayaLocomotionToMesh(
        now: Long
    ) {
        val elapsedSeconds =
            (
                now -
                    locomotionStartNanos
                )
                .coerceAtLeast(
                    0L
                ) /
                1_000_000_000.0

        // Walking V3:
        // 让“走路”和“观察”变成两套不同节奏。
        // 脚步是快节奏，注意力转移是慢节奏：
        // 眼睛先看 -> 头跟过去 -> 停留 -> 回中 -> 看另一侧。
        val phase =
            elapsedSeconds *
                2.0 *
                PI /
                walkingCycleSeconds()

        val leftSignal =
            sin(
                phase
            )

        val rightSignal =
            -leftSignal

        val bodySway =
            sin(
                phase
            )

        val bodyBob =
            abs(
                sin(
                    phase
                )
            )

        val headLook =
            locomotionHeadLook
                .toDouble()

        val eyeLook =
            locomotionEyeLook
                .toDouble()

        val headLift =
            locomotionHeadLift
                .toDouble()

        val direction =
            locomotionDirection
                .toDouble()

        val gaitIntensity =
            locomotionIntensity
                .toDouble()

        val departureLean =
            (
                1.0 -
                    smoothStep(
                        clamp(
                            elapsedSeconds /
                                0.34,
                            0.0,
                            1.0
                        )
                    )
                ) *
                direction *
                gaitIntensity

        val viewW =
            width.toDouble()

        val viewH =
            height.toDouble()

        var index =
            0

        for (
            row in
            0..meshHeight
        ) {
            val v =
                row.toDouble() /
                    meshHeight.toDouble()

            for (
                col in
                0..meshWidth
            ) {
                val u =
                    col.toDouble() /
                        meshWidth.toDouble()

                var x =
                    verts[
                        index
                    ]
                        .toDouble() /
                        viewW

                var y =
                    verts[
                        index +
                            1
                    ]
                        .toDouble() /
                        viewH

                // 1) 身体：保持 V2 的明显步幅，但再增加一点重心感。
                val torsoWeight =
                    exp(
                        -square(
                            (
                                u -
                                    0.515
                                ) /
                                0.33
                        ) -
                            square(
                                (
                                    v -
                                        0.66
                                    ) /
                                0.32
                            )
                    )
                        .coerceIn(
                            0.0,
                            1.0
                        )

                x +=
                    (
                        -bodySway *
                            0.0105 *
                            gaitIntensity +
                            departureLean *
                                0.0048
                        ) *
                        torsoWeight

                y -=
                    bodyBob *
                        0.0072 *
                        gaitIntensity *
                        torsoWeight

                // 2) 头部：步态稳定 + 主动左右观察。
                val headWeight =
                    exp(
                        -square(
                            (
                                u -
                                    0.51
                                ) /
                                0.315
                        ) -
                            square(
                                (
                                    v -
                                        0.39
                                    ) /
                                0.295
                            )
                    )
                        .coerceIn(
                            0.0,
                            1.0
                        )

                x +=
                    (
                        bodySway *
                            0.0035 *
                            gaitIntensity +
                            departureLean *
                                0.0030
                        ) *
                        headWeight

                // 整个头先跟随注意力目标，再叠加极轻抬头/低头。
                x +=
                    headLook *
                        0.0180 *
                        headWeight

                y -=
                    headLift *
                        0.0042 *
                        headWeight

                // 再围绕头部中心做更明显的“转头/侧看”。
                // 这里不是纯平面歪头：先做一点横向压缩与侧移，
                // 模拟从正脸转成轻微 3/4 视角，再叠加少量旋转。
                if (
                    headWeight >
                    0.002
                ) {
                    val headCenterU =
                        0.51

                    val headCenterV =
                        0.39

                    val turnAmount =
                        abs(
                            headLook
                        )

                    val dx0 =
                        x -
                            headCenterU

                    val dy0 =
                        y -
                            headCenterV

                    val yawScaleX =
                        1.0 -
                            0.056 *
                                turnAmount

                    val yawX =
                        headCenterU +
                            dx0 *
                                yawScaleX +
                            headLook *
                                0.0110

                    // 转头时脸部有极轻的纵向错位，
                    // 避免只像整张圆形头像横移。
                    val yawY =
                        y +
                            headLook *
                                dx0 *
                                0.018

                    val angle =
                        headLook *
                            5.4 *
                            PI /
                            180.0

                    val dx =
                        yawX -
                            headCenterU

                    val dy =
                        yawY -
                            headCenterV

                    val rx =
                        headCenterU +
                            dx *
                                cos(
                                    angle
                                ) -
                            dy *
                                sin(
                                    angle
                                )

                    val ry =
                        headCenterV +
                            dx *
                                sin(
                                    angle
                                ) +
                            dy *
                                cos(
                                    angle
                                )

                    x =
                        x *
                            (
                                1.0 -
                                    headWeight
                                ) +
                            rx *
                                headWeight

                    y =
                        y *
                            (
                                1.0 -
                                    headWeight
                                ) +
                            ry *
                                headWeight
                }

                // 3) 眼睛：独立于头部提前移动。
                // 用比眨眼更紧的局部蒙版，只拉动眼睛附近，
                // 避免整张脸被一起扯动。
                val leftEyeWeight =
                    exp(
                        -square(
                            (
                                u -
                                    0.415
                                ) /
                                0.043
                        ) -
                            square(
                                (
                                    v -
                                        0.447
                                    ) /
                                0.037
                            )
                    )

                val rightEyeWeight =
                    exp(
                        -square(
                            (
                                u -
                                    0.642
                                ) /
                                0.043
                        ) -
                            square(
                                (
                                    v -
                                        0.438
                                    ) /
                                0.037
                            )
                    )

                val eyeWeight =
                    clamp(
                        leftEyeWeight +
                            rightEyeWeight,
                        0.0,
                        1.0
                    )

                if (
                    eyeWeight >
                    0.002
                ) {
                    // 视线移动幅度比 V2 头部补偿明显得多。
                    // 眼睛与头部同向，但领先一个相位。
                    x +=
                        eyeLook *
                            0.0220 *
                            eyeWeight

                    // 看向两侧时略微抬一点眼神，让表情更有“观察”感。
                    y -=
                        (
                            abs(
                                eyeLook
                            ) *
                                0.0026 +
                                headLift *
                                    0.0012
                            ) *
                            eyeWeight
                }

                // 4) 耳朵：改成真正的“转动 + 滞后”，而不只是几像素平移。
                for (
                    earIndex in
                    0..1
                ) {
                    val left =
                        earIndex ==
                            0

                    val pivotU =
                        if (
                            left
                        ) {
                            0.292
                        } else {
                            0.755
                        }

                    val pivotV =
                        if (
                            left
                        ) {
                            0.382
                        } else {
                            0.395
                        }

                    val centerU =
                        if (
                            left
                        ) {
                            0.205
                        } else {
                            0.865
                        }

                    val centerV =
                        if (
                            left
                        ) {
                            0.565
                        } else {
                            0.575
                        }

                    val radiusU =
                        if (
                            left
                        ) {
                            0.148
                        } else {
                            0.132
                        }

                    val radiusV =
                        if (
                            left
                        ) {
                            0.235
                        } else {
                            0.225
                        }

                    var earWeight =
                        exp(
                            -square(
                                (
                                    u -
                                        centerU
                                    ) /
                                    radiusU
                            ) -
                                square(
                                    (
                                        v -
                                            centerV
                                        ) /
                                    radiusV
                                )
                        )

                    val sourceDx =
                        u -
                            pivotU

                    val sourceDy =
                        v -
                            pivotV

                    val distanceFromRoot =
                        hypot(
                            sourceDx,
                            sourceDy
                        )

                    earWeight *=
                        clamp(
                            (
                                distanceFromRoot -
                                    0.025
                                ) /
                                0.30,
                            0.0,
                            1.0
                        )

                    if (
                        earWeight >
                        0.002
                    ) {
                        // 脚步惯性：比身体晚约半拍。
                        val stepLag =
                            sin(
                                phase -
                                    0.90 +
                                    (
                                        if (
                                            left
                                        ) {
                                            0.10
                                        } else {
                                            -0.10
                                        }
                                        )
                            )

                        // 转头惯性：头转过去时耳朵会略向反方向拖一下。
                        val lookLag =
                            -headLook *
                                (
                                    if (
                                        left
                                    ) {
                                        1.0
                                    } else {
                                        0.92
                                    }
                                    )

                        val earAngleDegrees =
                            stepLag *
                                7.2 *
                                gaitIntensity +
                                lookLag *
                                    4.6

                        val earAngle =
                            earAngleDegrees *
                                PI /
                                180.0

                        val dx =
                            x -
                                pivotU

                        val dy =
                            y -
                                pivotV

                        val rx =
                            pivotU +
                                dx *
                                    cos(
                                        earAngle
                                    ) -
                                dy *
                                    sin(
                                        earAngle
                                    )

                        val ry =
                            pivotV +
                                dx *
                                    sin(
                                        earAngle
                                    ) +
                                dy *
                                    cos(
                                        earAngle
                                    ) +
                                abs(
                                    stepLag
                                ) *
                                    0.0045 *
                                    gaitIntensity

                        x =
                            x *
                                (
                                    1.0 -
                                        earWeight
                                    ) +
                                rx *
                                    earWeight

                        y =
                            y *
                                (
                                    1.0 -
                                        earWeight
                                    ) +
                                ry *
                                    earWeight
                    }
                }

                // 5) 左右腿：维持 V2 已经比较明显的步幅。
                for (
                    legIndex in
                    0..1
                ) {
                    val left =
                        legIndex ==
                            0

                    val signal =
                        if (
                            left
                        ) {
                            leftSignal
                        } else {
                            rightSignal
                        }

                    val centerU =
                        if (
                            left
                        ) {
                            0.375
                        } else {
                            0.680
                        }

                    val centerV =
                        0.865

                    val pivotV =
                        if (
                            left
                        ) {
                            0.785
                        } else {
                            0.790
                        }

                    var legWeight =
                        exp(
                            -square(
                                (
                                    u -
                                        centerU
                                    ) /
                                    0.145
                            ) -
                                square(
                                    (
                                        v -
                                            centerV
                                        ) /
                                    0.135
                                )
                        )

                    val lowerProgress =
                        clamp(
                            (
                                v -
                                    pivotV +
                                    0.015
                                ) /
                                0.19,
                            0.0,
                            1.0
                        )

                    legWeight *=
                        smoothStep(
                            lowerProgress
                        )

                    if (
                        legWeight >
                        0.002
                    ) {
                        val lift =
                            maxOf(
                                0.0,
                                signal
                            ) *
                                0.040 *
                                gaitIntensity

                        val settle =
                            maxOf(
                                0.0,
                                -signal
                            ) *
                                0.0048 *
                                gaitIntensity

                        val stride =
                            signal *
                                0.022 *
                                direction *
                                gaitIntensity

                        val outward =
                            maxOf(
                                0.0,
                                signal
                            ) *
                                (
                                    if (
                                        left
                                    ) {
                                        -0.0048
                                    } else {
                                        0.0048
                                    }
                                    ) *
                                gaitIntensity

                        x +=
                            (
                                stride +
                                    outward
                                ) *
                                legWeight

                        y +=
                            (
                                -lift +
                                    settle
                                ) *
                                legWeight
                    }
                }

                verts[
                    index
                ] =
                    (
                        x *
                            viewW
                        )
                        .toFloat()

                verts[
                    index +
                        1
                ] =
                    (
                        y *
                            viewH
                        )
                        .toFloat()

                index +=
                    2
            }
        }
    }

    /**
     * 橘团：活泼型步态。
     * 正面静态素材不强行伪造成侧身跑，而是用更明显的弹跳重心、
     * 左右下肢交替、视线前探和尾巴反向惯性，形成“小跑两步”的感觉。
     */
    private fun applyOrangeLocomotionToMesh(
        now: Long
    ) {
        val elapsedSeconds =
            (
                now -
                    locomotionStartNanos
                )
                .coerceAtLeast(
                    0L
                ) /
                1_000_000_000.0

        val phase =
            elapsedSeconds *
                2.0 *
                PI /
                walkingCycleSeconds()

        val step =
            sin(
                phase
            )

        val oppositeStep =
            -step

        val bounce =
            abs(
                sin(
                    phase
                )
            )

        val direction =
            locomotionDirection
                .toDouble()

        val intensity =
            locomotionIntensity
                .toDouble()

        val headLook =
            locomotionHeadLook
                .toDouble()

        val eyeLook =
            locomotionEyeLook
                .toDouble()

        val headLift =
            locomotionHeadLift
                .toDouble()

        val viewW =
            width.toDouble()

        val viewH =
            height.toDouble()

        var index =
            0

        for (
            row in
            0..meshHeight
        ) {
            val v =
                row.toDouble() /
                    meshHeight.toDouble()

            for (
                col in
                0..meshWidth
            ) {
                val u =
                    col.toDouble() /
                        meshWidth.toDouble()

                var x =
                    verts[
                        index
                    ]
                        .toDouble() /
                        viewW

                var y =
                    verts[
                        index +
                            1
                    ]
                        .toDouble() /
                        viewH

                val bodyWeight =
                    exp(
                        -square(
                            (
                                u -
                                    0.50
                                ) /
                                0.36
                        ) -
                            square(
                                (
                                    v -
                                        0.68
                                    ) /
                                0.33
                            )
                    )
                        .coerceIn(
                            0.0,
                            1.0
                        )

                val departureLean =
                    (
                        1.0 -
                            smoothStep(
                                clamp(
                                    elapsedSeconds /
                                        0.30,
                                    0.0,
                                    1.0
                                )
                            )
                        ) *
                        direction *
                        intensity

                x +=
                    (
                        -step *
                            0.0125 *
                            intensity +
                            departureLean *
                                0.0065
                        ) *
                        bodyWeight

                y -=
                    bounce *
                        0.0105 *
                        intensity *
                        bodyWeight

                val headWeight =
                    exp(
                        -square(
                            (
                                u -
                                    0.505
                                ) /
                                0.315
                        ) -
                            square(
                                (
                                    v -
                                        0.355
                                    ) /
                                0.285
                            )
                    )
                        .coerceIn(
                            0.0,
                            1.0
                        )

                x +=
                    headLook *
                        0.0145 *
                        headWeight +
                        step *
                            0.0035 *
                            intensity *
                            headWeight +
                        departureLean *
                            0.0040 *
                            headWeight

                y -=
                    headLift *
                        0.0038 *
                        headWeight

                // 橘团转头比芽芽更快、更警觉：
                // 用轻量横向压缩 + 侧移 + 小角度旋转模拟 3/4 侧看。
                if (
                    headWeight >
                    0.002
                ) {
                    val headCenterU =
                        0.505

                    val headCenterV =
                        0.355

                    val turnAmount =
                        abs(
                            headLook
                        )

                    val dx0 =
                        x -
                            headCenterU

                    val dy0 =
                        y -
                            headCenterV

                    val yawScaleX =
                        1.0 -
                            0.034 *
                                turnAmount

                    val yawX =
                        headCenterU +
                            dx0 *
                                yawScaleX +
                            headLook *
                                0.0072

                    val angle =
                        headLook *
                            3.6 *
                            PI /
                            180.0

                    val dx =
                        yawX -
                            headCenterU

                    val dy =
                        dy0

                    val rx =
                        headCenterU +
                            dx *
                                cos(
                                    angle
                                ) -
                            dy *
                                sin(
                                    angle
                                )

                    val ry =
                        headCenterV +
                            dx *
                                sin(
                                    angle
                                ) +
                            dy *
                                cos(
                                    angle
                                )

                    x =
                        x *
                            (
                                1.0 -
                                    headWeight
                                ) +
                            rx *
                                headWeight

                    y =
                        y *
                            (
                                1.0 -
                                    headWeight
                                ) +
                            ry *
                                headWeight
                }

                // 眼睛先扫向新目标，再等头跟过来。
                val leftEyeWeight =
                    exp(
                        -square(
                            (
                                u -
                                    0.400
                                ) /
                                0.050
                        ) -
                            square(
                                (
                                    v -
                                        0.392
                                    ) /
                                0.045
                            )
                    )

                val rightEyeWeight =
                    exp(
                        -square(
                            (
                                u -
                                    0.606
                                ) /
                                0.050
                        ) -
                            square(
                                (
                                    v -
                                        0.392
                                    ) /
                                0.045
                            )
                    )

                val eyeWeight =
                    clamp(
                        leftEyeWeight +
                            rightEyeWeight,
                        0.0,
                        1.0
                    )

                x +=
                    eyeLook *
                        0.0205 *
                        eyeWeight

                y -=
                    (
                        headLift *
                            0.0018 +
                            abs(
                                eyeLook
                            ) *
                                0.0012
                        ) *
                        eyeWeight

                // 橘团尾巴跟身体反向甩，并比身体晚一点到位。
                val tailPivotU =
                    0.355

                val tailPivotV =
                    0.735

                var tailWeight =
                    exp(
                        -square(
                            (
                                u -
                                    0.205
                                ) /
                                0.118
                        ) -
                            square(
                                (
                                    v -
                                        0.690
                                    ) /
                                0.150
                            )
                    )

                val tailHorizontalMask =
                    1.0 -
                        smoothStep(
                            clamp(
                                (
                                    u -
                                        0.335
                                    ) /
                                    0.060,
                                0.0,
                                1.0
                            )
                        )

                val tailVerticalMask =
                    smoothStep(
                        clamp(
                            (
                                v -
                                    0.535
                                ) /
                                0.085,
                            0.0,
                            1.0
                        )
                    )

                tailWeight *=
                    tailHorizontalMask *
                        tailVerticalMask

                if (
                    tailWeight >
                    0.002
                ) {
                    val tailLag =
                        sin(
                            phase -
                                0.92
                        )

                    val angle =
                        (
                            -step *
                                8.0 +
                                tailLag *
                                    9.5
                            ) *
                            intensity *
                            PI /
                            180.0

                    val dx =
                        x -
                            tailPivotU

                    val dy =
                        y -
                            tailPivotV

                    val rx =
                        tailPivotU +
                            dx *
                                cos(
                                    angle
                                ) -
                            dy *
                                sin(
                                    angle
                                )

                    val ry =
                        tailPivotV +
                            dx *
                                sin(
                                    angle
                                ) +
                            dy *
                                cos(
                                    angle
                                )

                    x =
                        x *
                            (
                                1.0 -
                                    tailWeight
                                ) +
                            rx *
                                tailWeight

                    y =
                        y *
                            (
                                1.0 -
                                    tailWeight
                                ) +
                            ry *
                                tailWeight
                }

                // 底部两侧做轻量交替抬起，避免整张图只是上下弹。
                for (
                    legIndex in
                    0..1
                ) {
                    val left =
                        legIndex ==
                            0

                    val signal =
                        if (
                            left
                        ) {
                            step
                        } else {
                            oppositeStep
                        }

                    val centerU =
                        if (
                            left
                        ) {
                            0.405
                        } else {
                            0.635
                        }

                    var legWeight =
                        exp(
                            -square(
                                (
                                    u -
                                        centerU
                                    ) /
                                    0.155
                            ) -
                                square(
                                    (
                                        v -
                                            0.865
                                        ) /
                                    0.125
                                )
                        )

                    legWeight *=
                        smoothStep(
                            clamp(
                                (
                                    v -
                                        0.735
                                    ) /
                                    0.20,
                                0.0,
                                1.0
                            )
                        )

                    val lift =
                        maxOf(
                            0.0,
                            signal
                        ) *
                            0.029 *
                            intensity

                    val stride =
                        signal *
                            0.016 *
                            direction *
                            intensity

                    x +=
                        stride *
                            legWeight

                    y -=
                        lift *
                            legWeight
                }

                verts[
                    index
                ] =
                    (
                        x *
                            viewW
                        )
                        .toFloat()

                verts[
                    index +
                        1
                ] =
                    (
                        y *
                            viewH
                        )
                        .toFloat()

                index +=
                    2
            }
        }
    }

    /**
     * 雨团：慢吞吞的软步态。
     * 身体位移幅度较小，垂耳和云朵围巾明显晚半拍，
     * 让它看起来不是“缩小版芽芽”，而是更慢、更软、更有情绪。
     */
    private fun applyYutuanLocomotionToMesh(
        now: Long
    ) {
        val elapsedSeconds =
            (
                now -
                    locomotionStartNanos
                )
                .coerceAtLeast(
                    0L
                ) /
                1_000_000_000.0

        val phase =
            elapsedSeconds *
                2.0 *
                PI /
                walkingCycleSeconds()

        val step =
            sin(
                phase
            )

        val oppositeStep =
            -step

        val sway =
            sin(
                phase
            )

        val bob =
            abs(
                sin(
                    phase
                )
            )

        val headLook =
            locomotionHeadLook
                .toDouble()

        val eyeLook =
            locomotionEyeLook
                .toDouble()

        val headLift =
            locomotionHeadLift
                .toDouble()

        val direction =
            locomotionDirection
                .toDouble()

        val intensity =
            locomotionIntensity
                .toDouble()

        val viewW =
            width.toDouble()

        val viewH =
            height.toDouble()

        var index =
            0

        for (
            row in
            0..meshHeight
        ) {
            val v =
                row.toDouble() /
                    meshHeight.toDouble()

            for (
                col in
                0..meshWidth
            ) {
                val u =
                    col.toDouble() /
                        meshWidth.toDouble()

                var x =
                    verts[
                        index
                    ]
                        .toDouble() /
                        viewW

                var y =
                    verts[
                        index +
                            1
                    ]
                        .toDouble() /
                        viewH

                val bodyWeight =
                    exp(
                        -square(
                            (
                                u -
                                    0.50
                                ) /
                                0.36
                        ) -
                            square(
                                (
                                    v -
                                        0.69
                                    ) /
                                0.34
                            )
                    )
                        .coerceIn(
                            0.0,
                            1.0
                        )

                val departureLean =
                    (
                        1.0 -
                            smoothStep(
                                clamp(
                                    elapsedSeconds /
                                        0.42,
                                    0.0,
                                    1.0
                                )
                            )
                        ) *
                        direction *
                        intensity

                x +=
                    (
                        -sway *
                            0.0075 *
                            intensity +
                            departureLean *
                                0.0042
                        ) *
                        bodyWeight

                y -=
                    bob *
                        0.0048 *
                        intensity *
                        bodyWeight

                val headWeight =
                    exp(
                        -square(
                            (
                                u -
                                    0.50
                                ) /
                                0.36
                        ) -
                            square(
                                (
                                    v -
                                        0.32
                                    ) /
                                0.29
                            )
                    )
                        .coerceIn(
                            0.0,
                            1.0
                        )

                x +=
                    headLook *
                        0.0105 *
                        headWeight +
                        sway *
                            0.0020 *
                            intensity *
                            headWeight +
                        departureLean *
                            0.0024 *
                            headWeight

                y -=
                    headLift *
                        0.0030 *
                        headWeight

                // 雨团转头更慢、更软，只做很轻的 3/4 偏转，避免长耳被扯变形。
                if (
                    headWeight >
                    0.002
                ) {
                    val headCenterU =
                        0.50

                    val headCenterV =
                        0.32

                    val turnAmount =
                        abs(
                            headLook
                        )

                    val dx0 =
                        x -
                            headCenterU

                    val dy0 =
                        y -
                            headCenterV

                    val yawScaleX =
                        1.0 -
                            0.024 *
                                turnAmount

                    val yawX =
                        headCenterU +
                            dx0 *
                                yawScaleX +
                            headLook *
                                0.0052

                    val angle =
                        headLook *
                            2.7 *
                            PI /
                            180.0

                    val dx =
                        yawX -
                            headCenterU

                    val rx =
                        headCenterU +
                            dx *
                                cos(
                                    angle
                                ) -
                            dy0 *
                                sin(
                                    angle
                                )

                    val ry =
                        headCenterV +
                            dx *
                                sin(
                                    angle
                                ) +
                            dy0 *
                                cos(
                                    angle
                                )

                    x =
                        x *
                            (
                                1.0 -
                                    headWeight
                                ) +
                            rx *
                                headWeight

                    y =
                        y *
                            (
                                1.0 -
                                    headWeight
                                ) +
                            ry *
                                headWeight
                }

                // 雨团眼睛先慢慢扫过去，头随后才到。
                val leftEyeWeight =
                    exp(
                        -square(
                            (
                                u -
                                    0.395
                                ) /
                                0.047
                        ) -
                            square(
                                (
                                    v -
                                        0.365
                                    ) /
                                0.041
                            )
                    )

                val rightEyeWeight =
                    exp(
                        -square(
                            (
                                u -
                                    0.610
                                ) /
                                0.047
                        ) -
                            square(
                                (
                                    v -
                                        0.365
                                    ) /
                                0.041
                            )
                    )

                val eyeWeight =
                    clamp(
                        leftEyeWeight +
                            rightEyeWeight,
                        0.0,
                        1.0
                    )

                x +=
                    eyeLook *
                        0.0140 *
                        eyeWeight

                y -=
                    (
                        headLift *
                            0.0014 +
                            abs(
                                eyeLook
                            ) *
                                0.0008
                        ) *
                        eyeWeight

                // 两只长垂耳比身体慢半拍，幅度不大但延迟明显。
                for (
                    earIndex in
                    0..1
                ) {
                    val left =
                        earIndex ==
                            0

                    val pivotU =
                        if (
                            left
                        ) {
                            0.315
                        } else {
                            0.690
                        }

                    val pivotV =
                        0.300

                    val centerU =
                        if (
                            left
                        ) {
                            0.190
                        } else {
                            0.815
                        }

                    var earWeight =
                        exp(
                            -square(
                                (
                                    u -
                                        centerU
                                    ) /
                                    0.170
                            ) -
                                square(
                                    (
                                        v -
                                            0.355
                                        ) /
                                    0.185
                                )
                        )

                    val distance =
                        hypot(
                            u -
                                pivotU,
                            v -
                                pivotV
                        )

                    earWeight *=
                        smoothStep(
                            clamp(
                                (
                                    distance -
                                        0.025
                                    ) /
                                    0.235,
                                0.0,
                                1.0
                            )
                        )

                    if (
                        earWeight >
                        0.002
                    ) {
                        val lag =
                            sin(
                                phase -
                                    1.12 +
                                    if (
                                        left
                                    ) {
                                        0.10
                                    } else {
                                        -0.10
                                    }
                            )

                        val angle =
                            lag *
                                (
                                    if (
                                        left
                                    ) {
                                        8.0
                                    } else {
                                        -8.0
                                    }
                                    ) *
                                intensity *
                                PI /
                                180.0

                        val dx =
                            x -
                                pivotU

                        val dy =
                            y -
                                pivotV

                        val rx =
                            pivotU +
                                dx *
                                    cos(
                                        angle
                                    ) -
                                dy *
                                    sin(
                                        angle
                                    )

                        val ry =
                            pivotV +
                                dx *
                                    sin(
                                        angle
                                    ) +
                                dy *
                                    cos(
                                        angle
                                    ) +
                                abs(
                                    lag
                                ) *
                                    0.0030 *
                                    intensity

                        x =
                            x *
                                (
                                    1.0 -
                                        earWeight
                                    ) +
                                rx *
                                    earWeight

                        y =
                            y *
                                (
                                    1.0 -
                                        earWeight
                                    ) +
                                ry *
                                    earWeight
                    }
                }

                // 云朵围巾尾端是雨团最明显的二级惯性。
                val scarfPivotU =
                    0.705

                val scarfPivotV =
                    0.595

                var scarfWeight =
                    exp(
                        -square(
                            (
                                u -
                                    0.805
                                ) /
                                0.105
                        ) -
                            square(
                                (
                                    v -
                                        0.650
                                    ) /
                                0.155
                            )
                    )

                scarfWeight *=
                    smoothStep(
                        clamp(
                            (
                                u -
                                    0.690
                                ) /
                                0.180,
                            0.0,
                            1.0
                        )
                    )

                if (
                    scarfWeight >
                    0.002
                ) {
                    val scarfLag =
                        sin(
                            phase -
                                1.42
                        )

                    val angle =
                        (
                            -sway *
                                4.0 +
                                scarfLag *
                                    7.5
                            ) *
                            intensity *
                            PI /
                            180.0

                    val dx =
                        x -
                            scarfPivotU

                    val dy =
                        y -
                            scarfPivotV

                    val rx =
                        scarfPivotU +
                            dx *
                                cos(
                                    angle
                                ) -
                            dy *
                                sin(
                                    angle
                                )

                    val ry =
                        scarfPivotV +
                            dx *
                                sin(
                                    angle
                                ) +
                            dy *
                                cos(
                                    angle
                                )

                    x =
                        x *
                            (
                                1.0 -
                                    scarfWeight
                                ) +
                            rx *
                                scarfWeight

                    y =
                        y *
                            (
                                1.0 -
                                    scarfWeight
                                ) +
                            ry *
                                scarfWeight
                }

                // 底部小步幅交替，整体比芽芽更慢、更轻。
                for (
                    legIndex in
                    0..1
                ) {
                    val left =
                        legIndex ==
                            0

                    val signal =
                        if (
                            left
                        ) {
                            step
                        } else {
                            oppositeStep
                        }

                    val centerU =
                        if (
                            left
                        ) {
                            0.395
                        } else {
                            0.625
                        }

                    var legWeight =
                        exp(
                            -square(
                                (
                                    u -
                                        centerU
                                    ) /
                                    0.155
                            ) -
                                square(
                                    (
                                        v -
                                            0.875
                                        ) /
                                    0.120
                                )
                        )

                    legWeight *=
                        smoothStep(
                            clamp(
                                (
                                    v -
                                        0.745
                                    ) /
                                    0.19,
                                0.0,
                                1.0
                            )
                        )

                    val lift =
                        maxOf(
                            0.0,
                            signal
                        ) *
                            0.020 *
                            intensity

                    val stride =
                        signal *
                            0.0105 *
                            direction *
                            intensity

                    x +=
                        stride *
                            legWeight

                    y -=
                        lift *
                            legWeight
                }

                verts[
                    index
                ] =
                    (
                        x *
                            viewW
                        )
                        .toFloat()

                verts[
                    index +
                        1
                ] =
                    (
                        y *
                            viewH
                        )
                        .toFloat()

                index +=
                    2
            }
        }
    }

    private fun applyCurrentMotionToMesh(
        now: Long
    ) {
        val motion =
            currentMotion

        if (
            motion ==
            PetMotion.NONE ||
            width <=
            0 ||
            height <=
            0
        ) {
            return
        }

        val p =
            motionProgress(
                now
            )

        val profile =
            PetMotionProfiles
                .forPet(
                    petKind
                )

        val intensity =
            motionModifier
                .intensity
                .toDouble()

        val direction =
            motionModifier
                .direction
                .toDouble()

        val envelope =
            sin(
                p *
                    PI
            )
                .coerceAtLeast(
                    0.0
                )

        val viewW =
            width.toDouble()

        val viewH =
            height.toDouble()

        var index =
            0

        for (
            row in
            0..meshHeight
        ) {
            val v =
                row.toDouble() /
                    meshHeight.toDouble()

            for (
                col in
                0..meshWidth
            ) {
                val u =
                    col.toDouble() /
                        meshWidth.toDouble()

                var x =
                    verts[index]
                        .toDouble() /
                        viewW

                var y =
                    verts[
                        index +
                            1
                    ]
                        .toDouble() /
                        viewH

                val headWeight =
                    exp(
                        -square(
                            (
                                u -
                                    profile
                                        .headCenterU
                                ) /
                                profile
                                    .headRadiusU
                        ) -
                            square(
                                (
                                    v -
                                        profile
                                            .headCenterV
                                    ) /
                                    profile
                                        .headRadiusV
                            )
                    )
                    .coerceIn(
                        0.0,
                        1.0
                    )

                val upperWeight =
                    smoothStep(
                        clamp(
                            (
                                0.82 -
                                    v
                                ) /
                                0.56,
                            0.0,
                            1.0
                        )
                    )

                val bodyWeight =
                    exp(
                        -square(
                            (
                                u -
                                    0.50
                                ) /
                                0.38
                        ) -
                            square(
                                (
                                    v -
                                        0.67
                                    ) /
                                    0.34
                            )
                    )
                    .coerceIn(
                        0.0,
                        1.0
                    )

                fun rotateHead(
                    angleDegrees:
                        Double,
                    weight:
                        Double =
                        headWeight
                ) {
                    if (
                        weight <=
                        0.001
                    ) {
                        return
                    }

                    val angle =
                        angleDegrees *
                            PI /
                            180.0

                    val dx =
                        x -
                            profile
                                .headCenterU

                    val dy =
                        y -
                            profile
                                .headCenterV

                    val rx =
                        profile
                            .headCenterU +
                            dx *
                                cos(
                                    angle
                                ) -
                            dy *
                                sin(
                                    angle
                                )

                    val ry =
                        profile
                            .headCenterV +
                            dx *
                                sin(
                                    angle
                                ) +
                            dy *
                                cos(
                                    angle
                                )

                    x =
                        x *
                            (
                                1.0 -
                                    weight
                                ) +
                            rx *
                                weight

                    y =
                        y *
                            (
                                1.0 -
                                    weight
                                ) +
                            ry *
                                weight
                }

                when (
                    motion
                ) {
                    PetMotion.LOOK_AROUND -> {
                        val look =
                            sin(
                                p *
                                    PI *
                                    2.0
                            )

                        val shift =
                            profile
                                .lookDistance *
                                look *
                                intensity

                        x +=
                            shift *
                                headWeight

                        x -=
                            shift *
                                0.10 *
                                bodyWeight

                        rotateHead(
                            angleDegrees =
                                2.4 *
                                    look *
                                    direction *
                                    intensity
                        )
                    }

                    PetMotion.HEAD_TILT -> {
                        rotateHead(
                            angleDegrees =
                                profile
                                    .headTiltDegrees *
                                    direction *
                                    envelope *
                                    intensity
                        )

                        y -=
                            0.0035 *
                                envelope *
                                headWeight
                    }

                    PetMotion.LOOK_UP -> {
                        y -=
                            0.020 *
                                envelope *
                                intensity *
                                headWeight

                        rotateHead(
                            angleDegrees =
                                -2.4 *
                                    direction *
                                    envelope *
                                    intensity
                        )
                    }

                    PetMotion.STRETCH -> {
                        val stretch =
                            profile
                                .stretchAmount *
                                envelope *
                                intensity

                        y -=
                            stretch *
                                upperWeight *
                                (
                                    0.70 +
                                        0.30 *
                                            headWeight
                                    )

                        x +=
                            (
                                u -
                                    0.50
                                ) *
                                stretch *
                                0.90 *
                                bodyWeight

                        y +=
                            0.006 *
                                envelope *
                                (
                                    1.0 -
                                        upperWeight
                                    ) *
                                bodyWeight
                    }

                    PetMotion.SMALL_JUMP -> {
                        val prep =
                            if (
                                p <
                                0.20
                            ) {
                                sin(
                                    p /
                                        0.20 *
                                        PI
                                )
                            } else if (
                                p >
                                0.76
                            ) {
                                sin(
                                    (
                                        p -
                                            0.76
                                        ) /
                                        0.24 *
                                        PI
                                )
                            } else {
                                0.0
                            }
                                .coerceAtLeast(
                                    0.0
                                )

                        val flight =
                            if (
                                p in
                                0.12..0.86
                            ) {
                                sin(
                                    (
                                        p -
                                            0.12
                                        ) /
                                        0.74 *
                                        PI
                                )
                            } else {
                                0.0
                            }
                                .coerceAtLeast(
                                    0.0
                                )

                        val jump =
                            profile
                                .jumpHeight *
                                profile
                                    .personalityBounce *
                                flight *
                                intensity

                        val sx =
                            1.0 +
                                0.035 *
                                    prep *
                                    intensity

                        val sy =
                            1.0 -
                                0.055 *
                                    prep *
                                    intensity

                        x =
                            0.50 +
                                (
                                    x -
                                        0.50
                                    ) *
                                sx

                        y =
                            0.88 +
                                (
                                    y -
                                        0.88
                                    ) *
                                sy -
                                jump
                    }

                    PetMotion.DOZE_NOD -> {
                        val nod =
                            envelope *
                                (
                                    0.72 +
                                        0.28 *
                                            sin(
                                                p *
                                                    PI *
                                                    3.0
                                            )
                                            .coerceAtLeast(
                                                0.0
                                            )
                                    )

                        y +=
                            0.020 *
                                nod *
                                intensity *
                                headWeight

                        rotateHead(
                            angleDegrees =
                                4.2 *
                                    direction *
                                    nod *
                                    intensity
                        )
                    }

                    PetMotion.SHY -> {
                        val shrink =
                            0.030 *
                                envelope *
                                intensity

                        x =
                            0.50 +
                                (
                                    x -
                                        0.50
                                    ) *
                                (
                                    1.0 -
                                        shrink *
                                            upperWeight
                                    )

                        y +=
                            0.016 *
                                envelope *
                                headWeight *
                                intensity

                        rotateHead(
                            angleDegrees =
                                -3.6 *
                                    direction *
                                    envelope *
                                    intensity
                        )
                    }

                    PetMotion.SEEK_ATTENTION -> {
                        val lean =
                            envelope *
                                direction *
                                intensity

                        x +=
                            0.018 *
                                lean *
                                upperWeight

                        y -=
                            0.010 *
                                abs(
                                    sin(
                                        p *
                                            PI *
                                            3.0
                                    )
                                ) *
                                profile
                                    .personalityBounce *
                                upperWeight *
                                intensity

                        rotateHead(
                            angleDegrees =
                                profile
                                    .headTiltDegrees *
                                    0.62 *
                                    lean
                        )
                    }

                    PetMotion.NONE ->
                        Unit
                }

                verts[index] =
                    (
                        x *
                            viewW
                        )
                        .toFloat()

                verts[
                    index +
                        1
                ] =
                    (
                        y *
                            viewH
                        )
                        .toFloat()

                index +=
                    2
            }
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

    private fun directionalSmootherStep(
        value: Double
    ): Double {
        val t =
            clamp(
                value,
                0.0,
                1.0
            )

        return t *
            t *
            t *
            (
                t *
                    (
                        t *
                            6.0 -
                            15.0
                        ) +
                    10.0
                )
    }

    private fun orangeDirectionalAnticipationProgress(
        now: Long
    ): Double {
        if (
            !locomotionActive ||
            locomotionStartNanos <=
            0L
        ) {
            return 0.0
        }

        val elapsed =
            (
                now -
                    locomotionStartNanos
                )
                .coerceAtLeast(
                    0L
                )
                .toDouble()

        return directionalSmootherStep(
            elapsed /
                105_000_000.0
        )
    }

    private fun orangeDirectionalAmountWhileWalking(
        now: Long
    ): Float {
        if (
            !locomotionActive ||
            locomotionStartNanos <=
            0L
        ) {
            return 0f
        }

        // V2.4.2：
        // 先给眼神 / 头部一个极短的预判，
        // 再用两段更长的视角过渡。3/4 中间保留很短的“读帧”时间，
        // 让大脑看到的是主动转身，而不是两张图快速闪过去。
        val anticipationNanos =
            105_000_000.0

        val frontToThreeQuarterNanos =
            245_000_000.0

        val threeQuarterHoldNanos =
            65_000_000.0

        val threeQuarterToSideNanos =
            275_000_000.0

        var elapsed =
            (
                now -
                    locomotionStartNanos
                )
                .coerceAtLeast(
                    0L
                )
                .toDouble()

        if (
            elapsed <
            anticipationNanos
        ) {
            return 0f
        }

        elapsed -=
            anticipationNanos

        if (
            elapsed <
            frontToThreeQuarterNanos
        ) {
            return directionalSmootherStep(
                elapsed /
                    frontToThreeQuarterNanos
            )
                .toFloat()
        }

        elapsed -=
            frontToThreeQuarterNanos

        if (
            elapsed <
            threeQuarterHoldNanos
        ) {
            return 1f
        }

        elapsed -=
            threeQuarterHoldNanos

        if (
            elapsed <
            threeQuarterToSideNanos
        ) {
            return (
                1.0 +
                    directionalSmootherStep(
                        elapsed /
                            threeQuarterToSideNanos
                    )
                )
                .toFloat()
        }

        return 2f
    }

    private fun orangeDirectionalReturnAmount(
        now: Long
    ): Float {
        val start =
            orangeDirectionalReturnStartNanos

        val from =
            orangeDirectionalReturnFromAmount
                .coerceIn(
                    0f,
                    2f
                )

        if (
            start <=
            0L ||
            from <=
            0f
        ) {
            return 0f
        }

        var elapsed =
            (
                now -
                    start
                )
                .coerceAtLeast(
                    0L
                )
                .toDouble()

        val sideToThreeQuarterNanos =
            245_000_000.0

        val threeQuarterHoldNanos =
            55_000_000.0

        val threeQuarterToFrontNanos =
            255_000_000.0

        var amount =
            from

        if (
            from >
            1f
        ) {
            val sideSpan =
                from -
                    1f

            val sideDuration =
                sideToThreeQuarterNanos *
                    sideSpan
                        .toDouble()

            if (
                elapsed <
                sideDuration
            ) {
                amount =
                    (
                        1.0 +
                            sideSpan
                                .toDouble() *
                                (
                                    1.0 -
                                        directionalSmootherStep(
                                            elapsed /
                                                sideDuration
                                        )
                                    )
                        )
                        .toFloat()

                return amount
                    .coerceIn(
                        0f,
                        2f
                    )
            }

            elapsed -=
                sideDuration

            if (
                elapsed <
                threeQuarterHoldNanos
            ) {
                return 1f
            }

            elapsed -=
                threeQuarterHoldNanos
        }

        val frontSpan =
            minOf(
                from,
                1f
            )

        val frontDuration =
            (
                threeQuarterToFrontNanos *
                    frontSpan
                        .toDouble()
                )
                .coerceAtLeast(
                    1.0
                )

        if (
            elapsed <
            frontDuration
        ) {
            amount =
                (
                    frontSpan
                        .toDouble() *
                        (
                            1.0 -
                                directionalSmootherStep(
                                    elapsed /
                                        frontDuration
                                )
                            )
                    )
                    .toFloat()
        } else {
            amount =
                0f

            orangeDirectionalReturnStartNanos =
                0L

            orangeDirectionalReturnFromAmount =
                0f
        }

        return amount
            .coerceIn(
                0f,
                2f
            )
    }

    private fun orangeThreeQuarterBitmap(
        direction: Float
    ): Bitmap =
        if (
            direction <
            0f
        ) {
            orange3qLeftBitmap
                ?: idleBitmap
        } else {
            orange3qRightBitmap
                ?: idleBitmap
        }

    private fun orangeSideBitmap(
        direction: Float
    ): Bitmap =
        if (
            direction <
            0f
        ) {
            orangeSideLeftBitmap
                ?: idleBitmap
        } else {
            orangeSideRightBitmap
                ?: idleBitmap
        }

    private fun buildOrangeAlignedNeutralMesh(
        bitmap: Bitmap
    ) {
        val viewW =
            width.toDouble()

        val viewH =
            height.toDouble()

        val reference =
            orangeFrontAlphaBounds

        val source =
            orangeAlphaBoundsFor(
                bitmap
            )

        // 统一“脚底基线 + 可见角色中心 + 可见高度”。
        // 生成的 3/4 / 侧面图透明边距并不完全一致，
        // 若直接把整张 256x256 填满 View，会在换图瞬间产生明显跳位。
        val scale =
            (
                reference.height /
                    source.height
                )
                .coerceIn(
                    0.90,
                    1.085
                )

        val targetCenterX =
            reference.centerX

        val targetBottom =
            reference.bottom

        var index =
            0

        for (
            row in
            0..meshHeight
        ) {
            val v =
                row.toDouble() /
                    meshHeight
                        .toDouble()

            for (
                col in
                0..meshWidth
            ) {
                val u =
                    col.toDouble() /
                        meshWidth
                            .toDouble()

                val alignedU =
                    targetCenterX +
                        (
                            u -
                                source.centerX
                            ) *
                            scale

                val alignedV =
                    targetBottom +
                        (
                            v -
                                source.bottom
                            ) *
                            scale

                verts[
                    index++
                ] =
                    (
                        alignedU *
                            viewW
                        )
                        .toFloat()

                verts[
                    index++
                ] =
                    (
                        alignedV *
                            viewH
                        )
                        .toFloat()
            }
        }
    }

    private fun drawCurrentMeshBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        alpha: Float
    ) {
        if (
            alpha <=
            0f
        ) {
            return
        }

        paint.alpha =
            (
                255f *
                    alpha.coerceIn(
                        0f,
                        1f
                    )
                )
                .toInt()

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

        paint.alpha =
            255
    }

    private fun buildOrangeDirectionalMesh(
        now: Long,
        direction: Float,
        blinkAmount: Double,
        sideView: Boolean,
        walking: Boolean,
        motionScale: Double = 1.0
    ) {
        val directionalBitmap =
            if (
                sideView
            ) {
                orangeSideBitmap(
                    direction
                )
            } else {
                orangeThreeQuarterBitmap(
                    direction
                )
            }

        buildOrangeAlignedNeutralMesh(
            directionalBitmap
        )

        val viewW =
            width.toDouble()

        val viewH =
            height.toDouble()

        val elapsedSeconds =
            if (
                walking
            ) {
                walkingElapsedSeconds(
                    now
                )
            } else {
                0.0
            }

        val phase =
            elapsedSeconds *
                2.0 *
                PI /
                0.48

        val step =
            if (
                walking
            ) {
                sin(
                    phase
                )
            } else {
                0.0
            }

        val bounce =
            if (
                walking
            ) {
                abs(
                    sin(
                        phase
                    )
                )
            } else {
                0.0
            }

        val intensity =
            if (
                walking
            ) {
                locomotionIntensity
                    .toDouble() *
                    motionScale
                        .coerceIn(
                            0.0,
                            1.0
                        )
            } else {
                0.0
            }

        val look =
            if (
                walking
            ) {
                locomotionHeadLook
                    .toDouble()
            } else {
                0.0
            }

        val eyeLook =
            if (
                walking
            ) {
                locomotionEyeLook
                    .toDouble()
            } else {
                0.0
            }

        val headLift =
            if (
                walking
            ) {
                locomotionHeadLift
                    .toDouble()
            } else {
                0.0
            }

        var index =
            0

        for (
            row in
            0..meshHeight
        ) {
            val v =
                row.toDouble() /
                    meshHeight
                        .toDouble()

            for (
                col in
                0..meshWidth
            ) {
                val u =
                    col.toDouble() /
                        meshWidth
                            .toDouble()

                var x =
                    verts[
                        index
                    ]
                        .toDouble() /
                        viewW

                var y =
                    verts[
                        index +
                            1
                    ]
                        .toDouble() /
                        viewH

                if (
                    walking
                ) {
                    // 整体连续前进已经由 WindowManager 负责，
                    // 贴图内部只做很小的重心与上下起伏。
                    val bodyWeight =
                        exp(
                            -square(
                                (
                                    u -
                                        0.50
                                    ) /
                                    0.42
                            ) -
                                square(
                                    (
                                        v -
                                            0.66
                                        ) /
                                    0.39
                                )
                        )
                            .coerceIn(
                                0.0,
                                1.0
                            )

                    x +=
                        -step *
                            (
                                if (
                                    sideView
                                ) {
                                    0.0038
                                } else {
                                    0.0028
                                }
                                ) *
                            intensity *
                            bodyWeight

                    y -=
                        bounce *
                            (
                                if (
                                    sideView
                                ) {
                                    0.0055
                                } else {
                                    0.0035
                                }
                                ) *
                            intensity *
                            bodyWeight

                    val headWeight =
                        exp(
                            -square(
                                (
                                    u -
                                        0.50
                                    ) /
                                    0.34
                            ) -
                                square(
                                    (
                                        v -
                                            0.34
                                        ) /
                                    0.27
                                )
                        )
                            .coerceIn(
                                0.0,
                                1.0
                            )

                    // 已经是真实方向图，所以头眼这里只做“活着”的微调，
                    // 不再用大角度 Mesh 强拗侧脸。
                    x +=
                        look *
                            0.0045 *
                            headWeight

                    y -=
                        headLift *
                            0.0028 *
                            headWeight

                    val eyeCenterU =
                        if (
                            direction <
                            0f
                        ) {
                            if (
                                sideView
                            ) {
                                0.365
                            } else {
                                0.405
                            }
                        } else {
                            if (
                                sideView
                            ) {
                                0.635
                            } else {
                                0.595
                            }
                        }

                    val eyeCenterV =
                        if (
                            sideView
                        ) {
                            0.355
                        } else {
                            0.382
                        }

                    val eyeWeight =
                        exp(
                            -square(
                                (
                                    u -
                                        eyeCenterU
                                    ) /
                                    (
                                        if (
                                            sideView
                                        ) {
                                            0.070
                                        } else {
                                            0.095
                                        }
                                    )
                            ) -
                                square(
                                    (
                                        v -
                                            eyeCenterV
                                        ) /
                                    0.065
                                )
                        )
                            .coerceIn(
                                0.0,
                                1.0
                            )

                    x +=
                        eyeLook *
                            0.0070 *
                            eyeWeight

                    if (
                        blinkAmount >
                        0.0
                    ) {
                        val compression =
                            0.54 *
                                blinkAmount *
                                eyeWeight

                        y =
                            eyeCenterV +
                                (
                                    y -
                                        eyeCenterV
                                    ) *
                                    (
                                        1.0 -
                                            compression
                                        )
                    }

                    if (
                        sideView
                    ) {
                        // 侧面资产单独绑定底部两组脚，
                        // 不复用正面橘团的腿部/尾巴蒙版。
                        for (
                            legIndex in
                            0..1
                        ) {
                            val frontLeg =
                                legIndex ==
                                    0

                            val centerU =
                                when {
                                    direction >
                                        0f &&
                                        frontLeg ->
                                        0.625

                                    direction >
                                        0f ->
                                        0.455

                                    frontLeg ->
                                        0.375

                                    else ->
                                        0.545
                                }

                            val signal =
                                if (
                                    frontLeg
                                ) {
                                    step
                                } else {
                                    -step
                                }

                            var legWeight =
                                exp(
                                    -square(
                                        (
                                            u -
                                                centerU
                                            ) /
                                            0.145
                                    ) -
                                        square(
                                            (
                                                v -
                                                    0.875
                                                ) /
                                            0.120
                                        )
                                )

                            legWeight *=
                                smoothStep(
                                    clamp(
                                        (
                                            v -
                                                0.735
                                            ) /
                                            0.20,
                                        0.0,
                                        1.0
                                    )
                                )

                            val lift =
                                maxOf(
                                    0.0,
                                    signal
                                ) *
                                    0.021 *
                                    intensity

                            val stride =
                                signal *
                                    0.0105 *
                                    direction
                                        .toDouble() *
                                    intensity

                            x +=
                                stride *
                                    legWeight

                            y -=
                                lift *
                                    legWeight
                        }

                        val tailPivotU =
                            if (
                                direction >
                                0f
                            ) {
                                0.405
                            } else {
                                0.595
                            }

                        val tailCenterU =
                            if (
                                direction >
                                0f
                            ) {
                                0.235
                            } else {
                                0.765
                            }

                        var tailWeight =
                            exp(
                                -square(
                                    (
                                        u -
                                            tailCenterU
                                        ) /
                                        0.160
                                ) -
                                    square(
                                        (
                                            v -
                                                0.690
                                            ) /
                                        0.190
                                    )
                            )

                        val tailSideMask =
                            if (
                                direction >
                                0f
                            ) {
                                1.0 -
                                    smoothStep(
                                        clamp(
                                            (
                                                u -
                                                    0.45
                                                ) /
                                                0.08,
                                            0.0,
                                            1.0
                                        )
                                    )
                            } else {
                                smoothStep(
                                    clamp(
                                        (
                                            u -
                                                0.55
                                            ) /
                                            0.08,
                                        0.0,
                                        1.0
                                    )
                                )
                            }

                        tailWeight *=
                            tailSideMask

                        if (
                            tailWeight >
                            0.003
                        ) {
                            val tailLag =
                                sin(
                                    phase -
                                        0.92
                                )

                            val angle =
                                tailLag *
                                    (
                                        if (
                                            direction >
                                            0f
                                        ) {
                                            -7.5
                                        } else {
                                            7.5
                                        }
                                        ) *
                                    intensity *
                                    PI /
                                    180.0

                            val pivotV =
                                0.715

                            val dx =
                                x -
                                    tailPivotU

                            val dy =
                                y -
                                    pivotV

                            val rx =
                                tailPivotU +
                                    dx *
                                        cos(
                                            angle
                                        ) -
                                    dy *
                                        sin(
                                            angle
                                        )

                            val ry =
                                pivotV +
                                    dx *
                                        sin(
                                            angle
                                        ) +
                                    dy *
                                        cos(
                                            angle
                                        )

                            x =
                                x *
                                    (
                                        1.0 -
                                            tailWeight
                                        ) +
                                    rx *
                                        tailWeight

                            y =
                                y *
                                    (
                                        1.0 -
                                            tailWeight
                                        ) +
                                    ry *
                                        tailWeight
                        }
                    }
                }

                verts[
                    index
                ] =
                    (
                        x *
                            viewW
                        )
                        .toFloat()

                verts[
                    index +
                        1
                ] =
                    (
                        y *
                            viewH
                        )
                        .toFloat()

                index +=
                    2
            }
        }
    }

    private fun applyOrangeTurnAnticipationToMesh(
        direction: Float,
        progress: Double
    ) {
        val p =
            clamp(
                progress,
                0.0,
                1.0
            )

        if (
            p <=
            0.0
        ) {
            return
        }

        val viewW =
            width.toDouble()

        val viewH =
            height.toDouble()

        val dir =
            direction
                .toDouble()

        var index =
            0

        for (
            row in
            0..meshHeight
        ) {
            val v =
                row.toDouble() /
                    meshHeight
                        .toDouble()

            for (
                col in
                0..meshWidth
            ) {
                val u =
                    col.toDouble() /
                        meshWidth
                            .toDouble()

                var x =
                    verts[
                        index
                    ]
                        .toDouble() /
                        viewW

                var y =
                    verts[
                        index +
                            1
                    ]
                        .toDouble() /
                        viewH

                val bodyWeight =
                    exp(
                        -square(
                            (
                                u -
                                    0.50
                                ) /
                                0.37
                        ) -
                            square(
                                (
                                    v -
                                        0.69
                                    ) /
                                0.34
                            )
                    )
                        .coerceIn(
                            0.0,
                            1.0
                        )

                // 身体先给出非常轻的方向预倾。
                x +=
                    dir *
                        0.0035 *
                        p *
                        bodyWeight

                val headWeight =
                    exp(
                        -square(
                            (
                                u -
                                    0.505
                                ) /
                                0.315
                        ) -
                            square(
                                (
                                    v -
                                        0.355
                                    ) /
                                0.285
                            )
                    )
                        .coerceIn(
                            0.0,
                            1.0
                        )

                val headCenterU =
                    0.505

                val headCenterV =
                    0.355

                val dx0 =
                    x -
                        headCenterU

                val dy0 =
                    y -
                        headCenterV

                val angle =
                    dir *
                        p *
                        2.25 *
                        PI /
                        180.0

                val yawX =
                    headCenterU +
                        dx0 *
                            (
                                1.0 -
                                    0.020 *
                                        p
                                ) +
                        dir *
                            0.0055 *
                            p

                val dx =
                    yawX -
                        headCenterU

                val rx =
                    headCenterU +
                        dx *
                            cos(
                                angle
                            ) -
                        dy0 *
                            sin(
                                angle
                            )

                val ry =
                    headCenterV +
                        dx *
                            sin(
                                angle
                            ) +
                        dy0 *
                            cos(
                                angle
                            )

                x =
                    x *
                        (
                            1.0 -
                                headWeight
                            ) +
                        rx *
                            headWeight

                y =
                    y *
                        (
                            1.0 -
                                headWeight
                            ) +
                        ry *
                            headWeight

                val leftEyeWeight =
                    exp(
                        -square(
                            (
                                u -
                                    0.400
                                ) /
                                0.050
                        ) -
                            square(
                                (
                                    v -
                                        0.392
                                    ) /
                                0.045
                            )
                    )

                val rightEyeWeight =
                    exp(
                        -square(
                            (
                                u -
                                    0.606
                                ) /
                                0.050
                        ) -
                            square(
                                (
                                    v -
                                        0.392
                                    ) /
                                0.045
                            )
                    )

                val eyeWeight =
                    clamp(
                        leftEyeWeight +
                            rightEyeWeight,
                        0.0,
                        1.0
                    )

                // 眼睛略微领先头部，让转身前先“看过去”。
                x +=
                    dir *
                        0.0125 *
                        directionalSmootherStep(
                            minOf(
                                1.0,
                                p *
                                    1.35
                            )
                        ) *
                        eyeWeight

                verts[
                    index
                ] =
                    (
                        x *
                            viewW
                        )
                        .toFloat()

                verts[
                    index +
                        1
                ] =
                    (
                        y *
                            viewH
                        )
                        .toFloat()

                index +=
                    2
            }
        }
    }

    private fun drawOrangeDirectionalLocomotion(
        canvas: Canvas,
        now: Long,
        idleSeconds: Double,
        stateSeconds: Double,
        blinkAmount: Double
    ) {
        val walking =
            locomotionActive

        if (
            walking
        ) {
            updateLocomotionAttention(
                now
            )
        }

        val direction =
            if (
                walking
            ) {
                locomotionDirection
            } else {
                orangeDirectionalReturnDirection
            }

        val amount =
            if (
                walking
            ) {
                orangeDirectionalAmountWhileWalking(
                    now
                )
            } else {
                orangeDirectionalReturnAmount(
                    now
                )
            }

        val anticipation =
            if (
                walking
            ) {
                orangeDirectionalAnticipationProgress(
                    now
                )
            } else {
                1.0
            }

        val threeQuarter =
            orangeThreeQuarterBitmap(
                direction
            )

        val side =
            orangeSideBitmap(
                direction
            )

        when {
            amount <=
                0.001f -> {
                buildOrangeMesh(
                    idleSeconds,
                    stateSeconds,
                    blinkAmount
                )

                if (
                    walking
                ) {
                    // 转图之前先由眼睛、头和身体给出方向预判。
                    // 这一小段不做完整走路步态，避免“还没转身腿已经跑起来”。
                    applyOrangeTurnAnticipationToMesh(
                        direction =
                            direction,
                        progress =
                            anticipation
                    )
                } else {
                    applyCurrentMotionToMesh(
                        now
                    )
                }

                drawIdleMesh(
                    canvas,
                    1f
                )
            }

            amount <
                1f -> {
                val p =
                    amount.coerceIn(
                        0f,
                        1f
                    )

                buildOrangeMesh(
                    idleSeconds,
                    stateSeconds,
                    blinkAmount
                )

                if (
                    walking
                ) {
                    applyOrangeTurnAnticipationToMesh(
                        direction =
                            direction,
                        progress =
                            1.0
                    )
                }

                drawIdleMesh(
                    canvas,
                    1f -
                        p
                )

                buildOrangeDirectionalMesh(
                    now =
                        now,
                    direction =
                        direction,
                    blinkAmount =
                        blinkAmount,
                    sideView =
                        false,
                    walking =
                        walking,
                    motionScale =
                        0.10 +
                            0.22 *
                                p
                                    .toDouble()
                )

                drawCurrentMeshBitmap(
                    canvas =
                        canvas,
                    bitmap =
                        threeQuarter,
                    alpha =
                        p
                )
            }

            else -> {
                val p =
                    (
                        amount -
                            1f
                        )
                        .coerceIn(
                            0f,
                            1f
                        )

                // 3/4 在切侧面时逐步增加动作量，而不是一出现就全幅摆动。
                buildOrangeDirectionalMesh(
                    now =
                        now,
                    direction =
                        direction,
                    blinkAmount =
                        blinkAmount,
                    sideView =
                        false,
                    walking =
                        walking,
                    motionScale =
                        0.32 +
                            0.34 *
                                p
                                    .toDouble()
                )

                drawCurrentMeshBitmap(
                    canvas =
                        canvas,
                    bitmap =
                        threeQuarter,
                    alpha =
                        1f -
                            p
                )

                buildOrangeDirectionalMesh(
                    now =
                        now,
                    direction =
                        direction,
                    blinkAmount =
                        blinkAmount,
                    sideView =
                        true,
                    walking =
                        walking,
                    motionScale =
                        0.24 +
                            0.76 *
                                p
                                    .toDouble()
                )

                drawCurrentMeshBitmap(
                    canvas =
                        canvas,
                    bitmap =
                        side,
                    alpha =
                        p
                )
            }
        }
    }

    private fun buildMesh(
        idleSeconds: Double,
        stateSeconds: Double,
        blinkAmount: Double
    ) {
        when (petKind) {
            PetKind.YAYA ->
                buildYayaMesh(
                    idleSeconds,
                    stateSeconds,
                    blinkAmount
                )

            PetKind.YUTUAN ->
                buildYutuanMesh(
                    idleSeconds,
                    stateSeconds,
                    blinkAmount
                )

            else ->
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


    private fun buildYutuanMesh(
        idleSeconds: Double,
        stateSeconds: Double,
        blinkAmount: Double
    ) {
        val viewW = width.toFloat()
        val viewH = height.toFloat()

        val hour = LocalTime.now().hour
        val quietNight =
            hour >= 23 || hour < 7

        val breath =
            sin(
                idleSeconds *
                    2.0 *
                    PI /
                    3.55
            )

        val signatureStrength =
            when (state) {
                State.TAIL_WAG -> 1.0
                State.HAPPY -> 0.88
                State.PETTED -> 0.58
                State.REMINDER -> 0.72
                State.WAVE -> 0.52
                State.DRAGGING -> 0.16
                State.TIRED -> 0.10
                State.SLEEP -> 0.03
                else ->
                    if (quietNight) {
                        0.10
                    } else {
                        0.21
                    }
            }

        val happyBounce =
            when (state) {
                State.HAPPY ->
                    -0.014 *
                        abs(
                            sin(
                                stateSeconds *
                                    PI *
                                    2.4
                            )
                        )

                State.PETTED ->
                    -0.004 *
                        abs(
                            sin(
                                stateSeconds *
                                    PI *
                                    1.8
                            )
                        )

                else -> 0.0
            }

        val sleepyPoseAmount =
            when (state) {
                State.TIRED ->
                    smoothStep(
                        clamp(
                            stateSeconds /
                                TIRED_DURATION_SECONDS,
                            0.0,
                            1.0
                        )
                    )

                State.SLEEP -> 1.0

                State.WAKE_UP ->
                    (
                        1.0 -
                            smoothStep(
                                clamp(
                                    stateSeconds /
                                        WAKE_DURATION_SECONDS,
                                    0.0,
                                    1.0
                                )
                            )
                        )

                else -> 0.0
            }

        val sleepyAmount =
            when (state) {
                State.TIRED ->
                    0.034 *
                        sleepyPoseAmount

                State.SLEEP ->
                    0.056

                State.WAKE_UP ->
                    0.050 *
                        sleepyPoseAmount

                else -> 0.0
            }

        val waveActive =
            state == State.WAVE ||
                state == State.REMINDER ||
                state == State.TAIL_WAG

        val waveStrength =
            if (waveActive) {
                abs(
                    sin(
                        stateSeconds *
                            PI *
                            2.2
                    )
                )
            } else {
                0.0
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

                val shakeBodyX =
                    if (
                        state ==
                        State.TAIL_WAG
                    ) {
                        0.018 *
                            sin(
                                stateSeconds *
                                    PI *
                                    7.2
                            )
                    } else {
                        0.0
                    }

                x += shakeBodyX
                y += happyBounce

                val headWeight =
                    exp(
                        -square(
                            (u - 0.50) /
                                0.37
                        ) -
                            square(
                                (v - 0.32) /
                                    0.28
                            )
                    )

                y +=
                    sleepyAmount *
                        headWeight

                if (
                    sleepyPoseAmount >
                    0.0
                ) {
                    val lowerBodyWeight =
                        exp(
                            -square(
                                (u - 0.50) /
                                    0.34
                            ) -
                                square(
                                    (v - 0.73) /
                                        0.24
                                )
                        )

                    y +=
                        0.018 *
                            sleepyPoseAmount *
                            lowerBodyWeight
                }

                if (state == State.DRAGGING) {
                    val hangingWeight =
                        smoothStep(
                            clamp(
                                (v - 0.50) /
                                    0.45,
                                0.0,
                                1.0
                            )
                        )

                    y +=
                        0.012 *
                            hangingWeight
                }

                var chestWeight =
                    exp(
                        -square(
                            (u - 0.505) /
                                0.245
                        ) -
                            square(
                                (v - 0.735) /
                                    0.205
                            )
                    )

                val cloudScarfProtected =
                    u in 0.315..0.805 &&
                        v in 0.510..0.690

                if (cloudScarfProtected) {
                    chestWeight *= 0.20
                }

                if (v >= 0.82) {
                    chestWeight *= 0.08
                }

                y +=
                    0.0026 *
                        breath *
                        chestWeight

                val faceProtected =
                    u in 0.285..0.725 &&
                        v in 0.245..0.535

                // 雨团两只长垂耳：
                // 根部固定，中段传递，耳尖幅度最大。
                for (earIndex in 0..1) {
                    val left =
                        earIndex == 0

                    val pivotU =
                        if (left) {
                            0.315
                        } else {
                            0.690
                        }

                    val pivotV =
                        if (left) {
                            0.300
                        } else {
                            0.300
                        }

                    val centerU =
                        if (left) {
                            0.190
                        } else {
                            0.815
                        }

                    val centerV = 0.355

                    val radiusU = 0.170
                    val radiusV = 0.185

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

                    val dx =
                        x -
                            pivotU
                    val dy =
                        y -
                            pivotV

                    val distance =
                        hypot(
                            u - pivotU,
                            v - pivotV
                        )

                    val anchor =
                        smoothStep(
                            clamp(
                                (
                                    distance -
                                        0.025
                                    ) /
                                    0.235,
                                0.0,
                                1.0
                            )
                        )

                    localWeight *= anchor

                    if (
                        sleepyPoseAmount >
                        0.0 &&
                        localWeight >
                        0.002
                    ) {
                        // 犯困/睡眠时耳尖明显更沉、更向外摊，
                        // 强化“泄力”和侧卧感。
                        y +=
                            0.042 *
                                sleepyPoseAmount *
                                localWeight

                        x +=
                            (
                                if (left) {
                                    -0.014
                                } else {
                                    0.014
                                }
                                ) *
                                sleepyPoseAmount *
                                localWeight
                    }

                    val phase =
                        if (left) {
                            0.0
                        } else {
                            0.42
                        }

                    val period =
                        if (left) {
                            3.15
                        } else {
                            3.35
                        }

                    val direction =
                        if (left) {
                            1.0
                        } else {
                            -1.0
                        }

                    val earAmplitude =
                        when (state) {
                            State.TAIL_WAG -> 18.0
                            State.HAPPY -> 13.5
                            State.REMINDER -> 11.5
                            State.WAVE -> 10.0
                            State.PETTED -> 8.5
                            State.DRAGGING -> 5.0
                            State.SLEEP -> 2.0
                            State.TIRED -> 3.5
                            else ->
                                if (quietNight) {
                                    5.0
                                } else {
                                    8.0
                                }
                        }

                    val angleDeg =
                        direction *
                            signatureStrength *
                            (
                                earAmplitude *
                                    sin(
                                        idleSeconds *
                                            2.0 *
                                            PI /
                                            period +
                                            phase
                                    )
                                )

                    if (localWeight > 0.002) {
                        val angle =
                            angleDeg *
                                PI /
                                180.0

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

                        x =
                            x *
                                (
                                    1.0 -
                                        localWeight
                                    ) +
                                rotatedX *
                                    localWeight

                        y =
                            y *
                                (
                                    1.0 -
                                        localWeight
                                    ) +
                                rotatedY *
                                    localWeight
                    }
                }

                if (blinkAmount > 0.0) {
                    val eyeCenterV = 0.365

                    val leftEyeWeight =
                        exp(
                            -square(
                                (u - 0.395) /
                                    0.063
                            ) -
                                square(
                                    (v - eyeCenterV) /
                                        0.052
                                )
                        )

                    val rightEyeWeight =
                        exp(
                            -square(
                                (u - 0.610) /
                                    0.063
                            ) -
                                square(
                                    (v - eyeCenterV) /
                                        0.052
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
                        0.70 *
                            blinkAmount *
                            eyeWeight

                    y =
                        eyeCenterV +
                            (
                                y -
                                    eyeCenterV
                                ) *
                                (
                                    1.0 -
                                        compression
                                    )
                }

                // 云朵围巾主体尽量稳定，仅右侧尾端轻摆。
                val scarfPivotU = 0.705
                val scarfPivotV = 0.595
                val scarfCenterU = 0.805
                val scarfCenterV = 0.650

                var scarfWeight =
                    exp(
                        -square(
                            (u - scarfCenterU) /
                                0.105
                        ) -
                            square(
                                (v - scarfCenterV) /
                                    0.155
                            )
                    )

                scarfWeight *=
                    smoothStep(
                        clamp(
                            (
                                u -
                                    0.690
                                ) /
                                0.180,
                            0.0,
                            1.0
                        )
                    )

                if (scarfWeight > 0.002) {
                    val scarfAmplitude =
                        when (state) {
                            State.TAIL_WAG -> 14.0
                            State.HAPPY -> 11.0
                            State.REMINDER -> 10.0
                            State.WAVE -> 9.0
                            State.PETTED -> 7.0
                            State.DRAGGING -> 5.0
                            State.TIRED -> 2.4
                            State.SLEEP -> 0.55
                            State.WAKE_UP -> 2.8
                            else -> 5.5
                        }

                    val scarfAngle =
                        scarfAmplitude *
                            sin(
                                idleSeconds *
                                    2.0 *
                                    PI /
                                    2.85 +
                                    0.45
                            ) *
                            PI /
                            180.0

                    val dx =
                        x -
                            scarfPivotU
                    val dy =
                        y -
                            scarfPivotV

                    val rotatedX =
                        scarfPivotU +
                            dx *
                                cos(
                                    scarfAngle
                                ) -
                            dy *
                                sin(
                                    scarfAngle
                                )

                    val rotatedY =
                        scarfPivotV +
                            dx *
                                sin(
                                    scarfAngle
                                ) +
                            dy *
                                cos(
                                    scarfAngle
                                )

                    x =
                        x *
                            (
                                1.0 -
                                    scarfWeight
                                ) +
                            rotatedX *
                                scarfWeight

                    y =
                        y *
                            (
                                1.0 -
                                    scarfWeight
                                ) +
                            rotatedY *
                                scarfWeight
                }

                if (waveActive) {
                    val pivotU = 0.425
                    val pivotV = 0.575
                    val centerU = 0.380
                    val centerV = 0.635

                    var pawWeight =
                        exp(
                            -square(
                                (u - centerU) /
                                    0.100
                            ) -
                                square(
                                    (v - centerV) /
                                        0.135
                                )
                        )

                    if (faceProtected) {
                        pawWeight *= 0.08
                    }

                    val distance =
                        hypot(
                            u - pivotU,
                            v - pivotV
                        )

                    pawWeight *=
                        smoothStep(
                            clamp(
                                (
                                    distance -
                                        0.018
                                    ) /
                                    0.145,
                                0.0,
                                1.0
                            )
                        )

                    val angle =
                        (
                            -31.0 *
                                waveStrength *
                                pawWeight
                            ) *
                            PI /
                            180.0

                    val dx =
                        x -
                            pivotU
                    val dy =
                        y -
                            pivotV

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

                    x =
                        x *
                            (
                                1.0 -
                                    pawWeight
                                ) +
                            rotatedX *
                                pawWeight

                    y =
                        y *
                            (
                                1.0 -
                                    pawWeight
                                ) +
                            rotatedY *
                                pawWeight
                }

                verts[index++] =
                    (
                        x *
                            viewW
                        ).toFloat()

                verts[index++] =
                    (
                        y *
                            viewH
                        ).toFloat()
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
            sin(idleSeconds * 2.0 * PI / 3.15)

        val signatureStrength =
            when (state) {
                State.TAIL_WAG -> 1.0
                State.HAPPY -> 0.90
                State.PETTED -> 0.68
                State.REMINDER -> 0.78
                State.WAVE -> 0.30
                State.DRAGGING -> 0.12
                else ->
                    if (quietNight) {
                        0.13
                    } else {
                        0.24
                    }
            }

        val legStrength =
            when (state) {
                State.HAPPY -> 0.90
                State.PETTED -> 0.40
                State.DRAGGING -> 0.55
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
                    -0.017 *
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
                    -0.0050 *
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
                    0.0028 *
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
                            2.90
                        } else {
                            3.12
                        }

                    val phase =
                        if (left) {
                            0.0
                        } else {
                            0.43
                        }

                    val tipDelay =
                        if (left) {
                            0.40
                        } else {
                            0.38
                        }

                    val rootAngle =
                        if (left) {
                            1.9
                        } else {
                            1.7
                        }

                    val midAngle =
                        if (left) {
                            6.1
                        } else {
                            5.6
                        }

                    val tipAngle =
                        if (left) {
                            11.4
                        } else {
                            10.1
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
                                    0.0092
                                } else {
                                    0.0087
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
                        0.66 *
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
                                3.55
                            } else {
                                3.75
                            }

                        val phase =
                            if (left) {
                                0.0
                            } else {
                                0.35
                            }

                        val rootAngle =
                            if (left) {
                                0.8
                            } else {
                                0.7
                            }

                        val midAngle =
                            if (left) {
                                2.2
                            } else {
                                1.9
                            }

                        val tipAngle =
                            if (left) {
                                3.7
                            } else {
                                3.3
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
                                        0.0062
                                    } else {
                                        0.0058
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
                -35.0 * p,
                0.014 * p,
                -0.031 * p
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
                -35.0 -
                    6.0 *
                    swing,
                0.014 +
                    0.0030 *
                    swing,
                -0.031 -
                    0.0025 *
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
                -35.0 *
                    remain,
                0.014 *
                    remain,
                -0.031 *
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
        const val AMBIENT_CONTENT_SCALE = 0.76f

        private const val WALK_CYCLE_SECONDS =
            0.60

        private const val WALK_LOOK_PERIOD_SECONDS =
            2.70

        private const val WAVE_DURATION_SECONDS = 1.35
        private const val REMINDER_DURATION_SECONDS = 2.70
        private const val HAPPY_DURATION_SECONDS = 1.15
        private const val TAIL_WAG_DURATION_SECONDS = 2.35
        private const val PETTED_DURATION_SECONDS = 1.85
        private const val TIRED_DURATION_SECONDS = 4.8
        private const val TIRED_CROSSFADE_SECONDS = 0.32
        private const val WAKE_DURATION_SECONDS = 1.90
        private const val SLEEP_CROSSFADE_SECONDS = 0.42
        private const val YUTUAN_SLEEP_SETTLE_SECONDS = 0.72


        private const val BLINK_DURATION_NANOS = 340_000_000L
        private const val MIN_BLINK_DELAY_MS = 3_200L
        private const val MAX_BLINK_DELAY_MS = 7_800L
        private const val MIN_IDLE_ACTION_DELAY_MS = 4_800L
        private const val MAX_IDLE_ACTION_DELAY_MS = 10_500L
    }
}
