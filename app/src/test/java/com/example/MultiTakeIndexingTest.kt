package com.example

import com.example.viewmodel.extractWavTimestampFromFileName
import com.example.viewmodel.parseWordAndSpeakerFromWavFileName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MultiTakeIndexingTest {

    @Test
    fun `parses standard and teproj imported multi-take wav filenames identically`() {
        assertEquals(
            12L to 3L,
            parseWordAndSpeakerFromWavFileName("word_12_speaker_3_1763381095123.wav")
        )
        assertEquals(
            12L to 3L,
            parseWordAndSpeakerFromWavFileName("word_12_speaker_3_1763381095123_0.wav")
        )
        assertEquals(
            12L to 3L,
            parseWordAndSpeakerFromWavFileName("word_12_speaker_3_1763381095999_2.wav")
        )
        assertEquals(
            9045L to 18L,
            parseWordAndSpeakerFromWavFileName("word_9045_speaker_18.wav")
        )
    }

    @Test
    fun `rejects non-take or malformed filenames`() {
        assertNull(parseWordAndSpeakerFromWavFileName("temp_record_123.wav"))
        assertNull(parseWordAndSpeakerFromWavFileName("word_abc_speaker_1_123.wav"))
        assertNull(parseWordAndSpeakerFromWavFileName("word_1_speaker_xyz_123.wav"))
        assertNull(parseWordAndSpeakerFromWavFileName("word_1_speaker_2_123.mp3"))
    }

    @Test
    fun `extracts capture timestamp from both standard and teproj imported takes`() {
        assertEquals(
            1763381095123L,
            extractWavTimestampFromFileName("word_12_speaker_3_1763381095123.wav", fallbackLastModified = 42L)
        )
        assertEquals(
            1763381095123L,
            extractWavTimestampFromFileName("word_12_speaker_3_1763381095123_0.wav", fallbackLastModified = 42L)
        )
        assertEquals(
            777888L,
            extractWavTimestampFromFileName("custom_imported_take.wav", fallbackLastModified = 777888L)
        )
    }
}
