package com.obdvis.android.domain.health

import org.junit.Assert.assertEquals
import org.junit.Test

class DtcInfoTest {

    @Test
    fun `system label identifies system and generic code`() {
        assertEquals("Powertrain — generic", DtcInfo.systemLabel("P0300"))
        assertEquals("Network / communication — generic", DtcInfo.systemLabel("U0123"))
    }

    @Test
    fun `system label identifies manufacturer-specific code`() {
        assertEquals("Body — manufacturer-specific", DtcInfo.systemLabel("B1234"))
    }

    @Test
    fun `generic explanation identifies only the broad system`() {
        assertEquals("Chassis-related fault", DtcInfo.genericExplanation("C0035"))
    }

    @Test
    fun `unknown code receives a safe fallback`() {
        assertEquals("Unknown system", DtcInfo.systemLabel(""))
        assertEquals("Unknown-system fault", DtcInfo.genericExplanation("X0000"))
    }
}
