package com.example.trener.data.local

import android.database.sqlite.SQLiteConstraintException
import com.example.trener.data.local.entity.ExerciseCatalogEntity
import com.example.trener.data.local.entity.TrainingDayEntity
import com.example.trener.data.local.entity.TrainingDayExerciseEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TrainingDayExerciseItem(
    val link: TrainingDayExerciseEntity,
    val catalogExercise: ExerciseCatalogEntity
)

sealed interface AddTrainingDayExerciseResult {
    data object Added : AddTrainingDayExerciseResult
    data object Duplicate : AddTrainingDayExerciseResult
    data object TrainingDayNotFound : AddTrainingDayExerciseResult
    data object CatalogExerciseNotFound : AddTrainingDayExerciseResult
}

class TrainingDayExerciseRepository(
    private val database: TrenerDatabase
) {
    private val trainingDayDao = database.trainingDayDao()
    private val trainingDayExerciseDao = database.trainingDayExerciseDao()
    private val exerciseCatalogDao = database.exerciseCatalogDao()

    suspend fun getTrainingDay(trainingDayId: Long): TrainingDayEntity? {
        return withContext(Dispatchers.IO) {
            trainingDayDao.getTrainingDayById(trainingDayId)
        }
    }

    suspend fun getTrainingDayExercises(trainingDayId: Long): List<TrainingDayExerciseItem> {
        return withContext(Dispatchers.IO) {
            val dayExercises = trainingDayExerciseDao.getTrainingDayExercisesByTrainingDayId(trainingDayId)
            if (dayExercises.isEmpty()) {
                return@withContext emptyList()
            }

            val catalogExercisesById = exerciseCatalogDao.getAllExercisesOrdered()
                .associateBy { it.id }

            dayExercises.mapNotNull { dayExercise ->
                catalogExercisesById[dayExercise.catalogExerciseId]?.let { catalogExercise ->
                    TrainingDayExerciseItem(
                        link = dayExercise,
                        catalogExercise = catalogExercise
                    )
                }
            }
        }
    }

    suspend fun getCatalogExercises(): List<ExerciseCatalogEntity> {
        return withContext(Dispatchers.IO) {
            exerciseCatalogDao.getAllExercisesOrdered()
        }
    }

    suspend fun addExerciseToDay(
        trainingDayId: Long,
        catalogExerciseId: String
    ): AddTrainingDayExerciseResult {
        return withContext(Dispatchers.IO) {
            val trainingDay = trainingDayDao.getTrainingDayById(trainingDayId)
                ?: return@withContext AddTrainingDayExerciseResult.TrainingDayNotFound
            val catalogExercise = exerciseCatalogDao.getExerciseById(catalogExerciseId)
                ?: return@withContext AddTrainingDayExerciseResult.CatalogExerciseNotFound

            val currentExercises = trainingDayExerciseDao
                .getTrainingDayExercisesByTrainingDayId(trainingDay.id)
            if (currentExercises.any { it.catalogExerciseId == catalogExercise.id }) {
                return@withContext AddTrainingDayExerciseResult.Duplicate
            }

            val nextSortOrder = (currentExercises.maxOfOrNull { it.sortOrder } ?: -1) + 1
            try {
                database.runInTransaction {
                    trainingDayExerciseDao.insertTrainingDayExercise(
                        TrainingDayExerciseEntity(
                            trainingDayId = trainingDay.id,
                            catalogExerciseId = catalogExercise.id,
                            sortOrder = nextSortOrder
                        )
                    )
                }
                AddTrainingDayExerciseResult.Added
            } catch (_: SQLiteConstraintException) {
                AddTrainingDayExerciseResult.Duplicate
            }
        }
    }

    suspend fun removeExerciseFromDay(dayExerciseId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            val target = trainingDayExerciseDao.getTrainingDayExerciseById(dayExerciseId)
                ?: return@withContext false
            trainingDayExerciseDao.deleteTrainingDayExercise(target) > 0
        }
    }

    suspend fun moveExerciseUp(dayExerciseId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            moveExercise(dayExerciseId, moveDown = false)
        }
    }

    suspend fun moveExerciseDown(dayExerciseId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            moveExercise(dayExerciseId, moveDown = true)
        }
    }

    private fun moveExercise(dayExerciseId: Long, moveDown: Boolean): Boolean {
        val current = trainingDayExerciseDao.getTrainingDayExerciseById(dayExerciseId)
            ?: return false
        val orderedExercises = trainingDayExerciseDao
            .getTrainingDayExercisesByTrainingDayId(current.trainingDayId)
        val currentIndex = orderedExercises.indexOfFirst { it.id == dayExerciseId }
        if (currentIndex < 0) {
            return false
        }

        val targetIndex = if (moveDown) currentIndex + 1 else currentIndex - 1
        if (targetIndex !in orderedExercises.indices) {
            return false
        }

        val neighbor = orderedExercises[targetIndex]
        val tempSortOrder = (orderedExercises.maxOfOrNull { it.sortOrder } ?: current.sortOrder) + 1

        database.runInTransaction {
            trainingDayExerciseDao.updateTrainingDayExercise(
                current.copy(sortOrder = tempSortOrder)
            )
            trainingDayExerciseDao.updateTrainingDayExercise(
                neighbor.copy(sortOrder = current.sortOrder)
            )
            trainingDayExerciseDao.updateTrainingDayExercise(
                current.copy(sortOrder = neighbor.sortOrder)
            )
        }
        return true
    }
}
