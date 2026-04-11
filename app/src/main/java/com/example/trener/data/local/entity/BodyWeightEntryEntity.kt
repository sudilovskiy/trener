package com.example.trener.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "body_weight_history"
)
data class BodyWeightEntryEntity(
    @PrimaryKey
    val entryDateEpochDay: Long,
    val weightKg: Double
)
