package com.nova.micfx.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class CompatStatus { OK, WARN, NO, INFO }
data class CompatItem(val title: String, val status: CompatStatus, val detail: String)

/** Reports what this device can really do. Nothing here is assumed: every line comes from a platform query. */
class CompatibilityChecker(context: Context, private val devices: AudioDeviceManager) {
    private val ctx = context.applicationContext
    private val pm = ctx.packageManager
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun run(): List<CompatItem> {
        val list = ArrayList<CompatItem>()
        val hasMic = pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        list += CompatItem("Microphone hardware", if (hasMic) CompatStatus.OK else CompatStatus.NO,
            if (hasMic) "A microphone is reported by the system." else "No microphone feature reported.")

        val perm = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        list += CompatItem("Microphone permission", if (perm) CompatStatus.OK else CompatStatus.WARN,
            if (perm) "Granted." else "Not granted. Press Start on the main screen to request it.")

        val lowLat = pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY)
        val pro = pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_PRO)
        list += CompatItem("Low-latency audio", if (lowLat) CompatStatus.OK else CompatStatus.WARN,
            (if (lowLat) "Device declares low-latency audio." else "Device does not declare low-latency audio; expect noticeable delay.") +
                if (pro) " Pro-audio feature present." else "")

        val rate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        val burst = am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
        list += CompatItem("Native output format", CompatStatus.INFO,
            "Sample rate: ${rate ?: "unknown"} Hz, burst size: ${burst ?: "unknown"} frames.")

        val av = AudioEffectController.availability()
        list += CompatItem("System noise suppressor", if (av.noiseSuppressor) CompatStatus.OK else CompatStatus.WARN,
            if (av.noiseSuppressor) "Available (optional, enable in Settings)." else "Not available. NOVA's own adaptive suppressor is used instead.")
        list += CompatItem("System echo canceler (AEC)", if (av.echoCanceler) CompatStatus.OK else CompatStatus.WARN,
            if (av.echoCanceler) "Available (optional, enable in Settings)." else "Not available on this device.")
        list += CompatItem("System automatic gain control", if (av.gainControl) CompatStatus.OK else CompatStatus.INFO,
            if (av.gainControl) "Available (off by default; it fights manual gain)." else "Not available.")

        val phones = devices.hasHeadphoneOutput()
        list += CompatItem("Headphone / external output", if (phones) CompatStatus.OK else CompatStatus.WARN,
            if (phones) "Connected. Safe monitoring is possible." else "None connected. Local monitoring is blocked to prevent feedback.")
        if (devices.hasBluetoothOutput()) {
            list += CompatItem("Bluetooth output", CompatStatus.WARN,
                "Bluetooth adds noticeable latency; wired or USB headphones are recommended for live monitoring.")
        }

        val canMonitor = hasMic && perm && phones
        list += CompatItem("Local monitoring (Mode A)", if (canMonitor) CompatStatus.OK else CompatStatus.WARN,
            if (canMonitor) "Processed audio can be monitored on your headphones." else "Needs microphone permission and headphones.")

        list += CompatItem("Processed audio into other apps (Mode B)", CompatStatus.NO,
            "Not supported. Android has no public API that lets an ordinary app replace another app's microphone input. " +
                "NOVA MIC FX does not use root, hidden APIs or accessibility tricks, so it cannot feed games, Discord or calls.")

        list += CompatItem("Android version", CompatStatus.INFO,
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}). From Android 10, apps in the background may receive silence from the microphone.")
        return list
    }
}
