package com.nova.micfx.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GainAndLevelTest {
    @Test fun dbConversionsAreCorrect() {
        assertEquals(1f, DspMath.dbToLinear(0f), 1e-6f)
        assertEquals(1.9953f, DspMath.dbToLinear(6f), 1e-3f)
        assertEquals(0.5012f, DspMath.dbToLinear(-6f), 1e-3f)
        assertEquals(0f, DspMath.linearToDb(1f), 1e-4f)
        assertEquals(-6.02f, DspMath.linearToDb(0.5f), 0.01f)
        assertEquals(DspMath.SILENCE_DB, DspMath.linearToDb(0f), 0f)
        assertEquals(DspMath.SILENCE_DB, DspMath.linearToDb(Float.NaN), 0f)
    }

    @Test fun peakAndRmsOfSine() {
        val s = TestSignals.sine(1000.0, 0.5f, 48000, 48000)
        assertEquals(0.5f, DspMath.peak(s, s.size), 1e-3f)
        assertEquals(0.5f / Math.sqrt(2.0).toFloat(), DspMath.rms(s, s.size), 1e-3f)
        assertEquals(0f, DspMath.rms(s, 0), 0f)
    }

    @Test fun applyGainScalesSamples() {
        val a = floatArrayOf(0.1f, -0.2f, 0.3f)
        DspMath.applyGain(a, 3, 2f)
        assertEquals(0.2f, a[0], 1e-6f); assertEquals(-0.4f, a[1], 1e-6f); assertEquals(0.6f, a[2], 1e-6f)
    }

    @Test fun clippingDetectionCountsFullScaleSamples() {
        val a = floatArrayOf(0.2f, 1.0f, -1.0f, 0.999f, 1.5f, 0.5f)
        assertEquals(4, DspMath.countAtOrAbove(a, a.size, 0.999f))
        assertEquals(0, DspMath.countAtOrAbove(floatArrayOf(0.1f, 0.5f), 2, 0.999f))
    }

    @Test fun shortConversionClampsAndRoundTrips() {
        assertEquals(32767, DspMath.floatToShort(2f).toInt())
        assertEquals(-32768, DspMath.floatToShort(-2f).toInt())
        assertEquals(0, DspMath.floatToShort(Float.NaN).toInt())
        assertEquals(0.5f, DspMath.shortToFloat(DspMath.floatToShort(0.5f)), 1e-3f)
    }

    @Test fun pipelineAppliesRealGainToSamples() {
        val sr = 48000
        val p = AudioProcessingPipeline(sr)
        p.setParams(EffectParams(inputGainDb = 20f, outputVolume = 1f))
        val out = TestSignals.processInBlocks(TestSignals.sine(1000.0, 0.05f, sr, sr)) { b, n -> p.process(b, n) }
        // 0.05 * 10 = 0.5 amplitude after the smoothing has settled
        assertEquals(0.5f, TestSignals.peak(out, sr / 2, sr), 0.03f)
    }

    @Test fun gainChangesAreSmoothedNotInstant() {
        val sr = 48000
        val p = AudioProcessingPipeline(sr)
        p.setParams(EffectParams(inputGainDb = 0f, outputVolume = 1f))
        val warm = TestSignals.sine(1000.0, 0.05f, sr / 2, sr)
        TestSignals.processInBlocks(warm) { b, n -> p.process(b, n) }
        p.setParams(EffectParams(inputGainDb = 20f, outputVolume = 1f))
        val next = TestSignals.sine(1000.0, 0.05f, 48, sr, sr / 2)
        val out = TestSignals.processInBlocks(next, 48) { b, n -> p.process(b, n) }
        assertTrue("first 1 ms must not jump to the full +20 dB", TestSignals.peak(out) < 0.25f)
    }

    @Test fun gainRangeIsLimited() {
        val v = EffectParams(inputGainDb = 1000f).validated()
        assertEquals(Limits.GAIN_MAX_DB, v.inputGainDb, 0f)
        assertEquals(Limits.GAIN_MIN_DB, EffectParams(inputGainDb = -1000f).validated().inputGainDb, 0f)
    }
}
