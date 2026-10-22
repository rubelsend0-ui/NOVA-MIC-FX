package com.nova.micfx.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.micfx.audio.EngineState
import com.nova.micfx.audio.StageReport
import com.nova.micfx.audio.VoiceMode
import com.nova.micfx.audio.Limits
import com.nova.micfx.ui.MicFxViewModel
import com.nova.micfx.ui.components.Dim
import com.nova.micfx.ui.components.GlassCard
import com.nova.micfx.ui.components.LabeledSlider
import com.nova.micfx.ui.components.LevelMeter
import com.nova.micfx.ui.components.SectionTitle
import com.nova.micfx.ui.components.ToggleRow
import com.nova.micfx.ui.theme.Danger
import com.nova.micfx.ui.theme.ElectricBlue
import com.nova.micfx.ui.theme.Good
import com.nova.micfx.ui.theme.TextDim
import com.nova.micfx.ui.theme.Violet
import com.nova.micfx.ui.theme.Warn

@Composable
fun HomeScreen(vm: MicFxViewModel, onStartStop: () -> Unit, openAppSettings: () -> Unit) {
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val p by vm.params.collectAsStateWithLifecycle()
    val granted by vm.permissionGranted.collectAsStateWithLifecycle()
    val asked by vm.permissionAsked.collectAsStateWithLifecycle()
    val running = snap.state == EngineState.RUNNING
    val busy = snap.state == EngineState.STARTING || snap.state == EngineState.STOPPING
    val m = snap.meters

    ScreenScaffold {
        Text(
            "NOVA MIC FX", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.headlineMedium.copy(brush = Brush.horizontalGradient(listOf(ElectricBlue, Violet)))
        )
        Dim("Live microphone effects for headphone monitoring (Mode A). Does not feed other apps.")

        GlassCard {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(132.dp).clip(CircleShape)
                        .background(Brush.linearGradient(
                            if (running) listOf(Danger, Violet) else listOf(ElectricBlue, Violet)))
                        .clickable(enabled = !busy) { onStartStop() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when (snap.state) { EngineState.RUNNING -> "STOP"; EngineState.STARTING -> "..."; EngineState.STOPPING -> "..."; else -> "START" },
                        fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center
                    )
                }
            }
            Text("Engine: ${snap.state.name}", fontWeight = FontWeight.SemiBold,
                color = when (snap.state) { EngineState.RUNNING -> Good; EngineState.ERROR -> Danger; else -> TextDim })
            Text(snap.message, style = MaterialTheme.typography.bodyMedium)
            snap.errorDetail?.let { Dim(it) }
            if (!granted) {
                Text("Microphone permission: NOT GRANTED", color = Warn)
                if (asked) OutlinedButton(onClick = openAppSettings) { Text("Open app settings") }
            } else Text("Microphone permission: granted", color = Good, style = MaterialTheme.typography.bodySmall)
            if (running) {
                Dim("Input: ${snap.inputDevice}")
                Dim("Output: ${snap.outputDevice} (verified headphone-safe route)")
                Dim("Sample rate ${snap.sampleRate} Hz, block ${snap.blockFrames} frames, latency estimate ~${"%.0f".format(snap.latencyEstimateMs)} ms")
                Dim("Underruns: ${snap.underruns}, late reads: ${snap.lateReads}")
                if (snap.inputSilent) Text("Input is digital silence. Another app may be using the microphone or Android is blocking it.", color = Warn, style = MaterialTheme.typography.bodySmall)
                snap.effectNotes.forEach { Dim(it) }
            }
            Dim("Audio routing: processed audio goes to your headphones only. Not connected to any game or call.")
        }

        GlassCard {
            SectionTitle("Live levels")
            LevelMeter("Input (after gain)", m.inputPeak, m.inputPeak, m.rawInputClipped)
            LevelMeter("Processed output", m.outputPeak, m.holdPeak, m.clipping)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (m.clipping) "CLIPPING" else "No clipping", color = if (m.clipping) Danger else Good, fontSize = 13.sp)
                Text(if (running) (if (m.voiceActive) "Voice detected" else "No voice") else "Voice: -", color = TextDim, fontSize = 13.sp)
            }
            if (running) Dim("Noise floor %.0f dB, suppression now %.1f dB, total clips ${m.clipCount}".format(m.noiseFloorDb, m.reductionDb))
        }

        GlassCard {
            SectionTitle("Quick controls")
            LabeledSlider("Mic gain (software)", "%+.0f dB".format(p.inputGainDb), p.inputGainDb,
                Limits.GAIN_MIN_DB..Limits.GAIN_MAX_DB, { v -> vm.update { it.copy(inputGainDb = v) } })
            Dim("Software gain multiplies captured samples. It does not change the phone's hardware microphone sensitivity.")
            LabeledSlider("Monitor volume", "${(p.outputVolume * 100).toInt()}%", p.outputVolume,
                0f..Limits.VOLUME_MAX, { v -> vm.update { it.copy(outputVolume = v) } })
            ToggleRow("Noise reduction", if (p.noiseReductionEnabled) "Adaptive suppressor running when engine is on" else "Off",
                p.noiseReductionEnabled) { on -> vm.update { it.copy(noiseReductionEnabled = on) } }
            LabeledSlider("Bass", "%+.0f dB".format(p.bassDb), p.bassDb, -Limits.TONE_MAX_DB..Limits.TONE_MAX_DB,
                { v -> vm.update { it.copy(bassDb = v) } })
            LabeledSlider("Treble", "%+.0f dB".format(p.trebleDb), p.trebleDb, -Limits.TONE_MAX_DB..Limits.TONE_MAX_DB,
                { v -> vm.update { it.copy(trebleDb = v) } })
            Text("Voice effect", style = MaterialTheme.typography.bodyMedium)
            ChipRow(VoiceMode.values().map { it.label to it }, { it == p.voiceMode }) { mode -> vm.update { it.copy(voiceMode = mode) } }
            Dim(p.voiceMode.description)
            ToggleRow("Echo effect", "Deliberate echo (not echo cancellation)", p.echoEnabled) { on -> vm.update { it.copy(echoEnabled = on) } }
            ToggleRow("Reverb", "Room reverb", p.reverbEnabled) { on -> vm.update { it.copy(reverbEnabled = on) } }
            ToggleRow("Bypass all effects", "Keeps gain, volume and limiter", p.bypass) { on -> vm.update { it.copy(bypass = on) } }
            Button(onClick = { vm.resetControls() }) { Text("Reset controls") }
        }

        GlassCard {
            SectionTitle("Processing status")
            StageReport.build(p).forEach {
                val on = it.enabled
                val label = when { !on -> "off"; running -> "ACTIVE"; else -> "ready" }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(it.name, fontSize = 13.sp)
                    Text(label, fontSize = 13.sp, color = if (on && running) Good else TextDim)
                }
            }
            Dim("Feedback warning: use headphones. Speaker monitoring is blocked by default because it causes loud howling.")
        }
    }
}
