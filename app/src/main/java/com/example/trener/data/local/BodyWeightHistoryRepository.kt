package com.example.trener.data.local

import com.example.trener.data.local.entity.BodyWeightEntryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

class BodyWeightHistoryRepository(
    private val database: TrenerDatabase
) {
    private val bodyWeightHistoryDao = database.bodyWeightHistoryDao()

    suspend fun getLastWeight(): BodyWeightEntryEntity? {
        return withContext(Dispatchers.IO) {
            bodyWeightHistoryDao.getLastWeightEntry()
        }
    }

    suspend fun getWeightForDate(entryDateEpochDay: Long): BodyWeightEntryEntity? {
        return withContext(Dispatchers.IO) {
            bodyWeightHistoryDao.getWeightEntryByDate(entryDateEpochDay)
        }
    }

    suspend fun saveWeightForToday(weightKg: Double): BodyWeightEntryEntity {
        return saveWeightForDate(LocalDate.now().toEpochDay(), weightKg)
    }

    suspend fun saveWeightForDate(
        entryDateEpochDay: Long,
        weightKg: Double
    ): BodyWeightEntryEntity {
        return withContext(Dispatchers.IO) {
            val entry = BodyWeightEntryEntity(
                entryDateEpochDay = entryDateEpochDay,
                weightKg = weightKg
            )
            val existingEntry = getWeightForDate(entryDateEpochDay)

            database.runInTransaction {
                if (existingEntry == null) {
                    bodyWeightHistoryDao.insertBodyWeightEntry(entry)
                } else {
                    bodyWeightHistoryDao.updateBodyWeightEntry(entry)
                }
            }

            entry
        }
    }
}
