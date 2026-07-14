package com.obdvis.android.data.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DTC encoding per SAE J2012:
 *   Byte 1 bits 7-6: type (00=P, 01=C, 10=B, 11=U)
 *   Byte 1 bits 5-4: digit 2 (0-3)
 *   Byte 1 bits 3-0: digit 3 (0-F)
 *   Byte 2 bits 7-4: digit 4 (0-F)
 *   Byte 2 bits 3-0: digit 5 (0-F)
 *
 * Worked examples used in tests:
 *   [0x01, 0x02] → P0102  (type=00, d2=0, d3=1, d4=0, d5=2)
 *   [0x03, 0x04] → P0304
 *   [0x41, 0x02] → C0102  (type=01, d2=0, d3=1, d4=0, d5=2)
 *   [0x80, 0x00] → B0000  (type=10)
 *   [0xC1, 0x23] → U0123  (type=11)
 */
class DtcParserTest {

    @Test
    fun `parse mode header only returns empty list`() {
        assertTrue(DtcParser.parse("43").isEmpty())
    }

    @Test
    fun `parse NODATA returns empty list`() {
        assertTrue(DtcParser.parse("NODATA").isEmpty())
    }

    @Test
    fun `parse ERROR returns empty list`() {
        assertTrue(DtcParser.parse("ERROR").isEmpty())
    }

    @Test
    fun `parse empty string returns empty list`() {
        assertTrue(DtcParser.parse("").isEmpty())
    }

    @Test
    fun `parse null-padded pairs are skipped`() {
        // "43" + "0000" = one pair of zero bytes → should be skipped
        assertTrue(DtcParser.parse("430000").isEmpty())
    }

    @Test
    fun `parse single stored P-type DTC`() {
        // [0x01, 0x02] → P0102
        val result = DtcParser.parse("430102")
        assertEquals(listOf("P0102"), result)
    }

    @Test
    fun `parse two stored DTCs returns both`() {
        // [0x01, 0x02] → P0102, [0x03, 0x04] → P0304
        val result = DtcParser.parse("430102" + "0304")
        assertEquals(listOf("P0102", "P0304"), result)
    }

    @Test
    fun `parse C-type DTC decoded correctly`() {
        // [0x41, 0x02]: type=(0x41>>6)&3=1→C, d2=(0x41>>4)&3=0, d3=0x41&0xF=1, d4=0, d5=2 → C0102
        val result = DtcParser.parse("434102")
        assertEquals(listOf("C0102"), result)
    }

    @Test
    fun `parse B-type DTC decoded correctly`() {
        // [0x80, 0x00]: type=(0x80>>6)&3=2→B, d2=0, d3=0, d4=0, d5=0 → B0000
        val result = DtcParser.parse("438000")
        assertEquals(listOf("B0000"), result)
    }

    @Test
    fun `parse U-type DTC decoded correctly`() {
        // [0xC1, 0x23]: type=(0xC1>>6)&3=3→U, d2=(0xC1>>4)&3=0, d3=0xC1&0xF=1, d4=2, d5=3 → U0123
        val result = DtcParser.parse("43C123")
        assertEquals(listOf("U0123"), result)
    }

    @Test
    fun `parse pending DTCs uses mode 47`() {
        val result = DtcParser.parse("470102", responseMode = "47")
        assertEquals(listOf("P0102"), result)
    }

    @Test
    fun `parse odd byte count drops count prefix byte`() {
        // "43" + "03" (count=3) + "010203" = 4 bytes total after mode, odd → drop 0x03
        // Remaining: [0x01, 0x02, 0x03] → still odd; only complete pair used → P0102
        // Actually: "43" + "030102" + "0304" = "43030102" + "0304"
        // bytes after "43": [0x03, 0x01, 0x02, 0x03, 0x04] size=5 → odd → drop first (0x03)
        // remaining: [0x01, 0x02, 0x03, 0x04] → P0102, P0304
        val result = DtcParser.parse("4303" + "0102" + "0304")
        assertEquals(listOf("P0102", "P0304"), result)
    }

    @Test
    fun `parse duplicate codes are deduplicated`() {
        // Two frames with same DTC
        val result = DtcParser.parse("430102" + "430102")
        assertEquals(listOf("P0102"), result)
    }

    @Test
    fun `parse response with spaces is cleaned before processing`() {
        val result = DtcParser.parse("43 01 02")
        assertEquals(listOf("P0102"), result)
    }

    @Test
    fun `parse DTC with hex digits in position 4 and 5`() {
        // [0x01, 0xAB]: d4=0xA=10, d5=0xB=11 → P01AB
        val result = DtcParser.parse("4301AB")
        assertEquals(listOf("P01AB"), result)
    }
}
