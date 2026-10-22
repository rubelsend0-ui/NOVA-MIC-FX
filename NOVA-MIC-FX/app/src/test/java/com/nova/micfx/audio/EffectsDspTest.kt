package com.nova.micfx.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectsDspTest {
    private val sr = 48000

    @Test fun echoProducesDelayedCopy() {
        val e = EchoProcessor(sr)
        e.configure(true, 100f, 0f, 0.5f)
        val d = FloatArray(6000)
        d[0] = 1f
        e.process(d, d.size)
        assertEquals(1f, d[0], 1e-6f)
        assertEquals(0f, d[2000], 1e-6f)
        assertEquals(0.5f, d[4800], 1e-4f)
    }

    @Test fun echoDisabledIsTransparent() {
        val e = EchoProcessor(sr)
        e.configure(false, 100f, 0.5f, 0.5f)
        val src = TestSignals.noise(0.3f, 4800)
        val out = TestSignals.processInBlocks(src) { b, n -> e.process(b, n) }
        for (i in src.indices) assertEquals(src[i], out[i], 0f)
    }

    @Test fun echoFeedbackCannotRunAway() {
        val e = EchoProcessor(sr)
        e.configure(true, 100f, 5f, 1f)         // 5.0 is clamped to the hard maximum
        val d = FloatArray(sr * 10)
        d[0] = 1f
        val out = TestSignals.processInBlocks(d) { b, n -> e.process(b, n) }
        for (v in out) assertTrue(!v.isNaN() && Math.abs(v) <= 1.5f)
        assertTrue(TestSignals.peak(out, sr * 9, sr * 10) < 1e-3f)
    }

    @Test fun echoResetsWhenDisabled() {
        val e = EchoProcessor(sr)
        e.configure(true, 100f, 0.5f, 0.5f)
        val burst = TestSignals.noise(0.5f, 4800)
        TestSignals.processInBlocks(burst) { b, n -> e.process(b, n) }
        e.configure(false, 100f, 0.5f, 0.5f)
        e.configure(true, 100f, 0.5f, 0.5f)
        val silence = FloatArray(9600)
        e.process(silence, silence.size)
        assertEquals(0f, TestSignals.peak(silence), 0f)
    }

    @Test fun reverbCreatesDecayingTail() {
        val r = ReverbProcessor(sr)
        r.configure(true, 1f, 1f)
        val d = FloatArray(sr * 6)
        d[0] = 1f
        val out = TestSignals.processInBlocks(d) { b, n -> r.process(b, n) }
        for (v in out) assertTrue(!v.isNaN() && !v.isInfinite())
        val early = TestSignals.rms(out, sr / 20, sr)
        val late = TestSignals.rms(out, sr * 5, sr * 6)
        assertTrue("early tail must exist", early > 1e-4f)
        assertTrue("tail must decay", late < early * 0.01f)
    }

    @Test fun reverbDisabledIsTransparent() {
        val r = ReverbProcessor(sr)
        r.configure(false, 0.5f, 0.5f)
        val src = TestSignals.noise(0.3f, 4800)
        val out = TestSignals.processInBlocks(src) { b, n -> r.process(b, n) }
        for (i in src.indices) assertEquals(src[i], out[i], 0f)
    }

    @Test fun pitchShiftDownChangesFrequencyNotSpeed() {
        val ps = PitchShifter(sr)
        ps.ratio = DspMath.semitonesToRatio(-4f)
        val src = TestSignals.sine(440.0, 0.5f, sr * 2, sr)
        val out = TestSignals.processInBlocks(src) { b, n -> ps.process(b, n) }
        val f = TestSignals.estimateFrequency(out, sr / 2, sr * 2, sr)
        assertEquals(440.0 * Math.pow(2.0, -4.0 / 12.0), f, 349.0 * 0.06)
        assertTrue("signal must last as long as the input", TestSignals.rms(out, sr * 2 - sr / 4, sr * 2) > 0.2f)
    }

    @Test fun pitchShiftUpOneOctave() {
        val ps = PitchShifter(sr)
        ps.ratio = 2f
        val src = TestSignals.sine(440.0, 0.5f, sr * 2, sr)
        val out = TestSignals.processInBlocks(src) { b, n -> ps.process(b, n) }
        assertEquals(880.0, TestSignals.estimateFrequency(out, sr / 2, sr * 2, sr), 880.0 * 0.06)
    }

    @Test fun pitchShifterAtUnityIsTransparent() {
        val ps = PitchShifter(sr)
        ps.ratio = 1f
        assertTrue(!ps.isActive)
        val src = TestSignals.noise(0.3f, 4800)
        val out = TestSignals.processInBlocks(src) { b, n -> ps.process(b, n) }
        for (i in src.indices) assertEquals(src[i], out[i], 0f)
    }

    @Test fun ringModulatorMixZeroIsTransparentAndFullMixModulates() {
        val rm = RingModulator(sr)
        val a = FloatArray(4800) { 1f }
        rm.process(a, a.size, 0f)
        for (v in a) assertEquals(1f, v, 0f)
        val b = FloatArray(sr) { 1f }
        RingModulator(sr).process(b, b.size, 1f)
        assertEquals(1f, TestSignals.peak(b), 0.01f)
        var sum = 0.0
        for (v in b) sum += v
        assertEquals(0.0, sum / b.size, 0.01)
    }
}
