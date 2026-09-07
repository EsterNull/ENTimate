package com.example.entimate.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val version: String,
    val downloadUrl: String,
    val releaseName: String,
    val body: String,
)

object UpdateChecker {
    private const val API_URL = "https://api.github.com/repos/EsterNull/ENTimate/releases/latest"
    private const val USER_AGENT = "ENTimate"

    suspend fun check(): UpdateInfo = withContext(Dispatchers.IO) {
        val conn = URL(API_URL).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            val code = conn.responseCode
            if (code !in 200..299) {
                throw RuntimeException("GitHub: HTTP $code")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val assets = json.optJSONArray("assets")
            val downloadUrl = (0 until (assets?.length() ?: 0))
                .map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }
                ?.optString("browser_download_url")
                ?: throw RuntimeException("В релизе не найден APK-файл")
            UpdateInfo(
                version = json.optString("tag_name", "").removePrefix("v"),
                downloadUrl = downloadUrl,
                releaseName = json.optString("name", "").ifBlank { json.optString("tag_name", "") },
                body = json.optString("body", ""),
            )
        } finally {
            conn.disconnect()
        }
    }

    fun isNewer(latest: String, current: String): Boolean {
        val latestParts = parse(latest)
        val currentParts = parse(current)
        if (latestParts.isEmpty() || currentParts.isEmpty()) return false
        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val l = latestParts.getOrNull(i) ?: 0
            val c = currentParts.getOrNull(i) ?: 0
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    private fun parse(version: String): List<Int> =
        version.split('.', '-', '+').mapNotNull { it.toIntOrNull() }
}