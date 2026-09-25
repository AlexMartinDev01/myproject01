package com.shiguangbox.app

import android.content.Context
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class RestoreResult(
    val notes: Int,
    val tasks: Int,
    val favorites: Int,
    val dailySummaries: Int
)

object BackupUtils {

    suspend fun buildBackup(
        context: Context,
        db: AppDatabase
    ): String {
        val prefs =
            context.getSharedPreferences(
                "shiguangbox_settings",
                Context.MODE_PRIVATE
            )

        val root =
            JSONObject()

        root.put(
            "format",
            "shiguangbox-backup-v1"
        )

        root.put(
            "appVersion",
            BuildConfig.VERSION_NAME
        )

        root.put(
            "exportedAt",
            Instant.now().toString()
        )

        root.put(
            "settings",
            JSONObject().apply {
                put(
                    "name",
                    prefs.getString(
                        "name",
                        "我"
                    ) ?: "我"
                )

                put(
                    "city",
                    prefs.getString(
                        "city",
                        ""
                    ) ?: ""
                )

                put(
                    "summaryTime",
                    prefs.getString(
                        "summary_time",
                        "22:30"
                    ) ?: "22:30"
                )

                put(
                    "autoSummary",
                    prefs.getBoolean(
                        "auto_summary",
                        false
                    )
                )

                put(
                    "autoFavoriteAi",
                    prefs.getBoolean(
                        "auto_favorite_ai",
                        false
                    )
                )

                put(
                    "journalMood",
                    prefs.getString(
                        "journal_mood",
                        "calm"
                    ) ?: "calm"
                )

                put(
                    "journalThemeOffset",
                    prefs.getInt(
                        "journal_theme_offset",
                        0
                    )
                )
            }
        )

        val notes =
            JSONArray()

        db.noteDao()
            .getAllOnce()
            .forEach {
                notes.put(
                    JSONObject().apply {
                        put(
                            "id",
                            it.id
                        )
                        put(
                            "content",
                            it.content
                        )
                        put(
                            "category",
                            it.category
                        )
                        put(
                            "createdAt",
                            it.createdAt
                        )
                        put(
                            "updatedAt",
                            it.updatedAt
                        )
                    }
                )
            }

        root.put(
            "notes",
            notes
        )

        val tasks =
            JSONArray()

        db.taskDao()
            .getAllOnce()
            .forEach {
                tasks.put(
                    JSONObject().apply {
                        put(
                            "id",
                            it.id
                        )
                        put(
                            "title",
                            it.title
                        )
                        put(
                            "dueAt",
                            it.dueAt
                                ?: JSONObject.NULL
                        )
                        put(
                            "remindAt",
                            it.remindAt
                                ?: JSONObject.NULL
                        )
                        put(
                            "repeatType",
                            it.repeatType
                        )
                        put(
                            "priority",
                            it.priority
                        )
                        put(
                            "completed",
                            it.completed
                        )
                        put(
                            "completedAt",
                            it.completedAt
                                ?: JSONObject.NULL
                        )
                        put(
                            "createdAt",
                            it.createdAt
                        )
                    }
                )
            }

        root.put(
            "tasks",
            tasks
        )

        val favorites =
            JSONArray()

        db.favoriteDao()
            .getAllOnce()
            .forEach {
                favorites.put(
                    JSONObject().apply {
                        put(
                            "id",
                            it.id
                        )
                        put(
                            "title",
                            it.title
                        )
                        put(
                            "url",
                            it.url
                        )
                        put(
                            "platform",
                            it.platform
                        )
                        put(
                            "rawText",
                            it.rawText
                        )
                        put(
                            "note",
                            it.note
                        )
                        put(
                            "aiSummary",
                            it.aiSummary
                        )
                        put(
                            "aiTags",
                            it.aiTags
                        )
                        put(
                            "knowledgeType",
                            it.knowledgeType
                        )
                        put(
                            "imagePaths",
                            it.imagePaths
                        )
                        put(
                            "imageAnalysis",
                            it.imageAnalysis
                        )
                        put(
                            "topic",
                            it.topic
                        )
                        put(
                            "suggestedTopic",
                            it.suggestedTopic
                        )
                        put(
                            "important",
                            it.important
                        )
                        put(
                            "processingStatus",
                            it.processingStatus
                        )
                        put(
                            "createdAt",
                            it.createdAt
                        )
                        put(
                            "updatedAt",
                            it.updatedAt
                        )
                    }
                )
            }

        root.put(
            "favorites",
            favorites
        )

        val summaries =
            JSONArray()

        db.dailySummaryDao()
            .getAllOnce()
            .forEach {
                summaries.put(
                    JSONObject().apply {
                        put(
                            "dateKey",
                            it.dateKey
                        )
                        put(
                            "content",
                            it.content
                        )
                        put(
                            "model",
                            it.model
                        )
                        put(
                            "createdAt",
                            it.createdAt
                        )
                        put(
                            "updatedAt",
                            it.updatedAt
                        )
                    }
                )
            }

        root.put(
            "dailySummaries",
            summaries
        )

        return root.toString(
            2
        )
    }

    suspend fun restoreBackup(
        context: Context,
        db: AppDatabase,
        jsonText: String
    ): RestoreResult {
        val root =
            JSONObject(
                jsonText
            )

        require(
            root.optString(
                "format"
            ) ==
                "shiguangbox-backup-v1"
        ) {
            "这不是有效的拾光盒备份文件"
        }

        val settings =
            root.optJSONObject(
                "settings"
            )

        settings?.let {
            context
                .getSharedPreferences(
                    "shiguangbox_settings",
                    Context.MODE_PRIVATE
                )
                .edit()
                .putString(
                    "name",
                    it.optString(
                        "name",
                        "我"
                    )
                )
                .putString(
                    "city",
                    it.optString(
                        "city",
                        ""
                    )
                )
                .putString(
                    "summary_time",
                    it.optString(
                        "summaryTime",
                        "22:30"
                    )
                )
                .putBoolean(
                    "auto_summary",
                    it.optBoolean(
                        "autoSummary",
                        false
                    )
                )
                .putBoolean(
                    "auto_favorite_ai",
                    it.optBoolean(
                        "autoFavoriteAi",
                        false
                    )
                )
                .putString(
                    "journal_mood",
                    it.optString(
                        "journalMood",
                        "calm"
                    )
                )
                .putInt(
                    "journal_theme_offset",
                    it.optInt(
                        "journalThemeOffset",
                        0
                    )
                )
                .putBoolean(
                    "onboarding_done",
                    true
                )
                .apply()
        }

        var noteCount = 0
        var taskCount = 0
        var favoriteCount = 0
        var summaryCount = 0

        val restoredTasks =
            mutableListOf<TaskEntity>()

        db.withTransaction {
            val notes =
                root.optJSONArray(
                    "notes"
                )

            if (notes != null) {
                for (
                    index in
                    0 until
                        notes.length()
                ) {
                    val item =
                        notes.optJSONObject(
                            index
                        )
                            ?: continue

                    db.noteDao()
                        .insert(
                            NoteEntity(
                                content =
                                    item.optString(
                                        "content",
                                        ""
                                    ),
                                category =
                                    item.optString(
                                        "category",
                                        "生活"
                                    ),
                                createdAt =
                                    item.optLong(
                                        "createdAt",
                                        System
                                            .currentTimeMillis()
                                    ),
                                updatedAt =
                                    item.optLong(
                                        "updatedAt",
                                        System
                                            .currentTimeMillis()
                                    )
                            )
                        )

                    noteCount +=
                        1
                }
            }

            val tasks =
                root.optJSONArray(
                    "tasks"
                )

            if (tasks != null) {
                for (
                    index in
                    0 until
                        tasks.length()
                ) {
                    val item =
                        tasks.optJSONObject(
                            index
                        )
                            ?: continue

                    val restored =
                        TaskEntity(
                            title =
                                item.optString(
                                    "title",
                                    "待办"
                                ),
                            dueAt =
                                nullableLong(
                                    item,
                                    "dueAt"
                                ),
                            remindAt =
                                nullableLong(
                                    item,
                                    "remindAt"
                                ),
                            repeatType =
                                item.optString(
                                    "repeatType",
                                    "不重复"
                                ),
                            priority =
                                item.optString(
                                    "priority",
                                    "普通"
                                ),
                            completed =
                                item.optBoolean(
                                    "completed",
                                    false
                                ),
                            completedAt =
                                nullableLong(
                                    item,
                                    "completedAt"
                                ),
                            createdAt =
                                item.optLong(
                                    "createdAt",
                                    System
                                        .currentTimeMillis()
                                )
                        )

                    val newId =
                        db.taskDao()
                            .insert(
                                restored
                            )

                    restoredTasks +=
                        restored.copy(
                            id =
                                newId
                        )

                    taskCount +=
                        1
                }
            }

            val favorites =
                root.optJSONArray(
                    "favorites"
                )

            if (
                favorites !=
                null
            ) {
                for (
                    index in
                    0 until
                        favorites.length()
                ) {
                    val item =
                        favorites
                            .optJSONObject(
                                index
                            )
                            ?: continue

                    db.favoriteDao()
                        .insert(
                            FavoriteEntity(
                                title =
                                    item.optString(
                                        "title",
                                        "收藏"
                                    ),
                                url =
                                    item.optString(
                                        "url",
                                        ""
                                    ),
                                platform =
                                    item.optString(
                                        "platform",
                                        "其他"
                                    ),
                                rawText =
                                    item.optString(
                                        "rawText",
                                        ""
                                    ),
                                note =
                                    item.optString(
                                        "note",
                                        ""
                                    ),
                                aiSummary =
                                    item.optString(
                                        "aiSummary",
                                        ""
                                    ),
                                aiTags =
                                    item.optString(
                                        "aiTags",
                                        ""
                                    ),
                                knowledgeType =
                                    item.optString(
                                        "knowledgeType",
                                        "收藏"
                                    ),
                                imagePaths =
                                    item.optString(
                                        "imagePaths",
                                        ""
                                    ),
                                imageAnalysis =
                                    item.optString(
                                        "imageAnalysis",
                                        ""
                                    ),
                                topic =
                                    item.optString(
                                        "topic",
                                        ""
                                    ),
                                suggestedTopic =
                                    item.optString(
                                        "suggestedTopic",
                                        ""
                                    ),
                                important =
                                    item.optBoolean(
                                        "important",
                                        false
                                    ),
                                processingStatus =
                                    item.optString(
                                        "processingStatus",
                                        "已保存"
                                    ),
                                createdAt =
                                    item.optLong(
                                        "createdAt",
                                        System
                                            .currentTimeMillis()
                                    ),
                                updatedAt =
                                    item.optLong(
                                        "updatedAt",
                                        System
                                            .currentTimeMillis()
                                    )
                            )
                        )

                    favoriteCount +=
                        1
                }
            }

            val summaries =
                root.optJSONArray(
                    "dailySummaries"
                )

            if (
                summaries !=
                null
            ) {
                for (
                    index in
                    0 until
                        summaries.length()
                ) {
                    val item =
                        summaries
                            .optJSONObject(
                                index
                            )
                            ?: continue

                    val dateKey =
                        item.optString(
                            "dateKey",
                            ""
                        )

                    if (
                        dateKey
                            .isBlank()
                    ) {
                        continue
                    }

                    db.dailySummaryDao()
                        .upsert(
                            DailySummaryEntity(
                                dateKey =
                                    dateKey,
                                content =
                                    item.optString(
                                        "content",
                                        ""
                                    ),
                                model =
                                    item.optString(
                                        "model",
                                        "deepseek-flash"
                                    ),
                                createdAt =
                                    item.optLong(
                                        "createdAt",
                                        System
                                            .currentTimeMillis()
                                    ),
                                updatedAt =
                                    item.optLong(
                                        "updatedAt",
                                        System
                                            .currentTimeMillis()
                                    )
                            )
                        )

                    summaryCount +=
                        1
                }
            }
        }

        val now =
            System
                .currentTimeMillis()

        restoredTasks
            .filter {
                !it.completed &&
                    it.remindAt
                        ?.let {
                            remindAt ->
                            remindAt >
                                now
                        } ==
                    true
            }
            .forEach {
                ReminderScheduler
                    .schedule(
                        context,
                        it
                    )
            }

        return RestoreResult(
            notes =
                noteCount,
            tasks =
                taskCount,
            favorites =
                favoriteCount,
            dailySummaries =
                summaryCount
        )
    }

    private fun nullableLong(
        objectValue: JSONObject,
        key: String
    ): Long? {
        if (
            !objectValue.has(
                key
            ) ||
            objectValue.isNull(
                key
            )
        ) {
            return null
        }

        return objectValue.optLong(
            key
        )
    }
}
