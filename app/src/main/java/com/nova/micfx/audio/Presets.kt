package com.nova.micfx.audio

/** A voice preset. [id] is a stable identifier (never the display name). */
data class Preset(val id: String, val name: String, val description: String, val params: EffectParams)

object Presets {
    const val CUSTOM_ID = "custom"

    val NATURAL = Preset("natural", "Natural Voice", "Neutral. Only the safe high-pass filter and limiter.", EffectParams())

    val CLEAR_VOICE = Preset(
        "clean", "Clean Voice",
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
    val LOW_PITCH = Preset(
        "low_pitch", "Low Pitch",
        "Pitch shifted down 6 semitones with a little extra bass.",
        EffectParams(pitchSemitones = -6f, bassDb = 2f, noiseReductionEnabled = true, noiseReductionStrength = 0.3f)
    )
    val HIGH_PITCH = Preset(
        "high_pitch", "High Pitch",
        "Pitch shifted up 5 semitones with a touch of treble.",
        EffectParams(pitchSemitones = 5f, trebleDb = 2f, noiseReductionEnabled = true, noiseReductionStrength = 0.3f)
    )
    val ROBOT = Preset(
        "robot", "Robot",
        "Metallic ring-modulated voice (50 Hz carrier).",
        EffectParams(voiceMode = VoiceMode.ROBOT, inputGainDb = 3f)
    )
    val ALIEN = Preset(
        "alien", "Alien",
        "Pitch up 3 semitones, 200 Hz ring modulation and a little reverb.",
        EffectParams(voiceMode = VoiceMode.ALIEN, reverbEnabled = true, reverbRoom = 0.4f, reverbMix = 0.2f)
    )
    val RADIO = Preset(
        "radio", "Radio",
        "Band-limited radio sound with light noise suppression.",
        EffectParams(
            inputGainDb = 3f, noiseReductionEnabled = true, noiseReductionStrength = 0.3f,
            trebleDb = 2f, voiceMode = VoiceMode.RADIO
        )
    )
    val ECHO = Preset(
        "echo", "Echo",
        "Audible echo: 300 ms delay, damped feedback.",
        EffectParams(echoEnabled = true, echoDelayMs = 300f, echoFeedback = 0.4f, echoMix = 0.4f)
    )
    val REVERB = Preset(
        "reverb", "Reverb",
        "Large room reverb.",
        EffectParams(reverbEnabled = true, reverbRoom = 0.75f, reverbMix = 0.35f)
    )
    val GAMING = Preset(
        "gaming", "Gaming",
        "Voice boost with noise suppressor and gate, slight presence lift.",
        EffectParams(
            inputGainDb = 6f, noiseReductionEnabled = true, noiseReductionStrength = 0.6f,
            gateEnabled = true, gateThresholdDb = -50f, midDb = 3f, trebleDb = 2f
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
    val BRIGHT = Preset("bright", "Bright Voice", "Pitch up 2 semitones with presence boost.", EffectParams(voiceMode = VoiceMode.BRIGHT))
    val WARM = Preset("warm", "Warm Voice", "Fuller lows and softer highs (EQ only).", EffectParams(voiceMode = VoiceMode.WARM))
    val TELEPHONE = Preset("telephone", "Telephone", "Narrow-band telephone sound.", EffectParams(voiceMode = VoiceMode.TELEPHONE, inputGainDb = 3f))

    val builtIn: List<Preset> = listOf(
        NATURAL, CLEAR_VOICE, DEEP_VOICE, LOW_PITCH, HIGH_PITCH, ROBOT, ALIEN, RADIO, ECHO, REVERB,
        GAMING, NOISE_REDUCTION, BRIGHT, WARM, TELEPHONE
    )

    fun byId(id: String?): Preset? = builtIn.firstOrNull { it.id == id }

    /** Case-insensitive search over name and description. */
    fun search(query: String, list: List<Preset> = builtIn): List<Preset> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return list
        return list.filter { it.name.lowercase().contains(q) || it.description.lowercase().contains(q) || it.id.contains(q) }
    }

    /** Applies a preset: returns validated parameters that the engine will really use. */
    fun apply(preset: Preset): EffectParams = preset.params.copy(bypass = false).validated()

    /** The preset whose parameters equal [params], if any (used to restore the selected-preset indicator). */
    fun matching(params: EffectParams): Preset? = builtIn.firstOrNull { apply(it) == params.validated() }
}

/** Text codec used to store the Custom preset and last-used settings locally. */
object ParamsCodec {
    fun encode(p: EffectParams): String = listOf(
        "bypass" to p.bypass, "inputGainDb" to p.inputGainDb, "outputVolume" to p.outputVolume,
        "dryWet" to p.dryWet, "limiterCeilingDb" to p.limiterCeilingDb,
        "highPassEnabled" to p.highPassEnabled,
        "noiseReductionEnabled" to p.noiseReductionEnabled, "noiseReductionStrength" to p.noiseReductionStrength,
        "gateEnabled" to p.gateEnabled, "gateThresholdDb" to p.gateThresholdDb,
        "bassDb" to p.bassDb, "midDb" to p.midDb, "trebleDb" to p.trebleDb,
        "voiceMode" to p.voiceMode.name, "pitchSemitones" to p.pitchSemitones,
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
            dryWet = f("dryWet", d.dryWet),
            limiterCeilingDb = f("limiterCeilingDb", d.limiterCeilingDb),
            highPassEnabled = b("highPassEnabled", d.highPassEnabled),
            noiseReductionEnabled = b("noiseReductionEnabled", d.noiseReductionEnabled),
            noiseReductionStrength = f("noiseReductionStrength", d.noiseReductionStrength),
            gateEnabled = b("gateEnabled", d.gateEnabled),
            gateThresholdDb = f("gateThresholdDb", d.gateThresholdDb),
            bassDb = f("bassDb", d.bassDb),
            midDb = f("midDb", d.midDb),
            trebleDb = f("trebleDb", d.trebleDb),
            voiceMode = VoiceMode.values().firstOrNull { it.name == m["voiceMode"] } ?: d.voiceMode,
            pitchSemitones = f("pitchSemitones", d.pitchSemitones),
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
