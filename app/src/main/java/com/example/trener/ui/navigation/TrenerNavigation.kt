package com.example.trener.ui.navigation

import android.net.Uri

object AppRoute {
    const val Overview = "overview"
    const val Workout = "workout"
    const val History = "history"
    const val SessionIdArg = "sessionId"
    const val DateEpochDayArg = "dateEpochDay"
    const val ExerciseIdArg = "exerciseId"
    const val HistoryByDate = "history/date/{$DateEpochDayArg}"
    const val WorkoutDetail = "history/{$SessionIdArg}"
    const val WorkoutEdit = "history/{$SessionIdArg}/edit"
    const val Exercise = "exercise/{$ExerciseIdArg}"
    const val ExerciseComparison = "exercise/comparison"
    const val BleEntry = "weight/ble-entry"

    fun workoutDetail(sessionId: Long): String = "history/$sessionId"
    fun historyForDate(epochDay: Long): String = "history/date/$epochDay"
    fun workoutEdit(sessionId: Long): String = "history/$sessionId/edit"
    fun exercise(exerciseId: String): String = "exercise/${Uri.encode(exerciseId)}"
}

fun getNextTrainingDay(trainingDay: Int): Int {
    return when (trainingDay) {
        1 -> 2
        2 -> 3
        else -> 1
    }
}
