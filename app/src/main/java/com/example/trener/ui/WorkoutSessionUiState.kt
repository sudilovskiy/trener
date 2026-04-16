package com.example.trener

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.trener.domain.workout.PreparedWorkoutSessionSet

class WorkoutSessionUiState : ViewModel() {
    private val exerciseSets = mutableStateMapOf<String, List<PreparedWorkoutSessionSet>>()
    private val completedSetNumbers = mutableStateMapOf<String, Set<Int>>()
    var activeWorkout by mutableStateOf<ActiveWorkoutState?>(null)
        private set
    var pendingFinishTrigger by mutableStateOf<WorkoutFinishTrigger?>(null)
        private set
    var isSaving by mutableStateOf(false)
        private set
    var lastSaveResult by mutableStateOf<WorkoutSaveResult?>(null)
        private set

    fun getExerciseSets(exerciseId: String, setCount: Int): List<PreparedWorkoutSessionSet> {
        return normalizeSetList(
            exerciseId = exerciseId,
            setCount = setCount,
            sets = exerciseSets[exerciseId]
        )
    }

    fun getExerciseSetUiStates(exerciseId: String, setCount: Int): List<ExerciseSetUiState> {
        val completedSets = completedSetNumbers[exerciseId].orEmpty()
        val activeSetNumber = (1..setCount).firstOrNull { it !in completedSets }
        return getExerciseSets(exerciseId, setCount).map { set ->
            ExerciseSetUiState(
                set = set,
                isCompleted = set.setNumber in completedSets,
                isLocked = set.setNumber in completedSets,
                isActiveTarget = set.setNumber == activeSetNumber
            )
        }
    }

    fun updateExerciseSets(
        exerciseId: String,
        sets: List<PreparedWorkoutSessionSet>,
        allowCompletedOverride: Boolean = false
    ) {
        if (!isExerciseInActiveWorkout(exerciseId) || isSaving) {
            return
        }

        val setCount = getRequiredSetCount(exerciseId)
        val currentSets = getExerciseSets(exerciseId, setCount)
        val completedSets = completedSetNumbers[exerciseId].orEmpty()
        val updatedSets = normalizeSetList(
            exerciseId = exerciseId,
            setCount = setCount,
            sets = sets
        ).mapIndexed { index, updatedSet ->
            if (!allowCompletedOverride && updatedSet.setNumber in completedSets) {
                currentSets[index]
            } else {
                updatedSet
            }
        }
        exerciseSets[exerciseId] = updatedSets
    }

    fun prefillExerciseSetsIfEmpty(exerciseId: String, sets: List<PreparedWorkoutSessionSet>) {
        if (!isExerciseInActiveWorkout(exerciseId) || isSaving) {
            return
        }

        val setCount = getRequiredSetCount(exerciseId)
        val currentSets = getExerciseSets(exerciseId, setCount)
        if (currentSets.any(::hasDraftContent)) {
            return
        }

        exerciseSets[exerciseId] = normalizeSetList(
            exerciseId = exerciseId,
            setCount = setCount,
            sets = sets
        )
    }

    fun completeSet(exerciseId: String, setNumber: Int): ExerciseSetCompletionResult? {
        if (!isExerciseInActiveWorkout(exerciseId) || isSaving) {
            return null
        }

        val setCount = getRequiredSetCount(exerciseId)
        if (setNumber !in 1..setCount) {
            return null
        }
        val updatedCompletedSets = completedSetNumbers[exerciseId].orEmpty() + setNumber
        if (updatedCompletedSets.isEmpty()) {
            completedSetNumbers.remove(exerciseId)
        } else {
            completedSetNumbers[exerciseId] = updatedCompletedSets
        }

        val nextActiveSetNumber = (1..setCount).firstOrNull { it !in updatedCompletedSets }
        return ExerciseSetCompletionResult(
            nextActiveSetNumber = nextActiveSetNumber,
            isExerciseCompleted = nextActiveSetNumber == null
        )
    }

    fun reactivateSet(exerciseId: String, setNumber: Int) {
        if (!isExerciseInActiveWorkout(exerciseId) || isSaving) {
            return
        }

        val currentCompletedSets = completedSetNumbers[exerciseId].orEmpty()
        if (setNumber !in currentCompletedSets) {
            return
        }

        val updatedCompletedSets = currentCompletedSets - setNumber
        if (updatedCompletedSets.isEmpty()) {
            completedSetNumbers.remove(exerciseId)
        } else {
            completedSetNumbers[exerciseId] = updatedCompletedSets
        }
    }

    fun hasEnteredData(exerciseId: String): Boolean {
        return exerciseSets[exerciseId]?.any(::hasDraftContent) ?: false
    }

    fun getEnteredSetsForDay(trainingDay: Int): List<PreparedWorkoutSessionSet> {
        return getExercisesForDay(trainingDay)
            .flatMap { definition ->
                val completedSets = completedSetNumbers[definition.exerciseId].orEmpty()
                exerciseSets[definition.exerciseId]
                    .orEmpty()
                    .filter { set ->
                        set.setNumber in completedSets && hasTrackedValue(set)
                    }
            }
    }

    fun hasActiveWorkout(): Boolean {
        return activeWorkout != null
    }

    fun isWorkoutActiveForDay(trainingDay: Int): Boolean {
        return activeWorkout?.trainingDay == trainingDay
    }

    fun isExerciseInActiveWorkout(exerciseId: String): Boolean {
        val currentWorkout = activeWorkout ?: return false
        return getExercisesForDay(currentWorkout.trainingDay)
            .any { definition -> definition.exerciseId == exerciseId }
    }

    fun getRequiredSetCount(@Suppress("UNUSED_PARAMETER") exerciseId: String): Int {
        return DEFAULT_SET_COUNT
    }

    fun isExerciseCompleted(exerciseId: String): Boolean {
        return isExerciseCompleted(
            exerciseId = exerciseId,
            setCount = getRequiredSetCount(exerciseId)
        )
    }

    fun isRepBasedExerciseCompleted(exerciseId: String): Boolean {
        val definition = getExerciseDefinition(exerciseId) ?: return false
        if (definition.inputType != ExerciseInputType.REPS) {
            return false
        }

        return isExerciseCompleted(
            exerciseId = exerciseId,
            setCount = getRequiredSetCount(exerciseId)
        )
    }

    fun isExerciseCompleted(exerciseId: String, setCount: Int): Boolean {
        return (1..setCount).all { setNumber ->
            completedSetNumbers[exerciseId]?.contains(setNumber) == true
        }
    }

    fun areAllExercisesCompleted(): Boolean {
        val currentWorkout = activeWorkout ?: return false
        return getExercisesForDay(currentWorkout.trainingDay)
            .all { definition ->
                isExerciseCompleted(
                    exerciseId = definition.exerciseId,
                    setCount = getRequiredSetCount(definition.exerciseId)
                )
            }
    }

    fun startWorkout(sessionId: Long, trainingDay: Int, startedAtMillis: Long) {
        clearDay(trainingDay)
        activeWorkout = ActiveWorkoutState(
            sessionId = sessionId,
            trainingDay = trainingDay,
            startedAtMillis = startedAtMillis
        )
        pendingFinishTrigger = null
        isSaving = false
        lastSaveResult = null
    }

    fun requestFinishWorkout(trigger: WorkoutFinishTrigger): Boolean {
        if (activeWorkout == null || isSaving || pendingFinishTrigger != null) {
            return false
        }

        pendingFinishTrigger = trigger
        return true
    }

    fun beginFinishingWorkout(): ActiveWorkoutFinishRequest? {
        val currentWorkout = activeWorkout ?: return null
        if (isSaving) {
            return null
        }
        val finishTrigger = pendingFinishTrigger ?: return null

        isSaving = true
        lastSaveResult = null

        return ActiveWorkoutFinishRequest(
            sessionId = currentWorkout.sessionId,
            trainingDay = currentWorkout.trainingDay,
            startedAtMillis = currentWorkout.startedAtMillis,
            finishTrigger = finishTrigger
        )
    }

    fun completeWorkoutSaveSuccessfully() {
        val trainingDay = activeWorkout?.trainingDay
        if (trainingDay != null) {
            clearDay(trainingDay)
        }
        activeWorkout = null
        pendingFinishTrigger = null
        isSaving = false
        lastSaveResult = WorkoutSaveResult.Success
    }

    fun failWorkoutSave() {
        pendingFinishTrigger = null
        isSaving = false
        lastSaveResult = WorkoutSaveResult.Error
    }

    fun clearLastSaveResult() {
        lastSaveResult = null
    }

    fun reset() {
        exerciseSets.clear()
        completedSetNumbers.clear()
        activeWorkout = null
        pendingFinishTrigger = null
        isSaving = false
        lastSaveResult = null
    }

    fun clearDay(trainingDay: Int) {
        getExercisesForDay(trainingDay).forEach { definition ->
            exerciseSets.remove(definition.exerciseId)
            completedSetNumbers.remove(definition.exerciseId)
        }
    }

    private fun normalizeSetList(
        exerciseId: String,
        setCount: Int,
        sets: List<PreparedWorkoutSessionSet>?
    ): List<PreparedWorkoutSessionSet> {
        return List(setCount) { index ->
            sets?.getOrNull(index)?.copy(
                exerciseId = exerciseId,
                setNumber = index + 1
            ) ?: PreparedWorkoutSessionSet(
                exerciseId = exerciseId,
                setNumber = index + 1
            )
        }
    }

}

data class ActiveWorkoutState(
    val sessionId: Long,
    val trainingDay: Int,
    val startedAtMillis: Long
)

data class ActiveWorkoutFinishRequest(
    val sessionId: Long,
    val trainingDay: Int,
    val startedAtMillis: Long,
    val finishTrigger: WorkoutFinishTrigger
)

enum class WorkoutFinishTrigger {
    UserAction,
    AllExercisesCompleted
}

enum class WorkoutSaveResult {
    Success,
    Error
}

private const val DEFAULT_SET_COUNT = 4

fun PreparedWorkoutSessionSet.hasEnteredValues(): Boolean {
    return hasTrackedValue(this)
}

private fun hasTrackedValue(set: PreparedWorkoutSessionSet): Boolean {
    return when (getExerciseDefinition(set.exerciseId)?.inputType ?: ExerciseInputType.REPS) {
        ExerciseInputType.REPS -> set.reps != null
        ExerciseInputType.TIME_SECONDS -> set.additionalValue != null
    }
}

private fun hasDraftContent(set: PreparedWorkoutSessionSet): Boolean {
    return hasTrackedValue(set) || set.note.isNotBlank()
}
