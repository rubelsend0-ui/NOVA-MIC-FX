package com.nova.micfx.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Normalised biquad coefficients (a0 == 1): y = b0 x + b1 x1 + b2 x2 - a1 y1 - a2 y2. */
data class BiquadCoefficients(
    val b0: Double, val b1: Double, val b2: Double,
    val a1: Double, val a2: Double
) {
    /** Stability triangle: both poles inside the unit circle. */
    fun isStable(): Boolean = abs(a2) < 1.0 && abs(a1) < 1.0 + a2

    companion object {
        val IDENTITY = BiquadCoefficients(1.0, 0.0, 0.0, 0.0, 0.0)
    }
}

/** RBJ "Audio EQ Cookbook" designs. */
object BiquadDesign {
    private fun clampFreq(fs: Int, f: Double) = f.coerceIn(10.0, fs * 0.45)

    private fun norm(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
        BiquadCoefficients(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)

    fun lowPass(fs: Int, freq: Double, q: Double = 0.7071): BiquadCoefficients {
        val w0 = 2.0 * PI * clampFreq(fs, freq) / fs
        val c = cos(w0); val alpha = sin(w0) / (2.0 * q)
        return norm((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + alpha, -2 * c, 1 - alpha)
    }

    fun highPass(fs: Int, freq: Double, q: Double = 0.7071): BiquadCoefficients {
        val w0 = 2.0 * PI * clampFreq(fs, freq) / fs
        val c = cos(w0); val alpha = sin(w0) / (2.0 * q)
        return norm((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha)
    }

    fun peaking(fs: Int, freq: Double, gainDb: Double, q: Double = 0.9): BiquadCoefficients {
        val a = Math.pow(10.0, gainDb / 40.0)
        val w0 = 2.0 * PI * clampFreq(fs, freq) / fs
        val c = cos(w0); val alpha = sin(w0) / (2.0 * q)
        return norm(1 + alpha * a, -2 * c, 1 - alpha * a, 1 + alpha / a, -2 * c, 1 - alpha / a)
    }

    fun lowShelf(fs: Int, freq: Double, gainDb: Double): BiquadCoefficients {
        val a = Math.pow(10.0, gainDb / 40.0)
        val w0 = 2.0 * PI * clampFreq(fs, freq) / fs
        val c = cos(w0)
        val alpha = sin(w0) / 2.0 * sqrt(2.0)          // shelf slope S = 1
        val t = 2.0 * sqrt(a) * alpha
        return norm(
            a * ((a + 1) - (a - 1) * c + t),
            2 * a * ((a - 1) - (a + 1) * c),
            a * ((a + 1) - (a - 1) * c - t),
            (a + 1) + (a - 1) * c + t,
            -2 * ((a - 1) + (a + 1) * c),
            (a + 1) + (a - 1) * c - t
        )
    }

    fun highShelf(fs: Int, freq: Double, gainDb: Double): BiquadCoefficients {
        val a = Math.pow(10.0, gainDb / 40.0)
        val w0 = 2.0 * PI * clampFreq(fs, freq) / fs
        val c = cos(w0)
        val alpha = sin(w0) / 2.0 * sqrt(2.0)
        val t = 2.0 * sqrt(a) * alpha
        return norm(
            a * ((a + 1) + (a - 1) * c + t),
            -2 * a * ((a - 1) + (a + 1) * c),
            a * ((a + 1) + (a - 1) * c - t),
            (a + 1) - (a - 1) * c + t,
            2 * ((a - 1) - (a + 1) * c),
            (a + 1) - (a - 1) * c - t
        )
    }
}

/** Transposed direct-form II biquad (double precision state, allocation free). */
class BiquadFilter(initial: BiquadCoefficients = BiquadCoefficients.IDENTITY) {
    private var b0 = initial.b0
    private var b1 = initial.b1
    private var b2 = initial.b2
    private var a1 = initial.a1
    private var a2 = initial.a2
    private var z1 = 0.0
    private var z2 = 0.0

    /** Rejects unstable coefficient sets (keeps the previous, stable ones). */
    fun setCoefficients(c: BiquadCoefficients): Boolean {
        if (!c.isStable()) return false
        b0 = c.b0; b1 = c.b1; b2 = c.b2; a1 = c.a1; a2 = c.a2
        return true
    }

    fun reset() { z1 = 0.0; z2 = 0.0 }

    fun process(x: Float): Float {
        val xd = x.toDouble()
        val y = b0 * xd + z1
        z1 = b1 * xd - a1 * y + z2
        z2 = b2 * xd - a2 * y
        if (abs(z1) < 1e-20) z1 = 0.0
        if (abs(z2) < 1e-20) z2 = 0.0
        return y.toFloat()
    }

    fun processBlock(buf: FloatArray, n: Int) {
        for (i in 0 until n) buf[i] = process(buf[i])
    }
}

/**
 * Three-band tone control: low shelf (bass), peaking (voice presence / mids), high shelf (treble).
 * Gains glide toward their targets in small steps per block so slider moves never click.
 */
class ToneEqualizer(private val sampleRate: Int) {
    private val low = BiquadFilter()
    private val mid = BiquadFilter()
    private val high = BiquadFilter()
    private var curBass = 0f
    private var curMid = 0f
    private var curTreble = 0f
    private var tgtBass = 0f
    private var tgtMid = 0f
    private var tgtTreble = 0f

    fun setTargets(bassDb: Float, midDb: Float, trebleDb: Float) {
        tgtBass = bassDb.safe(-MAX_DB, MAX_DB, 0f)
        tgtMid = midDb.safe(-MAX_DB, MAX_DB, 0f)
        tgtTreble = trebleDb.safe(-MAX_DB, MAX_DB, 0f)
    }

    /** Jump immediately to the targets (used by tests and on start-up). */
    fun snapToTargets() {
        curBass = tgtBass; curMid = tgtMid; curTreble = tgtTreble
        updateCoefficients()
    }

    val isNeutral: Boolean get() = abs(curBass) < 0.01f && abs(curMid) < 0.01f && abs(curTreble) < 0.01f

    private fun step(cur: Float, tgt: Float): Float {
        val d = tgt - cur
        return when {
            d > MAX_STEP_DB -> cur + MAX_STEP_DB
            d < -MAX_STEP_DB -> cur - MAX_STEP_DB
            else -> tgt
        }
    }

    private fun updateCoefficients() {
        low.setCoefficients(BiquadDesign.lowShelf(sampleRate, BASS_HZ, curBass.toDouble()))
        mid.setCoefficients(BiquadDesign.peaking(sampleRate, MID_HZ, curMid.toDouble(), 0.9))
        high.setCoefficients(BiquadDesign.highShelf(sampleRate, TREBLE_HZ, curTreble.toDouble()))
    }

    fun process(buf: FloatArray, n: Int) {
        if (curBass != tgtBass || curMid != tgtMid || curTreble != tgtTreble) {
            curBass = step(curBass, tgtBass); curMid = step(curMid, tgtMid); curTreble = step(curTreble, tgtTreble)
            updateCoefficients()
        }
        for (i in 0 until n) buf[i] = high.process(mid.process(low.process(buf[i])))
    }

    fun reset() { low.reset(); mid.reset(); high.reset() }

    companion object {
        const val BASS_HZ = 120.0
        const val MID_HZ = 2500.0
        const val TREBLE_HZ = 6000.0
        const val MAX_DB = 15f
        const val MAX_STEP_DB = 0.75f
    }
}

/** Optional band-limiting (radio / telephone character): 2nd-order high-pass + low-pass pair. */
class BandLimiter(private val sampleRate: Int) {
    private val hp = BiquadFilter()
    private val lp = BiquadFilter()
    private var active = false
    private var lowHz = 0f
    private var highHz = 0f

    fun configure(lowHz: Float, highHz: Float) {
        val on = highHz > 0f
        if (on && (!active || lowHz != this.lowHz || highHz != this.highHz)) {
            hp.setCoefficients(BiquadDesign.highPass(sampleRate, lowHz.toDouble()))
            lp.setCoefficients(BiquadDesign.lowPass(sampleRate, highHz.toDouble()))
            if (!active) { hp.reset(); lp.reset() }
        }
        active = on
        this.lowHz = lowHz
        this.highHz = highHz
    }

    val isActive: Boolean get() = active

    fun process(buf: FloatArray, n: Int) {
        if (!active) return
        for (i in 0 until n) buf[i] = lp.process(hp.process(buf[i]))
    }
}
