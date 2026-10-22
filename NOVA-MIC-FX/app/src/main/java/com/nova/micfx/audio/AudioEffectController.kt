package com.nova.micfx.audio

import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor

/**
 * Optional Android platform effects attached to the AudioRecord session. Availability depends on the
 * device and audio source; the REAL enabled state is read back, never assumed.
 */
class AudioEffectController {
    data class Availability(val noiseSuppressor: Boolean, val echoCanceler: Boolean, val gainControl: Boolean)
    data class Result(val ns: Boolean, val aec: Boolean, val agc: Boolean, val notes: List<String>)

    private var ns: NoiseSuppressor? = null
    private var aec: AcousticEchoCanceler? = null
    private var agc: AutomaticGainControl? = null

    fun attach(sessionId: Int, wantNs: Boolean, wantAec: Boolean, wantAgc: Boolean): Result {
        release()
        val notes = ArrayList<String>()
        if (wantNs) {
            ns = create(NoiseSuppressor.isAvailable(), "System noise suppressor", notes) { NoiseSuppressor.create(sessionId) }
        }
        if (wantAec) {
            aec = create(AcousticEchoCanceler.isAvailable(), "System echo canceler", notes) { AcousticEchoCanceler.create(sessionId) }
        }
        if (wantAgc) {
            agc = create(AutomaticGainControl.isAvailable(), "System automatic gain control", notes) { AutomaticGainControl.create(sessionId) }
        }
        return Result(ns?.enabled == true, aec?.enabled == true, agc?.enabled == true, notes)
    }

    private fun <T : AudioEffect> create(available: Boolean, name: String, notes: MutableList<String>, factory: () -> T?): T? {
        if (!available) { notes.add("$name is not available on this device."); return null }
        return try {
            val e = factory()
            if (e == null) { notes.add("$name could not be created."); null }
            else {
                val r = e.setEnabled(true)
                if (r != AudioEffect.SUCCESS) { notes.add("$name failed to enable (code $r)."); e.release(); null } else e
            }
        } catch (t: Throwable) {
            notes.add("$name error: ${t.javaClass.simpleName}")
            null
        }
    }

    fun release() {
        try { ns?.release() } catch (_: Throwable) {}
        try { aec?.release() } catch (_: Throwable) {}
        try { agc?.release() } catch (_: Throwable) {}
        ns = null; aec = null; agc = null
    }

    companion object {
        fun availability() = Availability(
            NoiseSuppressor.isAvailable(), AcousticEchoCanceler.isAvailable(), AutomaticGainControl.isAvailable()
        )
    }
}
