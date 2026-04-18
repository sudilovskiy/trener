package com.example.trener.heartratealert

interface HeartRateThresholdAlarmDelivery {
    fun alert(heartRateBpm: Int, thresholdBpm: Int)
}
