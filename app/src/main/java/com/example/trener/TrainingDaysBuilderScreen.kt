package com.example.trener

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.trener.data.local.TrainingDayRepository
import com.example.trener.data.local.entity.TrainingDayEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class TrainingDayEditorState(
    val title: String,
    val initialName: String,
    val dayId: Long? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingDaysBuilderScreen(
    databaseRefreshToken: Int,
    onManageExercises: (Long) -> Unit,
    onManageCatalog: () -> Unit,
    onBack: () -> Unit
) {
    val database = rememberTrenerDatabase(databaseRefreshToken)
    val repository = remember(database) { TrainingDayRepository(database) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var days by remember { mutableStateOf<List<TrainingDayEntity>>(emptyList()) }
    var isLoaded by remember { mutableStateOf(false) }
    var editorState by remember { mutableStateOf<TrainingDayEditorState?>(null) }
    var deleteTarget by remember { mutableStateOf<TrainingDayEntity?>(null) }
    var nameInput by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }

    suspend fun refreshDays() {
        days = repository.getTrainingDays()
        isLoaded = true
    }

    fun openAddDialog() {
        val nextNumber = days.size + 1
        val defaultName = context.getString(R.string.training_day_title, nextNumber)
        editorState = TrainingDayEditorState(
            title = context.getString(R.string.training_days_edit_dialog_title_add),
            initialName = defaultName
        )
        nameInput = defaultName
        nameError = null
    }

    fun openRenameDialog(day: TrainingDayEntity) {
        editorState = TrainingDayEditorState(
            title = context.getString(R.string.training_days_edit_dialog_title_rename),
            initialName = day.name,
            dayId = day.id
        )
        nameInput = day.name
        nameError = null
    }

    fun closeEditor() {
        editorState = null
        nameInput = ""
        nameError = null
    }

    fun saveEditor() {
        val currentEditor = editorState ?: return
        val normalizedName = nameInput.trim()
        if (normalizedName.isBlank()) {
            nameError = context.getString(R.string.training_days_name_error)
            return
        }

        coroutineScope.launch {
            val saved = runCatching {
                withContext(Dispatchers.IO) {
                    if (currentEditor.dayId == null) {
                        repository.addTrainingDay(normalizedName)
                    } else {
                        repository.renameTrainingDay(currentEditor.dayId, normalizedName)
                    }
                }
            }
            if (saved.isSuccess) {
                refreshDays()
                closeEditor()
            }
        }
    }

    fun requestDelete(day: TrainingDayEntity) {
        deleteTarget = day
    }

    fun confirmDelete(day: TrainingDayEntity) {
        coroutineScope.launch {
            val deleted = runCatching {
                withContext(Dispatchers.IO) {
                    repository.deleteTrainingDay(day.id)
                }
            }
            if (deleted.isSuccess) {
                refreshDays()
                deleteTarget = null
            }
        }
    }

    fun moveUp(day: TrainingDayEntity) {
        coroutineScope.launch {
            val moved = runCatching {
                withContext(Dispatchers.IO) {
                    repository.moveTrainingDayUp(day.id)
                }
            }
            if (moved.isSuccess) {
                refreshDays()
            }
        }
    }

    fun moveDown(day: TrainingDayEntity) {
        coroutineScope.launch {
            val moved = runCatching {
                withContext(Dispatchers.IO) {
                    repository.moveTrainingDayDown(day.id)
                }
            }
            if (moved.isSuccess) {
                refreshDays()
            }
        }
    }

    LaunchedEffect(repository, databaseRefreshToken) {
        refreshDays()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.training_days_builder_title)) },
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Text(text = stringResource(R.string.training_days_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { openAddDialog() },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
            ) {
                Text(stringResource(R.string.training_days_add_button))
            }

            OutlinedButton(
                onClick = onManageCatalog,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
            ) {
                Text(stringResource(R.string.training_days_manage_catalog_button))
            }

            if (!isLoaded) {
                Text(
                    text = stringResource(R.string.loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (days.isEmpty()) {
                EmptyBuilderState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(days, key = { _, day -> day.id }) { index, day ->
                        TrainingDayCard(
                            day = day,
                            position = index + 1,
                            canMoveUp = day != days.firstOrNull(),
                            canMoveDown = day != days.lastOrNull(),
                            onMoveUp = { moveUp(day) },
                            onMoveDown = { moveDown(day) },
                            onRename = { openRenameDialog(day) },
                            onManageExercises = { onManageExercises(day.id) },
                            onDelete = { requestDelete(day) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    editorState?.let { state ->
        AlertDialog(
            onDismissRequest = { closeEditor() },
            title = { Text(text = state.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = {
                            nameInput = it
                            nameError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.training_days_edit_dialog_label)) },
                        isError = nameError != null,
                        supportingText = {
                            nameError?.let { errorText ->
                                Text(errorText)
                            }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { saveEditor() }) {
                    Text(
                        text = if (state.dayId == null) {
                            stringResource(R.string.training_days_edit_dialog_confirm_add)
                        } else {
                            stringResource(R.string.training_days_edit_dialog_confirm_rename)
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { closeEditor() }) {
                    Text(text = stringResource(R.string.training_days_edit_dialog_cancel))
                }
            }
        )
    }

    deleteTarget?.let { day ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(text = stringResource(R.string.training_days_delete_dialog_title)) },
            text = { Text(text = stringResource(R.string.training_days_delete_dialog_message, day.name)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete(day) }) {
                    Text(text = stringResource(R.string.training_days_delete_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(text = stringResource(R.string.training_days_delete_dialog_cancel))
                }
            }
        )
    }
}

@Composable
private fun EmptyBuilderState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.training_days_empty),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.training_days_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TrainingDayCard(
    day: TrainingDayEntity,
    position: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRename: () -> Unit,
    onManageExercises: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = day.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.training_day_title, position),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = null
                    )
                    Text(
                        text = stringResource(R.string.training_days_move_up_button),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDownward,
                        contentDescription = null
                    )
                    Text(
                        text = stringResource(R.string.training_days_move_down_button),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            OutlinedButton(
                onClick = onManageExercises,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = stringResource(R.string.training_days_manage_exercises_button),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRename,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null
                    )
                    Text(
                        text = stringResource(R.string.training_days_rename_button),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null
                    )
                    Text(
                        text = stringResource(R.string.training_days_delete_button),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
