package com.example.audio

import android.content.Context
import com.example.data.Speaker
import com.example.data.Word
import com.example.i18n.S
import com.example.i18n.withArgs
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.*

data class CorpusAnalysisProgress(
    val completed: Int,
    val total: Int,
    val cacheHits: Int = 0
)

object CorpusPhonologyAnalyzer {

    private val corpusWorkerCount: Int by lazy {
        Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
    }

    private val corpusExecutor by lazy {
        Executors.newFixedThreadPool(corpusWorkerCount) { runnable ->
            Thread(runnable, "PhonAcq-Corpus-Worker").apply { isDaemon = true }
        }
    }

    private data class ReportCacheKey(
        val projectId: Long,
        val speakerId: Long,
        val recordedCount: Int,
        val signatureHash: Int
    )

    private val reportCache = ConcurrentHashMap<ReportCacheKey, SpeakerCorpusPhonologyReport>()
    private val latestReportByProjectSpeaker = ConcurrentHashMap<String, SpeakerCorpusPhonologyReport>()

    private val TONE_GROUP_KEYWORDS = listOf("平", "上", "去", "入", "调", "舒", "促", "阴", "阳", "tone", "Tone")
    private val VOWEL_GROUP_KEYWORDS = listOf("元音", "韵", "摄", "开", "齐", "合", "撮", "鼻", "vowel", "rime")

    fun clearCache() {
        reportCache.clear()
        latestReportByProjectSpeaker.clear()
    }

    fun lookupCachedCanonicalMember(
        projectId: Long,
        speakerId: Long,
        wordId: Long
    ): CanonicalToneMember? {
        val report = latestReportByProjectSpeaker["${projectId}_${speakerId}"] ?: return null
        return report.wordIdToCanonicalMember[wordId]
    }

    fun lookupCachedSpeakerReport(
        projectId: Long,
        speakerId: Long
    ): SpeakerCorpusPhonologyReport? {
        return latestReportByProjectSpeaker["${projectId}_${speakerId}"]
    }

    /**
     * Builds (or retrieves from cache) the complete corpus-level phonology report for one speaker:
     * - Robust speaker pitch range & median duration calibration
     * - Unsupervised agglomerative tone clustering (eliminating one-word-one-tone fragmentation)
     * - Group-guided tone summaries (with automatic detection of whether groups are tone vs rime groups)
     * - +/- 1 sigma tone ribbons & 2 sigma sandhi/misread outlier flags
     * - Dynamic vowel tokens (monophthongs + diphthong/triphthong trajectories), Lobanov Z-scores, and 1 sigma ellipses
     */
    fun buildSpeakerPhonologyReport(
        context: Context,
        projectId: Long,
        speaker: Speaker?,
        words: List<Word>,
        onProgress: ((CorpusAnalysisProgress) -> Unit)? = null,
        isCancelled: () -> Boolean = { false }
    ): SpeakerCorpusPhonologyReport {
        val speakerId = speaker?.id ?: -1L
        val speakerName = speaker?.name?.ifBlank { S.common.defaultSpeakerFallback } ?: S.common.defaultSpeakerFallback
        val speakerGender = speaker?.gender

        val recordedWords = words
            .filter { it.isRecorded && !it.recordingFile.isNullOrEmpty() }
            .sortedBy { it.originalIndex }

        val overrideRev = VowelGlideOverrideStore.overrideRevision.value
        val sigHash = recordedWords.fold(37 + overrideRev * 97) { acc, w ->
            31 * acc + w.id.hashCode() + (w.recordingFile?.hashCode() ?: 0) + w.groupName.hashCode()
        }
        val cacheKey = ReportCacheKey(projectId, speakerId, recordedWords.size, sigHash)
        reportCache[cacheKey]?.let { cached ->
            latestReportByProjectSpeaker["${projectId}_${speakerId}"] = cached
            onProgress?.invoke(CorpusAnalysisProgress(recordedWords.size, recordedWords.size, recordedWords.size))
            return cached
        }

        val projDir = File(context.getExternalFilesDir(null), "project_$projectId")
        val sp = context.getSharedPreferences("speaker_prefs", Context.MODE_PRIVATE)
        val pMin = sp.getFloat("pitch_min_$speakerId", -1f)
        val pMax = sp.getFloat("pitch_max_$speakerId", -1f)
        val manualRange = if (pMin > 40f && pMax > pMin + 15f) Pair(pMin, pMax) else null

        onProgress?.invoke(CorpusAnalysisProgress(0, recordedWords.size, 0))

        val corpusStats = AcousticAnalyzer.resolveSpeakerCorpusStats(
            projDir = projDir,
            speakerId = speakerId,
            speakerGender = speakerGender,
            manualRange = manualRange
        )
        val effectivePitchRange = corpusStats?.pitchRange ?: manualRange
        val medianVoicedMs = corpusStats?.medianVoicedDurationMs ?: 280

        data class AnalyzedToken(
            val word: Word,
            val result: AcousticAnalyzer.AnalysisResult
        )

        val totalCount = recordedWords.size
        val completedCounter = AtomicInteger(0)
        val cacheHitCounter = AtomicInteger(0)
        val lastReportedStep = AtomicInteger(0)
        val reportStride = (totalCount / 24).coerceIn(1, 16)

        fun analyzeSingleWord(w: Word): AnalyzedToken? {
            if (isCancelled()) return null
            val fn = w.recordingFile ?: return null
            val f = File(projDir, fn)
            if (!f.exists() || f.length() <= 44L) return null
            val parts = AcousticAnalyzer.extractSyllableUnits(w.text)
            val segCount = parts.size.coerceAtLeast(1)
            val wasCached = AcousticAnalyzer.isAnalysisCached(
                file = f,
                speakerGender = speakerGender,
                segmentCount = segCount,
                pitchRange = effectivePitchRange,
                segmentLabels = parts,
                speakerMedianVoicedMs = medianVoicedMs
            )
            if (wasCached) {
                cacheHitCounter.incrementAndGet()
            }
            val res = AcousticAnalyzer.analyzeByGender(
                file = f,
                speakerGender = speakerGender,
                segmentCount = segCount,
                pitchRange = effectivePitchRange,
                segmentLabels = parts,
                speakerMedianVoicedMs = medianVoicedMs
            )
            val done = completedCounter.incrementAndGet()
            if (onProgress != null && (done == totalCount || done - lastReportedStep.get() >= reportStride)) {
                lastReportedStep.set(done)
                onProgress.invoke(CorpusAnalysisProgress(done, totalCount, cacheHitCounter.get()))
            }
            return if (res != null && res.isValidVoiced) AnalyzedToken(w, res) else null
        }

        val analyzedTokens: List<AnalyzedToken> = if (totalCount >= 8 && corpusWorkerCount >= 2) {
            val futures = recordedWords.map { w ->
                corpusExecutor.submit<AnalyzedToken?> { analyzeSingleWord(w) }
            }
            futures.mapNotNull { fut ->
                if (isCancelled()) {
                    fut.cancel(false)
                    null
                } else {
                    try {
                        fut.get()
                    } catch (_: Exception) {
                        null
                    }
                }
            }
        } else {
            recordedWords.mapNotNull { analyzeSingleWord(it) }
        }
        AcousticFeatureDiskCache.flushProject(projDir)
        onProgress?.invoke(CorpusAnalysisProgress(totalCount, totalCount, cacheHitCounter.get()))

        if (analyzedTokens.isEmpty()) {
            val emptyReport = SpeakerCorpusPhonologyReport(
                speakerId = speakerId,
                speakerName = speakerName,
                analyzedCount = 0,
                medianVoicedDurationMs = medianVoicedMs,
                calibratedPitchRangeHz = effectivePitchRange,
                hasCustomWordGroups = false,
                mergedToneClusters = emptyList(),
                pureAcousticToneClusters = emptyList(),
                wordIdToCanonicalMember = emptyMap(),
                vowelTokens = emptyList(),
                vowelEllipses = emptyList(),
                lobanovValid = false
            )
            reportCache[cacheKey] = emptyReport
            return emptyReport
        }

        // For tone system discovery, focus primarily on monosyllabic tokens (or initial syllable when all tokens are polysyllabic)
        val monosyllabicTokens = analyzedTokens.filter { it.result.segmentCount == 1 && it.result.contourPoints.size >= 5 }
        val toneSourceTokens = monosyllabicTokens.ifEmpty {
            analyzedTokens.filter { it.result.contourPoints.size >= 5 }
        }

        val tokenContourPairs = toneSourceTokens.map { tok ->
            tok.word to tok.result.copy(
                contourPoints = tok.result.contourPoints.take(5)
            )
        }

        // 1. Unsupervised Acoustic Phonological Clustering (3..8 canonical tone classes, Smooth vs Checked separated)
        val pureAcousticClusters = clusterToneContoursUnsupervised(
            tokens = tokenContourPairs,
            medianSmoothDurationMs = medianVoicedMs,
            isCalibrated = effectivePitchRange != null
        )

        // 2. Group-Guided Tone Summaries (when custom groups exist)
        val nonBlankGroupCount = tokenContourPairs.count {
            val g = it.first.groupName.trim()
            g.isNotEmpty() && g != "默认组" && g != "未分组" && g != S.common.defaultGroupFallback
        }
        val hasCustomGroups = nonBlankGroupCount >= (tokenContourPairs.size * 0.4f).toInt().coerceAtLeast(1) &&
            tokenContourPairs.map { it.first.groupName.trim().ifEmpty { S.common.defaultGroupFallback } }.distinct().size >= 2

        val byGroupClusters = if (hasCustomGroups) {
            buildGroupToneClusters(
                tokens = tokenContourPairs,
                medianSmoothDurationMs = medianVoicedMs,
                isCalibrated = effectivePitchRange != null
            )
        } else {
            emptyList()
        }

        // Determine whether custom groups actually represent TONE categories (vs RIME/semantic groups)
        val groupsAreToneCoherent = if (byGroupClusters.isNotEmpty()) {
            val namedAsTone = byGroupClusters.count { c ->
                TONE_GROUP_KEYWORDS.any { kw -> c.categoryName.contains(kw) }
            } >= (byGroupClusters.size + 1) / 2
            val avgInternalSpread = byGroupClusters.map { c ->
                c.upperBand1Sigma.zip(c.lowerBand1Sigma).map { (u, l) -> (u - l) * 0.5f }.average()
            }.average()
            namedAsTone || avgInternalSpread <= 0.19
        } else {
            false
        }

        val preferredCanonicalClusters = if (groupsAreToneCoherent && byGroupClusters.isNotEmpty()) {
            byGroupClusters
        } else {
            pureAcousticClusters
        }

        val wordIdToMember = HashMap<Long, CanonicalToneMember>(analyzedTokens.size * 2)
        for (cluster in preferredCanonicalClusters) {
            for (m in cluster.members) {
                wordIdToMember[m.word.id] = m
            }
        }

        // 2B. Polysyllabic Tone Sandhi / Connected Pattern Summary (kept strictly separate from monosyllabic citation tone clusters)
        val polysyllabicTokens = analyzedTokens
            .filter { it.result.segmentCount >= 2 && it.result.contourPoints.size >= 10 }
            .map { it.word to it.result }
        val polysyllabicPatterns = buildPolysyllabicTonePatterns(polysyllabicTokens)

        // 3. Vowel Space Tokens, Lobanov Z-score Normalization, and 1-Sigma Confidence Ellipses
        val (vowelTokens, vowelEllipses, lobanovValid) = buildNormalizedVowelSpaceAndEllipses(
            context = context,
            projectId = projectId,
            tokens = analyzedTokens.map { it.word to it.result },
            speakerId = speakerId,
            speakerName = speakerName,
            hasCustomGroups = hasCustomGroups
        )

        val report = SpeakerCorpusPhonologyReport(
            speakerId = speakerId,
            speakerName = speakerName,
            analyzedCount = analyzedTokens.size,
            medianVoicedDurationMs = medianVoicedMs,
            calibratedPitchRangeHz = effectivePitchRange,
            hasCustomWordGroups = hasCustomGroups,
            mergedToneClusters = if (byGroupClusters.isNotEmpty()) byGroupClusters else pureAcousticClusters,
            pureAcousticToneClusters = pureAcousticClusters,
            wordIdToCanonicalMember = wordIdToMember,
            vowelTokens = vowelTokens,
            vowelEllipses = vowelEllipses,
            lobanovValid = lobanovValid,
            polysyllabicTonePatterns = polysyllabicPatterns
        )

        if (reportCache.size > 24) reportCache.clear()
        reportCache[cacheKey] = report
        latestReportByProjectSpeaker["${projectId}_${speakerId}"] = report
        return report
    }

    private fun toSubscriptIndex(idx1Based: Int): String {
        val subs = charArrayOf('₀', '₁', '₂', '₃', '₄', '₅', '₆', '₇', '₈', '₉')
        return idx1Based.toString().map { ch ->
            if (ch in '0'..'9') subs[ch - '0'] else ch
        }.joinToString("")
    }

    private fun buildPolysyllabicTonePatterns(
        rawTokens: List<Pair<Word, AcousticAnalyzer.AnalysisResult>>
    ): List<PolysyllabicTonePatternGroup> {
        if (rawTokens.isEmpty()) return emptyList()
        val grouped = rawTokens.groupBy { (_, res) ->
            "${res.segmentCount}_${res.chaoToneNumbers.ifBlank { "--" }}"
        }
        return grouped.entries.mapIndexedNotNull { idx, (_, pairs) ->
            if (pairs.isEmpty()) return@mapIndexedNotNull null
            val firstRes = pairs.first().second
            val sylCount = firstRes.segmentCount.coerceAtLeast(2)
            val patternChao = firstRes.chaoToneNumbers.ifBlank {
                firstRes.syllableToneNumbers.joinToString("-")
            }
            val sylMeanContours = ArrayList<List<Float>>(sylCount)
            val sylNeutralFlags = ArrayList<Boolean>(sylCount)
            val sylCheckedFlags = ArrayList<Boolean>(sylCount)

            for (s in 0 until sylCount) {
                val offset = s * 5
                val mean5 = (0..4).map { pt ->
                    val vals = pairs.mapNotNull { (_, r) ->
                        r.contourPoints.getOrNull(offset + pt)
                    }
                    if (vals.isNotEmpty()) vals.average().toFloat().coerceIn(0.02f, 0.98f) else 0.5f
                }
                sylMeanContours.add(mean5)
                val neutralMaj = pairs.count { (_, r) -> r.syllableNeutralFlags.getOrElse(s) { false } } * 2 > pairs.size
                val checkedMaj = !neutralMaj && pairs.count { (_, r) -> r.syllableCheckedFlags.getOrElse(s) { false } } * 2 > pairs.size
                sylNeutralFlags.add(neutralMaj)
                sylCheckedFlags.add(checkedMaj)
            }

            val patternLabel = buildString {
                append(
                    when (sylCount) {
                        2 -> S.acoustics.sandhiPatternDisyllabic
                        3 -> S.acoustics.sandhiPatternTrisyllabic
                        else -> S.acoustics.sandhiPatternPolysyllabic.withArgs("count" to sylCount)
                    }
                )
                if (sylNeutralFlags.any { it }) append(S.acoustics.withNeutralSuffix)
                else if (sylCheckedFlags.any { it }) append(S.acoustics.withCheckedSuffix)
            }

            val members = pairs.map { (w, r) ->
                val labels = r.segments.mapIndexed { sIdx, seg ->
                    seg.label.ifBlank { S.common.syllableFallback.withArgs("index" to sIdx + 1) }
                }
                PolysyllabicTonePatternMember(
                    word = w,
                    patternChao = r.chaoToneNumbers,
                    syllableCount = r.segmentCount,
                    syllableLabels = labels,
                    syllableTones = r.syllableToneNumbers,
                    syllableNeutralFlags = r.syllableNeutralFlags,
                    syllableCheckedFlags = r.syllableCheckedFlags,
                    contourPoints = r.contourPoints,
                    meanF0Hz = r.pitchMean,
                    voicedDurationMs = r.voicedDurationMs
                )
            }.sortedBy { it.word.originalIndex }

            PolysyllabicTonePatternGroup(
                patternKey = "poly_tone_$idx",
                patternChao = patternChao,
                syllableCount = sylCount,
                patternLabel = patternLabel,
                sampleCount = members.size,
                meanSyllableContours = sylMeanContours,
                syllableNeutralFlags = sylNeutralFlags,
                syllableCheckedFlags = sylCheckedFlags,
                exampleWords = members.take(5).map { it.word.text },
                members = members
            )
        }.sortedWith(
            compareByDescending<PolysyllabicTonePatternGroup> { it.sampleCount }
                .thenBy { it.syllableCount }
                .thenBy { it.patternChao }
        )
    }

    /**
     * Builds a cross-speaker normalized vowel comparison report across all recorded speakers of a project.
     */
    fun buildMultiSpeakerComparisonReport(
        context: Context,
        projectId: Long,
        speakerWordPairs: List<Pair<Speaker, List<Word>>>,
        onProgress: ((CorpusAnalysisProgress) -> Unit)? = null,
        isCancelled: () -> Boolean = { false }
    ): MultiSpeakerVowelComparisonReport {
        val totalWordsAcrossSpeakers = speakerWordPairs.sumOf { (_, wList) ->
            wList.count { it.isRecorded && !it.recordingFile.isNullOrEmpty() }
        }
        var cumulativeDone = 0
        var cumulativeHits = 0
        val reports = speakerWordPairs.mapNotNull { (spk, wList) ->
            if (isCancelled()) return@mapNotNull null
            val spkRecorded = wList.count { it.isRecorded && !it.recordingFile.isNullOrEmpty() }
            val baseDone = cumulativeDone
            val baseHits = cumulativeHits
            var lastSpkHits = 0
            val r = buildSpeakerPhonologyReport(
                context = context,
                projectId = projectId,
                speaker = spk,
                words = wList,
                onProgress = { p ->
                    lastSpkHits = p.cacheHits
                    onProgress?.invoke(
                        CorpusAnalysisProgress(
                            completed = (baseDone + p.completed).coerceAtMost(totalWordsAcrossSpeakers),
                            total = totalWordsAcrossSpeakers,
                            cacheHits = baseHits + p.cacheHits
                        )
                    )
                },
                isCancelled = isCancelled
            )
            cumulativeDone += spkRecorded
            cumulativeHits += lastSpkHits
            if (r.vowelTokens.isNotEmpty()) r else null
        }
        onProgress?.invoke(CorpusAnalysisProgress(totalWordsAcrossSpeakers, totalWordsAcrossSpeakers, cumulativeHits))
        val allTokens = reports.flatMap { it.vowelTokens }
        // Also compute overall per-speaker vowel space hull/ellipse + per-group ellipses
        val allEllipses = reports.flatMap { it.vowelEllipses }
        val spkMap = speakerWordPairs.associate { it.first.id to it.first }
        return MultiSpeakerVowelComparisonReport(
            speakerReports = reports,
            allNormalizedTokens = allTokens,
            speakerEllipses = allEllipses,
            speakersById = spkMap
        )
    }

    private data class ToneFeatureVec(
        val word: Word,
        val res: AcousticAnalyzer.AnalysisResult,
        val pts: FloatArray,
        val meanLevel: Float,
        val slope: Float,
        val curvature: Float,
        val isChecked: Boolean
    )

    private fun extractToneFeature(
        word: Word,
        res: AcousticAnalyzer.AnalysisResult
    ): ToneFeatureVec {
        val p = FloatArray(5) { idx -> res.contourPoints[idx.coerceAtMost(res.contourPoints.lastIndex)] }
        val meanLvl = 0.18f * p[0] + 0.26f * p[1] + 0.30f * p[2] + 0.20f * p[3] + 0.06f * p[4]
        val slope = ((-2f * p[0]) - p[1] + p[3] + (2f * p[4])) / 10f * 4f
        val curv = ((2f * p[0]) - p[1] - 2f * p[2] - p[3] + (2f * p[4])) / 14f * 4f
        return ToneFeatureVec(
            word = word,
            res = res,
            pts = p,
            meanLevel = meanLvl,
            slope = slope,
            curvature = curv,
            isChecked = res.isCheckedTone
        )
    }

    private fun toneFeatureDistance(a: ToneFeatureVec, b: ToneFeatureVec): Float {
        var sqPts = 0f
        for (i in 0..4) {
            val d = a.pts[i] - b.pts[i]
            sqPts += d * d
        }
        val rmse = sqrt(sqPts / 5f)
        val dSlope = abs(a.slope - b.slope)
        val dCurv = abs(a.curvature - b.curvature)
        val dMean = abs(a.meanLevel - b.meanLevel)
        return sqrt(
            0.42f * rmse * rmse +
                0.34f * dSlope * dSlope +
                0.18f * dCurv * dCurv +
                0.24f * dMean * dMean
        )
    }

    private fun clusterToneContoursUnsupervised(
        tokens: List<Pair<Word, AcousticAnalyzer.AnalysisResult>>,
        medianSmoothDurationMs: Int,
        isCalibrated: Boolean
    ): List<CanonicalToneCluster> {
        if (tokens.isEmpty()) return emptyList()
        val features = tokens.map { (w, r) -> extractToneFeature(w, r) }

        val smoothFeats = features.filter { !it.isChecked }
        val checkedFeats = features.filter { it.isChecked }

        val smoothPartition = runAgglomerativePartition(
            items = smoothFeats,
            baseMergeDist = 0.215f,
            maxClusters = 6
        )
        val checkedPartition = runAgglomerativePartition(
            items = checkedFeats,
            baseMergeDist = 0.24f,
            maxClusters = 3
        )

        val rawClusters = ArrayList<CanonicalToneCluster>()
        smoothPartition.forEachIndexed { idx, group ->
            buildCanonicalClusterFromGroup(
                clusterId = "auto_smooth_$idx",
                explicitCategoryName = null,
                feats = group,
                isCheckedCategory = false,
                medianSmoothDurationMs = medianSmoothDurationMs,
                isCalibrated = isCalibrated
            )?.let { rawClusters.add(it) }
        }
        checkedPartition.forEachIndexed { idx, group ->
            buildCanonicalClusterFromGroup(
                clusterId = "auto_checked_$idx",
                explicitCategoryName = null,
                feats = group,
                isCheckedCategory = true,
                medianSmoothDurationMs = medianSmoothDurationMs,
                isCalibrated = isCalibrated
            )?.let { rawClusters.add(it) }
        }

        // Consolidate any clusters that map to the identical (isCheckedCategory, canonicalChao) toneme
        val mergedByChao = rawClusters.groupBy { "${it.isCheckedCategory}_${it.canonicalChao}" }
        val consolidated = mergedByChao.entries.mapIndexedNotNull { idx, (_, sameClusters) ->
            if (sameClusters.size == 1) {
                sameClusters.first()
            } else {
                val combinedFeats = sameClusters.flatMap { cl ->
                    cl.members.mapNotNull { m ->
                        features.firstOrNull { it.word.id == m.word.id }
                    }
                }
                buildCanonicalClusterFromGroup(
                    clusterId = "auto_merged_$idx",
                    explicitCategoryName = null,
                    feats = combinedFeats,
                    isCheckedCategory = sameClusters.first().isCheckedCategory,
                    medianSmoothDurationMs = medianSmoothDurationMs,
                    isCalibrated = isCalibrated
                )
            }
        }

        return consolidated.sortedWith(
            compareBy<CanonicalToneCluster> { it.isCheckedCategory }
                .thenByDescending { it.sampleCount }
                .thenByDescending { it.meanF0Hz }
        )
    }

    private fun runAgglomerativePartition(
        items: List<ToneFeatureVec>,
        baseMergeDist: Float,
        maxClusters: Int
    ): List<List<ToneFeatureVec>> {
        if (items.isEmpty()) return emptyList()
        if (items.size == 1) return listOf(items)

        val clusters = items.map { mutableListOf(it) }.toMutableList()

        fun clusterDistance(c1: List<ToneFeatureVec>, c2: List<ToneFeatureVec>): Float {
            var sum = 0f
            for (a in c1) {
                for (b in c2) {
                    sum += toneFeatureDistance(a, b)
                }
            }
            return sum / (c1.size * c2.size).coerceAtLeast(1)
        }

        while (clusters.size > 1) {
            var bestI = -1
            var bestJ = -1
            var minDist = Float.MAX_VALUE
            for (i in 0 until clusters.size) {
                for (j in i + 1 until clusters.size) {
                    val d = clusterDistance(clusters[i], clusters[j])
                    if (d < minDist) {
                        minDist = d
                        bestI = i
                        bestJ = j
                    }
                }
            }
            if (bestI < 0 || bestJ < 0) break

            // Merge if within natural phonetic allotone threshold OR if cluster count still exceeds maxClusters
            val allowSingletonAbsorb = (clusters[bestI].size == 1 || clusters[bestJ].size == 1) &&
                items.size >= 8 && minDist <= baseMergeDist * 1.28f
            if (minDist <= baseMergeDist || clusters.size > maxClusters || allowSingletonAbsorb) {
                clusters[bestI].addAll(clusters[bestJ])
                clusters.removeAt(bestJ)
            } else {
                break
            }
        }
        return clusters
    }

    private fun buildGroupToneClusters(
        tokens: List<Pair<Word, AcousticAnalyzer.AnalysisResult>>,
        medianSmoothDurationMs: Int,
        isCalibrated: Boolean
    ): List<CanonicalToneCluster> {
        val grouped = tokens.groupBy { it.first.groupName.trim().ifEmpty { S.common.defaultGroupFallback } }
        return grouped.entries.mapIndexedNotNull { idx, (groupName, pairs) ->
            val feats = pairs.map { (w, r) -> extractToneFeature(w, r) }
            val checkedMajority = feats.count { it.isChecked } > feats.size / 2
            buildCanonicalClusterFromGroup(
                clusterId = "group_$idx",
                explicitCategoryName = groupName,
                feats = feats,
                isCheckedCategory = checkedMajority || groupName.contains("入") || groupName.contains("促"),
                medianSmoothDurationMs = medianSmoothDurationMs,
                isCalibrated = isCalibrated
            )
        }
    }

    private fun buildCanonicalClusterFromGroup(
        clusterId: String,
        explicitCategoryName: String?,
        feats: List<ToneFeatureVec>,
        isCheckedCategory: Boolean,
        medianSmoothDurationMs: Int,
        isCalibrated: Boolean
    ): CanonicalToneCluster? {
        if (feats.isEmpty()) return null

        // Compute 15% trimmed mean contour & 1-sigma band so single misread tokens don't distort canonical value
        val meanContour = ArrayList<Float>(5)
        val sdContour = ArrayList<Float>(5)

        for (ptIdx in 0..4) {
            val vals = feats.map { it.pts[ptIdx] }.sorted()
            val trimCnt = if (vals.size >= 7) (vals.size * 0.15f).toInt().coerceAtLeast(1) else 0
            val coreVals = if (vals.size - 2 * trimCnt >= 2) {
                vals.subList(trimCnt, vals.size - trimCnt)
            } else {
                vals
            }
            val avg = coreVals.average().toFloat()
            val variance = vals.map { (it - avg) * (it - avg) }.average().toFloat()
            val sd = sqrt(variance).coerceIn(0.025f, 0.28f)
            meanContour.add(avg)
            sdContour.add(sd)
        }

        val upper1Sigma = (0..4).map { (meanContour[it] + sdContour[it]).coerceIn(0.02f, 0.99f) }
        val lower1Sigma = (0..4).map { (meanContour[it] - sdContour[it]).coerceIn(0.01f, 0.98f) }

        val canonicalQuad = AcousticAnalyzer.classifyContourRobust(
            contourPoints = meanContour,
            isValidVoiced = true,
            isCalibrated = isCalibrated,
            isCheckedTone = isCheckedCategory
        )
        val canonicalChao = canonicalQuad.chaoDigits

        val shapeLabel = describeCanonicalCategoryTitle(canonicalChao, isCheckedCategory)
        val finalCatName = explicitCategoryName ?: "$shapeLabel [$canonicalChao]"

        val avgClusterSd = sdContour.average().toFloat().coerceAtLeast(0.075f)
        val meanF0 = feats.map { it.res.pitchMean }.average().toFloat()
        val avgDurRaw = feats.map { it.res.voicedDurationMs }.average()
        val meanDurMs = (if (avgDurRaw.isFinite()) avgDurRaw.roundToInt() else 180).coerceAtLeast(40)
        val relDurRatio = if (isCheckedCategory) {
            (meanDurMs.toFloat() / medianSmoothDurationMs.coerceAtLeast(180).toFloat()).coerceIn(0.36f, 0.62f)
        } else {
            1.0f
        }

        val members = feats.map { f ->
            var sqSum = 0f
            for (i in 0..4) {
                val diff = f.pts[i] - meanContour[i]
                sqSum += diff * diff
            }
            val rmse = sqrt(sqSum / 5f)
            val devSigma = rmse / avgClusterSd
            val isOutlier = feats.size >= 4 && rmse >= 0.175f && devSigma >= 1.90f
            CanonicalToneMember(
                word = f.word,
                rawChao = f.res.chaoToneNumbers,
                rawToneCode = f.res.toneCode,
                canonicalChao = canonicalChao,
                canonicalCategoryName = finalCatName,
                contourPoints = f.pts.toList(),
                meanF0Hz = f.res.pitchMean,
                voicedDurationMs = f.res.voicedDurationMs,
                isCheckedTone = f.isChecked,
                deviationSigma = devSigma,
                isOutlier = isOutlier
            )
        }.sortedBy { it.deviationSigma }

        // Representative words: pick tokens closest to cluster medoid/mean first!
        val repExamples = members.filter { !it.isOutlier }.ifEmpty { members }
            .take(5)
            .map { it.word.text }

        return CanonicalToneCluster(
            clusterId = clusterId,
            categoryName = finalCatName,
            canonicalChao = canonicalChao,
            toneShapeLabel = shapeLabel,
            isCheckedCategory = isCheckedCategory,
            sampleCount = members.size,
            meanF0Hz = meanF0,
            meanDurationMs = meanDurMs,
            relativeDurationRatio = relDurRatio,
            meanContour = meanContour,
            upperBand1Sigma = upper1Sigma,
            lowerBand1Sigma = lower1Sigma,
            exampleWords = repExamples,
            outlierCount = members.count { it.isOutlier },
            members = members.sortedBy { it.word.originalIndex }
        )
    }

    private fun describeCanonicalCategoryTitle(chao: String, isChecked: Boolean): String {
        if (isChecked) {
            return when {
                chao.length == 1 -> {
                    val d = chao[0].digitToIntOrNull() ?: 3
                    when {
                        d >= 4 -> S.acoustics.checkedHighCat
                        d == 3 -> S.acoustics.checkedMidCat
                        else -> S.acoustics.checkedLowCat
                    }
                }
                chao.first() > chao.last() -> S.acoustics.checkedShortFallCat
                else -> S.acoustics.checkedShortRiseCat
            }
        }
        if (chao.length == 3) {
            val d0 = chao[0].digitToIntOrNull() ?: 3
            val d1 = chao[1].digitToIntOrNull() ?: 2
            return if (d1 < d0) S.acoustics.concaveToneCat else S.acoustics.convexToneCat
        }
        if (chao.length == 2) {
            val d0 = chao[0].digitToIntOrNull() ?: 3
            val d1 = chao[1].digitToIntOrNull() ?: 3
            return when {
                d0 == d1 -> when (d0) {
                    5 -> S.acoustics.highLevelCat
                    4 -> S.acoustics.halfHighLevelCat
                    3 -> S.acoustics.midLevelCat
                    2 -> S.acoustics.halfLowLevelCat
                    else -> S.acoustics.lowLevelCat
                }
                d1 > d0 -> if (d1 >= 4 && d0 >= 3) S.acoustics.highRiseCat else if (d0 <= 2 && d1 <= 3) S.acoustics.lowRiseCat else S.acoustics.riseCat
                else -> if (d0 >= 4 && d1 <= 2) S.acoustics.fullFallCat else if (d0 >= 4) S.acoustics.highFallCat else S.acoustics.lowFallCat
            }
        }
        return S.acoustics.genericToneCat
    }

    private fun buildNormalizedVowelSpaceAndEllipses(
        context: Context,
        projectId: Long,
        tokens: List<Pair<Word, AcousticAnalyzer.AnalysisResult>>,
        speakerId: Long,
        speakerName: String,
        hasCustomGroups: Boolean
    ): Triple<List<NormalizedVowelToken>, List<VowelConfidenceEllipse>, Boolean> {
        data class RawVowelEntry(
            val word: Word,
            val traj: SyllableFormantTrajectory,
            val syllableIndex: Int,
            val wordSyllableCount: Int,
            val isNeutralSyllable: Boolean
        )

        val entries = ArrayList<RawVowelEntry>(tokens.size * 2)
        for ((w, r) in tokens) {
            if (r.formantTrajectories.isNotEmpty()) {
                val totalSyls = maxOf(r.segmentCount, r.formantTrajectories.size)
                r.formantTrajectories.forEachIndexed { idx, traj ->
                    if (traj.nucleusF1 in 180..1080 && traj.nucleusF2 in 500..2950) {
                        val isNeutral = r.syllableNeutralFlags.getOrElse(idx) { false }
                        entries.add(
                            RawVowelEntry(
                                word = w,
                                traj = traj,
                                syllableIndex = idx,
                                wordSyllableCount = totalSyls,
                                isNeutralSyllable = isNeutral
                            )
                        )
                    }
                }
            } else {
                val totalSyls = maxOf(r.segmentCount, r.speakerF1.size)
                val sylUnits = AcousticAnalyzer.extractSyllableUnits(w.text)
                for (idx in 0 until totalSyls) {
                    val f1 = r.speakerF1.getOrNull(idx) ?: 0
                    val f2 = r.speakerF2.getOrNull(idx) ?: 0
                    if (f1 in 180..1080 && f2 in 500..2950) {
                        val sylLbl = r.segments.getOrNull(idx)?.label?.ifBlank { null }
                            ?: sylUnits.getOrNull(idx)
                            ?: w.text.take(2)
                        val isNeutral = r.syllableNeutralFlags.getOrElse(idx) { false }
                        entries.add(
                            RawVowelEntry(
                                word = w,
                                traj = SyllableFormantTrajectory(
                                    syllableIndex = idx,
                                    syllableLabel = sylLbl,
                                    vowelType = VowelGlideType.MONOPHTHONG,
                                    anchors = emptyList(),
                                    onsetF1 = f1,
                                    onsetF2 = f2,
                                    nucleusF1 = f1,
                                    nucleusF2 = f2,
                                    nucleusF3 = r.speakerF3.getOrNull(idx) ?: 0,
                                    targetF1 = f1,
                                    targetF2 = f2,
                                    barkGlideSpan = 0f,
                                    trajectoryAngleDeg = 0f,
                                    trajectorySummary = S.acoustics.monophthongSteadyNucleusSummary.withArgs(
                                        "f1" to f1,
                                        "f2" to f2
                                    )
                                ),
                                syllableIndex = idx,
                                wordSyllableCount = totalSyls,
                                isNeutralSyllable = isNeutral
                            )
                        )
                    }
                }
            }
        }

        if (entries.isEmpty()) return Triple(emptyList(), emptyList(), false)

        // Check whether custom word groups represent VOWEL/RIME groups (tight Bark spread) vs TONE groups
        val useWordGroupsForVowels = if (hasCustomGroups && entries.none { it.wordSyllableCount > 1 }) {
            val byWordGrp = entries.groupBy { it.word.groupName.trim().ifEmpty { S.common.defaultGroupFallback } }
            val namedAsVowel = byWordGrp.keys.count { g ->
                VOWEL_GROUP_KEYWORDS.any { kw -> g.contains(kw) }
            } >= (byWordGrp.size + 1) / 2
            val namedAsTone = byWordGrp.keys.count { g ->
                TONE_GROUP_KEYWORDS.any { kw -> g.contains(kw) }
            } >= (byWordGrp.size + 1) / 2
            val avgBarkSpread = byWordGrp.values.filter { it.size >= 2 }.map { list ->
                val b1m = list.map { PhoneticScaleUtils.hzToBark(it.traj.nucleusF1.toFloat()) }.average()
                val b2m = list.map { PhoneticScaleUtils.hzToBark(it.traj.nucleusF2.toFloat()) }.average()
                sqrt(list.map {
                    val d1 = PhoneticScaleUtils.hzToBark(it.traj.nucleusF1.toFloat()) - b1m
                    val d2 = PhoneticScaleUtils.hzToBark(it.traj.nucleusF2.toFloat()) - b2m
                    d1 * d1 + d2 * d2
                }.average())
            }.average().let { if (it.isNaN()) 99.0 else it }

            namedAsVowel || (!namedAsTone && avgBarkSpread <= 1.75)
        } else {
            false
        }

        // Apply any user-specified Word/Syllable or Rime-Group VowelGlideType overrides using real measured anchors
        val resolvedEntries = entries.map { e ->
            val candidateGroupName = if (useWordGroupsForVowels) {
                e.word.groupName.trim().ifEmpty { S.acoustics.defaultRimeGroupFallback }
            } else {
                assignArticulatoryVowelGroup(e.traj).first
            }
            val forcedType = VowelGlideOverrideStore.resolveEffectiveOverride(
                context = context,
                projectId = projectId,
                wordId = e.word.id,
                syllableIndex = e.syllableIndex,
                groupKeyOrName = candidateGroupName
            )
            if (forcedType != null) {
                e.copy(traj = VowelGlideOverrideStore.applyOverrideToTrajectory(e.traj, forcedType))
            } else {
                e
            }
        }

        // Compute speaker intrinsic F1/F2 mean & SD for Lobanov Z-score normalization
        val f1Pool = ArrayList<Float>(resolvedEntries.size * 2)
        val f2Pool = ArrayList<Float>(resolvedEntries.size * 2)
        for (e in resolvedEntries) {
            if (e.isNeutralSyllable && resolvedEntries.size >= 4) continue
            f1Pool.add(e.traj.nucleusF1.toFloat())
            f2Pool.add(e.traj.nucleusF2.toFloat())
            if (e.traj.vowelType != VowelGlideType.MONOPHTHONG) {
                f1Pool.add(e.traj.onsetF1.toFloat())
                f1Pool.add(e.traj.targetF1.toFloat())
                f2Pool.add(e.traj.onsetF2.toFloat())
                f2Pool.add(e.traj.targetF2.toFloat())
            }
        }
        if (f1Pool.isEmpty()) {
            for (e in resolvedEntries) {
                f1Pool.add(e.traj.nucleusF1.toFloat())
                f2Pool.add(e.traj.nucleusF2.toFloat())
            }
        }

        val meanF1 = f1Pool.average().toFloat()
        val meanF2 = f2Pool.average().toFloat()
        val rawSdF1 = sqrt(f1Pool.map { (it - meanF1) * (it - meanF1) }.average()).toFloat()
        val rawSdF2 = sqrt(f2Pool.map { (it - meanF2) * (it - meanF2) }.average()).toFloat()

        val lobanovValid = resolvedEntries.size >= 6 && rawSdF1 >= 45f && rawSdF2 >= 140f
        val sdF1 = if (lobanovValid) rawSdF1 else maxOf(rawSdF1, 130f)
        val sdF2 = if (lobanovValid) rawSdF2 else maxOf(rawSdF2, 420f)

        fun toZ1(f1: Int): Float = ((f1 - meanF1) / sdF1).coerceIn(-2.8f, 2.8f)
        fun toZ2(f2: Int): Float = ((f2 - meanF2) / sdF2).coerceIn(-2.8f, 2.8f)

        val normalizedTokens = resolvedEntries.map { e ->
            val t = e.traj
            val (grpKey, grpLabel) = if (useWordGroupsForVowels) {
                val g = e.word.groupName.trim().ifEmpty { S.acoustics.defaultRimeGroupFallback }
                "wg_$g" to g
            } else {
                assignArticulatoryVowelGroup(t)
            }

            val anchorsHz = if (t.anchors.isNotEmpty()) {
                t.anchors.map { it.f1Hz to it.f2Hz }
            } else {
                listOf(t.onsetF1 to t.onsetF2, t.nucleusF1 to t.nucleusF2, t.targetF1 to t.targetF2)
            }
            val anchorsLob = anchorsHz.map { (f1, f2) -> toZ1(f1) to toZ2(f2) }
            val rawSylLbl = t.syllableLabel.ifBlank { e.word.text.take(2) }
            val dispLbl = if (e.wordSyllableCount > 1) {
                "${rawSylLbl.take(2)}${toSubscriptIndex(e.syllableIndex + 1)}"
            } else {
                e.word.text.take(2)
            }

            NormalizedVowelToken(
                word = e.word,
                speakerId = speakerId,
                speakerName = speakerName,
                groupKey = grpKey,
                groupLabel = grpLabel,
                displayLabel = dispLbl,
                vowelType = t.vowelType,
                onsetF1Hz = t.onsetF1,
                onsetF2Hz = t.onsetF2,
                nucleusF1Hz = t.nucleusF1,
                nucleusF2Hz = t.nucleusF2,
                nucleusF3Hz = t.nucleusF3,
                targetF1Hz = t.targetF1,
                targetF2Hz = t.targetF2,
                barkGlideSpan = t.barkGlideSpan,
                trajectorySummary = t.trajectorySummary,
                anchorsHz = anchorsHz,
                z1Onset = toZ1(t.onsetF1),
                z2Onset = toZ2(t.onsetF2),
                z1Nucleus = toZ1(t.nucleusF1),
                z2Nucleus = toZ2(t.nucleusF2),
                z1Target = toZ1(t.targetF1),
                z2Target = toZ2(t.targetF2),
                anchorsLobanov = anchorsLob,
                syllableIndex = e.syllableIndex,
                wordSyllableCount = e.wordSyllableCount,
                syllableLabel = rawSylLbl
            )
        }

        // Fit 1-sigma confidence ellipses primarily for non-neutral steady monophthong clusters (or custom rime groups)
        val nonNeutralIds = resolvedEntries.filter { !it.isNeutralSyllable }
            .map { "${it.word.id}_${it.syllableIndex}" }
            .toHashSet()
        val ellipsePool = normalizedTokens.filter { "${it.word.id}_${it.syllableIndex}" in nonNeutralIds }
            .ifEmpty { normalizedTokens }
        val ellipseSource = if (useWordGroupsForVowels) {
            ellipsePool.groupBy { it.groupKey }
        } else {
            ellipsePool.filter { it.vowelType == VowelGlideType.MONOPHTHONG }.groupBy { it.groupKey }
        }

        val ellipses = ellipseSource.entries.mapNotNull { (gKey, list) ->
            if (list.isEmpty()) return@mapNotNull null
            val gLabel = list.first().groupLabel
            val cF1 = list.map { it.nucleusF1Hz }.average().toFloat()
            val cF2 = list.map { it.nucleusF2Hz }.average().toFloat()

            val barkPts = list.map {
                // X = -Bark(F2) oriented left-front, Y = Bark(F1) oriented top-close
                PhoneticScaleUtils.hzToBark(it.nucleusF2Hz.toFloat()) to
                    PhoneticScaleUtils.hzToBark(it.nucleusF1Hz.toFloat())
            }
            val (cB2, cB1) = barkPts.map { it.first }.average().toFloat() to barkPts.map { it.second }.average().toFloat()
            val (majBark, minBark, angBark) = compute2DEllipseAxes(barkPts, cB2, cB1, minAxis = 0.22f, maxAxis = 1.65f)

            val lobPts = list.map { it.z2Nucleus to it.z1Nucleus }
            val (cZ2, cZ1) = lobPts.map { it.first }.average().toFloat() to lobPts.map { it.second }.average().toFloat()
            val (majLob, minLob, angLob) = compute2DEllipseAxes(lobPts, cZ2, cZ1, minAxis = 0.16f, maxAxis = 1.15f)

            // Representative word closest to centroid in Bark space
            val repWord = list.minByOrNull { tok ->
                val d1 = PhoneticScaleUtils.hzToBark(tok.nucleusF1Hz.toFloat()) - cB1
                val d2 = PhoneticScaleUtils.hzToBark(tok.nucleusF2Hz.toFloat()) - cB2
                d1 * d1 + d2 * d2
            }?.word?.text?.take(2) ?: list.first().displayLabel

            VowelConfidenceEllipse(
                groupKey = gKey,
                groupLabel = gLabel,
                speakerId = speakerId,
                speakerName = speakerName,
                sampleCount = list.size,
                representativeWord = repWord,
                centerF1Hz = cF1,
                centerF2Hz = cF2,
                centerBarkF1 = cB1,
                centerBarkF2 = cB2,
                semiMajorBark = majBark,
                semiMinorBark = minBark,
                angleRadBark = angBark,
                centerLobanovZ1 = cZ1,
                centerLobanovZ2 = cZ2,
                semiMajorLobanov = majLob,
                semiMinorLobanov = minLob,
                angleRadLobanov = angLob
            )
        }.sortedByDescending { it.sampleCount }

        return Triple(normalizedTokens, ellipses, lobanovValid)
    }

    private fun assignArticulatoryVowelGroup(traj: SyllableFormantTrajectory): Pair<String, String> {
        if (traj.vowelType == VowelGlideType.TRIPHTHONG) {
            return "auto_tri" to S.acoustics.vowelAutoTri
        }
        if (traj.vowelType == VowelGlideType.DIPHTHONG) {
            val endFront = traj.targetF2 >= traj.onsetF2 + 180
            val endHigh = traj.targetF1 <= traj.onsetF1 - 90
            return when {
                endHigh && endFront -> "auto_diph_front_up" to S.acoustics.vowelAutoDiphFrontUp
                endHigh && !endFront -> "auto_diph_back_up" to S.acoustics.vowelAutoDiphBackUp
                traj.onsetF1 < 420 && traj.onsetF2 >= 1750 -> "auto_diph_ia" to S.acoustics.vowelAutoDiphFrontRising
                traj.onsetF1 < 440 && traj.onsetF2 <= 1180 -> "auto_diph_ua" to S.acoustics.vowelAutoDiphBackRising
                else -> "auto_diph_other" to S.acoustics.vowelAutoDiphOther
            }
        }
        val f1 = traj.nucleusF1
        val f2 = traj.nucleusF2
        return when {
            f1 < 410 && f2 >= 1780 -> "auto_mono_i" to S.acoustics.vowelAutoMonoI
            f1 < 430 && f2 <= 1160 -> "auto_mono_u" to S.acoustics.vowelAutoMonoU
            f1 < 430 -> "auto_mono_high_mid" to S.acoustics.vowelAutoMonoHighMid
            f1 in 410..635 && f2 >= 1720 -> "auto_mono_e" to S.acoustics.vowelAutoMonoE
            f1 in 430..645 && f2 <= 1160 -> "auto_mono_o" to S.acoustics.vowelAutoMonoO
            f1 <= 645 -> "auto_mono_schwa" to S.acoustics.vowelAutoMonoSchwa
            else -> "auto_mono_a" to S.acoustics.vowelAutoMonoA
        }
    }

    /**
     * Exact 2x2 symmetric covariance eigendecomposition in (x, y) data coordinates.
     * Note: x = F2 (or Z2), y = F1 (or Z1); caller transforms orientation when rendering on screen.
     */
    private fun compute2DEllipseAxes(
        pts: List<Pair<Float, Float>>,
        cx: Float,
        cy: Float,
        minAxis: Float,
        maxAxis: Float
    ): Triple<Float, Float, Float> {
        if (pts.size < 2) {
            return Triple(minAxis * 1.25f, minAxis, 0f)
        }
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

        // 1.18x corresponds to ~1-sigma 2D coverage halo
        val major = (sqrt(lambda1).toFloat() * 1.18f).coerceIn(minAxis, maxAxis)
        val minor = (sqrt(lambda2).toFloat() * 1.18f).coerceIn(minAxis * 0.85f, major)
        val angle = (0.5 * atan2(2.0 * vxy, diff)).toFloat()
        return Triple(major, minor, if (angle.isFinite()) angle else 0f)
    }
}
