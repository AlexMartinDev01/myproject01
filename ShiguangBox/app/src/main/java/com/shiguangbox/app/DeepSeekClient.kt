package com.shiguangbox.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object DeepSeekClient {

    const val MODEL = "deepseek-flash"
    private const val ENDPOINT = "https://api.deepseek.com/chat/completions"

    data class Result(
        val success: Boolean,
        val content: String = "",
        val error: String = ""
    )

    fun testConnection(apiKey: String): Result {
        return chat(
            apiKey = apiKey,
            system = "你是一个接口连通性测试助手。",
            user = "只回复四个字：连接成功",
            maxTokens = 32
        )
    }

    fun summarizeFavorite(
        apiKey: String,
        title: String,
        platform: String,
        rawText: String,
        note: String
    ): Result {
        val content = buildString {
            appendLine("来源：" + platform)
            appendLine("标题：" + title)
            appendLine("用户收藏时可获得的原始内容：")
            appendLine(rawText.ifBlank { "无" })
            if (note.isNotBlank()) {
                appendLine()
                appendLine("用户备注：")
                appendLine(note)
            }
        }

        return chat(
            apiKey = apiKey,
            system = """
                你是“拾光盒”的知识整理助手。只允许基于用户提供的文字整理，不要假装看过无法访问的视频、书籍或网页。
                如果信息不足，请明确写“当前可用信息有限”，不要补写未提供的事实。
                输出必须简洁，使用以下结构：
                【一句话概要】
                【核心要点】
                1.
                2.
                3.
                【值得记住】
                【建议标签】
                标签用“·”分隔，3到5个。
            """.trimIndent(),
            user = content,
            maxTokens = 700
        )
    }

    fun dailySummary(
        apiKey: String,
        date: String,
        completedTasks: List<String>,
        pendingTasks: List<String>,
        notes: List<String>,
        favorites: List<String>
    ): Result {
        val payload = buildString {
            appendLine("日期：" + date)
            appendLine()
            appendLine("【今天完成的待办】")
            if (completedTasks.isEmpty()) appendLine("无")
            else completedTasks.forEach { appendLine("- " + it) }

            appendLine()
            appendLine("【今天未完成的待办】")
            if (pendingTasks.isEmpty()) appendLine("无")
            else pendingTasks.forEach { appendLine("- " + it) }

            appendLine()
            appendLine("【今天的随手记录】")
            if (notes.isEmpty()) appendLine("无")
            else notes.forEach { appendLine("- " + it) }

            appendLine()
            appendLine("【今天收藏的内容标题】")
            if (favorites.isEmpty()) appendLine("无")
            else favorites.forEach { appendLine("- " + it) }
        }

        return chat(
            apiKey = apiKey,
            system = """
                你是“拾光盒”的每日整理助手。
                只能根据用户提供的真实数据总结，不要编造用户做过、想过或学过的事情。
                不要替用户做人生决策。
                输出自然、温柔、简洁，适合作为日记和工作总结。
                请使用以下结构：
                【今天完成了什么】
                【今天发生了什么】
                【今天值得记住】
                【还没完成】
                【给明天留一句话】
            """.trimIndent(),
            user = payload,
            maxTokens = 900
        )
    }

    private fun chat(
        apiKey: String,
        system: String,
        user: String,
        maxTokens: Int
    ): Result {
        if (apiKey.isBlank()) {
            return Result(false, error = "还没有配置 DeepSeek API Key")
        }

        var connection: HttpURLConnection? = null

        return try {
            connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 45000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer " + apiKey)
                setRequestProperty("User-Agent", "ShiguangBox/0.4")
            }

            val body = JSONObject().apply {
                put("model", MODEL)
                put(
                    "messages",
                    JSONArray().apply {
                        put(JSONObject().put("role", "system").put("content", system))
                        put(JSONObject().put("role", "user").put("content", user))
                    }
                )
                put("thinking", JSONObject().put("type", "disabled"))
                put("max_tokens", maxTokens)
                put("stream", false)
            }

            connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val response = BufferedReader(
                InputStreamReader(stream, Charsets.UTF_8)
            ).use { it.readText() }

            val json = JSONObject(response)

            if (code !in 200..299) {
                val message = json.optJSONObject("error")
                    ?.optString("message")
                    ?.takeIf { it.isNotBlank() }
                    ?: ("DeepSeek 请求失败（HTTP " + code + "）")

                Result(false, error = message)
            } else {
                val content = json
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()

                if (content.isBlank()) {
                    Result(false, error = "DeepSeek 返回了空内容")
                } else {
                    Result(true, content = content.trim())
                }
            }
        } catch (e: Exception) {
            Result(false, error = e.message ?: "网络请求失败")
        } finally {
            connection?.disconnect()
        }
    }
}
