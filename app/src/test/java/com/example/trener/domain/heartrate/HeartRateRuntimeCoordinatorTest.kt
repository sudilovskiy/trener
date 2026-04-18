package com.example.trener.domain.heartrate

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRateRuntimeCoordinatorTest {
    @Test
    fun triggersThresholdOnlyOnUpwardCrossing() {
        val alertSink = RecordingAlertSink()
        val coordinator = HeartRateRuntimeCoordinator(thresholdAlertSink = alertSink)

        coordinator.configureThreshold(120)
        coordinator.ingestHeartRateSample(118, timestampEpochMillis = 1_000L)
        assertEquals(HeartRateThresholdAlertState.Armed, coordinator.state.value.thresholdAlertState)
        assertTrue(alertSink.triggeredHeartRates.isEmpty())

        coordinator.ingestHeartRateSample(121, timestampEpochMillis = 2_000L)

        assertEquals(HeartRateThresholdAlertState.Triggered, coordinator.state.value.thresholdAlertState)
        assertEquals(listOf(121), alertSink.triggeredHeartRates)
    }

    @Test
    fun doesNotRetriggerWhileStillAboveThresholdAndReArmsAfterDroppingBelow() {
        val coordinator = HeartRateRuntimeCoordinator()

        coordinator.configureThreshold(120)
        coordinator.ingestHeartRateSample(118, timestampEpochMillis = 1_000L)
        coordinator.ingestHeartRateSample(121, timestampEpochMillis = 2_000L)

        assertEquals(HeartRateThresholdAlertState.Triggered, coordinator.state.value.thresholdAlertState)

        coordinator.ingestHeartRateSample(125, timestampEpochMillis = 3_000L)

        assertEquals(HeartRateThresholdAlertState.Triggered, coordinator.state.value.thresholdAlertState)

        coordinator.ingestHeartRateSample(119, timestampEpochMillis = 4_000L)

        assertEquals(HeartRateThresholdAlertState.Armed, coordinator.state.value.thresholdAlertState)

        coordinator.ingestHeartRateSample(122, timestampEpochMillis = 5_000L)

        assertEquals(HeartRateThresholdAlertState.Triggered, coordinator.state.value.thresholdAlertState)
    }

    @Test
    fun thresholdAlertSinkDoesNotFireOnEveryAboveThresholdSample() {
        val alertSink = RecordingAlertSink()
        val coordinator = HeartRateRuntimeCoordinator(thresholdAlertSink = alertSink)

        coordinator.configureThreshold(120)
        coordinator.ingestHeartRateSample(119, timestampEpochMillis = 1_000L)
        coordinator.ingestHeartRateSample(121, timestampEpochMillis = 2_000L)
        coordinator.ingestHeartRateSample(125, timestampEpochMillis = 3_000L)
        coordinator.ingestHeartRateSample(118, timestampEpochMillis = 4_000L)
        coordinator.ingestHeartRateSample(122, timestampEpochMillis = 5_000L)

        assertEquals(listOf(121, 122), alertSink.triggeredHeartRates)
    }

    @Test
    fun thresholdAlertSinkFiresAgainOnlyAfterReArmOnSecondCrossing() {
        val alertSink = RecordingAlertSink()
        val coordinator = HeartRateRuntimeCoordinator(thresholdAlertSink = alertSink)

        coordinator.configureThreshold(120)
        coordinator.ingestHeartRateSample(119, timestampEpochMillis = 1_000L)
        coordinator.ingestHeartRateSample(121, timestampEpochMillis = 2_000L)
        coordinator.ingestHeartRateSample(125, timestampEpochMillis = 3_000L)
        coordinator.ingestHeartRateSample(119, timestampEpochMillis = 4_000L)
        coordinator.ingestHeartRateSample(122, timestampEpochMillis = 5_000L)

        assertEquals(listOf(121, 122), alertSink.triggeredHeartRates)
        assertEquals(HeartRateThresholdAlertState.Triggered, coordinator.state.value.thresholdAlertState)
    }

    @Test
    fun marksDataStaleWithoutClearingLatestValue() {
        val coordinator = HeartRateRuntimeCoordinator(staleTimeoutMillis = 12_000L)

        coordinator.ingestHeartRateSample(115, timestampEpochMillis = 1_000L)

        val freshState = coordinator.refreshStaleness(nowEpochMillis = 13_000L)

        assertTrue(freshState.isStale)
        assertEquals(115, freshState.currentHeartRateBpm)
        assertEquals(1_000L, freshState.lastUpdateEpochMillis)

        val retainedState = coordinator.state.value
        assertEquals(115, retainedState.currentHeartRateBpm)
        assertEquals(1_000L, retainedState.lastUpdateEpochMillis)
    }

    @Test
    fun transitionsBetweenConnectedReconnectingAndNoData() {
        val coordinator = HeartRateRuntimeCoordinator()

        assertEquals(HeartRateConnectionStatus.NoData, coordinator.state.value.connectionStatus)
        assertNull(coordinator.state.value.currentHeartRateBpm)
        assertFalse(coordinator.state.value.isStale)

        coordinator.markConnected()
        assertEquals(HeartRateConnectionStatus.Connected, coordinator.state.value.connectionStatus)

        coordinator.ingestHeartRateSample(108, timestampEpochMillis = 2_000L)
        assertEquals(HeartRateConnectionStatus.Connected, coordinator.state.value.connectionStatus)

        coordinator.markReconnecting()
        assertEquals(HeartRateConnectionStatus.Reconnecting, coordinator.state.value.connectionStatus)
        assertEquals(108, coordinator.state.value.currentHeartRateBpm)

        coordinator.markNoData()
        assertEquals(HeartRateConnectionStatus.NoData, coordinator.state.value.connectionStatus)
        assertEquals(108, coordinator.state.value.currentHeartRateBpm)
    }

    @Test
    fun captureHeartRateSampleAppendsToBoundWorkoutSession() = runBlocking {
        val appender = RecordingAppender()
        val coordinator = HeartRateRuntimeCoordinator(sampleAppender = appender)

        coordinator.bindWorkoutSession(77L)
        coordinator.captureHeartRateSample(
            heartRateBpm = 133,
            timestampEpochMillis = 9_000L
        )

        assertEquals(listOf(77L), appender.workoutSessionIds)
        assertEquals(
            listOf(HeartRateSample(heartRateBpm = 133, timestampEpochMillis = 9_000L)),
            appender.samples
        )
        assertEquals(133, coordinator.state.value.currentHeartRateBpm)
        assertEquals(HeartRateConnectionStatus.Connected, coordinator.state.value.connectionStatus)
    }

    @Test
    fun captureHeartRateSampleDoesNotAppendWithoutAnActiveWorkoutBinding() = runBlocking {
        val appender = RecordingAppender()
        val coordinator = HeartRateRuntimeCoordinator(sampleAppender = appender)

        coordinator.captureHeartRateSample(
            heartRateBpm = 133,
            timestampEpochMillis = 9_000L
        )

        assertTrue(appender.samples.isEmpty())
        assertNull(appender.workoutSessionIds.firstOrNull())
    }

    @Test
    fun captureHeartRateSampleRejectsSamplesWhileFinalizingAndRestoresAfterFailure() = runBlocking {
        val appender = RecordingAppender()
        val coordinator = HeartRateRuntimeCoordinator(sampleAppender = appender)

        coordinator.bindWorkoutSession(77L)
        coordinator.beginFinalizingWorkoutSession(77L)

        coordinator.captureHeartRateSample(
            heartRateBpm = 133,
            timestampEpochMillis = 9_000L
        )

        assertTrue(appender.samples.isEmpty())

        coordinator.restoreActiveWorkoutSessionBinding(77L)
        coordinator.captureHeartRateSample(
            heartRateBpm = 134,
            timestampEpochMillis = 10_000L
        )

        assertEquals(
            listOf(HeartRateSample(heartRateBpm = 134, timestampEpochMillis = 10_000L)),
            appender.samples
        )
        assertEquals(listOf(77L), appender.workoutSessionIds)
    }

    @Test
    fun finalizingAndClearingWorkoutBindingAreIdempotent() = runBlocking {
        val appender = RecordingAppender()
        val coordinator = HeartRateRuntimeCoordinator(sampleAppender = appender)

        coordinator.bindWorkoutSession(77L)
        assertTrue(coordinator.beginFinalizingWorkoutSession(77L))
        assertTrue(coordinator.beginFinalizingWorkoutSession(77L))
        coordinator.clearWorkoutSessionBinding(77L)
        coordinator.clearWorkoutSessionBinding(77L)

        coordinator.captureHeartRateSample(
            heartRateBpm = 135,
            timestampEpochMillis = 11_000L
        )

        assertTrue(appender.samples.isEmpty())
    }

    private class RecordingAppender : HeartRateSampleAppender {
        val workoutSessionIds = mutableListOf<Long>()
        val samples = mutableListOf<HeartRateSample>()

        override suspend fun append(workoutSessionId: Long, sample: HeartRateSample) {
            workoutSessionIds += workoutSessionId
            samples += sample
        }
    }

    private class RecordingAlertSink : HeartRateThresholdAlertSink {
        val triggeredHeartRates = mutableListOf<Int>()

        override fun onThresholdTriggered(heartRateBpm: Int, thresholdBpm: Int) {
            triggeredHeartRates += heartRateBpm
        }
    }
}
