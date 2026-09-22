package com.soda.livewidget;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int REQ_NOTIFICATIONS = 1001;

    private boolean wentToOverlaySettings = false;
    private boolean startAfterNotificationRequest = false;
    private TextView permissionStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        permissionStatus = findViewById(R.id.permissionStatus);
        Button startButton = findViewById(R.id.startOverlayButton);
        Button stopButton = findViewById(R.id.stopOverlayButton);

        startButton.setOnClickListener(v -> beginGuidedSetup());

        stopButton.setOnClickListener(v -> {
            stopService(new Intent(this, OverlayService.class));
            Toast.makeText(this, "六点见悬浮窗已关闭", Toast.LENGTH_SHORT).show();
            updatePermissionStatus();
        });

        updatePermissionStatus();

        // 已经授权过悬浮窗时，之后打开 App 直接显示。
        if (Settings.canDrawOverlays(this)) {
            startOverlayAndClose();
        }
    }

    private void beginGuidedSetup() {
        if (!Settings.canDrawOverlays(this)) {
            wentToOverlaySettings = true;
            Toast.makeText(
                    this,
                    "下一页只需打开「允许显示在其他应用上层」，再返回即可",
                    Toast.LENGTH_LONG
            ).show();

            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(intent);
            return;
        }

        requestNotificationPermissionThenStart();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (wentToOverlaySettings) {
            if (Settings.canDrawOverlays(this)) {
                wentToOverlaySettings = false;
                Toast.makeText(
                        this,
                        "悬浮权限已开启，再确认一次锁屏通知权限",
                        Toast.LENGTH_SHORT
                ).show();
                requestNotificationPermissionThenStart();
            } else {
                permissionStatus.setText("还差一步：请打开系统悬浮窗权限");
            }
        }
    }

    private void requestNotificationPermissionThenStart() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            startAfterNotificationRequest = true;
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQ_NOTIFICATIONS
            );
        } else {
            startOverlayAndClose();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_NOTIFICATIONS && startAfterNotificationRequest) {
            startAfterNotificationRequest = false;

            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(
                        this,
                        "锁屏倒计时已开启",
                        Toast.LENGTH_SHORT
                ).show();
            } else {
                Toast.makeText(
                        this,
                        "悬浮窗仍可使用；锁屏倒计时需要通知权限",
                        Toast.LENGTH_LONG
                ).show();
            }

            startOverlayAndClose();
        }
    }

    private void updatePermissionStatus() {
        if (Settings.canDrawOverlays(this)) {
            permissionStatus.setText("✓ 悬浮显示已准备好");
        } else {
            permissionStatus.setText("① 第一次使用：开启悬浮显示权限");
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
