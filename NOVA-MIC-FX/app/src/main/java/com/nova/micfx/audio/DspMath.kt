package com.nova.micfx.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

object DspMath {
    const val SILENCE_DB = -120f

    fun dbToLinear(db: Float): Float = 10.0.pow(db / 20.0).toFloat()

    fun linearToDb(linear: Float): Float {
        if (linear.isNaN() || linear <= 1e-6f) return SILENCE_DB
        return (20.0 * log10(linear.toDouble())).toFloat().coerceAtLeast(SILENCE_DB)
    }

    fun semitonesToRatio(semitones: Float): Float = 2.0.pow(semitones / 12.0).toFloat()

    /** One-pole smoothing coefficient for a time constant; y = target + (y - target) * coef. */
    fun smoothingCoefficient(timeMs: Float, sampleRate: Int): Float {
        if (timeMs <= 0f) return 0f
        return exp(-1.0 / (timeMs * 0.001 * sampleRate)).toFloat()
    }

    fun peak(buf: FloatArray, n: Int): Float {
        var p = 0f
        for (i in 0 until n) {
            val a = abs(buf[i])
            if (a > p) p = a
        }
        return p
    }

    fun rms(buf: FloatArray, n: Int): Float {
        if (n <= 0) return 0f
        var s = 0.0
        for (i in 0 until n) s += buf[i].toDouble() * buf[i]
        return sqrt(s / n).toFloat()
    }

    fun applyGain(buf: FloatArray, n: Int, gain: Float) {
        for (i in 0 until n) buf[i] *= gain
    }

    /** Number of samples at or above [threshold] magnitude. */
    fun countAtOrAbove(buf: FloatArray, n: Int, threshold: Float): Int {
        var c = 0
        for (i in 0 until n) if (abs(buf[i]) >= threshold) c++
        return c
    }

    fun shortToFloat(s: Short): Float = s / 32768f

    fun floatToShort(f: Float): Short {
        val v = f * 32767f
        return when {
            v.isNaN() -> 0
            v >= 32767f -> 32767
            v <= -32768f -> -32768
            else -> v.toInt()
        }.toShort()
    }
}
