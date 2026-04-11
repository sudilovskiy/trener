package com.example.trener.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BleWeightMeasurementParserTest {
    @Test
    fun parsesTrailingLittleEndianWeightField() {
        val payload = byteArrayOf(
            0x02, 0x04, 0xEA.toByte(), 0x07, 0x04, 0x0B, 0x14,
            0x2E, 0x40, 0x00, 0x00, 0x2E, 0x40
        )

        val parsed = requireNotNull(BleWeightMeasurementParser.parse(payload))

        assertEquals(82.15f, parsed.weightKg, 0.01f)
        assertEquals(0x402E, parsed.rawValue)
        assertEquals(11, parsed.rawFieldOffset)
        assertEquals("2E 40", parsed.rawFieldHex)
    }

    @Test
    fun rejectsImpossibleTrailingWeight() {
        val payload = byteArrayOf(
            0x02, 0x04, 0xEA.toByte(), 0x07, 0x04, 0x0B, 0x14,
            0x2E, 0x40, 0x00, 0x00, 0x04, 0xEA.toByte()
        )

        val parsed = BleWeightMeasurementParser.parse(payload)

        assertNull(parsed)
    }

    @Test
    fun rejectsTooShortPayload() {
        val payload = byteArrayOf(0x00)

        val parsed = BleWeightMeasurementParser.parse(payload)

        assertNull(parsed)
    }
}
