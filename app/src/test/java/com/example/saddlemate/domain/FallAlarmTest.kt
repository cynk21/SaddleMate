package com.example.saddlemate.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FallAlarmTest {

    @Test
    fun countdownStartsAt30Seconds() {
        val alarm = FallAlarm()
        alarm.start(nowMillis = 0L)
        assertEquals(30, alarm.remainingSec(nowMillis = 0L))
    }

    @Test
    fun cancelWithinCountdown_preventsSos() {
        val alarm = FallAlarm()
        alarm.start(0L)
        assertTrue(alarm.cancel())
        assertFalse(alarm.tick(nowMillis = 60_000L))
        assertEquals(FallAlarm.State.CANCELLED, alarm.currentState)
    }

    @Test
    fun expiredCountdown_triggersSosExactlyOnce() {
        val alarm = FallAlarm()
        alarm.start(0L)
        assertFalse(alarm.tick(nowMillis = 29_000L))
        assertTrue(alarm.tick(nowMillis = 30_000L))
        assertFalse(alarm.tick(nowMillis = 31_000L))
        assertEquals(FallAlarm.State.SOS_TRIGGERED, alarm.currentState)
    }

    @Test
    fun cancelAfterSos_isRejected() {
        val alarm = FallAlarm()
        alarm.start(0L)
        alarm.tick(30_000L)
        assertFalse(alarm.cancel())
    }

    @Test
    fun remainingCountsDown() {
        val alarm = FallAlarm()
        alarm.start(0L)
        assertEquals(25, alarm.remainingSec(nowMillis = 5_000L))
        assertEquals(0, alarm.remainingSec(nowMillis = 45_000L))
    }

    @Test
    fun startWhileCounting_doesNotRestart() {
        val alarm = FallAlarm()
        alarm.start(0L)
        alarm.start(10_000L)
        assertEquals(20, alarm.remainingSec(nowMillis = 10_000L))
    }

    @Test
    fun reset_returnsToIdle() {
        val alarm = FallAlarm()
        alarm.start(0L)
        alarm.reset()
        assertEquals(FallAlarm.State.IDLE, alarm.currentState)
        assertEquals(0, alarm.remainingSec(nowMillis = 1_000L))
    }
}
