package com.shiguangbox.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra("task_id", 0L)
        val title = intent.getStringExtra("task_title") ?: "你有一个待办"

        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = "shiguangbox_reminders"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "待办提醒",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "拾光盒的待办事项提醒"
                }
            )
        }

        val openIntent = Intent(context, MainActivity::class.java)
        val openPending = PendingIntent.getActivity(
            context,
            taskId.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle("拾光盒")
            .setContentText("该做「$title」啦")
            .setStyle(NotificationCompat.BigTextStyle().bigText("该做「$title」啦，完成后记得回来勾掉它。"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .build()

        manager.notify(taskId.toInt(), notification)

        val prefs = context.getSharedPreferences(
            "shiguangbox_settings",
            Context.MODE_PRIVATE
        )

        if (prefs.getBoolean("pet_enabled", false) &&
            Settings.canDrawOverlays(context)
        ) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, PetOverlayService::class.java).apply {
                        action = PetOverlayService.ACTION_REMINDER
                        putExtra(PetOverlayService.EXTRA_TASK_ID, taskId)
                        putExtra(PetOverlayService.EXTRA_TASK_TITLE, title)
                    }
                )
            }
        }
    }
}
