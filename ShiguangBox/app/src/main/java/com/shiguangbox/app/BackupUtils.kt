package com.shiguangbox.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

object BackupUtils {

    suspend fun buildBackup(context: Context, db: AppDatabase): String {
        val prefs = context.getSharedPreferences("shiguangbox_settings", Context.MODE_PRIVATE)

        val root = JSONObject()
        root.put("format", "shiguangbox-backup-v1")
        root.put("appVersion", "0.5.0")
        root.put("exportedAt", Instant.now().toString())

        root.put(
            "settings",
            JSONObject().apply {
                put("name", prefs.getString("name", "我") ?: "我")
                put("city", prefs.getString("city", "") ?: "")
                put("summaryTime", prefs.getString("summary_time", "22:30") ?: "22:30")
                put("autoSummary", prefs.getBoolean("auto_summary", false))
                put("autoFavoriteAi", prefs.getBoolean("auto_favorite_ai", false))
            }
        )

        val notes = JSONArray()
        db.noteDao().getAllOnce().forEach {
            notes.put(
                JSONObject().apply {
                    put("id", it.id)
                    put("content", it.content)
                    put("category", it.category)
                    put("createdAt", it.createdAt)
                    put("updatedAt", it.updatedAt)
                }
            )
        }
        root.put("notes", notes)

        val tasks = JSONArray()
        db.taskDao().getAllOnce().forEach {
            tasks.put(
                JSONObject().apply {
                    put("id", it.id)
                    put("title", it.title)
                    put("dueAt", it.dueAt ?: JSONObject.NULL)
                    put("remindAt", it.remindAt ?: JSONObject.NULL)
                    put("repeatType", it.repeatType)
                    put("priority", it.priority)
                    put("completed", it.completed)
                    put("completedAt", it.completedAt ?: JSONObject.NULL)
                    put("createdAt", it.createdAt)
                }
            )
        }
        root.put("tasks", tasks)

        val favorites = JSONArray()
        db.favoriteDao().getAllOnce().forEach {
            favorites.put(
                JSONObject().apply {
                    put("id", it.id)
                    put("title", it.title)
                    put("url", it.url)
                    put("platform", it.platform)
                    put("rawText", it.rawText)
                    put("note", it.note)
                    put("aiSummary", it.aiSummary)
                    put("aiTags", it.aiTags)
                    put("createdAt", it.createdAt)
                    put("updatedAt", it.updatedAt)
                }
            )
        }
        root.put("favorites", favorites)

        val summaries = JSONArray()
        db.dailySummaryDao().getAllOnce().forEach {
            summaries.put(
                JSONObject().apply {
                    put("dateKey", it.dateKey)
                    put("content", it.content)
                    put("model", it.model)
                    put("createdAt", it.createdAt)
                    put("updatedAt", it.updatedAt)
                }
            )
        }
        root.put("dailySummaries", summaries)

        return root.toString(2)
    }
}
