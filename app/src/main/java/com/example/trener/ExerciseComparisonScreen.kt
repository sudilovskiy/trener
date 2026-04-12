package com.example.trener

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.trener.data.local.TrenerDatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

private enum class ComparisonSheet {
    SelectorA,
    SelectorB,
    Range
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseComparisonScreen(
    databaseRefreshToken: Int,
    historyRefreshToken: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val database = remember(context, databaseRefreshToken) {
        TrenerDatabaseProvider.getInstance(context)
    }
    val allExercises = remember { getAllExercises() }
    val defaultExerciseA = remember(allExercises) {
        allExercises.firstOrNull()?.exerciseId.orEmpty()
    }
    val defaultExerciseB = remember(allExercises, defaultExerciseA) {
        allExercises.getOrNull(1)?.exerciseId ?: defaultExerciseA
    }
    var selectedExerciseA by rememberSaveable { mutableStateOf(defaultExerciseA) }
    var selectedExerciseB by rememberSaveable { mutableStateOf(defaultExerciseB) }
    var exerciseProgressEntriesA by remember { mutableStateOf<List<ExerciseProgressPoint>>(emptyList()) }
    var exerciseProgressEntriesB by remember { mutableStateOf<List<ExerciseProgressPoint>>(emptyList()) }

    var sharedRangeMode by rememberSaveable {
        mutableStateOf(ExerciseProgressRangeMode.SevenDays)
    }
    var sharedRangeStartEpochDay by rememberSaveable {
        mutableLongStateOf(LocalDate.now().minusDays(6).toEpochDay())
    }
    var sharedRangeEndEpochDay by rememberSaveable {
        mutableLongStateOf(LocalDate.now().toEpochDay())
    }
    var sharedRangeDraftMode by rememberSaveable {
        mutableStateOf(ExerciseProgressRangeMode.SevenDays)
    }
    var sharedRangeDraftStartEpochDay by rememberSaveable {
        mutableLongStateOf(LocalDate.now().minusDays(6).toEpochDay())
    }
    var sharedRangeDraftEndEpochDay by rememberSaveable {
        mutableLongStateOf(LocalDate.now().toEpochDay())
    }
    var activeSheet by remember { mutableStateOf<ComparisonSheet?>(null) }
    val rangeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var hasInitializedRange by rememberSaveable { mutableStateOf(false) }
    var hasManualRangeChange by rememberSaveable { mutableStateOf(false) }
    val orientation = LocalConfiguration.current.orientation
    val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE

    val visibleRange = remember(sharedRangeStartEpochDay, sharedRangeEndEpochDay) {
        ExerciseProgressRange(
            startEpochDay = sharedRangeStartEpochDay,
            endEpochDay = sharedRangeEndEpochDay
        )
    }
    val combinedRecords = remember(exerciseProgressEntriesA, exerciseProgressEntriesB) {
        (exerciseProgressEntriesA + exerciseProgressEntriesB)
            .sortedBy(ExerciseProgressPoint::entryDateEpochDay)
    }
    val selectedExerciseLabelA = remember(selectedExerciseA, context) {
        getExerciseLabel(context, selectedExerciseA)
    }
    val selectedExerciseLabelB = remember(selectedExerciseB, context) {
        getExerciseLabel(context, selectedExerciseB)
    }
    val filteredEntriesA = remember(exerciseProgressEntriesA, visibleRange) {
        exerciseProgressEntriesA.filter { point ->
            point.entryDateEpochDay in visibleRange.startEpochDay..visibleRange.endEpochDay
        }
    }
    val filteredEntriesB = remember(exerciseProgressEntriesB, visibleRange) {
        exerciseProgressEntriesB.filter { point ->
            point.entryDateEpochDay in visibleRange.startEpochDay..visibleRange.endEpochDay
        }
    }

    LaunchedEffect(selectedExerciseA, databaseRefreshToken, historyRefreshToken) {
        exerciseProgressEntriesA = withContext(Dispatchers.IO) {
            loadExerciseProgressEntries(
                database = database,
                exerciseId = selectedExerciseA
            )
        }
    }

    LaunchedEffect(selectedExerciseB, databaseRefreshToken, historyRefreshToken) {
        exerciseProgressEntriesB = withContext(Dispatchers.IO) {
            loadExerciseProgressEntries(
                database = database,
                exerciseId = selectedExerciseB
            )
        }
    }

    LaunchedEffect(combinedRecords, hasInitializedRange, hasManualRangeChange) {
        if (hasInitializedRange || hasManualRangeChange || combinedRecords.isEmpty()) {
            return@LaunchedEffect
        }

        val resolvedRange = resolveExerciseProgressRange(
            records = combinedRecords,
            mode = ExerciseProgressRangeMode.All,
            currentRange = visibleRange
        )
        sharedRangeMode = ExerciseProgressRangeMode.All
        sharedRangeStartEpochDay = resolvedRange.startEpochDay
        sharedRangeEndEpochDay = resolvedRange.endEpochDay
        hasInitializedRange = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exercise_comparison_title)) },
                actions = {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.exercise_comparison_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { activeSheet = ComparisonSheet.SelectorA },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(
                            R.string.exercise_comparison_selector_a,
                            selectedExerciseLabelA
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                OutlinedButton(
                    onClick = { activeSheet = ComparisonSheet.SelectorB },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(
                            R.string.exercise_comparison_selector_b,
                            selectedExerciseLabelB
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    sharedRangeDraftMode = sharedRangeMode
                    sharedRangeDraftStartEpochDay = sharedRangeStartEpochDay
                    sharedRangeDraftEndEpochDay = sharedRangeEndEpochDay
                    activeSheet = ComparisonSheet.Range
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(
                        R.string.exercise_progress_range_label,
                        sharedRangeMode.displayLabel(context)
                    ),
                    maxLines = 1,
                    softWrap = false
                )
            }

            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ComparisonChartPanel(
                        title = selectedExerciseLabelA,
                        entries = filteredEntriesA,
                        range = visibleRange,
                        sharedRecords = combinedRecords,
                        sharedRange = visibleRange,
                        onRangeChanged = { range ->
                            hasManualRangeChange = true
                            sharedRangeMode = ExerciseProgressRangeMode.Custom
                            sharedRangeStartEpochDay = range.startEpochDay
                            sharedRangeEndEpochDay = range.endEpochDay
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ComparisonChartPanel(
                        title = selectedExerciseLabelB,
                        entries = filteredEntriesB,
                        range = visibleRange,
                        sharedRecords = combinedRecords,
                        sharedRange = visibleRange,
                        onRangeChanged = { range ->
                            hasManualRangeChange = true
                            sharedRangeMode = ExerciseProgressRangeMode.Custom
                            sharedRangeStartEpochDay = range.startEpochDay
                            sharedRangeEndEpochDay = range.endEpochDay
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ComparisonChartPanel(
                        title = selectedExerciseLabelA,
                        entries = filteredEntriesA,
                        range = visibleRange,
                        sharedRecords = combinedRecords,
                        sharedRange = visibleRange,
                        onRangeChanged = { range ->
                            hasManualRangeChange = true
                            sharedRangeMode = ExerciseProgressRangeMode.Custom
                            sharedRangeStartEpochDay = range.startEpochDay
                            sharedRangeEndEpochDay = range.endEpochDay
                        }
                    )
                    ComparisonChartPanel(
                        title = selectedExerciseLabelB,
                        entries = filteredEntriesB,
                        range = visibleRange,
                        sharedRecords = combinedRecords,
                        sharedRange = visibleRange,
                        onRangeChanged = { range ->
                            hasManualRangeChange = true
                            sharedRangeMode = ExerciseProgressRangeMode.Custom
                            sharedRangeStartEpochDay = range.startEpochDay
                            sharedRangeEndEpochDay = range.endEpochDay
                        }
                    )
                }
            }
        }

        if (activeSheet != null) {
            ModalBottomSheet(
                onDismissRequest = { activeSheet = null },
                sheetState = rangeSheetState
            ) {
                when (activeSheet) {
                    ComparisonSheet.SelectorA -> ExerciseComparisonSelectorSheet(
                        title = stringResource(R.string.exercise_comparison_selector_a_sheet_title),
                        selectedExerciseId = selectedExerciseA,
                        exercises = allExercises,
                        onExerciseSelected = {
                            selectedExerciseA = it
                            activeSheet = null
                        },
                        onDismiss = { activeSheet = null }
                    )
                    ComparisonSheet.SelectorB -> ExerciseComparisonSelectorSheet(
                        title = stringResource(R.string.exercise_comparison_selector_b_sheet_title),
                        selectedExerciseId = selectedExerciseB,
                        exercises = allExercises,
                        onExerciseSelected = {
                            selectedExerciseB = it
                            activeSheet = null
                        },
                        onDismiss = { activeSheet = null }
                    )
                    ComparisonSheet.Range -> ExerciseProgressRangeSheet(
                        context = context,
                        selectedMode = sharedRangeDraftMode,
                        startEpochDay = sharedRangeDraftStartEpochDay,
                        endEpochDay = sharedRangeDraftEndEpochDay,
                        onPresetSelected = { preset ->
                            sharedRangeDraftMode = preset
                        },
                        onStartDatePicked = { epochDay ->
                            sharedRangeDraftStartEpochDay = epochDay
                        },
                        onEndDatePicked = { epochDay ->
                            sharedRangeDraftEndEpochDay = epochDay
                        },
                        onApply = {
                            val resolvedRange = when (sharedRangeDraftMode) {
                                ExerciseProgressRangeMode.SevenDays,
                                ExerciseProgressRangeMode.ThirtyDays,
                                ExerciseProgressRangeMode.All -> resolveExerciseProgressRange(
                                    records = combinedRecords,
                                    mode = sharedRangeDraftMode,
                                    currentRange = visibleRange
                                )

                                ExerciseProgressRangeMode.Custom -> resolveClampedExerciseProgressRange(
                                    startEpochDay = sharedRangeDraftStartEpochDay,
                                    endEpochDay = sharedRangeDraftEndEpochDay,
                                    records = combinedRecords
                                )
                            }

                            hasManualRangeChange = true
                            sharedRangeMode = sharedRangeDraftMode
                            sharedRangeStartEpochDay = resolvedRange.startEpochDay
                            sharedRangeEndEpochDay = resolvedRange.endEpochDay
                            activeSheet = null
                        },
                        onDismiss = { activeSheet = null }
                    )
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun ComparisonChartPanel(
    title: String,
    entries: List<ExerciseProgressPoint>,
    range: ExerciseProgressRange,
    sharedRecords: List<ExerciseProgressPoint>,
    sharedRange: ExerciseProgressRange,
    onRangeChanged: (ExerciseProgressRange) -> Unit,
    modifier: Modifier = Modifier
) {
    var chartWidthPx by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = androidx.compose.material3.MaterialTheme.typography.titleSmall
        )
        ExerciseProgressChartPanel(
            entries = entries,
            range = range,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            onSizeChanged = { chartWidthPx = it.width },
            onGesture = { pan, zoom ->
                val resolved = resolveExerciseProgressGestureRange(
                    currentRange = sharedRange,
                    records = sharedRecords,
                    chartWidthPx = chartWidthPx,
                    panX = pan.x,
                    zoom = zoom
                )
                if (resolved != null) {
                    onRangeChanged(resolved)
                }
            }
        )
    }
}

@Composable
private fun ExerciseComparisonSelectorSheet(
    title: String,
    selectedExerciseId: String,
    exercises: List<ExerciseDefinition>,
    onExerciseSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)

        exercises.forEach { exercise ->
            ExerciseSelectorButton(
                label = getExerciseLabel(context, exercise.exerciseId),
                selected = exercise.exerciseId == selectedExerciseId,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onExerciseSelected(exercise.exerciseId) }
            )
        }

        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.exercise_progress_range_cancel))
        }
    }
}
