package com.soda.livewidget;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Locale;

public class LockScreenActivity extends Activity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView timeText;
    private TextView labelText;
    private BroadcastReceiver exitReceiver;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateCountdown();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(false);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }

        setContentView(R.layout.activity_lock_screen);

        timeText = findViewById(R.id.lockTime);
        labelText = findViewById(R.id.lockLabel);
        findViewById(R.id.lockClose).setOnClickListener(v -> finish());

        registerExitReceiver();
        updateCountdown();
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

    private void updateCountdown() {
        long diffMs = getTodayTarget().getTimeInMillis()
                - Calendar.getInstance().getTimeInMillis();

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
        } else {
            labelText.setText("苏打水 · 今天六点见");
            timeText.setText("正在直播 ✦");
        }
    }

    private void registerExitReceiver() {
        exitReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_USER_PRESENT.equals(action)
                        || Intent.ACTION_SCREEN_OFF.equals(action)) {
                    finish();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_OFF);

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(exitReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(exitReceiver, filter);
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(ticker);

        if (exitReceiver != null) {
            try {
                unregisterReceiver(exitReceiver);
            } catch (Exception ignored) {
            }
            exitReceiver = null;
        }

        super.onDestroy();
    }
}
