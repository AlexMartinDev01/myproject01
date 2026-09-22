package com.soda.livewidget;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Locale;

public class OverlayService extends Service {

    private static final String CHANNEL_ID = "six_meet_lock_v1";
    private static final int NOTIFICATION_ID = 1800;

    private WindowManager windowManager;
    private View overlayView;
    private WindowManager.LayoutParams params;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView labelText;
    private TextView timeText;
    private boolean lastLiveState = false;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            boolean liveNow = updateCountdown();

            // 系统计时器会自己按秒走，只在 18:00 状态切换时重建通知。
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

        createNotificationChannel();
        lastLiveState = isLiveNow();
        startForeground(NOTIFICATION_ID, buildNotification());
        showOverlay();
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
                .setCategory(Notification.CATEGORY_EVENT)
                .setVisibility(Notification.VISIBILITY_PUBLIC);

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
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("在锁屏和通知栏显示距离 18:00 开播的倒计时");
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
        params.x = 32;
        params.y = 180;

        labelText = overlayView.findViewById(R.id.overlayLabel);
        timeText = overlayView.findViewById(R.id.overlayTime);
        TextView closeButton = overlayView.findViewById(R.id.overlayClose);
        View dragHandle = overlayView.findViewById(R.id.dragHandle);

        closeButton.setOnClickListener(v -> stopSelf());

        dragHandle.setOnTouchListener(new View.OnTouchListener() {
            private int startX;
            private int startY;
            private float startRawX;
            private float startRawY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = params.x;
                        startY = params.y;
                        startRawX = event.getRawX();
                        startRawY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        params.x = startX + (int) (event.getRawX() - startRawX);
                        params.y = startY + (int) (event.getRawY() - startRawY);

                        if (windowManager != null && overlayView != null) {
                            windowManager.updateViewLayout(overlayView, params);
                        }
                        return true;

                    default:
                        return false;
                }
            }
        });

        windowManager.addView(overlayView, params);
        updateCountdown();
    }

    private boolean updateCountdown() {
        if (labelText == null || timeText == null) {
            return isLiveNow();
        }

        Calendar now = Calendar.getInstance();
        Calendar target = getTodayTarget();
        long diffMs = target.getTimeInMillis() - now.getTimeInMillis();

        if (diffMs > 0) {
            long totalSeconds = diffMs / 1000;
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
            return false;
        } else {
            labelText.setText("苏打水 · 今天六点见");
            timeText.setText("正在直播 ✦");
            return true;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(ticker);

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
