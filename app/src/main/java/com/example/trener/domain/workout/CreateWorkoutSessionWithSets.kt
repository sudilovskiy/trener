package com.example.trener.domain.workout

import com.example.trener.data.local.TrenerDatabase
import com.example.trener.data.local.entity.WorkoutSessionEntity
import com.example.trener.data.local.entity.WorkoutSessionSetEntity

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
                WorkoutSessionSetEntity(
                    workoutSessionId = sessionId,
                    exerciseId = set.exerciseId,
                    setNumber = set.setNumber,
                    reps = set.reps,
                    weight = set.weight,
                    additionalValue = set.additionalValue,
                    flag = set.flag,
                    note = set.note
                )
            }

            database.workoutSessionSetDao().insertWorkoutSessionSets(sessionSets)
            sessionId
        }
    }
}
