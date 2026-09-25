package com.shiguangbox.app

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val versionName: String,
    val title: String,
    val notes: String,
    val apkUrl: String
)

sealed interface UpdateCheckResult {
    data class Available(
        val info: UpdateInfo
    ) : UpdateCheckResult

    data object UpToDate :
        UpdateCheckResult

    data class Error(
        val message: String
    ) : UpdateCheckResult
}

object AppUpdater {

    private const val RELEASE_API =
        "https://api.github.com/repos/AlexMartinDev01/myproject01/releases/latest"

    private const val PREFS =
        "shiguangbox_update"

    private const val KEY_LAST_AUTO_CHECK =
        "last_auto_check"

    private const val KEY_PENDING_DOWNLOAD =
        "pending_download_id"

    private const val KEY_PENDING_VERSION =
        "pending_version"

    private const val APK_MIME =
        "application/vnd.android.package-archive"

    private val autoCheckIntervalMs =
        TimeUnit.HOURS.toMillis(
            24
        )

    suspend fun checkLatestRelease():
        UpdateCheckResult =
        withContext(
            Dispatchers.IO
        ) {
            var connection:
                HttpURLConnection? =
                null

            try {
                connection =
                    (
                        URL(
                            RELEASE_API
                        )
                            .openConnection()
                        as HttpURLConnection
                        )
                        .apply {
                            requestMethod =
                                "GET"
                            connectTimeout =
                                8_000
                            readTimeout =
                                10_000

                            setRequestProperty(
                                "Accept",
                                "application/vnd.github+json"
                            )

                            setRequestProperty(
                                "User-Agent",
                                "ShiguangBox/" +
                                    BuildConfig
                                        .VERSION_NAME
                            )
                        }

                val code =
                    connection
                        .responseCode

                if (code == 404) {
                    return@withContext
                        UpdateCheckResult
                            .Error(
                                "线上正式版本尚未发布"
                            )
                }

                if (
                    code !in
                    200..299
                ) {
                    return@withContext
                        UpdateCheckResult
                            .Error(
                                "检查更新失败（HTTP " +
                                    code +
                                    "）"
                            )
                }

                val body =
                    connection
                        .inputStream
                        .bufferedReader()
                        .use {
                            it.readText()
                        }

                val json =
                    JSONObject(
                        body
                    )

                val tag =
                    json.optString(
                        "tag_name"
                    )

                val versionName =
                    extractVersionName(
                        tag
                    )
                        ?: return@withContext
                            UpdateCheckResult
                                .Error(
                                    "线上版本号格式无法识别"
                                )

                val assets =
                    json.optJSONArray(
                        "assets"
                    )

                var apkUrl:
                    String? =
                    null

                if (assets != null) {
                    for (
                        index in
                        0 until
                            assets.length()
                    ) {
                        val asset =
                            assets
                                .optJSONObject(
                                    index
                                )
                                ?: continue

                        val name =
                            asset
                                .optString(
                                    "name"
                                )

                        val url =
                            asset
                                .optString(
                                    "browser_download_url"
                                )

                        if (
                            name.endsWith(
                                ".apk",
                                ignoreCase =
                                    true
                            ) &&
                            url.isNotBlank()
                        ) {
                            apkUrl =
                                url
                            break
                        }
                    }
                }

                if (
                    apkUrl
                        .isNullOrBlank()
                ) {
                    return@withContext
                        UpdateCheckResult
                            .Error(
                                "线上版本没有找到 APK 安装包"
                            )
                }

                if (
                    compareVersions(
                        versionName,
                        BuildConfig
                            .VERSION_NAME
                    ) <= 0
                ) {
                    return@withContext
                        UpdateCheckResult
                            .UpToDate
                }

                val notes =
                    json.optString(
                        "body"
                    )
                        .trim()
                        .ifBlank {
                            "本次更新包含功能优化与问题修复。"
                        }

                val title =
                    json.optString(
                        "name"
                    )
                        .trim()
                        .ifBlank {
                            "拾光盒 V" +
                                versionName
                        }

                UpdateCheckResult
                    .Available(
                        UpdateInfo(
                            versionName =
                                versionName,
                            title =
                                title,
                            notes =
                                notes,
                            apkUrl =
                                apkUrl
                        )
                    )
            } catch (
                error:
                    Exception
            ) {
                UpdateCheckResult
                    .Error(
                        error.message
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?: "网络连接失败，请稍后再试"
                    )
            } finally {
                connection
                    ?.disconnect()
            }
        }

    fun autoCheckEnabled(
        context: Context
    ): Boolean =
        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .getBoolean(
                "auto_check",
                true
            )

    fun setAutoCheckEnabled(
        context: Context,
        enabled: Boolean
    ) {
        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putBoolean(
                "auto_check",
                enabled
            )
            .apply()
    }

    fun shouldAutoCheck(
        context: Context
    ): Boolean {
        if (
            !autoCheckEnabled(
                context
            )
        ) {
            return false
        }

        val last =
            context
                .getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )
                .getLong(
                    KEY_LAST_AUTO_CHECK,
                    0L
                )

        return System
            .currentTimeMillis() -
            last >=
            autoCheckIntervalMs
    }

    fun markAutoChecked(
        context: Context
    ) {
        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putLong(
                KEY_LAST_AUTO_CHECK,
                System
                    .currentTimeMillis()
            )
            .apply()
    }

    fun enqueueUpdate(
        context: Context,
        info: UpdateInfo
    ): Long {
        val manager =
            context
                .getSystemService(
                    DownloadManager::class.java
                )

        val fileName =
            "ShiguangBox_v" +
                info.versionName +
                ".apk"

        val request =
            DownloadManager
                .Request(
                    Uri.parse(
                        info.apkUrl
                    )
                )
                .setTitle(
                    "拾光盒 V" +
                        info.versionName
                )
                .setDescription(
                    "正在下载更新安装包"
                )
                .setMimeType(
                    APK_MIME
                )
                .setAllowedOverMetered(
                    true
                )
                .setAllowedOverRoaming(
                    true
                )
                .setNotificationVisibility(
                    DownloadManager
                        .Request
                        .VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationInExternalFilesDir(
                    context,
                    Environment
                        .DIRECTORY_DOWNLOADS,
                    fileName
                )

        val downloadId =
            manager.enqueue(
                request
            )

        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putLong(
                KEY_PENDING_DOWNLOAD,
                downloadId
            )
            .putString(
                KEY_PENDING_VERSION,
                info.versionName
            )
            .apply()

        return downloadId
    }

    fun onDownloadComplete(
        activity: Activity,
        downloadId: Long
    ) {
        val prefs =
            activity
                .getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )

        if (
            prefs.getLong(
                KEY_PENDING_DOWNLOAD,
                -1L
            ) != downloadId
        ) {
            return
        }

        tryInstallPendingUpdate(
            activity
        )
    }

    fun tryInstallPendingUpdate(
        activity: Activity
    ): Boolean {
        val prefs =
            activity
                .getSharedPreferences(
                    PREFS,
                    Context.MODE_PRIVATE
                )

        val downloadId =
            prefs.getLong(
                KEY_PENDING_DOWNLOAD,
                -1L
            )

        if (downloadId < 0L) {
            return false
        }

        val manager =
            activity
                .getSystemService(
                    DownloadManager::class.java
                )

        val query =
            DownloadManager
                .Query()
                .setFilterById(
                    downloadId
                )

        val completed =
            manager
                .query(
                    query
                )
                ?.use {
                    cursor ->
                    if (
                        !cursor
                            .moveToFirst()
                    ) {
                        false
                    } else {
                        val statusIndex =
                            cursor
                                .getColumnIndex(
                                    DownloadManager
                                        .COLUMN_STATUS
                                )

                        statusIndex >= 0 &&
                            cursor
                                .getInt(
                                    statusIndex
                                ) ==
                            DownloadManager
                                .STATUS_SUCCESSFUL
                    }
                }
                ?: false

        if (!completed) {
            return false
        }

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O &&
            !activity
                .packageManager
                .canRequestPackageInstalls()
        ) {
            activity
                .startActivity(
                    Intent(
                        Settings
                            .ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse(
                            "package:" +
                                activity
                                    .packageName
                        )
                    )
                )

            return true
        }

        val apkUri =
            manager
                .getUriForDownloadedFile(
                    downloadId
                )
                ?: return false

        prefs
            .edit()
            .remove(
                KEY_PENDING_DOWNLOAD
            )
            .remove(
                KEY_PENDING_VERSION
            )
            .apply()

        activity
            .startActivity(
                Intent(
                    Intent.ACTION_VIEW
                )
                    .setDataAndType(
                        apkUri,
                        APK_MIME
                    )
                    .addFlags(
                        Intent
                            .FLAG_GRANT_READ_URI_PERMISSION
                    )
            )

        return true
    }

    private fun extractVersionName(
        value: String
    ): String? =
        Regex(
            """(\d+(?:\.\d+){1,3})"""
        )
            .find(
                value
            )
            ?.groupValues
            ?.getOrNull(
                1
            )

    private fun compareVersions(
        remote: String,
        local: String
    ): Int {
        val remoteParts =
            remote
                .split(
                    "."
                )
                .map {
                    it.toIntOrNull()
                        ?: 0
                }

        val localParts =
            local
                .split(
                    "."
                )
                .map {
                    it.toIntOrNull()
                        ?: 0
                }

        val size =
            maxOf(
                remoteParts.size,
                localParts.size
            )

        for (
            index in
            0 until size
        ) {
            val remotePart =
                remoteParts
                    .getOrElse(
                        index
                    ) {
                        0
                    }

            val localPart =
                localParts
                    .getOrElse(
                        index
                    ) {
                        0
                    }

            if (
                remotePart !=
                localPart
            ) {
                return remotePart
                    .compareTo(
                        localPart
                    )
            }
        }

        return 0
    }
}
