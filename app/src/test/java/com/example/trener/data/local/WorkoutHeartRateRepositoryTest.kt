package com.example.trener.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.trener.data.local.entity.WorkoutHeartRateSampleEntity
import com.example.trener.data.local.entity.WorkoutSessionEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class WorkoutHeartRateRepositoryTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private lateinit var database: TrenerDatabase
    private lateinit var repository: WorkoutHeartRateRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, TrenerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = WorkoutHeartRateRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun loadsHeartRateSamplesOrderedByTimestamp() {
        val sessionId = insertWorkoutSession()

        runBlockingIO {
            repository.appendHeartRateSamples(
                listOf(
                    sample(sessionId, timestamp = 3_000L, heartRate = 132),
                    sample(sessionId, timestamp = 1_000L, heartRate = 118),
                    sample(sessionId, timestamp = 2_000L, heartRate = 125)
                )
            )
        }

        val samples = runBlockingIO {
            repository.getHeartRateSamplesForWorkout(sessionId)
        }

        assertEquals(listOf(1_000L, 2_000L, 3_000L), samples.map { it.timestamp })
        assertEquals(listOf(118, 125, 132), samples.map { it.heartRate })
    }

    @Test
    fun recalculatesAggregatesAsNullsWhenNoSamplesExist() {
        val sessionId = insertWorkoutSession()

        val aggregates = runBlockingIO {
            repository.recalculateWorkoutSessionHeartRateAggregates(sessionId)
        }

        assertEquals(WorkoutHeartRateAggregates(null, null), aggregates)

        val session = database.workoutSessionDao().getWorkoutSessionById(sessionId)
        assertNull(session?.averageHeartRate)
        assertNull(session?.maxHeartRate)
    }

    @Test
    fun recalculatesAggregatesForSingleAndMultipleSamples() {
        val singleSessionId = insertWorkoutSession()
        runBlockingIO {
            repository.appendHeartRateSample(
                sample(singleSessionId, timestamp = 10_000L, heartRate = 147)
            )
        }

        val singleAggregates = runBlockingIO {
            repository.recalculateWorkoutSessionHeartRateAggregates(singleSessionId)
        }

        assertEquals(WorkoutHeartRateAggregates(147, 147), singleAggregates)

        val multiSessionId = insertWorkoutSession()
        runBlockingIO {
            repository.appendHeartRateSamples(
                listOf(
                    sample(multiSessionId, timestamp = 1_000L, heartRate = 100),
                    sample(multiSessionId, timestamp = 2_000L, heartRate = 120),
                    sample(multiSessionId, timestamp = 3_000L, heartRate = 130)
                )
            )
        }

        val multiAggregates = runBlockingIO {
            repository.recalculateWorkoutSessionHeartRateAggregates(multiSessionId)
        }

        assertEquals(WorkoutHeartRateAggregates(116, 130), multiAggregates)

        val session = database.workoutSessionDao().getWorkoutSessionById(multiSessionId)
        assertEquals(116, session?.averageHeartRate)
        assertEquals(130, session?.maxHeartRate)
    }

    @Test
    fun deletingWorkoutSessionCascadesToHeartRateSamples() {
        val sessionId = insertWorkoutSession()
        runBlockingIO {
            repository.appendHeartRateSamples(
                listOf(
                    sample(sessionId, timestamp = 1_000L, heartRate = 111),
                    sample(sessionId, timestamp = 2_000L, heartRate = 122)
                )
            )
        }

        assertTrue(database.workoutSessionDao().deleteWorkoutSession(sessionId) > 0)

        assertEquals(0, database.workoutHeartRateSampleDao()
            .countWorkoutHeartRateSamplesByWorkoutSessionId(sessionId))
        assertTrue(
            runBlockingIO {
                repository.getHeartRateSamplesForWorkout(sessionId)
            }.isEmpty()
        )
    }

    private fun insertWorkoutSession(): Long {
        return database.workoutSessionDao().insertWorkoutSession(
            WorkoutSessionEntity(
                trainingDay = 1,
                startTimestampEpochMillis = 1_700_000_000_000L
            )
        )
    }

    private fun sample(
        workoutSessionId: Long,
        timestamp: Long,
        heartRate: Int
    ): WorkoutHeartRateSampleEntity {
        return WorkoutHeartRateSampleEntity(
            workoutSessionId = workoutSessionId,
            timestamp = timestamp,
            heartRate = heartRate
        )
    }

    private fun <T> runBlockingIO(block: suspend () -> T): T {
        return kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            block()
        }
    }
}
