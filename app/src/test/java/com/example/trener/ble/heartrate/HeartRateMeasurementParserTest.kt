package com.example.trener.ble.heartrate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateMeasurementParserTest {
    @Test
    fun parsesEightBitHeartRateMeasurement() {
        val parsed = HeartRateMeasurementParser.parse(
            byteArrayOf(0x00, 0x48)
        )

        assertEquals(
            ParsedHeartRateMeasurement(
                heartRateBpm = 72,
                usesUInt16Format = false,
                rawFlags = 0x00
            ),
            parsed
        )
    }

    @Test
    fun parsesSixteenBitHeartRateMeasurementWithTrailingFields() {
        val parsed = HeartRateMeasurementParser.parse(
            byteArrayOf(
                0x11,
                0xB4.toByte(),
                0x00,
                0x4D,
                0x00,
                0x50,
                0x00
            )
        )

        assertEquals(
            ParsedHeartRateMeasurement(
                heartRateBpm = 180,
                usesUInt16Format = true,
                rawFlags = 0x11
            ),
            parsed
        )
    }

    @Test
    fun returnsNullForEmptyOrInvalidPayloads() {
        assertNull(HeartRateMeasurementParser.parse(byteArrayOf()))
        assertNull(HeartRateMeasurementParser.parse(byteArrayOf(0x00)))
        assertNull(HeartRateMeasurementParser.parse(byteArrayOf(0x00, 0x00)))
    }
}
