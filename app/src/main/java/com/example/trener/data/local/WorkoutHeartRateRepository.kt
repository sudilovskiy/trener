package com.example.trener.data.local

import com.example.trener.data.local.entity.WorkoutHeartRateSampleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class WorkoutHeartRateAggregates(
    val averageHeartRate: Int?,
    val maxHeartRate: Int?
)

class WorkoutHeartRateRepository(
    private val database: TrenerDatabase
) {
    private val workoutHeartRateSampleDao = database.workoutHeartRateSampleDao()
    private val workoutSessionDao = database.workoutSessionDao()

    suspend fun appendHeartRateSample(sample: WorkoutHeartRateSampleEntity): Long {
        return withContext(Dispatchers.IO) {
            workoutHeartRateSampleDao.insertWorkoutHeartRateSample(sample)
        }
    }

    suspend fun appendHeartRateSamples(samples: List<WorkoutHeartRateSampleEntity>): List<Long> {
        return withContext(Dispatchers.IO) {
            if (samples.isEmpty()) {
                emptyList()
            } else {
                database.runInTransaction<List<Long>> {
                    workoutHeartRateSampleDao.insertWorkoutHeartRateSamples(samples)
                }
            }
        }
    }

    suspend fun getHeartRateSamplesForWorkout(
        workoutSessionId: Long
    ): List<WorkoutHeartRateSampleEntity> {
        return withContext(Dispatchers.IO) {
            workoutHeartRateSampleDao.getWorkoutHeartRateSamplesByWorkoutSessionId(workoutSessionId)
        }
    }

    suspend fun recalculateWorkoutSessionHeartRateAggregates(
        workoutSessionId: Long
    ): WorkoutHeartRateAggregates? {
        return withContext(Dispatchers.IO) {
            database.runInTransaction<WorkoutHeartRateAggregates?> {
                val session = workoutSessionDao.getWorkoutSessionById(workoutSessionId)
                    ?: return@runInTransaction null
                val samples = workoutHeartRateSampleDao
                    .getWorkoutHeartRateSamplesByWorkoutSessionId(workoutSessionId)
                val aggregates = samples.toWorkoutHeartRateAggregates()

                workoutSessionDao.updateWorkoutSessionHeartRateAggregates(
                    sessionId = session.id,
                    averageHeartRate = aggregates.averageHeartRate,
                    maxHeartRate = aggregates.maxHeartRate
                )
                aggregates
            }
        }
    }
}

private fun List<WorkoutHeartRateSampleEntity>.toWorkoutHeartRateAggregates(): WorkoutHeartRateAggregates {
    if (isEmpty()) {
        return WorkoutHeartRateAggregates(
            averageHeartRate = null,
            maxHeartRate = null
        )
    }

    val totalHeartRate = sumOf { it.heartRate.toLong() }
    val averageHeartRate = (totalHeartRate / size).toInt()
    val maxHeartRate = maxOf { it.heartRate }

    return WorkoutHeartRateAggregates(
        averageHeartRate = averageHeartRate,
        maxHeartRate = maxHeartRate
    )
}
