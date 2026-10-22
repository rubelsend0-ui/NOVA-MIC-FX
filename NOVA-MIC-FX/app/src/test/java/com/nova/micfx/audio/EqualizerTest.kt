package com.nova.micfx.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin

class EqualizerTest {
    private fun gainDb(c: BiquadCoefficients, fs: Int, f: Double): Double {
        val filt = BiquadFilter(c)
        var inE = 0.0
        var outE = 0.0
        for (i in 0 until fs) {
            val x = sin(2.0 * PI * f * i / fs).toFloat()
            val y = filt.process(x)
            if (i >= fs / 2) { inE += x * x; outE += y * y }
        }
        return 10.0 * log10(outE / inE)
    }

    @Test fun allDesignsAreStableAcrossRatesAndGains() {
        for (fs in intArrayOf(48000, 44100, 16000)) {
            for (g in doubleArrayOf(-15.0, -6.0, 0.0, 6.0, 15.0)) {
                assertTrue(BiquadDesign.lowShelf(fs, 120.0, g).isStable())
                assertTrue(BiquadDesign.highShelf(fs, 6000.0, g).isStable())
                assertTrue(BiquadDesign.peaking(fs, 2500.0, g).isStable())
            }
            assertTrue(BiquadDesign.highPass(fs, 80.0).isStable())
            assertTrue(BiquadDesign.highPass(fs, 300.0).isStable())
            assertTrue(BiquadDesign.lowPass(fs, 3400.0).isStable())
        }
    }

    @Test fun unstableCoefficientsAreRejected() {
        val f = BiquadFilter()
        assertTrue(!f.setCoefficients(BiquadCoefficients(1.0, 0.0, 0.0, -2.5, 1.5)))
    }

    @Test fun zeroGainShelfIsTransparent() {
        val c = BiquadDesign.lowShelf(48000, 120.0, 0.0)
        assertEquals(1.0, c.b0, 1e-9); assertEquals(c.a1, c.b1, 1e-9); assertEquals(c.a2, c.b2, 1e-9)
    }

    @Test fun bassBoostRaisesLowFrequencies() {
        val g = gainDb(BiquadDesign.lowShelf(48000, 120.0, 12.0), 48000, 50.0)
        assertTrue("bass gain was $g dB", g in 8.0..12.5)
        val hi = gainDb(BiquadDesign.lowShelf(48000, 120.0, 12.0), 48000, 5000.0)
        assertTrue("treble must be nearly unchanged, was $hi dB", Math.abs(hi) < 1.0)
    }

    @Test fun trebleBoostRaisesHighFrequencies() {
        val g = gainDb(BiquadDesign.highShelf(48000, 6000.0, 12.0), 48000, 15000.0)
        assertTrue("treble gain was $g dB", g in 8.0..12.5)
    }

    @Test fun peakingGainMatchesAtCenter() {
        assertEquals(6.0, gainDb(BiquadDesign.peaking(48000, 2500.0, 6.0), 48000, 2500.0), 0.3)
    }

    @Test fun highPassRemovesRumbleButKeepsVoice() {
        val c = BiquadDesign.highPass(48000, 80.0)
        assertTrue(gainDb(c, 48000, 20.0) < -20.0)
        assertTrue(gainDb(c, 48000, 1000.0) > -0.5)
    }

    @Test fun neutralToneEqualizerDoesNotChangeAudio() {
        val eq = ToneEqualizer(48000)
        eq.setTargets(0f, 0f, 0f)
        eq.snapToTargets()
        assertTrue(eq.isNeutral)
        val src = TestSignals.noise(0.5f, 4800)
        val out = TestSignals.processInBlocks(src) { b, n -> eq.process(b, n) }
        for (i in src.indices) assertEquals(src[i], out[i], 1e-4f)
    }

    @Test fun extremeSettingsStayFiniteAndBounded() {
        val eq = ToneEqualizer(44100)
        eq.setTargets(12f, 12f, 12f)
        val out = TestSignals.processInBlocks(TestSignals.noise(0.5f, 44100 * 2), 441) { b, n -> eq.process(b, n) }
        for (v in out) { assertTrue(!v.isNaN() && !v.isInfinite()); assertTrue(Math.abs(v) < 20f) }
    }

    @Test fun neutralResetBringsTargetsBack() {
        val eq = ToneEqualizer(48000)
        eq.setTargets(10f, 5f, -8f); eq.snapToTargets()
        assertTrue(!eq.isNeutral)
        eq.setTargets(0f, 0f, 0f); eq.snapToTargets()
        assertTrue(eq.isNeutral)
    }
}
