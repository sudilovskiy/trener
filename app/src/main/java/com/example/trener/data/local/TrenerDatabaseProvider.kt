package com.example.trener.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.trener.exerciseDefinitionsByDay
import java.io.File

object TrenerDatabaseProvider {
    private const val DATABASE_NAME = "trener_database"

    @Volatile
    private var instance: TrenerDatabase? = null

    fun getInstance(context: Context): TrenerDatabase {
        instance?.takeIf { it.isOpen }?.let { return it }
        return instance ?: synchronized(this) {
            instance?.takeIf { it.isOpen } ?: buildDatabase(context.applicationContext).also { database ->
                instance = database
            }
        }
    }

    fun closeInstance() {
        synchronized(this) {
            instance?.close()
            instance = null
        }
    }

    fun getDatabaseFile(context: Context): File {
        return context.applicationContext.getDatabasePath(DATABASE_NAME)
    }

    fun getCompanionDatabaseFiles(context: Context): List<File> {
        val databaseFile = getDatabaseFile(context)
        return listOf(
            databaseFile,
            File(databaseFile.path + "-wal"),
            File(databaseFile.path + "-shm"),
            File(databaseFile.path + "-journal")
        )
    }

    private fun buildDatabase(context: Context): TrenerDatabase {
        return Room.databaseBuilder(
            context,
            TrenerDatabase::class.java,
            DATABASE_NAME
        )
            .addMigrations(MIGRATION_4_5)
            .addMigrations(MIGRATION_5_6)
            .addMigrations(MIGRATION_6_7)
            .addMigrations(MIGRATION_7_8)
            .addMigrations(MIGRATION_8_9)
            .addMigrations(MIGRATION_9_10)
            .addMigrations(MIGRATION_10_11)
            .fallbackToDestructiveMigration()
            .build()
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE workout_session_sets
            ADD COLUMN note TEXT NOT NULL DEFAULT ''
            """.trimIndent()
        )
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `body_weight_history_new` (
                `entryDateEpochDay` INTEGER NOT NULL,
                `weightKg` REAL NOT NULL,
                PRIMARY KEY(`entryDateEpochDay`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `body_weight_history_new` (`entryDateEpochDay`, `weightKg`)
            SELECT `entryDateEpochDay`, `weightKg`
            FROM `body_weight_history`
            ORDER BY `entryDateEpochDay` ASC
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `body_weight_history`")
        db.execSQL("ALTER TABLE `body_weight_history_new` RENAME TO `body_weight_history`")
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            UPDATE `body_weight_history`
            SET `weightKg` = ROUND(`weightKg`, 1)
            WHERE `weightKg` != ROUND(`weightKg`, 1)
            """.trimIndent()
        )
    }
}

private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `exercise_catalog` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `parameterType` TEXT NOT NULL,
                `parameterLabel` TEXT NOT NULL,
                `sortOrder` INTEGER NOT NULL,
                `isActive` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_exercise_catalog_sortOrder`
            ON `exercise_catalog` (`sortOrder`)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_exercise_catalog_isActive_sortOrder`
            ON `exercise_catalog` (`isActive`, `sortOrder`)
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `training_days` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `sortOrder` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_training_days_sortOrder`
            ON `training_days` (`sortOrder`)
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `training_day_exercises` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `trainingDayId` INTEGER NOT NULL,
                `catalogExerciseId` TEXT NOT NULL,
                `sortOrder` INTEGER NOT NULL,
                FOREIGN KEY(`trainingDayId`) REFERENCES `training_days`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`catalogExerciseId`) REFERENCES `exercise_catalog`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_training_day_exercises_trainingDayId`
            ON `training_day_exercises` (`trainingDayId`)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_training_day_exercises_catalogExerciseId`
            ON `training_day_exercises` (`catalogExerciseId`)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS `index_training_day_exercises_trainingDayId_catalogExerciseId`
            ON `training_day_exercises` (`trainingDayId`, `catalogExerciseId`)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_training_day_exercises_trainingDayId_sortOrder`
            ON `training_day_exercises` (`trainingDayId`, `sortOrder`)
            """.trimIndent()
        )
    }
}

private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE `exercise_catalog`
            ADD COLUMN `parameterUnit` TEXT NOT NULL DEFAULT ''
            """.trimIndent()
        )

        val builtInDefinitionsById = exerciseDefinitionsByDay
            .values
            .flatten()
            .associateBy { it.exerciseId }

        db.query(
            SimpleSQLiteQuery(
                """
                SELECT `id`, `parameterType`, `parameterLabel`, `parameterUnit`
                FROM `exercise_catalog`
                """.trimIndent()
            )
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val exerciseId = cursor.getString(0)
                val builtInDefinition = builtInDefinitionsById[exerciseId]
                val normalizedMetadata = if (builtInDefinition != null) {
                    catalogParameterDefaultsForInputType(builtInDefinition.inputType)
                } else {
                    val normalizedType = normalizeCatalogParameterType(cursor.getString(1).orEmpty())
                    CatalogParameterMetadata(
                        parameterType = normalizedType,
                        parameterLabel = cursor.getString(2)
                            .orEmpty()
                            .trim()
                            .ifBlank { catalogParameterLabelForType(normalizedType) },
                        parameterUnit = cursor.getString(3)
                            .orEmpty()
                            .trim()
                            .ifBlank { catalogParameterUnitForType(normalizedType) }
                    )
                }

                db.compileStatement(
                    """
                    UPDATE `exercise_catalog`
                    SET `parameterType` = ?, `parameterLabel` = ?, `parameterUnit` = ?
                    WHERE `id` = ?
                    """.trimIndent()
                ).use { statement ->
                    statement.bindString(1, normalizedMetadata.parameterType)
                    statement.bindString(2, normalizedMetadata.parameterLabel)
                    statement.bindString(3, normalizedMetadata.parameterUnit)
                    statement.bindString(4, exerciseId)
                    statement.executeUpdateDelete()
                }
            }
        }
    }
}

private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE `workout_session_sets`
            ADD COLUMN `parameterValue` REAL
            """.trimIndent()
        )
        db.execSQL(
            """
            ALTER TABLE `workout_session_sets`
            ADD COLUMN `parameterOverrideValue` REAL
            """.trimIndent()
        )
    }
}

private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE `workout_sessions`
            ADD COLUMN `averageHeartRate` INTEGER
            """.trimIndent()
        )
        db.execSQL(
            """
            ALTER TABLE `workout_sessions`
            ADD COLUMN `maxHeartRate` INTEGER
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `workout_heart_rate_samples` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `workoutSessionId` INTEGER NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `heartRate` INTEGER NOT NULL,
                FOREIGN KEY(`workoutSessionId`) REFERENCES `workout_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_workout_heart_rate_samples_workoutSessionId`
            ON `workout_heart_rate_samples` (`workoutSessionId`)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_workout_heart_rate_samples_workoutSessionId_timestamp`
            ON `workout_heart_rate_samples` (`workoutSessionId`, `timestamp`)
            """.trimIndent()
        )
    }
}
