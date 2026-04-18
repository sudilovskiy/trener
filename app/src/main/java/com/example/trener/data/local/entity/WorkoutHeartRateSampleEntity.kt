package com.example.trener.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_heart_rate_samples",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutSessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["workoutSessionId"]),
        Index(value = ["workoutSessionId", "timestamp"])
    ]
)
data class WorkoutHeartRateSampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val workoutSessionId: Long,
    val timestamp: Long,
    val heartRate: Int
)
