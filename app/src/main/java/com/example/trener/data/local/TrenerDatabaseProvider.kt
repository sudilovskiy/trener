package com.example.trener.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
