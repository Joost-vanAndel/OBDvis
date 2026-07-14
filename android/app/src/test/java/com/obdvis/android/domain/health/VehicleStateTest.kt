package com.obdvis.android.domain.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleStateTest {

    private fun state(
        rpm: Float? = null,
        coolantTempC: Float? = null,
        fuelSystemStatusRaw: Float? = null,
        o2SensorsPresentRaw: Float? = null,
        monitorReadinessPacked: Float? = null,
    ) = VehicleState(
        timestamp = 0L,
        rpm = rpm,
        speedKph = null,
        coolantTempC = coolantTempC,
        intakeTempC = null,
        mafGps = null,
        mapKpa = null,
        throttlePct = null,
        engineLoadPct = null,
        fuelLevelPct = null,
        stftBank1Pct = null,
        ltftBank1Pct = null,
        fuelSystemStatusRaw = fuelSystemStatusRaw,
        o2SensorsPresentRaw = o2SensorsPresentRaw,
        monitorReadinessPacked = monitorReadinessPacked,
    )

    // ── engineOn ─────────────────────────────────────────────────────────────

    @Test
    fun `engineOn is true when RPM is above 100`() {
        assertTrue(state(rpm = 800f).engineOn)
    }

    @Test
    fun `engineOn is true at boundary value 101`() {
        assertTrue(state(rpm = 101f).engineOn)
    }

    @Test
    fun `engineOn is false when RPM is exactly 100`() {
        assertFalse(state(rpm = 100f).engineOn)
    }

    @Test
    fun `engineOn is false when RPM is zero`() {
        assertFalse(state(rpm = 0f).engineOn)
    }

    @Test
    fun `engineOn is false when RPM is null`() {
        assertFalse(state(rpm = null).engineOn)
    }

    // ── isClosedLoop ─────────────────────────────────────────────────────────

    @Test
    fun `isClosedLoop is true when bit 1 of fuelSystemStatusRaw is set`() {
        // bit 1 = 0x02 = 2
        assertEquals(true, state(fuelSystemStatusRaw = 2f).isClosedLoop)
    }

    @Test
    fun `isClosedLoop is false when bit 1 is not set`() {
        assertEquals(false, state(fuelSystemStatusRaw = 1f).isClosedLoop)
    }

    @Test
    fun `isClosedLoop is true when multiple bits set including bit 1`() {
        // bits 1 and 2 set = 0b110 = 6
        assertEquals(true, state(fuelSystemStatusRaw = 6f).isClosedLoop)
    }

    @Test
    fun `isClosedLoop is null when fuelSystemStatusRaw is null`() {
        assertNull(state(fuelSystemStatusRaw = null).isClosedLoop)
    }

    // ── isOpenLoopFault ───────────────────────────────────────────────────────

    @Test
    fun `isOpenLoopFault is true when bit 3 (0x08) is set`() {
        assertTrue(state(fuelSystemStatusRaw = 8f).isOpenLoopFault)
    }

    @Test
    fun `isOpenLoopFault is false when bit 3 is not set`() {
        assertFalse(state(fuelSystemStatusRaw = 2f).isOpenLoopFault)
    }

    @Test
    fun `isOpenLoopFault is false when fuelSystemStatusRaw is null`() {
        assertFalse(state(fuelSystemStatusRaw = null).isOpenLoopFault)
    }

    // ── isClosedLoopFault ─────────────────────────────────────────────────────

    @Test
    fun `isClosedLoopFault is true when bit 4 (0x10) is set`() {
        assertTrue(state(fuelSystemStatusRaw = 16f).isClosedLoopFault)
    }

    @Test
    fun `isClosedLoopFault is false when bit 4 is not set`() {
        assertFalse(state(fuelSystemStatusRaw = 8f).isClosedLoopFault)
    }

    // ── hasUpstreamO2B1 ───────────────────────────────────────────────────────

    @Test
    fun `hasUpstreamO2B1 is true when bit 0 of o2SensorsPresentRaw is set`() {
        assertTrue(state(o2SensorsPresentRaw = 1f).hasUpstreamO2B1)
    }

    @Test
    fun `hasUpstreamO2B1 is false when bit 0 is not set`() {
        assertFalse(state(o2SensorsPresentRaw = 2f).hasUpstreamO2B1)
    }

    @Test
    fun `hasUpstreamO2B1 defaults to true when o2SensorsPresentRaw is null`() {
        // null → (0x01 & 0x01) = 1 ≠ 0 → true
        assertTrue(state(o2SensorsPresentRaw = null).hasUpstreamO2B1)
    }

    // ── hasDownstreamO2B1 ─────────────────────────────────────────────────────

    @Test
    fun `hasDownstreamO2B1 is true when bit 1 of o2SensorsPresentRaw is set`() {
        assertTrue(state(o2SensorsPresentRaw = 2f).hasDownstreamO2B1)
    }

    @Test
    fun `hasDownstreamO2B1 is false when bit 1 is not set`() {
        assertFalse(state(o2SensorsPresentRaw = 1f).hasDownstreamO2B1)
    }

    @Test
    fun `hasDownstreamO2B1 defaults to true when o2SensorsPresentRaw is null`() {
        // null → (0x02 & 0x02) = 2 ≠ 0 → true
        assertTrue(state(o2SensorsPresentRaw = null).hasDownstreamO2B1)
    }

    // ── monitorStatuses ───────────────────────────────────────────────────────

    @Test
    fun `monitorStatuses returns null when monitorReadinessPacked is null`() {
        assertNull(state(monitorReadinessPacked = null).monitorStatuses)
    }

    @Test
    fun `monitorStatuses marks Catalyst as INCOMPLETE when available and incomplete`() {
        // available = 0x01 (Catalyst supported), incomplete = 0x01 (Catalyst not done)
        // packed = (0x01 << 8) | 0x01 = 257
        val statuses = state(monitorReadinessPacked = 257f).monitorStatuses!!
        val catalyst = statuses.first { it.name == "Catalyst" }
        assertEquals(MonitorReadinessState.INCOMPLETE, catalyst.state)
    }

    @Test
    fun `monitorStatuses marks Catalyst as COMPLETE when available and not in incomplete mask`() {
        // available = 0x01, incomplete = 0x00
        // packed = (0x01 << 8) | 0x00 = 256
        val statuses = state(monitorReadinessPacked = 256f).monitorStatuses!!
        val catalyst = statuses.first { it.name == "Catalyst" }
        assertEquals(MonitorReadinessState.COMPLETE, catalyst.state)
    }

    @Test
    fun `monitorStatuses marks Catalyst as NOT_SUPPORTED when not in available mask`() {
        // available = 0x00, incomplete = 0x00
        // packed = 0
        val statuses = state(monitorReadinessPacked = 0f).monitorStatuses!!
        val catalyst = statuses.first { it.name == "Catalyst" }
        assertEquals(MonitorReadinessState.NOT_SUPPORTED, catalyst.state)
    }

    @Test
    fun `monitorStatuses mixed — supported complete and supported incomplete`() {
        // Catalyst (0x01) supported + complete, Heated Catalyst (0x02) supported + incomplete
        // available = 0x03, incomplete = 0x02
        // packed = (0x03 << 8) | 0x02 = 770
        val statuses = state(monitorReadinessPacked = 770f).monitorStatuses!!
        val catalyst = statuses.first { it.name == "Catalyst" }
        val heated = statuses.first { it.name == "Heated Catalyst" }
        assertEquals(MonitorReadinessState.COMPLETE, catalyst.state)
        assertEquals(MonitorReadinessState.INCOMPLETE, heated.state)
    }

    // ── fromLatestValues ─────────────────────────────────────────────────────

    @Test
    fun `fromLatestValues maps rpm and speed correctly`() {
        val state = VehicleState.fromLatestValues(mapOf("rpm" to 1500f, "speed" to 80f))
        assertEquals(1500f, state.rpm)
        assertEquals(80f, state.speedKph)
    }

    @Test
    fun `fromLatestValues uses obd_voltage as batteryVoltage with ecu_voltage fallback`() {
        val onlyObd = VehicleState.fromLatestValues(mapOf("obd_voltage" to 14f))
        assertEquals(14f, onlyObd.batteryVoltage)

        val onlyEcu = VehicleState.fromLatestValues(mapOf("ecu_voltage" to 13.5f))
        assertEquals(13.5f, onlyEcu.batteryVoltage)
    }

    @Test
    fun `fromLatestValues field absent in map produces null`() {
        val state = VehicleState.fromLatestValues(emptyMap())
        assertNull(state.rpm)
        assertNull(state.coolantTempC)
        assertNull(state.batteryVoltage)
    }
}
