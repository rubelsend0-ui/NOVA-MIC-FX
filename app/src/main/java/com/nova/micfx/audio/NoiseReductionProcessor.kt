package com.nova.micfx.audio

import kotlin.math.abs

/**
 * Lightweight adaptive noise suppressor + noise gate. This is NOT AI noise cancellation.
 *
 * - Noise floor = quietest 20 ms RMS frame of the last ~2 s (minimum statistics).
 * - Voice activity = frame RMS clearly above the floor (with a short hang time).
 * - Suppressor: when no voice is detected, gain is reduced by up to 24 dB (strength 0..1).
 *   Noise that is present WHILE you speak is not removed.
 * - Gate: separate absolute-threshold gate with hold time.
 */
class NoiseReductionProcessor(private val sampleRate: Int) {
    private var suppressorEnabled = false
    private var strength = 0.5f
    private var gateEnabled = false
    private var gateThresholdLin = DspMath.dbToLinear(-50f)

    private val frameLen = sampleRate / 50          // 20 ms
    private var frameCount = 0
    private var frameEnergy = 0.0
    private var hangFrames = 0

    var noiseFloor = DspMath.dbToLinear(-60f)
        private set
    var voiceActive = false
        private set

    private var supGain = 1f
    private var gateGain = 1f
    private var env = 0f
    private var gateOpen = true
    private var gateHold = 0

    private val envAttack = DspMath.smoothingCoefficient(1f, sampleRate)
    private val envRelease = DspMath.smoothingCoefficient(30f, sampleRate)
    private val supOpen = DspMath.smoothingCoefficient(5f, sampleRate)
    private val supClose = DspMath.smoothingCoefficient(60f, sampleRate)
    private val gateOpenCoef = DspMath.smoothingCoefficient(3f, sampleRate)
    private val gateCloseCoef = DspMath.smoothingCoefficient(50f, sampleRate)
    private val gateHoldSamples = (0.08f * sampleRate).toInt()

    /** Smoothed total reduction applied to the last block, in dB (>= 0). */
    var reductionDb = 0f
        private set

    fun configure(suppressor: Boolean, strength: Float, gate: Boolean, gateThresholdDb: Float) {
        suppressorEnabled = suppressor
        this.strength = strength.safe(0f, 1f, 0.5f)
        gateEnabled = gate
        gateThresholdLin = DspMath.dbToLinear(gateThresholdDb.safe(Limits.GATE_MIN_DB, Limits.GATE_MAX_DB, -50f))
    }

    val noiseFloorDb: Float get() = DspMath.linearToDb(noiseFloor)

    private val history = FloatArray(HISTORY)
    private var histIdx = 0
    private var histFilled = 0

    /** Minimum-statistics style estimate: noise floor = quietest 20 ms frame within the last ~2 s. */
    private fun updateFrame() {
        val rms = Math.sqrt(frameEnergy / frameLen).toFloat()
        frameEnergy = 0.0
        frameCount = 0
        history[histIdx] = rms
        histIdx = (histIdx + 1) % HISTORY
        if (histFilled < HISTORY) histFilled++
        var m = Float.MAX_VALUE
        for (k in 0 until histFilled) if (history[k] < m) m = history[k]
        val target = m.coerceIn(1e-4f, MAX_FLOOR)
        noiseFloor = noiseFloor * 0.8f + target * 0.2f
        val loud = rms > maxOf(noiseFloor * 2.5f, 0.003f)
        if (loud) { voiceActive = true; hangFrames = 10 }
        else if (hangFrames > 0) hangFrames-- else voiceActive = false
    }

    fun process(buf: FloatArray, n: Int) {
        val maxCutDb = strength * 24f
        val idleGain = DspMath.dbToLinear(-maxCutDb)
        var gainSum = 0f
        for (i in 0 until n) {
            val x = buf[i]
            val a = abs(x)
            frameEnergy += x.toDouble() * x
            if (++frameCount >= frameLen) updateFrame()

            var y = x
            if (suppressorEnabled) {
                val target = if (voiceActive) 1f else idleGain
                val c = if (target > supGain) supOpen else supClose
                supGain = target + (supGain - target) * c
                y *= supGain
            } else {
                supGain = 1f
            }

            if (gateEnabled) {
                env = a + (env - a) * (if (a > env) envAttack else envRelease)
                if (env > gateThresholdLin) { gateOpen = true; gateHold = gateHoldSamples }
                else if (gateHold > 0) gateHold-- else if (env < gateThresholdLin * 0.7f) gateOpen = false
                val gt = if (gateOpen) 1f else GATE_FLOOR
                val gc = if (gt > gateGain) gateOpenCoef else gateCloseCoef
                gateGain = gt + (gateGain - gt) * gc
                y *= gateGain
            } else {
                gateGain = 1f; gateOpen = true
            }
            buf[i] = y
            gainSum += supGain * gateGain
        }
        val avg = if (n > 0) gainSum / n else 1f
        reductionDb = if (avg >= 1f) 0f else -DspMath.linearToDb(avg)
    }

    fun reset() {
        supGain = 1f; gateGain = 1f; env = 0f; gateOpen = true; gateHold = 0; reductionDb = 0f
        histIdx = 0; histFilled = 0; frameEnergy = 0.0; frameCount = 0
    }

    companion object {
        const val GATE_FLOOR = 0.001f   // -60 dB
        const val HISTORY = 100         // frames (2 s)
        const val MAX_FLOOR = 0.03f     // sustained signals above about -30 dBFS are never treated as noise
    }
}
