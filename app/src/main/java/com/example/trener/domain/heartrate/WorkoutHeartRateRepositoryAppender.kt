package com.example.trener.domain.heartrate

import com.example.trener.data.local.WorkoutHeartRateRepository
import com.example.trener.data.local.entity.WorkoutHeartRateSampleEntity

class WorkoutHeartRateRepositoryAppender(
    private val repository: WorkoutHeartRateRepository
) : HeartRateSampleAppender {
    override suspend fun append(workoutSessionId: Long, sample: HeartRateSample) {
        repository.appendHeartRateSample(
            WorkoutHeartRateSampleEntity(
                workoutSessionId = workoutSessionId,
                timestamp = sample.timestampEpochMillis,
                heartRate = sample.heartRateBpm
            )
        )
    }
}
