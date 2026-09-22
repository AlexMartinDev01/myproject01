package com.shiguangbox.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) {
            finish()
            return
        }

        val rawText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)

        if (rawText.isBlank()) {
            Toast.makeText(this, "这条分享暂时没有可保存的文字或链接", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            val parsed = FavoriteParser.parse(rawText, subject)
            val dao = AppDatabase.get(this@ShareReceiverActivity).favoriteDao()

            val existing = parsed.url
                .takeIf { it.isNotBlank() }
                ?.let { dao.findByUrl(it) }

            val favoriteId: Long

            if (existing != null) {
                val updated = existing.copy(
                    title = parsed.title,
                    platform = parsed.platform,
                    rawText = parsed.rawText,
                    updatedAt = System.currentTimeMillis()
                )
                dao.update(updated)
                favoriteId = existing.id

                Toast.makeText(
                    this@ShareReceiverActivity,
                    "这个内容已经在拾光盒里啦 ✓",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                favoriteId = dao.insert(parsed)

                Toast.makeText(
                    this@ShareReceiverActivity,
                    "已收进拾光盒 ✓",
                    Toast.LENGTH_SHORT
                ).show()
            }

            val prefs = getSharedPreferences(
                "shiguangbox_settings",
                MODE_PRIVATE
            )
            if (prefs.getBoolean("auto_favorite_ai", false)) {
                FavoriteAiWorker.enqueue(
                    this@ShareReceiverActivity,
                    favoriteId
                )
            }

            finish()
        }
    }
}
