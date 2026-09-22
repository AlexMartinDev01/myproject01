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
        val material = buildString {
            appendLine("来源：" + platform)
            appendLine("标题：" + title)
            appendLine("原始内容：")
            appendLine(rawText.ifBlank { "无" })
            if (note.isNotBlank()) {
                appendLine()
                appendLine("用户备注：")
                appendLine(note)
            }
        }

        return chat(
            apiKey = apiKey,
            system = knowledgeSystemPrompt(),
            user = material,
            maxTokens = 900
        )
    }

    fun analyzeKnowledgeImages(
        apiKey: String,
        title: String,
        text: String,
        note: String,
        imageBase64List: List<String>
    ): Result {
        if (imageBase64List.isEmpty()) {
            return Result(false, error = "没有可分析的图片")
        }

        val parts = JSONArray()
        parts.put(
            JSONObject()
                .put("type", "text")
                .put(
                    "text",
                    buildString {
                        appendLine("请把这些图片和附带文字整理成一张可靠的个人知识卡。")
                        appendLine("标题：" + title)
                        if (text.isNotBlank()) {
                            appendLine("附带文字：")
                            appendLine(text)
                        }
                        if (note.isNotBlank()) {
                            appendLine("我的备注：")
                            appendLine(note)
                        }
                        appendLine()
                        appendLine("如果图片是截图、书页、PPT、图表、聊天记录或网页，请读取其中真正可见的信息；看不清的部分不要猜。")
                    }
                )
        )

        imageBase64List.take(6).forEach { base64 ->
            parts.put(
                JSONObject()
                    .put("type", "image_url")
                    .put(
                        "image_url",
                        JSONObject()
                            .put("url", "data:image/jpeg;base64," + base64)
                            .put("detail", "original")
                    )
            )
        }

        return chatWithParts(
            apiKey = apiKey,
            system = knowledgeSystemPrompt(),
            userParts = parts,
            maxTokens = 1400
        )
    }

    fun answerFromMemory(
        apiKey: String,
        question: String,
        contextItems: List<String>
    ): Result {
        val material = buildString {
            appendLine("用户问题：" + question)
            appendLine()
            appendLine("以下内容来自用户自己的拾光盒：")
            if (contextItems.isEmpty()) {
                appendLine("没有找到明确相关的历史内容。")
            } else {
                contextItems.take(24).forEachIndexed { index, item ->
                    appendLine("[" + (index + 1) + "] " + item.take(1600))
                }
            }
        }

        return chat(
            apiKey = apiKey,
            system = """
                你是“拾光盒”的个人知识库问答助手。
                你的主要依据只能是用户提供给你的拾光盒检索结果。
                如果资料不足，请明确说“我在你的拾光盒里暂时没找到足够依据”，不要编造用户的历史、经历或收藏内容。
                如果能回答，先给直接结论，再说明主要依据来自哪些记录、知识卡或每日总结。
                可以综合多条资料，但不要把外部常识伪装成用户自己的资料。
                中文优先，结构清晰，避免空话。
            """.trimIndent(),
            user = material,
            maxTokens = 1300
        )
    }

    fun summarizeTopic(
        apiKey: String,
        topic: String,
        items: List<String>
    ): Result {
        val material = buildString {
            appendLine("专题：" + topic)
            appendLine("专题内共有 " + items.size + " 条资料。")
            appendLine()
            items.take(80).forEachIndexed { index, item ->
                appendLine("资料" + (index + 1) + "：")
                appendLine(item.take(2200))
                appendLine()
            }
        }

        return chat(
            apiKey = apiKey,
            system = """
                你是“拾光盒”的专题知识整理助手。
                只能基于用户专题中的真实资料进行综合，不要补写资料中没有的信息。
                输出：
                【专题概览】
                【已经积累的核心知识】
                1.
                2.
                3.
                【重复出现的观点】
                【不同资料之间的补充关系】
                【目前资料中相对缺少的部分】
                【值得再次回顾的内容】
                “缺少的部分”只描述现有资料覆盖不足，不替用户决定必须学习什么。
            """.trimIndent(),
            user = material,
            maxTokens = 1800
        )
    }

    fun weeklyKnowledgeReport(
        apiKey: String,
        items: List<String>
    ): Result {
        val material = buildString {
            appendLine("以下是用户最近7天加入拾光盒的知识资料：")
            if (items.isEmpty()) {
                appendLine("无")
            } else {
                items.take(100).forEachIndexed { index, item ->
                    appendLine("[" + (index + 1) + "] " + item.take(1800))
                }
            }
        }

        return chat(
            apiKey = apiKey,
            system = """
                你是“拾光盒”的每周知识回顾助手。
                只总结这一周真实新增的资料，不猜测用户没有表达过的兴趣或目标。
                输出：
                【本周积累了什么】
                【高频主题】
                【本周最值得记住的5点】
                【内容之间的关联】
                【适合下周回顾的旧知识】
                最后一项只推荐回顾，不替用户设定任务。
            """.trimIndent(),
            user = material,
            maxTokens = 1600
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
            appendLine("【今天收藏/整理的知识】")
            if (favorites.isEmpty()) appendLine("无")
            else favorites.forEach { appendLine("- " + it) }
        }

        return chat(
            apiKey = apiKey,
            system = """
                你是“拾光盒”的每日整理助手。
                只能根据用户提供的真实数据总结，不要编造用户做过、想过或学过的事情。
                输出自然、简洁，适合作为日记和工作总结。
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

    private fun knowledgeSystemPrompt(): String = """
        你是“拾光盒”的个人知识整理助手。
        只根据用户给出的文字和图片中真实可见的信息整理，不要凭空补充。
        对截图、书页、PPT、图表、网页图片：可以识别并概括可见文字与结构；模糊或被遮挡的部分要明确不确定。
        输出固定使用以下结构：
        【一句话概要】
        【核心知识】
        1.
        2.
        3.
        【图片/原文中的关键信息】
        【值得记住】
        【可行动内容】
        没有明确行动含义时写“无明确行动项”，不要强行制造任务。
        【建议标签】
        用“·”分隔3到6个。
        【建议专题】
        只给一个简短专题名，例如“秋招”“自媒体”“AI学习”“材料学习”；无法判断时写“待分类”。
        【关联关键词】
        用“·”分隔3到8个，用于以后寻找相关知识。
    """.trimIndent()

    private fun chat(
        apiKey: String,
        system: String,
        user: String,
        maxTokens: Int
    ): Result {
        val parts = JSONArray().put(
            JSONObject().put("type", "text").put("text", user)
        )
        return chatWithParts(apiKey, system, parts, maxTokens)
    }

    private fun chatWithParts(
        apiKey: String,
        system: String,
        userParts: JSONArray,
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
                readTimeout = 90000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer " + apiKey)
                setRequestProperty("User-Agent", "ShiguangBox/0.6")
            }

            val body = JSONObject().apply {
                put("model", MODEL)
                put(
                    "messages",
                    JSONArray().apply {
                        put(JSONObject().put("role", "system").put("content", system))
                        put(JSONObject().put("role", "user").put("content", userParts))
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
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
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
                val answer = json
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()

                if (answer.isBlank()) {
                    Result(false, error = "DeepSeek 返回了空内容")
                } else {
                    Result(true, content = answer.trim())
                }
            }
        } catch (e: Exception) {
            Result(false, error = e.message ?: "网络请求失败")
        } finally {
            connection?.disconnect()
        }
    }
}
