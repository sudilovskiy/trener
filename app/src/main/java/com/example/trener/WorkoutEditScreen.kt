package com.example.trener

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.trener.data.local.entity.WorkoutSessionEntity
import com.example.trener.data.local.entity.WorkoutSessionSetEntity
import com.example.trener.domain.workout.GetWorkoutSessionWithSets
import com.example.trener.domain.workout.ReplaceWorkoutSessionSets
import com.example.trener.domain.workout.WorkoutSessionWithSets
import com.example.trener.domain.workout.loadPersistedMaxValuesBySetNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutEditScreen(
    databaseRefreshToken: Int,
    programRefreshToken: Int,
    sessionId: Long,
    activeWorkoutSessionId: Long?,
    onSaveSuccess: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val database = rememberTrenerDatabase(databaseRefreshToken)
    val getWorkoutSessionWithSets = remember(database) { GetWorkoutSessionWithSets(database) }
    val replaceWorkoutSessionSets = remember(database) { ReplaceWorkoutSessionSets(database) }
    val coroutineScope = rememberCoroutineScope()

    var editState by remember { mutableStateOf<WorkoutEditState?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(
        sessionId,
        activeWorkoutSessionId,
        databaseRefreshToken,
        programRefreshToken,
        getWorkoutSessionWithSets
    ) {
        isLoading = true
        loadError = null
        editState = null

        if (sessionId <= 0L) {
            loadError = context.getString(R.string.workout_edit_invalid_id)
            isLoading = false
            return@LaunchedEffect
        }

        if (activeWorkoutSessionId == sessionId) {
            loadError = context.getString(R.string.workout_edit_active_blocked)
            isLoading = false
            return@LaunchedEffect
        }

        runCatching {
            withContext(Dispatchers.IO) {
                getWorkoutSessionWithSets(sessionId)?.toWorkoutEditState(context)
            }
        }.onSuccess { state ->
            if (state == null) {
                loadError = context.getString(R.string.workout_edit_not_found)
            } else {
                editState = state
            }
        }.onFailure { throwable ->
            Log.e(logTag, "Failed to load workout edit state", throwable)
            loadError = context.getString(R.string.workout_edit_load_error)
        }

        isLoading = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.workout_edit_title)) }
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isLoading) {
                    item {
                        Text(
                            text = stringResource(R.string.loading),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                loadError?.let { error ->
                    item {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                editState?.let { state ->
                    item {
                        SectionCard(title = stringResource(R.string.workout_edit_metadata_title)) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = stringResource(
                                        R.string.date_label,
                                        state.session.toWorkoutDateText()
                                    )
                                )
                                Text(
                                    text = stringResource(
                                        R.string.duration_label,
                                        state.session.durationSeconds?.let { formatDuration(context, it) }
                                            ?: stringResource(R.string.not_specified)
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(
                                        R.string.workout_edit_day_label,
                                        state.session.trainingDay
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    items(
                        items = state.exercises,
                        key = { exercise -> exercise.exerciseId }
                    ) { exercise ->
                        SectionCard(title = exercise.title) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = stringResource(
                                            if (exercise.included) {
                                                R.string.workout_edit_exercise_present
                                            } else {
                                                R.string.workout_edit_exercise_absent
                                            }
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    TextButton(
                                        onClick = {
                                            editState = editState?.toggleExercisePresence(exercise.exerciseId)
                                        },
                                        enabled = !isSaving
                                    ) {
                                        Text(
                                            text = stringResource(
                                                if (exercise.included) {
                                                    R.string.workout_edit_remove_exercise
                                                } else {
                                                    R.string.workout_edit_add_exercise
                                                }
                                            )
                                        )
                                    }
                                }

                                if (exercise.included) {
                                    exercise.sets.forEach { set ->
                                        EditableWorkoutSetCard(
                                            exercise = exercise,
                                            set = set,
                                            onValueChange = { updatedValue ->
                                                editState = editState?.updateSetValue(
                                                    exerciseId = exercise.exerciseId,
                                                    setNumber = set.setNumber,
                                                    updatedValue = updatedValue
                                                )
                                            },
                                            onNoteChange = { updatedNote ->
                                                editState = editState?.updateSetNote(
                                                    exerciseId = exercise.exerciseId,
                                                    setNumber = set.setNumber,
                                                    updatedNote = updatedNote
                                                )
                                            },
                                            enabled = !isSaving
                                        )
                                    }
                                } else {
                                    Text(
                                        text = stringResource(R.string.workout_edit_exercise_removed_hint),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onCancel,
                                enabled = !isSaving,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = stringResource(R.string.workout_edit_cancel_button))
                            }

                            Button(
                                onClick = {
                                    val currentState = editState ?: return@Button
                                    if (isSaving) return@Button
                                    isSaving = true
                                    coroutineScope.launch {
                                        runCatching {
                                            val persistedSets = withContext(Dispatchers.IO) {
                                                currentState.toPersistedSets()
                                            }

                                            withContext(Dispatchers.IO) {
                                                replaceWorkoutSessionSets(
                                                    workoutSessionId = currentState.session.id,
                                                    sets = persistedSets
                                                )
                                            }

                                            withContext(Dispatchers.IO) {
                                                recomputePersistedMaxValues(
                                                    database = database,
                                                    exercises = currentState.exercises
                                                )
                                            }
                                        }.onSuccess {
                                            isSaving = false
                                            onSaveSuccess()
                                        }.onFailure { throwable ->
                                            Log.e(logTag, "Failed to save workout edit", throwable)
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.workout_edit_save_error),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                },
                                enabled = editState != null && !isSaving,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = if (isSaving) {
                                        stringResource(R.string.workout_edit_saving_button)
                                    } else {
                                        stringResource(R.string.workout_edit_save_button)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditableWorkoutSetCard(
    exercise: WorkoutEditExerciseState,
    set: WorkoutEditSetState,
    onValueChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.set_number_label, set.setNumber),
                style = MaterialTheme.typography.titleSmall
            )

            OutlinedTextField(
                value = set.valueText,
                onValueChange = { updatedValue ->
                    if (updatedValue.all(Char::isDigit)) {
                        onValueChange(updatedValue)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = true,
                label = {
                        Text(
                            text = stringResource(
                                if (exercise.inputType == ExerciseInputType.TIME_SECONDS) {
                                    R.string.exercise_time_seconds_label
                                } else {
                                    R.string.workout_edit_reps_label
                                }
                            )
                        )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                )
            )

            OutlinedTextField(
                value = set.note,
                onValueChange = onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                singleLine = true,
                label = { Text(text = stringResource(R.string.exercise_note_label)) }
            )
        }
    }
}

private data class WorkoutEditState(
    val session: WorkoutSessionEntity,
    val exercises: List<WorkoutEditExerciseState>
) {
    fun toggleExercisePresence(exerciseId: String): WorkoutEditState {
        return copy(
            exercises = exercises.map { exercise ->
                if (exercise.exerciseId == exerciseId) {
                    exercise.copy(included = !exercise.included)
                } else {
                    exercise
                }
            }
        )
    }

    fun updateSetValue(
        exerciseId: String,
        setNumber: Int,
        updatedValue: String
    ): WorkoutEditState {
        return copy(
            exercises = exercises.map { exercise ->
                if (exercise.exerciseId == exerciseId) {
                    exercise.copy(
                        sets = exercise.sets.map { set ->
                            if (set.setNumber == setNumber) {
                                set.copy(valueText = updatedValue)
                            } else {
                                set
                            }
                        }
                    )
                } else {
                    exercise
                }
            }
        )
    }

    fun updateSetNote(
        exerciseId: String,
        setNumber: Int,
        updatedNote: String
    ): WorkoutEditState {
        return copy(
            exercises = exercises.map { exercise ->
                if (exercise.exerciseId == exerciseId) {
                    exercise.copy(
                        sets = exercise.sets.map { set ->
                            if (set.setNumber == setNumber) {
                                set.copy(note = updatedNote)
                            } else {
                                set
                            }
                        }
                    )
                } else {
                    exercise
                }
            }
        )
    }

    fun toPersistedSets(): List<WorkoutSessionSetEntity> {
        return exercises
            .filter { it.included }
            .flatMap { exercise ->
                exercise.sets.map { set ->
                    WorkoutSessionSetEntity(
                        workoutSessionId = session.id,
                        exerciseId = exercise.exerciseId,
                        setNumber = set.setNumber,
                        reps = when (exercise.inputType) {
                            ExerciseInputType.REPS -> set.valueText.takeIf(String::isNotBlank)
                                ?.toIntOrNull()
                            ExerciseInputType.TIME_SECONDS -> set.original?.reps
                        },
                        weight = set.original?.weight,
                        additionalValue = when (exercise.inputType) {
                            ExerciseInputType.REPS -> set.original?.additionalValue
                            ExerciseInputType.TIME_SECONDS -> set.valueText.takeIf(String::isNotBlank)
                                ?.toDoubleOrNull()
                        },
                        parameterValue = set.original?.parameterValue,
                        parameterOverrideValue = set.original?.parameterOverrideValue,
                        flag = set.original?.flag,
                        note = set.note
                    )
                }
            }
    }
}

private data class WorkoutEditExerciseState(
    val exerciseId: String,
    val title: String,
    val inputType: ExerciseInputType,
    val included: Boolean,
    val sets: List<WorkoutEditSetState>
)

private data class WorkoutEditSetState(
    val setNumber: Int,
    val valueText: String,
    val note: String,
    val original: WorkoutSessionSetEntity?
)

private fun WorkoutSessionWithSets.toWorkoutEditState(
    context: android.content.Context
): WorkoutEditState {
    val setsByExercise = sets.groupBy(WorkoutSessionSetEntity::exerciseId)
    val exerciseIds = sets.map(WorkoutSessionSetEntity::exerciseId).distinct()

    return WorkoutEditState(
        session = session,
        exercises = exerciseIds.map { exerciseId ->
            val persistedSetsForExercise = setsByExercise[exerciseId].orEmpty()
            val definition = resolveWorkoutExerciseDefinitionFromHistory(
                exerciseId = exerciseId,
                persistedSets = persistedSetsForExercise
            )
            val persistedSets = setsByExercise[definition.exerciseId].orEmpty()
                .associateBy(WorkoutSessionSetEntity::setNumber)

            WorkoutEditExerciseState(
                exerciseId = definition.exerciseId,
                title = getExerciseLabel(context, definition.exerciseId),
                inputType = definition.inputType,
                included = persistedSets.isNotEmpty(),
                sets = List(EDIT_SET_COUNT) { index ->
                    val setNumber = index + 1
                    val persistedSet = persistedSets[setNumber]
                    WorkoutEditSetState(
                        setNumber = setNumber,
                        valueText = persistedSet?.trackedValueText(definition.inputType).orEmpty(),
                        note = persistedSet?.note.orEmpty(),
                        original = persistedSet
                    )
                }
            )
        }
    )
}

private suspend fun recomputePersistedMaxValues(
    database: com.example.trener.data.local.TrenerDatabase,
    exercises: List<WorkoutEditExerciseState>
) {
    exercises.forEach { exercise ->
        loadPersistedMaxValuesBySetNumber(
            database = database,
            exerciseId = exercise.exerciseId,
            exerciseInputType = exercise.inputType
        )
    }
}

private fun WorkoutSessionSetEntity.trackedValueText(inputType: ExerciseInputType): String {
    return when (inputType) {
        ExerciseInputType.REPS -> reps?.toString()
        ExerciseInputType.TIME_SECONDS -> additionalValue?.toInt()?.toString()
    }.orEmpty()
}

private const val EDIT_SET_COUNT = 4
