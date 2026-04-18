package com.example.trener.data.local

import com.example.trener.ExerciseInputType

const val CATALOG_PARAMETER_TYPE_WEIGHT = "WEIGHT"
const val CATALOG_PARAMETER_TYPE_COUNT = "COUNT"
const val CATALOG_PARAMETER_TYPE_TIME = "TIME"
const val CATALOG_PARAMETER_TYPE_CUSTOM = "CUSTOM"

data class CatalogParameterMetadata(
    val parameterType: String,
    val parameterLabel: String,
    val parameterUnit: String
)

fun normalizeCatalogParameterType(parameterType: String): String {
    return when (parameterType.trim().uppercase()) {
        CATALOG_PARAMETER_TYPE_WEIGHT -> CATALOG_PARAMETER_TYPE_WEIGHT
        CATALOG_PARAMETER_TYPE_COUNT,
        "REPS",
        "ASSIST" -> CATALOG_PARAMETER_TYPE_COUNT
        CATALOG_PARAMETER_TYPE_TIME,
        "TIME_SECONDS" -> CATALOG_PARAMETER_TYPE_TIME
        CATALOG_PARAMETER_TYPE_CUSTOM -> CATALOG_PARAMETER_TYPE_CUSTOM
        else -> CATALOG_PARAMETER_TYPE_CUSTOM
    }
}

fun catalogParameterTypeForInputType(inputType: ExerciseInputType): String {
    return when (inputType) {
        ExerciseInputType.REPS -> CATALOG_PARAMETER_TYPE_COUNT
        ExerciseInputType.TIME_SECONDS -> CATALOG_PARAMETER_TYPE_TIME
    }
}

fun catalogParameterLabelForType(parameterType: String): String {
    return when (normalizeCatalogParameterType(parameterType)) {
        CATALOG_PARAMETER_TYPE_WEIGHT -> "\u0412\u0435\u0441"
        CATALOG_PARAMETER_TYPE_COUNT -> "\u041a\u043e\u043b\u0438\u0447\u0435\u0441\u0442\u0432\u043e"
        CATALOG_PARAMETER_TYPE_TIME -> "\u0412\u0440\u0435\u043c\u044f"
        CATALOG_PARAMETER_TYPE_CUSTOM -> ""
        else -> ""
    }
}

fun catalogParameterUnitForType(parameterType: String): String {
    return when (normalizeCatalogParameterType(parameterType)) {
        CATALOG_PARAMETER_TYPE_WEIGHT -> "\u043a\u0433"
        CATALOG_PARAMETER_TYPE_COUNT -> "\u0448\u0442"
        CATALOG_PARAMETER_TYPE_TIME -> "\u0441\u0435\u043a"
        CATALOG_PARAMETER_TYPE_CUSTOM -> ""
        else -> ""
    }
}

fun catalogParameterDefaultsForInputType(inputType: ExerciseInputType): CatalogParameterMetadata {
    val parameterType = catalogParameterTypeForInputType(inputType)
    return CatalogParameterMetadata(
        parameterType = parameterType,
        parameterLabel = catalogParameterLabelForType(parameterType),
        parameterUnit = catalogParameterUnitForType(parameterType)
    )
}

