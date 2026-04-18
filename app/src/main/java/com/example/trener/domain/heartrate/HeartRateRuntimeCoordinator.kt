package com.example.trener.domain.heartrate

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HeartRateRuntimeCoordinator(
    private val staleTimeoutMillis: Long = DEFAULT_STALE_TIMEOUT_MILLIS,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val sampleAppender: HeartRateSampleAppender? = null,
    private val thresholdAlertSink: HeartRateThresholdAlertSink? = null
) {
    private val _state = MutableStateFlow(HeartRateRuntimeState())
    val state: StateFlow<HeartRateRuntimeState> = _state.asStateFlow()

    private var workoutLoggingSessionState = WorkoutLoggingSessionState()

    fun configureThreshold(thresholdBpm: Int?) {
        _state.value = _state.value.withThreshold(thresholdBpm)
    }

    fun publishDiagnostics(diagnostics: HeartRateDiagnosticsState) {
        _state.value = _state.value.copy(diagnostics = diagnostics)
    }

    fun bindWorkoutSession(workoutSessionId: Long) {
        workoutLoggingSessionState = workoutLoggingSessionState.bind(workoutSessionId)
    }

    fun beginFinalizingWorkoutSession(workoutSessionId: Long): Boolean {
        val updatedState = workoutLoggingSessionState.beginFinalizing(workoutSessionId)
        val changed = updatedState != workoutLoggingSessionState
        workoutLoggingSessionState = updatedState
        return changed || workoutLoggingSessionState.isFinalizing(workoutSessionId)
    }

    fun restoreActiveWorkoutSessionBinding(workoutSessionId: Long): Boolean {
        val updatedState = workoutLoggingSessionState.restoreActive(workoutSessionId)
        val changed = updatedState != workoutLoggingSessionState
        workoutLoggingSessionState = updatedState
        return changed || workoutLoggingSessionState.isActive(workoutSessionId)
    }

    fun clearWorkoutSessionBinding(workoutSessionId: Long? = null) {
        if (workoutSessionId == null || workoutLoggingSessionState.workoutSessionId == workoutSessionId) {
            workoutLoggingSessionState = WorkoutLoggingSessionState()
        }
    }

    fun markConnected() {
        updateConnectionStatus(HeartRateConnectionStatus.Connected)
    }

    fun markReconnecting() {
        updateConnectionStatus(HeartRateConnectionStatus.Reconnecting)
    }

    fun markNoData() {
        updateConnectionStatus(HeartRateConnectionStatus.NoData)
    }

    fun ingestHeartRateSample(
        heartRateBpm: Int,
        timestampEpochMillis: Long = nowEpochMillis()
    ): HeartRateRuntimeState {
        val currentState = _state.value
        val updatedState = currentState.afterHeartRateSample(
            heartRateBpm = heartRateBpm,
            timestampEpochMillis = timestampEpochMillis,
            staleTimeoutMillis = staleTimeoutMillis
        )
        _state.value = updatedState
        return updatedState
    }

    suspend fun captureHeartRateSample(
        heartRateBpm: Int,
        timestampEpochMillis: Long = nowEpochMillis()
    ): HeartRateRuntimeState {
        val updatedState = ingestHeartRateSample(
            heartRateBpm = heartRateBpm,
            timestampEpochMillis = timestampEpochMillis
        )
        val workoutSessionId = workoutLoggingSessionState.workoutSessionId
        val appender = sampleAppender
        if (workoutSessionId != null &&
            workoutLoggingSessionState.isActive(workoutSessionId) &&
            appender != null
        ) {
            appender.append(
                workoutSessionId = workoutSessionId,
                sample = HeartRateSample(
                    heartRateBpm = heartRateBpm,
                    timestampEpochMillis = timestampEpochMillis
                )
            )
        }
        return updatedState
    }

    fun refreshStaleness(nowEpochMillis: Long = nowEpochMillis()): HeartRateRuntimeState {
        val currentState = _state.value
        val updatedState = currentState.withStalenessEvaluated(
            nowEpochMillis = nowEpochMillis,
            staleTimeoutMillis = staleTimeoutMillis
        )
        _state.value = updatedState
        return updatedState
    }

    fun reset() {
        workoutLoggingSessionState = WorkoutLoggingSessionState()
        _state.value = HeartRateRuntimeState()
    }

    private fun updateConnectionStatus(
        connectionStatus: HeartRateConnectionStatus
    ) {
        _state.value = _state.value.copy(
            connectionStatus = connectionStatus,
            isStale = _state.value.isStale && _state.value.currentHeartRateBpm != null
        )
    }

    private fun HeartRateRuntimeState.withThreshold(thresholdBpm: Int?): HeartRateRuntimeState {
        val nextThresholdState = when {
            thresholdBpm == null -> HeartRateThresholdAlertState.Armed
            currentHeartRateBpm == null -> HeartRateThresholdAlertState.Armed
            currentHeartRateBpm <= thresholdBpm -> HeartRateThresholdAlertState.Armed
            thresholdAlertState == HeartRateThresholdAlertState.Triggered -> HeartRateThresholdAlertState.Triggered
            else -> HeartRateThresholdAlertState.Armed
        }

        return copy(
            thresholdBpm = thresholdBpm,
            thresholdAlertState = nextThresholdState
        )
    }

    private fun HeartRateRuntimeState.afterHeartRateSample(
        heartRateBpm: Int,
        timestampEpochMillis: Long,
        staleTimeoutMillis: Long
    ): HeartRateRuntimeState {
        val previousHeartRate = currentHeartRateBpm
        val thresholdTransition = evaluateThresholdTransition(
            previousHeartRateBpm = previousHeartRate,
            currentHeartRateBpm = heartRateBpm,
            thresholdBpm = thresholdBpm,
            thresholdAlertState = thresholdAlertState
        )
        if (thresholdTransition.shouldNotify) {
            thresholdAlertSink?.onThresholdTriggered(
                heartRateBpm = heartRateBpm,
                thresholdBpm = requireNotNull(thresholdTransition.thresholdBpm)
            )
        }

        return copy(
            currentHeartRateBpm = heartRateBpm,
            lastUpdateEpochMillis = timestampEpochMillis,
            connectionStatus = HeartRateConnectionStatus.Connected,
            isStale = false,
            thresholdAlertState = thresholdTransition.nextState
        ).withStalenessEvaluated(
            nowEpochMillis = timestampEpochMillis,
            staleTimeoutMillis = staleTimeoutMillis
        )
    }

    private fun HeartRateRuntimeState.withStalenessEvaluated(
        nowEpochMillis: Long,
        staleTimeoutMillis: Long
    ): HeartRateRuntimeState {
        val lastUpdate = lastUpdateEpochMillis
        val stale = currentHeartRateBpm != null &&
            lastUpdate != null &&
            nowEpochMillis - lastUpdate >= staleTimeoutMillis

        return copy(isStale = stale)
    }

    private fun evaluateThresholdTransition(
        previousHeartRateBpm: Int?,
        currentHeartRateBpm: Int,
        thresholdBpm: Int?,
        thresholdAlertState: HeartRateThresholdAlertState
    ): ThresholdTransitionResult {
        val configuredThreshold = thresholdBpm ?: return ThresholdTransitionResult(
            nextState = HeartRateThresholdAlertState.Armed,
            shouldNotify = false,
            thresholdBpm = null
        )

        return when {
            currentHeartRateBpm <= configuredThreshold -> ThresholdTransitionResult(
                nextState = HeartRateThresholdAlertState.Armed,
                shouldNotify = false,
                thresholdBpm = configuredThreshold
            )

            previousHeartRateBpm != null &&
                previousHeartRateBpm <= configuredThreshold &&
                thresholdAlertState == HeartRateThresholdAlertState.Armed ->
                ThresholdTransitionResult(
                    nextState = HeartRateThresholdAlertState.Triggered,
                    shouldNotify = true,
                    thresholdBpm = configuredThreshold
                )

            else -> ThresholdTransitionResult(
                nextState = thresholdAlertState,
                shouldNotify = false,
                thresholdBpm = configuredThreshold
            )
        }
    }

    private fun WorkoutLoggingSessionState.bind(workoutSessionId: Long): WorkoutLoggingSessionState {
        return when {
            this.workoutSessionId == workoutSessionId && phase == WorkoutLoggingSessionPhase.Active ->
                this

            this.workoutSessionId == workoutSessionId && phase == WorkoutLoggingSessionPhase.Finalizing ->
                this

            else -> copy(
                workoutSessionId = workoutSessionId,
                phase = WorkoutLoggingSessionPhase.Active
            )
        }
    }

    private fun WorkoutLoggingSessionState.beginFinalizing(
        workoutSessionId: Long
    ): WorkoutLoggingSessionState {
        return when {
            this.workoutSessionId != workoutSessionId -> this
            phase == WorkoutLoggingSessionPhase.Finalizing -> this
            else -> copy(phase = WorkoutLoggingSessionPhase.Finalizing)
        }
    }

    private fun WorkoutLoggingSessionState.restoreActive(
        workoutSessionId: Long
    ): WorkoutLoggingSessionState {
        return when {
            this.workoutSessionId != workoutSessionId -> this
            phase == WorkoutLoggingSessionPhase.Active -> this
            else -> copy(phase = WorkoutLoggingSessionPhase.Active)
        }
    }

    private fun WorkoutLoggingSessionState.isActive(workoutSessionId: Long): Boolean {
        return this.workoutSessionId == workoutSessionId &&
            phase == WorkoutLoggingSessionPhase.Active
    }

    private fun WorkoutLoggingSessionState.isFinalizing(workoutSessionId: Long): Boolean {
        return this.workoutSessionId == workoutSessionId &&
            phase == WorkoutLoggingSessionPhase.Finalizing
    }

    private companion object {
        private const val DEFAULT_STALE_TIMEOUT_MILLIS = 12_000L
    }
}

private data class ThresholdTransitionResult(
    val nextState: HeartRateThresholdAlertState,
    val shouldNotify: Boolean,
    val thresholdBpm: Int?
)

private data class WorkoutLoggingSessionState(
    val workoutSessionId: Long? = null,
    val phase: WorkoutLoggingSessionPhase = WorkoutLoggingSessionPhase.Unbound
)

private enum class WorkoutLoggingSessionPhase {
    Unbound,
    Active,
    Finalizing
}
