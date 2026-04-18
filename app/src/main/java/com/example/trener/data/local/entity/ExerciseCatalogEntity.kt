package com.example.trener.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exercise_catalog",
    indices = [
        Index(value = ["sortOrder"]),
        Index(value = ["isActive", "sortOrder"])
    ]
)
data class ExerciseCatalogEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val parameterType: String,
    val parameterLabel: String,
    val parameterUnit: String = "",
    val sortOrder: Int,
    val isActive: Boolean = true
)
