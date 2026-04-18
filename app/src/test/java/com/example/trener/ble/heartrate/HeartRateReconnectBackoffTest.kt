package com.example.trener.ble.heartrate

import org.junit.Assert.assertEquals
import org.junit.Test

class HeartRateReconnectBackoffTest {
    @Test
    fun increasesDelayConservativelyAndCapsAtOneMinute() {
        assertEquals(5_000L, HeartRateReconnectBackoff.delayForAttempt(0))
        assertEquals(10_000L, HeartRateReconnectBackoff.delayForAttempt(1))
        assertEquals(20_000L, HeartRateReconnectBackoff.delayForAttempt(2))
        assertEquals(40_000L, HeartRateReconnectBackoff.delayForAttempt(3))
        assertEquals(60_000L, HeartRateReconnectBackoff.delayForAttempt(4))
        assertEquals(60_000L, HeartRateReconnectBackoff.delayForAttempt(12))
    }
}
