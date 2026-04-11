package com.example.trener.domain.workout

import com.example.trener.data.local.TrenerDatabase
import com.example.trener.data.local.entity.WorkoutSessionSetEntity

class ReplaceWorkoutSessionSets(
    private val database: TrenerDatabase
) {
    suspend operator fun invoke(
        workoutSessionId: Long,
        sets: List<WorkoutSessionSetEntity>
    ) {
        database.runInTransaction<Unit> {
            database.workoutSessionSetDao()
                .deleteWorkoutSessionSetsByWorkoutSessionId(workoutSessionId)

            if (sets.isNotEmpty()) {
                database.workoutSessionSetDao().insertWorkoutSessionSets(sets)
            }
        }
    }
}
