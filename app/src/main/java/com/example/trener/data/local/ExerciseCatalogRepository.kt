package com.example.trener.data.local

import com.example.trener.data.local.entity.ExerciseCatalogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

sealed interface ExerciseCatalogSaveResult {
    data object Saved : ExerciseCatalogSaveResult
    data object DuplicateName : ExerciseCatalogSaveResult
    data object NotFound : ExerciseCatalogSaveResult
}

sealed interface ExerciseCatalogDeleteResult {
    data object Deleted : ExerciseCatalogDeleteResult
    data object NotFound : ExerciseCatalogDeleteResult
    data object InUse : ExerciseCatalogDeleteResult
}

class ExerciseCatalogRepository(
    private val database: TrenerDatabase
) {
    private val exerciseCatalogDao = database.exerciseCatalogDao()
    private val trainingDayExerciseDao = database.trainingDayExerciseDao()

    suspend fun getExercises(): List<ExerciseCatalogEntity> {
        return withContext(Dispatchers.IO) {
            exerciseCatalogDao.getAllExercisesOrdered()
        }
    }

    suspend fun createExercise(
        name: String,
        parameterType: String,
        parameterLabel: String
    ): ExerciseCatalogSaveResult {
        return withContext(Dispatchers.IO) {
            val normalizedName = normalizeName(name)
            val normalizedParameterLabel = normalizeParameterLabel(parameterLabel)
            val normalizedParameterType = normalizeCatalogParameterType(parameterType)
            val normalizedParameterUnit = catalogParameterUnitForType(normalizedParameterType)
            if (hasDuplicateName(normalizedName)) {
                return@withContext ExerciseCatalogSaveResult.DuplicateName
            }

            val nextSortOrder = (exerciseCatalogDao.getAllExercisesOrdered()
                .maxOfOrNull { it.sortOrder } ?: -1) + 1
            exerciseCatalogDao.insertExercise(
                ExerciseCatalogEntity(
                    id = UUID.randomUUID().toString(),
                    name = normalizedName,
                    parameterType = normalizedParameterType,
                    parameterLabel = normalizedParameterLabel,
                    parameterUnit = normalizedParameterUnit,
                    sortOrder = nextSortOrder
                )
            )
            ExerciseCatalogSaveResult.Saved
        }
    }

    suspend fun updateExercise(
        exerciseId: String,
        name: String,
        parameterType: String,
        parameterLabel: String
    ): ExerciseCatalogSaveResult {
        return withContext(Dispatchers.IO) {
            val existing = exerciseCatalogDao.getExerciseById(exerciseId)
                ?: return@withContext ExerciseCatalogSaveResult.NotFound
            val normalizedName = normalizeName(name)
            val normalizedParameterLabel = normalizeParameterLabel(parameterLabel)
            val normalizedParameterType = normalizeCatalogParameterType(parameterType)
            val normalizedParameterUnit = catalogParameterUnitForType(normalizedParameterType)
            if (hasDuplicateName(normalizedName, excludeId = exerciseId)) {
                return@withContext ExerciseCatalogSaveResult.DuplicateName
            }

            exerciseCatalogDao.updateExercise(
                existing.copy(
                    name = normalizedName,
                    parameterType = normalizedParameterType,
                    parameterLabel = normalizedParameterLabel,
                    parameterUnit = normalizedParameterUnit
                )
            )
            ExerciseCatalogSaveResult.Saved
        }
    }

    suspend fun deleteExercise(exerciseId: String): ExerciseCatalogDeleteResult {
        return withContext(Dispatchers.IO) {
            val existing = exerciseCatalogDao.getExerciseById(exerciseId)
                ?: return@withContext ExerciseCatalogDeleteResult.NotFound
            if (
                trainingDayExerciseDao.countTrainingDayExercisesByCatalogExerciseId(exerciseId) > 0 ||
                database.workoutSessionSetDao().countWorkoutSessionSetsByExerciseId(exerciseId) > 0
            ) {
                return@withContext ExerciseCatalogDeleteResult.InUse
            }

            exerciseCatalogDao.deleteExercise(existing)
            ExerciseCatalogDeleteResult.Deleted
        }
    }

    private fun hasDuplicateName(name: String, excludeId: String? = null): Boolean {
        return exerciseCatalogDao.getAllExercisesOrdered().any { exercise ->
            exercise.id != excludeId && exercise.name.trim().equals(name, ignoreCase = true)
        }
    }
}

private fun normalizeName(name: String): String {
    return name.trim()
}

private fun normalizeParameterLabel(parameterLabel: String): String {
    return parameterLabel.trim()
}
