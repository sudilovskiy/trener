package com.example.trener

import android.app.DatePickerDialog
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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

private fun formatExerciseProgressDate(epochDay: Long): String {
    return safeLocalDateOfEpochDay(epochDay).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
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
