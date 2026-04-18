package com.example.trener

import android.widget.Toast
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.trener.data.local.ExerciseCatalogDeleteResult
import com.example.trener.data.local.ExerciseCatalogRepository
import com.example.trener.data.local.ExerciseCatalogSaveResult
import com.example.trener.data.local.entity.ExerciseCatalogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ExerciseCatalogEditorState(
    val title: String,
    val exerciseId: String? = null,
    val initialName: String = "",
    val initialParameterType: String = CatalogParameterTypes.defaultValue,
    val initialParameterLabel: String = ""
)

private data class CatalogParameterTypeOption(
    val value: String,
    val labelResId: Int
)

private val catalogParameterTypeOptions = listOf(
    CatalogParameterTypeOption(CatalogParameterTypes.Weight, R.string.exercise_catalog_parameter_type_weight),
    CatalogParameterTypeOption(CatalogParameterTypes.Assist, R.string.exercise_catalog_parameter_type_assist),
    CatalogParameterTypeOption(CatalogParameterTypes.Time, R.string.exercise_catalog_parameter_type_time),
    CatalogParameterTypeOption(CatalogParameterTypes.Custom, R.string.exercise_catalog_parameter_type_custom)
)

private object CatalogParameterTypes {
    const val Weight = "WEIGHT"
    const val Assist = "ASSIST"
    const val Time = "TIME"
    const val Custom = "CUSTOM"

    val defaultValue: String = Weight
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseCatalogManagementScreen(
    databaseRefreshToken: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val database = rememberTrenerDatabase(databaseRefreshToken)
    val repository = remember(database) { ExerciseCatalogRepository(database) }
    val coroutineScope = rememberCoroutineScope()

    var exercises by remember { mutableStateOf<List<ExerciseCatalogEntity>>(emptyList()) }
    var isLoaded by remember { mutableStateOf(false) }
    var editorState by remember { mutableStateOf<ExerciseCatalogEditorState?>(null) }
    var deleteTarget by remember { mutableStateOf<ExerciseCatalogEntity?>(null) }
    var nameInput by rememberSaveable { mutableStateOf("") }
    var parameterTypeInput by rememberSaveable { mutableStateOf(CatalogParameterTypes.defaultValue) }
    var parameterLabelInput by rememberSaveable { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var parameterLabelError by remember { mutableStateOf<String?>(null) }

    suspend fun refreshExercises() {
        exercises = repository.getExercises()
        isLoaded = true
    }

    fun openCreateDialog() {
        editorState = ExerciseCatalogEditorState(
            title = context.getString(R.string.exercise_catalog_add_dialog_title)
        )
        nameInput = ""
        parameterTypeInput = CatalogParameterTypes.defaultValue
        parameterLabelInput = ""
        nameError = null
        parameterLabelError = null
    }

    fun openEditDialog(exercise: ExerciseCatalogEntity) {
        editorState = ExerciseCatalogEditorState(
            title = context.getString(R.string.exercise_catalog_edit_dialog_title),
            exerciseId = exercise.id,
            initialName = exercise.name,
            initialParameterType = exercise.parameterType,
            initialParameterLabel = exercise.parameterLabel
        )
        nameInput = exercise.name
        parameterTypeInput = exercise.parameterType.ifBlank { CatalogParameterTypes.defaultValue }
        parameterLabelInput = exercise.parameterLabel
        nameError = null
        parameterLabelError = null
    }

    fun closeEditor() {
        editorState = null
        nameInput = ""
        parameterTypeInput = CatalogParameterTypes.defaultValue
        parameterLabelInput = ""
        nameError = null
        parameterLabelError = null
    }

    fun saveExercise() {
        val currentEditor = editorState ?: return
        val normalizedName = nameInput.trim()
        val normalizedParameterLabel = parameterLabelInput.trim()
        val selectedParameterType = parameterTypeInput.trim().ifBlank {
            CatalogParameterTypes.defaultValue
        }

        var hasError = false
        nameError = null
        parameterLabelError = null

        if (normalizedName.isBlank()) {
            nameError = context.getString(R.string.exercise_catalog_name_error)
            hasError = true
        }
        if (normalizedParameterLabel.isBlank()) {
            parameterLabelError = context.getString(R.string.exercise_catalog_parameter_label_error)
            hasError = true
        }
        if (hasError) {
            return
        }

        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                if (currentEditor.exerciseId == null) {
                    repository.createExercise(
                        name = normalizedName,
                        parameterType = selectedParameterType,
                        parameterLabel = normalizedParameterLabel
                    )
                } else {
                    repository.updateExercise(
                        exerciseId = currentEditor.exerciseId,
                        name = normalizedName,
                        parameterType = selectedParameterType,
                        parameterLabel = normalizedParameterLabel
                    )
                }
            }
            when (result) {
                ExerciseCatalogSaveResult.Saved -> {
                    DatabaseRefreshCoordinator.requestRefresh()
                    refreshExercises()
                    closeEditor()
                }
                    ExerciseCatalogSaveResult.DuplicateName -> {
                        nameError = context.getString(R.string.exercise_catalog_duplicate_name_error)
                    }
                    ExerciseCatalogSaveResult.NotFound -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.exercise_catalog_not_found_error),
                            Toast.LENGTH_SHORT
                        ).show()
                        refreshExercises()
                        closeEditor()
                    }
                }
            }
        }

    fun requestDelete(exercise: ExerciseCatalogEntity) {
        deleteTarget = exercise
    }

    fun confirmDelete(exercise: ExerciseCatalogEntity) {
        coroutineScope.launch {
            when (
                withContext(Dispatchers.IO) {
                    repository.deleteExercise(exercise.id)
                }
            ) {
                ExerciseCatalogDeleteResult.Deleted -> {
                    DatabaseRefreshCoordinator.requestRefresh()
                    refreshExercises()
                    deleteTarget = null
                }
                ExerciseCatalogDeleteResult.InUse -> {
                    deleteTarget = null
                    Toast.makeText(
                        context,
                        context.getString(R.string.exercise_catalog_delete_blocked_message),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                ExerciseCatalogDeleteResult.NotFound -> {
                    deleteTarget = null
                    refreshExercises()
                    Toast.makeText(
                        context,
                        context.getString(R.string.exercise_catalog_not_found_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    LaunchedEffect(repository, databaseRefreshToken) {
        refreshExercises()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.exercise_catalog_title)) },
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Text(text = stringResource(R.string.exercise_catalog_back))
                    }
                },
                actions = {
                    IconButton(onClick = { openCreateDialog() }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.exercise_catalog_add_button)
                        )
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
                onClick = { openCreateDialog() },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Text(text = stringResource(R.string.exercise_catalog_add_button))
            }

            if (!isLoaded) {
                Text(
                    text = stringResource(R.string.loading),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (exercises.isEmpty()) {
                EmptyExerciseCatalogState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(exercises, key = { _, item -> item.id }) { index, exercise ->
                        ExerciseCatalogCard(
                            exercise = exercise,
                            position = index + 1,
                            onEdit = { openEditDialog(exercise) },
                            onDelete = { requestDelete(exercise) }
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
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = {
                            nameInput = it
                            nameError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.exercise_catalog_name_label)) },
                        isError = nameError != null,
                        supportingText = {
                            nameError?.let { errorText ->
                                Text(errorText)
                            }
                        }
                    )
                    ExerciseCatalogParameterTypeField(
                        value = parameterTypeInput,
                        onValueChange = {
                            parameterTypeInput = it
                        }
                    )
                    OutlinedTextField(
                        value = parameterLabelInput,
                        onValueChange = {
                            parameterLabelInput = it
                            parameterLabelError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(R.string.exercise_catalog_parameter_label)) },
                        isError = parameterLabelError != null,
                        supportingText = {
                            parameterLabelError?.let { errorText ->
                                Text(errorText)
                            }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { saveExercise() }) {
                    Text(
                        text = if (state.exerciseId == null) {
                            stringResource(R.string.exercise_catalog_add_dialog_confirm)
                        } else {
                            stringResource(R.string.exercise_catalog_edit_dialog_confirm)
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { closeEditor() }) {
                    Text(text = stringResource(R.string.exercise_catalog_dialog_cancel))
                }
            }
        )
    }

    deleteTarget?.let { exercise ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(text = stringResource(R.string.exercise_catalog_delete_dialog_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.exercise_catalog_delete_dialog_message,
                        exercise.name
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete(exercise) }) {
                    Text(text = stringResource(R.string.exercise_catalog_delete_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(text = stringResource(R.string.exercise_catalog_dialog_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseCatalogParameterTypeField(
    value: String,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedOption = catalogParameterTypeOptions.firstOrNull { it.value == value }
        ?: catalogParameterTypeOptions.first()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = stringResource(R.string.exercise_catalog_parameter_type_label))
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Text(
                text = stringResource(selectedOption.labelResId),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            catalogParameterTypeOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelResId)) },
                    onClick = {
                        onValueChange(option.value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyExerciseCatalogState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.exercise_catalog_empty_title),
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.exercise_catalog_empty_hint),
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExerciseCatalogCard(
    exercise: ExerciseCatalogEntity,
    position: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = exercise.name,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.exercise_catalog_position_label, position),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatExerciseCatalogDetails(exercise),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Edit, contentDescription = null)
                    Text(
                        text = stringResource(R.string.exercise_catalog_edit_button),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(imageVector = Icons.Filled.Delete, contentDescription = null)
                    Text(
                        text = stringResource(R.string.exercise_catalog_delete_button),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun formatExerciseCatalogDetails(exercise: ExerciseCatalogEntity): String {
    return buildList {
        add(exercise.parameterLabel.trim())
        add(exercise.parameterType.trim())
    }.filter { it.isNotEmpty() }
        .joinToString(" - ")
        .ifBlank { exercise.id }
}
