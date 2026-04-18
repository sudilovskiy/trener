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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import com.example.trener.data.local.AddTrainingDayExerciseResult
import com.example.trener.data.local.TrainingDayExerciseItem
import com.example.trener.data.local.TrainingDayExerciseRepository
import com.example.trener.data.local.entity.ExerciseCatalogEntity
import com.example.trener.data.local.entity.TrainingDayEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingDayExercisesScreen(
    trainingDayId: Long,
    databaseRefreshToken: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val database = rememberTrenerDatabase(databaseRefreshToken)
    val exerciseRepository = remember(database) { TrainingDayExerciseRepository(database) }
    val coroutineScope = rememberCoroutineScope()

    var trainingDay by remember { mutableStateOf<TrainingDayEntity?>(null) }
    var dayExercises by remember { mutableStateOf<List<TrainingDayExerciseItem>>(emptyList()) }
    var catalogExercises by remember { mutableStateOf<List<ExerciseCatalogEntity>>(emptyList()) }
    var isLoaded by remember { mutableStateOf(false) }
    var showAddSelector by remember { mutableStateOf(false) }

    suspend fun loadContent(): Triple<TrainingDayEntity?, List<TrainingDayExerciseItem>, List<ExerciseCatalogEntity>> {
        val loadedDay = exerciseRepository.getTrainingDay(trainingDayId)
        val loadedExercises = if (loadedDay != null) {
            exerciseRepository.getTrainingDayExercises(trainingDayId)
        } else {
            emptyList()
        }
        val loadedCatalog = exerciseRepository.getCatalogExercises()
        return Triple(loadedDay, loadedExercises, loadedCatalog)
    }

    fun reload() {
        coroutineScope.launch {
            isLoaded = false
            val loaded = withContext(Dispatchers.IO) { loadContent() }
            trainingDay = loaded.first
            dayExercises = loaded.second
            catalogExercises = loaded.third
            isLoaded = true
        }
    }

    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun requestAddExercise() {
        if (catalogExercises.isEmpty()) {
            showAddSelector = true
            return
        }
        showAddSelector = true
    }

    fun addExercise(catalogExercise: ExerciseCatalogEntity) {
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                exerciseRepository.addExerciseToDay(trainingDayId, catalogExercise.id)
            }
            when (result) {
                AddTrainingDayExerciseResult.Added -> {
                    reload()
                    showAddSelector = false
                }
                AddTrainingDayExerciseResult.Duplicate -> {
                    showToast(context.getString(R.string.training_day_exercises_duplicate_message))
                }
                AddTrainingDayExerciseResult.TrainingDayNotFound -> {
                    showToast(context.getString(R.string.training_day_exercises_not_found))
                    showAddSelector = false
                    onBack()
                }
                AddTrainingDayExerciseResult.CatalogExerciseNotFound -> {
                    showToast(context.getString(R.string.training_day_exercises_catalog_missing))
                    reload()
                }
            }
        }
    }

    fun removeExercise(dayExercise: TrainingDayExerciseItem) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                exerciseRepository.removeExerciseFromDay(dayExercise.link.id)
            }
            reload()
        }
    }

    fun moveExerciseUp(dayExercise: TrainingDayExerciseItem) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                exerciseRepository.moveExerciseUp(dayExercise.link.id)
            }
            reload()
        }
    }

    fun moveExerciseDown(dayExercise: TrainingDayExerciseItem) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                exerciseRepository.moveExerciseDown(dayExercise.link.id)
            }
            reload()
        }
    }

    LaunchedEffect(database, databaseRefreshToken, trainingDayId) {
        reload()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = trainingDay?.name ?: stringResource(R.string.training_day_exercises_title)
                    )
                },
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Text(text = stringResource(R.string.training_day_exercises_back))
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
                onClick = { requestAddExercise() },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = null)
                Text(text = stringResource(R.string.training_day_exercises_add_button))
            }

            if (!isLoaded) {
                Text(
                    text = stringResource(R.string.loading),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (trainingDay == null) {
                EmptyTrainingDayExercisesState(
                    message = stringResource(R.string.training_day_exercises_not_found)
                )
            } else if (dayExercises.isEmpty()) {
                EmptyTrainingDayExercisesState(
                    message = stringResource(R.string.training_day_exercises_empty),
                    hint = stringResource(R.string.training_day_exercises_empty_hint)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(dayExercises, key = { _, item -> item.link.id }) { index, item ->
                        TrainingDayExerciseCard(
                            item = item,
                            position = index + 1,
                            canMoveUp = index > 0,
                            canMoveDown = index < dayExercises.lastIndex,
                            onMoveUp = { moveExerciseUp(item) },
                            onMoveDown = { moveExerciseDown(item) },
                            onDelete = { removeExercise(item) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    if (showAddSelector) {
        val existingCatalogIds = remember(dayExercises) {
            dayExercises.map { it.catalogExercise.id }.toSet()
        }
        AlertDialog(
            onDismissRequest = { showAddSelector = false },
            title = { Text(text = stringResource(R.string.training_day_exercises_selector_title)) },
            text = {
                if (catalogExercises.isEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = stringResource(R.string.training_day_exercises_selector_empty))
                        Text(text = stringResource(R.string.training_day_exercises_selector_empty_hint))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.training_day_exercises_selector_hint)
                        )
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(catalogExercises, key = { _, item -> item.id }) { _, exercise ->
                                val isAlreadyAdded = exercise.id in existingCatalogIds
                                OutlinedButton(
                                    onClick = { addExercise(exercise) },
                                    enabled = !isAlreadyAdded,
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.Start,
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = exercise.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = formatExerciseCatalogDetails(exercise),
                                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (isAlreadyAdded) {
                                            Text(
                                                text = stringResource(R.string.training_day_exercises_already_added),
                                                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddSelector = false }) {
                    Text(text = stringResource(R.string.training_day_exercises_selector_close))
                }
            }
        )
    }
}

@Composable
private fun EmptyTrainingDayExercisesState(
    message: String,
    hint: String? = null
) {
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
                text = message,
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
            hint?.let {
                Text(
                    text = it,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TrainingDayExerciseCard(
    item: TrainingDayExerciseItem,
    position: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
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
                    text = item.catalogExercise.name,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.training_day_exercises_position_label, position),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatExerciseCatalogDetails(item.catalogExercise),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                    Icon(imageVector = Icons.Filled.ArrowUpward, contentDescription = null)
                    Text(
                        text = stringResource(R.string.training_day_exercises_move_up_button),
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
                    Icon(imageVector = Icons.Filled.ArrowDownward, contentDescription = null)
                    Text(
                        text = stringResource(R.string.training_day_exercises_move_down_button),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Icon(imageVector = Icons.Filled.Delete, contentDescription = null)
                Text(
                    text = stringResource(R.string.training_day_exercises_remove_button),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatExerciseCatalogDetails(exercise: ExerciseCatalogEntity): String {
    val parts = buildList {
        exercise.parameterLabel.trim().takeIf { it.isNotEmpty() }?.let(::add)
        exercise.parameterType.trim().takeIf { it.isNotEmpty() }?.let(::add)
    }
    return if (parts.isEmpty()) {
        exercise.id
    } else {
        parts.joinToString(" - ")
    }
}
