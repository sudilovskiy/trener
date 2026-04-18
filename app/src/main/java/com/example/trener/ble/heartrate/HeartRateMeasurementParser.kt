package com.example.trener.ble.heartrate

data class ParsedHeartRateMeasurement(
    val heartRateBpm: Int,
    val usesUInt16Format: Boolean,
    val rawFlags: Int
)

object HeartRateMeasurementParser {
    fun parse(payload: ByteArray): ParsedHeartRateMeasurement? {
        if (payload.isEmpty()) {
            return null
        }

        val flags = payload[0].toInt() and 0xFF
        val heartRateBpm = if (flags and FLAG_UINT16_VALUE_FORMAT != 0) {
            readUInt16LittleEndian(payload, 1) ?: return null
        } else {
            if (payload.size < 2) {
                return null
            }
            payload[1].toInt() and 0xFF
        }

        if (heartRateBpm <= 0) {
            return null
        }

        return ParsedHeartRateMeasurement(
            heartRateBpm = heartRateBpm,
            usesUInt16Format = flags and FLAG_UINT16_VALUE_FORMAT != 0,
            rawFlags = flags
        )
    }

    private fun readUInt16LittleEndian(payload: ByteArray, offset: Int): Int? {
        if (offset < 0 || payload.size < offset + 2) {
            return null
        }

        return (payload[offset].toInt() and 0xFF) or
            ((payload[offset + 1].toInt() and 0xFF) shl 8)
    }

    private const val FLAG_UINT16_VALUE_FORMAT = 0x01
}
