package com.example.textgrid.layout

import java.util.Collections
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Semantic token types for lossless TextGrid string micro-typography (VIZ §4.4).
 */
enum class RichTokenKind {
    TEXT,
    TAB,
    SPACE_META,
    CTRL,
    NEWLINE,
    WRAP
}

data class RichToken(
    val kind: RichTokenKind,
    val str: String
)

data class RichLine(
    val tokens: List<RichToken>,
    val width: Float
)

data class RichTextLayout(
    val lines: List<RichLine>,
    val width: Float,
    val height: Float,
    val lineHeight: Float,
    val gloss: String?,
    val glossHeight: Float,
    val glossIsRaw: Boolean
)

enum class TrigraphDisplayMode {
    AUTO,
    IPA_ONLY,
    DUAL,
    RAW_ONLY
}

data class RichLayoutOptions(
    val hideMetaSymbols: Boolean = false,
    val trigraphMode: TrigraphDisplayMode = TrigraphDisplayMode.AUTO,
    val fontScale: Float = 1f
)

/**
 * Font style categories matching Dual-Scale Score elements so layout measurement
 * matches final Compose Canvas text rendering.
 */
enum class ScoreFontRole {
    INTERVAL_TEXT,
    POINT_TEXT,
    TIER_NAME,
    TICK_LABEL,
    GLOSS_IPA,
    GLOSS_RAW,
    CTRL_TAG
}

/**
 * Platform-independent text width measurer interface.
 * Unit tests can inject [HeuristicTextWidthMeasurer]; Compose UI injects a `TextMeasurer`-backed implementation.
 */
fun interface TextWidthMeasurer {
    fun measurePx(text: String, role: ScoreFontRole, fontScale: Float): Float
}

/**
 * Deterministic fallback measurer for pure JVM unit tests and non-Compose contexts.
 */
object HeuristicTextWidthMeasurer : TextWidthMeasurer {
    override fun measurePx(text: String, role: ScoreFontRole, fontScale: Float): Float {
        if (text.isEmpty()) return 0f
        val safeScale = TextGridSafeMath.safeCoerceIn(fontScale, 0.5f, 3f, 1f)
        val baseCharPx = when (role) {
            ScoreFontRole.INTERVAL_TEXT -> 7.4f
            ScoreFontRole.POINT_TEXT -> 6.8f
            ScoreFontRole.TIER_NAME -> 7.0f
            ScoreFontRole.TICK_LABEL -> 6.1f
            ScoreFontRole.GLOSS_IPA -> 5.6f
            ScoreFontRole.GLOSS_RAW -> 5.4f
            ScoreFontRole.CTRL_TAG -> 5.7f
        } * safeScale

        var total = 0f
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val charCount = Character.charCount(cp)
            val type = Character.getType(cp)
            total += when {
                type == Character.NON_SPACING_MARK.toInt() || type == Character.ENCLOSING_MARK.toInt() -> 0f
                cp == ' '.code -> baseCharPx * 0.45f
                cp in 0x4E00..0x9FFF || cp in 0x3400..0x4DBF || cp in 0x3000..0x30FF || cp in 0xAC00..0xD7AF ->
                    baseCharPx * 1.65f
                cp in 0x20..0x7E -> {
                    when (cp.toChar()) {
                        'i', 'l', 'I', '!', '.', ',', ':', ';', '|' -> baseCharPx * 0.55f
                        'm', 'w', 'M', 'W' -> baseCharPx * 1.3f
                        else -> baseCharPx
                    }
                }
                else -> baseCharPx * 1.05f
            }
            i += charCount
        }
        return total
    }
}

/**
 * Thread-safe bounded LRU cache wrapping any [TextWidthMeasurer].
 */
class CachedTextWidthMeasurer(
    private val delegate: TextWidthMeasurer,
    private val maxEntries: Int = 2048
) : TextWidthMeasurer {
    private data class Key(val text: String, val role: ScoreFontRole, val scaleBucket: Int)

    private val cache: MutableMap<Key, Float> = Collections.synchronizedMap(
        object : LinkedHashMap<Key, Float>(maxEntries.coerceAtLeast(64) + 16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Float>?): Boolean {
                return size > maxEntries
            }
        }
    )

    override fun measurePx(text: String, role: ScoreFontRole, fontScale: Float): Float {
        if (text.isEmpty()) return 0f
        val safeScale = TextGridSafeMath.safeCoerceIn(fontScale, 0.5f, 3f, 1f)
        val key = Key(text, role, TextGridSafeMath.safeRoundToInt(safeScale * 100f, 100))
        cache[key]?.let { return it }
        val measured = TextGridSafeMath.safeCoerceIn(delegate.measurePx(text, role, safeScale), 0f, 8192f, 0f)
        cache[key] = measured
        return measured
    }

    fun clear() {
        cache.clear()
    }
}

/**
 * Praat backslash trigraph decoder (408 entries from Praat Longchar_database).
 * Used to produce non-destructive IPA glosses above/below raw Praat strings (VIZ §4.4).
 */
object PraatTrigraphDecoder {
    private const val PRAAT_AL0_PACK =
        "!d¡?d¿-h\u00AD--–+-±bs\\'a'\"l“\"r”<<«>>»a`àa'áa^âa~ãa\"äaoåa;ąc'ćc<čd<ďd-đe`èe'ée^êe\"ëe<ěe;ęg<ǧi`ìi'íi^îi\"ïl/łn'ńn~ñn<ňo`òo'óo^ôo~õo\"öo:őr<řs'śs<št<ťu`ùu'úu^ûu\"üu:űuoůy'ýy\"ÿz'źz<žz!żA`ÀA'ÁA^ÂA~ÃA\"ÄAoÅA;ĄC'ĆC<ČD<ĎD-ĐE`ÈE'ÉE^ÊE\"ËE<ĚE;ĘG<ǦI`ÌI'ÍI^ÎI\"ÏL/ŁN'ŃN~ÑN<ŇO`ÒO'ÓO^ÔO~ÕO\"ÖO:ŐR<ŘS'ŚS<ŠT<ŤU`ÙU'ÚU^ÛU\"ÜU:ŰUoŮY'ÝY\"ŸZ'ŹZ<ŽZ!Żssßaeæo/øc,çs,şt,ţthþAeÆO/ØC,ÇS,ŞT,ŢThÞ.c·dg°c/¢Lp£cu¤eu€Y=¥SS§||¶co©re®a_ªo_ºFIﬁFLﬂ"

    private const val PRAAT_AL1_PACK =
        "alαbeβgaγdeδepεzeζetηteθt2ϑioιkaκlaλmuμnuνxiξonοpiπroρsiσs2ςtaτupυfiφf2ϕciχpsψomωo2ϖAlΑBeΒGaΓDeΔEpΕZeΖEtΗTeΘIoΙKaΚLaΛMuΜNuΝXiΞOnΟPiΠRoΡSiΣTaΤUpΥFiΦCiΧPsΨOmΩtm™TM™no¬xx×:-÷fdƒbu•'p′\"p″At∀dd∂Er∃O|∅e=∈su∑-m−/d∕Vr√oc∝oo∞an∧or∨ni∩uu∪in∫.3∴=~≅~~≈un_=/≠=3≡<_≤>_≥c=⊂o+⊕ox⊗Tt⊥..⋯cl♣di♦he♥sp♠<-←^|↑->→_|↓<>↔<=⇐^#⇑=>⇒_#⇓eq⇔"

    private const val PRAAT_AL2_PACK =
        "dhðh-ħoeœatɐasɑayɒabɒb^ɓ[f[]f]bcʙctɔccɕd.ɖd^ɗe-ɘswəsrɚefɛerɜkbɞj-ɟg^ɠgsɡgcɢgfɣrhɤG^ʛhtɥh^ɦhjɧhcʜi-ɨicɪjcʝl~ɫl-ɬl.ɭlzɮlcʟmtɯmlɰmjɱngŋnjɲn.ɳncɴo-ɵOeɶ|1ǀ|2ǁ|-ǂ|f|ffɸrtɹrlɺr.ɻf.ɽfhɾrcʀriʁs.ʂshʃj^ʄt.ʈu-ʉhsʊvsʋvtʌwtʍytʎycʏz.ʐzcʑzhʒ?gʔ9eʕO.ʘ?-ʡ9-ʢV^ⱱ:fː.fˑhr˞`^\u0300``\u030F'^\u0301''\u030B^^\u0302~^\u0303//\u0338-/\u0336~/\u0334-^\u0304:^\u03080^\u030Av^\u030CN^\u0306cn\u031Acv\u031CT^\u031DTv\u031ET(\u0318T)\u0319+v\u031F:v\u03240v\u0325vv\u032C|v\u0329Nv\u032AUv\u033ADv\u033Bnv\u032F~v\u0330-v\u0320.v\u0323~<\u03343v\u0339li\u0361LI\u035Cmv\u033Cwv\u032B-'\u1DC4'-\u1DC7`-\u1DC5-`\u1DC6rf\u1DC8fr\u1DC9bfβtfθcfχ'1ˈ'2ˌ^hʰ^Hʱ^jʲ^wʷ^Yᶣ^yʸ^?ˀ^9ˁ^lˡ^sˢ^gˠ^Mᵚ^Gᶭ^xˣ^fᶠ^nⁿ^mᵐ^Nᵑidɿirʅ_u‿-5˥-4˦-3˧-2˨-1˩|uꜛ|dꜜNE↗SE↘NW↖SW↙tsʦtSʧdzʣdZʤapʼx^\u033D"

    private val table: Map<String, Char> by lazy {
        val map = HashMap<String, Char>(432)
        fun unpack(pack: String) {
            var i = 0
            while (i + 2 < pack.length) {
                val key = pack.substring(i, i + 2)
                map[key] = pack[i + 2]
                i += 3
            }
        }
        // Unpack in order so phonetic AL2 entries take precedence where codes collide
        unpack(PRAAT_AL1_PACK)
        unpack(PRAAT_AL0_PACK)
        unpack(PRAAT_AL2_PACK)
        map
    }

    private val decodeCache: MutableMap<String, String> = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(512, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 512
        }
    )

    private const val NO_CHANGE_SENTINEL = "\u0000__NO_CHANGE__"

    /**
     * Decodes valid Praat `\xx` trigraphs in [input].
     * Returns the decoded IPA string if at least one valid trigraph was found, or `null` otherwise.
     */
    fun decodeGlossOrNull(input: String): String? {
        if (input.length < 3 || !input.contains('\\')) return null
        decodeCache[input]?.let { cached ->
            return if (cached == NO_CHANGE_SENTINEL) null else cached
        }
        val sb = StringBuilder(input.length)
        var changed = false
        var i = 0
        val n = input.length
        while (i < n) {
            if (input[i] == '\\' && i + 2 < n) {
                val code = input.substring(i + 1, i + 3)
                val mapped = table[code]
                if (mapped != null) {
                    sb.append(mapped)
                    changed = true
                    i += 3
                    continue
                }
            }
            sb.append(input[i])
            i++
        }
        val result = if (changed) sb.toString() else NO_CHANGE_SENTINEL
        decodeCache[input] = result
        return if (changed) result else null
    }
}

object TextGridRichText {

    fun formatPraatDouble(value: Double): String {
        if (!value.isFinite()) return "--undefined--"
        if (value == 0.0) return "0"
        val raw = value.toString()
        return if (raw.endsWith(".0")) raw.dropLast(2) else raw
    }

    fun formatDisplayTick(
        value: Double,
        precisionDigits: Int?,
        fixedDecimals: Boolean = false
    ): String {
        if (!value.isFinite()) return "--undefined--"
        if (precisionDigits == null) return formatPraatDouble(value)
        val digits = precisionDigits.coerceIn(0, 6)
        val clean = if (abs(value) < 1e-12) 0.0 else value
        if (fixedDecimals) {
            val formatted = String.format(Locale.US, "%.${digits}f", clean)
            return if (formatted == "-0" || formatted.matches(Regex("^-0\\.0+$"))) {
                String.format(Locale.US, "%.${digits}f", 0.0)
            } else {
                formatted
            }
        }
        if (clean == 0.0) return "0"
        val formatted = String.format(Locale.US, "%.${digits}f", clean)
        val stripped = if (formatted.contains('.')) {
            formatted.trimEnd('0').trimEnd('.')
        } else {
            formatted
        }
        return if (stripped == "-0" || stripped.isEmpty()) "0" else stripped
    }

    /**
     * Builds adaptive tick strings for a sorted list of timestamps.
     * Locally expands colliding adjacent pairs up to 6 decimals until distinct (VIZ §4.1).
     */
    fun buildDisplayTickStrings(
        spineT: DoubleArray,
        precisionDigits: Int?,
        fixedDecimals: Boolean = false
    ): Array<String> {
        if (precisionDigits == null) {
            return Array(spineT.size) { formatPraatDouble(spineT[it]) }
        }
        val baseDigits = precisionDigits.coerceIn(0, 6)
        val labels = Array(spineT.size) { formatDisplayTick(spineT[it], baseDigits, fixedDecimals) }
        for (d in (baseDigits + 1)..6) {
            val colliding = HashSet<Int>()
            for (i in 1 until spineT.size) {
                if (spineT[i] != spineT[i - 1] && labels[i] == labels[i - 1]) {
                    colliding.add(i - 1)
                    colliding.add(i)
                }
            }
            if (colliding.isEmpty()) break
            for (idx in colliding) {
                labels[idx] = formatDisplayTick(spineT[idx], d, fixedDecimals)
            }
        }
        return labels
    }

    /**
     * Splits a string into user-perceived grapheme clusters, preserving combining diacritics
     * and IPA affricate tie bars (`U+0361`, `U+035C`) attached to their adjacent base glyph.
     */
    fun splitGraphemes(str: String): List<String> {
        if (str.isEmpty()) return emptyList()
        val raw = ArrayList<String>(str.length)
        var i = 0
        val n = str.length
        while (i < n) {
            val start = i
            val cp = str.codePointAt(i)
            i += Character.charCount(cp)
            while (i < n) {
                val nextCp = str.codePointAt(i)
                val cat = Character.getType(nextCp)
                val isMark = cat == Character.NON_SPACING_MARK.toInt() ||
                    cat == Character.ENCLOSING_MARK.toInt() ||
                    cat == Character.COMBINING_SPACING_MARK.toInt() ||
                    nextCp == 0xFE00 || nextCp == 0xFE0F || nextCp == 0x200D
                if (isMark) {
                    i += Character.charCount(nextCp)
                } else {
                    break
                }
            }
            raw.add(str.substring(start, i))
        }
        val merged = ArrayList<String>(raw.size)
        var idx = 0
        while (idx < raw.size) {
            var g = raw[idx]
            while ((g.endsWith('\u0361') || g.endsWith('\u035C')) && idx + 1 < raw.size) {
                idx++
                g += raw[idx]
            }
            merged.add(g)
            idx++
        }
        return merged
    }

    /**
     * Middle-ellipsis truncation for long tier names (VIZ §3.2 / §3.3), keeping distinguishing
     * prefix and suffix characters (e.g. `说话…甲`, `说话…乙`).
     */
    fun truncateMiddleEllipsis(
        name: String,
        maxWidthPx: Float,
        measurer: TextWidthMeasurer,
        role: ScoreFontRole = ScoreFontRole.TIER_NAME,
        fontScale: Float = 1f
    ): String {
        if (name.isEmpty()) return name
        val safeMaxW = TextGridSafeMath.safeAtLeast(maxWidthPx, 8f)
        val safeScale = TextGridSafeMath.safeCoerceIn(fontScale, 0.5f, 3f, 1f)
        if (TextGridSafeMath.safeAtLeast(measurer.measurePx(name, role, safeScale), 0f) <= safeMaxW) return name
        val graphemes = splitGraphemes(name)
        if (graphemes.size <= 1) return name
        var lo = 2
        var hi = graphemes.size - 1
        var best: String? = null
        while (lo <= hi) {
            val keep = (lo + hi) ushr 1
            val tailLen = max(1, keep / 2)
            val headLen = keep - tailLen
            val candidate = buildString {
                for (i in 0 until headLen) append(graphemes[i])
                append('…')
                for (i in (graphemes.size - tailLen) until graphemes.size) append(graphemes[i])
            }
            if (TextGridSafeMath.safeAtLeast(measurer.measurePx(candidate, role, safeScale), 0f) <= safeMaxW) {
                best = candidate
                lo = keep + 1
            } else {
                hi = keep - 1
            }
        }
        return best ?: "${graphemes[0]}…"
    }

    private fun isControlOrInvisible(code: Int): Boolean {
        return code < 0x20 ||
            (code in 0x7F..0x9F) ||
            code == 0x00A0 ||
            (code in 0x200B..0x200F) ||
            code == 0xFEFF
    }

    /**
     * Tokenizes a single logical line (without `\n`) into semantic lossless tokens:
     * - `\t` -> `⇥`
     * - leading/trailing/consecutive spaces -> `␣`
     * - control/invisible chars (`U+00A0`, `U+200B`, C0/C1, BOM) -> `[XXXX]`
     * - trailing real newline -> `↵`
     */
    fun tokenizeLine(
        rawLine: String,
        hasTrailingNewline: Boolean,
        hideMetaSymbols: Boolean = false
    ): List<RichToken> {
        val tokens = ArrayList<RichToken>(rawLine.length.coerceAtMost(32) + 2)
        val n = rawLine.length
        var i = 0
        while (i < n) {
            val ch = rawLine[i]
            val code = ch.code
            when {
                ch == '\t' -> {
                    tokens.add(
                        if (hideMetaSymbols) RichToken(RichTokenKind.TEXT, "  ")
                        else RichToken(RichTokenKind.TAB, "⇥")
                    )
                    i++
                }
                ch == ' ' -> {
                    var j = i
                    while (j < n && rawLine[j] == ' ') j++
                    val runLen = j - i
                    if (!hideMetaSymbols && (i == 0 || j == n || runLen > 1)) {
                        repeat(runLen) { tokens.add(RichToken(RichTokenKind.SPACE_META, "␣")) }
                    } else {
                        tokens.add(RichToken(RichTokenKind.TEXT, " ".repeat(runLen)))
                    }
                    i = j
                }
                isControlOrInvisible(code) -> {
                    if (!hideMetaSymbols) {
                        tokens.add(
                            RichToken(
                                RichTokenKind.CTRL,
                                String.format(Locale.US, "[%04X]", code)
                            )
                        )
                    }
                    i++
                }
                else -> {
                    var j = i
                    while (j < n) {
                        val c = rawLine[j]
                        if (c == ' ' || c == '\t' || isControlOrInvisible(c.code)) break
                        j++
                    }
                    tokens.add(RichToken(RichTokenKind.TEXT, rawLine.substring(i, j)))
                    i = j
                }
            }
        }
        if (hasTrailingNewline && !hideMetaSymbols) {
            tokens.add(RichToken(RichTokenKind.NEWLINE, "↵"))
        }
        return tokens
    }

    fun tokenWidthPx(
        token: RichToken,
        role: ScoreFontRole,
        fontScale: Float,
        measurer: TextWidthMeasurer
    ): Float {
        val safeScale = TextGridSafeMath.safeCoerceIn(fontScale, 0.5f, 3f, 1f)
        val raw = when (token.kind) {
            RichTokenKind.CTRL -> measurer.measurePx(token.str, ScoreFontRole.CTRL_TAG, safeScale) + 2f
            RichTokenKind.TEXT -> measurer.measurePx(token.str, role, safeScale) * 1.04f
            else -> measurer.measurePx(token.str, role, safeScale) + 2.5f
        }
        return TextGridSafeMath.safeCoerceIn(raw, 0f, 4096f, 0f)
    }

    /**
     * Performs lossless rich-text layout with soft-wrapping (`↪`), trailing-space upgrade (` ` -> `␣`)
     * with pre-reserved width delta, and optional Praat trigraph IPA glosses.
     */
    fun layoutRichText(
        str: String,
        maxWidthPx: Float,
        role: ScoreFontRole,
        measurer: TextWidthMeasurer,
        isEditMode: Boolean = false,
        options: RichLayoutOptions = RichLayoutOptions()
    ): RichTextLayout {
        val fontScale = TextGridSafeMath.safeCoerceIn(options.fontScale, 0.6f, 2.5f, 1f)
        val safeMaxWidthPx = if (maxWidthPx.isFinite()) maxWidthPx else 0f
        val lineH = TextGridSafeMath.safeRoundToInt(17f * fontScale, 17).toFloat()
        if (str.isEmpty()) {
            return RichTextLayout(
                lines = emptyList(),
                width = 0f,
                height = 0f,
                lineHeight = lineH,
                gloss = null,
                glossHeight = 0f,
                glossIsRaw = false
            )
        }

        val hideMeta = options.hideMetaSymbols
        val decoded = PraatTrigraphDecoder.decodeGlossOrNull(str)

        val primaryStr: String
        val gloss: String?
        val glossIsRaw: Boolean

        when (options.trigraphMode) {
            TrigraphDisplayMode.IPA_ONLY -> {
                primaryStr = decoded ?: str
                gloss = null
                glossIsRaw = false
            }
            TrigraphDisplayMode.RAW_ONLY -> {
                primaryStr = str
                gloss = null
                glossIsRaw = false
            }
            TrigraphDisplayMode.DUAL -> {
                glossIsRaw = decoded != null
                primaryStr = decoded ?: str
                val rawFlat = if (hideMeta) {
                    str.replace('\n', ' ').replace('\t', ' ')
                } else {
                    str.replace("\n", "↵").replace("\t", "⇥")
                }
                gloss = if (decoded != null) rawFlat else null
            }
            TrigraphDisplayMode.AUTO -> {
                glossIsRaw = decoded != null && !isEditMode
                primaryStr = if (glossIsRaw) decoded!! else str
                gloss = if (decoded != null) {
                    if (glossIsRaw) str.replace("\n", "↵").replace("\t", "⇥") else decoded
                } else {
                    null
                }
            }
        }

        val rawLines = primaryStr.split('\n')
        val visualLines = ArrayList<RichLine>(rawLines.size)

        val wrapToken = RichToken(RichTokenKind.WRAP, "↪")
        val spaceMetaToken = RichToken(RichTokenKind.SPACE_META, "␣")
        val plainSpaceToken = RichToken(RichTokenKind.TEXT, " ")
        val wrapPrefixW = if (hideMeta) 0f else tokenWidthPx(wrapToken, role, fontScale, measurer)
        val spaceUpgradeExtra = if (hideMeta) {
            0f
        } else {
            max(
                0f,
                tokenWidthPx(spaceMetaToken, role, fontScale, measurer) -
                    tokenWidthPx(plainSpaceToken, role, fontScale, measurer)
            )
        }

        for (li in rawLines.indices) {
            val hasNl = li < rawLines.lastIndex
            if (li == rawLines.lastIndex && rawLines[li].isEmpty() && rawLines.size > 1) break
            val tokens = tokenizeLine(rawLines[li], hasNl, hideMeta)
            if (safeMaxWidthPx <= 0f) {
                var lineW = 0f
                for (t in tokens) lineW += tokenWidthPx(t, role, fontScale, measurer)
                visualLines.add(RichLine(tokens, lineW))
                continue
            }

            var curTokens = ArrayList<RichToken>()
            var curW = 0f

            fun pushCurrentLine() {
                if (!hideMeta && curTokens.isNotEmpty()) {
                    val last = curTokens[curTokens.lastIndex]
                    if (last.kind == RichTokenKind.TEXT && last.str == " ") {
                        val oldW = tokenWidthPx(last, role, fontScale, measurer)
                        curTokens[curTokens.lastIndex] = spaceMetaToken
                        curW = max(0f, curW - oldW + tokenWidthPx(spaceMetaToken, role, fontScale, measurer))
                    }
                }
                visualLines.add(RichLine(curTokens, TextGridSafeMath.safeAtLeast(curW, 0f)))
            }

            for (tok in tokens) {
                val tw = tokenWidthPx(tok, role, fontScale, measurer)
                val tailExtra = if (
                    !hideMeta &&
                    curTokens.isNotEmpty() &&
                    curTokens[curTokens.lastIndex].kind == RichTokenKind.TEXT &&
                    curTokens[curTokens.lastIndex].str == " "
                ) {
                    spaceUpgradeExtra
                } else {
                    0f
                }

                if (curTokens.isNotEmpty() && curW + tw + tailExtra > safeMaxWidthPx) {
                    if (tok.kind == RichTokenKind.TEXT && tok.str.length > 1 && tw > safeMaxWidthPx - wrapPrefixW) {
                        val piece = StringBuilder()
                        for (ch in splitGraphemes(tok.str)) {
                            val cw = TextGridSafeMath.safeCoerceIn(measurer.measurePx(ch, role, fontScale) * 1.04f, 0f, 4096f, 0f)
                            if (curW + cw > safeMaxWidthPx && (curTokens.isNotEmpty() || piece.isNotEmpty())) {
                                if (piece.isNotEmpty()) {
                                    curTokens.add(RichToken(RichTokenKind.TEXT, piece.toString()))
                                    piece.setLength(0)
                                }
                                pushCurrentLine()
                                curTokens = ArrayList()
                                if (!hideMeta) curTokens.add(wrapToken)
                                curW = wrapPrefixW
                            }
                            piece.append(ch)
                            curW += cw
                        }
                        if (piece.isNotEmpty()) {
                            curTokens.add(RichToken(RichTokenKind.TEXT, piece.toString()))
                        }
                        continue
                    }
                    pushCurrentLine()
                    curTokens = ArrayList()
                    if (!hideMeta) curTokens.add(wrapToken)
                    curTokens.add(tok)
                    curW = wrapPrefixW + tw
                } else {
                    curTokens.add(tok)
                    curW += tw
                }
            }
            if (curTokens.isNotEmpty()) {
                visualLines.add(RichLine(curTokens, TextGridSafeMath.safeAtLeast(curW, 0f)))
            }
        }

        val glossH = if (gloss != null) TextGridSafeMath.safeRoundToInt(13f * fontScale, 13).toFloat() else 0f
        val glossRole = if (glossIsRaw) ScoreFontRole.GLOSS_RAW else ScoreFontRole.GLOSS_IPA
        var maxW = 0f
        for (line in visualLines) {
            if (line.width.isFinite() && line.width > maxW) maxW = line.width
        }
        if (gloss != null) {
            val gw = TextGridSafeMath.safeCoerceIn(measurer.measurePx(gloss, glossRole, fontScale), 0f, 4096f, 0f)
            if (gw > maxW) maxW = gw
        }

        return RichTextLayout(
            lines = visualLines,
            width = TextGridSafeMath.safeCeil(maxW, 0f),
            height = visualLines.size * lineH + glossH,
            lineHeight = lineH,
            gloss = gloss,
            glossHeight = glossH,
            glossIsRaw = glossIsRaw
        )
    }
}
