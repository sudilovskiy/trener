package com.example.trener.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.trener.data.local.entity.WorkoutHeartRateSampleEntity

@Dao
interface WorkoutHeartRateSampleDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertWorkoutHeartRateSample(sample: WorkoutHeartRateSampleEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertWorkoutHeartRateSamples(samples: List<WorkoutHeartRateSampleEntity>): List<Long>

    @Query(
        """
        SELECT * FROM workout_heart_rate_samples
        WHERE workoutSessionId = :workoutSessionId
        ORDER BY timestamp ASC, id ASC
        """
    )
    fun getWorkoutHeartRateSamplesByWorkoutSessionId(
        workoutSessionId: Long
    ): List<WorkoutHeartRateSampleEntity>

    @Query(
        """
        SELECT COUNT(*)
        FROM workout_heart_rate_samples
        WHERE workoutSessionId = :workoutSessionId
        """
    )
    fun countWorkoutHeartRateSamplesByWorkoutSessionId(workoutSessionId: Long): Int
}
