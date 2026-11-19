package com.example.audio

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Cross-platform (Android + Windows/macOS Desktop) project-scoped binary disk cache for
 * [AcousticAnalyzer.AnalysisResult] and [AcousticAnalyzer.QuickPitchSummary].
 *
 * Stored as a hidden sidecar file inside each project directory (`.phonacq_acoustic_cache_v1.bin`)
 * so it never interferes with `.wav` scanning, Room DB schema migrations, or project exports,
 * and is automatically removed when a project folder is deleted.
 *
 * Every entry key encodes filename, byte length, lastModified timestamp, speaker gender,
 * segment count/labels, and pitch calibration bucket — guaranteeing instant invalidation
 * whenever a word is re-recorded or pitch calibration changes.
 */
object AcousticFeatureDiskCache {

    private const val MAGIC = 0x50414348 // "PACH"
    private const val SCHEMA_VERSION = 1
    private const val CACHE_FILE_NAME = ".phonacq_acoustic_cache_v1.bin"
    private const val MAX_ANALYSIS_ENTRIES_PER_PROJECT = 5000
    private const val MAX_QUICK_PITCH_ENTRIES_PER_PROJECT = 5000
    private const val AUTO_FLUSH_DIRTY_THRESHOLD = 24

    private class ProjectCacheStore(val projDir: File) {
        val analysisMap = ConcurrentHashMap<String, AcousticAnalyzer.AnalysisResult>()
        val quickPitchMap = ConcurrentHashMap<String, AcousticAnalyzer.QuickPitchSummary>()
        val loaded = AtomicBoolean(false)
        val dirtyCount = java.util.concurrent.atomic.AtomicInteger(0)
        val ioLock = Any()
    }

    private val storesByDir = ConcurrentHashMap<String, ProjectCacheStore>()

    fun buildAnalysisKey(
        file: File,
        normGender: String,
        segmentCount: Int,
        pitchMin: Int,
        pitchMax: Int,
        medianDurMs: Int,
        labelSig: String
    ): String {
        return "${file.name}|${file.length()}|${file.lastModified()}|$normGender|$segmentCount|$pitchMin|$pitchMax|$medianDurMs|$labelSig"
    }

    fun buildQuickPitchKey(file: File, normGender: String): String {
        return "${file.name}|${file.length()}|${file.lastModified()}|$normGender"
    }

    fun getAnalysis(file: File, key: String): AcousticAnalyzer.AnalysisResult? {
        val dir = file.parentFile ?: return null
        val store = getLoadedStore(dir) ?: return null
        return store.analysisMap[key]
    }

    fun putAnalysis(file: File, key: String, result: AcousticAnalyzer.AnalysisResult, deferFlush: Boolean = false) {
        val dir = file.parentFile ?: return
        val store = getLoadedStore(dir) ?: return
        if (store.analysisMap.size >= MAX_ANALYSIS_ENTRIES_PER_PROJECT && !store.analysisMap.containsKey(key)) {
            val evictKey = store.analysisMap.keys().takeIf { it.hasMoreElements() }?.nextElement()
            if (evictKey != null) store.analysisMap.remove(evictKey)
        }
        store.analysisMap[key] = result
        val dirty = store.dirtyCount.incrementAndGet()
        if (!deferFlush && dirty >= AUTO_FLUSH_DIRTY_THRESHOLD) {
            flushProject(dir)
        }
    }

    internal fun getQuickPitch(file: File, key: String): AcousticAnalyzer.QuickPitchSummary? {
        val dir = file.parentFile ?: return null
        val store = getLoadedStore(dir) ?: return null
        return store.quickPitchMap[key]
    }

    internal fun putQuickPitch(
        file: File,
        key: String,
        summary: AcousticAnalyzer.QuickPitchSummary,
        deferFlush: Boolean = false
    ) {
        val dir = file.parentFile ?: return
        val store = getLoadedStore(dir) ?: return
        if (store.quickPitchMap.size >= MAX_QUICK_PITCH_ENTRIES_PER_PROJECT && !store.quickPitchMap.containsKey(key)) {
            val evictKey = store.quickPitchMap.keys().takeIf { it.hasMoreElements() }?.nextElement()
            if (evictKey != null) store.quickPitchMap.remove(evictKey)
        }
        store.quickPitchMap[key] = summary
        val dirty = store.dirtyCount.incrementAndGet()
        if (!deferFlush && dirty >= AUTO_FLUSH_DIRTY_THRESHOLD) {
            flushProject(dir)
        }
    }

    /**
     * Atomically flushes any pending entries for [projDir] to disk (write temp -> rename).
     */
    fun flushProject(projDir: File?) {
        if (projDir == null || !projDir.exists() || !projDir.isDirectory) return
        val store = storesByDir[projDir.absolutePath] ?: return
        if (store.dirtyCount.get() <= 0) return

        synchronized(store.ioLock) {
            if (store.dirtyCount.get() <= 0) return
            val targetFile = File(projDir, CACHE_FILE_NAME)
            val tmpFile = File(projDir, "$CACHE_FILE_NAME.tmp")
            try {
                DataOutputStream(BufferedOutputStream(FileOutputStream(tmpFile), 32768)).use { out ->
                    out.writeInt(MAGIC)
                    out.writeInt(SCHEMA_VERSION)

                    val analysisSnapshot = store.analysisMap.entries.toList()
                    out.writeInt(analysisSnapshot.size)
                    for ((k, v) in analysisSnapshot) {
                        out.writeUTF(k)
                        writeAnalysisResult(out, v)
                    }

                    val qpSnapshot = store.quickPitchMap.entries.toList()
                    out.writeInt(qpSnapshot.size)
                    for ((k, v) in qpSnapshot) {
                        out.writeUTF(k)
                        writeQuickPitchSummary(out, v)
                    }
                }
                if (targetFile.exists() && !targetFile.delete()) {
                    // Fallback overwrite if Windows holds a brief handle
                    tmpFile.copyTo(targetFile, overwrite = true)
                    tmpFile.delete()
                } else if (!tmpFile.renameTo(targetFile)) {
                    tmpFile.copyTo(targetFile, overwrite = true)
                    tmpFile.delete()
                }
                store.dirtyCount.set(0)
            } catch (_: Exception) {
                try {
                    tmpFile.delete()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun clearMemoryIndex() {
        storesByDir.clear()
    }

    fun deleteProjectCache(projDir: File?) {
        if (projDir == null) return
        storesByDir.remove(projDir.absolutePath)
        try {
            File(projDir, CACHE_FILE_NAME).delete()
            File(projDir, "$CACHE_FILE_NAME.tmp").delete()
        } catch (_: Exception) {
        }
    }

    private fun getLoadedStore(projDir: File): ProjectCacheStore? {
        if (!projDir.exists() || !projDir.isDirectory) return null
        val store = storesByDir.computeIfAbsent(projDir.absolutePath) { ProjectCacheStore(projDir) }
        if (store.loaded.get()) return store
        synchronized(store.ioLock) {
            if (store.loaded.get()) return store
            loadFromDiskLocked(store)
            store.loaded.set(true)
        }
        return store
    }

    private fun loadFromDiskLocked(store: ProjectCacheStore) {
        val file = File(store.projDir, CACHE_FILE_NAME)
        if (!file.exists() || file.length() < 12L) return
        try {
            DataInputStream(BufferedInputStream(FileInputStream(file), 32768)).use { inp ->
                val magic = inp.readInt()
                val version = inp.readInt()
                if (magic != MAGIC || version != SCHEMA_VERSION) {
                    file.delete()
                    return
                }
                val analysisCount = inp.readInt().coerceIn(0, MAX_ANALYSIS_ENTRIES_PER_PROJECT)
                for (i in 0 until analysisCount) {
                    val key = inp.readUTF()
                    val res = readAnalysisResult(inp)
                    store.analysisMap[key] = res
                }
                val qpCount = inp.readInt().coerceIn(0, MAX_QUICK_PITCH_ENTRIES_PER_PROJECT)
                for (i in 0 until qpCount) {
                    val key = inp.readUTF()
                    val qp = readQuickPitchSummary(inp)
                    store.quickPitchMap[key] = qp
                }
            }
        } catch (_: Exception) {
            // Corrupt or truncated cache: silently drop and recompute on demand
            store.analysisMap.clear()
            store.quickPitchMap.clear()
            try {
                file.delete()
            } catch (_: Exception) {
            }
        }
    }

    private fun writeFloatList(out: DataOutputStream, list: List<Float>) {
        out.writeInt(list.size)
        for (v in list) out.writeFloat(v)
    }

    private fun readFloatList(inp: DataInputStream): List<Float> {
        val n = inp.readInt().coerceIn(0, 65536)
        return List(n) { inp.readFloat() }
    }

    private fun writeIntList(out: DataOutputStream, list: List<Int>) {
        out.writeInt(list.size)
        for (v in list) out.writeInt(v)
    }

    private fun readIntList(inp: DataInputStream): List<Int> {
        val n = inp.readInt().coerceIn(0, 16384)
        return List(n) { inp.readInt() }
    }

    private fun writeBoolList(out: DataOutputStream, list: List<Boolean>) {
        out.writeInt(list.size)
        for (v in list) out.writeBoolean(v)
    }

    private fun readBoolList(inp: DataInputStream): List<Boolean> {
        val n = inp.readInt().coerceIn(0, 4096)
        return List(n) { inp.readBoolean() }
    }

    private fun writeStringList(out: DataOutputStream, list: List<String>) {
        out.writeInt(list.size)
        for (v in list) out.writeUTF(v)
    }

    private fun readStringList(inp: DataInputStream): List<String> {
        val n = inp.readInt().coerceIn(0, 4096)
        return List(n) { inp.readUTF() }
    }

    private fun writeQuickPitchSummary(out: DataOutputStream, summary: AcousticAnalyzer.QuickPitchSummary) {
        out.writeBoolean(summary.isValidVoiced)
        out.writeInt(summary.perSyllableVoicedMs)
        out.writeInt(summary.interiorCorePitches.size)
        for (hz in summary.interiorCorePitches) {
            out.writeFloat(hz)
        }
    }

    private fun readQuickPitchSummary(inp: DataInputStream): AcousticAnalyzer.QuickPitchSummary {
        val isValid = inp.readBoolean()
        val durMs = inp.readInt()
        val n = inp.readInt().coerceIn(0, 65536)
        val arr = FloatArray(n) { inp.readFloat() }
        return AcousticAnalyzer.QuickPitchSummary(isValid, durMs, arr)
    }

    private fun writeAnalysisResult(out: DataOutputStream, r: AcousticAnalyzer.AnalysisResult) {
        out.writeBoolean(r.isValidVoiced)
        out.writeFloat(r.intensityDbfs)
        out.writeFloat(r.peakDbfs)
        out.writeFloat(r.snrDb)
        out.writeFloat(r.jitterPercent)
        out.writeFloat(r.shimmerPercent)
        out.writeFloat(r.pitchMean)
        out.writeFloat(r.pitchMin)
        out.writeFloat(r.pitchMax)
        out.writeUTF(r.pitchRange)
        out.writeBoolean(r.isPitchCalibrated)
        out.writeUTF(r.chaoToneNumbers)
        out.writeUTF(r.toneCode)
        out.writeUTF(r.toneDesc)
        out.writeUTF(r.toneTrajectory)
        writeFloatList(out, r.contourPoints)
        writeFloatList(out, r.rawVoicedPitches)
        writeIntList(out, r.speakerF1)
        writeIntList(out, r.speakerF2)
        out.writeInt(r.segmentCount)
        out.writeInt(r.segments.size)
        for (seg in r.segments) {
            out.writeDouble(seg.startSec)
            out.writeDouble(seg.endSec)
            out.writeUTF(seg.label)
        }
        out.writeInt(r.creakyBreaks)
        out.writeInt(r.voicedDurationMs)
        out.writeInt(r.totalDurationMs)
        writeStringList(out, r.syllableToneNumbers)
        writeIntList(out, r.speakerF3)
        out.writeInt(r.formantTrajectories.size)
        for (traj in r.formantTrajectories) {
            out.writeInt(traj.syllableIndex)
            out.writeUTF(traj.syllableLabel)
            out.writeInt(traj.vowelType.ordinal)
            out.writeInt(traj.anchors.size)
            for (a in traj.anchors) {
                out.writeFloat(a.normPos)
                out.writeFloat(a.timeSec)
                out.writeInt(a.f1Hz)
                out.writeInt(a.f2Hz)
                out.writeInt(a.f3Hz)
                out.writeFloat(a.confidence)
            }
            out.writeInt(traj.onsetF1)
            out.writeInt(traj.onsetF2)
            out.writeInt(traj.nucleusF1)
            out.writeInt(traj.nucleusF2)
            out.writeInt(traj.nucleusF3)
            out.writeInt(traj.targetF1)
            out.writeInt(traj.targetF2)
            out.writeFloat(traj.barkGlideSpan)
            out.writeFloat(traj.trajectoryAngleDeg)
            out.writeUTF(traj.trajectorySummary)
        }
        out.writeBoolean(r.isCheckedTone)
        out.writeFloat(r.checkedToneRatio)
        writeBoolList(out, r.syllableCheckedFlags)
        writeBoolList(out, r.syllableNeutralFlags)
        writeIntList(out, r.syllableVoicedMs)
    }

    private fun readAnalysisResult(inp: DataInputStream): AcousticAnalyzer.AnalysisResult {
        val isValidVoiced = inp.readBoolean()
        val intensityDbfs = inp.readFloat()
        val peakDbfs = inp.readFloat()
        val snrDb = inp.readFloat()
        val jitterPercent = inp.readFloat()
        val shimmerPercent = inp.readFloat()
        val pitchMean = inp.readFloat()
        val pitchMin = inp.readFloat()
        val pitchMax = inp.readFloat()
        val pitchRange = inp.readUTF()
        val isPitchCalibrated = inp.readBoolean()
        val chaoToneNumbers = inp.readUTF()
        val toneCode = inp.readUTF()
        val toneDesc = inp.readUTF()
        val toneTrajectory = inp.readUTF()
        val contourPoints = readFloatList(inp)
        val rawVoicedPitches = readFloatList(inp)
        val speakerF1 = readIntList(inp)
        val speakerF2 = readIntList(inp)
        val segmentCount = inp.readInt()
        val segCount = inp.readInt().coerceIn(0, 1024)
        val segments = List(segCount) {
            AcousticAnalyzer.SegmentInterval(
                startSec = inp.readDouble(),
                endSec = inp.readDouble(),
                label = inp.readUTF()
            )
        }
        val creakyBreaks = inp.readInt()
        val voicedDurationMs = inp.readInt()
        val totalDurationMs = inp.readInt()
        val syllableToneNumbers = readStringList(inp)
        val speakerF3 = readIntList(inp)
        val trajCount = inp.readInt().coerceIn(0, 512)
        val vowelTypes = VowelGlideType.entries
        val formantTrajectories = List(trajCount) {
            val syllableIndex = inp.readInt()
            val syllableLabel = inp.readUTF()
            val vowelTypeOrd = inp.readInt().coerceIn(0, vowelTypes.lastIndex)
            val anchorCount = inp.readInt().coerceIn(0, 1024)
            val anchors = List(anchorCount) {
                FormantAnchorFrame(
                    normPos = inp.readFloat(),
                    timeSec = inp.readFloat(),
                    f1Hz = inp.readInt(),
                    f2Hz = inp.readInt(),
                    f3Hz = inp.readInt(),
                    confidence = inp.readFloat()
                )
            }
            SyllableFormantTrajectory(
                syllableIndex = syllableIndex,
                syllableLabel = syllableLabel,
                vowelType = vowelTypes[vowelTypeOrd],
                anchors = anchors,
                onsetF1 = inp.readInt(),
                onsetF2 = inp.readInt(),
                nucleusF1 = inp.readInt(),
                nucleusF2 = inp.readInt(),
                nucleusF3 = inp.readInt(),
                targetF1 = inp.readInt(),
                targetF2 = inp.readInt(),
                barkGlideSpan = inp.readFloat(),
                trajectoryAngleDeg = inp.readFloat(),
                trajectorySummary = inp.readUTF()
            )
        }
        val isCheckedTone = inp.readBoolean()
        val checkedToneRatio = inp.readFloat()
        val syllableCheckedFlags = readBoolList(inp)
        val syllableNeutralFlags = readBoolList(inp)
        val syllableVoicedMs = readIntList(inp)

        return AcousticAnalyzer.AnalysisResult(
            isValidVoiced = isValidVoiced,
            intensityDbfs = intensityDbfs,
            peakDbfs = peakDbfs,
            snrDb = snrDb,
            jitterPercent = jitterPercent,
            shimmerPercent = shimmerPercent,
            pitchMean = pitchMean,
            pitchMin = pitchMin,
            pitchMax = pitchMax,
            pitchRange = pitchRange,
            isPitchCalibrated = isPitchCalibrated,
            chaoToneNumbers = chaoToneNumbers,
            toneCode = toneCode,
            toneDesc = toneDesc,
            toneTrajectory = toneTrajectory,
            contourPoints = contourPoints,
            rawVoicedPitches = rawVoicedPitches,
            speakerF1 = speakerF1,
            speakerF2 = speakerF2,
            segmentCount = segmentCount,
            segments = segments,
            creakyBreaks = creakyBreaks,
            voicedDurationMs = voicedDurationMs,
            totalDurationMs = totalDurationMs,
            syllableToneNumbers = syllableToneNumbers,
            speakerF3 = speakerF3,
            formantTrajectories = formantTrajectories,
            isCheckedTone = isCheckedTone,
            checkedToneRatio = checkedToneRatio,
            syllableCheckedFlags = syllableCheckedFlags,
            syllableNeutralFlags = syllableNeutralFlags,
            syllableVoicedMs = syllableVoicedMs
        )
    }
}
