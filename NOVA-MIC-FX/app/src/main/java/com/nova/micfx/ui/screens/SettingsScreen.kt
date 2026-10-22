package com.nova.micfx.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.micfx.audio.BufferSizing
import com.nova.micfx.audio.DeviceItem
import com.nova.micfx.ui.MicFxViewModel
import com.nova.micfx.ui.components.Dim
import com.nova.micfx.ui.components.GlassCard
import com.nova.micfx.ui.components.SectionTitle
import com.nova.micfx.ui.components.ToggleRow
import com.nova.micfx.ui.theme.Warn

@Composable
private fun DevicePicker(title: String, devices: List<DeviceItem>, selected: Int, onSelect: (Int) -> Unit) {
    Text(title)
    Row(Modifier.fillMaxWidth().clickable { onSelect(-1) }, verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected == -1, onClick = { onSelect(-1) }); Text("System default")
    }
    devices.forEach { d ->
        Row(Modifier.fillMaxWidth().clickable { onSelect(d.id) }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected == d.id, onClick = { onSelect(d.id) }); Text(d.name)
        }
    }
}

@Composable
fun SettingsScreen(vm: MicFxViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val ins by vm.inputDevices.collectAsStateWithLifecycle()
    val outs by vm.outputDevices.collectAsStateWithLifecycle()
    val av = vm.systemEffects

    ScreenScaffold {
        SectionTitle("Settings")
        Dim("Audio settings are applied the next time you press Start.")

        GlassCard {
            SectionTitle("Audio format")
            Text("Preferred sample rate")
            ChipRow(listOf("Auto" to 0) + BufferSizing.SUPPORTED_RATES.map { "$it Hz" to it }, { it == s.preferredSampleRate }) { r ->
                vm.updateSettings { it.copy(preferredSampleRate = r) }
            }
            Text("Buffer size (higher = more stable, more latency)")
            ChipRow(listOf("1x" to 1, "2x" to 2, "3x" to 3, "4x" to 4), { it == s.bufferMultiplier }) { m ->
                vm.updateSettings { it.copy(bufferMultiplier = m) }
            }
        }

        GlassCard {
            SectionTitle("Devices")
            DevicePicker("Preferred input", ins, s.preferredInputId) { id -> vm.updateSettings { it.copy(preferredInputId = id) } }
            DevicePicker("Preferred output", outs, s.preferredOutputId) { id -> vm.updateSettings { it.copy(preferredOutputId = id) } }
            Dim("Android may ignore a preference. The real routes are shown on the main screen while running.")
        }

        GlassCard {
            SectionTitle("Monitoring")
            ToggleRow("Allow phone-speaker monitoring", "Risk of loud feedback. Off = blocked unless headphones are used.", s.allowSpeakerMonitoring) { on ->
                vm.updateSettings { it.copy(allowSpeakerMonitoring = on) }
            }
            if (s.allowSpeakerMonitoring) Text("Feedback warning: keep volume very low.", color = Warn)
            ToggleRow("Keep running in background", "Uses a foreground service and a persistent notification. Off = microphone stops when the app leaves the screen.", s.runInBackground) { on ->
                vm.updateSettings { it.copy(runInBackground = on) }
            }
            ToggleRow("Voice-communication audio source", "May enable platform processing and different routing.", s.useCommunicationSource) { on ->
                vm.updateSettings { it.copy(useCommunicationSource = on) }
            }
        }

        GlassCard {
            SectionTitle("System audio effects")
            ToggleRow("System noise suppressor", if (av.noiseSuppressor) "Available" else "Not available on this device", s.requestSystemNs, av.noiseSuppressor) { on ->
                vm.updateSettings { it.copy(requestSystemNs = on) }
            }
            ToggleRow("System echo canceler (AEC)", if (av.echoCanceler) "Available" else "Not available on this device", s.requestSystemAec, av.echoCanceler) { on ->
                vm.updateSettings { it.copy(requestSystemAec = on) }
            }
            ToggleRow("System automatic gain control", if (av.gainControl) "Available (conflicts with manual gain)" else "Not available on this device", s.requestSystemAgc, av.gainControl) { on ->
                vm.updateSettings { it.copy(requestSystemAgc = on) }
            }
            Dim("The main screen reports whether each effect actually became active.")
        }

        GlassCard {
            SectionTitle("Privacy")
            Dim("Audio is processed on your device only. Nothing is recorded to storage and nothing is uploaded. The app has no internet permission. Settings and presets are stored locally.")
        }

        GlassCard {
            SectionTitle("Troubleshooting")
            Dim("No sound: connect headphones, raise Monitor volume. Silent input: close other apps using the mic. " +
                "Crackling: raise buffer size. Bluetooth: expect delay; prefer wired. Howling: lower volume, use closed headphones.")
        }
    }
}
