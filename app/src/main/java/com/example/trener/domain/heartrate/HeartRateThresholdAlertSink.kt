package com.example.trener.domain.heartrate

fun interface HeartRateThresholdAlertSink {
    fun onThresholdTriggered(heartRateBpm: Int, thresholdBpm: Int)
}
