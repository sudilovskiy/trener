package com.example.trener.data.local.dao

import androidx.room.ColumnInfo

data class ExerciseProgressSessionRow(
    @ColumnInfo(name = "sessionId")
    val sessionId: Long,
    @ColumnInfo(name = "startTimestampEpochMillis")
    val startTimestampEpochMillis: Long,
    @ColumnInfo(name = "endTimestampEpochMillis")
    val endTimestampEpochMillis: Long?,
    @ColumnInfo(name = "setReps")
    val setReps: Int?
)
