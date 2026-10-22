package com.nova.micfx.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log10

class NoiseReductionTest {
    private val sr = 48000

    @Test fun suppressorReducesSteadyNoise() {
        val nr = NoiseReductionProcessor(sr)
        nr.configure(true, 1f, false, -50f)
        val src = TestSignals.noise(0.02f, sr * 3)
        val out = TestSignals.processInBlocks(src) { b, n -> nr.process(b, n) }
        val from = (sr * 2.5).toInt()
        val reduction = 20.0 * log10((TestSignals.rms(src, from, src.size) / TestSignals.rms(out, from, out.size)).toDouble())
        assertTrue("noise reduced by only $reduction dB", reduction > 15.0)
        assertTrue(nr.reductionDb > 10f)
    }

    @Test fun speechLikeToneIsPreserved() {
        val nr = NoiseReductionProcessor(sr)
        nr.configure(true, 1f, false, -50f)
        val noise = TestSignals.noise(0.02f, sr * 2)
        val tone = TestSignals.sine(300.0, 0.3f, sr, sr)
        val src = noise + tone
        val out = TestSignals.processInBlocks(src) { b, n -> nr.process(b, n) }
        val from = sr * 2 + sr / 2
        val ratio = TestSignals.rms(out, from, src.size) / TestSignals.rms(src, from, src.size)
        assertTrue("tone level ratio $ratio", ratio > 0.9f)
        assertTrue(nr.voiceActive)
    }

    @Test fun disabledProcessorDoesNotChangeAudio() {
        val nr = NoiseReductionProcessor(sr)
        nr.configure(false, 1f, false, -50f)
        val src = TestSignals.noise(0.1f, 9600)
        val out = TestSignals.processInBlocks(src) { b, n -> nr.process(b, n) }
        for (i in src.indices) assertEquals(src[i], out[i], 1e-7f)
        assertEquals(0f, nr.reductionDb, 1e-6f)
    }

    @Test fun gateClosesBelowThresholdAndOpensAbove() {
        val quiet = NoiseReductionProcessor(sr)
        quiet.configure(false, 0f, true, -40f)
        val q = TestSignals.sine(500.0, 0.001f, sr, sr)
        val qo = TestSignals.processInBlocks(q) { b, n -> quiet.process(b, n) }
        assertTrue(TestSignals.rms(qo, sr / 2, sr) < 0.1f * TestSignals.rms(q, sr / 2, sr))

        val loud = NoiseReductionProcessor(sr)
        loud.configure(false, 0f, true, -40f)
        val l = TestSignals.sine(500.0, 0.2f, sr, sr)
        val lo = TestSignals.processInBlocks(l) { b, n -> loud.process(b, n) }
        assertTrue(TestSignals.rms(lo, sr / 2, sr) > 0.95f * TestSignals.rms(l, sr / 2, sr))
    }

    @Test fun noiseFloorTracksTheQuietLevel() {
        val nr = NoiseReductionProcessor(sr)
        nr.configure(false, 0f, false, -50f)
        TestSignals.processInBlocks(TestSignals.noise(0.02f, sr * 2)) { b, n -> nr.process(b, n) }
        // uniform noise +-0.02 has an RMS of about 0.0115 = about -39 dBFS
        assertTrue("floor ${nr.noiseFloorDb} dB", nr.noiseFloorDb in -45f..-34f)
    }
}
