package com.example.trener

import com.example.trener.data.local.entity.BodyWeightEntryEntity
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

private const val BODY_WEIGHT_DISPLAY_SCALE = 1

fun normalizeBodyWeightKg(weightKg: Double): Double {
    return BigDecimal.valueOf(weightKg)
        .setScale(BODY_WEIGHT_DISPLAY_SCALE, RoundingMode.HALF_UP)
        .toDouble()
}

fun normalizeBodyWeightKg(weightKg: Float): Double {
    return normalizeBodyWeightKg(weightKg.toDouble())
}

fun formatBodyWeightKg(weightKg: Double): String {
    return String.format(Locale.US, "%.1f", normalizeBodyWeightKg(weightKg))
}

fun formatBodyWeightKg(weightKg: Float): String {
    return formatBodyWeightKg(weightKg.toDouble())
}

fun BodyWeightEntryEntity.normalizedWeight(): BodyWeightEntryEntity {
    return copy(weightKg = normalizeBodyWeightKg(weightKg))
}
