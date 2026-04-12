package com.example.trener

import android.widget.Toast
import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trener.data.local.TrenerDatabase
import com.example.trener.data.local.TrenerDatabaseProvider
import com.example.trener.data.local.entity.WorkoutSessionSetEntity
import com.example.trener.domain.workout.PreparedWorkoutSessionSet
import com.example.trener.domain.workout.loadPersistedMaxValuesBySetNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseScreen(
    exerciseId: String,
    databaseRefreshToken: Int,
    historyRefreshToken: Int,
    sessionUiState: WorkoutSessionUiState,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val database = remember(context.applicationContext) {
        TrenerDatabaseProvider.getInstance(context.applicationContext)
    }
    val coroutineScope = rememberCoroutineScope()
    val setCount = sessionUiState.getRequiredSetCount(exerciseId)
    val setLabels = List(setCount) { index ->
        context.getString(R.string.exercise_set_label, index + 1)
    }
    val exerciseDefinition = remember(exerciseId) { getExerciseDefinition(exerciseId) }
    val exerciseName = exerciseDefinition?.let { context.getString(it.labelResId) } ?: exerciseId
    val exerciseInputType = exerciseDefinition?.inputType ?: ExerciseInputType.REPS
    val setStates = sessionUiState.getExerciseSetUiStates(exerciseId, setCount)
    val currentExerciseTotalReps by remember(setStates) {
        derivedStateOf {
            setStates.sumOf { setUiState -> setUiState.set.reps ?: 0 }
        }
    }
    val isPullUpsExercise = exerciseId == "pull_ups_ui"
    val areAllVisibleSetsCompleted = setStates.isNotEmpty() && setStates.all(ExerciseSetUiState::isCompleted)
    val isEditingEnabled = sessionUiState.isExerciseInActiveWorkout(exerciseId) && !sessionUiState.isSaving
    val focusManager = LocalFocusManager.current
    var savingSetNumber by remember(exerciseId) { mutableStateOf<Int?>(null) }
    var isFinishingAllSets by remember(exerciseId) { mutableStateOf(false) }
    var showFinishExerciseConfirmation by rememberSaveable(exerciseId) { mutableStateOf(false) }
    var historyLoaded by rememberSaveable(exerciseId) { mutableStateOf(false) }
    var previousExerciseTotalReps by remember(exerciseId) { mutableIntStateOf(0) }
    var exerciseProgressEntries by remember(exerciseId) {
        mutableStateOf<List<ExerciseProgressPoint>>(emptyList())
    }
    var exerciseProgressRange by remember(exerciseId) {
        mutableStateOf(
            ExerciseProgressRange(
                startEpochDay = LocalDate.now().minusDays(6).toEpochDay(),
                endEpochDay = LocalDate.now().toEpochDay()
            )
        )
    }
    var maxValuesBySetNumber by remember(exerciseId, exerciseInputType) {
        mutableStateOf<Map<Int, Int>>(emptyMap())
    }

    LaunchedEffect(exerciseId, exerciseInputType, isEditingEnabled, historyLoaded, setCount) {
        if (historyLoaded || !isEditingEnabled) return@LaunchedEffect

        val previousSets = withContext(Dispatchers.IO) {
            List(setCount) { index ->
                database.workoutSessionSetDao().getLatestSetForExerciseAndNumber(
                    exerciseId = exerciseId,
                    setNumber = index + 1
                )?.let { previousSet ->
                    PreparedWorkoutSessionSet(
                        exerciseId = exerciseId,
                        setNumber = index + 1,
                        reps = previousSet.reps,
                        weight = previousSet.weight,
                        additionalValue = previousSet.additionalValue,
                        flag = previousSet.flag,
                        note = previousSet.note
                    )
                } ?: PreparedWorkoutSessionSet(
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
        exerciseProgressEntries = loadedExerciseProgressEntries
        exerciseProgressRange = resolveExerciseProgressRange(
            records = loadedExerciseProgressEntries,
            mode = ExerciseProgressRangeMode.All,
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
                if (isPullUpsExercise && areAllVisibleSetsCompleted) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .heightIn(min = 36.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = stringResource(R.string.exercise_completed_label),
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = { showFinishExerciseConfirmation = true },
                        enabled = isEditingEnabled && savingSetNumber == null && !isFinishingAllSets,
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
                                    .height(SetCardHeight)
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
                                                                if (completionResult?.isExerciseCompleted == true && !isPullUpsExercise) {
                                                                    onFinished()
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
                        entries = exerciseProgressEntries,
                        range = exerciseProgressRange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        onSizeChanged = { _: androidx.compose.ui.unit.IntSize -> }
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
                                        if (!isPullUpsExercise) {
                                            onFinished()
                                        }
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
        WorkoutSessionSetEntity(
            workoutSessionId = workoutSessionId,
            exerciseId = set.exerciseId,
            setNumber = set.setNumber,
            reps = set.reps,
            weight = set.weight,
            additionalValue = set.additionalValue,
            flag = set.flag,
            note = set.note
        )
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
        SET reps = ?, weight = ?, additionalValue = ?, flag = ?, note = ?
        WHERE workoutSessionId = ? AND exerciseId = ? AND setNumber = ?
        """.trimIndent()
    )

    if (set.reps != null) statement.bindLong(1, set.reps.toLong()) else statement.bindNull(1)
    if (set.weight != null) statement.bindDouble(2, set.weight) else statement.bindNull(2)
    if (set.additionalValue != null) statement.bindDouble(3, set.additionalValue) else statement.bindNull(3)
    when (set.flag) {
        true -> statement.bindLong(4, 1)
        false -> statement.bindLong(4, 0)
        null -> statement.bindNull(4)
    }
    statement.bindString(5, set.note)
    statement.bindLong(6, workoutSessionId)
    statement.bindString(7, set.exerciseId)
    statement.bindLong(8, set.setNumber.toLong())
    return statement.executeUpdateDelete()
}

private fun Int?.toDisplayString(): String = this?.toString() ?: ""

private fun Double?.toDisplayString(): String = this?.toInt()?.toString() ?: ""

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

/*

private suspend fun loadExerciseProgressEntries(
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
private fun ExerciseProgressRangeSheet(
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
                        text = stringResource(R.string.exercise_progress_range_from, formatExerciseProgressDate(startEpochDay)),
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
                        text = stringResource(R.string.exercise_progress_range_to, formatExerciseProgressDate(endEpochDay)),
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
    modifier: Modifier = Modifier,
    onSizeChanged: (androidx.compose.ui.unit.IntSize) -> Unit
) {
    val emptyStateColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
    val strokeColor = MaterialTheme.colorScheme.primary
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
    val pointSurfaceColor = MaterialTheme.colorScheme.surface

    Canvas(
        modifier = modifier.onSizeChanged(onSizeChanged)
    ) {
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

private enum class ExerciseProgressRangeMode {
    SevenDays,
    ThirtyDays,
    All,
    Custom
}

private data class ExerciseProgressRange(
    val startEpochDay: Long,
    val endEpochDay: Long
) {
    val dayCount: Long
        get() = (endEpochDay - startEpochDay + 1).coerceAtLeast(1L)
}

private data class ExerciseProgressPoint(
    val entryDateEpochDay: Long,
    val totalReps: Int
)

private data class ExerciseProgressSessionPoint(
    val sessionId: Long,
    val entryDateEpochDay: Long,
    val totalReps: Int
)

private data class ExerciseProgressAxisTick(
    val value: Double,
    val step: Double
)

private fun ExerciseProgressRangeMode.displayLabel(context: android.content.Context): String {
    return when (this) {
        ExerciseProgressRangeMode.SevenDays -> context.getString(R.string.exercise_progress_range_7_days)
        ExerciseProgressRangeMode.ThirtyDays -> context.getString(R.string.exercise_progress_range_30_days)
        ExerciseProgressRangeMode.All -> context.getString(R.string.exercise_progress_range_all)
        ExerciseProgressRangeMode.Custom -> context.getString(R.string.exercise_progress_range_custom)
    }
}

private fun resolveExerciseProgressRange(
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

private fun resolveClampedExerciseProgressRange(
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

private fun normalizeExerciseProgressRange(
    startEpochDay: Long,
    endEpochDay: Long
): ExerciseProgressRange {
    return ExerciseProgressRange(min(startEpochDay, endEpochDay), max(startEpochDay, endEpochDay))
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

private fun buildExerciseYAxisTicks(
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

private fun formatExerciseAxisLabel(value: Double, step: Double): String {
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

private fun formatExerciseProgressDate(epochDay: Long): String {
    return safeLocalDateOfEpochDay(epochDay).format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
}

*/

private const val CompletedSetAlpha = 0.6f
private val SetCardHeight = 84.dp
private val SetHeaderHeight = 30.dp
private val SetBottomRowHeight = 32.dp
private val SetActionButtonHeight = 30.dp
private val CompactFieldHeight = 32.dp
private val SetStepperButtonSize = 26.dp
private val SetCurrentValueAreaWidth = 112.dp
