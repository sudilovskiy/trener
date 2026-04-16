package com.example.trener

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
internal fun ComparisonSeriesChart(
    entries: List<ComparisonSeriesPoint>,
    range: ExerciseProgressRange,
    modifier: Modifier = Modifier,
    formatYAxisLabel: (Double, Double) -> String = ::formatExerciseAxisLabel
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
        val yTicks = buildComparisonYAxisTicks(
            points = entries,
            desiredTickCount = maxYTickCount.coerceAtLeast(minYTickCount)
        )
        val yLabelWidth = yTicks.maxOfOrNull { tick ->
            yLabelPaint.measureText(formatYAxisLabel(tick.value, tick.step))
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
            val yFraction = ((point.value - minReps) / repsRange).toFloat().coerceIn(0f, 1f)
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
                    formatYAxisLabel(tick.value, tick.step),
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

private fun buildExerciseXAxisTicks(
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

private fun buildComparisonYAxisTicks(
    points: List<ComparisonSeriesPoint>,
    desiredTickCount: Int
): List<ExerciseProgressAxisTick> {
    val minValue = points.minOf { it.value }
    val maxValue = points.maxOf { it.value }

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

private fun niceExerciseStep(value: Double, round: Boolean = true): Double {
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

internal fun formatExerciseAxisLabel(value: Double, step: Double): String {
    val normalizedStep = abs(step)
    return when {
        normalizedStep >= 1.0 || abs(value - value.roundToLong().toDouble()) < 0.05 -> {
            value.roundToLong().toString()
        }
        normalizedStep >= 0.5 -> String.format(Locale.getDefault(), "%.1f", value)
        else -> String.format(Locale.getDefault(), "%.2f", value)
    }
}

private fun formatExerciseAxisDateLabel(epochDay: Long, range: ExerciseProgressRange): String {
    val date = safeLocalDateOfEpochDay(epochDay)
    return if (range.dayCount <= 60L) {
        date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    } else {
        date.format(DateTimeFormatter.ofPattern("MMM yy", Locale.getDefault()))
    }
}

private fun safeLocalDateOfEpochDay(epochDay: Long): LocalDate {
    return runCatching { LocalDate.ofEpochDay(epochDay) }
        .getOrElse { LocalDate.now() }
}
