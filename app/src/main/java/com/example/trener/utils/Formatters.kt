package com.example.trener

import android.content.Context
import com.example.trener.data.local.entity.WorkoutSessionEntity
import com.example.trener.data.local.entity.WorkoutSessionSetEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val workoutDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

fun WorkoutSessionEntity.toWorkoutHistoryItem(
    context: Context,
    sets: List<WorkoutSessionSetEntity>
): WorkoutHistoryItem {
    return WorkoutHistoryItem(
        id = id,
        trainingDay = trainingDay,
        dateText = toWorkoutDateText(),
        durationText = durationSeconds?.let { formatDuration(context, it) }
            ?: context.getString(R.string.not_specified),
        exerciseSummaryText = sets
            .groupWorkoutSessionEntitiesByExercise()
            .joinToString(separator = ", ") { exercise ->
                getExerciseLabel(context, exercise.exerciseId)
            }
    )
}

fun WorkoutSessionEntity.toWorkoutDateText(): String {
    return toWorkoutLocalDate().format(workoutDateFormatter)
}

fun WorkoutSessionEntity.toWorkoutLocalDate(): LocalDate {
    return Instant.ofEpochMilli(startTimestampEpochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
}

fun buildWorkoutSetValuesText(context: Context, set: WorkoutSessionSetEntity): String {
    val values = buildList {
        set.reps?.let { add(context.getString(R.string.set_value_reps, it)) }
        set.weight?.let { add(context.getString(R.string.set_value_weight, it.toDisplayString())) }
        set.additionalValue?.let {
            add(context.getString(R.string.set_value_additional, it.toDisplayString()))
        }
        set.flag?.let {
            add(
                context.getString(
                    R.string.set_value_flag,
                    context.getString(if (it) R.string.flag_yes else R.string.flag_no)
                )
            )
        }
    }

    return if (values.isEmpty()) {
        context.getString(R.string.set_values_not_specified)
    } else {
        values.joinToString(separator = " / ")
    }
}

fun formatDuration(context: Context, totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return when {
        hours > 0 -> context.getString(R.string.duration_hms, hours, minutes, seconds)
        minutes > 0 -> context.getString(R.string.duration_ms, minutes, seconds)
        else -> context.getString(R.string.duration_s, seconds)
    }
}

fun formatElapsedTimer(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

fun formatDurationAxisLabel(value: Double, step: Double): String {
    val totalSeconds = value.toLong().coerceAtLeast(0L)
    return when {
        totalSeconds >= 3600L || step >= 3600.0 -> {
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            if (minutes == 0L) {
                "${hours}h"
            } else {
                "${hours}h ${minutes}m"
            }
        }

        totalSeconds >= 60L || step >= 60.0 -> {
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            if (seconds == 0L) {
                "${minutes}m"
            } else {
                "${minutes}m ${seconds}s"
            }
        }

        else -> "${totalSeconds}s"
    }
}

private fun Double.toDisplayString(): String {
    val integerValue = toLong()
    return if (integerValue.toDouble() == this) {
        integerValue.toString()
    } else {
        toString()
    }
}
