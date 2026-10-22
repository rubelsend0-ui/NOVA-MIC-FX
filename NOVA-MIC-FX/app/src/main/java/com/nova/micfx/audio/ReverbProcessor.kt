package com.nova.micfx.audio

/** Compact Schroeder/Freeverb-style reverb: 4 damped comb filters in parallel + 2 all-pass filters. */
class ReverbProcessor(sampleRate: Int) {
    private class Comb(size: Int) {
        val buf = FloatArray(size)
        var idx = 0
        var store = 0f
    }

    private class AllPass(size: Int) {
        val buf = FloatArray(size)
        var idx = 0
    }

    private val scale = sampleRate / 44100f
    private val combs = intArrayOf(1116, 1188, 1277, 1356).map { Comb(maxOf(8, (it * scale).toInt())) }
    private val allPasses = intArrayOf(556, 441).map { AllPass(maxOf(8, (it * scale).toInt())) }
    private var feedback = 0.8f
    private val damping = 0.35f
    private var mix = 0.25f
    private var enabled = false

    fun configure(enabled: Boolean, room: Float, mix: Float) {
        if (!enabled && this.enabled) reset()
        this.enabled = enabled
        feedback = 0.7f + 0.2f * room.safe(0f, 1f, 0.5f)       // 0.70 .. 0.90 (always < 1)
        this.mix = mix.safe(0f, 1f, 0.25f)
    }

    val isEnabled: Boolean get() = enabled

    fun process(buf: FloatArray, n: Int) {
        if (!enabled) return
        for (i in 0 until n) {
            val x = buf[i]
            val input = x * 0.03f
            var wet = 0f
            for (c in combs) {
                val out = c.buf[c.idx]
                c.store = out * (1f - damping) + c.store * damping
                c.buf[c.idx] = input + c.store * feedback
                if (++c.idx >= c.buf.size) c.idx = 0
                wet += out
            }
            for (a in allPasses) {
                val bufOut = a.buf[a.idx]
                val y = -wet + bufOut
                a.buf[a.idx] = wet + bufOut * 0.5f
                if (++a.idx >= a.buf.size) a.idx = 0
                wet = y
            }
            buf[i] = x * (1f - 0.5f * mix) + wet * 4f * mix
        }
    }

    fun reset() {
        for (c in combs) { java.util.Arrays.fill(c.buf, 0f); c.store = 0f }
        for (a in allPasses) java.util.Arrays.fill(a.buf, 0f)
    }
}
