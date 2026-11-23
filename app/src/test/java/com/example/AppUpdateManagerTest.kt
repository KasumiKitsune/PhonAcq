package com.example

import com.example.utils.AppUpdateManager
import com.example.utils.CheckReleaseResult
import com.example.utils.DownloadAssetResult
import com.example.utils.UpdateAssetInfo
import com.example.utils.UpdatePlatform
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.net.InetSocketAddress
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
class AppUpdateManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun compareVersions_handlesSemanticAndPrerelease() {
        assertTrue(AppUpdateManager.compareVersions("2.0.10", "2.0.3") > 0)
        assertTrue(AppUpdateManager.compareVersions("v2.1.0", "2.0.9") > 0)
        assertEquals(0, AppUpdateManager.compareVersions("v2.0.3", "2.0.3"))
        assertTrue(AppUpdateManager.compareVersions("2.1.0", "2.1.0-rc1") > 0)
        assertTrue(AppUpdateManager.compareVersions("2.1.0-beta.10", "2.1.0-beta.2") > 0)
        assertTrue(AppUpdateManager.compareVersions("2.0.2", "2.0.3") < 0)
    }

    @Test
    fun parseGitHubReleaseJson_matchesAllFourPlatformsAndCleansNotes() {
        val apkSha = "a".repeat(64)
        val winSha = "b".repeat(64)
        val macArmSha = "c".repeat(64)
        val macX64Sha = "d".repeat(64)
        val sampleBody = """
            - 新增 PhonTracer .teproj 全版本导入兼容
            - 优化关于页面与内置下载更新

            ---

            ### 安装包下载指引

            | 平台 | 文件名 |
            | :--- | :--- |
            | Android | `PhonAcq-v2.1.0-android.apk` |

            <details>
            <summary>SHA-256 文件校验值</summary>

            ```text
            $apkSha  PhonAcq-v2.1.0-android.apk
            $winSha  PhonAcq-v2.1.0-windows-x64-setup.exe
            $macArmSha  PhonAcq-v2.1.0-macos-arm64.dmg
            $macX64Sha  PhonAcq-v2.1.0-macos-x64.dmg
            ```
            </details>
        """.trimIndent()

        val json = org.json.JSONObject().apply {
            put("tag_name", "v2.1.0")
            put("name", "PhonAcq v2.1.0")
            put("html_url", "https://github.com/KasumiKitsune/PhonAcq/releases/tag/v2.1.0")
            put("published_at", "2026-11-23T10:00:00Z")
            put("body", sampleBody)
            put(
                "assets",
                org.json.JSONArray().apply {
                    put(assetJson("PhonAcq-v2.1.0-android-debug.apk", 10_000_000L))
                    put(assetJson("PhonAcq-v2.1.0-android.apk", 12_345_678L))
                    put(assetJson("PhonAcq-v2.1.0-windows-x64-setup.exe", 45_000_000L))
                    put(assetJson("PhonAcq-v2.1.0-macos-arm64.dmg", 48_000_000L))
                    put(assetJson("PhonAcq-v2.1.0-macos-x64.dmg", 50_000_000L))
                }
            )
        }.toString()

        val androidRel = AppUpdateManager.parseGitHubReleaseJson(json, UpdatePlatform.ANDROID)
        assertEquals("2.1.0", androidRel.versionName)
        assertEquals("PhonAcq-v2.1.0-android.apk", androidRel.asset?.name)
        assertEquals(apkSha, androidRel.asset?.expectedSha256)
        assertTrue(androidRel.releaseNotes.contains("PhonTracer .teproj"))
        assertFalse(androidRel.releaseNotes.contains("安装包下载指引"))
        assertFalse(androidRel.releaseNotes.contains("SHA-256"))

        val winRel = AppUpdateManager.parseGitHubReleaseJson(json, UpdatePlatform.WINDOWS_X64)
        assertEquals("PhonAcq-v2.1.0-windows-x64-setup.exe", winRel.asset?.name)
        assertEquals(winSha, winRel.asset?.expectedSha256)

        val macArmRel = AppUpdateManager.parseGitHubReleaseJson(json, UpdatePlatform.MACOS_ARM64)
        assertEquals("PhonAcq-v2.1.0-macos-arm64.dmg", macArmRel.asset?.name)
        assertEquals(macArmSha, macArmRel.asset?.expectedSha256)

        val macX64Rel = AppUpdateManager.parseGitHubReleaseJson(json, UpdatePlatform.MACOS_X64)
        assertEquals("PhonAcq-v2.1.0-macos-x64.dmg", macX64Rel.asset?.name)
        assertEquals(macX64Sha, macX64Rel.asset?.expectedSha256)
    }

    @Test
    fun checkAndDownload_supportsRedirectAndSha256VerificationAndRateLimitFallback() = runBlocking {
        val payload = ByteArray(256 * 1024) { (it * 31 and 0xFF).toByte() }
        val expectedSha = sha256Hex(payload)

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val port = server.address.port
        val baseUrl = "http://127.0.0.1:$port"

        server.createContext("/api/latest") { ex ->
            val json = """
                {
                  "tag_name": "v2.2.0",
                  "name": "PhonAcq v2.2.0",
                  "html_url": "$baseUrl/releases/tag/v2.2.0",
                  "published_at": "2026-11-23T12:00:00Z",
                  "body": "修复导入与更新\n\n$expectedSha  PhonAcq-v2.2.0-windows-x64-setup.exe",
                  "assets": [
                    {
                      "name": "PhonAcq-v2.2.0-windows-x64-setup.exe",
                      "size": ${payload.size},
                      "browser_download_url": "$baseUrl/download/redirect"
                    }
                  ]
                }
            """.trimIndent().toByteArray(Charsets.UTF_8)
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, json.size.toLong())
            ex.responseBody.use { it.write(json) }
        }

        server.createContext("/api/ratelimited") { ex ->
            val body = "rate limited".toByteArray()
            ex.sendResponseHeaders(403, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }

        server.createContext("/releases/latest") { ex ->
            ex.responseHeaders.add("Location", "$baseUrl/releases/tag/v2.3.0")
            ex.sendResponseHeaders(302, -1)
            ex.close()
        }

        server.createContext("/download/redirect") { ex ->
            ex.responseHeaders.add("Location", "$baseUrl/cdn/setup.exe")
            ex.sendResponseHeaders(302, -1)
            ex.close()
        }

        server.createContext("/cdn/setup.exe") { ex ->
            ex.responseHeaders.add("Content-Type", "application/octet-stream")
            ex.sendResponseHeaders(200, payload.size.toLong())
            ex.responseBody.use { it.write(payload) }
        }

        server.start()
        try {
            // 1. Standard API check -> UpdateAvailable
            val checkRes = AppUpdateManager.checkLatestRelease(
                currentVersion = "2.0.3",
                platform = UpdatePlatform.WINDOWS_X64,
                apiUrl = "$baseUrl/api/latest",
                webLatestUrl = "$baseUrl/releases/latest"
            )
            assertTrue(checkRes is CheckReleaseResult.UpdateAvailable)
            val release = (checkRes as CheckReleaseResult.UpdateAvailable).release
            assertEquals("2.2.0", release.versionName)
            val asset = release.asset
            assertNotNull(asset)

            // 2. Standard API check when already on latest -> UpToDate
            val upToDateRes = AppUpdateManager.checkLatestRelease(
                currentVersion = "2.2.0",
                platform = UpdatePlatform.WINDOWS_X64,
                apiUrl = "$baseUrl/api/latest",
                webLatestUrl = "$baseUrl/releases/latest"
            )
            assertTrue(upToDateRes is CheckReleaseResult.UpToDate)

            // 3. API 403 rate-limit -> fallback to 302 web redirect
            val fallbackRes = AppUpdateManager.checkLatestRelease(
                currentVersion = "2.0.3",
                platform = UpdatePlatform.ANDROID,
                apiUrl = "$baseUrl/api/ratelimited",
                webLatestUrl = "$baseUrl/releases/latest"
            )
            assertTrue(fallbackRes is CheckReleaseResult.UpdateAvailable)
            val fbRelease = (fallbackRes as CheckReleaseResult.UpdateAvailable).release
            assertEquals("2.3.0", fbRelease.versionName)
            assertEquals("PhonAcq-v2.3.0-android.apk", fbRelease.asset?.name)

            // 4. Streaming download with 302 redirect + SHA-256 verification
            val outDir = tempFolder.newFolder("updates")
            var progressCallbacks = 0
            val dlRes = AppUpdateManager.downloadAsset(
                asset = asset!!,
                targetDir = outDir,
                onProgress = { _, _, _ -> progressCallbacks++ }
            )
            assertTrue("Expected download success, got $dlRes", dlRes is DownloadAssetResult.Success)
            val success = dlRes as DownloadAssetResult.Success
            assertTrue(success.sha256Verified)
            assertEquals(payload.size.toLong(), success.file.length())
            assertTrue(progressCallbacks >= 2)
            assertFalse(File(outDir, "${asset.name}.part").exists())

            // 5. SHA-256 mismatch rejects corrupted payload & cleans .part
            val tamperedDir = tempFolder.newFolder("updates_tampered")
            val badAsset = UpdateAssetInfo(
                name = asset.name,
                downloadUrl = asset.downloadUrl,
                sizeBytes = asset.sizeBytes,
                expectedSha256 = "0".repeat(64)
            )
            val badRes = AppUpdateManager.downloadAsset(
                asset = badAsset,
                targetDir = tamperedDir,
                onProgress = { _, _, _ -> }
            )
            assertTrue(badRes is DownloadAssetResult.Failure)
            assertFalse(File(tamperedDir, asset.name).exists())
            assertFalse(File(tamperedDir, "${asset.name}.part").exists())
        } finally {
            server.stop(0)
        }
    }

    private fun assetJson(name: String, size: Long): org.json.JSONObject =
        org.json.JSONObject().apply {
            put("name", name)
            put("size", size)
            put("browser_download_url", "https://github.com/KasumiKitsune/PhonAcq/releases/download/v2.1.0/$name")
        }

    private fun sha256Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
