package com.example.trener.heartratealert

import com.example.trener.domain.heartrate.HeartRateThresholdAlertSink

class AlertingHeartRateThresholdAlertSink(
    private val alarmDelivery: HeartRateThresholdAlarmDelivery
) : HeartRateThresholdAlertSink {
    override fun onThresholdTriggered(heartRateBpm: Int, thresholdBpm: Int) {
        alarmDelivery.alert(
            heartRateBpm = heartRateBpm,
            thresholdBpm = thresholdBpm
        )
    }
}
