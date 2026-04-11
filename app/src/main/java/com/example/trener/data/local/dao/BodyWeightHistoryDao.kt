package com.example.trener.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.trener.data.local.entity.BodyWeightEntryEntity

@Dao
interface BodyWeightHistoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertBodyWeightEntry(entry: BodyWeightEntryEntity): Long

    @Query(
        """
        SELECT * FROM body_weight_history
        ORDER BY entryDateEpochDay ASC, id ASC
        """
    )
    fun getWeightHistory(): List<BodyWeightEntryEntity>
}
