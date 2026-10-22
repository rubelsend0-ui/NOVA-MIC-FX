package com.nova.micfx.audio

import kotlin.math.abs

/**
 * Peak limiter: instant attack, smooth release, plus a hard safety clamp so the output can never
 * exceed [ceiling]. Not-a-number samples are replaced by silence.
 */
class LimiterProcessor(sampleRate: Int, ceiling: Float = 0.95f, releaseMs: Float = 80f) {
    /** Output ceiling (linear). Can be changed while running. */
    @Volatile var ceiling: Float = ceiling.coerceIn(0.05f, 0.999f)
    private val releaseCoef = DspMath.smoothingCoefficient(releaseMs, sampleRate)
    var gain = 1f
        private set

    /** Current gain reduction in dB (0 when the limiter is idle, negative when reducing). */
    val gainReductionDb: Float get() = if (gain >= 1f) 0f else DspMath.linearToDb(gain)

    /** @return number of samples whose magnitude exceeded the ceiling before limiting. */
    fun process(buf: FloatArray, n: Int): Int {
        val c = ceiling
        var over = 0
        var g = gain
        for (i in 0 until n) {
            val x = buf[i]
            if (x != x) { buf[i] = 0f; continue }
            val a = abs(x)
            val needed = if (a > c) c / a else 1f
            if (a > c) over++
            val released = 1f - (1f - g) * releaseCoef
            g = if (needed < released) needed else released
            var y = x * g
            if (y > c) y = c else if (y < -c) y = -c
            buf[i] = y
        }
        gain = g
        return over
    }

    fun reset() { gain = 1f }
}
