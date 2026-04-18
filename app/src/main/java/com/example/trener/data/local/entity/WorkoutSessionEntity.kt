package com.example.trener.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val trainingDay: Int,
    val startTimestampEpochMillis: Long,
    val endTimestampEpochMillis: Long? = null,
    val durationSeconds: Long? = null,
    val averageHeartRate: Int? = null,
    val maxHeartRate: Int? = null
)
