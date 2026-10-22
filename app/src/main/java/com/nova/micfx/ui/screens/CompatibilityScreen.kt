package com.nova.micfx.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.os.Build
import com.nova.micfx.audio.CompatItem
import com.nova.micfx.audio.CompatStatus
import com.nova.micfx.audio.EngineState
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
    "The voice-chat app has microphone permission",
    "I tried the voice-chat app's own voice effects / noise suppression",
    "I tested by recording in the target app and listening back",
    "I understand NOVA MIC FX output is NOT sent into the target app"
)

private fun color(s: CompatStatus): Color = when (s) {
    CompatStatus.OK -> Good; CompatStatus.WARN -> Warn; CompatStatus.NO -> Danger; CompatStatus.INFO -> Cyan
}

@Composable
private fun ItemCard(it: CompatItem) {
    GlassCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(it.title, modifier = Modifier.weight(1f))
            Text(it.status.name, color = color(it.status))
        }
        Dim(it.detail)
    }
}

@Composable
fun CompatibilityScreen(vm: MicFxViewModel) {
    val items by vm.compat.collectAsStateWithLifecycle()
    val cap by vm.capability.collectAsStateWithLifecycle()
    val checked by vm.checklist.collectAsStateWithLifecycle()
    val granted by vm.permissionGranted.collectAsStateWithLifecycle()
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val status by vm.displayStatus.collectAsStateWithLifecycle()
    val ins by vm.inputDevices.collectAsStateWithLifecycle()
    val outs by vm.outputDevices.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val rates by vm.supportedRates.collectAsStateWithLifecycle()
    LaunchedEffect(granted) { vm.runCompat() }

    ScreenScaffold {
        SectionTitle("Compatibility and diagnostics")

        cap?.let {
            Dim("Four different things. A success in one never implies another:")
            ItemCard(it.processingInsideNova); ItemCard(it.localMonitoring); ItemCard(it.externalHardware); ItemCard(it.directInjection)
        }

        GlassCard {
            Text("Free Fire, WePlay and other voice-chat apps", color = Danger)
            Dim("Status: NOT SUPPORTED / NOT VERIFIED. Android isolates every app's microphone. Audio played by NOVA never becomes another app's microphone input, " +
                "and successfully opening AudioRecord or AudioTrack proves nothing about third-party apps. NOVA does not use root, AudioPlaybackCapture, accessibility or overlays for this.")
            Dim("Practical alternatives: (1) use the voice-chat app's own voice effects and noise suppression; (2) a USB audio interface or hardware voice changer, " +
                "device- and app-dependent; (3) on a PC, a virtual audio cable. NOVA remains useful as a live headphone voice-effects monitor.")
        }

        GlassCard {
            SectionTitle("Engine and route")
            Dim("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            Dim("Engine status: ${status.label} (${snap.state.name})")
            Dim("Selected input: " + (ins.firstOrNull { it.id == settings.preferredInputId }?.name ?: "system default"))
            Dim("Selected output: " + (outs.firstOrNull { it.id == settings.preferredOutputId }?.name ?: "system default"))
            if (snap.state == EngineState.RUNNING) {
                Dim("Active input route: ${snap.inputDevice}")
                Dim("Active output route: ${snap.outputDevice}")
                Dim("Route verified as headphone-safe: ${if (snap.outputVerifiedSafe) "yes" else "no"}")
            } else Dim("Active route: not monitoring (nothing to verify)")
            Dim("Supported sample rates: " + if (rates.isEmpty()) "none found" else rates.joinToString(", ") { "$it Hz" })
        }

        GlassCard {
            SectionTitle("Available input devices")
            if (ins.isEmpty()) Dim("None reported.")
            ins.forEach { Dim("${it.name}  [id ${it.id}]" + if (it.id == settings.preferredInputId) "  (preferred)" else "") }
            SectionTitle("Available output devices")
            if (outs.isEmpty()) Dim("None reported.")
            outs.forEach { Dim("${it.name}  [id ${it.id}]" + (if (it.headphoneLike) "  (headphone-safe)" else "") + if (it.id == settings.preferredOutputId) "  (preferred)" else "") }
        }

        items.forEach { ItemCard(it) }
        Button(onClick = { vm.runCompat() }) { Text("Re-check") }

        GlassCard {
            SectionTitle("Known limitations")
            Dim("Android 10+ may silence the microphone for apps in the background. Manufacturers may stop background services. " +
                "Bluetooth adds 100+ ms delay. Pitch shifting adds ~20 ms and can sound artificial on fast speech. Latency shown is measured buffering only, not hardware delay.")
            SectionTitle("Troubleshooting")
            Dim("No sound: connect headphones and raise output volume. Microphone Busy: close calls/recorders/games. Crackling: increase buffer size in Settings. " +
                "Route blocked: use headphones or allow speaker monitoring (feedback risk). Silent input: keep NOVA in the foreground and check permission.")
        }

        GlassCard {
            SectionTitle("Manual checklist")
            CHECKLIST.forEachIndexed { i, t ->
                Row(Modifier.fillMaxWidth().clickable { vm.toggleChecklist(i) }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checked.contains(i), onCheckedChange = { vm.toggleChecklist(i) })
                    Text(t)
                }
            }
            Dim("A personal reminder only. It does not verify anything automatically. Physical-hardware behavior must be tested by hand.")
        }
    }
}
