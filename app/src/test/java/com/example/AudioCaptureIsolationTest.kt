package com.example

import com.example.audio.PreRollIsolationGate
import com.example.audio.SpeechTurnVadTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioCaptureIsolationTest {

    @Test
    fun preRollGate_blocksPreviousWordTailUntilCooldownAndSilenceConfirmed() {
        val gate = PreRollIsolationGate(
            postStopCooldownMs = 240L,
            quietFramesRequiredAfterStop = 2,
            maxPreRollDurationMs = 110
        )
        val rate = 48000
        val loudTail = ShortArray(512) { 2600 } // RMS = 2600 (speaker still finishing previous word)
        val quietAmbient = ShortArray(512) { 80 } // RMS = 80 (clean pause between words)
        val nextWordOnset = ShortArray(512) { 1400 } // RMS = 1400 (new word consonant onset)

        gate.onRecordingStopped(nowMs = 1000L)

        // 1) Within 240ms cooldown: even quiet or loud frames must be discarded
        gate.offerStandbyChunk(loudTail, loudTail.size, rate, nowMs = 1080L, quietRmsCeiling = 420f)
        gate.offerStandbyChunk(quietAmbient, quietAmbient.size, rate, nowMs = 1180L, quietRmsCeiling = 420f)
        assertTrue(gate.consumeForRecordingStart(rate).isEmpty())

        // 2) Past cooldown (t=1300..1500ms), speaker is STILL finishing 2nd half of previous word -> must NOT buffer!
        gate.offerStandbyChunk(loudTail, loudTail.size, rate, nowMs = 1300L, quietRmsCeiling = 420f)
        gate.offerStandbyChunk(loudTail, loudTail.size, rate, nowMs = 1420L, quietRmsCeiling = 420f)
        assertTrue(gate.consumeForRecordingStart(rate).isEmpty())

        // 3) Speaker pauses (2 consecutive quiet chunks confirm word boundary), then begins Word 2
        gate.offerStandbyChunk(quietAmbient, quietAmbient.size, rate, nowMs = 1520L, quietRmsCeiling = 420f)
        gate.offerStandbyChunk(quietAmbient, quietAmbient.size, rate, nowMs = 1540L, quietRmsCeiling = 420f)
        for (i in 0 until 15) {
            gate.offerStandbyChunk(nextWordOnset, nextWordOnset.size, rate, nowMs = 1560L + i * 11L, quietRmsCeiling = 420f)
        }

        val preRoll = gate.consumeForRecordingStart(rate)
        assertTrue(preRoll.isNotEmpty())
        val totalSamples = preRoll.sumOf { it.size }
        val expectedMaxSamples = (rate * 110) / 1000
        assertEquals(expectedMaxSamples, totalSamples)
    }

    @Test
    fun vadTracker_ignoresClickTransientAndPreservesDippingToneAndSecondSyllable() {
        val vad = SpeechTurnVadTracker(
            vadThreshold = 1200f,
            lowVolumeThreshold = 250f,
            silenceDurationMs = 900L
        )

        var t = 0L
        // Button tap transient at t=20ms must NOT arm speech detection
        assertFalse(vad.step(rms = 2500f, frameDurationMs = 23L, nowMs = 20L))
        assertFalse(vad.hasDetectedSpeech)

        // Quiet ambient pause before speaker reads (t=50..600ms)
        t = 50L
        while (t <= 600L) {
            assertFalse(vad.step(rms = 95f, frameDurationMs = 23L, nowMs = t))
            t += 23L
        }
        assertFalse(vad.hasDetectedSpeech)

        // Syllable 1 nucleus (t=623..800ms, sustained > 110ms) -> arms speech detection
        while (t <= 800L) {
            assertFalse(vad.step(rms = 2400f, frameDurationMs = 23L, nowMs = t))
            t += 23L
        }
        assertTrue(vad.hasDetectedSpeech)

        // Dipping tone valley / softer 2nd syllable (rms = 540f, below 1200f but above hysteresis sustain threshold)
        // Even if sustained for 950ms, must NOT prematurely split the word!
        val dipEnd = t + 960L
        while (t <= dipEnd) {
            assertFalse(vad.step(rms = 540f, frameDurationMs = 23L, nowMs = t))
            assertEquals(0f, vad.silenceProgress, 0.001f)
            t += 23L
        }

        // Genuine post-word silence (rms = 85f) for >= 900ms -> triggers auto-advance cleanly
        var triggered = false
        val silenceLimit = t + 1050L
        while (t <= silenceLimit) {
            if (vad.step(rms = 85f, frameDurationMs = 23L, nowMs = t)) {
                triggered = true
                break
            }
            t += 23L
        }
        assertTrue(triggered)
    }
}
