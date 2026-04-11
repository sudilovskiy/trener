package com.example.trener.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.trener.data.local.entity.BodyWeightEntryEntity

@Dao
interface BodyWeightHistoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertBodyWeightEntry(entry: BodyWeightEntryEntity): Long

    @Update
    fun updateBodyWeightEntry(entry: BodyWeightEntryEntity): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertBodyWeightEntry(entry: BodyWeightEntryEntity): Long

    @Query(
        """
        DELETE FROM body_weight_history
        """
    )
    fun deleteAllWeightHistory(): Int

    @Query(
        """
        DELETE FROM body_weight_history
        WHERE entryDateEpochDay = :entryDateEpochDay
        """
    )
    fun deleteWeightEntryByDate(entryDateEpochDay: Long): Int

    @Query(
        """
        DELETE FROM body_weight_history
        WHERE entryDateEpochDay BETWEEN :startEpochDay AND :endEpochDay
        """
    )
    fun deleteWeightEntriesInRange(startEpochDay: Long, endEpochDay: Long): Int

    @Query(
        """
        SELECT * FROM body_weight_history
        ORDER BY entryDateEpochDay DESC
        LIMIT 1
        """
    )
    fun getLastWeightEntry(): BodyWeightEntryEntity?

    @Query(
        """
        SELECT * FROM body_weight_history
        WHERE entryDateEpochDay = :entryDateEpochDay
        LIMIT 1
        """
    )
    fun getWeightEntryByDate(entryDateEpochDay: Long): BodyWeightEntryEntity?

    @Query(
        """
        SELECT * FROM body_weight_history
        ORDER BY entryDateEpochDay ASC
        """
    )
    fun getWeightHistory(): List<BodyWeightEntryEntity>
}
