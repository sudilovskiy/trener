package com.example.trener.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "training_days",
    indices = [Index(value = ["sortOrder"])]
)
data class TrainingDayEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val sortOrder: Int
)
