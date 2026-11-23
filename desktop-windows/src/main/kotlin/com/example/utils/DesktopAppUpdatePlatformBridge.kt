@file:Suppress("PackageDirectoryMismatch")

package com.example.utils

import android.content.Context
import java.awt.Desktop
import java.io.File
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.system.exitProcess

data class InstallLaunchResult(
    val launched: Boolean,
    val message: String,
    val needsUnknownSourcesPermission: Boolean = false
)

/**
 * Desktop JVM (Windows / macOS) platform bridge for in-app update download storage,
 * installer launch (`.exe` / `.msi` / `.dmg`), and system file explorer reveal.
 */
object AppUpdatePlatformBridge {

    fun currentPlatform(): UpdatePlatform {
        val osName = System.getProperty("os.name", "").lowercase(Locale.ROOT)
        val osArch = System.getProperty("os.arch", "").lowercase(Locale.ROOT)
        return when {
            osName.contains("win") -> UpdatePlatform.WINDOWS_X64
            osName.contains("mac") || osName.contains("darwin") -> {
                if (osArch.contains("aarch64") || osArch.contains("arm64")) {
                    UpdatePlatform.MACOS_ARM64
                } else {
                    UpdatePlatform.MACOS_X64
                }
            }
            else -> UpdatePlatform.UNSUPPORTED
        }
    }

    fun supportsRevealFolder(): Boolean = true

    fun getUpdateDownloadDir(context: Context): File {
        val userHome = System.getProperty("user.home").orEmpty()
        if (userHome.isNotBlank()) {
            val downloadsDir = File(File(userHome, "Downloads"), "PhonAcq-Updates")
            if (downloadsDir.exists() || downloadsDir.mkdirs()) {
                return downloadsDir
            }
        }
        return File(context.cacheDir, "updates")
    }

    fun installUpdateFile(context: Context, file: File): InstallLaunchResult {
        if (!file.isFile) {
            return InstallLaunchResult(launched = false, message = "安装包文件不存在，请重新下载。")
        }
        val osName = System.getProperty("os.name", "").lowercase(Locale.ROOT)
        val ext = file.extension.lowercase(Locale.ROOT)

        return runCatching {
            when {
                osName.contains("win") -> {
                    if (ext == "msi") {
                        ProcessBuilder("msiexec", "/i", file.absolutePath).start()
                    } else {
                        ProcessBuilder(file.absolutePath).start()
                    }
                    // Release file locks so the Windows setup wizard can overwrite PhonAcq cleanly
                    thread(isDaemon = true, name = "phonacq-update-exit") {
                        runCatching { Thread.sleep(900L) }
                        exitProcess(0)
                    }
                    InstallLaunchResult(
                        launched = true,
                        message = "已启动安装程序，PhonAcq 将自动关闭以完成覆盖更新。"
                    )
                }
                osName.contains("mac") || osName.contains("darwin") -> {
                    ProcessBuilder("open", file.absolutePath).start()
                    InstallLaunchResult(
                        launched = true,
                        message = "已打开 DMG 安装镜像，请将 PhonAcq 拖入「应用程序」覆盖原版本。"
                    )
                }
                else -> {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(file)
                    }
                    InstallLaunchResult(
                        launched = true,
                        message = "已打开安装包：${file.name}"
                    )
                }
            }
        }.getOrElse { e ->
            InstallLaunchResult(
                launched = false,
                message = "无法启动安装包：${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    fun openDownloadFolder(context: Context, file: File): Boolean {
        val osName = System.getProperty("os.name", "").lowercase(Locale.ROOT)
        return runCatching {
            when {
                osName.contains("win") -> {
                    if (file.exists()) {
                        ProcessBuilder("explorer.exe", "/select,${file.absolutePath}").start()
                    } else {
                        ProcessBuilder("explorer.exe", file.parentFile?.absolutePath ?: return false).start()
                    }
                    true
                }
                osName.contains("mac") || osName.contains("darwin") -> {
                    if (file.exists()) {
                        ProcessBuilder("open", "-R", file.absolutePath).start()
                    } else {
                        ProcessBuilder("open", file.parentFile?.absolutePath ?: return false).start()
                    }
                    true
                }
                else -> {
                    val parent = file.parentFile ?: return false
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(parent)
                        true
                    } else {
                        false
                    }
                }
            }
        }.getOrDefault(false)
    }
}
