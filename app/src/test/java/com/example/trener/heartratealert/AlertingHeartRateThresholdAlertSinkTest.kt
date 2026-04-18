package com.example.trener.heartratealert

import org.junit.Assert.assertEquals
import org.junit.Test

class AlertingHeartRateThresholdAlertSinkTest {
    @Test
    fun delegatesTriggeredHeartRateAndThresholdToDelivery() {
        val delivery = RecordingAlarmDelivery()
        val sink = AlertingHeartRateThresholdAlertSink(delivery)

        sink.onThresholdTriggered(
            heartRateBpm = 137,
            thresholdBpm = 120
        )

        assertEquals(listOf(137 to 120), delivery.alerts)
    }

    private class RecordingAlarmDelivery : HeartRateThresholdAlarmDelivery {
        val alerts = mutableListOf<Pair<Int, Int>>()

        override fun alert(heartRateBpm: Int, thresholdBpm: Int) {
            alerts += heartRateBpm to thresholdBpm
        }
    }
}
