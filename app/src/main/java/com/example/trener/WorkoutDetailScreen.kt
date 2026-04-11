package com.example.trener

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.trener.data.local.TrenerDatabaseProvider
import com.example.trener.domain.workout.GetWorkoutSessionWithSets
import com.example.trener.domain.workout.WorkoutSessionWithSets
import com.example.trener.data.local.entity.WorkoutSessionEntity
import com.example.trener.data.local.entity.WorkoutSessionSetEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    databaseRefreshToken: Int,
    sessionId: Long,
    activeWorkoutSessionId: Long?,
    refreshToken: Int,
    onEditClick: () -> Unit,
    onDeleteSuccess: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val database = remember(context, databaseRefreshToken) {
        TrenerDatabaseProvider.getInstance(context)
    }
    val getWorkoutSessionWithSets = remember(database) { GetWorkoutSessionWithSets(database) }
    val coroutineScope = rememberCoroutineScope()

    var workoutDetail by remember { mutableStateOf<WorkoutDetailUiModel?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmation by rememberSaveable(sessionId) { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    val isActiveWorkout = activeWorkoutSessionId == sessionId

    LaunchedEffect(sessionId, refreshToken, databaseRefreshToken, getWorkoutSessionWithSets) {
        isLoading = true
        loadError = null
        workoutDetail = null

        if (sessionId <= 0L) {
            loadError = context.getString(R.string.workout_detail_invalid_id)
            isLoading = false
            return@LaunchedEffect
        }

        runCatching {
            withContext(Dispatchers.IO) {
                getWorkoutSessionWithSets(sessionId)?.toWorkoutDetailUiModel(context)
            }
        }.onSuccess { detail ->
            if (detail == null) {
                loadError = context.getString(R.string.workout_detail_not_found)
            } else {
                workoutDetail = detail
            }
        }.onFailure { throwable ->
            Log.e(logTag, "Failed to load workout detail", throwable)
            loadError = context.getString(R.string.workout_detail_load_error)
        }

        isLoading = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.workout_detail_title)) }
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

                workoutDetail?.let { detail ->
                    item {
                        SectionCard(title = stringResource(R.string.training_day_title, detail.session.trainingDay)) {
                            Text(stringResource(R.string.date_label, detail.session.toWorkoutDateText()))
                            Text(
                                text = stringResource(
                                    R.string.duration_label,
                                    detail.session.durationSeconds?.let { formatDuration(context, it) }
                                        ?: stringResource(R.string.not_specified)
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (detail.exercises.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.sets_not_found),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(detail.exercises, key = { exercise -> exercise.exerciseId }) { exercise ->
                            SectionCard(title = exercise.title) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    exercise.sets.forEach { set ->
                                        SetDetailCard(set = set)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (isActiveWorkout) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.workout_detail_edit_active_error),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@OutlinedButton
                                    }

                                    onEditClick()
                                },
                                enabled = !isDeleting,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                            ) {
                                Text(text = stringResource(R.string.workout_detail_edit_button))
                            }

                            Button(
                                onClick = { showDeleteConfirmation = true },
                                enabled = !isDeleting,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                            ) {
                                Text(text = stringResource(R.string.workout_detail_delete_button))
                            }
                        }
                    }
                }
            }
        }

        if (showDeleteConfirmation) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = {
                    if (!isDeleting) {
                        showDeleteConfirmation = false
                    }
                },
                title = { Text(text = stringResource(R.string.workout_detail_delete_dialog_title)) },
                text = { Text(text = stringResource(R.string.workout_detail_delete_dialog_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (isActiveWorkout) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.workout_detail_delete_active_error),
                                    Toast.LENGTH_SHORT
                                ).show()
                                showDeleteConfirmation = false
                                return@TextButton
                            }

                            if (isDeleting) return@TextButton

                            isDeleting = true
                            showDeleteConfirmation = false
                            coroutineScope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) {
                                        database.workoutSessionDao().deleteWorkoutSession(sessionId)
                                    }
                                }.onSuccess { deletedRows ->
                                    if (deletedRows > 0) {
                                        onDeleteSuccess()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.workout_detail_delete_error),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }.onFailure { throwable ->
                                    Log.e(logTag, "Failed to delete workout session", throwable)
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.workout_detail_delete_error),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                isDeleting = false
                            }
                        },
                        enabled = !isDeleting
                    ) {
                        Text(text = stringResource(R.string.workout_detail_delete_dialog_confirm))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDeleteConfirmation = false },
                        enabled = !isDeleting
                    ) {
                        Text(text = stringResource(R.string.workout_detail_delete_dialog_cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun SetDetailCard(set: WorkoutDetailSetUiModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.set_number_label, set.setNumber),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = set.valuesText,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            set.note.takeIf(String::isNotBlank)?.let { note ->
                Text(
                    text = "${stringResource(R.string.exercise_note_label)}: $note",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private data class WorkoutDetailUiModel(
    val session: WorkoutSessionEntity,
    val exercises: List<WorkoutDetailExerciseUiModel>
)

private data class WorkoutDetailExerciseUiModel(
    val exerciseId: String,
    val title: String,
    val sets: List<WorkoutDetailSetUiModel>
)

private data class WorkoutDetailSetUiModel(
    val setNumber: Int,
    val valuesText: String,
    val note: String
)

private fun WorkoutSessionWithSets.toWorkoutDetailUiModel(context: android.content.Context): WorkoutDetailUiModel {
    val exercises = getExercisesForDay(session.trainingDay).map { definition ->
        val setsByNumber = sets
            .asSequence()
            .filter { it.exerciseId == definition.exerciseId }
            .associateBy(WorkoutSessionSetEntity::setNumber)

        WorkoutDetailExerciseUiModel(
            exerciseId = definition.exerciseId,
            title = getExerciseLabel(context, definition.exerciseId),
            sets = (1..DETAIL_SET_COUNT).map { setNumber ->
                val persistedSet = setsByNumber[setNumber]
                WorkoutDetailSetUiModel(
                    setNumber = setNumber,
                    valuesText = persistedSet?.let { buildWorkoutSetValuesText(context, it) }
                        ?: context.getString(R.string.set_values_not_specified),
                    note = persistedSet?.note.orEmpty()
                )
            }
        )
    }

    return WorkoutDetailUiModel(
        session = session,
        exercises = exercises
    )
}

private const val DETAIL_SET_COUNT = 4
