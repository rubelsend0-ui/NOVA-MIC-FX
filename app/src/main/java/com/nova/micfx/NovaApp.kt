package com.nova.micfx

import android.app.Application
import com.nova.micfx.audio.AudioEngine
import com.nova.micfx.settings.AudioSettingsRepository

class NovaApp : Application() {
    lateinit var settings: AudioSettingsRepository
        private set
    lateinit var engine: AudioEngine
        private set

    override fun onCreate() {
        super.onCreate()
        settings = AudioSettingsRepository(this)
        engine = AudioEngine(this, settings)
        engine.setParams(settings.loadLastParams())
        engine.init()
    }
}
