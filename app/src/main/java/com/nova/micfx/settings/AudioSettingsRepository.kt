package com.nova.micfx.settings

import android.content.Context
import com.nova.micfx.audio.EffectParams
import com.nova.micfx.audio.EngineSettings
import com.nova.micfx.audio.ParamsCodec
import com.nova.micfx.audio.SettingsCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Local-only persistence (SharedPreferences). Nothing is uploaded; no audio is ever stored. */
class AudioSettingsRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nova_mic_fx", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(SettingsCodec.decode(prefs.getString("engineSettings", null)))
    val settings: StateFlow<EngineSettings> = _settings

    fun update(transform: (EngineSettings) -> EngineSettings) {
        val s = transform(_settings.value).validated()
        _settings.value = s
        prefs.edit().putString("engineSettings", SettingsCodec.encode(s)).apply()
    }

    fun resetSettings() {
        _settings.value = EngineSettings()
        prefs.edit().remove("engineSettings").apply()
    }

    /** @return true only if the data was really written to storage. */
    fun saveCustomPreset(p: EffectParams): Boolean =
        prefs.edit().putString("customPreset", ParamsCodec.encode(p)).commit()

    fun loadCustomPreset(): EffectParams? = prefs.getString("customPreset", null)?.let { ParamsCodec.decode(it) }

    fun saveLast(p: EffectParams, presetId: String?) {
        prefs.edit().putString("lastParams", ParamsCodec.encode(p)).putString("lastPreset", presetId).apply()
    }

    fun loadLastParams(): EffectParams = ParamsCodec.decode(prefs.getString("lastParams", null))
    fun loadLastPresetId(): String? = prefs.getString("lastPreset", null)
}
