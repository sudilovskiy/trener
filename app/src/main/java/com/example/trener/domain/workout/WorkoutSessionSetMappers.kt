package com.example.trener.domain.workout

import com.example.trener.data.local.entity.WorkoutSessionSetEntity

fun WorkoutSessionSetEntity.toPreparedWorkoutSessionSet(): PreparedWorkoutSessionSet {
    return PreparedWorkoutSessionSet(
        exerciseId = exerciseId,
        setNumber = setNumber,
        reps = reps,
        weight = weight,
        additionalValue = additionalValue,
        parameterValue = parameterValue,
        parameterOverrideValue = parameterOverrideValue,
        flag = flag,
        note = note
    )
}

fun PreparedWorkoutSessionSet.toWorkoutSessionSetEntity(
    workoutSessionId: Long
): WorkoutSessionSetEntity {
    return WorkoutSessionSetEntity(
        workoutSessionId = workoutSessionId,
        exerciseId = exerciseId,
        setNumber = setNumber,
        reps = reps,
        weight = weight,
        additionalValue = additionalValue,
        parameterValue = parameterValue,
        parameterOverrideValue = parameterOverrideValue,
        flag = flag,
        note = note
    )
}
