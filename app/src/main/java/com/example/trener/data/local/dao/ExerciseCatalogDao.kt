package com.example.trener.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.trener.data.local.entity.ExerciseCatalogEntity

@Dao
interface ExerciseCatalogDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertExercise(exercise: ExerciseCatalogEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertExercises(exercises: List<ExerciseCatalogEntity>): List<Long>

    @Update
    fun updateExercise(exercise: ExerciseCatalogEntity): Int

    @Delete
    fun deleteExercise(exercise: ExerciseCatalogEntity): Int

    @Query(
        """
        SELECT * FROM exercise_catalog
        WHERE id = :exerciseId
        LIMIT 1
        """
    )
    fun getExerciseById(exerciseId: String): ExerciseCatalogEntity?

    @Query(
        """
        SELECT * FROM exercise_catalog
        WHERE isActive = 1
        ORDER BY sortOrder ASC, id ASC
        """
    )
    fun getActiveExercisesOrdered(): List<ExerciseCatalogEntity>

    @Query(
        """
        SELECT * FROM exercise_catalog
        ORDER BY sortOrder ASC, id ASC
        """
    )
    fun getAllExercisesOrdered(): List<ExerciseCatalogEntity>

    @Query(
        """
        SELECT COUNT(*) FROM exercise_catalog
        """
    )
    fun countExercises(): Int

    @Query(
        """
        DELETE FROM exercise_catalog
        """
    )
    fun deleteAllExercises(): Int
}
