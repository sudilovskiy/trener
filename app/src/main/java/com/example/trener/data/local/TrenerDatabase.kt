package com.example.trener.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.trener.data.local.dao.BodyWeightHistoryDao
import com.example.trener.data.local.dao.WorkoutSessionDao
import com.example.trener.data.local.dao.WorkoutSessionSetDao
import com.example.trener.data.local.entity.BodyWeightEntryEntity
import com.example.trener.data.local.entity.WorkoutSessionSetEntity
import com.example.trener.data.local.entity.WorkoutSessionEntity

@Database(
    entities = [
        BodyWeightEntryEntity::class,
        WorkoutSessionEntity::class,
        WorkoutSessionSetEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class TrenerDatabase : RoomDatabase() {
    abstract fun bodyWeightHistoryDao(): BodyWeightHistoryDao

    abstract fun workoutSessionDao(): WorkoutSessionDao

    abstract fun workoutSessionSetDao(): WorkoutSessionSetDao
}
