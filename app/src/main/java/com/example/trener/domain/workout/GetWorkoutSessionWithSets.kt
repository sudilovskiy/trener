package com.example.trener.domain.workout

import com.example.trener.data.local.TrenerDatabase

class GetWorkoutSessionWithSets(
    private val database: TrenerDatabase
) {
    suspend operator fun invoke(sessionId: Long): WorkoutSessionWithSets? {
        val session = database.workoutSessionDao().getWorkoutSessionById(sessionId)
            ?: return null

        val sets = database.workoutSessionSetDao().getSetsByWorkoutSessionId(sessionId)

        return WorkoutSessionWithSets(
            session = session,
            sets = sets
        )
    }
}
