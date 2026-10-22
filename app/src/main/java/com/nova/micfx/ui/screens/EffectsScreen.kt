package com.nova.micfx.ui.screens

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.micfx.audio.EngineState
import com.nova.micfx.audio.Limits
import com.nova.micfx.audio.VoiceMode
import com.nova.micfx.audio.VoiceProfile
import com.nova.micfx.ui.MicFxViewModel
import com.nova.micfx.ui.components.Dim
import com.nova.micfx.ui.components.EffectCard
import com.nova.micfx.ui.components.LabeledSlider
import com.nova.micfx.ui.components.SectionTitle
import com.nova.micfx.ui.components.ToggleRow

@Composable
fun EffectsScreen(vm: MicFxViewModel) {
    val p by vm.params.collectAsStateWithLifecycle()
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val running = snap.state == EngineState.RUNNING
    val m = snap.meters
    val tone = -Limits.TONE_MAX_DB..Limits.TONE_MAX_DB
    val fx = !p.bypass

    ScreenScaffold {
        SectionTitle("Effects")
        Dim("All effects process NOVA's own monitoring output only. They are not sent to other apps. Changes apply instantly without restarting audio.")

        EffectCard("Input gain and output", "Software gain on captured samples and the monitoring volume. A peak limiter always protects the output.", true, running) {
            LabeledSlider("Mic gain (software)", "%+.0f dB".format(p.inputGainDb), p.inputGainDb,
                Limits.GAIN_MIN_DB..Limits.GAIN_MAX_DB, { v -> vm.update { it.copy(inputGainDb = v) } })
            LabeledSlider("Output gain", "${(p.outputVolume * 100).toInt()}%", p.outputVolume,
                0f..Limits.VOLUME_MAX, { v -> vm.update { it.copy(outputVolume = v) } })
            LabeledSlider("Limiter ceiling", "%.1f dB".format(p.limiterCeilingDb), p.limiterCeilingDb,
                Limits.LIMITER_MIN_DB..Limits.LIMITER_MAX_DB, { v -> vm.update { it.copy(limiterCeilingDb = v) } })
            if (running) Dim("Limiter reduction now: %.1f dB".format(m.limiterReductionDb))
            ToggleRow("High-pass filter (80 Hz)", "Removes rumble and handling noise", p.highPassEnabled) { on -> vm.update { it.copy(highPassEnabled = on) } }
        }

        EffectCard("Pitch", "Real duration-preserving pitch shifting (adds about 20 ms delay). Adds to the voice character below.",
            fx && VoiceProfile.totalPitch(p) != 0f, running) {
            LabeledSlider("Pitch", "%+.0f semitones".format(p.pitchSemitones), p.pitchSemitones,
                -Limits.PITCH_MAX_ST..Limits.PITCH_MAX_ST, { v -> vm.update { it.copy(pitchSemitones = Math.round(v).toFloat()) } })
            Dim("Total applied now: %+.0f semitones".format(VoiceProfile.totalPitch(p)))
        }

        EffectCard("Voice character", "Filter, EQ, pitch and modulation profile. " + p.voiceMode.description,
            fx && p.voiceMode != VoiceMode.NATURAL && p.voiceMode != VoiceMode.CUSTOM, running) {
            ChipRow(VoiceMode.values().map { it.label to it }, { it == p.voiceMode }) { mode -> vm.update { it.copy(voiceMode = mode) } }
        }

        EffectCard("Noise reduction", "Adaptive suppressor reduces steady noise between speech. Not AI noise cancellation.",
            fx && (p.noiseReductionEnabled || p.gateEnabled), running) {
            ToggleRow("Noise suppressor", "Strength sets the maximum reduction (up to 24 dB)", p.noiseReductionEnabled) { on -> vm.update { it.copy(noiseReductionEnabled = on) } }
            LabeledSlider("Strength", "${(p.noiseReductionStrength * 100).toInt()}%", p.noiseReductionStrength,
                0f..1f, { v -> vm.update { it.copy(noiseReductionStrength = v) } }, enabled = p.noiseReductionEnabled)
            ToggleRow("Noise gate", "Silences audio below the threshold", p.gateEnabled) { on -> vm.update { it.copy(gateEnabled = on) } }
            LabeledSlider("Gate threshold", "%.0f dB".format(p.gateThresholdDb), p.gateThresholdDb,
                Limits.GATE_MIN_DB..Limits.GATE_MAX_DB, { v -> vm.update { it.copy(gateThresholdDb = v) } }, enabled = p.gateEnabled)
            if (running) Dim("Measured: noise floor %.0f dB, reduction %.1f dB, voice ${if (m.voiceActive) "detected" else "not detected"}".format(m.noiseFloorDb, m.reductionDb))
            Dim("System noise suppressor: " + (if (!vm.systemEffects.noiseSuppressor) "not available on this device"
                else if (running) (if (snap.systemNsActive) "ACTIVE" else "available, not enabled (Settings)") else "available (enable in Settings)"))
            Dim("System echo canceler: " + (if (!vm.systemEffects.echoCanceler) "not available on this device"
                else if (running) (if (snap.systemAecActive) "ACTIVE" else "available, not enabled (Settings)") else "available (Settings)"))
        }

        EffectCard("Equalizer", "Bass (120 Hz shelf), voice presence (2.5 kHz) and treble (6 kHz shelf) biquad filters.",
            fx && (p.bassDb != 0f || p.midDb != 0f || p.trebleDb != 0f), running) {
            LabeledSlider("Bass", "%+.0f dB".format(p.bassDb), p.bassDb, tone, { v -> vm.update { it.copy(bassDb = v) } })
            LabeledSlider("Voice presence", "%+.0f dB".format(p.midDb), p.midDb, tone, { v -> vm.update { it.copy(midDb = v) } })
            LabeledSlider("Treble", "%+.0f dB".format(p.trebleDb), p.trebleDb, tone, { v -> vm.update { it.copy(trebleDb = v) } })
            Button(onClick = { vm.update { it.copy(bassDb = 0f, midDb = 0f, trebleDb = 0f) } }) { Text("Neutral") }
        }

        EffectCard("Echo", "Audible echo effect. This is different from acoustic echo cancellation.", fx && p.echoEnabled, running) {
            ToggleRow("Echo", "Delay line with damped feedback (cannot run away)", p.echoEnabled) { on -> vm.update { it.copy(echoEnabled = on) } }
            LabeledSlider("Delay", "${p.echoDelayMs.toInt()} ms", p.echoDelayMs, Limits.ECHO_DELAY_MIN_MS..Limits.ECHO_DELAY_MAX_MS,
                { v -> vm.update { it.copy(echoDelayMs = v) } }, enabled = p.echoEnabled)
            LabeledSlider("Feedback", "${(p.echoFeedback * 100).toInt()}%", p.echoFeedback, 0f..Limits.ECHO_FEEDBACK_MAX,
                { v -> vm.update { it.copy(echoFeedback = v) } }, enabled = p.echoEnabled)
            LabeledSlider("Echo amount", "${(p.echoMix * 100).toInt()}%", p.echoMix, 0f..1f,
                { v -> vm.update { it.copy(echoMix = v) } }, enabled = p.echoEnabled)
        }

        EffectCard("Reverb", "Room reverb with size and amount.", fx && p.reverbEnabled, running) {
            ToggleRow("Reverb", "Comb and all-pass reverb", p.reverbEnabled) { on -> vm.update { it.copy(reverbEnabled = on) } }
            LabeledSlider("Room size", "${(p.reverbRoom * 100).toInt()}%", p.reverbRoom, 0f..1f,
                { v -> vm.update { it.copy(reverbRoom = v) } }, enabled = p.reverbEnabled)
            LabeledSlider("Reverb amount", "${(p.reverbMix * 100).toInt()}%", p.reverbMix, 0f..1f,
                { v -> vm.update { it.copy(reverbMix = v) } }, enabled = p.reverbEnabled)
        }

        EffectCard("Dry / wet mix", "Blends your unprocessed voice with the processed voice. 100% = fully processed.", fx && p.dryWet < 0.999f, running) {
            LabeledSlider("Processed amount", "${(p.dryWet * 100).toInt()}%", p.dryWet, 0f..1f, { v -> vm.update { it.copy(dryWet = v) } })
            Dim("Mixing the dry voice with a pitch-shifted voice can sound phasey; keep 100% for strong pitch effects.")
        }

        EffectCard("Bypass", "Turns every effect off. Gain, output volume and limiter stay active.", p.bypass, running) {
            ToggleRow("Bypass all effects", "A/B compare against your clean voice", p.bypass) { on -> vm.update { it.copy(bypass = on) } }
            Button(onClick = { vm.resetEffects() }) { Text("Reset effects") }
        }
    }
}
