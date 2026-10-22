package com.nova.micfx.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.micfx.audio.EngineState
import com.nova.micfx.audio.Presets
import com.nova.micfx.audio.StageReport
import com.nova.micfx.ui.MicFxViewModel
import com.nova.micfx.ui.components.Dim
import com.nova.micfx.ui.components.GlassCard
import com.nova.micfx.ui.components.SectionTitle
import com.nova.micfx.ui.theme.Good
import com.nova.micfx.ui.theme.TextDim
import androidx.compose.foundation.layout.fillMaxWidth

@Composable
fun PresetsScreen(vm: MicFxViewModel) {
    val active by vm.activePresetId.collectAsStateWithLifecycle()
    val custom by vm.customPreset.collectAsStateWithLifecycle()
    val p by vm.params.collectAsStateWithLifecycle()
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val running = snap.state == EngineState.RUNNING

    ScreenScaffold {
        SectionTitle("One-tap presets")
        Dim("Each preset sets real parameters on the audio engine. Stages below show what is on.")
        Presets.builtIn.forEach { preset ->
            GlassCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(preset.name + if (active == preset.id) "  (active)" else "")
                    Button(onClick = { vm.applyPreset(preset) }) { Text("Apply") }
                }
                Dim(preset.description)
            }
        }
        GlassCard {
            Text("Custom" + if (active == "custom") "  (active)" else "")
            Dim(if (custom == null) "Nothing saved yet. Set your controls, then press Save current." else "A custom setup is saved on this device.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.saveCustom() }) { Text("Save current") }
                OutlinedButton(onClick = { vm.applyCustom() }, enabled = custom != null) { Text("Restore") }
            }
        }
        GlassCard {
            SectionTitle("Current stages")
            StageReport.build(p).forEach {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(it.name)
                    Text(if (!it.enabled) "off" else if (running) "ACTIVE" else "ready", color = if (it.enabled && running) Good else TextDim)
                }
            }
        }
    }
}
