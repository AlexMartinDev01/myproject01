package com.shiguangbox.app

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class KnowledgeOrganizeWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val favoriteId = inputData.getLong(KEY_ID, 0L)
        if (favoriteId <= 0L) return Result.success()

        val dao = AppDatabase.get(context).favoriteDao()
        val item = dao.getById(favoriteId) ?: return Result.success()
        val apiKey = SecureApiKeyStore.load(context)

        if (apiKey.isNullOrBlank()) {
            dao.update(
                item.copy(
                    processingStatus = "等待AI配置",
                    updatedAt = System.currentTimeMillis()
                )
            )
            return Result.success()
        }

        dao.update(
            item.copy(
                processingStatus = "AI整理中",
                updatedAt = System.currentTimeMillis()
            )
        )

        val paths = ImageStorage.decodePaths(item.imagePaths)

        val ai = withContext(Dispatchers.IO) {
            if (paths.isNotEmpty()) {
                val images = paths.mapNotNull { ImageStorage.toBase64(it) }
                if (images.isEmpty()) {
                    DeepSeekClient.Result(false, error = "本地图片读取失败")
                } else {
                    DeepSeekClient.analyzeKnowledgeImages(
                        apiKey = apiKey,
                        title = item.title,
                        text = item.rawText,
                        note = item.note,
                        imageBase64List = images
                    )
                }
            } else {
                DeepSeekClient.summarizeFavorite(
                    apiKey = apiKey,
                    title = item.title,
                    platform = item.platform,
                    rawText = item.rawText,
                    note = item.note
                )
            }
        }

        val latest = dao.getById(favoriteId) ?: item

        return if (ai.success) {
            val topic = extractSection(ai.content, "建议专题")
                .lineSequence()
                .firstOrNull { it.isNotBlank() }
                ?.trim()
                ?.removePrefix("：")
                ?.removePrefix(":")
                ?.take(40)
                .orEmpty()

            val tags = extractSection(ai.content, "建议标签")
                .lineSequence()
                .firstOrNull { it.isNotBlank() }
                ?.trim()
                ?.removePrefix("：")
                ?.removePrefix(":")
                ?.take(160)
                .orEmpty()

            dao.update(
                latest.copy(
                    aiSummary = ai.content,
                    aiTags = tags,
                    imageAnalysis = if (paths.isNotEmpty()) ai.content else latest.imageAnalysis,
                    suggestedTopic = if (topic == "待分类") "" else topic,
                    processingStatus = "已整理",
                    updatedAt = System.currentTimeMillis()
                )
            )
            Result.success()
        } else {
            dao.update(
                latest.copy(
                    processingStatus = "整理失败",
                    updatedAt = System.currentTimeMillis()
                )
            )
            if (runAttemptCount < 1) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val KEY_ID = "favorite_id"

        fun enqueue(context: Context, favoriteId: Long) {
            val request = OneTimeWorkRequestBuilder<KnowledgeOrganizeWorker>()
                .setInputData(workDataOf(KEY_ID to favoriteId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "knowledge_organize_" + favoriteId,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}

private fun extractSection(content: String, title: String): String {
    val marker = "【" + title + "】"
    val start = content.indexOf(marker)
    if (start < 0) return ""

    val rest = content.substring(start + marker.length)
    val next = rest.indexOf("【")
    return if (next >= 0) rest.substring(0, next).trim() else rest.trim()
}
