@file:Suppress("PackageDirectoryMismatch")

package com.example.utils

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.example.ui.filepicker.DesktopFilePickerController
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.dnd.DropTargetEvent
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

data class PlatformScannedDocument(
    val uri: Uri,
    val displayName: String,
    val relativeFolder: String,
    val rootFolderName: String,
    val sizeBytes: Long
)

/**
 * Desktop JVM implementation of cross-platform directory tree scanning, OS window drag-and-drop,
 * and zero-bloat audio transcoding (pure-JVM RIFF/AIFF + Java Sound + macOS `afconvert` +
 * Windows 10/11 MediaFoundation `IMFSourceReader` + `ffmpeg` fallback) to canonical 16-bit mono WAV.
 */
object SmartImportPlatformBridge {

    fun isDirectoryUri(context: Context, uri: Uri): Boolean {
        val f = uri.toFile() ?: return false
        return f.exists() && f.isDirectory
    }

    fun queryDisplayNameAndSize(context: Context, uri: Uri): Pair<String, Long> {
        val f = uri.toFile()
        if (f != null && f.exists()) {
            return f.name to (if (f.isFile) f.length().coerceAtLeast(0L) else 0L)
        }
        return WordImageManager.getUriDisplayName(context, uri) to 0L
    }

    fun copyUriToTempFile(context: Context, uri: Uri, destFile: File): Boolean {
        val src = uri.toFile() ?: return false
        if (!src.exists() || !src.isFile) return false
        return runCatching {
            destFile.parentFile?.mkdirs()
            src.copyTo(destFile, overwrite = true)
            destFile.exists() && destFile.length() > 0L
        }.getOrDefault(false)
    }

    fun scanDirectoryTree(
        context: Context,
        treeUri: Uri,
        maxFiles: Int = 2500
    ): List<PlatformScannedDocument> {
        val rootDir = treeUri.toFile() ?: return emptyList()
        if (!rootDir.exists() || !rootDir.isDirectory) return emptyList()

        val rootName = rootDir.name.ifBlank { rootDir.absolutePath }
        val results = ArrayList<PlatformScannedDocument>()

        fun walk(dir: File, relPrefix: String, depth: Int) {
            if (depth > 8 || results.size >= maxFiles) return
            val children = runCatching { dir.listFiles() }.getOrNull() ?: return
            val sorted = children.sortedWith(
                compareBy<File>({ !it.isDirectory }, { it.name.lowercase() })
            )
            for (child in sorted) {
                if (results.size >= maxFiles) break
                val name = child.name
                if (name.startsWith(".") ||
                    name.equals("__MACOSX", ignoreCase = true) ||
                    name.equals("\$RECYCLE.BIN", ignoreCase = true) ||
                    name.equals("System Volume Information", ignoreCase = true)
                ) {
                    continue
                }
                if (child.isDirectory) {
                    val nextRel = if (relPrefix.isEmpty()) name else "$relPrefix/$name"
                    walk(child, nextRel, depth + 1)
                } else if (child.isFile && child.length() > 0L) {
                    results.add(
                        PlatformScannedDocument(
                            uri = Uri.fromFile(child),
                            displayName = name,
                            relativeFolder = relPrefix,
                            rootFolderName = rootName,
                            sizeBytes = child.length()
                        )
                    )
                }
            }
        }

        walk(rootDir, "", 0)
        return results
    }

    fun transcodeAudioToMono16Wav(
        context: Context,
        inputFile: File,
        outputWavFile: File
    ): Boolean {
        if (!inputFile.exists() || !inputFile.isFile || inputFile.length() <= 16L) return false

        // 1. Pure-Kotlin RIFF/WAVE & AIFF/AIFC PCM/Float normalizer (instantaneous)
        if (AudioWavNormalizer.tryNormalizeRiffOrAiffToMono16Wav(inputFile, outputWavFile)) {
            return true
        }

        // 2. Java Sound SPI (AudioSystem) if format plugin or uncompressed codec handles it
        if (tryJavaSoundDecode(inputFile, outputWavFile)) {
            return true
        }

        // 3. OS-native zero-bloat decoders (afconvert on macOS, ffmpeg if present, or Windows MediaFoundation)
        val osName = System.getProperty("os.name", "").lowercase()
        outputWavFile.parentFile?.mkdirs()

        if (osName.contains("mac") || osName.contains("darwin")) {
            if (tryMacAfconvert(inputFile, outputWavFile)) return true
            if (tryFfmpegCli(inputFile, outputWavFile)) return true
        } else if (osName.contains("win")) {
            if (tryFfmpegCli(inputFile, outputWavFile)) return true
            if (tryWindowsMediaFoundationTranscode(inputFile, outputWavFile)) return true
        } else {
            if (tryFfmpegCli(inputFile, outputWavFile)) return true
        }

        return false
    }

    private fun tryJavaSoundDecode(inputFile: File, outputWavFile: File): Boolean {
        return runCatching {
            AudioSystem.getAudioInputStream(inputFile).use { srcStream ->
                val baseFormat = srcStream.format
                val sampleRate = if (baseFormat.sampleRate > 0f) baseFormat.sampleRate else 44100f
                val pcmMonoFormat = AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sampleRate,
                    16,
                    1,
                    2,
                    sampleRate,
                    false
                )
                AudioSystem.getAudioInputStream(pcmMonoFormat, srcStream).use { pcmStream ->
                    val baos = ByteArrayOutputStream(256 * 1024)
                    val buf = ByteArray(16384)
                    while (true) {
                        val n = pcmStream.read(buf)
                        if (n < 0) break
                        if (n > 0) baos.write(buf, 0, n)
                    }
                    val raw = baos.toByteArray()
                    if (raw.size < 4) return false
                    val rateInt = sampleRate.toInt().coerceIn(8000, 192000)
                    outputWavFile.parentFile?.mkdirs()
                    val tempWav = File(outputWavFile.parentFile, "${outputWavFile.name}.tmp_js.wav")
                    FileOutputStream(tempWav).use { fos ->
                        fos.write(AudioWavNormalizer.buildCanonicalWavHeader(raw.size, rateInt))
                        fos.write(raw)
                    }
                    val ok = AudioWavNormalizer.tryNormalizeRiffOrAiffToMono16Wav(tempWav, outputWavFile)
                    runCatching { tempWav.delete() }
                    ok
                }
            }
        }.getOrDefault(false)
    }

    private fun tryMacAfconvert(inputFile: File, outputWavFile: File): Boolean {
        val afconvert = File("/usr/bin/afconvert")
        if (!afconvert.canExecute()) return false
        return runCatching {
            val tempWav = File(outputWavFile.parentFile, "${outputWavFile.name}.tmp_af.wav")
            val proc = ProcessBuilder(
                afconvert.absolutePath,
                "-f", "WAVE",
                "-d", "LEI16@44100",
                "-c", "1",
                inputFile.absolutePath,
                tempWav.absolutePath
            ).redirectErrorStream(true).start()
            proc.inputStream.readBytes()
            val code = proc.waitFor()
            val ok = code == 0 && AudioWavNormalizer.tryNormalizeRiffOrAiffToMono16Wav(tempWav, outputWavFile)
            runCatching { tempWav.delete() }
            ok
        }.getOrDefault(false)
    }

    private fun tryFfmpegCli(inputFile: File, outputWavFile: File): Boolean {
        return runCatching {
            val tempWav = File(outputWavFile.parentFile, "${outputWavFile.name}.tmp_ff.wav")
            val proc = ProcessBuilder(
                "ffmpeg",
                "-y",
                "-v", "error",
                "-i", inputFile.absolutePath,
                "-vn",
                "-ac", "1",
                "-ar", "44100",
                "-acodec", "pcm_s16le",
                tempWav.absolutePath
            ).redirectErrorStream(true).start()
            proc.inputStream.readBytes()
            val code = proc.waitFor()
            val ok = code == 0 && AudioWavNormalizer.tryNormalizeRiffOrAiffToMono16Wav(tempWav, outputWavFile)
            runCatching { tempWav.delete() }
            ok
        }.getOrDefault(false)
    }

    /**
     * Uses built-in Windows 10/11 Media Foundation (`mfplat.dll` + `mfreadwrite.dll`) via a tiny
     * in-memory C# `IMFSourceReader` P/Invoke helper to decode `.mp3`, `.m4a`, `.aac`, `.flac`, `.wma`
     * into PCM `.wav` with zero external libraries or installer bloat.
     */
    private fun tryWindowsMediaFoundationTranscode(inputFile: File, outputWavFile: File): Boolean {
        return runCatching {
            val tempWav = File(outputWavFile.parentFile, "${outputWavFile.name}.tmp_mf.wav")
            val inEsc = inputFile.absolutePath.replace("'", "''")
            val outEsc = tempWav.absolutePath.replace("'", "''")

            val psScript = """
                ${'$'}ErrorActionPreference = 'Stop'
                Add-Type -TypeDefinition @"
                using System;
                using System.IO;
                using System.Runtime.InteropServices;

                public static class PhonAcqMfDecoder {
                    private const int MF_VERSION = 0x00020070;
                    private const uint MF_SOURCE_READER_FIRST_AUDIO_STREAM = 0xFFFFFFFC;
                    private static readonly Guid MF_MT_MAJOR_TYPE = new Guid("48eba18e-f8c9-4687-bf11-0a74c9f96a8f");
                    private static readonly Guid MF_MT_SUBTYPE = new Guid("f7e34c9a-42e8-4714-b74b-cb29d72c35e5");
                    private static readonly Guid MF_MT_AUDIO_NUM_CHANNELS = new Guid("37e48bf5-645e-4c5b-89de-ada9e29b696a");
                    private static readonly Guid MF_MT_AUDIO_SAMPLES_PER_SECOND = new Guid("5faeeae7-0290-4c31-9e8a-c534f68d9dba");
                    private static readonly Guid MF_MT_AUDIO_BITS_PER_SAMPLE = new Guid("f2deb57f-40fa-4764-aa33-ed4f2d1ff669");
                    private static readonly Guid MFMediaType_Audio = new Guid("73647561-0000-0010-8000-00aa00389b71");
                    private static readonly Guid MFAudioFormat_PCM = new Guid("00000001-0000-0010-8000-00aa00389b71");

                    [DllImport("mfplat.dll", ExactSpelling = true)]
                    private static extern int MFStartup(int version, int flags);
                    [DllImport("mfplat.dll", ExactSpelling = true)]
                    private static extern int MFShutdown();
                    [DllImport("mfplat.dll", ExactSpelling = true)]
                    private static extern int MFCreateMediaType(out IntPtr ppMFType);
                    [DllImport("mfreadwrite.dll", CharSet = CharSet.Unicode, ExactSpelling = true)]
                    private static extern int MFCreateSourceReaderFromURL(string pwszURL, IntPtr pAttributes, out IntPtr ppSourceReader);

                    [UnmanagedFunctionPointer(CallingConvention.StdCall)]
                    private delegate int SetGuidDelegate(IntPtr pThis, ref Guid key, ref Guid val);
                    [UnmanagedFunctionPointer(CallingConvention.StdCall)]
                    private delegate int SetUINT32Delegate(IntPtr pThis, ref Guid key, uint val);
                    [UnmanagedFunctionPointer(CallingConvention.StdCall)]
                    private delegate int GetUINT32Delegate(IntPtr pThis, ref Guid key, out uint val);
                    [UnmanagedFunctionPointer(CallingConvention.StdCall)]
                    private delegate int GetCurrentMediaTypeDelegate(IntPtr pReader, uint streamIdx, out IntPtr ppType);
                    [UnmanagedFunctionPointer(CallingConvention.StdCall)]
                    private delegate int SetCurrentMediaTypeDelegate(IntPtr pReader, uint streamIdx, IntPtr reserved, IntPtr pType);
                    [UnmanagedFunctionPointer(CallingConvention.StdCall)]
                    private delegate int ReadSampleDelegate(IntPtr pReader, uint streamIdx, uint ctrlFlags, out uint actualIdx, out uint streamFlags, out long timestamp, out IntPtr ppSample);
                    [UnmanagedFunctionPointer(CallingConvention.StdCall)]
                    private delegate int ConvertToContiguousBufferDelegate(IntPtr pSample, out IntPtr ppBuffer);
                    [UnmanagedFunctionPointer(CallingConvention.StdCall)]
                    private delegate int LockBufferDelegate(IntPtr pBuffer, out IntPtr ppbBuffer, out uint maxLen, out uint curLen);
                    [UnmanagedFunctionPointer(CallingConvention.StdCall)]
                    private delegate int UnlockBufferDelegate(IntPtr pBuffer);

                    private static T Vtbl<T>(IntPtr comObj, int slot) where T : class {
                        IntPtr vtbl = Marshal.ReadIntPtr(comObj);
                        IntPtr fn = Marshal.ReadIntPtr(vtbl, slot * IntPtr.Size);
                        return Marshal.GetDelegateForFunctionPointer(fn, typeof(T)) as T;
                    }

                    public static void ConvertToWav(string inPath, string outPath) {
                        int hr = MFStartup(MF_VERSION, 0);
                        if (hr < 0) Marshal.ThrowExceptionForHR(hr);
                        IntPtr reader = IntPtr.Zero;
                        try {
                            hr = MFCreateSourceReaderFromURL(inPath, IntPtr.Zero, out reader);
                            if (hr < 0) Marshal.ThrowExceptionForHR(hr);

                            IntPtr partialType;
                            Marshal.ThrowExceptionForHR(MFCreateMediaType(out partialType));
                            try {
                                Guid kMajor = MF_MT_MAJOR_TYPE, vAudio = MFMediaType_Audio;
                                Guid kSub = MF_MT_SUBTYPE, vPcm = MFAudioFormat_PCM;
                                Guid kBits = MF_MT_AUDIO_BITS_PER_SAMPLE;
                                Marshal.ThrowExceptionForHR(Vtbl<SetGuidDelegate>(partialType, 24)(partialType, ref kMajor, ref vAudio));
                                Marshal.ThrowExceptionForHR(Vtbl<SetGuidDelegate>(partialType, 24)(partialType, ref kSub, ref vPcm));
                                Vtbl<SetUINT32Delegate>(partialType, 21)(partialType, ref kBits, 16);
                                Marshal.ThrowExceptionForHR(Vtbl<SetCurrentMediaTypeDelegate>(reader, 7)(reader, MF_SOURCE_READER_FIRST_AUDIO_STREAM, IntPtr.Zero, partialType));
                            } finally {
                                Marshal.Release(partialType);
                            }

                            IntPtr actualType;
                            Marshal.ThrowExceptionForHR(Vtbl<GetCurrentMediaTypeDelegate>(reader, 6)(reader, MF_SOURCE_READER_FIRST_AUDIO_STREAM, out actualType));
                            uint channels = 1, sampleRate = 44100, bits = 16;
                            try {
                                var getU32 = Vtbl<GetUINT32Delegate>(actualType, 11);
                                Guid kc = MF_MT_AUDIO_NUM_CHANNELS, kr = MF_MT_AUDIO_SAMPLES_PER_SECOND, kb = MF_MT_AUDIO_BITS_PER_SAMPLE;
                                getU32(actualType, ref kc, out channels);
                                getU32(actualType, ref kr, out sampleRate);
                                getU32(actualType, ref kb, out bits);
                            } finally {
                                Marshal.Release(actualType);
                            }

                            var readSample = Vtbl<ReadSampleDelegate>(reader, 9);
                            using (var ms = new MemoryStream(512 * 1024)) {
                                while (true) {
                                    uint actualIdx, flags;
                                    long ts;
                                    IntPtr sample;
                                    hr = readSample(reader, MF_SOURCE_READER_FIRST_AUDIO_STREAM, 0, out actualIdx, out flags, out ts, out sample);
                                    if (hr < 0) Marshal.ThrowExceptionForHR(hr);
                                    if ((flags & 0x00000002) != 0) {
                                        if (sample != IntPtr.Zero) Marshal.Release(sample);
                                        break;
                                    }
                                    if (sample != IntPtr.Zero) {
                                        try {
                                            IntPtr mediaBuf;
                                            if (Vtbl<ConvertToContiguousBufferDelegate>(sample, 42)(sample, out mediaBuf) >= 0 && mediaBuf != IntPtr.Zero) {
                                                try {
                                                    IntPtr ptr; uint maxL, curL;
                                                    if (Vtbl<LockBufferDelegate>(mediaBuf, 3)(mediaBuf, out ptr, out maxL, out curL) >= 0) {
                                                        try {
                                                            if (curL > 0) {
                                                                byte[] tmp = new byte[curL];
                                                                Marshal.Copy(ptr, tmp, 0, (int)curL);
                                                                ms.Write(tmp, 0, tmp.Length);
                                                            }
                                                        } finally {
                                                            Vtbl<UnlockBufferDelegate>(mediaBuf, 4)(mediaBuf);
                                                        }
                                                    }
                                                } finally {
                                                    Marshal.Release(mediaBuf);
                                                }
                                            }
                                        } finally {
                                            Marshal.Release(sample);
                                        }
                                    }
                                }
                                byte[] pcm = ms.ToArray();
                                using (var fs = new FileStream(outPath, FileMode.Create, FileAccess.Write))
                                using (var bw = new BinaryWriter(fs)) {
                                    ushort ch = (ushort)Math.Max(1u, channels);
                                    ushort bps = (ushort)Math.Max(8u, bits);
                                    ushort align = (ushort)(ch * (bps / 8));
                                    uint byteRate = sampleRate * align;
                                    bw.Write(System.Text.Encoding.ASCII.GetBytes("RIFF"));
                                    bw.Write((int)(36 + pcm.Length));
                                    bw.Write(System.Text.Encoding.ASCII.GetBytes("WAVEfmt "));
                                    bw.Write(16);
                                    bw.Write((ushort)1);
                                    bw.Write(ch);
                                    bw.Write((int)sampleRate);
                                    bw.Write((int)byteRate);
                                    bw.Write(align);
                                    bw.Write(bps);
                                    bw.Write(System.Text.Encoding.ASCII.GetBytes("data"));
                                    bw.Write(pcm.Length);
                                    bw.Write(pcm);
                                }
                            }
                        } finally {
                            if (reader != IntPtr.Zero) Marshal.Release(reader);
                            MFShutdown();
                        }
                    }
                }
"@
                [PhonAcqMfDecoder]::ConvertToWav('$inEsc', '$outEsc')
            """.trimIndent()

            val encoded = Base64.getEncoder().encodeToString(psScript.toByteArray(StandardCharsets.UTF_16LE))
            val proc = ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy", "Bypass",
                "-WindowStyle", "Hidden",
                "-EncodedCommand", encoded
            ).redirectErrorStream(true).start()
            proc.inputStream.readBytes()
            val code = proc.waitFor()
            val ok = code == 0 && AudioWavNormalizer.tryNormalizeRiffOrAiffToMono16Wav(tempWav, outputWavFile)
            runCatching { tempWav.delete() }
            ok
        }.getOrDefault(false)
    }
}

/**
 * Attaches an OS-level AWT `DropTarget` to the active `ComposeWindow` when [enabled] is true,
 * supporting stacked drop targets (restoring `previousDropTarget` cleanly on dispose).
 */
@Composable
fun SmartImportDesktopDropEffect(
    enabled: Boolean,
    onHoverChanged: (Boolean) -> Unit,
    onDroppedUris: (List<Uri>) -> Unit
) {
    val currentOnHover by rememberUpdatedState(onHoverChanged)
    val currentOnDropped by rememberUpdatedState(onDroppedUris)

    DisposableEffect(enabled) {
        if (!enabled) {
            return@DisposableEffect onDispose { }
        }
        val win = DesktopFilePickerController.mainWindow
        val previousDropTarget = win?.dropTarget
        if (win != null) {
            win.dropTarget = DropTarget(
                win,
                DnDConstants.ACTION_COPY_OR_MOVE,
                object : DropTargetAdapter() {
                    override fun dragEnter(dtde: DropTargetDragEvent) {
                        if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                            dtde.acceptDrag(DnDConstants.ACTION_COPY)
                            currentOnHover(true)
                        } else {
                            dtde.rejectDrag()
                        }
                    }

                    override fun dragOver(dtde: DropTargetDragEvent) {
                        if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                            dtde.acceptDrag(DnDConstants.ACTION_COPY)
                            currentOnHover(true)
                        }
                    }

                    override fun dragExit(dte: DropTargetEvent) {
                        currentOnHover(false)
                    }

                    override fun drop(dtde: DropTargetDropEvent) {
                        currentOnHover(false)
                        try {
                            if (!dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                                dtde.rejectDrop()
                                return
                            }
                            dtde.acceptDrop(DnDConstants.ACTION_COPY)
                            @Suppress("UNCHECKED_CAST")
                            val files = (dtde.transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>)
                                .orEmpty()
                            dtde.dropComplete(true)
                            if (files.isNotEmpty()) {
                                currentOnDropped(files.map { Uri.fromFile(it) })
                            }
                        } catch (_: Exception) {
                            runCatching { dtde.dropComplete(false) }
                        }
                    }
                },
                true
            )
        }
        onDispose {
            currentOnHover(false)
            if (win != null) {
                win.dropTarget = previousDropTarget
            }
        }
    }
}
