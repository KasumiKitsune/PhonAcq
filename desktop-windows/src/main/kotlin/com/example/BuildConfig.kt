package com.example

/**
 * Desktop JVM bridge matching AGP-generated `com.example.BuildConfig` on Android,
 * populated via `-Dphonacq.version=...` in `desktop-windows/build.gradle.kts`.
 */
object BuildConfig {
    val VERSION_NAME: String =
        System.getProperty("phonacq.version")
            ?.trim()
            ?.removePrefix("v")
            ?.removePrefix("V")
            ?.takeIf { it.isNotEmpty() }
            ?: "2.0.2"
}
