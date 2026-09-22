package com.shiguangbox.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val tasks = AppDatabase.get(context)
                    .taskDao()
                    .pendingReminders(System.currentTimeMillis())
                tasks.forEach { ReminderScheduler.schedule(context, it) }

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
                            Intent(context, PetOverlayService::class.java)
                                .setAction(PetOverlayService.ACTION_SHOW)
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
