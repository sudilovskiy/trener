package com.example.trener.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.trener.data.local.entity.WorkoutSessionSetEntity

@Dao
interface WorkoutSessionSetDao {
    @Insert
    fun insertWorkoutSessionSet(set: WorkoutSessionSetEntity): Long

    @Insert
    fun insertWorkoutSessionSets(sets: List<WorkoutSessionSetEntity>): List<Long>

    @Query(
        """
        SELECT * FROM workout_session_sets
        WHERE workoutSessionId = :workoutSessionId
        ORDER BY setNumber ASC, id ASC
        """
    )
    fun getSetsByWorkoutSessionId(workoutSessionId: Long): List<WorkoutSessionSetEntity>

    @Query(
        """
        DELETE FROM workout_session_sets
        WHERE workoutSessionId = :workoutSessionId
        """
    )
    fun deleteWorkoutSessionSetsByWorkoutSessionId(workoutSessionId: Long): Int

    @Query(
        """
        DELETE FROM workout_session_sets
        """
    )
    fun deleteAllWorkoutSessionSets(): Int

    @Query(
        """
        SELECT wss.* FROM workout_session_sets AS wss
        WHERE wss.exerciseId = :exerciseId
          AND wss.workoutSessionId = (
            SELECT ws_inner.id
            FROM workout_sessions AS ws_inner
            INNER JOIN workout_session_sets AS wss_inner
                ON wss_inner.workoutSessionId = ws_inner.id
            WHERE wss_inner.exerciseId = :exerciseId
            ORDER BY ws_inner.startTimestampEpochMillis DESC, ws_inner.id DESC
            LIMIT 1
          )
        ORDER BY wss.setNumber ASC, wss.id ASC
        """
    )
    fun getLatestSetsForExercise(exerciseId: String): List<WorkoutSessionSetEntity>

    @Query(
        """
        SELECT wss.* FROM workout_session_sets AS wss
        WHERE wss.exerciseId = :exerciseId
          AND wss.setNumber = :setNumber
          AND wss.workoutSessionId = (
            SELECT ws_inner.id
            FROM workout_sessions AS ws_inner
            INNER JOIN workout_session_sets AS wss_inner
                ON wss_inner.workoutSessionId = ws_inner.id
            WHERE wss_inner.exerciseId = :exerciseId
            ORDER BY ws_inner.startTimestampEpochMillis DESC, ws_inner.id DESC
            LIMIT 1
          )
        ORDER BY wss.id DESC
        LIMIT 1
        """
    )
    fun getLatestSetForExerciseAndNumber(
        exerciseId: String,
        setNumber: Int
    ): WorkoutSessionSetEntity?
}
