package com.shiguangbox.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object ReminderScheduler {

    fun schedule(
        context: Context,
        task: TaskEntity
    ) {
        val remindAt =
            task.remindAt ?: return

        if (task.completed ||
            remindAt <=
                System.currentTimeMillis()
        ) {
            return
        }

        val alarmManager =
            context.getSystemService(
                AlarmManager::class.java
            )

        scheduleAt(
            alarmManager = alarmManager,
            at = remindAt,
            pendingIntent =
                pendingIntent(
                    context,
                    task,
                    kind = KIND_DUE,
                    requestCode =
                        task.id.toInt()
                )
        )

        val dueAt = task.dueAt

        if (dueAt != null) {
            val preAt =
                dueAt -
                    PRE_REMINDER_MS

            if (preAt >
                System.currentTimeMillis() &&
                preAt < remindAt
            ) {
                scheduleAt(
                    alarmManager =
                        alarmManager,
                    at = preAt,
                    pendingIntent =
                        pendingIntent(
                            context,
                            task,
                            kind = KIND_PRE,
                            requestCode =
                                preRequestCode(
                                    task.id
                                )
                        )
                )
            }
        }
    }

    fun cancel(
        context: Context,
        taskId: Long
    ) {
        val alarmManager =
            context.getSystemService(
                AlarmManager::class.java
            )

        listOf(
            taskId.toInt(),
            preRequestCode(taskId)
        ).forEach { requestCode ->
            val pending =
                PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    Intent(
                        context,
                        ReminderReceiver::class.java
                    ),
                    PendingIntent.FLAG_NO_CREATE or
                        PendingIntent.FLAG_IMMUTABLE
                )

            if (pending != null) {
                alarmManager.cancel(pending)
                pending.cancel()
            }
        }
    }

    private fun scheduleAt(
        alarmManager: AlarmManager,
        at: Long,
        pendingIntent: PendingIntent
    ) {
        if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.S &&
            alarmManager
                .canScheduleExactAlarms()
        ) {
            alarmManager
                .setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    at,
                    pendingIntent
                )
        } else {
            alarmManager
                .setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    at,
                    pendingIntent
                )
        }
    }

    private fun pendingIntent(
        context: Context,
        task: TaskEntity,
        kind: String,
        requestCode: Int
    ): PendingIntent {
        val intent =
            Intent(
                context,
                ReminderReceiver::class.java
            ).apply {
                putExtra(
                    "task_id",
                    task.id
                )
                putExtra(
                    "task_title",
                    task.title
                )
                putExtra(
                    EXTRA_KIND,
                    kind
                )
            }

        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun preRequestCode(
        taskId: Long
    ): Int {
        return taskId.toInt() xor
            0x40000000
    }

    const val EXTRA_KIND =
        "reminder_kind"
    const val KIND_DUE = "due"
    const val KIND_PRE = "pre"

    private const val PRE_REMINDER_MS =
        10L * 60L * 1000L
}
