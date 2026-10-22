package com.nova.micfx.audio

data class Preset(val id: String, val name: String, val description: String, val params: EffectParams)

object Presets {
    val NATURAL = Preset("natural", "Natural", "Neutral. Only the safe default high-pass filter and limiter.", EffectParams())

    val GAMING = Preset(
        "gaming", "Gaming",
        "Voice boost with noise suppressor and gate, slight presence lift.",
        EffectParams(
            inputGainDb = 6f, highPassEnabled = true,
            noiseReductionEnabled = true, noiseReductionStrength = 0.6f,
            gateEnabled = true, gateThresholdDb = -50f,
            midDb = 3f, trebleDb = 2f
        )
    )
    val CLEAR_VOICE = Preset(
        "clear", "Clear Voice",
        "Light gain, gentle noise suppression, presence and treble for intelligibility.",
        EffectParams(
            inputGainDb = 3f, noiseReductionEnabled = true, noiseReductionStrength = 0.4f,
            bassDb = -2f, midDb = 3f, trebleDb = 3f
        )
    )
    val DEEP_VOICE = Preset(
        "deep", "Deep Voice",
        "Real pitch shift down 4 semitones with extra low end.",
        EffectParams(
            inputGainDb = 3f, noiseReductionEnabled = true, noiseReductionStrength = 0.3f,
            bassDb = 3f, voiceMode = VoiceMode.DEEP
        )
    )
    val NOISE_REDUCTION = Preset(
        "noise", "Noise Reduction",
        "Strong noise suppression and gate for noisy rooms.",
        EffectParams(
            inputGainDb = 6f, noiseReductionEnabled = true, noiseReductionStrength = 0.85f,
            gateEnabled = true, gateThresholdDb = -45f
        )
    )
    val RADIO = Preset(
        "radio", "Radio",
        "Band-limited radio sound with light noise suppression.",
        EffectParams(
            inputGainDb = 3f, noiseReductionEnabled = true, noiseReductionStrength = 0.3f,
            trebleDb = 2f, voiceMode = VoiceMode.RADIO
        )
    )

    val builtIn: List<Preset> = listOf(NATURAL, GAMING, CLEAR_VOICE, DEEP_VOICE, NOISE_REDUCTION, RADIO)

    /** Applies a preset: returns validated parameters that the engine will really use. */
    fun apply(preset: Preset): EffectParams = preset.params.copy(bypass = false).validated()
}

/** Text codec used to store the Custom preset and last-used settings locally. */
object ParamsCodec {
    fun encode(p: EffectParams): String = listOf(
        "bypass" to p.bypass, "inputGainDb" to p.inputGainDb, "outputVolume" to p.outputVolume,
        "highPassEnabled" to p.highPassEnabled,
        "noiseReductionEnabled" to p.noiseReductionEnabled, "noiseReductionStrength" to p.noiseReductionStrength,
        "gateEnabled" to p.gateEnabled, "gateThresholdDb" to p.gateThresholdDb,
        "bassDb" to p.bassDb, "midDb" to p.midDb, "trebleDb" to p.trebleDb,
        "voiceMode" to p.voiceMode.name, "customPitchSemitones" to p.customPitchSemitones,
        "echoEnabled" to p.echoEnabled, "echoDelayMs" to p.echoDelayMs,
        "echoFeedback" to p.echoFeedback, "echoMix" to p.echoMix,
        "reverbEnabled" to p.reverbEnabled, "reverbRoom" to p.reverbRoom, "reverbMix" to p.reverbMix
    ).joinToString(";") { "${it.first}=${it.second}" }

    fun decode(text: String?): EffectParams {
        if (text.isNullOrBlank()) return EffectParams()
        val m = HashMap<String, String>()
        for (part in text.split(";")) {
            val i = part.indexOf('=')
            if (i > 0) m[part.substring(0, i)] = part.substring(i + 1)
        }
        val d = EffectParams()
        fun f(k: String, def: Float) = m[k]?.toFloatOrNull() ?: def
        fun b(k: String, def: Boolean) = m[k]?.toBooleanStrictOrNull() ?: def
        return EffectParams(
            bypass = b("bypass", d.bypass),
            inputGainDb = f("inputGainDb", d.inputGainDb),
            outputVolume = f("outputVolume", d.outputVolume),
            highPassEnabled = b("highPassEnabled", d.highPassEnabled),
            noiseReductionEnabled = b("noiseReductionEnabled", d.noiseReductionEnabled),
            noiseReductionStrength = f("noiseReductionStrength", d.noiseReductionStrength),
            gateEnabled = b("gateEnabled", d.gateEnabled),
            gateThresholdDb = f("gateThresholdDb", d.gateThresholdDb),
            bassDb = f("bassDb", d.bassDb),
            midDb = f("midDb", d.midDb),
            trebleDb = f("trebleDb", d.trebleDb),
            voiceMode = VoiceMode.values().firstOrNull { it.name == m["voiceMode"] } ?: d.voiceMode,
            customPitchSemitones = f("customPitchSemitones", d.customPitchSemitones),
            echoEnabled = b("echoEnabled", d.echoEnabled),
            echoDelayMs = f("echoDelayMs", d.echoDelayMs),
            echoFeedback = f("echoFeedback", d.echoFeedback),
            echoMix = f("echoMix", d.echoMix),
            reverbEnabled = b("reverbEnabled", d.reverbEnabled),
            reverbRoom = f("reverbRoom", d.reverbRoom),
            reverbMix = f("reverbMix", d.reverbMix)
        ).validated()
    }
}
