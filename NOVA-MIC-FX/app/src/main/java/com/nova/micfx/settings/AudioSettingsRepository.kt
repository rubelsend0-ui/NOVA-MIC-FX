package com.nova.micfx.settings

import android.content.Context
import com.nova.micfx.audio.EffectParams
import com.nova.micfx.audio.EngineSettings
import com.nova.micfx.audio.ParamsCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Local-only persistence (SharedPreferences). Nothing is uploaded; no audio is ever stored. */
class AudioSettingsRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("nova_mic_fx", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<EngineSettings> = _settings

    private fun load(): EngineSettings = EngineSettings(
        preferredSampleRate = prefs.getInt("sampleRate", 0),
        bufferMultiplier = prefs.getInt("bufferMult", 2).coerceIn(1, 4),
        preferredInputId = prefs.getInt("inId", -1),
        preferredOutputId = prefs.getInt("outId", -1),
        allowSpeakerMonitoring = prefs.getBoolean("allowSpeaker", false),
        runInBackground = prefs.getBoolean("background", false),
        useCommunicationSource = prefs.getBoolean("commSource", false),
        requestSystemNs = prefs.getBoolean("sysNs", false),
        requestSystemAec = prefs.getBoolean("sysAec", false),
        requestSystemAgc = prefs.getBoolean("sysAgc", false)
    )

    fun update(transform: (EngineSettings) -> EngineSettings) {
        val s = transform(_settings.value)
        _settings.value = s
        prefs.edit()
            .putInt("sampleRate", s.preferredSampleRate)
            .putInt("bufferMult", s.bufferMultiplier.coerceIn(1, 4))
            .putInt("inId", s.preferredInputId)
            .putInt("outId", s.preferredOutputId)
            .putBoolean("allowSpeaker", s.allowSpeakerMonitoring)
            .putBoolean("background", s.runInBackground)
            .putBoolean("commSource", s.useCommunicationSource)
            .putBoolean("sysNs", s.requestSystemNs)
            .putBoolean("sysAec", s.requestSystemAec)
            .putBoolean("sysAgc", s.requestSystemAgc)
            .apply()
    }

    fun saveCustomPreset(p: EffectParams) { prefs.edit().putString("customPreset", ParamsCodec.encode(p)).apply() }
    fun loadCustomPreset(): EffectParams? = prefs.getString("customPreset", null)?.let { ParamsCodec.decode(it) }
    fun saveLastParams(p: EffectParams) { prefs.edit().putString("lastParams", ParamsCodec.encode(p)).apply() }
    fun loadLastParams(): EffectParams = ParamsCodec.decode(prefs.getString("lastParams", null))
}
