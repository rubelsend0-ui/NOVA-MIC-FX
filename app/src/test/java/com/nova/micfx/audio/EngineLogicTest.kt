package com.nova.micfx.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EngineLogicTest {
    @Test fun normalLifecycle() {
        val m = EngineStateMachine()
        assertEquals(EngineState.IDLE, m.state)
        assertTrue(m.transition(EngineState.STARTING))
        assertTrue(m.transition(EngineState.RUNNING))
        assertTrue(m.transition(EngineState.STOPPING))
        assertTrue(m.transition(EngineState.IDLE))
    }

    @Test fun illegalTransitionsAreRejected() {
        val m = EngineStateMachine()
        assertFalse(m.transition(EngineState.RUNNING))
        assertFalse(m.transition(EngineState.STOPPING))
        assertEquals(EngineState.IDLE, m.state)
        m.transition(EngineState.STARTING)
        m.transition(EngineState.RUNNING)
        m.transition(EngineState.STOPPING)
        assertFalse(m.transition(EngineState.RUNNING))
        assertEquals(EngineState.STOPPING, m.state)
    }

    @Test fun errorCanBeRetried() {
        val m = EngineStateMachine()
        m.transition(EngineState.STARTING)
        assertTrue(m.transition(EngineState.ERROR))
        assertTrue(m.transition(EngineState.STARTING))
        assertTrue(m.transition(EngineState.STOPPING))
        assertTrue(m.transition(EngineState.IDLE))
    }

    @Test fun startAbortedBeforeRunning() {
        val m = EngineStateMachine()
        m.transition(EngineState.STARTING)
        assertTrue(m.transition(EngineState.STOPPING))
        assertEquals(EngineState.STOPPING, m.state)
    }

    @Test fun blockSizesAreTenMilliseconds() {
        assertEquals(480, BufferSizing.blockFrames(48000))
        assertEquals(441, BufferSizing.blockFrames(44100))
        assertEquals(160, BufferSizing.blockFrames(16000))
    }

    @Test fun bufferBytesRespectMinimumAndAreEven() {
        assertEquals(480 * 2 * 2 * 2, BufferSizing.bufferBytes(1000, 480, 2))
        assertEquals(20000, BufferSizing.bufferBytes(20000, 480, 2))
        assertEquals(3842, BufferSizing.bufferBytes(3841, 100, 1))
        assertEquals(480 * 2 * 2 * 4, BufferSizing.bufferBytes(100, 480, 99))   // multiplier clamped to 4
    }

    @Test fun invalidBufferParametersAreRejected() {
        try { BufferSizing.bufferBytes(0, 480, 2); fail() } catch (_: IllegalArgumentException) {}
        try { BufferSizing.bufferBytes(-5, 480, 2); fail() } catch (_: IllegalArgumentException) {}
        try { BufferSizing.blockFrames(0); fail() } catch (_: IllegalArgumentException) {}
        try { BufferSizing.bufferBytes(100, 0, 2); fail() } catch (_: IllegalArgumentException) {}
    }

    @Test fun sampleRateSelection() {
        assertEquals(44100, BufferSizing.chooseSampleRate(44100, 48000) { true })
        assertEquals(48000, BufferSizing.chooseSampleRate(0, 48000) { true })
        assertEquals(16000, BufferSizing.chooseSampleRate(0, 0) { it == 16000 })
        assertEquals(48000, BufferSizing.chooseSampleRate(22050, 0) { true })   // unsupported preference ignored
        assertEquals(44100, BufferSizing.chooseSampleRate(48000, 48000) { it == 44100 })
        assertNull(BufferSizing.chooseSampleRate(0, 48000) { false })
        assertTrue(BufferSizing.isSupportedRate(48000))
        assertFalse(BufferSizing.isSupportedRate(22050))
    }

    @Test fun latencyDurationMath() {
        assertEquals(10f, BufferSizing.durationMs(480, 48000), 1e-4f)
        assertEquals(0f, BufferSizing.durationMs(480, 0), 0f)
    }
}
