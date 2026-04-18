package com.example.trener.data.local

import com.example.trener.WorkoutProgramDayDefinition
import com.example.trener.WorkoutProgramExerciseDefinition
import com.example.trener.WorkoutProgramSnapshot
import com.example.trener.data.local.entity.ExerciseCatalogEntity
import com.example.trener.exerciseDefinitionsByDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TrainingProgramRepository(
    private val database: TrenerDatabase
) {
    private val trainingDayDao = database.trainingDayDao()
    private val trainingDayExerciseDao = database.trainingDayExerciseDao()
    private val exerciseCatalogDao = database.exerciseCatalogDao()

    suspend fun getWorkoutProgramSnapshot(): WorkoutProgramSnapshot {
        return withContext(Dispatchers.IO) {
            val trainingDays = trainingDayDao.getTrainingDaysOrdered()
            if (trainingDays.isEmpty()) {
                return@withContext buildLegacyWorkoutProgramSnapshot()
            }

            val catalogById = exerciseCatalogDao.getAllExercisesOrdered()
                .associateBy(ExerciseCatalogEntity::id)

            val dayDefinitions = trainingDays.mapIndexed { index, day ->
                val linkedExercises = trainingDayExerciseDao
                    .getTrainingDayExercisesByTrainingDayId(day.id)
                    .mapNotNull { link ->
                        val catalogExercise = catalogById[link.catalogExerciseId] ?: return@mapNotNull null
                        catalogExercise.toWorkoutProgramExerciseDefinition(
                            sortOrder = link.sortOrder
                        )
                    }
                    .sortedWith(compareBy<WorkoutProgramExerciseDefinition> { it.sortOrder }.thenBy { it.exerciseId })

                WorkoutProgramDayDefinition(
                    id = day.id,
                    name = day.name,
                    sortOrder = day.sortOrder,
                    runtimeDayNumber = index + 1,
                    exercises = linkedExercises
                )
            }

            if (dayDefinitions.isEmpty()) {
                buildLegacyWorkoutProgramSnapshot()
            } else {
                WorkoutProgramSnapshot(days = dayDefinitions)
            }
        }
    }

    private fun ExerciseCatalogEntity.toWorkoutProgramExerciseDefinition(
        sortOrder: Int
    ): WorkoutProgramExerciseDefinition {
        return WorkoutProgramExerciseDefinition(
            exerciseId = id,
            name = name,
            parameterType = parameterType,
            parameterLabel = parameterLabel,
            parameterUnit = parameterUnit,
            sortOrder = sortOrder
        )
    }
}

private fun buildLegacyWorkoutProgramSnapshot(): WorkoutProgramSnapshot {
    val days = exerciseDefinitionsByDay.entries
        .sortedBy { it.key }
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

    return WorkoutProgramSnapshot(days = days)
}
