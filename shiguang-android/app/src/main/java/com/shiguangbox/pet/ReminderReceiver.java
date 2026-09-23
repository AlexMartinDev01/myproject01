package com.shiguangbox.pet;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String title = intent.getStringExtra("title");
        Intent service = new Intent(context, PetOverlayService.class)
                .setAction(PetOverlayService.ACTION_REMINDER)
                .putExtra("title", title == null ? "到提醒时间啦" : title);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(service);
        else context.startService(service);
    }
}
