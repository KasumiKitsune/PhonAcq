package com.example.textgrid

import com.example.textgrid.codec.TextGridCodec
import com.example.textgrid.model.DiagnosticCode
import com.example.textgrid.model.DiagnosticSeverity
import com.example.textgrid.model.TextGridAnomalyKind
import com.example.textgrid.model.TextGridDocument
import com.example.textgrid.model.TextGridFormatVariant
import com.example.textgrid.model.TextGridInterval
import com.example.textgrid.model.TextGridOpenMode
import com.example.textgrid.model.TextGridPoint
import com.example.textgrid.model.TextGridScaleMode
import com.example.textgrid.model.TextGridScoreTheme
import com.example.textgrid.model.TextGridSelection
import com.example.textgrid.model.TextGridTickPrecision
import com.example.textgrid.model.TextGridTier
import com.example.textgrid.model.TextGridVisualConfig
import com.example.textgrid.model.TextGridWrapMode
import com.example.textgrid.ops.TextGridOps
import com.example.textgrid.persistence.WordTextGridStore
import com.example.utils.TextGridFormatConfig
import com.example.utils.TextGridPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.zip.GZIPOutputStream

class TextGridCoreTest {

    // =========================================================================
    // 1. Visual Config Serialization, Defaults & Legacy Compatibility
    // =========================================================================

    @Test
    fun visualConfig_defaultsJsonRoundTripAndLegacyCompat() {
        val def = TextGridVisualConfig()
        assertEquals(TextGridOpenMode.VISUAL, def.defaultOpenMode)
        assertEquals(TextGridScaleMode.ELASTIC, def.scaleMode)
        assertEquals(TextGridWrapMode.AUTO, def.wrapMode)
        assertEquals(TextGridTickPrecision.P3, def.tickPrecision)
        assertTrue(def.showRichWhitespace)
        assertEquals(TextGridScoreTheme.FOLLOW_APP, def.scoreTheme)

        val custom = TextGridVisualConfig(
            defaultOpenMode = TextGridOpenMode.SOURCE,
            scaleMode = TextGridScaleMode.LINEAR,
            wrapMode = TextGridWrapMode.SINGLE,
            tickPrecision = TextGridTickPrecision.P4,
            showRichWhitespace = false,
            scoreTheme = TextGridScoreTheme.ACADEMIC_BW
        )
        val json = custom.toJsonString()
        assertEquals(custom, TextGridVisualConfig.fromJsonString(json))
        assertEquals(def, TextGridVisualConfig.fromJsonString(null))
        assertEquals(def, TextGridVisualConfig.fromJsonString("{corrupt"))

        // Legacy JSON containing deprecated edit/spectrogram keys maps cleanly
        val legacyJson = """
            {"scaleMode":"LINEAR","wrapMode":"SINGLE","tickPrecision":"NONE","showSpectrogram":true,"maxFreqHz":10000,"cueMode":"PITCH","defaultOpenMode":"VISUAL_EDIT","showMicroLoupe":true,"scoreTheme":"WARM_PAPER"}
        """.trimIndent()
        val migrated = TextGridVisualConfig.fromJsonString(legacyJson)
        assertEquals(TextGridOpenMode.VISUAL, migrated.defaultOpenMode)
        assertEquals(TextGridScaleMode.LINEAR, migrated.scaleMode)
        assertEquals(TextGridWrapMode.SINGLE, migrated.wrapMode)
        assertEquals(TextGridTickPrecision.NONE, migrated.tickPrecision)
        assertEquals(TextGridScoreTheme.WARM_PAPER, migrated.scoreTheme)

        val legacySourceJson = """{"defaultOpenMode":"SOURCE_CODE"}"""
        assertEquals(TextGridOpenMode.SOURCE, TextGridVisualConfig.fromJsonString(legacySourceJson).defaultOpenMode)
    }

    // =========================================================================
    // 2. Codec Full-Variant Round-Trip (Long/Short/Binary/Chronological/UTF-16BE/Gzip)
    // =========================================================================

    private fun buildRichReferenceDocument(): TextGridDocument {
        return TextGridDocument(
            name = "rich_sample",
            xmin = -0.5,
            xmax = 2.3456789012345,
            tiers = listOf(
                TextGridTier.IntervalTier(
                    name = "words \"1\"",
                    xmin = -0.5,
                    xmax = 2.3456789012345,
                    intervals = listOf(
                        TextGridInterval(-0.5, 0.25, "line1\n\nline3"),
                        TextGridInterval(0.25, 1.5, "\"quoted\" C:\\path\\ \\sh\\ae"),
                        TextGridInterval(1.5, 2.3456789012345, "你好𐌀😀")
                    )
                ),
                TextGridTier.IntervalTier(
                    name = "sil",
                    xmin = -0.5,
                    xmax = 2.3456789012345,
                    intervals = listOf(TextGridInterval(-0.5, 2.3456789012345, ""))
                ),
                TextGridTier.TextTier(
                    name = "bell",
                    xmin = -0.5,
                    xmax = 2.3456789012345,
                    points = listOf(
                        TextGridPoint(-0.5, "start"),
                        TextGridPoint(0.9, "ding \"!\" \\^h"),
                        TextGridPoint(2.3456789012345, "")
                    )
                ),
                TextGridTier.TextTier(
                    name = "empty_points",
                    xmin = -0.5,
                    xmax = 2.3456789012345,
                    points = emptyList()
                )
            )
        )
    }

    @Test
    fun codec_allVariantsRoundTripLossless() {
        val original = buildRichReferenceDocument()

        val longText = TextGridCodec.serialize(original, TextGridFormatVariant.LONG_TEXT)
        assertTrue(longText.contains("class = \"TextTier\""))
        assertFalse(longText.contains("PointTier"))
        assertTrue(longText.contains("number = "))
        assertTrue(longText.contains("mark = "))
        val parsedLong = TextGridCodec.parseText(longText, "rich_sample")
        assertTrue("Long parse failed: ${parsedLong.diagnostics}", parsedLong.isSuccess)
        assertEquals(original, parsedLong.document)

        // SourceSpanMap bidirectional mapping for playback line sync
        val span = parsedLong.spanMap.spanForSelection(TextGridSelection.Interval(0, 1), original)
        assertNotNull(span)
        assertTrue(parsedLong.spanMap.linesForTime(0.5, original, 0).isNotEmpty())

        val utf16Bytes = TextGridCodec.serializeToBytes(original, TextGridFormatVariant.LONG_TEXT, utf16Be = true)
        assertEquals(0xFE.toByte(), utf16Bytes[0])
        assertEquals(0xFF.toByte(), utf16Bytes[1])
        val parsedUtf16 = TextGridCodec.parseBytes(utf16Bytes, "rich_sample")
        assertTrue(parsedUtf16.isSuccess)
        assertEquals("UTF-16BE", parsedUtf16.detectedEncoding)
        assertEquals(original, parsedUtf16.document)

        val shortText = TextGridCodec.serialize(original, TextGridFormatVariant.SHORT_TEXT)
        val parsedShort = TextGridCodec.parseText(shortText, "rich_sample")
        assertTrue(parsedShort.isSuccess)
        assertEquals(TextGridFormatVariant.SHORT_TEXT, parsedShort.detectedVariant)
        assertEquals(original, parsedShort.document)

        val binaryBytes = TextGridCodec.serializeToBytes(original, TextGridFormatVariant.BINARY)
        assertEquals("ooBinaryFile", String(binaryBytes, 0, 12, Charsets.US_ASCII))
        val parsedBin = TextGridCodec.parseBytes(binaryBytes, "rich_sample")
        assertTrue("Binary parse failed: ${parsedBin.diagnostics}", parsedBin.isSuccess)
        assertEquals(TextGridFormatVariant.BINARY, parsedBin.detectedVariant)
        assertEquals(original, parsedBin.document)

        val chronBytes = TextGridCodec.serializeToBytes(original, TextGridFormatVariant.CHRONOLOGICAL)
        assertFalse(chronBytes.size >= 3 && chronBytes[0] == 0xEF.toByte() && chronBytes[1] == 0xBB.toByte())
        val parsedChron = TextGridCodec.parseBytes(chronBytes, "rich_sample")
        assertTrue("Chron parse failed: ${parsedChron.diagnostics}", parsedChron.isSuccess)
        assertEquals(TextGridFormatVariant.CHRONOLOGICAL, parsedChron.detectedVariant)
        assertEquals(original, parsedChron.document)

        val gzOut = ByteArrayOutputStream()
        GZIPOutputStream(gzOut).use { it.write(longText.toByteArray(Charsets.UTF_8)) }
        val parsedGz = TextGridCodec.parseBytes(gzOut.toByteArray(), "rich_sample")
        assertTrue(parsedGz.isSuccess)
        assertEquals(original, parsedGz.document)
    }

    @Test
    fun codec_hybridCommentsWindows1252MarkTierAndStandalone() {
        val hybrid = """
            "ooTextFile"
            "TextGrid"
            0 2.0 ! tricky comment with "quotes" <exists> 999
            <Exists> 2
            "IntervalTier" "w" 0 2.0
            0 ! empty interval tier should auto-repair
            "MarkTier" "pts" 0 2.0
            3
            0.8 "b"
            0.2 "a"
            0.8 "dup_dropped"
        """.trimIndent()

        val res = TextGridCodec.parseText(hybrid, "hybrid")
        assertTrue("Hybrid parse failed: ${res.diagnostics}", res.isSuccess)
        val doc = res.document!!
        assertEquals(2, doc.tiers.size)
        val ivTier = doc.tiers[0] as TextGridTier.IntervalTier
        assertEquals(1, ivTier.intervals.size)
        assertEquals(TextGridInterval(0.0, 2.0, ""), ivTier.intervals[0])
        assertTrue(res.nonFatalDiagnostics.any { it.code == DiagnosticCode.EMPTY_INTERVAL_TIER_REPAIRED })

        val ptTier = doc.tiers[1] as TextGridTier.TextTier
        assertEquals(listOf(0.2, 0.8), ptTier.points.map { it.number })
        assertEquals(listOf("a", "b"), ptTier.points.map { it.mark })

        val cp1252Header = "File type = \"ooTextFile\"\rObject class = \"TextGrid\"\r\r0\r1\r<exists>\r1\r\"IntervalTier\"\r\"w\"\r0\r1\r1\r0\r1\r\""
        val cp1252Bytes = cp1252Header.toByteArray(Charsets.ISO_8859_1) +
            byteArrayOf(0xE9.toByte(), 0x80.toByte()) +
            "\"\r".toByteArray(Charsets.ISO_8859_1)
        val cpRes = TextGridCodec.parseBytes(cp1252Bytes, "cp1252")
        assertTrue(cpRes.isSuccess)
        assertEquals("Windows-1252", cpRes.detectedEncoding)
        assertEquals("é€", (cpRes.document!!.tiers[0] as TextGridTier.IntervalTier).intervals[0].text)

        val standalone = """
            File type = "ooTextFile"
            Object class = "IntervalTier"

            xmin = 0
            xmax = 1.5
            intervals: size = 1
            intervals [1]:
                xmin = 0
                xmax = 1.5
                text = "solo"
        """.trimIndent()
        val soloRes = TextGridCodec.parseText(standalone, "solo_tier")
        assertTrue(soloRes.isSuccess)
        assertEquals(1, soloRes.document!!.tiers.size)
        assertEquals("solo", (soloRes.document!!.tiers[0] as TextGridTier.IntervalTier).intervals[0].text)
    }

    @Test
    fun codec_collectionExtractionTextAndBinary() {
        val textCol = """
            File type = "ooTextFile"
            Object class = "Collection"

            size = 2
            item []:
                item [1]:
                    class = "Sound 2"
                    name = "01.wav"
                    xmin = 0
                    xmax = 0.5
                    nx = 2
                    dx = 0.25
                    x1 = 0.125
                    ymin = 1
                    ymax = 1
                    ny = 1
                    dy = 1
                    y1 = 1
                    z [] []:
                        z [1] []:
                            z [1] [1] = 0.01
                            z [1] [2] = -0.02
                item [2]:
                    class = "TextGrid"
                    name = "utterance_01"
                    xmin = 0
                    xmax = 0.5
                    tiers? <exists>
                    size = 1
                    item []:
                        item [1]:
                            class = "IntervalTier"
                            name = "words"
                            xmin = 0
                            xmax = 0.5
                            intervals: size = 1
                            intervals [1]:
                                xmin = 0
                                xmax = 0.5
                                text = "hi"
        """.trimIndent()
        val parsedCol = TextGridCodec.parseText(textCol)
        assertTrue("Collection parse failed: ${parsedCol.diagnostics}", parsedCol.isSuccess)
        assertEquals("utterance_01", parsedCol.document!!.name)
        assertEquals("hi", (parsedCol.document!!.tiers[0] as TextGridTier.IntervalTier).intervals[0].text)

        val baos = ByteArrayOutputStream()
        fun writeU16(v: Int) = baos.write(ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(v.toShort()).array())
        fun writeI32(v: Int) = baos.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(v).array())
        fun writeR64(v: Double) = baos.write(ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putDouble(v).array())
        fun writeR32(v: Float) = baos.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putFloat(v).array())
        fun writeW8(s: String) {
            baos.write(s.length)
            baos.write(s.toByteArray(Charsets.US_ASCII))
        }
        fun writeW16(s: String) {
            writeU16(s.length)
            baos.write(s.toByteArray(Charsets.US_ASCII))
        }
        baos.write("ooBinaryFile".toByteArray(Charsets.US_ASCII))
        writeW8("Collection")
        writeI32(2)
        writeW8("Sound")
        writeW16("old.wav")
        writeR64(0.0); writeR64(0.2); writeI32(2); writeR64(0.1); writeR64(0.05)
        writeR64(1.0); writeR64(1.0); writeI32(1); writeR64(1.0); writeR64(1.0)
        writeR32(0.25f); writeR32(-0.25f)
        writeW8("TextGrid")
        writeW16("bin_tg")
        writeR64(0.0); writeR64(0.2); baos.write(1); writeI32(1)
        writeW8("TextTier"); writeW16("p"); writeR64(0.0); writeR64(0.2); writeI32(1)
        writeR64(0.1); writeW16("ok")

        val binColRes = TextGridCodec.parseBytes(baos.toByteArray())
        assertTrue("Binary Collection parse failed: ${binColRes.diagnostics}", binColRes.isSuccess)
        assertEquals("bin_tg", binColRes.document!!.name)
        assertEquals("ok", (binColRes.document!!.tiers[0] as TextGridTier.TextTier).points[0].mark)
    }

    @Test
    fun codec_fatalErrorsAndPostelAnomalies() {
        val badCases = listOf(
            "File type = \"ooTextFile\"\nObject class = \"TextGrid\"\n\nxmin = 2\nxmax = 1\ntiers? <exists>\nsize = 0\n" to DiagnosticCode.INVERTED_DOMAIN,
            "File type = \"ooTextFile\"\nObject class = \"TextGrid\"\n\nxmin = 0\nxmax = 1\ntiers? <absent>\n" to DiagnosticCode.ABSENT_TIERS,
            "File type = \"ooTextFile\"\nObject class = \"TextGrid\"\n\nxmin = 0\nxmax = 1\ntiers? <exists>\nsize = -1\n" to DiagnosticCode.NEGATIVE_SIZE,
            "File type = \"ooTextFile\"\nObject class = \"TextGrid\"\n\n0\n1\n<exists>\n1\n\"PointTier\"\n\"p\"\n0\n1\n0\n" to DiagnosticCode.UNKNOWN_TIER_CLASS,
            "File type = \"ooTextFile\"\nObject class = \"TextGrid 1\"\n\n0\n1\n<exists>\n0\n" to DiagnosticCode.UNSUPPORTED_VERSION,
            "File type = \"ooTextFile\"\nObject class = \"TextGrid\"\n\n0\n1\n<exists>\n1\n\"IntervalTier\"\n\"w\"\n0\n1\n1\n0\n1\n\"unclosed" to DiagnosticCode.UNCLOSED_STRING,
            "File type = \"ooTextFile\"\nObject class = \"TextGrid\"\n\n0\n1\n<exists>\n1\n\"IntervalTier\"\n\"w\"\n0\n1\n1\n0\n1\n\"ad\"x\n" to DiagnosticCode.INVALID_CHAR_AFTER_QUOTE,
            "File type = \"ooTextFile\"\nObject class = \"TextGrid\"\n\n0\n1\n<exists>\n1\nclass = 666 \"IntervalTier\"\n" to DiagnosticCode.UNEXPECTED_TOKEN,
            "File type = \"ooTextFile\"\nObject class = \"TextGrid\"\n\n0\n1\n<EXISTS>\n0\n" to DiagnosticCode.INVALID_ENUM
        )

        for ((text, expectedCode) in badCases) {
            val res = TextGridCodec.parseText(text)
            assertFalse("Expected failure for $expectedCode", res.isSuccess)
            assertTrue(
                "Expected $expectedCode but got ${res.fatalDiagnostics}",
                res.fatalDiagnostics.any { it.code == expectedCode && it.severity == DiagnosticSeverity.FATAL_SYNTAX && it.line >= 1 }
            )
        }

        val anomalousDoc = TextGridDocument(
            name = "anom",
            xmin = 0.0,
            xmax = 2.92,
            tiers = listOf(
                TextGridTier.IntervalTier(
                    name = "wrd",
                    xmin = 0.0,
                    xmax = 2.786625,
                    intervals = listOf(
                        TextGridInterval(0.0, 0.5, "a"),
                        TextGridInterval(0.7, 1.2, "gap_before"),
                        TextGridInterval(1.0, 1.5, "overlap"),
                        TextGridInterval(1.5, 1.5, "zero"),
                        TextGridInterval(1.5, 2.786625, "tail")
                    )
                ),
                TextGridTier.TextTier(
                    name = "pts",
                    xmin = 0.0,
                    xmax = 2.92,
                    points = listOf(TextGridPoint(3.1, "oob"))
                )
            )
        )
        val anomalies = TextGridCodec.detectAnomalies(anomalousDoc)
        val kinds = anomalies.map { it.kind }.toSet()
        assertTrue(TextGridAnomalyKind.OUT_OF_DOMAIN in kinds)
        assertTrue(TextGridAnomalyKind.GAP in kinds)
        assertTrue(TextGridAnomalyKind.OVERLAP in kinds)
        assertTrue(TextGridAnomalyKind.ZERO_DURATION in kinds)
        assertTrue(TextGridAnomalyKind.POINT_OUT_OF_DOMAIN in kinds)
    }

    // =========================================================================
    // 3. Read-Only Sample Builder & External Paired Audio Discovery
    // =========================================================================

    @Test
    fun ops_buildSampleDocumentFromFormatConfig() {
        val sampleDoc = TextGridOps.buildSampleDocumentFromFormatConfig(
            config = TextGridFormatConfig.fromPreset(TextGridPreset.CHINESE_DIALECT),
            includeDemoTextTier = true
        )
        assertTrue(sampleDoc.intervalTierCount >= 3)
        assertEquals(1, sampleDoc.textTierCount)
        assertTrue(sampleDoc.duration > 0.5)
    }

    @Test
    fun store_externalParseAndPairedAudioDiscovery() {
        val tempDir = Files.createTempDirectory("tg_store_readonly_test").toFile()
        try {
            val wavFile = File(tempDir, "word_01_test.WAV")
            wavFile.writeBytes(ByteArray(64))

            val tgFile = File(tempDir, "word_01_test.TextGrid")
            val doc = buildRichReferenceDocument()
            tgFile.writeBytes(TextGridCodec.serializeToBytes(doc, TextGridFormatVariant.LONG_TEXT))

            val parsed = WordTextGridStore.parseExternalFile(tgFile)
            assertTrue(parsed.isSuccess)
            assertEquals(doc.copy(name = "word_01_test"), parsed.document)

            val paired = WordTextGridStore.findPairedAudioForTextGrid(tgFile)
            assertNotNull(paired)
            assertEquals(wavFile.canonicalPath, paired!!.canonicalPath)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
