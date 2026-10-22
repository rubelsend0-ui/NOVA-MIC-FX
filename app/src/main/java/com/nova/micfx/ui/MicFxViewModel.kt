package com.nova.micfx.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nova.micfx.NovaApp
import com.nova.micfx.audio.AudioEffectController
import com.nova.micfx.audio.CapabilitySummary
import com.nova.micfx.audio.CompatItem
import com.nova.micfx.audio.CompatibilityChecker
import com.nova.micfx.audio.DisplayStatus
import com.nova.micfx.audio.EffectParams
import com.nova.micfx.audio.EngineSettings
import com.nova.micfx.audio.EngineState
import com.nova.micfx.audio.Preset
import com.nova.micfx.audio.Presets
import com.nova.micfx.audio.StatusResolver
import com.nova.micfx.service.MicFxService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MicFxViewModel(app: Application) : AndroidViewModel(app) {
    private val nova = app as NovaApp
    val engine = nova.engine
    private val repo = nova.settings

    val snapshot = engine.snapshot
    val params = engine.params
    val settings: StateFlow<EngineSettings> = repo.settings
    val inputDevices = engine.devices.inputs
    val outputDevices = engine.devices.outputs

    val permissionGranted = MutableStateFlow(engine.hasPermission())
    val permissionPending = MutableStateFlow(false)
    val permissionAsked = MutableStateFlow(false)
    val compat = MutableStateFlow<List<CompatItem>>(emptyList())
    val capability = MutableStateFlow<CapabilitySummary?>(null)
    val supportedRates = MutableStateFlow<List<Int>>(emptyList())
    val checklist = MutableStateFlow<Set<Int>>(emptySet())
    val customPreset = MutableStateFlow(repo.loadCustomPreset())
    val activePresetId = MutableStateFlow<String?>(restorePresetId())
    val message = MutableStateFlow<String?>(null)
    val systemEffects = AudioEffectController.availability()

    val displayStatus: StateFlow<DisplayStatus> = combine(snapshot, permissionGranted, permissionPending) { s, g, pending ->
        StatusResolver.resolve(s, g, pending)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DisplayStatus.READY)

    private var saveJob: Job? = null

    private fun restorePresetId(): String? {
        val id = repo.loadLastPresetId() ?: return null
        val current = engine.params.value
        return when {
            id == Presets.CUSTOM_ID && repo.loadCustomPreset() == current -> id
            Presets.byId(id)?.let { Presets.apply(it) == current } == true -> id
            else -> null
        }
    }

    fun presetName(id: String?): String = when (id) {
        null -> "Modified (custom settings)"
        Presets.CUSTOM_ID -> "Custom"
        else -> Presets.byId(id)?.name ?: "Modified (custom settings)"
    }

    fun refreshPermission() { permissionGranted.value = engine.hasPermission() }
    fun setPermissionPending(pending: Boolean) { permissionPending.value = pending }
    fun onPermissionResult() { permissionPending.value = false; permissionAsked.value = true; refreshPermission() }

    fun startEngine() {
        refreshPermission()
        if (!permissionGranted.value) return
        val ctx = getApplication<Application>()
        ContextCompat.startForegroundService(ctx, Intent(ctx, MicFxService::class.java))
    }

    fun stopEngine() { engine.stop() }

    fun toggleEngine() {
        val s = snapshot.value.state
        if (s == EngineState.RUNNING || s == EngineState.STARTING || s == EngineState.RECOVERING) stopEngine() else startEngine()
    }

    fun update(transform: (EffectParams) -> EffectParams) {
        engine.updateParams(transform)
        activePresetId.value = null
        scheduleSave()
    }

    fun applyPreset(p: Preset) {
        engine.setParams(Presets.apply(p)); activePresetId.value = p.id; scheduleSave()
    }

    fun saveCustom() {
        val p = params.value
        if (repo.saveCustomPreset(p)) {
            customPreset.value = p
            activePresetId.value = Presets.CUSTOM_ID
            scheduleSave()
            message.value = "Custom preset saved on this device."
        } else {
            message.value = "Could not save the custom preset (storage error)."
        }
    }

    fun applyCustom() {
        val c = customPreset.value
        if (c == null) { message.value = "No custom preset has been saved yet."; return }
        engine.setParams(c); activePresetId.value = Presets.CUSTOM_ID; scheduleSave()
        message.value = "Custom preset restored."
    }

    fun resetEffects() {
        engine.updateParams { it.resetEffects() }; activePresetId.value = null; scheduleSave()
    }

    fun resetControls() {
        engine.setParams(EffectParams()); activePresetId.value = Presets.NATURAL.id; scheduleSave()
    }

    fun clearMessage() { message.value = null }

    fun updateSettings(t: (EngineSettings) -> EngineSettings) = repo.update(t)
    fun resetSettings() { repo.resetSettings(); message.value = "Settings reset to defaults." }

    fun runCompat() {
        engine.devices.refresh()
        val checker = CompatibilityChecker(getApplication(), engine.devices)
        compat.value = checker.run()
        capability.value = checker.summary()
        supportedRates.value = checker.supportedSampleRates()
    }

    fun toggleChecklist(i: Int) {
        val c = checklist.value
        checklist.value = if (c.contains(i)) c - i else c + i
    }

    fun onAppStopped() {
        if (!settings.value.runInBackground) engine.stop()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(600); repo.saveLast(engine.params.value, activePresetId.value) }
    }
}
