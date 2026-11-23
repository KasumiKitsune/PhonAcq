package com.example.utils

import android.content.Context
import com.example.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

enum class UpdatePlatform {
    ANDROID,
    WINDOWS_X64,
    MACOS_ARM64,
    MACOS_X64,
    UNSUPPORTED
}

data class UpdateAssetInfo(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val expectedSha256: String? = null
)

data class ReleaseUpdateInfo(
    val tagName: String,
    val versionName: String,
    val releaseName: String,
    val releaseNotes: String,
    val htmlUrl: String,
    val publishedAt: String,
    val asset: UpdateAssetInfo?
)

sealed class AppUpdateUiState {
    data object Idle : AppUpdateUiState()
    data object Checking : AppUpdateUiState()
    data class UpToDate(
        val currentVersion: String,
        val latestVersion: String,
        val checkedAtMillis: Long
    ) : AppUpdateUiState()

    data class UpdateAvailable(
        val currentVersion: String,
        val release: ReleaseUpdateInfo
    ) : AppUpdateUiState()

    data class Downloading(
        val release: ReleaseUpdateInfo,
        val asset: UpdateAssetInfo,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long
    ) : AppUpdateUiState()

    data class Verifying(
        val release: ReleaseUpdateInfo,
        val asset: UpdateAssetInfo
    ) : AppUpdateUiState()

    data class ReadyToInstall(
        val release: ReleaseUpdateInfo,
        val asset: UpdateAssetInfo,
        val downloadedFile: File,
        val sha256Verified: Boolean
    ) : AppUpdateUiState()

    data class Error(
        val message: String,
        val releaseUrl: String = AppUpdateManager.GITHUB_RELEASES_PAGE_URL,
        val pendingRelease: ReleaseUpdateInfo? = null
    ) : AppUpdateUiState()
}

sealed class CheckReleaseResult {
    data class UpToDate(val latestVersion: String) : CheckReleaseResult()
    data class UpdateAvailable(val release: ReleaseUpdateInfo) : CheckReleaseResult()
    data class Failure(val message: String, val releaseUrl: String = AppUpdateManager.GITHUB_RELEASES_PAGE_URL) : CheckReleaseResult()
}

sealed class DownloadAssetResult {
    data class Success(val file: File, val sha256Verified: Boolean) : DownloadAssetResult()
    data class Failure(val message: String) : DownloadAssetResult()
}

/**
 * Pure official GitHub Releases update checker & streaming in-app downloader.
 * Shared across Android and Desktop (Windows / macOS) with zero third-party HTTP dependencies.
 */
object AppUpdateManager {
    const val GITHUB_OWNER = "KasumiKitsune"
    const val GITHUB_REPO = "PhonAcq"
    const val GITHUB_API_LATEST_RELEASE_URL =
        "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    const val GITHUB_RELEASES_PAGE_URL =
        "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases"
    const val GITHUB_LATEST_RELEASE_WEB_URL =
        "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val MAX_REDIRECTS = 5
    private const val PROGRESS_EMIT_INTERVAL_MS = 100L

    private val SHA256_LINE_REGEX = Regex("""(?i)\b([0-9a-f]{64})\s+\*?([^\s\r\n]+)\b""")
    private val VERSION_NUMBER_REGEX = Regex("""(\d+)""")

    fun normalizeVersion(raw: String): String {
        return raw.trim().removePrefix("v").removePrefix("V").trim()
    }

    /**
     * Compares two semantic version strings (e.g. "2.0.10" vs "2.0.3", "2.1.0" vs "2.1.0-rc1").
     * Returns > 0 if [v1] > [v2], 0 if equal, < 0 if [v1] < [v2].
     */
    fun compareVersions(v1: String, v2: String): Int {
        val n1 = normalizeVersion(v1)
        val n2 = normalizeVersion(v2)
        if (n1.equals(n2, ignoreCase = true)) return 0

        val (core1, pre1) = splitCoreAndPrerelease(n1)
        val (core2, pre2) = splitCoreAndPrerelease(n2)

        val nums1 = core1.split('.').map { it.toIntOrNull() ?: 0 }
        val nums2 = core2.split('.').map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(nums1.size, nums2.size)
        for (i in 0 until maxLen) {
            val a = nums1.getOrElse(i) { 0 }
            val b = nums2.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }

        // Same numeric core: stable release > pre-release
        if (pre1.isEmpty() && pre2.isEmpty()) return 0
        if (pre1.isEmpty()) return 1
        if (pre2.isEmpty()) return -1

        // Compare pre-release identifiers naturally (e.g. beta.2 < beta.10)
        val tokens1 = pre1.split('.', '-', '_')
        val tokens2 = pre2.split('.', '-', '_')
        val maxPre = maxOf(tokens1.size, tokens2.size)
        for (i in 0 until maxPre) {
            val t1 = tokens1.getOrElse(i) { "" }
            val t2 = tokens2.getOrElse(i) { "" }
            if (t1 == t2) continue
            val i1 = t1.toIntOrNull()
            val i2 = t2.toIntOrNull()
            if (i1 != null && i2 != null) return i1.compareTo(i2)
            val m1 = VERSION_NUMBER_REGEX.find(t1)
            val m2 = VERSION_NUMBER_REGEX.find(t2)
            if (m1 != null && m2 != null &&
                t1.substring(0, m1.range.first).equals(t2.substring(0, m2.range.first), ignoreCase = true)
            ) {
                val cmp = m1.value.toInt().compareTo(m2.value.toInt())
                if (cmp != 0) return cmp
            }
            return t1.lowercase(Locale.ROOT).compareTo(t2.lowercase(Locale.ROOT))
        }
        return 0
    }

    private fun splitCoreAndPrerelease(ver: String): Pair<String, String> {
        val clean = ver.substringBefore('+')
        val dashIdx = clean.indexOf('-')
        return if (dashIdx >= 0) {
            clean.substring(0, dashIdx) to clean.substring(dashIdx + 1)
        } else {
            clean to ""
        }
    }

    /**
     * Parses `sha256sum` lines embedded in `.github/workflows/release.yml` release body.
     * Returns a map of lowercase filename -> lowercase 64-hex SHA-256 digest.
     */
    fun parseSha256Map(releaseBody: String): Map<String, String> {
        if (releaseBody.isBlank()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        for (match in SHA256_LINE_REGEX.findAll(releaseBody)) {
            val hash = match.groupValues[1].lowercase(Locale.ROOT)
            val fileName = match.groupValues[2].trim().substringAfterLast('/').lowercase(Locale.ROOT)
            if (fileName.isNotEmpty()) {
                result[fileName] = hash
            }
        }
        return result
    }

    /**
     * Strips auto-appended installer download guide tables and `<details>` SHA-256 blocks
     * so the About dialog displays concise, human-readable release notes.
     */
    fun cleanReleaseNotes(releaseBody: String): String {
        if (releaseBody.isBlank()) return ""
        var text = releaseBody.replace("\r\n", "\n")

        // Cut auto-generated download instructions section onward
        val guideMarker = Regex("""(?m)^\s*###\s*安装包下载指引.*$""")
        guideMarker.find(text)?.let { match ->
            text = text.substring(0, match.range.first)
        }

        // Remove any remaining <details>...</details> checksum blocks
        text = text.replace(Regex("""(?is)<details>.*?</details>"""), "")

        // Remove trailing horizontal rules and blank lines
        val lines = text.lines().toMutableList()
        while (lines.isNotEmpty() && (lines.last().isBlank() || lines.last().trim() == "---")) {
            lines.removeAt(lines.lastIndex)
        }
        while (lines.isNotEmpty() && lines.first().isBlank()) {
            lines.removeAt(0)
        }
        return lines.joinToString("\n").trim()
    }

    fun selectBestAsset(assets: List<UpdateAssetInfo>, platform: UpdatePlatform): UpdateAssetInfo? {
        if (assets.isEmpty()) return null
        return when (platform) {
            UpdatePlatform.ANDROID -> {
                assets.firstOrNull {
                    val n = it.name.lowercase(Locale.ROOT)
                    n.endsWith("-android.apk") && !n.contains("debug")
                } ?: assets.firstOrNull {
                    val n = it.name.lowercase(Locale.ROOT)
                    n.endsWith(".apk") && !n.contains("debug")
                } ?: assets.firstOrNull {
                    it.name.lowercase(Locale.ROOT).endsWith(".apk")
                }
            }
            UpdatePlatform.WINDOWS_X64 -> {
                assets.firstOrNull {
                    val n = it.name.lowercase(Locale.ROOT)
                    n.contains("windows") && n.endsWith("-setup.exe")
                } ?: assets.firstOrNull {
                    val n = it.name.lowercase(Locale.ROOT)
                    n.contains("windows") && n.endsWith(".exe")
                } ?: assets.firstOrNull {
                    it.name.lowercase(Locale.ROOT).endsWith(".exe")
                } ?: assets.firstOrNull {
                    it.name.lowercase(Locale.ROOT).endsWith(".msi")
                }
            }
            UpdatePlatform.MACOS_ARM64 -> {
                assets.firstOrNull {
                    val n = it.name.lowercase(Locale.ROOT)
                    n.contains("macos") && (n.contains("arm64") || n.contains("aarch64")) && n.endsWith(".dmg")
                } ?: assets.firstOrNull {
                    val n = it.name.lowercase(Locale.ROOT)
                    (n.contains("arm64") || n.contains("aarch64")) && n.endsWith(".dmg")
                } ?: assets.firstOrNull {
                    val n = it.name.lowercase(Locale.ROOT)
                    n.endsWith(".dmg") && !n.contains("x64") && !n.contains("intel")
                }
            }
            UpdatePlatform.MACOS_X64 -> {
                assets.firstOrNull {
                    val n = it.name.lowercase(Locale.ROOT)
                    n.contains("macos") && (n.contains("x64") || n.contains("x86_64") || n.contains("intel")) && n.endsWith(".dmg")
                } ?: assets.firstOrNull {
                    val n = it.name.lowercase(Locale.ROOT)
                    (n.contains("x64") || n.contains("x86_64") || n.contains("intel")) && n.endsWith(".dmg")
                } ?: assets.firstOrNull {
                    val n = it.name.lowercase(Locale.ROOT)
                    n.endsWith(".dmg") && !n.contains("arm64") && !n.contains("aarch64")
                }
            }
            UpdatePlatform.UNSUPPORTED -> null
        }
    }

    fun parseGitHubReleaseJson(jsonText: String, platform: UpdatePlatform): ReleaseUpdateInfo {
        val root = JSONObject(jsonText)
        val tagName = root.optString("tag_name", "").trim()
        val versionName = normalizeVersion(tagName.ifEmpty { root.optString("name", "") })
        val releaseName = root.optString("name", "").trim().ifEmpty { "PhonAcq v$versionName" }
        val rawBody = root.optString("body", "")
        val htmlUrl = root.optString("html_url", "").trim().ifEmpty { GITHUB_RELEASES_PAGE_URL }
        val publishedAt = root.optString("published_at", "").trim()
        val sha256Map = parseSha256Map(rawBody)

        val assetsList = ArrayList<UpdateAssetInfo>()
        val assetsArr = root.optJSONArray("assets")
        if (assetsArr != null) {
            for (i in 0 until assetsArr.length()) {
                val obj = assetsArr.optJSONObject(i) ?: continue
                val name = obj.optString("name", "").trim()
                val downloadUrl = obj.optString("browser_download_url", "").trim()
                val sizeBytes = obj.optLong("size", 0L).coerceAtLeast(0L)
                if (name.isNotEmpty() && downloadUrl.isNotEmpty()) {
                    assetsList.add(
                        UpdateAssetInfo(
                            name = name,
                            downloadUrl = downloadUrl,
                            sizeBytes = sizeBytes,
                            expectedSha256 = sha256Map[name.lowercase(Locale.ROOT)]
                        )
                    )
                }
            }
        }

        return ReleaseUpdateInfo(
            tagName = tagName.ifEmpty { "v$versionName" },
            versionName = versionName,
            releaseName = releaseName,
            releaseNotes = cleanReleaseNotes(rawBody),
            htmlUrl = htmlUrl,
            publishedAt = publishedAt,
            asset = selectBestAsset(assetsList, platform)
        )
    }

    suspend fun checkLatestRelease(
        currentVersion: String,
        platform: UpdatePlatform,
        apiUrl: String = GITHUB_API_LATEST_RELEASE_URL,
        webLatestUrl: String = GITHUB_LATEST_RELEASE_WEB_URL
    ): CheckReleaseResult {
        currentCoroutineContext().ensureActive()
        val normCurrent = normalizeVersion(currentVersion)
        val userAgent = "PhonAcq/$normCurrent"

        var apiErrorMessage: String? = null
        var shouldFallbackToWebRedirect = false

        try {
            val conn = openHttpConnectionWithRedirects(
                urlStr = apiUrl,
                userAgent = userAgent,
                acceptHeader = "application/vnd.github+json",
                followRedirects = true
            )
            try {
                val code = conn.responseCode
                if (code in 200..299) {
                    val jsonBody = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val release = parseGitHubReleaseJson(jsonBody, platform)
                    if (release.versionName.isBlank()) {
                        return CheckReleaseResult.Failure("无法解析最新版本信息，请稍后重试。")
                    }
                    return if (compareVersions(release.versionName, normCurrent) > 0) {
                        CheckReleaseResult.UpdateAvailable(release)
                    } else {
                        CheckReleaseResult.UpToDate(release.versionName)
                    }
                } else if (code == 403 || code == 429) {
                    shouldFallbackToWebRedirect = true
                    apiErrorMessage = "GitHub API 访问受限 (HTTP $code)"
                } else if (code == 404) {
                    return CheckReleaseResult.Failure("未找到已发布的版本记录。", GITHUB_RELEASES_PAGE_URL)
                } else {
                    apiErrorMessage = "服务器返回异常状态 (HTTP $code)"
                }
            } finally {
                conn.disconnect()
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            apiErrorMessage = friendlyNetworkError(e)
            shouldFallbackToWebRedirect = true
        }

        if (shouldFallbackToWebRedirect && webLatestUrl.isNotBlank()) {
            val fallback = tryCheckViaWebLatestRedirect(normCurrent, platform, webLatestUrl, userAgent)
            if (fallback != null) return fallback
        }

        return CheckReleaseResult.Failure(
            message = apiErrorMessage ?: "检查更新失败，请检查网络连接后重试。",
            releaseUrl = GITHUB_RELEASES_PAGE_URL
        )
    }

    /**
     * Fallback when GitHub REST API hits anonymous IP rate-limit (HTTP 403/429):
     * resolves the `302 Location` header of `/releases/latest` -> `/releases/tag/vX.Y.Z`
     * and constructs the standard official release download URL for the current platform.
     */
    private fun tryCheckViaWebLatestRedirect(
        normCurrent: String,
        platform: UpdatePlatform,
        webLatestUrl: String,
        userAgent: String
    ): CheckReleaseResult? {
        return try {
            val conn = openHttpConnectionWithRedirects(
                urlStr = webLatestUrl,
                userAgent = userAgent,
                acceptHeader = "text/html",
                followRedirects = false
            )
            try {
                val code = conn.responseCode
                val location = conn.getHeaderField("Location").orEmpty().trim()
                if (code !in 300..399 || location.isEmpty()) return null
                val tagMarker = "/releases/tag/"
                val idx = location.indexOf(tagMarker)
                if (idx < 0) return null
                val rawTag = location.substring(idx + tagMarker.length)
                    .substringBefore('?')
                    .substringBefore('#')
                    .trim()
                if (rawTag.isEmpty()) return null
                val latestVer = normalizeVersion(rawTag)
                if (latestVer.isEmpty()) return null

                if (compareVersions(latestVer, normCurrent) <= 0) {
                    return CheckReleaseResult.UpToDate(latestVer)
                }

                val releaseWebUrl = if (location.startsWith("http", ignoreCase = true)) {
                    location
                } else {
                    "https://github.com$location"
                }
                val synthesizedAsset = buildFallbackAssetForTag(rawTag, latestVer, platform)
                CheckReleaseResult.UpdateAvailable(
                    ReleaseUpdateInfo(
                        tagName = rawTag,
                        versionName = latestVer,
                        releaseName = "PhonAcq $rawTag",
                        releaseNotes = "",
                        htmlUrl = releaseWebUrl,
                        publishedAt = "",
                        asset = synthesizedAsset
                    )
                )
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildFallbackAssetForTag(
        tagName: String,
        versionName: String,
        platform: UpdatePlatform
    ): UpdateAssetInfo? {
        val fileName = when (platform) {
            UpdatePlatform.ANDROID -> "PhonAcq-v$versionName-android.apk"
            UpdatePlatform.WINDOWS_X64 -> "PhonAcq-v$versionName-windows-x64-setup.exe"
            UpdatePlatform.MACOS_ARM64 -> "PhonAcq-v$versionName-macos-arm64.dmg"
            UpdatePlatform.MACOS_X64 -> "PhonAcq-v$versionName-macos-x64.dmg"
            UpdatePlatform.UNSUPPORTED -> return null
        }
        return UpdateAssetInfo(
            name = fileName,
            downloadUrl = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/download/$tagName/$fileName",
            sizeBytes = 0L,
            expectedSha256 = null
        )
    }

    suspend fun downloadAsset(
        asset: UpdateAssetInfo,
        targetDir: File,
        userAgent: String = "PhonAcq/${normalizeVersion(BuildConfig.VERSION_NAME)}",
        onProgress: (downloadedBytes: Long, totalBytes: Long, speedBytesPerSec: Long) -> Unit,
        onVerifying: () -> Unit = {}
    ): DownloadAssetResult {
        currentCoroutineContext().ensureActive()

        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return DownloadAssetResult.Failure("无法创建更新下载目录：${targetDir.absolutePath}")
        }

        val safeName = sanitizeDownloadFileName(asset.name)
        val finalFile = File(targetDir, safeName)
        val partFile = File(targetDir, "$safeName.part")

        // Reuse already-downloaded file if size and SHA-256 already match
        if (finalFile.isFile && finalFile.length() > 0L) {
            val expectedHash = asset.expectedSha256
            if (!expectedHash.isNullOrBlank()) {
                onVerifying()
                val existingHash = computeFileSha256(finalFile)
                if (existingHash.equals(expectedHash, ignoreCase = true)) {
                    return DownloadAssetResult.Success(finalFile, sha256Verified = true)
                }
            } else if (asset.sizeBytes > 0L && finalFile.length() == asset.sizeBytes) {
                return DownloadAssetResult.Success(finalFile, sha256Verified = false)
            }
        }

        // Remove stale installers from earlier versions so cache doesn't grow indefinitely
        cleanupOtherFiles(targetDir, keepNames = setOf(safeName, partFile.name))
        if (partFile.exists()) partFile.delete()

        var conn: HttpURLConnection? = null
        try {
            conn = openHttpConnectionWithRedirects(
                urlStr = asset.downloadUrl,
                userAgent = userAgent,
                acceptHeader = "application/octet-stream",
                followRedirects = true
            )
            val code = conn.responseCode
            if (code !in 200..299) {
                return DownloadAssetResult.Failure("下载失败：服务器返回 HTTP $code")
            }

            val headerLen = conn.contentLengthLong
            val totalBytes = when {
                headerLen > 0L -> headerLen
                asset.sizeBytes > 0L -> asset.sizeBytes
                else -> -1L
            }

            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(32 * 1024)
            var downloaded = 0L
            var lastEmitTime = System.currentTimeMillis()
            var windowStartTime = lastEmitTime
            var windowBytes = 0L
            var currentSpeed = 0L

            onProgress(0L, totalBytes, 0L)

            BufferedInputStream(conn.inputStream, 64 * 1024).use { input ->
                FileOutputStream(partFile).use { output ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        downloaded += read
                        windowBytes += read

                        val now = System.currentTimeMillis()
                        val elapsedEmit = now - lastEmitTime
                        if (elapsedEmit >= PROGRESS_EMIT_INTERVAL_MS) {
                            val windowElapsed = (now - windowStartTime).coerceAtLeast(1L)
                            if (windowElapsed >= 400L) {
                                currentSpeed = (windowBytes * 1000L) / windowElapsed
                                windowStartTime = now
                                windowBytes = 0L
                            } else if (currentSpeed == 0L && windowElapsed > 0L) {
                                currentSpeed = (windowBytes * 1000L) / windowElapsed
                            }
                            lastEmitTime = now
                            onProgress(downloaded, totalBytes, currentSpeed)
                        }
                    }
                    output.flush()
                }
            }

            if (downloaded <= 0L) {
                partFile.delete()
                return DownloadAssetResult.Failure("下载失败：未接收到有效安装包数据。")
            }
            if (totalBytes > 0L && downloaded != totalBytes) {
                partFile.delete()
                return DownloadAssetResult.Failure(
                    "下载不完整（已接收 ${formatBytes(downloaded)} / ${formatBytes(totalBytes)}），请重试。"
                )
            }

            onProgress(downloaded, if (totalBytes > 0L) totalBytes else downloaded, currentSpeed)
            onVerifying()

            val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
            val expectedSha256 = asset.expectedSha256?.trim()?.lowercase(Locale.ROOT)
            val shaVerified = if (!expectedSha256.isNullOrEmpty()) {
                if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                    partFile.delete()
                    return DownloadAssetResult.Failure("安装包 SHA-256 校验失败，文件可能已损坏，请重新下载。")
                }
                true
            } else {
                false
            }

            if (finalFile.exists() && !finalFile.delete()) {
                partFile.delete()
                return DownloadAssetResult.Failure("无法覆盖旧安装包文件：${finalFile.name}")
            }
            if (!partFile.renameTo(finalFile)) {
                partFile.copyTo(finalFile, overwrite = true)
                partFile.delete()
            }

            return DownloadAssetResult.Success(finalFile, sha256Verified = shaVerified)
        } catch (ce: CancellationException) {
            runCatching { partFile.delete() }
            throw ce
        } catch (e: Exception) {
            runCatching { partFile.delete() }
            return DownloadAssetResult.Failure("下载失败：${friendlyNetworkError(e)}")
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    /**
     * Opens an HTTP/HTTPS connection and manually follows up to [MAX_REDIRECTS] redirects,
     * including cross-host and cross-scheme transitions (required for GitHub Release CDN).
     */
    private fun openHttpConnectionWithRedirects(
        urlStr: String,
        userAgent: String,
        acceptHeader: String,
        followRedirects: Boolean
    ): HttpURLConnection {
        var currentUrl = urlStr
        var redirects = 0
        while (true) {
            val url = URL(currentUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                useCaches = false
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", acceptHeader)
            }
            if (!followRedirects) return conn

            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == HttpURLConnection.HTTP_SEE_OTHER ||
                code == 307 || code == 308
            ) {
                val nextLocation = conn.getHeaderField("Location")?.trim()
                conn.disconnect()
                if (nextLocation.isNullOrEmpty()) {
                    throw IOException("重定向响应缺少目标地址 (HTTP $code)")
                }
                if (redirects >= MAX_REDIRECTS) {
                    throw IOException("重定向次数过多")
                }
                currentUrl = URL(url, nextLocation).toString()
                redirects++
            } else {
                return conn
            }
        }
    }

    private fun computeFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(32 * 1024)
        file.inputStream().use { input ->
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sanitizeDownloadFileName(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\').trim()
        val cleaned = base.replace(Regex("""[<>:"/\\|?*\u0000-\u001F]"""), "_")
        return cleaned.ifBlank { "PhonAcq-update.bin" }
    }

    private fun cleanupOtherFiles(dir: File, keepNames: Set<String>) {
        runCatching {
            dir.listFiles()?.forEach { child ->
                if (child.isFile && child.name !in keepNames) {
                    child.delete()
                }
            }
        }
    }

    private fun friendlyNetworkError(e: Exception): String {
        val msg = e.message.orEmpty()
        return when {
            e is java.net.UnknownHostException -> "无法连接到 GitHub，请检查网络或代理设置。"
            e is java.net.SocketTimeoutException -> "连接 GitHub 超时，请稍后重试。"
            e is javax.net.ssl.SSLException -> "安全连接建立失败（SSL），请检查系统时间或网络环境。"
            msg.isNotBlank() -> msg
            else -> e.javaClass.simpleName
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 0L) return "未知大小"
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2f GB", gb)
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return ""
        return "${formatBytes(bytesPerSec)}/s"
    }
}

/**
 * Application-lifetime update controller so downloads survive dismissing the About dialog
 * or configuration changes. Never triggers background checks automatically.
 */
object AppUpdateController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<AppUpdateUiState>(AppUpdateUiState.Idle)
    val state: StateFlow<AppUpdateUiState> = _state.asStateFlow()

    @Volatile
    private var activeJob: Job? = null

    fun checkForUpdates(context: Context) {
        val cur = _state.value
        if (cur is AppUpdateUiState.Checking || cur is AppUpdateUiState.Downloading || cur is AppUpdateUiState.Verifying) {
            return
        }
        val currentVersion = AppUpdateManager.normalizeVersion(BuildConfig.VERSION_NAME)
        val platform = AppUpdatePlatformBridge.currentPlatform()

        activeJob?.cancel()
        _state.value = AppUpdateUiState.Checking
        activeJob = scope.launch {
            when (val res = AppUpdateManager.checkLatestRelease(currentVersion, platform)) {
                is CheckReleaseResult.UpToDate -> {
                    _state.value = AppUpdateUiState.UpToDate(
                        currentVersion = currentVersion,
                        latestVersion = res.latestVersion,
                        checkedAtMillis = System.currentTimeMillis()
                    )
                }
                is CheckReleaseResult.UpdateAvailable -> {
                    _state.value = AppUpdateUiState.UpdateAvailable(
                        currentVersion = currentVersion,
                        release = res.release
                    )
                }
                is CheckReleaseResult.Failure -> {
                    _state.value = AppUpdateUiState.Error(
                        message = res.message,
                        releaseUrl = res.releaseUrl
                    )
                }
            }
        }
    }

    fun startDownload(context: Context) {
        val cur = _state.value
        val release = when (cur) {
            is AppUpdateUiState.UpdateAvailable -> cur.release
            is AppUpdateUiState.Error -> cur.pendingRelease
            else -> null
        } ?: return

        val asset = release.asset
        if (asset == null) {
            _state.value = AppUpdateUiState.Error(
                message = "未在最新版本中找到适用于当前平台的安装包，请前往发布页手动下载。",
                releaseUrl = release.htmlUrl,
                pendingRelease = release
            )
            return
        }

        val targetDir = AppUpdatePlatformBridge.getUpdateDownloadDir(context)
        val appContext = context.applicationContext ?: context
        activeJob?.cancel()
        _state.value = AppUpdateUiState.Downloading(
            release = release,
            asset = asset,
            downloadedBytes = 0L,
            totalBytes = asset.sizeBytes,
            speedBytesPerSec = 0L
        )
        activeJob = scope.launch {
            val result = AppUpdateManager.downloadAsset(
                asset = asset,
                targetDir = targetDir,
                onProgress = { downloaded, total, speed ->
                    _state.value = AppUpdateUiState.Downloading(
                        release = release,
                        asset = asset,
                        downloadedBytes = downloaded,
                        totalBytes = total,
                        speedBytesPerSec = speed
                    )
                },
                onVerifying = {
                    _state.value = AppUpdateUiState.Verifying(release = release, asset = asset)
                }
            )
            when (result) {
                is DownloadAssetResult.Success -> {
                    _state.value = AppUpdateUiState.ReadyToInstall(
                        release = release,
                        asset = asset,
                        downloadedFile = result.file,
                        sha256Verified = result.sha256Verified
                    )
                    // Touch appContext reference purely to keep lint happy on both Android/Desktop
                    appContext.hashCode()
                }
                is DownloadAssetResult.Failure -> {
                    _state.value = AppUpdateUiState.Error(
                        message = result.message,
                        releaseUrl = release.htmlUrl,
                        pendingRelease = release
                    )
                }
            }
        }
    }

    fun cancelDownload() {
        val cur = _state.value
        val release = when (cur) {
            is AppUpdateUiState.Downloading -> cur.release
            is AppUpdateUiState.Verifying -> cur.release
            else -> return
        }
        activeJob?.cancel()
        activeJob = null
        _state.value = AppUpdateUiState.UpdateAvailable(
            currentVersion = AppUpdateManager.normalizeVersion(BuildConfig.VERSION_NAME),
            release = release
        )
    }

    fun installDownloaded(context: Context): InstallLaunchResult {
        val cur = _state.value as? AppUpdateUiState.ReadyToInstall
            ?: return InstallLaunchResult(launched = false, message = "尚未找到可安装的更新包。")
        if (!cur.downloadedFile.isFile) {
            _state.value = AppUpdateUiState.UpdateAvailable(
                currentVersion = AppUpdateManager.normalizeVersion(BuildConfig.VERSION_NAME),
                release = cur.release
            )
            return InstallLaunchResult(launched = false, message = "下载的安装包已不存在，请重新下载。")
        }
        return AppUpdatePlatformBridge.installUpdateFile(context, cur.downloadedFile)
    }

    fun openDownloadFolder(context: Context): Boolean {
        val cur = _state.value as? AppUpdateUiState.ReadyToInstall ?: return false
        return AppUpdatePlatformBridge.openDownloadFolder(context, cur.downloadedFile)
    }
}
