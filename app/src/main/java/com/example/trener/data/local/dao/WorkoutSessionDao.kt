package com.example.trener.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.trener.data.local.entity.WorkoutSessionEntity

@Dao
interface WorkoutSessionDao {
    @Insert
    fun insertWorkoutSession(session: WorkoutSessionEntity): Long

    @Query(
        """
        SELECT * FROM workout_sessions
        WHERE id = :sessionId
        LIMIT 1
        """
    )
    fun getWorkoutSessionById(sessionId: Long): WorkoutSessionEntity?

    @Query(
        """
        SELECT * FROM workout_sessions
        WHERE endTimestampEpochMillis IS NOT NULL
        ORDER BY endTimestampEpochMillis DESC, id DESC
        LIMIT 1
        """
    )
    fun getLastSession(): WorkoutSessionEntity?

    @Query(
        """
        SELECT * FROM workout_sessions
        WHERE endTimestampEpochMillis IS NOT NULL
        ORDER BY endTimestampEpochMillis DESC, id DESC
        """
    )
    fun getAllSessions(): List<WorkoutSessionEntity>

    @Query(
        """
        UPDATE workout_sessions
        SET endTimestampEpochMillis = :endTimestampEpochMillis,
            durationSeconds = :durationSeconds
        WHERE id = :sessionId
        """
    )
    fun finishWorkoutSession(
        sessionId: Long,
        endTimestampEpochMillis: Long,
        durationSeconds: Long
    )

    @Query(
        """
        DELETE FROM workout_sessions
        WHERE id = :sessionId
        """
    )
    fun deleteWorkoutSession(sessionId: Long): Int

    @Query(
        """
        DELETE FROM workout_sessions
        """
    )
    fun deleteAllWorkoutSessions(): Int
}
