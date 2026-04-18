package com.example.trener.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "training_day_exercises",
    foreignKeys = [
        ForeignKey(
            entity = TrainingDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["trainingDayId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ExerciseCatalogEntity::class,
            parentColumns = ["id"],
            childColumns = ["catalogExerciseId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["trainingDayId"]),
        Index(value = ["catalogExerciseId"]),
        Index(value = ["trainingDayId", "catalogExerciseId"], unique = true),
        Index(value = ["trainingDayId", "sortOrder"])
    ]
)
data class TrainingDayExerciseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val trainingDayId: Long,
    val catalogExerciseId: String,
    val sortOrder: Int
)
