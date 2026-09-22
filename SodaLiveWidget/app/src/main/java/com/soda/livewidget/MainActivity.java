package com.soda.livewidget;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {

    private boolean openedOverlaySettings = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView hint = findViewById(R.id.hintText);
        hint.setText("打开 App 后会自动显示悬浮倒计时。\n\n首次使用只需要允许一次“显示在其他应用上层”权限。之后再打开 App，会直接弹出悬浮卡片。");

        Button startButton = findViewById(R.id.startOverlayButton);
        Button stopButton = findViewById(R.id.stopOverlayButton);

        startButton.setOnClickListener(v -> ensurePermissionAndStart());
        stopButton.setOnClickListener(v -> stopService(new Intent(this, OverlayService.class)));

        if (Settings.canDrawOverlays(this)) {
            startOverlayAndClose();
        } else {
            openOverlaySettings();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (openedOverlaySettings && Settings.canDrawOverlays(this)) {
            startOverlayAndClose();
        }
    }

    private void ensurePermissionAndStart() {
        if (Settings.canDrawOverlays(this)) {
            startOverlayAndClose();
        } else {
            openOverlaySettings();
        }
    }

    private void openOverlaySettings() {
        openedOverlaySettings = true;
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
        );
        startActivity(intent);
    }

    private void startOverlayAndClose() {
        Intent serviceIntent = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        finish();
    }
}
