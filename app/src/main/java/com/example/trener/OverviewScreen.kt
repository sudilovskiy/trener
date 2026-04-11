package com.example.trener

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.trener.data.local.TrenerDatabaseProvider
import com.example.trener.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    activeWorkout: ActiveWorkoutState?,
    highlightedTrainingDay: Int?,
    databaseRefreshToken: Int,
    historyRefreshToken: Int,
    onTrainingDayClick: (Int) -> Unit,
    onHistoryClick: () -> Unit,
    onHistoryDateClick: (Long) -> Unit
) {
    val context = LocalContext.current.applicationContext
    val database = remember(context, databaseRefreshToken) {
        TrenerDatabaseProvider.getInstance(context)
    }
    val trainingDays = listOf(1, 2, 3)
    val activeTrainingDay = activeWorkout?.trainingDay
    var visibleMonthOffset by rememberSaveable { mutableIntStateOf(0) }
    var workoutsByEpochDay by remember { mutableStateOf<Map<Long, List<WorkoutSessionEntity>>>(emptyMap()) }
    var last30DaysWorkoutCount by remember { mutableIntStateOf(0) }
    val locale = remember { Locale.getDefault() }
    val firstDayOfWeek = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val visibleMonth = remember(visibleMonthOffset) {
        YearMonth.now().plusMonths(visibleMonthOffset.toLong())
    }
    val monthFormatter = remember(locale) {
        DateTimeFormatter.ofPattern("LLLL yyyy", locale)
    }
    val weekdayLabels = remember(locale, firstDayOfWeek) {
        buildWeekdayLabels(firstDayOfWeek, locale)
    }
    val calendarDays = remember(visibleMonth, firstDayOfWeek) {
        buildCalendarDays(visibleMonth, firstDayOfWeek)
    }

    LaunchedEffect(database, databaseRefreshToken, historyRefreshToken) {
        val sessions = withContext(Dispatchers.IO) {
            database.workoutSessionDao().getAllSessions()
        }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.overview_title)) }
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    trainingDays.forEach { trainingDay ->
                        val isActiveWorkoutButton = activeTrainingDay == trainingDay
                        val isHighlightedNextTrainingDay = highlightedTrainingDay == trainingDay
                        val buttonContainerColor = when {
                            isActiveWorkoutButton -> MaterialTheme.colorScheme.primaryContainer
                            isHighlightedNextTrainingDay -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        val buttonContentColor = when {
                            isActiveWorkoutButton -> MaterialTheme.colorScheme.onPrimaryContainer
                            isHighlightedNextTrainingDay -> MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Button(
                            onClick = { onTrainingDayClick(trainingDay) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            shape = MaterialTheme.shapes.large,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = buttonContainerColor,
                                contentColor = buttonContentColor,
                                disabledContainerColor = buttonContainerColor,
                                disabledContentColor = buttonContentColor
                            )
                        ) {
                            Text(
                                text = "День $trainingDay",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.overview_calendar_title),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "$last30DaysWorkoutCount/30",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { visibleMonthOffset -= 1 }
                            ) {
                                Text("<")
                            }
                            Text(
                                text = visibleMonth.atDay(1).format(monthFormatter),
                                style = MaterialTheme.typography.titleMedium
                            )
                            TextButton(
                                onClick = { visibleMonthOffset += 1 }
                            ) {
                                Text(">")
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
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

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            calendarDays.chunked(7).forEach { week ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    week.forEach { day ->
                                        val workoutSessions = day?.let {
                                            workoutsByEpochDay[it.toEpochDay()].orEmpty()
                                        }.orEmpty()
                                        CalendarDayCell(
                                            modifier = Modifier.weight(1f),
                                            date = day,
                                            workoutSessions = workoutSessions,
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
            }

            item {
                Button(
                    onClick = onHistoryClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.history_screen_title))
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    modifier: Modifier,
    date: LocalDate?,
    workoutSessions: List<WorkoutSessionEntity>,
    onClick: (() -> Unit)?
) {
    val workoutCount = workoutSessions.size
    val hasWorkouts = date != null && workoutCount > 0
    val containerColor = when {
        hasWorkouts -> MaterialTheme.colorScheme.primaryContainer
        date == null -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    val contentColor = when {
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
                .padding(6.dp)
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
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }

    if (hasWorkouts && onClick != null) {
        Card(
            modifier = modifier.aspectRatio(1f),
            onClick = onClick,
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = containerColor)
        ) {
            content()
        }
    } else {
        Card(
            modifier = modifier.aspectRatio(1f),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = containerColor)
        ) {
            content()
        }
    }
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
