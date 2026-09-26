package com.shiguangbox.app

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class PetOverlayService : Service() {

    private data class BubblePlacement(
        val x: Int,
        val y: Int,
        val placeAbove: Boolean,
        val score: Int
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: android.content.SharedPreferences

    private var petView: PetRigView? = null
    private var petParams: WindowManager.LayoutParams? = null
    private var panelView: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var reminderTaskId: Long = 0L

    private var dockedSide = 0
    private var tapCount = 0
    private var lastTapAt = 0L
    private var tapDispatchRunnable: Runnable? = null
    private var edgePeekRunnable: Runnable? = null

    private var speechBubble:
        PetSpeechBubbleView? = null

    private var speechBubbleParams:
        WindowManager.LayoutParams? = null

    private var speechBubblePriority = 0

    private var speechBubblePreferBelow =
        false

    private var speechBubbleHideRunnable:
        Runnable? = null

    private var contextBubbleRunnable:
        Runnable? = null

    private var lastAmbientBubbleAt = 0L

    private var yutuanWeatherRunnable:
        Runnable? = null

    private var yutuanSleepWeatherRunnable:
        Runnable? = null

    private var yutuanSleepWeatherSuspended =
        false

    private var rainbowFollowRunnable:
        Runnable? = null

    private var rainbowView:
        ImageView? = null

    private var rainbowParams:
        WindowManager.LayoutParams? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        prefs = getSharedPreferences("shiguangbox_settings", Context.MODE_PRIVATE)
        startPetForeground()
        ensurePetView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                prefs.edit().putBoolean("pet_enabled", false).apply()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PRE_REMINDER -> {
                ensurePetView()
                val title =
                    intent.getStringExtra(
                        EXTRA_TASK_TITLE
                    ) ?: "待办"
                showPreReminder(title)
            }

            ACTION_REMINDER -> {
                ensurePetView()
                val taskId =
                    intent.getLongExtra(
                        EXTRA_TASK_ID,
                        0L
                    )
                val title =
                    intent.getStringExtra(
                        EXTRA_TASK_TITLE
                    ) ?: "待办"
                showReminder(
                    taskId,
                    title
                )
            }

            ACTION_TASK_COMPLETED -> {
                ensurePetView()
                val title =
                    intent.getStringExtra(
                        EXTRA_TASK_TITLE
                    ) ?: "这件事"
                showTaskCompletionCelebration(
                    title
                )
            }

            ACTION_MOOD_CHANGED -> {
                ensurePetView()
                showMoodReaction(
                    intent.getStringExtra(
                        EXTRA_MOOD_ID
                    ) ?: "calm"
                )
            }

            ACTION_TEST_BUBBLE -> {
                ensurePetView()
                showPetBubble(
                    title =
                        petName() + " · 气泡预览",
                    message =
                        "以后提醒、心情和陪伴话都会用这种真正的气泡样式～",
                    tone =
                        PetSpeechBubbleView
                            .Tone.NORMAL,
                    priority =
                        PRIORITY_INTERACTION,
                    durationMs = 4_800L
                )
            }
            ACTION_TEST_WAVE -> {
                ensurePetView()
                petView?.playWave()
            }
            ACTION_TEST_TAIL -> {
                ensurePetView()
                petView?.playTailWag()
            }
            ACTION_TEST_PETTING -> {
                ensurePetView()
                petView?.playPetted()
            }
            ACTION_REFRESH_SETTINGS -> {
                ensurePetView()
                applyPetSettings()
            }
            ACTION_TEST_BLINK -> {
                ensurePetView()
                petView?.playBlink()
            }
            ACTION_TEST_TIRED -> {
                ensurePetView()
                petView?.playTired()
            }
            ACTION_TEST_SLEEP -> {
                ensurePetView()
                petView?.playSleep()
            }
            ACTION_TEST_WAKE -> {
                ensurePetView()
                petView?.wakeUp()
            }
            ACTION_SHOW, null -> {
                if (!prefs.getBoolean("pet_enabled", false)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                ensurePetView()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopYutuanWeatherCycle(
            resetRain = false
        )
        handler.removeCallbacksAndMessages(null)
        closePanel()
        hideSpeechBubble(
            immediate = true
        )
        petView?.release()
        petView?.let {
            runCatching { windowManager.removeView(it) }
        }
        petView = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun selectedPetKind(): PetKind =
        PetProfiles.fromId(
            prefs.getString(
                "pet_selected_id",
                PetKind.ORANGE.id
            )
        )

    private fun petName(): String =
        petView?.displayName()
            ?: selectedPetKind().displayName

    private fun petEmoji(): String =
        petView?.displayEmoji()
            ?: selectedPetKind().emoji

    private fun startPetForeground() {
        val channelId = "shiguangbox_pet_service"
        val manager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "拾光盒桌宠",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "保持拾光盒悬浮桌宠运行"
                    setShowBadge(false)
                }
            )
        }

        val openPending = PendingIntent.getActivity(
            this,
            9010,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopPending = PendingIntent.getService(
            this,
            9011,
            Intent(this, PetOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(petName() + "正在陪着你")
            .setContentText("点击桌宠可以快速记事和添加待办")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(0, "关闭桌宠", stopPending)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun ensurePetView() {
        if (petView != null) return
        if (!Settings.canDrawOverlays(this)) return

        val size = dp(
            prefs.getInt("pet_size_dp", 112)
                .coerceIn(88, 150)
        )
        val savedX = prefs.getInt("pet_x", resources.displayMetrics.widthPixels - size - dp(8))
        val savedY = prefs.getInt("pet_y", resources.displayMetrics.heightPixels / 2)

        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
        }

        val image = PetRigView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            contentDescription =
                displayName() + "桌宠"
            alpha =
                prefs.getInt("pet_alpha_percent", 100)
                    .coerceIn(55, 100) / 100f
            configureBehavior(
                actionLevel = prefs.getInt("pet_action_level", 1),
                autoSleep =
                    prefs.getBoolean("pet_auto_sleep", true),
                sleepMinutes =
                    prefs.getInt("pet_sleep_minutes", 4)
            )

            setStateChangeListener {
                previous,
                current ->
                handleYutuanStateChange(
                    previous,
                    current
                )
            }
        }

        image.setOnTouchListener(PetTouchListener(image, params))

        runCatching {
            windowManager.addView(image, params)
            petView = image
            petParams = params
            dockedSide = when {
                savedX < 0 -> -1
                savedX >
                    resources.displayMetrics.widthPixels - size -> 1
                else -> 0
            }
            startIdleAnimation()
            scheduleEdgePeek()
            maybeShowDailyGreeting()
            scheduleContextBubble()
            startYutuanWeatherCycle()
        }
    }

    private inner class PetTouchListener(
        private val view: View,
        private val params: WindowManager.LayoutParams
    ) : View.OnTouchListener {

        private val touchSlop =
            ViewConfiguration.get(
                this@PetOverlayService
            ).scaledTouchSlop

        private val longPressTimeout =
            ViewConfiguration.getLongPressTimeout().toLong()

        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        private var longPressed = false
        private var wasSleeping = false

        private val longPressRunnable = Runnable {
            if (!dragging) {
                longPressed = true
                closePanel()
                petView?.playPetted()
                showPetBubble(
                    title =
                        petName() + " · 被摸摸",
                    message =
                        "嘿嘿，好舒服～再摸一下也可以。",
                    tone =
                        PetSpeechBubbleView
                            .Tone.MOOD,
                    priority =
                        PRIORITY_INTERACTION,
                    durationMs = 3_200L
                )
            }
        }

        override fun onTouch(
            v: View?,
            event: MotionEvent
        ): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    longPressed = false
                    wasSleeping =
                        petView?.isSleepingOrTired() == true

                    handler.removeCallbacks(longPressRunnable)
                    handler.postDelayed(
                        longPressRunnable,
                        longPressTimeout
                    )
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY

                    if (!dragging &&
                        (
                            kotlin.math.abs(dx) > touchSlop ||
                                kotlin.math.abs(dy) > touchSlop
                            )
                    ) {
                        handler.removeCallbacks(
                            longPressRunnable
                        )
                        dragging = true
                        longPressed = false
                        closePanel()
                        dockedSide = 0
                        edgePeekRunnable?.let {
                            handler.removeCallbacks(it)
                        }
                        petView?.onUserInteraction()
                        petView?.startDragging()
                    }

                    if (dragging) {
                        val screenW =
                            resources.displayMetrics.widthPixels
                        val screenH =
                            resources.displayMetrics.heightPixels
                        val petW = params.width
                        val petH = params.height

                        params.x =
                            (startX + dx.toInt())
                                .coerceIn(
                                    -dp(28),
                                    screenW - petW + dp(28)
                                )

                        params.y =
                            (startY + dy.toInt())
                                .coerceIn(
                                    dp(24),
                                    screenH - petH - dp(28)
                                )

                        runCatching {
                            windowManager.updateViewLayout(
                                view,
                                params
                            )
                        }

                        syncSpeechBubblePosition()
                    }
                    return true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)

                    when {
                        dragging -> {
                            snapToEdge(params)
                            petView?.endDragging()
                            playLandingBounce()
                        }

                        longPressed -> {
                            petView?.startIdle()
                            scheduleEdgePeek()
                        }

                        event.actionMasked ==
                            MotionEvent.ACTION_UP &&
                            wasSleeping -> {
                            closePanel()
                            petView?.wakeUp()
                        }

                        event.actionMasked ==
                            MotionEvent.ACTION_UP -> {
                            queueTapReaction()
                        }

                        else -> petView?.resumeMotion()
                    }
                    return true
                }
            }
            return false
        }
    }

    private fun snapToEdge(
        params: WindowManager.LayoutParams
    ) {
        if (!prefs.getBoolean("pet_auto_snap", true)) {
            dockedSide = 0
            prefs.edit()
                .putInt("pet_x", params.x)
                .putInt("pet_y", params.y)
                .apply()
            return
        }

        val screenW = resources.displayMetrics.widthPixels
        val petWidth = params.width
        val left = -dp(10)
        val right = screenW - petWidth + dp(10)

        dockedSide =
            if (params.x + petWidth / 2 < screenW / 2) {
                -1
            } else {
                1
            }

        val target =
            if (dockedSide < 0) left else right
        val start = params.x

        ValueAnimator.ofInt(start, target).apply {
            duration = 220
            addUpdateListener { animator ->
                params.x =
                    animator.animatedValue as Int
                petView?.let {
                    runCatching {
                        windowManager.updateViewLayout(
                            it,
                            params
                        )
                    }

                    syncSpeechBubblePosition()
                }
            }
            start()
        }

        prefs.edit()
            .putInt("pet_x", target)
            .putInt("pet_y", params.y)
            .apply()

        scheduleEdgePeek()
    }

    private fun queueTapReaction() {
        val now = System.currentTimeMillis()

        tapCount =
            if (now - lastTapAt <= 340L) {
                tapCount + 1
            } else {
                1
            }

        lastTapAt = now
        tapDispatchRunnable?.let {
            handler.removeCallbacks(it)
        }

        val runnable = Runnable {
            val count = tapCount
            tapCount = 0

            when {
                count <= 1 -> {
                    petView?.playTouchReaction()
                    toggleQuickPanel()
                }

                count == 2 -> {
                    closePanel()
                    petView?.playHappy()
                    showTransientBubble(
                        "又来找我啦～"
                    )
                }

                else -> {
                    closePanel()
                    petView?.playTailWag()
                    playExcitedBounce()
                    showTransientBubble(
                        "好开心！再摸摸我～"
                    )
                }
            }

            scheduleEdgePeek()
        }

        tapDispatchRunnable = runnable
        handler.postDelayed(runnable, 300L)
    }

    private fun playExcitedBounce() {
        val pet = petView ?: return

        AnimatorSet().apply {
            duration = 520
            playTogether(
                ObjectAnimator.ofFloat(
                    pet,
                    View.SCALE_X,
                    1f,
                    1.10f,
                    0.97f,
                    1.06f,
                    1f
                ),
                ObjectAnimator.ofFloat(
                    pet,
                    View.SCALE_Y,
                    1f,
                    0.94f,
                    1.08f,
                    0.98f,
                    1f
                ),
                ObjectAnimator.ofFloat(
                    pet,
                    View.TRANSLATION_Y,
                    0f,
                    -dp(10).toFloat(),
                    0f
                )
            )
            start()
        }
    }

    private fun applyPetSettings() {
        val pet = petView ?: return

        val selectedId =
            prefs.getString(
                "pet_selected_id",
                PetKind.ORANGE.id
            ) ?: PetKind.ORANGE.id

        if (pet.currentPetId() != selectedId) {
            recreatePetViewForSelection()
            return
        }

        val params = petParams ?: return

        pet.configureBehavior(
            actionLevel =
                prefs.getInt("pet_action_level", 1),
            autoSleep =
                prefs.getBoolean(
                    "pet_auto_sleep",
                    true
                ),
            sleepMinutes =
                prefs.getInt("pet_sleep_minutes", 4)
        )

        pet.alpha =
            prefs.getInt("pet_alpha_percent", 100)
                .coerceIn(55, 100) / 100f

        val newSize =
            dp(
                prefs.getInt("pet_size_dp", 112)
                    .coerceIn(88, 150)
            )

        params.width = newSize
        params.height = newSize

        val screenW =
            resources.displayMetrics.widthPixels
        val screenH =
            resources.displayMetrics.heightPixels

        params.x =
            params.x.coerceIn(
                -dp(28),
                screenW - newSize + dp(28)
            )

        params.y =
            params.y.coerceIn(
                dp(24),
                screenH - newSize - dp(28)
            )

        runCatching {
            windowManager.updateViewLayout(
                pet,
                params
            )
        }

        syncSpeechBubblePosition(
            force = true
        )
        syncYutuanRainbowPosition()

        if (prefs.getBoolean("pet_auto_snap", true)) {
            snapToEdge(params)
        } else {
            dockedSide = 0
        }

        scheduleEdgePeek()
    }

    private fun recreatePetViewForSelection() {
        closePanel()
        hideSpeechBubble(
            immediate = true
        )
        stopYutuanWeatherCycle(
            resetRain = false
        )

        edgePeekRunnable?.let {
            handler.removeCallbacks(it)
        }
        edgePeekRunnable = null

        petParams?.let { params ->
            prefs.edit()
                .putInt("pet_x", params.x)
                .putInt("pet_y", params.y)
                .apply()
        }

        val oldPet = petView

        // Detach first, then stop animation. This avoids a pending
        // Canvas frame touching a bitmap while the pet is replaced.
        oldPet?.let {
            runCatching {
                windowManager.removeViewImmediate(it)
            }
        }

        oldPet?.release()

        petView = null
        petParams = null

        ensurePetView()
    }


    private fun handleYutuanStateChange(
        previous:
            PetRigView.State,
        current:
            PetRigView.State
    ) {
        val pet =
            petView ?: return

        if (
            pet.currentPetId() !=
            PetKind.YUTUAN.id
        ) {
            return
        }

        yutuanSleepWeatherRunnable
            ?.let {
                handler.removeCallbacks(
                    it
                )
            }
        yutuanSleepWeatherRunnable =
            null

        when (current) {
            PetRigView.State.TIRED -> {
                yutuanSleepWeatherSuspended =
                    true

                yutuanWeatherRunnable
                    ?.let {
                        handler.removeCallbacks(
                            it
                        )
                    }
                yutuanWeatherRunnable =
                    null

                hideYutuanRainbow(
                    immediate = false
                )

                pet.setYutuanRainTarget(
                    0.36f
                )
            }

            PetRigView.State.SLEEP -> {
                yutuanSleepWeatherSuspended =
                    true

                yutuanWeatherRunnable
                    ?.let {
                        handler.removeCallbacks(
                            it
                        )
                    }
                yutuanWeatherRunnable =
                    null

                hideYutuanRainbow(
                    immediate = true
                )

                pet.setYutuanRainTarget(
                    0.12f
                )

                val runnable =
                    Runnable {
                        val currentPet =
                            petView
                                ?: return@Runnable

                        if (
                            currentPet
                                .currentPetId() ==
                            PetKind.YUTUAN.id &&
                            currentPet
                                .currentState() ==
                            PetRigView.State.SLEEP
                        ) {
                            currentPet
                                .setYutuanRainTarget(
                                    if (
                                        kotlin.random.Random
                                            .nextFloat() <
                                        0.72f
                                    ) {
                                        0f
                                    } else {
                                        0.08f
                                    }
                                )
                        }
                    }

                yutuanSleepWeatherRunnable =
                    runnable

                handler.postDelayed(
                    runnable,
                    2_400L
                )
            }

            PetRigView.State.WAKE_UP -> {
                yutuanSleepWeatherSuspended =
                    true

                yutuanWeatherRunnable
                    ?.let {
                        handler.removeCallbacks(
                            it
                        )
                    }
                yutuanWeatherRunnable =
                    null

                hideYutuanRainbow(
                    immediate = true
                )

                pet.setYutuanRainTarget(
                    0.22f
                )
            }

            PetRigView.State.IDLE -> {
                if (
                    yutuanSleepWeatherSuspended
                ) {
                    yutuanSleepWeatherSuspended =
                        false

                    hideYutuanRainbow(
                        immediate = true
                    )

                    pet.setYutuanRainTarget(
                        0.48f
                    )

                    val first =
                        Runnable {
                            val currentPet =
                                petView
                                    ?: return@Runnable

                            if (
                                currentPet
                                    .currentPetId() !=
                                PetKind.YUTUAN.id ||
                                currentPet
                                    .currentState() !=
                                PetRigView.State.IDLE
                            ) {
                                return@Runnable
                            }

                            currentPet
                                .setYutuanRainTarget(
                                    0.76f
                                )

                            val second =
                                Runnable {
                                    val idlePet =
                                        petView
                                            ?: return@Runnable

                                    if (
                                        idlePet
                                            .currentPetId() !=
                                        PetKind.YUTUAN.id ||
                                        idlePet
                                            .currentState() !=
                                        PetRigView.State.IDLE
                                    ) {
                                        return@Runnable
                                    }

                                    idlePet
                                        .setYutuanRainTarget(
                                            1f
                                        )

                                    scheduleYutuanRainStop()
                                }

                            yutuanSleepWeatherRunnable =
                                second

                            handler.postDelayed(
                                second,
                                1_250L
                            )
                        }

                    yutuanSleepWeatherRunnable =
                        first

                    handler.postDelayed(
                        first,
                        850L
                    )
                }
            }

            else -> {
                if (
                    yutuanSleepWeatherSuspended
                ) {
                    pet.setYutuanRainTarget(
                        0.32f
                    )
                }
            }
        }
    }


    private fun startYutuanWeatherCycle() {
        stopYutuanWeatherCycle(
            resetRain = false
        )

        yutuanSleepWeatherSuspended =
            false

        val pet =
            petView ?: return

        if (
            pet.currentPetId() !=
            PetKind.YUTUAN.id
        ) {
            return
        }

        pet.setYutuanRainTarget(
            1f
        )

        scheduleYutuanRainStop()
    }

    private fun scheduleYutuanRainStop() {
        val pet =
            petView ?: return

        if (
            pet.currentPetId() !=
            PetKind.YUTUAN.id ||
            pet.isSleepingOrTired() ||
            yutuanSleepWeatherSuspended
        ) {
            return
        }

        yutuanWeatherRunnable
            ?.let {
                handler.removeCallbacks(
                    it
                )
            }

        val delay =
            kotlin.random.Random
                .nextLong(
                    10_000L,
                    20_001L
                )

        val runnable =
            Runnable {
                beginYutuanRainStop()
            }

        yutuanWeatherRunnable =
            runnable

        handler.postDelayed(
            runnable,
            delay
        )
    }

    private fun beginYutuanRainStop() {
        val pet =
            petView ?: return

        if (
            pet.currentPetId() !=
            PetKind.YUTUAN.id ||
            pet.isSleepingOrTired() ||
            yutuanSleepWeatherSuspended
        ) {
            return
        }

        pet.setYutuanRainTarget(
            0f
        )

        val runnable =
            Runnable {
                if (
                    petView
                        ?.currentPetId() !=
                    PetKind.YUTUAN.id ||
                    petView
                        ?.isSleepingOrTired() ==
                    true ||
                    yutuanSleepWeatherSuspended
                ) {
                    return@Runnable
                }

                if (
                    kotlin.random.Random
                        .nextFloat() <
                    0.65f
                ) {
                    showYutuanRainbowMoment()
                } else {
                    scheduleYutuanRainRestart(
                        kotlin.random.Random
                            .nextLong(
                                5_000L,
                                12_001L
                            )
                    )
                }
            }

        yutuanWeatherRunnable =
            runnable

        handler.postDelayed(
            runnable,
            1_800L
        )
    }

    private fun showYutuanRainbowMoment() {
        val pet =
            petView ?: return

        if (
            pet.currentPetId() !=
            PetKind.YUTUAN.id ||
            pet.isSleepingOrTired() ||
            yutuanSleepWeatherSuspended
        ) {
            return
        }

        showYutuanRainbow()

        val lines =
            listOf(
                "雨停啦，彩虹出来了～",
                "你看，是彩虹耶！",
                "雨过之后，也会有一点点晴天呀。",
                "今天的雨，先下到这里吧～"
            )

        handler.postDelayed(
            {
                if (
                    petView
                        ?.currentPetId() ==
                    PetKind.YUTUAN.id &&
                    petView
                        ?.isSleepingOrTired() !=
                    true &&
                    !yutuanSleepWeatherSuspended &&
                    rainbowView != null
                ) {
                    showPetBubble(
                        title =
                            "雨团 · 雨停啦 🌈",
                        message =
                            lines.random(),
                        tone =
                            PetSpeechBubbleView
                                .Tone.MOOD,
                        priority =
                            PRIORITY_AMBIENT,
                        durationMs =
                            4_800L,
                        preferBelow =
                            false
                    )
                }
            },
            260L
        )

        val rainbowDuration =
            kotlin.random.Random
                .nextLong(
                    5_000L,
                    8_001L
                )

        val runnable =
            Runnable {
                hideYutuanRainbow(
                    immediate = false
                )

                scheduleYutuanRainRestart(
                    kotlin.random.Random
                        .nextLong(
                            5_000L,
                            12_001L
                        )
                )
            }

        yutuanWeatherRunnable =
            runnable

        handler.postDelayed(
            runnable,
            rainbowDuration
        )
    }

    private fun scheduleYutuanRainRestart(
        delayMs: Long
    ) {
        yutuanWeatherRunnable
            ?.let {
                handler.removeCallbacks(
                    it
                )
            }

        val runnable =
            Runnable {
                val pet =
                    petView
                        ?: return@Runnable

                if (
                    pet.currentPetId() !=
                    PetKind.YUTUAN.id ||
                    pet.isSleepingOrTired() ||
                    yutuanSleepWeatherSuspended
                ) {
                    return@Runnable
                }

                pet.setYutuanRainTarget(
                    1f
                )

                scheduleYutuanRainStop()
            }

        yutuanWeatherRunnable =
            runnable

        handler.postDelayed(
            runnable,
            delayMs
        )
    }

    private fun showYutuanRainbow() {
        hideYutuanRainbow(
            immediate = true
        )

        val pet =
            petParams ?: return

        if (
            petView
                ?.currentPetId() !=
            PetKind.YUTUAN.id ||
            petView
                ?.isSleepingOrTired() ==
            true ||
            yutuanSleepWeatherSuspended
        ) {
            return
        }

        val width =
            (
                pet.width *
                    0.82f
                )
                .toInt()
                .coerceIn(
                    dp(58),
                    dp(124)
                )

        val height =
            (
                width *
                    172f /
                    256f
                )
                .toInt()
                .coerceAtLeast(
                    dp(38)
                )

        val view =
            ImageView(this).apply {
                setImageResource(
                    R.drawable
                        .pet_yutuan_rainbow
                )
                scaleType =
                    ImageView
                        .ScaleType
                        .FIT_CENTER
                isClickable = false
                alpha = 0f
                scaleX = 0.88f
                scaleY = 0.88f
                translationY =
                    dp(6)
                        .toFloat()
            }

        val params =
            WindowManager
                .LayoutParams(
                    width,
                    height,
                    WindowManager
                        .LayoutParams
                        .TYPE_APPLICATION_OVERLAY,
                    WindowManager
                        .LayoutParams
                        .FLAG_NOT_FOCUSABLE or
                        WindowManager
                            .LayoutParams
                            .FLAG_NOT_TOUCHABLE or
                        WindowManager
                            .LayoutParams
                            .FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                )
                .apply {
                    gravity =
                        Gravity.TOP or
                            Gravity.START
                }

        positionYutuanRainbow(
            params
        )

        runCatching {
            windowManager.addView(
                view,
                params
            )

            rainbowView =
                view
            rainbowParams =
                params

            syncSpeechBubblePosition(
                force = true
            )

            view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(620L)
                .start()

            startYutuanRainbowFollowing()
        }
    }

    private fun positionYutuanRainbow(
        params:
            WindowManager.LayoutParams
    ) {
        val pet =
            petParams ?: return

        val screenW =
            resources
                .displayMetrics
                .widthPixels

        val headTop =
            pet.y +
                (
                    pet.height *
                        0.10f
                    ).toInt()

        params.x =
            (
                pet.x +
                    (
                        pet.width -
                            params.width
                        ) /
                    2
                )
                .coerceIn(
                    -dp(6),
                    screenW -
                        params.width +
                        dp(6)
                )

        params.y =
            (
                headTop -
                    params.height -
                    dp(6)
                )
                .coerceAtLeast(
                    dp(8)
                )
    }

    private fun syncYutuanRainbowPosition() {
        val view =
            rainbowView ?: return
        val params =
            rainbowParams ?: return
        val pet =
            petParams ?: return

        val targetWidth =
            (
                pet.width *
                    0.82f
                )
                .toInt()
                .coerceIn(
                    dp(58),
                    dp(124)
                )

        params.width =
            targetWidth

        params.height =
            (
                targetWidth *
                    172f /
                    256f
                )
                .toInt()
                .coerceAtLeast(
                    dp(38)
                )

        positionYutuanRainbow(
            params
        )

        runCatching {
            windowManager
                .updateViewLayout(
                    view,
                    params
                )
        }

        syncSpeechBubblePosition()
    }

    private fun startYutuanRainbowFollowing() {
        rainbowFollowRunnable
            ?.let {
                handler.removeCallbacks(
                    it
                )
            }

        val runnable =
            object : Runnable {
                override fun run() {
                    if (
                        rainbowView ==
                        null
                    ) {
                        return
                    }

                    syncYutuanRainbowPosition()

                    handler.postDelayed(
                        this,
                        32L
                    )
                }
            }

        rainbowFollowRunnable =
            runnable

        handler.post(
            runnable
        )
    }

    private fun hideYutuanRainbow(
        immediate: Boolean
    ) {
        rainbowFollowRunnable
            ?.let {
                handler.removeCallbacks(
                    it
                )
            }

        rainbowFollowRunnable =
            null

        val view =
            rainbowView ?: return

        rainbowView =
            null
        rainbowParams =
            null

        if (immediate) {
            runCatching {
                windowManager
                    .removeView(
                        view
                    )
            }

            syncSpeechBubblePosition(
                force = true
            )
            return
        }

        view.animate()
            .alpha(0f)
            .scaleX(0.94f)
            .scaleY(0.94f)
            .translationY(
                -dp(3)
                    .toFloat()
            )
            .setDuration(480L)
            .withEndAction {
                runCatching {
                    windowManager
                        .removeView(
                            view
                        )
                }

                syncSpeechBubblePosition(
                    force = true
                )
            }
            .start()
    }

    private fun stopYutuanWeatherCycle(
        resetRain: Boolean = true
    ) {
        yutuanSleepWeatherRunnable
            ?.let {
                handler.removeCallbacks(
                    it
                )
            }

        yutuanSleepWeatherRunnable =
            null
        yutuanSleepWeatherSuspended =
            false

        yutuanWeatherRunnable
            ?.let {
                handler.removeCallbacks(
                    it
                )
            }

        yutuanWeatherRunnable =
            null

        hideYutuanRainbow(
            immediate = true
        )

        if (resetRain) {
            petView
                ?.setYutuanRainTarget(
                    1f
                )
        }
    }

    private fun scheduleEdgePeek() {
        edgePeekRunnable?.let {
            handler.removeCallbacks(it)
        }

        if (!prefs.getBoolean("pet_edge_peek", true) ||
            !prefs.getBoolean("pet_auto_snap", true) ||
            dockedSide == 0
        ) {
            return
        }

        val delay =
            kotlin.random.Random.nextLong(
                14_000L,
                26_001L
            )

        val runnable = Runnable {
            if (panelView == null &&
                petView?.canDoAmbientAction() == true
            ) {
                playEdgePeek()
            } else {
                scheduleEdgePeek()
            }
        }

        edgePeekRunnable = runnable
        handler.postDelayed(runnable, delay)
    }

    private fun playEdgePeek() {
        val pet = petView ?: return
        val params = petParams ?: return
        if (dockedSide == 0) return

        val screenW =
            resources.displayMetrics.widthPixels
        val petWidth = params.width

        val baseX =
            if (dockedSide < 0) {
                -dp(10)
            } else {
                screenW - petWidth + dp(10)
            }

        val hiddenX =
            if (dockedSide < 0) {
                -dp(34)
            } else {
                screenW - petWidth + dp(34)
            }

        pet.playBlink()

        ValueAnimator
            .ofInt(baseX, hiddenX, baseX)
            .apply {
                duration = 1_250L
                addUpdateListener { animator ->
                    params.x =
                        animator.animatedValue as Int
                    runCatching {
                        windowManager.updateViewLayout(
                            pet,
                            params
                        )
                    }

                    syncSpeechBubblePosition()
                }
                start()
            }

        handler.postDelayed({
            if (pet.canDoAmbientAction()) {
                pet.playWave()
            }
            scheduleEdgePeek()
        }, 1_350L)
    }

    private fun playLandingBounce() {
        val pet = petView ?: return
        AnimatorSet().apply {
            duration = 260
            playTogether(
                ObjectAnimator.ofFloat(pet, View.SCALE_X, 1f, 1.07f, 0.985f, 1f),
                ObjectAnimator.ofFloat(pet, View.SCALE_Y, 1f, 0.94f, 1.025f, 1f),
                ObjectAnimator.ofFloat(pet, View.TRANSLATION_Y, -dp(3).toFloat(), dp(5).toFloat(), 0f)
            )
            start()
        }
    }

    private fun startIdleAnimation() {
        petView?.startIdle()
    }

    private fun toggleQuickPanel() {
        if (panelView != null) {
            closePanel()
        } else {
            showQuickPanel()
        }
    }

    private fun showQuickPanel() {
        hideSpeechBubble(
            immediate = true
        )
        closePanel()

        val root = basePanel()
        val title = label(
            petName() + " " + petEmoji(),
            18f,
            true,
            Color.rgb(78, 58, 46)
        )
        val subtitle = label("想记点什么吗？", 12f, false, Color.rgb(138, 121, 105))

        root.addView(title)
        root.addView(subtitle)
        root.addView(spacer(8))

        val note = actionButton("✎ 记一下")
        val task = actionButton("✓ 加待办")
        val today = actionButton("☀ 查看今天")
        val close = actionButton("暂时收起")

        note.setOnClickListener { showInputPanel(InputMode.NOTE) }
        task.setOnClickListener { showInputPanel(InputMode.TASK) }
        today.setOnClickListener {
            closePanel()
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
        close.setOnClickListener {
            closePanel()
            petView?.visibility = View.GONE
            handler.postDelayed({
                petView?.visibility = View.VISIBLE
                startIdleAnimation()
            }, 60L * 60L * 1000L)
        }

        root.addView(note)
        root.addView(task)
        root.addView(today)
        root.addView(close)

        addPanel(root, focusable = false)

        scope.launch {
            val pending = withContext(Dispatchers.IO) {
                AppDatabase.get(this@PetOverlayService)
                    .taskDao()
                    .getAllOnce()
                    .count { !it.completed }
            }
            if (subtitle.isAttachedToWindow) {
                subtitle.text = if (pending > 0) "今天还有 " + pending + " 件待办在盒子里" else "今天没有未完成待办"
            }
        }
    }

    private fun showInputPanel(mode: InputMode) {
        hideSpeechBubble(
            immediate = true
        )
        closePanel()

        val root = basePanel()
        root.addView(
            label(
                if (mode == InputMode.NOTE) {
                    petName() + "帮你记一下"
                } else {
                    "快速加待办"
                },
                17f,
                true,
                Color.rgb(78, 58, 46)
            )
        )

        val edit = EditText(this).apply {
            hint = if (mode == InputMode.NOTE)
                "刚刚想到什么？"
            else
                "例如：明天下午三点提醒我交材料"
            textSize = 14f
            setTextColor(Color.rgb(73, 57, 44))
            setHintTextColor(Color.rgb(160, 145, 132))
            minLines = 2
            maxLines = 4
            background = roundedBackground(Color.rgb(255, 249, 240), 14)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        root.addView(spacer(8))
        root.addView(
            edit,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val save = actionButton(if (mode == InputMode.NOTE) "收进拾光盒" else "创建待办")
        val cancel = actionButton("取消")
        root.addView(save)
        root.addView(cancel)

        cancel.setOnClickListener {
            closePanel()
            showQuickPanel()
        }

        save.setOnClickListener {
            val text = edit.text.toString().trim()
            if (text.isBlank()) return@setOnClickListener
            closePanel()

            if (mode == InputMode.NOTE) {
                saveQuickNote(text)
            } else {
                saveQuickTask(text)
            }
        }

        addPanel(root, focusable = true)
        edit.requestFocus()
        handler.postDelayed({
            val imm = getSystemService(InputMethodManager::class.java)
            imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
        }, 180)
    }

    private fun saveQuickNote(text: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.get(this@PetOverlayService)
                    .noteDao()
                    .insert(
                        NoteEntity(
                            content = text,
                            category = "临时"
                        )
                    )
            }
            showSuccess("记好啦，已经放进拾光盒 ✨")
        }
    }

    private fun saveQuickTask(text: String) {
        scope.launch {
            val parsed = PetTaskParser.parse(text) ?: return@launch
            val task = TaskEntity(
                title = parsed.title,
                dueAt = parsed.dueAt,
                remindAt = parsed.dueAt,
                priority = "普通"
            )

            val id = withContext(Dispatchers.IO) {
                AppDatabase.get(this@PetOverlayService)
                    .taskDao()
                    .insert(task)
            }

            if (parsed.dueAt != null) {
                ReminderScheduler.schedule(
                    this@PetOverlayService,
                    task.copy(id = id)
                )
                showSuccess("记住啦，到时间我来提醒你～")
            } else {
                showSuccess("已经加进待办啦；这条没有识别到提醒时间")
            }
        }
    }

    private fun showPreReminder(
        title: String
    ) {
        petView?.playWave()

        showPetBubble(
            title =
                petName() + " · 提前提醒",
            message =
                "还有 10 分钟就是「" +
                    title +
                    "」啦，可以慢慢收尾咯～",
            tone =
                PetSpeechBubbleView
                    .Tone.NORMAL,
            priority = PRIORITY_TASK,
            durationMs = 5_200L
        )
    }

    private fun showReminder(
        taskId: Long,
        title: String
    ) {
        reminderTaskId = taskId
        closePanel()
        animateReminder()

        val bubble =
            PetSpeechBubbleView(
                this,
                selectedPetKind()
            ).apply {
                bind(
                    title =
                        petName() + "提醒你 · 到时间啦 🔔",
                    message =
                        "「" +
                            title +
                            "」现在该开始啦～",
                    tone =
                        PetSpeechBubbleView
                            .Tone.REMINDER
                )

                addAction(
                    label = "✓ 完成",
                    primary = true
                ) {
                    completeReminder(
                        taskId
                    )
                }

                addAction(
                    label =
                        "10 分钟后"
                ) {
                    snoozeReminder(
                        taskId
                    )
                }
            }

        showSpeechBubbleView(
            view = bubble,
            priority =
                PRIORITY_REMINDER,
            durationMs = 0L
        )
    }

    private fun completeReminder(taskId: Long) {
        closePanel()
        hideSpeechBubble(
            immediate = true
        )
        scope.launch {
            val db = AppDatabase.get(this@PetOverlayService)
            val task = withContext(Dispatchers.IO) {
                db.taskDao().getAllOnce().firstOrNull { it.id == taskId }
            }

            if (task != null && !task.completed) {
                val completed = task.copy(
                    completed = true,
                    completedAt = System.currentTimeMillis()
                )

                withContext(Dispatchers.IO) {
                    db.taskDao().update(completed)
                    if (task.repeatType != "不重复") {
                        val next = nextRecurringTask(task)
                        val nextId = db.taskDao().insert(next)
                        ReminderScheduler.schedule(
                            this@PetOverlayService,
                            next.copy(id = nextId)
                        )
                    }
                }
            }

            getSystemService(NotificationManager::class.java).cancel(taskId.toInt())
            reminderTaskId = 0L

            showTaskCompletionCelebration(
                task?.title ?: "这件事"
            )
        }
    }

    private fun snoozeReminder(taskId: Long) {
        closePanel()
        hideSpeechBubble(
            immediate = true
        )
        scope.launch {
            val db = AppDatabase.get(this@PetOverlayService)
            val task = withContext(Dispatchers.IO) {
                db.taskDao().getAllOnce().firstOrNull { it.id == taskId }
            }

            if (task != null && !task.completed) {
                val updated = task.copy(
                    remindAt = System.currentTimeMillis() + 10L * 60L * 1000L
                )
                withContext(Dispatchers.IO) {
                    db.taskDao().update(updated)
                }
                ReminderScheduler.schedule(this@PetOverlayService, updated)
            }

            getSystemService(NotificationManager::class.java).cancel(taskId.toInt())
            reminderTaskId = 0L
            showPetBubble(
                title =
                    petName() + " · 收到",
                message =
                    "好～10 分钟后我再来找你。",
                tone =
                    PetSpeechBubbleView
                        .Tone.NORMAL,
                priority =
                    PRIORITY_TASK,
                durationMs = 3_600L
            )
        }
    }

    private fun nextRecurringTask(task: TaskEntity): TaskEntity {
        val due = task.dueAt ?: System.currentTimeMillis()
        val reminder = task.remindAt ?: due
        val zone = ZoneId.systemDefault()

        val dueZoned = Instant.ofEpochMilli(due).atZone(zone)
        val reminderZoned = Instant.ofEpochMilli(reminder).atZone(zone)

        val nextDue = when (task.repeatType) {
            "每天" -> dueZoned.plusDays(1)
            "每周" -> dueZoned.plusWeeks(1)
            "每月" -> dueZoned.plusMonths(1)
            else -> dueZoned
        }

        val nextReminder = when (task.repeatType) {
            "每天" -> reminderZoned.plusDays(1)
            "每周" -> reminderZoned.plusWeeks(1)
            "每月" -> reminderZoned.plusMonths(1)
            else -> reminderZoned
        }

        return task.copy(
            id = 0,
            dueAt = nextDue.toInstant().toEpochMilli(),
            remindAt = nextReminder.toInstant().toEpochMilli(),
            completed = false,
            completedAt = null,
            createdAt = System.currentTimeMillis()
        )
    }

    private fun showSuccess(
        message: String
    ) {
        animateSuccess()
        showPetBubble(
            title =
                petName() + " · 完成啦 ✨",
            message = message,
            tone =
                PetSpeechBubbleView
                    .Tone.SUCCESS,
            priority = PRIORITY_TASK,
            durationMs = 4_000L
        )
    }

    private fun animateReminder() {
        petView?.playReminder()
    }

    private fun animateSuccess() {
        val pet = petView ?: return
        pet.playHappy()
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(pet, View.TRANSLATION_Y, 0f, -dp(24).toFloat(), 0f),
                ObjectAnimator.ofFloat(pet, View.SCALE_X, 1f, 1.13f, 1f),
                ObjectAnimator.ofFloat(pet, View.SCALE_Y, 1f, 1.13f, 1f)
            )
            duration = 620
            start()
        }

        handler.postDelayed({ startIdleAnimation() }, 700)
    }

    private fun showTransientBubble(
        message: String
    ) {
        showPetBubble(
            title = petName(),
            message = message,
            tone =
                PetSpeechBubbleView
                    .Tone.NORMAL,
            priority =
                PRIORITY_INTERACTION,
            durationMs = 3_200L
        )
    }

    private fun showPetBubble(
        title: String,
        message: String,
        tone:
            PetSpeechBubbleView.Tone,
        priority: Int,
        durationMs: Long,
        preferBelow: Boolean = false
    ) {
        val bubble =
            PetSpeechBubbleView(
                this,
                selectedPetKind()
            ).apply {
                bind(
                    title = title,
                    message = message,
                    tone = tone
                )
            }

        showSpeechBubbleView(
            view = bubble,
            priority = priority,
            durationMs = durationMs,
            preferBelow = preferBelow
        )
    }

    private fun showSpeechBubbleView(
        view: PetSpeechBubbleView,
        priority: Int,
        durationMs: Long,
        preferBelow: Boolean = false
    ) {
        val pet =
            petParams ?: return

        if (
            speechBubble != null &&
            priority <
            speechBubblePriority
        ) {
            return
        }

        hideSpeechBubble(
            immediate = true
        )

        val screenW =
            resources
                .displayMetrics
                .widthPixels

        val screenH =
            resources
                .displayMetrics
                .heightPixels

        val rainbowRect =
            rainbowParams
                ?.let {
                    Rect(
                        it.x,
                        it.y,
                        it.x +
                            it.width,
                        it.y +
                            it.height
                    )
                }

        val compactForRainbow =
            rainbowRect != null &&
                view.isDecorativeTheme()

        view.setCompactMode(
            compactForRainbow
        )

        val bubbleWidth =
            view.preferredWidthPx(
                screenW
            )

        view.setTail(
            atTop = false,
            centerPx =
                bubbleWidth *
                    view.tailAnchorFraction()
        )

        view.measure(
            View.MeasureSpec
                .makeMeasureSpec(
                    bubbleWidth,
                    View.MeasureSpec.EXACTLY
                ),
            View.MeasureSpec
                .makeMeasureSpec(
                    0,
                    View.MeasureSpec
                        .UNSPECIFIED
                )
        )

        val measuredH =
            view.measuredHeight
                .coerceAtLeast(
                    view.minimumOverlayHeightPx()
                )

        val placement =
            resolveSpeechBubblePlacement(
                pet = pet,
                bubbleWidth = bubbleWidth,
                bubbleHeight = measuredH,
                screenW = screenW,
                screenH = screenH,
                rainbowRect = rainbowRect,
                preferBelow = preferBelow
            )

        val petCenterX =
            pet.x +
                pet.width / 2

        val tailCenter =
            (petCenterX -
                placement.x)
                .toFloat()
                .coerceIn(
                    dp(34).toFloat(),
                    (
                        bubbleWidth -
                            dp(34)
                        ).toFloat()
                )

        view.setTail(
            atTop =
                !placement.placeAbove,
            centerPx =
                tailCenter
        )

        val params =
            WindowManager
                .LayoutParams(
                    bubbleWidth,
                    WindowManager
                        .LayoutParams
                        .WRAP_CONTENT,
                    WindowManager
                        .LayoutParams
                        .TYPE_APPLICATION_OVERLAY,
                    WindowManager
                        .LayoutParams
                        .FLAG_NOT_FOCUSABLE or
                        WindowManager
                            .LayoutParams
                            .FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                )
                .apply {
                    gravity =
                        Gravity.TOP or
                            Gravity.START
                    x =
                        placement.x
                    y =
                        placement.y
                }

        runCatching {
            windowManager.addView(
                view,
                params
            )

            speechBubble =
                view
            speechBubbleParams =
                params
            speechBubblePriority =
                priority
            speechBubblePreferBelow =
                preferBelow

            view.alpha =
                0f
            view.scaleX =
                0.94f
            view.scaleY =
                0.94f
            view.translationY =
                if (
                    placement.placeAbove
                ) {
                    dp(6).toFloat()
                } else {
                    -dp(6).toFloat()
                }

            view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(190L)
                .start()

            if (durationMs > 0L) {
                val hide =
                    Runnable {
                        hideSpeechBubble(
                            immediate =
                                false
                        )
                    }

                speechBubbleHideRunnable =
                    hide

                handler.postDelayed(
                    hide,
                    durationMs
                )
            }
        }
    }

    private fun resolveSpeechBubblePlacement(
        pet:
            WindowManager.LayoutParams,
        bubbleWidth: Int,
        bubbleHeight: Int,
        screenW: Int,
        screenH: Int,
        rainbowRect: Rect?,
        preferBelow: Boolean
    ): BubblePlacement {
        val safeLeft =
            dp(8)
        val safeRight =
            screenW -
                dp(8)
        val safeTop =
            dp(20)
        val safeBottom =
            screenH -
                dp(28)

        val petCenterX =
            pet.x +
                pet.width / 2

        val tailEdgeOffset =
            dp(44)

        val leftX =
            petCenterX -
                bubbleWidth +
                tailEdgeOffset

        val rightX =
            petCenterX -
                tailEdgeOffset

        val aboveY =
            pet.y -
                bubbleHeight +
                dp(12)

        val belowY =
            pet.y +
                pet.height -
                dp(4)

        val candidates =
            listOf(
                BubblePlacement(
                    x = leftX,
                    y = aboveY,
                    placeAbove = true,
                    score = 0
                ),
                BubblePlacement(
                    x = rightX,
                    y = aboveY,
                    placeAbove = true,
                    score = 0
                ),
                BubblePlacement(
                    x = leftX,
                    y = belowY,
                    placeAbove = false,
                    score = 0
                ),
                BubblePlacement(
                    x = rightX,
                    y = belowY,
                    placeAbove = false,
                    score = 0
                )
            )

        val petRect =
            Rect(
                pet.x,
                pet.y,
                pet.x +
                    pet.width,
                pet.y +
                    pet.height
            )

        val expandedRainbow =
            rainbowRect
                ?.let {
                    Rect(
                        it.left -
                            dp(8),
                        it.top -
                            dp(8),
                        it.right +
                            dp(8),
                        it.bottom +
                            dp(8)
                    )
                }

        fun scoreCandidate(
            candidate:
                BubblePlacement
        ): Int {
            val rect =
                Rect(
                    candidate.x,
                    candidate.y,
                    candidate.x +
                        bubbleWidth,
                    candidate.y +
                        bubbleHeight
                )

            var score =
                10_000

            val overflowLeft =
                (
                    safeLeft -
                        rect.left
                    )
                    .coerceAtLeast(0)

            val overflowRight =
                (
                    rect.right -
                        safeRight
                    )
                    .coerceAtLeast(0)

            val overflowTop =
                (
                    safeTop -
                        rect.top
                    )
                    .coerceAtLeast(0)

            val overflowBottom =
                (
                    rect.bottom -
                        safeBottom
                    )
                    .coerceAtLeast(0)

            score -=
                (
                    overflowLeft +
                        overflowRight +
                        overflowTop +
                        overflowBottom
                    ) *
                    24

            if (
                expandedRainbow != null &&
                Rect.intersects(
                    rect,
                    expandedRainbow
                )
            ) {
                score -=
                    8_000
            } else if (
                expandedRainbow != null
            ) {
                score +=
                    900
            }

            val petOverlap =
                Rect(
                    rect
                )

            if (
                petOverlap.intersect(
                    petRect
                )
            ) {
                val overlapArea =
                    petOverlap.width() *
                        petOverlap.height()

                score -=
                    (
                        overlapArea /
                            10
                        )
                        .coerceAtMost(
                            2_600
                        )
            } else {
                score +=
                    350
            }

            val bubbleCenterX =
                candidate.x +
                    bubbleWidth / 2

            val bubbleIsLeft =
                bubbleCenterX <
                    petCenterX

            val preferredLeft =
                petCenterX >
                    screenW / 2

            if (
                bubbleIsLeft ==
                preferredLeft
            ) {
                score +=
                    620
            }

            if (
                preferBelow &&
                !candidate
                    .placeAbove
            ) {
                score +=
                    240
            }

            if (
                expandedRainbow != null &&
                !candidate.placeAbove
            ) {
                score +=
                    180
            }

            val verticalGap =
                if (
                    candidate.placeAbove
                ) {
                    kotlin.math.abs(
                        pet.y -
                            rect.bottom
                    )
                } else {
                    kotlin.math.abs(
                        rect.top -
                            (
                                pet.y +
                                    pet.height
                                )
                    )
                }

            score -=
                verticalGap *
                    3

            return score
        }

        val best =
            candidates
                .map {
                    it.copy(
                        score =
                            scoreCandidate(
                                it
                            )
                    )
                }
                .maxByOrNull {
                    it.score
                }
                ?: candidates.first()

        val finalX =
            best.x
                .coerceIn(
                    safeLeft,
                    (
                        safeRight -
                            bubbleWidth
                        )
                        .coerceAtLeast(
                            safeLeft
                        )
                )

        val finalY =
            best.y
                .coerceIn(
                    safeTop,
                    (
                        safeBottom -
                            bubbleHeight
                        )
                        .coerceAtLeast(
                            safeTop
                        )
                )

        return best.copy(
            x = finalX,
            y = finalY
        )
    }

    private fun syncSpeechBubblePosition(
        force: Boolean = false
    ) {
        val bubble =
            speechBubble ?: return

        val params =
            speechBubbleParams ?: return

        val pet =
            petParams ?: return

        val screenW =
            resources
                .displayMetrics
                .widthPixels

        val screenH =
            resources
                .displayMetrics
                .heightPixels

        val rainbowRect =
            rainbowParams
                ?.let {
                    Rect(
                        it.x,
                        it.y,
                        it.x +
                            it.width,
                        it.y +
                            it.height
                    )
                }

        val compactForRainbow =
            rainbowRect != null &&
                bubble.isDecorativeTheme()

        bubble.setCompactMode(
            compactForRainbow
        )

        val bubbleWidth =
            bubble.preferredWidthPx(
                screenW
            )

        bubble.measure(
            View.MeasureSpec
                .makeMeasureSpec(
                    bubbleWidth,
                    View.MeasureSpec.EXACTLY
                ),
            View.MeasureSpec
                .makeMeasureSpec(
                    0,
                    View.MeasureSpec
                        .UNSPECIFIED
                )
        )

        val bubbleHeight =
            bubble.measuredHeight
                .coerceAtLeast(
                    bubble.minimumOverlayHeightPx()
                )

        val placement =
            resolveSpeechBubblePlacement(
                pet = pet,
                bubbleWidth = bubbleWidth,
                bubbleHeight = bubbleHeight,
                screenW = screenW,
                screenH = screenH,
                rainbowRect = rainbowRect,
                preferBelow =
                    speechBubblePreferBelow
            )

        val petCenterX =
            pet.x +
                pet.width / 2

        val tailCenter =
            (petCenterX -
                placement.x)
                .toFloat()
                .coerceIn(
                    dp(34).toFloat(),
                    (
                        bubbleWidth -
                            dp(34)
                        ).toFloat()
                )

        bubble.setTail(
            atTop =
                !placement.placeAbove,
            centerPx =
                tailCenter
        )

        val moveThreshold =
            dp(2)

        val positionChanged =
            kotlin.math.abs(
                params.x -
                    placement.x
            ) >=
                moveThreshold ||
                kotlin.math.abs(
                    params.y -
                        placement.y
                ) >=
                moveThreshold

        val widthChanged =
            params.width !=
                bubbleWidth

        if (
            !force &&
            !positionChanged &&
            !widthChanged
        ) {
            return
        }

        params.width =
            bubbleWidth
        params.x =
            placement.x
        params.y =
            placement.y

        runCatching {
            windowManager
                .updateViewLayout(
                    bubble,
                    params
                )
        }
    }


    private fun hideSpeechBubble(
        immediate: Boolean
    ) {
        speechBubbleHideRunnable
            ?.let {
                handler.removeCallbacks(
                    it
                )
            }

        speechBubbleHideRunnable =
            null

        val bubble =
            speechBubble ?: return

        speechBubble = null
        speechBubbleParams = null
        speechBubblePriority = 0
        speechBubblePreferBelow = false

        if (immediate) {
            runCatching {
                windowManager
                    .removeView(
                        bubble
                    )
            }
            return
        }

        bubble.animate()
            .alpha(0f)
            .scaleX(0.96f)
            .scaleY(0.96f)
            .setDuration(140L)
            .withEndAction {
                runCatching {
                    windowManager
                        .removeView(
                            bubble
                        )
                }
            }
            .start()
    }

    private fun maybeShowDailyGreeting() {
        val today =
            LocalDate.now()
                .toString()

        if (
            prefs.getString(
                "pet_last_greeting_date",
                ""
            ) == today
        ) {
            return
        }

        prefs.edit()
            .putString(
                "pet_last_greeting_date",
                today
            )
            .apply()

        handler.postDelayed({
            scope.launch {
                val stats =
                    todayTaskStats()

                val pendingToday =
                    stats.first

                val hour =
                    LocalTime.now().hour

                val mood =
                    currentMoodForToday()

                val greeting =
                    when {
                        hour < 6 ->
                            "这么晚还醒着呀～我陪你把最后一点事情收好。"

                        hour < 11 ->
                            if (
                                pendingToday > 0
                            ) {
                                "早呀～今天有 " +
                                    pendingToday +
                                    " 件事，我们慢慢来。"
                            } else {
                                "早呀～今天先从一件小事开始吧。"
                            }

                        hour < 18 ->
                            if (
                                pendingToday > 0
                            ) {
                                "下午好～今天还有 " +
                                    pendingToday +
                                    " 件事在盒子里。"
                            } else {
                                "下午好～今天的节奏看起来很轻松。"
                            }

                        hour < 23 ->
                            if (
                                pendingToday > 0
                            ) {
                                "晚上好～今天还剩 " +
                                    pendingToday +
                                    " 件事，别着急。"
                            } else {
                                "晚上好～今天的事情已经很轻啦。"
                            }

                        else ->
                            "已经很晚啦，我会安静一点陪着你。"
                    }

                val moodSuffix =
                    moodSentence(mood)

                showPetBubble(
                    title =
                        petName() + " · 今日见面",
                    message =
                        greeting +
                            if (
                                moodSuffix
                                    .isBlank()
                            ) {
                                ""
                            } else {
                                "\n" +
                                    moodSuffix
                            },
                    tone =
                        PetSpeechBubbleView
                            .Tone.MOOD,
                    priority =
                        PRIORITY_AMBIENT,
                    durationMs = 5_200L
                )
            }
        }, 850L)
    }

    private fun scheduleContextBubble() {
        contextBubbleRunnable
            ?.let {
                handler.removeCallbacks(
                    it
                )
            }

        val delay =
            kotlin.random.Random
                .nextLong(
                    15L * 60L * 1000L,
                    28L * 60L * 1000L
                )

        val runnable =
            Runnable {
                maybeShowDailyGreeting()

                if (
                    panelView == null &&
                    speechBubble == null &&
                    petView
                        ?.canDoAmbientAction() ==
                        true
                ) {
                    showContextBubble()
                } else {
                    scheduleContextBubble()
                }
            }

        contextBubbleRunnable =
            runnable

        handler.postDelayed(
            runnable,
            delay
        )
    }

    private fun showContextBubble() {
        val now =
            System.currentTimeMillis()

        if (
            now -
                lastAmbientBubbleAt <
            12L * 60L * 1000L
        ) {
            scheduleContextBubble()
            return
        }

        lastAmbientBubbleAt = now

        scope.launch {
            val stats =
                todayTaskStats()

            val pending =
                stats.first
            val completed =
                stats.second

            val hour =
                LocalTime.now().hour

            val mood =
                currentMoodForToday()

            val message =
                when {
                    hour >= 23 ||
                        hour < 6 ->
                        "夜深啦，我把动作放轻一点。忙完这件就休息会儿吧～"

                    pending > 0 ->
                        "今天还有 " +
                            pending +
                            " 件事。先挑最小的一件做，也算往前走。"

                    completed > 0 ->
                        "今天已经完成 " +
                            completed +
                            " 件事啦，剩下的时间可以松一点。"

                    mood == "tired" ->
                        "今天有点累的话，就给自己留一点余量。"

                    mood == "sad" ->
                        "今天不用急着变好，我就在旁边待着。"

                    mood == "quiet" ->
                        "收到～今天我安静一点，不总来打扰你。"

                    else ->
                        "我就在这里待着，需要的时候点点我就好～"
                }

            petView?.playBlink()

            showPetBubble(
                title =
                    petName() + " · 陪伴",
                message = message,
                tone =
                    PetSpeechBubbleView
                        .Tone.MOOD,
                priority =
                    PRIORITY_AMBIENT,
                durationMs = 4_800L
            )

            scheduleContextBubble()
        }
    }

    private fun showTaskCompletionCelebration(
        title: String
    ) {
        scope.launch {
            val stats =
                todayTaskStats()

            val pending =
                stats.first

            animateSuccess()

            val message =
                if (pending == 0) {
                    "「" +
                        title +
                        "」完成！今天的事情都收好啦 ✨"
                } else {
                    "「" +
                        title +
                        "」完成啦！今天还剩 " +
                        pending +
                        " 件～"
                }

            showPetBubble(
                title =
                    if (pending == 0) {
                        petName() + " · 今日清单完成 ✨"
                    } else {
                        petName() + " · 又完成一件"
                    },
                message = message,
                tone =
                    PetSpeechBubbleView
                        .Tone.SUCCESS,
                priority =
                    PRIORITY_TASK,
                durationMs =
                    if (pending == 0) {
                        5_400L
                    } else {
                        4_200L
                    }
            )
        }
    }

    private fun showMoodReaction(
        mood: String
    ) {
        val message =
            moodSentence(mood)
                .ifBlank {
                    "今天就按你的节奏来，我陪着你。"
                }

        when (mood) {
            "happy" ->
                petView
                    ?.playTailWag()

            "calm",
            "random" ->
                petView
                    ?.playWave()

            else ->
                petView
                    ?.playBlink()
        }

        showPetBubble(
            title =
                petName() + " · 收到今天的心情",
            message = message,
            tone =
                PetSpeechBubbleView
                    .Tone.MOOD,
            priority =
                PRIORITY_INTERACTION,
            durationMs = 4_500L
        )
    }

    private fun currentMoodForToday():
        String {
        val moodDate =
            prefs.getString(
                "journal_mood_date",
                ""
            )

        return if (
            moodDate ==
                LocalDate.now()
                    .toString()
        ) {
            prefs.getString(
                "journal_mood",
                "calm"
            ) ?: "calm"
        } else {
            "calm"
        }
    }

    private fun moodSentence(
        mood: String
    ): String {
        return when (mood) {
            "happy" ->
                "今天心情不错呀～那就把这点轻快留住。"

            "tired" ->
                "今天有点累，别把电量一次用完，慢一点也可以。"

            "sad" ->
                "今天难过也没关系，我安静陪你一会儿。"

            "quiet" ->
                "收到，今天我少一点打扰，多一点陪伴。"

            "random" ->
                "今天交给一点随机的小惊喜，也挺好。"

            else ->
                "慢慢来就很好，不需要一下子做完所有事。"
        }
    }

    private suspend fun todayTaskStats():
        Pair<Int, Int> {
        return withContext(
            Dispatchers.IO
        ) {
            val zone =
                ZoneId.systemDefault()

            val date =
                LocalDate.now()

            val start =
                date.atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli()

            val end =
                date.plusDays(1)
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli() -
                    1L

            val tasks =
                AppDatabase.get(
                    this@PetOverlayService
                )
                    .taskDao()
                    .getAllOnce()

            val pending =
                tasks.count {
                    !it.completed &&
                        it.dueAt !=
                        null &&
                        it.dueAt in
                        start..end
                }

            val completed =
                tasks.count {
                    it.completedAt !=
                        null &&
                        it.completedAt in
                        start..end
                }

            Pair(
                pending,
                completed
            )
        }
    }

    private fun addPanel(view: View, focusable: Boolean) {
        val pet = petParams ?: return
        val screenW = resources.displayMetrics.widthPixels
        val panelWidth = dp(270)
        val x = if (pet.x > screenW / 2) {
            (pet.x - panelWidth - dp(8)).coerceAtLeast(dp(8))
        } else {
            (pet.x + pet.width + dp(8)).coerceAtMost(screenW - panelWidth - dp(8))
        }

        val flags = if (focusable) {
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }

        val params = WindowManager.LayoutParams(
            panelWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            y = (pet.y - dp(24)).coerceAtLeast(dp(48))
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }

        runCatching {
            windowManager.addView(view, params)
            panelView = view
            panelParams = params
        }
    }

    private fun closePanel() {
        val view = panelView ?: return
        runCatching { windowManager.removeView(view) }
        panelView = null
        panelParams = null
    }

    private fun basePanel(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = roundedBackground(Color.argb(248, 255, 252, 247), 22)
            elevation = dp(8).toFloat()
        }
    }

    private fun actionButton(text: String): Button {
        return Button(this).apply {
            this.text = text
            isAllCaps = false
            textSize = 13f
            setTextColor(Color.rgb(73, 57, 44))
            background = roundedBackground(Color.rgb(255, 235, 199), 14)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)
            )
            lp.topMargin = dp(7)
            layoutParams = lp
        }
    }

    private fun label(
        text: String,
        size: Float,
        bold: Boolean,
        color: Int
    ): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
    }

    private fun spacer(heightDp: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, dp(heightDp))
        }
    }

    private fun roundedBackground(color: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private enum class InputMode {
        NOTE,
        TASK
    }

    companion object {
        const val ACTION_SHOW = "com.shiguangbox.app.pet.SHOW"
        const val ACTION_STOP = "com.shiguangbox.app.pet.STOP"
        const val ACTION_REMINDER =
            "com.shiguangbox.app.pet.REMINDER"
        const val ACTION_PRE_REMINDER =
            "com.shiguangbox.app.pet.PRE_REMINDER"
        const val ACTION_TASK_COMPLETED =
            "com.shiguangbox.app.pet.TASK_COMPLETED"
        const val ACTION_MOOD_CHANGED =
            "com.shiguangbox.app.pet.MOOD_CHANGED"
        const val ACTION_TEST_BUBBLE =
            "com.shiguangbox.app.pet.TEST_BUBBLE"
        const val ACTION_TEST_WAVE = "com.shiguangbox.app.pet.TEST_WAVE"
        const val ACTION_TEST_TAIL = "com.shiguangbox.app.pet.TEST_TAIL"
        const val ACTION_TEST_PETTING =
            "com.shiguangbox.app.pet.TEST_PETTING"
        const val ACTION_REFRESH_SETTINGS =
            "com.shiguangbox.app.pet.REFRESH_SETTINGS"
        const val ACTION_TEST_BLINK = "com.shiguangbox.app.pet.TEST_BLINK"
        const val ACTION_TEST_TIRED = "com.shiguangbox.app.pet.TEST_TIRED"
        const val ACTION_TEST_SLEEP = "com.shiguangbox.app.pet.TEST_SLEEP"
        const val ACTION_TEST_WAKE = "com.shiguangbox.app.pet.TEST_WAKE"
        const val EXTRA_TASK_ID = "pet_task_id"
        const val EXTRA_TASK_TITLE =
            "pet_task_title"
        const val EXTRA_MOOD_ID =
            "pet_mood_id"

        private const val PRIORITY_AMBIENT = 10
        private const val PRIORITY_INTERACTION = 30
        private const val PRIORITY_TASK = 60
        private const val PRIORITY_REMINDER = 100
        private const val NOTIFICATION_ID = 9001
    }
}
