package com.shiguangbox.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

object ImageStorage {

    fun importImage(context: Context, uri: Uri): String {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("无法读取图片")

        val bitmap = input.use { BitmapFactory.decodeStream(it) }
            ?: error("图片格式无法识别")

        val normalized = scaleDown(bitmap, 1800)
        val dir = File(context.filesDir, "knowledge_images").apply { mkdirs() }
        val file = File(dir, UUID.randomUUID().toString() + ".jpg")

        file.outputStream().use {
            normalized.compress(Bitmap.CompressFormat.JPEG, 84, it)
        }

        if (normalized !== bitmap) normalized.recycle()
        bitmap.recycle()

        return file.absolutePath
    }

    fun importImages(context: Context, uris: List<Uri>): List<String> {
        return uris.take(6).mapNotNull { uri ->
            runCatching { importImage(context, uri) }.getOrNull()
        }
    }

    fun toBase64(path: String): String? {
        return runCatching {
            val bytes = File(path).readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        }.getOrNull()
    }

    fun encodePaths(paths: List<String>): String {
        val array = JSONArray()
        paths.forEach { array.put(it) }
        return array.toString()
    }

    fun decodePaths(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val path = array.optString(i)
                    if (path.isNotBlank()) add(path)
                }
            }
        }.getOrElse { emptyList() }
    }

    fun deletePaths(json: String) {
        decodePaths(json).forEach { path ->
            runCatching { File(path).delete() }
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxSide) return bitmap

        val ratio = maxSide.toFloat() / largest.toFloat()
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }
}
