package com.nova.micfx.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LimiterTest {
    private val sr = 48000

    @Test fun neverExceedsCeiling() {
        val lim = LimiterProcessor(sr, ceiling = 0.9f)
        val data = TestSignals.sine(300.0, 5f, sr, sr)
        val over = lim.process(data, data.size)
        assertTrue(over > 0)
        assertTrue(TestSignals.peak(data) <= 0.9f + 1e-6f)
    }

    @Test fun quietSignalPassesUnchanged() {
        val lim = LimiterProcessor(sr)
        val src = TestSignals.sine(500.0, 0.2f, 4800, sr)
        val data = src.copyOf()
        assertEquals(0, lim.process(data, data.size))
        for (i in data.indices) assertEquals(src[i], data[i], 1e-6f)
        assertEquals(0f, lim.gainReductionDb, 0f)
    }

    @Test fun gainRecoversAfterLoudBurst() {
        val lim = LimiterProcessor(sr, ceiling = 0.9f, releaseMs = 20f)
        val burst = FloatArray(480) { 3f }
        lim.process(burst, burst.size)
        assertTrue(lim.gain < 0.5f)
        val quiet = FloatArray(sr / 2) { 0.01f }
        lim.process(quiet, quiet.size)
        assertTrue("gain should return close to unity", lim.gain > 0.99f)
    }

    @Test fun nanIsReplacedBySilence() {
        val lim = LimiterProcessor(sr)
        val d = floatArrayOf(0.1f, Float.NaN, 0.2f)
        lim.process(d, 3)
        assertEquals(0f, d[1], 0f)
    }

    @Test fun pipelineClipsAreCountedAndOutputIsSafe() {
        val p = AudioProcessingPipeline(sr)
        p.setParams(EffectParams(inputGainDb = 24f, outputVolume = 1.5f))
        val out = TestSignals.processInBlocks(TestSignals.sine(800.0, 0.9f, sr, sr)) { b, n -> p.process(b, n) }
        assertTrue(TestSignals.peak(out) <= p.ceiling + 1e-6f)
        assertTrue("clipping must be reported", p.totalClipped > 0)
    }
}
