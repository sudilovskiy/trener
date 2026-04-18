package com.example.trener

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.trener.healthconnect.HealthConnectWeightImportService
import com.example.trener.ble.BleEntryScreen
import com.example.trener.data.local.WorkoutHeartRateRepository
import com.example.trener.domain.workout.ReplaceWorkoutSessionSets
import com.example.trener.domain.workout.toWorkoutSessionSetEntity
import com.example.trener.ui.navigation.AppRoute
import com.example.trener.ui.navigation.getNextTrainingDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.trener.music.MusicPlayerViewModel
import com.example.trener.data.local.TrainingProgramBootstrapper
import com.example.trener.data.local.TrainingProgramRepository
import com.example.trener.WorkoutProgramCache
import com.example.trener.getWorkoutProgramDays

@Composable
fun TrenerApp() {
    val currentContext = LocalContext.current
    val context = currentContext.applicationContext
    val application = context as TrenerApplication
    val activity = currentContext as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    val databaseRefreshToken by DatabaseRefreshCoordinator.refreshGeneration.collectAsState()
    var selectedTrainingDay by rememberSaveable { mutableIntStateOf(1) }
    var lastCompletedTrainingDay by remember { mutableStateOf<Int?>(null) }
    var completedHistoryRefreshToken by remember { mutableIntStateOf(0) }
    var workoutDetailRefreshToken by remember { mutableIntStateOf(0) }
    var programRefreshToken by remember { mutableIntStateOf(0) }
    var healthConnectWeightImportInFlight by remember { mutableStateOf(false) }
    var showExitConfirmation by rememberSaveable { mutableStateOf(false) }
    var shouldExitAfterWorkoutSave by rememberSaveable { mutableStateOf(false) }
    val sessionUiState: WorkoutSessionUiState = viewModel()
    val musicPlayerViewModel: MusicPlayerViewModel = viewModel()
    val musicPlaybackState by musicPlayerViewModel.playbackState.collectAsState()
    val heartRateRuntimeState by application.heartRateRuntimeCoordinator.state.collectAsState()
    val heartRateBleState by application.heartRateBleService.state.collectAsState()
    val database = rememberTrenerDatabase(databaseRefreshToken)
    val workoutHeartRateRepository = remember(database) {
        WorkoutHeartRateRepository(database)
    }
    val workoutProgramRepository = remember(database) { TrainingProgramRepository(database) }
    val view = LocalView.current
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val shouldInterceptExit = currentRoute == AppRoute.Overview && sessionUiState.hasActiveWorkout()
    val updateHeartRateThreshold: (Int?) -> Unit = { thresholdBpm ->
        application.heartRateSettingsStore.setGlobalHeartRateThresholdBpm(thresholdBpm)
        application.heartRateRuntimeCoordinator.configureThreshold(thresholdBpm)
    }

    fun requestHealthConnectWeightImport() {
        if (currentRoute != AppRoute.Overview || healthConnectWeightImportInFlight) {
            return
        }

        healthConnectWeightImportInFlight = true
        coroutineScope.launch {
            try {
                val imported = withContext(Dispatchers.IO) {
                    HealthConnectWeightImportService.syncLatestWeight(context, database)
                }
                if (imported) {
                    DatabaseRefreshCoordinator.requestRefresh()
                }
            } finally {
                healthConnectWeightImportInFlight = false
            }
        }
    }

    LaunchedEffect(database, completedHistoryRefreshToken, databaseRefreshToken) {
        lastCompletedTrainingDay = withContext(Dispatchers.IO) {
            database.workoutSessionDao().getLastSession()?.trainingDay
        }
    }

    LaunchedEffect(database, databaseRefreshToken) {
        withContext(Dispatchers.IO) {
            TrainingProgramBootstrapper.ensureBootstrapped(context, database)
            WorkoutProgramCache.updateSnapshot(
                workoutProgramRepository.getWorkoutProgramSnapshot()
            )
        }
        programRefreshToken++
    }

    val availableWorkoutDayNumbers = remember(programRefreshToken) {
        getWorkoutProgramDays().map { day -> day.runtimeDayNumber }
    }
    val highlightedTrainingDay = remember(
        lastCompletedTrainingDay,
        programRefreshToken,
        availableWorkoutDayNumbers
    ) {
        getNextTrainingDay(lastCompletedTrainingDay, availableWorkoutDayNumbers)
    }

    LaunchedEffect(programRefreshToken) {
        if (availableWorkoutDayNumbers.isNotEmpty() &&
            selectedTrainingDay !in availableWorkoutDayNumbers
        ) {
            selectedTrainingDay = availableWorkoutDayNumbers.first()
        }
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute == AppRoute.Overview) {
            requestHealthConnectWeightImport()
        }
    }

    DisposableEffect(lifecycleOwner, currentRoute) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && currentRoute == AppRoute.Overview) {
                requestHealthConnectWeightImport()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(lifecycleOwner, musicPlayerViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                musicPlayerViewModel.pausePlayback()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(sessionUiState.pendingFinishTrigger, sessionUiState.activeWorkout) {
        val finishRequest = sessionUiState.beginFinishingWorkout() ?: return@LaunchedEffect
        val replaceWorkoutSessionSets = ReplaceWorkoutSessionSets(database)
        val hasMeaningfulCompletedSet = sessionUiState.hasMeaningfulCompletedWorkoutSet()
        application.heartRateRuntimeCoordinator.beginFinalizingWorkoutSession(
            finishRequest.sessionId
        )

        runCatching {
            withContext(Dispatchers.IO) {
                if (hasMeaningfulCompletedSet) {
                    persistActiveWorkoutSessionSets(
                        replaceWorkoutSessionSets = replaceWorkoutSessionSets,
                        sessionUiState = sessionUiState
                    )
                    database.workoutSessionDao().finishWorkoutSession(
                        sessionId = finishRequest.sessionId,
                        endTimestampEpochMillis = System.currentTimeMillis(),
                        durationSeconds = (
                            (System.currentTimeMillis() - finishRequest.startedAtMillis) / 1000L
                        ).coerceAtLeast(0L)
                    )
                    workoutHeartRateRepository.recalculateWorkoutSessionHeartRateAggregates(
                        finishRequest.sessionId
                    )
                } else {
                    database.workoutSessionDao().deleteWorkoutSession(finishRequest.sessionId)
                }
            }
        }.onSuccess {
            application.heartRateRuntimeCoordinator.clearWorkoutSessionBinding(
                finishRequest.sessionId
            )
            sessionUiState.completeWorkoutSaveSuccessfully()
            if (hasMeaningfulCompletedSet) {
                completedHistoryRefreshToken++
            }
            if (shouldExitAfterWorkoutSave) {
                shouldExitAfterWorkoutSave = false
                activity?.finish()
            }
        }.onFailure { throwable ->
            Log.e(logTag, "Failed to save workout session", throwable)
            application.heartRateRuntimeCoordinator.restoreActiveWorkoutSessionBinding(
                finishRequest.sessionId
            )
            sessionUiState.failWorkoutSave()
            shouldExitAfterWorkoutSave = false
        }
    }

    DisposableEffect(view) {
        val previousKeepScreenOn = view.keepScreenOn
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = previousKeepScreenOn
        }
    }

    BackHandler(enabled = shouldInterceptExit && !showExitConfirmation) {
        showExitConfirmation = true
    }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            NavHost(
                navController = navController,
                startDestination = AppRoute.Overview
            ) {
                composable(AppRoute.Overview) {
                    OverviewScreen(
                        activeWorkout = sessionUiState.activeWorkout,
                        highlightedTrainingDay = highlightedTrainingDay,
                        databaseRefreshToken = databaseRefreshToken,
                        programRefreshToken = programRefreshToken,
                        historyRefreshToken = completedHistoryRefreshToken,
                        musicPlaybackState = musicPlaybackState,
                        heartRateState = heartRateRuntimeState,
                        heartRateBleState = heartRateBleState,
                        onMusicPreviousClick = musicPlayerViewModel::previousTrack,
                        onMusicPlayPauseClick = musicPlayerViewModel::playPause,
                        onMusicNextClick = musicPlayerViewModel::nextTrack,
                        onHeartRateReconnect = application.heartRateBleService::reconnectNow,
                        onHeartRateSelectTargetDevice = application.heartRateBleService::selectTargetDevice,
                        onHeartRateThresholdChange = updateHeartRateThreshold,
                        onBleEntryClick = {
                            navController.navigate(AppRoute.BleEntry)
                        },
                        onExerciseComparisonClick = {
                            navController.navigate(AppRoute.ExerciseComparison)
                        },
                        onTrainingDaysBuilderClick = {
                            navController.navigate(AppRoute.TrainingDaysBuilder)
                        },
                        onTrainingDayClick = { trainingDay -> 
                            val activeWorkout = sessionUiState.activeWorkout
                            if (activeWorkout != null && activeWorkout.trainingDay != trainingDay) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.active_workout_already_exists),
                                    Toast.LENGTH_SHORT
                                ).show()
                                selectedTrainingDay = activeWorkout.trainingDay
                            } else {
                                selectedTrainingDay = trainingDay
                            }
                            navController.navigate(AppRoute.Workout)
                        },
                        onHistoryClick = {
                            navController.navigate(AppRoute.History)
                        },
                        onHistoryDateClick = { epochDay ->
                            navController.navigate(AppRoute.historyForDate(epochDay))
                        }
                    )
                }
                composable(AppRoute.Workout) {
                    WorkoutScreen(
                        databaseRefreshToken = databaseRefreshToken,
                        programRefreshToken = programRefreshToken,
                        trainingDay = selectedTrainingDay,
                        sessionUiState = sessionUiState,
                        heartRateState = heartRateRuntimeState,
                        heartRateBleState = heartRateBleState,
                        onHeartRateReconnect = application.heartRateBleService::reconnectNow,
                        onHeartRateSelectTargetDevice = application.heartRateBleService::selectTargetDevice,
                        onExerciseClick = { exerciseId ->
                            navController.navigate(AppRoute.exercise(exerciseId))
                        }
                    )
                }
                composable(AppRoute.TrainingDaysBuilder) {
                    TrainingDaysBuilderScreen(
                        databaseRefreshToken = databaseRefreshToken,
                        onManageExercises = { trainingDayId ->
                            navController.navigate(AppRoute.trainingDayExercises(trainingDayId))
                        },
                        onManageCatalog = {
                            navController.navigate(AppRoute.exerciseCatalog())
                        },
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }
                composable(
                    route = AppRoute.TrainingDayExercises,
                    arguments = listOf(
                        navArgument(AppRoute.TrainingDayIdArg) {
                            type = NavType.LongType
                        }
                    )
                ) { backStackEntry ->
                    TrainingDayExercisesScreen(
                        trainingDayId = backStackEntry.arguments
                            ?.getLong(AppRoute.TrainingDayIdArg)
                            ?: -1L,
                        databaseRefreshToken = databaseRefreshToken,
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }
                composable(AppRoute.ExerciseCatalog) {
                    ExerciseCatalogManagementScreen(
                        databaseRefreshToken = databaseRefreshToken,
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }
                composable(AppRoute.History) {
                    WorkoutHistoryScreen(
                        databaseRefreshToken = databaseRefreshToken,
                        programRefreshToken = programRefreshToken,
                        refreshToken = completedHistoryRefreshToken,
                        dateFilterEpochDay = null,
                        sessionUiState = sessionUiState,
                        onAdminDataChanged = {
                            sessionUiState.reset()
                            completedHistoryRefreshToken++
                            workoutDetailRefreshToken++
                        },
                        onSessionClick = { sessionId ->
                            navController.navigate(AppRoute.workoutDetail(sessionId))
                        }
                    )
                }
                composable(
                    route = AppRoute.HistoryByDate,
                    arguments = listOf(
                        navArgument(AppRoute.DateEpochDayArg) {
                            type = NavType.LongType
                        }
                    )
                ) { backStackEntry ->
                    WorkoutHistoryScreen(
                        databaseRefreshToken = databaseRefreshToken,
                        programRefreshToken = programRefreshToken,
                        refreshToken = completedHistoryRefreshToken,
                        dateFilterEpochDay = backStackEntry.arguments
                            ?.getLong(AppRoute.DateEpochDayArg),
                        sessionUiState = sessionUiState,
                        onAdminDataChanged = {
                            sessionUiState.reset()
                            completedHistoryRefreshToken++
                            workoutDetailRefreshToken++
                        },
                        onSessionClick = { sessionId ->
                            navController.navigate(AppRoute.workoutDetail(sessionId))
                        }
                    )
                }
                composable(
                    route = AppRoute.WorkoutDetail,
                    arguments = listOf(
                        navArgument(AppRoute.SessionIdArg) {
                            type = NavType.LongType
                        }
                    )
                ) { backStackEntry ->
                    WorkoutDetailScreen(
                        databaseRefreshToken = databaseRefreshToken,
                        programRefreshToken = programRefreshToken,
                        sessionId = backStackEntry.arguments?.getLong(AppRoute.SessionIdArg) ?: -1L,
                        activeWorkoutSessionId = sessionUiState.activeWorkout?.sessionId,
                        refreshToken = workoutDetailRefreshToken,
                        onEditClick = {
                            navController.navigate(
                                AppRoute.workoutEdit(
                                    backStackEntry.arguments?.getLong(AppRoute.SessionIdArg) ?: -1L
                                )
                            )
                        },
                        onDeleteSuccess = {
                            completedHistoryRefreshToken++
                            navController.popBackStack()
                        }
                    )
                }
                composable(
                    route = AppRoute.WorkoutEdit,
                    arguments = listOf(
                        navArgument(AppRoute.SessionIdArg) {
                            type = NavType.LongType
                        }
                    )
                ) { backStackEntry ->
                    WorkoutEditScreen(
                        databaseRefreshToken = databaseRefreshToken,
                        programRefreshToken = programRefreshToken,
                        sessionId = backStackEntry.arguments?.getLong(AppRoute.SessionIdArg) ?: -1L,
                        activeWorkoutSessionId = sessionUiState.activeWorkout?.sessionId,
                        onSaveSuccess = {
                            completedHistoryRefreshToken++
                            workoutDetailRefreshToken++
                            navController.popBackStack()
                        },
                        onCancel = {
                            navController.popBackStack()
                        }
                    )
                }
                composable(
                    route = AppRoute.Exercise,
                    arguments = listOf(
                        navArgument(AppRoute.ExerciseIdArg) {
                            type = NavType.StringType
                        }
                    )
                ) { backStackEntry ->
                    ExerciseScreen(
                        exerciseId = backStackEntry.arguments
                            ?.getString(AppRoute.ExerciseIdArg)
                            .orEmpty(),
                        databaseRefreshToken = databaseRefreshToken,
                        programRefreshToken = programRefreshToken,
                        historyRefreshToken = completedHistoryRefreshToken,
                        sessionUiState = sessionUiState,
                        heartRateState = heartRateRuntimeState,
                        heartRateBleState = heartRateBleState,
                        onHeartRateReconnect = application.heartRateBleService::reconnectNow,
                        onHeartRateSelectTargetDevice = application.heartRateBleService::selectTargetDevice,
                        onFinished = {
                            navController.popBackStack()
                        }
                    )
                }
                composable(AppRoute.ExerciseComparison) {
                    ExerciseComparisonScreen(
                        databaseRefreshToken = databaseRefreshToken,
                        programRefreshToken = programRefreshToken,
                        historyRefreshToken = completedHistoryRefreshToken,
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }
                composable(AppRoute.BleEntry) {
                    BleEntryScreen(
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }
            }

            if (showExitConfirmation) {
                AlertDialog(
                    onDismissRequest = { showExitConfirmation = false },
                    title = { Text(text = stringResource(R.string.exit_workout_dialog_title)) },
                    text = { Text(text = stringResource(R.string.exit_workout_dialog_message)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showExitConfirmation = false
                                shouldExitAfterWorkoutSave = true
                                sessionUiState.requestFinishWorkout(WorkoutFinishTrigger.UserAction)
                            }
                        ) {
                            Text(text = stringResource(R.string.exit_workout_dialog_confirm))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showExitConfirmation = false }) {
                            Text(text = stringResource(R.string.exit_workout_dialog_cancel))
                        }
                    }
                )
            }
        }
    }
}

private suspend fun persistActiveWorkoutSessionSets(
    replaceWorkoutSessionSets: ReplaceWorkoutSessionSets,
    sessionUiState: WorkoutSessionUiState
) {
    val activeWorkout = sessionUiState.activeWorkout ?: return
    val persistedSets = activeWorkout.exerciseDefinitions.flatMap { definition ->
        sessionUiState.getExerciseSetUiStates(
            exerciseId = definition.exerciseId,
            setCount = sessionUiState.getRequiredSetCount(definition.exerciseId)
        ).filter { setUiState ->
            setUiState.isCompleted ||
                hasWorkoutSetContent(setUiState.set) ||
                setUiState.set.note.isNotBlank()
        }.map { setUiState ->
            setUiState.set.toWorkoutSessionSetEntity(activeWorkout.sessionId)
        }
    }

    replaceWorkoutSessionSets(
        workoutSessionId = activeWorkout.sessionId,
        sets = persistedSets
    )
}

private fun hasWorkoutSetContent(set: com.example.trener.domain.workout.PreparedWorkoutSessionSet): Boolean {
    return set.reps != null ||
        set.weight != null ||
        set.additionalValue != null ||
        set.parameterValue != null ||
        set.parameterOverrideValue != null
}
