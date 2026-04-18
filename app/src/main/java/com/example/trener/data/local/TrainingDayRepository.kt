package com.example.trener.data.local

import com.example.trener.data.local.entity.TrainingDayEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TrainingDayRepository(
    private val database: TrenerDatabase
) {
    private val trainingDayDao = database.trainingDayDao()

    suspend fun getTrainingDays(): List<TrainingDayEntity> {
        return withContext(Dispatchers.IO) {
            trainingDayDao.getTrainingDaysOrdered()
        }
    }

    suspend fun addTrainingDay(name: String): TrainingDayEntity {
        return withContext(Dispatchers.IO) {
            val nextSortOrder = (trainingDayDao.getTrainingDaysOrdered()
                .maxOfOrNull { it.sortOrder } ?: -1) + 1
            val normalizedName = normalizeTrainingDayName(name)
            val day = TrainingDayEntity(
                name = normalizedName,
                sortOrder = nextSortOrder
            )
            val id = trainingDayDao.insertTrainingDay(day)
            day.copy(id = id)
        }
    }

    suspend fun renameTrainingDay(trainingDayId: Long, name: String): TrainingDayEntity? {
        return withContext(Dispatchers.IO) {
            val existing = trainingDayDao.getTrainingDayById(trainingDayId) ?: return@withContext null
            val updated = existing.copy(name = normalizeTrainingDayName(name))
            trainingDayDao.updateTrainingDay(updated)
            updated
        }
    }

    suspend fun getTrainingDay(trainingDayId: Long): TrainingDayEntity? {
        return withContext(Dispatchers.IO) {
            trainingDayDao.getTrainingDayById(trainingDayId)
        }
    }

    suspend fun deleteTrainingDay(trainingDayId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            val existing = trainingDayDao.getTrainingDayById(trainingDayId) ?: return@withContext false
            trainingDayDao.deleteTrainingDay(existing)
            true
        }
    }

    suspend fun moveTrainingDayUp(trainingDayId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            val orderedDays = trainingDayDao.getTrainingDaysOrdered()
            val currentIndex = orderedDays.indexOfFirst { it.id == trainingDayId }
            if (currentIndex <= 0) {
                return@withContext false
            }

            val currentDay = orderedDays[currentIndex]
            val previousDay = orderedDays[currentIndex - 1]
            database.runInTransaction {
                trainingDayDao.updateTrainingDay(
                    currentDay.copy(sortOrder = previousDay.sortOrder)
                )
                trainingDayDao.updateTrainingDay(
                    previousDay.copy(sortOrder = currentDay.sortOrder)
                )
            }
            true
        }
    }

    suspend fun moveTrainingDayDown(trainingDayId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            val orderedDays = trainingDayDao.getTrainingDaysOrdered()
            val currentIndex = orderedDays.indexOfFirst { it.id == trainingDayId }
            if (currentIndex < 0 || currentIndex >= orderedDays.lastIndex) {
                return@withContext false
            }

            val currentDay = orderedDays[currentIndex]
            val nextDay = orderedDays[currentIndex + 1]
            database.runInTransaction {
                trainingDayDao.updateTrainingDay(
                    currentDay.copy(sortOrder = nextDay.sortOrder)
                )
                trainingDayDao.updateTrainingDay(
                    nextDay.copy(sortOrder = currentDay.sortOrder)
                )
            }
            true
        }
    }
}

private fun normalizeTrainingDayName(name: String): String {
    return name.trim().ifBlank { "День" }
}
