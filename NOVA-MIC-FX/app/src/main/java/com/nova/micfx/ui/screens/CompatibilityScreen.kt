package com.nova.micfx.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.micfx.audio.CompatStatus
import com.nova.micfx.ui.MicFxViewModel
import com.nova.micfx.ui.components.Dim
import com.nova.micfx.ui.components.GlassCard
import com.nova.micfx.ui.components.SectionTitle
import com.nova.micfx.ui.theme.Cyan
import com.nova.micfx.ui.theme.Danger
import com.nova.micfx.ui.theme.Good
import com.nova.micfx.ui.theme.Warn

private val CHECKLIST = listOf(
    "Headphones are connected and the volume is low",
    "The target app/game has microphone permission",
    "The target app uses the phone microphone (not a separate USB/Bluetooth mic)",
    "I tested by recording in the target app and listening back",
    "I understand NOVA MIC FX output is NOT sent into the target app"
)

@Composable
fun CompatibilityScreen(vm: MicFxViewModel) {
    val items by vm.compat.collectAsStateWithLifecycle()
    val checked by vm.checklist.collectAsStateWithLifecycle()
    val granted by vm.permissionGranted.collectAsStateWithLifecycle()
    LaunchedEffect(granted) { vm.runCompat() }

    ScreenScaffold {
        SectionTitle("Device and game compatibility")
        GlassCard {
            Text("Important Android limitation", color = Danger)
            Dim("A normal Android app cannot replace the microphone input of a game, Discord or a phone call. " +
                "Audio processed inside NOVA MIC FX stays inside NOVA MIC FX. External-app compatibility is therefore always reported as NOT SUPPORTED here.")
        }
        items.forEach {
            GlassCard {
                val c = when (it.status) { CompatStatus.OK -> Good; CompatStatus.WARN -> Warn; CompatStatus.NO -> Danger; CompatStatus.INFO -> Cyan }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(it.title)
                    Text(it.status.name, color = c)
                }
                Dim(it.detail)
            }
        }
        Button(onClick = { vm.runCompat() }) { Text("Re-check") }
        GlassCard {
            SectionTitle("Manual checklist")
            CHECKLIST.forEachIndexed { i, t ->
                Row(Modifier.fillMaxWidth().clickable { vm.toggleChecklist(i) }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checked.contains(i), onCheckedChange = { vm.toggleChecklist(i) })
                    Text(t)
                }
            }
            Dim("This checklist is a personal reminder only. It does not verify anything automatically.")
        }
    }
}
