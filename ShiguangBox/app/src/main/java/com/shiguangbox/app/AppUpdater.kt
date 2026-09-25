package com.shiguangbox.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
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

    private const val KEY_PENDING_APK_PATH =
        "pending_apk_path"

    private const val KEY_INSTALL_PERMISSION_REQUESTED =
        "install_permission_requested"

    private const val APK_MIME =
        "application/vnd.android.package-archive"

    private val autoCheckIntervalMs =
        TimeUnit.HOURS.toMillis(24)

    private val scope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.Main.immediate
        )

    suspend fun checkLatestRelease(
        context: Context
    ): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            fetchLatestRelease(context)
        }

    fun shouldAutoCheck(
        context: Context
    ): Boolean {
        val prefs =
            context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )

        val last =
            prefs.getLong(
                KEY_LAST_AUTO_CHECK,
                0L
            )

        return System.currentTimeMillis() -
            last >=
            TimeUnit.HOURS.toMillis(1)
    }

    fun markAutoChecked(
        context: Context
    ) {
        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
            .edit()
            .putLong(
                KEY_LAST_AUTO_CHECK,
                System.currentTimeMillis()
            )
            .apply()
    }

    fun enqueueUpdate(
        context: Context,
        info: UpdateInfo
    ) {
        val activity =
            context as? Activity

        if (activity == null) {
            Toast.makeText(
                context,
                "无法启动更新，请重新打开拾光盒后再试",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        Toast.makeText(
            activity,
            "正在下载 V" +
                info.versionName +
                "，请稍候…",
            Toast.LENGTH_SHORT
        ).show()

        scope.launch {
            val result =
                withContext(
                    Dispatchers.IO
                ) {
                    downloadWithRetry(
                        activity.applicationContext,
                        info
                    )
                }

            result.fold(
                onSuccess = { apkFile ->
                    savePendingApk(
                        activity,
                        apkFile
                    )

                    Toast.makeText(
                        activity,
                        "下载完成，正在打开安装页面",
                        Toast.LENGTH_SHORT
                    ).show()

                    tryInstallPendingUpdate(
                        activity
                    )
                },
                onFailure = { error ->
                    Toast.makeText(
                        activity,
                        "更新下载失败：" +
                            (
                                error.message
                                    ?: "网络连接异常"
                                ),
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    fun onDownloadComplete(
        activity: Activity,
        downloadId: Long
    ) {
        // Kept for compatibility with the existing MainActivity receiver.
        // The updater no longer relies on Android DownloadManager.
    }

    fun tryInstallPendingUpdate(
        activity: Activity
    ): Boolean {
        val prefs =
            activity.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )

        val path =
            prefs.getString(
                KEY_PENDING_APK_PATH,
                null
            )
                ?: return false

        val apkFile =
            File(path)

        if (
            !apkFile.exists() ||
            apkFile.length() <= 0L
        ) {
            prefs.edit()
                .remove(
                    KEY_PENDING_APK_PATH
                )
                .apply()
            return false
        }

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O &&
            !activity.packageManager
                .canRequestPackageInstalls()
        ) {
            val alreadyRequested =
                prefs.getBoolean(
                    KEY_INSTALL_PERMISSION_REQUESTED,
                    false
                )

            if (alreadyRequested) {
                return false
            }

            prefs.edit()
                .putBoolean(
                    KEY_INSTALL_PERMISSION_REQUESTED,
                    true
                )
                .apply()

            activity.startActivity(
                Intent(
                    Settings
                        .ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse(
                        "package:" +
                            activity.packageName
                    )
                )
            )

            return true
        }

        prefs.edit()
            .putBoolean(
                KEY_INSTALL_PERMISSION_REQUESTED,
                false
            )
            .apply()

        val apkUri =
            FileProvider.getUriForFile(
                activity,
                activity.packageName +
                    ".fileprovider",
                apkFile
            )

        activity.startActivity(
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

    private fun savePendingApk(
        context: Context,
        apkFile: File
    ) {
        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
            .edit()
            .putString(
                KEY_PENDING_APK_PATH,
                apkFile.absolutePath
            )
            .putBoolean(
                KEY_INSTALL_PERMISSION_REQUESTED,
                false
            )
            .apply()
    }

    private fun downloadWithRetry(
        context: Context,
        info: UpdateInfo
    ): Result<File> {
        var lastError:
            Throwable? =
            null

        repeat(3) { attempt ->
            try {
                return Result.success(
                    downloadApk(
                        context,
                        info
                    )
                )
            } catch (
                error: Throwable
            ) {
                lastError =
                    error

                if (attempt < 2) {
                    Thread.sleep(
                        900L *
                            (attempt + 1)
                    )
                }
            }
        }

        return Result.failure(
            lastError
                ?: IllegalStateException(
                    "下载失败"
                )
        )
    }

    private fun downloadApk(
        context: Context,
        info: UpdateInfo
    ): File {
        val updateDir =
            File(
                context.cacheDir,
                "updates"
            )

        if (
            !updateDir.exists() &&
            !updateDir.mkdirs()
        ) {
            throw IllegalStateException(
                "无法创建更新缓存目录"
            )
        }

        updateDir
            .listFiles()
            ?.filter {
                it.extension.equals(
                    "apk",
                    ignoreCase = true
                )
            }
            ?.forEach {
                runCatching {
                    it.delete()
                }
            }

        val tempFile =
            File(
                updateDir,
                "ShiguangBox_v" +
                    info.versionName +
                    ".apk.part"
            )

        val finalFile =
            File(
                updateDir,
                "ShiguangBox_v" +
                    info.versionName +
                    ".apk"
            )

        var currentUrl =
            URL(
                info.apkUrl
            )

        var connection:
            HttpURLConnection? =
            null

        try {
            repeat(8) {
                connection =
                    (
                        currentUrl
                            .openConnection()
                        as HttpURLConnection
                        )
                        .apply {
                            requestMethod =
                                "GET"
                            instanceFollowRedirects =
                                false
                            connectTimeout =
                                15_000
                            readTimeout =
                                45_000
                            useCaches =
                                false

                            setRequestProperty(
                                "Accept",
                                "application/octet-stream"
                            )

                            setRequestProperty(
                                "User-Agent",
                                "ShiguangBox-Android-Updater"
                            )

                            setRequestProperty(
                                "Connection",
                                "close"
                            )
                        }

                val code =
                    connection!!
                        .responseCode

                if (
                    code == 301 ||
                    code == 302 ||
                    code == 303 ||
                    code == 307 ||
                    code == 308
                ) {
                    val location =
                        connection!!
                            .getHeaderField(
                                "Location"
                            )
                            ?: throw IllegalStateException(
                                "更新服务器重定向地址缺失"
                            )

                    val nextUrl =
                        URL(
                            currentUrl,
                            location
                        )

                    connection
                        ?.disconnect()

                    connection =
                        null

                    currentUrl =
                        nextUrl

                    return@repeat
                }

                if (
                    code !in
                    200..299
                ) {
                    throw IllegalStateException(
                        "下载服务器返回 HTTP " +
                            code
                    )
                }

                tempFile
                    .outputStream()
                    .buffered(
                        64 * 1024
                    )
                    .use {
                        output ->
                        connection!!
                            .inputStream
                            .buffered(
                                64 * 1024
                            )
                            .use {
                                input ->
                                input.copyTo(
                                    output,
                                    64 * 1024
                                )
                            }
                    }

                if (
                    tempFile.length() <
                    100_000L
                ) {
                    throw IllegalStateException(
                        "下载到的安装包不完整"
                    )
                }

                if (
                    finalFile.exists()
                ) {
                    finalFile.delete()
                }

                if (
                    !tempFile.renameTo(
                        finalFile
                    )
                ) {
                    tempFile.copyTo(
                        finalFile,
                        overwrite = true
                    )
                    tempFile.delete()
                }

                validateDownloadedApk(
                    context,
                    finalFile
                )

                return finalFile
            }

            throw IllegalStateException(
                "更新下载重定向次数过多"
            )
        } finally {
            connection
                ?.disconnect()

            if (
                tempFile.exists()
            ) {
                tempFile.delete()
            }
        }
    }

    private fun validateDownloadedApk(
        context: Context,
        apkFile: File
    ) {
        val info =
            context.packageManager
                .getPackageArchiveInfo(
                    apkFile.absolutePath,
                    0
                )
                ?: throw IllegalStateException(
                    "下载文件不是有效 APK"
                )

        if (
            info.packageName !=
            context.packageName
        ) {
            throw IllegalStateException(
                "安装包应用标识不匹配"
            )
        }
    }

    private fun fetchLatestRelease(
        context: Context
    ): UpdateCheckResult {
        var connection:
            HttpURLConnection? =
            null

        return try {
            connection =
                (
                    URL(RELEASE_API)
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
                                localVersionName(context)
                        )
                    }

            val code =
                connection.responseCode

            if (code == 404) {
                return UpdateCheckResult.UpToDate
            }

            if (code !in 200..299) {
                return UpdateCheckResult.Error(
                    "检查更新失败（HTTP " +
                        code +
                        "）"
                )
            }

            val body =
                connection.inputStream
                    .bufferedReader()
                    .use {
                        it.readText()
                    }

            val json =
                JSONObject(body)

            val versionName =
                extractVersionName(
                    json.optString("tag_name")
                )
                    ?: return UpdateCheckResult.Error(
                        "线上版本号格式无法识别"
                    )

            val assets =
                json.optJSONArray("assets")

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
                            .optJSONObject(index)
                            ?: continue

                    val name =
                        asset
                            .optString("name")

                    val url =
                        asset
                            .optString(
                                "browser_download_url"
                            )

                    if (
                        name.endsWith(
                            ".apk",
                            ignoreCase = true
                        ) &&
                        url.isNotBlank()
                    ) {
                        apkUrl =
                            url
                        break
                    }
                }
            }

            val finalApkUrl =
                apkUrl?.takeIf {
                    it.isNotBlank()
                }
                    ?: return UpdateCheckResult.Error(
                        "线上版本没有找到 APK 安装包"
                    )

            if (
                compareVersions(
                    versionName,
                    localVersionName(context)
                ) <= 0
            ) {
                return UpdateCheckResult.UpToDate
            }

            val title =
                json.optString("name")
                    .trim()
                    .ifBlank {
                        "拾光盒 V" +
                            versionName
                    }

            val notes =
                json.optString("body")
                    .trim()
                    .ifBlank {
                        "本次更新包含功能优化与问题修复。"
                    }

            UpdateCheckResult.Available(
                UpdateInfo(
                    versionName =
                        versionName,
                    title =
                        title,
                    notes =
                        notes,
                    apkUrl =
                        finalApkUrl
                )
            )
        } catch (
            error: Exception
        ) {
            UpdateCheckResult.Error(
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

    private fun localVersionName(
        context: Context
    ): String =
        runCatching {
            context.packageManager
                .getPackageInfo(
                    context.packageName,
                    0
                )
                .versionName
                ?: "0.0.0"
        }
            .getOrDefault(
                "0.0.0"
            )

    private fun extractVersionName(
        value: String
    ): String? =
        Regex(
            """(\d+(?:\.\d+){1,3})"""
        )
            .find(value)
            ?.groupValues
            ?.getOrNull(1)

    private fun compareVersions(
        remote: String,
        local: String
    ): Int {
        val remoteParts =
            remote.split(".")
                .map {
                    it.toIntOrNull()
                        ?: 0
                }

        val localParts =
            local.split(".")
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
                    .getOrElse(index) {
                        0
                    }

            val localPart =
                localParts
                    .getOrElse(index) {
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
