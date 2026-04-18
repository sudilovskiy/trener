package com.example.trener.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.trener.data.local.entity.WorkoutSessionEntity
import com.example.trener.data.local.dao.ExerciseProgressSessionRow
import com.example.trener.data.local.dao.ExerciseParameterProgressSessionRow

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
        SET averageHeartRate = :averageHeartRate,
            maxHeartRate = :maxHeartRate
        WHERE id = :sessionId
        """
    )
    fun updateWorkoutSessionHeartRateAggregates(
        sessionId: Long,
        averageHeartRate: Int?,
        maxHeartRate: Int?
    ): Int

    @Query(
        """
        SELECT
            ws.id AS sessionId,
            ws.startTimestampEpochMillis AS startTimestampEpochMillis,
            ws.endTimestampEpochMillis AS endTimestampEpochMillis,
            wss.reps AS setReps
        FROM workout_sessions AS ws
        INNER JOIN workout_session_sets AS wss
            ON wss.workoutSessionId = ws.id
        WHERE ws.endTimestampEpochMillis IS NOT NULL
          AND wss.exerciseId = :exerciseId
        ORDER BY ws.endTimestampEpochMillis DESC, ws.id DESC, wss.setNumber ASC, wss.id ASC
        """
    )
    fun getExerciseProgressRows(exerciseId: String): List<ExerciseProgressSessionRow>

    @Query(
        """
        SELECT
            ws.id AS sessionId,
            ws.startTimestampEpochMillis AS startTimestampEpochMillis,
            ws.endTimestampEpochMillis AS endTimestampEpochMillis,
            wss.reps AS setReps,
            wss.parameterValue AS parameterValue,
            wss.parameterOverrideValue AS parameterOverrideValue
        FROM workout_sessions AS ws
        INNER JOIN workout_session_sets AS wss
            ON wss.workoutSessionId = ws.id
        WHERE ws.endTimestampEpochMillis IS NOT NULL
          AND wss.exerciseId = :exerciseId
        ORDER BY ws.endTimestampEpochMillis DESC, ws.id DESC, wss.setNumber ASC, wss.id ASC
        """
    )
    fun getExerciseParameterProgressRows(
        exerciseId: String
    ): List<ExerciseParameterProgressSessionRow>

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
