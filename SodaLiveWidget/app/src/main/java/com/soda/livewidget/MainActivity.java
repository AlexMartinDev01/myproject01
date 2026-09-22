package com.soda.livewidget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView hint = findViewById(R.id.hintText);
        hint.setText("长按手机桌面空白处 → 小组件/桌面组件 → 找到“苏打水直播倒计时” → 添加到桌面。\n\n倒计时默认每天 18:00，系统会直接按秒跳动，不需要一直打开 App。");

        Button refresh = findViewById(R.id.refreshButton);
        refresh.setOnClickListener(v -> {
            AppWidgetManager manager = AppWidgetManager.getInstance(this);
            int[] ids = manager.getAppWidgetIds(new ComponentName(this, CountdownWidgetProvider.class));
            CountdownWidgetProvider.updateAll(this, manager, ids);
        });
    }
}
