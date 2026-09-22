package com.shiguangbox.app

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class PetParsedTask(
    val title: String,
    val dueAt: Long?
)

object PetTaskParser {

    fun parse(input: String): PetParsedTask? {
        val raw = input.trim()
        if (raw.isBlank()) return null

        var date = LocalDate.now()
        val hasExplicitDate = raw.contains("今天") ||
            raw.contains("明天") ||
            raw.contains("后天")

        when {
            raw.contains("后天") -> date = date.plusDays(2)
            raw.contains("明天") -> date = date.plusDays(1)
        }

        var hour: Int? = null
        var minute = 0

        Regex("(\\d{1,2}):(\\d{2})").find(raw)?.let {
            hour = it.groupValues[1].toIntOrNull()
            minute = it.groupValues[2].toIntOrNull() ?: 0
        }

        if (hour == null) {
            Regex("(\\d{1,2})点(半)?").find(raw)?.let {
                hour = it.groupValues[1].toIntOrNull()
                if (it.groupValues[2].isNotBlank()) minute = 30
            }
        }

        if (hour == null) {
            val chineseHours = mapOf(
                "一" to 1, "二" to 2, "两" to 2, "三" to 3,
                "四" to 4, "五" to 5, "六" to 6, "七" to 7,
                "八" to 8, "九" to 9, "十" to 10,
                "十一" to 11, "十二" to 12
            )
            Regex("([一二两三四五六七八九十]{1,2})点(半)?").find(raw)?.let {
                hour = chineseHours[it.groupValues[1]]
                if (it.groupValues[2].isNotBlank()) minute = 30
            }
        }

        if (hour != null) {
            if ((raw.contains("下午") || raw.contains("晚上")) && hour!! < 12) {
                hour = hour!! + 12
            }
            if (raw.contains("中午") && hour!! < 11) {
                hour = hour!! + 12
            }
        }

        val title = raw
            .replace("今天", "")
            .replace("明天", "")
            .replace("后天", "")
            .replace("早上", "")
            .replace("上午", "")
            .replace("下午", "")
            .replace("中午", "")
            .replace("晚上", "")
            .replace(Regex("\\d{1,2}:\\d{2}"), "")
            .replace(Regex("\\d{1,2}点(半)?"), "")
            .replace(Regex("[一二两三四五六七八九十]{1,2}点(半)?"), "")
            .replace("提醒我", "")
            .replace("提醒", "")
            .replace("记得", "")
            .trim()
            .ifBlank { raw }

        val dueAt = hour?.let {
            var target = date.atTime(
                LocalTime.of(it.coerceIn(0, 23), minute.coerceIn(0, 59))
            ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            if (!hasExplicitDate && target <= System.currentTimeMillis()) {
                target = date.plusDays(1)
                    .atTime(LocalTime.of(it.coerceIn(0, 23), minute.coerceIn(0, 59)))
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }
            target
        }

        return PetParsedTask(title = title, dueAt = dueAt)
    }
}
