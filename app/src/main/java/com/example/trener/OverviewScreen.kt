package com.example.trener

import android.app.DatePickerDialog
import android.graphics.Paint
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.mandatorySystemGesturesPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import com.example.trener.normalizedWeight
import com.example.trener.data.local.BodyWeightHistoryRepository
import com.example.trener.data.local.entity.BodyWeightEntryEntity
import com.example.trener.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

private enum class WeightRangeMode {
    SevenDays,
    ThirtyDays,
    All,
    Custom
}

private data class WeightChartRange(
    val startEpochDay: Long,
    val endEpochDay: Long
) {
    val dayCount: Long
        get() = (endEpochDay - startEpochDay + 1).coerceAtLeast(1L)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    activeWorkout: ActiveWorkoutState?,
    highlightedTrainingDay: Int?,
    databaseRefreshToken: Int,
    historyRefreshToken: Int,
    onBleEntryClick: () -> Unit,
    onExerciseComparisonClick: () -> Unit,
    onTrainingDayClick: (Int) -> Unit,
    onHistoryClick: () -> Unit,
    onHistoryDateClick: (Long) -> Unit
) {
    val uiContext = LocalContext.current
    val database = rememberTrenerDatabase(databaseRefreshToken)
    val bodyWeightRepository = remember(database) {
        BodyWeightHistoryRepository(database)
    }
    val coroutineScope = rememberCoroutineScope()
    val trainingDays = listOf(1, 2, 3)
    val activeTrainingDay = activeWorkout?.trainingDay
    var visibleMonthOffset by rememberSaveable { mutableIntStateOf(0) }
    var workoutsByEpochDay by remember { mutableStateOf<Map<Long, List<WorkoutSessionEntity>>>(emptyMap()) }
    var last30DaysWorkoutCount by remember { mutableIntStateOf(0) }
    var weightHistory by remember { mutableStateOf<List<BodyWeightEntryEntity>>(emptyList()) }
    var weightRangeMode by rememberSaveable { mutableStateOf(WeightRangeMode.All) }
    var weightRangeStartEpochDay by rememberSaveable { mutableLongStateOf(LocalDate.now().minusDays(6).toEpochDay()) }
    var weightRangeEndEpochDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var showWeightRangeSheet by remember { mutableStateOf(false) }
    var weightRangeDraftMode by rememberSaveable { mutableStateOf(WeightRangeMode.All) }
    var weightRangeDraftStartEpochDay by rememberSaveable { mutableLongStateOf(LocalDate.now().minusDays(6).toEpochDay()) }
    var weightRangeDraftEndEpochDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var weightRefreshToken by remember { mutableIntStateOf(0) }
    var weightChartWidthPx by remember { mutableIntStateOf(0) }
    var showAddWeightDialog by remember { mutableStateOf(false) }
    var showWeightManagementSheet by remember { mutableStateOf(false) }
    var showMonthYearPicker by remember { mutableStateOf(false) }
    var monthYearPickerYear by rememberSaveable { mutableIntStateOf(YearMonth.now().year) }
    var monthYearPickerMonth by rememberSaveable { mutableIntStateOf(YearMonth.now().monthValue) }
    var weightInput by rememberSaveable { mutableStateOf("") }
    var weightInputError by rememberSaveable { mutableStateOf<String?>(null) }
    var weightInputWasEdited by rememberSaveable { mutableStateOf(false) }
    var selectedWeightEntryEpochDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var weightDeletionTarget by remember { mutableStateOf<WeightDeletionTarget?>(null) }
    var deleteRangeStartEpochDay by rememberSaveable { mutableLongStateOf(LocalDate.now().minusDays(6).toEpochDay()) }
    var deleteRangeEndEpochDay by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var isWeightDeletionInProgress by remember { mutableStateOf(false) }

    val locale = remember { Locale.getDefault() }
    val firstDayOfWeek = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val visibleMonth = remember(visibleMonthOffset) {
        YearMonth.now().plusMonths(visibleMonthOffset.toLong())
    }
    val monthFormatter = remember(locale) {
        DateTimeFormatter.ofPattern("LLLL yyyy", locale)
    }
    val fullDateFormatter = remember(locale) {
        DateTimeFormatter.ofPattern("dd.MM.yyyy", locale)
    }
    val weekdayLabels = remember(locale, firstDayOfWeek) {
        buildWeekdayLabels(firstDayOfWeek, locale)
    }
    val calendarDays = remember(visibleMonth, firstDayOfWeek) {
        buildCalendarDays(visibleMonth, firstDayOfWeek)
    }
    val visibleWeightRange = remember(weightRangeStartEpochDay, weightRangeEndEpochDay) {
        WeightChartRange(weightRangeStartEpochDay, weightRangeEndEpochDay)
    }
    val visibleWeightEntries = remember(weightHistory, visibleWeightRange) {
        weightHistory.filter { entry ->
            entry.entryDateEpochDay in visibleWeightRange.startEpochDay..visibleWeightRange.endEpochDay
        }
    }
    val weightRangeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val weightManagementSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(database, databaseRefreshToken, historyRefreshToken, weightRefreshToken) {
        val loaded = withContext(Dispatchers.IO) {
            val sessions = database.workoutSessionDao().getAllSessions()
            val weights = database.bodyWeightHistoryDao().getWeightHistory()
            sessions to weights
        }

        val sessions = loaded.first
        weightHistory = loaded.second.map { it.normalizedWeight() }
        val todayEpochDay = LocalDate.now().toEpochDay()
        val windowStartEpochDay = todayEpochDay - 29
        workoutsByEpochDay = sessions.groupBy { session ->
            session.toWorkoutLocalDate().toEpochDay()
        }
        last30DaysWorkoutCount = sessions.count { session ->
            val sessionEpochDay = session.toWorkoutLocalDate().toEpochDay()
            sessionEpochDay in windowStartEpochDay..todayEpochDay
        }
    }

    LaunchedEffect(weightHistory, weightRangeMode) {
        val resolvedRange = resolveWeightRange(
            records = weightHistory,
            mode = weightRangeMode,
            currentRange = visibleWeightRange
        )
        weightRangeStartEpochDay = resolvedRange.startEpochDay
        weightRangeEndEpochDay = resolvedRange.endEpochDay
    }

    LaunchedEffect(showAddWeightDialog) {
        if (!showAddWeightDialog) {
            return@LaunchedEffect
        }

        weightInputWasEdited = false
        val lastWeight = withContext(Dispatchers.IO) {
            bodyWeightRepository.getLastWeight()
        }
        if (!weightInputWasEdited) {
            weightInput = lastWeight?.weightKg?.let(::formatBodyWeightKg).orEmpty()
        }
        weightInputError = null
    }

    fun openWeightRangeSheet() {
        weightRangeDraftMode = weightRangeMode
        weightRangeDraftStartEpochDay = weightRangeStartEpochDay
        weightRangeDraftEndEpochDay = weightRangeEndEpochDay
        showWeightRangeSheet = true
    }

    fun openWeightManagementSheet() {
        val firstWeightEntry = weightHistory.firstOrNull()
        selectedWeightEntryEpochDay = firstWeightEntry?.entryDateEpochDay
        deleteRangeStartEpochDay = firstWeightEntry?.entryDateEpochDay
            ?: LocalDate.now().toEpochDay()
        deleteRangeEndEpochDay = weightHistory.lastOrNull()?.entryDateEpochDay
            ?: LocalDate.now().toEpochDay()
        showWeightManagementSheet = true
    }

    fun applyWeightRangeSelection(
        mode: WeightRangeMode,
        startEpochDay: Long,
        endEpochDay: Long
    ) {
        val resolvedRange = when (mode) {
            WeightRangeMode.SevenDays,
            WeightRangeMode.ThirtyDays,
            WeightRangeMode.All -> resolveWeightRange(
                records = weightHistory,
                mode = mode,
                currentRange = visibleWeightRange
            )
            WeightRangeMode.Custom -> resolveClampedWeightRange(
                startEpochDay = startEpochDay,
                endEpochDay = endEpochDay,
                records = weightHistory
            )
        }

        weightRangeMode = mode
        weightRangeStartEpochDay = resolvedRange.startEpochDay
        weightRangeEndEpochDay = resolvedRange.endEpochDay
    }

    fun requestWeightDeletion(target: WeightDeletionTarget) {
        weightDeletionTarget = target
    }

    fun performWeightDeletion(target: WeightDeletionTarget) {
        coroutineScope.launch {
            isWeightDeletionInProgress = true
            try {
                runCatching {
                    withContext(Dispatchers.IO) {
                        when (target) {
                            WeightDeletionTarget.DeleteAll -> {
                                database.bodyWeightHistoryDao().deleteAllWeightHistory()
                            }
                            is WeightDeletionTarget.DeleteSingle -> {
                                database.bodyWeightHistoryDao().deleteWeightEntryByDate(
                                    target.entryDateEpochDay
                                )
                            }
                            is WeightDeletionTarget.DeleteRange -> {
                                val normalized = normalizeWeightRange(
                                    startEpochDay = target.startEpochDay,
                                    endEpochDay = target.endEpochDay
                                )
                                database.bodyWeightHistoryDao().deleteWeightEntriesInRange(
                                    normalized.startEpochDay,
                                    normalized.endEpochDay
                                )
                            }
                        }
                    }
                }.onSuccess {
                    weightRefreshToken++
                    weightRangeMode = WeightRangeMode.All
                    showWeightManagementSheet = false
                    selectedWeightEntryEpochDay = null
                    if (target is WeightDeletionTarget.DeleteAll) {
                        weightRangeStartEpochDay = LocalDate.now().minusDays(6).toEpochDay()
                        weightRangeEndEpochDay = LocalDate.now().toEpochDay()
                    }
                }.onFailure { throwable ->
                    Log.e(logTag, "Weight deletion failed", throwable)
                }
            } finally {
                isWeightDeletionInProgress = false
            }
            weightDeletionTarget = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.overview_title)) },
                actions = {
                    TextButton(onClick = onExerciseComparisonClick) {
                        Text(stringResource(R.string.exercise_comparison_open_button))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                trainingDays.forEach { trainingDay ->
                    val isActiveWorkoutButton = activeTrainingDay == trainingDay
                    val isHighlightedNextTrainingDay = highlightedTrainingDay == trainingDay
                    val buttonContainerColor = when {
                        isActiveWorkoutButton -> ActiveDayContainerColor
                        isHighlightedNextTrainingDay -> RecommendedDayContainerColor
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                    val buttonContentColor = when {
                        isActiveWorkoutButton -> ActiveDayContentColor
                        isHighlightedNextTrainingDay -> RecommendedDayContentColor
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val buttonBorder = when {
                        isActiveWorkoutButton -> BorderStroke(1.5.dp, ActiveDayBorderColor)
                        isHighlightedNextTrainingDay -> BorderStroke(1.5.dp, RecommendedDayBorderColor)
                        else -> null
                    }

                    Button(
                        onClick = { onTrainingDayClick(trainingDay) },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = buttonBorder,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonContainerColor,
                            contentColor = buttonContentColor,
                            disabledContainerColor = buttonContainerColor,
                            disabledContentColor = buttonContentColor
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.training_day_title, trainingDay),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.overview_calendar_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "$last30DaysWorkoutCount/30",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { visibleMonthOffset -= 1 },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("‹")
                        }
                        TextButton(
                            onClick = {
                                monthYearPickerYear = visibleMonth.year
                                monthYearPickerMonth = visibleMonth.monthValue
                                showMonthYearPicker = true
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = visibleMonth.atDay(1).format(monthFormatter),
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        TextButton(
                            onClick = { visibleMonthOffset += 1 },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("›")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        weekdayLabels.forEach { weekdayLabel ->
                            Text(
                                text = weekdayLabel,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        calendarDays.chunked(7).forEach { week ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                week.forEach { day ->
                                    val workoutSessions = day?.let {
                                        workoutsByEpochDay[it.toEpochDay()].orEmpty()
                                    }.orEmpty()
                                    CalendarDayCell(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(32.dp),
                                        date = day,
                                        workoutSessions = workoutSessions,
                                        highlightedTrainingDay = highlightedTrainingDay,
                                        activeTrainingDay = activeTrainingDay,
                                        onClick = day?.takeIf { workoutSessions.isNotEmpty() }?.let {
                                            { onHistoryDateClick(it.toEpochDay()) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { openWeightRangeSheet() },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.weight_range_title),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        OutlinedButton(
                            onClick = { openWeightManagementSheet() },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.weight_manage_button),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBleEntryClick,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.weight_ble_button),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        Button(
                            onClick = { showAddWeightDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.weight_add_button),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    WeightChart(
                        entries = visibleWeightEntries,
                        range = visibleWeightRange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(146.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .padding(8.dp)
                            .pointerInput(
                                weightHistory,
                                weightChartWidthPx,
                                weightRangeStartEpochDay,
                                weightRangeEndEpochDay
                            ) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    if (weightHistory.isEmpty() || weightChartWidthPx <= 0) {
                                        return@detectTransformGestures
                                    }

                                    val currentRange = visibleWeightRange
                                    val domainStart = weightHistory.first().entryDateEpochDay
                                    val domainEnd = weightHistory.last().entryDateEpochDay
                                    val domainLength = (domainEnd - domainStart + 1).coerceAtLeast(1L)
                                    val currentLength = currentRange.dayCount
                                    val safeZoom = zoom.coerceIn(0.5f, 2.0f)
                                    val zoomedLength = (currentLength / safeZoom)
                                        .roundToLong()
                                        .coerceIn(1L, domainLength)
                                    val panDays = ((-pan.x / weightChartWidthPx.toFloat()) * currentLength)
                                        .roundToLong()
                                    val centerEpochDay = currentRange.startEpochDay + (currentLength - 1) / 2.0
                                    val shiftedCenter = centerEpochDay + panDays
                                    val newStart = (shiftedCenter - (zoomedLength - 1) / 2.0).roundToLong()
                                    val newEnd = newStart + zoomedLength - 1
                                    val resolved = fitRangeToDomain(
                                        startEpochDay = newStart,
                                        endEpochDay = newEnd,
                                        domainStartEpochDay = domainStart,
                                        domainEndEpochDay = domainEnd
                                    )

                                    weightRangeMode = WeightRangeMode.Custom
                                    weightRangeStartEpochDay = resolved.startEpochDay
                                    weightRangeEndEpochDay = resolved.endEpochDay
                                }
                            },
                        onSizeChanged = { weightChartWidthPx = it.width }
                    )
                }
            }
            Button(
                onClick = onHistoryClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
            ) {
                Text(stringResource(R.string.history_screen_title))
            }
        }
    }

    if (showWeightRangeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showWeightRangeSheet = false },
            sheetState = weightRangeSheetState
        ) {
            WeightRangeSheet(
                selectedMode = weightRangeDraftMode,
                startEpochDay = weightRangeDraftStartEpochDay,
                endEpochDay = weightRangeDraftEndEpochDay,
                dateFormatter = fullDateFormatter,
                onPresetSelected = { preset ->
                    weightRangeDraftMode = preset
                    if (preset != WeightRangeMode.Custom) {
                        val resolvedRange = resolveWeightRange(
                            records = weightHistory,
                            mode = preset,
                            currentRange = visibleWeightRange
                        )
                        weightRangeDraftStartEpochDay = resolvedRange.startEpochDay
                        weightRangeDraftEndEpochDay = resolvedRange.endEpochDay
                    }
                },
                onStartDateClick = {
                    weightRangeDraftMode = WeightRangeMode.Custom
                    openDatePicker(
                        context = uiContext,
                        initialEpochDay = weightRangeDraftStartEpochDay,
                        onDatePicked = { selectedEpochDay ->
                            weightRangeDraftStartEpochDay = selectedEpochDay
                        }
                    )
                },
                onEndDateClick = {
                    weightRangeDraftMode = WeightRangeMode.Custom
                    openDatePicker(
                        context = uiContext,
                        initialEpochDay = weightRangeDraftEndEpochDay,
                        onDatePicked = { selectedEpochDay ->
                            weightRangeDraftEndEpochDay = selectedEpochDay
                        }
                    )
                },
                onConfirm = {
                    applyWeightRangeSelection(
                        mode = weightRangeDraftMode,
                        startEpochDay = weightRangeDraftStartEpochDay,
                        endEpochDay = weightRangeDraftEndEpochDay
                    )
                    showWeightRangeSheet = false
                },
                onDismiss = { showWeightRangeSheet = false }
            )
        }
    }

    if (showWeightManagementSheet) {
        ModalBottomSheet(
            onDismissRequest = { showWeightManagementSheet = false },
            sheetState = weightManagementSheetState
        ) {
            WeightManagementSheet(
                records = weightHistory,
                selectedEntryEpochDay = selectedWeightEntryEpochDay,
                deleteRangeStartEpochDay = deleteRangeStartEpochDay,
                deleteRangeEndEpochDay = deleteRangeEndEpochDay,
                dateFormatter = fullDateFormatter,
                isDeletionInProgress = isWeightDeletionInProgress,
                onSelectEntry = { selectedWeightEntryEpochDay = it },
                onDeleteAll = {
                    requestWeightDeletion(WeightDeletionTarget.DeleteAll)
                    showWeightManagementSheet = false
                },
                onDeleteSelected = {
                    selectedWeightEntryEpochDay?.let { selectedEntryEpochDay ->
                        requestWeightDeletion(WeightDeletionTarget.DeleteSingle(selectedEntryEpochDay))
                        showWeightManagementSheet = false
                    }
                },
                onDeleteRangeStartClick = {
                    openDatePicker(
                        context = uiContext,
                        initialEpochDay = deleteRangeStartEpochDay,
                        onDatePicked = { selectedEpochDay ->
                            deleteRangeStartEpochDay = selectedEpochDay
                        }
                    )
                },
                onDeleteRangeEndClick = {
                    openDatePicker(
                        context = uiContext,
                        initialEpochDay = deleteRangeEndEpochDay,
                        onDatePicked = { selectedEpochDay ->
                            deleteRangeEndEpochDay = selectedEpochDay
                        }
                    )
                },
                onDeleteRange = {
                    requestWeightDeletion(
                        WeightDeletionTarget.DeleteRange(
                            startEpochDay = deleteRangeStartEpochDay,
                            endEpochDay = deleteRangeEndEpochDay
                        )
                    )
                    showWeightManagementSheet = false
                },
                onDismiss = { showWeightManagementSheet = false }
            )
        }
    }

    if (showMonthYearPicker) {
        ModalBottomSheet(
            onDismissRequest = { showMonthYearPicker = false }
        ) {
            MonthYearPickerSheet(
                year = monthYearPickerYear,
                selectedMonth = monthYearPickerMonth,
                locale = Locale.getDefault(),
                onPreviousYear = { monthYearPickerYear -= 1 },
                onNextYear = { monthYearPickerYear += 1 },
                onMonthSelected = { selectedMonth ->
                    monthYearPickerMonth = selectedMonth
                    val selectedMonthYear = YearMonth.of(monthYearPickerYear, selectedMonth)
                    visibleMonthOffset = ChronoUnit.MONTHS.between(
                        YearMonth.now(),
                        selectedMonthYear
                    ).toInt()
                    showMonthYearPicker = false
                },
                onDismiss = { showMonthYearPicker = false }
            )
        }
    }

    if (showAddWeightDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddWeightDialog = false
                weightInputError = null
                weightInput = ""
            },
            title = { Text(stringResource(R.string.weight_add_dialog_title)) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.weight_add_today_label,
                            LocalDate.now().format(fullDateFormatter)
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextField(
                        value = weightInput,
                        onValueChange = {
                            weightInput = it
                            weightInputWasEdited = true
                            weightInputError = null
                        },
                        singleLine = true,
                        isError = weightInputError != null,
                        label = { Text(stringResource(R.string.weight_add_input_label)) },
                        placeholder = { Text(stringResource(R.string.weight_add_input_placeholder)) },
                        colors = TextFieldDefaults.colors()
                    )
                    weightInputError?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsedWeight = weightInput.trim()
                            .replace(',', '.')
                            .toDoubleOrNull()
                        if (parsedWeight == null || parsedWeight <= 0.0) {
                            weightInputError = uiContext.getString(R.string.weight_add_invalid_error)
                            return@TextButton
                        }

                        coroutineScope.launch {
                            withContext(Dispatchers.IO) {
                                bodyWeightRepository.saveWeightForToday(parsedWeight)
                            }
                            weightInput = ""
                            weightInputError = null
                            weightInputWasEdited = false
                            showAddWeightDialog = false
                            weightRangeMode = WeightRangeMode.All
                            weightRefreshToken++
                        }
                    }
                ) {
                    Text(stringResource(R.string.weight_add_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddWeightDialog = false
                        weightInputError = null
                        weightInput = ""
                        weightInputWasEdited = false
                    }
                ) {
                    Text(stringResource(R.string.weight_add_cancel))
                }
            }
        )
    }

    weightDeletionTarget?.let { target ->
        val confirmationText = when (target) {
            WeightDeletionTarget.DeleteAll -> stringResource(
                R.string.weight_management_delete_all_confirm_message
            )
            is WeightDeletionTarget.DeleteSingle -> {
                val entry = weightHistory.firstOrNull {
                    it.entryDateEpochDay == target.entryDateEpochDay
                }
                val entryDateText = entry?.entryDateEpochDay?.let(::safeLocalDateOfEpochDay)
                    ?.format(fullDateFormatter)
                    ?: safeLocalDateOfEpochDay(target.entryDateEpochDay).format(fullDateFormatter)
                val weightText = entry?.weightKg?.let(::formatBodyWeightKg)
                    ?: stringResource(R.string.not_specified)
                stringResource(
                    R.string.weight_management_delete_single_confirm_message,
                    entryDateText,
                    weightText
                )
            }
            is WeightDeletionTarget.DeleteRange -> {
                val normalized = normalizeWeightRange(
                    startEpochDay = target.startEpochDay,
                    endEpochDay = target.endEpochDay
                )
                stringResource(
                    R.string.weight_management_delete_range_confirm_message,
                    safeLocalDateOfEpochDay(normalized.startEpochDay).format(fullDateFormatter),
                    safeLocalDateOfEpochDay(normalized.endEpochDay).format(fullDateFormatter)
                )
            }
        }

        AlertDialog(
            onDismissRequest = {
                if (!isWeightDeletionInProgress) {
                    weightDeletionTarget = null
                }
            },
            title = {
                Text(
                    when (target) {
                        WeightDeletionTarget.DeleteAll ->
                            stringResource(R.string.weight_management_delete_all_confirm_title)
                        is WeightDeletionTarget.DeleteSingle ->
                            stringResource(R.string.weight_management_delete_single_confirm_title)
                        is WeightDeletionTarget.DeleteRange ->
                            stringResource(R.string.weight_management_delete_range_confirm_title)
                    }
                )
            },
            text = { Text(confirmationText) },
            confirmButton = {
                TextButton(
                    onClick = { performWeightDeletion(target) },
                    enabled = !isWeightDeletionInProgress
                ) {
                    Text(stringResource(R.string.weight_management_confirm_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { weightDeletionTarget = null },
                    enabled = !isWeightDeletionInProgress
                ) {
                    Text(stringResource(R.string.weight_management_cancel_delete))
                }
            }
        )
    }
}

private sealed interface WeightDeletionTarget {
    data object DeleteAll : WeightDeletionTarget
    data class DeleteSingle(val entryDateEpochDay: Long) : WeightDeletionTarget
    data class DeleteRange(val startEpochDay: Long, val endEpochDay: Long) : WeightDeletionTarget
}

@Composable
private fun WeightRangeSheet(
    selectedMode: WeightRangeMode,
    startEpochDay: Long,
    endEpochDay: Long,
    dateFormatter: DateTimeFormatter,
    onPresetSelected: (WeightRangeMode) -> Unit,
    onStartDateClick: () -> Unit,
    onEndDateClick: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val hasInvalidManualRange = startEpochDay > endEpochDay
    val selectedRangeLabel = when (selectedMode) {
        WeightRangeMode.SevenDays -> stringResource(R.string.weight_range_7_days)
        WeightRangeMode.ThirtyDays -> stringResource(R.string.weight_range_30_days)
        WeightRangeMode.All -> stringResource(R.string.weight_range_all)
        WeightRangeMode.Custom -> stringResource(R.string.weight_range_custom)
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
                text = stringResource(R.string.weight_range_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(R.string.weight_range_selected, selectedRangeLabel),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RangePresetButton(
                    label = stringResource(R.string.weight_range_7_days),
                    selected = selectedMode == WeightRangeMode.SevenDays,
                    modifier = Modifier.weight(1f),
                    onClick = { onPresetSelected(WeightRangeMode.SevenDays) }
                )
                RangePresetButton(
                    label = stringResource(R.string.weight_range_30_days),
                    selected = selectedMode == WeightRangeMode.ThirtyDays,
                    modifier = Modifier.weight(1f),
                    onClick = { onPresetSelected(WeightRangeMode.ThirtyDays) }
                )
                RangePresetButton(
                    label = stringResource(R.string.weight_range_all),
                    selected = selectedMode == WeightRangeMode.All,
                    modifier = Modifier.weight(1f),
                    onClick = { onPresetSelected(WeightRangeMode.All) }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.weight_range_custom_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onStartDateClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            R.string.weight_range_from,
                            safeLocalDateOfEpochDay(startEpochDay).format(dateFormatter)
                        )
                    )
                }
                OutlinedButton(
                    onClick = onEndDateClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            R.string.weight_range_to,
                            safeLocalDateOfEpochDay(endEpochDay).format(dateFormatter)
                        )
                    )
                }
                if (hasInvalidManualRange) {
                    Text(
                        text = stringResource(R.string.weight_range_invalid_note),
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
                .mandatorySystemGesturesPadding()
                .padding(top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.weight_range_cancel))
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.weight_range_apply))
            }
        }
    }
}

@Composable
private fun WeightManagementSheet(
    records: List<BodyWeightEntryEntity>,
    selectedEntryEpochDay: Long?,
    deleteRangeStartEpochDay: Long,
    deleteRangeEndEpochDay: Long,
    dateFormatter: DateTimeFormatter,
    isDeletionInProgress: Boolean,
    onSelectEntry: (Long) -> Unit,
    onDeleteAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onDeleteRangeStartClick: () -> Unit,
    onDeleteRangeEndClick: () -> Unit,
    onDeleteRange: () -> Unit,
    onDismiss: () -> Unit
) {
    val selectedRecord = selectedEntryEpochDay?.let { epochDay ->
        records.firstOrNull { it.entryDateEpochDay == epochDay }
    }
    val normalizedDeleteRange = normalizeWeightRange(
        startEpochDay = deleteRangeStartEpochDay,
        endEpochDay = deleteRangeEndEpochDay
    )

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.weight_management_title),
                style = MaterialTheme.typography.titleLarge
            )
        }

        item {
            Button(
                onClick = onDeleteAll,
                enabled = records.isNotEmpty() && !isDeletionInProgress,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.weight_management_delete_all))
            }
        }

        if (records.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.weight_management_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(records, key = { it.entryDateEpochDay }) { entry ->
                val isSelected = entry.entryDateEpochDay == selectedEntryEpochDay
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onSelectEntry(entry.entryDateEpochDay) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = safeLocalDateOfEpochDay(entry.entryDateEpochDay).format(dateFormatter),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = "${formatBodyWeightKg(entry.weightKg)} kg",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = onDeleteSelected,
                    enabled = selectedRecord != null && !isDeletionInProgress,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.weight_management_delete_selected))
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onDeleteRangeStartClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(
                        R.string.weight_range_from,
                        safeLocalDateOfEpochDay(normalizedDeleteRange.startEpochDay).format(dateFormatter)
                    )
                )
            }
        }

        item {
            OutlinedButton(
                onClick = onDeleteRangeEndClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(
                        R.string.weight_range_to,
                        safeLocalDateOfEpochDay(normalizedDeleteRange.endEpochDay).format(dateFormatter)
                    )
                )
            }
        }

        item {
            Button(
                onClick = onDeleteRange,
                enabled = records.isNotEmpty() && !isDeletionInProgress,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.weight_management_delete_range))
            }
        }

        item {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isDeletionInProgress
            ) {
                Text(stringResource(R.string.weight_range_cancel))
            }
        }
    }
}

@Composable
private fun MonthYearPickerSheet(
    year: Int,
    selectedMonth: Int,
    locale: Locale,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onMonthSelected: (Int) -> Unit,
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
            text = stringResource(R.string.calendar_month_year_picker_title),
            style = MaterialTheme.typography.titleMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onPreviousYear) {
                Text(stringResource(R.string.calendar_month_year_picker_previous_year))
            }
            Text(
                text = year.toString(),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            TextButton(onClick = onNextYear) {
                Text(stringResource(R.string.calendar_month_year_picker_next_year))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Month.values().asList().chunked(3).forEach { monthRow ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    monthRow.forEach { month ->
                        val isSelected = month.value == selectedMonth
                        if (isSelected) {
                            Button(
                                onClick = { onMonthSelected(month.value) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = month.getDisplayName(TextStyle.SHORT_STANDALONE, locale),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onMonthSelected(month.value) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    text = month.getDisplayName(TextStyle.SHORT_STANDALONE, locale),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }

        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.calendar_month_year_picker_cancel))
        }
    }
}

@Composable
private fun CalendarDayCell(
    modifier: Modifier,
    date: LocalDate?,
    workoutSessions: List<WorkoutSessionEntity>,
    highlightedTrainingDay: Int?,
    activeTrainingDay: Int?,
    onClick: (() -> Unit)?
) {
    val workoutCount = workoutSessions.size
    val hasWorkouts = date != null && workoutCount > 0
    val isActiveTrainingDay = date != null && activeTrainingDay != null &&
        workoutSessions.any { it.trainingDay == activeTrainingDay }
    val isRecommendedTrainingDay = date != null && highlightedTrainingDay != null &&
        workoutSessions.any { it.trainingDay == highlightedTrainingDay }
    val containerColor = when {
        isActiveTrainingDay -> ActiveDayContainerColor
        isRecommendedTrainingDay -> RecommendedDayContainerColor
        hasWorkouts -> MaterialTheme.colorScheme.primaryContainer
        date == null -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    }
    val contentColor = when {
        isActiveTrainingDay -> ActiveDayContentColor
        isRecommendedTrainingDay -> RecommendedDayContentColor
        hasWorkouts -> MaterialTheme.colorScheme.onPrimaryContainer
        date == null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val markerText = when {
        workoutCount == 1 -> workoutSessions.firstOrNull()?.trainingDay?.toString()
        workoutCount > 1 -> "*"
        else -> null
    }

    val content: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            date?.let {
                Text(
                    text = it.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    modifier = Modifier.align(Alignment.TopStart)
                )
            }
            markerText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }

    if (hasWorkouts && onClick != null) {
        Card(
            modifier = modifier,
            onClick = onClick,
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor)
        ) {
            content()
        }
    } else {
        Card(
            modifier = modifier,
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor)
        ) {
            content()
        }
    }
}

@Composable
private fun RangePresetButton(
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
private fun WeightChart(
    entries: List<BodyWeightEntryEntity>,
    range: WeightChartRange,
    modifier: Modifier = Modifier,
    onSizeChanged: (IntSize) -> Unit
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
        val points = entries
        if (points.isEmpty()) {
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
        val yLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor.toArgb()
            textSize = axisTextSize
            textAlign = Paint.Align.RIGHT
        }
        val xLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor.toArgb()
            textSize = axisTextSize
            textAlign = Paint.Align.CENTER
        }
        val minYTickCount = 3
        val maxYTickCount = if (availableHeight < 128f) 3 else 4
        val yTicks = buildWeightYAxisTicks(
            points = points,
            desiredTickCount = maxYTickCount.coerceAtLeast(minYTickCount)
        )
        val yLabelWidth = yTicks.maxOfOrNull { tick ->
            yLabelPaint.measureText(formatWeightAxisLabel(tick.value, tick.step))
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
        val xLabelTickCount = buildWeightXAxisTicks(
            range = range,
            availableWidthPx = plotWidth,
            minLabelSpacingPx = 60.dp.toPx()
        )
        val xLabelFontMetrics = xLabelPaint.fontMetrics
        val xLabelBaseline = plotBottom + 4.dp.toPx() - xLabelFontMetrics.ascent
        val minWeight = yTicks.first().value
        val maxWeight = yTicks.last().value
        val weightRange = max(0.1, maxWeight - minWeight)
        val chartPoints = points.map { point ->
            val xFraction = if (abs(end - start) < 0.0001) {
                0.5f
            } else {
                ((point.entryDateEpochDay - start) / (end - start)).toFloat().coerceIn(0f, 1f)
            }
            val yFraction = ((point.weightKg - minWeight) / weightRange).toFloat().coerceIn(0f, 1f)
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
            val y = plotBottom - ((tick.value - minWeight) / weightRange).toFloat() * plotHeight
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
                    formatWeightAxisLabel(tick.value, tick.step),
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
                    formatWeightAxisDateLabel(epochDay, range),
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
                style = Stroke(width = 2.5.dp.toPx())
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

private fun updateWeightRangeByPreset(
    windowSizeDays: Int?,
    records: List<BodyWeightEntryEntity>
): WeightChartRange {
    return if (windowSizeDays == null) {
        resolveWeightRange(
            records = records,
            mode = WeightRangeMode.All,
            currentRange = WeightChartRange(0L, 0L)
        )
    } else {
        buildRangeForWindowSize(records = records, windowSizeDays = windowSizeDays)
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

private fun resolveWeightRange(
    records: List<BodyWeightEntryEntity>,
    mode: WeightRangeMode,
    currentRange: WeightChartRange
): WeightChartRange {
    if (records.isEmpty()) {
        val today = LocalDate.now().toEpochDay()
        return when (mode) {
            WeightRangeMode.SevenDays -> WeightChartRange(today - 6, today)
            WeightRangeMode.ThirtyDays -> WeightChartRange(today - 29, today)
            WeightRangeMode.All, WeightRangeMode.Custom -> WeightChartRange(today - 6, today)
        }
    }

    val domainStart = records.first().entryDateEpochDay
    val domainEnd = records.last().entryDateEpochDay
    return when (mode) {
        WeightRangeMode.SevenDays -> buildRangeForWindowSize(records, 7)
        WeightRangeMode.ThirtyDays -> buildRangeForWindowSize(records, 30)
        WeightRangeMode.All -> WeightChartRange(domainStart, domainEnd)
        WeightRangeMode.Custom -> fitRangeToDomain(
            startEpochDay = currentRange.startEpochDay,
            endEpochDay = currentRange.endEpochDay,
            domainStartEpochDay = domainStart,
            domainEndEpochDay = domainEnd
        )
    }
}

private fun buildRangeForWindowSize(
    records: List<BodyWeightEntryEntity>,
    windowSizeDays: Int
): WeightChartRange {
    if (records.isEmpty()) {
        val today = LocalDate.now().toEpochDay()
        val start = today - (windowSizeDays - 1)
        return WeightChartRange(start, today)
    }

    val domainStart = records.first().entryDateEpochDay
    val domainEnd = records.last().entryDateEpochDay
    val desiredLength = windowSizeDays.toLong().coerceAtMost(domainEnd - domainStart + 1)
    return fitRangeToDomain(
        startEpochDay = domainEnd - desiredLength + 1,
        endEpochDay = domainEnd,
        domainStartEpochDay = domainStart,
        domainEndEpochDay = domainEnd
    )
}

private fun fitRangeToDomain(
    startEpochDay: Long,
    endEpochDay: Long,
    domainStartEpochDay: Long,
    domainEndEpochDay: Long
): WeightChartRange {
    val normalizedStart = min(startEpochDay, endEpochDay)
    val normalizedEnd = max(startEpochDay, endEpochDay)
    val domainLength = (domainEndEpochDay - domainStartEpochDay + 1).coerceAtLeast(1L)
    val desiredLength = (normalizedEnd - normalizedStart + 1).coerceIn(1L, domainLength)

    var clampedStart = normalizedStart.coerceIn(domainStartEpochDay, domainEndEpochDay)
    var clampedEnd = (clampedStart + desiredLength - 1).coerceAtMost(domainEndEpochDay)
    clampedStart = (clampedEnd - desiredLength + 1).coerceAtLeast(domainStartEpochDay)
    clampedEnd = (clampedStart + desiredLength - 1).coerceAtMost(domainEndEpochDay)

    return WeightChartRange(clampedStart, clampedEnd)
}

private fun resolveClampedWeightRange(
    startEpochDay: Long,
    endEpochDay: Long,
    records: List<BodyWeightEntryEntity>
): WeightChartRange {
    val normalizedRange = normalizeWeightRange(startEpochDay, endEpochDay)
    return if (records.isEmpty()) {
        normalizedRange
    } else {
        fitRangeToDomain(
            startEpochDay = normalizedRange.startEpochDay,
            endEpochDay = normalizedRange.endEpochDay,
            domainStartEpochDay = records.first().entryDateEpochDay,
            domainEndEpochDay = records.last().entryDateEpochDay
        )
    }
}

private fun normalizeWeightRange(
    startEpochDay: Long,
    endEpochDay: Long
): WeightChartRange {
    return WeightChartRange(min(startEpochDay, endEpochDay), max(startEpochDay, endEpochDay))
}

private fun buildCalendarDays(
    month: YearMonth,
    firstDayOfWeek: DayOfWeek
): List<LocalDate?> {
    val leadingEmptyCells = (
        month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7
    ) % 7
    return buildList {
        repeat(leadingEmptyCells) {
            add(null)
        }
        for (dayOfMonth in 1..month.lengthOfMonth()) {
            add(month.atDay(dayOfMonth))
        }
        while (size % 7 != 0) {
            add(null)
        }
    }
}

private fun buildWeekdayLabels(
    firstDayOfWeek: DayOfWeek,
    locale: Locale
): List<String> {
    return (0 until 7).map { offset ->
        firstDayOfWeek.plus(offset.toLong())
            .getDisplayName(TextStyle.SHORT_STANDALONE, locale)
    }
}

private data class WeightAxisTick(
    val value: Double,
    val step: Double
)

private fun buildWeightXAxisTicks(
    range: WeightChartRange,
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

private fun buildWeightYAxisTicks(
    points: List<BodyWeightEntryEntity>,
    desiredTickCount: Int
): List<WeightAxisTick> {
    val minWeight = points.minOf { it.weightKg }
    val maxWeight = points.maxOf { it.weightKg }

    if (abs(maxWeight - minWeight) < 0.0001) {
        val center = minWeight
        val step = niceWeightStep(maxOf(1.0, abs(center) * 0.1))
        return listOf(
            WeightAxisTick(center - step, step),
            WeightAxisTick(center, step),
            WeightAxisTick(center + step, step)
        )
    }

    val intervals = max(2, desiredTickCount - 1)
    val niceRange = niceWeightStep(maxWeight - minWeight, round = false)
    val step = niceWeightStep(niceRange / intervals.toDouble())
    var tickStart = floor(minWeight / step) * step
    var tickEnd = ceil(maxWeight / step) * step
    if (tickEnd - tickStart < step * 1.5) {
        tickStart -= step
        tickEnd += step
    }

    val ticks = buildList {
        var value = tickStart
        while (value <= tickEnd + step / 2.0) {
            add(WeightAxisTick(value, step))
            value += step
        }
    }

    return if (ticks.size <= desiredTickCount + 1) {
        ticks
    } else {
        ticks.filterIndexed { index, _ -> index % 2 == 0 }.ifEmpty { ticks.take(desiredTickCount) }
    }
}

private fun niceWeightStep(value: Double, round: Boolean = true): Double {
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

private fun formatWeightAxisLabel(value: Double, step: Double): String {
    val normalizedStep = abs(step)
    return when {
        normalizedStep >= 1.0 || abs(value - value.roundToLong().toDouble()) < 0.05 -> {
            value.roundToLong().toString()
        }
        normalizedStep >= 0.5 -> String.format(Locale.getDefault(), "%.1f", value)
        else -> String.format(Locale.getDefault(), "%.2f", value)
    }
}

private fun formatWeightAxisDateLabel(epochDay: Long, range: WeightChartRange): String {
    val date = safeLocalDateOfEpochDay(epochDay)
    return if (range.dayCount <= 60L) {
        date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    } else {
        date.format(DateTimeFormatter.ofPattern("MMM yy", Locale.getDefault()))
    }
}

private val RecommendedDayContainerColor = Color(0xFFDDF4D8)
private val RecommendedDayContentColor = Color(0xFF2E7D32)
private val RecommendedDayBorderColor = Color(0xFF4E9A55)

private val ActiveDayContainerColor = Color(0xFFFEDAD5)
private val ActiveDayContentColor = Color(0xFFB42318)
private val ActiveDayBorderColor = Color(0xFFD14343)
