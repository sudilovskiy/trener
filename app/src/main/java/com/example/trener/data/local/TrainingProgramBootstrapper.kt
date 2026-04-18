package com.example.trener.data.local

import android.content.Context
import com.example.trener.ExerciseInputType
import com.example.trener.data.local.entity.ExerciseCatalogEntity
import com.example.trener.data.local.entity.TrainingDayEntity
import com.example.trener.data.local.entity.TrainingDayExerciseEntity
import com.example.trener.exerciseDefinitionsByDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TrainingProgramBootstrapper {
    suspend fun ensureBootstrapped(context: Context, database: TrenerDatabase): Boolean {
        return withContext(Dispatchers.IO) {
            val trainingDayDao = database.trainingDayDao()
            val trainingDayExerciseDao = database.trainingDayExerciseDao()
            val exerciseCatalogDao = database.exerciseCatalogDao()

            val trainingDaysEmpty = trainingDayDao.countTrainingDays() == 0
            val trainingDayExercisesEmpty = trainingDayExerciseDao.countTrainingDayExercises() == 0
            val exerciseCatalogEmpty = exerciseCatalogDao.countExercises() == 0

            if (!trainingDaysEmpty || !trainingDayExercisesEmpty || !exerciseCatalogEmpty) {
                return@withContext false
            }

            database.runInTransaction {
                val insertedCatalogIds = exerciseDefinitionsByDay.entries
                    .sortedBy { it.key }
                    .flatMap { (_, definitions) -> definitions }
                    .mapIndexed { index, definition ->
                        ExerciseCatalogEntity(
                            id = definition.exerciseId,
                            name = context.getString(definition.labelResId),
                            parameterType = catalogParameterTypeForInputType(definition.inputType),
                            parameterLabel = defaultParameterLabel(definition.inputType),
                            parameterUnit = defaultParameterUnit(definition.inputType),
                            sortOrder = index
                        )
                    }

                exerciseCatalogDao.insertExercises(insertedCatalogIds)

                val insertedDayIds = exerciseDefinitionsByDay.keys
                    .sorted()
                    .mapIndexed { index, trainingDay ->
                        trainingDayDao.insertTrainingDay(
                            TrainingDayEntity(
                                name = context.getString(
                                    com.example.trener.R.string.training_day_title,
                                    trainingDay
                                ),
                                sortOrder = index
                            )
                        )
                    }

                insertedDayIds.forEachIndexed { index, dayId ->
                    val trainingDay = index + 1
                    val exerciseDefinitions = exerciseDefinitionsByDay[trainingDay].orEmpty()
                    exerciseDefinitions.forEachIndexed { exerciseIndex, definition ->
                        trainingDayExerciseDao.insertTrainingDayExercise(
                            TrainingDayExerciseEntity(
                                trainingDayId = dayId,
                                catalogExerciseId = definition.exerciseId,
                                sortOrder = exerciseIndex
                            )
                        )
                    }
                }
            }

            true
        }
    }

    private fun defaultParameterLabel(inputType: ExerciseInputType): String {
        return catalogParameterLabelForType(catalogParameterTypeForInputType(inputType))
    }

    private fun defaultParameterUnit(inputType: ExerciseInputType): String {
        return catalogParameterUnitForType(catalogParameterTypeForInputType(inputType))
    }
}

