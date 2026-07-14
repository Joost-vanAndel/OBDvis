package com.obdvis.android.data.obd

import androidx.compose.ui.graphics.Color
import com.obdvis.android.domain.model.PidDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ObdResponseParserTest {

    private val parser = ObdResponseParser()

    // Minimal PID factory — Color is an inline class (ULong), fine on JVM.
    private fun pid(hex: String, formula: (List<Int>) -> Float) = PidDefinition(
        id = "test",
        hex = hex,
        name = "Test",
        unit = "",
        min = 0f,
        max = 10000f,
        color = Color(0xFF000000.toInt()),
        formula = formula,
    )

    private val rpmPid = pid("0C") { bytes -> (bytes[0] * 256 + bytes[1]) / 4f }

    // "410C1AF8": header "410C", data bytes [0x1A=26, 0xF8=248] → (26*256+248)/4 = 1726
    @Test
    fun `parse RPM response returns correct value`() {
        val result = parser.parse(rpmPid, "410C1AF8")
        assertEquals(1726f, result)
    }

    @Test
    fun `parse response with spaces strips them before parsing`() {
        val result = parser.parse(rpmPid, "41 0C 1A F8")
        assertEquals(1726f, result)
    }

    @Test
    fun `parse lowercase response is treated case-insensitively`() {
        val result = parser.parse(rpmPid, "410c1af8")
        assertEquals(1726f, result)
    }

    @Test
    fun `parse NODATA returns null`() {
        assertNull(parser.parse(rpmPid, "NODATA"))
    }

    @Test
    fun `parse NO DATA with space returns null`() {
        assertNull(parser.parse(rpmPid, "NO DATA"))
    }

    @Test
    fun `parse empty string returns null`() {
        assertNull(parser.parse(rpmPid, ""))
    }

    @Test
    fun `parse UNABLETOCONNECT returns null`() {
        assertNull(parser.parse(rpmPid, "UNABLETOCONNECT"))
    }

    @Test
    fun `parse ERROR token returns null`() {
        assertNull(parser.parse(rpmPid, "ERROR"))
    }

    @Test
    fun `parse question mark returns null`() {
        assertNull(parser.parse(rpmPid, "?"))
    }

    @Test
    fun `parse response without matching header returns null`() {
        // Header would be "410C" but response has a different mode byte
        assertNull(parser.parse(rpmPid, "420C1AF8"))
    }

    @Test
    fun `parse pid with null hex returns null`() {
        val nullHexPid = pid("0C") { 0f }.copy(hex = null)
        assertNull(parser.parse(nullHexPid, "410C1AF8"))
    }

    @Test
    fun `parse malformed hex data returns null`() {
        // Data after header contains non-hex characters
        assertNull(parser.parse(rpmPid, "410CXX00"))
    }

    @Test
    fun `parse formula exception returns null`() {
        val throwingPid = pid("0C") { throw RuntimeException("formula error") }
        assertNull(parser.parse(throwingPid, "410C1AF8"))
    }

    @Test
    fun `parse single byte pid returns correct value`() {
        // Throttle PID 11: value = byte[0] / 2.55 (scaled to 0-100%)
        val throttlePid = pid("11") { bytes -> bytes[0] / 2.55f }
        // "4111FF": data = [0xFF=255] → 255/2.55 ≈ 100
        val result = parser.parse(throttlePid, "4111FF")!!
        assertEquals(100f, result, 0.1f)
    }
}
