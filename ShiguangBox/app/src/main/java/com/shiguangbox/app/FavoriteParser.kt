package com.shiguangbox.app

object FavoriteParser {

    private val urlRegex = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)

    fun parse(rawText: String, subject: String? = null): FavoriteEntity {
        val cleaned = rawText.trim()
        val rawUrl = urlRegex.find(cleaned)?.value.orEmpty()
        val url = rawUrl.trimEnd('.', ',', '，', '。', ';', '；', ')', '）', ']', '】')

        val platform = detectPlatform(cleaned, url)

        val subjectTitle = subject
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("分享", ignoreCase = true) }

        val withoutUrl = if (url.isNotBlank()) {
            cleaned.replace(rawUrl, "").trim()
        } else {
            cleaned
        }

        val compact = withoutUrl
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', '—', '|', '：', ':')

        val title = subjectTitle
            ?: compact.takeIf { it.isNotBlank() }?.take(90)
            ?: when (platform) {
                "抖音" -> "来自抖音的收藏"
                "B站" -> "来自B站的收藏"
                "微信读书" -> "来自微信读书的收藏"
                "网页" -> "网页收藏"
                else -> "新的收藏"
            }

        return FavoriteEntity(
            title = title,
            url = url,
            platform = platform,
            rawText = cleaned
        )
    }

    fun detectPlatform(text: String, url: String): String {
        val source = (text + " " + url).lowercase()
        return when {
            "douyin.com" in source ||
                "v.douyin.com" in source ||
                "抖音" in text -> "抖音"

            "bilibili.com" in source ||
                "b23.tv" in source ||
                "哔哩哔哩" in text ||
                "b站" in text.lowercase() -> "B站"

            "weread.qq.com" in source ||
                "微信读书" in text -> "微信读书"

            url.isNotBlank() -> "网页"
            else -> "其他"
        }
    }
}
