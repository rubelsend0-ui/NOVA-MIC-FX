package com.nova.micfx.audio

enum class EngineState { IDLE, STARTING, RUNNING, RECOVERING, STOPPING, ERROR }

/** Why the engine stopped or failed. Used to show a precise status and recovery instructions. */
enum class FailureKind { NONE, PERMISSION, MIC_BUSY, AUDIO_FOCUS, UNSUPPORTED_ROUTE, DEVICE_DISCONNECTED, UNSUPPORTED_FORMAT, OTHER }

enum class VoiceMode(val label: String, val description: String, val isPitchShift: Boolean = false) {
    NATURAL("Natural", "No voice character change."),
    DEEP("Deep", "Real pitch shifting down 4 semitones (duration preserved) plus low-end warmth.", true),
    BRIGHT("Bright", "Real pitch shifting up 2 semitones plus presence and treble boost.", true),
    WARM("Warm", "EQ only (no pitch shift): fuller lows, softer highs."),
    RADIO("Radio", "Filter only (no pitch shift): band-limited 300-3400 Hz with presence."),
    TELEPHONE("Telephone", "Filter only (no pitch shift): narrow 400-3000 Hz band."),
    ROBOT("Robot", "Ring modulation with a 50 Hz carrier. Metallic, no pitch shift."),
    ALIEN("Alien", "Pitch up 3 semitones plus 200 Hz ring modulation.", true),
    CUSTOM("Custom", "No built-in character. Use the pitch slider and effects freely.")
}

/** Immutable parameter set. All values are clamped by [validated]. */
data class EffectParams(
    val bypass: Boolean = false,
    val inputGainDb: Float = 0f,
    val outputVolume: Float = 0.8f,
    val dryWet: Float = 1f,
    val limiterCeilingDb: Float = -0.5f,
    val highPassEnabled: Boolean = true,
    val noiseReductionEnabled: Boolean = false,
    val noiseReductionStrength: Float = 0.5f,
    val gateEnabled: Boolean = false,
    val gateThresholdDb: Float = -50f,
    val bassDb: Float = 0f,
    val midDb: Float = 0f,
    val trebleDb: Float = 0f,
    val voiceMode: VoiceMode = VoiceMode.NATURAL,
    val pitchSemitones: Float = 0f,
    val echoEnabled: Boolean = false,
    val echoDelayMs: Float = 250f,
    val echoFeedback: Float = 0.3f,
    val echoMix: Float = 0.3f,
    val reverbEnabled: Boolean = false,
    val reverbRoom: Float = 0.5f,
    val reverbMix: Float = 0.25f
) {
    fun validated(): EffectParams = copy(
        inputGainDb = inputGainDb.safe(Limits.GAIN_MIN_DB, Limits.GAIN_MAX_DB, 0f),
        outputVolume = outputVolume.safe(0f, Limits.VOLUME_MAX, 0.8f),
        dryWet = dryWet.safe(0f, 1f, 1f),
        limiterCeilingDb = limiterCeilingDb.safe(Limits.LIMITER_MIN_DB, Limits.LIMITER_MAX_DB, -0.5f),
        noiseReductionStrength = noiseReductionStrength.safe(0f, 1f, 0.5f),
        gateThresholdDb = gateThresholdDb.safe(Limits.GATE_MIN_DB, Limits.GATE_MAX_DB, -50f),
        bassDb = bassDb.safe(-Limits.TONE_MAX_DB, Limits.TONE_MAX_DB, 0f),
        midDb = midDb.safe(-Limits.TONE_MAX_DB, Limits.TONE_MAX_DB, 0f),
        trebleDb = trebleDb.safe(-Limits.TONE_MAX_DB, Limits.TONE_MAX_DB, 0f),
        pitchSemitones = pitchSemitones.safe(-Limits.PITCH_MAX_ST, Limits.PITCH_MAX_ST, 0f),
        echoDelayMs = echoDelayMs.safe(Limits.ECHO_DELAY_MIN_MS, Limits.ECHO_DELAY_MAX_MS, 250f),
        echoFeedback = echoFeedback.safe(0f, Limits.ECHO_FEEDBACK_MAX, 0.3f),
        echoMix = echoMix.safe(0f, 1f, 0.3f),
        reverbRoom = reverbRoom.safe(0f, 1f, 0.5f),
        reverbMix = reverbMix.safe(0f, 1f, 0.25f)
    )

    /** Resets every effect to its default but keeps gain, monitor volume, limiter and high-pass. */
    fun resetEffects(): EffectParams {
        val d = EffectParams()
        return d.copy(inputGainDb = inputGainDb, outputVolume = outputVolume, limiterCeilingDb = limiterCeilingDb,
            highPassEnabled = highPassEnabled).validated()
    }
}

object Limits {
    const val GAIN_MIN_DB = -12f
    const val GAIN_MAX_DB = 24f
    const val VOLUME_MAX = 1.5f
    const val LIMITER_MIN_DB = -12f
    const val LIMITER_MAX_DB = -0.3f
    const val GATE_MIN_DB = -80f
    const val GATE_MAX_DB = -20f
    const val TONE_MAX_DB = 12f
    const val PITCH_MAX_ST = 12f
    const val ECHO_DELAY_MIN_MS = 50f
    const val ECHO_DELAY_MAX_MS = 1000f
    /** Hard feedback ceiling: with damping inside the loop this can never run away. */
    const val ECHO_FEEDBACK_MAX = 0.8f
}

internal fun Float.safe(lo: Float, hi: Float, fallback: Float): Float =
    if (this.isNaN()) fallback else this.coerceIn(lo, hi)

/** Live measurements taken from real audio blocks. Linear amplitudes (1.0 = full scale). */
data class Meters(
    val inputPeak: Float = 0f,
    val inputRms: Float = 0f,
    val outputPeak: Float = 0f,
    val outputRms: Float = 0f,
    val holdPeak: Float = 0f,
    val clipping: Boolean = false,
    val clipCount: Long = 0L,
    val rawInputClipped: Boolean = false,
    val reductionDb: Float = 0f,
    val limiterReductionDb: Float = 0f,
    val voiceActive: Boolean = false,
    val noiseFloorDb: Float = -120f
)

data class EngineSnapshot(
    val state: EngineState = EngineState.IDLE,
    val message: String = "Stopped",
    val errorDetail: String? = null,
    val failure: FailureKind = FailureKind.NONE,
    val recoveryAttempt: Int = 0,
    val inputDevice: String = "-",
    val outputDevice: String = "-",
    val outputVerifiedSafe: Boolean = false,
    val sampleRate: Int = 0,
    val blockFrames: Int = 0,
    /** Buffer-size based worst-case estimate. */
    val latencyEstimateMs: Float = 0f,
    /** Measured: audio queued in the output buffer plus one capture block. Excludes hardware delay. Negative = not measured yet. */
    val measuredBufferLatencyMs: Float = -1f,
    val meters: Meters = Meters(),
    val underruns: Int = 0,
    val lateReads: Int = 0,
    val inputSilent: Boolean = false,
    val systemNsActive: Boolean = false,
    val systemAecActive: Boolean = false,
    val systemAgcActive: Boolean = false,
    val effectNotes: List<String> = emptyList()
)

data class EngineSettings(
    val preferredSampleRate: Int = 0,           // 0 = automatic
    val bufferMultiplier: Int = 2,              // 1..4
    val preferredInputId: Int = -1,             // -1 = system default
    val preferredOutputId: Int = -1,
    val allowSpeakerMonitoring: Boolean = false,
    val runInBackground: Boolean = false,
    val autoRecover: Boolean = true,
    val useCommunicationSource: Boolean = false,
    val requestSystemNs: Boolean = false,
    val requestSystemAec: Boolean = false,
    val requestSystemAgc: Boolean = false
) {
    fun validated(): EngineSettings = copy(
        preferredSampleRate = if (preferredSampleRate == 0 || BufferSizing.isSupportedRate(preferredSampleRate)) preferredSampleRate else 0,
        bufferMultiplier = bufferMultiplier.coerceIn(1, 4),
        preferredInputId = if (preferredInputId < -1) -1 else preferredInputId,
        preferredOutputId = if (preferredOutputId < -1) -1 else preferredOutputId
    )
}

/** Text codec for [EngineSettings]; decoding always validates. */
object SettingsCodec {
    fun encode(s: EngineSettings): String = listOf(
        "sampleRate" to s.preferredSampleRate, "bufferMult" to s.bufferMultiplier,
        "inId" to s.preferredInputId, "outId" to s.preferredOutputId,
        "allowSpeaker" to s.allowSpeakerMonitoring, "background" to s.runInBackground,
        "autoRecover" to s.autoRecover, "commSource" to s.useCommunicationSource,
        "sysNs" to s.requestSystemNs, "sysAec" to s.requestSystemAec, "sysAgc" to s.requestSystemAgc
    ).joinToString(";") { "${it.first}=${it.second}" }

    fun decode(text: String?): EngineSettings {
        if (text.isNullOrBlank()) return EngineSettings()
        val m = HashMap<String, String>()
        for (part in text.split(";")) {
            val i = part.indexOf('=')
            if (i > 0) m[part.substring(0, i)] = part.substring(i + 1)
        }
        val d = EngineSettings()
        fun i(k: String, def: Int) = m[k]?.toIntOrNull() ?: def
        fun b(k: String, def: Boolean) = m[k]?.toBooleanStrictOrNull() ?: def
        return EngineSettings(
            preferredSampleRate = i("sampleRate", d.preferredSampleRate),
            bufferMultiplier = i("bufferMult", d.bufferMultiplier),
            preferredInputId = i("inId", d.preferredInputId),
            preferredOutputId = i("outId", d.preferredOutputId),
            allowSpeakerMonitoring = b("allowSpeaker", d.allowSpeakerMonitoring),
            runInBackground = b("background", d.runInBackground),
            autoRecover = b("autoRecover", d.autoRecover),
            useCommunicationSource = b("commSource", d.useCommunicationSource),
            requestSystemNs = b("sysNs", d.requestSystemNs),
            requestSystemAec = b("sysAec", d.requestSystemAec),
            requestSystemAgc = b("sysAgc", d.requestSystemAgc)
        ).validated()
    }
}

data class StageStatus(val name: String, val enabled: Boolean, val detail: String)

/** What the dashboard shows. Derived only from verified engine state, never hard-coded. */
enum class DisplayStatus(val label: String) {
    READY("Ready"),
    REQUESTING_PERMISSION("Requesting Permission"),
    PERMISSION_NEEDED("Permission Needed"),
    STARTING("Starting"),
    MONITORING("Monitoring"),
    RECOVERING("Recovering"),
    STOPPING("Stopping"),
    UNSUPPORTED_ROUTE("Unsupported Route"),
    DEVICE_DISCONNECTED("Audio Device Disconnected"),
    MIC_BUSY("Microphone Busy"),
    ERROR("Error")
}

object StatusResolver {
    fun resolve(snapshot: EngineSnapshot, permissionGranted: Boolean, permissionRequestPending: Boolean): DisplayStatus {
        if (permissionRequestPending) return DisplayStatus.REQUESTING_PERMISSION
        fun byFailure(): DisplayStatus? = when (snapshot.failure) {
            FailureKind.PERMISSION -> DisplayStatus.PERMISSION_NEEDED
            FailureKind.MIC_BUSY -> DisplayStatus.MIC_BUSY
            FailureKind.UNSUPPORTED_ROUTE -> DisplayStatus.UNSUPPORTED_ROUTE
            FailureKind.DEVICE_DISCONNECTED -> DisplayStatus.DEVICE_DISCONNECTED
            else -> null
        }
        return when (snapshot.state) {
            EngineState.STARTING -> DisplayStatus.STARTING
            EngineState.RUNNING -> DisplayStatus.MONITORING
            EngineState.RECOVERING -> DisplayStatus.RECOVERING
            EngineState.STOPPING -> DisplayStatus.STOPPING
            EngineState.ERROR -> byFailure() ?: DisplayStatus.ERROR
            EngineState.IDLE -> byFailure() ?: if (!permissionGranted) DisplayStatus.PERMISSION_NEEDED else DisplayStatus.READY
        }
    }
}

/** Resolves a stored preferred-device id against the devices that really exist right now. */
object DevicePreference {
    /** @return the preferred id when it is currently available, otherwise null (use system default). */
    fun resolve(preferredId: Int, availableIds: List<Int>): Int? =
        if (preferredId >= 0 && availableIds.contains(preferredId)) preferredId else null

    fun isMissing(preferredId: Int, availableIds: List<Int>): Boolean =
        preferredId >= 0 && !availableIds.contains(preferredId)
}
