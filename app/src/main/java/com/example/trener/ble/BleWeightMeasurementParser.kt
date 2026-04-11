package com.example.trener.ble

import com.example.trener.formatBodyWeightKg

data class ParsedWeightMeasurement(
    val weightKg: Float,
    val confidence: Int,
    val rawValue: Int,
    val rawFieldHex: String,
    val rawFieldOffset: Int,
    val rawFieldLength: Int,
    val divisor: Int
)

object BleWeightMeasurementParser {
    private const val minWeightKg = 1.0f
    private const val maxWeightKg = 200.0f
    private const val weightDivisor = 200

    fun parse(payload: ByteArray): ParsedWeightMeasurement? {
        val rawFieldOffset = payload.size - 2
        val rawValue = readLittleEndianUInt(payload, rawFieldOffset, 2) ?: return null
        val weightKg = rawValue.toFloat() / weightDivisor

        if (!weightKg.isFinite() || weightKg !in minWeightKg..maxWeightKg) {
            return null
        }

        return ParsedWeightMeasurement(
            weightKg = weightKg,
            confidence = 100,
            rawValue = rawValue,
            rawFieldHex = payload.copyOfRange(rawFieldOffset, payload.size).toHexString(),
            rawFieldOffset = rawFieldOffset,
            rawFieldLength = 2,
            divisor = weightDivisor
        )
    }

    fun formatWeight(weightKg: Float): String {
        return formatBodyWeightKg(weightKg)
    }

    private fun readLittleEndianUInt(
        payload: ByteArray,
        offset: Int,
        byteCount: Int
    ): Int? {
        if (offset < 0 || byteCount <= 0 || payload.size < offset + byteCount) {
            return null
        }

        var rawValue = 0
        for (index in 0 until byteCount) {
            rawValue = rawValue or ((payload[offset + index].toInt() and 0xFF) shl (index * 8))
        }

        if (rawValue <= 0) {
            return null
        }

        return rawValue
    }
}

private fun ByteArray.toHexString(): String {
    return joinToString(separator = " ") { byte ->
        "%02X".format(byte.toInt() and 0xFF)
    }
}
