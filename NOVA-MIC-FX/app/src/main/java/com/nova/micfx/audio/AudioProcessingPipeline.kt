package com.nova.micfx.audio

/** Per-voice-mode DSP settings (pitch / EQ offsets / band limit / ring modulation). */
data class VoiceProfile(
    val pitchSemitones: Float = 0f,
    val bassDb: Float = 0f,
    val midDb: Float = 0f,
    val trebleDb: Float = 0f,
    val ringMix: Float = 0f,
    val bandLowHz: Float = 0f,
    val bandHighHz: Float = 0f      // 0 = no band limiting
) {
    companion object {
        fun of(mode: VoiceMode, customSemitones: Float): VoiceProfile = when (mode) {
            VoiceMode.NATURAL -> VoiceProfile()
            VoiceMode.DEEP -> VoiceProfile(pitchSemitones = -4f, bassDb = 4f)
            VoiceMode.BRIGHT -> VoiceProfile(pitchSemitones = 2f, midDb = 2f, trebleDb = 4f)
            VoiceMode.WARM -> VoiceProfile(bassDb = 4f, trebleDb = -3f)
            VoiceMode.RADIO -> VoiceProfile(midDb = 3f, bandLowHz = 300f, bandHighHz = 3400f)
            VoiceMode.TELEPHONE -> VoiceProfile(midDb = 2f, bandLowHz = 400f, bandHighHz = 3000f)
            VoiceMode.ROBOT -> VoiceProfile(ringMix = 0.8f)
            VoiceMode.CUSTOM -> VoiceProfile(pitchSemitones = customSemitones)
        }
    }
}

/** Describes which processing stages the given parameters really switch on. */
object StageReport {
    fun build(p: EffectParams): List<StageStatus> {
        val fx = !p.bypass
        val voice = VoiceProfile.of(p.voiceMode, p.customPitchSemitones)
        val voiceName = when {
            p.voiceMode == VoiceMode.NATURAL -> "Voice: Natural"
            voice.pitchSemitones != 0f -> "Voice: ${p.voiceMode.label} (pitch ${"%+.0f".format(voice.pitchSemitones)} st)"
            else -> "Voice: ${p.voiceMode.label}"
        }
        return listOf(
            StageStatus("Input gain", true, "%+.0f dB software gain".format(p.inputGainDb)),
            StageStatus("High-pass 80 Hz", p.highPassEnabled, if (p.highPassEnabled) "Removes rumble" else "Off"),
            StageStatus("Noise suppressor", fx && p.noiseReductionEnabled, if (p.bypass) "Bypassed" else "Adaptive noise-floor suppressor"),
            StageStatus("Noise gate", fx && p.gateEnabled, if (p.bypass) "Bypassed" else "Threshold %.0f dB".format(p.gateThresholdDb)),
            StageStatus("Tone EQ", fx && (p.bassDb != 0f || p.midDb != 0f || p.trebleDb != 0f), "Bass/mid/treble biquads"),
            StageStatus(voiceName, fx && p.voiceMode != VoiceMode.NATURAL, if (p.bypass) "Bypassed" else p.voiceMode.description),
            StageStatus("Echo effect", fx && p.echoEnabled, "Delay line with feedback"),
            StageStatus("Reverb", fx && p.reverbEnabled, "Comb + all-pass reverb"),
            StageStatus("Limiter", true, "Always on, protects output")
        )
    }
}

/**
 * The complete real-time chain. Allocation free inside [process]. Parameters are swapped through a
 * volatile reference so the UI thread can change them at any time.
 *
 * Order: gain -> high-pass -> noise reduction -> pitch / ring mod -> tone EQ -> band limit
 *        -> echo -> reverb -> output volume -> limiter.
 */
class AudioProcessingPipeline(val sampleRate: Int) {
    @Volatile private var params: EffectParams = EffectParams()

    private val highPass = BiquadFilter(BiquadDesign.highPass(sampleRate, 80.0))
    private var highPassWasOn = false
    private val noise = NoiseReductionProcessor(sampleRate)
    private val pitch = PitchShifter(sampleRate)
    private val ring = RingModulator(sampleRate)
    private val tone = ToneEqualizer(sampleRate)
    private val band = BandLimiter(sampleRate)
    private val echo = EchoProcessor(sampleRate)
    private val reverb = ReverbProcessor(sampleRate)
    private val limiter = LimiterProcessor(sampleRate)

    private val smoothCoef = DspMath.smoothingCoefficient(15f, sampleRate)
    private var curGain = 1f
    private var curVolume = 0.8f

    // Measurements of the most recent block
    var inputPeak = 0f; private set
    var inputRms = 0f; private set
    var outputPeak = 0f; private set
    var outputRms = 0f; private set
    var rawInputClipped = false; private set
    var blockClipped = false; private set
    var totalClipped = 0L; private set
    val reductionDb: Float get() = noise.reductionDb
    val voiceActive: Boolean get() = noise.voiceActive
    val noiseFloorDb: Float get() = noise.noiseFloorDb
    val limiterReductionDb: Float get() = limiter.gainReductionDb

    fun setParams(p: EffectParams) {
        if (p !== params) params = p.validated()
    }

    fun getParams(): EffectParams = params

    fun process(buf: FloatArray, n: Int) {
        val p = params
        val fx = !p.bypass
        val target = DspMath.dbToLinear(p.inputGainDb)

        rawInputClipped = DspMath.countAtOrAbove(buf, n, 0.999f) > 0

        // 1. Input gain with per-sample smoothing (no zipper noise / sudden jumps)
        var g = curGain
        for (i in 0 until n) {
            g = target + (g - target) * smoothCoef
            buf[i] *= g
        }
        curGain = g
        inputPeak = DspMath.peak(buf, n)
        inputRms = DspMath.rms(buf, n)

        // 2. High-pass
        if (p.highPassEnabled) {
            if (!highPassWasOn) highPass.reset()
            highPass.processBlock(buf, n)
        }
        highPassWasOn = p.highPassEnabled

        // 3. Noise reduction (also tracks voice activity for the UI when disabled)
        noise.configure(fx && p.noiseReductionEnabled, p.noiseReductionStrength, fx && p.gateEnabled, p.gateThresholdDb)
        noise.process(buf, n)

        // 4. Voice character
        val voice = if (fx) VoiceProfile.of(p.voiceMode, p.customPitchSemitones) else VoiceProfile()
        pitch.ratio = DspMath.semitonesToRatio(voice.pitchSemitones)
        pitch.process(buf, n)
        ring.process(buf, n, voice.ringMix)

        // 5. Tone controls (user offsets + voice profile offsets)
        if (fx) tone.setTargets(p.bassDb + voice.bassDb, p.midDb + voice.midDb, p.trebleDb + voice.trebleDb)
        else tone.setTargets(0f, 0f, 0f)
        tone.process(buf, n)
        band.configure(voice.bandLowHz, voice.bandHighHz)
        band.process(buf, n)

        // 6. Echo and reverb
        echo.configure(fx && p.echoEnabled, p.echoDelayMs, p.echoFeedback, p.echoMix)
        echo.process(buf, n)
        reverb.configure(fx && p.reverbEnabled, p.reverbRoom, p.reverbMix)
        reverb.process(buf, n)

        // 7. Output volume (smoothed) and limiter
        var v = curVolume
        for (i in 0 until n) {
            v = p.outputVolume + (v - p.outputVolume) * smoothCoef
            buf[i] *= v
        }
        curVolume = v
        val over = limiter.process(buf, n)
        val preClip = over > 0
        blockClipped = preClip || rawInputClipped
        if (preClip) totalClipped += over

        outputPeak = DspMath.peak(buf, n)
        outputRms = DspMath.rms(buf, n)
    }

    fun readMeters(holdPeak: Float): Meters = Meters(
        inputPeak = inputPeak, inputRms = inputRms,
        outputPeak = outputPeak, outputRms = outputRms,
        holdPeak = holdPeak, clipping = blockClipped, clipCount = totalClipped,
        rawInputClipped = rawInputClipped,
        reductionDb = reductionDb, voiceActive = voiceActive, noiseFloorDb = noiseFloorDb
    )

    fun reset() {
        highPass.reset(); noise.reset(); pitch.reset(); tone.reset(); echo.reset(); reverb.reset(); limiter.reset()
        curGain = 1f; curVolume = params.outputVolume
    }

    /** Hard ceiling of the final limiter. */
    val ceiling: Float get() = limiter.ceiling

}
