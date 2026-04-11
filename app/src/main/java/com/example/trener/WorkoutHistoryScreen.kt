package com.example.trener

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.example.trener.data.local.BackupDatabaseResult
import com.example.trener.data.local.ClearWorkoutDataResult
import com.example.trener.data.local.DatabaseMaintenanceFailureReason
import com.example.trener.data.local.DatabaseMaintenanceService
import com.example.trener.data.local.RestoreDatabaseResult
import com.example.trener.data.local.TrenerDatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutHistoryScreen(
    databaseRefreshToken: Int,
    refreshToken: Int,
    dateFilterEpochDay: Long? = null,
    sessionUiState: WorkoutSessionUiState,
    onAdminDataChanged: () -> Unit,
    onSessionClick: (Long) -> Unit
) {
    val context = LocalContext.current.applicationContext
    val database = remember(context, databaseRefreshToken) {
        TrenerDatabaseProvider.getInstance(context)
    }
    val coroutineScope = rememberCoroutineScope()

    var historyItems by remember { mutableStateOf<List<WorkoutHistoryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isWorking by remember { mutableStateOf(false) }
    var showDeleteAllConfirmation by remember { mutableStateOf(false) }
    var showRestoreConfirmation by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(database, refreshToken, databaseRefreshToken, dateFilterEpochDay) {
        isLoading = true
        loadError = null

        runCatching {
            withContext(Dispatchers.IO) {
                database.workoutSessionDao()
                    .getAllSessions()
                    .asSequence()
                    .filter { session ->
                        dateFilterEpochDay?.let { session.toWorkoutLocalDate().toEpochDay() == it }
                            ?: true
                    }
                    .map { session ->
                        session.toWorkoutHistoryItem(
                            context = context,
                            sets = database.workoutSessionSetDao()
                                .getSetsByWorkoutSessionId(session.id)
                        )
                    }
                    .toList()
            }
        }.onSuccess { items ->
            historyItems = items
        }.onFailure { throwable ->
            Log.e(logTag, "Failed to load workout history", throwable)
            historyItems = emptyList()
            loadError = context.getString(R.string.history_load_error)
        }

        isLoading = false
    }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null || isWorking) {
            return@rememberLauncherForActivityResult
        }

        coroutineScope.launch {
            isWorking = true
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    DatabaseMaintenanceService.backupDatabase(context, outputStream)
                } ?: BackupDatabaseResult.Failure(
                    reason = DatabaseMaintenanceFailureReason.UNKNOWN
                )
            }.onSuccess { result ->
                when (result) {
                    is BackupDatabaseResult.Success -> {
                        Toast.makeText(
                            context,
                            "Резервная копия создана",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    is BackupDatabaseResult.Failure -> {
                        Log.e(
                            logTag,
                            "Backup failed: ${result.reason}",
                            result.cause
                        )
                    }
                }
            }.onFailure { throwable ->
                Log.e(logTag, "Backup failed", throwable)
            }
            isWorking = false
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null || isWorking) {
            return@rememberLauncherForActivityResult
        }

        pendingRestoreUri = uri
        showRestoreConfirmation = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_screen_title)) }
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

            if (!isLoading && loadError == null && historyItems.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.history_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                SectionCard(title = "Управление") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showDeleteAllConfirmation = true },
                            enabled = !isWorking,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Удалить все тренировки")
                        }
                        Button(
                            onClick = {
                                if (!isWorking) {
                                    backupLauncher.launch(defaultBackupFileName())
                                }
                            },
                            enabled = !isWorking,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Создать резервную копию")
                        }
                        Button(
                            onClick = {
                                if (!isWorking) {
                                    restoreLauncher.launch(arrayOf("*/*"))
                                }
                            },
                            enabled = !isWorking,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Восстановить из резервной копии")
                        }
                    }
                }
            }

            items(historyItems, key = { item -> item.id }) { item ->
                SectionCard(
                    title = stringResource(R.string.training_day_title, item.trainingDay),
                    modifier = Modifier.clickable { onSessionClick(item.id) }
                ) {
                    Text(
                        text = stringResource(R.string.history_day_label, item.trainingDay),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(stringResource(R.string.date_label, item.dateText))
                    Text(
                        text = stringResource(R.string.duration_label, item.durationText),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(
                            R.string.history_exercises_label,
                            if (item.exerciseSummaryText.isBlank()) {
                                stringResource(R.string.not_specified)
                            } else {
                                item.exerciseSummaryText
                            }
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showDeleteAllConfirmation) {
        AlertDialog(
            onDismissRequest = {
                if (!isWorking) {
                    showDeleteAllConfirmation = false
                }
            },
            text = { Text("Удалить ВСЕ тренировки? Это действие нельзя отменить.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isWorking) {
                            return@TextButton
                        }

                        showDeleteAllConfirmation = false
                        coroutineScope.launch {
                            isWorking = true
                            runCatching {
                                DatabaseMaintenanceService.clearWorkoutData(context)
                            }.onSuccess { result ->
                                when (result) {
                                    is ClearWorkoutDataResult.Success -> {
                                        historyItems = emptyList()
                                        loadError = null
                                        sessionUiState.reset()
                                        onAdminDataChanged()
                                    }

                                    is ClearWorkoutDataResult.Failure -> {
                                        Log.e(
                                            logTag,
                                            "Clear all workout data failed: ${result.reason}",
                                            result.cause
                                        )
                                    }
                                }
                            }.onFailure { throwable ->
                                Log.e(logTag, "Clear all workout data failed", throwable)
                            }
                            isWorking = false
                        }
                    }
                ) {
                    Text("Да")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAllConfirmation = false },
                    enabled = !isWorking
                ) {
                    Text("Нет")
                }
            }
        )
    }

    if (showRestoreConfirmation) {
        AlertDialog(
            onDismissRequest = {
                if (!isWorking) {
                    showRestoreConfirmation = false
                    pendingRestoreUri = null
                }
            },
            text = { Text("Текущие данные будут удалены. Продолжить?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (isWorking) {
                            return@TextButton
                        }

                        val restoreUri = pendingRestoreUri
                        if (restoreUri == null) {
                            showRestoreConfirmation = false
                            return@TextButton
                        }

                        showRestoreConfirmation = false
                        coroutineScope.launch {
                            isWorking = true
                            runCatching {
                                context.contentResolver.openInputStream(restoreUri)?.use { input ->
                                    DatabaseMaintenanceService.restoreDatabase(context, input)
                                } ?: RestoreDatabaseResult.Failure(
                                    reason = DatabaseMaintenanceFailureReason.INVALID_BACKUP
                                )
                            }.onSuccess { result ->
                                when (result) {
                                    RestoreDatabaseResult.Cancelled -> Unit

                                    is RestoreDatabaseResult.Success -> {
                                        historyItems = emptyList()
                                        loadError = null
                                        sessionUiState.reset()
                                        onAdminDataChanged()
                                        Toast.makeText(
                                            context,
                                            "Данные восстановлены",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }

                                    is RestoreDatabaseResult.Failure -> {
                                        Log.e(
                                            logTag,
                                            "Restore failed: ${result.reason}",
                                            result.cause
                                        )
                                    }
                                }
                            }.onFailure { throwable ->
                                Log.e(logTag, "Restore failed", throwable)
                            }
                            pendingRestoreUri = null
                            isWorking = false
                        }
                    }
                ) {
                    Text("Да")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRestoreConfirmation = false
                        pendingRestoreUri = null
                    },
                    enabled = !isWorking
                ) {
                    Text("Нет")
                }
            }
        )
    }
}

private fun defaultBackupFileName(): String {
    return "trener-backup-${LocalDate.now()}.zip"
}
