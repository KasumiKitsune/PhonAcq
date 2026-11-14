package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.data.ParsedWord
import com.example.i18n.S
import com.example.i18n.withArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Cross-platform (Android + Windows Desktop) image storage, normalization, LRU memory cache,
 * and filename-to-word parser for Picture Wordlists ("图片词卡 / 看图发音").
 *
 * Storage conventions:
 * - Draft (unsaved editor) images: `cacheDir/draft_word_images/<fileName>` -> referenced as `"staged:<fileName>"`
 * - Wireless Link cached images:   `cacheDir/link_word_images/<fileName>`  -> referenced as `"link:<fileName>"`
 * - Committed project images:      `externalFilesDir/project_<id>/images/<fileName>` -> stored in `Word.imagePath` as `"<fileName>"`
 */
object WordImageManager {

    const val STAGED_PREFIX = "staged:"
    const val LINK_PREFIX = "link:"
    const val IMAGES_SUBDIR = "images"
    private const val DRAFT_CACHE_SUBDIR = "draft_word_images"
    private const val CAMERA_CAPTURE_SUBDIR = "camera_capture"
    private const val LINK_CACHE_SUBDIR = "link_word_images"
    private const val MAX_DIMENSION_PX = 1280
    private const val JPEG_QUALITY = 85
    private const val MAX_MEMORY_CACHE_ENTRIES = 72

    private val memoryCacheLock = Any()
    private val memoryCache = object : LinkedHashMap<String, ImageBitmap>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
            return size > MAX_MEMORY_CACHE_ENTRIES
        }
    }

    private val LEADING_SEQ_PREFIX_REGEX = Regex(
        """^(?:[(（\[]?\d{1,4}[)）\]]?\s*[-_.、:：\s]+\s*)+"""
    )
    private val GROUP_PREFIX_REGEX = Regex(
        """^(?:【([^】]+)】|\[([^\]]+)])\s*[-_.\s]*"""
    )
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")

    fun isSupportedImageFileName(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in IMAGE_EXTENSIONS
    }

    fun getDraftStagingDir(context: Context): File =
        File(context.cacheDir, DRAFT_CACHE_SUBDIR).apply { mkdirs() }

    fun getCameraCaptureTempFile(context: Context): File {
        val dir = File(context.cacheDir, CAMERA_CAPTURE_SUBDIR).apply { mkdirs() }
        return File(dir, "cam_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}.jpg")
    }

    fun getLinkImageCacheDir(context: Context): File =
        File(context.cacheDir, LINK_CACHE_SUBDIR).apply { mkdirs() }

    fun getProjectImagesDir(context: Context, projectId: Long): File =
        File(File(context.getExternalFilesDir(null), "project_$projectId"), IMAGES_SUBDIR)

    /**
     * Resolves a stored or staged `imagePath` token to an existing physical [File], or `null` if blank/missing.
     */
    fun resolveWordImageFile(context: Context, projectId: Long, imagePath: String): File? {
        val clean = imagePath.trim()
        if (clean.isEmpty()) return null
        val file = when {
            clean.startsWith(STAGED_PREFIX) -> {
                val name = clean.removePrefix(STAGED_PREFIX).substringAfterLast('/').substringAfterLast('\\')
                File(getDraftStagingDir(context), name)
            }
            clean.startsWith(LINK_PREFIX) -> {
                val name = clean.removePrefix(LINK_PREFIX).substringAfterLast('/').substringAfterLast('\\')
                File(getLinkImageCacheDir(context), name)
            }
            else -> {
                val safeName = clean.substringAfterLast('/').substringAfterLast('\\')
                if (projectId > 0L) {
                    File(getProjectImagesDir(context, projectId), safeName)
                } else {
                    File(getDraftStagingDir(context), safeName)
                }
            }
        }
        return file.takeIf { it.exists() && it.isFile && it.length() > 0L }
    }

    /**
     * Reads the original human-readable filename from a picker [Uri] (supporting Android `content://`
     * OpenableColumns and Desktop file paths).
     */
    fun getUriDisplayName(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).coerceAtLeast(0)
                    val name = cursor.getString(idx)
                    if (!name.isNullOrBlank()) return name.trim()
                }
            }
        }
        val raw = runCatching { URLDecoder.decode(uri.toString(), "UTF-8") }.getOrDefault(uri.toString())
        return raw.substringAfterLast('/').substringAfterLast('\\').substringBefore('?').trim()
    }

    /**
     * Parses a picture filename (e.g. `001_日头(太阳).jpg`, `【天文】02-月光.png`, `板凳.webp`)
     * into a clean [ParsedWord] ready for matching or creating a wordlist entry.
     */
    fun parseWordFromImageFileName(
        rawFileName: String,
        fallbackIndex: Int = 1,
        wordAndRemarkParser: ((String) -> Pair<String, String>)? = null
    ): ParsedWord {
        val base = rawFileName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .substringBeforeLast('.')
            .trim()

        var working = base
        var groupName = ""

        val grpMatch = GROUP_PREFIX_REGEX.find(working)
        if (grpMatch != null) {
            groupName = (grpMatch.groupValues[1].ifBlank { grpMatch.groupValues[2] }).trim()
            working = working.removeRange(grpMatch.range).trim()
        }

        val strippedSeq = working.replaceFirst(LEADING_SEQ_PREFIX_REGEX, "").trim()
        if (strippedSeq.isNotEmpty()) {
            working = strippedSeq
        }

        // Check group again in case filename was `01_【天文】日头.jpg`
        if (groupName.isEmpty()) {
            val grpAfterSeq = GROUP_PREFIX_REGEX.find(working)
            if (grpAfterSeq != null) {
                groupName = (grpAfterSeq.groupValues[1].ifBlank { grpAfterSeq.groupValues[2] }).trim()
                working = working.removeRange(grpAfterSeq.range).trim()
            }
        }

        // Normalize common underscore separator `日头_太阳` -> `日头 (太阳)` when not purely digits
        if (!working.contains('(') && !working.contains('（') && working.count { it == '_' } == 1) {
            val parts = working.split('_', limit = 2).map { it.trim() }
            if (parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                return ParsedWord(text = parts[0], remark = parts[1], groupName = groupName)
            }
        }

        if (wordAndRemarkParser != null) {
            val (w, r) = wordAndRemarkParser(working)
            if (w.isNotBlank()) {
                return ParsedWord(text = w.trim(), remark = r.trim(), groupName = groupName)
            }
        }

        val finalText = working.ifBlank { S.wordEditor.pictureWordFallback.withArgs("index" to fallbackIndex) }
        return ParsedWord(text = finalText, remark = "", groupName = groupName)
    }

    /**
     * Ingests an image from [uri], normalizes EXIF orientation, downscales longest edge to <= 1280px,
     * flattens alpha onto warm white, writes compressed JPEG into `cacheDir/draft_word_images/`,
     * and returns a `"staged:<fileName>"` token.
     */
    suspend fun stageImageFromUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val rawBytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return@withContext null
        stageImageFromBytes(context, rawBytes)
    }

    /**
     * Ingests an image from a local [file] into `cacheDir/draft_word_images/`, returning `"staged:<fileName>"`.
     */
    suspend fun stageImageFromFile(context: Context, file: File): String? = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.isFile || file.length() <= 0L) return@withContext null
        val rawBytes = runCatching { file.readBytes() }.getOrNull() ?: return@withContext null
        stageImageFromBytes(context, rawBytes)
    }

    fun stageImageFromBytesSync(context: Context, rawBytes: ByteArray): String? {
        if (rawBytes.isEmpty()) return null
        return runCatching {
            val outName = "wimg_${System.currentTimeMillis()}_${Random.nextInt(10000, 99999)}.jpg"
            val outFile = File(getDraftStagingDir(context), outName)
            val written = normalizeAndWriteJpeg(rawBytes, outFile)
            if (written && outFile.exists() && outFile.length() > 0L) {
                "$STAGED_PREFIX$outName"
            } else {
                null
            }
        }.getOrNull()
    }

    suspend fun stageImageFromBytes(context: Context, rawBytes: ByteArray): String? = withContext(Dispatchers.IO) {
        stageImageFromBytesSync(context, rawBytes)
    }

    /**
     * Commits a draft/staged or existing `rawImagePath` into `project_<projectId>/images/<fileName>`
     * and returns the clean `<fileName>` to persist in `Word.imagePath`.
     */
    fun commitImageToProjectSync(context: Context, projectId: Long, rawImagePath: String): String {
        val clean = rawImagePath.trim()
        if (clean.isEmpty() || projectId <= 0L) return ""
        val projImgDir = getProjectImagesDir(context, projectId).apply { mkdirs() }

        return if (clean.startsWith(STAGED_PREFIX)) {
            val fileName = clean.removePrefix(STAGED_PREFIX).substringAfterLast('/').substringAfterLast('\\')
            if (fileName.isBlank()) return ""
            val src = File(getDraftStagingDir(context), fileName)
            val dst = File(projImgDir, fileName)
            if (src.exists() && src.isFile) {
                runCatching {
                    src.copyTo(dst, overwrite = true)
                    src.delete()
                }
            }
            if (dst.exists() && dst.length() > 0L) fileName else ""
        } else {
            val fileName = clean.substringAfterLast('/').substringAfterLast('\\')
            val dst = File(projImgDir, fileName)
            if (dst.exists() && dst.length() > 0L) {
                fileName
            } else {
                // Fallback if copied from staging without prefix
                val stagedCandidate = File(getDraftStagingDir(context), fileName)
                if (stagedCandidate.exists() && stagedCandidate.isFile) {
                    runCatching { stagedCandidate.copyTo(dst, overwrite = true) }
                    if (dst.exists() && dst.length() > 0L) fileName else ""
                } else {
                    clean
                }
            }
        }
    }

    /**
     * Deletes any image files in `project_<projectId>/images/` not referenced by [activeImageNames].
     * Returns `(freedBytes, deletedCount)`.
     */
    fun pruneUnreferencedProjectImages(
        context: Context,
        projectId: Long,
        activeImageNames: Set<String>
    ): Pair<Long, Int> {
        val dir = getProjectImagesDir(context, projectId)
        if (!dir.exists() || !dir.isDirectory) return 0L to 0
        var freed = 0L
        var count = 0
        dir.listFiles()?.forEach { f ->
            if (f.isFile && f.name !in activeImageNames) {
                val len = f.length()
                if (runCatching { f.delete() }.getOrDefault(false)) {
                    evictCachedPath(f.absolutePath)
                    freed += len
                    count++
                }
            }
        }
        return freed to count
    }

    private fun normalizeAndWriteJpeg(rawBytes: ByteArray, destFile: File): Boolean {
        val rotationDeg = readExifRotationDegrees(rawBytes)

        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ByteArrayInputStream(rawBytes).use { BitmapFactory.decodeStream(it, null, boundsOpts) }
        val rawW = boundsOpts.outWidth
        val rawH = boundsOpts.outHeight
        if (rawW <= 0 || rawH <= 0) return false

        var sampleSize = 1
        while ((rawW / sampleSize) > MAX_DIMENSION_PX * 2 || (rawH / sampleSize) > MAX_DIMENSION_PX * 2) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bitmap = ByteArrayInputStream(rawBytes).use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return false

        if (rotationDeg != 0) {
            val matrix = Matrix().apply { postRotate(rotationDeg.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) {
                bitmap.recycle()
                bitmap = rotated
            }
        }

        val longest = max(bitmap.width, bitmap.height)
        if (longest > MAX_DIMENSION_PX) {
            val ratio = MAX_DIMENSION_PX.toFloat() / longest.toFloat()
            val targetW = (bitmap.width * ratio).roundToInt().coerceAtLeast(1)
            val targetH = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            if (scaled !== bitmap) {
                bitmap.recycle()
                bitmap = scaled
            }
        }

        val opaqueBitmap = flattenAlphaOntoWarmWhiteIfNeeded(bitmap)
        if (opaqueBitmap !== bitmap) {
            bitmap.recycle()
            bitmap = opaqueBitmap
        }

        destFile.parentFile?.mkdirs()
        val ok = FileOutputStream(destFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos)
        }
        bitmap.recycle()
        return ok
    }

    private fun flattenAlphaOntoWarmWhiteIfNeeded(src: Bitmap): Bitmap {
        if (!src.hasAlpha()) return src
        val w = src.width.coerceAtLeast(1)
        val h = src.height.coerceAtLeast(1)
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        var anyTranslucent = false
        for (i in pixels.indices) {
            val a = (pixels[i] ushr 24) and 0xFF
            if (a < 252) {
                anyTranslucent = true
                val c = pixels[i]
                val r = (c ushr 16) and 0xFF
                val g = (c ushr 8) and 0xFF
                val b = c and 0xFF
                val invA = 255 - a
                val outR = (r * a + 250 * invA) / 255
                val outG = (g * a + 249 * invA) / 255
                val outB = (b * a + 246 * invA) / 255
                pixels[i] = (0xFF shl 24) or (outR shl 16) or (outG shl 8) or outB
            }
        }
        return if (!anyTranslucent) {
            src
        } else {
            Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        }
    }

    private fun readExifRotationDegrees(rawBytes: ByteArray): Int {
        return runCatching {
            val exif = ExifInterface(ByteArrayInputStream(rawBytes))
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }.getOrDefault(0)
    }

    fun loadImageBitmapSync(file: File?, maxEdgePx: Int = MAX_DIMENSION_PX): ImageBitmap? {
        if (file == null || !file.exists() || !file.isFile || file.length() <= 0L) return null
        val cacheKey = "${file.absolutePath}:${file.lastModified()}:${file.length()}:$maxEdgePx"
        synchronized(memoryCacheLock) {
            memoryCache[cacheKey]?.let { return it }
        }
        val decoded = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / sample > maxEdgePx * 2 || bounds.outHeight / sample > maxEdgePx * 2) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
        }.getOrNull() ?: return null

        synchronized(memoryCacheLock) {
            memoryCache[cacheKey] = decoded
        }
        return decoded
    }

    fun peekCachedBitmap(file: File?, maxEdgePx: Int = MAX_DIMENSION_PX): ImageBitmap? {
        if (file == null || !file.exists() || !file.isFile) return null
        val cacheKey = "${file.absolutePath}:${file.lastModified()}:${file.length()}:$maxEdgePx"
        synchronized(memoryCacheLock) {
            return memoryCache[cacheKey]
        }
    }

    private fun evictCachedPath(absPath: String) {
        synchronized(memoryCacheLock) {
            val iter = memoryCache.entries.iterator()
            while (iter.hasNext()) {
                if (iter.next().key.startsWith("$absPath:")) {
                    iter.remove()
                }
            }
        }
    }
}

/**
 * Smooth, main-thread-safe Composable image loader backed by [WordImageManager]'s LRU memory cache.
 * Returns immediately on cache hit so Pager swiping and LazyColumn scrolling never flicker.
 */
@Composable
fun rememberWordImageBitmap(
    file: File?,
    maxEdgePx: Int = 1280,
    extraKey: Any? = null
): State<ImageBitmap?> {
    val fileKey = if (file != null) "${file.absolutePath}:${file.lastModified()}" else ""
    val initial = WordImageManager.peekCachedBitmap(file, maxEdgePx)
    return produceState(initialValue = initial, key1 = fileKey, key2 = maxEdgePx, key3 = extraKey) {
        if (file == null) {
            value = null
        } else {
            val hit = WordImageManager.peekCachedBitmap(file, maxEdgePx)
            value = hit ?: withContext(Dispatchers.IO) {
                WordImageManager.loadImageBitmapSync(file, maxEdgePx)
            }
        }
    }
}
