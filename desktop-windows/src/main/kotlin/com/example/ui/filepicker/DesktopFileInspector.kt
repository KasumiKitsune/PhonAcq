package com.example.ui.filepicker

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.example.textgrid.codec.TextGridCodec
import com.example.textgrid.model.TextGridDocument
import com.example.textgrid.model.TextGridFormatVariant
import com.example.textgrid.persistence.WordTextGridStore
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import java.util.zip.ZipFile
import javax.imageio.ImageIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class StudioFileSemanticKind(
    val shortBadge: String,
    val typeLabel: String
) {
    FOLDER("DIR", "文件夹"),
    SPREADSHEET("XLSX", "字表表格"),
    CSV_TSV("CSV", "分隔字表"),
    DOCUMENT("DOC", "文本文档"),
    TEPROJ("TEPROJ", "PhonTracer 工程"),
    ARCHIVE("ZIP", "归档压缩包"),
    TEXTGRID("PRAAT", "Praat 标注"),
    IMAGE("IMG", "图像配图"),
    AUDIO("WAV", "语音音频"),
    JSON("JSON", "结构化数据"),
    OTHER("FILE", "通用文件")
}

data class TextGridInspectorDetail(
    val document: TextGridDocument,
    val rawSourceText: String,
    val variantLabel: String,
    val encodingLabel: String,
    val durationSec: Double,
    val tierCount: Int,
    val intervalTierCount: Int,
    val textTierCount: Int,
    val totalIntervals: Int,
    val totalPoints: Int,
    val collectionDocCount: Int = 1,
    val anomalySummary: String? = null,
    val pairedAudioFile: File? = null
)

data class FileInspectorPreview(
    val file: File,
    val kind: StudioFileSemanticKind,
    val formattedSize: String,
    val formattedModified: String,
    val summaryHighlight: String? = null,
    val imageDimensions: String? = null,
    val textSnippet: String? = null,
    val archiveDetails: List<String> = emptyList(),
    val textGridDetail: TextGridInspectorDetail? = null,
    val textGridParseError: String? = null,
    val pairedAudioFile: File? = null
)

object DesktopFileInspector {

    private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "bmp", "gif")
    private val TEXTGRID_EXTENSIONS = setOf("textgrid", "collection")
    private val TEXT_PREVIEW_EXTENSIONS = setOf("txt", "csv", "tsv", "json", "md")

    private val thumbnailCache: MutableMap<String, ImageBitmap> = Collections.synchronizedMap(
        object : LinkedHashMap<String, ImageBitmap>(96, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
                return size > 120
            }
        }
    )

    fun classifyFile(file: File): StudioFileSemanticKind {
        if (file.isDirectory) return StudioFileSemanticKind.FOLDER
        return when (file.extension.lowercase()) {
            "xlsx", "xls" -> StudioFileSemanticKind.SPREADSHEET
            "csv", "tsv" -> StudioFileSemanticKind.CSV_TSV
            "docx", "doc", "txt", "md" -> StudioFileSemanticKind.DOCUMENT
            "teproj" -> StudioFileSemanticKind.TEPROJ
            "zip" -> StudioFileSemanticKind.ARCHIVE
            "textgrid", "collection" -> StudioFileSemanticKind.TEXTGRID
            in IMAGE_EXTENSIONS -> StudioFileSemanticKind.IMAGE
            "wav", "mp3", "flac", "m4a", "ogg" -> StudioFileSemanticKind.AUDIO
            "json" -> StudioFileSemanticKind.JSON
            else -> StudioFileSemanticKind.OTHER
        }
    }

    fun isImageFile(file: File): Boolean {
        return file.isFile && file.extension.lowercase() in IMAGE_EXTENSIONS
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 0L) return "—"
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.1f GB", gb)
    }

    fun formatTimestamp(epochMs: Long): String {
        if (epochMs <= 0L) return "—"
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(epochMs))
    }

    suspend fun loadThumbnail(file: File, maxEdgePx: Int = 192): ImageBitmap? = withContext(Dispatchers.IO) {
        if (!isImageFile(file) || !file.canRead()) return@withContext null
        val cacheKey = "${file.absolutePath}|${file.lastModified()}|${file.length()}|$maxEdgePx"
        thumbnailCache[cacheKey]?.let { return@withContext it }

        try {
            val raw: BufferedImage = ImageIO.read(file) ?: return@withContext null
            val srcW = raw.width.coerceAtLeast(1)
            val srcH = raw.height.coerceAtLeast(1)
            val scale = (maxEdgePx.toFloat() / maxOf(srcW, srcH).toFloat()).coerceAtMost(1f)
            val dstW = (srcW * scale).toInt().coerceAtLeast(1)
            val dstH = (srcH * scale).toInt().coerceAtLeast(1)

            val scaled = BufferedImage(dstW, dstH, BufferedImage.TYPE_INT_ARGB)
            val g2 = scaled.createGraphics()
            try {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                g2.drawImage(raw, 0, 0, dstW, dstH, null)
            } finally {
                g2.dispose()
            }
            val bitmap = scaled.toComposeImageBitmap()
            thumbnailCache[cacheKey] = bitmap
            bitmap
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun inspectFile(file: File): FileInspectorPreview = withContext(Dispatchers.IO) {
        val kind = classifyFile(file)
        val modifiedText = formatTimestamp(file.lastModified())

        if (file.isDirectory) {
            val children = try {
                file.listFiles()?.toList().orEmpty()
            } catch (_: Exception) {
                emptyList()
            }
            val dirCount = children.count { it.isDirectory }
            val fileCount = children.size - dirCount
            return@withContext FileInspectorPreview(
                file = file,
                kind = kind,
                formattedSize = "${children.size} 项",
                formattedModified = modifiedText,
                summaryHighlight = "包含 $dirCount 个子文件夹 · $fileCount 个文件"
            )
        }

        val sizeText = formatBytes(file.length())
        val ext = file.extension.lowercase()

        when {
            ext in IMAGE_EXTENSIONS -> {
                val dims = readImageDimensionsFast(file)
                FileInspectorPreview(
                    file = file,
                    kind = kind,
                    formattedSize = sizeText,
                    formattedModified = modifiedText,
                    imageDimensions = dims?.let { "${it.first} × ${it.second} px" },
                    summaryHighlight = dims?.let { "像素尺寸 ${it.first} × ${it.second}" }
                )
            }

            ext in TEXTGRID_EXTENSIONS || file.name.lowercase(Locale.ROOT).endsWith(".textgrid.gz") -> {
                val parsed = runCatching { TextGridCodec.parseFile(file) }.getOrNull()
                val pairedAudio = runCatching { WordTextGridStore.findPairedAudioForTextGrid(file) }.getOrNull()
                val doc = parsed?.document
                if (parsed != null && parsed.isSuccess && doc != null) {
                    val variantLabel = when (parsed.detectedVariant) {
                        TextGridFormatVariant.LONG_TEXT -> "长文本 (ooTextFile)"
                        TextGridFormatVariant.SHORT_TEXT -> "短文本 (short)"
                        TextGridFormatVariant.BINARY -> "二进制 (ooBinaryFile)"
                        TextGridFormatVariant.CHRONOLOGICAL -> "时序格式 (Chronological)"
                    }
                    val rawSrc = parsed.normalizedSource ?: TextGridCodec.serialize(doc, TextGridFormatVariant.LONG_TEXT)
                    val anomalyHint = if (parsed.anomalies.isNotEmpty()) {
                        "检测到 ${parsed.anomalies.size} 处间隙/重叠提示（已如实标注）"
                    } else null
                    val statsBadge = buildString {
                        append("${doc.tierCount} 层")
                        if (doc.totalIntervalCount > 0) append(" · ${doc.totalIntervalCount} 区间")
                        if (doc.totalPointCount > 0) append(" · ${doc.totalPointCount} 标点")
                        append(String.format(Locale.US, " · %.2fs", doc.duration))
                        if (parsed.collectionDocuments.size > 1) {
                            append("（集合共 ${parsed.collectionDocuments.size} 份）")
                        }
                    }
                    FileInspectorPreview(
                        file = file,
                        kind = StudioFileSemanticKind.TEXTGRID,
                        formattedSize = sizeText,
                        formattedModified = modifiedText,
                        summaryHighlight = statsBadge,
                        textSnippet = rawSrc.lines().take(16).joinToString("\n"),
                        textGridDetail = TextGridInspectorDetail(
                            document = doc,
                            rawSourceText = rawSrc,
                            variantLabel = variantLabel,
                            encodingLabel = parsed.detectedEncoding,
                            durationSec = doc.duration,
                            tierCount = doc.tierCount,
                            intervalTierCount = doc.intervalTierCount,
                            textTierCount = doc.textTierCount,
                            totalIntervals = doc.totalIntervalCount,
                            totalPoints = doc.totalPointCount,
                            collectionDocCount = parsed.collectionDocuments.size.coerceAtLeast(1),
                            anomalySummary = anomalyHint,
                            pairedAudioFile = pairedAudio
                        ),
                        pairedAudioFile = pairedAudio
                    )
                } else {
                    val fatalMsg = parsed?.fatalDiagnostics?.firstOrNull()?.let { d ->
                        "第 ${d.line} 行：${d.message}"
                    } ?: "无法解析为有效 Praat TextGrid"
                    val snippetPair = readSmartTextSnippet(file, maxLines = 14)
                    FileInspectorPreview(
                        file = file,
                        kind = StudioFileSemanticKind.TEXTGRID,
                        formattedSize = sizeText,
                        formattedModified = modifiedText,
                        summaryHighlight = "语法异常 · $fatalMsg",
                        textSnippet = snippetPair?.second,
                        textGridParseError = fatalMsg,
                        pairedAudioFile = pairedAudio
                    )
                }
            }

            ext in TEXT_PREVIEW_EXTENSIONS -> {
                val snippetPair = readSmartTextSnippet(file, maxLines = 14)
                FileInspectorPreview(
                    file = file,
                    kind = kind,
                    formattedSize = sizeText,
                    formattedModified = modifiedText,
                    summaryHighlight = snippetPair?.first,
                    textSnippet = snippetPair?.second
                )
            }

            ext == "zip" || ext == "teproj" -> {
                val details = inspectZipOrTeprojArchive(file)
                FileInspectorPreview(
                    file = file,
                    kind = kind,
                    formattedSize = sizeText,
                    formattedModified = modifiedText,
                    summaryHighlight = details.firstOrNull(),
                    archiveDetails = details.drop(1)
                )
            }

            ext == "xlsx" -> {
                val sheetCount = countOoxmlWorksheets(file)
                FileInspectorPreview(
                    file = file,
                    kind = kind,
                    formattedSize = sizeText,
                    formattedModified = modifiedText,
                    summaryHighlight = if (sheetCount > 0) "Excel 工作簿 · 共 $sheetCount 个工作表" else "Excel 电子表格"
                )
            }

            ext == "docx" -> {
                FileInspectorPreview(
                    file = file,
                    kind = kind,
                    formattedSize = sizeText,
                    formattedModified = modifiedText,
                    summaryHighlight = "Word 文档 · 支持直接提取词条字表"
                )
            }

            else -> FileInspectorPreview(
                file = file,
                kind = kind,
                formattedSize = sizeText,
                formattedModified = modifiedText
            )
        }
    }

    private fun readImageDimensionsFast(file: File): Pair<Int, Int>? {
        return try {
            ImageIO.createImageInputStream(file)?.use { stream ->
                val readers = ImageIO.getImageReaders(stream)
                if (readers.hasNext()) {
                    val reader = readers.next()
                    try {
                        reader.input = stream
                        val w = reader.getWidth(0)
                        val h = reader.getHeight(0)
                        if (w > 0 && h > 0) return w to h
                    } finally {
                        reader.dispose()
                    }
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun readSmartTextSnippet(file: File, maxLines: Int): Pair<String, String>? {
        return try {
            val maxBytes = 12 * 1024
            val rawBytes = file.inputStream().use { ins ->
                val buf = ByteArray(maxBytes)
                val read = ins.read(buf)
                if (read <= 0) return "空文件 (0 行)" to ""
                buf.copyOf(read)
            }

            val (charsetName, decoded) = decodeWithBestCharset(rawBytes)
            val normalized = decoded.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
            val allLines = normalized.lines()
            val previewLines = allLines
                .filter { it.isNotBlank() }
                .take(maxLines)
                .map { if (it.length > 90) it.take(90) + "…" else it }
            "$charsetName 文本预览" to previewLines.joinToString("\n")
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeWithBestCharset(bytes: ByteArray): Pair<String, String> {
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 0xFE && b1 == 0xFF) {
                return "UTF-16BE" to String(bytes, StandardCharsets.UTF_16BE)
            }
            if (b0 == 0xFF && b1 == 0xFE) {
                return "UTF-16LE" to String(bytes, StandardCharsets.UTF_16LE)
            }
        }
        val utf8 = String(bytes, StandardCharsets.UTF_8)
        val replacementCount = utf8.count { it == '\uFFFD' }
        if (replacementCount <= 1) {
            return "UTF-8" to utf8
        }
        return try {
            val gb = Charset.forName("GB18030")
            "GB18030" to String(bytes, gb)
        } catch (_: Exception) {
            "UTF-8" to utf8
        }
    }

    private fun inspectZipOrTeprojArchive(file: File): List<String> {
        return try {
            ZipFile(file).use { zf ->
                var totalFiles = 0
                var wavCount = 0
                var textGridCount = 0
                var hasPhonTracerJson = false
                var hasPhonAcqMeta = false
                val entries = zf.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    if (e.isDirectory) continue
                    totalFiles++
                    val lower = e.name.lowercase()
                    when {
                        lower.endsWith(".wav") -> wavCount++
                        lower.endsWith(".textgrid") -> textGridCount++
                        lower == "project.json" || lower.endsWith("/project.json") -> hasPhonTracerJson = true
                        lower.contains("wordlist") || lower.endsWith(".xlsx") || lower.endsWith(".csv") -> hasPhonAcqMeta = true
                    }
                }
                buildList {
                    when {
                        hasPhonTracerJson -> add("PhonTracer 标准语料工程")
                        hasPhonAcqMeta || wavCount > 0 -> add("PhonAcq 语料压缩包")
                        else -> add("ZIP 归档包 · 共 $totalFiles 个文件")
                    }
                    add("内部文件总数：$totalFiles 个")
                    if (wavCount > 0) add("音频文件 (.wav)：$wavCount 条")
                    if (textGridCount > 0) add("Praat 标注 (.TextGrid)：$textGridCount 份")
                }
            }
        } catch (_: Exception) {
            listOf("压缩归档文件")
        }
    }

    private fun countOoxmlWorksheets(file: File): Int {
        return try {
            ZipFile(file).use { zf ->
                var count = 0
                val entries = zf.entries()
                while (entries.hasMoreElements()) {
                    val name = entries.nextElement().name.lowercase()
                    if (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")) {
                        count++
                    }
                }
                count
            }
        } catch (_: Exception) {
            0
        }
    }
}
