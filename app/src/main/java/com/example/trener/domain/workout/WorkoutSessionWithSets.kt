package com.example.trener.domain.workout

import com.example.trener.data.local.entity.WorkoutSessionEntity
import com.example.trener.data.local.entity.WorkoutSessionSetEntity

data class WorkoutSessionWithSets(
    val session: WorkoutSessionEntity,
    val sets: List<WorkoutSessionSetEntity>
)
