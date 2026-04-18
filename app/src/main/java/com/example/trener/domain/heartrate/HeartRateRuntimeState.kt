package com.example.trener.domain.heartrate

data class HeartRateRuntimeState(
    val currentHeartRateBpm: Int? = null,
    val lastUpdateEpochMillis: Long? = null,
    val connectionStatus: HeartRateConnectionStatus = HeartRateConnectionStatus.NoData,
    val isStale: Boolean = false,
    val thresholdBpm: Int? = null,
    val thresholdAlertState: HeartRateThresholdAlertState = HeartRateThresholdAlertState.Armed,
    val diagnostics: HeartRateDiagnosticsState = HeartRateDiagnosticsState()
)
