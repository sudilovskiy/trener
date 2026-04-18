package com.example.trener.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.trener.data.local.dao.ExerciseCatalogDao
import com.example.trener.data.local.dao.BodyWeightHistoryDao
import com.example.trener.data.local.dao.TrainingDayDao
import com.example.trener.data.local.dao.TrainingDayExerciseDao
import com.example.trener.data.local.dao.WorkoutHeartRateSampleDao
import com.example.trener.data.local.dao.WorkoutSessionDao
import com.example.trener.data.local.dao.WorkoutSessionSetDao
import com.example.trener.data.local.entity.ExerciseCatalogEntity
import com.example.trener.data.local.entity.BodyWeightEntryEntity
import com.example.trener.data.local.entity.TrainingDayEntity
import com.example.trener.data.local.entity.TrainingDayExerciseEntity
import com.example.trener.data.local.entity.WorkoutHeartRateSampleEntity
import com.example.trener.data.local.entity.WorkoutSessionSetEntity
import com.example.trener.data.local.entity.WorkoutSessionEntity

@Database(
    entities = [
        BodyWeightEntryEntity::class,
        WorkoutSessionEntity::class,
        WorkoutSessionSetEntity::class,
        WorkoutHeartRateSampleEntity::class,
        ExerciseCatalogEntity::class,
        TrainingDayEntity::class,
        TrainingDayExerciseEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class TrenerDatabase : RoomDatabase() {
    abstract fun bodyWeightHistoryDao(): BodyWeightHistoryDao

    abstract fun exerciseCatalogDao(): ExerciseCatalogDao

    abstract fun trainingDayDao(): TrainingDayDao

    abstract fun trainingDayExerciseDao(): TrainingDayExerciseDao

    abstract fun workoutHeartRateSampleDao(): WorkoutHeartRateSampleDao

    abstract fun workoutSessionDao(): WorkoutSessionDao

    abstract fun workoutSessionSetDao(): WorkoutSessionSetDao
}
