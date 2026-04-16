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
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

private enum class ComparisonSheet {
    SelectorA,
    SelectorB,
    Range
}

private enum class ComparisonRangeTarget {
    Synced,
    ChartA,
    ChartB
}

private enum class ComparisonMetricMode {
    ExerciseComparison,
    TrainingDuration
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseComparisonScreen(
    databaseRefreshToken: Int,
    historyRefreshToken: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val database = rememberTrenerDatabase(databaseRefreshToken)
    val allExercises = remember { getAllExercises() }
    val defaultExerciseA = remember(allExercises) {
        allExercises.firstOrNull()?.exerciseId.orEmpty()
    }
    val defaultExerciseB = remember(allExercises, defaultExerciseA) {
        allExercises.getOrNull(1)?.exerciseId ?: defaultExerciseA
    }
    var comparisonMetricMode by rememberSaveable {
        mutableStateOf(ComparisonMetricMode.ExerciseComparison)
    }
    var selectedExerciseA by rememberSaveable { mutableStateOf(defaultExerciseA) }
    var selectedExerciseB by rememberSaveable { mutableStateOf(defaultExerciseB) }
    var exerciseProgressEntriesA by remember { mutableStateOf<List<ExerciseProgressPoint>>(emptyList()) }
    var exerciseProgressEntriesB by remember { mutableStateOf<List<ExerciseProgressPoint>>(emptyList()) }
    var trainingDurationEntries by remember { mutableStateOf<List<WorkoutDurationPoint>>(emptyList()) }

    var syncRanges by rememberSaveable { mutableStateOf(false) }
    var rangeModeA by rememberSaveable {
        mutableStateOf(ExerciseProgressRangeMode.SevenDays)
    }
    var rangeStartEpochDayA by rememberSaveable {
        mutableLongStateOf(LocalDate.now().minusDays(6).toEpochDay())
    }
    var rangeEndEpochDayA by rememberSaveable {
        mutableLongStateOf(LocalDate.now().toEpochDay())
    }
    var rangeModeB by rememberSaveable {
        mutableStateOf(ExerciseProgressRangeMode.SevenDays)
    }
    var rangeStartEpochDayB by rememberSaveable {
        mutableLongStateOf(LocalDate.now().minusDays(6).toEpochDay())
    }
    var rangeEndEpochDayB by rememberSaveable {
        mutableLongStateOf(LocalDate.now().toEpochDay())
    }
    var syncedRangeMode by rememberSaveable {
        mutableStateOf(ExerciseProgressRangeMode.SevenDays)
    }
    var syncedRangeStartEpochDay by rememberSaveable {
        mutableLongStateOf(LocalDate.now().minusDays(6).toEpochDay())
    }
    var syncedRangeEndEpochDay by rememberSaveable {
        mutableLongStateOf(LocalDate.now().toEpochDay())
    }
    var rangeDraftMode by rememberSaveable {
        mutableStateOf(ExerciseProgressRangeMode.SevenDays)
    }
    var rangeDraftStartEpochDay by rememberSaveable {
        mutableLongStateOf(LocalDate.now().minusDays(6).toEpochDay())
    }
    var rangeDraftEndEpochDay by rememberSaveable {
        mutableLongStateOf(LocalDate.now().toEpochDay())
    }
    var durationRangeMode by rememberSaveable {
        mutableStateOf(ExerciseProgressRangeMode.All)
    }
    var durationRangeStartEpochDay by rememberSaveable {
        mutableLongStateOf(LocalDate.now().minusDays(6).toEpochDay())
    }
    var durationRangeEndEpochDay by rememberSaveable {
        mutableLongStateOf(LocalDate.now().toEpochDay())
    }
    var durationRangeDraftMode by rememberSaveable {
        mutableStateOf(ExerciseProgressRangeMode.All)
    }
    var durationRangeDraftStartEpochDay by rememberSaveable {
        mutableLongStateOf(LocalDate.now().minusDays(6).toEpochDay())
    }
    var durationRangeDraftEndEpochDay by rememberSaveable {
        mutableLongStateOf(LocalDate.now().toEpochDay())
    }
    var activeSheet by remember { mutableStateOf<ComparisonSheet?>(null) }
    var comparisonRangeTarget by rememberSaveable {
        mutableStateOf(ComparisonRangeTarget.Synced)
    }
    val rangeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var hasInitializedRangeA by rememberSaveable { mutableStateOf(false) }
    var hasInitializedRangeB by rememberSaveable { mutableStateOf(false) }
    var hasManualRangeChangeA by rememberSaveable { mutableStateOf(false) }
    var hasManualRangeChangeB by rememberSaveable { mutableStateOf(false) }
    var hasInitializedDurationRange by rememberSaveable { mutableStateOf(false) }
    var hasManualDurationRangeChange by rememberSaveable { mutableStateOf(false) }
    val orientation = LocalConfiguration.current.orientation
    val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE

    val rangeA = remember(rangeStartEpochDayA, rangeEndEpochDayA) {
        ExerciseProgressRange(
            startEpochDay = rangeStartEpochDayA,
            endEpochDay = rangeEndEpochDayA
        )
    }
    val rangeB = remember(rangeStartEpochDayB, rangeEndEpochDayB) {
        ExerciseProgressRange(
            startEpochDay = rangeStartEpochDayB,
            endEpochDay = rangeEndEpochDayB
        )
    }
    val syncedRange = remember(syncedRangeStartEpochDay, syncedRangeEndEpochDay) {
        ExerciseProgressRange(
            startEpochDay = syncedRangeStartEpochDay,
            endEpochDay = syncedRangeEndEpochDay
        )
    }
    val visibleRangeA = if (syncRanges) syncedRange else rangeA
    val visibleRangeB = if (syncRanges) syncedRange else rangeB
    val selectedExerciseLabelA = remember(selectedExerciseA, context) {
        getExerciseLabel(context, selectedExerciseA)
    }
    val selectedExerciseLabelB = remember(selectedExerciseB, context) {
        getExerciseLabel(context, selectedExerciseB)
    }
    val exerciseComparisonSeriesA = remember(exerciseProgressEntriesA) {
        exerciseProgressEntriesA.map(ExerciseProgressPoint::toComparisonSeriesPoint)
    }
    val exerciseComparisonSeriesB = remember(exerciseProgressEntriesB) {
        exerciseProgressEntriesB.map(ExerciseProgressPoint::toComparisonSeriesPoint)
    }
    val combinedSeriesRecords = remember(exerciseComparisonSeriesA, exerciseComparisonSeriesB) {
        (exerciseComparisonSeriesA + exerciseComparisonSeriesB)
            .sortedBy(ComparisonSeriesPoint::entryDateEpochDay)
    }
    val trainingDurationSeries = remember(trainingDurationEntries) {
        trainingDurationEntries.map(WorkoutDurationPoint::toComparisonSeriesPoint)
    }
    val filteredSeriesA = remember(exerciseComparisonSeriesA, visibleRangeA) {
        exerciseComparisonSeriesA.filter { point ->
            point.entryDateEpochDay in visibleRangeA.startEpochDay..visibleRangeA.endEpochDay
        }
    }
    val filteredSeriesB = remember(exerciseComparisonSeriesB, visibleRangeB) {
        exerciseComparisonSeriesB.filter { point ->
            point.entryDateEpochDay in visibleRangeB.startEpochDay..visibleRangeB.endEpochDay
        }
    }
    val durationVisibleRange = remember(durationRangeStartEpochDay, durationRangeEndEpochDay) {
        ExerciseProgressRange(
            startEpochDay = durationRangeStartEpochDay,
            endEpochDay = durationRangeEndEpochDay
        )
    }
    val filteredDurationSeries = remember(trainingDurationSeries, durationVisibleRange) {
        trainingDurationSeries.filter { point ->
            point.entryDateEpochDay in durationVisibleRange.startEpochDay..durationVisibleRange.endEpochDay
        }
    }

    fun updateRangeA(mode: ExerciseProgressRangeMode, range: ExerciseProgressRange) {
        hasManualRangeChangeA = true
        rangeModeA = mode
        rangeStartEpochDayA = range.startEpochDay
        rangeEndEpochDayA = range.endEpochDay
    }

    fun updateRangeB(mode: ExerciseProgressRangeMode, range: ExerciseProgressRange) {
        hasManualRangeChangeB = true
        rangeModeB = mode
        rangeStartEpochDayB = range.startEpochDay
        rangeEndEpochDayB = range.endEpochDay
    }

    fun updateSyncedRange(mode: ExerciseProgressRangeMode, range: ExerciseProgressRange) {
        syncedRangeMode = mode
        syncedRangeStartEpochDay = range.startEpochDay
        syncedRangeEndEpochDay = range.endEpochDay
    }

    fun setSyncRanges(enabled: Boolean) {
        if (enabled == syncRanges) {
            return
        }
        if (enabled) {
            syncedRangeMode = rangeModeA
            syncedRangeStartEpochDay = rangeA.startEpochDay
            syncedRangeEndEpochDay = rangeA.endEpochDay
        }
        syncRanges = enabled
    }

    fun seedComparisonRangeDraft(target: ComparisonRangeTarget) {
        when (target) {
            ComparisonRangeTarget.Synced -> {
                rangeDraftMode = syncedRangeMode
                rangeDraftStartEpochDay = syncedRange.startEpochDay
                rangeDraftEndEpochDay = syncedRange.endEpochDay
            }
            ComparisonRangeTarget.ChartA -> {
                rangeDraftMode = rangeModeA
                rangeDraftStartEpochDay = rangeA.startEpochDay
                rangeDraftEndEpochDay = rangeA.endEpochDay
            }
            ComparisonRangeTarget.ChartB -> {
                rangeDraftMode = rangeModeB
                rangeDraftStartEpochDay = rangeB.startEpochDay
                rangeDraftEndEpochDay = rangeB.endEpochDay
            }
        }
    }

    fun openComparisonRangeSheet(target: ComparisonRangeTarget) {
        comparisonRangeTarget = target
        seedComparisonRangeDraft(target)
        activeSheet = ComparisonSheet.Range
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

    LaunchedEffect(databaseRefreshToken, historyRefreshToken) {
        trainingDurationEntries = withContext(Dispatchers.IO) {
            loadWorkoutDurationEntries(database)
        }
    }

    LaunchedEffect(exerciseProgressEntriesA, hasInitializedRangeA, hasManualRangeChangeA) {
        if (hasInitializedRangeA || hasManualRangeChangeA || exerciseProgressEntriesA.isEmpty()) {
            return@LaunchedEffect
        }

        val resolvedRange = resolveExerciseProgressRange(
            records = exerciseProgressEntriesA,
            mode = ExerciseProgressRangeMode.All,
            currentRange = rangeA
        )
        rangeModeA = ExerciseProgressRangeMode.All
        rangeStartEpochDayA = resolvedRange.startEpochDay
        rangeEndEpochDayA = resolvedRange.endEpochDay
        syncedRangeMode = ExerciseProgressRangeMode.All
        syncedRangeStartEpochDay = resolvedRange.startEpochDay
        syncedRangeEndEpochDay = resolvedRange.endEpochDay
        hasInitializedRangeA = true
    }

    LaunchedEffect(exerciseProgressEntriesB, hasInitializedRangeB, hasManualRangeChangeB) {
        if (hasInitializedRangeB || hasManualRangeChangeB || exerciseProgressEntriesB.isEmpty()) {
            return@LaunchedEffect
        }

        val resolvedRange = resolveExerciseProgressRange(
            records = exerciseProgressEntriesB,
            mode = ExerciseProgressRangeMode.All,
            currentRange = rangeB
        )
        rangeStartEpochDayB = resolvedRange.startEpochDay
        rangeEndEpochDayB = resolvedRange.endEpochDay
        hasInitializedRangeB = true
    }

    LaunchedEffect(trainingDurationEntries, hasInitializedDurationRange, hasManualDurationRangeChange) {
        if (hasInitializedDurationRange || hasManualDurationRangeChange || trainingDurationEntries.isEmpty()) {
            return@LaunchedEffect
        }

        val resolvedRange = resolveWorkoutDurationRange(
            records = trainingDurationEntries,
            mode = ExerciseProgressRangeMode.All,
            currentRange = durationVisibleRange
        )
        durationRangeMode = ExerciseProgressRangeMode.All
        durationRangeStartEpochDay = resolvedRange.startEpochDay
        durationRangeEndEpochDay = resolvedRange.endEpochDay
        hasInitializedDurationRange = true
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
                ExerciseSelectorButton(
                    label = stringResource(R.string.exercise_comparison_metric_exercise),
                    selected = comparisonMetricMode == ComparisonMetricMode.ExerciseComparison,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        comparisonMetricMode = ComparisonMetricMode.ExerciseComparison
                        activeSheet = null
                    }
                )
                ExerciseSelectorButton(
                    label = stringResource(R.string.exercise_comparison_metric_duration),
                    selected = comparisonMetricMode == ComparisonMetricMode.TrainingDuration,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        comparisonMetricMode = ComparisonMetricMode.TrainingDuration
                        activeSheet = null
                    }
                )
            }

            if (comparisonMetricMode == ComparisonMetricMode.ExerciseComparison) {
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

                if (syncRanges) {
                    val syncedRangeLabel = remember(syncedRangeMode, context) {
                        syncedRangeMode.displayLabel(context)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { openComparisonRangeSheet(ComparisonRangeTarget.Synced) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.exercise_progress_range_label,
                                    syncedRangeLabel
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = syncRanges,
                                onCheckedChange = { checked -> setSyncRanges(checked) }
                            )
                            Text(text = stringResource(R.string.exercise_comparison_sync_ranges))
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = syncRanges,
                            onCheckedChange = { checked -> setSyncRanges(checked) }
                        )
                        Text(text = stringResource(R.string.exercise_comparison_sync_ranges))
                    }
                }

                if (isLandscape) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!syncRanges) {
                                val rangeLabelA = remember(rangeModeA, context) {
                                    rangeModeA.displayLabel(context)
                                }
                                ComparisonRangeControlButton(
                                    label = stringResource(
                                        R.string.exercise_comparison_selector_a,
                                        rangeLabelA
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { openComparisonRangeSheet(ComparisonRangeTarget.ChartA) }
                                )
                            }
                            ComparisonChartPanel(
                                title = selectedExerciseLabelA,
                                entries = filteredSeriesA,
                                range = visibleRangeA,
                                gestureRecords = if (syncRanges) combinedSeriesRecords else exerciseComparisonSeriesA,
                                gestureRange = visibleRangeA,
                                onRangeChanged = { range ->
                                    if (syncRanges) {
                                        updateSyncedRange(ExerciseProgressRangeMode.Custom, range)
                                    } else {
                                        updateRangeA(ExerciseProgressRangeMode.Custom, range)
                                    }
                                }
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!syncRanges) {
                                val rangeLabelB = remember(rangeModeB, context) {
                                    rangeModeB.displayLabel(context)
                                }
                                ComparisonRangeControlButton(
                                    label = stringResource(
                                        R.string.exercise_comparison_selector_b,
                                        rangeLabelB
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { openComparisonRangeSheet(ComparisonRangeTarget.ChartB) }
                                )
                            }
                            ComparisonChartPanel(
                                title = selectedExerciseLabelB,
                                entries = filteredSeriesB,
                                range = visibleRangeB,
                                gestureRecords = if (syncRanges) combinedSeriesRecords else exerciseComparisonSeriesB,
                                gestureRange = visibleRangeB,
                                onRangeChanged = { range ->
                                    if (syncRanges) {
                                        updateSyncedRange(ExerciseProgressRangeMode.Custom, range)
                                    } else {
                                        updateRangeB(ExerciseProgressRangeMode.Custom, range)
                                    }
                                }
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!syncRanges) {
                                val rangeLabelA = remember(rangeModeA, context) {
                                    rangeModeA.displayLabel(context)
                                }
                                ComparisonRangeControlButton(
                                    label = stringResource(
                                        R.string.exercise_comparison_selector_a,
                                        rangeLabelA
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { openComparisonRangeSheet(ComparisonRangeTarget.ChartA) }
                                )
                            }
                            ComparisonChartPanel(
                                title = selectedExerciseLabelA,
                                entries = filteredSeriesA,
                                range = visibleRangeA,
                                gestureRecords = if (syncRanges) combinedSeriesRecords else exerciseComparisonSeriesA,
                                gestureRange = visibleRangeA,
                                onRangeChanged = { range ->
                                    if (syncRanges) {
                                        updateSyncedRange(ExerciseProgressRangeMode.Custom, range)
                                    } else {
                                        updateRangeA(ExerciseProgressRangeMode.Custom, range)
                                    }
                                }
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!syncRanges) {
                                val rangeLabelB = remember(rangeModeB, context) {
                                    rangeModeB.displayLabel(context)
                                }
                                ComparisonRangeControlButton(
                                    label = stringResource(
                                        R.string.exercise_comparison_selector_b,
                                        rangeLabelB
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { openComparisonRangeSheet(ComparisonRangeTarget.ChartB) }
                                )
                            }
                            ComparisonChartPanel(
                                title = selectedExerciseLabelB,
                                entries = filteredSeriesB,
                                range = visibleRangeB,
                                gestureRecords = if (syncRanges) combinedSeriesRecords else exerciseComparisonSeriesB,
                                gestureRange = visibleRangeB,
                                onRangeChanged = { range ->
                                    if (syncRanges) {
                                        updateSyncedRange(ExerciseProgressRangeMode.Custom, range)
                                    } else {
                                        updateRangeB(ExerciseProgressRangeMode.Custom, range)
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                val durationRangeLabel = remember(durationRangeMode, context) {
                    durationRangeMode.displayLabel(context)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            durationRangeDraftMode = durationRangeMode
                            durationRangeDraftStartEpochDay = durationVisibleRange.startEpochDay
                            durationRangeDraftEndEpochDay = durationVisibleRange.endEpochDay
                            activeSheet = ComparisonSheet.Range
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.exercise_progress_range_label,
                                durationRangeLabel
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                ComparisonChartPanel(
                    title = stringResource(R.string.exercise_comparison_metric_duration_chart),
                    entries = filteredDurationSeries,
                    range = durationVisibleRange,
                    gestureRecords = trainingDurationSeries,
                    gestureRange = durationVisibleRange,
                    valueLabelFormatter = ::formatDurationAxisLabel,
                    onRangeChanged = { range ->
                        durationRangeMode = ExerciseProgressRangeMode.Custom
                        durationRangeStartEpochDay = range.startEpochDay
                        durationRangeEndEpochDay = range.endEpochDay
                        hasManualDurationRangeChange = true
                    }
                )
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
                        selectedMode = if (comparisonMetricMode == ComparisonMetricMode.ExerciseComparison) {
                            rangeDraftMode
                        } else {
                            durationRangeDraftMode
                        },
                        startEpochDay = if (comparisonMetricMode == ComparisonMetricMode.ExerciseComparison) {
                            rangeDraftStartEpochDay
                        } else {
                            durationRangeDraftStartEpochDay
                        },
                        endEpochDay = if (comparisonMetricMode == ComparisonMetricMode.ExerciseComparison) {
                            rangeDraftEndEpochDay
                        } else {
                            durationRangeDraftEndEpochDay
                        },
                        onPresetSelected = { preset ->
                            if (comparisonMetricMode == ComparisonMetricMode.ExerciseComparison) {
                                rangeDraftMode = preset
                            } else {
                                durationRangeDraftMode = preset
                            }
                        },
                        onStartDatePicked = { epochDay ->
                            if (comparisonMetricMode == ComparisonMetricMode.ExerciseComparison) {
                                rangeDraftStartEpochDay = epochDay
                            } else {
                                durationRangeDraftStartEpochDay = epochDay
                            }
                        },
                        onEndDatePicked = { epochDay ->
                            if (comparisonMetricMode == ComparisonMetricMode.ExerciseComparison) {
                                rangeDraftEndEpochDay = epochDay
                            } else {
                                durationRangeDraftEndEpochDay = epochDay
                            }
                        },
                        onApply = {
                            val targetRecords = if (comparisonMetricMode == ComparisonMetricMode.ExerciseComparison) {
                                when (comparisonRangeTarget) {
                                    ComparisonRangeTarget.Synced -> combinedSeriesRecords
                                    ComparisonRangeTarget.ChartA -> exerciseComparisonSeriesA
                                    ComparisonRangeTarget.ChartB -> exerciseComparisonSeriesB
                                }
                            } else {
                                trainingDurationSeries
                            }
                            val targetRange = if (comparisonMetricMode == ComparisonMetricMode.ExerciseComparison) {
                                when (comparisonRangeTarget) {
                                    ComparisonRangeTarget.Synced -> syncedRange
                                    ComparisonRangeTarget.ChartA -> rangeA
                                    ComparisonRangeTarget.ChartB -> rangeB
                                }
                            } else {
                                durationVisibleRange
                            }
                            val resolvedRange = resolveComparisonRangeSelection(
                                mode = if (comparisonMetricMode == ComparisonMetricMode.ExerciseComparison) {
                                    rangeDraftMode
                                } else {
                                    durationRangeDraftMode
                                },
                                startEpochDay = if (comparisonMetricMode == ComparisonMetricMode.ExerciseComparison) {
                                    rangeDraftStartEpochDay
                                } else {
                                    durationRangeDraftStartEpochDay
                                },
                                endEpochDay = if (comparisonMetricMode == ComparisonMetricMode.ExerciseComparison) {
                                    rangeDraftEndEpochDay
                                } else {
                                    durationRangeDraftEndEpochDay
                                },
                                records = targetRecords,
                                currentRange = targetRange
                            )

                            if (comparisonMetricMode == ComparisonMetricMode.ExerciseComparison) {
                                when (comparisonRangeTarget) {
                                    ComparisonRangeTarget.Synced -> updateSyncedRange(
                                        rangeDraftMode,
                                        resolvedRange
                                    )
                                    ComparisonRangeTarget.ChartA -> updateRangeA(
                                        rangeDraftMode,
                                        resolvedRange
                                    )
                                    ComparisonRangeTarget.ChartB -> updateRangeB(
                                        rangeDraftMode,
                                        resolvedRange
                                    )
                                }
                            } else {
                                durationRangeMode = durationRangeDraftMode
                                durationRangeStartEpochDay = resolvedRange.startEpochDay
                                durationRangeEndEpochDay = resolvedRange.endEpochDay
                                hasManualDurationRangeChange = true
                            }
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

}

@Composable
private fun ComparisonChartPanel(
    title: String,
    entries: List<ComparisonSeriesPoint>,
    range: ExerciseProgressRange,
    gestureRecords: List<ComparisonSeriesPoint>,
    gestureRange: ExerciseProgressRange,
    onRangeChanged: (ExerciseProgressRange) -> Unit,
    valueLabelFormatter: (Double, Double) -> String = ::formatExerciseAxisLabel,
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
                val resolved = resolveComparisonSeriesGestureRange(
                    currentRange = gestureRange,
                    records = gestureRecords,
                    chartWidthPx = chartWidthPx,
                    panX = pan.x,
                    zoom = zoom
                )
                if (resolved != null) {
                    onRangeChanged(resolved)
                }
            },
            valueLabelFormatter = valueLabelFormatter
        )
    }
}

@Composable
private fun ComparisonRangeControlButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Text(
            text = label,
            maxLines = 1,
            softWrap = false
        )
    }
}

private fun resolveComparisonRangeSelection(
    mode: ExerciseProgressRangeMode,
    startEpochDay: Long,
    endEpochDay: Long,
    records: List<ComparisonSeriesPoint>,
    currentRange: ExerciseProgressRange
): ExerciseProgressRange {
    return when (mode) {
        ExerciseProgressRangeMode.SevenDays,
        ExerciseProgressRangeMode.ThirtyDays,
        ExerciseProgressRangeMode.All -> resolveComparisonSeriesRange(
            records = records,
            mode = mode,
            currentRange = currentRange
        )

        ExerciseProgressRangeMode.Custom -> resolveClampedComparisonSeriesRange(
            startEpochDay = startEpochDay,
            endEpochDay = endEpochDay,
            records = records
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
