package com.example

import com.example.ui.theme.containsPhoneticOrCombiningGlyphs
import com.example.ui.theme.formatQuickNoteSymbolForDisplay
import com.example.viewmodel.RecordingViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PhoneticSymbolAndFontCoverageTest {

    @Test
    fun combiningMarksReceiveDottedCircleCarrierWhileStandaloneSymbolsStayIntact() {
        // Bare combining marks (e.g. last item of Five-Level Tone "\u0303", voiceless ring "\u0325", macron below "\u0331")
        assertEquals("◌\u0303", formatQuickNoteSymbolForDisplay("\u0303"))
        assertEquals("◌\u0325", formatQuickNoteSymbolForDisplay("\u0325"))
        assertEquals("◌\u0331", formatQuickNoteSymbolForDisplay("\u0331"))
        assertEquals("◌\u031A", formatQuickNoteSymbolForDisplay("\u031A"))

        // Idempotent if already prefixed with dotted circle
        assertEquals("◌\u0303", formatQuickNoteSymbolForDisplay("◌\u0303"))

        // Composite symbols with base letters + trailing diacritic must NOT get leading dotted circle
        assertEquals("p̚", formatQuickNoteSymbolForDisplay("p̚"))
        assertEquals("tʰ", formatQuickNoteSymbolForDisplay("tʰ"))
        assertEquals("55", formatQuickNoteSymbolForDisplay("55"))
        assertEquals("˥", formatQuickNoteSymbolForDisplay("˥"))
        assertEquals("ɿ", formatQuickNoteSymbolForDisplay("ɿ"))
    }

    @Test
    fun phoneticAndCombiningGlyphDetectionDistinguishesPlainTextFromIpaChips() {
        assertFalse(containsPhoneticOrCombiningGlyphs("55"))
        assertFalse(containsPhoneticOrCombiningGlyphs("214"))
        assertFalse(containsPhoneticOrCombiningGlyphs("口语连读"))

        assertTrue(containsPhoneticOrCombiningGlyphs("◌\u0303"))
        assertTrue(containsPhoneticOrCombiningGlyphs("\u0325"))
        assertTrue(containsPhoneticOrCombiningGlyphs("˥"))
        assertTrue(containsPhoneticOrCombiningGlyphs("ɿ"))
        assertTrue(containsPhoneticOrCombiningGlyphs("ʮ"))
        assertTrue(containsPhoneticOrCombiningGlyphs("ȶʰ"))
        assertTrue(containsPhoneticOrCombiningGlyphs("ᴇ"))
        assertTrue(containsPhoneticOrCombiningGlyphs("p̚"))
    }

    @Test
    fun bundledIpaFontsCoverEveryDefaultToneInitialAndFinalCodePoint() {
        val fontDirCandidates = listOf(
            File("src/main/res/font"),
            File("app/src/main/res/font")
        )
        val fontDir = fontDirCandidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate res/font directory from ${File(".").absolutePath}")

        val regularFont = File(fontDir, "phonacq_ipa_sans.ttf")
        val boldFont = File(fontDir, "phonacq_ipa_sans_bold.ttf")
        assertTrue("Missing phonacq_ipa_sans.ttf", regularFont.isFile && regularFont.length() > 100_000L)
        assertTrue("Missing phonacq_ipa_sans_bold.ttf", boldFont.isFile && boldFont.length() > 100_000L)

        val requiredSymbols = buildList {
            addAll(listOf("55", "35", "214", "51", "44", "33", "21", "24", "42", "53", "˥", "˦", "˧", "˨", "˩", "ʔ", "ʰ", "\u0303"))
            addAll(RecordingViewModel.DEFAULT_DIALECT_IPA_INITIALS)
            addAll(RecordingViewModel.DEFAULT_DIALECT_IPA_FINALS)
            add("◌") // U+25CC dotted circle carrier
            add("\u0361") // tie bar above (t͡s)
            add("\u035C") // tie bar below
        }

        for (fontFile in listOf(regularFont, boldFont)) {
            val coveredCodePoints = parseTrueTypeBmpCmapFormat4(fontFile.readBytes())
            val missing = mutableListOf<String>()
            for (sym in requiredSymbols) {
                val displaySym = formatQuickNoteSymbolForDisplay(sym)
                var idx = 0
                while (idx < displaySym.length) {
                    val cp = displaySym.codePointAt(idx)
                    if (cp !in coveredCodePoints) {
                        missing.add("$displaySym (U+${cp.toString(16).uppercase().padStart(4, '0')})")
                    }
                    idx += Character.charCount(cp)
                }
            }
            assertTrue("Font ${fontFile.name} is missing glyphs: $missing", missing.isEmpty())
        }
    }

    private fun parseTrueTypeBmpCmapFormat4(ttf: ByteArray): Set<Int> {
        val buf = ByteBuffer.wrap(ttf).order(ByteOrder.BIG_ENDIAN)
        val numTables = buf.getShort(4).toInt() and 0xFFFF
        var cmapOffset = -1
        for (i in 0 until numTables) {
            val rec = 12 + i * 16
            val tag = String(ttf, rec, 4, Charsets.US_ASCII)
            if (tag == "cmap") {
                cmapOffset = buf.getInt(rec + 8)
                break
            }
        }
        require(cmapOffset > 0) { "Missing cmap table" }

        val numSubtables = buf.getShort(cmapOffset + 2).toInt() and 0xFFFF
        val result = HashSet<Int>(4096)
        for (i in 0 until numSubtables) {
            val subRec = cmapOffset + 4 + i * 8
            val platformId = buf.getShort(subRec).toInt() and 0xFFFF
            val encodingId = buf.getShort(subRec + 2).toInt() and 0xFFFF
            if (!((platformId == 3 && (encodingId == 1 || encodingId == 10)) || platformId == 0)) continue

            val subOffset = cmapOffset + buf.getInt(subRec + 4)
            val format = buf.getShort(subOffset).toInt() and 0xFFFF
            if (format == 4) {
                val segCount = (buf.getShort(subOffset + 6).toInt() and 0xFFFF) / 2
                val endCodesPos = subOffset + 14
                val startCodesPos = endCodesPos + segCount * 2 + 2
                val idDeltasPos = startCodesPos + segCount * 2
                val idRangeOffsetsPos = idDeltasPos + segCount * 2

                for (s in 0 until segCount) {
                    val endCode = buf.getShort(endCodesPos + s * 2).toInt() and 0xFFFF
                    val startCode = buf.getShort(startCodesPos + s * 2).toInt() and 0xFFFF
                    if (startCode == 0xFFFF && endCode == 0xFFFF) continue
                    val idDelta = buf.getShort(idDeltasPos + s * 2).toInt()
                    val idRangeOffsetPos = idRangeOffsetsPos + s * 2
                    val idRangeOffset = buf.getShort(idRangeOffsetPos).toInt() and 0xFFFF

                    for (cp in startCode..endCode) {
                        val glyphId = if (idRangeOffset == 0) {
                            (cp + idDelta) and 0xFFFF
                        } else {
                            val glyphIdxAddr = idRangeOffsetPos + idRangeOffset + (cp - startCode) * 2
                            val rawGlyph = buf.getShort(glyphIdxAddr).toInt() and 0xFFFF
                            if (rawGlyph == 0) 0 else (rawGlyph + idDelta) and 0xFFFF
                        }
                        if (glyphId != 0) result.add(cp)
                    }
                }
            }
        }
        return result
    }
}
