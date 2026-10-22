package com.nova.micfx.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PipelineTest {
    private val sr = 48000
    private fun run(p: AudioProcessingPipeline, src: FloatArray) =
        TestSignals.processInBlocks(src) { b, n -> p.process(b, n) }

    @Test fun bypassTurnsAllEffectsOff() {
        val src = TestSignals.sine(440.0, 0.3f, sr, sr)
        val all = EffectParams(
            bypass = true, noiseReductionEnabled = true, gateEnabled = true, bassDb = 12f, trebleDb = 12f,
            voiceMode = VoiceMode.DEEP, echoEnabled = true, reverbEnabled = true
        )
        val a = AudioProcessingPipeline(sr).also { it.setParams(all) }
        val b = AudioProcessingPipeline(sr).also { it.setParams(EffectParams()) }
        val oa = run(a, src); val ob = run(b, src)
        for (i in oa.indices) assertEquals(ob[i], oa[i], 1e-6f)
    }

    @Test fun enablingEchoChangesTheSignal() {
        val burst = FloatArray(sr) { i -> if (i < 480) 0.3f * TestSignals.sine(300.0, 1f, 480, sr)[i] else 0f }
        val off = AudioProcessingPipeline(sr).also { it.setParams(EffectParams(outputVolume = 1f)) }
        val on = AudioProcessingPipeline(sr).also {
            it.setParams(EffectParams(outputVolume = 1f, echoEnabled = true, echoDelayMs = 100f, echoMix = 0.6f, echoFeedback = 0f))
        }
        val a = run(off, burst); val b = run(on, burst)
        assertTrue(TestSignals.peak(a, 4800, 6000) < 1e-3f)
        assertTrue("echo must be audible 100 ms later", TestSignals.peak(b, 4800, 6000) > 0.02f)
    }

    @Test fun extremeSettingsNeverProduceNanOrOverCeiling() {
        val p = AudioProcessingPipeline(sr)
        p.setParams(EffectParams(
            inputGainDb = 24f, outputVolume = 1.5f, bassDb = 12f, midDb = 12f, trebleDb = 12f,
            noiseReductionEnabled = true, gateEnabled = true, voiceMode = VoiceMode.ROBOT,
            echoEnabled = true, echoFeedback = 0.8f, echoMix = 1f, reverbEnabled = true, reverbRoom = 1f, reverbMix = 1f
        ))
        val src = TestSignals.sine(700.0, 0.9f, sr * 2, sr)
        val noise = TestSignals.noise(0.5f, sr * 2)
        for (i in src.indices) src[i] += noise[i]
        val out = run(p, src)
        for (v in out) assertTrue(!v.isNaN() && Math.abs(v) <= p.ceiling + 1e-6f)
    }

    @Test fun setParamsValidatesValues() {
        val p = AudioProcessingPipeline(sr)
        p.setParams(EffectParams(inputGainDb = 1000f, bassDb = Float.NaN, echoFeedback = 9f))
        val v = p.getParams()
        assertEquals(24f, v.inputGainDb, 0f)
        assertEquals(0f, v.bassDb, 0f)
        assertEquals(Limits.ECHO_FEEDBACK_MAX, v.echoFeedback, 0f)
    }

    @Test fun deepVoiceReallyShiftsPitch() {
        val p = AudioProcessingPipeline(sr)
        p.setParams(EffectParams(voiceMode = VoiceMode.DEEP, outputVolume = 1f))
        val out = run(p, TestSignals.sine(440.0, 0.3f, sr * 2, sr))
        val f = TestSignals.estimateFrequency(out, sr, sr * 2, sr)
        assertEquals(349.0, f, 349.0 * 0.08)
    }

    @Test fun naturalVoiceKeepsPitch() {
        val p = AudioProcessingPipeline(sr)
        p.setParams(EffectParams(voiceMode = VoiceMode.NATURAL, outputVolume = 1f))
        val out = run(p, TestSignals.sine(440.0, 0.3f, sr * 2, sr))
        assertEquals(440.0, TestSignals.estimateFrequency(out, sr, sr * 2, sr), 5.0)
    }

    @Test fun radioVoiceBandLimitsTheSignal() {
        val low = AudioProcessingPipeline(sr).also { it.setParams(EffectParams(voiceMode = VoiceMode.RADIO, outputVolume = 1f)) }
        val ref = AudioProcessingPipeline(sr).also { it.setParams(EffectParams(outputVolume = 1f)) }
        val src = TestSignals.sine(12000.0, 0.3f, sr, sr)
        val lo = run(low, src); val re = run(ref, src)
        assertTrue(TestSignals.rms(lo, sr / 2, sr) < 0.2f * TestSignals.rms(re, sr / 2, sr))
    }

    @Test fun stageReportReflectsRealState() {
        fun stage(p: EffectParams, name: String) = StageReport.build(p).first { it.name.startsWith(name) }
        assertFalse(stage(EffectParams(), "Noise suppressor").enabled)
        assertTrue(stage(EffectParams(noiseReductionEnabled = true), "Noise suppressor").enabled)
        assertFalse(stage(EffectParams(noiseReductionEnabled = true, bypass = true), "Noise suppressor").enabled)
        assertTrue(stage(EffectParams(voiceMode = VoiceMode.DEEP), "Voice").enabled)
        assertFalse(stage(EffectParams(voiceMode = VoiceMode.NATURAL), "Voice").enabled)
        assertTrue(stage(EffectParams(bypass = true), "Limiter").enabled)
    }

    @Test fun voiceProfilesAreDistinct() {
        assertEquals(0f, VoiceProfile.of(VoiceMode.NATURAL).pitchSemitones, 0f)
        assertTrue(VoiceProfile.of(VoiceMode.DEEP).pitchSemitones < 0f)
        assertTrue(VoiceProfile.of(VoiceMode.BRIGHT).pitchSemitones > 0f)
        assertEquals(0f, VoiceProfile.of(VoiceMode.CUSTOM).pitchSemitones, 0f)
        assertTrue(VoiceProfile.of(VoiceMode.ROBOT).ringMix > 0f)
        assertTrue(VoiceProfile.of(VoiceMode.ALIEN).ringHz > VoiceProfile.of(VoiceMode.ROBOT).ringHz)
        assertTrue(VoiceProfile.of(VoiceMode.RADIO).bandHighHz > VoiceProfile.of(VoiceMode.RADIO).bandLowHz)
        assertEquals(0f, VoiceProfile.of(VoiceMode.WARM).pitchSemitones, 0f)
    }
}
