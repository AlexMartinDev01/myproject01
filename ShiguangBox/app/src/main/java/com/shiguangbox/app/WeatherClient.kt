package com.shiguangbox.app

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

data class WeatherSnapshot(
    val city: String,
    val temperature: Double,
    val apparentTemperature: Double,
    val minTemperature: Double,
    val maxTemperature: Double,
    val rainProbability: Int,
    val weatherCode: Int,
    val condition: String,
    val advice: String
)

object WeatherClient {

    fun fetch(city: String): Result<WeatherSnapshot> {
        return runCatching {
            val location = geocode(city)
                ?: error("没有找到“" + city + "”，请到“我的”里检查常用城市")

            val forecastUrl =
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=" + location.first +
                    "&longitude=" + location.second +
                    "&current=temperature_2m,apparent_temperature,weather_code" +
                    "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code" +
                    "&timezone=auto&forecast_days=2"

            val json = JSONObject(get(forecastUrl))
            val current = json.getJSONObject("current")
            val daily = json.getJSONObject("daily")

            val temp = current.optDouble("temperature_2m", 0.0)
            val apparent = current.optDouble("apparent_temperature", temp)
            val code = current.optInt("weather_code", 0)
            val max = daily.getJSONArray("temperature_2m_max").optDouble(0, temp)
            val min = daily.getJSONArray("temperature_2m_min").optDouble(0, temp)
            val rain = daily.getJSONArray("precipitation_probability_max").optInt(0, 0)

            WeatherSnapshot(
                city = city,
                temperature = temp,
                apparentTemperature = apparent,
                minTemperature = min,
                maxTemperature = max,
                rainProbability = rain,
                weatherCode = code,
                condition = weatherText(code),
                advice = makeAdvice(temp, apparent, rain, code)
            )
        }
    }

    private fun geocode(city: String): Pair<Double, Double>? {
        val encoded = URLEncoder.encode(city.trim(), "UTF-8")
        val url =
            "https://geocoding-api.open-meteo.com/v1/search?name=" +
                encoded +
                "&count=1&language=zh&format=json"

        val json = JSONObject(get(url))
        val results = json.optJSONArray("results") ?: return null
        if (results.length() == 0) return null

        val first = results.getJSONObject(0)
        return first.getDouble("latitude") to first.getDouble("longitude")
    }

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 12000
            connection.readTimeout = 12000
            connection.setRequestProperty("User-Agent", "ShiguangBox/0.4")

            val code = connection.responseCode
            val stream =
                if (code in 200..299) connection.inputStream
                else connection.errorStream

            val body = BufferedReader(
                InputStreamReader(stream, Charsets.UTF_8)
            ).use { it.readText() }

            if (code !in 200..299) {
                error("天气服务请求失败（HTTP " + code + "）")
            }

            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun weatherText(code: Int): String = when (code) {
        0 -> "晴"
        1 -> "大致晴朗"
        2 -> "多云"
        3 -> "阴"
        45, 48 -> "有雾"
        51, 53, 55, 56, 57 -> "毛毛雨"
        61, 63, 65, 66, 67 -> "下雨"
        71, 73, 75, 77 -> "下雪"
        80, 81, 82 -> "阵雨"
        85, 86 -> "阵雪"
        95, 96, 99 -> "雷雨"
        else -> "天气变化"
    }

    private fun makeAdvice(
        temp: Double,
        apparent: Double,
        rain: Int,
        code: Int
    ): String {
        return when {
            rain >= 60 || code in listOf(61, 63, 65, 80, 81, 82, 95, 96, 99) ->
                "今天带伞更稳妥。"
            apparent <= 8 ->
                "体感偏冷，出门多穿一点。"
            apparent >= 30 ->
                "体感偏热，记得补水和防晒。"
            temp in 18.0..27.0 ->
                "温度比较舒服，适合安排出门。"
            else ->
                "按今天的温度安排好衣物就行。"
        }
    }
}
