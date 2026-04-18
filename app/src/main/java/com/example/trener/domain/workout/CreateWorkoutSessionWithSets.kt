package com.example.trener.domain.workout

import com.example.trener.data.local.TrenerDatabase
import com.example.trener.data.local.entity.WorkoutSessionEntity

class CreateWorkoutSessionWithSets(
    private val database: TrenerDatabase
) {
    suspend operator fun invoke(
        trainingDay: Int,
        sets: List<PreparedWorkoutSessionSet>,
        startTimestampEpochMillis: Long,
        endTimestampEpochMillis: Long,
        durationSeconds: Long
    ): Long {
        return database.runInTransaction<Long> {
            val sessionId = database.workoutSessionDao().insertWorkoutSession(
                WorkoutSessionEntity(
                    trainingDay = trainingDay,
                    startTimestampEpochMillis = startTimestampEpochMillis,
                    endTimestampEpochMillis = endTimestampEpochMillis,
                    durationSeconds = durationSeconds
                )
            )

            val sessionSets = sets.map { set ->
                set.toWorkoutSessionSetEntity(sessionId)
            }

            database.workoutSessionSetDao().insertWorkoutSessionSets(sessionSets)
            sessionId
        }
    }
}
