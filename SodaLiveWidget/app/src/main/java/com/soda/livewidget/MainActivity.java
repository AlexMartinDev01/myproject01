package com.soda.livewidget;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private boolean wentToOverlaySettings = false;
    private TextView permissionStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        permissionStatus = findViewById(R.id.permissionStatus);
        Button startButton = findViewById(R.id.startOverlayButton);
        Button stopButton = findViewById(R.id.stopOverlayButton);

        startButton.setOnClickListener(v -> {
            if (Settings.canDrawOverlays(this)) {
                startOverlayAndClose();
            } else {
                wentToOverlaySettings = true;
                Toast.makeText(
                        this,
                        "下一页请打开「允许显示在其他应用上层」开关，然后返回这里",
                        Toast.LENGTH_LONG
                ).show();

                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())
                );
                startActivity(intent);
            }
        });

        stopButton.setOnClickListener(v -> {
            stopService(new Intent(this, OverlayService.class));
            Toast.makeText(this, "悬浮倒计时已关闭", Toast.LENGTH_SHORT).show();
            updatePermissionStatus();
        });

        if (Settings.canDrawOverlays(this)) {
            startOverlayAndClose();
        } else {
            updatePermissionStatus();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (wentToOverlaySettings) {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(
                        this,
                        "权限已开启，正在显示悬浮倒计时",
                        Toast.LENGTH_SHORT
                ).show();
                startOverlayAndClose();
            } else {
                permissionStatus.setText("还差一步：系统悬浮窗权限尚未开启");
            }
        }
    }

    private void updatePermissionStatus() {
        if (Settings.canDrawOverlays(this)) {
            permissionStatus.setText("✓ 悬浮窗权限已开启");
        } else {
            permissionStatus.setText("① 先开启系统悬浮窗权限");
        }
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
