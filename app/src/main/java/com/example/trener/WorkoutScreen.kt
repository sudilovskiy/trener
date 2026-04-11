package com.example.trener

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.trener.data.local.TrenerDatabaseProvider
import com.example.trener.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(
    databaseRefreshToken: Int,
    trainingDay: Int,
    sessionUiState: WorkoutSessionUiState,
    onExerciseClick: (String) -> Unit
) {
    val context = LocalContext.current.applicationContext
    val database = remember(context, databaseRefreshToken) {
        TrenerDatabaseProvider.getInstance(context)
    }
    val coroutineScope = rememberCoroutineScope()
    val dayExercises = remember(trainingDay) { getExercisesForDay(trainingDay) }

    var elapsedSeconds by remember(trainingDay) { mutableLongStateOf(0L) }
    var isStarting by remember(trainingDay) { mutableStateOf(false) }
    val activeWorkout = sessionUiState.activeWorkout
    val isWorkoutStarted = sessionUiState.isWorkoutActiveForDay(trainingDay)
    val isSaving = sessionUiState.isSaving
    val startedAtMillis = activeWorkout
        ?.takeIf { it.trainingDay == trainingDay }
        ?.startedAtMillis
    val saveResult = sessionUiState.lastSaveResult

    LaunchedEffect(saveResult) {
        if (saveResult != null) {
            delay(2500L)
            sessionUiState.clearLastSaveResult()
        }
    }

    LaunchedEffect(startedAtMillis) {
        val startTimestamp = startedAtMillis ?: run {
            elapsedSeconds = 0L
            return@LaunchedEffect
        }

        while (true) {
            elapsedSeconds = ((System.currentTimeMillis() - startTimestamp) / 1000L).coerceAtLeast(0L)
            delay(1000L)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.training_day_title, trainingDay)) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionCard(title = stringResource(R.string.workout_controls_title)) {
                    if (isWorkoutStarted) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.workout_timer_label,
                                    formatElapsedTimer(elapsedSeconds)
                                ),
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                val workoutStartedAtMillis = System.currentTimeMillis()
                                isStarting = true
                                coroutineScope.launch {
                                    runCatching {
                                        withContext(Dispatchers.IO) {
                                            database.workoutSessionDao().insertWorkoutSession(
                                                WorkoutSessionEntity(
                                                    trainingDay = trainingDay,
                                                    startTimestampEpochMillis = workoutStartedAtMillis
                                                )
                                            )
                                        }
                                    }.onSuccess { sessionId ->
                                        sessionUiState.startWorkout(
                                            sessionId = sessionId,
                                            trainingDay = trainingDay,
                                            startedAtMillis = workoutStartedAtMillis
                                        )
                                        elapsedSeconds = 0L
                                    }.onFailure {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.workout_start_error),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    isStarting = false
                                }
                            },
                            enabled = !sessionUiState.hasActiveWorkout() && !isSaving && !isStarting,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (isStarting) {
                                    stringResource(R.string.workout_starting_button)
                                } else {
                                    stringResource(R.string.workout_start_button)
                                }
                            )
                        }
                    }

                    Button(
                        onClick = {
                            sessionUiState.requestFinishWorkout(WorkoutFinishTrigger.UserAction)
                        },
                        enabled = isWorkoutStarted && !isSaving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (isSaving) {
                                stringResource(R.string.workout_saving_button)
                            } else {
                                stringResource(R.string.workout_finish_button)
                            }
                        )
                    }

                    if (saveResult == WorkoutSaveResult.Success) {
                        Text(
                            text = context.getString(R.string.workout_saved_message),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (saveResult == WorkoutSaveResult.Error) {
                        Text(
                            text = context.getString(R.string.workout_save_error),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.workout_exercises_title)) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        dayExercises.forEach { exercise ->
                            val isCompleted = sessionUiState.isRepBasedExerciseCompleted(exercise.exerciseId)
                            Button(
                                onClick = { onExerciseClick(exercise.exerciseId) },
                                enabled = isWorkoutStarted && !isSaving,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isCompleted) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    contentColor = if (isCompleted) {
                                        MaterialTheme.colorScheme.onSecondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onPrimary
                                    },
                                    disabledContainerColor = if (isCompleted) {
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    disabledContentColor = if (isCompleted) {
                                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = getExerciseLabel(context, exercise.exerciseId),
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isCompleted) {
                                        Text(
                                            text = CompletedExerciseIndicator,
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.padding(start = 12.dp)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.width(24.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val CompletedExerciseIndicator = "\u2713"
