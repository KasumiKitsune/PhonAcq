package com.example.audio

import android.content.Context
import com.example.i18n.S
import com.example.i18n.withArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Persistent user-controlled overrides for vowel trajectory classification (Monophthong / Diphthong / Triphthong).
 *
 * Precedence:
 * 1. Word + Syllable override (`word_{wordId}_syl_{syllableIndex}`)
 * 2. Project Rime-Group override (`proj_{projectId}_grp_{canonicalGroupKey}`)
 * 3. Pure objective acoustic classification (`null`)
 */
object VowelGlideOverrideStore {

    private const val PREFS_NAME = "phonacq_vowel_glide_overrides"
    private const val KEY_JSON = "overrides_json"
    private val cache = ConcurrentHashMap<String, VowelGlideType>()
    @Volatile
    private var loadedFromPrefs = false

    private val _overrideRevision = MutableStateFlow(0)
    val overrideRevision: StateFlow<Int> = _overrideRevision.asStateFlow()

    private fun ensureLoaded(context: Context?) {
        if (loadedFromPrefs || context == null) return
        synchronized(this) {
            if (loadedFromPrefs) return
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val rawJson = prefs.getString(KEY_JSON, null)
                if (!rawJson.isNullOrBlank()) {
                    val obj = JSONObject(rawJson)
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val code = obj.optString(k, "")
                        parseType(code)?.let { cache[k] = it }
                    }
                }
                loadedFromPrefs = true
            } catch (_: Exception) {
                loadedFromPrefs = true
            }
        }
    }

    private fun parseType(code: String?): VowelGlideType? {
        if (code.isNullOrBlank()) return null
        return VowelGlideType.entries.firstOrNull {
            it.csvCode.equals(code, ignoreCase = true) || it.name.equals(code, ignoreCase = true)
        }
    }

    fun canonicalGroupKey(groupKeyOrName: String): String {
        return groupKeyOrName.trim().removePrefix("wg_").trim()
    }

    private fun wordKey(wordId: Long, syllableIndex: Int): String = "word_${wordId}_syl_${syllableIndex}"

    private fun groupKey(projectId: Long, groupKeyOrName: String): String =
        "proj_${projectId}_grp_${canonicalGroupKey(groupKeyOrName)}"

    fun getWordSyllableOverride(
        context: Context?,
        wordId: Long,
        syllableIndex: Int = 0
    ): VowelGlideType? {
        if (wordId <= 0L) return null
        ensureLoaded(context)
        return cache[wordKey(wordId, syllableIndex)]
    }

    fun getGroupOverride(
        context: Context?,
        projectId: Long,
        groupKeyOrName: String
    ): VowelGlideType? {
        val canon = canonicalGroupKey(groupKeyOrName)
        if (projectId <= 0L || canon.isEmpty()) return null
        ensureLoaded(context)
        return cache[groupKey(projectId, canon)]
    }

    fun resolveEffectiveOverride(
        context: Context?,
        projectId: Long,
        wordId: Long,
        syllableIndex: Int,
        groupKeyOrName: String?
    ): VowelGlideType? {
        getWordSyllableOverride(context, wordId, syllableIndex)?.let { return it }
        if (!groupKeyOrName.isNullOrBlank()) {
            getGroupOverride(context, projectId, groupKeyOrName)?.let { return it }
        }
        return null
    }

    fun setWordSyllableOverride(
        context: Context,
        wordId: Long,
        syllableIndex: Int = 0,
        overrideType: VowelGlideType?
    ) {
        if (wordId <= 0L) return
        ensureLoaded(context)
        val key = wordKey(wordId, syllableIndex)
        updateEntry(context, key, overrideType)
    }

    fun setGroupOverride(
        context: Context,
        projectId: Long,
        groupKeyOrName: String,
        overrideType: VowelGlideType?
    ) {
        val canon = canonicalGroupKey(groupKeyOrName)
        if (projectId <= 0L || canon.isEmpty()) return
        ensureLoaded(context)
        val key = groupKey(projectId, canon)
        updateEntry(context, key, overrideType)
    }

    private fun updateEntry(context: Context, key: String, overrideType: VowelGlideType?) {
        if (overrideType == null) {
            cache.remove(key)
        } else {
            cache[key] = overrideType
        }
        try {
            val obj = JSONObject()
            for ((k, v) in cache.entries) {
                obj.put(k, v.csvCode)
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_JSON, obj.toString())
                .apply()
        } catch (_: Exception) {
        }
        CorpusPhonologyAnalyzer.clearCache()
        _overrideRevision.value = _overrideRevision.value + 1
    }

    /**
     * Re-projects an extracted 9-anchor formant trajectory onto the user-specified [forcedType]
     * using the syllable's actual measured formant anchors (never fake coordinates).
     */
    fun applyOverrideToTrajectory(
        traj: SyllableFormantTrajectory,
        forcedType: VowelGlideType
    ): SyllableFormantTrajectory {
        if (traj.anchors.size < 3) {
            if (traj.vowelType == forcedType) return traj
            return traj.copy(vowelType = forcedType)
        }

        val anchors = traj.anchors
        val n = anchors.size
        val b1Arr = FloatArray(n) { PhoneticScaleUtils.hzToBark(anchors[it].f1Hz.toFloat()) }
        val b2Arr = FloatArray(n) { PhoneticScaleUtils.hzToBark(anchors[it].f2Hz.toFloat()) }

        val onsetJump = hypot(b1Arr[1] - b1Arr[0], b2Arr[1] - b2Arr[0])
        val step12 = if (n >= 4) hypot(b1Arr[2] - b1Arr[1], b2Arr[2] - b2Arr[1]) else 0f
        val onsetCos = if (n >= 4 && onsetJump > 1e-4f && step12 > 1e-4f) {
            ((b1Arr[1] - b1Arr[0]) * (b1Arr[2] - b1Arr[1]) + (b2Arr[1] - b2Arr[0]) * (b2Arr[2] - b2Arr[1])) / (onsetJump * step12)
        } else 0f
        val offsetJump = hypot(b1Arr[n - 1] - b1Arr[n - 2], b2Arr[n - 1] - b2Arr[n - 2])
        val stepPenult = if (n >= 4) hypot(b1Arr[n - 2] - b1Arr[n - 3], b2Arr[n - 2] - b2Arr[n - 3]) else 0f
        val offsetCos = if (n >= 4 && offsetJump > 1e-4f && stepPenult > 1e-4f) {
            ((b1Arr[n - 1] - b1Arr[n - 2]) * (b1Arr[n - 2] - b1Arr[n - 3]) + (b2Arr[n - 1] - b2Arr[n - 2]) * (b2Arr[n - 2] - b2Arr[n - 3])) / (offsetJump * stepPenult)
        } else 0f
        val startIdx = if (onsetJump <= 0.65f || (onsetJump <= 1.65f && step12 >= 0.15f && onsetCos >= 0.40f)) 0 else 1
        val endIdx = if (offsetJump <= 0.65f || (offsetJump <= 1.65f && stepPenult >= 0.15f && offsetCos >= 0.40f)) {
            n - 1
        } else {
            (n - 2).coerceAtLeast(startIdx)
        }

        val midIndices = (0 until n).filter { it in 2..(n - 3) }.ifEmpty { listOf(n / 2) }
        val sortedF1 = midIndices.map { anchors[it].f1Hz }.sorted()
        val sortedF2 = midIndices.map { anchors[it].f2Hz }.sorted()
        val sortedF3 = midIndices.map { anchors[it].f3Hz }.sorted()
        val medF1 = sortedF1[sortedF1.size / 2]
        val medF2 = sortedF2[sortedF2.size / 2]
        val medF3 = sortedF3[sortedF3.size / 2]

        var pathLenBark = 0f
        for (i in 0 until n - 1) {
            pathLenBark += hypot(b1Arr[i + 1] - b1Arr[i], b2Arr[i + 1] - b2Arr[i])
        }

        var inflectIdx = n / 2
        var maxInflectDist = -1f
        for (i in 1..(n - 2)) {
            val dx = b2Arr[endIdx] - b2Arr[startIdx]
            val dy = b1Arr[endIdx] - b1Arr[startIdx]
            val lenSq = dx * dx + dy * dy
            val dist = if (lenSq < 1e-6f) {
                hypot(b2Arr[i] - b2Arr[startIdx], b1Arr[i] - b1Arr[startIdx])
            } else {
                val t = (((b2Arr[i] - b2Arr[startIdx]) * dx + (b1Arr[i] - b1Arr[startIdx]) * dy) / lenSq).coerceIn(0f, 1f)
                hypot(b2Arr[i] - (b2Arr[startIdx] + t * dx), b1Arr[i] - (b1Arr[startIdx] + t * dy))
            }
            if (dist > maxInflectDist) {
                maxInflectDist = dist
                inflectIdx = i
            }
        }

        val netSpanBark = hypot(b1Arr[endIdx] - b1Arr[startIdx], b2Arr[endIdx] - b2Arr[startIdx])
        val chartDx = -(b2Arr[endIdx] - b2Arr[startIdx])
        val chartDy = (b1Arr[endIdx] - b1Arr[startIdx])
        val angleDeg = Math.toDegrees(atan2(chartDy.toDouble(), chartDx.toDouble())).toFloat()

        return when (forcedType) {
            VowelGlideType.MONOPHTHONG -> {
                val reg = PhoneticScaleUtils.describeArticulatoryRegion(medF1, medF2)
                val summary = S.acoustics.formantSteadyNucleusFormat.withArgs(
                    "reg" to reg,
                    "f1" to medF1,
                    "f2" to medF2
                ) + " · 手动指定"
                traj.copy(
                    vowelType = VowelGlideType.MONOPHTHONG,
                    onsetF1 = medF1,
                    onsetF2 = medF2,
                    nucleusF1 = medF1,
                    nucleusF2 = medF2,
                    nucleusF3 = medF3,
                    targetF1 = medF1,
                    targetF2 = medF2,
                    barkGlideSpan = netSpanBark,
                    trajectoryAngleDeg = if (angleDeg.isFinite()) angleDeg else 0f,
                    trajectorySummary = summary
                )
            }
            VowelGlideType.DIPHTHONG -> {
                val oF1 = anchors[startIdx].f1Hz
                val oF2 = anchors[startIdx].f2Hz
                val mid = anchors[n / 2]
                val tF1 = anchors[endIdx].f1Hz
                val tF2 = anchors[endIdx].f2Hz
                val rStart = PhoneticScaleUtils.describeArticulatoryRegion(oF1, oF2)
                val rEnd = PhoneticScaleUtils.describeArticulatoryRegion(tF1, tF2)
                val summary = S.acoustics.diphthongGlideSummaryFormat.withArgs(
                    "start" to rStart,
                    "end" to rEnd,
                    "span" to String.format(Locale.US, "%.1f", netSpanBark)
                ) + " · 手动指定"
                traj.copy(
                    vowelType = VowelGlideType.DIPHTHONG,
                    onsetF1 = oF1,
                    onsetF2 = oF2,
                    nucleusF1 = mid.f1Hz,
                    nucleusF2 = mid.f2Hz,
                    nucleusF3 = mid.f3Hz,
                    targetF1 = tF1,
                    targetF2 = tF2,
                    barkGlideSpan = netSpanBark,
                    trajectoryAngleDeg = if (angleDeg.isFinite()) angleDeg else 0f,
                    trajectorySummary = summary
                )
            }
            VowelGlideType.TRIPHTHONG -> {
                val oF1 = anchors[startIdx].f1Hz
                val oF2 = anchors[startIdx].f2Hz
                val infl = anchors[inflectIdx]
                val tF1 = anchors[endIdx].f1Hz
                val tF2 = anchors[endIdx].f2Hz
                val rStart = PhoneticScaleUtils.describeArticulatoryRegion(oF1, oF2)
                val rMid = PhoneticScaleUtils.describeArticulatoryRegion(infl.f1Hz, infl.f2Hz)
                val rEnd = PhoneticScaleUtils.describeArticulatoryRegion(tF1, tF2)
                val summary = S.acoustics.triphthongInflectionSummaryFormat.withArgs(
                    "start" to rStart,
                    "mid" to rMid,
                    "end" to rEnd,
                    "span" to String.format(Locale.US, "%.1f", pathLenBark)
                ) + " · 手动指定"
                traj.copy(
                    vowelType = VowelGlideType.TRIPHTHONG,
                    onsetF1 = oF1,
                    onsetF2 = oF2,
                    nucleusF1 = infl.f1Hz,
                    nucleusF2 = infl.f2Hz,
                    nucleusF3 = infl.f3Hz,
                    targetF1 = tF1,
                    targetF2 = tF2,
                    barkGlideSpan = pathLenBark,
                    trajectoryAngleDeg = if (angleDeg.isFinite()) angleDeg else 0f,
                    trajectorySummary = summary
                )
            }
        }
    }
}
