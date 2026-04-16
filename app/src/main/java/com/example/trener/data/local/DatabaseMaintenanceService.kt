package com.example.trener.data.local

import android.database.sqlite.SQLiteDatabase
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import com.example.trener.DatabaseRefreshCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DatabaseMaintenanceService {
    suspend fun clearWorkoutData(context: android.content.Context): ClearWorkoutDataResult {
        return withContext(Dispatchers.IO) {
            runCatching {
                val database = TrenerDatabaseProvider.getInstance(context)
                var deletedWorkoutSessionSets = 0
                var deletedWorkoutSessions = 0

                database.runInTransaction {
                    deletedWorkoutSessionSets = database.workoutSessionSetDao()
                        .deleteAllWorkoutSessionSets()
                    deletedWorkoutSessions = database.workoutSessionDao()
                        .deleteAllWorkoutSessions()
                }
                DatabaseRefreshCoordinator.requestRefresh()

                ClearWorkoutDataResult.Success(
                    deletedWorkoutSessionSets = deletedWorkoutSessionSets,
                    deletedWorkoutSessions = deletedWorkoutSessions
                )
            }.getOrElse { throwable ->
                ClearWorkoutDataResult.Failure(
                    reason = DatabaseMaintenanceFailureReason.UNKNOWN,
                    cause = throwable
                )
            }
        }
    }

    suspend fun backupDatabase(
        context: android.content.Context,
        outputStream: OutputStream
    ): BackupDatabaseResult {
        return withContext(Dispatchers.IO) {
            var databaseClosed = false
            runCatching {
                val database = TrenerDatabaseProvider.getInstance(context)
                database.openHelper.writableDatabase.query(
                    androidx.sqlite.db.SimpleSQLiteQuery("PRAGMA wal_checkpoint(TRUNCATE)")
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        // Drain the pragma result set so the checkpoint completes before close.
                    }
                }

                val databaseFiles = getBackupSourceFiles(context)
                if (databaseFiles.isEmpty()) {
                    return@runCatching BackupDatabaseResult.Failure(
                        reason = DatabaseMaintenanceFailureReason.DATABASE_NOT_FOUND
                    )
                }

                val stagingRoot = createTempRoot(context, "backup-staging")
                val stagedFiles = mutableListOf<File>()

                try {
                    databaseFiles.forEach { sourceFile ->
                        val stagedFile = File(stagingRoot, sourceFile.name)
                        copyFile(sourceFile, stagedFile)
                        stagedFiles += stagedFile
                    }

                    database.close()
                    TrenerDatabaseProvider.closeInstance()
                    databaseClosed = true

                    var bytesWritten = 0L
                    ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOutputStream ->
                        stagedFiles.forEach { stagedFile ->
                            zipOutputStream.putNextEntry(ZipEntry(stagedFile.name).apply {
                                time = stagedFile.lastModified()
                            })
                            stagedFile.inputStream().use { inputStream ->
                                bytesWritten += inputStream.copyTo(zipOutputStream)
                            }
                            zipOutputStream.closeEntry()
                        }
                    }

                    BackupDatabaseResult.Success(
                        bytesWritten = bytesWritten,
                        includedFiles = stagedFiles.map(File::getName)
                    )
                } finally {
                    stagingRoot.deleteRecursively()
                }
            }.getOrElse { throwable ->
                BackupDatabaseResult.Failure(
                    reason = DatabaseMaintenanceFailureReason.UNKNOWN,
                    cause = throwable
                )
            }.also { result ->
                if (databaseClosed) {
                    runCatching { TrenerDatabaseProvider.getInstance(context) }
                    DatabaseRefreshCoordinator.requestRefresh()
                } else if (result is BackupDatabaseResult.Success) {
                    DatabaseRefreshCoordinator.requestRefresh()
                }
            }
        }
    }

    suspend fun restoreDatabase(
        context: android.content.Context,
        inputStream: InputStream
    ): RestoreDatabaseResult {
        return withContext(Dispatchers.IO) {
            var databaseClosed = false
            runCatching {
                val stagingRoot = createTempRoot(context, "restore-input")
                val archiveFile = File(stagingRoot, "backup.zip")
                copyStreamToFile(inputStream, archiveFile)

                val extractedRoot = File(stagingRoot, "extracted").apply { mkdirs() }
                val extractedFiles = extractBackupArchive(context, archiveFile, extractedRoot)
                if (extractedFiles[TrenerDatabaseProvider.getDatabaseFile(context).name] == null) {
                    return@runCatching RestoreDatabaseResult.Failure(
                        reason = DatabaseMaintenanceFailureReason.INVALID_BACKUP
                    )
                }

                TrenerDatabaseProvider.closeInstance()
                databaseClosed = true
                val restoredFiles = replaceDatabaseFiles(context, extractedFiles) { finalMainDbFile ->
                    validateRestoredDatabase(finalMainDbFile)
                }
                TrenerDatabaseProvider.getInstance(context)

                RestoreDatabaseResult.Success(
                    restoredFiles = restoredFiles
                )
            }.getOrElse { throwable ->
                if (throwable is java.util.concurrent.CancellationException) {
                    RestoreDatabaseResult.Cancelled
                } else if (throwable.isInvalidBackupThrowable()) {
                    RestoreDatabaseResult.Failure(
                        reason = DatabaseMaintenanceFailureReason.INVALID_BACKUP,
                        cause = throwable
                    )
                } else {
                    RestoreDatabaseResult.Failure(
                        reason = DatabaseMaintenanceFailureReason.UNKNOWN,
                        cause = throwable
                    )
                }
            }.also {
                if (databaseClosed) {
                    runCatching { TrenerDatabaseProvider.getInstance(context) }
                    DatabaseRefreshCoordinator.requestRefresh()
                }
            }
        }
    }

    private fun getBackupSourceFiles(context: android.content.Context): List<File> {
        return TrenerDatabaseProvider.getCompanionDatabaseFiles(context)
            .filter { it.exists() && it.length() >= 0L }
    }

    private fun copyStreamToFile(inputStream: InputStream, outputFile: File) {
        outputFile.parentFile?.mkdirs()
        inputStream.use { input ->
            FileOutputStream(outputFile).use { output ->
                input.copyTo(output)
            }
        }
        outputFile.setReadable(true, false)
        outputFile.setWritable(true, false)
    }

    private fun copyFile(sourceFile: File, targetFile: File) {
        targetFile.parentFile?.mkdirs()
        Files.copy(
            sourceFile.toPath(),
            targetFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING
        )
        targetFile.setReadable(true, false)
        targetFile.setWritable(true, false)
    }

    private fun createTempRoot(context: android.content.Context, prefix: String): File {
        return File(context.cacheDir, "trener-$prefix-${System.currentTimeMillis()}").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    private fun extractBackupArchive(
        context: android.content.Context,
        archiveFile: File,
        destinationDir: File
    ): Map<String, File> {
        val allowedNames = TrenerDatabaseProvider.getCompanionDatabaseFiles(context)
            .map(File::getName)
            .toSet()

        val extractedFiles = mutableMapOf<String, File>()
        ZipInputStream(BufferedInputStream(archiveFile.inputStream())).use { zipInputStream ->
            while (true) {
                val entry = zipInputStream.nextEntry ?: break
                if (entry.isDirectory) {
                    zipInputStream.closeEntry()
                    continue
                }

                if (entry.name !in allowedNames) {
                    throw IllegalArgumentException("Unexpected backup entry: ${entry.name}")
                }

                val outputFile = File(destinationDir, entry.name)
                outputFile.parentFile?.mkdirs()
                FileOutputStream(outputFile).use { output ->
                    zipInputStream.copyTo(output)
                }
                extractedFiles[entry.name] = outputFile
                zipInputStream.closeEntry()
            }
        }

        return extractedFiles
    }

    private fun validateRestoredDatabase(mainDatabaseFile: File) {
        val sqliteDatabase = SQLiteDatabase.openDatabase(
            mainDatabaseFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )

        sqliteDatabase.use { database ->
            val cursor = database.rawQuery(
                """
                SELECT name
                FROM sqlite_master
                WHERE type = 'table'
                """.trimIndent(),
                null
            )

            cursor.use {
                val tables = buildSet {
                    while (it.moveToNext()) {
                        add(it.getString(0))
                    }
                }

                val requiredTables = setOf(
                    "body_weight_history",
                    "workout_sessions",
                    "workout_session_sets"
                )

                if (!tables.containsAll(requiredTables)) {
                    throw IllegalArgumentException("Backup is missing required tables.")
                }

                val foreignKeyViolations = database.rawQuery(
                    """
                    PRAGMA foreign_key_check
                    """.trimIndent(),
                    null
                )
                foreignKeyViolations.use { cursor ->
                    if (cursor.moveToFirst()) {
                        throw IllegalArgumentException("Backup contains foreign key violations.")
                    }
                }

                val integrityCheck = database.rawQuery(
                    """
                    PRAGMA integrity_check
                    """.trimIndent(),
                    null
                )
                integrityCheck.use { cursor ->
                    if (!cursor.moveToFirst() || cursor.getString(0) != "ok") {
                        throw IllegalArgumentException("Backup failed integrity check.")
                    }
                }
            }
        }
    }

    private fun replaceDatabaseFiles(
        context: android.content.Context,
        extractedFiles: Map<String, File>,
        validateFinalDatabase: (File) -> Unit
    ): List<String> {
        val databaseFiles = TrenerDatabaseProvider.getCompanionDatabaseFiles(context)
        val databaseDirectory = TrenerDatabaseProvider.getDatabaseFile(context).parentFile
            ?: throw IllegalStateException("Database directory is unavailable.")
        val rollbackRoot = createTempRoot(context, "restore-rollback")
        val restoredFileNames = mutableListOf<String>()

        try {
            databaseFiles.filter { it.exists() }.forEach { currentFile ->
                moveFile(currentFile, File(rollbackRoot, currentFile.name))
            }

            extractedFiles.forEach { (fileName, extractedFile) ->
                moveFile(extractedFile, File(databaseDirectory, fileName))
                restoredFileNames += fileName
            }

            validateFinalDatabase(File(databaseDirectory, TrenerDatabaseProvider.getDatabaseFile(context).name))

            return restoredFileNames
        } catch (throwable: Throwable) {
            val rollbackFailure = runCatching {
                rollbackDatabaseFiles(databaseDirectory, rollbackRoot)
            }.exceptionOrNull()

            if (rollbackFailure != null) {
                rollbackFailure.addSuppressed(throwable)
                throw rollbackFailure
            }

            throw throwable
        } finally {
            rollbackRoot.deleteRecursively()
        }
    }

    private fun rollbackDatabaseFiles(databaseDirectory: File, rollbackRoot: File) {
        rollbackRoot.listFiles().orEmpty().forEach { rollbackFile ->
            moveFile(rollbackFile, File(databaseDirectory, rollbackFile.name))
        }
    }

    private fun moveFile(source: File, target: File) {
        target.parentFile?.mkdirs()
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
        target.setReadable(true, false)
        target.setWritable(true, false)
    }
}

private fun Throwable.isInvalidBackupThrowable(): Boolean {
    return when (this) {
        is IllegalArgumentException,
        is ZipException,
        is android.database.SQLException -> true
        else -> cause?.isInvalidBackupThrowable() == true
    }
}

sealed interface DatabaseMaintenanceResult

data object CancelledDatabaseMaintenanceResult : DatabaseMaintenanceResult

enum class DatabaseMaintenanceFailureReason {
    DATABASE_NOT_FOUND,
    INVALID_BACKUP,
    UNKNOWN
}

sealed interface ClearWorkoutDataResult : DatabaseMaintenanceResult {
    data class Success(
        val deletedWorkoutSessionSets: Int,
        val deletedWorkoutSessions: Int
    ) : ClearWorkoutDataResult

    data class Failure(
        val reason: DatabaseMaintenanceFailureReason,
        val cause: Throwable? = null
    ) : ClearWorkoutDataResult
}

sealed interface BackupDatabaseResult : DatabaseMaintenanceResult {
    data class Success(
        val bytesWritten: Long,
        val includedFiles: List<String>
    ) : BackupDatabaseResult

    data class Failure(
        val reason: DatabaseMaintenanceFailureReason,
        val cause: Throwable? = null
    ) : BackupDatabaseResult
}

sealed interface RestoreDatabaseResult : DatabaseMaintenanceResult {
    data object Cancelled : RestoreDatabaseResult

    data class Success(
        val restoredFiles: List<String>
    ) : RestoreDatabaseResult

    data class Failure(
        val reason: DatabaseMaintenanceFailureReason,
        val cause: Throwable? = null
    ) : RestoreDatabaseResult
}
