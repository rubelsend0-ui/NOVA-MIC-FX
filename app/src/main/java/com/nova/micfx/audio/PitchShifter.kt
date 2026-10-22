package com.nova.micfx.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Real pitch shifter (duration preserving): two crossfaded read heads moving through a delay line
 * at [ratio] times the write speed. Typical speech quality, adds roughly 20 ms latency while active.
 * This is NOT a playback-rate trick: speed and duration of the speech do not change.
 */
class PitchShifter(sampleRate: Int, windowMs: Float = 40f) {
    private val window = (sampleRate * windowMs / 1000f).coerceAtLeast(64f)
    private val size: Int = Integer.highestOneBit((2 * window).toInt() + 8) * 2
    private val mask = size - 1
    private val buf = FloatArray(size)
    private var w = 0
    private var phase = 0f
    var ratio = 1f
        set(value) { field = value.coerceIn(0.5f, 2f) }

    val isActive: Boolean get() = abs(ratio - 1f) > 1e-4f

    private fun read(delay: Float): Float {
        var pos = w - delay
        while (pos < 0f) pos += size
        val i0 = pos.toInt() and mask
        val frac = pos - pos.toInt()
        return buf[i0] * (1f - frac) + buf[(i0 + 1) and mask] * frac
    }

    fun process(data: FloatArray, n: Int) {
        val inc = (1f - ratio) / window
        val active = isActive
        for (i in 0 until n) {
            val x = data[i]
            buf[w] = x
            if (active) {
                val p2 = if (phase + 0.5f >= 1f) phase - 0.5f else phase + 0.5f
                val g1 = sin(PI.toFloat() * phase)
                val g2 = sin(PI.toFloat() * p2)
                data[i] = read(phase * window) * g1 * g1 + read(p2 * window) * g2 * g2
                phase += inc
                if (phase >= 1f) phase -= 1f else if (phase < 0f) phase += 1f
            }
            w = (w + 1) and mask
        }
    }

    fun reset() { java.util.Arrays.fill(buf, 0f); phase = 0f }
}

/** Ring modulator used for the Robot voice. */
class RingModulator(private val sampleRate: Int, private val carrierHz: Float = 50f) {
    private var phase = 0.0
    fun process(buf: FloatArray, n: Int, mix: Float) {
        if (mix <= 0f) return
        val inc = 2.0 * PI * carrierHz / sampleRate
        for (i in 0 until n) {
            val c = sin(phase).toFloat()
            buf[i] = buf[i] * (1f - mix) + buf[i] * c * mix
            phase += inc
            if (phase > 2.0 * PI) phase -= 2.0 * PI
        }
    }
}
