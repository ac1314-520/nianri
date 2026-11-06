package com.nianri.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import com.nianri.app.BuildConfig
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val notes: String,
    val publishedAt: String,
)

sealed class UpdateCheckResult {
    data object UpToDate : UpdateCheckResult()
    data class Available(val info: UpdateInfo) : UpdateCheckResult()
    data class Failed(val message: String) : UpdateCheckResult()
}

enum class InstallResult {
    STARTED,
    NEED_INSTALL_PERMISSION,
    FAILED,
}

object UpdateManager {
    private const val MAX_MANIFEST_BYTES = 512 * 1024
    private const val MAX_APK_BYTES = 200L * 1024 * 1024
    private const val USER_AGENT = "NianRi-Android/${BuildConfig.VERSION_NAME}"
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun check(rawUrl: String, callback: (UpdateCheckResult) -> Unit) {
        io.execute {
            val result = try {
                val manifestUrl = normalizeHttpsUrl(rawUrl, null)
                    ?: return@execute post(callback, UpdateCheckResult.Failed("更新清单地址需要以 https:// 开头"))
                val connection = openConnection(manifestUrl)
                try {
                    val code = connection.responseCode
                    if (code !in 200..299) {
                        return@execute post(
                            callback,
                            UpdateCheckResult.Failed("无法读取更新清单（HTTP $code）"),
                        )
                    }
                    val text = readLimited(connection.inputStream, MAX_MANIFEST_BYTES)
                        ?: return@execute post(
                            callback,
                            UpdateCheckResult.Failed("更新清单内容过大或为空"),
                        )
                    val info = parseManifest(text, manifestUrl)
                        ?: return@execute post(
                            callback,
                            UpdateCheckResult.Failed("更新清单格式不正确"),
                        )
                    if (info.versionCode <= BuildConfig.VERSION_CODE.toLong()) {
                        UpdateCheckResult.UpToDate
                    } else {
                        UpdateCheckResult.Available(info)
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                UpdateCheckResult.Failed(friendlyNetworkError(e))
            }
            post(callback, result)
        }
    }

    fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (downloaded: Long, total: Long) -> Unit,
        onComplete: (File) -> Unit,
        onError: (String) -> Unit,
    ) {
        val appContext = context.applicationContext
        io.execute {
            if (normalizeHttpsUrl(info.apkUrl, null) == null) {
                postError(onError, "更新包地址不安全，已取消下载")
                return@execute
            }
            val dir = File(appContext.cacheDir, "updates").apply { mkdirs() }
            val output = File(dir, "nianri-${info.versionCode}.apk")
            if (output.exists() && !output.delete()) {
                postError(onError, "无法清理旧的更新文件")
                return@execute
            }
            try {
                val connection = openConnection(info.apkUrl)
                try {
                    val code = connection.responseCode
                    if (code !in 200..299) {
                        postError(onError, "下载更新失败（HTTP $code）")
                        return@execute
                    }
                    val total = connection.contentLengthLong
                    if (total > MAX_APK_BYTES) {
                        postError(onError, "更新包大小异常，已停止下载")
                        return@execute
                    }
                    val digest = MessageDigest.getInstance("SHA-256")
                    var downloaded = 0L
                    FileOutputStream(output).use { stream ->
                        connection.inputStream.use { input ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                stream.write(buffer, 0, read)
                                digest.update(buffer, 0, read)
                                downloaded += read
                                if (downloaded > MAX_APK_BYTES) {
                                    throw IllegalStateException("更新包超过大小限制")
                                }
                                val current = downloaded
                                main.post { onProgress(current, total) }
                            }
                        }
                        stream.flush()
                    }
                    val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!actualHash.equals(info.sha256, ignoreCase = true)) {
                        output.delete()
                        postError(onError, "更新包校验失败，文件可能已损坏，请稍后重试")
                        return@execute
                    }
                    val verifyError = verifyPackage(appContext, output, info)
                    if (verifyError != null) {
                        output.delete()
                        postError(onError, verifyError)
                        return@execute
                    }
                    main.post { onComplete(output) }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                output.delete()
                postError(onError, friendlyNetworkError(e))
            }
        }
    }

    fun install(context: Context, apk: File): InstallResult {
        if (!apk.isFile) return InstallResult.FAILED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            requestInstallPermission(context)
            return InstallResult.NEED_INSTALL_PERMISSION
        }
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apk,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                clipData = android.content.ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            InstallResult.STARTED
        } catch (_: Exception) {
            InstallResult.FAILED
        }
    }

    fun requestInstallPermission(context: Context) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun currentVersionText(): String = "版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）"

    private fun parseManifest(text: String, manifestUrl: String): UpdateInfo? {
        return try {
            val json = JSONObject(text)
            val versionCode = json.optLong("versionCode", -1)
            val versionName = json.optString("versionName").trim()
            val apkRaw = json.optString("apkUrl").trim()
            val shaRaw = json.optString("sha256").trim()
            val sha = shaRaw.replace(":", "").replace(" ", "").lowercase()
            if (versionCode <= 0 || versionName.isEmpty() || apkRaw.isEmpty()) return null
            if (sha.length != 64 || !sha.all { it in '0'..'9' || it in 'a'..'f' }) return null
            val apkUrl = normalizeHttpsUrl(apkRaw, manifestUrl) ?: return null
            UpdateInfo(
                versionCode = versionCode,
                versionName = versionName,
                apkUrl = apkUrl,
                sha256 = sha,
                notes = json.optString("notes").trim(),
                publishedAt = json.optString("publishedAt").trim(),
            )
        } catch (_: Exception) {
            null
        }
    }

    /** 包名、版本号、下载源 APK 签名必须与当前安装的应用一致。 */
    private fun verifyPackage(context: Context, apk: File, info: UpdateInfo): String? {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val archive = archiveInfo(pm, apk.absolutePath, flags) ?: return "更新包无法解析"
        if (archive.packageName != context.packageName) {
            return "更新包的应用身份不匹配"
        }
        if (versionCodeOf(archive) != info.versionCode) {
            return "更新包版本与清单不一致"
        }
        if (versionCodeOf(archive) <= BuildConfig.VERSION_CODE.toLong()) {
            return "更新包版本号没有递增"
        }
        val current = installedInfo(pm, context.packageName, flags)
            ?: return "无法读取当前签名"
        val currentCerts = certificateHashes(current)
        val archiveCerts = certificateHashes(archive)
        if (currentCerts.isEmpty() || archiveCerts.isEmpty() || currentCerts != archiveCerts) {
            return "更新包签名与当前应用不一致，已阻止安装"
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun archiveInfo(pm: PackageManager, path: String, flags: Int): PackageInfo? {
        return try {
            pm.getPackageArchiveInfo(path, flags)
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun installedInfo(pm: PackageManager, packageName: String, flags: Int): PackageInfo? {
        return try {
            pm.getPackageInfo(packageName, flags)
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun versionCodeOf(info: PackageInfo): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
    }

    private fun certificateHashes(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        } ?: return emptySet()
        return signatures.mapNotNull { sig ->
            runCatching {
                MessageDigest.getInstance("SHA-256")
                    .digest(sig.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            }.getOrNull()
        }.toSet()
    }

    private fun normalizeHttpsUrl(raw: String, baseUrl: String?): String? {
        if (raw.isBlank()) return null
        return try {
            val resolved = if (baseUrl != null) {
                URI(baseUrl).resolve(raw).toString()
            } else {
                raw
            }
            val uri = URI(resolved)
            if (!uri.scheme.equals("https", ignoreCase = true)) return null
            if (uri.host.isNullOrBlank()) return null
            uri.toASCIIString()
        } catch (_: Exception) {
            null
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept", "application/json, application/vnd.android.package-archive, */*")
        return connection
    }

    private fun readLimited(stream: java.io.InputStream, maxBytes: Int): String? {
        val buffer = ByteArray(8192)
        val output = java.io.ByteArrayOutputStream()
        var total = 0
        stream.use {
            while (true) {
                val read = it.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxBytes) return null
                output.write(buffer, 0, read)
            }
        }
        if (total == 0) return null
        return output.toString("UTF-8")
    }

    private fun friendlyNetworkError(e: Exception): String {
        return when (e) {
            is java.net.UnknownHostException -> "网络连接失败，请检查网络后重试"
            is java.net.SocketTimeoutException -> "连接超时，请稍后重试"
            is javax.net.ssl.SSLException -> "安全连接失败，已取消更新"
            else -> e.message?.takeIf { it.isNotBlank() } ?: "更新检查失败，请稍后重试"
        }
    }

    private fun postError(callback: (String) -> Unit, message: String) {
        main.post { callback(message) }
    }

    private fun post(callback: (UpdateCheckResult) -> Unit, result: UpdateCheckResult) {
        main.post { callback(result) }
    }
}
