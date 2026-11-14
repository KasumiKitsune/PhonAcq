package com.example.utils

import com.example.audio.AcousticAnalyzer
import com.example.audio.VowelGlideType
import com.example.data.Word
import com.example.i18n.S
import com.example.i18n.withArgs
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Built-in Praat TextGrid annotation presets aligned with mainstream international
 * corpus phonetics (MFA / ProsodyPro / PraatSauce) and Chinese dialectology fieldwork.
 */
enum class TextGridPreset(
    val id: String
) {
    UNIVERSAL_STANDARD("UNIVERSAL_STANDARD"),
    MFA_WORDS_PHONES("MFA_WORDS_PHONES"),
    CHINESE_DIALECT("CHINESE_DIALECT"),
    PROSODY_SYLLABLES("PROSODY_SYLLABLES"),
    PHONTRACER_COMPAT("PHONTRACER_COMPAT"),
    CUSTOM("CUSTOM");

    val title: String
        get() = when (this) {
            UNIVERSAL_STANDARD -> S.textGrid.presetUniversalTitle
            MFA_WORDS_PHONES -> S.textGrid.presetMfaTitle
            CHINESE_DIALECT -> S.textGrid.presetDialectTitle
            PROSODY_SYLLABLES -> S.textGrid.presetProsodyTitle
            PHONTRACER_COMPAT -> S.textGrid.presetPhonTracerTitle
            CUSTOM -> S.textGrid.presetCustomTitle
        }

    val badge: String
        get() = when (this) {
            UNIVERSAL_STANDARD -> S.textGrid.presetUniversalBadge
            MFA_WORDS_PHONES -> S.textGrid.presetMfaBadge
            CHINESE_DIALECT -> S.textGrid.presetDialectBadge
            PROSODY_SYLLABLES -> S.textGrid.presetProsodyBadge
            PHONTRACER_COMPAT -> S.textGrid.presetPhonTracerBadge
            CUSTOM -> S.textGrid.presetCustomBadge
        }

    val description: String
        get() = when (this) {
            UNIVERSAL_STANDARD -> S.textGrid.presetUniversalDesc
            MFA_WORDS_PHONES -> S.textGrid.presetMfaDesc
            CHINESE_DIALECT -> S.textGrid.presetDialectDesc
            PROSODY_SYLLABLES -> S.textGrid.presetProsodyDesc
            PHONTRACER_COMPAT -> S.textGrid.presetPhonTracerDesc
            CUSTOM -> S.textGrid.presetCustomDesc
        }

    companion object {
        fun fromId(id: String?): TextGridPreset {
            if (id.isNullOrBlank()) return UNIVERSAL_STANDARD
            return entries.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) } ?: UNIVERSAL_STANDARD
        }
    }
}

/**
 * User-configurable Praat TextGrid generation settings persisted in SharedPreferences
 * and applied uniformly across `.zip`, `.Collection`, and single-word `.TextGrid` exports.
 */
data class TextGridFormatConfig(
    val preset: TextGridPreset = TextGridPreset.UNIVERSAL_STANDARD,
    val shortTextFormat: Boolean = false,
    val silenceMarker: String = "",
    val timeDecimals: Int = 6,
    val includeGroupsTier: Boolean = false,
    val groupsTierName: String = "groups",
    val includeWordsTier: Boolean = true,
    val wordsTierName: String = "words",
    val includeSyllablesTier: Boolean = true,
    val syllablesTierName: String = "syllables",
    val includeIpaTier: Boolean = false,
    val ipaTierName: String = "ipa",
    val includeTonesTier: Boolean = true,
    val tonesTierName: String = "tones",
    val includeSegmentsTier: Boolean = false,
    val segmentsTierName: String = "segments",
    val ipaFallbackToSyllable: Boolean = false
) {
    data class BuiltTier(
        val name: String,
        val intervals: List<Triple<Double, Double, String>>
    )

    fun sanitizedTierName(raw: String, fallback: String): String {
        val cleaned = raw.replace('\n', ' ').replace('\r', ' ').trim()
        return cleaned.ifEmpty { fallback }
    }

    fun sanitizedSilenceMarker(): String {
        return silenceMarker.replace('\n', ' ').replace('\r', ' ')
    }

    fun effectiveTimeDecimals(): Int {
        return if (timeDecimals == 4) 4 else 6
    }

    fun activeTierNames(): List<String> {
        val list = mutableListOf<String>()
        if (includeGroupsTier) list.add(sanitizedTierName(groupsTierName, "groups"))
        if (includeWordsTier) list.add(sanitizedTierName(wordsTierName, "words"))
        if (includeSyllablesTier) list.add(sanitizedTierName(syllablesTierName, "syllables"))
        if (includeIpaTier) list.add(sanitizedTierName(ipaTierName, "ipa"))
        if (includeTonesTier) list.add(sanitizedTierName(tonesTierName, "tones"))
        if (includeSegmentsTier) list.add(sanitizedTierName(segmentsTierName, "segments"))
        if (list.isEmpty()) {
            list.add(sanitizedTierName(wordsTierName, "words"))
        }
        return list
    }

    fun shortSummaryLabel(): String {
        val tiers = activeTierNames()
        return S.textGrid.tierCountSummary.withArgs(
            "count" to tiers.size,
            "names" to tiers.joinToString(" / ")
        )
    }

    fun formatModeSummary(): String {
        val syntax = if (shortTextFormat) S.textGrid.syntaxCompactShort else S.textGrid.syntaxStandardLong
        return "${preset.title} · ${shortSummaryLabel()} · $syntax"
    }

    fun toJsonString(): String {
        fun esc(s: String): String = buildString(s.length + 4) {
            append('"')
            for (ch in s) {
                when (ch) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
            append('"')
        }
        return buildString {
            append('{')
            append("\"preset\":").append(esc(preset.id)).append(',')
            append("\"shortTextFormat\":").append(shortTextFormat).append(',')
            append("\"silenceMarker\":").append(esc(silenceMarker)).append(',')
            append("\"timeDecimals\":").append(effectiveTimeDecimals()).append(',')
            append("\"includeGroupsTier\":").append(includeGroupsTier).append(',')
            append("\"groupsTierName\":").append(esc(groupsTierName)).append(',')
            append("\"includeWordsTier\":").append(includeWordsTier).append(',')
            append("\"wordsTierName\":").append(esc(wordsTierName)).append(',')
            append("\"includeSyllablesTier\":").append(includeSyllablesTier).append(',')
            append("\"syllablesTierName\":").append(esc(syllablesTierName)).append(',')
            append("\"includeIpaTier\":").append(includeIpaTier).append(',')
            append("\"ipaTierName\":").append(esc(ipaTierName)).append(',')
            append("\"includeTonesTier\":").append(includeTonesTier).append(',')
            append("\"tonesTierName\":").append(esc(tonesTierName)).append(',')
            append("\"includeSegmentsTier\":").append(includeSegmentsTier).append(',')
            append("\"segmentsTierName\":").append(esc(segmentsTierName)).append(',')
            append("\"ipaFallbackToSyllable\":").append(ipaFallbackToSyllable)
            append('}')
        }
    }

    companion object {
        private val WHITESPACE_SPLIT = Regex("\\s+")
        private val TRAILING_TONE_DIGITS = Regex("([1-5]{1,3})\\s*$")

        fun fromPreset(preset: TextGridPreset): TextGridFormatConfig {
            return when (preset) {
                TextGridPreset.UNIVERSAL_STANDARD -> TextGridFormatConfig(
                    preset = TextGridPreset.UNIVERSAL_STANDARD,
                    shortTextFormat = false,
                    silenceMarker = "",
                    timeDecimals = 6,
                    includeGroupsTier = false,
                    groupsTierName = "groups",
                    includeWordsTier = true,
                    wordsTierName = "words",
                    includeSyllablesTier = true,
                    syllablesTierName = "syllables",
                    includeIpaTier = false,
                    ipaTierName = "ipa",
                    includeTonesTier = true,
                    tonesTierName = "tones",
                    includeSegmentsTier = false,
                    segmentsTierName = "segments",
                    ipaFallbackToSyllable = false
                )

                TextGridPreset.MFA_WORDS_PHONES -> TextGridFormatConfig(
                    preset = TextGridPreset.MFA_WORDS_PHONES,
                    shortTextFormat = false,
                    silenceMarker = "",
                    timeDecimals = 6,
                    includeGroupsTier = false,
                    groupsTierName = "groups",
                    includeWordsTier = true,
                    wordsTierName = "words",
                    includeSyllablesTier = false,
                    syllablesTierName = "syllables",
                    includeIpaTier = true,
                    ipaTierName = "phones",
                    includeTonesTier = false,
                    tonesTierName = "tones",
                    includeSegmentsTier = false,
                    segmentsTierName = "segments",
                    ipaFallbackToSyllable = true
                )

                TextGridPreset.CHINESE_DIALECT -> TextGridFormatConfig(
                    preset = TextGridPreset.CHINESE_DIALECT,
                    shortTextFormat = false,
                    silenceMarker = "",
                    timeDecimals = 6,
                    includeGroupsTier = false,
                    groupsTierName = "groups",
                    includeWordsTier = true,
                    wordsTierName = "words",
                    includeSyllablesTier = true,
                    syllablesTierName = "syllables",
                    includeIpaTier = true,
                    ipaTierName = "ipa",
                    includeTonesTier = true,
                    tonesTierName = "tones",
                    includeSegmentsTier = false,
                    segmentsTierName = "segments",
                    ipaFallbackToSyllable = false
                )

                TextGridPreset.PROSODY_SYLLABLES -> TextGridFormatConfig(
                    preset = TextGridPreset.PROSODY_SYLLABLES,
                    shortTextFormat = false,
                    silenceMarker = "",
                    timeDecimals = 6,
                    includeGroupsTier = false,
                    groupsTierName = "groups",
                    includeWordsTier = false,
                    wordsTierName = "words",
                    includeSyllablesTier = true,
                    syllablesTierName = "syllables",
                    includeIpaTier = false,
                    ipaTierName = "ipa",
                    includeTonesTier = false,
                    tonesTierName = "tones",
                    includeSegmentsTier = false,
                    segmentsTierName = "segments",
                    ipaFallbackToSyllable = false
                )

                TextGridPreset.PHONTRACER_COMPAT -> TextGridFormatConfig(
                    preset = TextGridPreset.PHONTRACER_COMPAT,
                    shortTextFormat = false,
                    silenceMarker = "",
                    timeDecimals = 4,
                    includeGroupsTier = true,
                    groupsTierName = "groups",
                    includeWordsTier = true,
                    wordsTierName = "words",
                    includeSyllablesTier = true,
                    syllablesTierName = "chars",
                    includeIpaTier = false,
                    ipaTierName = "ipa",
                    includeTonesTier = false,
                    tonesTierName = "tones",
                    includeSegmentsTier = true,
                    segmentsTierName = "segments",
                    ipaFallbackToSyllable = false
                )

                TextGridPreset.CUSTOM -> TextGridFormatConfig(preset = TextGridPreset.CUSTOM)
            }
        }

        private fun parseFlatJsonMap(raw: String): Map<String, String> {
            val map = mutableMapOf<String, String>()
            var i = 0
            val n = raw.length
            fun skipWs() {
                while (i < n && raw[i].isWhitespace()) i++
            }
            fun readQuoted(): String {
                if (i >= n || raw[i] != '"') return ""
                i++ // skip opening quote
                val sb = StringBuilder()
                while (i < n) {
                    val ch = raw[i++]
                    if (ch == '"') break
                    if (ch == '\\' && i < n) {
                        when (val esc = raw[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> if (i + 4 <= n) {
                                raw.substring(i, i + 4).toIntOrNull(16)?.let { sb.append(it.toChar()) }
                                i += 4
                            }
                            else -> sb.append(esc)
                        }
                    } else {
                        sb.append(ch)
                    }
                }
                return sb.toString()
            }
            skipWs()
            if (i < n && raw[i] == '{') i++
            while (i < n) {
                skipWs()
                if (i >= n || raw[i] == '}') break
                if (raw[i] == ',') {
                    i++
                    continue
                }
                if (raw[i] != '"') {
                    i++
                    continue
                }
                val key = readQuoted()
                skipWs()
                if (i < n && raw[i] == ':') i++
                skipWs()
                if (i >= n) break
                val value = if (raw[i] == '"') {
                    readQuoted()
                } else {
                    val start = i
                    while (i < n && raw[i] != ',' && raw[i] != '}' && !raw[i].isWhitespace()) i++
                    raw.substring(start, i)
                }
                map[key] = value
            }
            return map
        }

        private fun unwrapOuterPhoneticDelimiters(raw: String): String {
            val s = raw.trim()
            return when {
                s.length >= 2 && s.startsWith("[") && s.endsWith("]") && !s.substring(1, s.length - 1).contains(']') ->
                    s.substring(1, s.length - 1).trim()
                s.length >= 2 && s.startsWith("/") && s.endsWith("/") && !s.substring(1, s.length - 1).contains('/') ->
                    s.substring(1, s.length - 1).trim()
                else -> s
            }
        }

        fun fromJsonString(raw: String?): TextGridFormatConfig {
            if (raw.isNullOrBlank()) return TextGridFormatConfig()
            return try {
                val map = parseFlatJsonMap(raw)
                val preset = TextGridPreset.fromId(map["preset"] ?: TextGridPreset.UNIVERSAL_STANDARD.id)
                val base = if (preset == TextGridPreset.CUSTOM) {
                    TextGridFormatConfig(preset = TextGridPreset.CUSTOM)
                } else {
                    fromPreset(preset)
                }
                fun bool(key: String, def: Boolean): Boolean =
                    map[key]?.lowercase(Locale.US)?.toBooleanStrictOrNull() ?: def
                fun str(key: String, def: String): String = map[key] ?: def
                val dec = map["timeDecimals"]?.toIntOrNull()?.let { if (it == 4) 4 else 6 } ?: base.timeDecimals

                base.copy(
                    preset = preset,
                    shortTextFormat = bool("shortTextFormat", base.shortTextFormat),
                    silenceMarker = str("silenceMarker", base.silenceMarker),
                    timeDecimals = dec,
                    includeGroupsTier = bool("includeGroupsTier", base.includeGroupsTier),
                    groupsTierName = str("groupsTierName", base.groupsTierName),
                    includeWordsTier = bool("includeWordsTier", base.includeWordsTier),
                    wordsTierName = str("wordsTierName", base.wordsTierName),
                    includeSyllablesTier = bool("includeSyllablesTier", base.includeSyllablesTier),
                    syllablesTierName = str("syllablesTierName", base.syllablesTierName),
                    includeIpaTier = bool("includeIpaTier", base.includeIpaTier),
                    ipaTierName = str("ipaTierName", base.ipaTierName),
                    includeTonesTier = bool("includeTonesTier", base.includeTonesTier),
                    tonesTierName = str("tonesTierName", base.tonesTierName),
                    includeSegmentsTier = bool("includeSegmentsTier", base.includeSegmentsTier),
                    segmentsTierName = str("segmentsTierName", base.segmentsTierName),
                    ipaFallbackToSyllable = bool("ipaFallbackToSyllable", base.ipaFallbackToSyllable)
                )
            } catch (_: Exception) {
                TextGridFormatConfig()
            }
        }

        /**
         * Builds the ordered list of Praat IntervalTiers for a word and its acoustic segmentation.
         * When `quantizeDecimals` is non-null (for standalone `.TextGrid` files), timestamps are rounded
         * to that decimal precision before enforcing strict interval continuity.
         * When `quantizeDecimals` is null (for binary/text `.Collection` objects), full IEEE-754 double
         * domain precision (`0.0 .. exactDomainXmax`) is preserved to match `Sound 2` sample bounds.
         */
        fun buildConfiguredTiers(
            config: TextGridFormatConfig,
            word: Word,
            analysis: AcousticAnalyzer.AnalysisResult?,
            domainXmax: Double,
            speechStartRaw: Double,
            speechEndRaw: Double,
            rawCharIntervals: List<Triple<Double, Double, String>>,
            includeAcousticTags: Boolean = false,
            quantizeDecimals: Int? = null
        ): List<BuiltTier> {
            val safeXmax = if (quantizeDecimals != null) {
                roundToDecimals(domainXmax.coerceAtLeast(0.01), quantizeDecimals).coerceAtLeast(
                    if (quantizeDecimals == 4) 0.0010 else 0.000100
                )
            } else {
                if (domainXmax.isFinite() && domainXmax > 0.002) domainXmax else 0.5
            }
            val minStep = when (quantizeDecimals) {
                4 -> 0.0010
                6 -> 0.000100
                else -> 1e-6
            }

            fun quantize(v: Double): Double {
                return if (quantizeDecimals != null) roundToDecimals(v, quantizeDecimals) else v
            }

            val sStart = quantize(speechStartRaw.coerceIn(0.0, safeXmax)).let {
                if (it < minStep || safeXmax - it < minStep * 2) 0.0 else it
            }
            val sEnd = quantize(speechEndRaw.coerceIn(sStart, safeXmax)).let {
                if (safeXmax - it < minStep || it <= sStart + minStep) safeXmax else it
            }

            val wordLabel = word.text.trim().ifEmpty { "token" }
            val groupLabel = word.groupName.trim().ifEmpty { S.common.defaultGroupFallback }
            val sil = config.sanitizedSilenceMarker()

            // Align syllable intervals strictly inside [sStart, sEnd]
            val alignedSyllables: List<Triple<Double, Double, String>> = if (rawCharIntervals.size <= 1) {
                val lbl = rawCharIntervals.firstOrNull()?.third?.ifBlank { wordLabel } ?: wordLabel
                listOf(Triple(sStart, sEnd, lbl))
            } else {
                val list = mutableListOf<Triple<Double, Double, String>>()
                var cursor = sStart
                rawCharIntervals.forEachIndexed { idx, (_, rawE, lbl) ->
                    val end = if (idx == rawCharIntervals.lastIndex) {
                        sEnd
                    } else {
                        val maxAllowed = (sEnd - minStep * (rawCharIntervals.lastIndex - idx)).coerceAtLeast(cursor + minStep)
                        quantize(rawE).coerceIn((cursor + minStep).coerceAtMost(maxAllowed), maxAllowed)
                    }
                    if (end > cursor) {
                        list.add(Triple(cursor, end, lbl))
                        cursor = end
                    }
                }
                if (list.isEmpty()) {
                    listOf(Triple(sStart, sEnd, wordLabel))
                } else {
                    val last = list.last()
                    if (last.second != sEnd) {
                        list[list.lastIndex] = Triple(last.first, sEnd, last.third)
                    }
                    list
                }
            }

            fun enforceContinuity(raw: List<Triple<Double, Double, String>>): List<Triple<Double, Double, String>> {
                if (raw.isEmpty()) return listOf(Triple(0.0, safeXmax, sil))
                val cleaned = mutableListOf<Triple<Double, Double, String>>()
                var cursor = 0.0
                for (i in raw.indices) {
                    val end = if (i == raw.lastIndex) safeXmax else quantize(raw[i].second.coerceIn(cursor, safeXmax))
                    if (end - cursor >= minStep) {
                        cleaned.add(Triple(cursor, end, raw[i].third))
                        cursor = end
                    }
                }
                if (cleaned.isEmpty()) {
                    return listOf(Triple(0.0, safeXmax, raw.first().third))
                }
                val last = cleaned.last()
                if (last.second != safeXmax) {
                    cleaned[cleaned.lastIndex] = Triple(last.first, safeXmax, last.third)
                }
                return cleaned
            }

            fun buildSingleSpan(label: String): List<Triple<Double, Double, String>> {
                val list = mutableListOf<Triple<Double, Double, String>>()
                if (sStart > 0.0) list.add(Triple(0.0, sStart, sil))
                val safeSpanEnd = if (sEnd <= sStart) safeXmax else sEnd
                list.add(Triple(sStart, safeSpanEnd, label))
                if (safeSpanEnd < safeXmax) list.add(Triple(safeSpanEnd, safeXmax, sil))
                return enforceContinuity(list)
            }

            fun wrapSyllableIntervals(items: List<Triple<Double, Double, String>>): List<Triple<Double, Double, String>> {
                val list = mutableListOf<Triple<Double, Double, String>>()
                if (sStart > 0.0) list.add(Triple(0.0, sStart, sil))
                list.addAll(items)
                if (sEnd < safeXmax) list.add(Triple(sEnd, safeXmax, sil))
                return enforceContinuity(list)
            }

            fun formatSyllableAcousticSuffix(idx: Int, includeToneInTag: Boolean): String {
                if (!includeAcousticTags || analysis == null) return ""
                val f1 = analysis.speakerF1.getOrNull(idx) ?: 0
                val f2 = analysis.speakerF2.getOrNull(idx) ?: 0
                val traj = analysis.formantTrajectories.getOrNull(idx)
                val toneStr = resolveSyllableToneLabel(analysis, idx, alignedSyllables.size, legacyChineseSuffix = false)
                return buildString {
                    val parts = mutableListOf<String>()
                    if (includeToneInTag && toneStr.isNotEmpty()) {
                        parts.add("T:$toneStr")
                    }
                    if (traj != null && traj.vowelType != VowelGlideType.MONOPHTHONG && traj.onsetF1 > 0 && traj.targetF1 > 0) {
                        parts.add("F1:${traj.onsetF1}->${traj.targetF1}")
                        parts.add("F2:${traj.onsetF2}->${traj.targetF2}")
                    } else if (f1 > 0 && f2 > 0) {
                        parts.add("F1:$f1")
                        parts.add("F2:$f2")
                    }
                    if (parts.isNotEmpty()) {
                        append("[")
                        append(parts.joinToString(" "))
                        append("]")
                    }
                }
            }

            // Determine which tier carries optional acoustic tags when requested
            val acousticTagCarrier = when {
                !includeAcousticTags -> "NONE"
                config.includeSegmentsTier -> "SEGMENTS"
                config.includeTonesTier -> "TONES"
                config.includeIpaTier -> "IPA"
                config.includeSyllablesTier -> "SYLLABLES"
                else -> "WORDS"
            }

            val hasAtLeastOneEnabled = config.includeGroupsTier ||
                config.includeWordsTier ||
                config.includeSyllablesTier ||
                config.includeIpaTier ||
                config.includeTonesTier ||
                config.includeSegmentsTier

            val tiers = mutableListOf<BuiltTier>()

            if (config.includeGroupsTier) {
                tiers.add(
                    BuiltTier(
                        name = config.sanitizedTierName(config.groupsTierName, "groups"),
                        intervals = buildSingleSpan(groupLabel)
                    )
                )
            }

            if (config.includeWordsTier || !hasAtLeastOneEnabled) {
                val wordWithOptTag = if (acousticTagCarrier == "WORDS") {
                    val suffix = formatSyllableAcousticSuffix(0, includeToneInTag = true)
                    if (suffix.isNotEmpty()) "$wordLabel $suffix" else wordLabel
                } else {
                    wordLabel
                }
                tiers.add(
                    BuiltTier(
                        name = config.sanitizedTierName(config.wordsTierName, "words"),
                        intervals = buildSingleSpan(wordWithOptTag)
                    )
                )
            }

            if (config.includeSyllablesTier) {
                val sylItems = alignedSyllables.mapIndexed { idx, (s, e, lbl) ->
                    val finalLbl = if (acousticTagCarrier == "SYLLABLES") {
                        val suffix = formatSyllableAcousticSuffix(idx, includeToneInTag = true)
                        if (suffix.isNotEmpty()) "$lbl $suffix" else lbl
                    } else {
                        lbl
                    }
                    Triple(s, e, finalLbl)
                }
                tiers.add(
                    BuiltTier(
                        name = config.sanitizedTierName(config.syllablesTierName, "syllables"),
                        intervals = wrapSyllableIntervals(sylItems)
                    )
                )
            }

            if (config.includeIpaTier) {
                val cleanNote = extractCleanNoteOrIpa(word)
                val unwrappedNote = unwrapOuterPhoneticDelimiters(cleanNote)
                val noteTokens = if (unwrappedNote.isNotEmpty()) {
                    unwrappedNote.split(WHITESPACE_SPLIT).filter { it.isNotEmpty() }
                } else {
                    emptyList()
                }

                val ipaIntervals = if (cleanNote.isNotEmpty() && alignedSyllables.size > 1 && noteTokens.size != alignedSyllables.size) {
                    // Whole-word note that cannot be split 1-to-1 per syllable: preserve cleanly across speech span
                    val wholeNote = if (acousticTagCarrier == "IPA") {
                        val suffix = formatSyllableAcousticSuffix(0, includeToneInTag = true)
                        if (suffix.isNotEmpty()) "$unwrappedNote $suffix" else unwrappedNote
                    } else {
                        unwrappedNote
                    }
                    buildSingleSpan(wholeNote)
                } else {
                    val perSylIpa = alignedSyllables.mapIndexed { idx, (s, e, sylLbl) ->
                        val baseToken = when {
                            alignedSyllables.size == 1 && unwrappedNote.isNotEmpty() -> unwrappedNote
                            noteTokens.size == alignedSyllables.size -> unwrapOuterPhoneticDelimiters(noteTokens[idx])
                            config.ipaFallbackToSyllable -> sylLbl
                            else -> ""
                        }
                        val withTag = if (acousticTagCarrier == "IPA") {
                            val suffix = formatSyllableAcousticSuffix(idx, includeToneInTag = true)
                            when {
                                suffix.isEmpty() -> baseToken
                                baseToken.isEmpty() -> suffix
                                else -> "$baseToken $suffix"
                            }
                        } else {
                            baseToken
                        }
                        Triple(s, e, withTag)
                    }
                    wrapSyllableIntervals(perSylIpa)
                }

                tiers.add(
                    BuiltTier(
                        name = config.sanitizedTierName(config.ipaTierName, "ipa"),
                        intervals = ipaIntervals
                    )
                )
            }

            if (config.includeTonesTier) {
                val cleanNote = unwrapOuterPhoneticDelimiters(extractCleanNoteOrIpa(word))
                val noteTokens = if (cleanNote.isNotEmpty()) {
                    cleanNote.split(WHITESPACE_SPLIT).filter { it.isNotEmpty() }
                } else {
                    emptyList()
                }
                val toneItems = alignedSyllables.mapIndexed { idx, (s, e, _) ->
                    val measuredTone = resolveSyllableToneLabel(
                        analysis = analysis,
                        idx = idx,
                        syllableCount = alignedSyllables.size,
                        legacyChineseSuffix = false
                    )
                    val fallbackNoteTone = if (measuredTone.isEmpty()) {
                        val tok = when {
                            alignedSyllables.size == 1 -> cleanNote.trim('[', ']', '/')
                            noteTokens.size == alignedSyllables.size -> noteTokens[idx]
                            else -> ""
                        }
                        TRAILING_TONE_DIGITS.find(tok)?.groupValues?.getOrNull(1) ?: ""
                    } else {
                        measuredTone
                    }
                    val finalTone = if (acousticTagCarrier == "TONES") {
                        val suffix = formatSyllableAcousticSuffix(idx, includeToneInTag = false)
                        when {
                            suffix.isEmpty() -> fallbackNoteTone
                            fallbackNoteTone.isEmpty() -> suffix
                            else -> "$fallbackNoteTone $suffix"
                        }
                    } else {
                        fallbackNoteTone
                    }
                    Triple(s, e, finalTone)
                }
                tiers.add(
                    BuiltTier(
                        name = config.sanitizedTierName(config.tonesTierName, "tones"),
                        intervals = wrapSyllableIntervals(toneItems)
                    )
                )
            }

            if (config.includeSegmentsTier) {
                val rawNoteText = word.speakerNote.trim().ifEmpty { word.remark.trim() }
                val noteTokens = if (alignedSyllables.size > 1 && rawNoteText.isNotEmpty()) {
                    rawNoteText.trim('[', ']', '/').split(WHITESPACE_SPLIT).filter { it.isNotEmpty() }
                } else {
                    emptyList()
                }
                val segItems = alignedSyllables.mapIndexed { idx, (s, e, charLbl) ->
                    val f1 = analysis?.speakerF1?.getOrNull(idx) ?: 0
                    val f2 = analysis?.speakerF2?.getOrNull(idx) ?: 0
                    val traj = analysis?.formantTrajectories?.getOrNull(idx)
                    val toneStr = resolveSyllableToneLabel(
                        analysis = analysis,
                        idx = idx,
                        syllableCount = alignedSyllables.size,
                        legacyChineseSuffix = true
                    ).ifEmpty { "--" }
                    val segLabel = buildString {
                        append(charLbl)
                        if (alignedSyllables.size == 1 && rawNoteText.isNotEmpty()) {
                            append(" ($rawNoteText)")
                        } else if (noteTokens.size == alignedSyllables.size) {
                            append(" (${noteTokens[idx]})")
                        } else if (rawNoteText.isNotEmpty() && idx == 0) {
                            append(" ($rawNoteText)")
                        }
                        if (includeAcousticTags) {
                            if (traj != null && traj.vowelType != VowelGlideType.MONOPHTHONG && traj.onsetF1 > 0 && traj.targetF1 > 0) {
                                append(" [T:$toneStr F1:${traj.onsetF1}->${traj.targetF1} F2:${traj.onsetF2}->${traj.targetF2}]")
                            } else if (f1 > 0 && f2 > 0) {
                                append(" [T:$toneStr F1:$f1 F2:$f2]")
                            } else if (analysis != null && analysis.isValidVoiced && toneStr != "--") {
                                append(" [T:$toneStr]")
                            }
                        }
                    }
                    Triple(s, e, segLabel)
                }
                tiers.add(
                    BuiltTier(
                        name = config.sanitizedTierName(config.segmentsTierName, "segments"),
                        intervals = wrapSyllableIntervals(segItems)
                    )
                )
            }

            return tiers
        }

        /**
         * Renders a complete standalone Praat `.TextGrid` UTF-8 text file (without BOM) in either
         * standard Long `ooTextFile` syntax or compact Short `ooTextFile` syntax.
         */
        fun renderPraatTextGridText(
            config: TextGridFormatConfig,
            totalSecRaw: Double,
            tiers: List<BuiltTier>
        ): String {
            val decimals = config.effectiveTimeDecimals()
            val fmt = "%.${decimals}f"
            val safeTotalSec = roundToDecimals(totalSecRaw.coerceAtLeast(0.01), decimals)
                .coerceAtLeast(if (decimals == 4) 0.0010 else 0.000100)
            val zeroStr = String.format(Locale.US, fmt, 0.0)
            val maxStr = String.format(Locale.US, fmt, safeTotalSec)

            val sb = StringBuilder()
            sb.append("File type = \"ooTextFile\"\n")
            sb.append("Object class = \"TextGrid\"\n\n")

            if (config.shortTextFormat) {
                sb.append("0\n")
                sb.append(maxStr).append('\n')
                sb.append("<exists>\n")
                sb.append(tiers.size).append('\n')
                for (tier in tiers) {
                    val escapedName = tier.name.replace("\"", "\"\"")
                    sb.append("\"IntervalTier\"\n")
                    sb.append("\"$escapedName\"\n")
                    sb.append("0\n")
                    sb.append(maxStr).append('\n')
                    sb.append(tier.intervals.size).append('\n')
                    for ((idx, iv) in tier.intervals.withIndex()) {
                        val sStr = if (idx == 0) "0" else String.format(Locale.US, fmt, iv.first)
                        val eStr = if (idx == tier.intervals.lastIndex) maxStr else String.format(Locale.US, fmt, iv.second)
                        val escapedText = iv.third.replace("\"", "\"\"")
                        sb.append(sStr).append('\n')
                        sb.append(eStr).append('\n')
                        sb.append("\"$escapedText\"\n")
                    }
                }
            } else {
                sb.append("xmin = $zeroStr\n")
                sb.append("xmax = $maxStr\n")
                sb.append("tiers? <exists>\n")
                sb.append("size = ${tiers.size}\n")
                sb.append("item []:\n")
                tiers.forEachIndexed { tIdx, tier ->
                    val escapedName = tier.name.replace("\"", "\"\"")
                    sb.append("    item [${tIdx + 1}]:\n")
                    sb.append("        class = \"IntervalTier\"\n")
                    sb.append("        name = \"$escapedName\"\n")
                    sb.append("        xmin = $zeroStr\n")
                    sb.append("        xmax = $maxStr\n")
                    sb.append("        intervals: size = ${tier.intervals.size}\n")
                    tier.intervals.forEachIndexed { iIdx, iv ->
                        val sStr = if (iIdx == 0) zeroStr else String.format(Locale.US, fmt, iv.first)
                        val eStr = if (iIdx == tier.intervals.lastIndex) maxStr else String.format(Locale.US, fmt, iv.second)
                        val escapedText = iv.third.replace("\"", "\"\"")
                        sb.append("        intervals [${iIdx + 1}]:\n")
                        sb.append("            xmin = $sStr\n")
                        sb.append("            xmax = $eStr\n")
                        sb.append("            text = \"$escapedText\"\n")
                    }
                }
            }
            return sb.toString()
        }

        /**
         * Builds a realistic live preview `.TextGrid` snippet for the Settings UI.
         */
        fun buildLiveSamplePreview(config: TextGridFormatConfig): String {
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
            val tiers = buildConfiguredTiers(
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
            return renderPraatTextGridText(
                config = config,
                totalSecRaw = 0.8400,
                tiers = tiers
            )
        }

        private fun extractCleanNoteOrIpa(word: Word): String {
            val spkNote = word.speakerNote.trim()
            if (spkNote.isNotEmpty()) {
                return spkNote
            }
            val remark = word.remark.trim()
            if (remark.isEmpty()) return ""
            val parsed = ZipUtility.parseWordRemarkMetadata(remark, word.groupName)
            if (parsed.ipa.isNotBlank()) return parsed.ipa.trim()
            return remark
                .substringBefore('|')
                .substringBefore('｜')
                .substringBefore('(')
                .substringBefore('（')
                .substringBefore('#')
                .trim()
        }

        private fun resolveSyllableToneLabel(
            analysis: AcousticAnalyzer.AnalysisResult?,
            idx: Int,
            syllableCount: Int,
            legacyChineseSuffix: Boolean
        ): String {
            if (analysis == null || !analysis.isValidVoiced) return ""
            val rawTone = analysis.syllableToneNumbers.getOrNull(idx)
                ?: (if (syllableCount == 1) analysis.chaoToneNumbers else null)
                ?: return ""
            if (rawTone.isBlank() || rawTone == "--") return ""
            val isChecked = analysis.syllableCheckedFlags.getOrNull(idx)
                ?: (analysis.isCheckedTone && syllableCount == 1)
            val isNeutral = analysis.syllableNeutralFlags.getOrNull(idx) ?: false
            return if (legacyChineseSuffix) {
                when {
                    isNeutral && !rawTone.endsWith("轻") -> "${rawTone}轻"
                    isChecked && !rawTone.endsWith("促") -> "${rawTone}促"
                    else -> rawTone
                }
            } else {
                rawTone.removeSuffix("促").removeSuffix("轻")
            }
        }

        fun roundToDecimals(value: Double, decimals: Int): Double {
            if (!value.isFinite()) return 0.0
            val factor = 10.0.pow(decimals.coerceIn(1, 8))
            return (value * factor).roundToLong() / factor
        }
    }
}
