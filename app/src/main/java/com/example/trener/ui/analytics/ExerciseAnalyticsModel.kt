package com.example.trener

import android.content.Context

enum class ExerciseProgressRangeMode {
    SevenDays,
    ThirtyDays,
    All,
    Custom
}

data class ExerciseProgressRange(
    val startEpochDay: Long,
    val endEpochDay: Long
) {
    val dayCount: Long
        get() = (endEpochDay - startEpochDay + 1).coerceAtLeast(1L)
}

data class ExerciseProgressPoint(
    val entryDateEpochDay: Long,
    val totalReps: Int
)

data class ExerciseProgressAxisTick(
    val value: Double,
    val step: Double
)

fun getAllExercises(): List<ExerciseDefinition> {
    return exerciseDefinitionsByDay.values.flatten()
}

fun ExerciseProgressRangeMode.displayLabel(context: Context): String {
    return when (this) {
        ExerciseProgressRangeMode.SevenDays -> context.getString(R.string.exercise_progress_range_7_days)
        ExerciseProgressRangeMode.ThirtyDays -> context.getString(R.string.exercise_progress_range_30_days)
        ExerciseProgressRangeMode.All -> context.getString(R.string.exercise_progress_range_all)
        ExerciseProgressRangeMode.Custom -> context.getString(R.string.exercise_progress_range_custom)
    }
}
