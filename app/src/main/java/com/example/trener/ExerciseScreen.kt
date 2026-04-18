package com.example.trener

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.trener.ble.heartrate.HeartRateBleSourceState
import com.example.trener.data.local.TrenerDatabase
import com.example.trener.domain.heartrate.HeartRateRuntimeState
import com.example.trener.domain.workout.PreparedWorkoutSessionSet
import com.example.trener.domain.workout.toPreparedWorkoutSessionSet
import com.example.trener.domain.workout.toWorkoutSessionSetEntity
import com.example.trener.domain.workout.loadPersistedMaxValuesBySetNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseScreen(
    exerciseId: String,
    databaseRefreshToken: Int,
    programRefreshToken: Int,
    historyRefreshToken: Int,
    sessionUiState: WorkoutSessionUiState,
    heartRateState: HeartRateRuntimeState,
    heartRateBleState: HeartRateBleSourceState? = null,
    onHeartRateReconnect: () -> Unit,
    onHeartRateSelectTargetDevice: ((String) -> Unit)? = null,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val database = rememberTrenerDatabase(databaseRefreshToken)
    val coroutineScope = rememberCoroutineScope()
    val setCount = sessionUiState.getRequiredSetCount(exerciseId)
    val setLabels = List(setCount) { index ->
        context.getString(R.string.exercise_set_label, index + 1)
    }
    val activeExerciseDefinition = remember(sessionUiState.activeWorkout, exerciseId) {
        sessionUiState.getActiveExerciseDefinition(exerciseId)
    }
    val exerciseDefinition = remember(exerciseId, programRefreshToken, activeExerciseDefinition) {
        getExerciseDefinition(
            exerciseId = exerciseId,
            preferredDefinitions = listOfNotNull(activeExerciseDefinition)
        )
    }
    val exerciseName = remember(exerciseId, programRefreshToken, activeExerciseDefinition) {
        getExerciseLabel(
            context = context,
            exerciseId = exerciseId,
            preferredDefinitions = listOfNotNull(activeExerciseDefinition)
        )
    }
    val exerciseInputType = exerciseDefinition?.inputType ?: ExerciseInputType.REPS
    val exerciseParameterLabel = exerciseDefinition?.parameterLabel
        ?.trim()
        .takeIf { !it.isNullOrBlank() }
        ?: stringResource(R.string.exercise_parameter_fallback_label)
    val exerciseParameterType = exerciseDefinition?.parameterType
        ?.trim()
        .orEmpty()
    val exerciseParameterUnit = exerciseDefinition?.parameterUnit
        ?.trim()
        .orEmpty()
    val isPullUpsExercise = exerciseId == "pull_ups_ui"
    val setStates = sessionUiState.getExerciseSetUiStates(exerciseId, setCount)
    val sharedParameterValue = remember(setStates) {
        setStates.asSequence()
            .map { it.set.parameterValue }
            .firstOrNull { it != null }
    }
    val currentExerciseTotalReps by remember(setStates) {
        derivedStateOf {
            setStates.sumOf { setUiState -> setUiState.set.reps ?: 0 }
        }
    }
    val isExerciseCompleted = sessionUiState.isExerciseCompleted(exerciseId, setCount)
    val isEditingEnabled = sessionUiState.isExerciseInActiveWorkout(exerciseId) && !sessionUiState.isSaving
    val focusManager = LocalFocusManager.current
    var exerciseAnalyticsMetric by rememberSaveable(exerciseId) {
        mutableStateOf(defaultExerciseAnalyticsMetric)
    }
    var savingSetNumber by rememberSaveable(exerciseId) { mutableStateOf<Int?>(null) }
    var isFinishingAllSets by rememberSaveable(exerciseId) { mutableStateOf(false) }
    var hasRequestedFinishNavigation by rememberSaveable(exerciseId) { mutableStateOf(false) }
    var showFinishExerciseConfirmation by rememberSaveable(exerciseId) { mutableStateOf(false) }
    var historyLoaded by rememberSaveable(exerciseId) { mutableStateOf(false) }
    var previousExerciseTotalReps by remember(exerciseId) { mutableIntStateOf(0) }
    var exerciseProgressEntries by remember(exerciseId) {
        mutableStateOf<List<ExerciseProgressPoint>>(emptyList())
    }
    var exerciseParameterProgressEntries by remember(exerciseId) {
        mutableStateOf<List<ComparisonSeriesPoint>>(emptyList())
    }
    val exerciseRepetitionProgressSeries = remember(exerciseProgressEntries) {
        exerciseProgressEntries.map(ExerciseProgressPoint::toComparisonSeriesPoint)
    }
    var exerciseProgressRangeMode by rememberSaveable(exerciseId) {
        mutableStateOf(ExerciseProgressRangeMode.All)
    }
    var exerciseProgressRange by remember(exerciseId) {
        mutableStateOf(
            ExerciseProgressRange(
                startEpochDay = LocalDate.now().minusDays(6).toEpochDay(),
                endEpochDay = LocalDate.now().toEpochDay()
            )
        )
    }
    var showExerciseRangeSheet by remember { mutableStateOf(false) }
    var exerciseProgressDraftRangeMode by rememberSaveable(exerciseId) {
        mutableStateOf(ExerciseProgressRangeMode.All)
    }
    var exerciseProgressDraftStartEpochDay by rememberSaveable(exerciseId) {
        mutableStateOf(LocalDate.now().minusDays(6).toEpochDay())
    }
    var exerciseProgressDraftEndEpochDay by rememberSaveable(exerciseId) {
        mutableStateOf(LocalDate.now().toEpochDay())
    }
    var maxValuesBySetNumber by remember(exerciseId, exerciseInputType) {
        mutableStateOf<Map<Int, Int>>(emptyMap())
    }
    val exerciseRangeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val fullDateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }
    val hasParameterAnalytics = remember(
        exerciseParameterType,
        exerciseRepetitionProgressSeries,
        exerciseParameterProgressEntries
    ) {
        exerciseParameterType.isNotBlank() || exerciseParameterProgressEntries.isNotEmpty()
    }
    val selectedExerciseProgressSeries = remember(
        exerciseAnalyticsMetric,
        exerciseRepetitionProgressSeries,
        exerciseParameterProgressEntries
    ) {
        when (exerciseAnalyticsMetric) {
            ExerciseAnalyticsMetric.Repetitions -> exerciseRepetitionProgressSeries
            ExerciseAnalyticsMetric.Parameter -> exerciseParameterProgressEntries
        }
    }
    val filteredExerciseProgressSeries = remember(
        selectedExerciseProgressSeries,
        exerciseProgressRange
    ) {
        selectedExerciseProgressSeries.filter { point ->
            point.entryDateEpochDay in exerciseProgressRange.startEpochDay..exerciseProgressRange.endEpochDay
        }
    }
    val exerciseAnalyticsValueLabelFormatter = remember(exerciseAnalyticsMetric, exerciseParameterUnit) {
        when (exerciseAnalyticsMetric) {
            ExerciseAnalyticsMetric.Repetitions -> ::formatExerciseAxisLabel
            ExerciseAnalyticsMetric.Parameter -> { value: Double, step: Double ->
                buildString {
                    append(formatExerciseAxisLabel(value, step))
                    if (exerciseParameterUnit.isNotBlank()) {
                        append(' ')
                        append(exerciseParameterUnit)
                    }
                }
            }
        }
    }

    fun finishExerciseOnce() {
        if (hasRequestedFinishNavigation) return
        hasRequestedFinishNavigation = true
        onFinished()
    }

    fun openExerciseRangeSheet() {
        exerciseProgressDraftRangeMode = exerciseProgressRangeMode
        exerciseProgressDraftStartEpochDay = exerciseProgressRange.startEpochDay
        exerciseProgressDraftEndEpochDay = exerciseProgressRange.endEpochDay
        showExerciseRangeSheet = true
    }

    fun applyExerciseRangeSelection(
        mode: ExerciseProgressRangeMode,
        startEpochDay: Long,
        endEpochDay: Long
    ) {
        val resolvedRange = when (mode) {
            ExerciseProgressRangeMode.SevenDays,
            ExerciseProgressRangeMode.ThirtyDays,
            ExerciseProgressRangeMode.All -> resolveExerciseProgressRange(
                records = exerciseProgressEntries,
                mode = mode,
                currentRange = exerciseProgressRange
            )

            ExerciseProgressRangeMode.Custom -> resolveClampedExerciseProgressRange(
                startEpochDay = startEpochDay,
                endEpochDay = endEpochDay,
                records = exerciseProgressEntries
            )
        }
        exerciseProgressRangeMode = mode
        exerciseProgressRange = resolvedRange
    }

    LaunchedEffect(hasParameterAnalytics, exerciseAnalyticsMetric) {
        if (!hasParameterAnalytics && exerciseAnalyticsMetric == ExerciseAnalyticsMetric.Parameter) {
            exerciseAnalyticsMetric = ExerciseAnalyticsMetric.Repetitions
        }
    }

    LaunchedEffect(
        exerciseId,
        exerciseInputType,
        isEditingEnabled,
        historyLoaded,
        setCount,
        programRefreshToken
    ) {
        if (historyLoaded || !isEditingEnabled) return@LaunchedEffect

        val previousSets = withContext(Dispatchers.IO) {
            List(setCount) { index ->
                database.workoutSessionSetDao().getLatestSetForExerciseAndNumber(
                    exerciseId = exerciseId,
                    setNumber = index + 1
                )?.toPreparedWorkoutSessionSet() ?: PreparedWorkoutSessionSet(
                    exerciseId = exerciseId,
                    setNumber = index + 1
                )
            }
        }
        sessionUiState.prefillExerciseSetsIfEmpty(
            exerciseId = exerciseId,
            sets = previousSets
        )

        historyLoaded = true
    }

    LaunchedEffect(exerciseId, exerciseInputType) {
        maxValuesBySetNumber = withContext(Dispatchers.IO) {
            loadPersistedMaxValuesBySetNumber(
                database = database,
                exerciseId = exerciseId,
                exerciseInputType = exerciseInputType
            )
        }
    }

    LaunchedEffect(exerciseId, databaseRefreshToken, historyRefreshToken) {
        val loadedPreviousExerciseTotalReps = withContext(Dispatchers.IO) {
            loadLatestExerciseSessionRepsTotal(
                database = database,
                exerciseId = exerciseId
            )
        }
        previousExerciseTotalReps = loadedPreviousExerciseTotalReps

        val loadedExerciseProgressEntries = withContext(Dispatchers.IO) {
            loadExerciseProgressEntries(
                database = database,
                exerciseId = exerciseId
            )
        }
        val loadedExerciseParameterProgressEntries = withContext(Dispatchers.IO) {
            loadExerciseParameterProgressEntries(
                database = database,
                exerciseId = exerciseId
            )
        }
        exerciseProgressEntries = loadedExerciseProgressEntries
        exerciseParameterProgressEntries = loadedExerciseParameterProgressEntries
        exerciseProgressRangeMode = ExerciseProgressRangeMode.All
        exerciseProgressRange = resolveSelectedExerciseAnalyticsRange(
            metric = exerciseAnalyticsMetric,
            repetitions = loadedExerciseProgressEntries.map(ExerciseProgressPoint::toComparisonSeriesPoint),
            parameter = loadedExerciseParameterProgressEntries,
            mode = ExerciseProgressRangeMode.All,
            currentRange = exerciseProgressRange
        )
    }

    LaunchedEffect(
        exerciseAnalyticsMetric,
        exerciseProgressEntries,
        exerciseParameterProgressEntries,
        exerciseProgressRangeMode
    ) {
        if (exerciseProgressRangeMode == ExerciseProgressRangeMode.Custom) {
            return@LaunchedEffect
        }

        exerciseProgressRange = resolveSelectedExerciseAnalyticsRange(
            metric = exerciseAnalyticsMetric,
            repetitions = exerciseRepetitionProgressSeries,
            parameter = exerciseParameterProgressEntries,
            mode = exerciseProgressRangeMode,
            currentRange = exerciseProgressRange
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(exerciseName) }) },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 2.dp
            ) {
                Button(
                    onClick = { showFinishExerciseConfirmation = true },
                    enabled = isEditingEnabled &&
                        savingSetNumber == null &&
                        !isFinishingAllSets &&
                        !isExerciseCompleted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .heightIn(min = 36.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 4.dp
                    )
                ) {
                    Text(
                        text = stringResource(R.string.exercise_finish_all_sets_button),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.exercise_progress_title),
                    modifier = Modifier
                        .weight(1f)
                        .alignByBaseline(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$currentExerciseTotalReps/$previousExerciseTotalReps",
                    modifier = Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    softWrap = false
                )
            }

            HeartRateRuntimeSection(
                state = heartRateState,
                sourceState = heartRateBleState,
                onReconnect = onHeartRateReconnect,
                onSelectTargetDevice = onHeartRateSelectTargetDevice
            )

            if (hasParameterAnalytics) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExerciseSelectorButton(
                        label = stringResource(R.string.exercise_progress_metric_repetitions),
                        selected = exerciseAnalyticsMetric == ExerciseAnalyticsMetric.Repetitions,
                        modifier = Modifier.weight(1f),
                        onClick = { exerciseAnalyticsMetric = ExerciseAnalyticsMetric.Repetitions }
                    )
                    ExerciseSelectorButton(
                        label = stringResource(R.string.exercise_progress_metric_parameter),
                        selected = exerciseAnalyticsMetric == ExerciseAnalyticsMetric.Parameter,
                        modifier = Modifier.weight(1f),
                        onClick = { exerciseAnalyticsMetric = ExerciseAnalyticsMetric.Parameter }
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ExerciseParameterSharedRow(
                        label = exerciseParameterLabel,
                        unit = exerciseParameterUnit,
                        value = sharedParameterValue.toParameterDisplayString(),
                        enabled = isEditingEnabled,
                        onValueChange = { updatedValue ->
                            val parsedValue = updatedValue.toDecimalOrNull()
                            sessionUiState.updateExerciseSets(
                                exerciseId = exerciseId,
                                sets = setStates.map { currentSetUiState ->
                                    currentSetUiState.set.copy(parameterValue = parsedValue)
                                },
                                allowCompletedOverride = true
                            )
                        }
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        setLabels.forEachIndexed { index, setLabel ->
                            val setUiState = setStates[index]
                            val setState = setUiState.set
                            val isSetEditable = isEditingEnabled && !setUiState.isLocked

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { testTag = "exercise-set-${setState.setNumber}" },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (setUiState.isCompleted) {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                        .alpha(if (setUiState.isCompleted) CompletedSetAlpha else 1f),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val maxValue = maxValuesBySetNumber[setState.setNumber] ?: 0

                                    ExerciseSetHeader(
                                        setLabel = setLabel,
                                        maxValue = maxValue.toString(),
                                        actionButton = {
                                            if (setUiState.isCompleted) {
                                                if (isPullUpsExercise && isEditingEnabled) {
                                                    OutlinedButton(
                                                        onClick = {},
                                                        enabled = true,
                                                        modifier = Modifier
                                                            .height(SetActionButtonHeight)
                                                            .pointerInput(setState.setNumber) {
                                                                detectTapGestures(
                                                                    onLongPress = {
                                                                        focusManager.clearFocus(force = true)
                                                                        sessionUiState.reactivateSet(
                                                                            exerciseId = exerciseId,
                                                                            setNumber = setState.setNumber
                                                                        )
                                                                    }
                                                                )
                                                            },
                                                        colors = ButtonDefaults.outlinedButtonColors(
                                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                        ),
                                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                                            horizontal = 10.dp,
                                                            vertical = 0.dp
                                                        )
                                                    ) {
                                                        Text(
                                                            text = stringResource(R.string.exercise_set_completed_button),
                                                            maxLines = 1,
                                                            softWrap = false,
                                                            style = MaterialTheme.typography.labelMedium
                                                        )
                                                    }
                                                } else {
                                                    OutlinedButton(
                                                        onClick = {},
                                                        enabled = false,
                                                        modifier = Modifier.height(SetActionButtonHeight),
                                                        colors = ButtonDefaults.outlinedButtonColors(
                                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                        ),
                                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                                            horizontal = 10.dp,
                                                            vertical = 0.dp
                                                        )
                                                    ) {
                                                        Text(
                                                            text = stringResource(R.string.exercise_set_completed_button),
                                                            maxLines = 1,
                                                            softWrap = false,
                                                            style = MaterialTheme.typography.labelMedium
                                                        )
                                                    }
                                                }
                                            } else {
                                                val completeButtonEnabled = isEditingEnabled &&
                                                    !setUiState.isCompleted &&
                                                    savingSetNumber == null &&
                                                    !isFinishingAllSets
                                                OutlinedButton(
                                                    onClick = {
                                                        val activeWorkout = sessionUiState.activeWorkout
                                                            ?: return@OutlinedButton
                                                        val setToPersist = setState
                                                        savingSetNumber = setState.setNumber
                                                        coroutineScope.launch {
                                                            runCatching {
                                                                withContext(Dispatchers.IO) {
                                                                    persistExerciseSet(
                                                                        database = database,
                                                                        workoutSessionId = activeWorkout.sessionId,
                                                                        set = setToPersist,
                                                                        replaceExisting = true
                                                                    )
                                                                }
                                                            }.onSuccess {
                                                                val savedValue = when (exerciseInputType) {
                                                                    ExerciseInputType.REPS -> setToPersist.reps ?: 0
                                                                    ExerciseInputType.TIME_SECONDS ->
                                                                        setToPersist.additionalValue?.toInt() ?: 0
                                                                }
                                                                maxValuesBySetNumber = maxValuesBySetNumber
                                                                    .toMutableMap()
                                                                    .apply {
                                                                        val currentMax = get(setState.setNumber) ?: 0
                                                                        if (savedValue > currentMax) {
                                                                            put(setState.setNumber, savedValue)
                                                                        }
                                                                    }
                                                                val completionResult = sessionUiState.completeSet(
                                                                    exerciseId = exerciseId,
                                                                    setNumber = setState.setNumber
                                                                )
                                                                focusManager.clearFocus(force = true)
                                                                if (completionResult?.isExerciseCompleted == true) {
                                                                    finishExerciseOnce()
                                                                }
                                                            }.onFailure {
                                                                Toast.makeText(
                                                                    context,
                                                                    context.getString(
                                                                        R.string.exercise_set_save_error
                                                                    ),
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                            }
                                                            savingSetNumber = null
                                                        }
                                                    },
                                                    enabled = completeButtonEnabled,
                                                    modifier = Modifier.height(SetActionButtonHeight),
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                    ),
                                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                                        horizontal = 10.dp,
                                                        vertical = 0.dp
                                                    )
                                                ) {
                                                    Text(
                                                        text = if (savingSetNumber == setState.setNumber) {
                                                            stringResource(R.string.exercise_set_saving_button)
                                                        } else {
                                                            stringResource(R.string.exercise_complete_set_button)
                                                        },
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        style = MaterialTheme.typography.labelMedium
                                                    )
                                                }
                                            }
                                        }
                                    )
                                    ExerciseSetDraftRow(
                                        note = setState.note,
                                        currentValue = when (exerciseInputType) {
                                            ExerciseInputType.TIME_SECONDS -> setState.additionalValue.toDisplayString()
                                            ExerciseInputType.REPS -> setState.reps.toDisplayString()
                                        },
                                        onNoteChange = { updatedNote ->
                                            updateSetStates(
                                                currentStates = setStates.map(ExerciseSetUiState::set),
                                                index = index,
                                                exerciseId = exerciseId,
                                                sessionUiState = sessionUiState
                                            ) { updated ->
                                                updated.copy(note = updatedNote)
                                            }
                                        },
                                        onCurrentValueChange = { updatedValue ->
                                            when (exerciseInputType) {
                                                ExerciseInputType.TIME_SECONDS -> {
                                                    updateSetStates(
                                                        currentStates = setStates.map(ExerciseSetUiState::set),
                                                        index = index,
                                                        exerciseId = exerciseId,
                                                        sessionUiState = sessionUiState
                                                    ) { updated ->
                                                        updated.copy(
                                                            additionalValue = updatedValue.toIntOrNull()?.toDouble()
                                                        )
                                                    }
                                                }

                                                ExerciseInputType.REPS -> {
                                                    updateSetStates(
                                                        currentStates = setStates.map(ExerciseSetUiState::set),
                                                        index = index,
                                                        exerciseId = exerciseId,
                                                        sessionUiState = sessionUiState
                                                    ) { updated ->
                                                        updated.copy(reps = updatedValue.toIntOrNull())
                                                    }
                                                }
                                            }
                                        },
                                        onDecrement = {
                                            when (exerciseInputType) {
                                                ExerciseInputType.TIME_SECONDS -> {
                                                    updateSetStates(
                                                        currentStates = setStates.map(ExerciseSetUiState::set),
                                                        index = index,
                                                        exerciseId = exerciseId,
                                                        sessionUiState = sessionUiState
                                                    ) { updated ->
                                                        updated.copy(
                                                            additionalValue = decrementDoubleValue(updated.additionalValue)
                                                        )
                                                    }
                                                }

                                                ExerciseInputType.REPS -> {
                                                    updateSetStates(
                                                        currentStates = setStates.map(ExerciseSetUiState::set),
                                                        index = index,
                                                        exerciseId = exerciseId,
                                                        sessionUiState = sessionUiState
                                                    ) { updated ->
                                                        updated.copy(reps = decrementIntValue(updated.reps))
                                                    }
                                                }
                                            }
                                        },
                                        onIncrement = {
                                            when (exerciseInputType) {
                                                ExerciseInputType.TIME_SECONDS -> {
                                                    updateSetStates(
                                                        currentStates = setStates.map(ExerciseSetUiState::set),
                                                        index = index,
                                                        exerciseId = exerciseId,
                                                        sessionUiState = sessionUiState
                                                    ) { updated ->
                                                        updated.copy(
                                                            additionalValue = incrementDoubleValue(updated.additionalValue)
                                                        )
                                                    }
                                                }

                                                ExerciseInputType.REPS -> {
                                                    updateSetStates(
                                                        currentStates = setStates.map(ExerciseSetUiState::set),
                                                        index = index,
                                                        exerciseId = exerciseId,
                                                        sessionUiState = sessionUiState
                                                    ) { updated ->
                                                        updated.copy(reps = incrementIntValue(updated.reps))
                                                    }
                                                }
                                            }
                                        },
                                        enabled = isSetEditable,
                                        isReadOnly = !isSetEditable
                                    )
                                    if (isSetEditable || setState.parameterOverrideValue != null) {
                                        ExerciseParameterOverrideRow(
                                            label = exerciseParameterLabel,
                                            unit = exerciseParameterUnit,
                                            value = setState.parameterOverrideValue.toParameterDisplayString(),
                                            hasValue = setState.parameterOverrideValue != null,
                                            enabled = isSetEditable,
                                            onEnableOverride = {
                                                val fallbackValue = sharedParameterValue ?: 0.0
                                                updateSetStates(
                                                    currentStates = setStates.map(ExerciseSetUiState::set),
                                                    index = index,
                                                    exerciseId = exerciseId,
                                                    sessionUiState = sessionUiState
                                                ) { updated ->
                                                    updated.copy(parameterOverrideValue = fallbackValue)
                                                }
                                            },
                                            onDisableOverride = {
                                                updateSetStates(
                                                    currentStates = setStates.map(ExerciseSetUiState::set),
                                                    index = index,
                                                    exerciseId = exerciseId,
                                                    sessionUiState = sessionUiState
                                                ) { updated ->
                                                    updated.copy(parameterOverrideValue = null)
                                                }
                                            },
                                            onValueChange = { updatedValue ->
                                                updateSetStates(
                                                    currentStates = setStates.map(ExerciseSetUiState::set),
                                                    index = index,
                                                    exerciseId = exerciseId,
                                                    sessionUiState = sessionUiState
                                                ) { updated ->
                                                    updated.copy(
                                                        parameterOverrideValue = updatedValue.toDecimalOrNull()
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.exercise_progress_title),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                    ExerciseProgressChartPanel(
                        entries = filteredExerciseProgressSeries,
                        range = exerciseProgressRange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        onSizeChanged = { _: androidx.compose.ui.unit.IntSize -> },
                        valueLabelFormatter = exerciseAnalyticsValueLabelFormatter
                    )
                    OutlinedButton(
                        onClick = { openExerciseRangeSheet() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.exercise_progress_range_label,
                                exerciseProgressRangeMode.displayLabel(context)
                            ),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (showExerciseRangeSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showExerciseRangeSheet = false },
                    sheetState = exerciseRangeSheetState
                ) {
                    ExerciseScreenRangeSheet(
                        selectedMode = exerciseProgressDraftRangeMode,
                        startEpochDay = exerciseProgressDraftStartEpochDay,
                        endEpochDay = exerciseProgressDraftEndEpochDay,
                        dateFormatter = fullDateFormatter,
                        onPresetSelected = { preset ->
                            exerciseProgressDraftRangeMode = preset
                            if (preset != ExerciseProgressRangeMode.Custom) {
                                val resolvedRange = resolveExerciseProgressRange(
                                    records = exerciseProgressEntries,
                                    mode = preset,
                                    currentRange = exerciseProgressRange
                                )
                                exerciseProgressDraftStartEpochDay = resolvedRange.startEpochDay
                                exerciseProgressDraftEndEpochDay = resolvedRange.endEpochDay
                            }
                        },
                        onStartDateClick = {
                            exerciseProgressDraftRangeMode = ExerciseProgressRangeMode.Custom
                            openExerciseDatePicker(
                                context = context,
                                initialEpochDay = exerciseProgressDraftStartEpochDay,
                                onDatePicked = { selectedEpochDay -> 
                                    exerciseProgressDraftStartEpochDay = selectedEpochDay
                                }
                            )
                        },
                        onEndDateClick = {
                            exerciseProgressDraftRangeMode = ExerciseProgressRangeMode.Custom
                            openExerciseDatePicker(
                                context = context,
                                initialEpochDay = exerciseProgressDraftEndEpochDay,
                                onDatePicked = { selectedEpochDay ->
                                    exerciseProgressDraftEndEpochDay = selectedEpochDay
                                }
                            )
                        },
                        onConfirm = {
                            applyExerciseRangeSelection(
                                mode = exerciseProgressDraftRangeMode,
                                startEpochDay = exerciseProgressDraftStartEpochDay,
                                endEpochDay = exerciseProgressDraftEndEpochDay
                            )
                            showExerciseRangeSheet = false
                        },
                        onDismiss = { showExerciseRangeSheet = false }
                    )
                }
            }

            if (showFinishExerciseConfirmation) {
                AlertDialog(
                    onDismissRequest = { showFinishExerciseConfirmation = false },
                    title = { Text(text = stringResource(R.string.exercise_finish_all_sets_dialog_title)) },
                    text = { Text(text = stringResource(R.string.exercise_finish_all_sets_dialog_message)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val activeWorkout = sessionUiState.activeWorkout ?: return@TextButton
                                val setsToPersist = setStates.map { currentSetUiState ->
                                    if (currentSetUiState.isCompleted || currentSetUiState.set.hasEnteredValues()) {
                                        currentSetUiState.set
                                    } else {
                                        currentSetUiState.set.zeroedFor(exerciseInputType)
                                    }
                                }
                                showFinishExerciseConfirmation = false
                                isFinishingAllSets = true
                                focusManager.clearFocus(force = true)
                                coroutineScope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            setsToPersist.forEach { setToPersist ->
                                                persistExerciseSet(
                                                    database = database,
                                                    workoutSessionId = activeWorkout.sessionId,
                                                    set = setToPersist,
                                                    replaceExisting = true
                                                )
                                            }
                                        }
                                    }.onSuccess {
                                        sessionUiState.updateExerciseSets(
                                            exerciseId = exerciseId,
                                            sets = setsToPersist,
                                            allowCompletedOverride = true
                                        )
                                        setStates.forEach { currentSetUiState ->
                                            if (currentSetUiState.isCompleted) return@forEach
                                            sessionUiState.completeSet(
                                                exerciseId = exerciseId,
                                                setNumber = currentSetUiState.set.setNumber
                                            )
                                        }
                                        finishExerciseOnce()
                                    }.onFailure {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.exercise_set_save_error),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    isFinishingAllSets = false
                                }
                            }
                        ) {
                            Text(text = stringResource(R.string.exercise_finish_all_sets_dialog_confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showFinishExerciseConfirmation = false }) {
                            Text(text = stringResource(R.string.exercise_finish_all_sets_dialog_cancel))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ExerciseScreenRangeSheet(
    selectedMode: ExerciseProgressRangeMode,
    startEpochDay: Long,
    endEpochDay: Long,
    dateFormatter: DateTimeFormatter,
    onPresetSelected: (ExerciseProgressRangeMode) -> Unit,
    onStartDateClick: () -> Unit,
    onEndDateClick: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val hasInvalidManualRange = startEpochDay > endEpochDay
    val selectedRangeLabel = when (selectedMode) {
        ExerciseProgressRangeMode.SevenDays -> stringResource(R.string.exercise_progress_range_7_days)
        ExerciseProgressRangeMode.ThirtyDays -> stringResource(R.string.exercise_progress_range_30_days)
        ExerciseProgressRangeMode.All -> stringResource(R.string.exercise_progress_range_all)
        ExerciseProgressRangeMode.Custom -> stringResource(R.string.exercise_progress_range_custom)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.exercise_progress_range_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(
                    R.string.exercise_progress_range_selected,
                    selectedRangeLabel
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExerciseScreenRangePresetButton(
                    label = stringResource(R.string.exercise_progress_range_7_days),
                    selected = selectedMode == ExerciseProgressRangeMode.SevenDays,
                    modifier = Modifier.weight(1f),
                    onClick = { onPresetSelected(ExerciseProgressRangeMode.SevenDays) }
                )
                ExerciseScreenRangePresetButton(
                    label = stringResource(R.string.exercise_progress_range_30_days),
                    selected = selectedMode == ExerciseProgressRangeMode.ThirtyDays,
                    modifier = Modifier.weight(1f),
                    onClick = { onPresetSelected(ExerciseProgressRangeMode.ThirtyDays) }
                )
                ExerciseScreenRangePresetButton(
                    label = stringResource(R.string.exercise_progress_range_all),
                    selected = selectedMode == ExerciseProgressRangeMode.All,
                    modifier = Modifier.weight(1f),
                    onClick = { onPresetSelected(ExerciseProgressRangeMode.All) }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.exercise_progress_range_custom_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onStartDateClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            R.string.exercise_progress_range_from,
                            exerciseScreenSafeLocalDateOfEpochDay(startEpochDay).format(dateFormatter)
                        )
                    )
                }
                OutlinedButton(
                    onClick = onEndDateClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            R.string.exercise_progress_range_to,
                            exerciseScreenSafeLocalDateOfEpochDay(endEpochDay).format(dateFormatter)
                        )
                    )
                }
                if (hasInvalidManualRange) {
                    Text(
                        text = stringResource(R.string.exercise_progress_range_invalid_note),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.exercise_progress_range_cancel))
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.exercise_progress_range_apply))
            }
        }
    }
}

@Composable
private fun ExerciseScreenRangePresetButton(
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
private fun ExerciseParameterSharedRow(
    label: String,
    unit: String,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactDecimalField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .height(CompactFieldHeight),
                enabled = enabled,
                isReadOnly = !enabled,
                onDone = { }
            )
            if (unit.isNotBlank()) {
                Text(
                    text = unit,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun ExerciseParameterOverrideRow(
    label: String,
    unit: String,
    value: String,
    hasValue: Boolean,
    enabled: Boolean,
    onEnableOverride: () -> Unit,
    onDisableOverride: () -> Unit,
    onValueChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.exercise_parameter_override_label, label),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            if (!hasValue) {
                TextButton(
                    onClick = onEnableOverride,
                    enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.exercise_parameter_override_enable),
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        if (hasValue) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactDecimalField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .weight(1f)
                        .height(CompactFieldHeight),
                    enabled = enabled,
                    isReadOnly = !enabled,
                    onDone = { }
                )
                if (unit.isNotBlank()) {
                    Text(
                        text = unit,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                TextButton(
                    onClick = onDisableOverride,
                    enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.exercise_parameter_override_clear),
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseSetDraftRow(
    note: String,
    currentValue: String,
    onNoteChange: (String) -> Unit,
    onCurrentValueChange: (String) -> Unit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    enabled: Boolean,
    isReadOnly: Boolean
) {
    val focusManager = LocalFocusManager.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SetBottomRowHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompactNoteField(
            value = note,
            onValueChange = onNoteChange,
            modifier = Modifier
                .weight(1f)
                .height(CompactFieldHeight),
            enabled = enabled,
            isReadOnly = isReadOnly,
            placeholder = stringResource(R.string.exercise_note_label),
            onDone = { focusManager.clearFocus(force = true) }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Row(
            modifier = Modifier.width(SetCurrentValueAreaWidth),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactStepperButton(
                symbol = stringResource(R.string.exercise_decrement_symbol),
                enabled = enabled,
                onClick = onDecrement
            )
            CompactNumberField(
                value = currentValue,
                onValueChange = { updatedValue ->
                    if (updatedValue.all(Char::isDigit)) {
                        onCurrentValueChange(updatedValue)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(CompactFieldHeight),
                enabled = enabled,
                isReadOnly = isReadOnly,
                onDone = { focusManager.clearFocus(force = true) }
            )
            CompactStepperButton(
                symbol = stringResource(R.string.exercise_increment_symbol),
                enabled = enabled,
                onClick = onIncrement
            )
        }
    }
}

@Composable
private fun ExerciseSetHeader(
    setLabel: String,
    maxValue: String,
    actionButton: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SetHeaderHeight),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = setLabel,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            actionButton()
            Text(
                text = "${stringResource(R.string.exercise_max_label)}: $maxValue",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CompactNoteField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    isReadOnly: Boolean,
    placeholder: String,
    onDone: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val textColor = if (enabled || isReadOnly) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = true,
        readOnly = isReadOnly,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(color = textColor),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        interactionSource = interactionSource,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            CompactFieldContainer(
                enabled = enabled,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (!isFocused && value.isNotEmpty()) {
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (isFocused || value.isEmpty()) 1f else 0f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        innerTextField()
                    }
                }
            }
        }
    )
}

@Composable
private fun CompactNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    isReadOnly: Boolean,
    onDone: () -> Unit
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = true,
        readOnly = isReadOnly,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(
            color = if (enabled || isReadOnly) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            CompactFieldContainer(
                enabled = enabled,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    innerTextField()
                }
            }
        }
    )
}

@Composable
private fun CompactDecimalField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    isReadOnly: Boolean,
    onDone: () -> Unit
) {
    BasicTextField(
        value = value,
        onValueChange = { updatedValue ->
            onValueChange(updatedValue.sanitizeDecimalInput())
        },
        modifier = modifier,
        enabled = true,
        readOnly = isReadOnly,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(
            color = if (enabled || isReadOnly) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            CompactFieldContainer(
                enabled = enabled,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    innerTextField()
                }
            }
        }
    )
}

@Composable
private fun CompactFieldContainer(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        tonalElevation = 0.dp,
        shape = MaterialTheme.shapes.medium,
        color = if (enabled) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline
        )
    ) {
        content()
    }
}

@Composable
private fun CompactStepperButton(
    symbol: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.size(SetStepperButtonSize),
        tonalElevation = 0.dp,
        shape = MaterialTheme.shapes.medium,
        color = if (enabled) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = symbol,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

private fun updateSetStates(
    currentStates: List<PreparedWorkoutSessionSet>,
    index: Int,
    exerciseId: String,
    sessionUiState: WorkoutSessionUiState,
    allowCompletedOverride: Boolean = false,
    transform: (PreparedWorkoutSessionSet) -> PreparedWorkoutSessionSet
): List<PreparedWorkoutSessionSet> {
    val updatedStates = currentStates.toMutableList().also { states ->
        states[index] = transform(states[index])
    }
    sessionUiState.updateExerciseSets(exerciseId, updatedStates, allowCompletedOverride)
    return updatedStates
}

private fun persistExerciseSet(
    database: TrenerDatabase,
    workoutSessionId: Long,
    set: PreparedWorkoutSessionSet,
    replaceExisting: Boolean
) {
    if (replaceExisting) {
        val updatedRows = updatePersistedExerciseSet(database, workoutSessionId, set)
        if (updatedRows > 0) return
    }

    database.workoutSessionSetDao().insertWorkoutSessionSet(
        set.toWorkoutSessionSetEntity(workoutSessionId)
    )
}

private suspend fun loadLatestExerciseSessionRepsTotal(
    database: TrenerDatabase,
    exerciseId: String
): Int {
    return database.workoutSessionSetDao()
        .getLatestSetsForExercise(exerciseId)
        .sumOf { set -> set.reps ?: 0 }
}

private fun updatePersistedExerciseSet(
    database: TrenerDatabase,
    workoutSessionId: Long,
    set: PreparedWorkoutSessionSet
): Int {
    val statement = database.openHelper.writableDatabase.compileStatement(
        """
        UPDATE workout_session_sets
        SET reps = ?, weight = ?, additionalValue = ?, parameterValue = ?, parameterOverrideValue = ?, flag = ?, note = ?
        WHERE workoutSessionId = ? AND exerciseId = ? AND setNumber = ?
        """.trimIndent()
    )

    if (set.reps != null) statement.bindLong(1, set.reps.toLong()) else statement.bindNull(1)
    if (set.weight != null) statement.bindDouble(2, set.weight) else statement.bindNull(2)
    if (set.additionalValue != null) statement.bindDouble(3, set.additionalValue) else statement.bindNull(3)
    if (set.parameterValue != null) statement.bindDouble(4, set.parameterValue) else statement.bindNull(4)
    if (set.parameterOverrideValue != null) statement.bindDouble(5, set.parameterOverrideValue) else statement.bindNull(5)
    when (set.flag) {
        true -> statement.bindLong(6, 1)
        false -> statement.bindLong(6, 0)
        null -> statement.bindNull(6)
    }
    statement.bindString(7, set.note)
    statement.bindLong(8, workoutSessionId)
    statement.bindString(9, set.exerciseId)
    statement.bindLong(10, set.setNumber.toLong())
    return statement.executeUpdateDelete()
}

private fun Int?.toDisplayString(): String = this?.toString() ?: ""

private fun Double?.toDisplayString(): String = this?.toInt()?.toString() ?: ""

private fun Double?.toParameterDisplayString(): String = when {
    this == null -> ""
    this == this.toLong().toDouble() -> this.toLong().toString()
    else -> this.toString().trimEnd('0').trimEnd('.')
}

private fun String.toDecimalOrNull(): Double? {
    val normalized = sanitizeDecimalInput()
    return normalized.takeIf(String::isNotBlank)?.toDoubleOrNull()
}

private fun String.sanitizeDecimalInput(): String {
    val normalized = replace(',', '.')
    val builder = StringBuilder()
    var decimalSeparatorSeen = false

    normalized.forEach { char ->
        when {
            char.isDigit() -> builder.append(char)
            char == '.' && !decimalSeparatorSeen -> {
                builder.append(char)
                decimalSeparatorSeen = true
            }
        }
    }

    val sanitized = builder.toString()
    return when {
        sanitized == "." -> ""
        sanitized.startsWith('.') -> "0$sanitized"
        else -> sanitized
    }
}

private fun incrementIntValue(value: Int?): Int = (value ?: 0) + 1

private fun decrementIntValue(value: Int?): Int = ((value ?: 0) - 1).coerceAtLeast(0)

private fun incrementDoubleValue(value: Double?): Double = ((value?.toInt() ?: 0) + 1).toDouble()

private fun decrementDoubleValue(value: Double?): Double = ((value?.toInt() ?: 0) - 1).coerceAtLeast(0).toDouble()

private fun PreparedWorkoutSessionSet.zeroedFor(exerciseInputType: ExerciseInputType): PreparedWorkoutSessionSet {
    return when (exerciseInputType) {
        ExerciseInputType.REPS -> copy(reps = 0)
        ExerciseInputType.TIME_SECONDS -> copy(additionalValue = 0.0)
    }
}

private fun openExerciseDatePicker(
    context: android.content.Context,
    initialEpochDay: Long,
    onDatePicked: (Long) -> Unit
) {
    val initialDate = exerciseScreenSafeLocalDateOfEpochDay(initialEpochDay)
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

private fun exerciseScreenSafeLocalDateOfEpochDay(epochDay: Long): LocalDate {
    return runCatching { LocalDate.ofEpochDay(epochDay) }
        .getOrElse { LocalDate.now() }
}

private fun resolveSelectedExerciseAnalyticsRange(
    metric: ExerciseAnalyticsMetric,
    repetitions: List<ComparisonSeriesPoint>,
    parameter: List<ComparisonSeriesPoint>,
    mode: ExerciseProgressRangeMode,
    currentRange: ExerciseProgressRange
): ExerciseProgressRange {
    val records = when (metric) {
        ExerciseAnalyticsMetric.Repetitions -> repetitions
        ExerciseAnalyticsMetric.Parameter -> parameter
    }
    return resolveComparisonSeriesRange(
        records = records,
        mode = mode,
        currentRange = currentRange
    )
}


private const val CompletedSetAlpha = 0.6f
private val SetHeaderHeight = 30.dp
private val SetBottomRowHeight = 32.dp
private val SetActionButtonHeight = 30.dp
private val CompactFieldHeight = 32.dp
private val SetStepperButtonSize = 26.dp
private val SetCurrentValueAreaWidth = 112.dp
