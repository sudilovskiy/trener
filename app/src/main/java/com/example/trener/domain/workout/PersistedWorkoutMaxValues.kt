package com.example.trener.domain.workout

import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.trener.ExerciseInputType
import com.example.trener.data.local.TrenerDatabase

fun loadPersistedMaxValuesBySetNumber(
    database: TrenerDatabase,
    exerciseId: String,
    exerciseInputType: ExerciseInputType
): Map<Int, Int> {
    val valueColumn = when (exerciseInputType) {
        ExerciseInputType.REPS -> "reps"
        ExerciseInputType.TIME_SECONDS -> "additionalValue"
    }
    val cursor = database.query(
        SimpleSQLiteQuery(
            """
            SELECT setNumber, MAX($valueColumn) AS maxValue
            FROM workout_session_sets
            WHERE exerciseId = ? AND $valueColumn IS NOT NULL
            GROUP BY setNumber
            ORDER BY setNumber ASC
            """.trimIndent(),
            arrayOf(exerciseId)
        )
    )

    return cursor.use {
        buildMap {
            while (it.moveToNext()) {
                val maxValue = when (exerciseInputType) {
                    ExerciseInputType.REPS -> it.getInt(1)
                    ExerciseInputType.TIME_SECONDS -> it.getDouble(1).toInt()
                }
                put(it.getInt(0), maxValue)
            }
        }
    }
}
