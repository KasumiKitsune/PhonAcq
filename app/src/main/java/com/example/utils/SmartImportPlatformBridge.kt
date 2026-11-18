package com.example.utils

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.compose.runtime.Composable
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

data class PlatformScannedDocument(
    val uri: Uri,
    val displayName: String,
    val relativeFolder: String,
    val rootFolderName: String,
    val sizeBytes: Long
)

/**
 * Android implementation of cross-platform directory tree scanning (zero-permission SAF
 * `DocumentsContract` traversal) and hardware/software `MediaExtractor` + `MediaCodec`
 * audio transcoding to canonical 16-bit mono PCM WAV.
 */
object SmartImportPlatformBridge {

    fun isDirectoryUri(context: Context, uri: Uri): Boolean {
        return runCatching {
            DocumentsContract.isTreeUri(uri)
        }.getOrDefault(false)
    }

    fun queryDisplayNameAndSize(context: Context, uri: Uri): Pair<String, Long> {
        var name = ""
        var size = -1L
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) name = cursor.getString(nameIdx).orEmpty().trim()
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        size = cursor.getLong(sizeIdx)
                    }
                }
            }
        }
        if (name.isBlank()) {
            name = WordImageManager.getUriDisplayName(context, uri)
        }
        return name to size.coerceAtLeast(0L)
    }

    fun copyUriToTempFile(context: Context, uri: Uri, destFile: File): Boolean {
        return runCatching {
            destFile.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output, bufferSize = 65536)
                }
                true
            } ?: false
        }.getOrDefault(false)
    }

    /**
     * Recursively enumerates all files inside an Android SAF `OpenDocumentTree` URI without
     * requesting any storage permissions.
     */
    fun scanDirectoryTree(
        context: Context,
        treeUri: Uri,
        maxFiles: Int = 2500
    ): List<PlatformScannedDocument> {
        val results = ArrayList<PlatformScannedDocument>()
        runCatching {
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            val rootDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
            val rootFolderName = queryDocumentName(context, rootDocUri)
                .ifBlank { rootDocId.substringAfterLast(':').substringAfterLast('/') }
                .trim()

            fun walk(parentDocId: String, relativePrefix: String, depth: Int) {
                if (depth > 8 || results.size >= maxFiles) return
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE
                )
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

                    while (cursor.moveToNext() && results.size < maxFiles) {
                        val childId = if (idCol >= 0) cursor.getString(idCol) else null
                        val childName = (if (nameCol >= 0) cursor.getString(nameCol) else null)?.trim().orEmpty()
                        val mime = if (mimeCol >= 0) cursor.getString(mimeCol).orEmpty() else ""
                        val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L

                        if (childId.isNullOrBlank() || childName.isEmpty()) continue
                        if (childName.startsWith(".") || childName.equals("__MACOSX", ignoreCase = true)) continue

                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            val nextRel = if (relativePrefix.isEmpty()) childName else "$relativePrefix/$childName"
                            walk(childId, nextRel, depth + 1)
                        } else {
                            val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                            results.add(
                                PlatformScannedDocument(
                                    uri = childUri,
                                    displayName = childName,
                                    relativeFolder = relativePrefix,
                                    rootFolderName = rootFolderName,
                                    sizeBytes = size.coerceAtLeast(0L)
                                )
                            )
                        }
                    }
                }
            }

            walk(rootDocId, "", 0)
        }
        return results
    }

    private fun queryDocumentName(context: Context, docUri: Uri): String {
        return runCatching {
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { c ->
                if (c.moveToFirst()) c.getString(0).orEmpty() else ""
            }.orEmpty()
        }.getOrDefault("")
    }

    /**
     * Normalizes or transcodes any supported audio file (`.wav`, `.mp3`, `.m4a`, `.aac`, `.flac`, `.ogg`, `.aiff`)
     * into a canonical 44-byte 16-bit mono PCM `.wav` file on Android.
     */
    fun transcodeAudioToMono16Wav(
        context: Context,
        inputFile: File,
        outputWavFile: File
    ): Boolean {
        if (!inputFile.exists() || inputFile.length() <= 16L) return false

        // 1. Try pure-Kotlin RIFF/WAVE or AIFF/AIFC PCM/Float normalizer first (instantaneous)
        if (AudioWavNormalizer.tryNormalizeRiffOrAiffToMono16Wav(inputFile, outputWavFile)) {
            return true
        }

        // 2. Decode compressed formats (MP3, M4A/AAC, FLAC, OGG/Opus, AMR) via Android MediaExtractor + MediaCodec
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        return try {
            extractor = MediaExtractor()
            extractor.setDataSource(inputFile.absolutePath)

            var audioTrackIndex = -1
            var trackFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    trackFormat = fmt
                    break
                }
            }
            if (audioTrackIndex < 0 || trackFormat == null) return false

            extractor.selectTrack(audioTrackIndex)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: return false
            var sampleRate = if (trackFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            var channelCount = if (trackFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            } else 1
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(trackFormat, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val monoPcmBytes = ByteArrayOutputStream(256 * 1024)
            var inputDone = false
            var outputDone = false
            val maxMonoBytes = 48000 * 2 * 60 * 25 // ~25 min safety cap

            while (!outputDone && monoPcmBytes.size() < maxMonoBytes) {
                if (!inputDone) {
                    val inBufIdx = codec.dequeueInputBuffer(10_000L)
                    if (inBufIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inBufIdx)
                        if (inBuf != null) {
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inBufIdx,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                val pts = extractor.sampleTime.coerceAtLeast(0L)
                                codec.queueInputBuffer(inBufIdx, 0, sampleSize, pts, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFmt = codec.outputFormat
                        if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8000)
                        }
                        if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channelCount = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                        }
                        if (outFmt.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            pcmEncoding = outFmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                    }
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            appendDownmixedMono16Le(outBuf, bufferInfo.size, channelCount, pcmEncoding, monoPcmBytes)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            val rawMonoLe = monoPcmBytes.toByteArray()
            if (rawMonoLe.size < 4) return false

            if (sampleRate in 16000..48000) {
                outputWavFile.parentFile?.mkdirs()
                val header = AudioWavNormalizer.buildCanonicalWavHeader(rawMonoLe.size, sampleRate)
                FileOutputStream(outputWavFile).use { fos ->
                    fos.write(header)
                    fos.write(rawMonoLe)
                }
                outputWavFile.exists() && outputWavFile.length() > 44L
            } else {
                val sampleCount = rawMonoLe.size / 2
                val floats = FloatArray(sampleCount)
                val bb = ByteBuffer.wrap(rawMonoLe).order(ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until sampleCount) {
                    floats[i] = bb.short.toFloat() / 32768f
                }
                val (resampled, finalRate) = AudioWavNormalizer.resampleIfOutOfBounds(floats, sampleRate)
                AudioWavNormalizer.writeMono16BitWavFromFloats(outputWavFile, resampled, finalRate)
            }
        } catch (_: Exception) {
            false
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor?.release() }
        }
    }

    private fun appendDownmixedMono16Le(
        buf: ByteBuffer,
        sizeBytes: Int,
        channels: Int,
        pcmEncoding: Int,
        out: ByteArrayOutputStream
    ) {
        val raw = ByteArray(sizeBytes)
        buf.get(raw)
        if (channels == 1 && pcmEncoding == AudioFormat.ENCODING_PCM_16BIT) {
            out.write(raw)
            return
        }
        val bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        val chCount = channels.coerceAtLeast(1)
        if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
            val frameSize = chCount * 4
            val frames = sizeBytes / frameSize
            val monoBytes = ByteArray(frames * 2)
            var wp = 0
            for (f in 0 until frames) {
                var sum = 0f
                for (c in 0 until chCount) {
                    val v = bb.getFloat((f * chCount + c) * 4)
                    if (v.isFinite()) sum += v
                }
                val s16 = ((sum / chCount).coerceIn(-1f, 1f) * 32767f).roundToInt().coerceIn(-32768, 32767)
                monoBytes[wp++] = (s16 and 0xFF).toByte()
                monoBytes[wp++] = ((s16 ushr 8) and 0xFF).toByte()
            }
            out.write(monoBytes, 0, wp)
        } else {
            val frameSize = chCount * 2
            val frames = sizeBytes / frameSize
            val monoBytes = ByteArray(frames * 2)
            var wp = 0
            for (f in 0 until frames) {
                var sum = 0
                for (c in 0 until chCount) {
                    sum += bb.getShort((f * chCount + c) * 2).toInt()
                }
                val s16 = (sum / chCount).coerceIn(-32768, 32767)
                monoBytes[wp++] = (s16 and 0xFF).toByte()
                monoBytes[wp++] = ((s16 ushr 8) and 0xFF).toByte()
            }
            out.write(monoBytes, 0, wp)
        }
    }
}

/**
 * On Android, external OS window drag-and-drop is a no-op (users pick files/folders via SAF buttons).
 */
@Composable
fun SmartImportDesktopDropEffect(
    enabled: Boolean,
    onHoverChanged: (Boolean) -> Unit,
    onDroppedUris: (List<Uri>) -> Unit
) {
    // No-op on Android
}
