package com.shiguangbox.pet;

import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Random;

public class PetOverlayService extends Service {
    public static final String ACTION_START = "com.shiguangbox.pet.START";
    public static final String ACTION_WAVE = "com.shiguangbox.pet.WAVE";
    public static final String ACTION_SUCCESS = "com.shiguangbox.pet.SUCCESS";
    public static final String ACTION_REMINDER = "com.shiguangbox.pet.REMINDER";
    public static final String ACTION_SLEEP = "com.shiguangbox.pet.SLEEP";
    public static final String ACTION_WAKE = "com.shiguangbox.pet.WAKE";
    public static final String ACTION_STOP = "com.shiguangbox.pet.STOP";

    private static final String SERVICE_CHANNEL = "shiguang_pet_service";
    private static final String REMINDER_CHANNEL = "shiguang_pet_reminder";
    private static final int FOREGROUND_ID = 1001;

    private enum State {
        IDLE(0), BLINK(10), TAIL_WAG(12), WAVE(15), TIRED(30), SLEEP(40),
        DRAGGING(50), SUCCESS(60), REMINDER(100);
        final int priority;
        State(int priority) { this.priority = priority; }
    }

    private WindowManager windowManager;
    private FrameLayout petRoot;
    private ImageView petImage;
    private WindowManager.LayoutParams petParams;
    private View panelView;
    private WindowManager.LayoutParams panelParams;
    private SharedPreferences prefs;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private State state = State.IDLE;
    private int animationToken = 0;
    private boolean petAdded = false;
    private boolean panelAdded = false;

    private float downRawX, downRawY;
    private int downX, downY;
    private boolean dragging;

    private final Runnable randomActionRunnable = new Runnable() {
        @Override public void run() {
            if (state == State.IDLE && petAdded) {
                int r = random.nextInt(100);
                if (r < 48) playBlink();
                else if (r < 82) playTailWag();
                else playWave(false);
            }
            scheduleRandomAction();
        }
    };

    private final Runnable sleepRunnable = () -> {
        if (state == State.IDLE && petAdded) playTiredThenSleep();
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("shiguang_prefs", MODE_PRIVATE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createChannels();
        startForeground(FOREGROUND_ID, buildServiceNotification());
        if (Settings.canDrawOverlays(this)) showPet();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (Settings.canDrawOverlays(this) && !petAdded) showPet();
        if (action == null) action = ACTION_START;

        switch (action) {
            case ACTION_WAVE -> playWave(true);
            case ACTION_SUCCESS -> playSuccess();
            case ACTION_REMINDER -> {
                String title = intent == null ? null : intent.getStringExtra("title");
                playReminder(title == null ? "到提醒时间啦！" : title);
            }
            case ACTION_SLEEP -> playTiredThenSleep();
            case ACTION_WAKE -> wakeUp();
            default -> {
                if (petAdded && state != State.SLEEP) enterIdle();
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void showPet() {
        if (petAdded || !Settings.canDrawOverlays(this)) return;

        petRoot = new FrameLayout(this);
        petRoot.setClipChildren(false);
        petRoot.setClipToPadding(false);

        petImage = new ImageView(this);
        setFrame(PetAssets.IDLE);
        petImage.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        petImage.setAdjustViewBounds(true);
        petRoot.addView(petImage, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int size = dp(220);
        petParams = new WindowManager.LayoutParams(
                size, size,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        petParams.gravity = Gravity.TOP | Gravity.START;
        petParams.x = prefs.getInt("pet_x", dp(18));
        petParams.y = prefs.getInt("pet_y", dp(180));

        petRoot.setOnTouchListener(this::handleTouch);
        windowManager.addView(petRoot, petParams);
        petAdded = true;
        prefs.edit().putBoolean("pet_enabled", true).apply();
        enterIdle();
        scheduleRandomAction();
        resetSleepTimer();
    }

    private boolean handleTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                downX = petParams.x;
                downY = petParams.y;
                dragging = false;
                hidePanel();
                resetSleepTimer();
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - downRawX;
                float dy = event.getRawY() - downRawY;
                if (!dragging && Math.hypot(dx, dy) > dp(8)) {
                    dragging = true;
                    forceState(State.DRAGGING);
                    cancelVisualAnimation();
                    setFrame(PetAssets.WAKE);
                    petImage.setRotation(-4f);
                    petImage.setScaleX(1.05f);
                    petImage.setScaleY(1.05f);
                }
                if (dragging) {
                    petParams.x = downX + Math.round(dx);
                    petParams.y = downY + Math.round(dy);
                    clampPosition();
                    safeUpdatePet();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    petImage.setRotation(0f);
                    petImage.setScaleX(1f);
                    petImage.setScaleY(1f);
                    snapToEdge();
                    handler.postDelayed(this::enterIdle, 260);
                } else {
                    if (state == State.SLEEP) wakeUp();
                    else {
                        playWave(true);
                        showPanel("橘团 ☀️", "今天也要好好照顾自己～");
                    }
                }
                resetSleepTimer();
                return true;
        }
        return false;
    }

    private void clampPosition() {
        DisplayMetrics dm = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(dm);
        int size = dp(220);
        petParams.x = Math.max(-dp(30), Math.min(petParams.x, dm.widthPixels - size + dp(30)));
        petParams.y = Math.max(0, Math.min(petParams.y, dm.heightPixels - size - dp(20)));
    }

    private void snapToEdge() {
        DisplayMetrics dm = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(dm);
        int size = dp(220);
        int from = petParams.x;
        int target = (from + size / 2 < dm.widthPixels / 2) ? 0 : dm.widthPixels - size;
        ValueAnimator animator = ValueAnimator.ofInt(from, target);
        animator.setDuration(220L);
        animator.addUpdateListener(a -> {
            petParams.x = (Integer) a.getAnimatedValue();
            safeUpdatePet();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                prefs.edit().putInt("pet_x", petParams.x).putInt("pet_y", petParams.y).apply();
            }
        });
        animator.start();
    }

    private void enterIdle() {
        if (!petAdded) return;
        animationToken++;
        state = State.IDLE;
        cancelVisualAnimation();
        setFrame(PetAssets.IDLE);
        petImage.setAlpha(1f);
        petImage.setRotation(0f);
        petImage.setScaleX(1f);
        petImage.setScaleY(1f);
        startBreathing(animationToken);
        resetSleepTimer();
    }

    private void startBreathing(int token) {
        if (!petAdded || state != State.IDLE || token != animationToken) return;
        petImage.animate().scaleX(1.012f).scaleY(1.018f).translationY(-dp(1)).setDuration(1050L).withEndAction(() -> {
            if (state != State.IDLE || token != animationToken) return;
            petImage.animate().scaleX(1f).scaleY(1f).translationY(0f).setDuration(1050L).withEndAction(() -> startBreathing(token)).start();
        }).start();
    }

    private void playBlink() {
        if (!beginState(State.BLINK, false)) return;
        final int token = animationToken;
        setFrame(PetAssets.IDLE);
        petImage.animate().scaleY(0.94f).setDuration(85L).withEndAction(() -> {
            if (token != animationToken) return;
            petImage.animate().scaleY(1f).setDuration(100L).withEndAction(() -> {
                if (token == animationToken) enterIdle();
            }).start();
        }).start();
    }

    private void playTailWag() {
        if (!beginState(State.TAIL_WAG, false)) return;
        int[] frames = {
                PetAssets.IDLE, PetAssets.HAPPY, PetAssets.WAVE_3,
                PetAssets.HAPPY, PetAssets.IDLE, PetAssets.WAVE_3,
                PetAssets.HAPPY, PetAssets.IDLE
        };
        playFrames(frames, 135L, 0, animationToken, this::enterIdle);
    }

    private void playWave(boolean force) {
        if (!beginState(State.WAVE, force)) return;
        int[] frames = {
                PetAssets.WAVE_1, PetAssets.WAVE_2, PetAssets.WAVE_3,
                PetAssets.WAVE_2, PetAssets.WAVE_3, PetAssets.WAVE_2,
                PetAssets.WAVE_1
        };
        playFrames(frames, 175L, 0, animationToken, this::enterIdle);
    }

    private void playSuccess() {
        if (!beginState(State.SUCCESS, true)) return;
        final int token = animationToken;
        setFrame(PetAssets.SUCCESS);
        petImage.setScaleX(0.94f);
        petImage.setScaleY(0.94f);
        petImage.setTranslationY(dp(10));
        petImage.animate().scaleX(1.08f).scaleY(1.08f).translationY(-dp(12)).rotation(3f).setDuration(320L).withEndAction(() -> {
            if (token != animationToken) return;
            petImage.animate().scaleX(1f).scaleY(1f).translationY(0f).rotation(0f).setDuration(360L).start();
        }).start();
        handler.postDelayed(() -> { if (token == animationToken) enterIdle(); }, 1850L);
    }

    private void playReminder(String title) {
        if (!beginState(State.REMINDER, true)) return;
        final int token = animationToken;
        setFrame(PetAssets.REMINDER);
        petImage.animate().rotation(-5f).setDuration(140L).withEndAction(() -> {
            if (token != animationToken) return;
            petImage.animate().rotation(5f).setDuration(140L).withEndAction(() -> {
                if (token == animationToken) petImage.animate().rotation(0f).setDuration(120L).start();
            }).start();
        }).start();
        vibrateReminder();
        postReminderNotification(title);
        showPanel("橘团提醒你 🔔", title);
        handler.postDelayed(() -> {
            if (token == animationToken) {
                hidePanel();
                enterIdle();
            }
        }, 5200L);
    }

    private void playTiredThenSleep() {
        if (!beginState(State.TIRED, true)) return;
        final int token = animationToken;
        setFrame(PetAssets.TIRED);
        petImage.setRotation(0f);
        petImage.animate().translationY(dp(8)).scaleX(0.98f).scaleY(0.98f).setDuration(900L).start();
        handler.postDelayed(() -> {
            if (token == animationToken && state == State.TIRED) enterSleep();
        }, 2100L);
    }

    private void enterSleep() {
        animationToken++;
        state = State.SLEEP;
        cancelVisualAnimation();
        setFrame(PetAssets.SLEEP);
        petImage.setRotation(0f);
        petImage.setTranslationY(dp(14));
        petImage.setScaleX(1f);
        petImage.setScaleY(1f);
        startSleepBreath(animationToken);
        hidePanel();
    }

    private void startSleepBreath(int token) {
        if (state != State.SLEEP || token != animationToken) return;
        petImage.animate().scaleX(1.006f).scaleY(1.012f).setDuration(1450L).withEndAction(() -> {
            if (state != State.SLEEP || token != animationToken) return;
            petImage.animate().scaleX(1f).scaleY(1f).setDuration(1450L).withEndAction(() -> startSleepBreath(token)).start();
        }).start();
    }

    private void wakeUp() {
        forceState(State.WAVE);
        final int token = animationToken;
        cancelVisualAnimation();
        setFrame(PetAssets.WAKE);
        petImage.setTranslationY(dp(8));
        petImage.setScaleX(0.95f);
        petImage.setScaleY(0.95f);
        petImage.animate().translationY(0f).scaleX(1.06f).scaleY(1.06f).setDuration(420L).start();
        handler.postDelayed(() -> { if (token == animationToken) enterIdle(); }, 900L);
    }

    private boolean beginState(State target, boolean force) {
        if (!petAdded) return false;
        if (!force && target.priority < state.priority) return false;
        animationToken++;
        state = target;
        cancelVisualAnimation();
        petImage.setAlpha(1f);
        petImage.setRotation(0f);
        petImage.setScaleX(1f);
        petImage.setScaleY(1f);
        petImage.setTranslationX(0f);
        petImage.setTranslationY(0f);
        return true;
    }

    private void forceState(State target) {
        animationToken++;
        state = target;
    }

    private void playFrames(int[] frames, long frameMs, int index, int token, Runnable onDone) {
        if (token != animationToken || !petAdded) return;
        if (index >= frames.length) {
            if (onDone != null) onDone.run();
            return;
        }
        setFrame(frames[index]);
        float pulse = (index % 2 == 0) ? 1.035f : 1f;
        petImage.setScaleX(pulse);
        petImage.setScaleY(pulse);
        handler.postDelayed(() -> playFrames(frames, frameMs, index + 1, token, onDone), frameMs);
    }

    private void scheduleRandomAction() {
        handler.removeCallbacks(randomActionRunnable);
        handler.postDelayed(randomActionRunnable, 3800L + random.nextInt(6200));
    }

    private void resetSleepTimer() {
        handler.removeCallbacks(sleepRunnable);
        if (state != State.SLEEP) handler.postDelayed(sleepRunnable, 90_000L);
    }

    private void cancelVisualAnimation() {
        if (petImage != null) petImage.animate().cancel();
    }

    private void showPanel(String heading, String message) {
        hidePanel();
        if (!petAdded) return;

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF7FFFFFF);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), Color.rgb(241, 211, 180));
        panel.setBackground(bg);
        panel.setElevation(dp(8));

        TextView h = new TextView(this);
        h.setText(heading);
        h.setTextSize(16);
        h.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        h.setTextColor(Color.rgb(92, 59, 38));
        panel.addView(h);

        TextView m = new TextView(this);
        m.setText(message);
        m.setTextSize(13);
        m.setTextColor(Color.rgb(124, 91, 68));
        m.setPadding(0, dp(4), 0, dp(8));
        panel.addView(m);

        Button open = miniButton("打开拾光盒");
        open.setOnClickListener(v -> {
            Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            hidePanel();
        });
        panel.addView(open);

        Button sleep = miniButton("我要专注 · 橘团睡会儿");
        sleep.setOnClickListener(v -> playTiredThenSleep());
        panel.addView(sleep);

        panelView = panel;
        panelParams = new WindowManager.LayoutParams(
                dp(225), WindowManager.LayoutParams.WRAP_CONTENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        panelParams.gravity = Gravity.TOP | Gravity.START;

        DisplayMetrics dm = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(dm);
        int petSize = dp(220);
        boolean petOnRight = petParams.x + petSize / 2 > dm.widthPixels / 2;
        panelParams.x = petOnRight ? Math.max(0, petParams.x - dp(215)) : Math.min(dm.widthPixels - dp(225), petParams.x + dp(160));
        panelParams.y = Math.max(dp(20), petParams.y + dp(25));

        try {
            windowManager.addView(panelView, panelParams);
            panelAdded = true;
        } catch (Exception ignored) {}
    }

    private Button miniButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setTextColor(Color.rgb(103, 70, 48));
        b.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(255, 239, 219));
        bg.setCornerRadius(dp(12));
        b.setBackground(bg);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        p.setMargins(0, dp(4), 0, 0);
        b.setLayoutParams(p);
        return b;
    }

    private void hidePanel() {
        if (panelAdded && panelView != null) {
            try { windowManager.removeView(panelView); } catch (Exception ignored) {}
        }
        panelAdded = false;
        panelView = null;
    }

    private void safeUpdatePet() {
        if (!petAdded) return;
        try { windowManager.updateViewLayout(petRoot, petParams); } catch (Exception ignored) {}
    }

    private void vibrateReminder() {
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null) return;
        long[] pattern = {0, 90, 70, 140};
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
        else vibrator.vibrate(pattern, -1);
    }

    private void createChannels() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel service = new NotificationChannel(SERVICE_CHANNEL, "橘团桌宠", NotificationManager.IMPORTANCE_LOW);
        service.setDescription("保持橘团悬浮桌宠运行");
        nm.createNotificationChannel(service);
        NotificationChannel reminder = new NotificationChannel(REMINDER_CHANNEL, "拾光盒提醒", NotificationManager.IMPORTANCE_HIGH);
        reminder.setDescription("待办和计划提醒");
        nm.createNotificationChannel(reminder);
    }

    private Notification buildServiceNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, SERVICE_CHANNEL)
                .setSmallIcon(R.drawable.ic_pet)
                .setContentTitle("橘团正在陪着你")
                .setContentText("点击进入拾光盒")
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void postReminderNotification(String title) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 11, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, REMINDER_CHANNEL)
                .setSmallIcon(R.drawable.ic_pet)
                .setContentTitle("橘团提醒你")
                .setContentText(title)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify((int) (System.currentTimeMillis() & 0x7fffffff), n);
    }

    private void setFrame(int index) {
        if (petImage != null) petImage.setImageBitmap(PetAssets.frame(this, index));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        hidePanel();
        if (petAdded && petRoot != null) {
            try { windowManager.removeView(petRoot); } catch (Exception ignored) {}
        }
        petAdded = false;
        prefs.edit().putBoolean("pet_enabled", false).apply();
        super.onDestroy();
    }
}
