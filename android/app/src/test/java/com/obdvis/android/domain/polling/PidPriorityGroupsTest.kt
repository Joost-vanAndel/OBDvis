package com.obdvis.android.domain.polling

import org.junit.Assert.assertEquals
import org.junit.Test

class PidPriorityGroupsTest {

    // ── overviewWeightOf ──────────────────────────────────────────────────────

    @Test
    fun `overviewWeightOf rpm is HIGH (4)`() {
        assertEquals(4f, PidPriorityGroups.overviewWeightOf("rpm"))
    }

    @Test
    fun `overviewWeightOf speed is HIGH (4)`() {
        assertEquals(4f, PidPriorityGroups.overviewWeightOf("speed"))
    }

    @Test
    fun `overviewWeightOf throttle is HIGH (4)`() {
        assertEquals(4f, PidPriorityGroups.overviewWeightOf("throttle"))
    }

    @Test
    fun `overviewWeightOf coolant is MEDIUM (2)`() {
        assertEquals(2f, PidPriorityGroups.overviewWeightOf("coolant"))
    }

    @Test
    fun `overviewWeightOf obd_voltage is MEDIUM (2)`() {
        assertEquals(2f, PidPriorityGroups.overviewWeightOf("obd_voltage"))
    }

    @Test
    fun `overviewWeightOf ecu_voltage is MEDIUM (2)`() {
        assertEquals(2f, PidPriorityGroups.overviewWeightOf("ecu_voltage"))
    }

    @Test
    fun `overviewWeightOf unknown pid is default (1)`() {
        assertEquals(1f, PidPriorityGroups.overviewWeightOf("baro"))
        assertEquals(1f, PidPriorityGroups.overviewWeightOf("stft"))
        assertEquals(1f, PidPriorityGroups.overviewWeightOf("unknown_pid"))
    }

    // ── diagnosticWeightOf ────────────────────────────────────────────────────

    @Test
    fun `diagnosticWeightOf rpm is HIGH (4)`() {
        assertEquals(4f, PidPriorityGroups.diagnosticWeightOf("rpm"))
    }

    @Test
    fun `diagnosticWeightOf stft is HIGH (4)`() {
        assertEquals(4f, PidPriorityGroups.diagnosticWeightOf("stft"))
    }

    @Test
    fun `diagnosticWeightOf o2_b1s1 is HIGH (4)`() {
        assertEquals(4f, PidPriorityGroups.diagnosticWeightOf("o2_b1s1"))
    }

    @Test
    fun `diagnosticWeightOf narrowband downstream and bank 2 sensors is HIGH (4)`() {
        assertEquals(4f, PidPriorityGroups.diagnosticWeightOf("o2_b1s2"))
        assertEquals(4f, PidPriorityGroups.diagnosticWeightOf("o2_b2s1"))
        assertEquals(4f, PidPriorityGroups.diagnosticWeightOf("o2_b2s2"))
    }

    @Test
    fun `diagnosticWeightOf coolant is MEDIUM (2)`() {
        assertEquals(2f, PidPriorityGroups.diagnosticWeightOf("coolant"))
    }

    @Test
    fun `diagnosticWeightOf timing is MEDIUM (2)`() {
        assertEquals(2f, PidPriorityGroups.diagnosticWeightOf("timing"))
    }

    @Test
    fun `diagnosticWeightOf unknown pid is default (1)`() {
        assertEquals(1f, PidPriorityGroups.diagnosticWeightOf("run_time"))
        assertEquals(1f, PidPriorityGroups.diagnosticWeightOf("dist_cleared"))
        assertEquals(1f, PidPriorityGroups.diagnosticWeightOf("unknown_pid"))
    }

    // ── maxAgeMs ──────────────────────────────────────────────────────────────

    @Test
    fun `maxAgeMs rpm is 2 seconds`() {
        assertEquals(2_000L, PidPriorityGroups.maxAgeMs("rpm"))
    }

    @Test
    fun `maxAgeMs stft is 2 seconds`() {
        assertEquals(2_000L, PidPriorityGroups.maxAgeMs("stft"))
    }

    @Test
    fun `maxAgeMs ltft is 5 seconds`() {
        assertEquals(5_000L, PidPriorityGroups.maxAgeMs("ltft"))
    }

    @Test
    fun `maxAgeMs coolant is 15 seconds`() {
        assertEquals(15_000L, PidPriorityGroups.maxAgeMs("coolant"))
    }

    @Test
    fun `maxAgeMs intake_temp is 30 seconds`() {
        assertEquals(30_000L, PidPriorityGroups.maxAgeMs("intake_temp"))
    }

    @Test
    fun `maxAgeMs unknown DIAG_HIGH pid falls back to 2 seconds`() {
        // fuel_sys_status is in DIAG_HIGH but also has an explicit entry (5s)
        // load is in DIAG_HIGH but has no explicit entry → falls back to 2s
        assertEquals(2_000L, PidPriorityGroups.maxAgeMs("load"))
    }

    @Test
    fun `maxAgeMs unknown DIAG_MEDIUM pid falls back to 10 seconds`() {
        // catalyst_temp_b1s1 is in DIAG_MEDIUM, no explicit entry → 10s
        assertEquals(10_000L, PidPriorityGroups.maxAgeMs("catalyst_temp_b1s1"))
    }

    @Test
    fun `maxAgeMs non-diagnostic pid falls back to 60 seconds`() {
        assertEquals(60_000L, PidPriorityGroups.maxAgeMs("run_time"))
        assertEquals(60_000L, PidPriorityGroups.maxAgeMs("totally_unknown"))
    }
}
