package com.example.trener.ui.navigation

import android.net.Uri

object AppRoute {
    const val Overview = "overview"
    const val Workout = "workout"
    const val TrainingDaysBuilder = "training-days-builder"
    const val ExerciseCatalog = "training-days-builder/catalog"
    const val History = "history"
    const val SessionIdArg = "sessionId"
    const val DateEpochDayArg = "dateEpochDay"
    const val TrainingDayIdArg = "trainingDayId"
    const val TrainingDayExercises = "training-days-builder/day/{$TrainingDayIdArg}"
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
    fun trainingDayExercises(trainingDayId: Long): String = "training-days-builder/day/$trainingDayId"
    fun exerciseCatalog(): String = ExerciseCatalog
}

fun getNextTrainingDay(trainingDay: Int?, availableTrainingDays: List<Int>): Int? {
    if (availableTrainingDays.isEmpty()) {
        return trainingDay
    }

    val currentIndex = trainingDay?.let(availableTrainingDays::indexOf)
        ?.takeIf { it >= 0 }

    return when (currentIndex) {
        null -> availableTrainingDays.first()
        availableTrainingDays.lastIndex -> availableTrainingDays.first()
        else -> availableTrainingDays[currentIndex + 1]
    }
}
