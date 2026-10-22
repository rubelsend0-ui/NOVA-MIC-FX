package com.nova.micfx.audio

enum class EngineState { IDLE, STARTING, RUNNING, STOPPING, ERROR }

enum class VoiceMode(val label: String, val description: String, val isPitchShift: Boolean = false) {
    NATURAL("Natural", "No voice change."),
    DEEP("Deep", "Real pitch shifting down 4 semitones (duration preserved) plus low-end warmth.", true),
    BRIGHT("Bright", "Real pitch shifting up 2 semitones plus presence and treble boost.", true),
    WARM("Warm", "EQ only (no pitch shift): fuller lows, softer highs."),
    RADIO("Radio", "Filter only (no pitch shift): band-limited 300-3400 Hz with presence."),
    TELEPHONE("Telephone", "Filter only (no pitch shift): narrow 400-3000 Hz band."),
    ROBOT("Robot", "Ring modulation (50 Hz carrier). Metallic effect, no pitch shift."),
    CUSTOM("Custom", "Real pitch shifting by the number of semitones you choose.", true)
}

/** Immutable parameter set. All values are clamped by [validated]. */
data class EffectParams(
    val bypass: Boolean = false,
    val inputGainDb: Float = 0f,
    val outputVolume: Float = 0.8f,
    val highPassEnabled: Boolean = true,
    val noiseReductionEnabled: Boolean = false,
    val noiseReductionStrength: Float = 0.5f,
    val gateEnabled: Boolean = false,
    val gateThresholdDb: Float = -50f,
    val bassDb: Float = 0f,
    val midDb: Float = 0f,
    val trebleDb: Float = 0f,
    val voiceMode: VoiceMode = VoiceMode.NATURAL,
    val customPitchSemitones: Float = 0f,
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
        noiseReductionStrength = noiseReductionStrength.safe(0f, 1f, 0.5f),
        gateThresholdDb = gateThresholdDb.safe(Limits.GATE_MIN_DB, Limits.GATE_MAX_DB, -50f),
        bassDb = bassDb.safe(-Limits.TONE_MAX_DB, Limits.TONE_MAX_DB, 0f),
        midDb = midDb.safe(-Limits.TONE_MAX_DB, Limits.TONE_MAX_DB, 0f),
        trebleDb = trebleDb.safe(-Limits.TONE_MAX_DB, Limits.TONE_MAX_DB, 0f),
        customPitchSemitones = customPitchSemitones.safe(-Limits.PITCH_MAX_ST, Limits.PITCH_MAX_ST, 0f),
        echoDelayMs = echoDelayMs.safe(Limits.ECHO_DELAY_MIN_MS, Limits.ECHO_DELAY_MAX_MS, 250f),
        echoFeedback = echoFeedback.safe(0f, Limits.ECHO_FEEDBACK_MAX, 0.3f),
        echoMix = echoMix.safe(0f, 1f, 0.3f),
        reverbRoom = reverbRoom.safe(0f, 1f, 0.5f),
        reverbMix = reverbMix.safe(0f, 1f, 0.25f)
    )
}

object Limits {
    const val GAIN_MIN_DB = -12f
    const val GAIN_MAX_DB = 24f
    const val VOLUME_MAX = 1.5f
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
    val voiceActive: Boolean = false,
    val noiseFloorDb: Float = -120f
)

data class EngineSnapshot(
    val state: EngineState = EngineState.IDLE,
    val message: String = "Stopped",
    val errorDetail: String? = null,
    val inputDevice: String = "-",
    val outputDevice: String = "-",
    val outputVerifiedSafe: Boolean = false,
    val sampleRate: Int = 0,
    val blockFrames: Int = 0,
    val latencyEstimateMs: Float = 0f,
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
    val useCommunicationSource: Boolean = false,
    val requestSystemNs: Boolean = false,
    val requestSystemAec: Boolean = false,
    val requestSystemAgc: Boolean = false
)

data class StageStatus(val name: String, val enabled: Boolean, val detail: String)
