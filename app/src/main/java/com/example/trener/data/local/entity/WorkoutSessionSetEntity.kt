package com.example.trener.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_session_sets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutSessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["workoutSessionId"])]
)
data class WorkoutSessionSetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val workoutSessionId: Long,
    val exerciseId: String,
    val setNumber: Int,
    val reps: Int? = null,
    val weight: Double? = null,
    val additionalValue: Double? = null,
    val flag: Boolean? = null,
    val note: String = ""
)
