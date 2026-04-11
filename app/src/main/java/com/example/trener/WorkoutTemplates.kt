package com.example.trener

import android.content.Context
import androidx.annotation.StringRes
import com.example.trener.data.local.entity.WorkoutSessionSetEntity
import com.example.trener.domain.workout.PreparedWorkoutSessionSet

data class ExerciseDefinition(
    val exerciseId: String,
    @StringRes val labelResId: Int,
    val inputType: ExerciseInputType
)

val exerciseDefinitionsByDay = mapOf(
    1 to listOf(
        ExerciseDefinition("pull_ups_ui", R.string.exercise_pull_ups_ui, ExerciseInputType.REPS),
        ExerciseDefinition("ab_wheel_ui", R.string.exercise_ab_wheel_ui, ExerciseInputType.REPS),
        ExerciseDefinition("wide_push_ups_ui", R.string.exercise_wide_push_ups_ui, ExerciseInputType.REPS),
        ExerciseDefinition("dumbbell_flyes_ui", R.string.exercise_dumbbell_flyes_ui, ExerciseInputType.REPS),
        ExerciseDefinition("spring_expander_ui", R.string.exercise_spring_expander_ui, ExerciseInputType.REPS)
    ),
    2 to listOf(
        ExerciseDefinition(
            "reverse_grip_pull_ups_ui",
            R.string.exercise_reverse_grip_pull_ups_ui,
            ExerciseInputType.REPS
        ),
        ExerciseDefinition("dumbbell_biceps_ui", R.string.exercise_dumbbell_biceps_ui, ExerciseInputType.REPS),
        ExerciseDefinition("bench_abs_ui", R.string.exercise_bench_abs_ui, ExerciseInputType.TIME_SECONDS),
        ExerciseDefinition(
            "bench_back_extensions_ui",
            R.string.exercise_bench_back_extensions_ui,
            ExerciseInputType.REPS
        )
    ),
    3 to listOf(
        ExerciseDefinition("push_ups_ui", R.string.exercise_push_ups_ui, ExerciseInputType.REPS),
        ExerciseDefinition("dips_ui", R.string.exercise_dips_ui, ExerciseInputType.REPS),
        ExerciseDefinition("dumbbell_triceps_ui", R.string.exercise_dumbbell_triceps_ui, ExerciseInputType.REPS),
        ExerciseDefinition("dips_abs_ui", R.string.exercise_dips_abs_ui, ExerciseInputType.TIME_SECONDS),
        ExerciseDefinition("neck_ui", R.string.exercise_neck_ui, ExerciseInputType.REPS)
    )
)

private val exerciseDefinitionsById = exerciseDefinitionsByDay
    .values
    .flatten()
    .associateBy(ExerciseDefinition::exerciseId)

fun getExercisesForDay(trainingDay: Int): List<ExerciseDefinition> {
    return exerciseDefinitionsByDay[trainingDay].orEmpty()
}

fun getExerciseDefinition(exerciseId: String): ExerciseDefinition? {
    return exerciseDefinitionsById[exerciseId]
}

fun getExerciseLabel(context: Context, exerciseId: String): String {
    val definition = getExerciseDefinition(exerciseId) ?: return exerciseId
    return context.getString(definition.labelResId)
}

fun List<PreparedWorkoutSessionSet>.groupPersistedSetsByExercise(): List<WorkoutExerciseWithSets> {
    return groupBy(PreparedWorkoutSessionSet::exerciseId)
        .map { (exerciseId, sets) ->
            WorkoutExerciseWithSets(
                exerciseId = exerciseId,
                sets = sets.sortedBy(PreparedWorkoutSessionSet::setNumber)
            )
        }
}

fun List<WorkoutSessionSetEntity>.groupWorkoutSessionEntitiesByExercise(): List<WorkoutExerciseWithPersistedSets> {
    return groupBy(WorkoutSessionSetEntity::exerciseId)
        .map { (exerciseId, sets) ->
            WorkoutExerciseWithPersistedSets(
                exerciseId = exerciseId,
                sets = sets.sortedBy(WorkoutSessionSetEntity::setNumber)
            )
        }
}
