package com.example.trener.data.local

import com.example.trener.normalizeBodyWeightKg
import com.example.trener.normalizedWeight
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
            bodyWeightHistoryDao.getLastWeightEntry()?.normalizedWeight()
        }
    }

    suspend fun getWeightForDate(entryDateEpochDay: Long): BodyWeightEntryEntity? {
        return withContext(Dispatchers.IO) {
            bodyWeightHistoryDao.getWeightEntryByDate(entryDateEpochDay)?.normalizedWeight()
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
            val normalizedWeightKg = normalizeBodyWeightKg(weightKg)
            val entry = BodyWeightEntryEntity(
                entryDateEpochDay = entryDateEpochDay,
                weightKg = normalizedWeightKg
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
