package com.shiguangbox.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent == null) {
            finish()
            return
        }

        when (intent.action) {
            Intent.ACTION_SEND -> handleSingle(intent)
            Intent.ACTION_SEND_MULTIPLE -> handleMultiple(intent)
            else -> finish()
        }
    }

    private fun handleSingle(intent: Intent) {
        val type = intent.type.orEmpty()

        if (type.startsWith("image/")) {
            @Suppress("DEPRECATION")
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            if (uri == null) {
                toastAndFinish("这张图片暂时无法读取")
                return
            }

            val rawText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
            saveSharedImages(listOf(uri), rawText, subject)
            return
        }

        val rawText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)

        if (rawText.isBlank()) {
            toastAndFinish("这条分享暂时没有可保存的文字或链接")
            return
        }

        lifecycleScope.launch {
            val parsed = FavoriteParser.parse(rawText, subject)
            val dao = AppDatabase.get(this@ShareReceiverActivity).favoriteDao()

            val existing = parsed.url
                .takeIf { it.isNotBlank() }
                ?.let { dao.findByUrl(it) }

            val favoriteId = if (existing != null) {
                dao.update(
                    existing.copy(
                        title = parsed.title,
                        platform = parsed.platform,
                        rawText = parsed.rawText,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                Toast.makeText(
                    this@ShareReceiverActivity,
                    "这个内容已经在拾光盒里啦 ✓",
                    Toast.LENGTH_SHORT
                ).show()
                existing.id
            } else {
                val id = dao.insert(parsed)
                Toast.makeText(
                    this@ShareReceiverActivity,
                    "已收进拾光盒 ✓",
                    Toast.LENGTH_SHORT
                ).show()
                id
            }

            val prefs = getSharedPreferences("shiguangbox_settings", MODE_PRIVATE)
            if (prefs.getBoolean("auto_favorite_ai", false)) {
                KnowledgeOrganizeWorker.enqueue(this@ShareReceiverActivity, favoriteId)
            }

            finish()
        }
    }

    private fun handleMultiple(intent: Intent) {
        if (!intent.type.orEmpty().startsWith("image/")) {
            finish()
            return
        }

        @Suppress("DEPRECATION")
        val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        if (uris.isEmpty()) {
            toastAndFinish("没有读取到可保存的图片")
            return
        }

        val rawText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
        saveSharedImages(uris, rawText, subject)
    }

    private fun saveSharedImages(
        uris: List<Uri>,
        rawText: String,
        subject: String
    ) {
        lifecycleScope.launch {
            val paths = withContext(Dispatchers.IO) {
                ImageStorage.importImages(
                    this@ShareReceiverActivity,
                    uris.take(6)
                )
            }

            if (paths.isEmpty()) {
                toastAndFinish("图片保存失败，请换一种方式导入")
                return@launch
            }

            val title = subject.trim()
                .takeIf { it.isNotBlank() }
                ?: rawText.lineSequence().firstOrNull()?.take(70)
                ?: "分享的图文"

            val id = AppDatabase.get(this@ShareReceiverActivity)
                .favoriteDao()
                .insert(
                    FavoriteEntity(
                        title = title,
                        platform = "图片",
                        rawText = rawText.trim(),
                        knowledgeType = "图文",
                        imagePaths = ImageStorage.encodePaths(paths),
                        processingStatus = "已保存"
                    )
                )

            val prefs = getSharedPreferences("shiguangbox_settings", MODE_PRIVATE)
            if (prefs.getBoolean("auto_favorite_ai", false)) {
                KnowledgeOrganizeWorker.enqueue(this@ShareReceiverActivity, id)
                Toast.makeText(
                    this@ShareReceiverActivity,
                    "图文已收进拾光盒，正在 AI 整理…",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this@ShareReceiverActivity,
                    "图文已收进拾光盒 ✓",
                    Toast.LENGTH_SHORT
                ).show()
            }

            finish()
        }
    }

    private fun toastAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        finish()
    }
}
