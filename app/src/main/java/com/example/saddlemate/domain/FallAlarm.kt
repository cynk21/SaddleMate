package com.example.saddlemate.domain

/**
 * Countdown after a confirmed fall. The rider can cancel within [countdownSec] seconds
 * ("Ich bin okay"). If the countdown runs out, the SOS path must be triggered.
 *
 * Pure logic, driven by the caller's clock (epoch millis), so it is unit-testable.
 */
class FallAlarm(private val countdownSec: Int = 30) {

    enum class State { IDLE, COUNTING, CANCELLED, SOS_TRIGGERED }

    private var startedAt: Long? = null
    private var state: State = State.IDLE

    val currentState: State get() = state

    /** Seconds left on the countdown at [nowMillis], 0 when not counting. */
    fun remainingSec(nowMillis: Long): Int {
        val start = startedAt ?: return 0
        if (state != State.COUNTING) return 0
        val elapsed = ((nowMillis - start) / 1000L).toInt()
        return (countdownSec - elapsed).coerceAtLeast(0)
    }

    /** Begins the countdown after a confirmed fall. Ignored if already running. */
    fun start(nowMillis: Long) {
        if (state == State.COUNTING) return
        startedAt = nowMillis
        state = State.COUNTING
    }

    /** Rider confirms they are fine. Only valid while counting. */
    fun cancel(): Boolean {
        if (state != State.COUNTING) return false
        state = State.CANCELLED
        return true
    }

    /**
     * Advances the clock. Returns true exactly once, when the countdown expires
     * without cancellation and the SOS path should fire.
     */
    fun tick(nowMillis: Long): Boolean {
        if (state != State.COUNTING) return false
        if (remainingSec(nowMillis) == 0) {
            state = State.SOS_TRIGGERED
            return true
        }
        return false
    }

    fun reset() {
        startedAt = null
        state = State.IDLE
    }
}
