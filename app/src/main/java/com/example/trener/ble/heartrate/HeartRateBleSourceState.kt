package com.example.trener.ble.heartrate

import com.example.trener.domain.heartrate.HeartRateConnectionStatus

data class HeartRateBleSourceState(
    val connectionStatus: HeartRateConnectionStatus = HeartRateConnectionStatus.NoData,
    val targetDeviceAddress: String? = null,
    val currentHeartRateBpm: Int? = null,
    val lastSampleEpochMillis: Long? = null,
    val retryDelayMillis: Long? = null,
    val lastErrorMessage: String? = null
)
