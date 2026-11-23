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
import java.util.Properties
import java.util.concurrent.Callable
import java.util.concurrent.Executors
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
            "**/utils/SmartImportPlatformBridge.kt",
            "**/utils/AppUpdatePlatformBridge.kt"
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

data class MultiSolidPackStats(
    val fileCount: Int,
    val totalRawBytes: Long,
    val payloadBytes: Long,
    val chunkSummaries: List<String>
)

/**
 * Resolves a full JDK containing `jpackage` when Gradle itself is launched under a stripped JBR
 * (such as Android Studio's bundled JBR, which omits `jdk.jpackage`). When `java.home` already
 * provides `jpackage` (e.g. Temurin 17 on GitHub Actions Windows/macOS runners), returns `null`
 * so default `JAVA_HOME` behavior is untouched.
 */
fun resolvePackagingJavaHome(): String? {
    val isWin = System.getProperty("os.name", "").lowercase(Locale.ROOT).contains("win")
    val jpkgName = if (isWin) "jpackage.exe" else "jpackage"
    val currentHome = System.getProperty("java.home") ?: ""
    if (currentHome.isNotBlank() && File(currentHome, "bin/$jpkgName").isFile) {
        return null
    }

    val userHome = System.getProperty("user.home") ?: ""
    val searchRoots = mutableListOf<File>()
    if (userHome.isNotBlank()) {
        searchRoots.add(File(userHome, ".jdks"))
        searchRoots.add(File(userHome, ".gradle/jdks"))
    }
    if (isWin) {
        searchRoots.add(File("C:\\Program Files\\Eclipse Adoptium"))
        searchRoots.add(File("C:\\Program Files\\Java"))
        searchRoots.add(File("C:\\Program Files\\Microsoft"))
    }

    val candidates = searchRoots
        .filter { it.isDirectory }
        .flatMap { root -> root.listFiles()?.filter { it.isDirectory } ?: emptyList() }
        .mapNotNull { dir ->
            when {
                File(dir, "bin/$jpkgName").isFile -> dir
                File(dir, "Contents/Home/bin/$jpkgName").isFile -> File(dir, "Contents/Home")
                else -> null
            }
        }
        .sortedWith(
            compareByDescending<File> { it.name.contains("17") }
                .thenByDescending { it.name }
        )

    return candidates.firstOrNull()?.absolutePath
}

/**
 * Builds the 4-way parallel solid LZMA archive (`PAQMPK1!` + `PAQBLK1!`) consumed by `ParallelSolidExtractor`
 * in `PhonAcqInstaller.cs`:
 *  - Chunk 1 (index 0): All native `.dll` / `.exe` (JVM + Skia + STORED native libs inside JARs)
 *  - Chunk 2 (index 1): JVM `modules` + app `.jar`s + remaining general runtime files
 *  - Chunk 3 (index 2): `SarasaUiSC-Regular` + `SarasaUiSC-SemiBold` (grouped in same solid dictionary)
 *  - Chunk 4 (index 3): `Plangothic` P1 + P2 + IPA fonts
 */
fun writeParallelMultiSolidPayload(appImageDir: File, destPayloadFile: File): MultiSolidPackStats {
    data class CarvedJarRange(val start: Int, val end: Int, val chunkIndex: Int, val entryName: String)
    data class RawSliceSpec(
        val fileId: Int,
        val relPath: String,
        val sortGroup: Int,
        val sortKey: String,
        val fileOffset: Long,
        val bytes: ByteArray,
        val crc32: Int
    )
    data class ManifestRecord(
        val relPath: String,
        val totalLength: Long,
        val lastModifiedMs: Long,
        val wholeCrc32: Int,
        var sliceCount: Int
    )

    fun readU16Le(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)

    fun readU32Le(b: ByteArray, off: Int): Long =
        (b[off].toLong() and 0xFFL) or
            ((b[off + 1].toLong() and 0xFFL) shl 8) or
            ((b[off + 2].toLong() and 0xFFL) shl 16) or
            ((b[off + 3].toLong() and 0xFFL) shl 24)

    fun writeLeInt(os: OutputStream, v: Int) {
        val b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
        os.write(b)
    }

    fun writeLeLong(os: OutputStream, v: Long) {
        val b = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array()
        os.write(b)
    }

    fun computeCrc32(bytes: ByteArray, off: Int = 0, len: Int = bytes.size): Int {
        val c = CRC32()
        c.update(bytes, off, len)
        return c.value.toInt()
    }

    fun classifySpecialEntryChunk(entryPathLower: String): Int {
        val base = entryPathLower.substringAfterLast('/')
        return when {
            base.endsWith(".dll") || base.endsWith(".exe") -> 0
            base.contains("sarasauisc") -> 2
            base.contains("plangothic") || base.contains("phonacq_ipa") ||
                base.endsWith(".ttf") || base.endsWith(".otf") || base.endsWith(".ttc") -> 3
            else -> -1
        }
    }

    fun carveStoredJarRanges(jarBytes: ByteArray, relPath: String): List<CarvedJarRange> {
        if (jarBytes.size < 22) return emptyList()
        val minSearch = maxOf(0, jarBytes.size - 65557)
        var eocdPos = -1
        for (pos in (jarBytes.size - 22) downTo minSearch) {
            if (readU32Le(jarBytes, pos) == 0x06054B50L) {
                val commentLen = readU16Le(jarBytes, pos + 20)
                if (pos + 22 + commentLen == jarBytes.size) {
                    eocdPos = pos
                    break
                }
            }
        }
        if (eocdPos < 0) return emptyList()

        val cdCount = readU16Le(jarBytes, eocdPos + 10)
        val cdOffset = readU32Le(jarBytes, eocdPos + 16)
        if (cdCount <= 0 || cdOffset < 0L || cdOffset >= jarBytes.size.toLong()) return emptyList()

        val carved = mutableListOf<CarvedJarRange>()
        var cur = cdOffset.toInt()
        for (idx in 0 until cdCount) {
            if (cur + 46 > jarBytes.size || readU32Le(jarBytes, cur) != 0x02014B50L) {
                throw GradleException("Malformed ZIP Central Directory in $relPath at offset $cur")
            }
            val method = readU16Le(jarBytes, cur + 10)
            val compSize = readU32Le(jarBytes, cur + 20)
            val uncompSize = readU32Le(jarBytes, cur + 24)
            val nameLen = readU16Le(jarBytes, cur + 28)
            val extraLen = readU16Le(jarBytes, cur + 30)
            val commentLen = readU16Le(jarBytes, cur + 32)
            val locOffset = readU32Le(jarBytes, cur + 42)
            val entryName = String(jarBytes, cur + 46, nameLen, Charsets.UTF_8)
            cur += 46 + nameLen + extraLen + commentLen

            if (method == 0 && compSize >= 4096L && compSize == uncompSize) {
                val targetChunk = classifySpecialEntryChunk(entryName.lowercase(Locale.ROOT))
                if (targetChunk >= 0) {
                    val locPos = locOffset.toInt()
                    if (locPos < 0 || locPos + 30 > jarBytes.size || readU32Le(jarBytes, locPos) != 0x04034B50L) {
                        throw GradleException("Malformed ZIP Local Header for $entryName in $relPath")
                    }
                    val locNameLen = readU16Le(jarBytes, locPos + 26)
                    val locExtraLen = readU16Le(jarBytes, locPos + 28)
                    val dataStart = locPos + 30 + locNameLen + locExtraLen
                    val dataEnd = dataStart + compSize.toInt()
                    if (dataStart < 0 || dataEnd > jarBytes.size || dataStart >= dataEnd) {
                        throw GradleException("Out-of-bounds STORED entry slice for $entryName in $relPath")
                    }
                    carved.add(CarvedJarRange(dataStart, dataEnd, targetChunk, entryName))
                }
            }
        }

        val sorted = carved.sortedBy { it.start }
        for (i in 1 until sorted.size) {
            if (sorted[i - 1].end > sorted[i].start) {
                throw GradleException("Overlapping ZIP entry ranges in $relPath")
            }
        }
        return sorted
    }

    val basePath = appImageDir.toPath()
    val allFiles = appImageDir.walkTopDown()
        .filter { it.isFile }
        .sortedBy { basePath.relativize(it.toPath()).toString().replace('\\', '/') }
        .toList()

    val manifest = ArrayList<ManifestRecord>(allFiles.size)
    val chunkSlices = Array(4) { mutableListOf<RawSliceSpec>() }
    var totalContentBytes = 0L

    for ((fileId, f) in allFiles.withIndex()) {
        val relPath = basePath.relativize(f.toPath()).toString().replace('\\', '/')
        val relLower = relPath.lowercase(Locale.ROOT)
        val baseLower = f.name.lowercase(Locale.ROOT)
        val extLower = f.extension.lowercase(Locale.ROOT)
        val rawBytes = f.readBytes()
        val fileLen = rawBytes.size.toLong()
        val wholeCrc = computeCrc32(rawBytes)
        totalContentBytes += fileLen

        if (rawBytes.isEmpty()) {
            manifest.add(ManifestRecord(relPath, 0L, f.lastModified(), wholeCrc, 0))
            continue
        }

        if (extLower == "jar") {
            val carved = carveStoredJarRanges(rawBytes, relPath)
            if (carved.isEmpty()) {
                manifest.add(ManifestRecord(relPath, fileLen, f.lastModified(), wholeCrc, 1))
                chunkSlices[1].add(
                    RawSliceSpec(fileId, relPath, 1, relLower, 0L, rawBytes, wholeCrc)
                )
            } else {
                var sliceCount = 0
                var cursor = 0
                val verifyAssemble = ByteArray(rawBytes.size)
                var verifyBytesCount = 0

                fun emitJarSlice(start: Int, end: Int, chunkIdx: Int, group: Int, key: String) {
                    if (end <= start) return
                    val sliceBytes = rawBytes.copyOfRange(start, end)
                    val sliceCrc = computeCrc32(sliceBytes)
                    System.arraycopy(sliceBytes, 0, verifyAssemble, start, sliceBytes.size)
                    verifyBytesCount += sliceBytes.size
                    sliceCount++
                    chunkSlices[chunkIdx].add(
                        RawSliceSpec(fileId, relPath, group, key, start.toLong(), sliceBytes, sliceCrc)
                    )
                }

                for (r in carved) {
                    if (r.start > cursor) {
                        emitJarSlice(cursor, r.start, 1, 1, "$relLower#${String.format(Locale.US, "%010d", cursor)}")
                    }
                    val entryKey = r.entryName.lowercase(Locale.ROOT)
                    val sortGrp = when {
                        r.chunkIndex == 2 && entryKey.contains("regular") -> 0
                        r.chunkIndex == 2 -> 1
                        r.chunkIndex == 3 && entryKey.contains("plangothicp1") -> 0
                        r.chunkIndex == 3 && entryKey.contains("plangothicp2") -> 1
                        r.chunkIndex == 3 && entryKey.contains("plangothic") -> 2
                        r.chunkIndex == 3 -> 3
                        else -> 1
                    }
                    emitJarSlice(r.start, r.end, r.chunkIndex, sortGrp, entryKey)
                    cursor = r.end
                }
                if (cursor < rawBytes.size) {
                    emitJarSlice(cursor, rawBytes.size, 1, 1, "$relLower#${String.format(Locale.US, "%010d", cursor)}")
                }

                if (verifyBytesCount != rawBytes.size || computeCrc32(verifyAssemble) != wholeCrc) {
                    throw GradleException("JAR slice reconstruction verification failed for $relPath")
                }
                manifest.add(ManifestRecord(relPath, fileLen, f.lastModified(), wholeCrc, sliceCount))
            }
        } else {
            val targetChunk = classifySpecialEntryChunk(relLower).let { if (it >= 0) it else 1 }
            val sortGrp = when (targetChunk) {
                0 -> if (extLower == "exe") 0 else 1
                1 -> when {
                    baseLower == "modules" -> 0
                    extLower == "class" -> 1
                    extLower == "dat" -> 2
                    else -> 3
                }
                2 -> if (baseLower.contains("regular")) 0 else 1
                else -> when {
                    baseLower.contains("plangothicp1") -> 0
                    baseLower.contains("plangothicp2") -> 1
                    baseLower.contains("plangothic") -> 2
                    else -> 3
                }
            }
            manifest.add(ManifestRecord(relPath, fileLen, f.lastModified(), wholeCrc, 1))
            chunkSlices[targetChunk].add(
                RawSliceSpec(fileId, relPath, sortGrp, relLower, 0L, rawBytes, wholeCrc)
            )
        }
    }

    val chunkLabels = listOf(
        "Chunk 1 [Native DLL/EXE]",
        "Chunk 2 [JVM modules + JARs + Runtime]",
        "Chunk 3 [SarasaUiSC Regular+SemiBold]",
        "Chunk 4 [Plangothic P1/P2 + IPA Fonts]"
    )

    // Build deterministic uncompressed PAQBLK1! byte buffers for all non-empty chunks
    data class BuiltRawChunk(val label: String, val sliceCount: Int, val payloadBytes: Long, val rawBlockBytes: ByteArray)
    val builtRawChunks = mutableListOf<BuiltRawChunk>()
    var accountedPayloadBytes = 0L

    for (cIdx in 0 until 4) {
        val sortedSlices = chunkSlices[cIdx].sortedWith(
            compareBy<RawSliceSpec>({ it.sortGroup }, { it.sortKey }, { it.fileOffset })
        )
        if (sortedSlices.isEmpty()) continue
        val sliceSum = sortedSlices.sumOf { it.bytes.size.toLong() }
        accountedPayloadBytes += sliceSum
        val cap = (12L + sortedSlices.size * 24L + sliceSum).coerceAtMost(512L * 1024 * 1024).toInt()
        val blkOut = ByteArrayOutputStream(cap)
        blkOut.write("PAQBLK1!".toByteArray(Charsets.US_ASCII))
        writeLeInt(blkOut, sortedSlices.size)
        for (s in sortedSlices) {
            writeLeInt(blkOut, s.fileId)
            writeLeLong(blkOut, s.fileOffset)
            writeLeLong(blkOut, s.bytes.size.toLong())
            writeLeInt(blkOut, s.crc32)
            blkOut.write(s.bytes)
        }
        builtRawChunks.add(BuiltRawChunk(chunkLabels[cIdx], sortedSlices.size, sliceSum, blkOut.toByteArray()))
    }

    if (accountedPayloadBytes != totalContentBytes) {
        throw GradleException("Chunk byte coverage mismatch: $accountedPayloadBytes != $totalContentBytes")
    }

    // Compress all solid chunks concurrently across 4 worker threads using classic 13-byte header .lzma
    val pool = Executors.newFixedThreadPool(builtRawChunks.size.coerceAtLeast(1))
    val compressedBlocks: List<ByteArray> = try {
        val futures = builtRawChunks.map { chunk ->
            pool.submit(Callable {
                val rawLen = chunk.rawBlockBytes.size
                val alignedDict = (((rawLen + 1048575) / 1048576) * 1048576)
                    .coerceIn(1048576, 32 * 1024 * 1024)
                val opts = LZMA2Options(9).apply {
                    dictSize = alignedDict
                }
                val compOut = ByteArrayOutputStream((rawLen / 2).coerceAtLeast(65536))
                LZMAOutputStream(compOut, opts, rawLen.toLong()).use { lzOut ->
                    lzOut.write(chunk.rawBlockBytes)
                }
                compOut.toByteArray()
            })
        }
        futures.map { it.get() }
    } finally {
        pool.shutdown()
    }

    // Write master PAQMPK1! header + File Manifest + Block Directory + Concatenated .lzma chunks
    BufferedOutputStream(FileOutputStream(destPayloadFile), 65536).use { out ->
        out.write("PAQMPK1!".toByteArray(Charsets.US_ASCII))
        writeLeInt(out, manifest.size)
        writeLeLong(out, totalContentBytes)
        writeLeInt(out, builtRawChunks.size)

        for (m in manifest) {
            val pathUtf8 = m.relPath.toByteArray(Charsets.UTF_8)
            writeLeInt(out, pathUtf8.size)
            out.write(pathUtf8)
            writeLeLong(out, m.totalLength)
            writeLeLong(out, m.lastModifiedMs)
            writeLeInt(out, m.wholeCrc32)
            writeLeInt(out, m.sliceCount)
        }

        var compRelOffset = 0L
        for (b in builtRawChunks.indices) {
            val compLen = compressedBlocks[b].size.toLong()
            val rawBlkLen = builtRawChunks[b].rawBlockBytes.size.toLong()
            writeLeLong(out, compRelOffset)
            writeLeLong(out, compLen)
            writeLeLong(out, rawBlkLen)
            compRelOffset += compLen
        }

        for (b in compressedBlocks.indices) {
            out.write(compressedBlocks[b])
        }
    }

    val summaries = builtRawChunks.indices.map { idx ->
        val rawMb = String.format(Locale.US, "%.2f", builtRawChunks[idx].payloadBytes / (1024.0 * 1024.0))
        val compMb = String.format(Locale.US, "%.2f", compressedBlocks[idx].size / (1024.0 * 1024.0))
        "${builtRawChunks[idx].label}: slices=${builtRawChunks[idx].sliceCount}, raw=${rawMb} MB -> lzma=${compMb} MB"
    }

    return MultiSolidPackStats(
        fileCount = manifest.size,
        totalRawBytes = totalContentBytes,
        payloadBytes = destPayloadFile.length(),
        chunkSummaries = summaries
    )
}

compose.desktop {
    application {
        mainClass = "com.example.DesktopMainKt"
        resolvePackagingJavaHome()?.let { resolvedJdk ->
            javaHome = resolvedJdk
            logger.lifecycle("[compose.desktop] Using packaging JDK with jpackage: $resolvedJdk")
        }

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
            // JavaSound WAV I/O, SQLite JDBC, XML (.xlsx/.docx), GBK/GB18030 charsets, and HTTPS/TLS updates.
            includeAllModules = false
            modules(
                "java.datatransfer",
                "java.desktop",
                "java.logging",
                "java.prefs",
                "java.sql",
                "java.xml",
                "jdk.crypto.ec",
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

        // Pack release distributable into 4-way parallel solid LZMA payload (PAQMPK1! + PAQSETM3)
        val payloadMulti = File(workDir, "payload.paqmpk")
        if (payloadMulti.exists()) payloadMulti.delete()
        val packStats = writeParallelMultiSolidPayload(appImageDir, payloadMulti)
        for (line in packStats.chunkSummaries) {
            logger.lifecycle("  [packageWindowsSetup] $line")
        }

        val outDir = layout.buildDirectory.dir("installer").get().asFile
        outDir.mkdirs()
        val finalSetupExe = File(outDir, "PhonAcq-v${rawAppVersion}-windows-x64-setup.exe")
        if (finalSetupExe.exists()) finalSetupExe.delete()

        val stubLen = stubExe.length()
        val payloadLen = payloadMulti.length()
        val trailer = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("PAQSETM3".toByteArray(Charsets.US_ASCII))
            putLong(stubLen)    // payloadOffset
            putLong(payloadLen) // payloadLength
            putLong(stubLen)    // stubLength (used when extracting clean Uninstall-PhonAcq.exe)
        }.array()

        BufferedOutputStream(FileOutputStream(finalSetupExe), 65536).use { out ->
            stubExe.inputStream().use { it.copyTo(out, bufferSize = 65536) }
            payloadMulti.inputStream().use { it.copyTo(out, bufferSize = 65536) }
            out.write(trailer)
        }

        val mbStr = String.format(Locale.US, "%.2f", finalSetupExe.length() / (1024.0 * 1024.0))
        val rawMbStr = String.format(Locale.US, "%.2f", packStats.totalRawBytes / (1024.0 * 1024.0))
        logger.lifecycle(
            "Built PhonAcq Windows 4-Chunk Parallel Solid-LZMA Installer (${packStats.fileCount} files, raw=$rawMbStr MB -> installer=$mbStr MB): ${finalSetupExe.absolutePath}"
        )
    }
}

tasks.register<JavaExec>("generateMsixAssets") {
    group = "compose desktop"
    description = "Generates Microsoft Store MSIX visual PNG assets (50x50, 44x44, 150x150, 310x150) using DesktopIconRenderer"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.DesktopIconRenderer")
    args(
        "--msix-assets",
        layout.buildDirectory.dir("msix-work/Assets").get().asFile.absolutePath
    )
    jvmArgs("-Djava.awt.headless=true")
}

tasks.register("packageMsix") {
    group = "compose desktop"
    description = "Packages the Release Windows distributable into an unsigned Microsoft Store .msix bundle (coexists with packageWindowsSetup)"
    dependsOn("createReleaseDistributable", "generateMsixAssets")

    doLast {
        val osName = System.getProperty("os.name").lowercase()
        if (!osName.contains("win")) {
            throw GradleException("packageMsix requires Windows with Windows 10/11 SDK (makeappx.exe). Current OS: $osName")
        }

        val releaseAppDir = layout.buildDirectory.dir("compose/binaries/main-release/app/PhonAcq").get().asFile
        val fallbackAppDir = layout.buildDirectory.dir("compose/binaries/main/app/PhonAcq").get().asFile
        val appImageDir = if (File(releaseAppDir, "PhonAcq.exe").exists()) releaseAppDir else fallbackAppDir
        if (!appImageDir.exists() || !File(appImageDir, "PhonAcq.exe").exists()) {
            throw GradleException("Distributable app image not found at: ${releaseAppDir.absolutePath}")
        }

        val msixProps = Properties()
        val msixPropsFile = project.file("msix.properties")
        if (msixPropsFile.isFile) {
            FileInputStream(msixPropsFile).use { msixProps.load(it.reader(Charsets.UTF_8)) }
        }
        fun resolveMsixProp(cliKey: String, fileKey: String, defaultVal: String): String {
            return (project.findProperty(cliKey) as? String)?.trim()?.takeIf { it.isNotEmpty() }
                ?: msixProps.getProperty(fileKey)?.trim()?.takeIf { it.isNotEmpty() }
                ?: defaultVal
        }
        fun xmlEscape(s: String): String = s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

        val identityName = resolveMsixProp("msixIdentityName", "identityName", "KasumiKitsune.PhonAcq")
        val publisher = resolveMsixProp("msixPublisher", "publisher", "CN=KasumiKitsune")
        val publisherDisplayName = resolveMsixProp("msixPublisherDisplayName", "publisherDisplayName", "KasumiKitsune")
        val displayName = resolveMsixProp("msixDisplayName", "displayName", "PhonAcq")
        val msixVersion = "$nativePkgVersion.0"

        val assetsSrcDir = layout.buildDirectory.dir("msix-work/Assets").get().asFile
        if (!File(assetsSrcDir, "StoreLogo.png").isFile) {
            throw GradleException("Missing MSIX visual assets in ${assetsSrcDir.absolutePath}")
        }

        val stageDir = layout.buildDirectory.dir("msix-work/stage").get().asFile
        if (stageDir.exists()) stageDir.deleteRecursively()
        stageDir.mkdirs()

        // 1. Copy release app image under stage/app/ so PhonAcq.exe + runtime/ + app/ stay intact
        val stageAppDir = File(stageDir, "app")
        appImageDir.copyRecursively(stageAppDir, overwrite = true)

        // 2. Copy MSIX Store PNG assets under stage/Assets/
        val stageAssetsDir = File(stageDir, "Assets")
        assetsSrcDir.copyRecursively(stageAssetsDir, overwrite = true)

        // 3. Write Microsoft Store compliant AppxManifest.xml
        val manifestXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <Package
              xmlns="http://schemas.microsoft.com/appx/manifest/foundation/windows10"
              xmlns:uap="http://schemas.microsoft.com/appx/manifest/uap/windows10"
              xmlns:rescap="http://schemas.microsoft.com/appx/manifest/foundation/windows10/restrictedcapabilities"
              IgnorableNamespaces="uap rescap">

              <Identity
                Name="${xmlEscape(identityName)}"
                Publisher="${xmlEscape(publisher)}"
                Version="$msixVersion"
                ProcessorArchitecture="x64" />

              <Properties>
                <DisplayName>${xmlEscape(displayName)}</DisplayName>
                <PublisherDisplayName>${xmlEscape(publisherDisplayName)}</PublisherDisplayName>
                <Logo>Assets\StoreLogo.png</Logo>
              </Properties>

              <Dependencies>
                <TargetDeviceFamily Name="Windows.Desktop" MinVersion="10.0.17763.0" MaxVersionTested="10.0.26100.0" />
              </Dependencies>

              <Resources>
                <Resource Language="zh-CN" />
                <Resource Language="en-US" />
              </Resources>

              <Applications>
                <Application
                  Id="PhonAcq"
                  Executable="app\PhonAcq.exe"
                  EntryPoint="Windows.FullTrustApplication">
                  <uap:VisualElements
                    DisplayName="${xmlEscape(displayName)}"
                    Description="PhonAcq 田野语音与方言字表采录系统"
                    BackgroundColor="transparent"
                    Square150x150Logo="Assets\Square150x150Logo.png"
                    Square44x44Logo="Assets\Square44x44Logo.png">
                    <uap:DefaultTile Wide310x150Logo="Assets\Wide310x150Logo.png" />
                  </uap:VisualElements>
                </Application>
              </Applications>

              <Capabilities>
                <rescap:Capability Name="runFullTrust" />
                <Capability Name="privateNetworkClientServer" />
                <DeviceCapability Name="microphone" />
              </Capabilities>
            </Package>
        """.trimIndent() + "\n"
        File(stageDir, "AppxManifest.xml").writeText(manifestXml, Charsets.UTF_8)

        // 4. Locate makeappx.exe from Windows 10/11 SDK
        val sdkBinRoot = File("C:\\Program Files (x86)\\Windows Kits\\10\\bin")
        val makeAppxExe = sdkBinRoot
            .takeIf { it.isDirectory }
            ?.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("10.") }
            ?.sortedByDescending { it.name }
            ?.map { File(it, "x64\\makeappx.exe") }
            ?.firstOrNull { it.isFile }
            ?.absolutePath
            ?: "makeappx.exe"

        val outDir = layout.buildDirectory.dir("msix").get().asFile
        outDir.mkdirs()
        val finalMsix = File(outDir, "PhonAcq-v${rawAppVersion}-windows-x64.msix")
        if (finalMsix.exists()) finalMsix.delete()

        val cmd = listOf(
            makeAppxExe,
            "pack",
            "/v",
            "/h", "SHA256",
            "/d", stageDir.absolutePath,
            "/p", finalMsix.absolutePath
        )
        val proc = ProcessBuilder(cmd)
            .directory(stageDir)
            .redirectErrorStream(true)
            .start()
        val toolOut = proc.inputStream.bufferedReader(Charsets.UTF_8).readText()
        val exitCode = proc.waitFor()
        if (exitCode != 0 || !finalMsix.isFile) {
            throw GradleException("makeappx.exe failed (exit=$exitCode):\n$toolOut")
        }

        val mbStr = String.format(Locale.US, "%.2f", finalMsix.length() / (1024.0 * 1024.0))
        logger.lifecycle(
            "Built PhonAcq Microsoft Store MSIX ($mbStr MB, Identity=$identityName, Publisher=$publisher, Version=$msixVersion): ${finalMsix.absolutePath}"
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
