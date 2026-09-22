package com.soda.livewidget;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class OverlayService extends Service {

    private static final String CHANNEL_ID = "six_meet_lock_v2";
    private static final int NOTIFICATION_ID = 1800;
    private static final long LONG_PRESS_MS = 500L;
    private static final long ENCOURAGEMENT_SHOW_MS = 10_000L;

    private WindowManager windowManager;
    private View overlayView;
    private WindowManager.LayoutParams params;

    private View encouragementView;
    private WindowManager.LayoutParams encouragementParams;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Handler dragHandler = new Handler(Looper.getMainLooper());

    private TextView labelText;
    private TextView timeText;
    private boolean lastLiveState = false;

    private SharedPreferences prefs;
    private BroadcastReceiver screenReceiver;
    private Runnable encouragementHideRunnable;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            boolean liveNow = updateCountdown();

            if (liveNow != lastLiveState) {
                lastLiveState = liveNow;
                NotificationManager manager =
                        (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (manager != null) {
                    manager.notify(NOTIFICATION_ID, buildNotification());
                }
            }

            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        prefs = getSharedPreferences("six_meet_prefs", MODE_PRIVATE);

        createNotificationChannel();
        lastLiveState = isLiveNow();
        startForeground(NOTIFICATION_ID, buildNotification());

        showOverlay();
        registerScreenReceiver();
        handler.post(ticker);
    }

    private Calendar getTodayTarget() {
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, 18);
        target.set(Calendar.MINUTE, 0);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);
        return target;
    }

    private boolean isLiveNow() {
        return Calendar.getInstance().getTimeInMillis()
                >= getTodayTarget().getTimeInMillis();
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        Calendar target = getTodayTarget();
        boolean liveNow = Calendar.getInstance().getTimeInMillis()
                >= target.getTimeInMillis();

        builder
                .setContentTitle("六点见")
                .setSmallIcon(R.drawable.ic_stat_sun)
                .setColor(Color.rgb(244, 184, 96))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_HIGH);

        if (!liveNow) {
            builder
                    .setContentText("距离今天 18:00 开播还有")
                    .setSubText("苏打水 · 今天六点见")
                    .setWhen(target.getTimeInMillis())
                    .setShowWhen(true)
                    .setUsesChronometer(true);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setChronometerCountDown(true);
            }
        } else {
            builder
                    .setContentText("正在直播 ✦")
                    .setSubText("苏打水 · 今天六点见")
                    .setShowWhen(false)
                    .setUsesChronometer(false);
        }

        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "六点见 · 锁屏倒计时",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("锁屏亮屏时显示六点见倒计时");
            channel.setSound(null, null);
            channel.enableVibration(false);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void showOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_countdown, null);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = prefs.getInt("overlay_x", 32);
        params.y = prefs.getInt("overlay_y", 180);

        labelText = overlayView.findViewById(R.id.overlayLabel);
        timeText = overlayView.findViewById(R.id.overlayTime);
        TextView closeButton = overlayView.findViewById(R.id.overlayClose);
        View dragHandle = overlayView.findViewById(R.id.dragHandle);

        closeButton.setOnClickListener(v -> stopSelf());
        setupLongPressDrag(dragHandle);

        windowManager.addView(overlayView, params);
        updateCountdown();
    }

    private void setupLongPressDrag(View dragHandle) {
        final int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

        dragHandle.setOnTouchListener(new View.OnTouchListener() {
            private int startX;
            private int startY;
            private float downRawX;
            private float downRawY;
            private boolean dragging = false;

            private final Runnable startDragging = () -> {
                dragging = true;
                dragHandle.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            };

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = params.x;
                        startY = params.y;
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        dragging = false;
                        dragHandler.postDelayed(startDragging, LONG_PRESS_MS);
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downRawX;
                        float dy = event.getRawY() - downRawY;

                        if (!dragging
                                && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                            dragHandler.removeCallbacks(startDragging);
                        }

                        if (dragging) {
                            params.x = startX + (int) dx;
                            params.y = startY + (int) dy;

                            if (windowManager != null && overlayView != null) {
                                windowManager.updateViewLayout(overlayView, params);
                            }
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        dragHandler.removeCallbacks(startDragging);

                        if (dragging) {
                            prefs.edit()
                                    .putInt("overlay_x", params.x)
                                    .putInt("overlay_y", params.y)
                                    .apply();
                        }

                        dragging = false;
                        return true;

                    default:
                        return false;
                }
            }
        });
    }

    private boolean updateCountdown() {
        Calendar now = Calendar.getInstance();
        Calendar target = getTodayTarget();
        long diffMs = target.getTimeInMillis() - now.getTimeInMillis();

        if (diffMs > 0) {
            long totalSeconds = diffMs / 1000;

            if (labelText != null && timeText != null) {
                long hours = totalSeconds / 3600;
                long minutes = (totalSeconds % 3600) / 60;
                long seconds = totalSeconds % 60;

                labelText.setText("距离今天 18:00 开播还有");
                timeText.setText(String.format(
                        Locale.getDefault(),
                        "%02d:%02d:%02d",
                        hours,
                        minutes,
                        seconds
                ));
            }

            maybeShowEncouragement(totalSeconds);
            return false;
        }

        if (labelText != null && timeText != null) {
            labelText.setText("苏打水 · 今天六点见");
            timeText.setText("正在直播 ✦");
        }
        return true;
    }

    private void maybeShowEncouragement(long totalSeconds) {
        int stage = 0;

        if (totalSeconds <= 180 && totalSeconds > 120) {
            stage = 3;
        } else if (totalSeconds <= 120 && totalSeconds > 60) {
            stage = 2;
        } else if (totalSeconds <= 60 && totalSeconds > 0) {
            stage = 1;
        }

        if (stage == 0) return;

        String date = new SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                .format(Calendar.getInstance().getTime());
        String key = "encouragement_" + date + "_" + stage;

        if (prefs.getBoolean(key, false)) return;

        prefs.edit().putBoolean(key, true).apply();
        showEncouragement(stage);
    }

    private void showEncouragement(int stage) {
        hideEncouragement();

        if (windowManager == null) return;

        encouragementView = LayoutInflater.from(this)
                .inflate(R.layout.overlay_encouragement_card, null);

        ImageView imageView = encouragementView.findViewById(R.id.encouragementImage);
        TextView closeButton = encouragementView.findViewById(R.id.encouragementClose);

        if (stage == 3) {
            imageView.setImageResource(R.drawable.encourage_1);
        } else if (stage == 2) {
            imageView.setImageResource(R.drawable.encourage_2);
        } else {
            imageView.setImageResource(R.drawable.encourage_3);
        }

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        encouragementParams = new WindowManager.LayoutParams(
                dpToPx(300),
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        encouragementParams.gravity = Gravity.CENTER;

        closeButton.setOnClickListener(v -> hideEncouragement());

        try {
            windowManager.addView(encouragementView, encouragementParams);
        } catch (Exception ignored) {
            encouragementView = null;
            return;
        }

        encouragementHideRunnable = this::hideEncouragement;
        handler.postDelayed(encouragementHideRunnable, ENCOURAGEMENT_SHOW_MS);
    }

    private void hideEncouragement() {
        if (encouragementHideRunnable != null) {
            handler.removeCallbacks(encouragementHideRunnable);
            encouragementHideRunnable = null;
        }

        if (windowManager != null && encouragementView != null) {
            try {
                windowManager.removeView(encouragementView);
            } catch (Exception ignored) {
            }
        }

        encouragementView = null;
    }

    private void registerScreenReceiver() {
        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    KeyguardManager keyguardManager =
                            (KeyguardManager) getSystemService(KEYGUARD_SERVICE);

                    if (keyguardManager != null && keyguardManager.isKeyguardLocked()) {
                        Intent lockIntent = new Intent(
                                OverlayService.this,
                                LockScreenActivity.class
                        );
                        lockIntent.addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK
                                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        );

                        try {
                            startActivity(lockIntent);
                        } catch (Exception ignored) {
                            // 某些厂商系统会限制锁屏 Activity，锁屏通知作为兜底。
                        }
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, filter);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(ticker);
        dragHandler.removeCallbacksAndMessages(null);
        hideEncouragement();

        if (screenReceiver != null) {
            try {
                unregisterReceiver(screenReceiver);
            } catch (Exception ignored) {
            }
            screenReceiver = null;
        }

        if (windowManager != null && overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {
            }
        }

        overlayView = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
