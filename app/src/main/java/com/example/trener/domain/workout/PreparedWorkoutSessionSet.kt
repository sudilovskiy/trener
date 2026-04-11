package com.example.trener.domain.workout

data class PreparedWorkoutSessionSet(
    val exerciseId: String,
    val setNumber: Int,
    val reps: Int? = null,
    val weight: Double? = null,
    val additionalValue: Double? = null,
    val flag: Boolean? = null,
    val note: String = ""
)
