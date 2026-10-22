package com.nova.micfx.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetsTest {
    @Test fun everyBuiltInPresetIsWithinValidRanges() {
        for (p in Presets.builtIn) assertEquals(p.name, p.params, p.params.validated())
        assertEquals(6, Presets.builtIn.size)
        assertEquals(Presets.builtIn.size, Presets.builtIn.map { it.id }.toSet().size)
    }

    @Test fun applyingPresetSetsRealParameters() {
        val g = Presets.apply(Presets.GAMING)
        assertTrue(g.noiseReductionEnabled); assertTrue(g.gateEnabled); assertEquals(6f, g.inputGainDb, 0f)
        val d = Presets.apply(Presets.DEEP_VOICE)
        assertEquals(VoiceMode.DEEP, d.voiceMode)
        val n = Presets.apply(Presets.NOISE_REDUCTION)
        assertEquals(0.85f, n.noiseReductionStrength, 0f)
        assertEquals(VoiceMode.RADIO, Presets.apply(Presets.RADIO).voiceMode)
        assertTrue(Presets.apply(Presets.CLEAR_VOICE).midDb > 0f)
    }

    @Test fun applyingPresetLeavesBypassOff() {
        assertFalse(Presets.apply(Presets.GAMING.copy(params = Presets.GAMING.params.copy(bypass = true))).bypass)
    }

    @Test fun presetsActivateTheMatchingStages() {
        val stages = StageReport.build(Presets.apply(Presets.GAMING))
        assertTrue(stages.first { it.name == "Noise suppressor" }.enabled)
        assertTrue(stages.first { it.name == "Noise gate" }.enabled)
        assertFalse(stages.first { it.name == "Echo effect" }.enabled)
    }

    @Test fun codecRoundTripKeepsEverything() {
        val p = EffectParams(
            bypass = true, inputGainDb = 7.5f, outputVolume = 1.2f, highPassEnabled = false,
            noiseReductionEnabled = true, noiseReductionStrength = 0.77f, gateEnabled = true, gateThresholdDb = -42f,
            bassDb = 3.5f, midDb = -2f, trebleDb = 4f, voiceMode = VoiceMode.ROBOT, customPitchSemitones = 3f,
            echoEnabled = true, echoDelayMs = 420f, echoFeedback = 0.55f, echoMix = 0.4f,
            reverbEnabled = true, reverbRoom = 0.9f, reverbMix = 0.35f
        )
        assertEquals(p, ParamsCodec.decode(ParamsCodec.encode(p)))
    }

    @Test fun codecHandlesBadInputSafely() {
        assertEquals(EffectParams(), ParamsCodec.decode(null))
        assertEquals(EffectParams(), ParamsCodec.decode(""))
        assertEquals(EffectParams(), ParamsCodec.decode("garbage;;==;x"))
        assertEquals(24f, ParamsCodec.decode("inputGainDb=999").inputGainDb, 0f)
        assertEquals(0f, ParamsCodec.decode("bassDb=NaN").bassDb, 0f)
        assertEquals(VoiceMode.NATURAL, ParamsCodec.decode("voiceMode=DOES_NOT_EXIST").voiceMode)
    }

    @Test fun validationClampsEveryRange() {
        val v = EffectParams(
            outputVolume = 9f, noiseReductionStrength = 3f, gateThresholdDb = 10f, bassDb = -99f, midDb = 99f,
            trebleDb = 99f, customPitchSemitones = 99f, echoDelayMs = 1f, echoFeedback = 2f, echoMix = -1f,
            reverbRoom = 5f, reverbMix = 5f
        ).validated()
        assertEquals(Limits.VOLUME_MAX, v.outputVolume, 0f)
        assertEquals(1f, v.noiseReductionStrength, 0f)
        assertEquals(Limits.GATE_MAX_DB, v.gateThresholdDb, 0f)
        assertEquals(-Limits.TONE_MAX_DB, v.bassDb, 0f)
        assertEquals(Limits.TONE_MAX_DB, v.midDb, 0f)
        assertEquals(Limits.PITCH_MAX_ST, v.customPitchSemitones, 0f)
        assertEquals(Limits.ECHO_DELAY_MIN_MS, v.echoDelayMs, 0f)
        assertEquals(Limits.ECHO_FEEDBACK_MAX, v.echoFeedback, 0f)
        assertEquals(0f, v.echoMix, 0f)
        assertEquals(1f, v.reverbRoom, 0f)
    }
}
