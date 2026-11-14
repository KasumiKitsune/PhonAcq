package com.example.audio

import androidx.compose.runtime.Immutable
import com.example.data.Speaker
import com.example.data.Word
import com.example.i18n.S
import kotlin.math.*

enum class VowelGlideType(val csvCode: String) {
    MONOPHTHONG("monophthong"),
    DIPHTHONG("diphthong"),
    TRIPHTHONG("triphthong");

    val chineseLabel: String
        get() = when (this) {
            MONOPHTHONG -> S.acoustics.vowelTypeMonophthongLabel
            DIPHTHONG -> S.acoustics.vowelTypeDiphthongLabel
            TRIPHTHONG -> S.acoustics.vowelTypeTriphthongLabel
        }
}

@Immutable
data class FormantAnchorFrame(
    val normPos: Float,       // 0.0f..1.0f within syllable
    val timeSec: Float,       // Absolute time in WAV file (seconds)
    val f1Hz: Int,
    val f2Hz: Int,
    val f3Hz: Int,
    val confidence: Float     // 0.0f..1.0f harmonic/energy confidence
)

@Immutable
data class SyllableFormantTrajectory(
    val syllableIndex: Int,
    val syllableLabel: String,
    val vowelType: VowelGlideType,
    val anchors: List<FormantAnchorFrame>,
    val onsetF1: Int,
    val onsetF2: Int,
    val nucleusF1: Int,
    val nucleusF2: Int,
    val nucleusF3: Int,
    val targetF1: Int,
    val targetF2: Int,
    val barkGlideSpan: Float,
    val trajectoryAngleDeg: Float,
    val trajectorySummary: String
)

@Immutable
data class WidebandSpectrogramData(
    val durationSec: Float,
    val windowStartSec: Float,
    val windowEndSec: Float,
    val coreStartSec: Float,
    val coreEndSec: Float,
    val maxFreqHz: Int,
    val timeCols: Int,
    val freqBins: Int,
    val normalizedDbGrid: FloatArray, // Row-major: [col * freqBins + bin], 0f..1f
    val pitchTrackTimeSec: FloatArray,
    val pitchTrackHz: FloatArray,
    val formantTrackPoints: List<FormantAnchorFrame>
)

@Immutable
data class CanonicalToneMember(
    val word: Word,
    val rawChao: String,
    val rawToneCode: String,
    val canonicalChao: String,
    val canonicalCategoryName: String,
    val contourPoints: List<Float>,
    val meanF0Hz: Float,
    val voicedDurationMs: Int,
    val isCheckedTone: Boolean,
    val deviationSigma: Float,
    val isOutlier: Boolean
)

@Immutable
data class CanonicalToneCluster(
    val clusterId: String,
    val categoryName: String,
    val canonicalChao: String,
    val toneShapeLabel: String,
    val isCheckedCategory: Boolean,
    val sampleCount: Int,
    val meanF0Hz: Float,
    val meanDurationMs: Int,
    val relativeDurationRatio: Float, // 0.35f..1.0f horizontal span on tone plot
    val meanContour: List<Float>,
    val upperBand1Sigma: List<Float>,
    val lowerBand1Sigma: List<Float>,
    val exampleWords: List<String>,
    val outlierCount: Int,
    val members: List<CanonicalToneMember>
)

@Immutable
data class VowelConfidenceEllipse(
    val groupKey: String,
    val groupLabel: String,
    val speakerId: Long,
    val speakerName: String,
    val sampleCount: Int,
    val representativeWord: String,
    val centerF1Hz: Float,
    val centerF2Hz: Float,
    val centerBarkF1: Float,
    val centerBarkF2: Float,
    val semiMajorBark: Float,
    val semiMinorBark: Float,
    val angleRadBark: Float,
    val centerLobanovZ1: Float,
    val centerLobanovZ2: Float,
    val semiMajorLobanov: Float,
    val semiMinorLobanov: Float,
    val angleRadLobanov: Float
)

@Immutable
data class NormalizedVowelToken(
    val word: Word,
    val speakerId: Long,
    val speakerName: String,
    val groupKey: String,
    val groupLabel: String,
    val displayLabel: String,
    val vowelType: VowelGlideType,
    val onsetF1Hz: Int,
    val onsetF2Hz: Int,
    val nucleusF1Hz: Int,
    val nucleusF2Hz: Int,
    val nucleusF3Hz: Int,
    val targetF1Hz: Int,
    val targetF2Hz: Int,
    val barkGlideSpan: Float,
    val trajectorySummary: String,
    val anchorsHz: List<Pair<Int, Int>>,
    val z1Onset: Float,
    val z2Onset: Float,
    val z1Nucleus: Float,
    val z2Nucleus: Float,
    val z1Target: Float,
    val z2Target: Float,
    val anchorsLobanov: List<Pair<Float, Float>>,
    val syllableIndex: Int = 0,
    val wordSyllableCount: Int = 1,
    val syllableLabel: String = ""
)

@Immutable
data class PolysyllabicTonePatternMember(
    val word: Word,
    val patternChao: String,
    val syllableCount: Int,
    val syllableLabels: List<String>,
    val syllableTones: List<String>,
    val syllableNeutralFlags: List<Boolean>,
    val syllableCheckedFlags: List<Boolean>,
    val contourPoints: List<Float>,
    val meanF0Hz: Float,
    val voicedDurationMs: Int
)

@Immutable
data class PolysyllabicTonePatternGroup(
    val patternKey: String,
    val patternChao: String,
    val syllableCount: Int,
    val patternLabel: String,
    val sampleCount: Int,
    val meanSyllableContours: List<List<Float>>,
    val syllableNeutralFlags: List<Boolean>,
    val syllableCheckedFlags: List<Boolean>,
    val exampleWords: List<String>,
    val members: List<PolysyllabicTonePatternMember>
)

@Immutable
data class SpeakerCorpusPhonologyReport(
    val speakerId: Long,
    val speakerName: String,
    val analyzedCount: Int,
    val medianVoicedDurationMs: Int,
    val calibratedPitchRangeHz: Pair<Float, Float>?,
    val hasCustomWordGroups: Boolean,
    val mergedToneClusters: List<CanonicalToneCluster>,
    val pureAcousticToneClusters: List<CanonicalToneCluster>,
    val wordIdToCanonicalMember: Map<Long, CanonicalToneMember>,
    val vowelTokens: List<NormalizedVowelToken>,
    val vowelEllipses: List<VowelConfidenceEllipse>,
    val lobanovValid: Boolean,
    val polysyllabicTonePatterns: List<PolysyllabicTonePatternGroup> = emptyList()
)

@Immutable
data class MultiSpeakerVowelComparisonReport(
    val speakerReports: List<SpeakerCorpusPhonologyReport>,
    val allNormalizedTokens: List<NormalizedVowelToken>,
    val speakerEllipses: List<VowelConfidenceEllipse>,
    val speakersById: Map<Long, Speaker>
)

object PhoneticScaleUtils {
    /**
     * Traunmüller (1990) critical-band rate transformation from Hz to Bark.
     */
    fun hzToBark(freqHz: Float): Float {
        val f = freqHz.coerceIn(50f, 8000f)
        return ((26.81f * f) / (1960f + f) - 0.53f).coerceAtLeast(0.1f)
    }

    fun barkToHz(bark: Float): Float {
        val z = (bark + 0.53f).coerceIn(0.6f, 24.0f)
        return (1960f * z / (26.81f - z)).coerceIn(80f, 6000f)
    }

    /**
     * Maps a Lobanov Z-score pair (Z1, Z2) into equivalent reference vocal-tract Hz
     * (centered at F1=500Hz SD=145Hz, F2=1520Hz SD=460Hz) when needed for dual-axis reference.
     */
    fun lobanovZ1ToEquivHz(z1: Float): Float = (500f + z1 * 145f).coerceIn(200f, 980f)
    fun lobanovZ2ToEquivHz(z2: Float): Float = (1520f + z2 * 460f).coerceIn(580f, 2700f)

    /**
     * Describes articulatory vowel region in concise Chinese for trajectory summaries.
     */
    fun describeArticulatoryRegion(f1Hz: Int, f2Hz: Int): String {
        val height = when {
            f1Hz < 375 -> S.acoustics.heightHigh
            f1Hz < 525 -> S.acoustics.heightSemiHigh
            f1Hz < 675 -> S.acoustics.heightMidLow
            else -> S.acoustics.heightLow
        }
        val backness = when {
            f2Hz >= 1820 -> S.acoustics.backnessFront
            f2Hz >= 1260 -> S.acoustics.backnessCentral
            else -> S.acoustics.backnessBack
        }
        return "$backness$height"
    }
}
