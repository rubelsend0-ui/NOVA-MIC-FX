package com.nova.micfx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.micfx.audio.DisplayStatus
import com.nova.micfx.audio.EngineState
import com.nova.micfx.audio.Limits
import com.nova.micfx.audio.Presets
import com.nova.micfx.audio.StageReport
import com.nova.micfx.audio.VoiceProfile
import com.nova.micfx.ui.MicFxViewModel
import com.nova.micfx.ui.components.Dim
import com.nova.micfx.ui.components.GlassCard
import com.nova.micfx.ui.components.LabeledSlider
import com.nova.micfx.ui.components.LevelMeter
import com.nova.micfx.ui.components.SectionTitle
import com.nova.micfx.ui.components.StatusPill
import com.nova.micfx.ui.components.ToggleRow
import com.nova.micfx.ui.theme.Danger
import com.nova.micfx.ui.theme.DeepBlue
import com.nova.micfx.ui.theme.ElectricBlue
import com.nova.micfx.ui.theme.Good
import com.nova.micfx.ui.theme.TextDim
import com.nova.micfx.ui.theme.Violet
import com.nova.micfx.ui.theme.Warn

private fun statusColor(s: DisplayStatus): Color = when (s) {
    DisplayStatus.MONITORING -> Good
    DisplayStatus.READY -> ElectricBlue
    DisplayStatus.STARTING, DisplayStatus.REQUESTING_PERMISSION, DisplayStatus.RECOVERING, DisplayStatus.STOPPING -> Warn
    else -> Danger
}

private fun recoveryHint(s: DisplayStatus): String? = when (s) {
    DisplayStatus.PERMISSION_NEEDED -> "Allow the microphone: tap Start and accept the prompt, or open app settings."
    DisplayStatus.MIC_BUSY -> "Close other apps that use the microphone (calls, recorders, games), then press Start."
    DisplayStatus.UNSUPPORTED_ROUTE -> "Connect wired, USB or Bluetooth headphones, then press Start. Speaker monitoring can be allowed in Settings (feedback risk)."
    DisplayStatus.DEVICE_DISCONNECTED -> "Reconnect the device or choose another one in Settings, then press Start."
    DisplayStatus.ERROR -> "Press Start to retry. If it repeats, open Compat > Troubleshooting."
    DisplayStatus.RECOVERING -> "The audio stream is restarting automatically. Monitoring resumes if it succeeds."
    else -> null
}

@Composable
fun HomeScreen(vm: MicFxViewModel, onStartStop: () -> Unit, openAppSettings: () -> Unit) {
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val p by vm.params.collectAsStateWithLifecycle()
    val status by vm.displayStatus.collectAsStateWithLifecycle()
    val granted by vm.permissionGranted.collectAsStateWithLifecycle()
    val asked by vm.permissionAsked.collectAsStateWithLifecycle()
    val presetId by vm.activePresetId.collectAsStateWithLifecycle()
    val outs by vm.outputDevices.collectAsStateWithLifecycle()
    val running = snap.state == EngineState.RUNNING
    val active = running || snap.state == EngineState.RECOVERING
    val busy = snap.state == EngineState.STARTING || snap.state == EngineState.STOPPING
    val headphones = outs.any { it.headphoneLike }
    val m = snap.meters

    ScreenScaffold {
        Text(
            "NOVA MIC FX", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.headlineMedium.copy(brush = Brush.horizontalGradient(listOf(ElectricBlue, DeepBlue, Violet)))
        )
        Dim("Voice effects with live headphone monitoring. Local only: not connected to games or voice-chat apps.")

        GlassCard {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(132.dp).clip(CircleShape)
                        .background(Brush.linearGradient(if (active) listOf(Danger, Violet) else listOf(ElectricBlue, DeepBlue, Violet)))
                        .clickable(enabled = !busy) { onStartStop() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when (snap.state) {
                            EngineState.RUNNING, EngineState.RECOVERING -> "STOP"
                            EngineState.STARTING, EngineState.STOPPING -> "..."
                            else -> "START"
                        },
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center
                    )
                }
            }
            Text(if (active) "Stop Monitoring" else "Start Monitoring", style = MaterialTheme.typography.labelLarge,
                color = TextDim, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(status.label, statusColor(status))
                StatusPill(if (headphones) "Headphones connected" else "No headphones", if (headphones) Good else Warn)
            }
            Text(snap.message, style = MaterialTheme.typography.bodyMedium)
            snap.errorDetail?.let { Dim(it) }
            recoveryHint(status)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Warn) }
            if (!granted) {
                Text("Microphone permission: NOT GRANTED", color = Warn)
                if (asked) OutlinedButton(onClick = openAppSettings) { Text("Open app settings") }
            } else Text("Microphone permission: granted", color = Good, style = MaterialTheme.typography.bodySmall)
            if (snap.inputSilent) Text("Input is digital silence. Another app may be using the microphone or Android is blocking it.", color = Warn, style = MaterialTheme.typography.bodySmall)
        }

        GlassCard {
            SectionTitle("Session")
            Dim("Voice preset: ${vm.presetName(presetId)}")
            Dim("Input device: " + if (active) snap.inputDevice else "not monitoring")
            Dim("Output device: " + if (active) snap.outputDevice + (if (snap.outputVerifiedSafe) " (route verified)" else "") else "not monitoring")
            if (active) {
                Dim("Sample rate ${snap.sampleRate} Hz, block ${snap.blockFrames} frames")
                Dim(if (snap.measuredBufferLatencyMs >= 0f)
                    "Latency: ~${"%.0f".format(snap.measuredBufferLatencyMs)} ms measured buffering (hardware delay not included). Buffer-based estimate ${"%.0f".format(snap.latencyEstimateMs)} ms."
                else "Latency: measuring...")
                Dim("Underruns: ${snap.underruns}, late reads: ${snap.lateReads}")
                snap.effectNotes.forEach { Dim(it) }
            }
            val on = StageReport.build(p).filter { it.enabled && it.name != "Input gain" && it.name != "Limiter" && it.name != "High-pass 80 Hz" }
            Dim("Effects: " + if (on.isEmpty()) "none" else on.joinToString(" / ") { it.name.removePrefix("Voice: ") })
            val pitch = VoiceProfile.totalPitch(p)
            Dim("Pitch: " + if (pitch == 0f) "unchanged" else "%+.0f semitones".format(pitch))
            Dim("Routing: local headphone monitoring only. NOVA cannot send this audio into Free Fire, WePlay or any other app.")
        }

        GlassCard {
            SectionTitle("Live levels")
            LevelMeter("Input (after gain)", m.inputPeak, m.inputPeak, m.rawInputClipped)
            LevelMeter("Processed output", m.outputPeak, m.holdPeak, m.clipping)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (m.clipping) "CLIPPING" else "No clipping", color = if (m.clipping) Danger else Good, fontSize = 13.sp)
                Text(if (running) (if (m.voiceActive) "Voice detected" else "No voice") else "Voice: -", color = TextDim, fontSize = 13.sp)
            }
            if (running) Dim("Noise floor %.0f dB, suppression %.1f dB, limiter %.1f dB, total clips ${m.clipCount}".format(m.noiseFloorDb, m.reductionDb, m.limiterReductionDb))
        }

        GlassCard {
            SectionTitle("Voice preset")
            ChipRow(Presets.builtIn.map { it.name to it }, { it.id == presetId }) { pr -> vm.applyPreset(pr) }
        }

        GlassCard {
            SectionTitle("Quick controls")
            LabeledSlider("Mic gain (software)", "%+.0f dB".format(p.inputGainDb), p.inputGainDb,
                Limits.GAIN_MIN_DB..Limits.GAIN_MAX_DB, { v -> vm.update { it.copy(inputGainDb = v) } })
            Dim("Software gain multiplies captured samples. It does not change the phone's hardware microphone sensitivity.")
            LabeledSlider("Output volume", "${(p.outputVolume * 100).toInt()}%", p.outputVolume,
                0f..Limits.VOLUME_MAX, { v -> vm.update { it.copy(outputVolume = v) } })
            LabeledSlider("Pitch", "%+.0f st".format(p.pitchSemitones), p.pitchSemitones,
                -Limits.PITCH_MAX_ST..Limits.PITCH_MAX_ST, { v -> vm.update { it.copy(pitchSemitones = Math.round(v).toFloat()) } })
            ToggleRow("Noise reduction", if (p.noiseReductionEnabled) "Adaptive suppressor" else "Off",
                p.noiseReductionEnabled) { on -> vm.update { it.copy(noiseReductionEnabled = on) } }
            ToggleRow("Echo effect", "Deliberate echo (not echo cancellation)", p.echoEnabled) { on -> vm.update { it.copy(echoEnabled = on) } }
            ToggleRow("Reverb", "Room reverb", p.reverbEnabled) { on -> vm.update { it.copy(reverbEnabled = on) } }
            ToggleRow("Bypass all effects", "Keeps gain, volume and limiter", p.bypass) { on -> vm.update { it.copy(bypass = on) } }
            Button(onClick = { vm.resetControls() }) { Text("Reset controls") }
        }
        Dim("Feedback warning: use headphones. Speaker monitoring is blocked by default because it causes loud howling.")
    }
}
