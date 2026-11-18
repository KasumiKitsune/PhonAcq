package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.Paragraph
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.platform.FontLoadResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import org.jetbrains.skia.paragraph.TypefaceFontProviderWithFallback
import java.util.Collections
import java.util.WeakHashMap

/**
 * Four-layer bundled studio typography & dialect-phonetics font chain for PhonAcq Desktop (Windows & macOS):
 *
 * Layer 1 — `Sarasa UI SC` (更纱黑体 UI 大陆规范版, Unhinted v1.0.43, Regular 400 + SemiBold 600):
 *   Replaces Windows' GDI-hinted Microsoft YaHei with clean, unhinted Mainland China SC glyphs,
 *   proportional compact UI punctuation, and crisp 11–14sp small-text legibility in Skia.
 *
 * Layer 2 — `PhonAcq IPA Sans` (`DejaVu Sans` Regular 400 + Bold 700):
 *   Full international phonetic alphabet, Sinological extensions (`ɿ`, `ʅ`, `ʮ`, `ʯ`, `ȶ`, `ȡ`, `ȵ`, `ᴇ`),
 *   Chao five-level tone letters (`˥`, `˦`, `˧`, `˨`, `˩`), dotted circle (`◌`), and GPOS combining marks
 *   (`◌̃`, `◌̥`, `◌̱`, `p̚`, `t͡ɕ`), preventing missing IPA symbols (`ɕ`, `ʅ`, `ᴇ`) from falling through to OS fonts.
 *
 * Layer 3 — `Plangothic P1` (遍黑体 P1 Regular, 65,440 glyphs):
 *   Complete sans-serif coverage of CJK Unified Ideographs Extension B, C, D, and E (`U+20000..U+2CEAF`),
 *   preventing Windows from substituting mismatched serif `SimSun-ExtB` on Cantonese/Min/Hakka/Wu dialect words.
 *
 * Layer 4 — `Plangothic P2` (遍黑体 P2 Regular, 42,152 glyphs):
 *   Complete sans-serif coverage of CJK Unified Ideographs Extension F, G, H, and I (`U+2CEB0..U+323AF`, `U+2EBF0..U+2EE5F`).
 */
object DesktopTypographyChain {

    const val UNIFIED_SARASA_ALIAS = "PhonAcqSarasaUI"
    const val UNIFIED_IPA_ALIAS = "PhonAcqIpaSans"
    const val UNIFIED_PLANGOTHIC_P1_ALIAS = "PhonAcqPlangothicP1"
    const val UNIFIED_PLANGOTHIC_P2_ALIAS = "PhonAcqPlangothicP2"

    val studioUiFontFamily: FontFamily by lazy {
        runCatching {
            FontFamily(
                Font(resource = "fonts/SarasaUiSC-Regular.ttf", weight = FontWeight.Light),
                Font(resource = "fonts/SarasaUiSC-Regular.ttf", weight = FontWeight.Normal),
                Font(resource = "fonts/SarasaUiSC-SemiBold.ttf", weight = FontWeight.Medium),
                Font(resource = "fonts/SarasaUiSC-SemiBold.ttf", weight = FontWeight.SemiBold),
                Font(resource = "fonts/SarasaUiSC-SemiBold.ttf", weight = FontWeight.Bold),
                Font(resource = "fonts/SarasaUiSC-SemiBold.ttf", weight = FontWeight.ExtraBold)
            )
        }.getOrDefault(FontFamily.Default)
    }

    val phoneticIpaFontFamily: FontFamily by lazy {
        runCatching {
            FontFamily(
                Font(resource = "fonts/phonacq_ipa_sans.ttf", weight = FontWeight.Light),
                Font(resource = "fonts/phonacq_ipa_sans.ttf", weight = FontWeight.Normal),
                Font(resource = "fonts/phonacq_ipa_sans.ttf", weight = FontWeight.Medium),
                Font(resource = "fonts/phonacq_ipa_sans_bold.ttf", weight = FontWeight.SemiBold),
                Font(resource = "fonts/phonacq_ipa_sans_bold.ttf", weight = FontWeight.Bold),
                Font(resource = "fonts/phonacq_ipa_sans_bold.ttf", weight = FontWeight.ExtraBold)
            )
        }.getOrDefault(FontFamily.Default)
    }

    val plangothicP1FontFamily: FontFamily by lazy {
        runCatching {
            FontFamily(
                Font(resource = "fonts/PlangothicP1-Regular.ttf", weight = FontWeight.Normal)
            )
        }.getOrDefault(FontFamily.Default)
    }

    val plangothicP2FontFamily: FontFamily by lazy {
        runCatching {
            FontFamily(
                Font(resource = "fonts/PlangothicP2-Regular.ttf", weight = FontWeight.Normal)
            )
        }.getOrDefault(FontFamily.Default)
    }

    val typography: Typography by lazy {
        buildPhonAcqTypography(studioUiFontFamily)
    }

    private val configuredResolvers = Collections.synchronizedMap(WeakHashMap<FontFamily.Resolver, Boolean>())

    @Volatile
    private var genericMappingPatched = false

    private val weightsToBind = listOf(
        FontWeight.Light,
        FontWeight.Normal,
        FontWeight.Medium,
        FontWeight.SemiBold,
        FontWeight.Bold,
        FontWeight.ExtraBold
    )

    /**
     * Deterministically binds the 4-layer font fallback chain into [resolver]'s Skia `FontCache`
     * and permanent `FontLoadResult` entries so Skia resolves bundled fonts in exact priority order
     * before querying OS DirectWrite / CoreText fallback managers.
     */
    fun installInto(resolver: FontFamily.Resolver) {
        if (configuredResolvers[resolver] == true) return
        synchronized(configuredResolvers) {
            if (configuredResolvers[resolver] == true) return

            val p1Result = resolveLoadResult(resolver, plangothicP1FontFamily, FontWeight.Normal)
            val p2Result = resolveLoadResult(resolver, plangothicP2FontFamily, FontWeight.Normal)
            val p1Aliases = p1Result?.aliases.orEmpty()
            val p2Aliases = p2Result?.aliases.orEmpty()

            val sarasaResultsByWeight = weightsToBind.associateWith { w ->
                resolveLoadResult(resolver, studioUiFontFamily, w)
            }
            val ipaResultsByWeight = weightsToBind.associateWith { w ->
                resolveLoadResult(resolver, phoneticIpaFontFamily, w)
            }

            // 1. Register unified multi-weight family aliases directly into Skia's TypefaceFontProviderWithFallback
            //    so GenericFontFamily / DefaultFontFamily (e.g., Canvas TextMeasurer) also honor weight 400 vs 600/700.
            registerUnifiedAliasesInFontProvider(
                resolver = resolver,
                sarasaRegular = sarasaResultsByWeight[FontWeight.Normal]?.typeface,
                sarasaSemiBold = sarasaResultsByWeight[FontWeight.SemiBold]?.typeface,
                ipaRegular = ipaResultsByWeight[FontWeight.Normal]?.typeface,
                ipaBold = ipaResultsByWeight[FontWeight.Bold]?.typeface,
                plangothicP1 = p1Result?.typeface,
                plangothicP2 = p2Result?.typeface
            )

            // 2. Patch permanent FontLoadResult.aliases on each weight entry in AsyncTypefaceCache:
            //    - Studio UI Typography: Sarasa UI SC (weight) -> IPA Sans (weight) -> Plangothic P1 -> Plangothic P2
            //    - Phonetic Chips / IPA: IPA Sans (weight) -> Sarasa UI SC (weight) -> Plangothic P1 -> Plangothic P2
            for (weight in weightsToBind) {
                val sarasaRes = sarasaResultsByWeight[weight]
                val ipaRes = ipaResultsByWeight[weight]
                if (sarasaRes != null) {
                    val mergedUiAliases = buildList {
                        addAll(sarasaRes.aliases)
                        if (ipaRes != null) addAll(ipaRes.aliases)
                        addAll(p1Aliases)
                        addAll(p2Aliases)
                    }.distinct()
                    setFontLoadResultAliases(sarasaRes, mergedUiAliases)
                }
                if (ipaRes != null) {
                    val mergedIpaAliases = buildList {
                        addAll(ipaRes.aliases)
                        if (sarasaRes != null) addAll(sarasaRes.aliases)
                        addAll(p1Aliases)
                        addAll(p2Aliases)
                    }.distinct()
                    setFontLoadResultAliases(ipaRes, mergedIpaAliases)
                }
            }

            // 3. Patch Compose Desktop's GenericFontFamiliesMapping so FontFamily.Default, FontFamily.SansSerif,
            //    FontFamily.Monospace, and FontFamily.Serif permanently include the 4-layer chain even across LruCache evictions.
            patchGenericFontFamiliesMappingOnce()

            configuredResolvers[resolver] = true
        }
    }

    private fun resolveLoadResult(
        resolver: FontFamily.Resolver,
        family: FontFamily,
        weight: FontWeight
    ): FontLoadResult? {
        return runCatching {
            resolver.resolve(
                fontFamily = family,
                fontWeight = weight,
                fontStyle = FontStyle.Normal,
                fontSynthesis = FontSynthesis.None
            ).value as? FontLoadResult
        }.getOrNull()
    }

    private fun setFontLoadResultAliases(target: FontLoadResult, newAliases: List<String>) {
        if (newAliases.isEmpty() || target.aliases == newAliases) return
        runCatching {
            val field = FontLoadResult::class.java.getDeclaredField("aliases")
            field.isAccessible = true
            field.set(target, newAliases)
        }
    }

    private fun registerUnifiedAliasesInFontProvider(
        resolver: FontFamily.Resolver,
        sarasaRegular: org.jetbrains.skia.Typeface?,
        sarasaSemiBold: org.jetbrains.skia.Typeface?,
        ipaRegular: org.jetbrains.skia.Typeface?,
        ipaBold: org.jetbrains.skia.Typeface?,
        plangothicP1: org.jetbrains.skia.Typeface?,
        plangothicP2: org.jetbrains.skia.Typeface?
    ) {
        runCatching {
            val loaderMethod = resolver.javaClass.methods.firstOrNull {
                it.name.startsWith("getPlatformFontLoader") && it.parameterCount == 0
            } ?: return
            val loader = loaderMethod.invoke(resolver) ?: return
            val fontCacheField = loader.javaClass.getDeclaredField("fontCache").apply { isAccessible = true }
            val fontCache = fontCacheField.get(loader) ?: return
            val providerField = fontCache.javaClass.getDeclaredField("fontProvider").apply { isAccessible = true }
            val provider = providerField.get(fontCache) as? TypefaceFontProviderWithFallback ?: return
            @Suppress("UNCHECKED_CAST")
            val registeredSet = runCatching {
                fontCache.javaClass.getDeclaredField("registered").apply { isAccessible = true }.get(fontCache) as? MutableSet<String>
            }.getOrNull()

            fun reg(tf: org.jetbrains.skia.Typeface?, alias: String, dedupKey: String) {
                if (tf == null) return
                if (registeredSet != null && !registeredSet.add(dedupKey)) return
                provider.registerTypeface(tf, alias)
            }

            reg(sarasaRegular, UNIFIED_SARASA_ALIAS, "$UNIFIED_SARASA_ALIAS|400")
            reg(sarasaSemiBold, UNIFIED_SARASA_ALIAS, "$UNIFIED_SARASA_ALIAS|600")
            reg(ipaRegular, UNIFIED_IPA_ALIAS, "$UNIFIED_IPA_ALIAS|400")
            reg(ipaBold, UNIFIED_IPA_ALIAS, "$UNIFIED_IPA_ALIAS|700")
            reg(plangothicP1, UNIFIED_PLANGOTHIC_P1_ALIAS, "$UNIFIED_PLANGOTHIC_P1_ALIAS|400")
            reg(plangothicP2, UNIFIED_PLANGOTHIC_P2_ALIAS, "$UNIFIED_PLANGOTHIC_P2_ALIAS|400")
        }
    }

    private fun patchGenericFontFamiliesMappingOnce() {
        if (genericMappingPatched) return
        runCatching {
            val ktCls = Class.forName("androidx.compose.ui.text.platform.PlatformFont_skikoKt")
            val getter = ktCls.getDeclaredMethod("getGenericFontFamiliesMapping").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val currentMap = getter.invoke(null) as? Map<String, List<String>> ?: return

            val studioChain = listOf(
                UNIFIED_SARASA_ALIAS,
                UNIFIED_IPA_ALIAS,
                UNIFIED_PLANGOTHIC_P1_ALIAS,
                UNIFIED_PLANGOTHIC_P2_ALIAS
            )
            val serifTail = listOf(
                UNIFIED_IPA_ALIAS,
                UNIFIED_PLANGOTHIC_P1_ALIAS,
                UNIFIED_PLANGOTHIC_P2_ALIAS
            )

            val updatedMap = LinkedHashMap<String, List<String>>(currentMap.size + 2)
            for ((k, v) in currentMap) {
                updatedMap[k] = when (k) {
                    "sans-serif" -> (studioChain + v).distinct()
                    "monospace" -> (v + studioChain).distinct()
                    "serif" -> (v + serifTail).distinct()
                    else -> (v + studioChain).distinct()
                }
            }

            val delegateField = ktCls.getDeclaredField("GenericFontFamiliesMapping\$delegate").apply { isAccessible = true }
            delegateField.set(null, lazyOf(updatedMap))
            genericMappingPatched = true
        }
    }
}

/**
 * Headless build-time verification runner invoked via Gradle (`verifyDesktopTypography`)
 * to prove Skia Paragraph resolution across Sarasa UI SC, IPA/Sinological extensions, and CJK Ext-B..I.
 */
object DesktopTypographyVerificationRunner {
    @JvmStatic
    fun main(args: Array<String>) {
        val resolver = createFontFamilyResolver()
        DesktopTypographyChain.installInto(resolver)

        // 1. Verify weight differentiation on Sarasa UI SC (Regular 400 vs SemiBold 600)
        val normalRes = resolver.resolve(
            fontFamily = DesktopTypographyChain.studioUiFontFamily,
            fontWeight = FontWeight.Normal
        ).value as FontLoadResult
        val mediumRes = resolver.resolve(
            fontFamily = DesktopTypographyChain.studioUiFontFamily,
            fontWeight = FontWeight.Medium
        ).value as FontLoadResult
        val boldRes = resolver.resolve(
            fontFamily = DesktopTypographyChain.studioUiFontFamily,
            fontWeight = FontWeight.Bold
        ).value as FontLoadResult

        val normalWeight = normalRes.typeface?.fontStyle?.weight ?: -1
        val mediumWeight = mediumRes.typeface?.fontStyle?.weight ?: -1
        val boldWeight = boldRes.typeface?.fontStyle?.weight ?: -1
        check(normalWeight == 400) { "Expected Sarasa Normal typeface weight 400, got $normalWeight" }
        check(mediumWeight == 600 && boldWeight == 600) {
            "Expected Sarasa Medium/Bold typeface weight 600 (SemiBold), got medium=$mediumWeight, bold=$boldWeight"
        }
        check(normalRes.aliases.size >= 4) {
            "Expected 4-layer alias chain on studioUiFontFamily, got ${normalRes.aliases}"
        }

        // 2. Verify every target dialect & phonetic test string lays out in Skia with ZERO .notdef glyphs!
        // Includes:
        // - Standard Mainland SC UI text & punctuation
        // - Missing-in-Sarasa IPA & Sinological symbols: ɕ (U+0255), ɿ (U+027F), ʅ (U+0285), ʮ (U+02AE), ʯ (U+02AF),
        //   ȶ (U+0236), ȡ (U+0221), ȵ (U+0235), ᴇ (U+1D07), Chao tones ˥˦˧˨˩, combining ◌̃ ◌̥ ◌̱ p̚ t͡ɕʰ
        // - CJK Ext-B..E (Plangothic P1): 𠮶 (U+20BB6), 𠲎 (U+20C8E), 𡃁 (U+210C1), 𥄫 (U+2512B), 𪜶 (U+2A736 Ext-C),
        //   𫝛 (U+2B75B Ext-D), 𫠜 (U+2B81C Ext-D), 𬮦 (U+2CBA6 Ext-E)
        // - CJK Ext-F..I (Plangothic P2): 𬺰 (U+2CEB0 Ext-F), 𰣻 (U+308FB Ext-G), 𱍐 (U+31350 Ext-H), 𮯰 (U+2EBF0 Ext-I)
        val samples = listOf(
            "UI+Dialect+IPA(Normal)" to Pair(
                DesktopTypographyChain.typography.bodyMedium,
                "田野方言字表：普通话、粤语𠮶啲𡃁仔𥄫、闽南𪜶、吴语𠲎、客家𫝛𫠜、Ext-E𬮦、Ext-F𬺰、biáng𰣻、Ext-H𱍐、Ext-I𮯰 [tɕʰɿ˥˩ tʂʅ˧˥ ᴇ p̚ ◌̃ ◌̥ ◌̱]"
            ),
            "UI+Dialect+IPA(Bold)" to Pair(
                DesktopTypographyChain.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                "粗体标题字重校验：更纱黑体 SemiBold 600 · 五度标调 ˥ ˦ ˧ ˨ ˩ · 舌尖元音 ɿ ʅ ʮ ʯ · 方言僻字 𠮶𪜶𫝛𰣻𮯰"
            ),
            "PhoneticChip(IPA-First)" to Pair(
                TextStyle(
                    fontFamily = DesktopTypographyChain.phoneticIpaFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                ),
                "tɕʰ ȶ ȡ ȵ ɿ ʅ ʮ ʯ ᴇ ◌̃ ◌̥ ◌̱ p̚ t̚ k̚ ˥ ˦ ˧ ˨ ˩ 鼻化音 𠮶 𰣻"
            ),
            "DefaultFontFamily(CanvasMeasurer)" to Pair(
                TextStyle(fontFamily = FontFamily.Default, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                "F1/F2 元音图 Canvas 默认字体：i y ɿ ʅ ʮ ʯ ᴇ u ɤ · 𠮶 𪜶 𫝛 𰣻 𮯰"
            ),
            "MonospacePreview(TextGrid)" to Pair(
                TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                "intervals [1]: xmin = 0.000, text = \"𠮶 [kɔ˧˥] ʅ ◌̃ 𰣻\""
            )
        )

        val density = Density(1f, 1f)
        for ((label, spec) in samples) {
            val (style, text) = spec
            val composeParagraph = Paragraph(
                text = text,
                style = style,
                constraints = Constraints(maxWidth = 2400),
                density = density,
                fontFamilyResolver = resolver
            )
            // Extract underlying org.jetbrains.skia.paragraph.Paragraph to assert unresolvedGlyphsCount == 0
            val skiaParaField = composeParagraph.javaClass.getDeclaredField("paragraphIntrinsics").apply {
                isAccessible = true
            }
            val intrinsics = skiaParaField.get(composeParagraph)
            val paraField = composeParagraph.javaClass.declaredFields.firstOrNull {
                it.type.name == "org.jetbrains.skia.paragraph.Paragraph"
            }?.apply { isAccessible = true }
            val skiaPara = paraField?.get(composeParagraph) as? org.jetbrains.skia.paragraph.Paragraph
                ?: error("Could not inspect underlying Skia Paragraph on ${composeParagraph.javaClass.name} (intrinsics=$intrinsics)")

            val unresolved = skiaPara.unresolvedGlyphsCount
            check(unresolved == 0) {
                "[$label] Skia Paragraph had $unresolved unresolved (.notdef) glyph(s) for text: $text"
            }
            check(composeParagraph.width > 50f && composeParagraph.height > 8f) {
                "[$label] Unexpected zero/tiny paragraph dimensions: ${composeParagraph.width}x${composeParagraph.height}"
            }
        }

        // 3. Directly verify glyph IDs != 0 in each specific bundled typeface for its target Unicode block
        val sarasaTf = normalRes.typeface ?: error("Missing Sarasa Regular Typeface")
        val ipaTf = (resolver.resolve(DesktopTypographyChain.phoneticIpaFontFamily, FontWeight.Normal).value as FontLoadResult).typeface
            ?: error("Missing IPA Sans Typeface")
        val p1Tf = (resolver.resolve(DesktopTypographyChain.plangothicP1FontFamily, FontWeight.Normal).value as FontLoadResult).typeface
            ?: error("Missing Plangothic P1 Typeface")
        val p2Tf = (resolver.resolve(DesktopTypographyChain.plangothicP2FontFamily, FontWeight.Normal).value as FontLoadResult).typeface
            ?: error("Missing Plangothic P2 Typeface")

        for (cp in intArrayOf('普'.code, '通'.code, '话'.code, 0x3400 /* Ext-A 㐀 */)) {
            check(sarasaTf.getUTF32Glyph(cp).toInt() != 0) { "Sarasa missing CJK BMP/Ext-A U+${cp.toString(16)}" }
        }
        for (cp in intArrayOf(0x0255 /* ɕ */, 0x027F /* ɿ */, 0x0285 /* ʅ */, 0x02AE /* ʮ */, 0x1D07 /* ᴇ */, 0x02E5 /* ˥ */, 0x25CC /* ◌ */, 0x0303, 0x0325, 0x0331, 0x031A)) {
            check(ipaTf.getUTF32Glyph(cp).toInt() != 0) { "IPA Sans missing phonetic U+${cp.toString(16)}" }
        }
        for (cp in intArrayOf(
            0x20BB6 /* 𠮶 Ext-B */,
            0x20C8E /* 𠲎 Ext-B */,
            0x2A736 /* 𪜶 Ext-C */,
            0x2B75B /* 𫝛 Ext-D */,
            0x2CBA6 /* 𬮦 Ext-E */,
            0x2CEB0 /* 𬺰 Ext-F */,
            0x2EBF0 /* 𮯰 Ext-I */
        )) {
            check(p1Tf.getUTF32Glyph(cp).toInt() != 0) { "Plangothic P1 (Plane 2) missing Ext-B..F/I U+${cp.toString(16)}" }
        }
        for (cp in intArrayOf(
            0x308FB /* 𰣻 Ext-G (biáng) */,
            0x31350 /* 𱍐 Ext-H */
        )) {
            check(p2Tf.getUTF32Glyph(cp).toInt() != 0) { "Plangothic P2 (Plane 3) missing Ext-G..H U+${cp.toString(16)}" }
        }

        println(
            "[TYPOGRAPHY-VERIFIED] Sarasa UI SC (${sarasaTf.familyName}, weights=$normalWeight/$mediumWeight, glyphs=${sarasaTf.glyphsCount}) + " +
                "IPA Sans (${ipaTf.familyName}, glyphs=${ipaTf.glyphsCount}) + " +
                "Plangothic P1 (${p1Tf.familyName}, Plane 2 Ext-B..F/I, glyphs=${p1Tf.glyphsCount}) + " +
                "Plangothic P2 (${p2Tf.familyName}, Plane 3 Ext-G..H, glyphs=${p2Tf.glyphsCount}) -> 0 unresolved glyphs across all 5 Skia Paragraph suites!"
        )
    }
}
