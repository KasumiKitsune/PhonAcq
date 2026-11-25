package com.example.textgrid.ops

import com.example.audio.AcousticAnalyzer
import com.example.data.Word
import com.example.i18n.S
import com.example.textgrid.model.TextGridDocument
import com.example.textgrid.model.TextGridInterval
import com.example.textgrid.model.TextGridPoint
import com.example.textgrid.model.TextGridTier
import com.example.utils.TextGridFormatConfig

/**
 * Pure read-only interop builders for converting PhonAcq's auto-segmentation tiers
 * and live settings sample into [TextGridDocument] instances.
 */
object TextGridOps {

    /**
     * Converts PhonAcq's auto-generated [TextGridFormatConfig.BuiltTier] list into a [TextGridDocument].
     */
    fun fromBuiltTiers(
        name: String,
        xmin: Double = 0.0,
        xmax: Double,
        builtTiers: List<TextGridFormatConfig.BuiltTier>
    ): TextGridDocument {
        val safeXmin = if (xmin.isFinite()) xmin else 0.0
        val safeXmax = if (xmax.isFinite() && xmax > safeXmin) xmax else safeXmin + 0.5
        val tiers = builtTiers.map { bt ->
            val ivs = if (bt.intervals.isEmpty()) {
                listOf(TextGridInterval(safeXmin, safeXmax, ""))
            } else {
                bt.intervals.map { (s, e, t) -> TextGridInterval(s, e, t) }
            }
            TextGridTier.IntervalTier(
                name = bt.name,
                xmin = safeXmin,
                xmax = safeXmax,
                intervals = ivs
            )
        }
        return TextGridDocument(
            name = name,
            xmin = safeXmin,
            xmax = safeXmax,
            tiers = tiers
        )
    }

    /**
     * Builds the realistic `"中国"` (`0.8400s`) sample [TextGridDocument] directly from [config]
     * for the Settings dual-mode live preview card.
     */
    fun buildSampleDocumentFromFormatConfig(
        config: TextGridFormatConfig,
        includeDemoTextTier: Boolean = false
    ): TextGridDocument {
        val sampleWord = Word(
            id = 1L,
            projectId = 1L,
            text = S.textGrid.sampleWordText,
            remark = "[tʂʊŋ55 kuo35] (central country)",
            speakerNote = "",
            groupName = S.textGrid.sampleWordGroup,
            originalIndex = 0,
            isRecorded = true,
            durationMs = 840L
        )
        val sampleIntervals = listOf(
            Triple(0.0850, 0.4210, S.textGrid.sampleSyllable1),
            Triple(0.4210, 0.7680, S.textGrid.sampleSyllable2)
        )
        val sampleAnalysis = AcousticAnalyzer.AnalysisResult(
            isValidVoiced = true,
            intensityDbfs = -14f,
            peakDbfs = -3f,
            snrDb = 28f,
            jitterPercent = 0.4f,
            shimmerPercent = 2.0f,
            pitchMean = 186f,
            pitchMin = 152f,
            pitchMax = 224f,
            pitchRange = "152 - 224 Hz",
            isPitchCalibrated = true,
            chaoToneNumbers = "55-35",
            toneCode = S.textGrid.sampleToneCode,
            toneDesc = S.textGrid.sampleToneDesc,
            toneTrajectory = "55 -> 35",
            contourPoints = listOf(0.82f, 0.82f, 0.48f, 0.86f),
            rawVoicedPitches = listOf(186f),
            speakerF1 = listOf(420, 510),
            speakerF2 = listOf(1350, 980),
            segmentCount = 2,
            segments = listOf(
                AcousticAnalyzer.SegmentInterval(0.0850, 0.4210, S.textGrid.sampleSyllable1),
                AcousticAnalyzer.SegmentInterval(0.4210, 0.7680, S.textGrid.sampleSyllable2)
            ),
            creakyBreaks = 0,
            voicedDurationMs = 683,
            totalDurationMs = 840,
            syllableToneNumbers = listOf("55", "35"),
            syllableCheckedFlags = listOf(false, false),
            syllableNeutralFlags = listOf(false, false)
        )
        val builtTiers = TextGridFormatConfig.buildConfiguredTiers(
            config = config,
            word = sampleWord,
            analysis = sampleAnalysis,
            domainXmax = 0.8400,
            speechStartRaw = 0.0850,
            speechEndRaw = 0.7680,
            rawCharIntervals = sampleIntervals,
            includeAcousticTags = false,
            quantizeDecimals = config.effectiveTimeDecimals()
        )
        val baseDoc = fromBuiltTiers(
            name = sampleWord.text,
            xmin = 0.0,
            xmax = 0.8400,
            builtTiers = builtTiers
        )
        if (!includeDemoTextTier) return baseDoc

        val demoTextTier = TextGridTier.TextTier(
            name = "events",
            xmin = 0.0,
            xmax = 0.8400,
            points = listOf(
                TextGridPoint(0.2530, "H*"),
                TextGridPoint(0.6120, "LH")
            )
        )
        return baseDoc.copy(tiers = baseDoc.tiers + demoTextTier)
    }
}
