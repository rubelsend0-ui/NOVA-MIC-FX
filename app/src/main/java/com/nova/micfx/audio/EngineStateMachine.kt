package com.nova.micfx.audio

/** Thread-safe engine lifecycle: IDLE -> STARTING -> RUNNING -> STOPPING -> IDLE (ERROR from any active state). */
class EngineStateMachine {
    private var current = EngineState.IDLE
    val state: EngineState @Synchronized get() = current

    @Synchronized
    fun canTransition(to: EngineState): Boolean = ALLOWED[current]?.contains(to) == true

    /** @return true if the transition was legal and has been applied. */
    @Synchronized
    fun transition(to: EngineState): Boolean {
        if (!canTransition(to)) return false
        current = to
        return true
    }

    companion object {
        private val ALLOWED: Map<EngineState, Set<EngineState>> = mapOf(
            EngineState.IDLE to setOf(EngineState.STARTING, EngineState.ERROR),
            EngineState.STARTING to setOf(EngineState.RUNNING, EngineState.STOPPING, EngineState.ERROR),
            EngineState.RUNNING to setOf(EngineState.STOPPING, EngineState.ERROR),
            EngineState.STOPPING to setOf(EngineState.IDLE),
            EngineState.ERROR to setOf(EngineState.STARTING, EngineState.IDLE)
        )
    }
}
