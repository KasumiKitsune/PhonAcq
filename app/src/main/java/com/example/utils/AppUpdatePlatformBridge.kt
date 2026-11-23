package com.example.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

data class InstallLaunchResult(
    val launched: Boolean,
    val message: String,
    val needsUnknownSourcesPermission: Boolean = false
)

/**
 * Android platform bridge for in-app update download directory & APK installation via FileProvider.
 */
object AppUpdatePlatformBridge {

    fun currentPlatform(): UpdatePlatform = UpdatePlatform.ANDROID

    fun supportsRevealFolder(): Boolean = false

    fun getUpdateDownloadDir(context: Context): File {
        val appContext = context.applicationContext ?: context
        val externalDir = runCatching { appContext.getExternalFilesDir("updates") }.getOrNull()
        return externalDir ?: File(appContext.cacheDir, "updates")
    }

    fun installUpdateFile(context: Context, file: File): InstallLaunchResult {
        if (!file.isFile) {
            return InstallLaunchResult(launched = false, message = "安装包文件不存在，请重新下载。")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canInstall = runCatching { context.packageManager.canRequestPackageInstalls() }
                .getOrDefault(true)
            if (!canInstall) {
                val permIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                return runCatching {
                    context.startActivity(permIntent)
                    InstallLaunchResult(
                        launched = false,
                        message = "请在系统设置中允许 PhonAcq 安装应用，返回后再次点击「立即安装」。",
                        needsUnknownSourcesPermission = true
                    )
                }.getOrElse {
                    InstallLaunchResult(
                        launched = false,
                        message = "请先在系统设置中开启 PhonAcq 的「安装未知应用」权限。",
                        needsUnknownSourcesPermission = true
                    )
                }
            }
        }

        return runCatching {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            InstallLaunchResult(
                launched = true,
                message = "已调起系统安装程序，请按提示完成更新。"
            )
        }.getOrElse { e ->
            InstallLaunchResult(
                launched = false,
                message = "无法启动系统安装程序：${e.message ?: e.javaClass.simpleName}"
            )
        }
    }

    fun openDownloadFolder(context: Context, file: File): Boolean = false
}
