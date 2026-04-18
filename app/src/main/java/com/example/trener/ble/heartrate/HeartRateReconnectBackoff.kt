package com.example.trener.ble.heartrate

object HeartRateReconnectBackoff {
    private const val INITIAL_DELAY_MILLIS = 5_000L
    private const val MAX_DELAY_MILLIS = 60_000L

    fun delayForAttempt(attempt: Int): Long {
        if (attempt <= 0) {
            return INITIAL_DELAY_MILLIS
        }

        val nextDelay = INITIAL_DELAY_MILLIS shl attempt.coerceAtMost(4)
        return nextDelay.coerceAtMost(MAX_DELAY_MILLIS)
    }
}
