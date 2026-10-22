package com.nova.micfx.ui.screens

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.micfx.audio.EngineState
import com.nova.micfx.audio.Limits
import com.nova.micfx.audio.VoiceMode
import com.nova.micfx.ui.MicFxViewModel
import com.nova.micfx.ui.components.Dim
import com.nova.micfx.ui.components.GlassCard
import com.nova.micfx.ui.components.LabeledSlider
import com.nova.micfx.ui.components.SectionTitle
import com.nova.micfx.ui.components.ToggleRow

@Composable
fun EffectsScreen(vm: MicFxViewModel) {
    val p by vm.params.collectAsStateWithLifecycle()
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val running = snap.state == EngineState.RUNNING
    val tone = -Limits.TONE_MAX_DB..Limits.TONE_MAX_DB

    ScreenScaffold {
        SectionTitle("Effects")
        Dim("All effects process the app's own monitoring output only. They are not sent to other apps.")

        GlassCard {
            SectionTitle("Gain and output")
            LabeledSlider("Mic gain (software)", "%+.0f dB".format(p.inputGainDb), p.inputGainDb,
                Limits.GAIN_MIN_DB..Limits.GAIN_MAX_DB, { v -> vm.update { it.copy(inputGainDb = v) } })
            LabeledSlider("Monitor volume", "${(p.outputVolume * 100).toInt()}%", p.outputVolume,
                0f..Limits.VOLUME_MAX, { v -> vm.update { it.copy(outputVolume = v) } })
            ToggleRow("High-pass filter (80 Hz)", "Removes rumble and handling noise", p.highPassEnabled) { on -> vm.update { it.copy(highPassEnabled = on) } }
            Dim("A peak limiter is always active so output never exceeds about -0.3 dBFS. Gain changes are smoothed.")
        }

        GlassCard {
            SectionTitle("Noise reduction")
            ToggleRow("Adaptive noise suppressor", "Reduces steady background noise between speech. Not AI noise cancellation.",
                p.noiseReductionEnabled) { on -> vm.update { it.copy(noiseReductionEnabled = on) } }
            LabeledSlider("Strength", "${(p.noiseReductionStrength * 100).toInt()}%", p.noiseReductionStrength,
                0f..1f, { v -> vm.update { it.copy(noiseReductionStrength = v) } }, enabled = p.noiseReductionEnabled)
            ToggleRow("Noise gate", "Silences audio below the threshold", p.gateEnabled) { on -> vm.update { it.copy(gateEnabled = on) } }
            LabeledSlider("Gate threshold", "%.0f dB".format(p.gateThresholdDb), p.gateThresholdDb,
                Limits.GATE_MIN_DB..Limits.GATE_MAX_DB, { v -> vm.update { it.copy(gateThresholdDb = v) } }, enabled = p.gateEnabled)
            if (running) Dim("Measured: noise floor %.0f dB, suppression %.1f dB, voice ${if (snap.meters.voiceActive) "detected" else "not detected"}"
                .format(snap.meters.noiseFloorDb, snap.meters.reductionDb))
            Dim("System noise suppressor: " + (if (!vm.systemEffects.noiseSuppressor) "not available on this device"
                else if (running) (if (snap.systemNsActive) "ACTIVE" else "available, not enabled (turn on in Settings)") else "available (enable in Settings)"))
            Dim("System echo canceler: " + (if (!vm.systemEffects.echoCanceler) "not available on this device"
                else if (running) (if (snap.systemAecActive) "ACTIVE" else "available, not enabled (Settings)") else "available (Settings)"))
        }

        GlassCard {
            SectionTitle("Tone")
            LabeledSlider("Bass (120 Hz shelf)", "%+.0f dB".format(p.bassDb), p.bassDb, tone, { v -> vm.update { it.copy(bassDb = v) } })
            LabeledSlider("Voice presence (2.5 kHz)", "%+.0f dB".format(p.midDb), p.midDb, tone, { v -> vm.update { it.copy(midDb = v) } })
            LabeledSlider("Treble (6 kHz shelf)", "%+.0f dB".format(p.trebleDb), p.trebleDb, tone, { v -> vm.update { it.copy(trebleDb = v) } })
            Button(onClick = { vm.update { it.copy(bassDb = 0f, midDb = 0f, trebleDb = 0f) } }) { Text("Neutral") }
        }

        GlassCard {
            SectionTitle("Voice changer")
            ChipRow(VoiceMode.values().map { it.label to it }, { it == p.voiceMode }) { mode -> vm.update { it.copy(voiceMode = mode) } }
            Dim(p.voiceMode.description)
            if (p.voiceMode == VoiceMode.CUSTOM) {
                LabeledSlider("Pitch shift", "%+.0f semitones".format(p.customPitchSemitones), p.customPitchSemitones,
                    -Limits.PITCH_MAX_ST..Limits.PITCH_MAX_ST, { v -> vm.update { it.copy(customPitchSemitones = Math.round(v).toFloat()) } })
            }
            Dim("Pitch shifting adds roughly 20 ms of delay and some artifacts on fast speech.")
        }

        GlassCard {
            SectionTitle("Echo effect")
            Dim("This adds an audible echo. It is different from acoustic echo cancellation.")
            ToggleRow("Echo", "Delay line with damped feedback", p.echoEnabled) { on -> vm.update { it.copy(echoEnabled = on) } }
            LabeledSlider("Delay", "${p.echoDelayMs.toInt()} ms", p.echoDelayMs, Limits.ECHO_DELAY_MIN_MS..Limits.ECHO_DELAY_MAX_MS,
                { v -> vm.update { it.copy(echoDelayMs = v) } }, enabled = p.echoEnabled)
            LabeledSlider("Feedback", "${(p.echoFeedback * 100).toInt()}%", p.echoFeedback, 0f..Limits.ECHO_FEEDBACK_MAX,
                { v -> vm.update { it.copy(echoFeedback = v) } }, enabled = p.echoEnabled)
            LabeledSlider("Wet level", "${(p.echoMix * 100).toInt()}%", p.echoMix, 0f..1f,
                { v -> vm.update { it.copy(echoMix = v) } }, enabled = p.echoEnabled)
        }

        GlassCard {
            SectionTitle("Reverb")
            ToggleRow("Reverb", "Comb and all-pass reverb", p.reverbEnabled) { on -> vm.update { it.copy(reverbEnabled = on) } }
            LabeledSlider("Room size", "${(p.reverbRoom * 100).toInt()}%", p.reverbRoom, 0f..1f,
                { v -> vm.update { it.copy(reverbRoom = v) } }, enabled = p.reverbEnabled)
            LabeledSlider("Wet / dry mix", "${(p.reverbMix * 100).toInt()}%", p.reverbMix, 0f..1f,
                { v -> vm.update { it.copy(reverbMix = v) } }, enabled = p.reverbEnabled)
        }

        GlassCard {
            ToggleRow("Bypass all effects", "Keeps gain, monitor volume and limiter", p.bypass) { on -> vm.update { it.copy(bypass = on) } }
            Button(onClick = { vm.resetControls() }) { Text("Reset everything") }
        }
        if (s.requestSystemAgc) Dim("System AGC requested in Settings; it may fight with the manual gain.")
    }
}
