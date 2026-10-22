package com.nova.micfx.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RobustnessTest {
    private val sr = 48000
    private fun run(p: AudioProcessingPipeline, src: FloatArray) = TestSignals.processInBlocks(src) { b, n -> p.process(b, n) }
    private fun everythingOn() = EffectParams(
        noiseReductionEnabled = true, gateEnabled = true, bassDb = 6f, trebleDb = 6f, voiceMode = VoiceMode.ALIEN,
        echoEnabled = true, reverbEnabled = true, dryWet = 0.7f, inputGainDb = 12f
    )

    @Test fun silenceStaysSilentAndFinite() {
        val p = AudioProcessingPipeline(sr).also { it.setParams(everythingOn()) }
        val out = run(p, FloatArray(sr))
        for (v in out) { assertTrue(!v.isNaN() && !v.isInfinite()); assertEquals(0f, v, 1e-6f) }
    }

    @Test fun veryQuietInputStaysFiniteAndQuiet() {
        val p = AudioProcessingPipeline(sr).also { it.setParams(EffectParams(inputGainDb = 0f, outputVolume = 1f)) }
        val out = run(p, TestSignals.noise(1e-5f, sr))
        for (v in out) assertTrue(!v.isNaN() && Math.abs(v) < 1e-3f)
    }

    @Test fun fullScaleInputNeverExceedsLimiterCeiling() {
        val p = AudioProcessingPipeline(sr).also { it.setParams(everythingOn().copy(outputVolume = 1.5f)) }
        val out = run(p, TestSignals.sine(500.0, 1f, sr * 2, sr))
        for (v in out) assertTrue(!v.isNaN() && Math.abs(v) <= p.ceiling + 1e-6f)
        assertTrue("output must still carry signal", TestSignals.peak(out) > 0.1f)
        assertTrue("clipping must have been counted", p.totalClipped > 0)
    }

    @Test fun limiterCeilingParameterIsHonoured() {
        val p = AudioProcessingPipeline(sr).also { it.setParams(EffectParams(limiterCeilingDb = -6f, inputGainDb = 12f, outputVolume = 1f)) }
        val out = run(p, TestSignals.sine(500.0, 0.8f, sr, sr))
        assertTrue(TestSignals.peak(out) <= DspMath.dbToLinear(-6f) + 1e-4f)
    }

    @Test fun parameterChangesWhileProcessingStayStable() {
        val p = AudioProcessingPipeline(sr)
        val tmp = FloatArray(480)
        val src = TestSignals.noise(0.4f, 480 * 60, 77L)
        val all = Presets.builtIn
        for (blk in 0 until 60) {
            p.setParams(Presets.apply(all[blk % all.size]))
            System.arraycopy(src, blk * 480, tmp, 0, 480)
            p.process(tmp, 480)
            for (v in tmp) assertTrue("block $blk", !v.isNaN() && !v.isInfinite() && Math.abs(v) <= p.ceiling + 1e-6f)
        }
    }

    @Test fun invalidParametersAreSanitisedBeforeUse() {
        val bad = EffectParams(
            inputGainDb = Float.NaN, outputVolume = Float.POSITIVE_INFINITY, dryWet = Float.NaN,
            limiterCeilingDb = 50f, echoDelayMs = Float.NaN, reverbRoom = Float.NaN, pitchSemitones = Float.NaN
        )
        val p = AudioProcessingPipeline(sr).also { it.setParams(bad) }
        val out = run(p, TestSignals.sine(440.0, 0.3f, sr / 2, sr))
        for (v in out) assertTrue(!v.isNaN() && !v.isInfinite())
        assertEquals(Limits.LIMITER_MAX_DB, p.getParams().limiterCeilingDb, 0f)
    }

    @Test fun pcmConversionRoundTripsAndClamps() {
        for (x in floatArrayOf(-1f, -0.5f, -0.001f, 0f, 0.001f, 0.5f, 0.999f)) {
            assertEquals(x, DspMath.shortToFloat(DspMath.floatToShort(x)), 1.5e-4f)
        }
        assertEquals(32767, DspMath.floatToShort(1f).toInt())
        assertEquals(32767, DspMath.floatToShort(10f).toInt())
        assertEquals(-32768, DspMath.floatToShort(-10f).toInt())
        assertEquals(0, DspMath.floatToShort(Float.NaN).toInt())
        assertEquals(-1f, DspMath.shortToFloat(Short.MIN_VALUE), 0f)
    }

    @Test fun pitchAddsToVoiceCharacterAndIsClamped() {
        assertEquals(-6f, VoiceProfile.totalPitch(EffectParams(voiceMode = VoiceMode.DEEP, pitchSemitones = -2f)), 0f)
        assertEquals(-12f, VoiceProfile.totalPitch(EffectParams(voiceMode = VoiceMode.DEEP, pitchSemitones = -12f)), 0f)
        assertEquals(0f, VoiceProfile.totalPitch(EffectParams(voiceMode = VoiceMode.DEEP, pitchSemitones = -2f, bypass = true)), 0f)
        assertEquals(3f, VoiceProfile.totalPitch(EffectParams(voiceMode = VoiceMode.CUSTOM, pitchSemitones = 3f)), 0f)
    }

    @Test fun pitchSliderReallyShiftsFrequency() {
        val p = AudioProcessingPipeline(sr).also { it.setParams(EffectParams(pitchSemitones = -6f, outputVolume = 1f)) }
        val out = run(p, TestSignals.sine(440.0, 0.3f, sr * 2, sr))
        assertEquals(440.0 * Math.pow(2.0, -0.5), TestSignals.estimateFrequency(out, sr, sr * 2, sr), 311.0 * 0.08)
    }

    @Test fun dryWetZeroReturnsTheUnprocessedVoice() {
        val src = TestSignals.sine(440.0, 0.3f, sr, sr)
        val wetOnly = AudioProcessingPipeline(sr).also { it.setParams(EffectParams(outputVolume = 1f)) }
        val dryMix = AudioProcessingPipeline(sr).also {
            it.setParams(EffectParams(outputVolume = 1f, dryWet = 0f, voiceMode = VoiceMode.ROBOT, echoEnabled = true, reverbEnabled = true, pitchSemitones = -5f))
        }
        val a = run(wetOnly, src); val b = run(dryMix, src)
        for (i in sr / 2 until sr) assertEquals(a[i], b[i], 2e-3f)
    }

    @Test fun resetEffectsKeepsLevelsButClearsEffects() {
        val p = EffectParams(inputGainDb = 6f, outputVolume = 1.1f, limiterCeilingDb = -3f, highPassEnabled = false,
            echoEnabled = true, reverbEnabled = true, voiceMode = VoiceMode.DEEP, pitchSemitones = 3f, noiseReductionEnabled = true, dryWet = 0.2f)
        val r = p.resetEffects()
        assertEquals(6f, r.inputGainDb, 0f); assertEquals(1.1f, r.outputVolume, 0f); assertEquals(-3f, r.limiterCeilingDb, 0f)
        assertFalse(r.highPassEnabled)
        assertFalse(r.echoEnabled); assertFalse(r.reverbEnabled); assertFalse(r.noiseReductionEnabled)
        assertEquals(VoiceMode.NATURAL, r.voiceMode); assertEquals(0f, r.pitchSemitones, 0f); assertEquals(1f, r.dryWet, 0f)
    }

    @Test fun presetLibrarySearchAndIds() {
        assertEquals(Presets.builtIn.size, Presets.search("").size)
        assertTrue(Presets.search("pitch").map { it.id }.containsAll(listOf("low_pitch", "high_pitch")))
        assertEquals(listOf("robot"), Presets.search("ROBOT").map { it.id })
        assertTrue(Presets.search("zzzzqq").isEmpty())
        assertEquals(Presets.builtIn.size, Presets.builtIn.map { it.id }.toSet().size)
        assertEquals("alien", Presets.byId("alien")?.id)
        assertNull(Presets.byId("does_not_exist"))
        assertEquals("robot", Presets.matching(Presets.apply(Presets.ROBOT))?.id)
        assertNull(Presets.matching(Presets.apply(Presets.ROBOT).copy(bassDb = 1f)))
    }

    @Test fun everyPresetReallyChangesProcessing() {
        val base = Presets.apply(Presets.NATURAL)
        for (pr in Presets.builtIn) if (pr.id != "natural") assertTrue(pr.id, Presets.apply(pr) != base)
        val src = TestSignals.sine(440.0, 0.3f, sr, sr)
        val clean = run(AudioProcessingPipeline(sr).also { it.setParams(base) }, src)
        for (id in listOf("deep", "low_pitch", "high_pitch", "robot", "alien", "radio", "echo", "reverb")) {
            val out = run(AudioProcessingPipeline(sr).also { it.setParams(Presets.apply(Presets.byId(id)!!)) }, src)
            var diff = 0f
            for (i in out.indices) diff = maxOf(diff, Math.abs(out[i] - clean[i]))
            assertTrue("preset $id must alter the audio", diff > 0.02f)
        }
    }

    @Test fun settingsCodecRoundTripsAndValidates() {
        val s = EngineSettings(48000, 3, 12, 7, true, true, false, true, true, true, true)
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
        assertEquals(EngineSettings(), SettingsCodec.decode(null))
        assertEquals(EngineSettings(), SettingsCodec.decode("junk;;=;a"))
        val bad = SettingsCodec.decode("sampleRate=22050;bufferMult=99;inId=-5;outId=-9")
        assertEquals(0, bad.preferredSampleRate); assertEquals(4, bad.bufferMultiplier)
        assertEquals(-1, bad.preferredInputId); assertEquals(-1, bad.preferredOutputId)
    }

    @Test fun missingPreferredDevicesFallBackToDefault() {
        assertEquals(7, DevicePreference.resolve(7, listOf(3, 7)))
        assertNull(DevicePreference.resolve(9, listOf(3, 7)))
        assertNull(DevicePreference.resolve(-1, listOf(3, 7)))
        assertTrue(DevicePreference.isMissing(9, listOf(3, 7)))
        assertFalse(DevicePreference.isMissing(-1, listOf(3, 7)))
        assertFalse(DevicePreference.isMissing(3, listOf(3, 7)))
    }

    @Test fun statusIsDerivedFromRealEngineState() {
        fun st(s: EngineState, f: FailureKind = FailureKind.NONE) = EngineSnapshot(state = s, failure = f)
        assertEquals(DisplayStatus.READY, StatusResolver.resolve(st(EngineState.IDLE), true, false))
        assertEquals(DisplayStatus.PERMISSION_NEEDED, StatusResolver.resolve(st(EngineState.IDLE), false, false))
        assertEquals(DisplayStatus.REQUESTING_PERMISSION, StatusResolver.resolve(st(EngineState.IDLE), false, true))
        assertEquals(DisplayStatus.STARTING, StatusResolver.resolve(st(EngineState.STARTING), true, false))
        assertEquals(DisplayStatus.MONITORING, StatusResolver.resolve(st(EngineState.RUNNING), true, false))
        assertEquals(DisplayStatus.RECOVERING, StatusResolver.resolve(st(EngineState.RECOVERING), true, false))
        assertEquals(DisplayStatus.UNSUPPORTED_ROUTE, StatusResolver.resolve(st(EngineState.ERROR, FailureKind.UNSUPPORTED_ROUTE), true, false))
        assertEquals(DisplayStatus.MIC_BUSY, StatusResolver.resolve(st(EngineState.ERROR, FailureKind.MIC_BUSY), true, false))
        assertEquals(DisplayStatus.PERMISSION_NEEDED, StatusResolver.resolve(st(EngineState.ERROR, FailureKind.PERMISSION), true, false))
        assertEquals(DisplayStatus.DEVICE_DISCONNECTED, StatusResolver.resolve(st(EngineState.IDLE, FailureKind.DEVICE_DISCONNECTED), true, false))
        assertEquals(DisplayStatus.ERROR, StatusResolver.resolve(st(EngineState.ERROR, FailureKind.OTHER), true, false))
    }

    @Test fun capabilitiesNeverClaimDirectInjection() {
        for (mic in listOf(true, false)) for (perm in listOf(true, false)) for (hp in listOf(true, false)) for (usb in listOf(true, false)) {
            val c = CapabilityModel.summarize(mic, perm, hp, usb)
            assertEquals(CompatStatus.NO, c.directInjection.status)
            assertTrue(c.externalHardware.status != CompatStatus.OK)   // never "verified"
        }
        assertEquals(CompatStatus.OK, CapabilityModel.summarize(true, true, true, false).localMonitoring.status)
        assertEquals(CompatStatus.WARN, CapabilityModel.summarize(true, true, false, false).localMonitoring.status)
        assertEquals(CompatStatus.NO, CapabilityModel.summarize(false, true, true, false).processingInsideNova.status)
    }
}
