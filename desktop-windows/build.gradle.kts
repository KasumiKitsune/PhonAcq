import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Deflater
import java.util.zip.ZipEntry
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
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.1")
    implementation("org.json:json:20240303")
    implementation("org.xerial:sqlite-jdbc:3.47.2.0")
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
            includeAllModules = true

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
                    file.inputStream().use { input -> input.copyTo(zos, bufferSize = 65536) }
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
