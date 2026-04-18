package com.example.trener.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.trener.data.local.entity.TrainingDayExerciseEntity

@Dao
interface TrainingDayExerciseDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertTrainingDayExercise(trainingDayExercise: TrainingDayExerciseEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertTrainingDayExercises(trainingDayExercises: List<TrainingDayExerciseEntity>): List<Long>

    @Update
    fun updateTrainingDayExercise(trainingDayExercise: TrainingDayExerciseEntity): Int

    @Delete
    fun deleteTrainingDayExercise(trainingDayExercise: TrainingDayExerciseEntity): Int

    @Query(
        """
        SELECT * FROM training_day_exercises
        WHERE id = :dayExerciseId
        LIMIT 1
        """
    )
    fun getTrainingDayExerciseById(dayExerciseId: Long): TrainingDayExerciseEntity?

    @Query(
        """
        SELECT * FROM training_day_exercises
        WHERE trainingDayId = :trainingDayId
        ORDER BY sortOrder ASC, id ASC
        """
    )
    fun getTrainingDayExercisesByTrainingDayId(trainingDayId: Long): List<TrainingDayExerciseEntity>

    @Query(
        """
        SELECT * FROM training_day_exercises
        WHERE catalogExerciseId = :catalogExerciseId
        ORDER BY trainingDayId ASC, sortOrder ASC, id ASC
        """
    )
    fun getTrainingDayExercisesByCatalogExerciseId(catalogExerciseId: String): List<TrainingDayExerciseEntity>

    @Query(
        """
        SELECT COUNT(*) FROM training_day_exercises
        WHERE catalogExerciseId = :catalogExerciseId
        """
    )
    fun countTrainingDayExercisesByCatalogExerciseId(catalogExerciseId: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM training_day_exercises
        """
    )
    fun countTrainingDayExercises(): Int

    @Query(
        """
        DELETE FROM training_day_exercises
        """
    )
    fun deleteAllTrainingDayExercises(): Int
}
