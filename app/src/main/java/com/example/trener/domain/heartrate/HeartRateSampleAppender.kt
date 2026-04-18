package com.example.trener.domain.heartrate

fun interface HeartRateSampleAppender {
    suspend fun append(workoutSessionId: Long, sample: HeartRateSample)
}
