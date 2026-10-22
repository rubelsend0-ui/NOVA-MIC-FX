package com.nova.micfx.ui

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nova.micfx.NovaApp
import com.nova.micfx.audio.AudioEffectController
import com.nova.micfx.audio.CompatItem
import com.nova.micfx.audio.CompatibilityChecker
import com.nova.micfx.audio.EffectParams
import com.nova.micfx.audio.EngineState
import com.nova.micfx.audio.EngineSettings
import com.nova.micfx.audio.Preset
import com.nova.micfx.audio.Presets
import com.nova.micfx.service.MicFxService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val permissionAsked = MutableStateFlow(false)
    val compat = MutableStateFlow<List<CompatItem>>(emptyList())
    val checklist = MutableStateFlow<Set<Int>>(emptySet())
    val customPreset = MutableStateFlow(repo.loadCustomPreset())
    val activePresetId = MutableStateFlow<String?>(null)
    val systemEffects = AudioEffectController.availability()

    private var saveJob: Job? = null

    fun refreshPermission() { permissionGranted.value = engine.hasPermission() }
    fun onPermissionResult() { permissionAsked.value = true; refreshPermission() }

    fun startEngine() {
        refreshPermission()
        if (!permissionGranted.value) return
        val ctx = getApplication<Application>()
        ContextCompat.startForegroundService(ctx, Intent(ctx, MicFxService::class.java))
    }

    fun stopEngine() { engine.stop() }

    fun toggleEngine() {
        val s = snapshot.value.state
        if (s == EngineState.RUNNING || s == EngineState.STARTING) stopEngine() else startEngine()
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
        repo.saveCustomPreset(p); customPreset.value = p
    }

    fun applyCustom() {
        customPreset.value?.let { engine.setParams(it); activePresetId.value = "custom"; scheduleSave() }
    }

    fun resetControls() {
        engine.setParams(EffectParams()); activePresetId.value = Presets.NATURAL.id; scheduleSave()
    }

    fun updateSettings(t: (EngineSettings) -> EngineSettings) = repo.update(t)

    fun runCompat() {
        engine.devices.refresh()
        compat.value = CompatibilityChecker(getApplication(), engine.devices).run()
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
        saveJob = viewModelScope.launch { delay(600); repo.saveLastParams(engine.params.value) }
    }
}
