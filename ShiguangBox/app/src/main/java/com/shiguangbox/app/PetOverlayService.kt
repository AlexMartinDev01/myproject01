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
import java.time.ZoneId

class PetOverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: android.content.SharedPreferences

    private var petView: PetRigView? = null
    private var petParams: WindowManager.LayoutParams? = null
    private var panelView: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var reminderTaskId: Long = 0L

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
            ACTION_REMINDER -> {
                ensurePetView()
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
                val title = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "待办"
                showReminder(taskId, title)
            }
            ACTION_TEST_WAVE -> {
                ensurePetView()
                petView?.playWave()
            }
            ACTION_TEST_TAIL -> {
                ensurePetView()
                petView?.playTailWag()
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
        handler.removeCallbacksAndMessages(null)
        closePanel()
        petView?.release()
        petView?.let {
            runCatching { windowManager.removeView(it) }
        }
        petView = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
                    description = "保持橘团悬浮桌宠运行"
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
            .setContentTitle("橘团正在陪着你")
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

        val size = dp(112)
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
            contentDescription = "橘团桌宠"
        }

        image.setOnTouchListener(PetTouchListener(image, params))

        runCatching {
            windowManager.addView(image, params)
            petView = image
            petParams = params
            startIdleAnimation()
        }
    }

    private inner class PetTouchListener(
        private val view: View,
        private val params: WindowManager.LayoutParams
    ) : View.OnTouchListener {

        private val touchSlop = ViewConfiguration.get(this@PetOverlayService).scaledTouchSlop
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        override fun onTouch(v: View?, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                    petView?.onUserInteraction()
                    petView?.pauseMotion()
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY

                    if (!dragging &&
                        (kotlin.math.abs(dx) > touchSlop || kotlin.math.abs(dy) > touchSlop)
                    ) {
                        dragging = true
                        closePanel()
                        petView?.startDragging()
                    }

                    if (dragging) {
                        val screenW = resources.displayMetrics.widthPixels
                        val screenH = resources.displayMetrics.heightPixels
                        params.x = (startX + dx.toInt()).coerceIn(-dp(24), screenW - dp(88))
                        params.y = (startY + dy.toInt()).coerceIn(dp(24), screenH - dp(140))
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        snapToEdge(params)
                        petView?.endDragging()
                        playLandingBounce()
                    } else if (event.actionMasked == MotionEvent.ACTION_UP) {
                        toggleQuickPanel()
                        petView?.resumeMotion()
                    } else {
                        petView?.resumeMotion()
                    }
                    return true
                }
            }
            return false
        }
    }

    private fun snapToEdge(params: WindowManager.LayoutParams) {
        val screenW = resources.displayMetrics.widthPixels
        val petWidth = params.width
        val left = -dp(10)
        val right = screenW - petWidth + dp(10)
        val target = if (params.x + petWidth / 2 < screenW / 2) left else right
        val start = params.x

        ValueAnimator.ofInt(start, target).apply {
            duration = 180
            addUpdateListener { animator ->
                params.x = animator.animatedValue as Int
                petView?.let { runCatching { windowManager.updateViewLayout(it, params) } }
            }
            start()
        }

        prefs.edit()
            .putInt("pet_x", target)
            .putInt("pet_y", params.y)
            .apply()
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
        closePanel()
        petView?.playWave()

        val root = basePanel()
        val title = label("橘团 ☀️", 18f, true, Color.rgb(78, 58, 46))
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
        closePanel()

        val root = basePanel()
        root.addView(
            label(
                if (mode == InputMode.NOTE) "橘团帮你记一下" else "快速加待办",
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

    private fun showReminder(taskId: Long, title: String) {
        reminderTaskId = taskId
        closePanel()
        animateReminder()

        val root = basePanel()
        root.addView(label("橘团提醒你 🔔", 17f, true, Color.rgb(78, 58, 46)))
        root.addView(spacer(6))
        root.addView(
            label(
                "「" + title + "」到时间啦～",
                14f,
                false,
                Color.rgb(73, 57, 44)
            )
        )

        val done = actionButton("✓ 完成")
        val snooze = actionButton("延后 10 分钟")
        root.addView(done)
        root.addView(snooze)

        done.setOnClickListener {
            completeReminder(taskId)
        }

        snooze.setOnClickListener {
            snoozeReminder(taskId)
        }

        addPanel(root, focusable = false)
    }

    private fun completeReminder(taskId: Long) {
        closePanel()
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
            showSuccess("太棒啦，又完成一件事！ ✨")
        }
    }

    private fun snoozeReminder(taskId: Long) {
        closePanel()
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
            showTransientBubble("好，10 分钟后我再来找你～")
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

    private fun showSuccess(message: String) {
        animateSuccess()
        showTransientBubble(message)
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

    private fun showTransientBubble(message: String) {
        closePanel()
        val root = basePanel()
        root.addView(label(message, 14f, true, Color.rgb(73, 57, 44)))
        addPanel(root, focusable = false)
        handler.postDelayed({ closePanel() }, 2300)
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
        const val ACTION_REMINDER = "com.shiguangbox.app.pet.REMINDER"
        const val ACTION_TEST_WAVE = "com.shiguangbox.app.pet.TEST_WAVE"
        const val ACTION_TEST_TAIL = "com.shiguangbox.app.pet.TEST_TAIL"
        const val ACTION_TEST_BLINK = "com.shiguangbox.app.pet.TEST_BLINK"
        const val ACTION_TEST_TIRED = "com.shiguangbox.app.pet.TEST_TIRED"
        const val ACTION_TEST_SLEEP = "com.shiguangbox.app.pet.TEST_SLEEP"
        const val ACTION_TEST_WAKE = "com.shiguangbox.app.pet.TEST_WAKE"
        const val EXTRA_TASK_ID = "pet_task_id"
        const val EXTRA_TASK_TITLE = "pet_task_title"
        private const val NOTIFICATION_ID = 9001
    }
}
