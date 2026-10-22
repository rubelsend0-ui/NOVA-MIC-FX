package com.nova.micfx.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class DeviceItem(val id: Int, val name: String, val typeName: String, val headphoneLike: Boolean)

class AudioDeviceManager(context: Context) {
    private val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _inputs = MutableStateFlow<List<DeviceItem>>(emptyList())
    private val _outputs = MutableStateFlow<List<DeviceItem>>(emptyList())
    val inputs: StateFlow<List<DeviceItem>> = _inputs
    val outputs: StateFlow<List<DeviceItem>> = _outputs

    private var removedListener: ((AudioDeviceInfo) -> Unit)? = null

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) { refresh() }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            refresh()
            removedDevices.forEach { removedListener?.invoke(it) }
        }
    }

    fun start(onRemoved: (AudioDeviceInfo) -> Unit) {
        removedListener = onRemoved
        am.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun refresh() {
        _inputs.value = am.getDevices(AudioManager.GET_DEVICES_INPUTS).map { toItem(it) }
        _outputs.value = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { toItem(it) }
    }

    fun findInput(id: Int): AudioDeviceInfo? = am.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.id == id }
    fun findOutput(id: Int): AudioDeviceInfo? = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == id }

    fun hasHeadphoneOutput(): Boolean =
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { isHeadphoneType(it.type) }

    fun hasBluetoothOutput(): Boolean =
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }

    private fun toItem(d: AudioDeviceInfo) = DeviceItem(d.id, describe(d), typeName(d.type), isHeadphoneType(d.type))

    companion object {
        fun describe(d: AudioDeviceInfo?): String {
            if (d == null) return "-"
            val n = d.productName?.toString()?.takeIf { it.isNotBlank() }
            val t = typeName(d.type)
            return if (n == null || n.equals(t, true)) t else "$t ($n)"
        }

        /** Output types that are safe for monitoring (no acoustic path back into the microphone). */
        fun isHeadphoneType(type: Int): Boolean = when (type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_HEARING_AID,
            AudioDeviceInfo.TYPE_BLE_HEADSET -> true
            else -> false
        }

        fun typeName(type: Int): String = when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in microphone"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone speaker"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth (SCO)"
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth (A2DP)"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB device"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB accessory"
            AudioDeviceInfo.TYPE_HEARING_AID -> "Hearing aid"
            AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE headset"
            AudioDeviceInfo.TYPE_HDMI -> "HDMI"
            AudioDeviceInfo.TYPE_TELEPHONY -> "Telephony"
            AudioDeviceInfo.TYPE_LINE_ANALOG -> "Line out"
            else -> "Audio device (type $type)"
        }
    }
}
