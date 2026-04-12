package com.example.trener

import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trener.data.local.TrenerDatabase
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

enum class ExerciseProgressRangeMode {
    SevenDays,
    ThirtyDays,
    All,
    Custom
}

data class ExerciseProgressRange(
    val startEpochDay: Long,
    val endEpochDay: Long
) {
    val dayCount: Long
        get() = (endEpochDay - startEpochDay + 1).coerceAtLeast(1L)
}

data class ExerciseProgressPoint(
    val entryDateEpochDay: Long,
    val totalReps: Int
)

data class ExerciseProgressAxisTick(
    val value: Double,
    val step: Double
)

fun getAllExercises(): List<ExerciseDefinition> {
    return exerciseDefinitionsByDay.values.flatten()
}

fun ExerciseProgressRangeMode.displayLabel(context: android.content.Context): String {
    return when (this) {
        ExerciseProgressRangeMode.SevenDays -> context.getString(R.string.exercise_progress_range_7_days)
        ExerciseProgressRangeMode.ThirtyDays -> context.getString(R.string.exercise_progress_range_30_days)
        ExerciseProgressRangeMode.All -> context.getString(R.string.exercise_progress_range_all)
        ExerciseProgressRangeMode.Custom -> context.getString(R.string.exercise_progress_range_custom)
    }
}

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

@Composable
fun ExerciseProgressRangeSheet(
    context: android.content.Context,
    selectedMode: ExerciseProgressRangeMode,
    startEpochDay: Long,
    endEpochDay: Long,
    onPresetSelected: (ExerciseProgressRangeMode) -> Unit,
    onStartDatePicked: (Long) -> Unit,
    onEndDatePicked: (Long) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.exercise_progress_range_title),
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExerciseRangePresetButton(
                label = stringResource(R.string.exercise_progress_range_7_days),
                selected = selectedMode == ExerciseProgressRangeMode.SevenDays,
                modifier = Modifier.weight(1f),
                onClick = { onPresetSelected(ExerciseProgressRangeMode.SevenDays) }
            )
            ExerciseRangePresetButton(
                label = stringResource(R.string.exercise_progress_range_30_days),
                selected = selectedMode == ExerciseProgressRangeMode.ThirtyDays,
                modifier = Modifier.weight(1f),
                onClick = { onPresetSelected(ExerciseProgressRangeMode.ThirtyDays) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExerciseRangePresetButton(
                label = stringResource(R.string.exercise_progress_range_all),
                selected = selectedMode == ExerciseProgressRangeMode.All,
                modifier = Modifier.weight(1f),
                onClick = { onPresetSelected(ExerciseProgressRangeMode.All) }
            )
            ExerciseRangePresetButton(
                label = stringResource(R.string.exercise_progress_range_custom),
                selected = selectedMode == ExerciseProgressRangeMode.Custom,
                modifier = Modifier.weight(1f),
                onClick = { onPresetSelected(ExerciseProgressRangeMode.Custom) }
            )
        }

        if (selectedMode == ExerciseProgressRangeMode.Custom) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        openDatePicker(
                            context = context,
                            initialEpochDay = startEpochDay,
                            onDatePicked = onStartDatePicked
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            R.string.exercise_progress_range_from,
                            formatExerciseProgressDate(startEpochDay)
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                TextButton(
                    onClick = {
                        openDatePicker(
                            context = context,
                            initialEpochDay = endEpochDay,
                            onDatePicked = onEndDatePicked
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            R.string.exercise_progress_range_to,
                            formatExerciseProgressDate(endEpochDay)
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Text(
                    text = stringResource(R.string.exercise_progress_range_invalid_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Button(
            onClick = onApply,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.exercise_progress_range_apply))
        }

        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.exercise_progress_range_cancel))
        }
    }
}

@Composable
fun ExerciseProgressChartPanel(
    entries: List<ExerciseProgressPoint>,
    range: ExerciseProgressRange,
    modifier: Modifier = Modifier,
    onSizeChanged: (IntSize) -> Unit,
    onGesture: ((pan: Offset, zoom: Float) -> Unit)? = null
) {
    val baseModifier = modifier.onSizeChanged(onSizeChanged)
    val interactiveModifier = if (onGesture == null || entries.isEmpty()) {
        baseModifier
    } else {
        baseModifier.pointerInput(entries, range) {
            detectTransformGestures { _, pan, zoom, _ ->
                onGesture(pan, zoom)
            }
        }
    }

    Box(
        modifier = interactiveModifier
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.25f),
                shape = MaterialTheme.shapes.large
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.large
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.exercise_progress_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            ExerciseProgressChart(
                entries = entries,
                range = range,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun ExerciseSelectorButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier.heightIn(min = 40.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
        ) {
            Text(
                text = label,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 40.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
        ) {
            Text(
                text = label,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
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

fun buildExerciseXAxisTicks(
    range: ExerciseProgressRange,
    availableWidthPx: Float,
    minLabelSpacingPx: Float
): List<Long> {
    val dayCount = range.dayCount.coerceAtLeast(1L)
    if (dayCount == 1L) {
        return listOf(range.startEpochDay)
    }

    val maxLabelCount = max(2, floor(availableWidthPx / minLabelSpacingPx).toInt())
    val labelCount = min(maxLabelCount, dayCount.toInt()).coerceAtLeast(2)
    if (labelCount == 2) {
        return listOf(range.startEpochDay, range.endEpochDay)
    }

    val step = (dayCount - 1).toDouble() / (labelCount - 1).toDouble()
    return buildList {
        for (index in 0 until labelCount) {
            val epochDay = (range.startEpochDay + (index * step)).roundToLong()
            if (isEmpty() || last() != epochDay) {
                add(epochDay)
            }
        }
        if (first() != range.startEpochDay) {
            add(0, range.startEpochDay)
        }
        if (last() != range.endEpochDay) {
            add(range.endEpochDay)
        }
    }.sorted()
}

fun buildExerciseYAxisTicks(
    points: List<ExerciseProgressPoint>,
    desiredTickCount: Int
): List<ExerciseProgressAxisTick> {
    val minValue = points.minOf { it.totalReps }.toDouble()
    val maxValue = points.maxOf { it.totalReps }.toDouble()

    if (abs(maxValue - minValue) < 0.0001) {
        val center = minValue
        val step = niceExerciseStep(maxOf(1.0, abs(center) * 0.1))
        return listOf(
            ExerciseProgressAxisTick(center - step, step),
            ExerciseProgressAxisTick(center, step),
            ExerciseProgressAxisTick(center + step, step)
        )
    }

    val intervals = max(2, desiredTickCount - 1)
    val niceRange = niceExerciseStep(maxValue - minValue, round = false)
    val step = niceExerciseStep(niceRange / intervals.toDouble())
    var tickStart = floor(minValue / step) * step
    var tickEnd = ceil(maxValue / step) * step
    if (tickEnd - tickStart < step * 1.5) {
        tickStart -= step
        tickEnd += step
    }

    val ticks = buildList {
        var value = tickStart
        while (value <= tickEnd + step / 2.0) {
            add(ExerciseProgressAxisTick(value, step))
            value += step
        }
    }

    return if (ticks.size <= desiredTickCount + 1) {
        ticks
    } else {
        ticks.filterIndexed { index, _ -> index % 2 == 0 }.ifEmpty { ticks.take(desiredTickCount) }
    }
}

fun niceExerciseStep(value: Double, round: Boolean = true): Double {
    if (value <= 0.0) {
        return 1.0
    }

    val exponent = floor(kotlin.math.log10(value))
    val fraction = value / 10.0.pow(exponent)
    val niceFraction = when {
        round && fraction < 1.5 -> 1.0
        round && fraction < 3.0 -> 2.0
        round && fraction < 7.0 -> 5.0
        round -> 10.0
        fraction <= 1.0 -> 1.0
        fraction <= 2.0 -> 2.0
        fraction <= 5.0 -> 5.0
        else -> 10.0
    }

    return niceFraction * 10.0.pow(exponent)
}

fun formatExerciseAxisLabel(value: Double, step: Double): String {
    val normalizedStep = abs(step)
    return when {
        normalizedStep >= 1.0 || abs(value - value.roundToLong().toDouble()) < 0.05 -> {
            value.roundToLong().toString()
        }
        normalizedStep >= 0.5 -> String.format(Locale.getDefault(), "%.1f", value)
        else -> String.format(Locale.getDefault(), "%.2f", value)
    }
}

fun formatExerciseAxisDateLabel(epochDay: Long, range: ExerciseProgressRange): String {
    val date = safeLocalDateOfEpochDay(epochDay)
    return if (range.dayCount <= 60L) {
        date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    } else {
        date.format(DateTimeFormatter.ofPattern("MMM yy", Locale.getDefault()))
    }
}

fun formatExerciseProgressDate(epochDay: Long): String {
    return safeLocalDateOfEpochDay(epochDay).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
}

@Composable
private fun ExerciseRangePresetButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier.heightIn(min = 34.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Text(label)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 34.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Text(label)
        }
    }
}

@Composable
private fun ExerciseProgressChart(
    entries: List<ExerciseProgressPoint>,
    range: ExerciseProgressRange,
    modifier: Modifier = Modifier
) {
    val emptyStateColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
    val strokeColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
    val pointSurfaceColor = MaterialTheme.colorScheme.surface

    Canvas(modifier = modifier) {
        if (entries.isEmpty()) {
            drawRoundRect(
                color = emptyStateColor,
                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
            )
            return@Canvas
        }

        val start = range.startEpochDay.toDouble()
        val end = range.endEpochDay.toDouble()
        val availableWidth = size.width
        val availableHeight = size.height
        val axisTextSize = 10.5.sp.toPx()
        val axisTickStroke = 1.dp.toPx()
        val axisMarkStroke = 1.dp.toPx()
        val yLabelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor.toArgb()
            textSize = axisTextSize
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        val xLabelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor.toArgb()
            textSize = axisTextSize
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val minYTickCount = 3
        val maxYTickCount = if (availableHeight < 128f) 3 else 4
        val yTicks = buildExerciseYAxisTicks(
            points = entries,
            desiredTickCount = maxYTickCount.coerceAtLeast(minYTickCount)
        )
        val yLabelWidth = yTicks.maxOfOrNull { tick ->
            yLabelPaint.measureText(formatExerciseAxisLabel(tick.value, tick.step))
        } ?: 0f
        val xLabelSampleHeight = run {
            val fontMetrics = xLabelPaint.fontMetrics
            fontMetrics.descent - fontMetrics.ascent
        }
        val leftMargin = max(34.dp.toPx(), yLabelWidth + 10.dp.toPx())
        val rightMargin = 8.dp.toPx()
        val topMargin = 8.dp.toPx()
        val bottomMargin = max(20.dp.toPx(), xLabelSampleHeight + 10.dp.toPx())
        val plotLeft = leftMargin
        val plotTop = topMargin
        val plotRight = (availableWidth - rightMargin).coerceAtLeast(plotLeft + 1f)
        val plotBottom = (availableHeight - bottomMargin).coerceAtLeast(plotTop + 1f)
        val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
        val plotHeight = (plotBottom - plotTop).coerceAtLeast(1f)
        val xLabelTickCount = buildExerciseXAxisTicks(
            range = range,
            availableWidthPx = plotWidth,
            minLabelSpacingPx = 60.dp.toPx()
        )
        val xLabelFontMetrics = xLabelPaint.fontMetrics
        val xLabelBaseline = plotBottom + 4.dp.toPx() - xLabelFontMetrics.ascent
        val minReps = yTicks.first().value
        val maxReps = yTicks.last().value
        val repsRange = max(0.1, maxReps - minReps)
        val chartPoints = entries.map { point ->
            val xFraction = if (abs(end - start) < 0.0001) {
                0.5f
            } else {
                ((point.entryDateEpochDay - start) / (end - start)).toFloat().coerceIn(0f, 1f)
            }
            val yFraction = ((point.totalReps - minReps) / repsRange).toFloat().coerceIn(0f, 1f)
            val x = plotLeft + (xFraction * plotWidth)
            val y = plotTop + ((1f - yFraction) * plotHeight)
            Offset(x, y)
        }
        val drawMarkers = chartPoints.size <= 240

        drawLine(
            color = axisColor,
            start = Offset(plotLeft, plotBottom),
            end = Offset(plotRight, plotBottom),
            strokeWidth = axisTickStroke
        )
        drawLine(
            color = axisColor,
            start = Offset(plotLeft, plotTop),
            end = Offset(plotLeft, plotBottom),
            strokeWidth = axisTickStroke
        )

        yTicks.forEach { tick ->
            val y = plotBottom - ((tick.value - minReps) / repsRange).toFloat() * plotHeight
            drawLine(
                color = gridColor,
                start = Offset(plotLeft, y),
                end = Offset(plotRight, y),
                strokeWidth = axisMarkStroke
            )
            drawLine(
                color = axisColor,
                start = Offset(plotLeft - 4.dp.toPx(), y),
                end = Offset(plotLeft, y),
                strokeWidth = axisTickStroke
            )
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    formatExerciseAxisLabel(tick.value, tick.step),
                    plotLeft - 6.dp.toPx(),
                    y - (yLabelPaint.descent() + yLabelPaint.ascent()) / 2f,
                    yLabelPaint
                )
            }
        }

        xLabelTickCount.forEach { epochDay ->
            val xFraction = if (range.dayCount <= 1L) {
                0.5f
            } else {
                ((epochDay - range.startEpochDay).toDouble() / (range.dayCount - 1).toDouble())
                    .toFloat()
                    .coerceIn(0f, 1f)
            }
            val x = plotLeft + (xFraction * plotWidth)
            drawLine(
                color = axisColor,
                start = Offset(x, plotBottom),
                end = Offset(x, plotBottom + 4.dp.toPx()),
                strokeWidth = axisTickStroke
            )
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    formatExerciseAxisDateLabel(epochDay, range),
                    x,
                    xLabelBaseline,
                    xLabelPaint
                )
            }
        }

        if (chartPoints.size > 1) {
            val linePath = Path().apply {
                moveTo(chartPoints.first().x, chartPoints.first().y)
                chartPoints.drop(1).forEach { point ->
                    lineTo(point.x, point.y)
                }
            }
            drawPath(
                path = linePath,
                color = strokeColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx())
            )
        }

        if (drawMarkers) {
            chartPoints.forEach { point ->
                drawCircle(
                    color = strokeColor,
                    radius = 4.5.dp.toPx(),
                    center = point
                )
                drawCircle(
                    color = pointSurfaceColor,
                    radius = 2.dp.toPx(),
                    center = point
                )
            }
        }
    }
}

private fun openDatePicker(
    context: android.content.Context,
    initialEpochDay: Long,
    onDatePicked: (Long) -> Unit
) {
    val initialDate = safeLocalDateOfEpochDay(initialEpochDay)
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            onDatePicked(LocalDate.of(year, month + 1, dayOfMonth).toEpochDay())
        },
        initialDate.year,
        initialDate.monthValue - 1,
        initialDate.dayOfMonth
    ).show()
}

private fun safeLocalDateOfEpochDay(epochDay: Long): LocalDate {
    return runCatching { LocalDate.ofEpochDay(epochDay) }
        .getOrElse { LocalDate.now() }
}

private fun epochMillisToLocalDate(epochMillis: Long): LocalDate {
    return Instant.ofEpochMilli(epochMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
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
