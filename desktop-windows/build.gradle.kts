import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.LZMAInputStream
import org.tukaani.xz.LZMAOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // Build-time ONLY solid LZMA encoder for Windows setup payload (adds 0 bytes to runtime app)
        classpath("org.tukaani:xz:1.10")
    }
}

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
        // and ProjectRepository remain a single source of truth across Android & Desktop.
        kotlin.srcDir("src/main/kotlin")
        kotlin.srcDir("../app/src/main/java")
        kotlin.exclude(
            "**/MainActivity.kt",
            "**/data/AppDatabase.kt",
            "**/ui/theme/Theme.kt",
            "**/utils/SmartImportPlatformBridge.kt"
        )
    }
}

// Bundle the single-source-of-truth full IPA & Sinological phonetics font family from ../app/src/main/res/font
tasks.processResources {
    from("../app/src/main/res/font") {
        include("phonacq_ipa_sans*.ttf")
        into("fonts")
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
 * from any JAR containing `org/sqlite/native/` AND repacks all JAR entries as STORED (0% inner Deflate).
 *
 * Storing JARs uncompressed:
 * 1) Exposes raw `.class` constant pools, `skiko` native binaries, and `icudtl.dat` directly to outer
 *    solid LZMA (Windows `.exe` & macOS `ULMO` `.dmg`), saving ~8-11 MB over double compression;
 * 2) Eliminates per-class inflate overhead during JVM cold startup on both Windows and macOS.
 */
fun optimizeDistributableJarInPlace(jarFile: File, keepSqliteNativePrefix: String): Pair<Int, Long> {
    if (!jarFile.isFile) return 0 to 0L
    val beforeLen = jarFile.length()
    var removedSqliteEntries = 0
    val out = ByteArrayOutputStream((beforeLen * 2).coerceIn(65536L, 96L * 1024 * 1024).toInt())
    val seen = HashSet<String>()

    ZipFile(jarFile).use { zf ->
        ZipOutputStream(out).use { zos ->
            zos.setMethod(ZipOutputStream.STORED)
            val entries = zf.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name
                if (!seen.add(name)) continue

                if (name.startsWith("org/sqlite/native/") && !name.endsWith("/")) {
                    if (!name.startsWith(keepSqliteNativePrefix)) {
                        removedSqliteEntries++
                        continue
                    }
                }

                val newEntry = ZipEntry(name)
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

    val finalBytes = out.toByteArray()
    jarFile.writeBytes(finalBytes)
    return removedSqliteEntries to (finalBytes.size.toLong() - beforeLen)
}

/**
 * Deterministic solid archive writer (`PAQPACK1`) ordered by file group so similar native binaries
 * (`*.dll`/`*.exe`/`*.dylib`) and JVM bytecode (`modules` + `*.jar`) are adjacent inside the LZMA window.
 */
fun buildSolidPackBytes(appImageDir: File): Pair<ByteArray, Int> {
    val basePath = appImageDir.toPath()
    val files = appImageDir.walkTopDown()
        .filter { it.isFile }
        .sortedWith(
            compareBy<File>(
                { f ->
                    val ext = f.extension.lowercase()
                    when {
                        ext == "exe" || ext == "dll" || ext == "dylib" || ext == "so" -> 0
                        f.name == "modules" || ext == "jar" || ext == "class" -> 1
                        ext == "dat" || ext == "ttf" || ext == "icns" || ext == "ico" -> 2
                        else -> 3
                    }
                },
                { f -> basePath.relativize(f.toPath()).toString().replace('\\', '/') }
            )
        )
        .toList()

    val totalContentBytes = files.sumOf { it.length() }
    val estimatedCap = (totalContentBytes + files.size * 128L + 64L).coerceIn(65536L, 512L * 1024 * 1024).toInt()
    val rawPackOut = ByteArrayOutputStream(estimatedCap)

    fun writeLeInt(os: OutputStream, v: Int) {
        val b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
        os.write(b)
    }

    fun writeLeLong(os: OutputStream, v: Long) {
        val b = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array()
        os.write(b)
    }

    // Stream Header (20 bytes): "PAQPACK1" + int32 entryCount + int64 totalContentBytes
    rawPackOut.write("PAQPACK1".toByteArray(Charsets.US_ASCII))
    writeLeInt(rawPackOut, files.size)
    writeLeLong(rawPackOut, totalContentBytes)

    val ioBuf = ByteArray(65536)
    for (f in files) {
        val relPath = basePath.relativize(f.toPath()).toString().replace('\\', '/')
        val pathUtf8 = relPath.toByteArray(Charsets.UTF_8)
        val fileLen = f.length()
        val crc = CRC32()
        FileInputStream(f).use { fis ->
            while (true) {
                val n = fis.read(ioBuf)
                if (n <= 0) break
                crc.update(ioBuf, 0, n)
            }
        }

        writeLeInt(rawPackOut, pathUtf8.size)
        rawPackOut.write(pathUtf8)
        writeLeLong(rawPackOut, f.lastModified())
        writeLeLong(rawPackOut, fileLen)
        writeLeInt(rawPackOut, crc.value.toInt())

        var written = 0L
        FileInputStream(f).use { fis ->
            while (true) {
                val n = fis.read(ioBuf)
                if (n <= 0) break
                rawPackOut.write(ioBuf, 0, n)
                written += n
            }
        }
        if (written != fileLen) {
            throw GradleException("Concurrent file size mismatch while packing $relPath ($written != $fileLen)")
        }
    }

    return rawPackOut.toByteArray() to files.size
}

/**
 * Compresses a `PAQPACK1` byte buffer into a standard 13-byte header `.lzma` solid stream
 * using a 32 MB sliding dictionary (`preset = 9`, `lc = 3, lp = 0, pb = 2`).
 */
fun writeSolidLzmaFile(rawPackBytes: ByteArray, destLzmaFile: File) {
    val opts = LZMA2Options(9).apply {
        dictSize = 32 * 1024 * 1024 // 32 MB solid dictionary (1024x Deflate's 32 KB window, modest RAM)
    }
    BufferedOutputStream(FileOutputStream(destLzmaFile), 65536).use { bos ->
        LZMAOutputStream(bos, opts, rawPackBytes.size.toLong()).use { lzOut ->
            lzOut.write(rawPackBytes)
        }
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

        // Safe Release Dead-Code Tree-Shaking: zero obfuscation, zero instruction rewriting
        buildTypes.release.proguard {
            version.set("7.7.0")
            isEnabled.set(true)
            obfuscate.set(false)
            optimize.set(false)
            configurationFiles.from(project.file("proguard-desktop.pro"))
        }

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb, TargetFormat.Dmg)
            packageName = "PhonAcq"
            packageVersion = nativePkgVersion
            description = "PhonAcq 田野语音与方言字表采录系统"
            vendor = "KasumiKitsune"
            // Bundle ONLY the exact JDK modules required by PhonAcq, Compose/Skiko,
            // JavaSound WAV I/O, SQLite JDBC, XML (.xlsx/.docx), and GBK/GB18030 Chinese wordlist charsets.
            includeAllModules = false
            modules(
                "java.datatransfer",
                "java.desktop",
                "java.logging",
                "java.prefs",
                "java.sql",
                "java.xml",
                "jdk.unsupported",
                "jdk.charsets"
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

// Eliminate double-compression entropy sabotage: use constant-pool string sharing WITHOUT inner Deflate
// (--compress=1 on JDK 17, --compress=zip-0 on JDK 21+) so outer solid LZMA / macOS ULMO achieves maximum
// cross-file compression AND installed JVM cold startup skips per-class inflate overhead.
tasks.withType<org.jetbrains.compose.desktop.application.tasks.AbstractJLinkTask>().configureEach {
    freeArgs.addAll(javaHome.map { homePath ->
        val releaseFile = File(homePath, "release")
        val jdkMajor = releaseFile.takeIf { it.isFile }?.readText()
            ?.let { Regex("""JAVA_VERSION="(\d+)""").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
            ?: JavaVersion.current().majorVersion.toIntOrNull()
            ?: 17
        listOf(if (jdkMajor >= 21) "--compress=zip-0" else "--compress=1")
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

// Post-process both createDistributable and createReleaseDistributable:
// 1. Strip non-host OS/arch native SQLite binaries (~12.8 MB) regardless of ProGuard JAR naming
// 2. Repack all app JARs to STORED (uncompressed) for faster startup & maximum outer LZMA / ULMO ratio
tasks.matching { it.name == "createDistributable" || it.name == "createReleaseDistributable" }.configureEach {
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
            (osName.contains("mac") || osName.contains("darwin") ) ->
                "org/sqlite/native/Mac/x86_64/"
            osName.contains("linux") && (osArch.contains("aarch64") || osArch.contains("arm64")) ->
                "org/sqlite/native/Linux/aarch64/"
            else ->
                "org/sqlite/native/Linux/x86_64/"
        }

        val subDirName = if (name == "createReleaseDistributable") "main-release/app" else "main/app"
        val appOutRoot = layout.buildDirectory.dir("compose/binaries/$subDirName").get().asFile
        if (appOutRoot.exists()) {
            var totalPrunedLibs = 0
            var jarCount = 0
            appOutRoot.walkTopDown()
                .filter { it.isFile && it.extension.equals("jar", ignoreCase = true) }
                .forEach { jar ->
                    val (removed, _) = optimizeDistributableJarInPlace(jar, keepPrefix)
                    totalPrunedLibs += removed
                    jarCount++
                }
            logger.lifecycle(
                "[$name] Optimized $jarCount app JAR(s) as STORED; pruned $totalPrunedLibs non-host SQLite native binary entries (kept $keepPrefix)."
            )
        }
    }
}

tasks.register("packageWindowsSetup") {
    group = "compose desktop"
    description = "Builds modern rounded HiDPI one-click PhonAcq Windows .exe installer using Release tree-shaking + 32MB Solid LZMA"
    dependsOn("createReleaseDistributable", "generateIco")

    doLast {
        val osName = System.getProperty("os.name").lowercase()
        if (!osName.contains("win")) {
            throw GradleException("packageWindowsSetup requires Windows (csc.exe + Win32 PE subsystem). Current OS: $osName")
        }

        val releaseAppDir = layout.buildDirectory.dir("compose/binaries/main-release/app/PhonAcq").get().asFile
        val fallbackAppDir = layout.buildDirectory.dir("compose/binaries/main/app/PhonAcq").get().asFile
        val appImageDir = if (File(releaseAppDir, "PhonAcq.exe").exists()) releaseAppDir else fallbackAppDir
        if (!appImageDir.exists() || !File(appImageDir, "PhonAcq.exe").exists()) {
            throw GradleException("Distributable app image not found at: ${releaseAppDir.absolutePath}")
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
        val versionAttrs = listOf(
            """[assembly: System.Reflection.AssemblyVersion("$nativePkgVersion.0")]""",
            """[assembly: System.Reflection.AssemblyFileVersion("$nativePkgVersion.0")]"""
        ).joinToString("\n")
        val rawCs = csSrc.readText(Charsets.UTF_8).replace("__PHONACQ_VERSION__", rawAppVersion)
        val nsAnchor = "namespace PhonAcqSetup"
        if (!rawCs.contains(nsAnchor)) {
            throw GradleException("Missing '$nsAnchor' declaration in ${csSrc.absolutePath}")
        }
        val csPatchedContent = rawCs.replaceFirst(nsAnchor, "$versionAttrs\n\n$nsAnchor")
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

        // Pack release distributable into deterministic PAQPACK1 solid LZMA payload
        val (rawPackBytes, packedFileCount) = buildSolidPackBytes(appImageDir)
        val payloadLzma = File(workDir, "payload.lzma")
        if (payloadLzma.exists()) payloadLzma.delete()
        writeSolidLzmaFile(rawPackBytes, payloadLzma)

        val outDir = layout.buildDirectory.dir("installer").get().asFile
        outDir.mkdirs()
        val finalSetupExe = File(outDir, "PhonAcq-v${rawAppVersion}-windows-x64-setup.exe")
        if (finalSetupExe.exists()) finalSetupExe.delete()

        val stubLen = stubExe.length()
        val lzmaLen = payloadLzma.length()
        val trailer = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("PAQSETL2".toByteArray(Charsets.US_ASCII))
            putLong(stubLen) // payloadOffset
            putLong(lzmaLen) // payloadLength
            putLong(stubLen) // stubLength (used when extracting clean Uninstall-PhonAcq.exe)
        }.array()

        BufferedOutputStream(FileOutputStream(finalSetupExe)).use { out ->
            stubExe.inputStream().use { it.copyTo(out, bufferSize = 65536) }
            payloadLzma.inputStream().use { it.copyTo(out, bufferSize = 65536) }
            out.write(trailer)
        }

        val mbStr = String.format(Locale.US, "%.2f", finalSetupExe.length() / (1024.0 * 1024.0))
        val rawMbStr = String.format(Locale.US, "%.2f", rawPackBytes.size / (1024.0 * 1024.0))
        logger.lifecycle(
            "Built PhonAcq Windows Solid-LZMA Installer ($packedFileCount files, raw=$rawMbStr MB -> installer=$mbStr MB): ${finalSetupExe.absolutePath}"
        )
    }
}

tasks.register<JavaExec>("verifyDesktopTypography") {
    group = "verification"
    description = "Runs headless Skia Paragraph glyph & weight verification across Sarasa UI SC, IPA Sans, and Plangothic Ext-B..I"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.ui.theme.DesktopTypographyVerificationRunner")
    jvmArgs("-Djava.awt.headless=true")
}

// Cross-platform verification task: validates ProGuard release distributable, bundled Sarasa UI SC + IPA + Plangothic Ext-B..I fonts,
// SQLite native entry retention, and exact PAQPACK1 + .lzma solid compression & CRC32 round-trip.
tasks.register("verifyReleaseSizeAndSolidStream") {
    group = "verification"
    description = "Verifies Release tree-shaken distributable integrity, studio + dialect font resources, and Solid LZMA round-trip"
    dependsOn("createReleaseDistributable", "verifyDesktopTypography")

    doLast {
        val releaseRoot = layout.buildDirectory.dir("compose/binaries/main-release/app").get().asFile
        val appDir = releaseRoot.listFiles()?.firstOrNull { it.isDirectory }
            ?: throw GradleException("No release app bundle directory found under ${releaseRoot.absolutePath}")

        // 1. Verify bundled Sarasa UI SC, IPA Sans, Plangothic P1/P2 fonts & SQLite native driver inside release JARs
        val requiredFontEntries = setOf(
            "fonts/SarasaUiSC-Regular.ttf",
            "fonts/SarasaUiSC-SemiBold.ttf",
            "fonts/PlangothicP1-Regular.ttf",
            "fonts/PlangothicP2-Regular.ttf",
            "fonts/phonacq_ipa_sans.ttf",
            "fonts/phonacq_ipa_sans_bold.ttf",
            "fonts/OFL-FONTS-ATTRIBUTION.txt"
        )
        val foundFontEntries = mutableSetOf<String>()
        var foundSqliteNative = false
        var foundSqliteJdbcClass = false

        appDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("jar", ignoreCase = true) }
            .forEach { jar ->
                ZipFile(jar).use { zf ->
                    for (fontPath in requiredFontEntries) {
                        if (zf.getEntry(fontPath) != null) foundFontEntries.add(fontPath)
                    }
                    if (zf.getEntry("org/sqlite/JDBC.class") != null) foundSqliteJdbcClass = true
                    val entries = zf.entries()
                    while (entries.hasMoreElements()) {
                        val e = entries.nextElement()
                        if (e.name.startsWith("org/sqlite/native/") && !e.isDirectory) {
                            foundSqliteNative = true
                        }
                    }
                }
            }

        val missingFonts = requiredFontEntries - foundFontEntries
        if (missingFonts.isNotEmpty()) {
            throw GradleException("Missing bundled font resources in release JARs: $missingFonts")
        }
        if (!foundSqliteJdbcClass || !foundSqliteNative) {
            throw GradleException("Missing SQLite JDBC class or host native library after ProGuard tree-shaking")
        }

        // 2. Pack release appDir into PAQPACK1 + Solid LZMA and verify full decompression + per-file CRC32
        val workDir = layout.buildDirectory.dir("installer-verify").get().asFile
        workDir.mkdirs()
        val testLzma = File(workDir, "verify-payload.lzma")
        if (testLzma.exists()) testLzma.delete()

        val (rawPackBytes, expectedCount) = buildSolidPackBytes(appDir)
        writeSolidLzmaFile(rawPackBytes, testLzma)

        // Verify classic 13-byte .lzma header layout expected by PhonAcqInstaller.cs LzmaDecoder
        val header = ByteArray(13)
        FileInputStream(testLzma).use { fis ->
            if (fis.read(header) != 13) throw GradleException("Short .lzma header")
        }
        val propByte = header[0].toInt() and 0xFF
        val dictSizeHeader = ByteBuffer.wrap(header, 1, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val uncompSizeHeader = ByteBuffer.wrap(header, 5, 8).order(ByteOrder.LITTLE_ENDIAN).long
        if (uncompSizeHeader != rawPackBytes.size.toLong()) {
            throw GradleException("LZMA header uncompressed size mismatch: $uncompSizeHeader != ${rawPackBytes.size}")
        }

        // Round-trip decode & verify every entry header + CRC32
        var verifiedEntries = 0
        var verifiedTotalBytes = 0L
        BufferedInputStream(FileInputStream(testLzma), 65536).use { bis ->
            LZMAInputStream(bis).use { lzIn ->
                val dis = DataInputStream(lzIn)
                val magic = ByteArray(8)
                dis.readFully(magic)
                if (String(magic, Charsets.US_ASCII) != "PAQPACK1") {
                    throw GradleException("Decoded stream magic mismatch")
                }
                fun readLeInt(): Int {
                    val b = ByteArray(4)
                    dis.readFully(b)
                    return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int
                }
                fun readLeLong(): Long {
                    val b = ByteArray(8)
                    dis.readFully(b)
                    return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).long
                }

                val entryCount = readLeInt()
                val declaredTotal = readLeLong()
                if (entryCount != expectedCount) {
                    throw GradleException("Entry count mismatch: $entryCount != $expectedCount")
                }

                val buf = ByteArray(65536)
                for (idx in 0 until entryCount) {
                    val pathLen = readLeInt()
                    val pathBytes = ByteArray(pathLen)
                    dis.readFully(pathBytes)
                    val relPath = String(pathBytes, Charsets.UTF_8)
                    val lastMod = readLeLong()
                    if (lastMod < 0L) throw GradleException("Negative timestamp on $relPath")
                    val fileLen = readLeLong()
                    val expectedCrc = readLeInt().toLong() and 0xFFFFFFFFL

                    val crc = CRC32()
                    var rem = fileLen
                    while (rem > 0L) {
                        val step = minOf(buf.size.toLong(), rem).toInt()
                        val r = dis.read(buf, 0, step)
                        if (r <= 0) throw GradleException("Unexpected EOF while reading $relPath")
                        crc.update(buf, 0, r)
                        rem -= r
                    }
                    if (crc.value != expectedCrc) {
                        throw GradleException("CRC32 mismatch on $relPath: ${crc.value} != $expectedCrc")
                    }
                    verifiedEntries++
                    verifiedTotalBytes += fileLen
                }
                if (verifiedTotalBytes != declaredTotal) {
                    throw GradleException("Total bytes mismatch: $verifiedTotalBytes != $declaredTotal")
                }
            }
        }

        // 3. Also execute an exact 1:1 algorithmic mirror of PhonAcqInstaller.cs's LzmaDecoder + Crc32Calculator
        //    against the real generated .lzma file to prove bit-exact compatibility with our C# 4.0 unpacker.
        run {
            class MirrorRangeDecoder {
                val kTopValue = 1L shl 24
                var range: Long = 0xFFFFFFFFL
                var code: Long = 0L
                lateinit var stream: InputStream
                fun init(s: InputStream) {
                    stream = s
                    code = 0L
                    range = 0xFFFFFFFFL
                    repeat(5) {
                        val b = stream.read()
                        if (b < 0) throw GradleException("EOF in MirrorRangeDecoder.init")
                        code = ((code shl 8) or b.toLong()) and 0xFFFFFFFFL
                    }
                }
                fun decodeDirectBits(numTotalBits: Int): Int {
                    var r = range
                    var c = code
                    var res = 0
                    for (i in numTotalBits downTo 1) {
                        r = r ushr 1
                        val diff = (c - r) and 0xFFFFFFFFL
                        val t = diff ushr 31
                        val mask = (t - 1L) and 0xFFFFFFFFL
                        c = (c - (r and mask)) and 0xFFFFFFFFL
                        res = (res shl 1) or (1 - t.toInt())
                        if (r < kTopValue) {
                            val b = stream.read()
                            if (b < 0) throw GradleException("EOF in MirrorRangeDecoder.decodeDirectBits")
                            c = ((c shl 8) or b.toLong()) and 0xFFFFFFFFL
                            r = (r shl 8) and 0xFFFFFFFFL
                        }
                    }
                    range = r
                    code = c
                    return res
                }
            }

            fun decodeBit(probs: IntArray, idx: Int, rd: MirrorRangeDecoder): Int {
                val prob = probs[idx]
                val newBound = ((rd.range ushr 11) * prob.toLong()) and 0xFFFFFFFFL
                return if (rd.code < newBound) {
                    rd.range = newBound
                    probs[idx] = prob + ((2048 - prob) ushr 5)
                    if (rd.range < rd.kTopValue) {
                        val b = rd.stream.read()
                        if (b < 0) throw GradleException("EOF in decodeBit(0)")
                        rd.code = ((rd.code shl 8) or b.toLong()) and 0xFFFFFFFFL
                        rd.range = (rd.range shl 8) and 0xFFFFFFFFL
                    }
                    0
                } else {
                    rd.range = (rd.range - newBound) and 0xFFFFFFFFL
                    rd.code = (rd.code - newBound) and 0xFFFFFFFFL
                    probs[idx] = prob - (prob ushr 5)
                    if (rd.range < rd.kTopValue) {
                        val b = rd.stream.read()
                        if (b < 0) throw GradleException("EOF in decodeBit(1)")
                        rd.code = ((rd.code shl 8) or b.toLong()) and 0xFFFFFFFFL
                        rd.range = (rd.range shl 8) and 0xFFFFFFFFL
                    }
                    1
                }
            }

            fun bitTreeDecode(models: IntArray, numBits: Int, rd: MirrorRangeDecoder): Int {
                var m = 1
                for (bit in numBits downTo 1) {
                    m = (m shl 1) + decodeBit(models, m, rd)
                }
                return m - (1 shl numBits)
            }

            fun bitTreeReverseDecode(models: IntArray, startIdx: Int, numBits: Int, rd: MirrorRangeDecoder): Int {
                var m = 1
                var sym = 0
                for (bitIdx in 0 until numBits) {
                    val bit = decodeBit(models, startIdx + m, rd)
                    m = (m shl 1) + bit
                    sym = sym or (bit shl bitIdx)
                }
                return sym
            }

            val mirrorCrc = CRC32()
            val expectedWholeCrc = CRC32().apply { update(rawPackBytes) }.value

            BufferedInputStream(FileInputStream(testLzma), 65536).use { fis ->
                val hdr = ByteArray(13)
                if (fis.read(hdr) != 13) throw GradleException("Short header in mirror check")
                val p0 = hdr[0].toInt() and 0xFF
                val lc = p0 % 9
                val rem = p0 / 9
                val lp = rem % 5
                val pb = rem / 5
                var dictSize = 0
                for (i in 0 until 4) dictSize += (hdr[1 + i].toInt() and 0xFF) shl (i * 8)
                val outSize = ByteBuffer.wrap(hdr, 5, 8).order(ByteOrder.LITTLE_ENDIAN).long

                val winSize = maxOf(dictSize, 4096)
                val win = ByteArray(winSize)
                var winPos = 0
                var streamPos = 0
                fun flushWin() {
                    val sz = winPos - streamPos
                    if (sz > 0) {
                        mirrorCrc.update(win, streamPos, sz)
                        if (winPos >= winSize) winPos = 0
                        streamPos = winPos
                    }
                }
                fun getWinByte(dist: Int): Int {
                    val p = if (winPos > dist) (winPos - dist - 1) else (winSize + winPos - dist - 1)
                    return win[p].toInt() and 0xFF
                }

                val rd = MirrorRangeDecoder().apply { init(fis) }
                val numPosStates = 1 shl pb
                val posStateMask = numPosStates - 1
                val litPosMask = (1 shl lp) - 1

                val isMatch = IntArray(12 shl 4) { 1024 }
                val isRep = IntArray(12) { 1024 }
                val isRepG0 = IntArray(12) { 1024 }
                val isRepG1 = IntArray(12) { 1024 }
                val isRepG2 = IntArray(12) { 1024 }
                val isRep0Long = IntArray(12 shl 4) { 1024 }
                val posSlot = Array(4) { IntArray(1 shl 6) { 1024 } }
                val posDecoders = IntArray(128 - 14) { 1024 }
                val posAlign = IntArray(1 shl 4) { 1024 }

                val lenChoice = IntArray(2) { 1024 }
                val lenLow = Array(numPosStates) { IntArray(1 shl 3) { 1024 } }
                val lenMid = Array(numPosStates) { IntArray(1 shl 3) { 1024 } }
                val lenHigh = IntArray(1 shl 8) { 1024 }

                val repChoice = IntArray(2) { 1024 }
                val repLow = Array(numPosStates) { IntArray(1 shl 3) { 1024 } }
                val repMid = Array(numPosStates) { IntArray(1 shl 3) { 1024 } }
                val repHigh = IntArray(1 shl 8) { 1024 }

                val litCoders = Array(1 shl (lp + lc)) { IntArray(0x300) { 1024 } }

                fun decodeLen(choice: IntArray, low: Array<IntArray>, mid: Array<IntArray>, high: IntArray, ps: Int): Int {
                    if (decodeBit(choice, 0, rd) == 0) return bitTreeDecode(low[ps], 3, rd)
                    return if (decodeBit(choice, 1, rd) == 0) 8 + bitTreeDecode(mid[ps], 3, rd)
                    else 16 + bitTreeDecode(high, 8, rd)
                }

                var state = 0
                var rep0 = 0
                var rep1 = 0
                var rep2 = 0
                var rep3 = 0
                var nowPos = 0L

                while (nowPos < outSize) {
                    val ps = nowPos.toInt() and posStateMask
                    if (decodeBit(isMatch, (state shl 4) + ps, rd) == 0) {
                        val prevByte = if (nowPos == 0L) 0 else getWinByte(0)
                        val litState = ((nowPos.toInt() and litPosMask) shl lc) + (prevByte ushr (8 - lc))
                        val dec = litCoders[litState]
                        var sym = 1
                        if (state >= 7) {
                            var matchByte = getWinByte(rep0)
                            do {
                                val matchBit = (matchByte ushr 7) and 1
                                matchByte = (matchByte shl 1) and 0xFF
                                val bit = decodeBit(dec, ((1 + matchBit) shl 8) + sym, rd)
                                sym = (sym shl 1) or bit
                                if (matchBit != bit) {
                                    while (sym < 0x100) sym = (sym shl 1) or decodeBit(dec, sym, rd)
                                    break
                                }
                            } while (sym < 0x100)
                        } else {
                            do {
                                sym = (sym shl 1) or decodeBit(dec, sym, rd)
                            } while (sym < 0x100)
                        }
                        win[winPos++] = sym.toByte()
                        if (winPos >= winSize || (winPos - streamPos) >= 65536) flushWin()
                        state = if (state < 4) 0 else if (state < 10) state - 3 else state - 6
                        nowPos++
                    } else {
                        val len: Int
                        if (decodeBit(isRep, state, rd) == 1) {
                            if (decodeBit(isRepG0, state, rd) == 0) {
                                if (decodeBit(isRep0Long, (state shl 4) + ps, rd) == 0) {
                                    state = if (state < 7) 9 else 11
                                    val repByte = getWinByte(rep0).toByte()
                                    win[winPos++] = repByte
                                    if (winPos >= winSize || (winPos - streamPos) >= 65536) flushWin()
                                    nowPos++
                                    continue
                                }
                            } else {
                                val dist: Int
                                if (decodeBit(isRepG1, state, rd) == 0) {
                                    dist = rep1
                                } else {
                                    if (decodeBit(isRepG2, state, rd) == 0) {
                                        dist = rep2
                                    } else {
                                        dist = rep3
                                        rep3 = rep2
                                    }
                                    rep2 = rep1
                                }
                                rep1 = rep0
                                rep0 = dist
                            }
                            len = decodeLen(repChoice, repLow, repMid, repHigh, ps) + 2
                            state = if (state < 7) 8 else 11
                        } else {
                            rep3 = rep2
                            rep2 = rep1
                            rep1 = rep0
                            len = 2 + decodeLen(lenChoice, lenLow, lenMid, lenHigh, ps)
                            state = if (state < 7) 7 else 10
                            val lenState = if (len - 2 < 4) len - 2 else 3
                            val pSlot = bitTreeDecode(posSlot[lenState], 6, rd)
                            if (pSlot >= 4) {
                                val numDirect = (pSlot ushr 1) - 1
                                rep0 = (2 or (pSlot and 1)) shl numDirect
                                if (pSlot < 14) {
                                    rep0 += bitTreeReverseDecode(posDecoders, rep0 - pSlot - 1, numDirect, rd)
                                } else {
                                    rep0 += rd.decodeDirectBits(numDirect - 4) shl 4
                                    rep0 += bitTreeReverseDecode(posAlign, 0, 4, rd)
                                }
                            } else {
                                rep0 = pSlot
                            }
                        }
                        var p = if (winPos > rep0) (winPos - rep0 - 1) else (winSize + winPos - rep0 - 1)
                        for (k in 0 until len) {
                            if (p >= winSize) p = 0
                            win[winPos++] = win[p++]
                            if (winPos >= winSize || (winPos - streamPos) >= 65536) flushWin()
                        }
                        nowPos += len
                    }
                }
                flushWin()
            }
            if (mirrorCrc.value != expectedWholeCrc) {
                throw GradleException("C#-mirror LzmaDecoder whole-stream CRC32 mismatch: ${mirrorCrc.value} != $expectedWholeCrc")
            }
        }

        val rawMb = String.format(Locale.US, "%.2f", rawPackBytes.size / (1024.0 * 1024.0))
        val lzmaMb = String.format(Locale.US, "%.2f", testLzma.length() / (1024.0 * 1024.0))
        testLzma.delete()
        logger.lifecycle(
            "[VERIFIED] Release Distributable + C#-Mirror Solid LZMA Round-Trip OK: files=$verifiedEntries, prop=0x${propByte.toString(16)}, dict=${dictSizeHeader / (1024 * 1024)}MB, raw=${rawMb}MB -> solidLzma=${lzmaMb}MB"
        )
    }
}
