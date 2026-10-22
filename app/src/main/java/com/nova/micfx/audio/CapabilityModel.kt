package com.nova.micfx.audio

enum class CompatStatus { OK, WARN, NO, INFO }
data class CompatItem(val title: String, val status: CompatStatus, val detail: String)

/** The four things the compatibility screen must never mix up. */
data class CapabilitySummary(
    val processingInsideNova: CompatItem,
    val localMonitoring: CompatItem,
    val externalHardware: CompatItem,
    val directInjection: CompatItem
)

object CapabilityModel {
    fun summarize(hasMic: Boolean, permission: Boolean, headphones: Boolean, usbAudioInput: Boolean): CapabilitySummary {
        val processing = when {
            !hasMic -> CompatItem("1. Audio processing inside NOVA", CompatStatus.NO, "No microphone reported by the system.")
            !permission -> CompatItem("1. Audio processing inside NOVA", CompatStatus.WARN, "Supported, but microphone permission is not granted yet.")
            else -> CompatItem("1. Audio processing inside NOVA", CompatStatus.OK, "Supported: NOVA captures the microphone and applies its effects.")
        }
        val monitoring = when {
            !hasMic || !permission -> CompatItem("2. Local monitoring", CompatStatus.WARN, "Needs microphone permission.")
            !headphones -> CompatItem("2. Local monitoring", CompatStatus.WARN, "Connect headphones. Speaker monitoring is blocked by default to prevent feedback.")
            else -> CompatItem("2. Local monitoring", CompatStatus.OK, "Possible: processed audio can be heard on your headphones.")
        }
        val hardware = if (usbAudioInput)
            CompatItem("3. External hardware routing", CompatStatus.INFO,
                "A USB audio input is connected. A hardware voice changer on it is a legitimate route, but whether another app uses it as its microphone depends on the phone and the app. NOT verified by NOVA.")
        else
            CompatItem("3. External hardware routing", CompatStatus.INFO,
                "No USB audio input detected. A USB audio interface or hardware voice changer is the practical way to change your voice in other apps. Device-dependent, not verified by NOVA.")
        val injection = CompatItem("4. Direct injection into other apps", CompatStatus.NO,
            "Unavailable. Android has no supported API for an ordinary app to replace another app's microphone. NOVA never claims compatibility with Free Fire, WePlay, Discord or calls.")
        return CapabilitySummary(processing, monitoring, hardware, injection)
    }
}
