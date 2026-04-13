package com.example.trener

import com.example.trener.data.local.entity.WorkoutSessionSetEntity
import com.example.trener.domain.workout.PreparedWorkoutSessionSet

enum class ExerciseInputType {
    REPS,
    TIME_SECONDS
}

data class WorkoutExerciseWithSets(
    val exerciseId: String,
    val sets: List<PreparedWorkoutSessionSet>
)

data class ExerciseSetUiState(
    val set: PreparedWorkoutSessionSet,
    val isCompleted: Boolean,
    val isLocked: Boolean,
    val isActiveTarget: Boolean
)

data class ExerciseSetCompletionResult(
    val nextActiveSetNumber: Int?,
    val isExerciseCompleted: Boolean
)

data class WorkoutExerciseWithPersistedSets(
    val exerciseId: String,
    val sets: List<WorkoutSessionSetEntity>
)

data class WorkoutHistoryItem(
    val id: Long,
    val trainingDay: Int,
    val dateText: String,
    val durationText: String,
    val exerciseSummaryText: String
)
