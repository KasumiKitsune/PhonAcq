import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    kotlin("jvm") version "2.1.10"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.10"
}

val rawAppVersion: String = (project.findProperty("appVersion") as? String)
    ?.trim()
    ?.removePrefix("v")
    ?.removePrefix("V")
    ?.takeIf { it.isNotEmpty() }
    ?: "2.0.2"

// WiX (.msi) and macOS jpackage (.dmg) strictly require X.Y.Z numeric format with X >= 1
val nativePkgVersion: String = run {
    val nums = Regex("""\d+""").findAll(rawAppVersion).mapNotNull { it.value.toIntOrNull() }.toList()
    val major = nums.getOrElse(0) { 2 }.coerceIn(1, 255)
    val minor = nums.getOrElse(1) { 0 }.coerceIn(0, 255)
    val patch = nums.getOrElse(2) { 0 }.coerceIn(0, 65535)
    "$major.$minor.$patch"
}

group = "io.github.kasumikitsune.phonacq"
version = rawAppVersion

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

sourceSets {
    main {
        // Directly mount the shared Android source tree so UI screens, ViewModel,
        // AcousticAnalyzer, ZipUtility (.teproj & 4-tier .TextGrid), Entities, DAOs,
        // and ProjectRepository remain a single source of truth across Android & Windows.
        kotlin.srcDir("src/main/kotlin")
        kotlin.srcDir("../app/src/main/java")
        kotlin.exclude(
            "**/MainActivity.kt",
            "**/data/AppDatabase.kt",
            "**/ui/theme/Theme.kt"
        )
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.1")
    implementation("org.json:json:20240303")
    implementation("org.xerial:sqlite-jdbc:3.47.2.0")
}

/**
 * Strips ~12.8 MB of unused cross-platform native libraries (Linux, FreeBSD, Android, non-host OS/arch)
 * from sqlite-jdbc-*.jar inside a packaged desktop app image, keeping only the target OS+arch binary.
 */
fun pruneSqliteJdbcJarInPlace(jarFile: File, keepNativePrefix: String) {
    if (!jarFile.isFile) return
    val patchedBytes = ByteArrayOutputStream(2 * 1024 * 1024)
    var removedCount = 0
    ZipFile(jarFile).use { zf ->
        ZipOutputStream(patchedBytes).use { zos ->
            zos.setLevel(Deflater.BEST_COMPRESSION)
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                if (name.startsWith("org/sqlite/native/") && !name.endsWith("/")) {
                    if (!name.startsWith(keepNativePrefix)) {
                        removedCount++
                        continue
                    }
                }
                val newEntry = ZipEntry(name)
                newEntry.time = entry.time
                zos.putNextEntry(newEntry)
                if (!entry.isDirectory) {
                    zf.getInputStream(entry).use { input -> input.copyTo(zos, bufferSize = 65536) }
                }
                zos.closeEntry()
            }
        }
    }
    if (removedCount > 0) {
        jarFile.writeBytes(patchedBytes.toByteArray())
    }
}

/**
 * Repacks a .jar with STORED (uncompressed) entries so outer payload.zip Deflate-9 compresses
 * across neighboring .class constant pools (~11.2 MB smaller installer + faster JVM startup classload).
 */
fun repackJarToStoredBytes(jarFile: File): ByteArray {
    return try {
        val out = ByteArrayOutputStream((jarFile.length() * 2).coerceIn(65536L, 64L * 1024 * 1024).toInt())
        val seen = HashSet<String>()
        ZipFile(jarFile).use { zf ->
            ZipOutputStream(out).use { zos ->
                zos.setMethod(ZipOutputStream.STORED)
                val entries = zf.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!seen.add(entry.name)) continue
                    val newEntry = ZipEntry(entry.name)
                    newEntry.time = entry.time
                    if (entry.isDirectory) {
                        newEntry.method = ZipEntry.STORED
                        newEntry.size = 0L
                        newEntry.compressedSize = 0L
                        newEntry.crc = 0L
                        zos.putNextEntry(newEntry)
                        zos.closeEntry()
                    } else {
                        val raw = zf.getInputStream(entry).use { it.readBytes() }
                        val crc = CRC32().apply { update(raw) }.value
                        newEntry.method = ZipEntry.STORED
                        newEntry.size = raw.size.toLong()
                        newEntry.compressedSize = raw.size.toLong()
                        newEntry.crc = crc
                        zos.putNextEntry(newEntry)
                        zos.write(raw)
                        zos.closeEntry()
                    }
                }
            }
        }
        out.toByteArray()
    } catch (_: Throwable) {
        jarFile.readBytes()
    }
}

compose.desktop {
    application {
        mainClass = "com.example.DesktopMainKt"

        jvmArgs += listOf(
            "-Dfile.encoding=UTF-8",
            "-Dsun.java2d.dpiaware=true",
            "-Dphonacq.version=$rawAppVersion"
        )

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Dmg)
            packageName = "PhonAcq"
            packageVersion = nativePkgVersion
            description = "PhonAcq Acoustic Fieldwork Studio (PhonTracer .teproj / Praat .TextGrid)"
            vendor = "KasumiKitsune"
            // Tier 1 JRE slimming: bundle ONLY the exact JDK modules used by PhonAcq, Compose/Skiko,
            // JavaSound WAV I/O, SQLite JDBC, XML (.xlsx/.docx), and GBK/GB18030 Chinese wordlist charsets
            // (replaces includeAllModules = true, cutting ~45+ MB of javac/jshell/jfr/localedata bloat).
            includeAllModules = false
            modules(
                "java.datatransfer",
                "java.desktop",
                "java.logging",
                "java.prefs",
                "java.sql",
                "java.xml",
                "jdk.unsupported",
                "jdk.charsets",
                "jdk.crypto.ec"
            )

            windows {
                iconFile.set(project.file("src/main/resources/phonacq.ico"))
                menuGroup = "PhonAcq"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "3c68c82d-217d-4db1-839d-6656cd3ad6fa"
            }

            macOS {
                bundleID = "io.github.kasumikitsune.phonacq"
                iconFile.set(project.file("src/main/resources/phonacq.icns"))
                entitlementsFile.set(project.file("entitlements.plist"))
                runtimeEntitlementsFile.set(project.file("runtime-entitlements.plist"))
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSMicrophoneUsageDescription</key>
                        <string>PhonAcq 需要访问麦克风以录制田野调查语音与方言字表音频。</string>
                    """.trimIndent()
                }
            }
        }
    }
}

tasks.register<JavaExec>("generateIco") {
    group = "compose desktop"
    description = "Regenerates src/main/resources/phonacq.ico, phonacq.icns, and installer logo PNG with the pure white Samsung pebble icon"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.DesktopIconRenderer")
    args(
        project.file("src/main/resources/phonacq.ico").absolutePath,
        project.file("src/main/resources/phonacq.icns").absolutePath,
        layout.buildDirectory.file("installer-work/phonacq-logo.png").get().asFile.absolutePath
    )
    jvmArgs("-Djava.awt.headless=true")
}

// Optimize jlink runtime image size (--compress) and handle minimal Linux environments lacking binutils objcopy
tasks.withType<org.jetbrains.compose.desktop.application.tasks.AbstractJLinkTask>().configureEach {
    freeArgs.addAll(javaHome.map { homePath ->
        val releaseFile = File(homePath, "release")
        val jdkMajor = releaseFile.takeIf { it.isFile }?.readText()
            ?.let { Regex("""JAVA_VERSION="(\d+)""").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
            ?: JavaVersion.current().majorVersion.toIntOrNull()
            ?: 17
        listOf(if (jdkMajor >= 21) "--compress=zip-6" else "--compress=2")
    })
    val osName = System.getProperty("os.name", "").lowercase()
    if (osName.contains("linux")) {
        val hasObjcopy = (System.getenv("PATH") ?: "")
            .split(File.pathSeparator)
            .any { dir -> dir.isNotBlank() && File(dir, "objcopy").canExecute() }
        if (!hasObjcopy) {
            @Suppress("UNCHECKED_CAST")
            val stripProp = this.javaClass.methods
                .firstOrNull { it.name.startsWith("getStripDebug") && it.parameterCount == 0 }
                ?.invoke(this) as? org.gradle.api.provider.Property<Boolean>
            if (stripProp != null) {
                stripProp.set(false)
                freeArgs.add("--strip-java-debug-attributes")
            }
        }
    }
}

// Automatically prune non-host OS/arch native binaries from sqlite-jdbc-*.jar right after createDistributable
tasks.matching { it.name == "createDistributable" }.configureEach {
    doLast {
        val osName = System.getProperty("os.name", "").lowercase()
        val osArch = System.getProperty("os.arch", "").lowercase()
        val keepPrefix = when {
            osName.contains("win") && (osArch.contains("aarch64") || osArch.contains("arm64")) ->
                "org/sqlite/native/Windows/aarch64/"
            osName.contains("win") ->
                "org/sqlite/native/Windows/x86_64/"
            (osName.contains("mac") || osName.contains("darwin")) && (osArch.contains("aarch64") || osArch.contains("arm64")) ->
                "org/sqlite/native/Mac/aarch64/"
            osName.contains("mac") || osName.contains("darwin") ->
                "org/sqlite/native/Mac/x86_64/"
            osName.contains("linux") && (osArch.contains("aarch64") || osArch.contains("arm64")) ->
                "org/sqlite/native/Linux/aarch64/"
            else ->
                "org/sqlite/native/Linux/x86_64/"
        }
        val appOutRoot = layout.buildDirectory.dir("compose/binaries/main/app").get().asFile
        if (appOutRoot.exists()) {
            appOutRoot.walkTopDown()
                .filter { it.isFile && it.name.startsWith("sqlite-jdbc") && it.name.endsWith(".jar") }
                .forEach { jar ->
                    val beforeKb = jar.length() / 1024
                    pruneSqliteJdbcJarInPlace(jar, keepPrefix)
                    val afterKb = jar.length() / 1024
                    logger.lifecycle("Pruned ${jar.name} for $keepPrefix: ${beforeKb} KB -> ${afterKb} KB")
                }
        }
    }
}

tasks.register("packageWindowsSetup") {
    group = "compose desktop"
    description = "Builds modern rounded HiDPI one-click PhonAcq Windows .exe installer supporting in-place same-version & cross-version overwrite"
    dependsOn("createDistributable", "generateIco")

    doLast {
        val osName = System.getProperty("os.name").lowercase()
        if (!osName.contains("win")) {
            throw GradleException("packageWindowsSetup requires Windows (csc.exe + Win32 PE subsystem). Current OS: $osName")
        }

        val appImageDir = layout.buildDirectory.dir("compose/binaries/main/app/PhonAcq").get().asFile
        if (!appImageDir.exists() || !File(appImageDir, "PhonAcq.exe").exists()) {
            throw GradleException("Distributable app image not found at: ${appImageDir.absolutePath}")
        }

        val workDir = layout.buildDirectory.dir("installer-work").get().asFile
        workDir.mkdirs()

        val icoFile = project.file("src/main/resources/phonacq.ico")
        val logoPngFile = File(workDir, "phonacq-logo.png")
        if (!icoFile.exists() || !logoPngFile.exists()) {
            throw GradleException("Missing icon assets (${icoFile.absolutePath} or ${logoPngFile.absolutePath})")
        }

        val manifestSrc = project.file("installer/installer.manifest")
        val csSrc = project.file("installer/PhonAcqInstaller.cs")
        if (!manifestSrc.exists() || !csSrc.exists()) {
            throw GradleException("Missing installer source files in ${project.file("installer").absolutePath}")
        }

        val manifestFile = File(workDir, "installer.manifest")
        manifestSrc.copyTo(manifestFile, overwrite = true)

        val csPatchedFile = File(workDir, "PhonAcqInstaller.cs")
        val csPatchedContent = buildString {
            appendLine("""[assembly: System.Reflection.AssemblyVersion("$nativePkgVersion.0")]""")
            appendLine("""[assembly: System.Reflection.AssemblyFileVersion("$nativePkgVersion.0")]""")
            append(csSrc.readText(Charsets.UTF_8).replace("__PHONACQ_VERSION__", rawAppVersion))
        }
        // Write UTF-8 with BOM so Windows .NET Framework 4.x csc.exe parses Chinese string literals cleanly regardless of system codepage
        FileOutputStream(csPatchedFile).use { fos ->
            fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            fos.write(csPatchedContent.toByteArray(Charsets.UTF_8))
        }

        val systemRoot = System.getenv("SystemRoot") ?: System.getenv("WINDIR") ?: "C:\\Windows"
        val cscCandidates = listOf(
            File(systemRoot, "Microsoft.NET\\Framework64\\v4.0.30319\\csc.exe"),
            File("C:\\Windows\\Microsoft.NET\\Framework64\\v4.0.30319\\csc.exe"),
            File(systemRoot, "Microsoft.NET\\Framework\\v4.0.30319\\csc.exe")
        )
        val cscExe = cscCandidates.firstOrNull { it.isFile }?.absolutePath ?: "csc.exe"

        val stubExe = File(workDir, "PhonAcqSetupStub.exe")
        if (stubExe.exists()) stubExe.delete()

        val cscCmd = listOf(
            cscExe,
            "/nologo",
            "/target:winexe",
            "/platform:x64",
            "/optimize+",
            "/utf8output",
            "/win32icon:${icoFile.absolutePath}",
            "/win32manifest:${manifestFile.absolutePath}",
            "/resource:${logoPngFile.absolutePath},phonacq-logo.png",
            "/r:System.dll",
            "/r:System.Core.dll",
            "/r:System.Drawing.dll",
            "/r:System.Windows.Forms.dll",
            "/r:System.IO.Compression.dll",
            "/r:System.IO.Compression.FileSystem.dll",
            "/out:${stubExe.absolutePath}",
            csPatchedFile.absolutePath
        )

        val proc = ProcessBuilder(cscCmd)
            .directory(workDir)
            .redirectErrorStream(true)
            .start()
        val compilerOutput = proc.inputStream.bufferedReader(Charsets.UTF_8).readText()
        val exitCode = proc.waitFor()
        if (exitCode != 0 || !stubExe.exists()) {
            throw GradleException("Failed to compile PhonAcqSetupStub.exe (exit=$exitCode):\n$compilerOutput")
        }

        // Compress createDistributable output into payload.zip
        val payloadZip = File(workDir, "payload.zip")
        if (payloadZip.exists()) payloadZip.delete()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(payloadZip))).use { zos ->
            zos.setLevel(Deflater.BEST_COMPRESSION)
            val basePath = appImageDir.toPath()
            appImageDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relName = basePath.relativize(file.toPath()).toString().replace('\\', '/')
                    val entry = ZipEntry(relName)
                    entry.time = file.lastModified()
                    zos.putNextEntry(entry)
                    if (file.name.endsWith(".jar", ignoreCase = true)) {
                        zos.write(repackJarToStoredBytes(file))
                    } else {
                        file.inputStream().use { input -> input.copyTo(zos, bufferSize = 65536) }
                    }
                    zos.closeEntry()
                }
            }
        }

        val outDir = layout.buildDirectory.dir("installer").get().asFile
        outDir.mkdirs()
        val finalSetupExe = File(outDir, "PhonAcq-v${rawAppVersion}-windows-x64-setup.exe")
        if (finalSetupExe.exists()) finalSetupExe.delete()

        val stubLen = stubExe.length()
        val zipLen = payloadZip.length()
        val trailer = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("PAQSETUP".toByteArray(Charsets.US_ASCII))
            putLong(stubLen) // payloadOffset
            putLong(zipLen)  // payloadLength
            putLong(stubLen) // stubLength (used when extracting clean Uninstall-PhonAcq.exe)
        }.array()

        BufferedOutputStream(FileOutputStream(finalSetupExe)).use { out ->
            stubExe.inputStream().use { it.copyTo(out, bufferSize = 65536) }
            payloadZip.inputStream().use { it.copyTo(out, bufferSize = 65536) }
            out.write(trailer)
        }

        logger.lifecycle("Built custom PhonAcq Windows installer: ${finalSetupExe.absolutePath} (${finalSetupExe.length() / (1024 * 1024)} MB)")
    }
}
