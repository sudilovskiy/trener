package com.example.trener.data.local

import android.content.Context
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import com.example.trener.normalizeBodyWeightKg
import com.example.trener.data.local.entity.BodyWeightEntryEntity
import com.example.trener.data.local.entity.WorkoutSessionEntity
import com.example.trener.data.local.entity.WorkoutSessionSetEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class DatabaseMaintenanceServiceRoundTripTest {
    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        resetDatabaseFiles()
    }

    @After
    fun tearDown() {
        resetDatabaseFiles()
    }

    @Test
    fun backupAndRestoreRoundTripPreservesCriticalPersistedState() {
        runBlocking(Dispatchers.IO) {
            seedRepresentativeState()
        }
        val before = runBlocking(Dispatchers.IO) {
            captureCriticalStateSnapshot()
        }

        val backupBytes = ByteArrayOutputStream().use { output ->
            val result = kotlinx.coroutines.runBlocking {
                DatabaseMaintenanceService.backupDatabase(context, output)
            }

            assertTrue("Backup result was $result", result is BackupDatabaseResult.Success)
            val success = result as BackupDatabaseResult.Success
            assertTrue(success.bytesWritten > 0L)
            assertTrue(
                success.includedFiles.contains(
                    TrenerDatabaseProvider.getDatabaseFile(context).name
                )
            )
            output.toByteArray()
        }

        TrenerDatabaseProvider.closeInstance()
        resetDatabaseFiles()

        val restoreResult = kotlinx.coroutines.runBlocking {
            DatabaseMaintenanceService.restoreDatabase(
                context = context,
                inputStream = ByteArrayInputStream(backupBytes)
            )
        }

        assertTrue("Restore result was $restoreResult", restoreResult is RestoreDatabaseResult.Success)

        val after = runBlocking(Dispatchers.IO) {
            captureCriticalStateSnapshot()
        }
        assertEquals(before, after)
        assertEquals(0, after.orphanWorkoutSessionSetCount)
        assertEquals(0, after.foreignKeyViolationCount)
        runBlocking(Dispatchers.IO) {
            assertDatabaseIntegrityChecksPass()
        }
    }

    @Test
    fun restoreRejectsCorruptBackupAndLeavesCurrentStateUntouched() {
        runBlocking(Dispatchers.IO) {
            seedRepresentativeState()
        }
        val before = runBlocking(Dispatchers.IO) {
            captureCriticalStateSnapshot()
        }

        val corruptBackup = createCorruptBackupArchive()
        val restoreResult = kotlinx.coroutines.runBlocking {
            DatabaseMaintenanceService.restoreDatabase(
                context = context,
                inputStream = ByteArrayInputStream(corruptBackup)
            )
        }

        assertTrue(restoreResult is RestoreDatabaseResult.Failure)
        val failure = restoreResult as RestoreDatabaseResult.Failure
        assertEquals(DatabaseMaintenanceFailureReason.INVALID_BACKUP, failure.reason)

        val after = runBlocking(Dispatchers.IO) {
            captureCriticalStateSnapshot()
        }
        assertEquals(before, after)
        assertEquals(0, after.orphanWorkoutSessionSetCount)
        assertEquals(0, after.foreignKeyViolationCount)
        runBlocking(Dispatchers.IO) {
            assertDatabaseIntegrityChecksPass()
        }
    }

    private fun seedRepresentativeState() {
        val database = TrenerDatabaseProvider.getInstance(context)

        database.runInTransaction {
            val firstSessionId = database.workoutSessionDao().insertWorkoutSession(
                WorkoutSessionEntity(
                    trainingDay = 1,
                    startTimestampEpochMillis = 1_700_000_000_000L
                )
            )
            val firstSetId = database.workoutSessionSetDao().insertWorkoutSessionSet(
                WorkoutSessionSetEntity(
                    workoutSessionId = firstSessionId,
                    exerciseId = "bench_press",
                    setNumber = 1,
                    reps = 8,
                    weight = 80.0,
                    additionalValue = null,
                    flag = false,
                    note = ""
                )
            )
            database.workoutSessionSetDao().insertWorkoutSessionSet(
                WorkoutSessionSetEntity(
                    workoutSessionId = firstSessionId,
                    exerciseId = "bench_press",
                    setNumber = 2,
                    reps = 6,
                    weight = 82.5,
                    additionalValue = 1.0,
                    flag = true,
                    note = "top set"
                )
            )
            database.workoutSessionSetDao().insertWorkoutSessionSet(
                WorkoutSessionSetEntity(
                    workoutSessionId = firstSessionId,
                    exerciseId = "row",
                    setNumber = 1,
                    reps = 10,
                    weight = 60.0,
                    additionalValue = 2.0,
                    flag = null,
                    note = "tempo"
                )
            )
            database.workoutSessionDao().finishWorkoutSession(
                sessionId = firstSessionId,
                endTimestampEpochMillis = 1_700_000_450_000L,
                durationSeconds = 450L
            )

            val secondSessionId = database.workoutSessionDao().insertWorkoutSession(
                WorkoutSessionEntity(
                    trainingDay = 2,
                    startTimestampEpochMillis = 1_700_086_400_000L
                )
            )
            database.workoutSessionSetDao().insertWorkoutSessionSet(
                WorkoutSessionSetEntity(
                    workoutSessionId = secondSessionId,
                    exerciseId = "squat",
                    setNumber = 1,
                    reps = 5,
                    weight = 100.0,
                    additionalValue = null,
                    flag = false,
                    note = "belted"
                )
            )
            database.workoutSessionSetDao().insertWorkoutSessionSet(
                WorkoutSessionSetEntity(
                    workoutSessionId = secondSessionId,
                    exerciseId = "deadlift",
                    setNumber = 1,
                    reps = null,
                    weight = 120.0,
                    additionalValue = null,
                    flag = null,
                    note = "warmup"
                )
            )
            database.workoutSessionSetDao().insertWorkoutSessionSet(
                WorkoutSessionSetEntity(
                    workoutSessionId = secondSessionId,
                    exerciseId = "bench_press",
                    setNumber = 1,
                    reps = 12,
                    weight = 75.0,
                    additionalValue = 0.5,
                    flag = true,
                    note = "volume"
                )
            )
            database.workoutSessionDao().finishWorkoutSession(
                sessionId = secondSessionId,
                endTimestampEpochMillis = 1_700_086_860_000L,
                durationSeconds = 460L
            )

            database.bodyWeightHistoryDao().upsertBodyWeightEntry(
                BodyWeightEntryEntity(
                    entryDateEpochDay = 19_000L,
                    weightKg = normalizeBodyWeightKg(82.15)
                )
            )
            database.bodyWeightHistoryDao().upsertBodyWeightEntry(
                BodyWeightEntryEntity(
                    entryDateEpochDay = 19_001L,
                    weightKg = normalizeBodyWeightKg(81.94)
                )
            )
            database.bodyWeightHistoryDao().upsertBodyWeightEntry(
                BodyWeightEntryEntity(
                    entryDateEpochDay = 19_001L,
                    weightKg = normalizeBodyWeightKg(82.04)
                )
            )
            database.bodyWeightHistoryDao().upsertBodyWeightEntry(
                BodyWeightEntryEntity(
                    entryDateEpochDay = 19_002L,
                    weightKg = normalizeBodyWeightKg(79.53)
                )
            )

            editWorkoutSessionSetNote(
                database = database,
                setId = firstSetId,
                note = "edited after logging"
            )
        }

        database.openHelper.writableDatabase.query(
            SimpleSQLiteQuery("PRAGMA journal_mode=DELETE")
        ).use { cursor ->
            while (cursor.moveToNext()) {
                // Drain the pragma result so the journal mode change is applied.
            }
        }
    }

    private fun editWorkoutSessionSetNote(
        database: TrenerDatabase,
        setId: Long,
        note: String
    ) {
        database.openHelper.writableDatabase.compileStatement(
            """
            UPDATE workout_session_sets
            SET note = ?
            WHERE id = ?
            """.trimIndent()
        ).use { statement ->
            statement.bindString(1, note)
            statement.bindLong(2, setId)
            statement.executeUpdateDelete()
        }
    }

    private fun captureCriticalStateSnapshot(): CriticalStateSnapshot {
        val database = TrenerDatabaseProvider.getInstance(context)
        val sessions = database.workoutSessionDao().getAllSessions()
        val sessionGraphs = sessions.map { session ->
            SessionGraphSnapshot(
                session = session,
                sets = database.workoutSessionSetDao().getSetsByWorkoutSessionId(session.id)
            )
        }

        return CriticalStateSnapshot(
            sessionGraphs = sessionGraphs,
            weightHistory = database.bodyWeightHistoryDao().getWeightHistory(),
            orphanWorkoutSessionSetCount = countOrphanWorkoutSessionSets(database),
            foreignKeyViolationCount = countForeignKeyViolations(database)
        )
    }

    private fun countOrphanWorkoutSessionSets(database: TrenerDatabase): Int {
        database.openHelper.readableDatabase.query(
            SimpleSQLiteQuery(
                """
                SELECT COUNT(*)
                FROM workout_session_sets AS wss
                LEFT JOIN workout_sessions AS ws
                    ON ws.id = wss.workoutSessionId
                WHERE ws.id IS NULL
                """.trimIndent()
            )
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            return cursor.getInt(0)
        }
    }

    private fun countForeignKeyViolations(database: TrenerDatabase): Int {
        database.openHelper.readableDatabase.query(
            SimpleSQLiteQuery("PRAGMA foreign_key_check")
        ).use { cursor ->
            var count = 0
            while (cursor.moveToNext()) {
                count += 1
            }
            return count
        }
    }

    private fun assertDatabaseIntegrityChecksPass() {
        val database = TrenerDatabaseProvider.getInstance(context)

        database.openHelper.readableDatabase.query(
            SimpleSQLiteQuery("PRAGMA integrity_check")
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("ok", cursor.getString(0))
        }

        assertEquals(0, countForeignKeyViolations(database))
    }

    private fun createCorruptBackupArchive(): ByteArray {
        val databaseFileName = TrenerDatabaseProvider.getDatabaseFile(context).name
        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry(databaseFileName))
                zip.write("not-a-sqlite-database".toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            output.toByteArray()
        }
    }

    private fun resetDatabaseFiles() {
        TrenerDatabaseProvider.closeInstance()
        TrenerDatabaseProvider.getCompanionDatabaseFiles(context).forEach { file ->
            if (file.exists()) {
                assertTrue("Failed to delete ${file.absolutePath}", file.delete())
            }
        }
    }

    private data class CriticalStateSnapshot(
        val sessionGraphs: List<SessionGraphSnapshot>,
        val weightHistory: List<BodyWeightEntryEntity>,
        val orphanWorkoutSessionSetCount: Int,
        val foreignKeyViolationCount: Int
    )

    private data class SessionGraphSnapshot(
        val session: WorkoutSessionEntity,
        val sets: List<WorkoutSessionSetEntity>
    )
}
