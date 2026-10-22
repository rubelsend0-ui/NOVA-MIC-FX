package com.nova.micfx.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.nova.micfx.ui.components.StatusPill
import com.nova.micfx.ui.components.EffectTag
import com.nova.micfx.ui.theme.ElectricBlue
import com.nova.micfx.ui.theme.Navy
import com.nova.micfx.ui.theme.NavyLight

@Composable
fun PresetsScreen(vm: MicFxViewModel) {
    val active by vm.activePresetId.collectAsStateWithLifecycle()
    val custom by vm.customPreset.collectAsStateWithLifecycle()
    val p by vm.params.collectAsStateWithLifecycle()
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val running = snap.state == EngineState.RUNNING
    var query by rememberSaveable { mutableStateOf("") }
    val results = Presets.search(query)

    ScreenScaffold {
        SectionTitle("Voice presets")
        Dim("Each preset sets real parameters on the audio engine. Selecting one updates the processing instantly.")
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            label = { Text("Search presets") }, modifier = Modifier.fillMaxWidth()
        )
        if (results.isEmpty()) Dim("No preset matches \"$query\".")
        results.forEach { preset ->
            val selected = active == preset.id
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (selected) NavyLight else Navy),
                border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) ElectricBlue else NavyLight)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(preset.name)
                        if (selected) StatusPill("SELECTED", ElectricBlue)
                    }
                    Dim(preset.description)
                    Button(onClick = { vm.applyPreset(preset) }) { Text(if (selected) "Reapply" else "Select") }
                }
            }
        }

        GlassCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Custom")
                if (active == Presets.CUSTOM_ID) StatusPill("SELECTED", ElectricBlue)
            }
            Dim(if (custom == null) "Nothing saved yet. Set your controls, then press Save current." else "A custom setup is saved on this device.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.saveCustom() }) { Text("Save current") }
                OutlinedButton(onClick = { vm.applyCustom() }, enabled = custom != null) { Text("Restore") }
            }
        }

        GlassCard {
            Text("Reset to defaults")
            Dim("Restores the Natural Voice settings.")
            OutlinedButton(onClick = { vm.resetControls() }) { Text("Reset to defaults") }
        }

        GlassCard {
            SectionTitle("Current stages")
            StageReport.build(p).forEach {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(it.name)
                    EffectTag(it.enabled, running)
                }
            }
        }
    }
}
