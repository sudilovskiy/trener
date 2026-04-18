package com.example.trener.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.trener.data.local.entity.TrainingDayEntity

@Dao
interface TrainingDayDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertTrainingDay(trainingDay: TrainingDayEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertTrainingDays(trainingDays: List<TrainingDayEntity>): List<Long>

    @Update
    fun updateTrainingDay(trainingDay: TrainingDayEntity): Int

    @Delete
    fun deleteTrainingDay(trainingDay: TrainingDayEntity): Int

    @Query(
        """
        SELECT * FROM training_days
        WHERE id = :trainingDayId
        LIMIT 1
        """
    )
    fun getTrainingDayById(trainingDayId: Long): TrainingDayEntity?

    @Query(
        """
        SELECT * FROM training_days
        ORDER BY sortOrder ASC, id ASC
        """
    )
    fun getTrainingDaysOrdered(): List<TrainingDayEntity>

    @Query(
        """
        SELECT COUNT(*) FROM training_days
        """
    )
    fun countTrainingDays(): Int

    @Query(
        """
        DELETE FROM training_days
        """
    )
    fun deleteAllTrainingDays(): Int
}
