package com.nova.micfx.audio

/**
 * Deliberate echo EFFECT (not acoustic echo cancellation). Fixed-size delay line with a damped
 * feedback path; feedback is hard-limited so the loop gain stays below 1 (cannot run away).
 */
class EchoProcessor(sampleRate: Int, maxDelayMs: Int = Limits.ECHO_DELAY_MAX_MS.toInt()) {
    private val sr = sampleRate
    private val size = sampleRate * maxDelayMs / 1000 + 4
    private val line = FloatArray(size)
    private var w = 0
    private var curDelay = 0.25f * sampleRate
    private var tgtDelay = 0.25f * sampleRate
    private var feedback = 0.3f
    private var mix = 0.3f
    private var damp = 0f
    private var enabled = false

    fun configure(enabled: Boolean, delayMs: Float, feedback: Float, mix: Float) {
        if (!enabled && this.enabled) reset()
        if (enabled && !this.enabled) curDelay = delayMs * sr / 1000f
        this.enabled = enabled
        val d = delayMs.safe(Limits.ECHO_DELAY_MIN_MS, Limits.ECHO_DELAY_MAX_MS, 250f)
        tgtDelay = d * sr / 1000f
        this.feedback = feedback.safe(0f, Limits.ECHO_FEEDBACK_MAX, 0.3f)
        this.mix = mix.safe(0f, 1f, 0.3f)
    }

    val isEnabled: Boolean get() = enabled

    fun process(buf: FloatArray, n: Int) {
        if (!enabled) return
        for (i in 0 until n) {
            if (curDelay < tgtDelay) curDelay = minOf(curDelay + 0.1f, tgtDelay)
            else if (curDelay > tgtDelay) curDelay = maxOf(curDelay - 0.1f, tgtDelay)
            var pos = w - curDelay
            if (pos < 0f) pos += size
            val i0 = pos.toInt()
            val frac = pos - i0
            val i1 = if (i0 + 1 >= size) 0 else i0 + 1
            val delayed = line[i0 % size] * (1f - frac) + line[i1] * frac
            damp += 0.6f * (delayed - damp)           // low-pass in the feedback loop
            val x = buf[i]
            line[w] = x + damp * feedback
            buf[i] = x + delayed * mix
            if (++w >= size) w = 0
        }
    }

    fun reset() { java.util.Arrays.fill(line, 0f); damp = 0f }
}
