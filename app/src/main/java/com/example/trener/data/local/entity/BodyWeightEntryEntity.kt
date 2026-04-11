package com.example.trener.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "body_weight_history",
    indices = [Index(value = ["entryDateEpochDay"], unique = true)]
)
data class BodyWeightEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entryDateEpochDay: Long,
    val weightKg: Double
)
