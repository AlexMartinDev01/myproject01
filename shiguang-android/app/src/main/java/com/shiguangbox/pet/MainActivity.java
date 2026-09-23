package com.shiguangbox.pet;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_OVERLAY = 1001;
    private static final String PREFS = "shiguang_prefs";

    private TextView overlayStatus;
    private TextView notesView;
    private EditText noteInput;
    private EditText reminderInput;
    private EditText minuteInput;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        refreshNotes();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(255, 248, 238));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView hero = new ImageView(this);
        hero.setImageBitmap(PetAssets.frame(this, PetAssets.IDLE));
        hero.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        root.addView(hero, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(170)));

        TextView title = text("拾光盒", 30, true, Color.rgb(91, 59, 39));
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title);

        TextView subtitle = text("橘团 · 温柔陪伴的悬浮桌宠", 15, false, Color.rgb(151, 105, 70));
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitle.setPadding(0, dp(4), 0, dp(18));
        root.addView(subtitle);

        LinearLayout statusCard = card();
        overlayStatus = text("", 15, true, Color.rgb(87, 65, 52));
        statusCard.addView(overlayStatus);
        Button permissionBtn = button("开启悬浮窗权限");
        permissionBtn.setOnClickListener(v -> requestOverlayPermission());
        statusCard.addView(permissionBtn);
        Button startBtn = button("启动橘团桌宠");
        startBtn.setOnClickListener(v -> startPet());
        statusCard.addView(startBtn);
        Button stopBtn = secondaryButton("暂时隐藏桌宠");
        stopBtn.setOnClickListener(v -> stopPet());
        statusCard.addView(stopBtn);
        root.addView(statusCard);

        root.addView(sectionTitle("快速记录"));
        LinearLayout noteCard = card();
        noteInput = input("今天想记下什么？");
        noteCard.addView(noteInput);
        Button saveBtn = button("记一下");
        saveBtn.setOnClickListener(v -> saveNote());
        noteCard.addView(saveBtn);
        notesView = text("", 14, false, Color.rgb(100, 83, 72));
        notesView.setPadding(0, dp(12), 0, 0);
        noteCard.addView(notesView);
        root.addView(noteCard);

        root.addView(sectionTitle("提醒"));
        LinearLayout reminderCard = card();
        reminderInput = input("例如：喝水 / 直播准备 / 交作业");
        reminderCard.addView(reminderInput);
        minuteInput = input("几分钟后提醒（默认 1）");
        minuteInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        reminderCard.addView(minuteInput);
        Button reminderBtn = button("创建提醒");
        reminderBtn.setOnClickListener(v -> scheduleReminder());
        reminderCard.addView(reminderBtn);
        Button testReminder = secondaryButton("立即测试提醒动作");
        testReminder.setOnClickListener(v -> sendPetAction(PetOverlayService.ACTION_REMINDER));
        reminderCard.addView(testReminder);
        root.addView(reminderCard);

        root.addView(sectionTitle("橘团互动测试"));
        LinearLayout actionCard = card();
        Button wave = secondaryButton("挥爪 / 摆手");
        wave.setOnClickListener(v -> sendPetAction(PetOverlayService.ACTION_WAVE));
        actionCard.addView(wave);
        Button success = secondaryButton("完成任务 · 庆祝");
        success.setOnClickListener(v -> sendPetAction(PetOverlayService.ACTION_SUCCESS));
        actionCard.addView(success);
        Button sleep = secondaryButton("让橘团睡觉");
        sleep.setOnClickListener(v -> sendPetAction(PetOverlayService.ACTION_SLEEP));
        actionCard.addView(sleep);
        Button wake = secondaryButton("叫醒橘团");
        wake.setOnClickListener(v -> sendPetAction(PetOverlayService.ACTION_WAKE));
        actionCard.addView(wake);
        root.addView(actionCard);

        TextView footer = text("V1.2.0 · 橘团动态桌宠版\n支持拖拽、自动吸边、随机动作、提醒、困倦与趴睡", 12, false, Color.rgb(170, 140, 118));
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(24), 0, 0);
        root.addView(footer);

        setContentView(scroll);
    }

    private void requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "悬浮窗权限已经开启", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, REQ_OVERLAY);
    }

    private void startPet() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission();
            return;
        }
        prefs.edit().putBoolean("pet_enabled", true).apply();
        Intent i = new Intent(this, PetOverlayService.class).setAction(PetOverlayService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i); else startService(i);
        Toast.makeText(this, "橘团已经出来陪你啦", Toast.LENGTH_SHORT).show();
        refreshStatus();
    }

    private void stopPet() {
        prefs.edit().putBoolean("pet_enabled", false).apply();
        stopService(new Intent(this, PetOverlayService.class));
        Toast.makeText(this, "橘团先休息一会儿", Toast.LENGTH_SHORT).show();
        refreshStatus();
    }

    private void sendPetAction(String action) {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission();
            return;
        }
        Intent i = new Intent(this, PetOverlayService.class).setAction(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i); else startService(i);
    }

    private void saveNote() {
        String note = noteInput.getText().toString().trim();
        if (note.isEmpty()) return;
        String old = prefs.getString("notes", "");
        String time = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date());
        String combined = "• " + time + "  " + note + "\n" + old;
        String[] lines = combined.split("\n");
        StringBuilder limited = new StringBuilder();
        for (int i = 0; i < Math.min(lines.length, 8); i++) limited.append(lines[i]).append('\n');
        prefs.edit().putString("notes", limited.toString()).apply();
        noteInput.setText("");
        refreshNotes();
        sendPetAction(PetOverlayService.ACTION_SUCCESS);
    }

    private void scheduleReminder() {
        String title = reminderInput.getText().toString().trim();
        if (title.isEmpty()) title = "该休息一下啦";
        int minutes = 1;
        try { minutes = Integer.parseInt(minuteInput.getText().toString().trim()); } catch (Exception ignored) {}
        if (minutes < 1) minutes = 1;

        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra("title", title);
        int requestCode = (int) (System.currentTimeMillis() & 0x7fffffff);
        PendingIntent pi = PendingIntent.getBroadcast(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        long at = System.currentTimeMillis() + minutes * 60_000L;
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        Toast.makeText(this, minutes + " 分钟后提醒：" + title, Toast.LENGTH_LONG).show();
        reminderInput.setText("");
        minuteInput.setText("");
    }

    private void refreshStatus() {
        if (overlayStatus == null) return;
        boolean permission = Settings.canDrawOverlays(this);
        boolean enabled = prefs.getBoolean("pet_enabled", false);
        overlayStatus.setText("悬浮权限：" + (permission ? "已开启 ✓" : "未开启") + "\n桌宠状态：" + (enabled ? "已启用" : "未启用"));
    }

    private void refreshNotes() {
        if (notesView == null) return;
        String notes = prefs.getString("notes", "").trim();
        notesView.setText(notes.isEmpty() ? "还没有记录，先写下一件小事吧～" : notes);
    }

    private LinearLayout card() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(20));
        box.setBackground(bg);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 0, dp(14));
        box.setLayoutParams(p);
        return box;
    }

    private TextView sectionTitle(String s) {
        TextView t = text(s, 18, true, Color.rgb(91, 59, 39));
        t.setPadding(dp(4), dp(8), 0, dp(10));
        return t;
    }

    private EditText input(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(15);
        e.setSingleLine(true);
        e.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(255, 251, 246));
        bg.setStroke(dp(1), Color.rgb(238, 219, 199));
        bg.setCornerRadius(dp(14));
        e.setBackground(bg);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        p.setMargins(0, 0, 0, dp(10));
        e.setLayoutParams(p);
        return e;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(242, 154, 76));
        bg.setCornerRadius(dp(16));
        b.setBackground(bg);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        p.setMargins(0, dp(6), 0, 0);
        b.setLayoutParams(p);
        return b;
    }

    private Button secondaryButton(String label) {
        Button b = button(label);
        b.setTextColor(Color.rgb(112, 76, 52));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.rgb(255, 240, 219));
        bg.setCornerRadius(dp(16));
        b.setBackground(bg);
        return b;
    }

    private TextView text(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
