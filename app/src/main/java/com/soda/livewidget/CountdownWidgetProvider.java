package com.soda.livewidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import java.util.Calendar;

public class CountdownWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        updateAll(context, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_DATE_CHANGED.equals(action)) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            int[] ids = manager.getAppWidgetIds(new ComponentName(context, CountdownWidgetProvider.class));
            updateAll(context, manager, ids);
        }
    }

    public static void updateAll(Context context, AppWidgetManager manager, int[] ids) {
        if (ids == null || ids.length == 0) return;
        for (int id : ids) updateOne(context, manager, id);
    }

    private static void updateOne(Context context, AppWidgetManager manager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_countdown);

        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, 18);
        target.set(Calendar.MINUTE, 0);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        long diff = target.getTimeInMillis() - now.getTimeInMillis();

        if (diff > 0) {
            long base = SystemClock.elapsedRealtime() + diff;
            views.setViewVisibility(R.id.countdown, View.VISIBLE);
            views.setViewVisibility(R.id.liveText, View.GONE);
            views.setTextViewText(R.id.subtitle, "距离今天 18:00 开播还有");
            views.setChronometer(R.id.countdown, base, "%s", true);
            views.setChronometerCountDown(R.id.countdown, true);
        } else {
            views.setViewVisibility(R.id.countdown, View.GONE);
            views.setViewVisibility(R.id.liveText, View.VISIBLE);
            views.setTextViewText(R.id.subtitle, "今天 18:00");
            views.setTextViewText(R.id.liveText, "苏打水开播啦 ✦");
        }

        Intent open = new Intent(context, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(
                context,
                0,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widgetRoot, pending);
        manager.updateAppWidget(appWidgetId, views);
    }
}
