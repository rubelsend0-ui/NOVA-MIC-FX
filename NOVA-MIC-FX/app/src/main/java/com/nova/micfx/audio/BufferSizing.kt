package com.nova.micfx.audio

object BufferSizing {
    val SUPPORTED_RATES = intArrayOf(48000, 44100, 16000)
    const val BYTES_PER_SAMPLE = 2          // PCM 16-bit mono

    fun isSupportedRate(rate: Int): Boolean = SUPPORTED_RATES.contains(rate)

    /** 10 ms processing block, clamped to a sane range. */
    fun blockFrames(sampleRate: Int): Int {
        require(sampleRate > 0) { "sampleRate must be positive" }
        return (sampleRate / 100).coerceIn(128, 2048)
    }

    /** Size in bytes for an AudioRecord/AudioTrack buffer: never below the platform minimum. */
    fun bufferBytes(minBufferBytes: Int, blockFrames: Int, multiplier: Int): Int {
        require(minBufferBytes > 0) { "Platform reported an invalid minimum buffer size ($minBufferBytes)" }
        require(blockFrames > 0) { "blockFrames must be positive" }
        val wanted = blockFrames * BYTES_PER_SAMPLE * 2 * multiplier.coerceIn(1, 4)
        val bytes = maxOf(minBufferBytes, wanted)
        return (bytes + 1) and 1.inv()          // keep an even number of bytes
    }

    fun durationMs(frames: Int, sampleRate: Int): Float =
        if (sampleRate <= 0) 0f else frames * 1000f / sampleRate

    /** Picks the first rate that is supported and accepted by the platform (min size > 0). */
    fun chooseSampleRate(preferred: Int, nativeRate: Int, platformAccepts: (Int) -> Boolean): Int? {
        val order = ArrayList<Int>()
        if (preferred > 0) order.add(preferred)
        if (nativeRate > 0) order.add(nativeRate)
        for (r in SUPPORTED_RATES) order.add(r)
        return order.distinct().firstOrNull { isSupportedRate(it) && platformAccepts(it) }
    }
}
