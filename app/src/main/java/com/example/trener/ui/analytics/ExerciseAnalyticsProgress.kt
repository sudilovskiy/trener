package com.example.trener

import com.example.trener.data.local.TrenerDatabase
import java.time.Instant
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

suspend fun loadExerciseProgressEntries(
    database: TrenerDatabase,
    exerciseId: String
): List<ExerciseProgressPoint> {
    val rows = database.workoutSessionDao().getExerciseProgressRows(exerciseId)
    if (rows.isEmpty()) {
        return emptyList()
    }

    val sessionPoints = buildList {
        var currentSessionId: Long? = null
        var currentSessionDateEpochDay = 0L
        var currentSessionTotal = 0

        fun flushCurrentSession() {
            val sessionId = currentSessionId ?: return
            add(
                ExerciseProgressSessionPoint(
                    sessionId = sessionId,
                    entryDateEpochDay = currentSessionDateEpochDay,
                    totalReps = currentSessionTotal
                )
            )
        }

        rows.forEach { row ->
            if (row.sessionId != currentSessionId) {
                flushCurrentSession()
                currentSessionId = row.sessionId
                currentSessionDateEpochDay = epochMillisToLocalDate(row.startTimestampEpochMillis).toEpochDay()
                currentSessionTotal = 0
            }
            currentSessionTotal += row.setReps ?: 0
        }
        flushCurrentSession()
    }

    val latestPointByDate = linkedMapOf<Long, ExerciseProgressPoint>()
    sessionPoints.forEach { sessionPoint ->
        if (sessionPoint.entryDateEpochDay !in latestPointByDate) {
            latestPointByDate[sessionPoint.entryDateEpochDay] = ExerciseProgressPoint(
                entryDateEpochDay = sessionPoint.entryDateEpochDay,
                totalReps = sessionPoint.totalReps
            )
        }
    }

    return latestPointByDate.values.sortedBy { it.entryDateEpochDay }
}

fun resolveExerciseProgressRange(
    records: List<ExerciseProgressPoint>,
    mode: ExerciseProgressRangeMode,
    currentRange: ExerciseProgressRange
): ExerciseProgressRange {
    if (records.isEmpty()) {
        val today = LocalDate.now().toEpochDay()
        return when (mode) {
            ExerciseProgressRangeMode.SevenDays -> ExerciseProgressRange(today - 6, today)
            ExerciseProgressRangeMode.ThirtyDays -> ExerciseProgressRange(today - 29, today)
            ExerciseProgressRangeMode.All, ExerciseProgressRangeMode.Custom -> ExerciseProgressRange(today - 6, today)
        }
    }

    val domainStart = records.first().entryDateEpochDay
    val domainEnd = records.last().entryDateEpochDay
    return when (mode) {
        ExerciseProgressRangeMode.SevenDays -> buildExerciseRangeForWindowSize(records, 7)
        ExerciseProgressRangeMode.ThirtyDays -> buildExerciseRangeForWindowSize(records, 30)
        ExerciseProgressRangeMode.All -> ExerciseProgressRange(domainStart, domainEnd)
        ExerciseProgressRangeMode.Custom -> fitExerciseProgressRangeToDomain(
            startEpochDay = currentRange.startEpochDay,
            endEpochDay = currentRange.endEpochDay,
            domainStartEpochDay = domainStart,
            domainEndEpochDay = domainEnd
        )
    }
}

fun resolveExerciseProgressGestureRange(
    currentRange: ExerciseProgressRange,
    records: List<ExerciseProgressPoint>,
    chartWidthPx: Int,
    panX: Float,
    zoom: Float
): ExerciseProgressRange? {
    if (records.isEmpty() || chartWidthPx <= 0) {
        return null
    }

    val domainStart = records.first().entryDateEpochDay
    val domainEnd = records.last().entryDateEpochDay
    val domainLength = (domainEnd - domainStart + 1).coerceAtLeast(1L)
    val currentLength = currentRange.dayCount
    val safeZoom = zoom.coerceIn(0.5f, 2.0f)
    val zoomedLength = (currentLength / safeZoom)
        .roundToLong()
        .coerceIn(1L, domainLength)
    val panDays = ((-panX / chartWidthPx.toFloat()) * currentLength).roundToLong()
    val centerEpochDay = currentRange.startEpochDay + (currentLength - 1) / 2.0
    val shiftedCenter = centerEpochDay + panDays
    val newStart = (shiftedCenter - (zoomedLength - 1) / 2.0).roundToLong()
    val newEnd = newStart + zoomedLength - 1
    return fitExerciseProgressRangeToDomain(
        startEpochDay = newStart,
        endEpochDay = newEnd,
        domainStartEpochDay = domainStart,
        domainEndEpochDay = domainEnd
    )
}

fun resolveClampedExerciseProgressRange(
    startEpochDay: Long,
    endEpochDay: Long,
    records: List<ExerciseProgressPoint>
): ExerciseProgressRange {
    val normalizedRange = normalizeExerciseProgressRange(startEpochDay, endEpochDay)
    return if (records.isEmpty()) {
        normalizedRange
    } else {
        fitExerciseProgressRangeToDomain(
            startEpochDay = normalizedRange.startEpochDay,
            endEpochDay = normalizedRange.endEpochDay,
            domainStartEpochDay = records.first().entryDateEpochDay,
            domainEndEpochDay = records.last().entryDateEpochDay
        )
    }
}

fun normalizeExerciseProgressRange(
    startEpochDay: Long,
    endEpochDay: Long
): ExerciseProgressRange {
    return ExerciseProgressRange(min(startEpochDay, endEpochDay), max(startEpochDay, endEpochDay))
}

private data class ExerciseProgressSessionPoint(
    val sessionId: Long,
    val entryDateEpochDay: Long,
    val totalReps: Int
)

private fun buildExerciseRangeForWindowSize(
    records: List<ExerciseProgressPoint>,
    windowSizeDays: Int
): ExerciseProgressRange {
    if (records.isEmpty()) {
        val today = LocalDate.now().toEpochDay()
        return ExerciseProgressRange(today - (windowSizeDays - 1), today)
    }

    val domainStart = records.first().entryDateEpochDay
    val domainEnd = records.last().entryDateEpochDay
    val desiredLength = windowSizeDays.toLong().coerceAtMost(domainEnd - domainStart + 1)
    return fitExerciseProgressRangeToDomain(
        startEpochDay = domainEnd - desiredLength + 1,
        endEpochDay = domainEnd,
        domainStartEpochDay = domainStart,
        domainEndEpochDay = domainEnd
    )
}

private fun fitExerciseProgressRangeToDomain(
    startEpochDay: Long,
    endEpochDay: Long,
    domainStartEpochDay: Long,
    domainEndEpochDay: Long
): ExerciseProgressRange {
    val normalizedStart = min(startEpochDay, endEpochDay)
    val normalizedEnd = max(startEpochDay, endEpochDay)
    val domainLength = (domainEndEpochDay - domainStartEpochDay + 1).coerceAtLeast(1L)
    val desiredLength = (normalizedEnd - normalizedStart + 1).coerceIn(1L, domainLength)

    var clampedStart = normalizedStart.coerceIn(domainStartEpochDay, domainEndEpochDay)
    var clampedEnd = (clampedStart + desiredLength - 1).coerceAtMost(domainEndEpochDay)
    clampedStart = (clampedEnd - desiredLength + 1).coerceAtLeast(domainStartEpochDay)
    clampedEnd = (clampedStart + desiredLength - 1).coerceAtMost(domainEndEpochDay)

    return ExerciseProgressRange(clampedStart, clampedEnd)
}

private fun epochMillisToLocalDate(epochMillis: Long): LocalDate {
    return Instant.ofEpochMilli(epochMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
}
