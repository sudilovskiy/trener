package com.example.trener

import android.content.Context
import com.example.trener.data.local.catalogParameterLabelForType
import com.example.trener.data.local.catalogParameterTypeForInputType
import com.example.trener.data.local.catalogParameterUnitForType
import com.example.trener.data.local.entity.WorkoutSessionSetEntity

data class WorkoutProgramExerciseDefinition(
    val exerciseId: String,
    val name: String,
    val parameterType: String,
    val parameterLabel: String,
    val parameterUnit: String = "",
    val sortOrder: Int
) {
    val inputType: ExerciseInputType
        get() = resolveExerciseInputType(parameterType)
}

data class WorkoutProgramDayDefinition(
    val id: Long,
    val name: String,
    val sortOrder: Int,
    val runtimeDayNumber: Int,
    val exercises: List<WorkoutProgramExerciseDefinition>
)

data class WorkoutProgramSnapshot(
    val days: List<WorkoutProgramDayDefinition>
)

object WorkoutProgramCache {
    @Volatile
    private var snapshot: WorkoutProgramSnapshot = buildLegacyWorkoutProgramSnapshot()

    fun getSnapshot(): WorkoutProgramSnapshot {
        return snapshot
    }

    fun updateSnapshot(updatedSnapshot: WorkoutProgramSnapshot) {
        snapshot = updatedSnapshot
    }
}

fun getWorkoutProgramDays(): List<WorkoutProgramDayDefinition> {
    return WorkoutProgramCache.getSnapshot().days
}

fun getExercisesForDay(trainingDay: Int): List<WorkoutProgramExerciseDefinition> {
    return WorkoutProgramCache.getSnapshot().days
        .firstOrNull { day -> day.runtimeDayNumber == trainingDay }
        ?.exercises
        .orEmpty()
}

fun getExerciseDefinition(
    exerciseId: String,
    preferredDefinitions: Iterable<WorkoutProgramExerciseDefinition> = emptyList()
): WorkoutProgramExerciseDefinition? {
    preferredDefinitions.firstOrNull { exercise -> exercise.exerciseId == exerciseId }?.let { preferred ->
        return preferred
    }

    return WorkoutProgramCache.getSnapshot().days
        .asSequence()
        .flatMap { day -> day.exercises.asSequence() }
        .firstOrNull { exercise -> exercise.exerciseId == exerciseId }
        ?: exerciseDefinitionsByDay.values
            .flatten()
            .firstOrNull { exercise -> exercise.exerciseId == exerciseId }
            ?.let { legacyExercise ->
                val parameterType = catalogParameterTypeForInputType(legacyExercise.inputType)
                WorkoutProgramExerciseDefinition(
                    exerciseId = legacyExercise.exerciseId,
                    name = legacyExercise.exerciseId,
                    parameterType = parameterType,
                    parameterLabel = catalogParameterLabelForType(parameterType),
                    parameterUnit = catalogParameterUnitForType(parameterType),
                    sortOrder = 0
                )
            }
}

fun getExerciseLabel(
    context: Context,
    exerciseId: String,
    preferredDefinitions: Iterable<WorkoutProgramExerciseDefinition> = emptyList()
): String {
    val workoutProgramExercise = getExerciseDefinition(
        exerciseId = exerciseId,
        preferredDefinitions = preferredDefinitions
    )
    if (workoutProgramExercise != null) {
        val name = workoutProgramExercise.name.trim()
        if (name.isNotEmpty() && name != workoutProgramExercise.exerciseId) {
            return name
        }
    }

    val legacyDefinition = exerciseDefinitionsById[exerciseId] ?: return exerciseId
    return context.getString(legacyDefinition.labelResId)
}

fun resolveWorkoutExerciseDefinitionFromHistory(
    exerciseId: String,
    persistedSets: List<WorkoutSessionSetEntity> = emptyList(),
    preferredDefinitions: Iterable<WorkoutProgramExerciseDefinition> = emptyList()
): WorkoutProgramExerciseDefinition {
    val inferredInputType = inferExerciseInputTypeFromPersistedSets(persistedSets)
    return getExerciseDefinition(
        exerciseId = exerciseId,
        preferredDefinitions = preferredDefinitions
    )?.let { definition ->
        if (persistedSets.isNotEmpty() && definition.inputType != inferredInputType) {
            definition.copy(parameterType = inferredInputType.name)
        } else {
            definition
        }
    } ?: WorkoutProgramExerciseDefinition(
        exerciseId = exerciseId,
        name = exerciseId,
        parameterType = inferredInputType.name,
        parameterLabel = legacyParameterLabelFor(inferredInputType),
        parameterUnit = legacyParameterUnitFor(inferredInputType),
        sortOrder = Int.MAX_VALUE
    )
}

private fun inferExerciseInputTypeFromPersistedSets(
    persistedSets: List<WorkoutSessionSetEntity>
): ExerciseInputType {
    return if (persistedSets.any { set ->
            set.additionalValue != null && set.reps == null
        }
    ) {
        ExerciseInputType.TIME_SECONDS
    } else {
        ExerciseInputType.REPS
    }
}

fun getWorkoutProgramExerciseIdsForDay(trainingDay: Int): List<String> {
    return getExercisesForDay(trainingDay).map(WorkoutProgramExerciseDefinition::exerciseId)
}

fun List<com.example.trener.domain.workout.PreparedWorkoutSessionSet>.groupPersistedSetsByExercise(): List<WorkoutExerciseWithSets> {
    return groupBy(com.example.trener.domain.workout.PreparedWorkoutSessionSet::exerciseId)
        .map { (exerciseId, sets) ->
            WorkoutExerciseWithSets(
                exerciseId = exerciseId,
                sets = sets.sortedBy(com.example.trener.domain.workout.PreparedWorkoutSessionSet::setNumber)
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

private fun buildLegacyWorkoutProgramSnapshot(): WorkoutProgramSnapshot {
    val legacyDays = exerciseDefinitionsByDay.entries
        .sortedBy { (trainingDay, _) -> trainingDay }
        .map { (trainingDay, definitions) ->
            WorkoutProgramDayDefinition(
                id = trainingDay.toLong(),
                name = "\u0414\u0435\u043d\u044c $trainingDay",
                sortOrder = trainingDay - 1,
                runtimeDayNumber = trainingDay,
                exercises = definitions.mapIndexed { index, definition ->
                    val parameterType = catalogParameterTypeForInputType(definition.inputType)
                    WorkoutProgramExerciseDefinition(
                        exerciseId = definition.exerciseId,
                        name = definition.exerciseId,
                        parameterType = parameterType,
                        parameterLabel = catalogParameterLabelForType(parameterType),
                        parameterUnit = catalogParameterUnitForType(parameterType),
                        sortOrder = index
                    )
                }
            )
        }

    return WorkoutProgramSnapshot(days = legacyDays)
}

private fun legacyParameterLabelFor(inputType: ExerciseInputType): String {
    return catalogParameterLabelForType(catalogParameterTypeForInputType(inputType))
}

private fun legacyParameterUnitFor(inputType: ExerciseInputType): String {
    return catalogParameterUnitForType(catalogParameterTypeForInputType(inputType))
}

private fun resolveExerciseInputType(parameterType: String): ExerciseInputType {
    return when (parameterType.trim().uppercase()) {
        ExerciseInputType.TIME_SECONDS.name -> ExerciseInputType.TIME_SECONDS
        else -> ExerciseInputType.REPS
    }
}
