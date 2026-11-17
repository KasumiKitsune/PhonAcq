package com.example.audio

import androidx.compose.runtime.Immutable
import com.example.data.Speaker
import com.example.data.Word
import java.util.Locale
import kotlin.math.*

enum class MultiSpeakerCohortMode {
    INDIVIDUAL,
    BY_GENDER,
    BY_SPEAKER_GROUP
}

@Immutable
data class MultiSpeakerCohort(
    val cohortId: String,
    val label: String,
    val metaSubtitle: String,
    val speakerIds: Set<Long>,
    val primarySpeakerId: Long,
    val colorIndex: Int,
    val analyzedWordCount: Int,
    val vowelTokenCount: Int,
    val calibratedBySharedAnchors: Boolean,
    val vowelSpaceAreaZ2: Float,
    val convexHullLobanov: List<Pair<Float, Float>>, // (z2, z1) ordered polygon vertices
    val convexHullBark: List<Pair<Float, Float>>     // (barkF2, barkF1) ordered polygon vertices
)

@Immutable
data class CohortToneRealization(
    val cohortId: String,
    val cohortLabel: String,
    val colorIndex: Int,
    val representativeChao: String,
    val toneShapeLabel: String,
    val isChecked: Boolean,
    val sampleCount: Int,
    val meanF0Hz: Float,
    val relativeDurationRatio: Float,
    val meanContour: List<Float>,       // 5 points in 0f..1f (maps to 1..5 Chao T-value)
    val upperBand1Sigma: List<Float>,
    val lowerBand1Sigma: List<Float>,
    val exampleWords: List<String>
)

@Immutable
data class AlignedToneCategory(
    val categoryKey: String,
    val categoryLabel: String,
    val alignmentSourceLabel: String,
    val isCheckedCategory: Boolean,
    val totalWordCount: Int,
    val maxPairwiseDiff5Deg: Float,
    val hasCategoryDivergence: Boolean,
    val cohortRealizations: List<CohortToneRealization>
)

@Immutable
data class SpeakerToneInventorySummary(
    val cohortId: String,
    val cohortLabel: String,
    val metaSubtitle: String,
    val colorIndex: Int,
    val totalToneCount: Int,
    val smoothToneCount: Int,
    val checkedToneCount: Int,
    val pitchRangeText: String,
    val inventoryChaoSummary: String
)

@Immutable
data class CohortVowelCategoryStats(
    val cohortId: String,
    val cohortLabel: String,
    val colorIndex: Int,
    val sampleCount: Int,
    val dominantVowelType: VowelGlideType,
    val meanF1Hz: Float,
    val meanF2Hz: Float,
    val meanBarkGlide: Float,
    val centerZ1: Float,
    val centerZ2: Float,
    val onsetZ1: Float,
    val onsetZ2: Float,
    val targetZ1: Float,
    val targetZ2: Float,
    val ellipse: VowelConfidenceEllipse
)

@Immutable
data class VowelShiftVector(
    val fromCohortId: String,
    val fromCohortLabel: String,
    val toCohortId: String,
    val toCohortLabel: String,
    val toColorIndex: Int,
    val fromZ1: Float,
    val fromZ2: Float,
    val toZ1: Float,
    val toZ2: Float,
    val fromF1Hz: Float,
    val fromF2Hz: Float,
    val toF1Hz: Float,
    val toF2Hz: Float,
    val deltaZ: Float,
    val directionSummary: String
)

@Immutable
data class AlignedVowelCategory(
    val groupKey: String,
    val groupLabel: String,
    val totalTokenCount: Int,
    val maxCentroidShiftZ: Float,
    val hasGlideTypeSplit: Boolean,
    val cohortStats: List<CohortVowelCategoryStats>,
    val shiftVectors: List<VowelShiftVector>
)

@Immutable
data class SpeakerWordRealization(
    val speakerId: Long,
    val speakerName: String,
    val cohortId: String,
    val cohortLabel: String,
    val colorIndex: Int,
    val word: Word,
    val chaoTone: String,
    val isCheckedTone: Boolean,
    val contourPoints: List<Float>,
    val meanF0Hz: Float,
    val primaryToken: NormalizedVowelToken?,
    val allSyllableTokens: List<NormalizedVowelToken>
)

@Immutable
data class CrossSpeakerWordComparison(
    val wordId: Long,
    val originalIndex: Int,
    val wordText: String,
    val groupName: String,
    val realizations: List<SpeakerWordRealization>,
    val vowelMaxShiftZ: Float,
    val toneMaxRmse5Deg: Float,
    val hasGlideSplit: Boolean,
    val hasToneSplit: Boolean,
    val compositeDivergenceScore: Float,
    val divergenceBadge: String,
    val divergenceDetail: String
)

@Immutable
data class MultiSpeakerAlignedAnalysis(
    val cohortMode: MultiSpeakerCohortMode,
    val onlySharedVocabulary: Boolean,
    val availableSpeakerGroups: List<String>,
    val availableGenders: List<String>,
    val cohorts: List<MultiSpeakerCohort>,
    val sharedWordIds: Set<Long>,
    val strictAllSpeakerSharedCount: Int,
    val pairwiseComparableWordCount: Int,
    val calibratedSpeakerCount: Int,
    val calibrationNote: String,
    val calibratedTokens: List<NormalizedVowelToken>,
    val tokenCohortMap: Map<Long, String>, // speakerId -> cohortId
    val toneAlignmentByWordGroup: Boolean,
    val speakerToneInventories: List<SpeakerToneInventorySummary>,
    val alignedToneCategories: List<AlignedToneCategory>,
    val alignedVowelCategories: List<AlignedVowelCategory>,
    val wordComparisons: List<CrossSpeakerWordComparison>,
    val topDivergentWords: List<CrossSpeakerWordComparison>,
    val toneConsistencyPercent: Int
)

/**
 * Pure statistical & phonological alignment engine for Multi-Speaker Comparison:
 * 1. Eliminates partial-progress Lobanov distortion via Shared-Vocabulary Anchor Calibration.
 * 2. Aligns tone systems across speakers/cohorts (by coherent wordlist tone groups or canonical tone archetypes).
 * 3. Computes per-speaker convex hulls, per-category centroid shift vectors, and word-by-word acoustic divergence rankings.
 */
object MultiSpeakerComparisonEngine {

    private val TONE_KEYWORDS = listOf("平", "上", "去", "入", "调", "舒", "促", "阴", "阳", "tone", "Tone")

    private data class VocalTractParams(
        val meanF1: Float,
        val sdF1: Float,
        val meanF2: Float,
        val sdF2: Float,
        val calibratedByAnchors: Boolean
    )

    fun buildAlignedAnalysis(
        multiReport: MultiSpeakerVowelComparisonReport,
        selectedSpeakerIds: Set<Long>,
        cohortMode: MultiSpeakerCohortMode = MultiSpeakerCohortMode.INDIVIDUAL,
        onlySharedVocabulary: Boolean = false
    ): MultiSpeakerAlignedAnalysis {
        val activeReports = multiReport.speakerReports.filter {
            selectedSpeakerIds.isEmpty() || it.speakerId in selectedSpeakerIds
        }.ifEmpty { multiReport.speakerReports }

        val availableGenders = multiReport.speakerReports
            .mapNotNull { r -> multiReport.speakersById[r.speakerId]?.gender?.trim()?.takeIf { it.isNotEmpty() } }
            .distinct()
        val availableSpeakerGroups = multiReport.speakerReports
            .mapNotNull { r ->
                val g = multiReport.speakersById[r.speakerId]?.groupName?.trim().orEmpty()
                g.takeIf { it.isNotEmpty() && it != "默认分组" && it != "默认组" && it != "未分组" }
            }
            .distinct()

        // 1. Identify word coverage across active speakers
        val speakerWordSets: Map<Long, Set<Long>> = activeReports.associate { rep ->
            val ids = HashSet<Long>(rep.analyzedCount * 2)
            rep.vowelTokens.forEach { ids.add(it.word.id) }
            ids.addAll(rep.wordIdToCanonicalMember.keys)
            rep.speakerId to ids
        }

        val allWordOccurrences = HashMap<Long, Int>()
        for (set in speakerWordSets.values) {
            for (wid in set) {
                allWordOccurrences[wid] = (allWordOccurrences[wid] ?: 0) + 1
            }
        }
        val strictSharedIds = allWordOccurrences.filterValues { it == activeReports.size }.keys
        val pairwiseSharedIds = allWordOccurrences.filterValues { it >= 2 }.keys
        val effectiveSharedIds = if (strictSharedIds.isNotEmpty()) strictSharedIds else pairwiseSharedIds

        // 2. Compute Shared-Anchor Calibrated Lobanov Parameters per speaker
        // Prevents partial-wordlist skew (e.g. a speaker who only recorded 8 front vowels [i, e])
        val tractParamsBySpeaker = computeAnchorCalibratedLobanovParams(
            reports = activeReports,
            onlySharedVocabulary = onlySharedVocabulary,
            sharedWordIds = effectiveSharedIds
        )

        // 3. Re-project NormalizedVowelTokens using calibrated vocal-tract parameters
        val calibratedTokensBySpeaker = LinkedHashMap<Long, List<NormalizedVowelToken>>()
        for (rep in activeReports) {
            val params = tractParamsBySpeaker[rep.speakerId] ?: VocalTractParams(500f, 140f, 1500f, 450f, false)
            fun toZ1(f1: Int): Float = ((f1 - params.meanF1) / params.sdF1).coerceIn(-2.85f, 2.85f)
            fun toZ2(f2: Int): Float = ((f2 - params.meanF2) / params.sdF2).coerceIn(-2.85f, 2.85f)

            val filteredSource = if (onlySharedVocabulary && effectiveSharedIds.isNotEmpty()) {
                rep.vowelTokens.filter { it.word.id in effectiveSharedIds }
            } else {
                rep.vowelTokens
            }

            calibratedTokensBySpeaker[rep.speakerId] = filteredSource.map { tok ->
                val recalAnchors = tok.anchorsHz.map { (f1, f2) -> toZ1(f1) to toZ2(f2) }
                tok.copy(
                    z1Onset = toZ1(tok.onsetF1Hz),
                    z2Onset = toZ2(tok.onsetF2Hz),
                    z1Nucleus = toZ1(tok.nucleusF1Hz),
                    z2Nucleus = toZ2(tok.nucleusF2Hz),
                    z1Target = toZ1(tok.targetF1Hz),
                    z2Target = toZ2(tok.targetF2Hz),
                    anchorsLobanov = recalAnchors
                )
            }
        }

        // 4. Build Cohorts (Individual vs Gender vs Speaker Group)
        val effectiveCohortMode = when (cohortMode) {
            MultiSpeakerCohortMode.BY_GENDER ->
                if (availableGenders.size >= 2) MultiSpeakerCohortMode.BY_GENDER else MultiSpeakerCohortMode.INDIVIDUAL
            MultiSpeakerCohortMode.BY_SPEAKER_GROUP ->
                if (availableSpeakerGroups.size >= 2) MultiSpeakerCohortMode.BY_SPEAKER_GROUP else MultiSpeakerCohortMode.INDIVIDUAL
            MultiSpeakerCohortMode.INDIVIDUAL -> MultiSpeakerCohortMode.INDIVIDUAL
        }

        data class RawCohortGroup(
            val id: String,
            val label: String,
            val subtitle: String,
            val reports: List<SpeakerCorpusPhonologyReport>
        )

        val rawCohorts: List<RawCohortGroup> = when (effectiveCohortMode) {
            MultiSpeakerCohortMode.INDIVIDUAL -> activeReports.map { r ->
                val spk = multiReport.speakersById[r.speakerId]
                val meta = buildList {
                    spk?.gender?.takeIf { it.isNotBlank() }?.let { add(it) }
                    spk?.age?.takeIf { it > 0 }?.let { add("${it}岁") }
                    spk?.dialect?.takeIf { it.isNotBlank() }?.let { add(it) }
                    spk?.groupName?.takeIf { it.isNotBlank() && it != "默认分组" }?.let { add(it) }
                }.joinToString(" · ")
                RawCohortGroup(
                    id = "spk_${r.speakerId}",
                    label = r.speakerName,
                    subtitle = meta.ifEmpty { "已录 ${r.analyzedCount} 词" },
                    reports = listOf(r)
                )
            }
            MultiSpeakerCohortMode.BY_GENDER -> activeReports
                .groupBy { multiReport.speakersById[it.speakerId]?.gender?.trim()?.ifBlank { "未标性别" } ?: "未标性别" }
                .entries.map { (gender, reps) ->
                    val names = reps.take(3).joinToString("、") { it.speakerName }
                    RawCohortGroup(
                        id = "gender_$gender",
                        label = "${gender}声组 (${reps.size}人)",
                        subtitle = names,
                        reports = reps
                    )
                }
            MultiSpeakerCohortMode.BY_SPEAKER_GROUP -> activeReports
                .groupBy {
                    multiReport.speakersById[it.speakerId]?.groupName?.trim()?.ifBlank { "默认分组" } ?: "默认分组"
                }
                .entries.map { (grp, reps) ->
                    val names = reps.take(3).joinToString("、") { it.speakerName }
                    RawCohortGroup(
                        id = "grp_$grp",
                        label = "$grp (${reps.size}人)",
                        subtitle = names,
                        reports = reps
                    )
                }
        }

        val speakerToCohortId = HashMap<Long, String>()
        val speakerToColorIndex = HashMap<Long, Int>()
        val cohorts = rawCohorts.mapIndexed { idx, rc ->
            val spkIds = rc.reports.map { it.speakerId }.toSet()
            spkIds.forEach { sid ->
                speakerToCohortId[sid] = rc.id
                speakerToColorIndex[sid] = idx
            }
            val cTokens = rc.reports.flatMap { calibratedTokensBySpeaker[it.speakerId].orEmpty() }
            val lobPts = cTokens.flatMap { tok ->
                if (tok.vowelType == VowelGlideType.MONOPHTHONG) {
                    listOf(tok.z2Nucleus to tok.z1Nucleus)
                } else {
                    listOf(tok.z2Onset to tok.z1Onset, tok.z2Nucleus to tok.z1Nucleus, tok.z2Target to tok.z1Target)
                }
            }
            val barkPts = cTokens.map { tok ->
                PhoneticScaleUtils.hzToBark(tok.nucleusF2Hz.toFloat()) to
                    PhoneticScaleUtils.hzToBark(tok.nucleusF1Hz.toFloat())
            }
            val hullLob = computeConvexHull(lobPts)
            val hullBark = computeConvexHull(barkPts)
            val areaZ2 = computePolygonArea(hullLob)
            val anyCalibrated = rc.reports.any { tractParamsBySpeaker[it.speakerId]?.calibratedByAnchors == true }
            val wordCnt = rc.reports.sumOf { rep ->
                if (onlySharedVocabulary && effectiveSharedIds.isNotEmpty()) {
                    speakerWordSets[rep.speakerId]?.count { it in effectiveSharedIds } ?: 0
                } else {
                    rep.analyzedCount
                }
            }
            MultiSpeakerCohort(
                cohortId = rc.id,
                label = rc.label,
                metaSubtitle = rc.subtitle,
                speakerIds = spkIds,
                primarySpeakerId = rc.reports.first().speakerId,
                colorIndex = idx,
                analyzedWordCount = wordCnt,
                vowelTokenCount = cTokens.size,
                calibratedBySharedAnchors = anyCalibrated,
                vowelSpaceAreaZ2 = areaZ2,
                convexHullLobanov = hullLob,
                convexHullBark = hullBark
            )
        }

        val allCalibratedTokens = activeReports.flatMap { calibratedTokensBySpeaker[it.speakerId].orEmpty() }

        // 5. Build Cross-Speaker Tone Inventory & Aligned Tone Categories
        val speakerToneInventories = cohorts.map { cohort ->
            val reps = activeReports.filter { it.speakerId in cohort.speakerIds }
            buildToneInventorySummary(cohort, reps)
        }

        val (alignedToneCategories, toneByWordGroup) = buildAlignedToneCategories(
            cohorts = cohorts,
            activeReports = activeReports,
            onlySharedVocabulary = onlySharedVocabulary,
            sharedWordIds = effectiveSharedIds
        )

        // 6. Build Aligned Vowel Categories, Ellipses & Shift Vectors
        val alignedVowelCategories = buildAlignedVowelCategories(
            cohorts = cohorts,
            tokensBySpeaker = calibratedTokensBySpeaker
        )

        // 7. Build Word-by-Word Cross-Speaker Alignment & Divergence Ranking
        val wordComparisons = buildWordByWordComparisons(
            activeReports = activeReports,
            tokensBySpeaker = calibratedTokensBySpeaker,
            speakerToCohortId = speakerToCohortId,
            speakerToColorIndex = speakerToColorIndex,
            cohortsById = cohorts.associateBy { it.cohortId },
            onlySharedVocabulary = onlySharedVocabulary,
            sharedWordIds = effectiveSharedIds
        )

        val toneComparableWords = wordComparisons.filter { wc ->
            wc.realizations.count { it.contourPoints.size >= 5 } >= 2
        }
        val toneConsistentCount = toneComparableWords.count { !it.hasToneSplit }
        val toneConsistencyPct = if (toneComparableWords.isNotEmpty()) {
            ((toneConsistentCount * 100f) / toneComparableWords.size).roundToInt().coerceIn(0, 100)
        } else {
            100
        }

        val calibratedSpkCount = activeReports.count { tractParamsBySpeaker[it.speakerId]?.calibratedByAnchors == true }
        val calibrationNote = when {
            onlySharedVocabulary && effectiveSharedIds.isNotEmpty() ->
                "当前仅纳入共同已录交集 (${effectiveSharedIds.size} 词)，彻底消除采录进度差异带来的统计偏置"
            calibratedSpkCount > 0 ->
                "检测到 ${calibratedSpkCount} 位受试者尚未录满全元音空间，已基于共同已录锚点词 (${effectiveSharedIds.size} 词) 自动校正 Lobanov 零点与尺度"
            strictSharedIds.isNotEmpty() ->
                "各受试者元音覆盖均衡，共有 ${strictSharedIds.size} 个同词条已跨全员对齐"
            else ->
                "共有 ${pairwiseSharedIds.size} 个词条具备至少 2 位受试者录音可供横向对比"
        }

        return MultiSpeakerAlignedAnalysis(
            cohortMode = effectiveCohortMode,
            onlySharedVocabulary = onlySharedVocabulary,
            availableSpeakerGroups = availableSpeakerGroups,
            availableGenders = availableGenders,
            cohorts = cohorts,
            sharedWordIds = effectiveSharedIds,
            strictAllSpeakerSharedCount = strictSharedIds.size,
            pairwiseComparableWordCount = pairwiseSharedIds.size,
            calibratedSpeakerCount = calibratedSpkCount,
            calibrationNote = calibrationNote,
            calibratedTokens = allCalibratedTokens,
            tokenCohortMap = speakerToCohortId,
            toneAlignmentByWordGroup = toneByWordGroup,
            speakerToneInventories = speakerToneInventories,
            alignedToneCategories = alignedToneCategories,
            alignedVowelCategories = alignedVowelCategories,
            wordComparisons = wordComparisons,
            topDivergentWords = wordComparisons.take(8),
            toneConsistencyPercent = toneConsistencyPct
        )
    }

    /**
     * Computes each speaker's vocal-tract normalization parameters (meanF1, sdF1, meanF2, sdF2):
     * - If all speakers have broad 3+ quadrant coverage and similar vocabulary, uses standard Lobanov (or shared-subset Lobanov when `onlySharedVocabulary` is enabled and shared subset is broad).
     * - Crucial anti-skew protection: if Speaker B recorded only a narrow or partial subset (e.g., 6 front high vowels)
     *   while Reference Speaker(s) recorded a broad full vowel space (`lobanovValid == true`), we align Speaker B's
     *   full-space center & scale via shared anchor words `(wordId, syllableIndex)` so Speaker B's `[i, e]` stay in the
     *   front-high quadrant instead of exploding across `[-1.5, +1.5]`!
     */
    private fun computeAnchorCalibratedLobanovParams(
        reports: List<SpeakerCorpusPhonologyReport>,
        onlySharedVocabulary: Boolean,
        sharedWordIds: Set<Long>
    ): Map<Long, VocalTractParams> {
        data class RawStats(
            val meanF1: Float,
            val sdF1: Float,
            val meanF2: Float,
            val sdF2: Float,
            val quadrantCount: Int,
            val isBroadSpace: Boolean
        )

        fun computeRawStats(tokens: List<NormalizedVowelToken>): RawStats {
            if (tokens.isEmpty()) {
                return RawStats(500f, 140f, 1500f, 450f, 0, false)
            }
            val f1Pool = ArrayList<Float>(tokens.size * 2)
            val f2Pool = ArrayList<Float>(tokens.size * 2)
            for (t in tokens) {
                f1Pool.add(t.nucleusF1Hz.toFloat())
                f2Pool.add(t.nucleusF2Hz.toFloat())
                if (t.vowelType != VowelGlideType.MONOPHTHONG) {
                    f1Pool.add(t.onsetF1Hz.toFloat())
                    f1Pool.add(t.targetF1Hz.toFloat())
                    f2Pool.add(t.onsetF2Hz.toFloat())
                    f2Pool.add(t.targetF2Hz.toFloat())
                }
            }
            val m1 = f1Pool.average().toFloat()
            val m2 = f2Pool.average().toFloat()
            val s1 = sqrt(f1Pool.map { (it - m1) * (it - m1) }.average()).toFloat()
            val s2 = sqrt(f2Pool.map { (it - m2) * (it - m2) }.average()).toFloat()

            // Check articulatory coverage across 3 classic corners: Front-High, Back-High/Mid, Low
            val hasFrontHigh = f1Pool.indices.any { f1Pool[it] < 460f && f2Pool[it] > 1720f }
            val hasBack = f1Pool.indices.any { f2Pool[it] < 1180f }
            val hasLow = f1Pool.indices.any { f1Pool[it] > 610f }
            val qCount = (if (hasFrontHigh) 1 else 0) + (if (hasBack) 1 else 0) + (if (hasLow) 1 else 0)
            val broad = tokens.size >= 6 && s1 >= 55f && s2 >= 180f && qCount >= 2
            return RawStats(
                meanF1 = m1,
                sdF1 = if (broad) s1 else maxOf(s1, 130f),
                meanF2 = m2,
                sdF2 = if (broad) s2 else maxOf(s2, 420f),
                quadrantCount = qCount,
                isBroadSpace = broad
            )
        }

        val fullStatsBySpeaker = reports.associate { it.speakerId to computeRawStats(it.vowelTokens) }

        // If user explicitly turned on onlySharedVocabulary AND the shared subset itself spans a broad vowel space across speakers,
        // compute Lobanov directly on the exact identical shared vocabulary for 1:1 lexical balance!
        if (onlySharedVocabulary && sharedWordIds.size >= 6) {
            val sharedStats = reports.associate { rep ->
                rep.speakerId to computeRawStats(rep.vowelTokens.filter { it.word.id in sharedWordIds })
            }
            if (sharedStats.values.all { it.isBroadSpace }) {
                return sharedStats.mapValues { (_, st) ->
                    VocalTractParams(st.meanF1, st.sdF1, st.meanF2, st.sdF2, calibratedByAnchors = false)
                }
            }
        }

        // Reference pool: speakers who have broad, unbiased vowel coverage (at least 2 articulatory corners and >= 6 tokens)
        val maxTokenCount = reports.maxOfOrNull { it.vowelTokens.size } ?: 0
        val referenceSpeakers = reports.filter { rep ->
            val st = fullStatsBySpeaker[rep.speakerId]!!
            st.isBroadSpace && rep.vowelTokens.size >= (maxTokenCount * 0.55f).toInt().coerceAtLeast(6)
        }

        if (referenceSpeakers.isEmpty()) {
            return fullStatsBySpeaker.mapValues { (_, st) ->
                VocalTractParams(st.meanF1, st.sdF1, st.meanF2, st.sdF2, calibratedByAnchors = false)
            }
        }

        // Build reference Z-coordinates per (wordId, syllableIndex) averaged across reference speakers
        data class AnchorRef(
            val refZ1: Float,
            val refZ2: Float,
            val refF1: Float,
            val refF2: Float
        )

        val refAnchorMap = HashMap<String, AnchorRef>()
        val groupedRefTokens = referenceSpeakers
            .flatMap { rep ->
                val st = fullStatsBySpeaker[rep.speakerId]!!
                rep.vowelTokens.map { tok ->
                    val z1 = (tok.nucleusF1Hz - st.meanF1) / st.sdF1
                    val z2 = (tok.nucleusF2Hz - st.meanF2) / st.sdF2
                    Triple("${tok.word.id}_${tok.syllableIndex}", Pair(z1, z2), Pair(tok.nucleusF1Hz.toFloat(), tok.nucleusF2Hz.toFloat()))
                }
            }
            .groupBy { it.first }

        for ((key, list) in groupedRefTokens) {
            refAnchorMap[key] = AnchorRef(
                refZ1 = list.map { it.second.first }.average().toFloat(),
                refZ2 = list.map { it.second.second }.average().toFloat(),
                refF1 = list.map { it.third.first }.average().toFloat(),
                refF2 = list.map { it.third.second }.average().toFloat()
            )
        }

        val avgRefSdF1 = referenceSpeakers.map { fullStatsBySpeaker[it.speakerId]!!.sdF1 }.average().toFloat()
        val avgRefSdF2 = referenceSpeakers.map { fullStatsBySpeaker[it.speakerId]!!.sdF2 }.average().toFloat()

        val result = HashMap<Long, VocalTractParams>()
        for (rep in reports) {
            val raw = fullStatsBySpeaker[rep.speakerId]!!
            val needsAnchorCalibration = !raw.isBroadSpace ||
                raw.quadrantCount < 3 && rep.vowelTokens.size < (maxTokenCount * 0.65f).toInt()

            if (!needsAnchorCalibration) {
                result[rep.speakerId] = VocalTractParams(raw.meanF1, raw.sdF1, raw.meanF2, raw.sdF2, calibratedByAnchors = false)
                continue
            }

            // Match shared anchor syllables with reference speakers
            val matchedPairs = rep.vowelTokens.mapNotNull { tok ->
                val ref = refAnchorMap["${tok.word.id}_${tok.syllableIndex}"] ?: return@mapNotNull null
                tok to ref
            }

            if (matchedPairs.size < 2) {
                result[rep.speakerId] = VocalTractParams(raw.meanF1, raw.sdF1, raw.meanF2, raw.sdF2, calibratedByAnchors = false)
                continue
            }

            val spkMeanF1 = matchedPairs.map { it.first.nucleusF1Hz.toFloat() }.average().toFloat()
            val spkMeanF2 = matchedPairs.map { it.first.nucleusF2Hz.toFloat() }.average().toFloat()
            val refMeanZ1 = matchedPairs.map { it.second.refZ1 }.average().toFloat()
            val refMeanZ2 = matchedPairs.map { it.second.refZ2 }.average().toFloat()
            val refMeanF1 = matchedPairs.map { it.second.refF1 }.average().toFloat().coerceAtLeast(220f)
            val refMeanF2 = matchedPairs.map { it.second.refF2 }.average().toFloat().coerceAtLeast(700f)

            val spkAnchorSdF1 = sqrt(matchedPairs.map { (it.first.nucleusF1Hz - spkMeanF1).pow(2) }.average()).toFloat()
            val spkAnchorSdF2 = sqrt(matchedPairs.map { (it.first.nucleusF2Hz - spkMeanF2).pow(2) }.average()).toFloat()
            val refAnchorSdZ1 = sqrt(matchedPairs.map { (it.second.refZ1 - refMeanZ1).pow(2) }.average()).toFloat()
            val refAnchorSdZ2 = sqrt(matchedPairs.map { (it.second.refZ2 - refMeanZ2).pow(2) }.average()).toFloat()

            // Physiological vocal-tract scaling factor from matched anchor frequencies
            val tractScale1 = (spkMeanF1 / refMeanF1).coerceIn(0.75f, 1.38f)
            val tractScale2 = (spkMeanF2 / refMeanF2).coerceIn(0.75f, 1.38f)

            val estSdF1 = if (refAnchorSdZ1 >= 0.48f && spkAnchorSdF1 >= 35f) {
                (spkAnchorSdF1 / refAnchorSdZ1).coerceIn(75f, 230f)
            } else {
                (avgRefSdF1 * tractScale1).coerceIn(85f, 220f)
            }

            val estSdF2 = if (refAnchorSdZ2 >= 0.48f && spkAnchorSdF2 >= 110f) {
                (spkAnchorSdF2 / refAnchorSdZ2).coerceIn(240f, 720f)
            } else {
                (avgRefSdF2 * tractScale2).coerceIn(260f, 680f)
            }

            val estMeanF1 = (spkMeanF1 - refMeanZ1 * estSdF1).coerceIn(300f, 720f)
            val estMeanF2 = (spkMeanF2 - refMeanZ2 * estSdF2).coerceIn(1050f, 2100f)

            result[rep.speakerId] = VocalTractParams(
                meanF1 = estMeanF1,
                sdF1 = estSdF1,
                meanF2 = estMeanF2,
                sdF2 = estSdF2,
                calibratedByAnchors = true
            )
        }
        return result
    }

    private fun buildToneInventorySummary(
        cohort: MultiSpeakerCohort,
        reports: List<SpeakerCorpusPhonologyReport>
    ): SpeakerToneInventorySummary {
        if (reports.isEmpty()) {
            return SpeakerToneInventorySummary(
                cohortId = cohort.cohortId,
                cohortLabel = cohort.label,
                metaSubtitle = cohort.metaSubtitle,
                colorIndex = cohort.colorIndex,
                totalToneCount = 0,
                smoothToneCount = 0,
                checkedToneCount = 0,
                pitchRangeText = "未校准",
                inventoryChaoSummary = "暂无有效声调语料"
            )
        }

        // For a single speaker, use their pure acoustic tone clusters directly; for a multi-speaker cohort, summarize consensus
        val primary = reports.first()
        val clusters = primary.pureAcousticToneClusters
        val smoothCnt = clusters.count { !it.isCheckedCategory }
        val checkedCnt = clusters.count { it.isCheckedCategory }

        val ranges = reports.mapNotNull { it.calibratedPitchRangeHz }
        val pitchRangeStr = if (ranges.isNotEmpty()) {
            val minHz = ranges.map { it.first }.average().roundToInt()
            val maxHz = ranges.map { it.second }.average().roundToInt()
            "${minHz}–${maxHz} Hz"
        } else {
            "自适应音域"
        }

        val chaoSummary = if (clusters.isEmpty()) {
            "多音节或样本不足"
        } else {
            clusters.take(6).joinToString(" · ") { c ->
                val mark = if (c.isCheckedCategory) "促" else ""
                "[${c.canonicalChao}]$mark(${c.sampleCount})"
            }
        }

        return SpeakerToneInventorySummary(
            cohortId = cohort.cohortId,
            cohortLabel = cohort.label,
            metaSubtitle = cohort.metaSubtitle,
            colorIndex = cohort.colorIndex,
            totalToneCount = clusters.size,
            smoothToneCount = smoothCnt,
            checkedToneCount = checkedCnt,
            pitchRangeText = pitchRangeStr,
            inventoryChaoSummary = chaoSummary
        )
    }

    private fun buildAlignedToneCategories(
        cohorts: List<MultiSpeakerCohort>,
        activeReports: List<SpeakerCorpusPhonologyReport>,
        onlySharedVocabulary: Boolean,
        sharedWordIds: Set<Long>
    ): Pair<List<AlignedToneCategory>, Boolean> {
        val reportsById = activeReports.associateBy { it.speakerId }
        val allMembers = activeReports.flatMap { rep ->
            rep.wordIdToCanonicalMember.values.filter { m ->
                !onlySharedVocabulary || sharedWordIds.isEmpty() || m.word.id in sharedWordIds
            }
        }
        if (allMembers.isEmpty()) return emptyList<AlignedToneCategory>() to false

        // Decide whether wordlist custom groups represent tone categories
        val customGroupNames = allMembers.map { it.word.groupName.trim() }
            .filter { it.isNotEmpty() && it != "默认组" && it != "未分组" && it != "默认分组" }
            .distinct()
        val toneNamedCount = customGroupNames.count { g -> TONE_KEYWORDS.any { kw -> g.contains(kw) } }
        val useWordGroupAlignment = customGroupNames.size >= 2 && (
            toneNamedCount >= (customGroupNames.size + 1) / 2 ||
                activeReports.all { rep ->
                    rep.hasCustomWordGroups && rep.mergedToneClusters.isNotEmpty() &&
                        rep.mergedToneClusters.size <= 10 &&
                        rep.mergedToneClusters.map { c ->
                            c.upperBand1Sigma.zip(c.lowerBand1Sigma).map { (u, l) -> (u - l) * 0.5f }.average()
                        }.average() <= 0.20
                }
            )

        data class BucketEntry(
            val cohort: MultiSpeakerCohort,
            val member: CanonicalToneMember
        )

        val buckets = LinkedHashMap<String, Pair<String, MutableList<BucketEntry>>>()

        if (useWordGroupAlignment) {
            for (cohort in cohorts) {
                for (sid in cohort.speakerIds) {
                    val rep = reportsById[sid] ?: continue
                    for (m in rep.wordIdToCanonicalMember.values) {
                        if (onlySharedVocabulary && sharedWordIds.isNotEmpty() && m.word.id !in sharedWordIds) continue
                        val gName = m.word.groupName.trim().ifEmpty { "未分组" }
                        val key = "tg_$gName"
                        val pair = buckets.getOrPut(key) { gName to ArrayList() }
                        pair.second.add(BucketEntry(cohort, m))
                    }
                }
            }
        } else {
            // Align by phonological tone contour archetype (using reference speaker's canonical category per wordId when shared,
            // falling back to contour archetype so every speaker's realization of the same lexical tone class lands in the same bucket!)
            val refReport = activeReports.maxByOrNull { it.wordIdToCanonicalMember.size }
            val refClusters = refReport?.pureAcousticToneClusters.orEmpty()
            val wordToRefCluster = HashMap<Long, CanonicalToneCluster>()
            refClusters.forEach { cl ->
                cl.members.forEach { m -> wordToRefCluster[m.word.id] = cl }
            }

            for (cohort in cohorts) {
                for (sid in cohort.speakerIds) {
                    val rep = reportsById[sid] ?: continue
                    for (m in rep.wordIdToCanonicalMember.values) {
                        if (onlySharedVocabulary && sharedWordIds.isNotEmpty() && m.word.id !in sharedWordIds) continue
                        val refCl = wordToRefCluster[m.word.id]
                        val (bKey, bLabel) = if (refCl != null && refClusters.size in 2..8) {
                            val ex = refCl.exampleWords.take(2).joinToString("")
                            "ref_${refCl.clusterId}" to "${refCl.categoryName} (例:$ex)"
                        } else {
                            classifyUniversalToneArchetype(m.contourPoints, m.isCheckedTone)
                        }
                        val pair = buckets.getOrPut(bKey) { bLabel to ArrayList() }
                        pair.second.add(BucketEntry(cohort, m))
                    }
                }
            }
        }

        val categories = buckets.entries.mapNotNull { (catKey, pair) ->
            val (catLabel, entries) = pair
            if (entries.isEmpty()) return@mapNotNull null

            val byCohort = entries.groupBy { it.cohort.cohortId }
            val realizations = cohorts.mapNotNull { cohort ->
                val list = byCohort[cohort.cohortId]?.map { it.member }?.filter { it.contourPoints.size >= 5 }
                    ?: return@mapNotNull null
                if (list.isEmpty()) return@mapNotNull null

                val mean5 = (0..4).map { ptIdx ->
                    list.map { it.contourPoints[ptIdx] }.average().toFloat().coerceIn(0f, 1f)
                }
                val std5 = (0..4).map { ptIdx ->
                    val m = mean5[ptIdx]
                    sqrt(list.map { (it.contourPoints[ptIdx] - m).pow(2) }.average()).toFloat()
                }
                val upper = (0..4).map { (mean5[it] + std5[it]).coerceIn(0f, 1f) }
                val lower = (0..4).map { (mean5[it] - std5[it]).coerceIn(0f, 1f) }
                val isChecked = list.count { it.isCheckedTone } > list.size / 2
                val repChao = contourToChaoString(mean5, isChecked)
                val shapeLbl = describeContourShape(mean5, isChecked)
                val meanF0 = list.map { it.meanF0Hz }.average().toFloat()
                val avgDur = list.map { it.voicedDurationMs }.average().toFloat()
                val durRatio = if (isChecked) 0.48f else (avgDur / 320f).coerceIn(0.75f, 1.0f)

                CohortToneRealization(
                    cohortId = cohort.cohortId,
                    cohortLabel = cohort.label,
                    colorIndex = cohort.colorIndex,
                    representativeChao = repChao,
                    toneShapeLabel = shapeLbl,
                    isChecked = isChecked,
                    sampleCount = list.size,
                    meanF0Hz = meanF0,
                    relativeDurationRatio = durRatio,
                    meanContour = mean5,
                    upperBand1Sigma = upper,
                    lowerBand1Sigma = lower,
                    exampleWords = list.map { it.word.text }.distinct().take(4)
                )
            }

            if (realizations.isEmpty()) return@mapNotNull null

            var maxDiff5Deg = 0f
            var checkedSplit = false
            for (i in realizations.indices) {
                for (j in i + 1 until realizations.size) {
                    val a = realizations[i]
                    val b = realizations[j]
                    val rmse01 = sqrt((0..4).map { k -> (a.meanContour[k] - b.meanContour[k]).pow(2) }.average()).toFloat()
                    val rmse5 = rmse01 * 4f
                    if (rmse5 > maxDiff5Deg) maxDiff5Deg = rmse5
                    if (a.isChecked != b.isChecked) checkedSplit = true
                }
            }
            val anyChecked = realizations.count { it.isChecked } > realizations.size / 2
            val distinctWordCnt = entries.map { it.member.word.id }.distinct().size

            AlignedToneCategory(
                categoryKey = catKey,
                categoryLabel = catLabel,
                alignmentSourceLabel = if (useWordGroupAlignment) "字表预设调类对齐" else "同源字类对应调型",
                isCheckedCategory = anyChecked,
                totalWordCount = distinctWordCnt,
                maxPairwiseDiff5Deg = maxDiff5Deg,
                hasCategoryDivergence = maxDiff5Deg >= 0.95f || checkedSplit,
                cohortRealizations = realizations
            )
        }.sortedByDescending { it.totalWordCount }

        return categories to useWordGroupAlignment
    }

    private fun buildAlignedVowelCategories(
        cohorts: List<MultiSpeakerCohort>,
        tokensBySpeaker: Map<Long, List<NormalizedVowelToken>>
    ): List<AlignedVowelCategory> {
        val allTokens = cohorts.flatMap { c ->
            c.speakerIds.flatMap { sid -> tokensBySpeaker[sid].orEmpty() }
        }
        if (allTokens.isEmpty()) return emptyList()

        val byGroup = allTokens.groupBy { it.groupKey }
        return byGroup.entries.mapNotNull { (gKey, grpTokens) ->
            if (grpTokens.isEmpty()) return@mapNotNull null
            val gLabel = grpTokens.first().groupLabel

            val cohortStats = cohorts.mapNotNull { cohort ->
                val list = grpTokens.filter { it.speakerId in cohort.speakerIds }
                if (list.isEmpty()) return@mapNotNull null

                val cF1 = list.map { it.nucleusF1Hz }.average().toFloat()
                val cF2 = list.map { it.nucleusF2Hz }.average().toFloat()
                val cZ1 = list.map { it.z1Nucleus }.average().toFloat()
                val cZ2 = list.map { it.z2Nucleus }.average().toFloat()
                val oZ1 = list.map { it.z1Onset }.average().toFloat()
                val oZ2 = list.map { it.z2Onset }.average().toFloat()
                val tZ1 = list.map { it.z1Target }.average().toFloat()
                val tZ2 = list.map { it.z2Target }.average().toFloat()
                val meanGlide = list.map { it.barkGlideSpan }.average().toFloat()

                val domType = list.groupingBy { it.vowelType }.eachCount().maxByOrNull { it.value }?.key
                    ?: VowelGlideType.MONOPHTHONG

                val barkPts = list.map {
                    PhoneticScaleUtils.hzToBark(it.nucleusF2Hz.toFloat()) to
                        PhoneticScaleUtils.hzToBark(it.nucleusF1Hz.toFloat())
                }
                val cB2 = barkPts.map { it.first }.average().toFloat()
                val cB1 = barkPts.map { it.second }.average().toFloat()
                val (majB, minB, angB) = computeEllipseAxes(barkPts, cB2, cB1, 0.20f, 1.55f)

                val lobPts = list.map { it.z2Nucleus to it.z1Nucleus }
                val (majZ, minZ, angZ) = computeEllipseAxes(lobPts, cZ2, cZ1, 0.15f, 1.10f)

                val ellipse = VowelConfidenceEllipse(
                    groupKey = gKey,
                    groupLabel = gLabel,
                    speakerId = cohort.primarySpeakerId,
                    speakerName = cohort.label,
                    sampleCount = list.size,
                    representativeWord = list.first().displayLabel,
                    centerF1Hz = cF1,
                    centerF2Hz = cF2,
                    centerBarkF1 = cB1,
                    centerBarkF2 = cB2,
                    semiMajorBark = majB,
                    semiMinorBark = minB,
                    angleRadBark = angB,
                    centerLobanovZ1 = cZ1,
                    centerLobanovZ2 = cZ2,
                    semiMajorLobanov = majZ,
                    semiMinorLobanov = minZ,
                    angleRadLobanov = angZ
                )

                CohortVowelCategoryStats(
                    cohortId = cohort.cohortId,
                    cohortLabel = cohort.label,
                    colorIndex = cohort.colorIndex,
                    sampleCount = list.size,
                    dominantVowelType = domType,
                    meanF1Hz = cF1,
                    meanF2Hz = cF2,
                    meanBarkGlide = meanGlide,
                    centerZ1 = cZ1,
                    centerZ2 = cZ2,
                    onsetZ1 = oZ1,
                    onsetZ2 = oZ2,
                    targetZ1 = tZ1,
                    targetZ2 = tZ2,
                    ellipse = ellipse
                )
            }

            if (cohortStats.isEmpty()) return@mapNotNull null

            val shiftVectors = ArrayList<VowelShiftVector>()
            var maxShift = 0f
            val base = cohortStats.first()
            for (k in 1 until cohortStats.size) {
                val other = cohortStats[k]
                val dz1 = other.centerZ1 - base.centerZ1
                val dz2 = other.centerZ2 - base.centerZ2
                val dist = hypot(dz1, dz2)
                val dir = describeLobanovShift(dz1, dz2, dist)
                shiftVectors.add(
                    VowelShiftVector(
                        fromCohortId = base.cohortId,
                        fromCohortLabel = base.cohortLabel,
                        toCohortId = other.cohortId,
                        toCohortLabel = other.cohortLabel,
                        toColorIndex = other.colorIndex,
                        fromZ1 = base.centerZ1,
                        fromZ2 = base.centerZ2,
                        toZ1 = other.centerZ1,
                        toZ2 = other.centerZ2,
                        fromF1Hz = base.meanF1Hz,
                        fromF2Hz = base.meanF2Hz,
                        toF1Hz = other.meanF1Hz,
                        toF2Hz = other.meanF2Hz,
                        deltaZ = dist,
                        directionSummary = dir
                    )
                )
            }
            for (i in cohortStats.indices) {
                for (j in i + 1 until cohortStats.size) {
                    val d = hypot(
                        cohortStats[i].centerZ1 - cohortStats[j].centerZ1,
                        cohortStats[i].centerZ2 - cohortStats[j].centerZ2
                    )
                    if (d > maxShift) maxShift = d
                }
            }
            val hasGlideSplit = cohortStats.map { it.dominantVowelType == VowelGlideType.MONOPHTHONG }.distinct().size > 1

            AlignedVowelCategory(
                groupKey = gKey,
                groupLabel = gLabel,
                totalTokenCount = grpTokens.size,
                maxCentroidShiftZ = maxShift,
                hasGlideTypeSplit = hasGlideSplit,
                cohortStats = cohortStats,
                shiftVectors = shiftVectors
            )
        }.sortedByDescending { it.totalTokenCount }
    }

    private fun buildWordByWordComparisons(
        activeReports: List<SpeakerCorpusPhonologyReport>,
        tokensBySpeaker: Map<Long, List<NormalizedVowelToken>>,
        speakerToCohortId: Map<Long, String>,
        speakerToColorIndex: Map<Long, Int>,
        cohortsById: Map<String, MultiSpeakerCohort>,
        onlySharedVocabulary: Boolean,
        sharedWordIds: Set<Long>
    ): List<CrossSpeakerWordComparison> {
        val wordMetaMap = LinkedHashMap<Long, Word>()
        val perWordRealizations = LinkedHashMap<Long, MutableList<SpeakerWordRealization>>()

        for (rep in activeReports) {
            val spkTokensByWord = tokensBySpeaker[rep.speakerId].orEmpty().groupBy { it.word.id }
            val allWordIdsForSpk = (spkTokensByWord.keys + rep.wordIdToCanonicalMember.keys).distinct()
            val cId = speakerToCohortId[rep.speakerId] ?: "spk_${rep.speakerId}"
            val cLbl = cohortsById[cId]?.label ?: rep.speakerName
            val cIdx = speakerToColorIndex[rep.speakerId] ?: 0

            for (wid in allWordIdsForSpk) {
                if (onlySharedVocabulary && sharedWordIds.isNotEmpty() && wid !in sharedWordIds) continue
                val sylTokens = spkTokensByWord[wid].orEmpty().sortedBy { it.syllableIndex }
                val toneMem = rep.wordIdToCanonicalMember[wid]
                val polyMem = if (toneMem == null) {
                    rep.polysyllabicTonePatterns.firstNotNullOfOrNull { pg -> pg.members.find { it.word.id == wid } }
                } else null

                val wordObj = sylTokens.firstOrNull()?.word ?: toneMem?.word ?: polyMem?.word ?: continue
                wordMetaMap.putIfAbsent(wid, wordObj)

                val chao = toneMem?.rawChao ?: polyMem?.patternChao ?: "--"
                val isChecked = toneMem?.isCheckedTone ?: (polyMem?.syllableCheckedFlags?.any { it } == true)
                val contour = toneMem?.contourPoints ?: polyMem?.contourPoints ?: emptyList()
                val f0 = toneMem?.meanF0Hz ?: polyMem?.meanF0Hz ?: 0f

                val list = perWordRealizations.getOrPut(wid) { ArrayList() }
                list.add(
                    SpeakerWordRealization(
                        speakerId = rep.speakerId,
                        speakerName = rep.speakerName,
                        cohortId = cId,
                        cohortLabel = cLbl,
                        colorIndex = cIdx,
                        word = wordObj,
                        chaoTone = chao,
                        isCheckedTone = isChecked,
                        contourPoints = contour,
                        meanF0Hz = f0,
                        primaryToken = sylTokens.firstOrNull(),
                        allSyllableTokens = sylTokens
                    )
                )
            }
        }

        return perWordRealizations.entries.mapNotNull { (wid, list) ->
            if (list.size < 2) return@mapNotNull null
            val refWord = wordMetaMap[wid] ?: list.first().word

            var maxVowelDistZ = 0f
            var glideSplit = false
            var maxGlideDiffBark = 0f

            for (i in list.indices) {
                val tA = list[i].primaryToken ?: continue
                for (j in i + 1 until list.size) {
                    val tB = list[j].primaryToken ?: continue
                    val nucDist = hypot(tA.z1Nucleus - tB.z1Nucleus, tA.z2Nucleus - tB.z2Nucleus)
                    val tgtDist = hypot(tA.z1Target - tB.z1Target, tA.z2Target - tB.z2Target)
                    val combinedDist = maxOf(nucDist, tgtDist * 0.85f)
                    if (combinedDist > maxVowelDistZ) maxVowelDistZ = combinedDist
                    val monoA = tA.vowelType == VowelGlideType.MONOPHTHONG
                    val monoB = tB.vowelType == VowelGlideType.MONOPHTHONG
                    if (monoA != monoB) glideSplit = true
                    maxGlideDiffBark = maxOf(maxGlideDiffBark, abs(tA.barkGlideSpan - tB.barkGlideSpan))
                }
            }

            var maxToneRmse5 = 0f
            var checkedMismatch = false
            for (i in list.indices) {
                val cA = list[i].contourPoints
                if (cA.size < 5) continue
                for (j in i + 1 until list.size) {
                    val cB = list[j].contourPoints
                    if (cB.size < 5) continue
                    val n = minOf(cA.size, cB.size)
                    val rmse01 = sqrt((0 until n).map { k -> (cA[k] - cB[k]).pow(2) }.average()).toFloat()
                    val rmse5 = rmse01 * 4f
                    if (rmse5 > maxToneRmse5) maxToneRmse5 = rmse5
                    if (list[i].isCheckedTone != list[j].isCheckedTone) checkedMismatch = true
                }
            }

            val toneSplit = maxToneRmse5 >= 1.15f || checkedMismatch
            val glideSplitConfirmed = glideSplit && maxGlideDiffBark >= 0.75f

            val score = maxVowelDistZ * 1.15f +
                (if (glideSplitConfirmed) 0.95f else 0f) +
                (maxToneRmse5 * 0.55f) +
                (if (toneSplit) 0.75f else 0f)

            val (badge, detail) = when {
                glideSplitConfirmed && toneSplit ->
                    "调韵双分歧" to "跨人同时存在声调异读与单/复韵母分化 (ΔZ=${String.format(Locale.US, "%.2f", maxVowelDistZ)})"
                glideSplitConfirmed -> {
                    val summary = list.mapNotNull { r ->
                        r.primaryToken?.let { "${r.speakerName}:${it.vowelType.chineseLabel.substringBefore(' ')}" }
                    }.take(3).joinToString(" / ")
                    "单复韵分化" to summary
                }
                toneSplit -> {
                    val summary = list.take(3).joinToString(" / ") { "${it.speakerName}[${it.chaoTone}]" }
                    "声调分歧" to summary
                }
                maxVowelDistZ >= 0.85f ->
                    "舌位偏移" to String.format(Locale.US, "归一化舌位最大跨距 ΔZ = %.2f", maxVowelDistZ)
                else ->
                    "读音一致" to String.format(Locale.US, "跨人高度吻合 (ΔZ = %.2f)", maxVowelDistZ)
            }

            CrossSpeakerWordComparison(
                wordId = wid,
                originalIndex = refWord.originalIndex,
                wordText = refWord.text,
                groupName = refWord.groupName,
                realizations = list,
                vowelMaxShiftZ = maxVowelDistZ,
                toneMaxRmse5Deg = maxToneRmse5,
                hasGlideSplit = glideSplitConfirmed,
                hasToneSplit = toneSplit,
                compositeDivergenceScore = score,
                divergenceBadge = badge,
                divergenceDetail = detail
            )
        }.sortedWith(
            compareByDescending<CrossSpeakerWordComparison> { it.compositeDivergenceScore }
                .thenBy { it.originalIndex }
        )
    }

    private fun classifyUniversalToneArchetype(pts: List<Float>, isChecked: Boolean): Pair<String, String> {
        if (pts.size < 5) return "arch_other" to "其他调型"
        if (isChecked) {
            val mean = pts.average()
            return if (mean >= 0.52) "arch_checked_high" to "高促调类" else "arch_checked_low" to "中低促调类"
        }
        val slope = (pts[4] + pts[3]) * 0.5f - (pts[0] + pts[1]) * 0.5f
        val midDip = ((pts[0] + pts[4]) * 0.5f) - pts[2]
        val mean = pts.average()
        return when {
            midDip >= 0.16f -> "arch_concave" to "曲折/降升调类"
            midDip <= -0.16f -> "arch_convex" to "凸拱/升降调类"
            slope >= 0.20f -> "arch_rising" to "升调类"
            slope <= -0.22f -> "arch_falling" to "降调类"
            mean >= 0.62 -> "arch_high_level" to "高平调类"
            mean >= 0.36 -> "arch_mid_level" to "中平调类"
            else -> "arch_low_level" to "低平调类"
        }
    }

    private fun contourToChaoString(pts: List<Float>, isChecked: Boolean): String {
        if (pts.isEmpty()) return "--"
        fun toDeg(v: Float): Int = (1f + v.coerceIn(0f, 1f) * 4f).roundToInt().coerceIn(1, 5)
        val d0 = toDeg(pts.first())
        val d2 = toDeg(pts[pts.size / 2])
        val d4 = toDeg(pts.last())
        if (isChecked) {
            return if (abs(d4 - d0) <= 1) "$d2" else "$d0$d4"
        }
        val midDev = abs(d2 - ((d0 + d4) / 2f))
        return when {
            midDev >= 1.1f && d2 != d0 && d2 != d4 -> "$d0$d2$d4"
            d0 == d4 -> "$d0$d4"
            else -> "$d0$d4"
        }
    }

    private fun describeContourShape(pts: List<Float>, isChecked: Boolean): String {
        if (isChecked) return "短促入声"
        val slope = pts.last() - pts.first()
        val dip = ((pts.first() + pts.last()) * 0.5f) - pts[pts.size / 2]
        return when {
            dip >= 0.16f -> "降升曲折"
            dip <= -0.16f -> "升降拱形"
            slope >= 0.20f -> "上扬升调"
            slope <= -0.22f -> "下滑降调"
            pts.average() >= 0.62 -> "高平稳态"
            pts.average() <= 0.35 -> "低平稳态"
            else -> "中平稳态"
        }
    }

    private fun describeLobanovShift(dz1: Float, dz2: Float, dist: Float): String {
        if (dist < 0.22f) return String.format(Locale.US, "位置基本重合 (ΔZ=%.2f)", dist)
        val fb = when {
            dz2 >= 0.22f -> "偏前"
            dz2 <= -0.22f -> "偏后"
            else -> ""
        }
        val hl = when {
            dz1 <= -0.22f -> "偏高"
            dz1 >= 0.22f -> "偏低"
            else -> ""
        }
        val dir = (fb + hl).ifEmpty { "微移" }
        return String.format(Locale.US, "%s (ΔZ=%.2f)", dir, dist)
    }

    /**
     * Andrew's monotone chain 2D convex hull on (x, y) coordinates.
     */
    fun computeConvexHull(points: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        val unique = points
            .filter { it.first.isFinite() && it.second.isFinite() }
            .distinctBy { "${(it.first * 1000).roundToInt()}_${(it.second * 1000).roundToInt()}" }
            .sortedWith(compareBy<Pair<Float, Float>> { it.first }.thenBy { it.second })

        if (unique.size <= 2) return unique

        fun cross(o: Pair<Float, Float>, a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
            return (a.first - o.first) * (b.second - o.second) - (a.second - o.second) * (b.first - o.first)
        }

        val lower = ArrayList<Pair<Float, Float>>(unique.size)
        for (p in unique) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0f) {
                lower.removeAt(lower.lastIndex)
            }
            lower.add(p)
        }

        val upper = ArrayList<Pair<Float, Float>>(unique.size)
        for (i in unique.indices.reversed()) {
            val p = unique[i]
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0f) {
                upper.removeAt(upper.lastIndex)
            }
            upper.add(p)
        }

        lower.removeAt(lower.lastIndex)
        upper.removeAt(upper.lastIndex)
        return lower + upper
    }

    private fun computePolygonArea(hull: List<Pair<Float, Float>>): Float {
        if (hull.size < 3) return 0f
        var area = 0.0
        for (i in hull.indices) {
            val j = (i + 1) % hull.size
            area += hull[i].first.toDouble() * hull[j].second.toDouble()
            area -= hull[j].first.toDouble() * hull[i].second.toDouble()
        }
        return (abs(area) * 0.5).toFloat()
    }

    private fun computeEllipseAxes(
        pts: List<Pair<Float, Float>>,
        cx: Float,
        cy: Float,
        minAxis: Float,
        maxAxis: Float
    ): Triple<Float, Float, Float> {
        if (pts.size < 2) return Triple(minAxis * 1.25f, minAxis, 0f)
        var sxx = 0.0
        var syy = 0.0
        var sxy = 0.0
        for ((x, y) in pts) {
            val dx = (x - cx).toDouble()
            val dy = (y - cy).toDouble()
            sxx += dx * dx
            syy += dy * dy
            sxy += dx * dy
        }
        val denom = (pts.size - 1).coerceAtLeast(1).toDouble()
        val vxx = sxx / denom
        val vyy = syy / denom
        val vxy = sxy / denom
        val trace = vxx + vyy
        val diff = vxx - vyy
        val disc = sqrt((diff * diff + 4.0 * vxy * vxy).coerceAtLeast(0.0))
        val lambda1 = ((trace + disc) * 0.5).coerceAtLeast(0.0)
        val lambda2 = ((trace - disc) * 0.5).coerceAtLeast(0.0)
        val major = (sqrt(lambda1).toFloat() * 1.18f).coerceIn(minAxis, maxAxis)
        val minor = (sqrt(lambda2).toFloat() * 1.18f).coerceIn(minAxis * 0.85f, major)
        val angle = (0.5 * atan2(2.0 * vxy, diff)).toFloat()
        return Triple(major, minor, if (angle.isFinite()) angle else 0f)
    }
}
