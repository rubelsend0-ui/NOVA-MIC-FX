package com.nova.micfx.audio

import kotlin.math.PI
import kotlin.math.sin

/** Generated PCM test signals: no microphone or Android runtime required. */
object TestSignals {
    fun sine(freq: Double, amp: Float, frames: Int, sampleRate: Int, startFrame: Int = 0): FloatArray =
        FloatArray(frames) { i -> (amp * sin(2.0 * PI * freq * (i + startFrame) / sampleRate)).toFloat() }

    /** Deterministic uniform noise in [-amp, amp]. */
    fun noise(amp: Float, frames: Int, seed: Long = 1234L): FloatArray {
        var s = seed
        return FloatArray(frames) {
            s = s * 6364136223846793005L + 1442695040888963407L
            amp * (((s ushr 40).toInt() / 8388608f) - 1f)
        }
    }

    /** Runs a processing function over [data] in blocks and returns the processed copy. */
    fun processInBlocks(data: FloatArray, block: Int = 480, fn: (FloatArray, Int) -> Unit): FloatArray {
        val out = data.copyOf()
        val tmp = FloatArray(block)
        var pos = 0
        while (pos < out.size) {
            val n = minOf(block, out.size - pos)
            System.arraycopy(out, pos, tmp, 0, n)
            fn(tmp, n)
            System.arraycopy(tmp, 0, out, pos, n)
            pos += n
        }
        return out
    }

    fun rms(a: FloatArray, from: Int = 0, to: Int = a.size): Float {
        var s = 0.0
        for (i in from until to) s += a[i].toDouble() * a[i]
        return Math.sqrt(s / (to - from)).toFloat()
    }

    fun peak(a: FloatArray, from: Int = 0, to: Int = a.size): Float {
        var p = 0f
        for (i in from until to) p = maxOf(p, Math.abs(a[i]))
        return p
    }

    /** Frequency estimate from rising zero crossings. */
    fun estimateFrequency(a: FloatArray, from: Int, to: Int, sampleRate: Int): Double {
        var c = 0
        for (i in from + 1 until to) if (a[i - 1] <= 0f && a[i] > 0f) c++
        return c / ((to - from).toDouble() / sampleRate)
    }
}
